/*
 * Copyright (c) 1999, 2011, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2008, 2009, 2010 Red Hat, Inc.
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

#ifndef SHARE_VM_SHARK_LLVMHEADERS_HPP
#define SHARE_VM_SHARK_LLVMHEADERS_HPP

#ifdef assert
  #undef assert
#endif

#ifdef DEBUG
  #define SHARK_DEBUG
  #undef DEBUG
#endif

#include <llvm/Analysis/Verifier.h>
#include <llvm/Argument.h>
#include <llvm/Constants.h>
#include <llvm/DerivedTypes.h>
#include <llvm/ExecutionEngine/ExecutionEngine.h>
#include <llvm/Instructions.h>
#include <llvm/LLVMContext.h>
#include <llvm/Module.h>
#if SHARK_LLVM_VERSION <= 31
#include <llvm/Support/IRBuilder.h>
#else
#include <llvm/IRBuilder.h>
#endif
#include <llvm/Support/Threading.h>
#include <llvm/Support/TargetSelect.h>
#include <llvm/Type.h>
#include <llvm/ExecutionEngine/JITMemoryManager.h>
#include <llvm/Support/CommandLine.h>
#include <llvm/ExecutionEngine/MCJIT.h>
#include <llvm/ExecutionEngine/JIT.h>
#include <llvm/ADT/StringMap.h>
#include <llvm/Support/Debug.h>
#include <llvm/Support/Host.h>

#include <map>

#ifdef assert
  #undef assert
#endif

// from hotspot/src/share/vm/utilities/debug.hpp
#ifdef ASSERT
#ifndef USE_REPEATED_ASSERTS
#define assert(p, msg)                                                       \
do {                                                                         \
  if (!(p)) {                                                                \
    report_vm_error(__FILE__, __LINE__, "assert(" #p ") failed", msg);       \
    BREAKPOINT;                                                              \
  }                                                                          \
} while (0)
#else // #ifndef USE_REPEATED_ASSERTS
#define assert(p, msg)
do {                                                                         \
  for (int __i = 0; __i < AssertRepeat; __i++) {                             \
    if (!(p)) {                                                              \
      report_vm_error(__FILE__, __LINE__, "assert(" #p ") failed", msg);     \
      BREAKPOINT;                                                            \
    }                                                                        \
  }                                                                          \
} while (0)
#endif // #ifndef USE_REPEATED_ASSERTS
#else
  #define assert(p, msg)
#endif

#ifdef DEBUG
  #undef DEBUG
#endif
#ifdef SHARK_DEBUG
  #define DEBUG
  #undef SHARK_DEBUG
#endif

#endif // SHARE_VM_SHARK_LLVMHEADERS_HPP
