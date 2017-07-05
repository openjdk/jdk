/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_VM_JVMCI_VMSTRUCTS_COMPILER_RUNTIME_HPP
#define SHARE_VM_JVMCI_VMSTRUCTS_COMPILER_RUNTIME_HPP

#if INCLUDE_AOT
#include "jvmci/compilerRuntime.hpp"

#define VM_ADDRESSES_COMPILER_RUNTIME(declare_address, declare_preprocessor_address, declare_function) \
  declare_function(CompilerRuntime::resolve_string_by_symbol)                     \
  declare_function(CompilerRuntime::resolve_klass_by_symbol)                      \
  declare_function(CompilerRuntime::resolve_method_by_symbol_and_load_counters)   \
  declare_function(CompilerRuntime::initialize_klass_by_symbol)                   \
  declare_function(CompilerRuntime::invocation_event)                             \
  declare_function(CompilerRuntime::backedge_event)

#else // INCLUDE_AOT

#define VM_ADDRESSES_COMPILER_RUNTIME(declare_address, declare_preprocessor_address, declare_function)

#endif // INCLUDE_AOT

#endif // SHARE_VM_AOT_VMSTRUCTS_COMPILER_RUNTIME_HPP
