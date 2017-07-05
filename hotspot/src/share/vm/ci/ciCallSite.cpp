/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "ci/ciCallSite.hpp"
#include "ci/ciUtilities.hpp"

// ciCallSite

bool ciCallSite::is_constant_call_site() {
  return klass()->is_subclass_of(CURRENT_ENV->ConstantCallSite_klass());
}
bool ciCallSite::is_mutable_call_site() {
  return klass()->is_subclass_of(CURRENT_ENV->MutableCallSite_klass());
}
bool ciCallSite::is_volatile_call_site() {
  return klass()->is_subclass_of(CURRENT_ENV->VolatileCallSite_klass());
}

// ------------------------------------------------------------------
// ciCallSite::get_target
//
// Return the target MethodHandle of this CallSite.
ciMethodHandle* ciCallSite::get_target() const {
  VM_ENTRY_MARK;
  oop method_handle_oop = java_lang_invoke_CallSite::target(get_oop());
  return CURRENT_ENV->get_object(method_handle_oop)->as_method_handle();
}

// ------------------------------------------------------------------
// ciCallSite::print
//
// Print debugging information about the CallSite.
void ciCallSite::print() {
  Unimplemented();
}
