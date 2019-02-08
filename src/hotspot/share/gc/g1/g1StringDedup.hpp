/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1STRINGDEDUP_HPP
#define SHARE_GC_G1_G1STRINGDEDUP_HPP

//
// G1 string deduplication candidate selection
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

#include "gc/shared/stringdedup/stringDedup.hpp"
#include "memory/allocation.hpp"
#include "oops/oop.hpp"

class OopClosure;
class BoolObjectClosure;
class G1GCPhaseTimes;
class G1StringDedupUnlinkOrOopsDoClosure;

//
// G1 interface for interacting with string deduplication.
//
class G1StringDedup : public StringDedup {
private:

  // Candidate selection policies, returns true if the given object is
  // candidate for string deduplication.
  static bool is_candidate_from_mark(oop obj);
  static bool is_candidate_from_evacuation(bool from_young, bool to_young, oop obj);

public:
  // Initialize string deduplication.
  static void initialize();

  // Enqueues a deduplication candidate for later processing by the deduplication
  // thread. Before enqueuing, these functions apply the appropriate candidate
  // selection policy to filters out non-candidates.
  static void enqueue_from_mark(oop java_string, uint worker_id);
  static void enqueue_from_evacuation(bool from_young, bool to_young,
                                      unsigned int queue, oop java_string);
};

#endif // SHARE_GC_G1_G1STRINGDEDUP_HPP
