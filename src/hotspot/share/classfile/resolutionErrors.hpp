/*
 * Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CLASSFILE_RESOLUTIONERRORS_HPP
#define SHARE_CLASSFILE_RESOLUTIONERRORS_HPP

#include "oops/constantPool.hpp"

class ResolutionErrorEntry;

// ResolutionError objects are used to record errors encountered during
// constant pool resolution (JVMS 5.4.3).

class ResolutionErrorTable : AllStatic {

public:
  static void initialize();
  static void add_entry(const constantPoolHandle& pool, int which, Symbol* error, Symbol* message,
                        Symbol* cause, Symbol* cause_msg);

  static void add_entry(const constantPoolHandle& pool, int which, const char* message);

  // find error given the constant pool and constant pool index
  static ResolutionErrorEntry* find_entry(const constantPoolHandle& pool, int cp_index);

  // purges unloaded entries from the table
  static void purge_resolution_errors();

  // RedefineClasses support - remove obsolete constant pool entry
  static void delete_entry(ConstantPool* c);

  // This value is added to the cpCache index of an invokedynamic instruction when
  // storing the resolution error resulting from that invokedynamic instruction.
  // This prevents issues where the cpCache index is the same as the constant pool
  // index of another entry in the table.
  static const int CPCACHE_INDEX_MANGLE_VALUE = 1000000;

  // This function is used to encode an invokedynamic index to differentiate it from a
  // constant pool index.  It assumes it is being called with a index that is less than 0
  static int encode_indy_index(int index) {
    assert(index < 0, "Unexpected non-negative cpCache index");
    return index + CPCACHE_INDEX_MANGLE_VALUE;
  }
};


class ResolutionErrorEntry : public CHeapObj<mtClass> {
 private:
  Symbol*           _error;
  Symbol*           _message;
  Symbol*           _cause;
  Symbol*           _cause_msg;
  const char*       _nest_host_error;

  NONCOPYABLE(ResolutionErrorEntry);

 public:
    ResolutionErrorEntry(Symbol* error, Symbol* message, Symbol* cause, Symbol* cause_msg);

    ResolutionErrorEntry(const char* message):
        _error(nullptr),
        _message(nullptr),
        _cause(nullptr),
        _cause_msg(nullptr),
        _nest_host_error(message) {}

    ~ResolutionErrorEntry();

    void set_nest_host_error(const char* message) {
      _nest_host_error = message;
    }


  Symbol*            error() const              { return _error; }
  Symbol*            message() const            { return _message; }
  Symbol*            cause() const              { return _cause; }
  Symbol*            cause_msg() const          { return _cause_msg; }
  const char*        nest_host_error() const    { return _nest_host_error; }
};

#endif // SHARE_CLASSFILE_RESOLUTIONERRORS_HPP
