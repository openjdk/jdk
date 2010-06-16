/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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

// PcDescs map a physical PC (given as offset from start of nmethod) to
// the corresponding source scope and byte code index.

class nmethod;

class PcDesc VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;
 private:
  int _pc_offset;           // offset from start of nmethod
  int _scope_decode_offset; // offset for scope in nmethod
  int _obj_decode_offset;

  union PcDescFlags {
    int word;
    struct {
      unsigned int reexecute: 1;
      unsigned int is_method_handle_invoke: 1;
      unsigned int return_oop: 1;
    } bits;
    bool operator ==(const PcDescFlags& other) { return word == other.word; }
  } _flags;

 public:
  int pc_offset() const           { return _pc_offset;   }
  int scope_decode_offset() const { return _scope_decode_offset; }
  int obj_decode_offset() const   { return _obj_decode_offset; }

  void set_pc_offset(int x)           { _pc_offset           = x; }
  void set_scope_decode_offset(int x) { _scope_decode_offset = x; }
  void set_obj_decode_offset(int x)   { _obj_decode_offset   = x; }

  // Constructor (only used for static in nmethod.cpp)
  // Also used by ScopeDesc::sender()]
  PcDesc(int pc_offset, int scope_decode_offset, int obj_decode_offset);

  enum {
    // upper and lower exclusive limits real offsets:
    lower_offset_limit = -1,
    upper_offset_limit = (unsigned int)-1 >> 1
  };

  // Flags
  bool     should_reexecute()              const { return _flags.bits.reexecute; }
  void set_should_reexecute(bool z)              { _flags.bits.reexecute = z;    }

  // Does pd refer to the same information as pd?
  bool is_same_info(const PcDesc* pd) {
    return _scope_decode_offset == pd->_scope_decode_offset &&
      _obj_decode_offset == pd->_obj_decode_offset &&
      _flags == pd->_flags;
  }

  bool     is_method_handle_invoke()       const { return _flags.bits.is_method_handle_invoke;     }
  void set_is_method_handle_invoke(bool z)       {        _flags.bits.is_method_handle_invoke = z; }

  bool     return_oop()                    const { return _flags.bits.return_oop;     }
  void set_return_oop(bool z)                    {        _flags.bits.return_oop = z; }

  // Returns the real pc
  address real_pc(const nmethod* code) const;

  void print(nmethod* code);
  bool verify(nmethod* code);
};
