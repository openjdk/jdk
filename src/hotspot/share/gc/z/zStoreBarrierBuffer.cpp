/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gc_globals.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zStoreBarrierBuffer.inline.hpp"
#include "gc/z/zUncoloredRoot.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/threadSMR.hpp"
#include "utilities/ostream.hpp"
#include "utilities/vmError.hpp"

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

ZStoreBarrierBuffer::ZStoreBarrierBuffer()
  : _buffer(),
    _last_processed_color(),
    _last_installed_color(),
    _base_pointer_lock(),
    _base_pointers(),
    _current(ZBufferStoreBarriers ? _buffer_size_bytes : 0) {}

void ZStoreBarrierBuffer::initialize() {
  _last_processed_color = ZPointerStoreGoodMask;
  _last_installed_color = ZPointerStoreGoodMask;
}

void ZStoreBarrierBuffer::clear() {
  _current = _buffer_size_bytes;
}

bool ZStoreBarrierBuffer::is_empty() const {
  return _current == _buffer_size_bytes;
}

void ZStoreBarrierBuffer::install_base_pointers_inner() {
  assert(ZPointer::remap_bits(_last_installed_color) ==
         ZPointer::remap_bits(_last_processed_color),
         "Can't deal with two pending base pointer installations");

  assert((ZPointer::remap_bits(_last_processed_color) & ZPointerRemappedYoungMask) == 0 ||
         (ZPointer::remap_bits(_last_processed_color) & ZPointerRemappedOldMask) == 0,
         "Should not have double bit errors");

  for (size_t i = current(); i < _buffer_length; ++i) {
    const ZStoreBarrierEntry& entry = _buffer[i];
    volatile zpointer* const p = entry._p;
    const zaddress_unsafe p_unsafe = to_zaddress_unsafe((uintptr_t)p);

    // Color with the last processed color
    const zpointer ptr = ZAddress::color(p_unsafe, _last_processed_color);

    // Look up the generation that thinks that this pointer is not
    // load good and check if the page is being relocated.
    ZGeneration* const remap_generation = ZBarrier::remap_generation(ptr);
    ZForwarding* const forwarding = remap_generation->forwarding(p_unsafe);
    if (forwarding != nullptr) {
      // Page is being relocated
      ZPage* const page = forwarding->page();
      _base_pointers[i] = page->find_base(p);
    } else {
      // Page is not being relocated
      _base_pointers[i] = zaddress_unsafe::null;
    }
  }
}

void ZStoreBarrierBuffer::install_base_pointers() {
  if (!ZBufferStoreBarriers) {
    return;
  }

  // Use a lock since both the GC and the Java thread race to install the base pointers
  ZLocker<ZLock> locker(&_base_pointer_lock);

  const bool should_install_base_pointers = ZPointer::remap_bits(_last_installed_color) != ZPointerRemapped;

  if (should_install_base_pointers) {
    install_base_pointers_inner();
  }

  // This is used as a claim mechanism to make sure that we only install the base pointers once
  _last_installed_color = ZPointerStoreGoodMask;
}

static volatile zpointer* make_load_good(volatile zpointer* p, zaddress_unsafe p_base, uintptr_t color) {
  assert(!is_null(p_base), "need base pointer");

  // Calculate field offset before p_base is remapped
  const uintptr_t offset = (uintptr_t)p - untype(p_base);

  // Remap local-copy of base pointer
  ZUncoloredRoot::process_no_keepalive(&p_base, color);

  // Retype now that the address is known to point to the correct address
  const zaddress p_base_remapped = safe(p_base);

  assert(offset < ZUtils::object_size(p_base_remapped),
         "wrong base object; live bits are invalid");

  // Calculate remapped field address
  const zaddress p_remapped = to_zaddress(untype(p_base_remapped) + offset);

  return (volatile zpointer*)p_remapped;
}

void ZStoreBarrierBuffer::on_new_phase_relocate(size_t i) {
  const uintptr_t last_remap_bits = ZPointer::remap_bits(_last_processed_color);
  if (last_remap_bits == ZPointerRemapped) {
    // All pointers are already remapped
    return;
  }

  const zaddress_unsafe p_base = _base_pointers[i];
  if (is_null(p_base)) {
    // Page is not part of the relocation set
    return;
  }

  ZStoreBarrierEntry& entry = _buffer[i];

  // Relocate the base object and calculate the remapped p
  entry._p = make_load_good(entry._p, p_base, _last_processed_color);
}

