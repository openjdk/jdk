/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

#include <assert.h>
#include <jni.h>

#include <pthread.h>

JavaVM* jvm;

void *
floobydust (void *p) {
  JNIEnv *env;

  jvm->AttachCurrentThread((void**)&env, NULL);

  jclass class_id = env->FindClass ("DoOverflow");
  assert (class_id);

  jmethodID method_id = env->GetStaticMethodID(class_id, "printIt", "()V");
  assert (method_id);

  env->CallStaticVoidMethod(class_id, method_id, NULL);

  jvm->DetachCurrentThread();
}

int
main (int argc, const char** argv) {
  JavaVMOption options[1];
  options[0].optionString = (char*) "-Xss320k";

  JavaVMInitArgs vm_args;
  vm_args.version = JNI_VERSION_1_2;
  vm_args.ignoreUnrecognized = JNI_TRUE;
  vm_args.options = options;
  vm_args.nOptions = 1;

  JNIEnv* env;
  jint result = JNI_CreateJavaVM(&jvm, (void**)&env, &vm_args);
  assert(result >= 0);

  pthread_t thr;
  pthread_create(&thr, NULL, floobydust, NULL);
  pthread_join(thr, NULL);

  floobydust(NULL);

  return 0;
}
