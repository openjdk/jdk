/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#include <jni.h>

/*
 * Class:     CallWithJNIWeak
 * Method:    doStuff
 * Signature: (Ljava/lang/Object;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL
Java_CallWithJNIWeak_doStuff(JNIEnv *env, jclass c, jobject o) {
  jmethodID id = (*env)->GetStaticMethodID(
    env, c, "doWithWeak", "(Ljava/lang/Object;)Ljava/lang/Object;");
  jweak w = (*env)->NewWeakGlobalRef(env, o);
  jobject param = w;
  (*env)->CallStaticVoidMethod(env, c, id, param);
  return param;
}

/*
 * Class:     CallWithJNIWeak
 * Method:    doWithWeak
 * Signature: (Ljava/lang/Object;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL
Java_CallWithJNIWeak_doWithWeak(JNIEnv *env, jclass c, jobject o) {
  jclass thr_class = (*env)->GetObjectClass(env, o); // o is java.lang.Thread
  jmethodID id = (*env)->GetMethodID(env, thr_class, "isInterrupted", "()Z");
  jboolean b = (*env)->CallBooleanMethod(env, o, id);
  return o;
}

