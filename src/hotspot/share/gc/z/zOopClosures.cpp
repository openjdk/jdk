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
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

static void z_verify_loaded_object(const oop* p, const oop obj) {
  guarantee(ZOop::is_good_or_null(obj),
            "Bad oop " PTR_FORMAT " found at " PTR_FORMAT ", expected " PTR_FORMAT,
            p2i(obj), p2i(p), p2i(ZOop::good(obj)));
  guarantee(oopDesc::is_oop_or_null(obj),
            "Bad object " PTR_FORMAT " found at " PTR_FORMAT,
            p2i(obj), p2i(p));
}

ZVerifyHeapOopClosure::ZVerifyHeapOopClosure(oop base)
    : _base(base) {}

void ZVerifyHeapOopClosure::do_oop(oop* p) {
  guarantee(ZHeap::heap()->is_in((uintptr_t)p), "oop* " PTR_FORMAT " not in heap", p2i(p));

  const oop obj = HeapAccess<ON_UNKNOWN_OOP_REF>::oop_load_at(_base, _base->field_offset(p));
  z_verify_loaded_object(p, obj);
}

void ZVerifyHeapOopClosure::do_oop(narrowOop* p) {
  ShouldNotReachHere();
}

void ZVerifyRootOopClosure::do_oop(oop* p) {
  guarantee(!ZHeap::heap()->is_in((uintptr_t)p), "oop* " PTR_FORMAT " in heap", p2i(p));

  const oop obj = NativeAccess<>::oop_load(p);
  z_verify_loaded_object(p, obj);
}

void ZVerifyRootOopClosure::do_oop(narrowOop* p) {
  ShouldNotReachHere();
}

void ZVerifyObjectClosure::do_object(oop o) {
  ZVerifyHeapOopClosure cl(o);
  o->oop_iterate(&cl);
}
