/*
 * Copyright (c) 1999, 2009, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_ciStreams.cpp.incl"

// ciExceptionHandlerStream
//
// Walk over some selected set of a methods exception handlers.

// ------------------------------------------------------------------
// ciExceptionHandlerStream::count
//
// How many exception handlers are there in this stream?
//
// Implementation note: Compiler2 needs this functionality, so I had
int ciExceptionHandlerStream::count() {
  int save_pos = _pos;
  int save_end = _end;

  int count = 0;

  _pos = -1;
  _end = _method->_handler_count;


  next();
  while (!is_done()) {
    count++;
    next();
  }

  _pos = save_pos;
  _end = save_end;

  return count;
}

int ciExceptionHandlerStream::count_remaining() {
  int save_pos = _pos;
  int save_end = _end;

  int count = 0;

  while (!is_done()) {
    count++;
    next();
  }

  _pos = save_pos;
  _end = save_end;

  return count;
}

// ciBytecodeStream
//
// The class is used to iterate over the bytecodes of a method.
// It hides the details of constant pool structure/access by
// providing accessors for constant pool items.

// ------------------------------------------------------------------
// ciBytecodeStream::wide
//
// Special handling for the wide bytcode
Bytecodes::Code ciBytecodeStream::wide()
{
  // Get following bytecode; do not return wide
  Bytecodes::Code bc = (Bytecodes::Code)_pc[1];
  _pc += 2;                     // Skip both bytecodes
  _pc += 2;                     // Skip index always
  if( bc == Bytecodes::_iinc )
    _pc += 2;                   // Skip optional constant
  _was_wide = _pc;              // Flag last wide bytecode found
  return bc;
}

// ------------------------------------------------------------------
// ciBytecodeStream::table
//
// Special handling for switch ops
Bytecodes::Code ciBytecodeStream::table( Bytecodes::Code bc ) {
  switch( bc ) {                // Check for special bytecode handling

  case Bytecodes::_lookupswitch:
    _pc++;                      // Skip wide bytecode
    _pc += (_start-_pc)&3;      // Word align
    _table_base = (jint*)_pc;   // Capture for later usage
                                // table_base[0] is default far_dest
    // Table has 2 lead elements (default, length), then pairs of u4 values.
    // So load table length, and compute address at end of table
    _pc = (address)&_table_base[2+ 2*Bytes::get_Java_u4((address)&_table_base[1])];
    break;

  case Bytecodes::_tableswitch: {
    _pc++;                      // Skip wide bytecode
    _pc += (_start-_pc)&3;      // Word align
    _table_base = (jint*)_pc;   // Capture for later usage
                                // table_base[0] is default far_dest
    int lo = Bytes::get_Java_u4((address)&_table_base[1]);// Low bound
    int hi = Bytes::get_Java_u4((address)&_table_base[2]);// High bound
    int len = hi - lo + 1;      // Dense table size
    _pc = (address)&_table_base[3+len]; // Skip past table
    break;
  }

  default:
    fatal("unhandled bytecode");
  }
  return bc;
}

// ------------------------------------------------------------------
// ciBytecodeStream::reset_to_bci
void ciBytecodeStream::reset_to_bci( int bci ) {
  _bc_start=_was_wide=0;
  _pc = _start+bci;
}

// ------------------------------------------------------------------
// ciBytecodeStream::force_bci
void ciBytecodeStream::force_bci(int bci) {
  if (bci < 0) {
    reset_to_bci(0);
    _bc_start = _start + bci;
    _bc = EOBC();
  } else {
    reset_to_bci(bci);
    next();
  }
}


// ------------------------------------------------------------------
// Constant pool access
// ------------------------------------------------------------------

// ------------------------------------------------------------------
// ciBytecodeStream::get_klass_index
//
// If this bytecodes references a klass, return the index of the
// referenced klass.
int ciBytecodeStream::get_klass_index() const {
  switch(cur_bc()) {
  case Bytecodes::_ldc:
    return get_index();
  case Bytecodes::_ldc_w:
  case Bytecodes::_ldc2_w:
  case Bytecodes::_checkcast:
  case Bytecodes::_instanceof:
  case Bytecodes::_anewarray:
  case Bytecodes::_multianewarray:
  case Bytecodes::_new:
  case Bytecodes::_newarray:
    return get_index_big();
  default:
    ShouldNotReachHere();
    return 0;
  }
}

// ------------------------------------------------------------------
// ciBytecodeStream::get_klass
//
// If this bytecode is a new, newarray, multianewarray, instanceof,
// or checkcast, get the referenced klass.
ciKlass* ciBytecodeStream::get_klass(bool& will_link) {
  VM_ENTRY_MARK;
  constantPoolHandle cpool(_method->get_methodOop()->constants());
  return CURRENT_ENV->get_klass_by_index(cpool, get_klass_index(), will_link, _holder);
}

// ------------------------------------------------------------------
// ciBytecodeStream::get_constant_index
//
// If this bytecode is one of the ldc variants, get the index of the
// referenced constant.
int ciBytecodeStream::get_constant_index() const {
  switch(cur_bc()) {
  case Bytecodes::_ldc:
    return get_index();
  case Bytecodes::_ldc_w:
  case Bytecodes::_ldc2_w:
    return get_index_big();
  default:
    ShouldNotReachHere();
    return 0;
  }
}
// ------------------------------------------------------------------
// ciBytecodeStream::get_constant
//
// If this bytecode is one of the ldc variants, get the referenced
// constant.
ciConstant ciBytecodeStream::get_constant() {
  VM_ENTRY_MARK;
  constantPoolHandle cpool(_method->get_methodOop()->constants());
  return CURRENT_ENV->get_constant_by_index(cpool, get_constant_index(), _holder);
}

// ------------------------------------------------------------------
bool ciBytecodeStream::is_unresolved_string() const {
  return CURRENT_ENV->is_unresolved_string(_holder, get_constant_index());
}

// ------------------------------------------------------------------
bool ciBytecodeStream::is_unresolved_klass() const {
  return CURRENT_ENV->is_unresolved_klass(_holder, get_klass_index());
}

// ------------------------------------------------------------------
// ciBytecodeStream::get_field_index
//
// If this is a field access bytecode, get the constant pool
// index of the referenced field.
int ciBytecodeStream::get_field_index() {
  assert(cur_bc() == Bytecodes::_getfield ||
         cur_bc() == Bytecodes::_putfield ||
         cur_bc() == Bytecodes::_getstatic ||
         cur_bc() == Bytecodes::_putstatic, "wrong bc");
  return get_index_big();
}


// ------------------------------------------------------------------
// ciBytecodeStream::get_field
//
// If this bytecode is one of get_field, get_static, put_field,
// or put_static, get the referenced field.
ciField* ciBytecodeStream::get_field(bool& will_link) {
  ciField* f = CURRENT_ENV->get_field_by_index(_holder, get_field_index());
  will_link = f->will_link(_holder, _bc);
  return f;
}


// ------------------------------------------------------------------
// ciBytecodeStream::get_declared_field_holder
//
// Get the declared holder of the currently referenced field.
//
// Usage note: the holder() of a ciField class returns the canonical
// holder of the field, rather than the holder declared in the
// bytecodes.
//
// There is no "will_link" result passed back.  The user is responsible
// for checking linkability when retrieving the associated field.
ciInstanceKlass* ciBytecodeStream::get_declared_field_holder() {
  VM_ENTRY_MARK;
  constantPoolHandle cpool(_method->get_methodOop()->constants());
  int holder_index = get_field_holder_index();
  bool ignore;
  return CURRENT_ENV->get_klass_by_index(cpool, holder_index, ignore, _holder)
      ->as_instance_klass();
}

// ------------------------------------------------------------------
// ciBytecodeStream::get_field_holder_index
//
// Get the constant pool index of the declared holder of the field
// referenced by the current bytecode.  Used for generating
// deoptimization information.
int ciBytecodeStream::get_field_holder_index() {
  GUARDED_VM_ENTRY(
    constantPoolOop cpool = _holder->get_instanceKlass()->constants();
    return cpool->klass_ref_index_at(get_field_index());
  )
}

// ------------------------------------------------------------------
// ciBytecodeStream::get_field_signature_index
//
// Get the constant pool index of the signature of the field
// referenced by the current bytecode.  Used for generating
// deoptimization information.
int ciBytecodeStream::get_field_signature_index() {
  VM_ENTRY_MARK;
  constantPoolOop cpool = _holder->get_instanceKlass()->constants();
  int nt_index = cpool->name_and_type_ref_index_at(get_field_index());
  return cpool->signature_ref_index_at(nt_index);
}

// ------------------------------------------------------------------
// ciBytecodeStream::get_method_index
//
// If this is a method invocation bytecode, get the constant pool
// index of the invoked method.
int ciBytecodeStream::get_method_index() {
#ifdef ASSERT
  switch (cur_bc()) {
  case Bytecodes::_invokeinterface:
  case Bytecodes::_invokevirtual:
  case Bytecodes::_invokespecial:
  case Bytecodes::_invokestatic:
  case Bytecodes::_invokedynamic:
    break;
  default:
    ShouldNotReachHere();
  }
#endif
  return get_index_int();
}

// ------------------------------------------------------------------
// ciBytecodeStream::get_method
//
// If this is a method invocation bytecode, get the invoked method.
ciMethod* ciBytecodeStream::get_method(bool& will_link) {
  VM_ENTRY_MARK;
  constantPoolHandle cpool(_method->get_methodOop()->constants());
  ciMethod* m = CURRENT_ENV->get_method_by_index(cpool, get_method_index(), cur_bc(), _holder);
  will_link = m->is_loaded();
  return m;
}

// ------------------------------------------------------------------
// ciBytecodeStream::get_declared_method_holder
//
// Get the declared holder of the currently referenced method.
//
// Usage note: the holder() of a ciMethod class returns the canonical
// holder of the method, rather than the holder declared in the
// bytecodes.
//
// There is no "will_link" result passed back.  The user is responsible
// for checking linkability when retrieving the associated method.
ciKlass* ciBytecodeStream::get_declared_method_holder() {
  VM_ENTRY_MARK;
  constantPoolHandle cpool(_method->get_methodOop()->constants());
  bool ignore;
  // report as InvokeDynamic for invokedynamic, which is syntactically classless
  if (cur_bc() == Bytecodes::_invokedynamic)
    return CURRENT_ENV->get_klass_by_name(_holder, ciSymbol::java_dyn_InvokeDynamic(), false);
  return CURRENT_ENV->get_klass_by_index(cpool, get_method_holder_index(), ignore, _holder);
}

// ------------------------------------------------------------------
// ciBytecodeStream::get_method_holder_index
//
// Get the constant pool index of the declared holder of the method
// referenced by the current bytecode.  Used for generating
// deoptimization information.
int ciBytecodeStream::get_method_holder_index() {
  constantPoolOop cpool = _method->get_methodOop()->constants();
  return cpool->klass_ref_index_at(get_method_index());
}

// ------------------------------------------------------------------
// ciBytecodeStream::get_method_signature_index
//
// Get the constant pool index of the signature of the method
// referenced by the current bytecode.  Used for generating
// deoptimization information.
int ciBytecodeStream::get_method_signature_index() {
  VM_ENTRY_MARK;
  constantPoolOop cpool = _holder->get_instanceKlass()->constants();
  int method_index = get_method_index();
  int name_and_type_index = cpool->name_and_type_ref_index_at(method_index);
  return cpool->signature_ref_index_at(name_and_type_index);
}

// ------------------------------------------------------------------
// ciBytecodeStream::get_cpcache
ciCPCache* ciBytecodeStream::get_cpcache() {
  VM_ENTRY_MARK;
  // Get the constant pool.
  constantPoolOop      cpool   = _holder->get_instanceKlass()->constants();
  constantPoolCacheOop cpcache = cpool->cache();

  return CURRENT_ENV->get_object(cpcache)->as_cpcache();
}

// ------------------------------------------------------------------
// ciBytecodeStream::get_call_site
ciCallSite* ciBytecodeStream::get_call_site() {
  VM_ENTRY_MARK;
  // Get the constant pool.
  constantPoolOop      cpool   = _holder->get_instanceKlass()->constants();
  constantPoolCacheOop cpcache = cpool->cache();

  // Get the CallSite from the constant pool cache.
  int method_index = get_method_index();
  ConstantPoolCacheEntry* cpcache_entry = cpcache->secondary_entry_at(method_index);
  oop call_site_oop = cpcache_entry->f1();

  // Create a CallSite object and return it.
  return CURRENT_ENV->get_object(call_site_oop)->as_call_site();
}
