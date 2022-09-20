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
#include "code/nativeInst.hpp"
#include "code/nmethod.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "utilities/debug.hpp"

class NativeMethodBarrier: public NativeInstruction {
  private:

  public:
    int get_guard_value() const {
      // Unimplemented();
      return -1;
    }

    void release_set_guard_value(int value) {
      // Unimplemented();
    }

};

static const int entry_barrier_offset() {
  // TODO: Implement this procedure
  return 0;
}

static NativeMethodBarrier* get_nmethod_barrier(nmethod* nm) {
  address barrier_address = nm->code_begin() + nm->frame_complete_offset() + entry_barrier_offset();
  auto barrier = reinterpret_cast<NativeMethodBarrier*>(barrier_address);

  return barrier;
}

void BarrierSetNMethod::deoptimize(nmethod* nm, address* return_address_ptr) {
  // TODO: Implement
  ShouldNotReachHere();
}

void BarrierSetNMethod::arm(nmethod* nm, int arm_value) {
  if (!supports_entry_barrier(nm)) {
    return;
  }

  NativeMethodBarrier* barrier = get_nmethod_barrier(nm);
  barrier->release_set_guard_value(arm_value);
}

void BarrierSetNMethod::disarm(nmethod* nm) {
  if (!supports_entry_barrier(nm)) {
    return;
  }

  NativeMethodBarrier* barrier = get_nmethod_barrier(nm);
  barrier->release_set_guard_value(disarmed_value());
}

bool BarrierSetNMethod::is_armed(nmethod* nm) {
  if (!supports_entry_barrier(nm)) {
    return false;
  }

  NativeMethodBarrier* barrier = get_nmethod_barrier(nm);
  return barrier->get_guard_value() != disarmed_value();
}
