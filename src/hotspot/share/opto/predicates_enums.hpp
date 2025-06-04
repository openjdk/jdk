/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_PREDICATES_ENUMS_HPP
#define SHARE_OPTO_PREDICATES_ENUMS_HPP

// The success projection of a Parse Predicate is always an IfTrueNode and the uncommon projection an IfFalseNode
typedef IfTrueNode ParsePredicateSuccessProj;
typedef IfFalseNode ParsePredicateUncommonProj;

// Assertion Predicates are either emitted to check the initial value of a range check in the first iteration or the last
// value of a range check in the last iteration of a loop.
enum class AssertionPredicateType {
  None, // Not an Assertion Predicate
  InitValue,
  LastValue,
  // Used for the Initialized Assertion Predicate emitted during Range Check Elimination for the final IV value.
  FinalIv
};

enum class PredicateState {
  // The Predicate is useless and will be cleaned up in the next round of IGVN. A useless Predicate is not visited
  // anymore by PredicateVisitors. If a Predicate loses its connection to a loop head, it will be marked useless by
  // EliminateUselessPredicates and cleaned up by the Value() methods of the associated Predicate IR nodes.
  Useless,
  // This state is used by EliminateUselessPredicates to temporarily mark a Predicate as neither useless nor useful.
  // Outside EliminateUselessPredicates, a Predicate should never be MaybeUseful.
  MaybeUseful,
  // Default state: The Predicate is useful and will be visited by PredicateVisitors.
  Useful
};

#endif // SHARE_OPTO_PREDICATES_ENUMS_HPP
