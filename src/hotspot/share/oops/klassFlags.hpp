/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_KLASSFLAGS_HPP
#define SHARE_OOPS_KLASSFLAGS_HPP

#include "utilities/globalDefinitions.hpp"

class outputStream;

// The Klass class contains only parse-time flags and are used by generated code, even though
// most apply to InstanceKlass, access is more straightforward through Klass pointers.
// These flags are JVM internal and not part of the AccessFlags classfile specification.

using klass_flags_t = u1;

class KlassFlags {
  friend class VMStructs;
  friend class JVMCIVMStructs;

 public:
#define KLASS_FLAGS_DO(flag)  \
    flag(is_hidden_class              , 1 << 0) \
    flag(is_value_based_class         , 1 << 1) \
    flag(has_finalizer                , 1 << 2) \
    flag(is_cloneable_fast            , 1 << 3) \
    /* end of list */

#define KLASS_FLAGS_ENUM_NAME(name, value)    _misc_##name = value,
  enum {
    KLASS_FLAGS_DO(KLASS_FLAGS_ENUM_NAME)
  };
#undef KLASS_FLAGS_ENUM_NAME

  // These flags are write-once before the class is published and then read-only
  // so don't require atomic updates.
  klass_flags_t _flags;

 public:
  KlassFlags() : _flags(0) {}

  klass_flags_t value() const { return _flags; }

  // Create getters and setters for the flag values.
#define KLASS_FLAGS_GET_SET(name, ignore)          \
  bool name() const { return (_flags & _misc_##name) != 0; } \
  void set_##name(bool b) {         \
    assert(!name(), "set once");    \
    if (b) _flags |= _misc_##name; \
  }
  KLASS_FLAGS_DO(KLASS_FLAGS_GET_SET)
#undef KLASS_FLAGS_GET_SET

  void print_on(outputStream* st) const;
};

#endif // SHARE_OOPS_KLASSFLAGS_HPP
