/*
* Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JFR_SUPPORT_JFRTRACEIDEXTENSION_HPP
#define SHARE_VM_JFR_SUPPORT_JFRTRACEIDEXTENSION_HPP

#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.hpp"

#define DEFINE_TRACE_ID_FIELD mutable traceid _trace_id

#define DEFINE_TRACE_ID_METHODS \
  traceid trace_id() const { return _trace_id; } \
  traceid* const trace_id_addr() const { return &_trace_id; } \
  void set_trace_id(traceid id) const { _trace_id = id; }

#define DEFINE_TRACE_ID_SIZE \
  static size_t trace_id_size() { return sizeof(traceid); }

#define INIT_ID(data) JfrTraceId::assign(data)
#define REMOVE_ID(k) JfrTraceId::remove(k);
#define RESTORE_ID(k) JfrTraceId::restore(k);

class JfrTraceFlag {
 private:
  mutable jbyte _flags;
 public:
  JfrTraceFlag() : _flags(0) {}
  explicit JfrTraceFlag(jbyte flags) : _flags(flags) {}
  void set_flag(jbyte flag) const {
    _flags |= flag;
  }
  void clear_flag(jbyte flag) const {
    _flags &= (~flag);
  }
  jbyte flags() const { return _flags; }
  bool is_set(jbyte flag) const {
    return (_flags & flag) != 0;
  }
  jbyte* const flags_addr() const {
    return &_flags;
  }
};

#define DEFINE_TRACE_FLAG mutable JfrTraceFlag _trace_flags

#define DEFINE_TRACE_FLAG_ACCESSOR                 \
  void set_trace_flag(jbyte flag) const {          \
    _trace_flags.set_flag(flag);                   \
  }                                                \
  jbyte trace_flags() const {                      \
    return _trace_flags.flags();                   \
  }                                                \
  bool is_trace_flag_set(jbyte flag) const {       \
    return _trace_flags.is_set(flag);              \
  }                                                \
  jbyte* const trace_flags_addr() const {          \
    return _trace_flags.flags_addr();              \
  }

#endif // SHARE_VM_JFR_SUPPORT_JFRTRACEIDEXTENSION_HPP
