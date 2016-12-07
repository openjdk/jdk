/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_TRACE_TRACESTREAM_HPP
#define SHARE_VM_TRACE_TRACESTREAM_HPP

#include "utilities/macros.hpp"
#if INCLUDE_TRACE
#include "memory/allocation.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"

class ClassLoaderData;
class Klass;
class Method;

class TraceStream : public StackObj {
 public:
  TraceStream() {
    assert(tty != NULL, "invariant");
  }

  void print(const char* val) const {
    tty->print("%s", val);
  }

  void print_val(const char* label, u1 val) const {
    tty->print("%s = " UINT32_FORMAT, label, val);
  }

  void print_val(const char* label, u2 val) const {
    tty->print("%s = " UINT32_FORMAT, label, val);
  }

  void print_val(const char* label, s2 val) const {
    tty->print("%s = " INT32_FORMAT, label, val);
  }

  void print_val(const char* label, u4 val) const {
    tty->print("%s = " UINT32_FORMAT, label, val);
  }

  void print_val(const char* label, s4 val) const {
    tty->print("%s = " INT32_FORMAT, label, val);
  }

  void print_val(const char* label, u8 val) const {
    tty->print("%s = " UINT64_FORMAT, label, val);
  }

  void print_val(const char* label, s8 val) const {
    tty->print("%s = " INT64_FORMAT, label, (int64_t) val);
  }

  void print_val(const char* label, bool val) const {
    tty->print("%s = %s", label, val ? "true" : "false");
  }

  void print_val(const char* label, float val) const {
    tty->print("%s = %f", label, val);
  }

  void print_val(const char* label, double val) const {
    tty->print("%s = %f", label, val);
  }

  void print_val(const char* label, const char* val) const {
    tty->print("%s = '%s'", label, val);
  }

  void print_val(const char* label, const Klass* val) const;
  void print_val(const char* label, const Method* val) const ;
  void print_val(const char* label, const ClassLoaderData* cld) const;
};

#endif // INCLUDE_TRACE
#endif // SHARE_VM_TRACE_TRACESTREAM_HPP
