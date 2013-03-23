/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "sun_jvm_hotspot_asm_Disassembler.h"

/*
 *  This file implements a binding between Java and the hsdis
 *  dissasembler.  It should compile on Linux/Solaris and Windows.
 *  The only platform dependent pieces of the code for doing
 *  dlopen/dlsym to find the entry point in hsdis.  All the rest is
 *  standard JNI code.
 */

#ifdef _WINDOWS

#define snprintf  _snprintf
#define vsnprintf _vsnprintf

#include <windows.h>
#include <sys/types.h>
#include <sys/stat.h>
#ifdef _DEBUG
#include <crtdbg.h>
#endif

#else

#include <string.h>
#include <dlfcn.h>

#ifndef __APPLE__
#include <link.h>
#endif

#endif

#include <limits.h>
#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>
#include <errno.h>

#ifdef _WINDOWS
static int getLastErrorString(char *buf, size_t len)
{
    long errval;

    if ((errval = GetLastError()) != 0)
    {
      /* DOS error */
      size_t n = (size_t)FormatMessage(
            FORMAT_MESSAGE_FROM_SYSTEM|FORMAT_MESSAGE_IGNORE_INSERTS,
            NULL,
            errval,
            0,
            buf,
            (DWORD)len,
            NULL);
      if (n > 3) {
        /* Drop final '.', CR, LF */
        if (buf[n - 1] == '\n') n--;
        if (buf[n - 1] == '\r') n--;
        if (buf[n - 1] == '.') n--;
        buf[n] = '\0';
      }
      return (int)n;
    }

    if (errno != 0)
    {
      /* C runtime error that has no corresponding DOS error code */
      const char *s = strerror(errno);
      size_t n = strlen(s);
      if (n >= len) n = len - 1;
      strncpy(buf, s, n);
      buf[n] = '\0';
      return (int)n;
    }
    return 0;
}
#endif /* _WINDOWS */

/*
 * Class:     sun_jvm_hotspot_asm_Disassembler
 * Method:    load_library
 * Signature: (Ljava/lang/String;)L
 */
JNIEXPORT jlong JNICALL Java_sun_jvm_hotspot_asm_Disassembler_load_1library(JNIEnv * env,
                                                                           jclass disclass,
                                                                           jstring jrepath_s,
                                                                           jstring libname_s) {
  uintptr_t func = 0;
  const char* error_message = NULL;
  jboolean isCopy;

  const char * jrepath = (*env)->GetStringUTFChars(env, jrepath_s, &isCopy); // like $JAVA_HOME/jre/lib/sparc/
  const char * libname = (*env)->GetStringUTFChars(env, libname_s, &isCopy);
  char buffer[128];

  /* Load the hsdis library */
#ifdef _WINDOWS
  HINSTANCE hsdis_handle;
  hsdis_handle = LoadLibrary(libname);
  if (hsdis_handle == NULL) {
    snprintf(buffer, sizeof(buffer), "%s%s", jrepath, libname);
    hsdis_handle = LoadLibrary(buffer);
  }
  if (hsdis_handle != NULL) {
    func = (uintptr_t)GetProcAddress(hsdis_handle, "decode_instructions_virtual");
  }
  if (func == 0) {
    getLastErrorString(buffer, sizeof(buffer));
    error_message = buffer;
  }
#else
  void* hsdis_handle;
  hsdis_handle = dlopen(libname, RTLD_LAZY | RTLD_GLOBAL);
  if (hsdis_handle == NULL) {
    snprintf(buffer, sizeof(buffer), "%s%s", jrepath, libname);
    hsdis_handle = dlopen(buffer, RTLD_LAZY | RTLD_GLOBAL);
  }
  if (hsdis_handle != NULL) {
    func = (uintptr_t)dlsym(hsdis_handle, "decode_instructions_virtual");
  }
  if (func == 0) {
    error_message = dlerror();
  }
#endif

  (*env)->ReleaseStringUTFChars(env, libname_s, libname);
  (*env)->ReleaseStringUTFChars(env, jrepath_s, jrepath);

  if (func == 0) {
    /* Couldn't find entry point.  error_message should contain some
     * platform dependent error message.
     */
    jclass eclass = (*env)->FindClass(env, "sun/jvm/hotspot/debugger/DebuggerException");
    (*env)->ThrowNew(env, eclass, error_message);
  }
  return (jlong)func;
}

/* signature of decode_instructions_virtual from hsdis.h */
typedef void* (*decode_func)(uintptr_t start_va, uintptr_t end_va,
                             unsigned char* start, uintptr_t length,
                             void* (*event_callback)(void*, const char*, void*),
                             void* event_stream,
                             int (*printf_callback)(void*, const char*, ...),
                             void* printf_stream,
                             const char* options,
                             int newline);

