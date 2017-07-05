/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CODE_RELOCINFO_EXT_HPP
#define SHARE_VM_CODE_RELOCINFO_EXT_HPP

// symbolic_Relocation allows to anotate some addresses in the generated code.
//
// This class was initially defined using the last unused relocType. The
// new version tries to limit the impact on open source code changes.
//
// Without compiled code support, symbolic_Relocation need not be a real
// relocation. To avoid using the last unused relocType, the
// symbolic_Relocation::spec(<any symbolic type>) has been replaced
// by additional methods using directly the symbolic type.
//
// Note: the order of the arguments in some methods had to reversed
// to avoid confusion between the relocType enum and the
// symbolic_reference enum.
class symbolic_Relocation : AllStatic {

 public:
  enum symbolic_reference {
    card_table_reference,
    eden_top_reference,
    heap_end_reference,
    polling_page_reference,
    mark_bits_reference,
    mark_mask_reference,
    oop_bits_reference,
    oop_mask_reference,
    debug_string_reference,
    last_symbolic_reference
  };

  // get the new value for a given symbolic type
  static address symbolic_value(symbolic_reference t);
};

#endif // SHARE_VM_CODE_RELOCINFO_EXT_HPP
