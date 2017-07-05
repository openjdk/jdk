/*
 * Copyright 2000-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

inline void MarkSweep::_adjust_pointer(oop* p, bool isroot) {
  oop obj = *p;
  VALIDATE_MARK_SWEEP_ONLY(oop saved_new_pointer = NULL);
  if (obj != NULL) {
    oop new_pointer = oop(obj->mark()->decode_pointer());
    assert(new_pointer != NULL ||                     // is forwarding ptr?
           obj->mark() == markOopDesc::prototype() || // not gc marked?
           (UseBiasedLocking && obj->mark()->has_bias_pattern()) || // not gc marked?
           obj->is_shared(),                          // never forwarded?
           "should contain a forwarding pointer");
    if (new_pointer != NULL) {
      *p = new_pointer;
      assert(Universe::heap()->is_in_reserved(new_pointer),
             "should be in object space");
      VALIDATE_MARK_SWEEP_ONLY(saved_new_pointer = new_pointer);
    }
  }
  VALIDATE_MARK_SWEEP_ONLY(track_adjusted_pointer(p, saved_new_pointer, isroot));
}

inline void MarkSweep::mark_object(oop obj) {

#ifndef SERIALGC
  if (UseParallelOldGC && VerifyParallelOldWithMarkSweep) {
    assert(PSParallelCompact::mark_bitmap()->is_marked(obj),
      "Should be marked in the marking bitmap");
  }
#endif // SERIALGC

  // some marks may contain information we need to preserve so we store them away
  // and overwrite the mark.  We'll restore it at the end of markSweep.
  markOop mark = obj->mark();
  obj->set_mark(markOopDesc::prototype()->set_marked());

  if (mark->must_be_preserved(obj)) {
    preserve_mark(obj, mark);
  }
}
