/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "memory/metaspaceClosure.hpp"

// Update the reference to point to new_loc.
void MetaspaceClosure::Ref::update(address new_loc) const {
  log_trace(cds)("Ref: [" PTR_FORMAT "] -> " PTR_FORMAT " => " PTR_FORMAT,
                 p2i(mpp()), p2i(obj()), p2i(new_loc));
  uintx p = (uintx)new_loc;
  p |= flag_bits(); // Make sure the flag bits are copied to the new pointer.
  *(address*)mpp() = (address)p;
}

void MetaspaceClosure::push_impl(MetaspaceClosure::Ref* ref, Writability w) {
  if (ref->not_null()) {
    bool read_only;
    switch (w) {
    case _writable:
      read_only = false;
      break;
    case _not_writable:
      read_only = true;
      break;
    default:
      assert(w == _default, "must be");
      read_only = ref->is_read_only_by_default();
    }
    if (do_ref(ref, read_only)) { // true means we want to iterate the embedded pointer in <ref>
      ref->metaspace_pointers_do(this);
    }
  }
}

bool UniqueMetaspaceClosure::do_ref(MetaspaceClosure::Ref* ref, bool read_only) {
  bool* found = _has_been_visited.get(ref->obj());
  if (found != NULL) {
    assert(*found == read_only, "must be");
    return false; // Already visited: no need to iterate embedded pointers.
  } else {
    bool isnew = _has_been_visited.put(ref->obj(), read_only);
    assert(isnew, "sanity");
    do_unique_ref(ref, read_only);
    return true;  // Saw this for the first time: iterate the embedded pointers.
  }
}
