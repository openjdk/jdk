/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1COLLECTORPOLICY_HPP
#define SHARE_VM_GC_G1_G1COLLECTORPOLICY_HPP

#include "gc/shared/collectorPolicy.hpp"

// G1CollectorPolicy is primarily used during initialization and to expose the
// functionality of the CollectorPolicy interface to the rest of the VM.

class G1YoungGenSizer;

class G1CollectorPolicy: public CollectorPolicy {
protected:
  void initialize_alignments();
  void initialize_flags();

public:
  G1CollectorPolicy();

  G1CollectorPolicy* as_g1_policy() { return this; }

  void post_heap_initialize() {} // Nothing needed.

  // Create jstat counters for the policy.
  virtual void initialize_gc_policy_counters();
};

#endif // SHARE_VM_GC_G1_G1COLLECTORPOLICY_HPP
