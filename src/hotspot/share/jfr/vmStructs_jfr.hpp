/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_VMSTRUCTS_JFR_HPP
#define SHARE_GC_SHARED_VMSTRUCTS_JFR_HPP

#if INCLUDE_JFR

#include "jfr/recorder/context/jfrContext.hpp"
#include "jfr/recorder/context/jfrContextBinding.hpp"
#include "jfr/recorder/context/jfrContextFilter.hpp"

#define VM_STRUCTS_JFR(nonstatic_field) \
  nonstatic_field(JfrContextBinding,         _entries_len,                                  int)                                   \
  nonstatic_field(JfrContextBinding,         _entries,                                      JfrContextEntry*)                      \
  nonstatic_field(JfrContextEntry,           _name,                                         char*)                                 \
  nonstatic_field(JfrContextEntry,           _value,                                        char*)

#define VM_TYPES_JFR(declare_type,                                        \
                     declare_toplevel_type,                               \
                     declare_integer_type)                                \
                                                                          \
  declare_toplevel_type(JfrContextBinding)                                \
  declare_toplevel_type(JfrContextEntry)

#else // !INCLUDE_JFR

#define VM_STRUCTS_JFR(nonstatic_field)

#define VM_TYPES_JFR(declare_type,                                        \
                     declare_toplevel_type,                                \
                     declare_integer_type)

#endif // INCLUDE_JFR

#endif // SHARE_GC_SHARED_VMSTRUCTS_JFR_HPP
