/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

// Base class for different kinds of abstractions working
// relative to an objects 'this' pointer.

class ThisRelativeObj VALUE_OBJ_CLASS_SPEC {
 public:
  // Address computation
  address addr_at            (int offset)        const     { return (address)this + offset; }
  int     byte_at            (int offset)        const     { return *(addr_at(offset)); }
  address aligned_addr_at    (int offset)        const     { return (address)round_to((intptr_t)addr_at(offset), jintSize); }
  int     aligned_offset     (int offset)        const     { return aligned_addr_at(offset) - addr_at(0); }

  // Word access:
  int     get_Java_u2_at     (int offset)        const     { return Bytes::get_Java_u2(addr_at(offset)); }
  int     get_Java_u4_at     (int offset)        const     { return Bytes::get_Java_u4(addr_at(offset)); }
  int     get_native_u2_at   (int offset)        const     { return Bytes::get_native_u2(addr_at(offset)); }
  int     get_native_u4_at   (int offset)        const     { return Bytes::get_native_u4(addr_at(offset)); }
};


// The base class for different kinds of bytecode abstractions.
// Provides the primitive operations to manipulate code relative
// to an objects 'this' pointer.
// FIXME: Make this a ResourceObj, include the enclosing methodOop, and cache the opcode.

class Bytecode: public ThisRelativeObj {
 protected:
  u_char byte_at(int offset) const               { return *addr_at(offset); }
  bool check_must_rewrite(Bytecodes::Code bc) const;

 public:
  // Attributes
  address bcp() const                            { return addr_at(0); }
  int instruction_size() const                   { return Bytecodes::length_at(bcp()); }

  // Warning: Use code() with caution on live bytecode streams.  4926272
  Bytecodes::Code code() const                   { return Bytecodes::code_at(addr_at(0)); }
  Bytecodes::Code java_code() const              { return Bytecodes::java_code(code()); }
  bool must_rewrite(Bytecodes::Code code) const  { return Bytecodes::can_rewrite(code) && check_must_rewrite(code); }

  // Creation
  inline friend Bytecode* Bytecode_at(address bcp);

  // Static functions for parsing bytecodes in place.
  int get_index_u1(Bytecodes::Code bc) const {
    assert_same_format_as(bc); assert_index_size(1, bc);
    return *(jubyte*)addr_at(1);
  }
  int get_index_u2(Bytecodes::Code bc, bool is_wide = false) const {
    assert_same_format_as(bc, is_wide); assert_index_size(2, bc, is_wide);
    address p = addr_at(is_wide ? 2 : 1);
    if (can_use_native_byte_order(bc, is_wide))
          return Bytes::get_native_u2(p);
    else  return Bytes::get_Java_u2(p);
  }
  int get_index_u2_cpcache(Bytecodes::Code bc) const {
    assert_same_format_as(bc); assert_index_size(2, bc); assert_native_index(bc);
    return Bytes::get_native_u2(addr_at(1)) DEBUG_ONLY(+ constantPoolOopDesc::CPCACHE_INDEX_TAG);
  }
  int get_index_u4(Bytecodes::Code bc) const {
    assert_same_format_as(bc); assert_index_size(4, bc);
    assert(can_use_native_byte_order(bc), "");
    return Bytes::get_native_u4(addr_at(1));
  }
  bool has_index_u4(Bytecodes::Code bc) const {
    return bc == Bytecodes::_invokedynamic;
  }

  int get_offset_s2(Bytecodes::Code bc) const {
    assert_same_format_as(bc); assert_offset_size(2, bc);
    return (jshort) Bytes::get_Java_u2(addr_at(1));
  }
  int get_offset_s4(Bytecodes::Code bc) const {
    assert_same_format_as(bc); assert_offset_size(4, bc);
    return (jint) Bytes::get_Java_u4(addr_at(1));
  }

  int get_constant_u1(int offset, Bytecodes::Code bc) const {
    assert_same_format_as(bc); assert_constant_size(1, offset, bc);
    return *(jbyte*)addr_at(offset);
  }
  int get_constant_u2(int offset, Bytecodes::Code bc, bool is_wide = false) const {
    assert_same_format_as(bc, is_wide); assert_constant_size(2, offset, bc, is_wide);
    return (jshort) Bytes::get_Java_u2(addr_at(offset));
  }

  // These are used locally and also from bytecode streams.
  void assert_same_format_as(Bytecodes::Code testbc, bool is_wide = false) const NOT_DEBUG_RETURN;
  static void assert_index_size(int required_size, Bytecodes::Code bc, bool is_wide = false) NOT_DEBUG_RETURN;
  static void assert_offset_size(int required_size, Bytecodes::Code bc, bool is_wide = false) NOT_DEBUG_RETURN;
  static void assert_constant_size(int required_size, int where, Bytecodes::Code bc, bool is_wide = false) NOT_DEBUG_RETURN;
  static void assert_native_index(Bytecodes::Code bc, bool is_wide = false) NOT_DEBUG_RETURN;
  static bool can_use_native_byte_order(Bytecodes::Code bc, bool is_wide = false) {
    return (!Bytes::is_Java_byte_ordering_different() || Bytecodes::native_byte_order(bc /*, is_wide*/));
  }
};

