/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/support/jfrMethodData.hpp"
#include "memory/resourceArea.hpp"
#include "oops/method.inline.hpp"
#include "oops/methodData.inline.hpp"
#include "runtime/handles.inline.hpp"

 // Caller requires ResourceMark.
static inline BitData* get_bit_data(MethodData* mdo, int bci) {
  assert(mdo != nullptr, "invariant");
  BitData* const bit_data = static_cast<BitData*>(mdo->bci_to_data(bci));
  assert(bit_data != nullptr, "invariant");
  assert(bit_data->is_VirtualCallData() || bit_data->is_VirtualCallTypeData() ||
    bit_data->is_CounterData() || bit_data->is_CallTypeData(), "invariant");
  return bit_data;
}

static inline MethodData* build_mdo(Method* method, JavaThread* jt) {
  methodHandle method_handle(jt, method);
  Method::build_profiling_method_data(method_handle, jt);
  return method->method_data();
}

static inline MethodData* get_mdo(Method* method, JavaThread* jt) {
  MethodData* mdo = method->method_data();
  return mdo != nullptr ? mdo : build_mdo(method, jt);
}

static bool mark_mdo(Method* method, int bci, JavaThread* jt) {
  assert(method != nullptr, "invariant");
  assert(!method->is_native(), "native methods have no MDO bit data");
  assert(jt != nullptr, "invariant");
  MethodData* const mdo = get_mdo(method, jt);
  assert(mdo != nullptr, "invariant");

  // Lock to access ProfileData, and ensure lock is not broken by a safepoint
  MutexLocker ml(mdo->extra_data_lock(), Mutex::_no_safepoint_check_flag);

  // Get the datalayout for the invocation bci.
  BitData* const bit_data = get_bit_data(mdo, bci);
  // Returns true if this callsite is not yet linked and
  // our attempt to set the deprecated flag was succesful.
  if (bit_data->set_deprecated_method_call_site()) {
    assert(bit_data->deprecated_method_call_site(), "invariant");
    return true;
  }
  assert(bit_data->deprecated_method_call_site(), "invariant");
  return false;
}

bool JfrMethodData::mark_deprecated_call_site(Method* method, int bci, JavaThread* jt) {
  assert(method != nullptr, "invariant");
  assert(jt != nullptr, "invariant");
  assert(method->validate_bci(bci) >= 0, "invariant");
  ResourceMark rm(jt);
  return mark_mdo(method, bci, jt);
}
