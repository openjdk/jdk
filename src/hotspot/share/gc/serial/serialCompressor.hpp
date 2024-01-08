/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SERIAL_SERIALCOMPRESSOR_HPP
#define SHARE_GC_SERIAL_SERIALCOMPRESSOR_HPP

#include "gc/shared/gcTrace.hpp"
#include "gc/shared/markBitMap.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"
#include "utilities/stack.hpp"

class Generation;
class Space;
class STWGCTimer;

class SCBlockOffsetTable {
private:
  static const int WORDS_PER_BLOCK = 64;

  HeapWord** _table;
  MarkBitMap& _mark_bitmap;
  MemRegion _covered;

  inline size_t addr_to_block_idx(HeapWord* addr) const;

  void build_table_for_space(ContiguousSpace* space);
  void build_table_for_generation(Generation* generation);

public:
  SCBlockOffsetTable(MarkBitMap& mark_bitmap);
  ~SCBlockOffsetTable();

  void build_table();

  inline HeapWord* forwardee(HeapWord* addr) const;
};

class SerialCompressor : public StackObj {
private:

  MemRegion  _mark_bitmap_region;
  MarkBitMap _mark_bitmap;
  Stack<oop,mtGC> _marking_stack;

  SCBlockOffsetTable _bot;

  STWGCTimer* _gc_timer;
  SerialOldTracer _gc_tracer;

  void phase1_mark(bool clear_all_softrefs);
  void phase2_build_bot();
  void phase3_compact_and_update();

  bool mark_object(oop obj);
  void follow_object(oop obj);

  void update_roots();
  void compact_and_update_space(ContiguousSpace* space);
  void compact_and_update_generation(Generation* generation);

public:
  SerialCompressor(STWGCTimer* gc_timer);
  ~SerialCompressor();

  // TODO: better scoping?
  void follow_stack();
  template<class T>
  void mark_and_push(T* p);

  void invoke_at_safepoint(bool clear_all_softrefs);
};

#endif // SHARE_GC_SERIAL_SERIALCOMPRESSOR_HPP
