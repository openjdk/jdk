/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_oopsHierarchy.cpp.incl"

#ifdef CHECK_UNHANDLED_OOPS

void oop::register_oop() {
  assert (CheckUnhandledOops, "should only call when CheckUnhandledOops");
  if (!Universe::is_fully_initialized()) return;
  // This gets expensive, which is why checking unhandled oops is on a switch.
  Thread* t = ThreadLocalStorage::thread();
  if (t != NULL && t->is_Java_thread()) {
     frame fr = os::current_frame();
     // This points to the oop creator, I guess current frame points to caller
     assert (fr.pc(), "should point to a vm frame");
     t->unhandled_oops()->register_unhandled_oop(this, fr.pc());
  }
}

void oop::unregister_oop() {
  assert (CheckUnhandledOops, "should only call when CheckUnhandledOops");
  if (!Universe::is_fully_initialized()) return;
  // This gets expensive, which is why checking unhandled oops is on a switch.
  Thread* t = ThreadLocalStorage::thread();
  if (t != NULL && t->is_Java_thread()) {
    t->unhandled_oops()->unregister_unhandled_oop(this);
  }
}
#endif // CHECK_UNHANDLED_OOPS
