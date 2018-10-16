/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "code/codeCache.hpp"
#include "code/nmethod.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "logging/log.hpp"
#include "runtime/thread.hpp"
#include "utilities/debug.hpp"

int BarrierSetNMethod::disarmed_value() const {
  char* disarmed_addr = reinterpret_cast<char*>(Thread::current());
  disarmed_addr += in_bytes(thread_disarmed_offset());
  return *reinterpret_cast<int*>(disarmed_addr);
}

bool BarrierSetNMethod::supports_entry_barrier(nmethod* nm) {
  if (nm->method()->is_method_handle_intrinsic()) {
    return false;
  }

  if (!nm->is_native_method() && !nm->is_compiled_by_c2() && !nm->is_compiled_by_c1()) {
    return false;
  }

  return true;
}

int BarrierSetNMethod::nmethod_stub_entry_barrier(address* return_address_ptr) {
  address return_address = *return_address_ptr;
  CodeBlob* cb = CodeCache::find_blob(return_address);
  assert(cb != NULL, "invariant");

  nmethod* nm = cb->as_nmethod();
  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();

  if (!bs_nm->is_armed(nm)) {
    return 0;
  }

  assert(!nm->is_osr_method(), "Should not reach here");
  // Called upon first entry after being armed
  bool may_enter = bs_nm->nmethod_entry_barrier(nm);
  if (!may_enter) {
    log_trace(nmethod, barrier)("Deoptimizing nmethod: " PTR_FORMAT, p2i(nm));
    bs_nm->deoptimize(nm, return_address_ptr);
  }
  return may_enter ? 0 : 1;
}

bool BarrierSetNMethod::nmethod_osr_entry_barrier(nmethod* nm) {
  // This check depends on the invariant that all nmethods that are deoptimized / made not entrant
  // are NOT disarmed.
  // This invariant is important because a method can be deoptimized after the method have been
  // resolved / looked up by OSR by another thread. By not deoptimizing them we guarantee that
  // a deoptimized method will always hit the barrier and come to the same conclusion - deoptimize
  if (!is_armed(nm)) {
    return true;
  }

  assert(nm->is_osr_method(), "Should not reach here");
  log_trace(nmethod, barrier)("Running osr nmethod entry barrier: " PTR_FORMAT, p2i(nm));
  return nmethod_entry_barrier(nm);
}
