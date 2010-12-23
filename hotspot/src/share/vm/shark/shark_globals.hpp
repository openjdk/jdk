/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_SHARK_SHARK_GLOBALS_HPP
#define SHARE_VM_SHARK_SHARK_GLOBALS_HPP

#include "runtime/globals.hpp"
#ifdef TARGET_ARCH_zero
# include "shark_globals_zero.hpp"
#endif

#define SHARK_FLAGS(develop, develop_pd, product, product_pd, diagnostic, notproduct) \
                                                                              \
  product(intx, MaxNodeLimit, 65000,                                          \
          "Maximum number of nodes")                                          \
                                                                              \
  /* inlining */                                                              \
  product(intx, SharkMaxInlineSize, 32,                                       \
          "Maximum bytecode size of methods to inline when using Shark")      \
                                                                              \
  /* compiler debugging */                                                    \
  develop(ccstr, SharkPrintTypeflowOf, NULL,                                  \
          "Print the typeflow of the specified method")                       \
                                                                              \
  diagnostic(ccstr, SharkPrintBitcodeOf, NULL,                                \
          "Print the LLVM bitcode of the specified method")                   \
                                                                              \
  diagnostic(ccstr, SharkPrintAsmOf, NULL,                                    \
          "Print the asm of the specified method")                            \
                                                                              \
  develop(bool, SharkTraceBytecodes, false,                                   \
          "Trace bytecode compilation")                                       \
                                                                              \
  diagnostic(bool, SharkTraceInstalls, false,                                 \
          "Trace method installation")                                        \
                                                                              \
  diagnostic(bool, SharkPerformanceWarnings, false,                           \
          "Warn about things that could be made faster")                      \

SHARK_FLAGS(DECLARE_DEVELOPER_FLAG, DECLARE_PD_DEVELOPER_FLAG, DECLARE_PRODUCT_FLAG, DECLARE_PD_PRODUCT_FLAG, DECLARE_DIAGNOSTIC_FLAG, DECLARE_NOTPRODUCT_FLAG)

#endif // SHARE_VM_SHARK_SHARK_GLOBALS_HPP
