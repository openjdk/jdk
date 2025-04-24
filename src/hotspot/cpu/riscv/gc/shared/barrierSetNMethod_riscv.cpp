/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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

#include "code/codeCache.hpp"
#include "code/nativeInst.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/registerMap.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#if INCLUDE_JVMCI
#include "jvmci/jvmciRuntime.hpp"
#endif

static int slow_path_size(nmethod* nm) {
  // The slow path code is out of line with C2.
  // Leave a jal to the stub in the fast path.
  return nm->is_compiled_by_c2() ? 1 : 8;
}

static int entry_barrier_offset(nmethod* nm) {
  BarrierSetAssembler* bs_asm = BarrierSet::barrier_set()->barrier_set_assembler();
  switch (bs_asm->nmethod_patching_type()) {
    case NMethodPatchingType::stw_instruction_and_data_patch:
      return -4 * (4 + slow_path_size(nm));
    case NMethodPatchingType::conc_data_patch:
      return -4 * (5 + slow_path_size(nm));
    case NMethodPatchingType::conc_instruction_and_data_patch:
      return -4 * (15 + slow_path_size(nm));
  }
  ShouldNotReachHere();
  return 0;
}

class NativeNMethodBarrier {
  address  _instruction_address;
  int*     _guard_addr;
  nmethod* _nm;

  address instruction_address() const { return _instruction_address; }

  int *guard_addr() {
    return _guard_addr;
  }

  int local_guard_offset(nmethod* nm) {
    // It's the last instruction
    return (-entry_barrier_offset(nm)) - 4;
  }

public:
  NativeNMethodBarrier(nmethod* nm): _nm(nm) {
#if INCLUDE_JVMCI
    if (nm->is_compiled_by_jvmci()) {
      address pc = nm->code_begin() + nm->jvmci_nmethod_data()->nmethod_entry_patch_offset();
      RelocIterator iter(nm, pc, pc + 4);
      guarantee(iter.next(), "missing relocs");
      guarantee(iter.type() == relocInfo::section_word_type, "unexpected reloc");

      _guard_addr = (int*) iter.section_word_reloc()->target();
      _instruction_address = pc;
    } else
#endif
      {
        _instruction_address = nm->code_begin() + nm->frame_complete_offset() + entry_barrier_offset(nm);
        if (nm->is_compiled_by_c2()) {
          // With c2 compiled code, the guard is out-of-line in a stub
          // We find it using the RelocIterator.
          RelocIterator iter(nm);
          while (iter.next()) {
            if (iter.type() == relocInfo::entry_guard_type) {
              entry_guard_Relocation* const reloc = iter.entry_guard_reloc();
              _guard_addr = reinterpret_cast<int*>(reloc->addr());
              return;
            }
          }
          ShouldNotReachHere();
        }
        _guard_addr = reinterpret_cast<int*>(instruction_address() + local_guard_offset(nm));
      }
  }

  int get_value() {
    return Atomic::load_acquire(guard_addr());
  }

  void set_value(int value) {
    Atomic::release_store(guard_addr(), value);
  }

  bool check_barrier(err_msg& msg) const;
  void verify() const {
    err_msg msg("%s", "");
    assert(check_barrier(msg), "%s", msg.buffer());
  }
};

// Store the instruction bitmask, bits and name for checking the barrier.
struct CheckInsn {
  uint32_t mask;
  uint32_t bits;
  const char *name;
};

static const struct CheckInsn barrierInsn[] = {
  { 0x00000fff, 0x00000297, "auipc  t0, 0                     "},
  { 0x000fffff, 0x0002e283, "lwu    t0, guard_offset(t0)      "},
  /* ...... */
  /* ...... */
  /* guard: */
  /* 32bit nmethod guard value */
};

// The encodings must match the instructions emitted by
// BarrierSetAssembler::nmethod_entry_barrier. The matching ignores the specific
// register numbers and immediate values in the encoding.
bool NativeNMethodBarrier::check_barrier(err_msg& msg) const {
  address addr = instruction_address();
  for(unsigned int i = 0; i < sizeof(barrierInsn)/sizeof(struct CheckInsn); i++ ) {
    uint32_t inst = Assembler::ld_instr(addr);
    if ((inst & barrierInsn[i].mask) != barrierInsn[i].bits) {
      msg.print("Addr: " INTPTR_FORMAT " Code: 0x%x not an %s instruction", p2i(addr), inst, barrierInsn[i].name);
      return false;
    }
    addr += 4;
  }
  return true;
}


/* We're called from an nmethod when we need to deoptimize it. We do
   this by throwing away the nmethod's frame and jumping to the
   ic_miss stub. This looks like there has been an IC miss at the
   entry of the nmethod, so we resolve the call, which will fall back
   to the interpreter if the nmethod has been unloaded. */
void BarrierSetNMethod::deoptimize(nmethod* nm, address* return_address_ptr) {

  typedef struct {
    intptr_t *sp; intptr_t *fp; address ra; address pc;
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
  new_frame->ra = frame.pc();
  new_frame->pc = SharedRuntime::get_handle_wrong_method_stub();
}

void BarrierSetNMethod::set_guard_value(nmethod* nm, int value) {
  if (!supports_entry_barrier(nm)) {
    return;
  }

  if (value == disarmed_guard_value()) {
    // The patching epoch is incremented before the nmethod is disarmed. Disarming
    // is performed with a release store. In the nmethod entry barrier, the values
    // are read in the opposite order, such that the load of the nmethod guard
    // acquires the patching epoch. This way, the guard is guaranteed to block
    // entries to the nmethod, until it has safely published the requirement for
    // further fencing by mutators, before they are allowed to enter.
    BarrierSetAssembler* bs_asm = BarrierSet::barrier_set()->barrier_set_assembler();
    bs_asm->increment_patching_epoch();
  }

  NativeNMethodBarrier barrier(nm);
  barrier.set_value(value);
}

int BarrierSetNMethod::guard_value(nmethod* nm) {
  if (!supports_entry_barrier(nm)) {
    return disarmed_guard_value();
  }

  NativeNMethodBarrier barrier(nm);
  return barrier.get_value();
}

#if INCLUDE_JVMCI
bool BarrierSetNMethod::verify_barrier(nmethod* nm, err_msg& msg) {
  NativeNMethodBarrier barrier(nm);
  return barrier.check_barrier(msg);
}
#endif