inline Bytecode* Bytecode_at(address bcp) {
  // Warning: Use with caution on live bytecode streams.  4926272
  return (Bytecode*)bcp;
}


// Abstractions for lookupswitch bytecode

class LookupswitchPair: ThisRelativeObj {
 private:
  int  _match;
  int  _offset;

 public:
  int  match() const                             { return get_Java_u4_at(0 * jintSize); }
  int  offset() const                            { return get_Java_u4_at(1 * jintSize); }
};


class Bytecode_lookupswitch: public Bytecode {
 public:
  void verify() const PRODUCT_RETURN;

  // Attributes
  int  default_offset() const                    { return get_Java_u4_at(aligned_offset(1 + 0*jintSize)); }
  int  number_of_pairs() const                   { return get_Java_u4_at(aligned_offset(1 + 1*jintSize)); }
  LookupswitchPair* pair_at(int i) const         { assert(0 <= i && i < number_of_pairs(), "pair index out of bounds");
                                                   return (LookupswitchPair*)aligned_addr_at(1 + (1 + i)*2*jintSize); }
  // Creation
  inline friend Bytecode_lookupswitch* Bytecode_lookupswitch_at(address bcp);
};

inline Bytecode_lookupswitch* Bytecode_lookupswitch_at(address bcp) {
  Bytecode_lookupswitch* b = (Bytecode_lookupswitch*)bcp;
  debug_only(b->verify());
  return b;
}


class Bytecode_tableswitch: public Bytecode {
 public:
  void verify() const PRODUCT_RETURN;

  // Attributes
  int  default_offset() const                    { return get_Java_u4_at(aligned_offset(1 + 0*jintSize)); }
  int  low_key() const                           { return get_Java_u4_at(aligned_offset(1 + 1*jintSize)); }
  int  high_key() const                          { return get_Java_u4_at(aligned_offset(1 + 2*jintSize)); }
  int  dest_offset_at(int i) const;
  int  length()                                  { return high_key()-low_key()+1; }

  // Creation
  inline friend Bytecode_tableswitch* Bytecode_tableswitch_at(address bcp);
};

inline Bytecode_tableswitch* Bytecode_tableswitch_at(address bcp) {
  Bytecode_tableswitch* b = (Bytecode_tableswitch*)bcp;
  debug_only(b->verify());
  return b;
}


// Abstraction for invoke_{virtual, static, interface, special}

class Bytecode_invoke: public ResourceObj {
 protected:
  methodHandle _method;                          // method containing the bytecode
  int          _bci;                             // position of the bytecode

  Bytecode_invoke(methodHandle method, int bci)  : _method(method), _bci(bci) {}

 public:
  void verify() const;

  // Attributes
  methodHandle method() const                    { return _method; }
  int          bci() const                       { return _bci; }
  address      bcp() const                       { return _method->bcp_from(bci()); }

  int          index() const;                    // the constant pool index for the invoke
  symbolOop    name() const;                     // returns the name of the invoked method
  symbolOop    signature() const;                // returns the signature of the invoked method
  BasicType    result_type(Thread *thread) const; // returns the result type of the invoke

  Bytecodes::Code code() const                   { return Bytecodes::code_at(bcp(), _method()); }
  Bytecodes::Code adjusted_invoke_code() const   { return Bytecodes::java_code(code()); }

  methodHandle static_target(TRAPS);             // "specified" method   (from constant pool)

  // Testers
  bool is_invokeinterface() const                { return adjusted_invoke_code() == Bytecodes::_invokeinterface; }
  bool is_invokevirtual() const                  { return adjusted_invoke_code() == Bytecodes::_invokevirtual; }
  bool is_invokestatic() const                   { return adjusted_invoke_code() == Bytecodes::_invokestatic; }
  bool is_invokespecial() const                  { return adjusted_invoke_code() == Bytecodes::_invokespecial; }
  bool is_invokedynamic() const                  { return adjusted_invoke_code() == Bytecodes::_invokedynamic; }

  bool has_receiver() const                      { return !is_invokestatic() && !is_invokedynamic(); }

  bool is_valid() const                          { return is_invokeinterface() ||
                                                          is_invokevirtual()   ||
                                                          is_invokestatic()    ||
                                                          is_invokespecial()   ||
                                                          is_invokedynamic(); }

  // Creation
  inline friend Bytecode_invoke* Bytecode_invoke_at(methodHandle method, int bci);

