/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "code/compiledIC.hpp"
#include "memory/resourceArea.hpp"
#include "nativeInst_x86.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/ostream.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#endif

void NativeInstruction::wrote(int offset) {
  ICache::invalidate_word(addr_at(offset));
}

void NativeCall::verify() {
  // Make sure code pattern is actually a call imm32 instruction.
  int inst = ubyte_at(0);
  if (inst != instruction_code) {
    tty->print_cr("Addr: " INTPTR_FORMAT " Code: 0x%x", p2i(instruction_address()),
                                                        inst);
    fatal("not a call disp32");
  }
}

address NativeCall::destination() const {
  // Getting the destination of a call isn't safe because that call can
  // be getting patched while you're calling this.  There's only special
  // places where this can be called but not automatically verifiable by
  // checking which locks are held.  The solution is true atomic patching
  // on x86, nyi.
  return return_address() + displacement();
}

void NativeCall::print() {
  tty->print_cr(PTR_FORMAT ": call " PTR_FORMAT,
                p2i(instruction_address()), p2i(destination()));
}

// Inserts a native call instruction at a given pc
void NativeCall::insert(address code_pos, address entry) {
  intptr_t disp = (intptr_t)entry - ((intptr_t)code_pos + 1 + 4);
  guarantee(disp == (intptr_t)(jint)disp, "must be 32-bit offset");
  *code_pos = instruction_code;
  *((int32_t *)(code_pos+1)) = (int32_t) disp;
  ICache::invalidate_range(code_pos, instruction_size);
}

// MT-safe patching of a call instruction.
// First patches first word of instruction to two jmp's that jmps to themselves
// (spinlock). Then patches the last byte, and then atomically replaces
// the jmp's with the first 4 byte of the new instruction.
void NativeCall::replace_mt_safe(address instr_addr, address code_buffer) {
  assert(CodeCache_lock->is_locked() ||
         SafepointSynchronize::is_at_safepoint(), "concurrent code patching");
  assert (instr_addr != nullptr, "illegal address for code patching");

  NativeCall* n_call =  nativeCall_at (instr_addr); // checking that it is a call
  guarantee((intptr_t)instr_addr % BytesPerWord == 0, "must be aligned");

  // First patch dummy jmp in place
  unsigned char patch[4];
  assert(sizeof(patch)==sizeof(jint), "sanity check");
  patch[0] = 0xEB;       // jmp rel8
  patch[1] = 0xFE;       // jmp to self
  patch[2] = 0xEB;
  patch[3] = 0xFE;

  // First patch dummy jmp in place
  *(jint*)instr_addr = *(jint *)patch;

  // Invalidate.  Opteron requires a flush after every write.
  n_call->wrote(0);

  // Patch 4th byte
  instr_addr[4] = code_buffer[4];

  n_call->wrote(4);

  // Patch bytes 0-3
  *(jint*)instr_addr = *(jint *)code_buffer;

  n_call->wrote(0);

#ifdef ASSERT
   // verify patching
   for ( int i = 0; i < instruction_size; i++) {
     address ptr = (address)((intptr_t)code_buffer + i);
     int a_byte = (*ptr) & 0xFF;
     assert(*((address)((intptr_t)instr_addr + i)) == a_byte, "mt safe patching failed");
   }
#endif

}

bool NativeCall::is_displacement_aligned() {
  return (uintptr_t) displacement_address() % 4 == 0;
}

// Similar to replace_mt_safe, but just changes the destination.  The
// important thing is that free-running threads are able to execute this
// call instruction at all times.  If the displacement field is aligned
// we can simply rely on atomicity of 32-bit writes to make sure other threads
// will see no intermediate states.  Otherwise, the first two bytes of the
// call are guaranteed to be aligned, and can be atomically patched to a
// self-loop to guard the instruction while we change the other bytes.

