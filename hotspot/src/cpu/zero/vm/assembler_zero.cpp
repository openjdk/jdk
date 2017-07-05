/*
 * Copyright (c) 2003, 2007, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2007, 2008, 2009 Red Hat, Inc.
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
#include "incls/_assembler_zero.cpp.incl"

int AbstractAssembler::code_fill_byte() {
  return 0;
}

void Assembler::pd_patch_instruction(address branch, address target) {
  ShouldNotCallThis();
}

#ifndef PRODUCT
void Assembler::pd_print_patched_instruction(address branch) {
  ShouldNotCallThis();
}
#endif // PRODUCT

void MacroAssembler::align(int modulus) {
  while (offset() % modulus != 0)
    emit_byte(AbstractAssembler::code_fill_byte());
}

void MacroAssembler::bang_stack_with_offset(int offset) {
  ShouldNotCallThis();
}

void MacroAssembler::advance(int bytes) {
  _code_pos += bytes;
  sync();
}

RegisterOrConstant MacroAssembler::delayed_value_impl(
  intptr_t* delayed_value_addr, Register tmpl, int offset) {
  ShouldNotCallThis();
}

void MacroAssembler::store_oop(jobject obj) {
  code_section()->relocate(pc(), oop_Relocation::spec_for_immediate());
  emit_address((address) obj);
}

static void should_not_call() {
  report_should_not_call(__FILE__, __LINE__);
}

address ShouldNotCallThisStub() {
  return (address) should_not_call;
}

address ShouldNotCallThisEntry() {
  return (address) should_not_call;
}
