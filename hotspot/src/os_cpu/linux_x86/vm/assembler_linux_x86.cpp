/*
 * Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_assembler_linux_x86.cpp.incl"

#ifndef _LP64
void MacroAssembler::int3() {
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, os::breakpoint)));
}

void MacroAssembler::get_thread(Register thread) {
  movl(thread, rsp);
  shrl(thread, PAGE_SHIFT);

  ExternalAddress tls_base((address)ThreadLocalStorage::sp_map_addr());
  Address index(noreg, thread, Address::times_4);
  ArrayAddress tls(tls_base, index);

  movptr(thread, tls);
}
#else
void MacroAssembler::int3() {
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, os::breakpoint)));
}

void MacroAssembler::get_thread(Register thread) {
  // call pthread_getspecific
  // void * pthread_getspecific(pthread_key_t key);
   if (thread != rax) {
     push(rax);
   }
   push(rdi);
   push(rsi);
   push(rdx);
   push(rcx);
   push(r8);
   push(r9);
   push(r10);
   // XXX
   mov(r10, rsp);
   andq(rsp, -16);
   push(r10);
   push(r11);

   movl(rdi, ThreadLocalStorage::thread_index());
   call(RuntimeAddress(CAST_FROM_FN_PTR(address, pthread_getspecific)));

   pop(r11);
   pop(rsp);
   pop(r10);
   pop(r9);
   pop(r8);
   pop(rcx);
   pop(rdx);
   pop(rsi);
   pop(rdi);
   if (thread != rax) {
       mov(thread, rax);
       pop(rax);
   }
}
#endif
