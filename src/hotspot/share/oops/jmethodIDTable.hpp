/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_JMETHODIDTABLE_HPP
#define SHARE_OOPS_JMETHODIDTABLE_HPP

#include "jni.h"
#include "memory/allocation.hpp"

// Class for associating Method with jmethodID
class Method;

class JmethodIDTable : public AllStatic {
 public:
  static void initialize();

  // Given a Method return a jmethodID.
  static jmethodID make_jmethod_id(Method* m);

  // Given a jmethodID, return a Method.
  static Method* resolve_jmethod_id(jmethodID mid);

  // Class unloading support, remove the associations from the tables.  Stale jmethodID will
  // not be found and return null.
  static void remove(jmethodID mid);

  // RedefineClasses support
  static void change_method_associated_with_jmethod_id(jmethodID jmid, Method* new_method);
  static void clear_jmethod_id(jmethodID jmid, Method* m);

  static uint64_t get_entry_count();
};

#endif // SHARE_OOPS_JMETHODIDTABLE_HPP
