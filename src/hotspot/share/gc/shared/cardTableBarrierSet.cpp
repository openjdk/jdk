/*
 * Copyright (c) 2000, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "compiler/compilerDefinitions.inline.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/cardTableBarrierSet.inline.hpp"
#include "gc/shared/cardTableBarrierSetAssembler.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/space.hpp"
#include "logging/log.hpp"
#include "nmt/memTracker.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/javaThread.hpp"
#include "utilities/align.hpp"
#include "utilities/macros.hpp"
#ifdef COMPILER1
#include "gc/shared/c1/cardTableBarrierSetC1.hpp"
#endif
#ifdef COMPILER2
#include "gc/shared/c2/cardTableBarrierSetC2.hpp"
#endif

class CardTableBarrierSetC1;
class CardTableBarrierSetC2;

// This kind of "BarrierSet" allows a "CollectedHeap" to detect and
// enumerate ref fields that have been modified (since the last
// enumeration.)

CardTableBarrierSet::CardTableBarrierSet(BarrierSetAssembler* barrier_set_assembler,
                                         BarrierSetC1* barrier_set_c1,
                                         BarrierSetC2* barrier_set_c2,
                                         CardTable* card_table,
                                         const BarrierSet::FakeRtti& fake_rtti) :
  BarrierSet(barrier_set_assembler,
             barrier_set_c1,
             barrier_set_c2,
             nullptr /* barrier_set_nmethod */,
             nullptr /* barrier_set_stack_chunk */,
             fake_rtti.add_tag(BarrierSet::CardTableBarrierSet)),
  _card_table(card_table)
{}

CardTableBarrierSet::CardTableBarrierSet(CardTable* card_table) :
  BarrierSet(make_barrier_set_assembler<CardTableBarrierSetAssembler>(),
             make_barrier_set_c1<CardTableBarrierSetC1>(),
             make_barrier_set_c2<CardTableBarrierSetC2>(),
             nullptr /* barrier_set_nmethod */,
             nullptr /* barrier_set_stack_chunk */,
             BarrierSet::FakeRtti(BarrierSet::CardTableBarrierSet)),
  _card_table(card_table)
{}

CardTableBarrierSet::~CardTableBarrierSet() {
  delete card_table();
}

void CardTableBarrierSet::write_region(MemRegion mr) {
  card_table()->dirty_MemRegion(mr);
}

void CardTableBarrierSet::print_on(outputStream* st) const {
  card_table()->print_on(st);
}

// Helper for ReduceInitialCardMarks. For performance,
// compiled code may elide card-marks for initializing stores
// to a newly allocated object along the fast-path. We
// compensate for such elided card-marks as follows:
// (a) Generational, non-concurrent collectors, such as
//     SerialHeap(DefNew,Tenured) and
//     ParallelScavengeHeap(ParallelGC, ParallelOldGC)
//     need the card-mark if and only if the region is
//     in the old gen, and do not care if the card-mark
//     succeeds or precedes the initializing stores themselves,
//     so long as the card-mark is completed before the next
//     scavenge. For all these cases, we can do a card mark
//     at the point at which we do a slow path allocation
//     in the old gen, i.e. in this call.
// (b) G1CollectedHeap(G1) uses two kinds of write barriers. When a
//     G1 concurrent marking is in progress an SATB (pre-write-)barrier
//     is used to remember the pre-value of any store. Initializing
//     stores will not need this barrier, so we need not worry about
//     compensating for the missing pre-barrier here. Turning now
//     to the post-barrier, we note that G1 needs a RS update barrier
//     which simply enqueues a (sequence of) dirty cards which may
//     optionally be refined by the concurrent update threads. Note
//     that this barrier need only be applied to a non-young write.
//
// For any future collector, this code should be reexamined with
// that specific collector in mind, and the documentation above suitably
// extended and updated.
void CardTableBarrierSet::on_slowpath_allocation_exit(JavaThread* thread, oop new_obj) {
#if COMPILER2_OR_JVMCI
  if (!ReduceInitialCardMarks) {
    return;
  }
  if (new_obj->is_typeArray() || card_table()->is_in_young(new_obj)) {
    // Arrays of non-references don't need a post-barrier.
  } else {
    MemRegion mr(cast_from_oop<HeapWord*>(new_obj), new_obj->size());
    assert(!mr.is_empty(), "Error");
    // Do the card mark
    write_region(mr);
  }
#endif // COMPILER2_OR_JVMCI
}
