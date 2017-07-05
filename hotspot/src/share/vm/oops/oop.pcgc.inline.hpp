/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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

inline void oopDesc::update_contents(ParCompactionManager* cm) {
  // The klass field must be updated before anything else
  // can be done.
  DEBUG_ONLY(klassOopDesc* original_klass = klass());

  // Can the option to update and/or copy be moved up in the
  // call chain to avoid calling into here?

  if (PSParallelCompact::should_update_klass(klass())) {
    update_header();
    assert(klass()->is_klass(), "Not updated correctly");
  } else {
    assert(klass()->is_klass(), "Not updated");
  }

  Klass* new_klass = blueprint();
  if (!new_klass->oop_is_typeArray()) {
    // It might contain oops beyond the header, so take the virtual call.
    new_klass->oop_update_pointers(cm, this);
  }
  // Else skip it.  The typeArrayKlass in the header never needs scavenging.
}

inline void oopDesc::update_contents(ParCompactionManager* cm,
                                     HeapWord* begin_limit,
                                     HeapWord* end_limit) {
  // The klass field must be updated before anything else
  // can be done.
  debug_only(klassOopDesc* original_klass = klass());

  update_contents(cm, klass(), begin_limit, end_limit);
}

inline void oopDesc::update_contents(ParCompactionManager* cm,
                                     klassOop old_klass,
                                     HeapWord* begin_limit,
                                     HeapWord* end_limit) {

  klassOop updated_klass =
    PSParallelCompact::summary_data().calc_new_klass(old_klass);

  // Needs to be boundary aware for the 64 bit case
  // update_header();
  // The klass has moved.  Is the location of the klass
  // within the limits?
  if ((((HeapWord*)&_metadata._klass) >= begin_limit) &&
      (((HeapWord*)&_metadata._klass) < end_limit)) {
    set_klass(updated_klass);
  }

  Klass* klass = updated_klass->klass_part();
  if (!klass->oop_is_typeArray()) {
    // It might contain oops beyond the header, so take the virtual call.
    klass->oop_update_pointers(cm, this, begin_limit, end_limit);
  }
  // Else skip it.  The typeArrayKlass in the header never needs scavenging.
}

inline void oopDesc::follow_contents(ParCompactionManager* cm) {
  assert (PSParallelCompact::mark_bitmap()->is_marked(this),
    "should be marked");
  blueprint()->oop_follow_contents(cm, this);
}

// Used by parallel old GC.

inline void oopDesc::follow_header(ParCompactionManager* cm) {
  if (UseCompressedOops) {
    PSParallelCompact::mark_and_push(cm, compressed_klass_addr());
  } else {
    PSParallelCompact::mark_and_push(cm, klass_addr());
  }
}

inline oop oopDesc::forward_to_atomic(oop p) {
  assert(ParNewGeneration::is_legal_forward_ptr(p),
         "illegal forwarding pointer value.");
  markOop oldMark = mark();
  markOop forwardPtrMark = markOopDesc::encode_pointer_as_mark(p);
  markOop curMark;

  assert(forwardPtrMark->decode_pointer() == p, "encoding must be reversable");
  assert(sizeof(markOop) == sizeof(intptr_t), "CAS below requires this.");

  while (!is_forwarded()) {
    curMark = (markOop)Atomic::cmpxchg_ptr(forwardPtrMark, &_mark, oldMark);
    if (curMark == oldMark) {
      assert(is_forwarded(), "the CAS should have succeeded.");
      return NULL;
    }
    oldMark = curMark;
  }
  return forwardee();
}

inline void oopDesc::update_header() {
  if (UseCompressedOops) {
    PSParallelCompact::adjust_pointer(compressed_klass_addr());
  } else {
    PSParallelCompact::adjust_pointer(klass_addr());
  }
}

inline void oopDesc::update_header(HeapWord* beg_addr, HeapWord* end_addr) {
  if (UseCompressedOops) {
    PSParallelCompact::adjust_pointer(compressed_klass_addr(),
                                      beg_addr, end_addr);
  } else {
    PSParallelCompact::adjust_pointer(klass_addr(), beg_addr, end_addr);
  }
}
