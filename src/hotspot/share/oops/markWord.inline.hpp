/*
 * Copyright (c) 2006, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_MARKWORD_INLINE_HPP
#define SHARE_OOPS_MARKWORD_INLINE_HPP

#include "oops/markWord.hpp"

#include "oops/klass.hpp"
#include "runtime/globals.hpp"

// Should this header be preserved during GC?
template <typename KlassProxy>
inline bool markWord::must_be_preserved(KlassProxy klass) const {
  if (UseBiasedLocking) {
    if (has_bias_pattern()) {
      // Will reset bias at end of collection
      // Mark words of biased and currently locked objects are preserved separately
      return false;
    }
    markWord prototype_header = prototype_for_klass(klass);
    if (prototype_header.has_bias_pattern()) {
      // Individual instance which has its bias revoked; must return
      // true for correctness
      return true;
    }
  }
  return (!is_unlocked() || !has_no_hash());
}

// Should this header be preserved in the case of a promotion failure during scavenge?
template <typename KlassProxy>
inline bool markWord::must_be_preserved_for_promotion_failure(KlassProxy klass) const {
  if (UseBiasedLocking) {
    // We don't explicitly save off the mark words of biased and
    // currently-locked objects during scavenges, so if during a
    // promotion failure we encounter either a biased mark word or a
    // klass which still has a biasable prototype header, we have to
    // preserve the mark word. This results in oversaving, but promotion
    // failures are rare, and this avoids adding more complex logic to
    // the scavengers to call new variants of
    // BiasedLocking::preserve_marks() / restore_marks() in the middle
    // of a scavenge when a promotion failure has first been detected.
    if (has_bias_pattern() || prototype_for_klass(klass).has_bias_pattern()) {
      return true;
    }
  }
  return (!is_unlocked() || !has_no_hash());
}

inline markWord markWord::prototype_for_klass(const Klass* klass) {
  markWord prototype_header = klass->prototype_header();
  assert(prototype_header == prototype() || prototype_header.has_bias_pattern(), "corrupt prototype header");

  return prototype_header;
}

#endif // SHARE_OOPS_MARKWORD_INLINE_HPP