// We cannot rely on locks here, since the free-running threads must run at
// full speed.
//
// Used in the runtime linkage of calls; see class CompiledIC.
// (Cf. 4506997 and 4479829, where threads witnessed garbage displacements.)
void NativeCall::set_destination_mt_safe(address dest) {
  DEBUG_ONLY(verify());
  // Make sure patching code is locked.  No two threads can patch at the same
  // time but one may be executing this code.
  assert(CodeCache_lock->is_locked() || SafepointSynchronize::is_at_safepoint() ||
         CompiledICLocker::is_safe(instruction_address()), "concurrent code patching");
  // Both C1 and C2 should now be generating code which aligns the patched address
  // to be within a single cache line.
  bool is_aligned = is_displacement_aligned();

  guarantee(is_aligned, "destination must be aligned");

  // The destination lies within a single cache line.
  set_destination(dest);
}


void NativeMovConstReg::verify() {
  // make sure code pattern is actually a mov reg64, imm64 instruction
  bool valid_rex_prefix  = ubyte_at(0) == Assembler::REX_W || ubyte_at(0) == Assembler::REX_WB;
  bool valid_rex2_prefix = ubyte_at(0) == Assembler::REX2  &&
       (ubyte_at(1) == Assembler::REX2BIT_W  ||
        ubyte_at(1) == Assembler::REX2BIT_WB ||
        ubyte_at(1) == Assembler::REX2BIT_WB4);
  int opcode = has_rex2_prefix() ? ubyte_at(2) : ubyte_at(1);
  if ((!valid_rex_prefix || !valid_rex2_prefix) && (opcode & (0xff ^ register_mask)) != 0xB8) {
    print();
    fatal("not a REX.W[B] mov reg64, imm64");
  }
}


void NativeMovConstReg::print() {
  tty->print_cr(PTR_FORMAT ": mov reg, " INTPTR_FORMAT,
                p2i(instruction_address()), data());
}

//-------------------------------------------------------------------

int NativeMovRegMem::instruction_start() const {
  int off = 0;
  u_char instr_0 = ubyte_at(off);

  // See comment in Assembler::locate_operand() about VEX prefixes.
  if (instr_0 == instruction_VEX_prefix_2bytes) {
    assert((UseAVX > 0), "shouldn't have VEX prefix");
    return 2;
  }
  if (instr_0 == instruction_VEX_prefix_3bytes) {
    assert((UseAVX > 0), "shouldn't have VEX prefix");
    return 3;
  }
  if (instr_0 == instruction_EVEX_prefix_4bytes) {
    assert(VM_Version::supports_evex(), "shouldn't have EVEX prefix");
    return 4;
  }

  // First check to see if we have a (prefixed or not) xor
  if (instr_0 >= instruction_prefix_wide_lo && // 0x40
      instr_0 <= instruction_prefix_wide_hi) { // 0x4f
    off++;
    instr_0 = ubyte_at(off);
  }

  if (instr_0 == instruction_REX2_prefix) {
    off+=2;
    instr_0 = ubyte_at(off);
  }

  if (instr_0 == instruction_code_xor) {
    off += 2;
    instr_0 = ubyte_at(off);
  }

  // Now look for the real instruction and the many prefix/size specifiers.

  if (instr_0 == instruction_operandsize_prefix ) {  // 0x66
    off++; // Not SSE instructions
    instr_0 = ubyte_at(off);
  }

  if ( instr_0 == instruction_code_xmm_ss_prefix || // 0xf3
       instr_0 == instruction_code_xmm_sd_prefix) { // 0xf2
    off++;
    instr_0 = ubyte_at(off);
  }

  if (instr_0 == instruction_REX2_prefix) {
    off+=2;
    instr_0 = ubyte_at(off);
  }

  if ( instr_0 >= instruction_prefix_wide_lo && // 0x40
       instr_0 <= instruction_prefix_wide_hi) { // 0x4f
    off++;
    instr_0 = ubyte_at(off);
  }

  // Extended prefixes can only follow REX prefixes,
  // REX2 is directly followed by main opcode.
  if (instr_0 == instruction_extended_prefix ) {  // 0x0f
    off++;
  }

  // Offset of instruction opcode.
  return off;
}

