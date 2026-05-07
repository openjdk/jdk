/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "asm/macroAssembler.hpp"
#include "code/codeCache.hpp"
#include "code/nativeInst.hpp"
#include "gc/shared/barrierSet.hpp"
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
#include "utilities/formatBuffer.hpp"
#if INCLUDE_JVMCI
#include "jvmci/jvmciRuntime.hpp"
#endif

static int slow_path_size(nmethod* nm) {
  // The slow path code is out of line with C2
  return nm->is_compiled_by_c2() ? 0 : 6;
}

// This is the offset of the entry barrier relative to where the frame is completed.
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
  }
  ShouldNotReachHere();
  return 0;
}

static int* decode_guard_from_instruction(nmethod* nm, address& instruction) {
  int* result = reinterpret_cast<int*>(MacroAssembler::target_addr_for_insn(instruction));
  assert(nm->insts_contains(reinterpret_cast<address>(result)) ||
         nm->stub_contains(reinterpret_cast<address>(result)),
         "guard must be in nmethod code");
  return result;
}

// The NativeNMethodBarrier class encapsulates up to three entrypoints and handles their
// arming/verification.
// An entrypoint is defined as a tuple of <instr. address, guard address>:
// * The instr. address corresponds to the ldr of the guard value of that entrypoint.
// * The guard address is the address where the guard value of that entrypoint resides.
//
// Each nmethod has at least one entrypoint. The default must always be well-defined
// (neither instruction nor guard are nullptr).
//
// When using the scalarized calling convention, up to two additional (verified) entrypoints,
// alt1 and alt2 can be present. The meaning of these depends on who compiled the nmethod.
//
// The mapping of C1-compiled methods (scalarization used) looks as follows:
// * alt1: verified entry point
// * alt2 (optional): verified inline ro entry point
//
// The mapping of C2-compiled methods (scalarization used) looks as follows:
// * alt1: verified inline entry point
// * alt2 (optional): verified inline ro entry point
//
// In other scenarios, neither alt1 nor alt2 are defined.
class NativeNMethodBarrier {
 private:
  // The addresses of the instructions that act as the guards.
  address _default_entry_instruction;
  address _verified_alt1_instruction;
  address _verified_alt2_instruction;
  // Pointers representing the actual guard values themselves.
  int* _default_entry_guard;
  int* _verified_alt1_guard;
  int* _verified_alt2_guard;

 public:
  NativeNMethodBarrier(nmethod* nm) :
    _default_entry_instruction(nullptr),
    _verified_alt1_instruction(nullptr),
    _verified_alt2_instruction(nullptr),
    _default_entry_guard(nullptr),
    _verified_alt1_guard(nullptr),
    _verified_alt2_guard(nullptr) {
#if INCLUDE_JVMCI
    if (nm->is_compiled_by_jvmci()) {
      address pc = nm->code_begin() + nm->jvmci_nmethod_data()->nmethod_entry_patch_offset();
      RelocIterator iter(nm, pc, pc + 4);
      guarantee(iter.next(), "missing relocs");
      guarantee(iter.type() == relocInfo::section_word_type, "unexpected reloc");

      // Only set the verified entry, the others are not supported.
      _default_entry_guard = (int*) iter.section_word_reloc()->target();
      _default_entry_instruction = pc;
    } else
#endif
    {
      // The default entry point has a known address. The guard address can be
      // decoded from the literal in the instruction. Verification will confirm
      // that this instruction corresponds to a load.
      _default_entry_instruction = nm->code_begin() + nm->frame_complete_offset() + entry_barrier_offset(nm);
      _default_entry_guard = decode_guard_from_instruction(nm, _default_entry_instruction);

      // If the nmethod has scalarized arguments, then there are more entry
      // points, each with their own nmethod entry barrier.
      if (!nm->is_osr_method() && nm->method()->has_scalarized_args()) {
        assert(nm->verified_entry_point() != nm->verified_inline_entry_point(), "scalarized entry point not found");
        address method_body = nm->is_compiled_by_c1() ? nm->verified_inline_entry_point() : nm->verified_entry_point();
        int barrier_offset = _default_entry_instruction - method_body;

        // Set the first alternative entry point.
        address entry_point2 = nm->is_compiled_by_c1() ? nm->verified_entry_point() : nm->verified_inline_entry_point();
        _verified_alt1_instruction = entry_point2 + barrier_offset;
        assert(_default_entry_instruction != _verified_alt1_instruction, "sanity");
        _verified_alt1_guard = decode_guard_from_instruction(nm, _verified_alt1_instruction);

        // If there is a second alternative entry point, set it too.
        if (method_body != nm->verified_inline_ro_entry_point() && entry_point2 != nm->verified_inline_ro_entry_point()) {
          _verified_alt2_instruction = nm->verified_inline_ro_entry_point() + barrier_offset;
          _verified_alt2_guard = decode_guard_from_instruction(nm, _verified_alt2_instruction);
          assert(_default_entry_instruction != _verified_alt2_instruction &&
                 _verified_alt1_instruction != _verified_alt2_instruction,
                 "sanity");
        }
      }
      // Perform the checking as verification.
      err_msg msg("%s", "");
      assert(check_barriers(msg), "%s", msg.buffer());
    }
  }

