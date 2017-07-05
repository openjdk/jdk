/*
 * Copyright (c) 2001, 2007, Oracle and/or its affiliates. All rights reserved.
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

// These are the CPU-specific fields, types and integer
// constants required by the Serviceability Agent. This file is
// referenced by vmStructs.cpp.

#define VM_STRUCTS_CPU(nonstatic_field, static_field, unchecked_nonstatic_field, volatile_nonstatic_field, nonproduct_nonstatic_field, c2_nonstatic_field, unchecked_c1_static_field, unchecked_c2_static_field, last_entry)            \
                                                                                                                                     \
  /******************************/                                                                                                   \
  /* JavaCallWrapper            */                                                                                                   \
  /******************************/                                                                                                   \
  /******************************/                                                                                                   \
  /* JavaFrameAnchor            */                                                                                                   \
  /******************************/                                                                                                   \
  volatile_nonstatic_field(JavaFrameAnchor,     _last_Java_fp,                                    intptr_t*)                              \
                                                                                                                                     \

  /* NOTE that we do not use the last_entry() macro here; it is used  */
  /* in vmStructs_<os>_<cpu>.hpp's VM_STRUCTS_OS_CPU macro (and must  */
  /* be present there)                                                */


#define VM_TYPES_CPU(declare_type, declare_toplevel_type, declare_oop_type, declare_integer_type, declare_unsigned_integer_type, declare_c1_toplevel_type, declare_c2_type, declare_c2_toplevel_type, last_entry)                               \

  /* NOTE that we do not use the last_entry() macro here; it is used  */
  /* in vmStructs_<os>_<cpu>.hpp's VM_TYPES_OS_CPU macro (and must    */
  /* be present there)                                                */


#define VM_INT_CONSTANTS_CPU(declare_constant, declare_preprocessor_constant, declare_c1_constant, declare_c2_constant, declare_c2_preprocessor_constant, last_entry)                                                              \

  /* NOTE that we do not use the last_entry() macro here; it is used        */
  /* in vmStructs_<os>_<cpu>.hpp's VM_INT_CONSTANTS_OS_CPU macro (and must  */
  /* be present there)                                                      */

#define VM_LONG_CONSTANTS_CPU(declare_constant, declare_preprocessor_constant, declare_c1_constant, declare_c2_constant, declare_c2_preprocessor_constant, last_entry)                                                              \

  /* NOTE that we do not use the last_entry() macro here; it is used         */
  /* in vmStructs_<os>_<cpu>.hpp's VM_LONG_CONSTANTS_OS_CPU macro (and must  */
  /* be present there)                                                       */
