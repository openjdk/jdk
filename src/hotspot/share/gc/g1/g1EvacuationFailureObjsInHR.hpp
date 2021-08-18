/*
 * Copyright (c) 2021, Huawei and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1EVACUATIONFAILUREOBJSINHR_HPP
#define SHARE_GC_G1_G1EVACUATIONFAILUREOBJSINHR_HPP

#include "memory/iterator.hpp"
#include "oops/oop.hpp"

// This class
//   1. records the objects per region which have failed to evacuate.
//   2. speeds up removing self forwarded ptrs in post evacuation phase.
//
class G1EvacuationFailureObjsInHR {
  class Node : public CHeapObj<mtGC>{
    friend G1EvacuationFailureObjsInHR;
  private:
    Node* volatile _next;
    oop _obj;
  public:
    Node(oop obj = NULL) : _next(NULL), _obj(obj) {}
  };

private:
  const uint _region_idx;
  Node  _head;
  Node* volatile _tail;
  uint _objs_num;
  oop* _oop_array;

private:
  void compact();
  void sort();
  void iterate_internal(ObjectClosure* closure);
  void clear_list();
  void clear_array();
  void reset();

public:
  G1EvacuationFailureObjsInHR(uint region_idx);
  ~G1EvacuationFailureObjsInHR();

  void record(oop obj);
  void iterate(ObjectClosure* closure);
};


#endif //SHARE_GC_G1_G1EVACUATIONFAILUREOBJSINHR_HPP
