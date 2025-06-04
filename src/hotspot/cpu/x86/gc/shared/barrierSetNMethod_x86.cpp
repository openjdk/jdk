/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/barrierSetNMethod.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_JVMCI
#include "jvmci/jvmciRuntime.hpp"
#endif

class NativeNMethodCmpBarrier: public NativeInstruction {
public:
  enum Intel_specific_constants {
    instruction_code        = 0x81,
    instruction_size        = 8,
    imm_offset              = 4,
    instruction_rex_prefix  = Assembler::REX | Assembler::REX_B,
    instruction_modrm       = 0x7f  // [r15 + offset]
  };

  address instruction_address() const { return addr_at(0); }
  address immediate_address() const { return addr_at(imm_offset); }

  jint get_immediate() const { return int_at(imm_offset); }
  void set_immediate(jint imm) { set_int_at(imm_offset, imm); }
  bool check_barrier(err_msg& msg) const;
  void verify() const {
#ifdef ASSERT
    err_msg msg("%s", "");
    assert(check_barrier(msg), "%s", msg.buffer());
#endif
  }
};

bool NativeNMethodCmpBarrier::check_barrier(err_msg& msg) const {
  // Only require 4 byte alignment
  if (((uintptr_t) instruction_address()) & 0x3) {
    msg.print("Addr: " INTPTR_FORMAT " not properly aligned", p2i(instruction_address()));
    return false;
  }

  int prefix = ubyte_at(0);
  if (prefix != instruction_rex_prefix) {
    msg.print("Addr: " INTPTR_FORMAT " Code: 0x%x expected 0x%x", p2i(instruction_address()), prefix, instruction_rex_prefix);
    return false;
  }

  int inst = ubyte_at(1);
  if (inst != instruction_code) {
    msg.print("Addr: " INTPTR_FORMAT " Code: 0x%x expected 0x%x", p2i(instruction_address()), inst, instruction_code);
    return false;
  }

  int modrm = ubyte_at(2);
  if (modrm != instruction_modrm) {
    msg.print("Addr: " INTPTR_FORMAT " Code: 0x%x expected mod/rm 0x%x", p2i(instruction_address()), modrm, instruction_modrm);
    return false;
  }
  return true;
}

void BarrierSetNMethod::deoptimize(nmethod* nm, address* return_address_ptr) {
  /*
   * [ callers frame          ]
   * [ callers return address ] <- callers rsp
   * [ callers rbp            ] <- callers rbp
   * [ callers frame slots    ]
   * [ return_address         ] <- return_address_ptr
   * [ cookie ]                 <- used to write the new rsp (callers rsp)
   * [ stub rbp ]
   * [ stub stuff             ]
   */

  address* stub_rbp = return_address_ptr - 2;
  address* callers_rsp = return_address_ptr + nm->frame_size(); /* points to callers return_address now */
  address* callers_rbp = callers_rsp - 1; // 1 to move to the callers return address, 1 more to move to the rbp
  address* cookie = return_address_ptr - 1;

  LogTarget(Trace, nmethod, barrier) out;
  if (out.is_enabled()) {
    JavaThread* jth = JavaThread::current();
    ResourceMark mark;
    log_trace(nmethod, barrier)("deoptimize(nmethod: %p, return_addr: %p, osr: %d, thread: %p(%s), making rsp: %p) -> %p",
                               nm, (address *) return_address_ptr, nm->is_osr_method(), jth,
                               jth->name(), callers_rsp, nm->verified_entry_point());
  }

  assert(nm->frame_size() >= 3, "invariant");
  assert(*cookie == (address) -1, "invariant");

  // Preserve caller rbp.
  *stub_rbp = *callers_rbp;

  // At the cookie address put the callers rsp.
  *cookie = (address) callers_rsp; // should point to the return address

  // In the slot that used to be the callers rbp we put the address that our stub needs to jump to at the end.
  // Overwriting the caller rbp should be okay since our stub rbp has the same value.
  address* jmp_addr_ptr = callers_rbp;
  *jmp_addr_ptr = SharedRuntime::get_handle_wrong_method_stub();
}

// This is the offset of the entry barrier from where the frame is completed.
// If any code changes between the end of the verified entry where the entry
// barrier resides, and the completion of the frame, then
// NativeNMethodCmpBarrier::verify() will immediately complain when it does
// not find the expected native instruction at this offset, which needs updating.
// Note that this offset is invariant of PreserveFramePointer.
static int entry_barrier_offset(nmethod* nm) {
  if (nm->is_compiled_by_c2()) {
    return -14;
  } else {
    return -15;
  }
}

static NativeNMethodCmpBarrier* native_nmethod_barrier(nmethod* nm) {
  address barrier_address;
#if INCLUDE_JVMCI
  if (nm->is_compiled_by_jvmci()) {
    barrier_address = nm->code_begin() + nm->jvmci_nmethod_data()->nmethod_entry_patch_offset();
  } else
#endif
    {
      barrier_address = nm->code_begin() + nm->frame_complete_offset() + entry_barrier_offset(nm);
    }

  NativeNMethodCmpBarrier* barrier = reinterpret_cast<NativeNMethodCmpBarrier*>(barrier_address);
  barrier->verify();
  return barrier;
}

void BarrierSetNMethod::set_guard_value(nmethod* nm, int value) {
  if (!supports_entry_barrier(nm)) {
    return;
  }

  NativeNMethodCmpBarrier* cmp = native_nmethod_barrier(nm);
  cmp->set_immediate(value);
}

int BarrierSetNMethod::guard_value(nmethod* nm) {
  if (!supports_entry_barrier(nm)) {
    return disarmed_guard_value();
  }

  NativeNMethodCmpBarrier* cmp = native_nmethod_barrier(nm);
  return cmp->get_immediate();
}


#if INCLUDE_JVMCI
bool BarrierSetNMethod::verify_barrier(nmethod* nm, err_msg& msg) {
  NativeNMethodCmpBarrier* barrier = native_nmethod_barrier(nm);
  return barrier->check_barrier(msg);
}
#endif
