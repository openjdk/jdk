/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/assembler.hpp"
#include "assembler_x86.inline.hpp"
#include "code/icBuffer.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "interpreter/bytecodes.hpp"
#include "memory/resourceArea.hpp"
#include "nativeInst_x86.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oop.inline2.hpp"

int InlineCacheBuffer::ic_stub_code_size() {
  return NativeMovConstReg::instruction_size +
         NativeJump::instruction_size +
         1;
  // so that code_end can be set in CodeBuffer
  // 64bit 16 = 5 + 10 bytes + 1 byte
  // 32bit 11 = 10 bytes + 1 byte
}



void InlineCacheBuffer::assemble_ic_buffer_code(address code_begin, oop cached_oop, address entry_point) {
  ResourceMark rm;
  CodeBuffer      code(code_begin, ic_stub_code_size());
  MacroAssembler* masm            = new MacroAssembler(&code);
  // note: even though the code contains an embedded oop, we do not need reloc info
  // because
  // (1) the oop is old (i.e., doesn't matter for scavenges)
  // (2) these ICStubs are removed *before* a GC happens, so the roots disappear
  assert(cached_oop == NULL || cached_oop->is_perm(), "must be perm oop");
  masm->lea(rax, OopAddress((address) cached_oop));
  masm->jump(ExternalAddress(entry_point));
}


address InlineCacheBuffer::ic_buffer_entry_point(address code_begin) {
  NativeMovConstReg* move = nativeMovConstReg_at(code_begin);   // creation also verifies the object
  NativeJump*        jump = nativeJump_at(move->next_instruction_address());
  return jump->jump_destination();
}


oop InlineCacheBuffer::ic_buffer_cached_oop(address code_begin) {
  // creation also verifies the object
  NativeMovConstReg* move = nativeMovConstReg_at(code_begin);
  // Verifies the jump
  NativeJump*        jump = nativeJump_at(move->next_instruction_address());
  return (oop)move->data();
}