// Format [REX/REX2] [OPCODE] [ModRM] [SIB] [IMM/DISP32]
int NativeMovRegMem::patch_offset() const {
  int off = data_offset + instruction_start();
  u_char mod_rm = *(u_char*)(instruction_address() + 1);
  // nnnn(r12|rsp) isn't coded as simple mod/rm since that is
  // the encoding to use an SIB byte. Which will have the nnnn
  // field off by one byte
  // ModRM Byte Format = Mod[2] REG[3] RM[3]
  if ((mod_rm & 7) == 0x4) {
    off++;
  }
  // Displacement offset.
  return off;
}

void NativeMovRegMem::verify() {
  // make sure code pattern is actually a mov [reg+offset], reg instruction
  u_char test_byte = *(u_char*)instruction_address();
  switch (test_byte) {
    case instruction_code_reg2memb:  // 0x88 movb a, r
    case instruction_code_reg2mem:   // 0x89 movl a, r (can be movq in 64bit)
    case instruction_code_mem2regb:  // 0x8a movb r, a
    case instruction_code_mem2reg:   // 0x8b movl r, a (can be movq in 64bit)
      break;

    case instruction_code_mem2reg_movslq: // 0x63 movsql r, a
    case instruction_code_mem2reg_movzxb: // 0xb6 movzbl r, a (movzxb)
    case instruction_code_mem2reg_movzxw: // 0xb7 movzwl r, a (movzxw)
    case instruction_code_mem2reg_movsxb: // 0xbe movsbl r, a (movsxb)
    case instruction_code_mem2reg_movsxw: // 0xbf  movswl r, a (movsxw)
      break;

    case instruction_code_float_s:   // 0xd9 fld_s a
    case instruction_code_float_d:   // 0xdd fld_d a
    case instruction_code_xmm_load:  // 0x10 movsd xmm, a
    case instruction_code_xmm_store: // 0x11 movsd a, xmm
    case instruction_code_xmm_lpd:   // 0x12 movlpd xmm, a
      break;

    case instruction_code_lea:       // 0x8d lea r, a
      break;

    default:
          fatal ("not a mov [reg+offs], reg instruction");
  }
}


void NativeMovRegMem::print() {
  tty->print_cr(PTR_FORMAT ": mov reg, [reg + %x]", p2i(instruction_address()), offset());
}

//-------------------------------------------------------------------

void NativeLoadAddress::verify() {
  // make sure code pattern is actually a mov [reg+offset], reg instruction
  u_char test_byte = *(u_char*)instruction_address();
  if ((test_byte != lea_instruction_code) && (test_byte != mov64_instruction_code)) {
    fatal ("not a lea reg, [reg+offs] instruction");
  }
}


void NativeLoadAddress::print() {
  tty->print_cr(PTR_FORMAT ": lea [reg + %x], reg", p2i(instruction_address()), offset());
}

//--------------------------------------------------------------------------------

void NativeJump::verify() {
  if (*(u_char*)instruction_address() != instruction_code) {
    // far jump
    NativeMovConstReg* mov = nativeMovConstReg_at(instruction_address());
    NativeInstruction* jmp = nativeInstruction_at(mov->next_instruction_address());
    if (!jmp->is_jump_reg()) {
      fatal("not a jump instruction");
    }
  }
}


void NativeJump::insert(address code_pos, address entry) {
  intptr_t disp = (intptr_t)entry - ((intptr_t)code_pos + 1 + 4);
  guarantee(disp == (intptr_t)(int32_t)disp, "must be 32-bit offset");

  *code_pos = instruction_code;
  *((int32_t*)(code_pos + 1)) = (int32_t)disp;

  ICache::invalidate_range(code_pos, instruction_size);
}

void NativeIllegalInstruction::insert(address code_pos) {
  assert(NativeIllegalInstruction::instruction_size == sizeof(short), "right address unit for update");
  *(short *)code_pos = instruction_code;
  ICache::invalidate_range(code_pos, instruction_size);
}

void NativeGeneralJump::verify() {
  assert(((NativeInstruction *)this)->is_jump() ||
         ((NativeInstruction *)this)->is_cond_jump(), "not a general jump instruction");
}


