/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/cms/cmsLockVerifier.hpp"
#include "gc/cms/compactibleFreeListSpace.hpp"
#include "gc/cms/concurrentMarkSweepGeneration.inline.hpp"
#include "gc/cms/concurrentMarkSweepThread.hpp"
#include "gc/shared/blockOffsetTable.inline.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "gc/shared/space.inline.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "logging/logStream.inline.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/java.hpp"
#include "runtime/orderAccess.inline.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/copy.hpp"

/////////////////////////////////////////////////////////////////////////
//// CompactibleFreeListSpace
/////////////////////////////////////////////////////////////////////////

// highest ranked  free list lock rank
int CompactibleFreeListSpace::_lockRank = Mutex::leaf + 3;

// Defaults are 0 so things will break badly if incorrectly initialized.
size_t CompactibleFreeListSpace::IndexSetStart  = 0;
size_t CompactibleFreeListSpace::IndexSetStride = 0;

size_t MinChunkSize = 0;

void CompactibleFreeListSpace::set_cms_values() {
  // Set CMS global values
  assert(MinChunkSize == 0, "already set");

  // MinChunkSize should be a multiple of MinObjAlignment and be large enough
  // for chunks to contain a FreeChunk.
  size_t min_chunk_size_in_bytes = align_size_up(sizeof(FreeChunk), MinObjAlignmentInBytes);
  MinChunkSize = min_chunk_size_in_bytes / BytesPerWord;

  assert(IndexSetStart == 0 && IndexSetStride == 0, "already set");
  IndexSetStart  = MinChunkSize;
  IndexSetStride = MinObjAlignment;
}

// Constructor
CompactibleFreeListSpace::CompactibleFreeListSpace(BlockOffsetSharedArray* bs, MemRegion mr) :
  _bt(bs, mr),
  // free list locks are in the range of values taken by _lockRank
  // This range currently is [_leaf+2, _leaf+3]
  // Note: this requires that CFLspace c'tors
  // are called serially in the order in which the locks are
  // are acquired in the program text. This is true today.
  _freelistLock(_lockRank--, "CompactibleFreeListSpace._lock", true,
                Monitor::_safepoint_check_sometimes),
  _parDictionaryAllocLock(Mutex::leaf - 1,  // == rank(ExpandHeap_lock) - 1
                          "CompactibleFreeListSpace._dict_par_lock", true,
                          Monitor::_safepoint_check_never),
  _rescan_task_size(CardTableModRefBS::card_size_in_words * BitsPerWord *
                    CMSRescanMultiple),
  _marking_task_size(CardTableModRefBS::card_size_in_words * BitsPerWord *
                    CMSConcMarkMultiple),
  _collector(NULL),
  _preconsumptionDirtyCardClosure(NULL)
{
  assert(sizeof(FreeChunk) / BytesPerWord <= MinChunkSize,
         "FreeChunk is larger than expected");
  _bt.set_space(this);
  initialize(mr, SpaceDecorator::Clear, SpaceDecorator::Mangle);

  _dictionary = new AFLBinaryTreeDictionary(mr);

  assert(_dictionary != NULL, "CMS dictionary initialization");
  // The indexed free lists are initially all empty and are lazily
  // filled in on demand. Initialize the array elements to NULL.
  initializeIndexedFreeListArray();

  _smallLinearAllocBlock.set(0, 0, 1024*SmallForLinearAlloc,
                             SmallForLinearAlloc);

  // CMSIndexedFreeListReplenish should be at least 1
  CMSIndexedFreeListReplenish = MAX2((uintx)1, CMSIndexedFreeListReplenish);
  _promoInfo.setSpace(this);
  if (UseCMSBestFit) {
    _fitStrategy = FreeBlockBestFitFirst;
  } else {
    _fitStrategy = FreeBlockStrategyNone;
  }
  check_free_list_consistency();

  // Initialize locks for parallel case.
  for (size_t i = IndexSetStart; i < IndexSetSize; i += IndexSetStride) {
    _indexedFreeListParLocks[i] = new Mutex(Mutex::leaf - 1, // == ExpandHeap_lock - 1
                                            "a freelist par lock", true, Mutex::_safepoint_check_sometimes);
    DEBUG_ONLY(
      _indexedFreeList[i].set_protecting_lock(_indexedFreeListParLocks[i]);
    )
  }
  _dictionary->set_par_lock(&_parDictionaryAllocLock);
}

// Like CompactibleSpace forward() but always calls cross_threshold() to
// update the block offset table.  Removed initialize_threshold call because
// CFLS does not use a block offset array for contiguous spaces.
HeapWord* CompactibleFreeListSpace::forward(oop q, size_t size,
                                    CompactPoint* cp, HeapWord* compact_top) {
  // q is alive
  // First check if we should switch compaction space
  assert(this == cp->space, "'this' should be current compaction space.");
  size_t compaction_max_size = pointer_delta(end(), compact_top);
  assert(adjustObjectSize(size) == cp->space->adjust_object_size_v(size),
    "virtual adjustObjectSize_v() method is not correct");
  size_t adjusted_size = adjustObjectSize(size);
  assert(compaction_max_size >= MinChunkSize || compaction_max_size == 0,
         "no small fragments allowed");
  assert(minimum_free_block_size() == MinChunkSize,
         "for de-virtualized reference below");
  // Can't leave a nonzero size, residual fragment smaller than MinChunkSize
  if (adjusted_size + MinChunkSize > compaction_max_size &&
      adjusted_size != compaction_max_size) {
    do {
      // switch to next compaction space
      cp->space->set_compaction_top(compact_top);
      cp->space = cp->space->next_compaction_space();
      if (cp->space == NULL) {
        cp->gen = GenCollectedHeap::heap()->young_gen();
        assert(cp->gen != NULL, "compaction must succeed");
        cp->space = cp->gen->first_compaction_space();
        assert(cp->space != NULL, "generation must have a first compaction space");
      }
      compact_top = cp->space->bottom();
      cp->space->set_compaction_top(compact_top);
      // The correct adjusted_size may not be the same as that for this method
      // (i.e., cp->space may no longer be "this" so adjust the size again.
      // Use the virtual method which is not used above to save the virtual
      // dispatch.
      adjusted_size = cp->space->adjust_object_size_v(size);
      compaction_max_size = pointer_delta(cp->space->end(), compact_top);
      assert(cp->space->minimum_free_block_size() == 0, "just checking");
    } while (adjusted_size > compaction_max_size);
  }

  // store the forwarding pointer into the mark word
  if ((HeapWord*)q != compact_top) {
    q->forward_to(oop(compact_top));
    assert(q->is_gc_marked(), "encoding the pointer should preserve the mark");
  } else {
    // if the object isn't moving we can just set the mark to the default
    // mark and handle it specially later on.
    q->init_mark();
    assert(q->forwardee() == NULL, "should be forwarded to NULL");
  }

  compact_top += adjusted_size;

  // we need to update the offset table so that the beginnings of objects can be
  // found during scavenge.  Note that we are updating the offset table based on
  // where the object will be once the compaction phase finishes.

  // Always call cross_threshold().  A contiguous space can only call it when
  // the compaction_top exceeds the current threshold but not for an
  // non-contiguous space.
  cp->threshold =
    cp->space->cross_threshold(compact_top - adjusted_size, compact_top);
  return compact_top;
}

// A modified copy of OffsetTableContigSpace::cross_threshold() with _offsets -> _bt
// and use of single_block instead of alloc_block.  The name here is not really
// appropriate - maybe a more general name could be invented for both the
// contiguous and noncontiguous spaces.

HeapWord* CompactibleFreeListSpace::cross_threshold(HeapWord* start, HeapWord* the_end) {
  _bt.single_block(start, the_end);
  return end();
}

// Initialize them to NULL.
void CompactibleFreeListSpace::initializeIndexedFreeListArray() {
  for (size_t i = 0; i < IndexSetSize; i++) {
    // Note that on platforms where objects are double word aligned,
    // the odd array elements are not used.  It is convenient, however,
    // to map directly from the object size to the array element.
    _indexedFreeList[i].reset(IndexSetSize);
    _indexedFreeList[i].set_size(i);
    assert(_indexedFreeList[i].count() == 0, "reset check failed");
    assert(_indexedFreeList[i].head() == NULL, "reset check failed");
    assert(_indexedFreeList[i].tail() == NULL, "reset check failed");
    assert(_indexedFreeList[i].hint() == IndexSetSize, "reset check failed");
  }
}

size_t CompactibleFreeListSpace::obj_size(const HeapWord* addr) const {
  return adjustObjectSize(oop(addr)->size());
}

void CompactibleFreeListSpace::resetIndexedFreeListArray() {
  for (size_t i = 1; i < IndexSetSize; i++) {
    assert(_indexedFreeList[i].size() == (size_t) i,
      "Indexed free list sizes are incorrect");
    _indexedFreeList[i].reset(IndexSetSize);
    assert(_indexedFreeList[i].count() == 0, "reset check failed");
    assert(_indexedFreeList[i].head() == NULL, "reset check failed");
    assert(_indexedFreeList[i].tail() == NULL, "reset check failed");
    assert(_indexedFreeList[i].hint() == IndexSetSize, "reset check failed");
  }
}

void CompactibleFreeListSpace::reset(MemRegion mr) {
  resetIndexedFreeListArray();
  dictionary()->reset();
  if (BlockOffsetArrayUseUnallocatedBlock) {
    assert(end() == mr.end(), "We are compacting to the bottom of CMS gen");
    // Everything's allocated until proven otherwise.
    _bt.set_unallocated_block(end());
  }
  if (!mr.is_empty()) {
    assert(mr.word_size() >= MinChunkSize, "Chunk size is too small");
    _bt.single_block(mr.start(), mr.word_size());
    FreeChunk* fc = (FreeChunk*) mr.start();
    fc->set_size(mr.word_size());
    if (mr.word_size() >= IndexSetSize ) {
      returnChunkToDictionary(fc);
    } else {
      _bt.verify_not_unallocated((HeapWord*)fc, fc->size());
      _indexedFreeList[mr.word_size()].return_chunk_at_head(fc);
    }
    coalBirth(mr.word_size());
  }
  _promoInfo.reset();
  _smallLinearAllocBlock._ptr = NULL;
  _smallLinearAllocBlock._word_size = 0;
}

void CompactibleFreeListSpace::reset_after_compaction() {
  // Reset the space to the new reality - one free chunk.
  MemRegion mr(compaction_top(), end());
  reset(mr);
  // Now refill the linear allocation block(s) if possible.
  refillLinearAllocBlocksIfNeeded();
}

// Walks the entire dictionary, returning a coterminal
// chunk, if it exists. Use with caution since it involves
// a potentially complete walk of a potentially large tree.
FreeChunk* CompactibleFreeListSpace::find_chunk_at_end() {

  assert_lock_strong(&_freelistLock);

  return dictionary()->find_chunk_ends_at(end());
}


#ifndef PRODUCT
void CompactibleFreeListSpace::initializeIndexedFreeListArrayReturnedBytes() {
  for (size_t i = IndexSetStart; i < IndexSetSize; i += IndexSetStride) {
    _indexedFreeList[i].allocation_stats()->set_returned_bytes(0);
  }
}

size_t CompactibleFreeListSpace::sumIndexedFreeListArrayReturnedBytes() {
  size_t sum = 0;
  for (size_t i = IndexSetStart; i < IndexSetSize; i += IndexSetStride) {
    sum += _indexedFreeList[i].allocation_stats()->returned_bytes();
  }
  return sum;
}

size_t CompactibleFreeListSpace::totalCountInIndexedFreeLists() const {
  size_t count = 0;
  for (size_t i = IndexSetStart; i < IndexSetSize; i++) {
    debug_only(
      ssize_t total_list_count = 0;
      for (FreeChunk* fc = _indexedFreeList[i].head(); fc != NULL;
         fc = fc->next()) {
        total_list_count++;
      }
      assert(total_list_count ==  _indexedFreeList[i].count(),
        "Count in list is incorrect");
    )
    count += _indexedFreeList[i].count();
  }
  return count;
}

size_t CompactibleFreeListSpace::totalCount() {
  size_t num = totalCountInIndexedFreeLists();
  num +=  dictionary()->total_count();
  if (_smallLinearAllocBlock._word_size != 0) {
    num++;
  }
  return num;
}
#endif

bool CompactibleFreeListSpace::is_free_block(const HeapWord* p) const {
  FreeChunk* fc = (FreeChunk*) p;
  return fc->is_free();
}

size_t CompactibleFreeListSpace::used() const {
  return capacity() - free();
}

size_t CompactibleFreeListSpace::free() const {
  // "MT-safe, but not MT-precise"(TM), if you will: i.e.
  // if you do this while the structures are in flux you
  // may get an approximate answer only; for instance
  // because there is concurrent allocation either
  // directly by mutators or for promotion during a GC.
  // It's "MT-safe", however, in the sense that you are guaranteed
  // not to crash and burn, for instance, because of walking
  // pointers that could disappear as you were walking them.
  // The approximation is because the various components
  // that are read below are not read atomically (and
  // further the computation of totalSizeInIndexedFreeLists()
  // is itself a non-atomic computation. The normal use of
  // this is during a resize operation at the end of GC
  // and at that time you are guaranteed to get the
  // correct actual value. However, for instance, this is
  // also read completely asynchronously by the "perf-sampler"
  // that supports jvmstat, and you are apt to see the values
  // flicker in such cases.
  assert(_dictionary != NULL, "No _dictionary?");
  return (_dictionary->total_chunk_size(DEBUG_ONLY(freelistLock())) +
          totalSizeInIndexedFreeLists() +
          _smallLinearAllocBlock._word_size) * HeapWordSize;
}

size_t CompactibleFreeListSpace::max_alloc_in_words() const {
  assert(_dictionary != NULL, "No _dictionary?");
  assert_locked();
  size_t res = _dictionary->max_chunk_size();
  res = MAX2(res, MIN2(_smallLinearAllocBlock._word_size,
                       (size_t) SmallForLinearAlloc - 1));
  // XXX the following could potentially be pretty slow;
  // should one, pessimistically for the rare cases when res
  // calculated above is less than IndexSetSize,
  // just return res calculated above? My reasoning was that
  // those cases will be so rare that the extra time spent doesn't
  // really matter....
  // Note: do not change the loop test i >= res + IndexSetStride
  // to i > res below, because i is unsigned and res may be zero.
  for (size_t i = IndexSetSize - 1; i >= res + IndexSetStride;
       i -= IndexSetStride) {
    if (_indexedFreeList[i].head() != NULL) {
      assert(_indexedFreeList[i].count() != 0, "Inconsistent FreeList");
      return i;
    }
  }
  return res;
}

