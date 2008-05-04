/*
 * Copyright 1998-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_vmreg.cpp.incl"

// First VMReg value that could refer to a stack slot
VMReg VMRegImpl::stack0 = (VMReg)(intptr_t)((ConcreteRegisterImpl::number_of_registers + 1) & ~1);

// VMRegs are 4 bytes wide on all platforms
const int VMRegImpl::stack_slot_size = 4;
const int VMRegImpl::slots_per_word = wordSize / stack_slot_size;

const int VMRegImpl::register_count = ConcreteRegisterImpl::number_of_registers;
// Register names
const char *VMRegImpl::regName[ConcreteRegisterImpl::number_of_registers];

#ifndef PRODUCT
void VMRegImpl::print_on(outputStream* st) const {
  if( is_reg() ) {
    assert( VMRegImpl::regName[value()], "" );
    st->print("%s",VMRegImpl::regName[value()]);
  } else if (is_stack()) {
    int stk = value() - stack0->value();
    st->print("[%d]", stk*4);
  } else {
    st->print("BAD!");
  }
}
#endif // PRODUCT