void NativeGeneralJump::insert_unconditional(address code_pos, address entry) {
  intptr_t disp = (intptr_t)entry - ((intptr_t)code_pos + 1 + 4);
  guarantee(disp == (intptr_t)(int32_t)disp, "must be 32-bit offset");

  *code_pos = unconditional_long_jump;
  *((int32_t *)(code_pos+1)) = (int32_t) disp;
  ICache::invalidate_range(code_pos, instruction_size);
}


// MT-safe patching of a long jump instruction.
// First patches first word of instruction to two jmp's that jmps to themselves
// (spinlock). Then patches the last byte, and then atomically replaces
// the jmp's with the first 4 byte of the new instruction.
void NativeGeneralJump::replace_mt_safe(address instr_addr, address code_buffer) {
   assert (instr_addr != nullptr, "illegal address for code patching (4)");
   NativeGeneralJump* n_jump =  nativeGeneralJump_at (instr_addr); // checking that it is a jump

   // Temporary code
   unsigned char patch[4];
   assert(sizeof(patch)==sizeof(int32_t), "sanity check");
   patch[0] = 0xEB;       // jmp rel8
   patch[1] = 0xFE;       // jmp to self
   patch[2] = 0xEB;
   patch[3] = 0xFE;

   // First patch dummy jmp in place
   *(int32_t*)instr_addr = *(int32_t *)patch;
    n_jump->wrote(0);

   // Patch 4th byte
   instr_addr[4] = code_buffer[4];

    n_jump->wrote(4);

   // Patch bytes 0-3
   *(jint*)instr_addr = *(jint *)code_buffer;

    n_jump->wrote(0);

#ifdef ASSERT
   // verify patching
   for ( int i = 0; i < instruction_size; i++) {
     address ptr = (address)((intptr_t)code_buffer + i);
     int a_byte = (*ptr) & 0xFF;
     assert(*((address)((intptr_t)instr_addr + i)) == a_byte, "mt safe patching failed");
   }
#endif
}



address NativeGeneralJump::jump_destination() const {
  int op_code = ubyte_at(0);
  bool is_rel32off = (op_code == 0xE9 || op_code == 0x0F);
  int  offset  = (op_code == 0x0F)  ? 2 : 1;
  int  length  = offset + ((is_rel32off) ? 4 : 1);

  if (is_rel32off)
    return addr_at(0) + length + int_at(offset);
  else
    return addr_at(0) + length + sbyte_at(offset);
}

void NativePostCallNop::make_deopt() {
  /* makes the first 3 bytes into UD
   * With the 8 bytes possibly (likely) split over cachelines the protocol on x86 looks like:
   *
   * Original state: NOP (4 bytes) offset (4 bytes)
   * Writing the offset only touches the 4 last bytes (offset bytes)
   * Making a deopt only touches the first 4 bytes and turns the NOP into a UD
   * and to make disasembly look "reasonable" it turns the last byte into a
   * TEST eax, offset so that the offset bytes of the NOP now becomes the imm32.
   */

  unsigned char patch[4];
  NativeDeoptInstruction::insert((address) patch, false);
  patch[3] = 0xA9; // TEST eax, imm32 - this is just to keep disassembly looking correct and fills no real use.
  address instr_addr = addr_at(0);
  *(int32_t *)instr_addr = *(int32_t *)patch;
  ICache::invalidate_range(instr_addr, instruction_size);
}

bool NativePostCallNop::patch(int32_t oopmap_slot, int32_t cb_offset) {
  if (((oopmap_slot & 0xff) != oopmap_slot) || ((cb_offset & 0xffffff) != cb_offset)) {
    return false; // cannot encode
  }
  int32_t data = (oopmap_slot << 24) | cb_offset;
  assert(data != 0, "must be");
  int32_t *code_pos = (int32_t *) addr_at(displacement_offset);
  *((int32_t *)(code_pos)) = (int32_t) data;
  return true; // successfully encoded
}

void NativeDeoptInstruction::verify() {
}

// Inserts an undefined instruction at a given pc
void NativeDeoptInstruction::insert(address code_pos, bool invalidate) {
  *code_pos = instruction_prefix;
  *(code_pos+1) = instruction_code;
  *(code_pos+2) = 0x00;
  if (invalidate) {
    ICache::invalidate_range(code_pos, instruction_size);
  }
}
