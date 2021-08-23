/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zCollector.inline.hpp"
#include "gc/z/zStoreBarrierBuffer.inline.hpp"
#include "gc/z/zUncoloredRoot.inline.hpp"
#include "runtime/threadSMR.hpp"

ByteSize ZStoreBarrierEntry::p_offset() {
  return byte_offset_of(ZStoreBarrierEntry, _p);
}

ByteSize ZStoreBarrierEntry::prev_offset() {
  return byte_offset_of(ZStoreBarrierEntry, _prev);
}

ByteSize ZStoreBarrierBuffer::buffer_offset() {
  return byte_offset_of(ZStoreBarrierBuffer, _buffer);
}

ByteSize ZStoreBarrierBuffer::current_offset() {
  return byte_offset_of(ZStoreBarrierBuffer, _current);
}

ZStoreBarrierBuffer::ZStoreBarrierBuffer() :
    _buffer(),
    _last_processed_color(),
    _last_installed_color(),
    _base_pointer_lock(),
    _base_pointers(),
    _current(ZBufferStoreBarriers ? _buffer_size_bytes : 0) {
}

void ZStoreBarrierBuffer::initialize() {
  _last_processed_color = ZAddressStoreGoodMask;
  _last_installed_color = ZAddressStoreGoodMask;
}

void ZStoreBarrierBuffer::clear() {
  _current = _buffer_size_bytes;
}

bool ZStoreBarrierBuffer::is_empty() {
  return _current == _buffer_size_bytes;
}

void ZStoreBarrierBuffer::install_base_pointers() {
  if (!ZBufferStoreBarriers) {
    return;
  }

  ZLocker<ZLock> locker(&_base_pointer_lock);
  uintptr_t last_installed_remap_bits = _last_installed_color & ZAddressRemappedMask;
  if (last_installed_remap_bits == ZAddressRemapped || is_empty()) {
    // Already installed or empty - nothing to do, but update installed color
    _last_installed_color = ZAddressStoreGoodMask;
    return;
  }
  assert(last_installed_remap_bits == (_last_processed_color & ZAddressRemappedMask),
         "can't deal with two pending base pointer installations");
  for (size_t i = current(); i < _buffer_length; ++i) {
    assert(((_last_installed_color) & ZAddressRemappedMinorMask) == 0 ||
           ((_last_installed_color) & ZAddressRemappedMajorMask) == 0,
           "Should not have double bit errors");
    const ZStoreBarrierEntry& entry = _buffer[i];
    volatile zpointer* p = entry._p;
    zaddress_unsafe p_unsafe = (zaddress_unsafe)(uintptr_t)p;
    zpointer ptr = ZAddress::color(safe(p_unsafe), _last_installed_color);
    ZCollector* remap_collector = ZHeap::heap()->remap_collector(ptr);
    ZForwarding* forwarding = remap_collector->forwarding(p_unsafe);
    if (forwarding != NULL) {
      ZPage* page = forwarding->page();
      _base_pointers[i] = page->find_base(p);
    } else {
      _base_pointers[i] = zaddress_unsafe::null;
    }
  }
  _last_installed_color = ZAddressStoreGoodMask;
}

static volatile zpointer* make_load_good(volatile zpointer* p, zaddress_unsafe p_base, uintptr_t color) {
  assert(!is_null(p_base), "need base pointer");
  // Relocating base pointer
  uintptr_t offset = ((uintptr_t)p) - ((uintptr_t)p_base);
  ZUncoloredRoot::process_no_keepalive(&p_base, color);
  zaddress p_base_safe = safe(p_base);
  assert(offset < ZUtils::object_size(p_base_safe),
         "wrong base object; live bits are invalid");
  return (volatile zpointer*)(((uintptr_t)p_base_safe) + offset);
}

void ZStoreBarrierBuffer::on_new_phase_relocate(int i) {
  uintptr_t last_remap_bits = _last_processed_color & ZAddressRemappedMask;
  if (last_remap_bits == ZAddressRemapped) {
    // Nothing to process
    return;
  }

  ZStoreBarrierEntry& entry = _buffer[i];
  volatile zpointer* p = entry._p;
  zpointer prev = entry._prev;
  zaddress_unsafe p_base = _base_pointers[i];
  if (!is_null(p_base)) {
    // Relocating base pointer
    entry._p = make_load_good(entry._p, p_base, _last_processed_color);
  }
}

