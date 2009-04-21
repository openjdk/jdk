/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

 friend class AbstractInterpreterGenerator;

 private:

  address generate_normal_entry(bool synchronized);
  address generate_native_entry(bool synchronized);
  address generate_abstract_entry(void);
  address generate_method_handle_entry(void);
  address generate_math_entry(AbstractInterpreter::MethodKind kind);
  address generate_empty_entry(void);
  address generate_accessor_entry(void);
  void lock_method(void);
  void save_native_result(void);
  void restore_native_result(void);

  void generate_counter_incr(Label* overflow, Label* profile_method, Label* profile_method_continue);
  void generate_counter_overflow(Label& Lcontinue);
