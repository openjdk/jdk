/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2013, 2014 SAP AG. All rights reserved.
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

#ifndef CPU_PPC_VM_TEMPLATEINTERPRETERGENERATOR_PPC_HPP
#define CPU_PPC_VM_TEMPLATEINTERPRETERGENERATOR_PPC_HPP

 protected:
  address generate_normal_entry(bool synchronized);
  address generate_native_entry(bool synchronized);
  address generate_math_entry(AbstractInterpreter::MethodKind kind);
  address generate_empty_entry(void);

  void lock_method(Register Rflags, Register Rscratch1, Register Rscratch2, bool flags_preloaded=false);
  void unlock_method(bool check_exceptions = true);

  void generate_counter_incr(Label* overflow, Label* profile_method, Label* profile_method_continue);
  void generate_counter_overflow(Label& continue_entry);

  void generate_fixed_frame(bool native_call, Register Rsize_of_parameters, Register Rsize_of_locals);
  void generate_stack_overflow_check(Register Rframe_size, Register Rscratch1);

#endif // CPU_PPC_VM_TEMPLATEINTERPRETERGENERATOR_PPC_HPP
