/*
 * Copyright 1997-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

// An objArrayOop is an array containing oops.
// Evaluating "String arg[10]" will create an objArrayOop.

class objArrayOopDesc : public arrayOopDesc {
 public:
  // Accessing
  oop obj_at(int index) const           { return *obj_at_addr(index);           }
  void obj_at_put(int index, oop value) { oop_store(obj_at_addr(index), value); }
  oop* base() const                     { return (oop*) arrayOopDesc::base(T_OBJECT); }

  // Sizing
  static int header_size()              { return arrayOopDesc::header_size(T_OBJECT); }
  static int object_size(int length)    { return align_object_size(header_size() + length); }
  int object_size()                     { return object_size(length()); }

  // Returns the address of the index'th element
  oop* obj_at_addr(int index) const {
    assert(is_within_bounds(index), "index out of bounds");
    return &base()[index];
  }
};
