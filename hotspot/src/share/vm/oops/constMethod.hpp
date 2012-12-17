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

#ifndef SHARE_VM_OOPS_CONSTMETHODOOP_HPP
#define SHARE_VM_OOPS_CONSTMETHODOOP_HPP

#include "oops/oop.hpp"

// An ConstMethod* represents portions of a Java method which
// do not vary.
//
// Memory layout (each line represents a word). Note that most
// applications load thousands of methods, so keeping the size of this
// structure small has a big impact on footprint.
//
// |------------------------------------------------------|
// | header                                               |
// | klass                                                |
// |------------------------------------------------------|
// | fingerprint 1                                        |
// | fingerprint 2                                        |
// | constants                      (oop)                 |
// | stackmap_data                  (oop)                 |
// | constMethod_size                                     |
// | interp_kind  | flags    | code_size                  |
// | name index              | signature index            |
// | method_idnum            | max_stack                  |
// |------------------------------------------------------|
// |                                                      |
// | byte codes                                           |
// |                                                      |
// |------------------------------------------------------|
// | compressed linenumber table                          |
// |  (see class CompressedLineNumberReadStream)          |
// |  (note that length is unknown until decompressed)    |
// |  (access flags bit tells whether table is present)   |
// |  (indexed from start of ConstMethod*)                |
// |  (elements not necessarily sorted!)                  |
// |------------------------------------------------------|
// | localvariable table elements + length (length last)  |
// |  (length is u2, elements are 6-tuples of u2)         |
// |  (see class LocalVariableTableElement)               |
// |  (access flags bit tells whether table is present)   |
// |  (indexed from end of ConstMethod*)                  |
// |------------------------------------------------------|
// | exception table + length (length last)               |
// |  (length is u2, elements are 4-tuples of u2)         |
// |  (see class ExceptionTableElement)                   |
// |  (access flags bit tells whether table is present)   |
// |  (indexed from end of ConstMethod*)                  |
// |------------------------------------------------------|
// | checked exceptions elements + length (length last)   |
// |  (length is u2, elements are u2)                     |
// |  (see class CheckedExceptionElement)                 |
// |  (access flags bit tells whether table is present)   |
// |  (indexed from end of ConstMethod*)                  |
// |------------------------------------------------------|
// | generic signature index (u2)                         |
// |  (indexed from start of constMethodOop)              |
// |------------------------------------------------------|


// Utitily class decribing elements in checked exceptions table inlined in Method*.
class CheckedExceptionElement VALUE_OBJ_CLASS_SPEC {
 public:
  u2 class_cp_index;
};


// Utitily class decribing elements in local variable table inlined in Method*.
class LocalVariableTableElement VALUE_OBJ_CLASS_SPEC {
 public:
  u2 start_bci;
  u2 length;
  u2 name_cp_index;
  u2 descriptor_cp_index;
  u2 signature_cp_index;
  u2 slot;
};

// Utitily class describing elements in exception table
class ExceptionTableElement VALUE_OBJ_CLASS_SPEC {
 public:
  u2 start_pc;
  u2 end_pc;
  u2 handler_pc;
  u2 catch_type_index;
};


class ConstMethod : public MetaspaceObj {
  friend class VMStructs;

public:
  typedef enum { NORMAL, OVERPASS } MethodType;

private:
  enum {
    _has_linenumber_table = 1,
    _has_checked_exceptions = 2,
    _has_localvariable_table = 4,
    _has_exception_table = 8,
    _has_generic_signature = 16,
    _is_overpass = 32
  };

  // Bit vector of signature
  // Callers interpret 0=not initialized yet and
  // -1=too many args to fix, must parse the slow way.
  // The real initial value is special to account for nonatomicity of 64 bit
  // loads and stores.  This value may updated and read without a lock by
  // multiple threads, so is volatile.
  volatile uint64_t _fingerprint;

  ConstantPool*     _constants;                  // Constant pool

