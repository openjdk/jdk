/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include <llvm/ExecutionEngine/ExecutionEngine.h>

// includes specific to each version
#if SHARK_LLVM_VERSION <= 31
#include <llvm/Support/IRBuilder.h>
#include <llvm/Type.h>
#include <llvm/Argument.h>
#include <llvm/Constants.h>
#include <llvm/DerivedTypes.h>
#include <llvm/Instructions.h>
#include <llvm/LLVMContext.h>
#include <llvm/Module.h>
#elif SHARK_LLVM_VERSION <= 32
#include <llvm/IRBuilder.h>
#include <llvm/Type.h>
#include <llvm/Argument.h>
#include <llvm/Constants.h>
#include <llvm/DerivedTypes.h>
#include <llvm/Instructions.h>
#include <llvm/LLVMContext.h>
#include <llvm/Module.h>
#else // SHARK_LLVM_VERSION <= 34
#include <llvm/IR/IRBuilder.h>
#include <llvm/IR/Argument.h>
#include <llvm/IR/Constants.h>
#include <llvm/IR/DerivedTypes.h>
#include <llvm/ExecutionEngine/ExecutionEngine.h>
#include <llvm/IR/Instructions.h>
#include <llvm/IR/LLVMContext.h>
#include <llvm/IR/Module.h>
#include <llvm/ADT/StringRef.h>
#include <llvm/IR/Type.h>
#endif

// common includes
#include <llvm/Support/Threading.h>
#include <llvm/Support/TargetSelect.h>
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

#define assert(p, msg) vmassert(p, msg)

#ifdef DEBUG
  #undef DEBUG
#endif
#ifdef SHARK_DEBUG
  #define DEBUG
  #undef SHARK_DEBUG
#endif

#endif // SHARE_VM_SHARK_LLVMHEADERS_HPP
