/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
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
#include "asm/macroAssembler.hpp"
#include "memory/metaspaceShared.hpp"

// Generate the self-patching vtable method:
//
// This method will be called (as any other Klass virtual method) with
// the Klass itself as the first argument.  Example:
//
//      oop obj;
//      int size = obj->klass()->oop_size(this);
//
// for which the virtual method call is Klass::oop_size();
//
// The dummy method is called with the Klass object as the first
// operand, and an object as the second argument.
//

//=====================================================================

// All of the dummy methods in the vtable are essentially identical,
// differing only by an ordinal constant, and they bear no relationship
// to the original method which the caller intended. Also, there needs
// to be 'vtbl_list_size' instances of the vtable in order to
// differentiate between the 'vtable_list_size' original Klass objects.

#define __ masm->

extern "C" {
  void aarch64_prolog(void);
}

void MetaspaceShared::generate_vtable_methods(void** vtbl_list,
                                                   void** vtable,
                                                   char** md_top,
                                                   char* md_end,
                                                   char** mc_top,
                                                   char* mc_end) {

#ifdef BUILTIN_SIM
  // Write a dummy word to the writable shared metaspace.
  // MetaspaceShared::initialize_shared_spaces will fill it with the
  // address of aarch64_prolog().
  address *prolog_ptr = (address*)*md_top;
  *(intptr_t *)(*md_top) = (intptr_t)0;
  (*md_top) += sizeof(intptr_t);
#endif

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

      // We're called directly from C code.
#ifdef BUILTIN_SIM
      __ c_stub_prolog(8, 0, MacroAssembler::ret_type_integral, prolog_ptr);
#endif
      // Load rscratch1 with a value indicating vtable/offset pair.
      // -- bits[ 7..0]  (8 bits) which virtual method in table?
      // -- bits[12..8]  (5 bits) which virtual method table?
      __ mov(rscratch1, (i << 8) + j);
      __ b(common_code);
    }
  }

  __ bind(common_code);

  Register tmp0 = r10, tmp1 = r11;       // AAPCS64 temporary registers
  __ enter();
  __ lsr(tmp0, rscratch1, 8);            // isolate vtable identifier.
  __ mov(tmp1, (address)vtbl_list);      // address of list of vtable pointers.
  __ ldr(tmp1, Address(tmp1, tmp0, Address::lsl(LogBytesPerWord))); // get correct vtable pointer.
  __ str(tmp1, Address(c_rarg0));        // update vtable pointer in obj.
  __ add(rscratch1, tmp1, rscratch1, ext::uxtb, LogBytesPerWord); // address of real method pointer.
  __ ldr(rscratch1, Address(rscratch1)); // get real method pointer.
  __ blrt(rscratch1, 8, 0, 1);           // jump to the real method.
  __ leave();
  __ ret(lr);

  *mc_top = (char*)__ pc();
}

#ifdef BUILTIN_SIM
void MetaspaceShared::relocate_vtbl_list(char **buffer) {
  void **sim_entry = (void**)*buffer;
  *sim_entry = (void*)aarch64_prolog;
  *buffer += sizeof(intptr_t);
}
#endif