  // Raw stackmap data for the method
  Array<u1>*        _stackmap_data;

  int               _constMethod_size;
  jbyte             _interpreter_kind;
  jbyte             _flags;

  // Size of Java bytecodes allocated immediately after Method*.
  u2                _code_size;
  u2                _name_index;                 // Method name (index in constant pool)
  u2                _signature_index;            // Method signature (index in constant pool)
  u2                _method_idnum;               // unique identification number for the method within the class
                                                 // initially corresponds to the index into the methods array.
                                                 // but this may change with redefinition
  u2                _max_stack;                  // Maximum number of entries on the expression stack


  // Constructor
  ConstMethod(int byte_code_size,
              int compressed_line_number_size,
              int localvariable_table_length,
              int exception_table_length,
              int checked_exceptions_length,
              u2  generic_signature_index,
              MethodType is_overpass,
              int size);
public:

  static ConstMethod* allocate(ClassLoaderData* loader_data,
                               int byte_code_size,
                               int compressed_line_number_size,
                               int localvariable_table_length,
                               int exception_table_length,
                               int checked_exceptions_length,
                               u2  generic_signature_index,
                               MethodType mt,
                               TRAPS);

  bool is_constMethod() const { return true; }

  // Inlined tables
  void set_inlined_tables_length(u2  generic_signature_index,
                                 int checked_exceptions_len,
                                 int compressed_line_number_size,
                                 int localvariable_table_len,
                                 int exception_table_len);

  bool has_generic_signature() const
    { return (_flags & _has_generic_signature) != 0; }

  bool has_linenumber_table() const
    { return (_flags & _has_linenumber_table) != 0; }

  bool has_checked_exceptions() const
    { return (_flags & _has_checked_exceptions) != 0; }

  bool has_localvariable_table() const
    { return (_flags & _has_localvariable_table) != 0; }

  bool has_exception_handler() const
    { return (_flags & _has_exception_table) != 0; }

  MethodType method_type() const {
    return ((_flags & _is_overpass) == 0) ? NORMAL : OVERPASS;
  }

  void set_method_type(MethodType mt) {
    if (mt == NORMAL) {
      _flags &= ~(_is_overpass);
    } else {
      _flags |= _is_overpass;
    }
  }


  void set_interpreter_kind(int kind)      { _interpreter_kind = kind; }
  int  interpreter_kind(void) const        { return _interpreter_kind; }

  // constant pool
  ConstantPool* constants() const        { return _constants; }
  void set_constants(ConstantPool* c)    { _constants = c; }

  Method* method() const;

  // stackmap table data
  Array<u1>* stackmap_data() const { return _stackmap_data; }
  void set_stackmap_data(Array<u1>* sd) { _stackmap_data = sd; }
  bool has_stackmap_table() const { return _stackmap_data != NULL; }

  void init_fingerprint() {
    const uint64_t initval = CONST64(0x8000000000000000);
    _fingerprint = initval;
  }

  uint64_t fingerprint() const                   {
    // Since reads aren't atomic for 64 bits, if any of the high or low order
    // word is the initial value, return 0.  See init_fingerprint for initval.
    uint high_fp = (uint)(_fingerprint >> 32);
    if ((int) _fingerprint == 0 || high_fp == 0x80000000) {
      return 0L;
    } else {
      return _fingerprint;
    }
  }

  uint64_t set_fingerprint(uint64_t new_fingerprint) {
#ifdef ASSERT
    // Assert only valid if complete/valid 64 bit _fingerprint value is read.
    uint64_t oldfp = fingerprint();
#endif // ASSERT
    _fingerprint = new_fingerprint;
    assert(oldfp == 0L || new_fingerprint == oldfp,
           "fingerprint cannot change");
    assert(((new_fingerprint >> 32) != 0x80000000) && (int)new_fingerprint !=0,
           "fingerprint should call init to set initial value");
    return new_fingerprint;
  }

