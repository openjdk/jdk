/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/gcLocker.hpp"
#include "memory/metadataFactory.hpp"
#include "oops/constMethod.hpp"
#include "oops/method.hpp"

// Static initialization
const u2 ConstMethod::MAX_IDNUM   = 0xFFFE;
const u2 ConstMethod::UNSET_IDNUM = 0xFFFF;

ConstMethod* ConstMethod::allocate(ClassLoaderData* loader_data,
                                   int byte_code_size,
                                   int compressed_line_number_size,
                                   int localvariable_table_length,
                                   int exception_table_length,
                                   int checked_exceptions_length,
                                   MethodType method_type,
                                   TRAPS) {
  int size = ConstMethod::size(byte_code_size,
                                      compressed_line_number_size,
                                      localvariable_table_length,
                                      exception_table_length,
                                      checked_exceptions_length);
  return new (loader_data, size, true, THREAD) ConstMethod(
      byte_code_size, compressed_line_number_size, localvariable_table_length,
      exception_table_length, checked_exceptions_length, method_type, size);
}

ConstMethod::ConstMethod(int byte_code_size,
                         int compressed_line_number_size,
                         int localvariable_table_length,
                         int exception_table_length,
                         int checked_exceptions_length,
                         MethodType method_type,
                         int size) {

  No_Safepoint_Verifier no_safepoint;
  set_interpreter_kind(Interpreter::invalid);
  init_fingerprint();
  set_constants(NULL);
  set_stackmap_data(NULL);
  set_code_size(byte_code_size);
  set_constMethod_size(size);
  set_inlined_tables_length(checked_exceptions_length,
                            compressed_line_number_size,
                            localvariable_table_length,
                            exception_table_length);
  set_method_type(method_type);
  assert(this->size() == size, "wrong size for object");
}


// Deallocate metadata fields associated with ConstMethod*
void ConstMethod::deallocate_contents(ClassLoaderData* loader_data) {
  set_interpreter_kind(Interpreter::invalid);
  if (stackmap_data() != NULL) {
    MetadataFactory::free_array<u1>(loader_data, stackmap_data());
  }
  set_stackmap_data(NULL);
}

// How big must this constMethodObject be?

int ConstMethod::size(int code_size,
                                    int compressed_line_number_size,
                                    int local_variable_table_length,
                                    int exception_table_length,
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
  if (exception_table_length > 0) {
    extra_bytes += sizeof(u2);
    extra_bytes += exception_table_length * sizeof(ExceptionTableElement);
  }
  int extra_words = align_size_up(extra_bytes, BytesPerWord) / BytesPerWord;
  return align_object_size(header_size() + extra_words);
}

Method* ConstMethod::method() const {
    return InstanceKlass::cast(_constants->pool_holder())->method_with_idnum(
                               _method_idnum);
  }

// linenumber table - note that length is unknown until decompression,
// see class CompressedLineNumberReadStream.

u_char* ConstMethod::compressed_linenumber_table() const {
  // Located immediately following the bytecodes.
  assert(has_linenumber_table(), "called only if table is present");
  return code_end();
}

u2* ConstMethod::checked_exceptions_length_addr() const {
  // Located at the end of the constMethod.
  assert(has_checked_exceptions(), "called only if table is present");
  return last_u2_element();
}

u2* ConstMethod::exception_table_length_addr() const {
  assert(has_exception_handler(), "called only if table is present");
  if (has_checked_exceptions()) {
    // If checked_exception present, locate immediately before them.
    return (u2*) checked_exceptions_start() - 1;
  } else {
    // Else, the exception table is at the end of the constMethod.
    return last_u2_element();
  }
}

u2* ConstMethod::localvariable_table_length_addr() const {
  assert(has_localvariable_table(), "called only if table is present");
  if (has_exception_handler()) {
    // If exception_table present, locate immediately before them.
    return (u2*) exception_table_start() - 1;
  } else {
    if (has_checked_exceptions()) {
      // If checked_exception present, locate immediately before them.
      return (u2*) checked_exceptions_start() - 1;
    } else {
      // Else, the linenumber table is at the end of the constMethod.
      return last_u2_element();
    }
  }
}


// Update the flags to indicate the presence of these optional fields.
void ConstMethod::set_inlined_tables_length(
                                              int checked_exceptions_len,
                                              int compressed_line_number_size,
                                              int localvariable_table_len,
                                              int exception_table_len) {
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
  if (exception_table_len > 0) {
    _flags |= _has_exception_table;
    *(exception_table_length_addr()) = exception_table_len;
  }
  if (localvariable_table_len > 0) {
    _flags |= _has_localvariable_table;
    *(localvariable_table_length_addr()) = localvariable_table_len;
  }
}


