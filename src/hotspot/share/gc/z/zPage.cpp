/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/z/zCycle.inline.hpp"
#include "gc/z/zList.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPhysicalMemory.inline.hpp"
#include "gc/z/zRememberSet.inline.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "logging/logStream.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/growableArray.hpp"

ZPage::ZPage(const ZVirtualMemory& vmem, const ZPhysicalMemory& pmem) :
    ZPage(type_from_size(vmem.size()), vmem, pmem) {}

ZPage::ZPage(uint8_t type, const ZVirtualMemory& vmem, const ZPhysicalMemory& pmem) :
    _type(type),
    _generation_id(ZGenerationId::old),
    _age(ZPageAge::eden),
    _numa_id((uint8_t)-1),
    _seqnum(0),
    _seqnum_other(0),
    _virtual(vmem),
    _top(start()),
    _livemap(object_max_count()),
    _remember_set(size()),
    _last_used(0),
    _physical(pmem),
    _node() {
  assert(!_virtual.is_null(), "Should not be null");
  assert(!_physical.is_null(), "Should not be null");
  assert(_virtual.size() == _physical.size(), "Virtual/Physical size mismatch");
  assert((_type == ZPageTypeSmall && size() == ZPageSizeSmall) ||
         (_type == ZPageTypeMedium && size() == ZPageSizeMedium) ||
         (_type == ZPageTypeLarge && is_aligned(size(), ZGranuleSize)),
         "Page type/size mismatch");
}

ZPage::ZPage(const ZPage& other) :
    _type(other._type),
    _generation_id(other._generation_id),
    _age(other._age),
    _numa_id(other._numa_id),
    _seqnum(other._seqnum),
    _seqnum_other(other._seqnum_other),
    _virtual(other._virtual),
    _top(other._top),
    _livemap(other._livemap),
    _remember_set(size()),
    _last_used(other._last_used),
    _physical(other._physical),
    _node() {}

ZPage::~ZPage() {}

void ZPage::reset_seqnum(ZGenerationId generation_id) {
  Atomic::store(&_seqnum, ZHeap::heap()->get_cycle(generation_id)->seqnum());
  Atomic::store(&_seqnum_other, ZHeap::heap()->get_cycle(generation_id == ZGenerationId::young ? ZGenerationId::old : ZGenerationId::young)->seqnum());
}

void ZPage::reset(ZGenerationId generation_id, ZPageAge age, ZPage::ZPageResetType type) {
  ZPageAge prev_age = _age;
  _age = age;
  _last_used = 0;
  _generation_id = generation_id;
  reset_seqnum(_generation_id);

  if (type != FlipReset) {
    _top = start();
  }

  if (is_old()) {
    if (type == InPlaceReset && prev_age == ZPageAge::old) {
      // Current bits are needed to copy the remset incrementally. It will get
      // cleared later on.
      _remember_set.clear_previous();
    } else {
      _remember_set.reset();
    }
  }

  if (type != InPlaceReset || (prev_age != ZPageAge::old && age == ZPageAge::old)) {
    // Promoted in-place relocations reset the live map,
    // because they clone the page.
    _livemap.reset();
  }
}

void ZPage::finalize_reset_for_in_place_relocation() {
  // Now we're done iterating over the livemaps
  _livemap.reset();
}

ZPage* ZPage::retype(uint8_t type) {
  assert(_type != type, "Invalid retype");
  _type = type;
  _livemap.resize(object_max_count());
  return this;
}

ZPage* ZPage::split(size_t split_of_size) {
  return split(type_from_size(split_of_size), split_of_size);
}

ZPage* ZPage::split(uint8_t type, size_t split_of_size) {
  assert(_virtual.size() > split_of_size, "Invalid split");

  // Resize this page, keep _numa_id, _seqnum, and _last_used
  const ZVirtualMemory vmem = _virtual.split(split_of_size);
  const ZPhysicalMemory pmem = _physical.split(split_of_size);
  _type = type_from_size(_virtual.size());
  _top = start();
  _livemap.resize(object_max_count());
  _remember_set.resize(size());

  // Create new page, inherit _seqnum and _last_used
  ZPage* const page = new ZPage(type, vmem, pmem);
  page->_seqnum = _seqnum;
  page->_last_used = _last_used;

  log_debug(gc, heap)("Split page [" PTR_FORMAT ", " PTR_FORMAT "] [" PTR_FORMAT ", " PTR_FORMAT "]",
      untype(vmem.start()),
      untype(vmem.end()),
      untype(_virtual.start()),
      untype(_virtual.end()));

  return page;
}

ZPage* ZPage::split_committed() {
  // Split any committed part of this page into a separate page,
  // leaving this page with only uncommitted physical memory.
  const ZPhysicalMemory pmem = _physical.split_committed();
  if (pmem.is_null()) {
    // Nothing committed
    return NULL;
  }

  assert(!_physical.is_null(), "Should not be null");

  // Resize this page
  const ZVirtualMemory vmem = _virtual.split(pmem.size());
  _type = type_from_size(_virtual.size());
  _top = start();
  _livemap.resize(object_max_count());
  _remember_set.resize(size());

  // Create new page
  return new ZPage(vmem, pmem);
}

class ZFindBaseOopClosure : public ObjectClosure {
private:
  volatile zpointer* _p;
  oop _result;

public:
  ZFindBaseOopClosure(volatile zpointer* p) :
      _p(p),
      _result(NULL) {}

  virtual void do_object(oop obj) {
    uintptr_t p_int = reinterpret_cast<uintptr_t>(_p);
    uintptr_t base_int = cast_from_oop<uintptr_t>(obj);
    uintptr_t end_int = base_int + wordSize * obj->size();
    if (p_int >= base_int && p_int < end_int) {
      _result = obj;
    }
  }

  oop result() const { return _result; }
};

void ZPage::clear_current_remembered() {
 _remember_set.clear_current();
}

void ZPage::clear_previous_remembered() {
 _remember_set.clear_previous();
}

void ZPage::log_msg(const char* msg) const {
  LogStreamHandle(Debug, gc, reloc) handle;
  print_on_msg(&handle, msg);
}

void ZPage::print_on_msg(outputStream* out, const char* msg) const {
  out->print_cr(" %-6s  " PTR_FORMAT " " PTR_FORMAT " " PTR_FORMAT " %s/%-4u %s%s%s",
                type_to_string(), untype(start()), untype(top()), untype(end()),
                is_young() ? "Y" : "O",
                seqnum(),
                is_allocating()  ? " Allocating " : "",
                is_relocatable() ? " Relocatable" : "",
                msg == NULL ? "" : msg);
}

void ZPage::print_on(outputStream* out) const {
  print_on_msg(out, NULL);
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