void LinearAllocBlock::print_on(outputStream* st) const {
  st->print_cr(" LinearAllocBlock: ptr = " PTR_FORMAT ", word_size = " SIZE_FORMAT
            ", refillsize = " SIZE_FORMAT ", allocation_size_limit = " SIZE_FORMAT,
            p2i(_ptr), _word_size, _refillSize, _allocation_size_limit);
}

void CompactibleFreeListSpace::print_on(outputStream* st) const {
  st->print_cr("COMPACTIBLE FREELIST SPACE");
  st->print_cr(" Space:");
  Space::print_on(st);

  st->print_cr("promoInfo:");
  _promoInfo.print_on(st);

  st->print_cr("_smallLinearAllocBlock");
  _smallLinearAllocBlock.print_on(st);

  // dump_memory_block(_smallLinearAllocBlock->_ptr, 128);

  st->print_cr(" _fitStrategy = %s", BOOL_TO_STR(_fitStrategy));
}

void CompactibleFreeListSpace::print_indexed_free_lists(outputStream* st)
const {
  reportIndexedFreeListStatistics(st);
  st->print_cr("Layout of Indexed Freelists");
  st->print_cr("---------------------------");
  AdaptiveFreeList<FreeChunk>::print_labels_on(st, "size");
  for (size_t i = IndexSetStart; i < IndexSetSize; i += IndexSetStride) {
    _indexedFreeList[i].print_on(st);
    for (FreeChunk* fc = _indexedFreeList[i].head(); fc != NULL; fc = fc->next()) {
      st->print_cr("\t[" PTR_FORMAT "," PTR_FORMAT ")  %s",
                   p2i(fc), p2i((HeapWord*)fc + i),
                   fc->cantCoalesce() ? "\t CC" : "");
    }
  }
}

void CompactibleFreeListSpace::print_promo_info_blocks(outputStream* st)
const {
  _promoInfo.print_on(st);
}

void CompactibleFreeListSpace::print_dictionary_free_lists(outputStream* st)
const {
  _dictionary->report_statistics(st);
  st->print_cr("Layout of Freelists in Tree");
  st->print_cr("---------------------------");
  _dictionary->print_free_lists(st);
}

class BlkPrintingClosure: public BlkClosure {
  const CMSCollector*             _collector;
  const CompactibleFreeListSpace* _sp;
  const CMSBitMap*                _live_bit_map;
  const bool                      _post_remark;
  outputStream*                   _st;
public:
  BlkPrintingClosure(const CMSCollector* collector,
                     const CompactibleFreeListSpace* sp,
                     const CMSBitMap* live_bit_map,
                     outputStream* st):
    _collector(collector),
    _sp(sp),
    _live_bit_map(live_bit_map),
    _post_remark(collector->abstract_state() > CMSCollector::FinalMarking),
    _st(st) { }
  size_t do_blk(HeapWord* addr);
};

size_t BlkPrintingClosure::do_blk(HeapWord* addr) {
  size_t sz = _sp->block_size_no_stall(addr, _collector);
  assert(sz != 0, "Should always be able to compute a size");
  if (_sp->block_is_obj(addr)) {
    const bool dead = _post_remark && !_live_bit_map->isMarked(addr);
    _st->print_cr(PTR_FORMAT ": %s object of size " SIZE_FORMAT "%s",
      p2i(addr),
      dead ? "dead" : "live",
      sz,
      (!dead && CMSPrintObjectsInDump) ? ":" : ".");
    if (CMSPrintObjectsInDump && !dead) {
      oop(addr)->print_on(_st);
      _st->print_cr("--------------------------------------");
    }
  } else { // free block
    _st->print_cr(PTR_FORMAT ": free block of size " SIZE_FORMAT "%s",
      p2i(addr), sz, CMSPrintChunksInDump ? ":" : ".");
    if (CMSPrintChunksInDump) {
      ((FreeChunk*)addr)->print_on(_st);
      _st->print_cr("--------------------------------------");
    }
  }
  return sz;
}

void CompactibleFreeListSpace::dump_at_safepoint_with_locks(CMSCollector* c, outputStream* st) {
  st->print_cr("=========================");
  st->print_cr("Block layout in CMS Heap:");
  st->print_cr("=========================");
  BlkPrintingClosure  bpcl(c, this, c->markBitMap(), st);
  blk_iterate(&bpcl);

  st->print_cr("=======================================");
  st->print_cr("Order & Layout of Promotion Info Blocks");
  st->print_cr("=======================================");
  print_promo_info_blocks(st);

  st->print_cr("===========================");
  st->print_cr("Order of Indexed Free Lists");
  st->print_cr("=========================");
  print_indexed_free_lists(st);

  st->print_cr("=================================");
  st->print_cr("Order of Free Lists in Dictionary");
  st->print_cr("=================================");
  print_dictionary_free_lists(st);
}


void CompactibleFreeListSpace::reportFreeListStatistics(const char* title) const {
  assert_lock_strong(&_freelistLock);
  Log(gc, freelist, stats) log;
  if (!log.is_debug()) {
    return;
  }
  log.debug("%s", title);

  LogStream out(log.debug());
  _dictionary->report_statistics(&out);

  if (log.is_trace()) {
    LogStream trace_out(log.trace());
    reportIndexedFreeListStatistics(&trace_out);
    size_t total_size = totalSizeInIndexedFreeLists() +
                       _dictionary->total_chunk_size(DEBUG_ONLY(freelistLock()));
    log.trace(" free=" SIZE_FORMAT " frag=%1.4f", total_size, flsFrag());
  }
}

void CompactibleFreeListSpace::reportIndexedFreeListStatistics(outputStream* st) const {
  assert_lock_strong(&_freelistLock);
  st->print_cr("Statistics for IndexedFreeLists:");
  st->print_cr("--------------------------------");
  size_t total_size = totalSizeInIndexedFreeLists();
  size_t free_blocks = numFreeBlocksInIndexedFreeLists();
  st->print_cr("Total Free Space: " SIZE_FORMAT, total_size);
  st->print_cr("Max   Chunk Size: " SIZE_FORMAT, maxChunkSizeInIndexedFreeLists());
  st->print_cr("Number of Blocks: " SIZE_FORMAT, free_blocks);
  if (free_blocks != 0) {
    st->print_cr("Av.  Block  Size: " SIZE_FORMAT, total_size/free_blocks);
  }
}

size_t CompactibleFreeListSpace::numFreeBlocksInIndexedFreeLists() const {
  size_t res = 0;
  for (size_t i = IndexSetStart; i < IndexSetSize; i += IndexSetStride) {
    debug_only(
      ssize_t recount = 0;
      for (FreeChunk* fc = _indexedFreeList[i].head(); fc != NULL;
         fc = fc->next()) {
        recount += 1;
      }
      assert(recount == _indexedFreeList[i].count(),
        "Incorrect count in list");
    )
    res += _indexedFreeList[i].count();
  }
  return res;
}

size_t CompactibleFreeListSpace::maxChunkSizeInIndexedFreeLists() const {
  for (size_t i = IndexSetSize - 1; i != 0; i -= IndexSetStride) {
    if (_indexedFreeList[i].head() != NULL) {
      assert(_indexedFreeList[i].count() != 0, "Inconsistent FreeList");
      return (size_t)i;
    }
  }
  return 0;
}

void CompactibleFreeListSpace::set_end(HeapWord* value) {
  HeapWord* prevEnd = end();
  assert(prevEnd != value, "unnecessary set_end call");
  assert(prevEnd == NULL || !BlockOffsetArrayUseUnallocatedBlock || value >= unallocated_block(),
        "New end is below unallocated block");
  _end = value;
  if (prevEnd != NULL) {
    // Resize the underlying block offset table.
    _bt.resize(pointer_delta(value, bottom()));
    if (value <= prevEnd) {
      assert(!BlockOffsetArrayUseUnallocatedBlock || value >= unallocated_block(),
             "New end is below unallocated block");
    } else {
      // Now, take this new chunk and add it to the free blocks.
      // Note that the BOT has not yet been updated for this block.
      size_t newFcSize = pointer_delta(value, prevEnd);
      // Add the block to the free lists, if possible coalescing it
      // with the last free block, and update the BOT and census data.
      addChunkToFreeListsAtEndRecordingStats(prevEnd, newFcSize);
    }
  }
}

class FreeListSpaceDCTOC : public FilteringDCTOC {
  CompactibleFreeListSpace* _cfls;
  CMSCollector* _collector;
  bool _parallel;
protected:
  // Override.
#define walk_mem_region_with_cl_DECL(ClosureType)                       \
  virtual void walk_mem_region_with_cl(MemRegion mr,                    \
                                       HeapWord* bottom, HeapWord* top, \
                                       ClosureType* cl);                \
      void walk_mem_region_with_cl_par(MemRegion mr,                    \
                                       HeapWord* bottom, HeapWord* top, \
                                       ClosureType* cl);                \
    void walk_mem_region_with_cl_nopar(MemRegion mr,                    \
                                       HeapWord* bottom, HeapWord* top, \
                                       ClosureType* cl)
  walk_mem_region_with_cl_DECL(ExtendedOopClosure);
  walk_mem_region_with_cl_DECL(FilteringClosure);

public:
  FreeListSpaceDCTOC(CompactibleFreeListSpace* sp,
                     CMSCollector* collector,
                     ExtendedOopClosure* cl,
                     CardTableModRefBS::PrecisionStyle precision,
                     HeapWord* boundary,
                     bool parallel) :
    FilteringDCTOC(sp, cl, precision, boundary),
    _cfls(sp), _collector(collector), _parallel(parallel) {}
};

// We de-virtualize the block-related calls below, since we know that our
// space is a CompactibleFreeListSpace.

#define FreeListSpaceDCTOC__walk_mem_region_with_cl_DEFN(ClosureType)           \
void FreeListSpaceDCTOC::walk_mem_region_with_cl(MemRegion mr,                  \
                                                 HeapWord* bottom,              \
                                                 HeapWord* top,                 \
                                                 ClosureType* cl) {             \
   if (_parallel) {                                                             \
     walk_mem_region_with_cl_par(mr, bottom, top, cl);                          \
   } else {                                                                     \
     walk_mem_region_with_cl_nopar(mr, bottom, top, cl);                        \
   }                                                                            \
}                                                                               \
void FreeListSpaceDCTOC::walk_mem_region_with_cl_par(MemRegion mr,              \
                                                     HeapWord* bottom,          \
                                                     HeapWord* top,             \
                                                     ClosureType* cl) {         \
  /* Skip parts that are before "mr", in case "block_start" sent us             \
     back too far. */                                                           \
  HeapWord* mr_start = mr.start();                                              \
  size_t bot_size = _cfls->CompactibleFreeListSpace::block_size(bottom);        \
  HeapWord* next = bottom + bot_size;                                           \
  while (next < mr_start) {                                                     \
    bottom = next;                                                              \
    bot_size = _cfls->CompactibleFreeListSpace::block_size(bottom);             \
    next = bottom + bot_size;                                                   \
  }                                                                             \
                                                                                \
  while (bottom < top) {                                                        \
    if (_cfls->CompactibleFreeListSpace::block_is_obj(bottom) &&                \
        !_cfls->CompactibleFreeListSpace::obj_allocated_since_save_marks(       \
                    oop(bottom)) &&                                             \
        !_collector->CMSCollector::is_dead_obj(oop(bottom))) {                  \
      size_t word_sz = oop(bottom)->oop_iterate_size(cl, mr);                   \
      bottom += _cfls->adjustObjectSize(word_sz);                               \
    } else {                                                                    \
      bottom += _cfls->CompactibleFreeListSpace::block_size(bottom);            \
    }                                                                           \
  }                                                                             \
}                                                                               \
void FreeListSpaceDCTOC::walk_mem_region_with_cl_nopar(MemRegion mr,            \
                                                       HeapWord* bottom,        \
                                                       HeapWord* top,           \
                                                       ClosureType* cl) {       \
  /* Skip parts that are before "mr", in case "block_start" sent us             \
     back too far. */                                                           \
  HeapWord* mr_start = mr.start();                                              \
  size_t bot_size = _cfls->CompactibleFreeListSpace::block_size_nopar(bottom);  \
  HeapWord* next = bottom + bot_size;                                           \
  while (next < mr_start) {                                                     \
    bottom = next;                                                              \
    bot_size = _cfls->CompactibleFreeListSpace::block_size_nopar(bottom);       \
    next = bottom + bot_size;                                                   \
  }                                                                             \
                                                                                \
  while (bottom < top) {                                                        \
    if (_cfls->CompactibleFreeListSpace::block_is_obj_nopar(bottom) &&          \
        !_cfls->CompactibleFreeListSpace::obj_allocated_since_save_marks(       \
                    oop(bottom)) &&                                             \
        !_collector->CMSCollector::is_dead_obj(oop(bottom))) {                  \
      size_t word_sz = oop(bottom)->oop_iterate_size(cl, mr);                   \
      bottom += _cfls->adjustObjectSize(word_sz);                               \
    } else {                                                                    \
      bottom += _cfls->CompactibleFreeListSpace::block_size_nopar(bottom);      \
    }                                                                           \
  }                                                                             \
}

// (There are only two of these, rather than N, because the split is due
// only to the introduction of the FilteringClosure, a local part of the
// impl of this abstraction.)
FreeListSpaceDCTOC__walk_mem_region_with_cl_DEFN(ExtendedOopClosure)
FreeListSpaceDCTOC__walk_mem_region_with_cl_DEFN(FilteringClosure)

DirtyCardToOopClosure*
CompactibleFreeListSpace::new_dcto_cl(ExtendedOopClosure* cl,
                                      CardTableModRefBS::PrecisionStyle precision,
                                      HeapWord* boundary,
                                      bool parallel) {
  return new FreeListSpaceDCTOC(this, _collector, cl, precision, boundary, parallel);
}


// Note on locking for the space iteration functions:
// since the collector's iteration activities are concurrent with
// allocation activities by mutators, absent a suitable mutual exclusion
// mechanism the iterators may go awry. For instance a block being iterated
// may suddenly be allocated or divided up and part of it allocated and
// so on.

// Apply the given closure to each block in the space.
void CompactibleFreeListSpace::blk_iterate_careful(BlkClosureCareful* cl) {
  assert_lock_strong(freelistLock());
  HeapWord *cur, *limit;
  for (cur = bottom(), limit = end(); cur < limit;
       cur += cl->do_blk_careful(cur));
}

// Apply the given closure to each block in the space.
void CompactibleFreeListSpace::blk_iterate(BlkClosure* cl) {
  assert_lock_strong(freelistLock());
  HeapWord *cur, *limit;
  for (cur = bottom(), limit = end(); cur < limit;
       cur += cl->do_blk(cur));
}

