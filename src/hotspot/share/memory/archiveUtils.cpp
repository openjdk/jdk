/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/archiveUtils.hpp"
#include "memory/metaspace.hpp"
#include "utilities/bitMap.inline.hpp"

#if INCLUDE_CDS

CHeapBitMap* ArchivePtrMarker::_ptrmap = NULL;
address* ArchivePtrMarker::_ptr_base;
address* ArchivePtrMarker::_ptr_end;
bool ArchivePtrMarker::_compacted;

void ArchivePtrMarker::initialize(CHeapBitMap* ptrmap, address* ptr_base, address* ptr_end) {
  assert(_ptrmap == NULL, "initialize only once");
  _ptr_base = ptr_base;
  _ptr_end = ptr_end;
  _compacted = false;
  _ptrmap = ptrmap;

  // Use this as initial guesstimate. We should need less space in the
  // archive, but if we're wrong the bitmap will be expanded automatically.
  size_t estimated_archive_size = MetaspaceGC::capacity_until_GC();
  // But set it smaller in debug builds so we always test the expansion code.
  // (Default archive is about 12MB).
  DEBUG_ONLY(estimated_archive_size = 6 * M);

  // We need one bit per pointer in the archive.
  _ptrmap->initialize(estimated_archive_size / sizeof(intptr_t));
}

void ArchivePtrMarker::mark_pointer(address* ptr_loc) {
  assert(_ptrmap != NULL, "not initialized");
  assert(!_compacted, "cannot mark anymore");

  if (_ptr_base <= ptr_loc && ptr_loc < _ptr_end) {
    address value = *ptr_loc;
    // We don't want any pointer that points to very bottom of the archive, otherwise when
    // MetaspaceShared::default_base_address()==0, we can't distinguish between a pointer
    // to nothing (NULL) vs a pointer to an objects that happens to be at the very bottom
    // of the archive.
    assert(value != (address)_ptr_base, "don't point to the bottom of the archive");

    if (value != NULL) {
      assert(uintx(ptr_loc) % sizeof(intptr_t) == 0, "pointers must be stored in aligned addresses");
      size_t idx = ptr_loc - _ptr_base;
      if (_ptrmap->size() <= idx) {
        _ptrmap->resize((idx + 1) * 2);
      }
      assert(idx < _ptrmap->size(), "must be");
      _ptrmap->set_bit(idx);
      //tty->print_cr("Marking pointer [%p] -> %p @ " SIZE_FORMAT_W(9), ptr_loc, *ptr_loc, idx);
    }
  }
}

class ArchivePtrBitmapCleaner: public BitMapClosure {
  CHeapBitMap* _ptrmap;
  address* _ptr_base;
  address  _relocatable_base;
  address  _relocatable_end;
  size_t   _max_non_null_offset;

public:
  ArchivePtrBitmapCleaner(CHeapBitMap* ptrmap, address* ptr_base, address relocatable_base, address relocatable_end) :
    _ptrmap(ptrmap), _ptr_base(ptr_base),
    _relocatable_base(relocatable_base), _relocatable_end(relocatable_end), _max_non_null_offset(0) {}

  bool do_bit(size_t offset) {
    address* ptr_loc = _ptr_base + offset;
    address  ptr_value = *ptr_loc;
    if (ptr_value != NULL) {
      assert(_relocatable_base <= ptr_value && ptr_value < _relocatable_end, "do not point to arbitrary locations!");
      if (_max_non_null_offset < offset) {
        _max_non_null_offset = offset;
      }
    } else {
      _ptrmap->clear_bit(offset);
      DEBUG_ONLY(log_trace(cds, reloc)("Clearing pointer [" PTR_FORMAT  "] -> NULL @ " SIZE_FORMAT_W(9), p2i(ptr_loc), offset));
    }

    return true;
  }

  size_t max_non_null_offset() const { return _max_non_null_offset; }
};

void ArchivePtrMarker::compact(address relocatable_base, address relocatable_end) {
  assert(!_compacted, "cannot compact again");
  ArchivePtrBitmapCleaner cleaner(_ptrmap, _ptr_base, relocatable_base, relocatable_end);
  _ptrmap->iterate(&cleaner);
  compact(cleaner.max_non_null_offset());
}

void ArchivePtrMarker::compact(size_t max_non_null_offset) {
  assert(!_compacted, "cannot compact again");
  _ptrmap->resize(max_non_null_offset + 1);
  _compacted = true;
}

#endif // INCLUDE_CDS
