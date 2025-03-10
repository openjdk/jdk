/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_METHODFLAGS_HPP
#define SHARE_OOPS_METHODFLAGS_HPP

#include "runtime/atomic.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class outputStream;

// The MethodFlags class contains the writeable flags aka. status associated with
// an Method, and their associated accessors.
// _status are set at runtime and require atomic access.
// These flags are JVM internal and not part of the AccessFlags classfile specification.

class MethodFlags {
  friend class VMStructs;
  friend class JVMCIVMStructs;
   /* end of list */

#define M_STATUS_DO(status)  \
   status(has_monitor_bytecodes       , 1 << 0)  /* Method contains monitorenter/monitorexit bytecodes */ \
   status(has_jsrs                    , 1 << 1) \
   status(is_old                      , 1 << 2) /* RedefineClasses() has replaced this method */ \
   status(is_obsolete                 , 1 << 3) /* RedefineClasses() has made method obsolete */ \
   status(is_deleted                  , 1 << 4) /* RedefineClasses() has deleted this method */  \
   status(is_prefixed_native          , 1 << 5) /* JVMTI has prefixed this native method */ \
   status(monitor_matching            , 1 << 6) /* True if we know that monitorenter/monitorexit bytecodes match */ \
   status(queued_for_compilation      , 1 << 7) \
   status(is_not_c2_compilable        , 1 << 8) \
   status(is_not_c1_compilable        , 1 << 9) \
   status(is_not_c2_osr_compilable    , 1 << 10) \
   status(force_inline                , 1 << 11) /* Annotations but also set/reset at runtime */ \
   status(dont_inline                 , 1 << 12) \
   status(has_loops_flag              , 1 << 13) /* Method has loops */ \
   status(has_loops_flag_init         , 1 << 14) /* The loop flag has been initialized */ \
   status(on_stack_flag               , 1 << 15) /* RedefineClasses support to keep Metadata from being cleaned */ \
   /* end of list */

#define M_STATUS_ENUM_NAME(name, value)    _misc_##name = value,
  enum {
    M_STATUS_DO(M_STATUS_ENUM_NAME)
  };
#undef M_STATUS_ENUM_NAME

  // These flags are written during execution so require atomic stores
  u4 _status;

 public:

  MethodFlags() : _status(0) {}

  // Create getters and setters for the status values.
#define M_STATUS_GET_SET(name, ignore)          \
  bool name() const { return (_status & _misc_##name) != 0; } \
  void set_##name(bool b) {         \
    if (b) { \
      atomic_set_bits(_misc_##name); \
    } else { \
      atomic_clear_bits(_misc_##name); \
    } \
  }
  M_STATUS_DO(M_STATUS_GET_SET)
#undef M_STATUS_GET_SET

  int as_int() const { return _status; }
  void atomic_set_bits(u4 bits)   { Atomic::fetch_then_or(&_status, bits); }
  void atomic_clear_bits(u4 bits) { Atomic::fetch_then_and(&_status, ~bits); }
  void print_on(outputStream* st) const;
};

#endif // SHARE_OOPS_METHODFLAGS_HPP