// Apply the given closure to each oop in the space.
void CompactibleFreeListSpace::oop_iterate(ExtendedOopClosure* cl) {
  assert_lock_strong(freelistLock());
  HeapWord *cur, *limit;
  size_t curSize;
  for (cur = bottom(), limit = end(); cur < limit;
       cur += curSize) {
    curSize = block_size(cur);
    if (block_is_obj(cur)) {
      oop(cur)->oop_iterate(cl);
    }
  }
}

// NOTE: In the following methods, in order to safely be able to
// apply the closure to an object, we need to be sure that the
// object has been initialized. We are guaranteed that an object
// is initialized if we are holding the Heap_lock with the
// world stopped.
void CompactibleFreeListSpace::verify_objects_initialized() const {
  if (is_init_completed()) {
    assert_locked_or_safepoint(Heap_lock);
    if (Universe::is_fully_initialized()) {
      guarantee(SafepointSynchronize::is_at_safepoint(),
                "Required for objects to be initialized");
    }
  } // else make a concession at vm start-up
}

// Apply the given closure to each object in the space
void CompactibleFreeListSpace::object_iterate(ObjectClosure* blk) {
  assert_lock_strong(freelistLock());
  NOT_PRODUCT(verify_objects_initialized());
  HeapWord *cur, *limit;
  size_t curSize;
  for (cur = bottom(), limit = end(); cur < limit;
       cur += curSize) {
    curSize = block_size(cur);
    if (block_is_obj(cur)) {
      blk->do_object(oop(cur));
    }
  }
}

// Apply the given closure to each live object in the space
//   The usage of CompactibleFreeListSpace
// by the ConcurrentMarkSweepGeneration for concurrent GC's allows
// objects in the space with references to objects that are no longer
// valid.  For example, an object may reference another object
// that has already been sweep up (collected).  This method uses
// obj_is_alive() to determine whether it is safe to apply the closure to
// an object.  See obj_is_alive() for details on how liveness of an
// object is decided.

void CompactibleFreeListSpace::safe_object_iterate(ObjectClosure* blk) {
  assert_lock_strong(freelistLock());
  NOT_PRODUCT(verify_objects_initialized());
  HeapWord *cur, *limit;
  size_t curSize;
  for (cur = bottom(), limit = end(); cur < limit;
       cur += curSize) {
    curSize = block_size(cur);
    if (block_is_obj(cur) && obj_is_alive(cur)) {
      blk->do_object(oop(cur));
    }
  }
}

void CompactibleFreeListSpace::object_iterate_mem(MemRegion mr,
                                                  UpwardsObjectClosure* cl) {
  assert_locked(freelistLock());
  NOT_PRODUCT(verify_objects_initialized());
  assert(!mr.is_empty(), "Should be non-empty");
  // We use MemRegion(bottom(), end()) rather than used_region() below
  // because the two are not necessarily equal for some kinds of
  // spaces, in particular, certain kinds of free list spaces.
  // We could use the more complicated but more precise:
  // MemRegion(used_region().start(), round_to(used_region().end(), CardSize))
  // but the slight imprecision seems acceptable in the assertion check.
  assert(MemRegion(bottom(), end()).contains(mr),
         "Should be within used space");
  HeapWord* prev = cl->previous();   // max address from last time
  if (prev >= mr.end()) { // nothing to do
    return;
  }
  // This assert will not work when we go from cms space to perm
  // space, and use same closure. Easy fix deferred for later. XXX YSR
  // assert(prev == NULL || contains(prev), "Should be within space");

  bool last_was_obj_array = false;
  HeapWord *blk_start_addr, *region_start_addr;
  if (prev > mr.start()) {
    region_start_addr = prev;
    blk_start_addr    = prev;
    // The previous invocation may have pushed "prev" beyond the
    // last allocated block yet there may be still be blocks
    // in this region due to a particular coalescing policy.
    // Relax the assertion so that the case where the unallocated
    // block is maintained and "prev" is beyond the unallocated
    // block does not cause the assertion to fire.
    assert((BlockOffsetArrayUseUnallocatedBlock &&
            (!is_in(prev))) ||
           (blk_start_addr == block_start(region_start_addr)), "invariant");
  } else {
    region_start_addr = mr.start();
    blk_start_addr    = block_start(region_start_addr);
  }
  HeapWord* region_end_addr = mr.end();
  MemRegion derived_mr(region_start_addr, region_end_addr);
  while (blk_start_addr < region_end_addr) {
    const size_t size = block_size(blk_start_addr);
    if (block_is_obj(blk_start_addr)) {
      last_was_obj_array = cl->do_object_bm(oop(blk_start_addr), derived_mr);
    } else {
      last_was_obj_array = false;
    }
    blk_start_addr += size;
  }
  if (!last_was_obj_array) {
    assert((bottom() <= blk_start_addr) && (blk_start_addr <= end()),
           "Should be within (closed) used space");
    assert(blk_start_addr > prev, "Invariant");
    cl->set_previous(blk_start_addr); // min address for next time
  }
}

// Callers of this iterator beware: The closure application should
// be robust in the face of uninitialized objects and should (always)
// return a correct size so that the next addr + size below gives us a
// valid block boundary. [See for instance,
// ScanMarkedObjectsAgainCarefullyClosure::do_object_careful()
// in ConcurrentMarkSweepGeneration.cpp.]
HeapWord*
CompactibleFreeListSpace::object_iterate_careful_m(MemRegion mr,
  ObjectClosureCareful* cl) {
  assert_lock_strong(freelistLock());
  // Can't use used_region() below because it may not necessarily
  // be the same as [bottom(),end()); although we could
  // use [used_region().start(),round_to(used_region().end(),CardSize)),
  // that appears too cumbersome, so we just do the simpler check
  // in the assertion below.
  assert(!mr.is_empty() && MemRegion(bottom(),end()).contains(mr),
         "mr should be non-empty and within used space");
  HeapWord *addr, *end;
  size_t size;
  for (addr = block_start_careful(mr.start()), end  = mr.end();
       addr < end; addr += size) {
    FreeChunk* fc = (FreeChunk*)addr;
    if (fc->is_free()) {
      // Since we hold the free list lock, which protects direct
      // allocation in this generation by mutators, a free object
      // will remain free throughout this iteration code.
      size = fc->size();
    } else {
      // Note that the object need not necessarily be initialized,
      // because (for instance) the free list lock does NOT protect
      // object initialization. The closure application below must
      // therefore be correct in the face of uninitialized objects.
      size = cl->do_object_careful_m(oop(addr), mr);
      if (size == 0) {
        // An unparsable object found. Signal early termination.
        return addr;
      }
    }
  }
  return NULL;
}


HeapWord* CompactibleFreeListSpace::block_start_const(const void* p) const {
  NOT_PRODUCT(verify_objects_initialized());
  return _bt.block_start(p);
}

HeapWord* CompactibleFreeListSpace::block_start_careful(const void* p) const {
  return _bt.block_start_careful(p);
}

size_t CompactibleFreeListSpace::block_size(const HeapWord* p) const {
  NOT_PRODUCT(verify_objects_initialized());
  // This must be volatile, or else there is a danger that the compiler
  // will compile the code below into a sometimes-infinite loop, by keeping
  // the value read the first time in a register.
  while (true) {
    // We must do this until we get a consistent view of the object.
    if (FreeChunk::indicatesFreeChunk(p)) {
      volatile FreeChunk* fc = (volatile FreeChunk*)p;
      size_t res = fc->size();

      // Bugfix for systems with weak memory model (PPC64/IA64). The
      // block's free bit was set and we have read the size of the
      // block. Acquire and check the free bit again. If the block is
      // still free, the read size is correct.
      OrderAccess::acquire();

      // If the object is still a free chunk, return the size, else it
      // has been allocated so try again.
      if (FreeChunk::indicatesFreeChunk(p)) {
        assert(res != 0, "Block size should not be 0");
        return res;
      }
    } else {
      // must read from what 'p' points to in each loop.
      Klass* k = ((volatile oopDesc*)p)->klass_or_null();
      if (k != NULL) {
        assert(k->is_klass(), "Should really be klass oop.");
        oop o = (oop)p;
        assert(o->is_oop(true /* ignore mark word */), "Should be an oop.");

        // Bugfix for systems with weak memory model (PPC64/IA64).
        // The object o may be an array. Acquire to make sure that the array
        // size (third word) is consistent.
        OrderAccess::acquire();

        size_t res = o->size_given_klass(k);
        res = adjustObjectSize(res);
        assert(res != 0, "Block size should not be 0");
        return res;
      }
    }
  }
}

// TODO: Now that is_parsable is gone, we should combine these two functions.
// A variant of the above that uses the Printezis bits for
// unparsable but allocated objects. This avoids any possible
// stalls waiting for mutators to initialize objects, and is
// thus potentially faster than the variant above. However,
// this variant may return a zero size for a block that is
// under mutation and for which a consistent size cannot be
// inferred without stalling; see CMSCollector::block_size_if_printezis_bits().
size_t CompactibleFreeListSpace::block_size_no_stall(HeapWord* p,
                                                     const CMSCollector* c)
const {
  assert(MemRegion(bottom(), end()).contains(p), "p not in space");
  // This must be volatile, or else there is a danger that the compiler
  // will compile the code below into a sometimes-infinite loop, by keeping
  // the value read the first time in a register.
  DEBUG_ONLY(uint loops = 0;)
  while (true) {
    // We must do this until we get a consistent view of the object.
    if (FreeChunk::indicatesFreeChunk(p)) {
      volatile FreeChunk* fc = (volatile FreeChunk*)p;
      size_t res = fc->size();

      // Bugfix for systems with weak memory model (PPC64/IA64). The
      // free bit of the block was set and we have read the size of
      // the block. Acquire and check the free bit again. If the
      // block is still free, the read size is correct.
      OrderAccess::acquire();

      if (FreeChunk::indicatesFreeChunk(p)) {
        assert(res != 0, "Block size should not be 0");
        assert(loops == 0, "Should be 0");
        return res;
      }
    } else {
      // must read from what 'p' points to in each loop.
      Klass* k = ((volatile oopDesc*)p)->klass_or_null();
      // We trust the size of any object that has a non-NULL
      // klass and (for those in the perm gen) is parsable
      // -- irrespective of its conc_safe-ty.
      if (k != NULL) {
        assert(k->is_klass(), "Should really be klass oop.");
        oop o = (oop)p;
        assert(o->is_oop(), "Should be an oop");

        // Bugfix for systems with weak memory model (PPC64/IA64).
        // The object o may be an array. Acquire to make sure that the array
        // size (third word) is consistent.
        OrderAccess::acquire();

        size_t res = o->size_given_klass(k);
        res = adjustObjectSize(res);
        assert(res != 0, "Block size should not be 0");
        return res;
      } else {
        // May return 0 if P-bits not present.
        return c->block_size_if_printezis_bits(p);
      }
    }
    assert(loops == 0, "Can loop at most once");
    DEBUG_ONLY(loops++;)
  }
}

size_t CompactibleFreeListSpace::block_size_nopar(const HeapWord* p) const {
  NOT_PRODUCT(verify_objects_initialized());
  assert(MemRegion(bottom(), end()).contains(p), "p not in space");
  FreeChunk* fc = (FreeChunk*)p;
  if (fc->is_free()) {
    return fc->size();
  } else {
    // Ignore mark word because this may be a recently promoted
    // object whose mark word is used to chain together grey
    // objects (the last one would have a null value).
    assert(oop(p)->is_oop(true), "Should be an oop");
    return adjustObjectSize(oop(p)->size());
  }
}

// This implementation assumes that the property of "being an object" is
// stable.  But being a free chunk may not be (because of parallel
// promotion.)
bool CompactibleFreeListSpace::block_is_obj(const HeapWord* p) const {
  FreeChunk* fc = (FreeChunk*)p;
  assert(is_in_reserved(p), "Should be in space");
  if (FreeChunk::indicatesFreeChunk(p)) return false;
  Klass* k = oop(p)->klass_or_null();
  if (k != NULL) {
    // Ignore mark word because it may have been used to
    // chain together promoted objects (the last one
    // would have a null value).
    assert(oop(p)->is_oop(true), "Should be an oop");
    return true;
  } else {
    return false;  // Was not an object at the start of collection.
  }
}

// Check if the object is alive. This fact is checked either by consulting
// the main marking bitmap in the sweeping phase or, if it's a permanent
// generation and we're not in the sweeping phase, by checking the
// perm_gen_verify_bit_map where we store the "deadness" information if
// we did not sweep the perm gen in the most recent previous GC cycle.
bool CompactibleFreeListSpace::obj_is_alive(const HeapWord* p) const {
  assert(SafepointSynchronize::is_at_safepoint() || !is_init_completed(),
         "Else races are possible");
  assert(block_is_obj(p), "The address should point to an object");

  // If we're sweeping, we use object liveness information from the main bit map
  // for both perm gen and old gen.
  // We don't need to lock the bitmap (live_map or dead_map below), because
  // EITHER we are in the middle of the sweeping phase, and the
  // main marking bit map (live_map below) is locked,
  // OR we're in other phases and perm_gen_verify_bit_map (dead_map below)
  // is stable, because it's mutated only in the sweeping phase.
  // NOTE: This method is also used by jmap where, if class unloading is
  // off, the results can return "false" for legitimate perm objects,
  // when we are not in the midst of a sweeping phase, which can result
  // in jmap not reporting certain perm gen objects. This will be moot
  // if/when the perm gen goes away in the future.
  if (_collector->abstract_state() == CMSCollector::Sweeping) {
    CMSBitMap* live_map = _collector->markBitMap();
    return live_map->par_isMarked((HeapWord*) p);
  }
  return true;
}

bool CompactibleFreeListSpace::block_is_obj_nopar(const HeapWord* p) const {
  FreeChunk* fc = (FreeChunk*)p;
  assert(is_in_reserved(p), "Should be in space");
  assert(_bt.block_start(p) == p, "Should be a block boundary");
  if (!fc->is_free()) {
    // Ignore mark word because it may have been used to
    // chain together promoted objects (the last one
    // would have a null value).
    assert(oop(p)->is_oop(true), "Should be an oop");
    return true;
  }
  return false;
}

// "MT-safe but not guaranteed MT-precise" (TM); you may get an
// approximate answer if you don't hold the freelistlock when you call this.
size_t CompactibleFreeListSpace::totalSizeInIndexedFreeLists() const {
  size_t size = 0;
  for (size_t i = IndexSetStart; i < IndexSetSize; i += IndexSetStride) {
    debug_only(
      // We may be calling here without the lock in which case we
      // won't do this modest sanity check.
      if (freelistLock()->owned_by_self()) {
        size_t total_list_size = 0;
        for (FreeChunk* fc = _indexedFreeList[i].head(); fc != NULL;
          fc = fc->next()) {
          total_list_size += i;
        }
        assert(total_list_size == i * _indexedFreeList[i].count(),
               "Count in list is incorrect");
      }
    )
    size += i * _indexedFreeList[i].count();
  }
  return size;
}

