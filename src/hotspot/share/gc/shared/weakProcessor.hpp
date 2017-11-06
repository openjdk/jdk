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

#ifndef SHARE_VM_GC_SHARED_WEAKPROCESSOR_HPP
#define SHARE_VM_GC_SHARED_WEAKPROCESSOR_HPP

#include "memory/allocation.hpp"
#include "memory/iterator.hpp"

// Helper class to aid in root scanning and cleaning of weak oops in the VM.
//
// New containers of weak oops added to this class will automatically
// be cleaned by all GCs, including the young generation GCs.
class WeakProcessor : AllStatic {
public:
  // Visit all oop*s and apply the keep_alive closure if the referenced
  // object is considered alive by the is_alive closure, otherwise do some
  // container specific cleanup of element holding the oop.
  static void weak_oops_do(BoolObjectClosure* is_alive, OopClosure* keep_alive);

  // Visit all oop*s and apply the given closure.
  static void oops_do(OopClosure* closure);
};

#endif // SHARE_VM_GC_SHARED_WEAKPROCESSOR_HPP
