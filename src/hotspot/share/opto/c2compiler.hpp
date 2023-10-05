/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_C2COMPILER_HPP
#define SHARE_OPTO_C2COMPILER_HPP

#include "compiler/abstractCompiler.hpp"
#include "opto/output.hpp"

class C2Compiler : public AbstractCompiler {
 private:
  static bool init_c2_runtime();

public:
  C2Compiler() : AbstractCompiler(compiler_c2) {}

  // Name
  const char *name() { return "C2"; }
  void initialize();

  // Compilation entry point for methods
  void compile_method(ciEnv* env,
                      ciMethod* target,
                      int entry_bci,
                      bool install_code,
                      DirectiveSet* directive);

  // sentinel value used to trigger backtracking in compile_method().
  static const char* retry_no_subsuming_loads();
  static const char* retry_no_escape_analysis();
  static const char* retry_no_iterative_escape_analysis();
  static const char* retry_no_reduce_allocation_merges();
  static const char* retry_no_locks_coarsening();
  static const char* retry_no_superword();

  // Print compilation timers and statistics
  void print_timers();

  // Return true if the intrinsification of a method supported by the compiler
  // assuming a non-virtual dispatch. (A virtual dispatch is
  // possible for only a limited set of available intrinsics whereas
  // a non-virtual dispatch is possible for all available intrinsics.)
  // Return false otherwise.
  virtual bool is_intrinsic_supported(const methodHandle& method);

  // Return true if the intrinsic `id` is supported by C2
  static bool is_intrinsic_supported(vmIntrinsics::ID id);
  // Initial size of the code buffer (may be increased at runtime)
  static int initial_code_buffer_size(int const_size = initial_const_capacity);
};

#endif // SHARE_OPTO_C2COMPILER_HPP
