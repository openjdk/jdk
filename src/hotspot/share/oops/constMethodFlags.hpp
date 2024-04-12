/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_CONSTMETHODFLAGS_HPP
#define SHARE_OOPS_CONSTMETHODFLAGS_HPP

#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class outputStream;

// The ConstMethodFlags class contains the parse-time flags associated with
// a Method, and its associated accessors.
// These flags are JVM internal and not part of the AccessFlags classfile specification.

class ConstMethodFlags {
  friend class VMStructs;
  friend class JVMCIVMStructs;

#define CM_FLAGS_DO(flag)  \
   flag(has_linenumber_table      , 1 << 0) \
   flag(has_checked_exceptions    , 1 << 1) \
   flag(has_localvariable_table   , 1 << 2) \
   flag(has_exception_table       , 1 << 3) \
   flag(has_generic_signature     , 1 << 4) \
   flag(has_method_parameters     , 1 << 5) \
   flag(is_overpass               , 1 << 6) \
   flag(has_method_annotations    , 1 << 7) \
   flag(has_parameter_annotations , 1 << 8) \
   flag(has_type_annotations      , 1 << 9) \
   flag(has_default_annotations   , 1 << 10) \
   flag(caller_sensitive          , 1 << 11) \
   flag(is_hidden                 , 1 << 12) \
   flag(has_injected_profile      , 1 << 13) \
   flag(intrinsic_candidate       , 1 << 14) \
   flag(reserved_stack_access     , 1 << 15) \
   flag(is_scoped                 , 1 << 16) \
   flag(changes_current_thread    , 1 << 17) \
   flag(jvmti_mount_transition    , 1 << 18) \
   flag(deprecated                , 1 << 19) \
   flag(deprecated_for_removal    , 1 << 20) \
   /* end of list */

#define CM_FLAGS_ENUM_NAME(name, value)    _misc_##name = value,
  enum {
    CM_FLAGS_DO(CM_FLAGS_ENUM_NAME)
  };
#undef CM_FLAGS_ENUM_NAME

  // These flags are write-once before the class is published and then read-only so don't require atomic updates.
  u4 _flags;

 public:

  ConstMethodFlags() : _flags(0) {}

  // Create getters and setters for the flag values.
#define CM_FLAGS_GET_SET(name, ignore)          \
  bool name() const { return (_flags & _misc_##name) != 0; } \
  void set_##name() {         \
    _flags |= _misc_##name;  \
  }
  CM_FLAGS_DO(CM_FLAGS_GET_SET)
#undef CM_FLAGS_GET_SET

  int as_int() const { return _flags; }
  void print_on(outputStream* st) const;
};

#endif // SHARE_OOPS_CONSTMETHODFLAGS_HPP