  // Like Bytecode_invoke_at. Instead it returns NULL if the bci is not at an invoke.
  inline friend Bytecode_invoke* Bytecode_invoke_at_check(methodHandle method, int bci);
};

inline Bytecode_invoke* Bytecode_invoke_at(methodHandle method, int bci) {
  Bytecode_invoke* b = new Bytecode_invoke(method, bci);
  debug_only(b->verify());
  return b;
}

inline Bytecode_invoke* Bytecode_invoke_at_check(methodHandle method, int bci) {
  Bytecode_invoke* b = new Bytecode_invoke(method, bci);
  return b->is_valid() ? b : NULL;
}


// Abstraction for all field accesses (put/get field/static_
class Bytecode_field: public Bytecode {
public:
  void verify() const;

  int  index() const;
  bool is_static() const;

  // Creation
  inline friend Bytecode_field* Bytecode_field_at(const methodOop method, address bcp);
};

inline Bytecode_field* Bytecode_field_at(const methodOop method, address bcp) {
  Bytecode_field* b = (Bytecode_field*)bcp;
  debug_only(b->verify());
  return b;
}


// Abstraction for checkcast

class Bytecode_checkcast: public Bytecode {
 public:
  void verify() const { assert(Bytecodes::java_code(code()) == Bytecodes::_checkcast, "check checkcast"); }

  // Returns index
  long index() const   { return get_index_u2(Bytecodes::_checkcast); };

  // Creation
  inline friend Bytecode_checkcast* Bytecode_checkcast_at(address bcp);
};

inline Bytecode_checkcast* Bytecode_checkcast_at(address bcp) {
  Bytecode_checkcast* b = (Bytecode_checkcast*)bcp;
  debug_only(b->verify());
  return b;
}


// Abstraction for instanceof

class Bytecode_instanceof: public Bytecode {
 public:
  void verify() const { assert(code() == Bytecodes::_instanceof, "check instanceof"); }

  // Returns index
  long index() const   { return get_index_u2(Bytecodes::_instanceof); };

  // Creation
  inline friend Bytecode_instanceof* Bytecode_instanceof_at(address bcp);
};

inline Bytecode_instanceof* Bytecode_instanceof_at(address bcp) {
  Bytecode_instanceof* b = (Bytecode_instanceof*)bcp;
  debug_only(b->verify());
  return b;
}


class Bytecode_new: public Bytecode {
 public:
  void verify() const { assert(java_code() == Bytecodes::_new, "check new"); }

  // Returns index
  long index() const   { return get_index_u2(Bytecodes::_new); };

  // Creation
  inline friend Bytecode_new* Bytecode_new_at(address bcp);
};

inline Bytecode_new* Bytecode_new_at(address bcp) {
  Bytecode_new* b = (Bytecode_new*)bcp;
  debug_only(b->verify());
  return b;
}


class Bytecode_multianewarray: public Bytecode {
 public:
  void verify() const { assert(java_code() == Bytecodes::_multianewarray, "check new"); }

  // Returns index
  long index() const   { return get_index_u2(Bytecodes::_multianewarray); };

  // Creation
  inline friend Bytecode_multianewarray* Bytecode_multianewarray_at(address bcp);
};

inline Bytecode_multianewarray* Bytecode_multianewarray_at(address bcp) {
  Bytecode_multianewarray* b = (Bytecode_multianewarray*)bcp;
  debug_only(b->verify());
  return b;
}


class Bytecode_anewarray: public Bytecode {
 public:
  void verify() const { assert(java_code() == Bytecodes::_anewarray, "check anewarray"); }

  // Returns index
  long index() const   { return get_index_u2(Bytecodes::_anewarray); };

  // Creation
  inline friend Bytecode_anewarray* Bytecode_anewarray_at(address bcp);
};

inline Bytecode_anewarray* Bytecode_anewarray_at(address bcp) {
  Bytecode_anewarray* b = (Bytecode_anewarray*)bcp;
  debug_only(b->verify());
  return b;
}


// Abstraction for ldc, ldc_w and ldc2_w

class Bytecode_loadconstant: public Bytecode {
 public:
  void verify() const {
    Bytecodes::Code stdc = Bytecodes::java_code(code());
    assert(stdc == Bytecodes::_ldc ||
           stdc == Bytecodes::_ldc_w ||
           stdc == Bytecodes::_ldc2_w, "load constant");
  }

  int index() const;

  inline friend Bytecode_loadconstant* Bytecode_loadconstant_at(const methodOop method, address bcp);
};

inline Bytecode_loadconstant* Bytecode_loadconstant_at(const methodOop method, address bcp) {
  Bytecode_loadconstant* b = (Bytecode_loadconstant*)bcp;
  debug_only(b->verify());
  return b;
}
