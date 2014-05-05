/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_RESOLUTIONERRORS_HPP
#define SHARE_VM_CLASSFILE_RESOLUTIONERRORS_HPP

#include "oops/constantPool.hpp"
#include "utilities/hashtable.hpp"

class ResolutionErrorEntry;

// ResolutionError objects are used to record errors encountered during
// constant pool resolution (JVMS 5.4.3).

class ResolutionErrorTable : public Hashtable<ConstantPool*, mtClass> {

public:
  ResolutionErrorTable(int table_size);

  ResolutionErrorEntry* new_entry(int hash, ConstantPool* pool, int cp_index,
                                  Symbol* error, Symbol* message);
  void free_entry(ResolutionErrorEntry *entry);

  ResolutionErrorEntry* bucket(int i) {
    return (ResolutionErrorEntry*)Hashtable<ConstantPool*, mtClass>::bucket(i);
  }

  ResolutionErrorEntry** bucket_addr(int i) {
    return (ResolutionErrorEntry**)Hashtable<ConstantPool*, mtClass>::bucket_addr(i);
  }

  void add_entry(int index, ResolutionErrorEntry* new_entry) {
    Hashtable<ConstantPool*, mtClass>::add_entry(index,
      (HashtableEntry<ConstantPool*, mtClass>*)new_entry);
  }

  void add_entry(int index, unsigned int hash,
                 constantPoolHandle pool, int which, Symbol* error, Symbol* message);


  // find error given the constant pool and constant pool index
  ResolutionErrorEntry* find_entry(int index, unsigned int hash,
                                   constantPoolHandle pool, int cp_index);


  unsigned int compute_hash(constantPoolHandle pool, int cp_index) {
    return (unsigned int) pool->identity_hash() + cp_index;
  }

  // purges unloaded entries from the table
  void purge_resolution_errors();

  // RedefineClasses support - remove obsolete constant pool entry
  void delete_entry(ConstantPool* c);
};


class ResolutionErrorEntry : public HashtableEntry<ConstantPool*, mtClass> {
 private:
  int               _cp_index;
  Symbol*           _error;
  Symbol*           _message;

 public:
  ConstantPool*      pool() const               { return literal(); }

  int                cp_index() const           { return _cp_index; }
  void               set_cp_index(int cp_index) { _cp_index = cp_index; }

  Symbol*            error() const              { return _error; }
  void               set_error(Symbol* e);

  Symbol*            message() const            { return _message; }
  void               set_message(Symbol* c);

  ResolutionErrorEntry* next() const {
    return (ResolutionErrorEntry*)HashtableEntry<ConstantPool*, mtClass>::next();
  }

  ResolutionErrorEntry** next_addr() {
    return (ResolutionErrorEntry**)HashtableEntry<ConstantPool*, mtClass>::next_addr();
  }
};

#endif // SHARE_VM_CLASSFILE_RESOLUTIONERRORS_HPP