HeapWord* CompactibleFreeListSpace::par_allocate(size_t size) {
  MutexLockerEx x(freelistLock(), Mutex::_no_safepoint_check_flag);
  return allocate(size);
}

HeapWord*
CompactibleFreeListSpace::getChunkFromSmallLinearAllocBlockRemainder(size_t size) {
  return getChunkFromLinearAllocBlockRemainder(&_smallLinearAllocBlock, size);
}

HeapWord* CompactibleFreeListSpace::allocate(size_t size) {
  assert_lock_strong(freelistLock());
  HeapWord* res = NULL;
  assert(size == adjustObjectSize(size),
         "use adjustObjectSize() before calling into allocate()");

  res = allocate_adaptive_freelists(size);

  if (res != NULL) {
    // check that res does lie in this space!
    assert(is_in_reserved(res), "Not in this space!");
    assert(is_aligned((void*)res), "alignment check");

    FreeChunk* fc = (FreeChunk*)res;
    fc->markNotFree();
    assert(!fc->is_free(), "shouldn't be marked free");
    assert(oop(fc)->klass_or_null() == NULL, "should look uninitialized");
    // Verify that the block offset table shows this to
    // be a single block, but not one which is unallocated.
    _bt.verify_single_block(res, size);
    _bt.verify_not_unallocated(res, size);
    // mangle a just allocated object with a distinct pattern.
    debug_only(fc->mangleAllocated(size));
  }

  return res;
}

HeapWord* CompactibleFreeListSpace::allocate_adaptive_freelists(size_t size) {
  assert_lock_strong(freelistLock());
  HeapWord* res = NULL;
  assert(size == adjustObjectSize(size),
         "use adjustObjectSize() before calling into allocate()");

  // Strategy
  //   if small
  //     exact size from small object indexed list if small
  //     small or large linear allocation block (linAB) as appropriate
  //     take from lists of greater sized chunks
  //   else
  //     dictionary
  //     small or large linear allocation block if it has the space
  // Try allocating exact size from indexTable first
  if (size < IndexSetSize) {
    res = (HeapWord*) getChunkFromIndexedFreeList(size);
    if(res != NULL) {
      assert(res != (HeapWord*)_indexedFreeList[size].head(),
        "Not removed from free list");
      // no block offset table adjustment is necessary on blocks in
      // the indexed lists.

    // Try allocating from the small LinAB
    } else if (size < _smallLinearAllocBlock._allocation_size_limit &&
        (res = getChunkFromSmallLinearAllocBlock(size)) != NULL) {
        // if successful, the above also adjusts block offset table
        // Note that this call will refill the LinAB to
        // satisfy the request.  This is different that
        // evm.
        // Don't record chunk off a LinAB?  smallSplitBirth(size);
    } else {
      // Raid the exact free lists larger than size, even if they are not
      // overpopulated.
      res = (HeapWord*) getChunkFromGreater(size);
    }
  } else {
    // Big objects get allocated directly from the dictionary.
    res = (HeapWord*) getChunkFromDictionaryExact(size);
    if (res == NULL) {
      // Try hard not to fail since an allocation failure will likely
      // trigger a synchronous GC.  Try to get the space from the
      // allocation blocks.
      res = getChunkFromSmallLinearAllocBlockRemainder(size);
    }
  }

  return res;
}

// A worst-case estimate of the space required (in HeapWords) to expand the heap
// when promoting obj.
size_t CompactibleFreeListSpace::expansionSpaceRequired(size_t obj_size) const {
  // Depending on the object size, expansion may require refilling either a
  // bigLAB or a smallLAB plus refilling a PromotionInfo object.  MinChunkSize
  // is added because the dictionary may over-allocate to avoid fragmentation.
  size_t space = obj_size;
  space += _promoInfo.refillSize() + 2 * MinChunkSize;
  return space;
}

FreeChunk* CompactibleFreeListSpace::getChunkFromGreater(size_t numWords) {
  FreeChunk* ret;

  assert(numWords >= MinChunkSize, "Size is less than minimum");
  assert(linearAllocationWouldFail() || bestFitFirst(),
    "Should not be here");

  size_t i;
  size_t currSize = numWords + MinChunkSize;
  assert(currSize % MinObjAlignment == 0, "currSize should be aligned");
  for (i = currSize; i < IndexSetSize; i += IndexSetStride) {
    AdaptiveFreeList<FreeChunk>* fl = &_indexedFreeList[i];
    if (fl->head()) {
      ret = getFromListGreater(fl, numWords);
      assert(ret == NULL || ret->is_free(), "Should be returning a free chunk");
      return ret;
    }
  }

  currSize = MAX2((size_t)SmallForDictionary,
                  (size_t)(numWords + MinChunkSize));

  /* Try to get a chunk that satisfies request, while avoiding
     fragmentation that can't be handled. */
  {
    ret =  dictionary()->get_chunk(currSize);
    if (ret != NULL) {
      assert(ret->size() - numWords >= MinChunkSize,
             "Chunk is too small");
      _bt.allocated((HeapWord*)ret, ret->size());
      /* Carve returned chunk. */
      (void) splitChunkAndReturnRemainder(ret, numWords);
      /* Label this as no longer a free chunk. */
      assert(ret->is_free(), "This chunk should be free");
      ret->link_prev(NULL);
    }
    assert(ret == NULL || ret->is_free(), "Should be returning a free chunk");
    return ret;
  }
  ShouldNotReachHere();
}

bool CompactibleFreeListSpace::verifyChunkInIndexedFreeLists(FreeChunk* fc) const {
  assert(fc->size() < IndexSetSize, "Size of chunk is too large");
  return _indexedFreeList[fc->size()].verify_chunk_in_free_list(fc);
}

bool CompactibleFreeListSpace::verify_chunk_is_linear_alloc_block(FreeChunk* fc) const {
  assert((_smallLinearAllocBlock._ptr != (HeapWord*)fc) ||
         (_smallLinearAllocBlock._word_size == fc->size()),
         "Linear allocation block shows incorrect size");
  return ((_smallLinearAllocBlock._ptr == (HeapWord*)fc) &&
          (_smallLinearAllocBlock._word_size == fc->size()));
}

// Check if the purported free chunk is present either as a linear
// allocation block, the size-indexed table of (smaller) free blocks,
// or the larger free blocks kept in the binary tree dictionary.
bool CompactibleFreeListSpace::verify_chunk_in_free_list(FreeChunk* fc) const {
  if (verify_chunk_is_linear_alloc_block(fc)) {
    return true;
  } else if (fc->size() < IndexSetSize) {
    return verifyChunkInIndexedFreeLists(fc);
  } else {
    return dictionary()->verify_chunk_in_free_list(fc);
  }
}

#ifndef PRODUCT
void CompactibleFreeListSpace::assert_locked() const {
  CMSLockVerifier::assert_locked(freelistLock(), parDictionaryAllocLock());
}

void CompactibleFreeListSpace::assert_locked(const Mutex* lock) const {
  CMSLockVerifier::assert_locked(lock);
}
#endif

FreeChunk* CompactibleFreeListSpace::allocateScratch(size_t size) {
  // In the parallel case, the main thread holds the free list lock
  // on behalf the parallel threads.
  FreeChunk* fc;
  {
    // If GC is parallel, this might be called by several threads.
    // This should be rare enough that the locking overhead won't affect
    // the sequential code.
    MutexLockerEx x(parDictionaryAllocLock(),
                    Mutex::_no_safepoint_check_flag);
    fc = getChunkFromDictionary(size);
  }
  if (fc != NULL) {
    fc->dontCoalesce();
    assert(fc->is_free(), "Should be free, but not coalescable");
    // Verify that the block offset table shows this to
    // be a single block, but not one which is unallocated.
    _bt.verify_single_block((HeapWord*)fc, fc->size());
    _bt.verify_not_unallocated((HeapWord*)fc, fc->size());
  }
  return fc;
}

oop CompactibleFreeListSpace::promote(oop obj, size_t obj_size) {
  assert(obj_size == (size_t)obj->size(), "bad obj_size passed in");
  assert_locked();

  // if we are tracking promotions, then first ensure space for
  // promotion (including spooling space for saving header if necessary).
  // then allocate and copy, then track promoted info if needed.
  // When tracking (see PromotionInfo::track()), the mark word may
  // be displaced and in this case restoration of the mark word
  // occurs in the (oop_since_save_marks_)iterate phase.
  if (_promoInfo.tracking() && !_promoInfo.ensure_spooling_space()) {
    return NULL;
  }
  // Call the allocate(size_t, bool) form directly to avoid the
  // additional call through the allocate(size_t) form.  Having
  // the compile inline the call is problematic because allocate(size_t)
  // is a virtual method.
  HeapWord* res = allocate(adjustObjectSize(obj_size));
  if (res != NULL) {
    Copy::aligned_disjoint_words((HeapWord*)obj, res, obj_size);
    // if we should be tracking promotions, do so.
    if (_promoInfo.tracking()) {
        _promoInfo.track((PromotedObject*)res);
    }
  }
  return oop(res);
}

HeapWord*
CompactibleFreeListSpace::getChunkFromSmallLinearAllocBlock(size_t size) {
  assert_locked();
  assert(size >= MinChunkSize, "minimum chunk size");
  assert(size <  _smallLinearAllocBlock._allocation_size_limit,
    "maximum from smallLinearAllocBlock");
  return getChunkFromLinearAllocBlock(&_smallLinearAllocBlock, size);
}

HeapWord*
CompactibleFreeListSpace::getChunkFromLinearAllocBlock(LinearAllocBlock *blk,
                                                       size_t size) {
  assert_locked();
  assert(size >= MinChunkSize, "too small");
  HeapWord* res = NULL;
  // Try to do linear allocation from blk, making sure that
  if (blk->_word_size == 0) {
    // We have probably been unable to fill this either in the prologue or
    // when it was exhausted at the last linear allocation. Bail out until
    // next time.
    assert(blk->_ptr == NULL, "consistency check");
    return NULL;
  }
  assert(blk->_word_size != 0 && blk->_ptr != NULL, "consistency check");
  res = getChunkFromLinearAllocBlockRemainder(blk, size);
  if (res != NULL) return res;

  // about to exhaust this linear allocation block
  if (blk->_word_size == size) { // exactly satisfied
    res = blk->_ptr;
    _bt.allocated(res, blk->_word_size);
  } else if (size + MinChunkSize <= blk->_refillSize) {
    size_t sz = blk->_word_size;
    // Update _unallocated_block if the size is such that chunk would be
    // returned to the indexed free list.  All other chunks in the indexed
    // free lists are allocated from the dictionary so that _unallocated_block
    // has already been adjusted for them.  Do it here so that the cost
    // for all chunks added back to the indexed free lists.
    if (sz < SmallForDictionary) {
      _bt.allocated(blk->_ptr, sz);
    }
    // Return the chunk that isn't big enough, and then refill below.
    addChunkToFreeLists(blk->_ptr, sz);
    split_birth(sz);
    // Don't keep statistics on adding back chunk from a LinAB.
  } else {
    // A refilled block would not satisfy the request.
    return NULL;
  }

  blk->_ptr = NULL; blk->_word_size = 0;
  refillLinearAllocBlock(blk);
  assert(blk->_ptr == NULL || blk->_word_size >= size + MinChunkSize,
         "block was replenished");
  if (res != NULL) {
    split_birth(size);
    repairLinearAllocBlock(blk);
  } else if (blk->_ptr != NULL) {
    res = blk->_ptr;
    size_t blk_size = blk->_word_size;
    blk->_word_size -= size;
    blk->_ptr  += size;
    split_birth(size);
    repairLinearAllocBlock(blk);
    // Update BOT last so that other (parallel) GC threads see a consistent
    // view of the BOT and free blocks.
    // Above must occur before BOT is updated below.
    OrderAccess::storestore();
    _bt.split_block(res, blk_size, size);  // adjust block offset table
  }
  return res;
}

HeapWord*  CompactibleFreeListSpace::getChunkFromLinearAllocBlockRemainder(
                                        LinearAllocBlock* blk,
                                        size_t size) {
  assert_locked();
  assert(size >= MinChunkSize, "too small");

  HeapWord* res = NULL;
  // This is the common case.  Keep it simple.
  if (blk->_word_size >= size + MinChunkSize) {
    assert(blk->_ptr != NULL, "consistency check");
    res = blk->_ptr;
    // Note that the BOT is up-to-date for the linAB before allocation.  It
    // indicates the start of the linAB.  The split_block() updates the
    // BOT for the linAB after the allocation (indicates the start of the
    // next chunk to be allocated).
    size_t blk_size = blk->_word_size;
    blk->_word_size -= size;
    blk->_ptr  += size;
    split_birth(size);
    repairLinearAllocBlock(blk);
    // Update BOT last so that other (parallel) GC threads see a consistent
    // view of the BOT and free blocks.
    // Above must occur before BOT is updated below.
    OrderAccess::storestore();
    _bt.split_block(res, blk_size, size);  // adjust block offset table
    _bt.allocated(res, size);
  }
  return res;
}

FreeChunk*
CompactibleFreeListSpace::getChunkFromIndexedFreeList(size_t size) {
  assert_locked();
  assert(size < SmallForDictionary, "just checking");
  FreeChunk* res;
  res = _indexedFreeList[size].get_chunk_at_head();
  if (res == NULL) {
    res = getChunkFromIndexedFreeListHelper(size);
  }
  _bt.verify_not_unallocated((HeapWord*) res, size);
  assert(res == NULL || res->size() == size, "Incorrect block size");
  return res;
}

