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

ShenandoahStripedCounter::ShenandoahStripedCounter()
  : _num_stripes(round_down_power_of_2((uint32_t) MAX2(os::processor_count(), 1)))
  , _stripe_mask(_num_stripes - 1)
  , _log_num_stripes(log2i_exact(_num_stripes)) {
  _stripes = PaddedArray<Atomic<size_t>, mtGC>::create_unfreeable(_num_stripes);
}

ShenandoahStripedCounter::~ShenandoahStripedCounter() { }
