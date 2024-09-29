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

#ifndef SHARE_OOPS_INSTANCEKLASSFLAGS_HPP
#define SHARE_OOPS_INSTANCEKLASSFLAGS_HPP

#include "runtime/atomic.hpp"

class ClassLoaderData;

// The InstanceKlassFlags class contains the parse-time and writeable flags associated with
// an InstanceKlass, and their associated accessors.
// _flags are parse-time and constant in the InstanceKlass after that.  _status are set at runtime and
// require atomic access.
// These flags are JVM internal and not part of the AccessFlags classfile specification.

class InstanceKlassFlags {
  friend class VMStructs;
  friend class JVMCIVMStructs;

#define IK_FLAGS_DO(flag)  \
    flag(rewritten                          , 1 << 0) /* methods rewritten. */ \
    flag(has_nonstatic_fields               , 1 << 1) /* for sizing with UseCompressedOops */ \
    flag(should_verify_class                , 1 << 2) /* allow caching of preverification */ \
    flag(is_contended                       , 1 << 3) /* marked with contended annotation */ \
    flag(has_nonstatic_concrete_methods     , 1 << 4) /* class/superclass/implemented interfaces has non-static, concrete methods */ \
    flag(declares_nonstatic_concrete_methods, 1 << 5) /* directly declares non-static, concrete methods */ \
    flag(shared_loading_failed              , 1 << 6) /* class has been loaded from shared archive */ \
    flag(is_shared_boot_class               , 1 << 7) /* defining class loader is boot class loader */ \
    flag(is_shared_platform_class           , 1 << 8) /* defining class loader is platform class loader */ \
    flag(is_shared_app_class                , 1 << 9) /* defining class loader is app class loader */ \
    flag(has_contended_annotations          , 1 << 10) /* has @Contended annotation */ \
    flag(has_localvariable_table            , 1 << 11) /* has localvariable information */ \
    flag(has_miranda_methods                , 1 << 12) /* True if this class has miranda methods in it's vtable */ \
    flag(has_final_method                   , 1 << 13) /* True if klass has final method */ \
    /* end of list */

#define IK_FLAGS_ENUM_NAME(name, value)    _misc_##name = value,
  enum {
    IK_FLAGS_DO(IK_FLAGS_ENUM_NAME)
  };
#undef IK_FLAGS_ENUM_NAME

#define IK_STATUS_DO(status)  \
    status(is_being_redefined                , 1 << 0) /* True if the klass is being redefined */ \
    status(has_resolved_methods              , 1 << 1) /* True if the klass has resolved MethodHandle methods */ \
    status(has_been_redefined                , 1 << 2) /* class has been redefined */ \
    status(is_scratch_class                  , 1 << 3) /* class is the redefined scratch class */ \
    status(is_marked_dependent               , 1 << 4) /* class is the redefined scratch class */ \
    /* end of list */

#define IK_STATUS_ENUM_NAME(name, value)    _misc_##name = value,
  enum {
    IK_STATUS_DO(IK_STATUS_ENUM_NAME)
  };
#undef IK_STATUS_ENUM_NAME

  u2 shared_loader_type_bits() const {
    return _misc_is_shared_boot_class|_misc_is_shared_platform_class|_misc_is_shared_app_class;
  }

  // These flags are write-once before the class is published and then read-only so don't require atomic updates.
  u2 _flags;

  // These flags are written during execution so require atomic stores
  u1 _status;

 public:

  InstanceKlassFlags() : _flags(0), _status(0) {}

  // Create getters and setters for the flag values.
#define IK_FLAGS_GET_SET(name, ignore)          \
  bool name() const { return (_flags & _misc_##name) != 0; } \
  void set_##name(bool b) {         \
    assert_is_safe(name());         \
    if (b) _flags |= _misc_##name; \
  }
  IK_FLAGS_DO(IK_FLAGS_GET_SET)
#undef IK_FLAGS_GET_SET

  bool is_shared_unregistered_class() const {
    return (_flags & shared_loader_type_bits()) == 0;
  }

  void set_shared_class_loader_type(s2 loader_type);

  void assign_class_loader_type(const ClassLoaderData* cld);
  void assert_is_safe(bool set) NOT_DEBUG_RETURN;

  // Create getters and setters for the status values.
#define IK_STATUS_GET_SET(name, ignore)          \
  bool name() const { return (_status & _misc_##name) != 0; } \
  void set_##name(bool b) {         \
    if (b) { \
      atomic_set_bits(_misc_##name); \
    } else { \
      atomic_clear_bits(_misc_##name); \
    } \
  }
  IK_STATUS_DO(IK_STATUS_GET_SET)
#undef IK_STATUS_GET_SET

  void atomic_set_bits(u1 bits)   { Atomic::fetch_then_or(&_status, bits); }
  void atomic_clear_bits(u1 bits) { Atomic::fetch_then_and(&_status, (u1)(~bits)); }
  void print_on(outputStream* st) const;
};

#endif // SHARE_OOPS_INSTANCEKLASSFLAGS_HPP