FreeChunk*
CompactibleFreeListSpace::getChunkFromIndexedFreeListHelper(size_t size,
  bool replenish) {
  assert_locked();
  FreeChunk* fc = NULL;
  if (size < SmallForDictionary) {
    assert(_indexedFreeList[size].head() == NULL ||
      _indexedFreeList[size].surplus() <= 0,
      "List for this size should be empty or under populated");
    // Try best fit in exact lists before replenishing the list
    if (!bestFitFirst() || (fc = bestFitSmall(size)) == NULL) {
      // Replenish list.
      //
      // Things tried that failed.
      //   Tried allocating out of the two LinAB's first before
      // replenishing lists.
      //   Tried small linAB of size 256 (size in indexed list)
      // and replenishing indexed lists from the small linAB.
      //
      FreeChunk* newFc = NULL;
      const size_t replenish_size = CMSIndexedFreeListReplenish * size;
      if (replenish_size < SmallForDictionary) {
        // Do not replenish from an underpopulated size.
        if (_indexedFreeList[replenish_size].surplus() > 0 &&
            _indexedFreeList[replenish_size].head() != NULL) {
          newFc = _indexedFreeList[replenish_size].get_chunk_at_head();
        } else if (bestFitFirst()) {
          newFc = bestFitSmall(replenish_size);
        }
      }
      if (newFc == NULL && replenish_size > size) {
        assert(CMSIndexedFreeListReplenish > 1, "ctl pt invariant");
        newFc = getChunkFromIndexedFreeListHelper(replenish_size, false);
      }
      // Note: The stats update re split-death of block obtained above
      // will be recorded below precisely when we know we are going to
      // be actually splitting it into more than one pieces below.
      if (newFc != NULL) {
        if  (replenish || CMSReplenishIntermediate) {
          // Replenish this list and return one block to caller.
          size_t i;
          FreeChunk *curFc, *nextFc;
          size_t num_blk = newFc->size() / size;
          assert(num_blk >= 1, "Smaller than requested?");
          assert(newFc->size() % size == 0, "Should be integral multiple of request");
          if (num_blk > 1) {
            // we are sure we will be splitting the block just obtained
            // into multiple pieces; record the split-death of the original
            splitDeath(replenish_size);
          }
          // carve up and link blocks 0, ..., num_blk - 2
          // The last chunk is not added to the lists but is returned as the
          // free chunk.
          for (curFc = newFc, nextFc = (FreeChunk*)((HeapWord*)curFc + size),
               i = 0;
               i < (num_blk - 1);
               curFc = nextFc, nextFc = (FreeChunk*)((HeapWord*)nextFc + size),
               i++) {
            curFc->set_size(size);
            // Don't record this as a return in order to try and
            // determine the "returns" from a GC.
            _bt.verify_not_unallocated((HeapWord*) fc, size);
            _indexedFreeList[size].return_chunk_at_tail(curFc, false);
            _bt.mark_block((HeapWord*)curFc, size);
            split_birth(size);
            // Don't record the initial population of the indexed list
            // as a split birth.
          }

          // check that the arithmetic was OK above
          assert((HeapWord*)nextFc == (HeapWord*)newFc + num_blk*size,
            "inconsistency in carving newFc");
          curFc->set_size(size);
          _bt.mark_block((HeapWord*)curFc, size);
          split_birth(size);
          fc = curFc;
        } else {
          // Return entire block to caller
          fc = newFc;
        }
      }
    }
  } else {
    // Get a free chunk from the free chunk dictionary to be returned to
    // replenish the indexed free list.
    fc = getChunkFromDictionaryExact(size);
  }
  // assert(fc == NULL || fc->is_free(), "Should be returning a free chunk");
  return fc;
}

FreeChunk*
CompactibleFreeListSpace::getChunkFromDictionary(size_t size) {
  assert_locked();
  FreeChunk* fc = _dictionary->get_chunk(size,
                                         FreeBlockDictionary<FreeChunk>::atLeast);
  if (fc == NULL) {
    return NULL;
  }
  _bt.allocated((HeapWord*)fc, fc->size());
  if (fc->size() >= size + MinChunkSize) {
    fc = splitChunkAndReturnRemainder(fc, size);
  }
  assert(fc->size() >= size, "chunk too small");
  assert(fc->size() < size + MinChunkSize, "chunk too big");
  _bt.verify_single_block((HeapWord*)fc, fc->size());
  return fc;
}

FreeChunk*
CompactibleFreeListSpace::getChunkFromDictionaryExact(size_t size) {
  assert_locked();
  FreeChunk* fc = _dictionary->get_chunk(size,
                                         FreeBlockDictionary<FreeChunk>::atLeast);
  if (fc == NULL) {
    return fc;
  }
  _bt.allocated((HeapWord*)fc, fc->size());
  if (fc->size() == size) {
    _bt.verify_single_block((HeapWord*)fc, size);
    return fc;
  }
  assert(fc->size() > size, "get_chunk() guarantee");
  if (fc->size() < size + MinChunkSize) {
    // Return the chunk to the dictionary and go get a bigger one.
    returnChunkToDictionary(fc);
    fc = _dictionary->get_chunk(size + MinChunkSize,
                                FreeBlockDictionary<FreeChunk>::atLeast);
    if (fc == NULL) {
      return NULL;
    }
    _bt.allocated((HeapWord*)fc, fc->size());
  }
  assert(fc->size() >= size + MinChunkSize, "tautology");
  fc = splitChunkAndReturnRemainder(fc, size);
  assert(fc->size() == size, "chunk is wrong size");
  _bt.verify_single_block((HeapWord*)fc, size);
  return fc;
}

void
CompactibleFreeListSpace::returnChunkToDictionary(FreeChunk* chunk) {
  assert_locked();

  size_t size = chunk->size();
  _bt.verify_single_block((HeapWord*)chunk, size);
  // adjust _unallocated_block downward, as necessary
  _bt.freed((HeapWord*)chunk, size);
  _dictionary->return_chunk(chunk);
#ifndef PRODUCT
  if (CMSCollector::abstract_state() != CMSCollector::Sweeping) {
    TreeChunk<FreeChunk, AdaptiveFreeList<FreeChunk> >* tc = TreeChunk<FreeChunk, AdaptiveFreeList<FreeChunk> >::as_TreeChunk(chunk);
    TreeList<FreeChunk, AdaptiveFreeList<FreeChunk> >* tl = tc->list();
    tl->verify_stats();
  }
#endif // PRODUCT
}

void
CompactibleFreeListSpace::returnChunkToFreeList(FreeChunk* fc) {
  assert_locked();
  size_t size = fc->size();
  _bt.verify_single_block((HeapWord*) fc, size);
  _bt.verify_not_unallocated((HeapWord*) fc, size);
  _indexedFreeList[size].return_chunk_at_tail(fc);
#ifndef PRODUCT
  if (CMSCollector::abstract_state() != CMSCollector::Sweeping) {
     _indexedFreeList[size].verify_stats();
  }
#endif // PRODUCT
}

// Add chunk to end of last block -- if it's the largest
// block -- and update BOT and census data. We would
// of course have preferred to coalesce it with the
// last block, but it's currently less expensive to find the
// largest block than it is to find the last.
void
CompactibleFreeListSpace::addChunkToFreeListsAtEndRecordingStats(
  HeapWord* chunk, size_t     size) {
  // check that the chunk does lie in this space!
  assert(chunk != NULL && is_in_reserved(chunk), "Not in this space!");
  // One of the parallel gc task threads may be here
  // whilst others are allocating.
  Mutex* lock = &_parDictionaryAllocLock;
  FreeChunk* ec;
  {
    MutexLockerEx x(lock, Mutex::_no_safepoint_check_flag);
    ec = dictionary()->find_largest_dict();  // get largest block
    if (ec != NULL && ec->end() == (uintptr_t*) chunk) {
      // It's a coterminal block - we can coalesce.
      size_t old_size = ec->size();
      coalDeath(old_size);
      removeChunkFromDictionary(ec);
      size += old_size;
    } else {
      ec = (FreeChunk*)chunk;
    }
  }
  ec->set_size(size);
  debug_only(ec->mangleFreed(size));
  if (size < SmallForDictionary) {
    lock = _indexedFreeListParLocks[size];
  }
  MutexLockerEx x(lock, Mutex::_no_safepoint_check_flag);
  addChunkAndRepairOffsetTable((HeapWord*)ec, size, true);
  // record the birth under the lock since the recording involves
  // manipulation of the list on which the chunk lives and
  // if the chunk is allocated and is the last on the list,
  // the list can go away.
  coalBirth(size);
}

void
CompactibleFreeListSpace::addChunkToFreeLists(HeapWord* chunk,
                                              size_t     size) {
  // check that the chunk does lie in this space!
  assert(chunk != NULL && is_in_reserved(chunk), "Not in this space!");
  assert_locked();
  _bt.verify_single_block(chunk, size);

  FreeChunk* fc = (FreeChunk*) chunk;
  fc->set_size(size);
  debug_only(fc->mangleFreed(size));
  if (size < SmallForDictionary) {
    returnChunkToFreeList(fc);
  } else {
    returnChunkToDictionary(fc);
  }
}

void
CompactibleFreeListSpace::addChunkAndRepairOffsetTable(HeapWord* chunk,
  size_t size, bool coalesced) {
  assert_locked();
  assert(chunk != NULL, "null chunk");
  if (coalesced) {
    // repair BOT
    _bt.single_block(chunk, size);
  }
  addChunkToFreeLists(chunk, size);
}

// We _must_ find the purported chunk on our free lists;
// we assert if we don't.
void
CompactibleFreeListSpace::removeFreeChunkFromFreeLists(FreeChunk* fc) {
  size_t size = fc->size();
  assert_locked();
  debug_only(verifyFreeLists());
  if (size < SmallForDictionary) {
    removeChunkFromIndexedFreeList(fc);
  } else {
    removeChunkFromDictionary(fc);
  }
  _bt.verify_single_block((HeapWord*)fc, size);
  debug_only(verifyFreeLists());
}

void
CompactibleFreeListSpace::removeChunkFromDictionary(FreeChunk* fc) {
  size_t size = fc->size();
  assert_locked();
  assert(fc != NULL, "null chunk");
  _bt.verify_single_block((HeapWord*)fc, size);
  _dictionary->remove_chunk(fc);
  // adjust _unallocated_block upward, as necessary
  _bt.allocated((HeapWord*)fc, size);
}

void
CompactibleFreeListSpace::removeChunkFromIndexedFreeList(FreeChunk* fc) {
  assert_locked();
  size_t size = fc->size();
  _bt.verify_single_block((HeapWord*)fc, size);
  NOT_PRODUCT(
    if (FLSVerifyIndexTable) {
      verifyIndexedFreeList(size);
    }
  )
  _indexedFreeList[size].remove_chunk(fc);
  NOT_PRODUCT(
    if (FLSVerifyIndexTable) {
      verifyIndexedFreeList(size);
    }
  )
}

FreeChunk* CompactibleFreeListSpace::bestFitSmall(size_t numWords) {
  /* A hint is the next larger size that has a surplus.
     Start search at a size large enough to guarantee that
     the excess is >= MIN_CHUNK. */
  size_t start = align_object_size(numWords + MinChunkSize);
  if (start < IndexSetSize) {
    AdaptiveFreeList<FreeChunk>* it   = _indexedFreeList;
    size_t    hint = _indexedFreeList[start].hint();
    while (hint < IndexSetSize) {
      assert(hint % MinObjAlignment == 0, "hint should be aligned");
      AdaptiveFreeList<FreeChunk> *fl = &_indexedFreeList[hint];
      if (fl->surplus() > 0 && fl->head() != NULL) {
        // Found a list with surplus, reset original hint
        // and split out a free chunk which is returned.
        _indexedFreeList[start].set_hint(hint);
        FreeChunk* res = getFromListGreater(fl, numWords);
        assert(res == NULL || res->is_free(),
          "Should be returning a free chunk");
        return res;
      }
      hint = fl->hint(); /* keep looking */
    }
    /* None found. */
    it[start].set_hint(IndexSetSize);
  }
  return NULL;
}

/* Requires fl->size >= numWords + MinChunkSize */
FreeChunk* CompactibleFreeListSpace::getFromListGreater(AdaptiveFreeList<FreeChunk>* fl,
  size_t numWords) {
  FreeChunk *curr = fl->head();
  size_t oldNumWords = curr->size();
  assert(numWords >= MinChunkSize, "Word size is too small");
  assert(curr != NULL, "List is empty");
  assert(oldNumWords >= numWords + MinChunkSize,
        "Size of chunks in the list is too small");

  fl->remove_chunk(curr);
  // recorded indirectly by splitChunkAndReturnRemainder -
  // smallSplit(oldNumWords, numWords);
  FreeChunk* new_chunk = splitChunkAndReturnRemainder(curr, numWords);
  // Does anything have to be done for the remainder in terms of
  // fixing the card table?
  assert(new_chunk == NULL || new_chunk->is_free(),
    "Should be returning a free chunk");
  return new_chunk;
}

FreeChunk*
CompactibleFreeListSpace::splitChunkAndReturnRemainder(FreeChunk* chunk,
  size_t new_size) {
  assert_locked();
  size_t size = chunk->size();
  assert(size > new_size, "Split from a smaller block?");
  assert(is_aligned(chunk), "alignment problem");
  assert(size == adjustObjectSize(size), "alignment problem");
  size_t rem_sz = size - new_size;
  assert(rem_sz == adjustObjectSize(rem_sz), "alignment problem");
  assert(rem_sz >= MinChunkSize, "Free chunk smaller than minimum");
  FreeChunk* ffc = (FreeChunk*)((HeapWord*)chunk + new_size);
  assert(is_aligned(ffc), "alignment problem");
  ffc->set_size(rem_sz);
  ffc->link_next(NULL);
  ffc->link_prev(NULL); // Mark as a free block for other (parallel) GC threads.
  // Above must occur before BOT is updated below.
  // adjust block offset table
  OrderAccess::storestore();
  assert(chunk->is_free() && ffc->is_free(), "Error");
  _bt.split_block((HeapWord*)chunk, chunk->size(), new_size);
  if (rem_sz < SmallForDictionary) {
    // The freeList lock is held, but multiple GC task threads might be executing in parallel.
    bool is_par = Thread::current()->is_GC_task_thread();
    if (is_par) _indexedFreeListParLocks[rem_sz]->lock();
    returnChunkToFreeList(ffc);
    split(size, rem_sz);
    if (is_par) _indexedFreeListParLocks[rem_sz]->unlock();
  } else {
    returnChunkToDictionary(ffc);
    split(size, rem_sz);
  }
  chunk->set_size(new_size);
  return chunk;
}

void
CompactibleFreeListSpace::sweep_completed() {
  // Now that space is probably plentiful, refill linear
  // allocation blocks as needed.
  refillLinearAllocBlocksIfNeeded();
}

void
CompactibleFreeListSpace::gc_prologue() {
  assert_locked();
  reportFreeListStatistics("Before GC:");
  refillLinearAllocBlocksIfNeeded();
}

void
CompactibleFreeListSpace::gc_epilogue() {
  assert_locked();
  assert(_promoInfo.noPromotions(), "_promoInfo inconsistency");
  _promoInfo.stopTrackingPromotions();
  repairLinearAllocationBlocks();
  reportFreeListStatistics("After GC:");
}

// Iteration support, mostly delegated from a CMS generation

void CompactibleFreeListSpace::save_marks() {
  assert(Thread::current()->is_VM_thread(),
         "Global variable should only be set when single-threaded");
  // Mark the "end" of the used space at the time of this call;
  // note, however, that promoted objects from this point
  // on are tracked in the _promoInfo below.
  set_saved_mark_word(unallocated_block());
#ifdef ASSERT
  // Check the sanity of save_marks() etc.
  MemRegion ur    = used_region();
  MemRegion urasm = used_region_at_save_marks();
  assert(ur.contains(urasm),
         " Error at save_marks(): [" PTR_FORMAT "," PTR_FORMAT ")"
         " should contain [" PTR_FORMAT "," PTR_FORMAT ")",
         p2i(ur.start()), p2i(ur.end()), p2i(urasm.start()), p2i(urasm.end()));
#endif
  // inform allocator that promotions should be tracked.
  assert(_promoInfo.noPromotions(), "_promoInfo inconsistency");
  _promoInfo.startTrackingPromotions();
}

