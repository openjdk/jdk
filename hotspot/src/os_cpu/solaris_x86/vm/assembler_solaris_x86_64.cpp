/*
 * Copyright 2004-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_assembler_solaris_x86_64.cpp.incl"

void MacroAssembler::int3() {
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, os::breakpoint)));
}

void MacroAssembler::get_thread(Register thread) {
  // Try to emit a Solaris-specific fast TSD/TLS accessor.
  ThreadLocalStorage::pd_tlsAccessMode tlsMode = ThreadLocalStorage::pd_getTlsAccessMode();
  if (tlsMode == ThreadLocalStorage::pd_tlsAccessIndirect) {            // T1
    // Use thread as a temporary: mov r, fs:[0]; mov r, [r+tlsOffset]
    emit_byte(Assembler::FS_segment);
    movq(thread, Address(NULL, relocInfo::none));
    movq(thread, Address(thread, ThreadLocalStorage::pd_getTlsOffset()));
    return;
  } else if (tlsMode == ThreadLocalStorage::pd_tlsAccessDirect) {       // T2
    // mov r, fs:[tlsOffset]
    emit_byte(Assembler::FS_segment);
    ExternalAddress tls_off((address) ThreadLocalStorage::pd_getTlsOffset());
    movptr(thread, tls_off);
    return;
  }

  // slow call to of thr_getspecific
  // int thr_getspecific(thread_key_t key, void **value);
  // Consider using pthread_getspecific instead.

  if (thread != rax) {
    pushq(rax);
  }
  pushq(0); // space for return value
  pushq(rdi);
  pushq(rsi);
  leaq(rsi, Address(rsp, 16)); // pass return value address
  pushq(rdx);
  pushq(rcx);
  pushq(r8);
  pushq(r9);
  pushq(r10);
  // XXX
  movq(r10, rsp);
  andq(rsp, -16);
  pushq(r10);
  pushq(r11);

  movl(rdi, ThreadLocalStorage::thread_index());
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, thr_getspecific)));

  popq(r11);
  popq(rsp);
  popq(r10);
  popq(r9);
  popq(r8);
  popq(rcx);
  popq(rdx);
  popq(rsi);
  popq(rdi);
  popq(thread); // load return value
  if (thread != rax) {
    popq(rax);
  }
}

bool MacroAssembler::needs_explicit_null_check(intptr_t offset) {
  // Identical to Sparc/Solaris code

  // Exception handler checks the nmethod's implicit null checks table
  // only when this method returns false.
  if (UseCompressedOops) {
    // The first page after heap_base is unmapped and
    // the 'offset' is equal to [heap_base + offset] for
    // narrow oop implicit null checks.
    uintptr_t heap_base = (uintptr_t)Universe::heap_base();
    if ((uintptr_t)offset >= heap_base) {
      // Normalize offset for the next check.
      offset = (intptr_t)(pointer_delta((void*)offset, (void*)heap_base, 1));
    }
  }
  bool offset_in_first_page = 0 <= offset && offset < os::vm_page_size();
  return !offset_in_first_page;
}
