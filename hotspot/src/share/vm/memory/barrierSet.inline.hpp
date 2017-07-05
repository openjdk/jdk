/*
 * Copyright 2001-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

// Inline functions of BarrierSet, which de-virtualize certain
// performance-critical calls when the barrier is the most common
// card-table kind.

template <class T> void BarrierSet::write_ref_field_pre(T* field, oop new_val) {
  if (kind() == CardTableModRef) {
    ((CardTableModRefBS*)this)->inline_write_ref_field_pre(field, new_val);
  } else {
    write_ref_field_pre_work(field, new_val);
  }
}

void BarrierSet::write_ref_field(void* field, oop new_val) {
  if (kind() == CardTableModRef) {
    ((CardTableModRefBS*)this)->inline_write_ref_field(field, new_val);
  } else {
    write_ref_field_work(field, new_val);
  }
}

void BarrierSet::write_ref_array(MemRegion mr) {
  if (kind() == CardTableModRef) {
    ((CardTableModRefBS*)this)->inline_write_ref_array(mr);
  } else {
    write_ref_array_work(mr);
  }
}

void BarrierSet::write_region(MemRegion mr) {
  if (kind() == CardTableModRef) {
    ((CardTableModRefBS*)this)->inline_write_region(mr);
  } else {
    write_region_work(mr);
  }
}
