/*
 * Copyright (c) 2006, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_MARKOOP_INLINE_HPP
#define SHARE_VM_OOPS_MARKOOP_INLINE_HPP

#include "oops/klass.hpp"
#include "oops/markOop.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/globals.hpp"

// Should this header be preserved during GC (when biased locking is enabled)?
inline bool markOopDesc::must_be_preserved_with_bias(oop obj_containing_mark) const {
  assert(UseBiasedLocking, "unexpected");
  if (has_bias_pattern()) {
    // Will reset bias at end of collection
    // Mark words of biased and currently locked objects are preserved separately
    return false;
  }
  markOop prototype_header = prototype_for_object(obj_containing_mark);
  if (prototype_header->has_bias_pattern()) {
    // Individual instance which has its bias revoked; must return
    // true for correctness
    return true;
  }
  return (!is_unlocked() || !has_no_hash());
}

// Should this header be preserved during GC?
inline bool markOopDesc::must_be_preserved(oop obj_containing_mark) const {
  if (!UseBiasedLocking)
    return (!is_unlocked() || !has_no_hash());
  return must_be_preserved_with_bias(obj_containing_mark);
}

// Should this header be preserved in the case of a promotion failure
// during scavenge (when biased locking is enabled)?
inline bool markOopDesc::must_be_preserved_with_bias_for_promotion_failure(oop obj_containing_mark) const {
  assert(UseBiasedLocking, "unexpected");
  // We don't explicitly save off the mark words of biased and
  // currently-locked objects during scavenges, so if during a
  // promotion failure we encounter either a biased mark word or a
  // klass which still has a biasable prototype header, we have to
  // preserve the mark word. This results in oversaving, but promotion
  // failures are rare, and this avoids adding more complex logic to
  // the scavengers to call new variants of
  // BiasedLocking::preserve_marks() / restore_marks() in the middle
  // of a scavenge when a promotion failure has first been detected.
  if (has_bias_pattern() ||
      prototype_for_object(obj_containing_mark)->has_bias_pattern()) {
    return true;
  }
  return (!is_unlocked() || !has_no_hash());
}

// Should this header be preserved in the case of a promotion failure
// during scavenge?
inline bool markOopDesc::must_be_preserved_for_promotion_failure(oop obj_containing_mark) const {
  if (!UseBiasedLocking)
    return (!is_unlocked() || !has_no_hash());
  return must_be_preserved_with_bias_for_promotion_failure(obj_containing_mark);
}


// Same as must_be_preserved_with_bias_for_promotion_failure() except that
// it takes a Klass* argument, instead of the object of which this is the mark word.
inline bool markOopDesc::must_be_preserved_with_bias_for_cms_scavenge(Klass* klass_of_obj_containing_mark) const {
  assert(UseBiasedLocking, "unexpected");
  // CMS scavenges preserve mark words in similar fashion to promotion failures; see above
  if (has_bias_pattern() ||
      klass_of_obj_containing_mark->prototype_header()->has_bias_pattern()) {
    return true;
  }
  return (!is_unlocked() || !has_no_hash());
}

// Same as must_be_preserved_for_promotion_failure() except that
// it takes a Klass* argument, instead of the object of which this is the mark word.
inline bool markOopDesc::must_be_preserved_for_cms_scavenge(Klass* klass_of_obj_containing_mark) const {
  if (!UseBiasedLocking)
    return (!is_unlocked() || !has_no_hash());
  return must_be_preserved_with_bias_for_cms_scavenge(klass_of_obj_containing_mark);
}

inline markOop markOopDesc::prototype_for_object(oop obj) {
#ifdef ASSERT
  markOop prototype_header = obj->klass()->prototype_header();
  assert(prototype_header == prototype() || prototype_header->has_bias_pattern(), "corrupt prototype header");
#endif
  return obj->klass()->prototype_header();
}

#endif // SHARE_VM_OOPS_MARKOOP_INLINE_HPP