bool CompactibleFreeListSpace::no_allocs_since_save_marks() {
  assert(_promoInfo.tracking(), "No preceding save_marks?");
  return _promoInfo.noPromotions();
}

#define CFLS_OOP_SINCE_SAVE_MARKS_DEFN(OopClosureType, nv_suffix)           \
                                                                            \
void CompactibleFreeListSpace::                                             \
oop_since_save_marks_iterate##nv_suffix(OopClosureType* blk) {              \
  _promoInfo.promoted_oops_iterate##nv_suffix(blk);                         \
  /*                                                                        \
   * This also restores any displaced headers and removes the elements from \
   * the iteration set as they are processed, so that we have a clean slate \
   * at the end of the iteration. Note, thus, that if new objects are       \
   * promoted as a result of the iteration they are iterated over as well.  \
   */                                                                       \
  assert(_promoInfo.noPromotions(), "_promoInfo inconsistency");            \
}

ALL_SINCE_SAVE_MARKS_CLOSURES(CFLS_OOP_SINCE_SAVE_MARKS_DEFN)

bool CompactibleFreeListSpace::linearAllocationWouldFail() const {
  return _smallLinearAllocBlock._word_size == 0;
}

void CompactibleFreeListSpace::repairLinearAllocationBlocks() {
  // Fix up linear allocation blocks to look like free blocks
  repairLinearAllocBlock(&_smallLinearAllocBlock);
}

void CompactibleFreeListSpace::repairLinearAllocBlock(LinearAllocBlock* blk) {
  assert_locked();
  if (blk->_ptr != NULL) {
    assert(blk->_word_size != 0 && blk->_word_size >= MinChunkSize,
           "Minimum block size requirement");
    FreeChunk* fc = (FreeChunk*)(blk->_ptr);
    fc->set_size(blk->_word_size);
    fc->link_prev(NULL);   // mark as free
    fc->dontCoalesce();
    assert(fc->is_free(), "just marked it free");
    assert(fc->cantCoalesce(), "just marked it uncoalescable");
  }
}

void CompactibleFreeListSpace::refillLinearAllocBlocksIfNeeded() {
  assert_locked();
  if (_smallLinearAllocBlock._ptr == NULL) {
    assert(_smallLinearAllocBlock._word_size == 0,
      "Size of linAB should be zero if the ptr is NULL");
    // Reset the linAB refill and allocation size limit.
    _smallLinearAllocBlock.set(0, 0, 1024*SmallForLinearAlloc, SmallForLinearAlloc);
  }
  refillLinearAllocBlockIfNeeded(&_smallLinearAllocBlock);
}

void
CompactibleFreeListSpace::refillLinearAllocBlockIfNeeded(LinearAllocBlock* blk) {
  assert_locked();
  assert((blk->_ptr == NULL && blk->_word_size == 0) ||
         (blk->_ptr != NULL && blk->_word_size >= MinChunkSize),
         "blk invariant");
  if (blk->_ptr == NULL) {
    refillLinearAllocBlock(blk);
  }
}

void
CompactibleFreeListSpace::refillLinearAllocBlock(LinearAllocBlock* blk) {
  assert_locked();
  assert(blk->_word_size == 0 && blk->_ptr == NULL,
         "linear allocation block should be empty");
  FreeChunk* fc;
  if (blk->_refillSize < SmallForDictionary &&
      (fc = getChunkFromIndexedFreeList(blk->_refillSize)) != NULL) {
    // A linAB's strategy might be to use small sizes to reduce
    // fragmentation but still get the benefits of allocation from a
    // linAB.
  } else {
    fc = getChunkFromDictionary(blk->_refillSize);
  }
  if (fc != NULL) {
    blk->_ptr  = (HeapWord*)fc;
    blk->_word_size = fc->size();
    fc->dontCoalesce();   // to prevent sweeper from sweeping us up
  }
}

// Support for compaction
void CompactibleFreeListSpace::prepare_for_compaction(CompactPoint* cp) {
  scan_and_forward(this, cp);
  // Prepare_for_compaction() uses the space between live objects
  // so that later phase can skip dead space quickly.  So verification
  // of the free lists doesn't work after.
}

void CompactibleFreeListSpace::adjust_pointers() {
  // In other versions of adjust_pointers(), a bail out
  // based on the amount of live data in the generation
  // (i.e., if 0, bail out) may be used.
  // Cannot test used() == 0 here because the free lists have already
  // been mangled by the compaction.

  scan_and_adjust_pointers(this);
  // See note about verification in prepare_for_compaction().
}

void CompactibleFreeListSpace::compact() {
  scan_and_compact(this);
}

// Fragmentation metric = 1 - [sum of (fbs**2) / (sum of fbs)**2]
// where fbs is free block sizes
double CompactibleFreeListSpace::flsFrag() const {
  size_t itabFree = totalSizeInIndexedFreeLists();
  double frag = 0.0;
  size_t i;

  for (i = IndexSetStart; i < IndexSetSize; i += IndexSetStride) {
    double sz  = i;
    frag      += _indexedFreeList[i].count() * (sz * sz);
  }

  double totFree = itabFree +
                   _dictionary->total_chunk_size(DEBUG_ONLY(freelistLock()));
  if (totFree > 0) {
    frag = ((frag + _dictionary->sum_of_squared_block_sizes()) /
            (totFree * totFree));
    frag = (double)1.0  - frag;
  } else {
    assert(frag == 0.0, "Follows from totFree == 0");
  }
  return frag;
}

void CompactibleFreeListSpace::beginSweepFLCensus(
  float inter_sweep_current,
  float inter_sweep_estimate,
  float intra_sweep_estimate) {
  assert_locked();
  size_t i;
  for (i = IndexSetStart; i < IndexSetSize; i += IndexSetStride) {
    AdaptiveFreeList<FreeChunk>* fl    = &_indexedFreeList[i];
    log_trace(gc, freelist)("size[" SIZE_FORMAT "] : ", i);
    fl->compute_desired(inter_sweep_current, inter_sweep_estimate, intra_sweep_estimate);
    fl->set_coal_desired((ssize_t)((double)fl->desired() * CMSSmallCoalSurplusPercent));
    fl->set_before_sweep(fl->count());
    fl->set_bfr_surp(fl->surplus());
  }
  _dictionary->begin_sweep_dict_census(CMSLargeCoalSurplusPercent,
                                    inter_sweep_current,
                                    inter_sweep_estimate,
                                    intra_sweep_estimate);
}

void CompactibleFreeListSpace::setFLSurplus() {
  assert_locked();
  size_t i;
  for (i = IndexSetStart; i < IndexSetSize; i += IndexSetStride) {
    AdaptiveFreeList<FreeChunk> *fl = &_indexedFreeList[i];
    fl->set_surplus(fl->count() -
                    (ssize_t)((double)fl->desired() * CMSSmallSplitSurplusPercent));
  }
}

void CompactibleFreeListSpace::setFLHints() {
  assert_locked();
  size_t i;
  size_t h = IndexSetSize;
  for (i = IndexSetSize - 1; i != 0; i -= IndexSetStride) {
    AdaptiveFreeList<FreeChunk> *fl = &_indexedFreeList[i];
    fl->set_hint(h);
    if (fl->surplus() > 0) {
      h = i;
    }
  }
}

void CompactibleFreeListSpace::clearFLCensus() {
  assert_locked();
  size_t i;
  for (i = IndexSetStart; i < IndexSetSize; i += IndexSetStride) {
    AdaptiveFreeList<FreeChunk> *fl = &_indexedFreeList[i];
    fl->set_prev_sweep(fl->count());
    fl->set_coal_births(0);
    fl->set_coal_deaths(0);
    fl->set_split_births(0);
    fl->set_split_deaths(0);
  }
}

void CompactibleFreeListSpace::endSweepFLCensus(size_t sweep_count) {
  log_debug(gc, freelist)("CMS: Large block " PTR_FORMAT, p2i(dictionary()->find_largest_dict()));
  setFLSurplus();
  setFLHints();
  printFLCensus(sweep_count);
  clearFLCensus();
  assert_locked();
  _dictionary->end_sweep_dict_census(CMSLargeSplitSurplusPercent);
}

bool CompactibleFreeListSpace::coalOverPopulated(size_t size) {
  if (size < SmallForDictionary) {
    AdaptiveFreeList<FreeChunk> *fl = &_indexedFreeList[size];
    return (fl->coal_desired() < 0) ||
           ((int)fl->count() > fl->coal_desired());
  } else {
    return dictionary()->coal_dict_over_populated(size);
  }
}

void CompactibleFreeListSpace::smallCoalBirth(size_t size) {
  assert(size < SmallForDictionary, "Size too large for indexed list");
  AdaptiveFreeList<FreeChunk> *fl = &_indexedFreeList[size];
  fl->increment_coal_births();
  fl->increment_surplus();
}

void CompactibleFreeListSpace::smallCoalDeath(size_t size) {
  assert(size < SmallForDictionary, "Size too large for indexed list");
  AdaptiveFreeList<FreeChunk> *fl = &_indexedFreeList[size];
  fl->increment_coal_deaths();
  fl->decrement_surplus();
}

void CompactibleFreeListSpace::coalBirth(size_t size) {
  if (size  < SmallForDictionary) {
    smallCoalBirth(size);
  } else {
    dictionary()->dict_census_update(size,
                                   false /* split */,
                                   true /* birth */);
  }
}

void CompactibleFreeListSpace::coalDeath(size_t size) {
  if(size  < SmallForDictionary) {
    smallCoalDeath(size);
  } else {
    dictionary()->dict_census_update(size,
                                   false /* split */,
                                   false /* birth */);
  }
}

void CompactibleFreeListSpace::smallSplitBirth(size_t size) {
  assert(size < SmallForDictionary, "Size too large for indexed list");
  AdaptiveFreeList<FreeChunk> *fl = &_indexedFreeList[size];
  fl->increment_split_births();
  fl->increment_surplus();
}

void CompactibleFreeListSpace::smallSplitDeath(size_t size) {
  assert(size < SmallForDictionary, "Size too large for indexed list");
  AdaptiveFreeList<FreeChunk> *fl = &_indexedFreeList[size];
  fl->increment_split_deaths();
  fl->decrement_surplus();
}

void CompactibleFreeListSpace::split_birth(size_t size) {
  if (size  < SmallForDictionary) {
    smallSplitBirth(size);
  } else {
    dictionary()->dict_census_update(size,
                                   true /* split */,
                                   true /* birth */);
  }
}

void CompactibleFreeListSpace::splitDeath(size_t size) {
  if (size  < SmallForDictionary) {
    smallSplitDeath(size);
  } else {
    dictionary()->dict_census_update(size,
                                   true /* split */,
                                   false /* birth */);
  }
}

void CompactibleFreeListSpace::split(size_t from, size_t to1) {
  size_t to2 = from - to1;
  splitDeath(from);
  split_birth(to1);
  split_birth(to2);
}

void CompactibleFreeListSpace::print() const {
  print_on(tty);
}

void CompactibleFreeListSpace::prepare_for_verify() {
  assert_locked();
  repairLinearAllocationBlocks();
  // Verify that the SpoolBlocks look like free blocks of
  // appropriate sizes... To be done ...
}

class VerifyAllBlksClosure: public BlkClosure {
 private:
  const CompactibleFreeListSpace* _sp;
  const MemRegion                 _span;
  HeapWord*                       _last_addr;
  size_t                          _last_size;
  bool                            _last_was_obj;
  bool                            _last_was_live;

 public:
  VerifyAllBlksClosure(const CompactibleFreeListSpace* sp,
    MemRegion span) :  _sp(sp), _span(span),
                       _last_addr(NULL), _last_size(0),
                       _last_was_obj(false), _last_was_live(false) { }

  virtual size_t do_blk(HeapWord* addr) {
    size_t res;
    bool   was_obj  = false;
    bool   was_live = false;
    if (_sp->block_is_obj(addr)) {
      was_obj = true;
      oop p = oop(addr);
      guarantee(p->is_oop(), "Should be an oop");
      res = _sp->adjustObjectSize(p->size());
      if (_sp->obj_is_alive(addr)) {
        was_live = true;
        p->verify();
      }
    } else {
      FreeChunk* fc = (FreeChunk*)addr;
      res = fc->size();
      if (FLSVerifyLists && !fc->cantCoalesce()) {
        guarantee(_sp->verify_chunk_in_free_list(fc),
                  "Chunk should be on a free list");
      }
    }
    if (res == 0) {
      Log(gc, verify) log;
      log.error("Livelock: no rank reduction!");
      log.error(" Current:  addr = " PTR_FORMAT ", size = " SIZE_FORMAT ", obj = %s, live = %s \n"
                " Previous: addr = " PTR_FORMAT ", size = " SIZE_FORMAT ", obj = %s, live = %s \n",
        p2i(addr),       res,        was_obj      ?"true":"false", was_live      ?"true":"false",
        p2i(_last_addr), _last_size, _last_was_obj?"true":"false", _last_was_live?"true":"false");
      ResourceMark rm;
      _sp->print_on(log.error_stream());
      guarantee(false, "Verification failed.");
    }
    _last_addr = addr;
    _last_size = res;
    _last_was_obj  = was_obj;
    _last_was_live = was_live;
    return res;
  }
};

class VerifyAllOopsClosure: public OopClosure {
 private:
  const CMSCollector*             _collector;
  const CompactibleFreeListSpace* _sp;
  const MemRegion                 _span;
  const bool                      _past_remark;
  const CMSBitMap*                _bit_map;

 protected:
  void do_oop(void* p, oop obj) {
    if (_span.contains(obj)) { // the interior oop points into CMS heap
      if (!_span.contains(p)) { // reference from outside CMS heap
        // Should be a valid object; the first disjunct below allows
        // us to sidestep an assertion in block_is_obj() that insists
        // that p be in _sp. Note that several generations (and spaces)
        // are spanned by _span (CMS heap) above.
        guarantee(!_sp->is_in_reserved(obj) ||
                  _sp->block_is_obj((HeapWord*)obj),
                  "Should be an object");
        guarantee(obj->is_oop(), "Should be an oop");
        obj->verify();
        if (_past_remark) {
          // Remark has been completed, the object should be marked
          _bit_map->isMarked((HeapWord*)obj);
        }
      } else { // reference within CMS heap
        if (_past_remark) {
          // Remark has been completed -- so the referent should have
          // been marked, if referring object is.
          if (_bit_map->isMarked(_collector->block_start(p))) {
            guarantee(_bit_map->isMarked((HeapWord*)obj), "Marking error?");
          }
        }
      }
    } else if (_sp->is_in_reserved(p)) {
      // the reference is from FLS, and points out of FLS
      guarantee(obj->is_oop(), "Should be an oop");
      obj->verify();
    }
  }