int ConstMethod::checked_exceptions_length() const {
  return has_checked_exceptions() ? *(checked_exceptions_length_addr()) : 0;
}


CheckedExceptionElement* ConstMethod::checked_exceptions_start() const {
  u2* addr = checked_exceptions_length_addr();
  u2 length = *addr;
  assert(length > 0, "should only be called if table is present");
  addr -= length * sizeof(CheckedExceptionElement) / sizeof(u2);
  return (CheckedExceptionElement*) addr;
}


int ConstMethod::localvariable_table_length() const {
  return has_localvariable_table() ? *(localvariable_table_length_addr()) : 0;
}


LocalVariableTableElement* ConstMethod::localvariable_table_start() const {
  u2* addr = localvariable_table_length_addr();
  u2 length = *addr;
  assert(length > 0, "should only be called if table is present");
  addr -= length * sizeof(LocalVariableTableElement) / sizeof(u2);
  return (LocalVariableTableElement*) addr;
}

int ConstMethod::exception_table_length() const {
  return has_exception_handler() ? *(exception_table_length_addr()) : 0;
}

ExceptionTableElement* ConstMethod::exception_table_start() const {
  u2* addr = exception_table_length_addr();
  u2 length = *addr;
  assert(length > 0, "should only be called if table is present");
  addr -= length * sizeof(ExceptionTableElement) / sizeof(u2);
  return (ExceptionTableElement*)addr;
}


// Printing

void ConstMethod::print_on(outputStream* st) const {
  ResourceMark rm;
  assert(is_constMethod(), "must be constMethod");
  st->print_cr(internal_name());
  st->print(" - method:       " INTPTR_FORMAT " ", (address)method());
  method()->print_value_on(st); st->cr();
  if (has_stackmap_table()) {
    st->print(" - stackmap data:       ");
    stackmap_data()->print_value_on(st);
    st->cr();
  }
}

// Short version of printing ConstMethod* - just print the name of the
// method it belongs to.
void ConstMethod::print_value_on(outputStream* st) const {
  assert(is_constMethod(), "must be constMethod");
  st->print(" const part of method " );
  method()->print_value_on(st);
}


// Verification

void ConstMethod::verify_on(outputStream* st) {
  guarantee(is_constMethod(), "object must be constMethod");
  guarantee(is_metadata(), err_msg("Should be metadata " PTR_FORMAT, this));

  // Verification can occur during oop construction before the method or
  // other fields have been initialized.
  guarantee(is_metadata(), err_msg("Should be metadata " PTR_FORMAT, this));
  guarantee(method()->is_method(), "should be method");

  address m_end = (address)((oop*) this + size());
  address compressed_table_start = code_end();
  guarantee(compressed_table_start <= m_end, "invalid method layout");
  address compressed_table_end = compressed_table_start;
  // Verify line number table
  if (has_linenumber_table()) {
    CompressedLineNumberReadStream stream(compressed_linenumber_table());
    while (stream.read_pair()) {
      guarantee(stream.bci() >= 0 && stream.bci() <= code_size(), "invalid bci in line number table");
    }
    compressed_table_end += stream.position();
  }
  guarantee(compressed_table_end <= m_end, "invalid method layout");
  // Verify checked exceptions, exception table and local variable tables
  if (has_checked_exceptions()) {
    u2* addr = checked_exceptions_length_addr();
    guarantee(*addr > 0 && (address) addr >= compressed_table_end && (address) addr < m_end, "invalid method layout");
  }
  if (has_exception_handler()) {
    u2* addr = exception_table_length_addr();
     guarantee(*addr > 0 && (address) addr >= compressed_table_end && (address) addr < m_end, "invalid method layout");
  }
  if (has_localvariable_table()) {
    u2* addr = localvariable_table_length_addr();
    guarantee(*addr > 0 && (address) addr >= compressed_table_end && (address) addr < m_end, "invalid method layout");
  }
  // Check compressed_table_end relative to uncompressed_table_start
  u2* uncompressed_table_start;
  if (has_localvariable_table()) {
    uncompressed_table_start = (u2*) localvariable_table_start();
  } else if (has_exception_handler()) {
    uncompressed_table_start = (u2*) exception_table_start();
  } else if (has_checked_exceptions()) {
      uncompressed_table_start = (u2*) checked_exceptions_start();
  } else {
      uncompressed_table_start = (u2*) m_end;
  }
  int gap = (intptr_t) uncompressed_table_start - (intptr_t) compressed_table_end;
  int max_gap = align_object_size(1)*BytesPerWord;
  guarantee(gap >= 0 && gap < max_gap, "invalid method layout");
}
