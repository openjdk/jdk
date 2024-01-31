/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/bytecodeAssembler.hpp"
#include "classfile/vmSymbols.hpp"
#include "interpreter/bytecodes.hpp"
#include "memory/oopFactory.hpp"
#include "oops/constantPool.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/bytes.hpp"
#include "utilities/checkedCast.hpp"

u2 BytecodeConstantPool::find_or_add(BytecodeCPEntry const& bcpe, TRAPS) {

  // Check for overflow
  int new_size = _orig->length() + _entries.length();
  if (new_size > USHRT_MAX) {
    THROW_MSG_0(vmSymbols::java_lang_InternalError(), "default methods constant pool overflowed");
  }

  u2 index = checked_cast<u2>(_entries.length());
  bool created = false;
  u2* probe = _indices.put_if_absent(bcpe, index, &created);
  if (created) {
    _entries.append(bcpe);
  } else {
    index = *probe;
  }
  return checked_cast<u2>(index + _orig->length());
}

ConstantPool* BytecodeConstantPool::create_constant_pool(TRAPS) const {
  if (_entries.length() == 0) {
    return _orig;
  }

  int new_size = _orig->length() + _entries.length();
  ConstantPool* cp = ConstantPool::allocate(
      _orig->pool_holder()->class_loader_data(),
      new_size, CHECK_NULL);

  cp->set_pool_holder(_orig->pool_holder());
  constantPoolHandle cp_h(THREAD, cp);
  _orig->copy_cp_to(1, _orig->length() - 1, cp_h, 1, CHECK_NULL);

  // Preserve dynamic constant information from the original pool
  cp->copy_fields(_orig);

  for (int i = 0; i < _entries.length(); ++i) {
    BytecodeCPEntry entry = _entries.at(i);
    int idx = i + _orig->length();
    switch (entry._tag) {
      case BytecodeCPEntry::UTF8:
        entry._u.utf8->increment_refcount();
        cp->symbol_at_put(idx, entry._u.utf8);
        break;
      case BytecodeCPEntry::KLASS:
        cp->klass_index_at_put(
            idx, entry._u.klass);
        break;
      case BytecodeCPEntry::STRING:
        cp->unresolved_string_at_put(
            idx, cp->symbol_at(entry._u.string));
        break;
      case BytecodeCPEntry::NAME_AND_TYPE:
        cp->name_and_type_at_put(idx,
            entry._u.name_and_type.name_index,
            entry._u.name_and_type.type_index);
        break;
      case BytecodeCPEntry::METHODREF:
        cp->method_at_put(idx,
            entry._u.methodref.class_index,
            entry._u.methodref.name_and_type_index);
        break;
      default:
        ShouldNotReachHere();
    }
  }

  cp->initialize_unresolved_klasses(_orig->pool_holder()->class_loader_data(),
                                    CHECK_NULL);
  return cp;
}

void BytecodeAssembler::append(u1 imm_u1) {
  _code->append(imm_u1);
}

void BytecodeAssembler::append(u2 imm_u2) {
  _code->append(0);
  _code->append(0);
  Bytes::put_Java_u2(_code->adr_at(_code->length() - 2), imm_u2);
}

void BytecodeAssembler::append(u4 imm_u4) {
  _code->append(0);
  _code->append(0);
  _code->append(0);
  _code->append(0);
  Bytes::put_Java_u4(_code->adr_at(_code->length() - 4), imm_u4);
}

void BytecodeAssembler::dup() {
  _code->append(Bytecodes::_dup);
}

void BytecodeAssembler::_new(Symbol* sym, TRAPS) {
  u2 cpool_index = _cp->klass(sym, CHECK);
  _code->append(Bytecodes::_new);
  append(cpool_index);
}

void BytecodeAssembler::load_string(Symbol* sym, TRAPS) {
  u2 cpool_index = _cp->string(sym, CHECK);
  if (cpool_index < 0x100) {
    ldc((u1)cpool_index);
  } else {
    ldc_w(cpool_index);
  }
}

void BytecodeAssembler::ldc(u1 index) {
  _code->append(Bytecodes::_ldc);
  append(index);
}

void BytecodeAssembler::ldc_w(u2 index) {
  _code->append(Bytecodes::_ldc_w);
  append(index);
}

void BytecodeAssembler::athrow() {
  _code->append(Bytecodes::_athrow);
}

void BytecodeAssembler::invokespecial(Symbol* klss, Symbol* name, Symbol* sig, TRAPS) {
  u2 methodref_index = _cp->methodref(klss, name, sig, CHECK);
  _code->append(Bytecodes::_invokespecial);
  append(methodref_index);
}

int BytecodeAssembler::assemble_method_error(BytecodeConstantPool* cp,
                                             BytecodeBuffer* buffer,
                                             Symbol* errorName,
                                             Symbol* message, TRAPS) {

  Symbol* init = vmSymbols::object_initializer_name();
  Symbol* sig = vmSymbols::string_void_signature();

  BytecodeAssembler assem(buffer, cp);

  assem._new(errorName, CHECK_0);
  assem.dup();
  assem.load_string(message, CHECK_0);
  assem.invokespecial(errorName, init, sig, CHECK_0);
  assem.athrow();

  return 3; // max stack size: [ exception, exception, string ]
}
