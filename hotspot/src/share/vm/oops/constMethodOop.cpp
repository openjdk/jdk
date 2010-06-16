/*
 * Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_constMethodOop.cpp.incl"

// Static initialization
const u2 constMethodOopDesc::MAX_IDNUM   = 0xFFFE;
const u2 constMethodOopDesc::UNSET_IDNUM = 0xFFFF;

// How big must this constMethodObject be?

int constMethodOopDesc::object_size(int code_size,
                                    int compressed_line_number_size,
                                    int local_variable_table_length,
                                    int checked_exceptions_length) {
  int extra_bytes = code_size;
  if (compressed_line_number_size > 0) {
    extra_bytes += compressed_line_number_size;
  }
  if (checked_exceptions_length > 0) {
    extra_bytes += sizeof(u2);
    extra_bytes += checked_exceptions_length * sizeof(CheckedExceptionElement);
  }
  if (local_variable_table_length > 0) {
    extra_bytes += sizeof(u2);
    extra_bytes +=
              local_variable_table_length * sizeof(LocalVariableTableElement);
  }
  int extra_words = align_size_up(extra_bytes, BytesPerWord) / BytesPerWord;
  return align_object_size(header_size() + extra_words);
}


// linenumber table - note that length is unknown until decompression,
// see class CompressedLineNumberReadStream.

u_char* constMethodOopDesc::compressed_linenumber_table() const {
  // Located immediately following the bytecodes.
  assert(has_linenumber_table(), "called only if table is present");
  return code_end();
}

u2* constMethodOopDesc::checked_exceptions_length_addr() const {
  // Located at the end of the constMethod.
  assert(has_checked_exceptions(), "called only if table is present");
  return last_u2_element();
}

u2* constMethodOopDesc::localvariable_table_length_addr() const {
  assert(has_localvariable_table(), "called only if table is present");
  if (has_checked_exceptions()) {
    // If checked_exception present, locate immediately before them.
    return (u2*) checked_exceptions_start() - 1;
  } else {
    // Else, the linenumber table is at the end of the constMethod.
    return last_u2_element();
  }
}


// Update the flags to indicate the presence of these optional fields.
void constMethodOopDesc::set_inlined_tables_length(
                                              int checked_exceptions_len,
                                              int compressed_line_number_size,
                                              int localvariable_table_len) {
  // Must be done in the order below, otherwise length_addr accessors
  // will not work. Only set bit in header if length is positive.
  assert(_flags == 0, "Error");
  if (compressed_line_number_size > 0) {
    _flags |= _has_linenumber_table;
  }
  if (checked_exceptions_len > 0) {
    _flags |= _has_checked_exceptions;
    *(checked_exceptions_length_addr()) = checked_exceptions_len;
  }
  if (localvariable_table_len > 0) {
    _flags |= _has_localvariable_table;
    *(localvariable_table_length_addr()) = localvariable_table_len;
  }
}


int constMethodOopDesc::checked_exceptions_length() const {
  return has_checked_exceptions() ? *(checked_exceptions_length_addr()) : 0;
}


CheckedExceptionElement* constMethodOopDesc::checked_exceptions_start() const {
  u2* addr = checked_exceptions_length_addr();
  u2 length = *addr;
  assert(length > 0, "should only be called if table is present");
  addr -= length * sizeof(CheckedExceptionElement) / sizeof(u2);
  return (CheckedExceptionElement*) addr;
}


int constMethodOopDesc::localvariable_table_length() const {
  return has_localvariable_table() ? *(localvariable_table_length_addr()) : 0;
}


LocalVariableTableElement* constMethodOopDesc::localvariable_table_start() const {
  u2* addr = localvariable_table_length_addr();
  u2 length = *addr;
  assert(length > 0, "should only be called if table is present");
  addr -= length * sizeof(LocalVariableTableElement) / sizeof(u2);
  return (LocalVariableTableElement*) addr;
}
