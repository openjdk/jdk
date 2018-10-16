/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Google and/or its affiliates. All rights reserved.
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
 */
#ifndef NSK_EXCEPTIONCHECKINGJNIENV_DEFINED
#define NSK_EXCEPTIONCHECKINGJNIENV_DEFINED

#include <jni.h>

/**
 * ExceptionCheckingJniEnv wraps around the JNIEnv data structure and
 * methods to enable automatic exception checking. This allows test writers
 * and readers to concentrate on what the test is to do and leave the
 * error checking and throwing to this data structure and subsystem.
 *
 * For example:
 *
 * ... JNIEnv* env ...
 *  jclass klass = env->GetObjectClass(o);
 *  if (klass == NULL) {
 *      printf("Error: GetObjectClass returned NULL\n");
 *      return;
 *  }
 *  if (env->ExceptionCheck()) {
 *    ...
 *  }
 *
 *  Can be simplified to:
 * ... ExceptionCheckingJniEnv* env ...
 *  jclass klass = env->GetObjectClass(o);
 *
 *  Where now the JNI Exception checking and the NULL return checking are done
 *  internally and will perform whatever action the ErrorHandler requires.
 *
 *  By default, the error handler describes the exception via the JNI
 *  ExceptionDescribe method and calls FatalError.
 *
 *  Note: at a future date, this will also include the tracing mechanism done in
 *  NSK_VERIFY, which will thus embed its logic into the ExceptionCheckingJniEnv
 *  and clearing that up for the code readers and writers.
 */
class ExceptionCheckingJniEnv {
 public:
  // JNIEnv API redefinitions.
  jfieldID GetFieldID(jclass klass, const char *name, const char* type);
  jclass GetObjectClass(jobject obj);
  jobject GetObjectField(jobject obj, jfieldID field);
  void SetObjectField(jobject obj, jfieldID field, jobject value);

  jsize GetArrayLength(jarray array);
  jsize GetStringLength(jstring str);

  void* GetPrimitiveArrayCritical(jarray array, jboolean* isCopy);
  void ReleasePrimitiveArrayCritical(jarray array, void* carray, jint mode);
  const jchar* GetStringCritical(jstring str, jboolean* isCopy);
  void ReleaseStringCritical(jstring str, const jchar* carray);

  jobject NewGlobalRef(jobject obj);
  void DeleteGlobalRef(jobject obj);
  jobject NewLocalRef(jobject ref);
  void DeleteLocalRef(jobject ref);
  jweak NewWeakGlobalRef(jobject obj);
  void DeleteWeakGlobalRef(jweak obj);

  // ExceptionCheckingJniEnv methods.
  JNIEnv* GetJNIEnv() {
    return _jni_env;
  }

  void HandleError(const char* msg) {
    if (_error_handler) {
      _error_handler(_jni_env, msg);
    }
  }

  typedef void (*ErrorHandler)(JNIEnv* env, const char* error_message);

  static void FatalError(JNIEnv* env, const char* message) {
    if (env->ExceptionCheck()) {
      env->ExceptionDescribe();
    }
    env->FatalError(message);
  }

  ExceptionCheckingJniEnv(JNIEnv* jni_env, ErrorHandler error_handler) :
    _jni_env(jni_env), _error_handler(error_handler) {}

 private:
  JNIEnv* _jni_env;
  ErrorHandler _error_handler;
};

// We cannot use unique_ptr due to this being gnu98++, so use this instead:
class ExceptionCheckingJniEnvPtr {
 private:
  ExceptionCheckingJniEnv _env;

 public:
  ExceptionCheckingJniEnv* operator->() {
    return &_env;
  }

  ExceptionCheckingJniEnvPtr(
      JNIEnv* jni_env,
      ExceptionCheckingJniEnv::ErrorHandler error_handler = ExceptionCheckingJniEnv::FatalError) :
          _env(jni_env, error_handler) {
  }
};

#endif
