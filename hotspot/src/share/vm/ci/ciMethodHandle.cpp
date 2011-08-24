/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciClassList.hpp"
#include "ci/ciInstance.hpp"
#include "ci/ciMethodData.hpp"
#include "ci/ciMethodHandle.hpp"
#include "ci/ciUtilities.hpp"
#include "prims/methodHandleWalk.hpp"
#include "prims/methodHandles.hpp"

// ciMethodHandle

// ------------------------------------------------------------------
// ciMethodHandle::get_adapter
//
// Return an adapter for this MethodHandle.
ciMethod* ciMethodHandle::get_adapter_impl(bool is_invokedynamic) const {
  VM_ENTRY_MARK;
  Handle h(get_oop());
  methodHandle callee(_callee->get_methodOop());
  assert(callee->is_method_handle_invoke(), "");
  oop mt1 = callee->method_handle_type();
  oop mt2 = java_lang_invoke_MethodHandle::type(h());
  if (!java_lang_invoke_MethodType::equals(mt1, mt2)) {
    if (PrintMiscellaneous && (Verbose || WizardMode)) {
      tty->print_cr("ciMethodHandle::get_adapter: types not equal");
      mt1->print(); mt2->print();
    }
    return NULL;
  }
  // We catch all exceptions here that could happen in the method
  // handle compiler and stop the VM.
  MethodHandleCompiler mhc(h, callee->name(), callee->signature(), _profile.count(), is_invokedynamic, THREAD);
  if (!HAS_PENDING_EXCEPTION) {
    methodHandle m = mhc.compile(THREAD);
    if (!HAS_PENDING_EXCEPTION) {
      return CURRENT_ENV->get_object(m())->as_method();
    }
  }
  if (PrintMiscellaneous && (Verbose || WizardMode)) {
    tty->print("*** ciMethodHandle::get_adapter => ");
    PENDING_EXCEPTION->print();
    tty->print("*** get_adapter (%s): ", is_invokedynamic ? "indy" : "mh"); ((ciObject*)this)->print();
  }
  CLEAR_PENDING_EXCEPTION;
  return NULL;
}

// ------------------------------------------------------------------
// ciMethodHandle::get_adapter
//
// Return an adapter for this MethodHandle.
ciMethod* ciMethodHandle::get_adapter(bool is_invokedynamic) const {
  ciMethod* result = get_adapter_impl(is_invokedynamic);
  if (result) {
    // Fake up the MDO maturity.
    ciMethodData* mdo = result->method_data();
    if (mdo != NULL && _caller->method_data() != NULL && _caller->method_data()->is_mature()) {
      mdo->set_mature();
    }
  }
  return result;
}


// ------------------------------------------------------------------
// ciMethodHandle::print_impl
//
// Implementation of the print method.
void ciMethodHandle::print_impl(outputStream* st) {
  st->print(" type=");
  get_oop()->print();
}
