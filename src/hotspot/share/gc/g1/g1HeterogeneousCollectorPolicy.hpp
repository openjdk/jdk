/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1HETEROGENEOUSCOLLECTORPOLICY_HPP
#define SHARE_GC_G1_G1HETEROGENEOUSCOLLECTORPOLICY_HPP

#include "gc/g1/g1CollectorPolicy.hpp"
#include "gc/g1/g1HeterogeneousHeapYoungGenSizer.hpp"

class G1HeterogeneousCollectorPolicy : public G1CollectorPolicy {
private:
  // Max fraction of dram to use for young generation when MaxRAMFraction and
  // MaxRAMPercentage are not specified on commandline.
  static const double MaxRamFractionForYoung;
  static size_t MaxMemoryForYoung;

protected:
  virtual void initialize_flags();

public:
  G1HeterogeneousCollectorPolicy() {}
  virtual size_t heap_reserved_size_bytes() const;
  virtual bool is_heterogeneous_heap() const;
  static size_t reasonable_max_memory_for_young();
};

#endif // SHARE_GC_G1_G1HETEROGENEOUSCOLLECTORPOLICY_HPP
