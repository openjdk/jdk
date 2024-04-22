/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

void BytecodeConstantPool::init() {
  for (int i = 1; i < _orig->length(); i++) {
    BytecodeCPEntry entry;
    switch(_orig->tag_at(i).value()) {
    case JVM_CONSTANT_Class:
    case JVM_CONSTANT_UnresolvedClass:
      entry = BytecodeCPEntry::klass(_orig->klass_slot_at(i).name_index());
      break;
    case JVM_CONSTANT_Utf8:
      entry = BytecodeCPEntry::utf8(_orig->symbol_at(i));
      break;
    case JVM_CONSTANT_NameAndType:
      entry = BytecodeCPEntry::name_and_type(_orig->name_ref_index_at(i), _orig->signature_ref_index_at(i));
      break;
    case JVM_CONSTANT_Methodref:
      entry = BytecodeCPEntry::methodref(_orig->uncached_klass_ref_index_at(i), _orig->uncached_name_and_type_ref_index_at(i));
      break;
    case JVM_CONSTANT_String:
      entry = BytecodeCPEntry::string(_orig->unresolved_string_at(i));
      break;
    }
    if (entry._tag != BytecodeCPEntry::tag::ERROR_TAG) {
      bool created = false;
      _index_map.put_if_absent(entry, i, &created);
      if (created) {
        _orig_cp_added += 1;
        _added_entries.append(entry);
      }
    }
  }
}

u2 BytecodeConstantPool::find_or_add(BytecodeCPEntry const& bcpe, TRAPS) {

  // Check for overflow
  int new_size = _orig->length() + _added_entries.length() - _orig_cp_added;
  if (new_size > USHRT_MAX) {
    THROW_MSG_0(vmSymbols::java_lang_InternalError(), "default methods constant pool overflowed");
  }

  u2 index = checked_cast<u2>(new_size);
  bool created = false;
  u2* probe = _index_map.put_if_absent(bcpe, index, &created);
  if (created) {
    _added_entries.append(bcpe);
  } else {
    index = *probe;
  }
  return index;
}

ConstantPool* BytecodeConstantPool::create_constant_pool(TRAPS) const {
  if (_added_entries.length() == 0) {
    return _orig;
  }

  int new_size = _orig->length() + _added_entries.length() - _orig_cp_added;
  ConstantPool* cp = ConstantPool::allocate(
      _orig->pool_holder()->class_loader_data(),
      new_size, CHECK_NULL);

  cp->set_pool_holder(_orig->pool_holder());
  constantPoolHandle cp_h(THREAD, cp);
  _orig->copy_cp_to(1, _orig->length() - 1, cp_h, 1, CHECK_NULL);

  // Preserve dynamic constant information from the original pool
  cp->copy_fields(_orig);

  for (int i = _orig_cp_added; i < _added_entries.length(); ++i) {
    // Add new entries that we added to the temporary constant pool, to the
    // newly creatd constant pool.
    BytecodeCPEntry entry = _added_entries.at(i);
    // Get the constant pool index saved in the hashtable for this new entry.
    u2* value = _index_map.get(entry);
    int idx = *value;
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
            idx, entry._u.utf8);
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
