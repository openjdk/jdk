/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "logging/log.hpp"
#include "memory/iterator.hpp"
#include "memory/universe.hpp"
#include "oops/access.inline.hpp"
#include "oops/method.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/threadWXSetters.inline.hpp"
#include "runtime/threads.hpp"
#include "utilities/debug.hpp"

int BarrierSetNMethod::disarmed_value() const {
  return *disarmed_value_address();
}

bool BarrierSetNMethod::supports_entry_barrier(nmethod* nm) {
  if (nm->method()->is_method_handle_intrinsic()) {
    return false;
  }

  if (nm->method()->is_continuation_enter_intrinsic()) {
    return false;
  }

  if (!nm->is_native_method() && !nm->is_compiled_by_c2() && !nm->is_compiled_by_c1()) {
    return false;
  }

  return true;
}

bool BarrierSetNMethod::nmethod_entry_barrier(nmethod* nm) {
  class OopKeepAliveClosure : public OopClosure {
  public:
    virtual void do_oop(oop* p) {
      // Loads on nmethod oops are phantom strength.
      //
      // Note that we could have used NativeAccess<ON_PHANTOM_OOP_REF>::oop_load(p),
      // but that would have *required* us to convert the returned LoadOopProxy to an oop,
      // or else keep alive load barrier will never be called. It's the LoadOopProxy-to-oop
      // conversion that performs the load barriers. This is too subtle, so we instead
      // perform an explicit keep alive call.
      oop obj = NativeAccess<ON_PHANTOM_OOP_REF | AS_NO_KEEPALIVE>::oop_load(p);
      if (obj != nullptr) {
        Universe::heap()->keep_alive(obj);
      }
    }

    virtual void do_oop(narrowOop* p) { ShouldNotReachHere(); }
  };

  // If the nmethod is the only thing pointing to the oops, and we are using a
  // SATB GC, then it is important that this code marks them live.
  OopKeepAliveClosure cl;
  nm->oops_do(&cl);

  // CodeCache sweeper support
  nm->mark_as_maybe_on_continuation();

  disarm(nm);

  return true;
}

int* BarrierSetNMethod::disarmed_value_address() const {
  return (int*) &_current_phase;
}

ByteSize BarrierSetNMethod::thread_disarmed_offset() const {
  return Thread::nmethod_disarmed_offset();
}

class BarrierSetNMethodArmClosure : public ThreadClosure {
private:
  int _disarm_value;

public:
  BarrierSetNMethodArmClosure(int disarm_value) :
      _disarm_value(disarm_value) {}

  virtual void do_thread(Thread* thread) {
    thread->set_nmethod_disarm_value(_disarm_value);
  }
};

void BarrierSetNMethod::arm_all_nmethods() {
  // Change to a new global GC phase. Doing this requires changing the thread-local
  // disarm value for all threads, to reflect the new GC phase.
  ++_current_phase;
  if (_current_phase == 4) {
    _current_phase = 1;
  }
  BarrierSetNMethodArmClosure cl(_current_phase);
  Threads::threads_do(&cl);

  // We clear the patching epoch when disarming nmethods, so that
  // the counter won't overflow.
  AARCH64_PORT_ONLY(BarrierSetAssembler::clear_patching_epoch());
}

int BarrierSetNMethod::nmethod_stub_entry_barrier(address* return_address_ptr) {
  // Enable WXWrite: the function is called directly from nmethod_entry_barrier
  // stub.
  MACOS_AARCH64_ONLY(ThreadWXEnable wx(WXWrite, Thread::current()));

  address return_address = *return_address_ptr;
  AARCH64_PORT_ONLY(return_address = pauth_strip_pointer(return_address));
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

  // Diagnostic option to force deoptimization 1 in 3 times. It is otherwise
  // a very rare event.
  if (DeoptimizeNMethodBarriersALot) {
    static volatile uint32_t counter=0;
    if (Atomic::add(&counter, 1u) % 3 == 0) {
      may_enter = false;
    }
  }

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
