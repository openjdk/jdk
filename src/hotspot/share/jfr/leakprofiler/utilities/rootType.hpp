/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_LEAKPROFILER_UTILITIES_ROOTTYPE_HPP
#define SHARE_VM_LEAKPROFILER_UTILITIES_ROOTTYPE_HPP

#include "memory/allocation.hpp"
#include "utilities/debug.hpp"

class OldObjectRoot : public AllStatic {
 public:
  enum System {
    _system_undetermined,
    _universe,
    _global_jni_handles,
    _threads,
    _object_synchronizer,
    _system_dictionary,
    _class_loader_data,
    _management,
    _jvmti,
    _code_cache,
    _string_table,
    _aot,
    _number_of_systems
  };

  enum Type {
    _type_undetermined,
    _stack_variable,
    _local_jni_handle,
    _global_jni_handle,
    _handle_area,
    _number_of_types
  };

  static const char* system_description(System system) {
    switch (system) {
      case _system_undetermined:
        return "<unknown>";
      case _universe:
        return "Universe";
      case _global_jni_handles:
        return "Global JNI Handles";
      case _threads:
        return "Threads";
      case _object_synchronizer:
        return "Object Monitor";
      case _system_dictionary:
        return "System Dictionary";
      case _class_loader_data:
        return "Class Loader Data";
      case _management:
        return "Management";
      case _jvmti:
        return "JVMTI";
      case _code_cache:
        return "Code Cache";
      case _string_table:
        return "String Table";
      case _aot:
        return "AOT";
      default:
        ShouldNotReachHere();
    }
    return NULL;
  }

  static const char* type_description(Type type) {
    switch (type) {
      case _type_undetermined:
        return "<unknown>";
      case _stack_variable:
        return "Stack Variable";
      case _local_jni_handle:
        return "Local JNI Handle";
      case _global_jni_handle:
        return "Global JNI Handle";
      case _handle_area:
        return "Handle Area";
      default:
        ShouldNotReachHere();
    }
    return NULL;
  }
};

#endif // SHARE_VM_LEAKPROFILER_UTILITIES_ROOTTYPE_HPP
