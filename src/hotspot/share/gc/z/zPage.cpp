/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/gc_globals.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageAge.hpp"
#include "gc/z/zRememberedSet.inline.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

ZPage::ZPage(ZPageType type, ZPageAge age, const ZVirtualMemory& vmem, ZMultiPartitionTracker* multi_partition_tracker, uint32_t partition_id)
  : _type(type),
    _generation_id(/* set in reset */),
    _age(/* set in reset */),
    _seqnum(/* set in reset */),
    _seqnum_other(/* set in reset */),
    _single_partition_id(partition_id),
    _virtual(vmem),
    _top(to_zoffset_end(start())),
    _livemap(object_max_count()),
    _remembered_set(),
    _multi_partition_tracker(multi_partition_tracker) {
  assert(!_virtual.is_null(), "Should not be null");
  assert((_type == ZPageType::small && size() == ZPageSizeSmall) ||
         (_type == ZPageType::medium && ZPageSizeMediumMin <= size() && size() <= ZPageSizeMediumMax) ||
         (_type == ZPageType::large && is_aligned(size(), ZGranuleSize)),
         "Page type/size mismatch");
  reset(age);

  if (is_old()) {
    remset_alloc();
  }
}

ZPage::ZPage(ZPageType type, ZPageAge age, const ZVirtualMemory& vmem, uint32_t partition_id)
  : ZPage(type, age, vmem, nullptr /* multi_partition_tracker */, partition_id) {}

ZPage::ZPage(ZPageType type, ZPageAge age, const ZVirtualMemory& vmem, ZMultiPartitionTracker* multi_partition_tracker)
  : ZPage(type, age, vmem, multi_partition_tracker, -1u /* partition_id */) {}

ZPage* ZPage::clone_for_promotion() const {
  assert(_age != ZPageAge::old, "must be used for promotion");
  // Only copy type and memory layouts, and also update _top. Let the rest be
  // lazily reconstructed when needed.
  ZPage* const page = new ZPage(_type, ZPageAge::old, _virtual, _multi_partition_tracker, _single_partition_id);
  page->_top = _top;

  return page;
}

ZGeneration* ZPage::generation() {
  return ZGeneration::generation(_generation_id);
}

const ZGeneration* ZPage::generation() const {
  return ZGeneration::generation(_generation_id);
}

void ZPage::reset_seqnum() {
  Atomic::store(&_seqnum, generation()->seqnum());
  Atomic::store(&_seqnum_other, ZGeneration::generation(_generation_id == ZGenerationId::young ? ZGenerationId::old : ZGenerationId::young)->seqnum());
}

void ZPage::remset_alloc() {
  // Remsets should only be allocated/initialized once and only for old pages.
  assert(!_remembered_set.is_initialized(), "Should not be initialized");
  assert(is_old(), "Only old pages need a remset");

  _remembered_set.initialize(size());
}

ZPage* ZPage::reset(ZPageAge age) {
  _age = age;

  _generation_id = age == ZPageAge::old
      ? ZGenerationId::old
      : ZGenerationId::young;

  reset_seqnum();

  return this;
}

void ZPage::reset_livemap() {
  _livemap.reset();
}

void ZPage::reset_top_for_allocation() {
  _top = to_zoffset_end(start());
}

class ZFindBaseOopClosure : public ObjectClosure {
private:
  volatile zpointer* _p;
  oop _result;

public:
  ZFindBaseOopClosure(volatile zpointer* p)
    : _p(p),
      _result(nullptr) {}

  virtual void do_object(oop obj) {
    const uintptr_t p_int = reinterpret_cast<uintptr_t>(_p);
    const uintptr_t base_int = cast_from_oop<uintptr_t>(obj);
    const uintptr_t end_int = base_int + wordSize * obj->size();
    if (p_int >= base_int && p_int < end_int) {
      _result = obj;
    }
  }

  oop result() const { return _result; }
};

bool ZPage::is_remset_cleared_current() const {
  return _remembered_set.is_cleared_current();
}

bool ZPage::is_remset_cleared_previous() const {
  return _remembered_set.is_cleared_previous();
}

void ZPage::verify_remset_cleared_current() const {
  if (ZVerifyRemembered && !is_remset_cleared_current()) {
    fatal_msg(" current remset bits should be cleared");
  }
}

void ZPage::verify_remset_cleared_previous() const {
  if (ZVerifyRemembered && !is_remset_cleared_previous()) {
    fatal_msg(" previous remset bits should be cleared");
  }
}

void ZPage::clear_remset_previous() {
  _remembered_set.clear_previous();
}

void ZPage::swap_remset_bitmaps() {
  _remembered_set.swap_remset_bitmaps();
}

void* ZPage::remset_current() {
  return _remembered_set.current();
}

void ZPage::print_on_msg(outputStream* st, const char* msg) const {
  st->print_cr("%-6s  " PTR_FORMAT " " PTR_FORMAT " " PTR_FORMAT " %s/%-4u %s%s%s%s",
                type_to_string(), untype(start()), untype(top()), untype(end()),
                is_young() ? "Y" : "O",
                seqnum(),
                is_relocatable() ? " Relocatable" : "",
                is_allocating()  ? " Allocating"  : "",
                is_allocating() && msg != nullptr ? " " : "",
                msg != nullptr ? msg : "");
}

void ZPage::print_on(outputStream* st) const {
  print_on_msg(st, nullptr);
}

void ZPage::print() const {
  print_on(tty);
}

void ZPage::verify_live(uint32_t live_objects, size_t live_bytes, bool in_place) const {
  if (!in_place) {
    // In-place relocation has changed the page to allocating
    assert_zpage_mark_state();
  }
  guarantee(live_objects == _livemap.live_objects(), "Invalid number of live objects");
  guarantee(live_bytes == _livemap.live_bytes(), "Invalid number of live bytes");
}

void ZPage::fatal_msg(const char* msg) const {
  stringStream ss;
  print_on_msg(&ss, msg);
  fatal("%s", ss.base());
}
