/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_APROFILER_HPP
#define SHARE_VM_RUNTIME_APROFILER_HPP

#include "memory/allocation.hpp"
#include "memory/universe.hpp"
#include "oops/klass.hpp"
#include "oops/klassOop.hpp"
#include "utilities/top.hpp"

// A simple allocation profiler for Java. The profiler collects and prints
// the number and total size of instances allocated per class, including
// array classes.
//
// The profiler is currently global for all threads. It can be changed to a
// per threads profiler by keeping a more elaborate data structure and calling
// iterate_since_last_scavenge at thread switches.


class AllocationProfiler: AllStatic {
  friend class GenCollectedHeap;
  friend class G1CollectedHeap;
  friend class MarkSweep;
 private:
  static bool _active;                          // tells whether profiler is active
  static GrowableArray<klassOop>* _print_array; // temporary array for printing

  // Utility printing functions
  static void add_class_to_array(klassOop k);
  static void add_classes_to_array(klassOop k);
  static int  compare_classes(klassOop* k1, klassOop* k2);
  static int  average(size_t alloc_size, int alloc_count);
  static void sort_and_print_array(size_t cutoff);

  // Call for collecting allocation information. Called at scavenge, mark-sweep and disengage.
  static void iterate_since_last_gc();

 public:
  // Start profiler
  static void engage();
  // Stop profiler
  static void disengage();
  // Tells whether profiler is active
  static bool is_active()                   { return _active; }
  // Print profile
  static void print(size_t cutoff);   // Cutoff in total allocation size (in words)
};

#endif // SHARE_VM_RUNTIME_APROFILER_HPP
