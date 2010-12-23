/*
 * Copyright (c) 2004, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "assembler_x86.inline.hpp"
#include "memory/compactingPermGenGen.hpp"
#include "memory/generation.inline.hpp"
#include "memory/space.inline.hpp"



// Generate the self-patching vtable method:
//
// This method will be called (as any other Klass virtual method) with
// the Klass itself as the first argument.  Example:
//
//      oop obj;
//      int size = obj->klass()->klass_part()->oop_size(this);
//
// for which the virtual method call is Klass::oop_size();
//
// The dummy method is called with the Klass object as the first
// operand, and an object as the second argument.
//

//=====================================================================

// All of the dummy methods in the vtable are essentially identical,
// differing only by an ordinal constant, and they bear no releationship
// to the original method which the caller intended. Also, there needs
// to be 'vtbl_list_size' instances of the vtable in order to
// differentiate between the 'vtable_list_size' original Klass objects.

#define __ masm->

void CompactingPermGenGen::generate_vtable_methods(void** vtbl_list,
                                                   void** vtable,
                                                   char** md_top,
                                                   char* md_end,
                                                   char** mc_top,
                                                   char* mc_end) {

  intptr_t vtable_bytes = (num_virtuals * vtbl_list_size) * sizeof(void*);
  *(intptr_t *)(*md_top) = vtable_bytes;
  *md_top += sizeof(intptr_t);
  void** dummy_vtable = (void**)*md_top;
  *vtable = dummy_vtable;
  *md_top += vtable_bytes;

  // Get ready to generate dummy methods.

  CodeBuffer cb((unsigned char*)*mc_top, mc_end - *mc_top);
  MacroAssembler* masm = new MacroAssembler(&cb);

  Label common_code;
  for (int i = 0; i < vtbl_list_size; ++i) {
    for (int j = 0; j < num_virtuals; ++j) {
      dummy_vtable[num_virtuals * i + j] = (void*)masm->pc();

      // Load eax with a value indicating vtable/offset pair.
      // -- bits[ 7..0]  (8 bits) which virtual method in table?
      // -- bits[12..8]  (5 bits) which virtual method table?
      // -- must fit in 13-bit instruction immediate field.
      __ movl(rax, (i << 8) + j);
      __ jmp(common_code);
    }
  }

  __ bind(common_code);

  // Expecting to be called with "thiscall" convections -- the arguments
  // are on the stack and the "this" pointer is in c_rarg0. In addition, rax
  // was set (above) to the offset of the method in the table.

  __ push(c_rarg1);                     // save & free register
  __ push(c_rarg0);                     // save "this"
  __ mov(c_rarg0, rax);
  __ shrptr(c_rarg0, 8);                // isolate vtable identifier.
  __ shlptr(c_rarg0, LogBytesPerWord);
  __ lea(c_rarg1, ExternalAddress((address)vtbl_list)); // ptr to correct vtable list.
  __ addptr(c_rarg1, c_rarg0);          // ptr to list entry.
  __ movptr(c_rarg1, Address(c_rarg1, 0));      // get correct vtable address.
  __ pop(c_rarg0);                      // restore "this"
  __ movptr(Address(c_rarg0, 0), c_rarg1);      // update vtable pointer.

  __ andptr(rax, 0x00ff);                       // isolate vtable method index
  __ shlptr(rax, LogBytesPerWord);
  __ addptr(rax, c_rarg1);              // address of real method pointer.
  __ pop(c_rarg1);                      // restore register.
  __ movptr(rax, Address(rax, 0));      // get real method pointer.
  __ jmp(rax);                          // jump to the real method.

  __ flush();

  *mc_top = (char*)__ pc();
}
