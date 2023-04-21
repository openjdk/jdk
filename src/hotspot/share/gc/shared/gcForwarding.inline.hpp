/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHARED_GCFORWARDING_INLINE_HPP
#define SHARE_GC_SHARED_GCFORWARDING_INLINE_HPP

#include "gc/shared/gcForwarding.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/slidingForwarding.inline.hpp"
#include "oops/oop.inline.hpp"

inline bool GCForwarding::is_forwarded(oop obj) {
  return obj->is_forwarded();
}

inline bool GCForwarding::is_not_forwarded(oop obj) {
  return !obj->is_forwarded();
}

inline oop GCForwarding::forwardee(oop obj) {
  if (UseAltGCForwarding) {
    assert(_sliding_forwarding != nullptr, "expect sliding forwarding initialized");
    return _sliding_forwarding->forwardee(obj);
  } else {
    return obj->forwardee();
  }
}

inline void GCForwarding::forward_to(oop obj, oop fwd) {
  if (UseAltGCForwarding) {
    assert(_sliding_forwarding != nullptr, "expect sliding forwarding initialized");
    _sliding_forwarding->forward_to(obj, fwd);
    assert(forwardee(obj) == fwd, "must be forwarded to correct forwardee");
  } else {
    obj->forward_to(fwd);
  }
}

#endif // SHARE_GC_SHARED_GCFORWARDING_INLINE_HPP