/* container for call back state when decoding instructions */
typedef struct {
  JNIEnv* env;
  jobject dis;
  jobject visitor;
  jmethodID handle_event;
  jmethodID raw_print;
  char buffer[4096];
} decode_env;


/* event callback binding to Disassembler.handleEvent */
static void* event_to_env(void* env_pv, const char* event, void* arg) {
  decode_env* denv = (decode_env*)env_pv;
  JNIEnv* env = denv->env;
  jstring event_string = (*env)->NewStringUTF(env, event);
  jlong result = (*env)->CallLongMethod(env, denv->dis, denv->handle_event, denv->visitor,
                                        event_string, (jlong) (uintptr_t)arg);
  if ((*env)->ExceptionOccurred(env) != NULL) {
    /* ignore exceptions for now */
    (*env)->ExceptionClear(env);
    result = 0;
  }
  return (void*)(uintptr_t)result;
}

/* printing callback binding to Disassembler.rawPrint */
static int printf_to_env(void* env_pv, const char* format, ...) {
  jstring output;
  va_list ap;
  int cnt;
  decode_env* denv = (decode_env*)env_pv;
  JNIEnv* env = denv->env;
  size_t flen = strlen(format);
  const char* raw = NULL;

  if (flen == 0)  return 0;
  if (flen < 2 ||
      strchr(format, '%') == NULL) {
    raw = format;
  } else if (format[0] == '%' && format[1] == '%' &&
             strchr(format+2, '%') == NULL) {
    // happens a lot on machines with names like %foo
    flen--;
    raw = format+1;
  }
  if (raw != NULL) {
    jstring output = (*env)->NewStringUTF(env, raw);
    (*env)->CallVoidMethod(env, denv->dis, denv->raw_print, denv->visitor, output);
    if ((*env)->ExceptionOccurred(env) != NULL) {
      /* ignore exceptions for now */
      (*env)->ExceptionClear(env);
    }
    return (int) flen;
  }
  va_start(ap, format);
  cnt = vsnprintf(denv->buffer, sizeof(denv->buffer), format, ap);
  va_end(ap);

  output = (*env)->NewStringUTF(env, denv->buffer);
  (*env)->CallVoidMethod(env, denv->dis, denv->raw_print, denv->visitor, output);
  if ((*env)->ExceptionOccurred(env) != NULL) {
    /* ignore exceptions for now */
    (*env)->ExceptionClear(env);
  }
  return cnt;
}

/*
 * Class:     sun_jvm_hotspot_asm_Disassembler
 * Method:    decode
 * Signature: (Lsun/jvm/hotspot/asm/InstructionVisitor;J[BLjava/lang/String;J)V
 */
JNIEXPORT void JNICALL Java_sun_jvm_hotspot_asm_Disassembler_decode(JNIEnv * env,
                                                                    jobject dis,
                                                                    jobject visitor,
                                                                    jlong startPc,
                                                                    jbyteArray code,
                                                                    jstring options_s,
                                                                    jlong decode_instructions_virtual) {
  jboolean isCopy;
  jbyte* start = (*env)->GetByteArrayElements(env, code, &isCopy);
  jbyte* end = start + (*env)->GetArrayLength(env, code);
  const char * options = (*env)->GetStringUTFChars(env, options_s, &isCopy);
  jclass disclass = (*env)->GetObjectClass(env, dis);

  decode_env denv;
  denv.env = env;
  denv.dis = dis;
  denv.visitor = visitor;

  /* find Disassembler.handleEvent callback */
  denv.handle_event = (*env)->GetMethodID(env, disclass, "handleEvent",
                                          "(Lsun/jvm/hotspot/asm/InstructionVisitor;Ljava/lang/String;J)J");
  if ((*env)->ExceptionOccurred(env)) {
    return;
  }

  /* find Disassembler.rawPrint callback */
  denv.raw_print = (*env)->GetMethodID(env, disclass, "rawPrint",
                                       "(Lsun/jvm/hotspot/asm/InstructionVisitor;Ljava/lang/String;)V");
  if ((*env)->ExceptionOccurred(env)) {
    return;
  }

  /* decode the buffer */
  (*(decode_func)(uintptr_t)decode_instructions_virtual)(startPc,
                                                         startPc + end - start,
                                                         (unsigned char*)start,
                                                         end - start,
                                                         &event_to_env,  (void*) &denv,
                                                         &printf_to_env, (void*) &denv,
                                                         options, 0 /* newline */);

  /* cleanup */
  (*env)->ReleaseByteArrayElements(env, code, start, JNI_ABORT);
  (*env)->ReleaseStringUTFChars(env, options_s, options);
}
