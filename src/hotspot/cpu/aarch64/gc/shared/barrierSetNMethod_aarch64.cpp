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
#include "code/nativeInst.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/registerMap.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

static int slow_path_size(nmethod* nm) {
  // The slow path code is out of line with C2
  return nm->is_compiled_by_c2() ? 0 : 6;
}

// This is the offset of the entry barrier from where the frame is completed.
// If any code changes between the end of the verified entry where the entry
// barrier resides, and the completion of the frame, then
// NativeNMethodCmpBarrier::verify() will immediately complain when it does
// not find the expected native instruction at this offset, which needs updating.
// Note that this offset is invariant of PreserveFramePointer.
static int entry_barrier_offset(nmethod* nm) {
  BarrierSetAssembler* bs_asm = BarrierSet::barrier_set()->barrier_set_assembler();
  switch (bs_asm->nmethod_patching_type()) {
  case NMethodPatchingType::stw_instruction_and_data_patch:
    return -4 * (4 + slow_path_size(nm));
  case NMethodPatchingType::conc_instruction_and_data_patch:
    return -4 * (10 + slow_path_size(nm));
  case NMethodPatchingType::conc_data_patch:
    return -4 * (5 + slow_path_size(nm));
  }
  ShouldNotReachHere();
  return 0;
}

class NativeNMethodBarrier: public NativeInstruction {
  address instruction_address() const { return addr_at(0); }

  int local_guard_offset(nmethod* nm) {
    // It's the last instruction
    return (-entry_barrier_offset(nm)) - 4;
  }

  int *guard_addr(nmethod* nm) {
    if (nm->is_compiled_by_c2()) {
      // With c2 compiled code, the guard is out-of-line in a stub
      // We find it using the RelocIterator.
      RelocIterator iter(nm);
      while (iter.next()) {
        if (iter.type() == relocInfo::entry_guard_type) {
          entry_guard_Relocation* const reloc = iter.entry_guard_reloc();
          return reinterpret_cast<int*>(reloc->addr());
        }
      }
      ShouldNotReachHere();
    }
    return reinterpret_cast<int*>(instruction_address() + local_guard_offset(nm));
  }

public:
  int get_value(nmethod* nm) {
    return Atomic::load_acquire(guard_addr(nm));
  }

  void set_value(nmethod* nm, int value) {
    Atomic::release_store(guard_addr(nm), value);
  }

  void verify() const;
};

// Store the instruction bitmask, bits and name for checking the barrier.
struct CheckInsn {
  uint32_t mask;
  uint32_t bits;
  const char *name;
};

// The first instruction of the nmethod entry barrier is an ldr (literal)
// instruction. Verify that it's really there, so the offsets are not skewed.
void NativeNMethodBarrier::verify() const {
  uint32_t* addr = (uint32_t*) instruction_address();
  uint32_t inst = *addr;
  if ((inst & 0xff000000) != 0x18000000) {
    tty->print_cr("Addr: " INTPTR_FORMAT " Code: 0x%x", (intptr_t)addr, inst);
    fatal("not an ldr (literal) instruction.");
  }
}


/* We're called from an nmethod when we need to deoptimize it. We do
   this by throwing away the nmethod's frame and jumping to the
   ic_miss stub. This looks like there has been an IC miss at the
   entry of the nmethod, so we resolve the call, which will fall back
   to the interpreter if the nmethod has been unloaded. */
void BarrierSetNMethod::deoptimize(nmethod* nm, address* return_address_ptr) {

  typedef struct {
    intptr_t *sp; intptr_t *fp; address lr; address pc;
  } frame_pointers_t;

  frame_pointers_t *new_frame = (frame_pointers_t *)(return_address_ptr - 5);

  JavaThread *thread = JavaThread::current();
  RegisterMap reg_map(thread,
                      RegisterMap::UpdateMap::skip,
                      RegisterMap::ProcessFrames::include,
                      RegisterMap::WalkContinuation::skip);
  frame frame = thread->last_frame();

  assert(frame.is_compiled_frame() || frame.is_native_frame(), "must be");
  assert(frame.cb() == nm, "must be");
  frame = frame.sender(&reg_map);

  LogTarget(Trace, nmethod, barrier) out;
  if (out.is_enabled()) {
    ResourceMark mark;
    log_trace(nmethod, barrier)("deoptimize(nmethod: %s(%p), return_addr: %p, osr: %d, thread: %p(%s), making rsp: %p) -> %p",
                                nm->method()->name_and_sig_as_C_string(),
                                nm, *(address *) return_address_ptr, nm->is_osr_method(), thread,
                                thread->name(), frame.sp(), nm->verified_entry_point());
  }

  new_frame->sp = frame.sp();
  new_frame->fp = frame.fp();
  new_frame->lr = frame.pc();
  new_frame->pc = SharedRuntime::get_handle_wrong_method_stub();
}

static NativeNMethodBarrier* native_nmethod_barrier(nmethod* nm) {
  address barrier_address = nm->code_begin() + nm->frame_complete_offset() + entry_barrier_offset(nm);
  NativeNMethodBarrier* barrier = reinterpret_cast<NativeNMethodBarrier*>(barrier_address);
  debug_only(barrier->verify());
  return barrier;
}

void BarrierSetNMethod::disarm(nmethod* nm) {
  if (!supports_entry_barrier(nm)) {
    return;
  }

  // The patching epoch is incremented before the nmethod is disarmed. Disarming
  // is performed with a release store. In the nmethod entry barrier, the values
  // are read in the opposite order, such that the load of the nmethod guard
  // acquires the patching epoch. This way, the guard is guaranteed to block
  // entries to the nmethod, until it has safely published the requirement for
  // further fencing by mutators, before they are allowed to enter.
  BarrierSetAssembler* bs_asm = BarrierSet::barrier_set()->barrier_set_assembler();
  bs_asm->increment_patching_epoch();

  // Disarms the nmethod guard emitted by BarrierSetAssembler::nmethod_entry_barrier.
  // Symmetric "LDR; DMB ISHLD" is in the nmethod barrier.
  NativeNMethodBarrier* barrier = native_nmethod_barrier(nm);
  barrier->set_value(nm, disarmed_value());
}

void BarrierSetNMethod::arm(nmethod* nm, int arm_value) {
  if (!supports_entry_barrier(nm)) {
    return;
  }

  if (arm_value == disarmed_value()) {
    // The patching epoch is incremented before the nmethod is disarmed. Disarming
    // is performed with a release store. In the nmethod entry barrier, the values
    // are read in the opposite order, such that the load of the nmethod guard
    // acquires the patching epoch. This way, the guard is guaranteed to block
    // entries to the nmethod, until it has safely published the requirement for
    // further fencing by mutators, before they are allowed to enter.
    BarrierSetAssembler* bs_asm = BarrierSet::barrier_set()->barrier_set_assembler();
    bs_asm->increment_patching_epoch();
  }

  NativeNMethodBarrier* barrier = native_nmethod_barrier(nm);
  barrier->set_value(nm, arm_value);
}

bool BarrierSetNMethod::is_armed(nmethod* nm) {
  if (!supports_entry_barrier(nm)) {
    return false;
  }

  NativeNMethodBarrier* barrier = native_nmethod_barrier(nm);
  return barrier->get_value(nm) != disarmed_value();
}