  template <class T> void do_oop_work(T* p) {
    T heap_oop = oopDesc::load_heap_oop(p);
    if (!oopDesc::is_null(heap_oop)) {
      oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
      do_oop(p, obj);
    }
  }

 public:
  VerifyAllOopsClosure(const CMSCollector* collector,
    const CompactibleFreeListSpace* sp, MemRegion span,
    bool past_remark, CMSBitMap* bit_map) :
    _collector(collector), _sp(sp), _span(span),
    _past_remark(past_remark), _bit_map(bit_map) { }

  virtual void do_oop(oop* p)       { VerifyAllOopsClosure::do_oop_work(p); }
  virtual void do_oop(narrowOop* p) { VerifyAllOopsClosure::do_oop_work(p); }
};

void CompactibleFreeListSpace::verify() const {
  assert_lock_strong(&_freelistLock);
  verify_objects_initialized();
  MemRegion span = _collector->_span;
  bool past_remark = (_collector->abstract_state() ==
                      CMSCollector::Sweeping);

  ResourceMark rm;
  HandleMark  hm;

  // Check integrity of CFL data structures
  _promoInfo.verify();
  _dictionary->verify();
  if (FLSVerifyIndexTable) {
    verifyIndexedFreeLists();
  }
  // Check integrity of all objects and free blocks in space
  {
    VerifyAllBlksClosure cl(this, span);
    ((CompactibleFreeListSpace*)this)->blk_iterate(&cl);  // cast off const
  }
  // Check that all references in the heap to FLS
  // are to valid objects in FLS or that references in
  // FLS are to valid objects elsewhere in the heap
  if (FLSVerifyAllHeapReferences)
  {
    VerifyAllOopsClosure cl(_collector, this, span, past_remark,
      _collector->markBitMap());

    // Iterate over all oops in the heap. Uses the _no_header version
    // since we are not interested in following the klass pointers.
    GenCollectedHeap::heap()->oop_iterate_no_header(&cl);
  }

  if (VerifyObjectStartArray) {
    // Verify the block offset table
    _bt.verify();
  }
}

#ifndef PRODUCT
void CompactibleFreeListSpace::verifyFreeLists() const {
  if (FLSVerifyLists) {
    _dictionary->verify();
    verifyIndexedFreeLists();
  } else {
    if (FLSVerifyDictionary) {
      _dictionary->verify();
    }
    if (FLSVerifyIndexTable) {
      verifyIndexedFreeLists();
    }
  }
}
#endif

void CompactibleFreeListSpace::verifyIndexedFreeLists() const {
  size_t i = 0;
  for (; i < IndexSetStart; i++) {
    guarantee(_indexedFreeList[i].head() == NULL, "should be NULL");
  }
  for (; i < IndexSetSize; i++) {
    verifyIndexedFreeList(i);
  }
}

void CompactibleFreeListSpace::verifyIndexedFreeList(size_t size) const {
  FreeChunk* fc   =  _indexedFreeList[size].head();
  FreeChunk* tail =  _indexedFreeList[size].tail();
  size_t    num = _indexedFreeList[size].count();
  size_t      n = 0;
  guarantee(((size >= IndexSetStart) && (size % IndexSetStride == 0)) || fc == NULL,
            "Slot should have been empty");
  for (; fc != NULL; fc = fc->next(), n++) {
    guarantee(fc->size() == size, "Size inconsistency");
    guarantee(fc->is_free(), "!free?");
    guarantee(fc->next() == NULL || fc->next()->prev() == fc, "Broken list");
    guarantee((fc->next() == NULL) == (fc == tail), "Incorrect tail");
  }
  guarantee(n == num, "Incorrect count");
}

#ifndef PRODUCT
void CompactibleFreeListSpace::check_free_list_consistency() const {
  assert((TreeChunk<FreeChunk, AdaptiveFreeList<FreeChunk> >::min_size() <= IndexSetSize),
    "Some sizes can't be allocated without recourse to"
    " linear allocation buffers");
  assert((TreeChunk<FreeChunk, AdaptiveFreeList<FreeChunk> >::min_size()*HeapWordSize == sizeof(TreeChunk<FreeChunk, AdaptiveFreeList<FreeChunk> >)),
    "else MIN_TREE_CHUNK_SIZE is wrong");
  assert(IndexSetStart != 0, "IndexSetStart not initialized");
  assert(IndexSetStride != 0, "IndexSetStride not initialized");
}
#endif

void CompactibleFreeListSpace::printFLCensus(size_t sweep_count) const {
  assert_lock_strong(&_freelistLock);
  LogTarget(Debug, gc, freelist, census) log;
  if (!log.is_enabled()) {
    return;
  }
  AdaptiveFreeList<FreeChunk> total;
  log.print("end sweep# " SIZE_FORMAT, sweep_count);
  ResourceMark rm;
  outputStream* out = log.stream();
  AdaptiveFreeList<FreeChunk>::print_labels_on(out, "size");
  size_t total_free = 0;
  for (size_t i = IndexSetStart; i < IndexSetSize; i += IndexSetStride) {
    const AdaptiveFreeList<FreeChunk> *fl = &_indexedFreeList[i];
    total_free += fl->count() * fl->size();
    if (i % (40*IndexSetStride) == 0) {
      AdaptiveFreeList<FreeChunk>::print_labels_on(out, "size");
    }
    fl->print_on(out);
    total.set_bfr_surp(    total.bfr_surp()     + fl->bfr_surp()    );
    total.set_surplus(    total.surplus()     + fl->surplus()    );
    total.set_desired(    total.desired()     + fl->desired()    );
    total.set_prev_sweep(  total.prev_sweep()   + fl->prev_sweep()  );
    total.set_before_sweep(total.before_sweep() + fl->before_sweep());
    total.set_count(      total.count()       + fl->count()      );
    total.set_coal_births( total.coal_births()  + fl->coal_births() );
    total.set_coal_deaths( total.coal_deaths()  + fl->coal_deaths() );
    total.set_split_births(total.split_births() + fl->split_births());
    total.set_split_deaths(total.split_deaths() + fl->split_deaths());
  }
  total.print_on(out, "TOTAL");
  log.print("Total free in indexed lists " SIZE_FORMAT " words", total_free);
  log.print("growth: %8.5f  deficit: %8.5f",
            (double)(total.split_births()+total.coal_births()-total.split_deaths()-total.coal_deaths())/
                    (total.prev_sweep() != 0 ? (double)total.prev_sweep() : 1.0),
            (double)(total.desired() - total.count())/(total.desired() != 0 ? (double)total.desired() : 1.0));
  _dictionary->print_dict_census(out);
}

///////////////////////////////////////////////////////////////////////////
// CompactibleFreeListSpaceLAB
///////////////////////////////////////////////////////////////////////////

#define VECTOR_257(x)                                                                                  \
  /* 1  2  3  4  5  6  7  8  9 1x 11 12 13 14 15 16 17 18 19 2x 21 22 23 24 25 26 27 28 29 3x 31 32 */ \
  {  x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x,   \
     x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x,   \
     x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x,   \
     x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x,   \
     x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x,   \
     x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x,   \
     x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x,   \
     x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x, x,   \
     x }

// Initialize with default setting for CMS, _not_
// generic OldPLABSize, whose static default is different; if overridden at the
// command-line, this will get reinitialized via a call to
// modify_initialization() below.
AdaptiveWeightedAverage CompactibleFreeListSpaceLAB::_blocks_to_claim[]    =
  VECTOR_257(AdaptiveWeightedAverage(OldPLABWeight, (float)CompactibleFreeListSpaceLAB::_default_dynamic_old_plab_size));
size_t CompactibleFreeListSpaceLAB::_global_num_blocks[]  = VECTOR_257(0);
uint   CompactibleFreeListSpaceLAB::_global_num_workers[] = VECTOR_257(0);

CompactibleFreeListSpaceLAB::CompactibleFreeListSpaceLAB(CompactibleFreeListSpace* cfls) :
  _cfls(cfls)
{
  assert(CompactibleFreeListSpace::IndexSetSize == 257, "Modify VECTOR_257() macro above");
  for (size_t i = CompactibleFreeListSpace::IndexSetStart;
       i < CompactibleFreeListSpace::IndexSetSize;
       i += CompactibleFreeListSpace::IndexSetStride) {
    _indexedFreeList[i].set_size(i);
    _num_blocks[i] = 0;
  }
}

static bool _CFLS_LAB_modified = false;

void CompactibleFreeListSpaceLAB::modify_initialization(size_t n, unsigned wt) {
  assert(!_CFLS_LAB_modified, "Call only once");
  _CFLS_LAB_modified = true;
  for (size_t i = CompactibleFreeListSpace::IndexSetStart;
       i < CompactibleFreeListSpace::IndexSetSize;
       i += CompactibleFreeListSpace::IndexSetStride) {
    _blocks_to_claim[i].modify(n, wt, true /* force */);
  }
}

HeapWord* CompactibleFreeListSpaceLAB::alloc(size_t word_sz) {
  FreeChunk* res;
  assert(word_sz == _cfls->adjustObjectSize(word_sz), "Error");
  if (word_sz >=  CompactibleFreeListSpace::IndexSetSize) {
    // This locking manages sync with other large object allocations.
    MutexLockerEx x(_cfls->parDictionaryAllocLock(),
                    Mutex::_no_safepoint_check_flag);
    res = _cfls->getChunkFromDictionaryExact(word_sz);
    if (res == NULL) return NULL;
  } else {
    AdaptiveFreeList<FreeChunk>* fl = &_indexedFreeList[word_sz];
    if (fl->count() == 0) {
      // Attempt to refill this local free list.
      get_from_global_pool(word_sz, fl);
      // If it didn't work, give up.
      if (fl->count() == 0) return NULL;
    }
    res = fl->get_chunk_at_head();
    assert(res != NULL, "Why was count non-zero?");
  }
  res->markNotFree();
  assert(!res->is_free(), "shouldn't be marked free");
  assert(oop(res)->klass_or_null() == NULL, "should look uninitialized");
  // mangle a just allocated object with a distinct pattern.
  debug_only(res->mangleAllocated(word_sz));
  return (HeapWord*)res;
}

// Get a chunk of blocks of the right size and update related
// book-keeping stats
void CompactibleFreeListSpaceLAB::get_from_global_pool(size_t word_sz, AdaptiveFreeList<FreeChunk>* fl) {
  // Get the #blocks we want to claim
  size_t n_blks = (size_t)_blocks_to_claim[word_sz].average();
  assert(n_blks > 0, "Error");
  assert(ResizeOldPLAB || n_blks == OldPLABSize, "Error");
  // In some cases, when the application has a phase change,
  // there may be a sudden and sharp shift in the object survival
  // profile, and updating the counts at the end of a scavenge
  // may not be quick enough, giving rise to large scavenge pauses
  // during these phase changes. It is beneficial to detect such
  // changes on-the-fly during a scavenge and avoid such a phase-change
  // pothole. The following code is a heuristic attempt to do that.
  // It is protected by a product flag until we have gained
  // enough experience with this heuristic and fine-tuned its behavior.
  // WARNING: This might increase fragmentation if we overreact to
  // small spikes, so some kind of historical smoothing based on
  // previous experience with the greater reactivity might be useful.
  // Lacking sufficient experience, CMSOldPLABResizeQuicker is disabled by
  // default.
  if (ResizeOldPLAB && CMSOldPLABResizeQuicker) {
    //
    // On a 32-bit VM, the denominator can become zero because of integer overflow,
    // which is why there is a cast to double.
    //
    size_t multiple = (size_t) (_num_blocks[word_sz]/(((double)CMSOldPLABToleranceFactor)*CMSOldPLABNumRefills*n_blks));
    n_blks +=  CMSOldPLABReactivityFactor*multiple*n_blks;
    n_blks = MIN2(n_blks, CMSOldPLABMax);
  }
  assert(n_blks > 0, "Error");
  _cfls->par_get_chunk_of_blocks(word_sz, n_blks, fl);
  // Update stats table entry for this block size
  _num_blocks[word_sz] += fl->count();
}

void CompactibleFreeListSpaceLAB::compute_desired_plab_size() {
  for (size_t i =  CompactibleFreeListSpace::IndexSetStart;
       i < CompactibleFreeListSpace::IndexSetSize;
       i += CompactibleFreeListSpace::IndexSetStride) {
    assert((_global_num_workers[i] == 0) == (_global_num_blocks[i] == 0),
           "Counter inconsistency");
    if (_global_num_workers[i] > 0) {
      // Need to smooth wrt historical average
      if (ResizeOldPLAB) {
        _blocks_to_claim[i].sample(
          MAX2(CMSOldPLABMin,
          MIN2(CMSOldPLABMax,
               _global_num_blocks[i]/_global_num_workers[i]/CMSOldPLABNumRefills)));
      }
      // Reset counters for next round
      _global_num_workers[i] = 0;
      _global_num_blocks[i] = 0;
      log_trace(gc, plab)("[" SIZE_FORMAT "]: " SIZE_FORMAT, i, (size_t)_blocks_to_claim[i].average());
    }
  }
}

// If this is changed in the future to allow parallel
// access, one would need to take the FL locks and,
// depending on how it is used, stagger access from
// parallel threads to reduce contention.
void CompactibleFreeListSpaceLAB::retire(int tid) {
  // We run this single threaded with the world stopped;
  // so no need for locks and such.
  NOT_PRODUCT(Thread* t = Thread::current();)
  assert(Thread::current()->is_VM_thread(), "Error");
  for (size_t i =  CompactibleFreeListSpace::IndexSetStart;
       i < CompactibleFreeListSpace::IndexSetSize;
       i += CompactibleFreeListSpace::IndexSetStride) {
    assert(_num_blocks[i] >= (size_t)_indexedFreeList[i].count(),
           "Can't retire more than what we obtained");
    if (_num_blocks[i] > 0) {
      size_t num_retire =  _indexedFreeList[i].count();
      assert(_num_blocks[i] > num_retire, "Should have used at least one");
      {
        // MutexLockerEx x(_cfls->_indexedFreeListParLocks[i],
        //                Mutex::_no_safepoint_check_flag);

        // Update globals stats for num_blocks used
        _global_num_blocks[i] += (_num_blocks[i] - num_retire);
        _global_num_workers[i]++;
        assert(_global_num_workers[i] <= ParallelGCThreads, "Too big");
        if (num_retire > 0) {
          _cfls->_indexedFreeList[i].prepend(&_indexedFreeList[i]);
          // Reset this list.
          _indexedFreeList[i] = AdaptiveFreeList<FreeChunk>();
          _indexedFreeList[i].set_size(i);
        }
      }
      log_trace(gc, plab)("%d[" SIZE_FORMAT "]: " SIZE_FORMAT "/" SIZE_FORMAT "/" SIZE_FORMAT,
                          tid, i, num_retire, _num_blocks[i], (size_t)_blocks_to_claim[i].average());
      // Reset stats for next round
      _num_blocks[i]         = 0;
    }
  }
}

