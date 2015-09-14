/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_VM_STUBROUTINES_X86_32_HPP
#define CPU_X86_VM_STUBROUTINES_X86_32_HPP

// This file holds the platform specific parts of the StubRoutines
// definition. See stubRoutines.hpp for a description on how to
// extend it.

enum platform_dependent_constants {
  code_size1 =  9000,           // simply increase if too small (assembler will crash if too small)
  code_size2 = 30000            // simply increase if too small (assembler will crash if too small)
};

class x86 {
 friend class StubGenerator;
 friend class VMStructs;

 private:
  static address _verify_fpu_cntrl_wrd_entry;

 public:
  static address verify_fpu_cntrl_wrd_entry()                { return _verify_fpu_cntrl_wrd_entry; }

# include "stubRoutines_x86.hpp"

};

  static bool    returns_to_call_stub(address return_pc)     { return return_pc == _call_stub_return_address; }

#endif // CPU_X86_VM_STUBROUTINES_X86_32_HPP
