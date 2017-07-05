/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2008 Red Hat, Inc.
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
#include "assembler_zero.inline.hpp"
#include "entry_zero.hpp"
#include "interpreter/cppInterpreter.hpp"
#include "memory/resourceArea.hpp"
#include "nativeInst_zero.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/ostream.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#endif

// This method is called by nmethod::make_not_entrant_or_zombie to
// insert a jump to SharedRuntime::get_handle_wrong_method_stub()
// (dest) at the start of a compiled method (verified_entry) to avoid
// a race where a method is invoked while being made non-entrant.
//
// In Shark, verified_entry is a pointer to a SharkEntry.  We can
// handle this simply by changing it's entry point to point at the
// interpreter.  This only works because the interpreter and Shark
// calling conventions are the same.

void NativeJump::patch_verified_entry(address entry,
                                      address verified_entry,
                                      address dest) {
  assert(dest == SharedRuntime::get_handle_wrong_method_stub(), "should be");

#ifdef CC_INTERP
  ((ZeroEntry*) verified_entry)->set_entry_point(
    (address) CppInterpreter::normal_entry);
#else
  Unimplemented();
#endif // CC_INTERP
}