void ZStoreBarrierBuffer::on_new_phase_remember(size_t i) {
  volatile zpointer* const p = _buffer[i]._p;

  if (ZHeap::heap()->is_young(p)) {
    // Only need remset entries for old objects
    return;
  }

  const uintptr_t last_mark_young_bits = _last_processed_color & (ZPointerMarkedYoung0 | ZPointerMarkedYoung1);
  const bool woke_up_in_young_mark = last_mark_young_bits != ZPointerMarkedYoung;

  if (woke_up_in_young_mark) {
    // When young mark starts we "flip" the remembered sets. The remembered
    // sets used before the young mark start becomes read-only and used by
    // the GC to scan for old-to-young pointers to use as marking roots.
    //
    // Entries in the store buffer that were added before the mark young start,
    // were supposed to be part of the remembered sets that the GC scans.
    // However, it is too late to add those entries at this point, so instead
    // we perform the GC remembered set scanning up-front here.
    ZGeneration::young()->scan_remembered_field(p);
  } else {
    // The remembered set wasn't flipped in this phase shift,
    // so just add the remembered set entry.
    ZGeneration::young()->remember(p);
  }
}

bool ZStoreBarrierBuffer::is_old_mark() const {
  return ZGeneration::old()->is_phase_mark();
}

bool ZStoreBarrierBuffer::stored_during_old_mark() const {
  const uintptr_t last_mark_old_bits = _last_processed_color & (ZPointerMarkedOld0 | ZPointerMarkedOld1);
  return last_mark_old_bits == ZPointerMarkedOld;
}

void ZStoreBarrierBuffer::on_new_phase_mark(size_t i) {
  const ZStoreBarrierEntry& entry = _buffer[i];
  const zpointer prev = entry._prev;

  if (is_null_any(prev)) {
    return;
  }

  volatile zpointer* const p = entry._p;

  // Young collections can start during old collections, but not the other
  // way around. Therefore, only old marking can see a collection phase
  // shift (resulting in a call to this function).
  //
  // Stores before the marking phase started is not a part of the SATB snapshot,
  // and therefore shouldn't be used for marking.
  //
  // Locations in the young generation are not part of the old marking.
  if (is_old_mark() && stored_during_old_mark() && ZHeap::heap()->is_old(p)) {
    const zaddress addr = ZBarrier::make_load_good(prev);
    ZUncoloredRoot::mark_object(addr);
  }
}

void ZStoreBarrierBuffer::on_new_phase() {
  if (!ZBufferStoreBarriers) {
    return;
  }

  // Install all base pointers for relocation
  install_base_pointers();

  for (size_t i = current(); i < _buffer_length; ++i) {
    on_new_phase_relocate(i);
    on_new_phase_remember(i);
    on_new_phase_mark(i);
  }

  clear();

  _last_processed_color = ZPointerStoreGoodMask;
  assert(_last_installed_color == _last_processed_color, "invariant");
}

class ZStoreBarrierBuffer::OnError : public VMErrorCallback {
private:
  ZStoreBarrierBuffer* _buffer;

public:
  OnError(ZStoreBarrierBuffer* buffer)
    : _buffer(buffer) {}

  virtual void call(outputStream* st) {
    _buffer->on_error(st);
  }
};

void ZStoreBarrierBuffer::on_error(outputStream* st) {
  st->print_cr("ZStoreBarrierBuffer: error when flushing");
  st->print_cr(" _last_processed_color: " PTR_FORMAT, _last_processed_color);
  st->print_cr(" _last_installed_color: " PTR_FORMAT, _last_installed_color);

  for (size_t i = current(); i < _buffer_length; ++i) {
    st->print_cr(" [%2zu]: base: " PTR_FORMAT " p: " PTR_FORMAT " prev: " PTR_FORMAT,
        i,
        untype(_base_pointers[i]),
        p2i(_buffer[i]._p),
        untype(_buffer[i]._prev));
  }
}

void ZStoreBarrierBuffer::flush() {
  if (!ZBufferStoreBarriers) {
    return;
  }

  OnError on_error(this);
  VMErrorCallbackMark mark(&on_error);

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

  for (JavaThreadIteratorWithHandle jtiwh; JavaThread * const jt = jtiwh.next(); ) {
    ZStoreBarrierBuffer* const buffer = ZThreadLocalData::store_barrier_buffer(jt);

    const uintptr_t  last_remap_bits = ZPointer::remap_bits(buffer->_last_processed_color) & ZPointerRemappedMask;
    const bool needs_remap = last_remap_bits != ZPointerRemapped;

    for (size_t i = buffer->current(); i < _buffer_length; ++i) {
      const ZStoreBarrierEntry& entry = buffer->_buffer[i];
      volatile zpointer* entry_p = entry._p;

      // Potentially remap p
      if (needs_remap) {
        const zaddress_unsafe entry_p_base = buffer->_base_pointers[i];
        if (!is_null(entry_p_base)) {
          entry_p = make_load_good(entry_p, entry_p_base, buffer->_last_processed_color);
        }
      }

      // Check if p matches
      if (entry_p == p) {
        return true;
      }
    }
  }

  return false;
}
