/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

//
// Checked JNI routines that are useful for outside of checked JNI
//

class jniCheck : public AllStatic {
 public:
  static oop validate_handle(JavaThread* thr, jobject obj);
  static oop validate_object(JavaThread* thr, jobject obj);
  static klassOop validate_class(JavaThread* thr, jclass clazz, bool allow_primitive = false);
  static void validate_class_descriptor(JavaThread* thr, const char* name);
  static void validate_throwable_klass(JavaThread* thr, klassOop klass);
  static void validate_call_object(JavaThread* thr, jobject obj, jmethodID method_id);
  static void validate_call_class(JavaThread* thr, jclass clazz, jmethodID method_id);
  static methodOop validate_jmethod_id(JavaThread* thr, jmethodID method_id);
};
