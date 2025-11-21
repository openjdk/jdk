/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHTRACE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHTRACE_HPP

#include "gc/shared/gcTrace.hpp"
#include "memory/allocation.hpp"

class ShenandoahCollectionSet;

class ShenandoahTracer : public GCTracer, public CHeapObj<mtGC> {
public:
  ShenandoahTracer() : GCTracer(Shenandoah) {}

  // Sends a JFR event (if enabled) summarizing the composition of the collection set
  static void report_evacuation_info(const ShenandoahCollectionSet* cset,
    size_t free_regions, size_t regions_promoted_humongous, size_t regions_promoted_regular,
    size_t regular_promoted_garbage, size_t regular_promoted_free, size_t regions_immediate,
    size_t immediate_size);
};

#endif
