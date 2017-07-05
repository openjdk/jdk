/*
 * Copyright 1998-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// Useful entry points shared by JNI and JVM interface.
// We do not allow real JNI or JVM entry point to call each other.

jclass find_class_from_class_loader(JNIEnv* env, symbolHandle name, jboolean init, Handle loader, Handle protection_domain, jboolean throwError, TRAPS);

void trace_class_resolution(klassOop to_class);

/*
 * Support for Serialization and RMI. Currently used by HotSpot only.
 */

extern "C" {

void JNICALL
JVM_SetPrimitiveFieldValues(JNIEnv *env, jclass cb, jobject obj,
                            jlongArray fieldIDs, jcharArray typecodes, jbyteArray data);

void JNICALL
JVM_GetPrimitiveFieldValues(JNIEnv *env, jclass cb, jobject obj,
                            jlongArray fieldIDs, jcharArray typecodes, jbyteArray data);

}

/*
 * Support for -Xcheck:jni
 */

extern struct JNINativeInterface_* jni_functions_nocheck();
extern struct JNINativeInterface_* jni_functions_check();

/*
 * Support for swappable jni function table.
 */
extern struct JNINativeInterface_* jni_functions();
extern void copy_jni_function_table(const struct JNINativeInterface_* new_function_table);

// Support for fast JNI accessors
extern "C" {
  typedef jboolean (JNICALL *GetBooleanField_t)
      (JNIEnv *env, jobject obj, jfieldID fieldID);
  typedef jbyte (JNICALL *GetByteField_t)
      (JNIEnv *env, jobject obj, jfieldID fieldID);
  typedef jchar (JNICALL *GetCharField_t)
      (JNIEnv *env, jobject obj, jfieldID fieldID);
  typedef jshort (JNICALL *GetShortField_t)
      (JNIEnv *env, jobject obj, jfieldID fieldID);
  typedef jint (JNICALL *GetIntField_t)
      (JNIEnv *env, jobject obj, jfieldID fieldID);
  typedef jlong (JNICALL *GetLongField_t)
      (JNIEnv *env, jobject obj, jfieldID fieldID);
  typedef jfloat (JNICALL *GetFloatField_t)
      (JNIEnv *env, jobject obj, jfieldID fieldID);
  typedef jdouble (JNICALL *GetDoubleField_t)
    (JNIEnv *env, jobject obj, jfieldID fieldID);
}

void    quicken_jni_functions();
address jni_GetBooleanField_addr();
address jni_GetByteField_addr();
address jni_GetCharField_addr();
address jni_GetShortField_addr();
address jni_GetIntField_addr();
address jni_GetLongField_addr();
address jni_GetFloatField_addr();
address jni_GetDoubleField_addr();
