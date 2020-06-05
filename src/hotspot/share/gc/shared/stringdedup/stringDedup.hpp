/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_STRINGDEDUP_STRINGDEDUP_HPP
#define SHARE_GC_SHARED_STRINGDEDUP_STRINGDEDUP_HPP

//
// String Deduplication
//
// String deduplication aims to reduce the heap live-set by deduplicating identical
// instances of String so that they share the same backing character array.
//
// The deduplication process is divided in two main parts, 1) finding the objects to
// deduplicate, and 2) deduplicating those objects. The first part is done as part of
// a normal GC cycle when objects are marked or evacuated. At this time a check is
// applied on each object to check if it is a candidate for deduplication. If so, the
// object is placed on the deduplication queue for later processing. The second part,
// processing the objects on the deduplication queue, is a concurrent phase which
// starts right after the stop-the-wold marking/evacuation phase. This phase is
// executed by the deduplication thread, which pulls deduplication candidates of the
// deduplication queue and tries to deduplicate them.
//
// A deduplication hashtable is used to keep track of all unique character arrays
// used by String objects. When deduplicating, a lookup is made in this table to see
// if there is already an identical character array somewhere on the heap. If so, the
// String object is adjusted to point to that character array, releasing the reference
// to the original array allowing it to eventually be garbage collected. If the lookup
// fails the character array is instead inserted into the hashtable so that this array
// can be shared at some point in the future.
//
// Candidate selection criteria is GC specific.
//
// Interned strings are a bit special. They are explicitly deduplicated just before
// being inserted into the StringTable (to avoid counteracting C2 optimizations done
// on string literals), then they also become deduplication candidates if they reach
// the deduplication age threshold or are evacuated to an old heap region. The second
// attempt to deduplicate such strings will be in vain, but we have no fast way of
// filtering them out. This has not shown to be a problem, as the number of interned
// strings is usually dwarfed by the number of normal (non-interned) strings.
//
// For additional information on string deduplication, please see JEP 192,
// http://openjdk.java.net/jeps/192
//

#include "gc/shared/stringdedup/stringDedupQueue.hpp"
#include "gc/shared/stringdedup/stringDedupStat.hpp"
#include "gc/shared/stringdedup/stringDedupTable.hpp"
#include "memory/allocation.hpp"
#include "runtime/thread.hpp"

class ThreadClosure;

//
// Main interface for interacting with string deduplication.
//
class StringDedup : public AllStatic {
private:
  // Single state for checking if string deduplication is enabled.
  static bool _enabled;

public:
  // Returns true if string deduplication is enabled.
  static bool is_enabled() {
    return _enabled;
  }

  // Stop the deduplication thread.
  static void stop();

  // Immediately deduplicates the given String object, bypassing the
  // the deduplication queue.
  static void deduplicate(oop java_string);

  static void parallel_unlink(StringDedupUnlinkOrOopsDoClosure* unlink, uint worker_id);

  static void threads_do(ThreadClosure* tc);

  static void verify();

  // GC support
  static void gc_prologue(bool resize_and_rehash_table);
  static void gc_epilogue();

protected:
  // Initialize string deduplication.
  // Q: String Dedup Queue implementation
  // S: String Dedup Stat implementation
  template <typename Q, typename S>
  static void initialize_impl();
};

//
// This closure encapsulates the closures needed when scanning
// the deduplication queue and table during the unlink_or_oops_do() operation.
//
class StringDedupUnlinkOrOopsDoClosure : public StackObj {
  AlwaysTrueClosure   _always_true;
  DoNothingClosure    _do_nothing;
  BoolObjectClosure*  _is_alive;
  OopClosure*         _keep_alive;

public:
  StringDedupUnlinkOrOopsDoClosure(BoolObjectClosure* is_alive,
                                   OopClosure* keep_alive);

  bool is_alive(oop o) { return _is_alive->do_object_b(o); }

  void keep_alive(oop* p) { _keep_alive->do_oop(p); }
};

#endif // SHARE_GC_SHARED_STRINGDEDUP_STRINGDEDUP_HPP
