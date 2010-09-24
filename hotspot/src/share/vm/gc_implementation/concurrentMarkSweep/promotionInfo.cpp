/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_promotionInfo.cpp.incl"

/////////////////////////////////////////////////////////////////////////
//// PromotionInfo
/////////////////////////////////////////////////////////////////////////


//////////////////////////////////////////////////////////////////////////////
// We go over the list of promoted objects, removing each from the list,
// and applying the closure (this may, in turn, add more elements to
// the tail of the promoted list, and these newly added objects will
// also be processed) until the list is empty.
// To aid verification and debugging, in the non-product builds
// we actually forward _promoHead each time we process a promoted oop.
// Note that this is not necessary in general (i.e. when we don't need to
// call PromotionInfo::verify()) because oop_iterate can only add to the
// end of _promoTail, and never needs to look at _promoHead.

#define PROMOTED_OOPS_ITERATE_DEFN(OopClosureType, nv_suffix)               \
                                                                            \
void PromotionInfo::promoted_oops_iterate##nv_suffix(OopClosureType* cl) {  \
  NOT_PRODUCT(verify());                                                    \
  PromotedObject *curObj, *nextObj;                                         \
  for (curObj = _promoHead; curObj != NULL; curObj = nextObj) {             \
    if ((nextObj = curObj->next()) == NULL) {                               \
      /* protect ourselves against additions due to closure application     \
         below by resetting the list.  */                                   \
      assert(_promoTail == curObj, "Should have been the tail");            \
      _promoHead = _promoTail = NULL;                                       \
    }                                                                       \
    if (curObj->hasDisplacedMark()) {                                       \
      /* restore displaced header */                                        \
      oop(curObj)->set_mark(nextDisplacedHeader());                         \
    } else {                                                                \
      /* restore prototypical header */                                     \
      oop(curObj)->init_mark();                                             \
    }                                                                       \
    /* The "promoted_mark" should now not be set */                         \
    assert(!curObj->hasPromotedMark(),                                      \
           "Should have been cleared by restoring displaced mark-word");    \
    NOT_PRODUCT(_promoHead = nextObj);                                      \
    if (cl != NULL) oop(curObj)->oop_iterate(cl);                           \
    if (nextObj == NULL) { /* start at head of list reset above */          \
      nextObj = _promoHead;                                                 \
    }                                                                       \
  }                                                                         \
  assert(noPromotions(), "post-condition violation");                       \
  assert(_promoHead == NULL && _promoTail == NULL, "emptied promoted list");\
  assert(_spoolHead == _spoolTail, "emptied spooling buffers");             \
  assert(_firstIndex == _nextIndex, "empty buffer");                        \
}

// This should have been ALL_SINCE_...() just like the others,
// but, because the body of the method above is somehwat longer,
// the MSVC compiler cannot cope; as a workaround, we split the
// macro into its 3 constituent parts below (see original macro
// definition in specializedOopClosures.hpp).
SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES_YOUNG(PROMOTED_OOPS_ITERATE_DEFN)
PROMOTED_OOPS_ITERATE_DEFN(OopsInGenClosure,_v)


// Return the next displaced header, incrementing the pointer and
// recycling spool area as necessary.
markOop PromotionInfo::nextDisplacedHeader() {
  assert(_spoolHead != NULL, "promotionInfo inconsistency");
  assert(_spoolHead != _spoolTail || _firstIndex < _nextIndex,
         "Empty spool space: no displaced header can be fetched");
  assert(_spoolHead->bufferSize > _firstIndex, "Off by one error at head?");
  markOop hdr = _spoolHead->displacedHdr[_firstIndex];
  // Spool forward
  if (++_firstIndex == _spoolHead->bufferSize) { // last location in this block
    // forward to next block, recycling this block into spare spool buffer
    SpoolBlock* tmp = _spoolHead->nextSpoolBlock;
    assert(_spoolHead != _spoolTail, "Spooling storage mix-up");
    _spoolHead->nextSpoolBlock = _spareSpool;
    _spareSpool = _spoolHead;
    _spoolHead = tmp;
    _firstIndex = 1;
    NOT_PRODUCT(
      if (_spoolHead == NULL) {  // all buffers fully consumed
        assert(_spoolTail == NULL && _nextIndex == 1,
               "spool buffers processing inconsistency");
      }
    )
  }
  return hdr;
}

void PromotionInfo::track(PromotedObject* trackOop) {
  track(trackOop, oop(trackOop)->klass());
}

