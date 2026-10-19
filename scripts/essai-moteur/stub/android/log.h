#pragma once
#include <cstdio>
#define ANDROID_LOG_DEFAULT 1
#define ANDROID_LOG_VERBOSE 2
#define ANDROID_LOG_DEBUG 3
#define ANDROID_LOG_INFO 4
#define ANDROID_LOG_WARN 5
#define ANDROID_LOG_ERROR 6
static inline int __android_log_is_loggable(int, const char*, int) { return 0; }
static inline int __android_log_print(int, const char*, const char* fmt, ...) { return 0; }
static inline int __android_log_write(int, const char*, const char*) { return 0; }
