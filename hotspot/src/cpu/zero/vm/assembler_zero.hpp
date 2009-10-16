/*
 * Copyright 1997-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// In normal, CPU-specific ports of HotSpot these two classes are used
// for generating assembly language.  We don't do any of this in zero,
// of course, but we do sneak entry points around in CodeBuffers so we
// generate those here.

class Assembler : public AbstractAssembler {
 public:
  Assembler(CodeBuffer* code) : AbstractAssembler(code) {}

 public:
  void pd_patch_instruction(address branch, address target);
#ifndef PRODUCT
  static void pd_print_patched_instruction(address branch);
#endif // PRODUCT
};

class MacroAssembler : public Assembler {
 public:
  MacroAssembler(CodeBuffer* code) : Assembler(code) {}

 public:
  void align(int modulus);
  void bang_stack_with_offset(int offset);
  bool needs_explicit_null_check(intptr_t offset);
  RegisterOrConstant delayed_value_impl(intptr_t* delayed_value_addr,
                                        Register tmp, int offset);
 public:
  void advance(int bytes);
  void store_oop(jobject obj);
};

#ifdef ASSERT
inline bool AbstractAssembler::pd_check_instruction_mark() {
  ShouldNotCallThis();
}
#endif

address ShouldNotCallThisStub();
address ShouldNotCallThisEntry();