void PromotionInfo::track(PromotedObject* trackOop, klassOop klassOfOop) {
  // make a copy of header as it may need to be spooled
  markOop mark = oop(trackOop)->mark();
  trackOop->clearNext();
  if (mark->must_be_preserved_for_cms_scavenge(klassOfOop)) {
    // save non-prototypical header, and mark oop
    saveDisplacedHeader(mark);
    trackOop->setDisplacedMark();
  } else {
    // we'd like to assert something like the following:
    // assert(mark == markOopDesc::prototype(), "consistency check");
    // ... but the above won't work because the age bits have not (yet) been
    // cleared. The remainder of the check would be identical to the
    // condition checked in must_be_preserved() above, so we don't really
    // have anything useful to check here!
  }
  if (_promoTail != NULL) {
    assert(_promoHead != NULL, "List consistency");
    _promoTail->setNext(trackOop);
    _promoTail = trackOop;
  } else {
    assert(_promoHead == NULL, "List consistency");
    _promoHead = _promoTail = trackOop;
  }
  // Mask as newly promoted, so we can skip over such objects
  // when scanning dirty cards
  assert(!trackOop->hasPromotedMark(), "Should not have been marked");
  trackOop->setPromotedMark();
}

// Save the given displaced header, incrementing the pointer and
// obtaining more spool area as necessary.
void PromotionInfo::saveDisplacedHeader(markOop hdr) {
  assert(_spoolHead != NULL && _spoolTail != NULL,
         "promotionInfo inconsistency");
  assert(_spoolTail->bufferSize > _nextIndex, "Off by one error at tail?");
  _spoolTail->displacedHdr[_nextIndex] = hdr;
  // Spool forward
  if (++_nextIndex == _spoolTail->bufferSize) { // last location in this block
    // get a new spooling block
    assert(_spoolTail->nextSpoolBlock == NULL, "tail should terminate spool list");
    _splice_point = _spoolTail;                   // save for splicing
    _spoolTail->nextSpoolBlock = getSpoolBlock(); // might fail
    _spoolTail = _spoolTail->nextSpoolBlock;      // might become NULL ...
    // ... but will attempt filling before next promotion attempt
    _nextIndex = 1;
  }
}

// Ensure that spooling space exists. Return false if spooling space
// could not be obtained.
bool PromotionInfo::ensure_spooling_space_work() {
  assert(!has_spooling_space(), "Only call when there is no spooling space");
  // Try and obtain more spooling space
  SpoolBlock* newSpool = getSpoolBlock();
  assert(newSpool == NULL ||
         (newSpool->bufferSize != 0 && newSpool->nextSpoolBlock == NULL),
        "getSpoolBlock() sanity check");
  if (newSpool == NULL) {
    return false;
  }
  _nextIndex = 1;
  if (_spoolTail == NULL) {
    _spoolTail = newSpool;
    if (_spoolHead == NULL) {
      _spoolHead = newSpool;
      _firstIndex = 1;
    } else {
      assert(_splice_point != NULL && _splice_point->nextSpoolBlock == NULL,
             "Splice point invariant");
      // Extra check that _splice_point is connected to list
      #ifdef ASSERT
      {
        SpoolBlock* blk = _spoolHead;
        for (; blk->nextSpoolBlock != NULL;
             blk = blk->nextSpoolBlock);
        assert(blk != NULL && blk == _splice_point,
               "Splice point incorrect");
      }
      #endif // ASSERT
      _splice_point->nextSpoolBlock = newSpool;
    }
  } else {
    assert(_spoolHead != NULL, "spool list consistency");
    _spoolTail->nextSpoolBlock = newSpool;
    _spoolTail = newSpool;
  }
  return true;
}

// Get a free spool buffer from the free pool, getting a new block
// from the heap if necessary.
SpoolBlock* PromotionInfo::getSpoolBlock() {
  SpoolBlock* res;
  if ((res = _spareSpool) != NULL) {
    _spareSpool = _spareSpool->nextSpoolBlock;
    res->nextSpoolBlock = NULL;
  } else {  // spare spool exhausted, get some from heap
    res = (SpoolBlock*)(space()->allocateScratch(refillSize()));
    if (res != NULL) {
      res->init();
    }
  }
  assert(res == NULL || res->nextSpoolBlock == NULL, "postcondition");
  return res;
}

void PromotionInfo::startTrackingPromotions() {
  assert(_spoolHead == _spoolTail && _firstIndex == _nextIndex,
         "spooling inconsistency?");
  _firstIndex = _nextIndex = 1;
  _tracking = true;
}

#define CMSPrintPromoBlockInfo 1

void PromotionInfo::stopTrackingPromotions(uint worker_id) {
  assert(_spoolHead == _spoolTail && _firstIndex == _nextIndex,
         "spooling inconsistency?");
  _firstIndex = _nextIndex = 1;
  _tracking = false;
  if (CMSPrintPromoBlockInfo > 1) {
    print_statistics(worker_id);
  }
}

void PromotionInfo::print_statistics(uint worker_id) const {
  assert(_spoolHead == _spoolTail && _firstIndex == _nextIndex,
         "Else will undercount");
  assert(CMSPrintPromoBlockInfo > 0, "Else unnecessary call");
  // Count the number of blocks and slots in the free pool
  size_t slots  = 0;
  size_t blocks = 0;
  for (SpoolBlock* cur_spool = _spareSpool;
       cur_spool != NULL;
       cur_spool = cur_spool->nextSpoolBlock) {
    // the first entry is just a self-pointer; indices 1 through
    // bufferSize - 1 are occupied (thus, bufferSize - 1 slots).
    assert((void*)cur_spool->displacedHdr == (void*)&cur_spool->displacedHdr,
           "first entry of displacedHdr should be self-referential");
    slots += cur_spool->bufferSize - 1;
    blocks++;
  }
  if (_spoolHead != NULL) {
    slots += _spoolHead->bufferSize - 1;
    blocks++;
  }
  gclog_or_tty->print_cr(" [worker %d] promo_blocks = %d, promo_slots = %d ",
                         worker_id, blocks, slots);
}

