/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef AGENT_TEST_SUPPORT_HPP
#define AGENT_TEST_SUPPORT_HPP

#include "jvmti.h"

static void check_java(JNIEnv* jni) {
  if (jni->ExceptionCheck()) {
    jni->ExceptionDescribe();
    jni->FatalError("Java test hook failed");
  }
}

static jclass application_class(JNIEnv* jni, const char* name) {
  jclass loaders = jni->FindClass("java/lang/ClassLoader");
  check_java(jni);
  jmethodID system = jni->GetStaticMethodID(loaders, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
  check_java(jni);
  jobject loader = jni->CallStaticObjectMethod(loaders, system);
  check_java(jni);
  if (loader == nullptr) {
    jni->FatalError("No system class loader");
  }
  jmethodID load = jni->GetMethodID(loaders, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
  check_java(jni);
  jstring class_name = jni->NewStringUTF(name);
  check_java(jni);
  jclass result = static_cast<jclass>(jni->CallObjectMethod(loader, load, class_name));
  check_java(jni);
  if (result == nullptr) {
    jni->FatalError("Test class not found");
  }
  jni->DeleteLocalRef(class_name);
  jni->DeleteLocalRef(loader);
  jni->DeleteLocalRef(loaders);
  return result;
}

static void java_hook(JNIEnv* jni, const char* class_name, const char* method_name) {
  jclass klass = application_class(jni, class_name);
  jmethodID method = jni->GetStaticMethodID(klass, method_name, "()V");
  check_java(jni);
  jni->CallStaticVoidMethod(klass, method);
  check_java(jni);
  jni->DeleteLocalRef(klass);
}

static void dispose_environment(JNIEnv* jni, jvmtiEnv* env) {
  if (env == nullptr || env->DisposeEnvironment() != JVMTI_ERROR_NONE) {
    jni->FatalError("Could not dispose test environment");
  }
}

#endif // AGENT_TEST_SUPPORT_HPP