  // Gets the value of the default entry guard.
  // This does not consider the alternative entrypoints, as these should
  // all be consistent. It is up to the caller to enforce this.
  int get_default_guard_value() {
    return AtomicAccess::load_acquire(_default_entry_guard);
  }

  // Sets the value for all barriers.
  void set_values(int value, int bit_mask) {
    set_value_impl(_default_entry_guard, value, bit_mask);
    if (_verified_alt1_guard != nullptr) {
      set_value_impl(_verified_alt1_guard, value, bit_mask);
    }
    if (_verified_alt2_guard != nullptr) {
      set_value_impl(_verified_alt2_guard, value, bit_mask);
    }
  }

  // Verifies that all potential barriers are correct.
  bool check_barriers(err_msg& msg) {
    // The default entry barrier should always be checked.
    if (!check_barrier_impl(_default_entry_instruction, msg)) {
      return false;
    }
    // Check the alternative entry barriers only if they are specified.
    // Note that the guard values are already validated at construction time,
    // if they fall out of the nmethod range, this will be caught earlier.
    if (_verified_alt1_instruction != nullptr &&
        !check_barrier_impl(_verified_alt1_instruction, msg)) {
      return false;
    }
    if (_verified_alt2_instruction != nullptr &&
        !check_barrier_impl(_verified_alt2_instruction, msg)) {
      return false;
    }
    return true;
  }

private:
  // Sets the value for a single barrier.
  void set_value_impl(int* guard, int value, int bit_mask) {
    if (bit_mask == ~0) {
      AtomicAccess::release_store(guard, value);
      return;
    }
    assert((value & ~bit_mask) == 0, "trying to set bits outside the mask");
    value &= bit_mask;
    int old_value = AtomicAccess::load(guard);
    while (true) {
      // Only bits in the mask are changed
      int new_value = value | (old_value & ~bit_mask);
      if (new_value == old_value) break;
      int v = AtomicAccess::cmpxchg(guard, old_value, new_value, memory_order_release);
      if (v == old_value) break;
      old_value = v;
    }
  }

  // Checks the validity of a single barrier.
  bool check_barrier_impl(address& instruction, err_msg& msg) {
    uint32_t* addr = (uint32_t*) instruction;
    uint32_t inst = *addr;
    if ((inst & 0xff000000) != 0x18000000) {
      msg.print("Nmethod entry barrier did not start with ldr (literal) as expected. "
                "Addr: " PTR_FORMAT " Code: " UINT32_FORMAT, p2i(addr), inst);
      return false;
    }
    return true;
  }
};

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

void BarrierSetNMethod::set_guard_value(nmethod* nm, int value, int bit_mask) {
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

  // Enable WXWrite: the function is called directly from nmethod_entry_barrier
  // stub.
  MACOS_AARCH64_ONLY(ThreadWXEnable wx(WXWrite, Thread::current()));

  NativeNMethodBarrier barrier(nm);
  barrier.set_values(value, bit_mask);
}

int BarrierSetNMethod::guard_value(nmethod* nm) {
  if (!supports_entry_barrier(nm)) {
    return disarmed_guard_value();
  }

  NativeNMethodBarrier barrier(nm);
  return barrier.get_default_guard_value();
}

#if INCLUDE_JVMCI
bool BarrierSetNMethod::verify_barrier(nmethod* nm, err_msg& msg) {
  NativeNMethodBarrier barrier(nm);
  return barrier.check_barriers(msg);
}
#endif