  // name
  int name_index() const                         { return _name_index; }
  void set_name_index(int index)                 { _name_index = index; }

  // signature
  int signature_index() const                    { return _signature_index; }
  void set_signature_index(int index)            { _signature_index = index; }

  // generics support
  int generic_signature_index() const            {
    if (has_generic_signature()) {
      return *generic_signature_index_addr();
    } else {
      return 0;
    }
  }
  void set_generic_signature_index(u2 index)    {
    assert(has_generic_signature(), "");
    u2* addr = generic_signature_index_addr();
    *addr = index;
  }

  // Sizing
  static int header_size() {
    return sizeof(ConstMethod)/HeapWordSize;
  }

  // Size needed
  static int size(int code_size, int compressed_line_number_size,
                         int local_variable_table_length,
                         int exception_table_length,
                         int checked_exceptions_length,
                         u2  generic_signature_index);

  int size() const                    { return _constMethod_size;}
  void set_constMethod_size(int size)     { _constMethod_size = size; }

  // code size
  int code_size() const                          { return _code_size; }
  void set_code_size(int size) {
    assert(max_method_code_size < (1 << 16),
           "u2 is too small to hold method code size in general");
    assert(0 <= size && size <= max_method_code_size, "invalid code size");
    _code_size = size;
  }

  // linenumber table - note that length is unknown until decompression,
  // see class CompressedLineNumberReadStream.
  u_char* compressed_linenumber_table() const;         // not preserved by gc
  u2* generic_signature_index_addr() const;
  u2* checked_exceptions_length_addr() const;
  u2* localvariable_table_length_addr() const;
  u2* exception_table_length_addr() const;

  // checked exceptions
  int checked_exceptions_length() const;
  CheckedExceptionElement* checked_exceptions_start() const;

  // localvariable table
  int localvariable_table_length() const;
  LocalVariableTableElement* localvariable_table_start() const;

  // exception table
  int exception_table_length() const;
  ExceptionTableElement* exception_table_start() const;

  // byte codes
  void    set_code(address code) {
    if (code_size() > 0) {
      memcpy(code_base(), code, code_size());
    }
  }
  address code_base() const            { return (address) (this+1); }
  address code_end() const             { return code_base() + code_size(); }
  bool    contains(address bcp) const  { return code_base() <= bcp
                                                     && bcp < code_end(); }
  // Offset to bytecodes
  static ByteSize codes_offset()
                            { return in_ByteSize(sizeof(ConstMethod)); }

  static ByteSize constants_offset()
                            { return byte_offset_of(ConstMethod, _constants); }

  static ByteSize max_stack_offset()
                            { return byte_offset_of(ConstMethod, _max_stack); }

  // Unique id for the method
  static const u2 MAX_IDNUM;
  static const u2 UNSET_IDNUM;
  u2 method_idnum() const                        { return _method_idnum; }
  void set_method_idnum(u2 idnum)                { _method_idnum = idnum; }

  // max stack
  int  max_stack() const                         { return _max_stack; }
  void set_max_stack(int size)                   { _max_stack = size; }

  // Deallocation for RedefineClasses
  void deallocate_contents(ClassLoaderData* loader_data);
  bool is_klass() const { return false; }
  DEBUG_ONLY(bool on_stack() { return false; })

private:
  // Since the size of the compressed line number table is unknown, the
  // offsets of the other variable sized sections are computed backwards
  // from the end of the ConstMethod*.

  // First byte after ConstMethod*
  address constMethod_end() const
                          { return (address)((oop*)this + _constMethod_size); }

  // Last short in ConstMethod*
  u2* last_u2_element() const
                                         { return (u2*)constMethod_end() - 1; }

 public:
  // Printing
  void print_on      (outputStream* st) const;
  void print_value_on(outputStream* st) const;

  const char* internal_name() const { return "{constMethod}"; }

  // Verify
  void verify_on(outputStream* st);
};

#endif // SHARE_VM_OOPS_CONSTMETHODOOP_HPP
