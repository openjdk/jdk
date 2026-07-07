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

#include "gc/shenandoah/shenandoahStripedCounter.hpp"
#include "memory/padded.inline.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"

// The constructor and destructor are defined out-of-line (rather than in the .inline.hpp) so that
// translation units which construct or destroy a ShenandoahStripedCounter -- possibly only via an
// enclosing object such as ShenandoahHeap -- resolve them without having to include the inline
// header. The hot-path methods (add/sum/drain/current_stripe) remain inline.

ShenandoahStripedCounter::ShenandoahStripedCounter()
  : _striped(false)
    // Round the CPU count down to a power of two so current_stripe() can mask instead of modulo.
    // Rounding down keeps the count <= number of cores. At least 1 stripe.
  , _num_stripes(round_down_power_of_2((uint) MAX2(os::processor_count(), 1)))
  , _stripe_mask(_num_stripes - 1)
  , _log_num_stripes(log2i_exact(_num_stripes)) {
  // create_unfreeable aligns both the base and per-element stride to a cache line and
  // default-constructs each Atomic to 0.
  _stripes = PaddedArray<Atomic<size_t>, mtGC>::create_unfreeable(_num_stripes);
}

ShenandoahStripedCounter::~ShenandoahStripedCounter() {
  // _stripes is created "unfreeable" (raw chunk not tracked); nothing to free. Counters live as long
  // as the owner, which for the sole current user (per-heap alloc rate) is the process lifetime.
}
