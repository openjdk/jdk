/*
 * Copyright 2004-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_dump_sparc.cpp.incl"



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

  guarantee(*md_top <= md_end, "Insufficient space for vtables.");

  // Get ready to generate dummy methods.

  CodeBuffer cb((unsigned char*)*mc_top, mc_end - *mc_top);
  MacroAssembler* masm = new MacroAssembler(&cb);

  Label common_code;
  for (int i = 0; i < vtbl_list_size; ++i) {
    for (int j = 0; j < num_virtuals; ++j) {
      dummy_vtable[num_virtuals * i + j] = (void*)masm->pc();
      __ save(SP, -256, SP);
      __ brx(Assembler::always, false, Assembler::pt, common_code);

      // Load L0 with a value indicating vtable/offset pair.
      // -- bits[ 7..0]  (8 bits) which virtual method in table?
      // -- bits[12..8]  (5 bits) which virtual method table?
      // -- must fit in 13-bit instruction immediate field.
      __ delayed()->set((i << 8) + j, L0);
    }
  }

  __ bind(common_code);

  // Expecting to be called with the "this" pointer in O0/I0 (where
  // "this" is a Klass object).  In addition, L0 was set (above) to
  // identify the method and table.

  // Look up the correct vtable pointer.

  __ set((intptr_t)vtbl_list, L2);      // L2 = address of new vtable list.
  __ srl(L0, 8, L3);                    // Isolate L3 = vtable identifier.
  __ sll(L3, LogBytesPerWord, L3);
  __ ld_ptr(L2, L3, L3);                // L3 = new (correct) vtable pointer.
  __ st_ptr(L3, Address(I0, 0));        // Save correct vtable ptr in entry.

  // Restore registers and jump to the correct method;

  __ and3(L0, 255, L4);                 // Isolate L3 = method offset;.
  __ sll(L4, LogBytesPerWord, L4);
  __ ld_ptr(L3, L4, L4);                // Get address of correct virtual method
  __ jmpl(L4, 0, G0);                   // Jump to correct method.
  __ delayed()->restore();              // Restore registers.

  __ flush();
  *mc_top = (char*)__ pc();

  guarantee(*mc_top <= mc_end, "Insufficient space for method wrappers.");
}
