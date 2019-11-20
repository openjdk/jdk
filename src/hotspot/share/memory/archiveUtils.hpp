/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_ARCHIVEUTILS_HPP
#define SHARE_MEMORY_ARCHIVEUTILS_HPP

#include "logging/log.hpp"
#include "runtime/arguments.hpp"
#include "utilities/bitMap.hpp"

// ArchivePtrMarker is used to mark the location of pointers embedded in a CDS archive. E.g., when an
// InstanceKlass k is dumped, we mark the location of the k->_name pointer by effectively calling
// mark_pointer(/*ptr_loc=*/&k->_name). It's required that (_prt_base <= ptr_loc < _ptr_end). _ptr_base is
// fixed, but _ptr_end can be expanded as more objects are dumped.
class ArchivePtrMarker : AllStatic {
  static CHeapBitMap* _ptrmap;
  static address*     _ptr_base;
  static address*     _ptr_end;

  // Once _ptrmap is compacted, we don't allow bit marking anymore. This is to
  // avoid unintentional copy operations after the bitmap has been finalized and written.
  static bool         _compacted;
public:
  static void initialize(CHeapBitMap* ptrmap, address* ptr_base, address* ptr_end);
  static void mark_pointer(address* ptr_loc);
  static void compact(address relocatable_base, address relocatable_end);
  static void compact(size_t max_non_null_offset);

  template <typename T>
  static void mark_pointer(T* ptr_loc) {
    mark_pointer((address*)ptr_loc);
  }

  static void expand_ptr_end(address *new_ptr_end) {
    assert(_ptr_end <= new_ptr_end, "must be");
    _ptr_end = new_ptr_end;
  }

  static CHeapBitMap* ptrmap() {
    return _ptrmap;
  }
};

// SharedDataRelocator is used to shift pointers in the CDS archive.
//
// The CDS archive is basically a contiguous block of memory (divided into several regions)
// that contains multiple objects. The objects may contain direct pointers that point to other objects
// within the archive (e.g., InstanceKlass::_name points to a Symbol in the archive). During dumping, we
// built a bitmap that marks the locations of all these pointers (using ArchivePtrMarker, see comments above).
//
// The contents of the archive assumes that it’s mapped at the default SharedBaseAddress (e.g. 0x800000000).
// If the archive ends up being mapped at a different address (e.g. 0x810000000), SharedDataRelocator
// is used to shift each marked pointer by a delta (0x10000000 in this example), so that it points to
// the actually mapped location of the target object.
template <bool COMPACTING>
class SharedDataRelocator: public BitMapClosure {
  // for all (address** p), where (is_marked(p) && _patch_base <= p && p < _patch_end) { *p += delta; }

  // Patch all pointers within this region that are marked.
  address* _patch_base;
  address* _patch_end;

  // Before patching, all pointers must point to this region.
  address _valid_old_base;
  address _valid_old_end;

  // After patching, all pointers must point to this region.
  address _valid_new_base;
  address _valid_new_end;

  // How much to relocate for each pointer.
  intx _delta;

  // The following fields are used only when COMPACTING == true;
  // The highest offset (inclusive) in the bitmap that contains a non-null pointer.
  // This is used at dump time to reduce the size of the bitmap (which may have been over-allocated).
  size_t _max_non_null_offset;
  CHeapBitMap* _ptrmap;

 public:
  SharedDataRelocator(address* patch_base, address* patch_end,
                      address valid_old_base, address valid_old_end,
                      address valid_new_base, address valid_new_end, intx delta,
                      CHeapBitMap* ptrmap = NULL) :
    _patch_base(patch_base), _patch_end(patch_end),
    _valid_old_base(valid_old_base), _valid_old_end(valid_old_end),
    _valid_new_base(valid_new_base), _valid_new_end(valid_new_end),
    _delta(delta) {
    log_debug(cds, reloc)("SharedDataRelocator::_patch_base     = " PTR_FORMAT, p2i(_patch_base));
    log_debug(cds, reloc)("SharedDataRelocator::_patch_end      = " PTR_FORMAT, p2i(_patch_end));
    log_debug(cds, reloc)("SharedDataRelocator::_valid_old_base = " PTR_FORMAT, p2i(_valid_old_base));
    log_debug(cds, reloc)("SharedDataRelocator::_valid_old_end  = " PTR_FORMAT, p2i(_valid_old_end));
    log_debug(cds, reloc)("SharedDataRelocator::_valid_new_base = " PTR_FORMAT, p2i(_valid_new_base));
    log_debug(cds, reloc)("SharedDataRelocator::_valid_new_end  = " PTR_FORMAT, p2i(_valid_new_end));
    if (COMPACTING) {
      assert(ptrmap != NULL, "must be");
      _max_non_null_offset = 0;
      _ptrmap = ptrmap;
    } else {
      // Don't touch the _max_non_null_offset and _ptrmap fields. Hopefully a good C++ compiler can
      // elide them.
      assert(ptrmap == NULL, "must be");
    }
  }

  size_t max_non_null_offset() {
    assert(COMPACTING, "must be");
    return _max_non_null_offset;
  }

  inline bool do_bit(size_t offset);
};


#endif // SHARE_MEMORY_ARCHIVEUTILS_HPP
