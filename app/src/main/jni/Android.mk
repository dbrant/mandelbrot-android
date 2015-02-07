BASE_PATH := .

include $(CLEAR_VARS)
LOCAL_CFLAGS += -DNDEBUG
LOCAL_CFLAGS += -O3
LOCAL_CPPFLAGS += -DNDEBUG
LOCAL_CPPFLAGS += -O3
LOCAL_LDLIBS := -llog
LOCAL_LDFLAGS += -ljnigraphics

LOCAL_PATH := $(BASE_PATH)
LOCAL_MODULE := mandelnative_jni

LOCAL_SRC_FILES := \
    mandelbrot_wrap.c \
	mandel_native.cpp \

include $(BUILD_SHARED_LIBRARY)