void ZStoreBarrierBuffer::on_new_phase_remember(int i) {
  uintptr_t last_mark_minor_bits = _last_processed_color & (ZAddressMarkedMinor0 | ZAddressMarkedMinor1);
  bool woke_up_in_minor_mark = last_mark_minor_bits != ZAddressMarkedMinor;
  ZStoreBarrierEntry& entry = _buffer[i];
  volatile zpointer* p = entry._p;

  if (woke_up_in_minor_mark) {
    if (ZHeap::heap()->is_old((zaddress)(uintptr_t)p)) {
      ZHeap::heap()->young_generation()->mark_and_remember(p);
    }
  } else {
    ZHeap::heap()->remember_filtered(p);
  }
}

bool ZStoreBarrierBuffer::is_inside_marking_snapshot(volatile zpointer* p) {
  bool during_major_marking = ZHeap::heap()->major_collector()->phase() == ZPhase::Mark;

  if (!during_major_marking) {
    return false;
  }

  uintptr_t last_mark_major_bits = _last_processed_color & (ZAddressMarkedMajor0 | ZAddressMarkedMajor1);
  bool stored_during_major_marking = last_mark_major_bits == ZAddressMarkedMajor;

  if (!stored_during_major_marking) {
    return false;
  }

  bool p_is_old = ZHeap::heap()->is_old((zaddress)(uintptr_t)p);

  return p_is_old;
}

void ZStoreBarrierBuffer::on_new_phase_mark(int i) {
  ZStoreBarrierEntry& entry = _buffer[i];
  volatile zpointer* p = entry._p;
  zpointer prev = entry._prev;

  if (!is_null_any(prev) && is_inside_marking_snapshot(p)) {
    const zaddress addr = ZBarrier::make_load_good(prev);
    ZUncoloredRoot::mark_object(addr);
  }
}

void ZStoreBarrierBuffer::on_new_phase() {
  if (!ZBufferStoreBarriers) {
    return;
  }

  install_base_pointers();

  for (size_t i = current(); i < _buffer_length; ++i) {
    on_new_phase_relocate(i);
    on_new_phase_remember(i);
    on_new_phase_mark(i);
  }

  clear();

  _last_processed_color = ZAddressStoreGoodMask;
  assert(_last_installed_color == _last_processed_color, "invariant");
}

void ZStoreBarrierBuffer::flush() {
  if (!ZBufferStoreBarriers) {
    return;
  }

  for (size_t i = current(); i < _buffer_length; ++i) {
    const ZStoreBarrierEntry& entry = _buffer[i];
    const zaddress addr = ZBarrier::make_load_good(entry._prev);
    ZBarrier::mark_and_remember(entry._p, addr);
  }

  clear();
}

bool ZStoreBarrierBuffer::is_in(volatile zpointer* p) {
  if (!ZBufferStoreBarriers) {
    return false;
  }

  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *jt = jtiwh.next(); ) {
    ZStoreBarrierBuffer* buffer = ZThreadLocalData::store_barrier_buffer(jt);

    uintptr_t last_remap_bits = buffer->_last_processed_color & ZAddressRemappedMask;
    bool needs_remap = last_remap_bits != ZAddressRemapped;

    for (size_t i = buffer->current(); i < _buffer_length; ++i) {
      const ZStoreBarrierEntry& entry = buffer->_buffer[i];
      volatile zpointer* entry_p = entry._p;

      // Potentially remap p
      if (needs_remap) {
        zaddress_unsafe entry_p_base = buffer->_base_pointers[i];
        if (!is_null(entry_p_base)) {
          entry_p = make_load_good(entry_p, entry_p_base, buffer->_last_processed_color);
        }
      }

      // Check if p matches
      if (entry._p == p) {
        return true;
      }
    }
  }

  return false;
}
