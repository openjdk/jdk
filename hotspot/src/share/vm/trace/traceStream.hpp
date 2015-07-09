/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/klass.hpp"
#include "oops/method.hpp"
#include "oops/symbol.hpp"
#include "utilities/ostream.hpp"

class TraceStream : public StackObj {
 private:
  outputStream& _st;

 public:
  TraceStream(outputStream& stream): _st(stream) {}

  void print_val(const char* label, u1 val) {
    _st.print("%s = " UINT32_FORMAT, label, val);
  }

  void print_val(const char* label, u2 val) {
    _st.print("%s = " UINT32_FORMAT, label, val);
  }

  void print_val(const char* label, s2 val) {
    _st.print("%s = " INT32_FORMAT, label, val);
  }

  void print_val(const char* label, u4 val) {
    _st.print("%s = " UINT32_FORMAT, label, val);
  }

  void print_val(const char* label, s4 val) {
    _st.print("%s = " INT32_FORMAT, label, val);
  }

  void print_val(const char* label, u8 val) {
    _st.print("%s = " UINT64_FORMAT, label, val);
  }

  void print_val(const char* label, s8 val) {
    _st.print("%s = " INT64_FORMAT, label, (int64_t) val);
  }

  void print_val(const char* label, bool val) {
    _st.print("%s = %s", label, val ? "true" : "false");
  }

  void print_val(const char* label, float val) {
    _st.print("%s = %f", label, val);
  }

  void print_val(const char* label, double val) {
    _st.print("%s = %f", label, val);
  }

  // Caller is machine generated code located in traceEventClasses.hpp
  // Event<TraceId>::writeEvent() (pseudocode) contains the
  // necessary ResourceMark for the resource allocations below.
  // See traceEventClasses.xsl for details.
  void print_val(const char* label, const Klass* const val) {
    const char* description = "NULL";
    if (val != NULL) {
      Symbol* name = val->name();
      if (name != NULL) {
        description = name->as_C_string();
      }
    }
    _st.print("%s = %s", label, description);
  }

  // Caller is machine generated code located in traceEventClasses.hpp
  // Event<TraceId>::writeEvent() (pseudocode) contains the
  // necessary ResourceMark for the resource allocations below.
  // See traceEventClasses.xsl for details.
  void print_val(const char* label, const Method* const val) {
    const char* description = "NULL";
    if (val != NULL) {
      description = val->name_and_sig_as_C_string();
    }
    _st.print("%s = %s", label, description);
  }

  void print_val(const char* label, const char* val) {
    _st.print("%s = '%s'", label, val);
  }

  void print(const char* val) {
    _st.print("%s", val);
  }
};

#endif // INCLUDE_TRACE
#endif // SHARE_VM_TRACE_TRACESTREAM_HPP
