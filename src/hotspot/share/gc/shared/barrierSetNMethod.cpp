/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
#if INCLUDE_JVMCI
#include "jvmci/jvmciRuntime.hpp"
#endif

int BarrierSetNMethod::disarmed_guard_value() const {
  return *disarmed_guard_value_address();
}

bool BarrierSetNMethod::supports_entry_barrier(nmethod* nm) {
  if (nm->method()->is_method_handle_intrinsic()) {
    return false;
  }

  if (nm->method()->is_continuation_enter_intrinsic()) {
    return false;
  }

  if (nm->method()->is_continuation_yield_intrinsic()) {
    return false;
  }

  if (nm->method()->is_continuation_native_intrinsic()) {
    guarantee(false, "Unknown Continuation native intrinsic");
    return false;
  }

  if (nm->is_native_method() || nm->is_compiled_by_c2() || nm->is_compiled_by_c1() || nm->is_compiled_by_jvmci()) {
    return true;
  }

  return false;
}

void BarrierSetNMethod::disarm(nmethod* nm) {
  set_guard_value(nm, disarmed_guard_value());
}

bool BarrierSetNMethod::is_armed(nmethod* nm) {
  return guard_value(nm) != disarmed_guard_value();
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

  if (!is_armed(nm)) {
    // Some other thread got here first and healed the oops
    // and disarmed the nmethod. No need to continue.
    return true;
  }

  // If the nmethod is the only thing pointing to the oops, and we are using a
  // SATB GC, then it is important that this code marks them live.
  // Also, with concurrent GC, it is possible that frames in continuation stack
  // chunks are not visited if they are allocated after concurrent GC started.
  OopKeepAliveClosure cl;
  nm->oops_do(&cl);

  // CodeCache unloading support
  nm->mark_as_maybe_on_stack();

  disarm(nm);

  return true;
}

int* BarrierSetNMethod::disarmed_guard_value_address() const {
  return (int*) &_current_phase;
}

ByteSize BarrierSetNMethod::thread_disarmed_guard_value_offset() const {
  return Thread::nmethod_disarmed_guard_value_offset();
}

class BarrierSetNMethodArmClosure : public ThreadClosure {
private:
  int _disarmed_guard_value;

public:
  BarrierSetNMethodArmClosure(int disarmed_guard_value) :
      _disarmed_guard_value(disarmed_guard_value) {}

  virtual void do_thread(Thread* thread) {
    thread->set_nmethod_disarmed_guard_value(_disarmed_guard_value);
  }
};

void BarrierSetNMethod::arm_all_nmethods() {
  // Change to a new global GC phase. Doing this requires changing the thread-local
  // disarm value for all threads, to reflect the new GC phase.
  // We wrap around at INT_MAX. That means that we assume nmethods won't have ABA
  // problems in their nmethod disarm values after INT_MAX - 1 GCs. Every time a GC
  // completes, ABA problems are removed, but if a concurrent GC is started and then
  // aborted N times, that is when there could be ABA problems. If there are anything
  // close to INT_MAX - 1 GCs starting without being able to finish, something is
  // seriously wrong.
  ++_current_phase;
  if (_current_phase == INT_MAX) {
    _current_phase = 1;
  }
  BarrierSetNMethodArmClosure cl(_current_phase);
  Threads::threads_do(&cl);

#if (defined(AARCH64) || defined(RISCV64)) && !defined(ZERO)
  // We clear the patching epoch when disarming nmethods, so that
  // the counter won't overflow.
  BarrierSetAssembler::clear_patching_epoch();
#endif
}

int BarrierSetNMethod::nmethod_stub_entry_barrier(address* return_address_ptr) {
  // Enable WXWrite: the function is called directly from nmethod_entry_barrier
  // stub.
  MACOS_AARCH64_ONLY(ThreadWXEnable wx(WXWrite, Thread::current()));

  address return_address = *return_address_ptr;
  AARCH64_PORT_ONLY(return_address = pauth_strip_pointer(return_address));
  CodeBlob* cb = CodeCache::find_blob(return_address);
  assert(cb != nullptr, "invariant");

  nmethod* nm = cb->as_nmethod();
  BarrierSetNMethod* bs_nm = BarrierSet::barrier_set()->barrier_set_nmethod();

  // Called upon first entry after being armed
  bool may_enter = bs_nm->nmethod_entry_barrier(nm);
  assert(!nm->is_osr_method() || may_enter, "OSR nmethods should always be entrant after migration");

  // In case a concurrent thread disarmed the nmethod, we need to ensure the new instructions
  // are made visible, by using a cross modify fence. Note that this is synchronous cross modifying
  // code, where the existence of new instructions is communicated via data (the guard value).
  // This cross modify fence is only needed when the nmethod entry barrier modifies the
  // instructions. Not all platforms currently do that, so if this check becomes expensive,
  // it can be made conditional on the nmethod_patching_type.
  OrderAccess::cross_modify_fence();

  // Diagnostic option to force deoptimization 1 in 10 times. It is otherwise
  // a very rare event.
  if (DeoptimizeNMethodBarriersALot && !nm->is_osr_method()) {
    static volatile uint32_t counter=0;
    if (Atomic::add(&counter, 1u) % 10 == 0) {
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
  assert(nm->is_osr_method(), "Should not reach here");
  log_trace(nmethod, barrier)("Running osr nmethod entry barrier: " PTR_FORMAT, p2i(nm));
  bool result = nmethod_entry_barrier(nm);
  OrderAccess::cross_modify_fence();
  return result;
}
