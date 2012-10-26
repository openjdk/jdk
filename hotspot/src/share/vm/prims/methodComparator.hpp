/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_PRIMS_METHODCOMPARATOR_HPP
#define SHARE_VM_PRIMS_METHODCOMPARATOR_HPP

#include "interpreter/bytecodeStream.hpp"
#include "oops/constantPool.hpp"
#include "oops/method.hpp"

class BciMap;

// methodComparator provides an interface for determining if methods of
// different versions of classes are equivalent or switchable

class MethodComparator {
 private:
  static BytecodeStream *_s_old, *_s_new;
  static ConstantPool* _old_cp;
  static ConstantPool* _new_cp;
  static BciMap *_bci_map;
  static bool _switchable_test;
  static GrowableArray<int> *_fwd_jmps;

  static bool args_same(Bytecodes::Code c_old, Bytecodes::Code c_new);
  static bool pool_constants_same(int cpi_old, int cpi_new);
  static int check_stack_and_locals_size(Method* old_method, Method* new_method);

 public:
  // Check if the new method is equivalent to the old one modulo constant pool (EMCP).
  // Intuitive definition: two versions of the same method are EMCP, if they don't differ
  // on the source code level. Practically, we check whether the only difference between
  // method versions is some constantpool indices embedded into the bytecodes, and whether
  // these indices eventually point to the same constants for both method versions.
  static bool methods_EMCP(Method* old_method, Method* new_method);

  static bool methods_switchable(Method* old_method, Method* new_method, BciMap &bci_map);
};


// ByteCode Index Map. For two versions of the same method, where the new version may contain
// fragments not found in the old version, provides a mapping from an index of a bytecode in
// the old method to the index of the same bytecode in the new method.

class BciMap {
 private:
  int *_old_bci, *_new_st_bci, *_new_end_bci;
  int _cur_size, _cur_pos;
  int _pos;

 public:
  BciMap() {
    _cur_size = 50;
    _old_bci = (int*) malloc(sizeof(int) * _cur_size);
    _new_st_bci = (int*) malloc(sizeof(int) * _cur_size);
    _new_end_bci = (int*) malloc(sizeof(int) * _cur_size);
    _cur_pos = 0;
  }

  ~BciMap() {
    free(_old_bci);
    free(_new_st_bci);
    free(_new_end_bci);
  }

  // Store the position of an added fragment, e.g.
  //
  //                              |<- old_bci
  // -----------------------------------------
  // Old method   |invokevirtual 5|aload 1|...
  // -----------------------------------------
  //
  //                                 |<- new_st_bci          |<- new_end_bci
  // --------------------------------------------------------------------
  // New method       |invokevirual 5|aload 2|invokevirtual 6|aload 1|...
  // --------------------------------------------------------------------
  //                                 ^^^^^^^^^^^^^^^^^^^^^^^^
  //                                    Added fragment

  void store_fragment_location(int old_bci, int new_st_bci, int new_end_bci) {
    if (_cur_pos == _cur_size) {
      _cur_size += 10;
      _old_bci = (int*) realloc(_old_bci, sizeof(int) * _cur_size);
      _new_st_bci = (int*) realloc(_new_st_bci, sizeof(int) * _cur_size);
      _new_end_bci = (int*) realloc(_new_end_bci, sizeof(int) * _cur_size);
    }
    _old_bci[_cur_pos] = old_bci;
    _new_st_bci[_cur_pos] = new_st_bci;
    _new_end_bci[_cur_pos] = new_end_bci;
    _cur_pos++;
  }

  int new_bci_for_old(int old_bci) {
    if (_cur_pos == 0 || old_bci < _old_bci[0]) return old_bci;
    _pos = 1;
    while (_pos < _cur_pos && old_bci >= _old_bci[_pos])
      _pos++;
    return _new_end_bci[_pos-1] + (old_bci - _old_bci[_pos-1]);
  }

  // Test if two indexes - one in the old method and another in the new one - correspond
  // to the same bytecode
  bool old_and_new_locations_same(int old_dest_bci, int new_dest_bci) {
    if (new_bci_for_old(old_dest_bci) == new_dest_bci)
      return true;
    else if (_old_bci[_pos-1] == old_dest_bci)
      return (new_dest_bci == _new_st_bci[_pos-1]);
    else return false;
  }
};

#endif // SHARE_VM_PRIMS_METHODCOMPARATOR_HPP
