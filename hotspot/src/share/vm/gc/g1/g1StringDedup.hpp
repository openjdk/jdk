/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1STRINGDEDUP_HPP
#define SHARE_VM_GC_G1_G1STRINGDEDUP_HPP

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
// Candidate selection
//
// An object is considered a deduplication candidate if all of the following
// statements are true:
//
// - The object is an instance of java.lang.String
//
// - The object is being evacuated from a young heap region
//
// - The object is being evacuated to a young/survivor heap region and the
//   object's age is equal to the deduplication age threshold
//
//   or
//
//   The object is being evacuated to an old heap region and the object's age is
//   less than the deduplication age threshold
//
// Once an string object has been promoted to an old region, or its age is higher
// than the deduplication age threshold, is will never become a candidate again.
// This approach avoids making the same object a candidate more than once.
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

#include "memory/allocation.hpp"
#include "oops/oop.hpp"

class OopClosure;
class BoolObjectClosure;
class ThreadClosure;
class outputStream;
class G1StringDedupTable;
class G1GCPhaseTimes;

//
// Main interface for interacting with string deduplication.
//
class G1StringDedup : public AllStatic {
private:
  // Single state for checking if both G1 and string deduplication is enabled.
  static bool _enabled;

  // Candidate selection policies, returns true if the given object is
  // candidate for string deduplication.
  static bool is_candidate_from_mark(oop obj);
  static bool is_candidate_from_evacuation(bool from_young, bool to_young, oop obj);

public:
  // Returns true if both G1 and string deduplication is enabled.
  static bool is_enabled() {
    return _enabled;
  }

  // Initialize string deduplication.
  static void initialize();

  // Stop the deduplication thread.
  static void stop();

  // Immediately deduplicates the given String object, bypassing the
  // the deduplication queue.
  static void deduplicate(oop java_string);

  // Enqueues a deduplication candidate for later processing by the deduplication
  // thread. Before enqueuing, these functions apply the appropriate candidate
  // selection policy to filters out non-candidates.
  static void enqueue_from_mark(oop java_string);
  static void enqueue_from_evacuation(bool from_young, bool to_young,
                                      unsigned int queue, oop java_string);

  static void oops_do(OopClosure* keep_alive);
  static void unlink(BoolObjectClosure* is_alive);
  static void unlink_or_oops_do(BoolObjectClosure* is_alive, OopClosure* keep_alive,
                                bool allow_resize_and_rehash, G1GCPhaseTimes* phase_times = NULL);

  static void threads_do(ThreadClosure* tc);
  static void print_worker_threads_on(outputStream* st);
  static void verify();
};

//
// This closure encapsulates the state and the closures needed when scanning
// the deduplication queue and table during the unlink_or_oops_do() operation.
// A single instance of this closure is created and then shared by all worker
// threads participating in the scan. The _next_queue and _next_bucket fields
// provide a simple mechanism for GC workers to claim exclusive access to a
// queue or a table partition.
//
class G1StringDedupUnlinkOrOopsDoClosure : public StackObj {
private:
  BoolObjectClosure*  _is_alive;
  OopClosure*         _keep_alive;
  G1StringDedupTable* _resized_table;
  G1StringDedupTable* _rehashed_table;
  size_t              _next_queue;
  size_t              _next_bucket;

public:
  G1StringDedupUnlinkOrOopsDoClosure(BoolObjectClosure* is_alive,
                                     OopClosure* keep_alive,
                                     bool allow_resize_and_rehash);
  ~G1StringDedupUnlinkOrOopsDoClosure();

  bool is_resizing() {
    return _resized_table != NULL;
  }

  G1StringDedupTable* resized_table() {
    return _resized_table;
  }

  bool is_rehashing() {
    return _rehashed_table != NULL;
  }

  // Atomically claims the next available queue for exclusive access by
  // the current thread. Returns the queue number of the claimed queue.
  size_t claim_queue();

  // Atomically claims the next available table partition for exclusive
  // access by the current thread. Returns the table bucket number where
  // the claimed partition starts.
  size_t claim_table_partition(size_t partition_size);

  // Applies and returns the result from the is_alive closure, or
  // returns true if no such closure was provided.
  bool is_alive(oop o) {
    if (_is_alive != NULL) {
      return _is_alive->do_object_b(o);
    }
    return true;
  }

  // Applies the keep_alive closure, or does nothing if no such
  // closure was provided.
  void keep_alive(oop* p) {
    if (_keep_alive != NULL) {
      _keep_alive->do_oop(p);
    }
  }
};

#endif // SHARE_VM_GC_G1_G1STRINGDEDUP_HPP
