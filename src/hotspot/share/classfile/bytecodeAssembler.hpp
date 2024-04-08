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

#ifndef SHARE_CLASSFILE_BYTECODEASSEMBLER_HPP
#define SHARE_CLASSFILE_BYTECODEASSEMBLER_HPP

#include "memory/allocation.hpp"
#include "oops/method.hpp"
#include "oops/symbol.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/resourceHash.hpp"


/**
 * Bytecode Assembler
 *
 * These classes are used to synthesize code for creating new methods from
 * within the VM.  This is only a partial implementation of an assembler;
 * only the bytecodes that are needed by clients are implemented at this time.
 * This is used during default method analysis to create overpass methods
 * and add them to a call during parsing.  Other uses (such as creating
 * bridges) may come later.  Any missing bytecodes can be implemented on an
 * as-need basis.
 */

class BytecodeBuffer : public GrowableArray<u1> {
 public:
  BytecodeBuffer() : GrowableArray<u1>(20) {}
};

// Entries in a yet-to-be-created constant pool.  Limited types for now.
class BytecodeCPEntry {
 public:
  enum tag {
    ERROR_TAG,
    UTF8,
    KLASS,
    STRING,
    NAME_AND_TYPE,
    METHODREF
  };

  u1 _tag;
  union {
    Symbol* utf8;
    u2 klass;
    struct {
      u2 name_index;
      u2 type_index;
    } name_and_type;
    struct {
      u2 class_index;
      u2 name_and_type_index;
    } methodref;
    uintptr_t hash;
  } _u;

  BytecodeCPEntry() : _tag(ERROR_TAG) { _u.hash = 0; }
  BytecodeCPEntry(u1 tag) : _tag(tag) { _u.hash = 0; }

  static BytecodeCPEntry utf8(Symbol* symbol) {
    BytecodeCPEntry bcpe(UTF8);
    bcpe._u.utf8 = symbol;
    return bcpe;
  }

  static BytecodeCPEntry klass(u2 index) {
    BytecodeCPEntry bcpe(KLASS);
    bcpe._u.klass = index;
    return bcpe;
  }

  static BytecodeCPEntry string(Symbol* symbol) {
    BytecodeCPEntry bcpe(STRING);
    bcpe._u.utf8 = symbol;
    return bcpe;
  }

  static BytecodeCPEntry name_and_type(u2 name, u2 type) {
    BytecodeCPEntry bcpe(NAME_AND_TYPE);
    bcpe._u.name_and_type.name_index = name;
    bcpe._u.name_and_type.type_index = type;
    return bcpe;
  }

  static BytecodeCPEntry methodref(u2 class_index, u2 nat) {
    BytecodeCPEntry bcpe(METHODREF);
    bcpe._u.methodref.class_index = class_index;
    bcpe._u.methodref.name_and_type_index = nat;
    return bcpe;
  }

  static bool equals(BytecodeCPEntry const& e0, BytecodeCPEntry const& e1) {
    // The hash is the "union trick" value of the information saved for the tag,
    // so can be compared for equality.
    return e0._tag == e1._tag && e0._u.hash == e1._u.hash;
  }

  static unsigned hash(BytecodeCPEntry const& e0) {
    return (unsigned)(e0._tag ^ e0._u.hash);
  }
};

class BytecodeConstantPool : public ResourceObj {
 private:
  typedef ResourceHashtable<BytecodeCPEntry, u2,
      256, AnyObj::RESOURCE_AREA, mtInternal,
      &BytecodeCPEntry::hash, &BytecodeCPEntry::equals> IndexHash;

  ConstantPool* _orig;
  GrowableArray<BytecodeCPEntry> _added_entries;
  IndexHash _index_map;
  int _orig_cp_added;

  u2 find_or_add(BytecodeCPEntry const& bcpe, TRAPS);

  void init();
 public:

  BytecodeConstantPool(ConstantPool* orig) : _orig(orig), _orig_cp_added(0) {
    init();
  }

  BytecodeCPEntry const& at(u2 index) const { return _added_entries.at(index); }

  InstanceKlass* pool_holder() const {
    return _orig->pool_holder();
  }

  u2 utf8(Symbol* sym, TRAPS) {
    return find_or_add(BytecodeCPEntry::utf8(sym), THREAD);
  }

  u2 klass(Symbol* class_name, TRAPS) {
    u2 utf8_entry = utf8(class_name, CHECK_0);
    return find_or_add(BytecodeCPEntry::klass(utf8_entry), THREAD);
  }

  u2 string(Symbol* str, TRAPS) {
    // Create the utf8_entry in the hashtable but use Symbol for matching.
    (void)utf8(str, CHECK_0);
    return find_or_add(BytecodeCPEntry::string(str), THREAD);
  }

  u2 name_and_type(Symbol* name, Symbol* sig, TRAPS) {
    u2 utf8_name = utf8(name, CHECK_0);
    u2 utf8_sig  = utf8(sig, CHECK_0);
    return find_or_add(BytecodeCPEntry::name_and_type(utf8_name, utf8_sig), THREAD);
  }

  u2 methodref(Symbol* class_name, Symbol* name, Symbol* sig, TRAPS) {
    u2 klass_entry = klass(class_name, CHECK_0);
    u2 type_entry = name_and_type(name, sig, CHECK_0);
    return find_or_add(BytecodeCPEntry::methodref(klass_entry, type_entry), THREAD);
  }

  ConstantPool* create_constant_pool(TRAPS) const;
};

// Partial bytecode assembler - only what we need for creating
// overpass methods for default methods is implemented
class BytecodeAssembler : StackObj {
 private:
  BytecodeBuffer* _code;
  BytecodeConstantPool* _cp;

  void append(u1 imm_u1);
  void append(u2 imm_u2);
  void append(u4 imm_u4);

  void athrow();
  void dup();
  void invokespecial(Symbol* cls, Symbol* name, Symbol* sig, TRAPS);
  void ldc(u1 index);
  void ldc_w(u2 index);
  void _new(Symbol* sym, TRAPS);
  void load_string(Symbol* sym, TRAPS);

 public:
  BytecodeAssembler(BytecodeBuffer* buffer, BytecodeConstantPool* cp)
    : _code(buffer), _cp(cp) {}

  static int assemble_method_error(BytecodeConstantPool* cp,
                                   BytecodeBuffer* buffer,
                                   Symbol* errorName,
                                   Symbol* message, TRAPS);
};

#endif // SHARE_CLASSFILE_BYTECODEASSEMBLER_HPP
