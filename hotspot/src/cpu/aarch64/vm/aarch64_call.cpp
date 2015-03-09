/*
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
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

#ifdef BUILTIN_SIM

#include <stdio.h>
#include <sys/types.h>
#include "asm/macroAssembler.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "../../../../../../simulator/cpustate.hpp"
#include "../../../../../../simulator/simulator.hpp"

/*
 * a routine to initialise and enter ARM simulator execution when
 * calling into ARM code from x86 code.
 *
 * we maintain a simulator per-thread and provide it with 8 Mb of
 * stack space
 */
#define SIM_STACK_SIZE (1024 * 1024) // in units of u_int64_t

extern "C" u_int64_t get_alt_stack()
{
  return AArch64Simulator::altStack();
}

extern "C" void setup_arm_sim(void *sp, u_int64_t calltype)
{
  // n.b. this function runs on the simulator stack so as to avoid
  // simulator frames appearing in between VM x86 and ARM frames. note
  // that arfgument sp points to the old (VM) stack from which the
  // call into the sim was made. The stack switch and entry into this
  // routine is handled by x86 prolog code planted in the head of the
  // ARM code buffer which the sim is about to start executing (see
  // aarch64_linkage.S).
  //
  // The first ARM instruction in the buffer is identified by fnptr
  // stored at the top of the old stack. x86 register contents precede
  // fnptr. preceding that are the fp and return address of the VM
  // caller into ARM code. any extra, non-register arguments passed to
  // the linkage routine precede the fp (this is as per any normal x86
  // call wirth extra args).
  //
  // note that the sim creates Java frames on the Java stack just
  // above sp (i.e. directly above fnptr). it sets the sim FP register
  // to the pushed fp for the caller effectively eliding the register
  // data saved by the linkage routine.
  //
  // x86 register call arguments are loaded from the stack into ARM
  // call registers. if extra arguments occur preceding the x86
  // caller's fp then they are copied either into extra ARM registers
  // (ARM has 8 rather than 6 gp call registers) or up the stack
  // beyond the saved x86 registers so that they immediately precede
  // the ARM frame where the ARM calling convention expects them to
  // be.
  //
  // n.b. the number of register/stack values passed to the ARM code
  // is determined by calltype
  //
  // +--------+
  // | fnptr  |  <--- argument sp points here
  // +--------+  |
  // | rax    |  | return slot if we need to return a value
  // +--------+  |
  // | rdi    |  increasing
  // +--------+  address
  // | rsi    |  |
  // +--------+  V
  // | rdx    |
  // +--------+
  // | rcx    |
  // +--------+
  // | r8     |
  // +--------+
  // | r9     |
  // +--------+
  // | xmm0   |
  // +--------+
  // | xmm1   |
  // +--------+
  // | xmm2   |
  // +--------+
  // | xmm3   |
  // +--------+
  // | xmm4   |
  // +--------+
  // | xmm5   |
  // +--------+
  // | xmm6   |
  // +--------+
  // | xmm7   |
  // +--------+
  // | fp     |
  // +--------+
  // | caller |
  // | ret ip |
  // +--------+
  // | arg0   | <-- any extra call args start here
  // +--------+     offset = 18 * wordSize
  // | . . .  |     (i.e. 1 * calladdr + 1 * rax  + 6 * gp call regs
  //                      + 8 * fp call regs + 2 * frame words)
  //
  // we use a unique sim/stack per thread
  const int cursor2_offset = 18;
  const int fp_offset = 16;
  u_int64_t *cursor = (u_int64_t *)sp;
  u_int64_t *cursor2 = ((u_int64_t *)sp) + cursor2_offset;
  u_int64_t *fp = ((u_int64_t *)sp) + fp_offset;
  int gp_arg_count = calltype & 0xf;
  int fp_arg_count = (calltype >> 4) & 0xf;
  int return_type = (calltype >> 8) & 0x3;
  AArch64Simulator *sim = AArch64Simulator::get_current(UseSimulatorCache, DisableBCCheck);
  // save previous cpu state in case this is a recursive entry
  CPUState saveState = sim->getCPUState();
  // set up initial sim pc, sp and fp registers
  sim->init(*cursor++, (u_int64_t)sp, (u_int64_t)fp);
  u_int64_t *return_slot = cursor++;

  // if we need to pass the sim extra args on the stack then bump
  // the stack pointer now
  u_int64_t *cursor3 = (u_int64_t *)sim->getCPUState().xreg(SP, 1);
  if (gp_arg_count > 8) {
    cursor3 -= gp_arg_count - 8;
  }
  if (fp_arg_count > 8) {
    cursor3 -= fp_arg_count - 8;
  }
  sim->getCPUState().xreg(SP, 1) = (u_int64_t)(cursor3++);

  for (int i = 0; i < gp_arg_count; i++) {
    if (i < 6) {
      // copy saved register to sim register
      GReg reg = (GReg)i;
      sim->getCPUState().xreg(reg, 0) = *cursor++;
    } else if (i < 8) {
      // copy extra int arg to sim register
      GReg reg = (GReg)i;
      sim->getCPUState().xreg(reg, 0) = *cursor2++;
    } else {
      // copy extra fp arg to sim stack
      *cursor3++ = *cursor2++;
    }
  }
  for (int i = 0; i < fp_arg_count; i++) {
    if (i < 8) {
      // copy saved register to sim register
      GReg reg = (GReg)i;
      sim->getCPUState().xreg(reg, 0) = *cursor++;
    } else {
      // copy extra arg to sim stack
      *cursor3++ = *cursor2++;
    }
  }
  AArch64Simulator::status_t return_status = sim->run();
  if (return_status != AArch64Simulator::STATUS_RETURN){
    sim->simPrint0();
    fatal("invalid status returned from simulator.run()\n");
  }
  switch (return_type) {
  case MacroAssembler::ret_type_void:
  default:
    break;
  case MacroAssembler::ret_type_integral:
  // this overwrites the saved r0
    *return_slot = sim->getCPUState().xreg(R0, 0);
    break;
  case MacroAssembler::ret_type_float:
    *(float *)return_slot = sim->getCPUState().sreg(V0);
    break;
  case MacroAssembler::ret_type_double:
    *(double *)return_slot = sim->getCPUState().dreg(V0);
    break;
  }
  // restore incoimng cpu state
  sim->getCPUState() = saveState;
}

#endif
