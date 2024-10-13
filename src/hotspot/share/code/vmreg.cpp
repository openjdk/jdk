/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "code/vmreg.hpp"

// First VMReg value that could refer to a stack slot.  This is only
// used by SA and jvmti, but it's a leaky abstraction: SA and jvmti
// "know" that stack0 is an integer masquerading as a pointer. For the
// sake of those clients, we preserve this interface.
VMReg VMRegImpl::stack0 = (VMReg)(intptr_t)FIRST_STACK;

// VMRegs are 4 bytes wide on all platforms
const int VMRegImpl::stack_slot_size = 4;
const int VMRegImpl::slots_per_word = wordSize / stack_slot_size;

const int VMRegImpl::register_count = ConcreteRegisterImpl::number_of_registers;
// Register names
const char *VMRegImpl::regName[ConcreteRegisterImpl::number_of_registers];

void VMRegImpl::print_on(outputStream* st) const {
  if (is_reg()) {
    assert(VMRegImpl::regName[value()], "VMRegImpl::regName[%d] returns nullptr", value());
    st->print("%s",VMRegImpl::regName[value()]);
  } else if (is_stack()) {
    int stk = reg2stack();
    st->print("[%d]", stk*4);
  } else {
    st->print("BAD!");
  }
}

VMRegImpl all_VMRegs[ConcreteRegisterImpl::number_of_registers + 1];

void VMRegImpl::print() const { print_on(tty); }
