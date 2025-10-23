/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZMARKSTACKALLOCATOR_HPP
#define SHARE_GC_Z_ZMARKSTACKALLOCATOR_HPP

#include "gc/z/zArray.hpp"
#include "gc/z/zValue.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

class ZMarkStackListNode;

class ZMarkingSMR: public CHeapObj<mtGC> {
private:
  struct ZWorkerState {
    ZMarkStackListNode* volatile _hazard_ptr;
    ZArray<ZMarkStackListNode*>  _scanned_hazards;
    ZArray<ZMarkStackListNode*>  _freeing;
  };

  ZPerWorker<ZWorkerState> _worker_states;
  volatile bool            _expanded_recently;

public:
  ZMarkingSMR();
  void free();
  ZMarkStackListNode* allocate_stack();
  void free_node(ZMarkStackListNode* stack);
  ZMarkStackListNode* volatile* hazard_ptr();
};

#endif // SHARE_GC_Z_ZMARKSTACKALLOCATOR_HPP
