LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := libtermux-bootstrap
LOCAL_SRC_FILES := termux-bootstrap-zip.S termux-bootstrap.c
include $(BUILD_SHARED_LIBRARY)

# termux-bootstrap-zip.S embeds the zips with .incbin, but clang's generated
# dependency file only lists termux-bootstrap-zip.S. Without an explicit
# dependency, ndk-build would keep a stale object after the bootstrap zips are
# replaced (e.g. when switching from upstream to custom-prefix bootstraps) and
# the APK would still contain the old bootstrap. LOCAL_OBJS_DIR is only defined
# after the build rules above have been evaluated, hence the rule comes last.
ifdef LOCAL_OBJS_DIR
$(LOCAL_OBJS_DIR)/termux-bootstrap-zip$(TARGET_OBJ_EXTENSION): $(wildcard $(LOCAL_PATH)/bootstrap-*.zip)
endif