// Used by par_get_chunk_of_blocks() for the chunks from the
// indexed_free_lists.  Looks for a chunk with size that is a multiple
// of "word_sz" and if found, splits it into "word_sz" chunks and add
// to the free list "fl".  "n" is the maximum number of chunks to
// be added to "fl".
bool CompactibleFreeListSpace:: par_get_chunk_of_blocks_IFL(size_t word_sz, size_t n, AdaptiveFreeList<FreeChunk>* fl) {

  // We'll try all multiples of word_sz in the indexed set, starting with
  // word_sz itself and, if CMSSplitIndexedFreeListBlocks, try larger multiples,
  // then try getting a big chunk and splitting it.
  {
    bool found;
    int  k;
    size_t cur_sz;
    for (k = 1, cur_sz = k * word_sz, found = false;
         (cur_sz < CompactibleFreeListSpace::IndexSetSize) &&
         (CMSSplitIndexedFreeListBlocks || k <= 1);
         k++, cur_sz = k * word_sz) {
      AdaptiveFreeList<FreeChunk> fl_for_cur_sz;  // Empty.
      fl_for_cur_sz.set_size(cur_sz);
      {
        MutexLockerEx x(_indexedFreeListParLocks[cur_sz],
                        Mutex::_no_safepoint_check_flag);
        AdaptiveFreeList<FreeChunk>* gfl = &_indexedFreeList[cur_sz];
        if (gfl->count() != 0) {
          // nn is the number of chunks of size cur_sz that
          // we'd need to split k-ways each, in order to create
          // "n" chunks of size word_sz each.
          const size_t nn = MAX2(n/k, (size_t)1);
          gfl->getFirstNChunksFromList(nn, &fl_for_cur_sz);
          found = true;
          if (k > 1) {
            // Update split death stats for the cur_sz-size blocks list:
            // we increment the split death count by the number of blocks
            // we just took from the cur_sz-size blocks list and which
            // we will be splitting below.
            ssize_t deaths = gfl->split_deaths() +
                             fl_for_cur_sz.count();
            gfl->set_split_deaths(deaths);
          }
        }
      }
      // Now transfer fl_for_cur_sz to fl.  Common case, we hope, is k = 1.
      if (found) {
        if (k == 1) {
          fl->prepend(&fl_for_cur_sz);
        } else {
          // Divide each block on fl_for_cur_sz up k ways.
          FreeChunk* fc;
          while ((fc = fl_for_cur_sz.get_chunk_at_head()) != NULL) {
            // Must do this in reverse order, so that anybody attempting to
            // access the main chunk sees it as a single free block until we
            // change it.
            size_t fc_size = fc->size();
            assert(fc->is_free(), "Error");
            for (int i = k-1; i >= 0; i--) {
              FreeChunk* ffc = (FreeChunk*)((HeapWord*)fc + i * word_sz);
              assert((i != 0) ||
                        ((fc == ffc) && ffc->is_free() &&
                         (ffc->size() == k*word_sz) && (fc_size == word_sz)),
                        "Counting error");
              ffc->set_size(word_sz);
              ffc->link_prev(NULL); // Mark as a free block for other (parallel) GC threads.
              ffc->link_next(NULL);
              // Above must occur before BOT is updated below.
              OrderAccess::storestore();
              // splitting from the right, fc_size == i * word_sz
              _bt.mark_block((HeapWord*)ffc, word_sz, true /* reducing */);
              fc_size -= word_sz;
              assert(fc_size == i*word_sz, "Error");
              _bt.verify_not_unallocated((HeapWord*)ffc, word_sz);
              _bt.verify_single_block((HeapWord*)fc, fc_size);
              _bt.verify_single_block((HeapWord*)ffc, word_sz);
              // Push this on "fl".
              fl->return_chunk_at_head(ffc);
            }
            // TRAP
            assert(fl->tail()->next() == NULL, "List invariant.");
          }
        }
        // Update birth stats for this block size.
        size_t num = fl->count();
        MutexLockerEx x(_indexedFreeListParLocks[word_sz],
                        Mutex::_no_safepoint_check_flag);
        ssize_t births = _indexedFreeList[word_sz].split_births() + num;
        _indexedFreeList[word_sz].set_split_births(births);
        return true;
      }
    }
    return found;
  }
}

FreeChunk* CompactibleFreeListSpace::get_n_way_chunk_to_split(size_t word_sz, size_t n) {

  FreeChunk* fc = NULL;
  FreeChunk* rem_fc = NULL;
  size_t rem;
  {
    MutexLockerEx x(parDictionaryAllocLock(),
                    Mutex::_no_safepoint_check_flag);
    while (n > 0) {
      fc = dictionary()->get_chunk(MAX2(n * word_sz, _dictionary->min_size()),
                                  FreeBlockDictionary<FreeChunk>::atLeast);
      if (fc != NULL) {
        break;
      } else {
        n--;
      }
    }
    if (fc == NULL) return NULL;
    // Otherwise, split up that block.
    assert((ssize_t)n >= 1, "Control point invariant");
    assert(fc->is_free(), "Error: should be a free block");
    _bt.verify_single_block((HeapWord*)fc, fc->size());
    const size_t nn = fc->size() / word_sz;
    n = MIN2(nn, n);
    assert((ssize_t)n >= 1, "Control point invariant");
    rem = fc->size() - n * word_sz;
    // If there is a remainder, and it's too small, allocate one fewer.
    if (rem > 0 && rem < MinChunkSize) {
      n--; rem += word_sz;
    }
    // Note that at this point we may have n == 0.
    assert((ssize_t)n >= 0, "Control point invariant");

    // If n is 0, the chunk fc that was found is not large
    // enough to leave a viable remainder.  We are unable to
    // allocate even one block.  Return fc to the
    // dictionary and return, leaving "fl" empty.
    if (n == 0) {
      returnChunkToDictionary(fc);
      return NULL;
    }

    _bt.allocated((HeapWord*)fc, fc->size(), true /* reducing */);  // update _unallocated_blk
    dictionary()->dict_census_update(fc->size(),
                                     true /*split*/,
                                     false /*birth*/);

    // First return the remainder, if any.
    // Note that we hold the lock until we decide if we're going to give
    // back the remainder to the dictionary, since a concurrent allocation
    // may otherwise see the heap as empty.  (We're willing to take that
    // hit if the block is a small block.)
    if (rem > 0) {
      size_t prefix_size = n * word_sz;
      rem_fc = (FreeChunk*)((HeapWord*)fc + prefix_size);
      rem_fc->set_size(rem);
      rem_fc->link_prev(NULL); // Mark as a free block for other (parallel) GC threads.
      rem_fc->link_next(NULL);
      // Above must occur before BOT is updated below.
      assert((ssize_t)n > 0 && prefix_size > 0 && rem_fc > fc, "Error");
      OrderAccess::storestore();
      _bt.split_block((HeapWord*)fc, fc->size(), prefix_size);
      assert(fc->is_free(), "Error");
      fc->set_size(prefix_size);
      if (rem >= IndexSetSize) {
        returnChunkToDictionary(rem_fc);
        dictionary()->dict_census_update(rem, true /*split*/, true /*birth*/);
        rem_fc = NULL;
      }
      // Otherwise, return it to the small list below.
    }
  }
  if (rem_fc != NULL) {
    MutexLockerEx x(_indexedFreeListParLocks[rem],
                    Mutex::_no_safepoint_check_flag);
    _bt.verify_not_unallocated((HeapWord*)rem_fc, rem_fc->size());
    _indexedFreeList[rem].return_chunk_at_head(rem_fc);
    smallSplitBirth(rem);
  }
  assert(n * word_sz == fc->size(),
         "Chunk size " SIZE_FORMAT " is not exactly splittable by "
         SIZE_FORMAT " sized chunks of size " SIZE_FORMAT,
         fc->size(), n, word_sz);
  return fc;
}

void CompactibleFreeListSpace:: par_get_chunk_of_blocks_dictionary(size_t word_sz, size_t targetted_number_of_chunks, AdaptiveFreeList<FreeChunk>* fl) {

  FreeChunk* fc = get_n_way_chunk_to_split(word_sz, targetted_number_of_chunks);

  if (fc == NULL) {
    return;
  }

  size_t n = fc->size() / word_sz;

  assert((ssize_t)n > 0, "Consistency");
  // Now do the splitting up.
  // Must do this in reverse order, so that anybody attempting to
  // access the main chunk sees it as a single free block until we
  // change it.
  size_t fc_size = n * word_sz;
  // All but first chunk in this loop
  for (ssize_t i = n-1; i > 0; i--) {
    FreeChunk* ffc = (FreeChunk*)((HeapWord*)fc + i * word_sz);
    ffc->set_size(word_sz);
    ffc->link_prev(NULL); // Mark as a free block for other (parallel) GC threads.
    ffc->link_next(NULL);
    // Above must occur before BOT is updated below.
    OrderAccess::storestore();
    // splitting from the right, fc_size == (n - i + 1) * wordsize
    _bt.mark_block((HeapWord*)ffc, word_sz, true /* reducing */);
    fc_size -= word_sz;
    _bt.verify_not_unallocated((HeapWord*)ffc, ffc->size());
    _bt.verify_single_block((HeapWord*)ffc, ffc->size());
    _bt.verify_single_block((HeapWord*)fc, fc_size);
    // Push this on "fl".
    fl->return_chunk_at_head(ffc);
  }
  // First chunk
  assert(fc->is_free() && fc->size() == n*word_sz, "Error: should still be a free block");
  // The blocks above should show their new sizes before the first block below
  fc->set_size(word_sz);
  fc->link_prev(NULL);    // idempotent wrt free-ness, see assert above
  fc->link_next(NULL);
  _bt.verify_not_unallocated((HeapWord*)fc, fc->size());
  _bt.verify_single_block((HeapWord*)fc, fc->size());
  fl->return_chunk_at_head(fc);

  assert((ssize_t)n > 0 && (ssize_t)n == fl->count(), "Incorrect number of blocks");
  {
    // Update the stats for this block size.
    MutexLockerEx x(_indexedFreeListParLocks[word_sz],
                    Mutex::_no_safepoint_check_flag);
    const ssize_t births = _indexedFreeList[word_sz].split_births() + n;
    _indexedFreeList[word_sz].set_split_births(births);
    // ssize_t new_surplus = _indexedFreeList[word_sz].surplus() + n;
    // _indexedFreeList[word_sz].set_surplus(new_surplus);
  }

  // TRAP
  assert(fl->tail()->next() == NULL, "List invariant.");
}

void CompactibleFreeListSpace:: par_get_chunk_of_blocks(size_t word_sz, size_t n, AdaptiveFreeList<FreeChunk>* fl) {
  assert(fl->count() == 0, "Precondition.");
  assert(word_sz < CompactibleFreeListSpace::IndexSetSize,
         "Precondition");

  if (par_get_chunk_of_blocks_IFL(word_sz, n, fl)) {
    // Got it
    return;
  }

  // Otherwise, we'll split a block from the dictionary.
  par_get_chunk_of_blocks_dictionary(word_sz, n, fl);
}

const size_t CompactibleFreeListSpace::max_flag_size_for_task_size() const {
  const size_t ergo_max = _old_gen->reserved().word_size() / (CardTableModRefBS::card_size_in_words * BitsPerWord);
  return ergo_max;
}

// Set up the space's par_seq_tasks structure for work claiming
// for parallel rescan. See CMSParRemarkTask where this is currently used.
// XXX Need to suitably abstract and generalize this and the next
// method into one.
void
CompactibleFreeListSpace::
initialize_sequential_subtasks_for_rescan(int n_threads) {
  // The "size" of each task is fixed according to rescan_task_size.
  assert(n_threads > 0, "Unexpected n_threads argument");
  const size_t task_size = rescan_task_size();
  size_t n_tasks = (used_region().word_size() + task_size - 1)/task_size;
  assert((n_tasks == 0) == used_region().is_empty(), "n_tasks incorrect");
  assert(n_tasks == 0 ||
         ((used_region().start() + (n_tasks - 1)*task_size < used_region().end()) &&
          (used_region().start() + n_tasks*task_size >= used_region().end())),
         "n_tasks calculation incorrect");
  SequentialSubTasksDone* pst = conc_par_seq_tasks();
  assert(!pst->valid(), "Clobbering existing data?");
  // Sets the condition for completion of the subtask (how many threads
  // need to finish in order to be done).
  pst->set_n_threads(n_threads);
  pst->set_n_tasks((int)n_tasks);
}

// Set up the space's par_seq_tasks structure for work claiming
// for parallel concurrent marking. See CMSConcMarkTask where this is currently used.
void
CompactibleFreeListSpace::
initialize_sequential_subtasks_for_marking(int n_threads,
                                           HeapWord* low) {
  // The "size" of each task is fixed according to rescan_task_size.
  assert(n_threads > 0, "Unexpected n_threads argument");
  const size_t task_size = marking_task_size();
  assert(task_size > CardTableModRefBS::card_size_in_words &&
         (task_size %  CardTableModRefBS::card_size_in_words == 0),
         "Otherwise arithmetic below would be incorrect");
  MemRegion span = _old_gen->reserved();
  if (low != NULL) {
    if (span.contains(low)) {
      // Align low down to  a card boundary so that
      // we can use block_offset_careful() on span boundaries.
      HeapWord* aligned_low = (HeapWord*)align_size_down((uintptr_t)low,
                                 CardTableModRefBS::card_size);
      // Clip span prefix at aligned_low
      span = span.intersection(MemRegion(aligned_low, span.end()));
    } else if (low > span.end()) {
      span = MemRegion(low, low);  // Null region
    } // else use entire span
  }
  assert(span.is_empty() ||
         ((uintptr_t)span.start() %  CardTableModRefBS::card_size == 0),
        "span should start at a card boundary");
  size_t n_tasks = (span.word_size() + task_size - 1)/task_size;
  assert((n_tasks == 0) == span.is_empty(), "Inconsistency");
  assert(n_tasks == 0 ||
         ((span.start() + (n_tasks - 1)*task_size < span.end()) &&
          (span.start() + n_tasks*task_size >= span.end())),
         "n_tasks calculation incorrect");
  SequentialSubTasksDone* pst = conc_par_seq_tasks();
  assert(!pst->valid(), "Clobbering existing data?");
  // Sets the condition for completion of the subtask (how many threads
  // need to finish in order to be done).
  pst->set_n_threads(n_threads);
  pst->set_n_tasks((int)n_tasks);
}