// When _spoolTail is not NULL, then the slot <_spoolTail, _nextIndex>
// points to the next slot available for filling.
// The set of slots holding displaced headers are then all those in the
// right-open interval denoted by:
//
//    [ <_spoolHead, _firstIndex>, <_spoolTail, _nextIndex> )
//
// When _spoolTail is NULL, then the set of slots with displaced headers
// is all those starting at the slot <_spoolHead, _firstIndex> and
// going up to the last slot of last block in the linked list.
// In this lartter case, _splice_point points to the tail block of
// this linked list of blocks holding displaced headers.
void PromotionInfo::verify() const {
  // Verify the following:
  // 1. the number of displaced headers matches the number of promoted
  //    objects that have displaced headers
  // 2. each promoted object lies in this space
  debug_only(
    PromotedObject* junk = NULL;
    assert(junk->next_addr() == (void*)(oop(junk)->mark_addr()),
           "Offset of PromotedObject::_next is expected to align with "
           "  the OopDesc::_mark within OopDesc");
  )
  // FIXME: guarantee????
  guarantee(_spoolHead == NULL || _spoolTail != NULL ||
            _splice_point != NULL, "list consistency");
  guarantee(_promoHead == NULL || _promoTail != NULL, "list consistency");
  // count the number of objects with displaced headers
  size_t numObjsWithDisplacedHdrs = 0;
  for (PromotedObject* curObj = _promoHead; curObj != NULL; curObj = curObj->next()) {
    guarantee(space()->is_in_reserved((HeapWord*)curObj), "Containment");
    // the last promoted object may fail the mark() != NULL test of is_oop().
    guarantee(curObj->next() == NULL || oop(curObj)->is_oop(), "must be an oop");
    if (curObj->hasDisplacedMark()) {
      numObjsWithDisplacedHdrs++;
    }
  }
  // Count the number of displaced headers
  size_t numDisplacedHdrs = 0;
  for (SpoolBlock* curSpool = _spoolHead;
       curSpool != _spoolTail && curSpool != NULL;
       curSpool = curSpool->nextSpoolBlock) {
    // the first entry is just a self-pointer; indices 1 through
    // bufferSize - 1 are occupied (thus, bufferSize - 1 slots).
    guarantee((void*)curSpool->displacedHdr == (void*)&curSpool->displacedHdr,
              "first entry of displacedHdr should be self-referential");
    numDisplacedHdrs += curSpool->bufferSize - 1;
  }
  guarantee((_spoolHead == _spoolTail) == (numDisplacedHdrs == 0),
            "internal consistency");
  guarantee(_spoolTail != NULL || _nextIndex == 1,
            "Inconsistency between _spoolTail and _nextIndex");
  // We overcounted (_firstIndex-1) worth of slots in block
  // _spoolHead and we undercounted (_nextIndex-1) worth of
  // slots in block _spoolTail. We make an appropriate
  // adjustment by subtracting the first and adding the
  // second:  - (_firstIndex - 1) + (_nextIndex - 1)
  numDisplacedHdrs += (_nextIndex - _firstIndex);
  guarantee(numDisplacedHdrs == numObjsWithDisplacedHdrs, "Displaced hdr count");
}

void PromotionInfo::print_on(outputStream* st) const {
  SpoolBlock* curSpool = NULL;
  size_t i = 0;
  st->print_cr(" start & end indices: [" SIZE_FORMAT ", " SIZE_FORMAT ")",
               _firstIndex, _nextIndex);
  for (curSpool = _spoolHead; curSpool != _spoolTail && curSpool != NULL;
       curSpool = curSpool->nextSpoolBlock) {
    curSpool->print_on(st);
    st->print_cr(" active ");
    i++;
  }
  for (curSpool = _spoolTail; curSpool != NULL;
       curSpool = curSpool->nextSpoolBlock) {
    curSpool->print_on(st);
    st->print_cr(" inactive ");
    i++;
  }
  for (curSpool = _spareSpool; curSpool != NULL;
       curSpool = curSpool->nextSpoolBlock) {
    curSpool->print_on(st);
    st->print_cr(" free ");
    i++;
  }
  st->print_cr("  " SIZE_FORMAT " header spooling blocks", i);
}

void SpoolBlock::print_on(outputStream* st) const {
  st->print("[" PTR_FORMAT "," PTR_FORMAT "), " SIZE_FORMAT " HeapWords -> " PTR_FORMAT,
            this, (HeapWord*)displacedHdr + bufferSize,
            bufferSize, nextSpoolBlock);
}
