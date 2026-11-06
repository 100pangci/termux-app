package com.termux.app.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.shell.ArgumentTokenizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Discovers host aliases in the user's OpenSSH config for the import UI.
 *
 * <p>This is deliberately <b>not</b> a replacement OpenSSH config parser. It only collects
 * {@code Host} patterns that are concrete aliases so they can be shown in a picker. The app always
 * passes the selected alias to {@code ssh}, which resolves HostName, User, Port, IdentityFile,
 * Include, wildcard matching and precedence itself.</p>
 */
public final class SshConfigHosts {

    private static final int MAX_INCLUDE_DEPTH = 8;
    private static final int MAX_CONFIG_FILES = 128;
    private static final long MAX_CONFIG_FILE_SIZE = 4L * 1024L * 1024L;

    private SshConfigHosts() {
    }

    @NonNull
    public static List<String> readAliases(@NonNull File configFile, @NonNull File sshDirectory,
                                           @NonNull File homeDirectory) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        Set<String> visitedFiles = new HashSet<>();
        int[] visitedCount = new int[]{0};

        readConfigFile(configFile, sshDirectory, homeDirectory, aliases, visitedFiles,
            visitedCount, 0);

        List<String> result = new ArrayList<>(aliases);
        Collections.sort(result, (left, right) -> left.compareToIgnoreCase(right));
        return result;
    }

    private static void readConfigFile(@NonNull File file, @NonNull File sshDirectory,
                                       @NonNull File homeDirectory, @NonNull Set<String> aliases,
                                       @NonNull Set<String> visitedFiles, @NonNull int[] visitedCount,
                                       int depth) {
        if (depth > MAX_INCLUDE_DEPTH || visitedCount[0] >= MAX_CONFIG_FILES) return;

        File canonicalFile;
        try {
            canonicalFile = file.getCanonicalFile();
        } catch (Exception e) {
            return;
        }

        if (!canonicalFile.isFile() || canonicalFile.length() > MAX_CONFIG_FILE_SIZE) return;
        if (!visitedFiles.add(canonicalFile.getPath())) return;
        visitedCount[0]++;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            new FileInputStream(canonicalFile), StandardCharsets.UTF_8))) {

            String line;
            boolean conditional = false;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                if (line.charAt(0) == '\uFEFF') line = line.substring(1);

                String uncommented = stripComment(line);
                String trimmed = uncommented.trim();
                if (trimmed.isEmpty()) continue;

                int separator = findKeywordSeparator(trimmed);
                String keyword = separator < 0 ? trimmed : trimmed.substring(0, separator);
                String rest = separator < 0 ? "" : trimmed.substring(separator + 1);
                if (separator >= 0 && Character.isWhitespace(trimmed.charAt(separator))) {
                    // OpenSSH also accepts one optional '=' after the whitespace separator, so
                    // "Host = alias" is equivalent to "Host alias" and "Host = *" is a wildcard,
                    // not a pattern named "=". A compact "Host=alias" has already consumed its
                    // '=' in findKeywordSeparator() and must not be stripped again.
                    rest = stripEqualsSeparator(rest);
                }

                if (keyword.equalsIgnoreCase("Host")) {
                    // A Host line always starts a new conditional block, even when nested in another
                    // block. Only wildcard Host * leaves the following Include directives active for
                    // every destination.
                    conditional = !isUnconditionalHost(rest);
                    for (String pattern : ArgumentTokenizer.tokenize(rest)) {
                        if (isConcreteAlias(pattern)) aliases.add(pattern);
                    }
                } else if (keyword.equalsIgnoreCase("Match")) {
                    conditional = !isUnconditionalMatch(rest);
                } else if (keyword.equalsIgnoreCase("Include")) {
                    // OpenSSH only processes an Include when the enclosing Host/Match block is
                    // active. Importing aliases from a conditional Include would list hosts that
                    // OpenSSH does not actually define for that destination.
                    if (conditional) continue;

                    for (String includePattern : ArgumentTokenizer.tokenize(rest)) {
                        for (File includedFile : expandIncludePattern(includePattern, sshDirectory,
                            homeDirectory)) {
                            readConfigFile(includedFile, sshDirectory, homeDirectory, aliases,
                                visitedFiles, visitedCount, depth + 1);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // A missing or unreadable config is treated as "no aliases"; the actual SSH invocation
            // will still report the real problem to the user.
        }
    }

    /**
     * Find where the keyword ends. OpenSSH accepts both {@code Host foo} and {@code Host=foo}.
     */
    private static int findKeywordSeparator(@NonNull String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c) || c == '=') return i;
        }
        return -1;
    }

    /**
     * Remove the single optional {@code =} that OpenSSH allows between a keyword and its
     * arguments. Only this separator is special; a second {@code =} belongs to the pattern.
     */
    @NonNull
    private static String stripEqualsSeparator(@NonNull String rest) {
        int index = 0;
        while (index < rest.length() && Character.isWhitespace(rest.charAt(index))) index++;
        if (index < rest.length() && rest.charAt(index) == '=') index++;
        return rest.substring(index);
    }

    /** {@code Host *} applies to every destination, so a following Include is not conditional. */
    private static boolean isUnconditionalHost(@NonNull String rest) {
        List<String> patterns = ArgumentTokenizer.tokenize(rest);
        return patterns.size() == 1 && "*".equals(patterns.get(0));
    }

    /** {@code Match all} applies to every destination. */
    private static boolean isUnconditionalMatch(@NonNull String rest) {
        List<String> criteria = ArgumentTokenizer.tokenize(rest);
        return !criteria.isEmpty() && "all".equalsIgnoreCase(criteria.get(0));
    }

    /**
     * Strip a comment that starts at the beginning of the line or after whitespace. A {@code #}
     * inside a quoted token is not a comment.
     */
    @NonNull
    private static String stripComment(@NonNull String line) {
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaped = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\' && !singleQuoted) {
                escaped = true;
                continue;
            }

            if (c == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
                continue;
            }

            if (c == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
                continue;
            }

            if (c == '#' && !singleQuoted && !doubleQuoted
                && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                return line.substring(0, i);
            }
        }

        return line;
    }

    private static boolean isConcreteAlias(@Nullable String alias) {
        if (alias == null) return false;
        String trimmed = alias.trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.indexOf('*') >= 0 || trimmed.indexOf('?') >= 0
            || trimmed.indexOf('[') >= 0 || trimmed.indexOf(']') >= 0) {
            return false;
        }
        if (trimmed.startsWith("!")) return false;
        return SshHostValidator.isValidConfigAlias(trimmed);
    }

    @NonNull
    private static List<File> expandIncludePattern(@NonNull String pattern, @NonNull File sshDirectory,
                                                   @NonNull File homeDirectory) {
        List<File> result = new ArrayList<>();
        String expanded = pattern;

        if (expanded.equals("~")) {
            expanded = homeDirectory.getPath();
        } else if (expanded.startsWith("~/")) {
            expanded = new File(homeDirectory, expanded.substring(2)).getPath();
        }

        File base;
        String relativePattern;
        if (expanded.startsWith("/")) {
            base = new File("/");
            relativePattern = expanded.substring(1);
        } else {
            base = sshDirectory;
            relativePattern = expanded;
        }

        List<String> segments = splitPath(relativePattern);
        expandGlobSegments(base, segments, 0, result);
        return result;
    }

    @NonNull
    private static List<String> splitPath(@NonNull String path) {
        List<String> segments = new ArrayList<>();
        for (String segment : path.replace('\\', '/').split("/")) {
            if (!segment.isEmpty()) segments.add(segment);
        }
        return segments;
    }

    private static void expandGlobSegments(@NonNull File base, @NonNull List<String> segments,
                                           int index, @NonNull List<File> result) {
        if (result.size() >= MAX_CONFIG_FILES) return;

        if (index >= segments.size()) {
            if (base.isFile()) result.add(base);
            return;
        }

        String segment = segments.get(index);
        if (segment.equals(".")) {
            expandGlobSegments(base, segments, index + 1, result);
            return;
        }
        if (segment.equals("..")) {
            File parent = base.getParentFile();
            if (parent != null) expandGlobSegments(parent, segments, index + 1, result);
            return;
        }

        if (!containsGlob(segment)) {
            expandGlobSegments(new File(base, segment), segments, index + 1, result);
            return;
        }

        Pattern pattern = compileGlob(segment);
        if (pattern == null) return;

        File[] children = base.listFiles();
        if (children == null) return;

        List<String> sortedChildren = new ArrayList<>();
        for (File child : children) {
            if (pattern.matcher(child.getName()).matches()) sortedChildren.add(child.getName());
        }
        Collections.sort(sortedChildren);

        for (String childName : sortedChildren) {
            expandGlobSegments(new File(base, childName), segments, index + 1, result);
            if (result.size() >= MAX_CONFIG_FILES) return;
        }
    }

    private static boolean containsGlob(@NonNull String value) {
        return value.indexOf('*') >= 0 || value.indexOf('?') >= 0 || value.indexOf('[') >= 0;
    }

    @Nullable
    private static Pattern compileGlob(@NonNull String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append('.');
            } else if (c == '[') {
                int end = glob.indexOf(']', i + 1);
                if (end < 0) {
                    regex.append("\\[");
                } else {
                    String characterClass = glob.substring(i + 1, end);
                    if (characterClass.startsWith("!")) {
                        characterClass = "^" + characterClass.substring(1);
                    }
                    regex.append('[').append(characterClass).append(']');
                    i = end;
                }
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        regex.append('$');

        try {
            return Pattern.compile(regex.toString());
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

}
