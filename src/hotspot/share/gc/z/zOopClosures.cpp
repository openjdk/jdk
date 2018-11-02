/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "gc/z/zHeap.hpp"
#include "gc/z/zOopClosures.inline.hpp"
#include "gc/z/zOop.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

void ZVerifyOopClosure::do_oop(oop* p) {
  guarantee(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  guarantee(ZGlobalPhase == ZPhaseMarkCompleted, "Invalid phase");
  guarantee(!ZResurrection::is_blocked(), "Invalid phase");

  const oop o = RawAccess<>::oop_load(p);
  if (o != NULL) {
    guarantee(ZOop::is_good(o) || ZOop::is_finalizable_good(o),
              "Bad oop " PTR_FORMAT " found at " PTR_FORMAT ", expected " PTR_FORMAT,
              p2i(o), p2i(p), p2i(ZOop::good(o)));
    guarantee(oopDesc::is_oop(ZOop::good(o)),
              "Bad object " PTR_FORMAT " found at " PTR_FORMAT,
              p2i(o), p2i(p));
  }
}

void ZVerifyOopClosure::do_oop(narrowOop* p) {
  ShouldNotReachHere();
}

void ZVerifyObjectClosure::do_object(oop o) {
  ZVerifyOopClosure cl;
  o->oop_iterate(&cl);
}
