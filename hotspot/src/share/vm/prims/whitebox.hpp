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

#ifndef SHARE_VM_PRIMS_WHITEBOX_HPP
#define SHARE_VM_PRIMS_WHITEBOX_HPP

#include "prims/jni.h"

#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"

// Entry macro to transition from JNI to VM state.

#define WB_ENTRY(result_type, header) JNI_ENTRY(result_type, header)
#define WB_END JNI_END
#define WB_METHOD_DECLARE(result_type) extern "C" result_type JNICALL

#define CHECK_JNI_EXCEPTION_(env, value)                               \
  do {                                                                 \
    JavaThread* THREAD = JavaThread::thread_from_jni_environment(env); \
    if (HAS_PENDING_EXCEPTION) {                                       \
      CLEAR_PENDING_EXCEPTION;                                         \
      return(value);                                                   \
    }                                                                  \
  } while (0)

#define CHECK_JNI_EXCEPTION(env)                                       \
  do {                                                                 \
    JavaThread* THREAD = JavaThread::thread_from_jni_environment(env); \
    if (HAS_PENDING_EXCEPTION) {                                       \
      CLEAR_PENDING_EXCEPTION;                                         \
      return;                                                          \
    }                                                                  \
  } while (0)

class WhiteBox : public AllStatic {
 private:
  static bool _used;
 public:
  static bool used()     { return _used; }
  static void set_used() { _used = true; }
  static int offset_for_field(const char* field_name, oop object,
    Symbol* signature_symbol);
  static const char* lookup_jstring(const char* field_name, oop object);
  static bool lookup_bool(const char* field_name, oop object);
};



#endif // SHARE_VM_PRIMS_WHITEBOX_HPP
