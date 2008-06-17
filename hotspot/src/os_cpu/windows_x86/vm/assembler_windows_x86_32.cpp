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
#include "incls/_assembler_windows_x86_32.cpp.incl"


void MacroAssembler::int3() {
  emit_byte(0xCC);
}

//  The current scheme to accelerate access to the thread
//  pointer is to store the current thread in the os_exception_wrapper
//  and reference the current thread from stubs and compiled code
//  via the FS register.  FS[0] contains a pointer to the structured
//  exception block which is actually a stack address.  The first time
//  we call the os exception wrapper, we calculate and store the
//  offset from this exception block and use that offset here.
//
//  The last mechanism we used was problematic in that the
//  the offset we had hard coded in the VM kept changing as Microsoft
//  evolved the OS.
//
// Warning: This mechanism assumes that we only attempt to get the
//          thread when we are nested below a call wrapper.
//
// movl reg, fs:[0]                        Get exeception pointer
// movl reg, [reg + thread_ptr_offset]     Load thread
//
void MacroAssembler::get_thread(Register thread) {
  // can't use ExternalAddress because it can't take NULL
  AddressLiteral null(0, relocInfo::none);

  prefix(FS_segment);
  movptr(thread, null);
  assert(ThreadLocalStorage::get_thread_ptr_offset() != 0,
         "Thread Pointer Offset has not been initialized");
  movl(thread, Address(thread, ThreadLocalStorage::get_thread_ptr_offset()));
}

bool MacroAssembler::needs_explicit_null_check(intptr_t offset) {
  return offset < 0 || (int)os::vm_page_size() <= offset;
}
