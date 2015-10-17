/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_AARCH64_VM_INTERPRETERGENERATOR_AARCH64_HPP
#define CPU_AARCH64_VM_INTERPRETERGENERATOR_AARCH64_HPP


// Generation of Interpreter
//
  friend class AbstractInterpreterGenerator;

protected:

  void bang_stack_shadow_pages(bool native_call);

private:

  address generate_normal_entry(bool synchronized);
  address generate_native_entry(bool synchronized);
  address generate_abstract_entry(void);
  address generate_math_entry(AbstractInterpreter::MethodKind kind);
  address generate_accessor_entry(void) { return NULL; }
  address generate_empty_entry(void) { return NULL; }
  void generate_transcendental_entry(AbstractInterpreter::MethodKind kind, int fpargs);
  address generate_Reference_get_entry();
  address generate_CRC32_update_entry();
  address generate_CRC32_updateBytes_entry(AbstractInterpreter::MethodKind kind);
  address generate_CRC32C_updateBytes_entry(AbstractInterpreter::MethodKind kind) { return NULL; }
  void generate_stack_overflow_check(void);

  void generate_counter_incr(Label* overflow, Label* profile_method, Label* profile_method_continue);
  void generate_counter_overflow(Label* do_continue);

#endif // CPU_AARCH64_VM_INTERPRETERGENERATOR_AARCH64_HPP
