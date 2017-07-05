/*
 * Copyright 1999-2003 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_assembler_linux_x86_32.cpp.incl"

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

bool MacroAssembler::needs_explicit_null_check(intptr_t offset) {
  // Linux kernel guarantees that the first page is always unmapped. Don't
  // assume anything more than that.
  bool offset_in_first_page =   0 <= offset  &&  offset < os::vm_page_size();
  return !offset_in_first_page;
}
