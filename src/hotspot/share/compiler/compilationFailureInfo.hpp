/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2024, Red Hat, Inc. and/or its affiliates.
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

#ifndef SHARE_COMPILER_COMPILATIONFAILUREINFO_HPP
#define SHARE_COMPILER_COMPILATIONFAILUREINFO_HPP

#if defined(COMPILER1) || defined(COMPILER2)

#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/nativeCallStack.hpp"

class outputStream;
class Symbol;

class CompilationFailureInfo : public CHeapObj<mtCompiler> {
  NativeCallStack _stack;
  char* const _failure_reason;
  const double _elapsed_seconds;
  const int _compile_id;
  static int current_compile_id_or_0();
public:
  CompilationFailureInfo(const char* failure_reason);
  ~CompilationFailureInfo();
  void print_on(outputStream* st) const;

  // Convenience function to print, safely, current compile failure iff
  // current thread is compiler thread and there is an ongoing compilation
  // and a pending failure.
  // Otherwise prints nothing.
  static bool print_pending_compilation_failure(outputStream* st);
};

#endif // defined(COMPILER1) || defined(COMPILER2)

#endif // SHARE_COMPILER_COMPILATIONFAILUREINFO_HPP
