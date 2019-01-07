/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_PARALLEL_GENERATIONSIZER_HPP
#define SHARE_VM_GC_PARALLEL_GENERATIONSIZER_HPP

#include "gc/shared/collectorPolicy.hpp"

// There is a nice batch of tested generation sizing code in
// GenCollectorPolicy. Lets reuse it!

class GenerationSizer : public GenCollectorPolicy {
 private:
  // The alignment used for boundary between young gen and old gen
  static size_t default_gen_alignment() { return 64 * K * HeapWordSize; }

 protected:

  void initialize_alignments();
  void initialize_flags();
  void initialize_size_info();

 public:
  virtual size_t heap_reserved_size_bytes() const;
  virtual bool is_hetero_heap() const;
};
#endif // SHARE_VM_GC_PARALLEL_GENERATIONSIZER_HPP
