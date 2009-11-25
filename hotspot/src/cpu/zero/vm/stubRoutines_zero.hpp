/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

  // This file holds the platform specific parts of the StubRoutines
  // definition. See stubRoutines.hpp for a description on how to
  // extend it.

 public:
  static address call_stub_return_pc() {
    return (address) -1;
  }

  static bool returns_to_call_stub(address return_pc) {
    return return_pc == call_stub_return_pc();
  }

  enum platform_dependent_constants {
    code_size1 = 0,      // The assembler will fail with a guarantee
    code_size2 = 0       // if these are too small.  Simply increase
  };                     // them if that happens.

#ifdef IA32
  class x86 {
    friend class VMStructs;

   private:
    static address _call_stub_compiled_return;
  };
#endif // IA32
