/*
 * Copyright (c) 2017, 2018, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHALLOCTRACKER_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHALLOCTRACKER_HPP

#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahNumberSeq.hpp"
#include "memory/allocation.hpp"
#include "utilities/ostream.hpp"

class ShenandoahAllocTracker : public CHeapObj<mtGC> {
private:
  BinaryMagnitudeSeq _alloc_size[ShenandoahAllocRequest::_ALLOC_LIMIT];
  BinaryMagnitudeSeq _alloc_latency[ShenandoahAllocRequest::_ALLOC_LIMIT];

public:
  void record_alloc_latency(size_t words_size,
                            ShenandoahAllocRequest::Type _alloc_type,
                            double latency_us) {
    _alloc_size[_alloc_type].add(words_size);
    _alloc_latency[_alloc_type].add((size_t)latency_us);
  }

  void print_on(outputStream* out) const;
};

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHALLOCTRACKER_HPP
