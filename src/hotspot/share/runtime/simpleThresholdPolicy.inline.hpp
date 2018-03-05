/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_SIMPLETHRESHOLDPOLICY_INLINE_HPP
#define SHARE_VM_RUNTIME_SIMPLETHRESHOLDPOLICY_INLINE_HPP

#include "compiler/compilerOracle.hpp"
#include "oops/method.inline.hpp"

#ifdef TIERED

template<CompLevel level>
bool SimpleThresholdPolicy::call_predicate_helper(int i, int b, double scale, Method* method) {
  double threshold_scaling;
  if (CompilerOracle::has_option_value(method, "CompileThresholdScaling", threshold_scaling)) {
    scale *= threshold_scaling;
  }
  switch(level) {
  case CompLevel_aot:
    return (i >= Tier3AOTInvocationThreshold * scale) ||
           (i >= Tier3AOTMinInvocationThreshold * scale && i + b >= Tier3AOTCompileThreshold * scale);
  case CompLevel_none:
  case CompLevel_limited_profile:
    return (i >= Tier3InvocationThreshold * scale) ||
           (i >= Tier3MinInvocationThreshold * scale && i + b >= Tier3CompileThreshold * scale);
  case CompLevel_full_profile:
   return (i >= Tier4InvocationThreshold * scale) ||
          (i >= Tier4MinInvocationThreshold * scale && i + b >= Tier4CompileThreshold * scale);
  }
  return true;
}

template<CompLevel level>
bool SimpleThresholdPolicy::loop_predicate_helper(int i, int b, double scale, Method* method) {
  double threshold_scaling;
  if (CompilerOracle::has_option_value(method, "CompileThresholdScaling", threshold_scaling)) {
    scale *= threshold_scaling;
  }
  switch(level) {
  case CompLevel_aot:
    return b >= Tier3AOTBackEdgeThreshold * scale;
  case CompLevel_none:
  case CompLevel_limited_profile:
    return b >= Tier3BackEdgeThreshold * scale;
  case CompLevel_full_profile:
    return b >= Tier4BackEdgeThreshold * scale;
  }
  return true;
}

// Simple methods are as good being compiled with C1 as C2.
// Determine if a given method is such a case.
bool SimpleThresholdPolicy::is_trivial(Method* method) {
  if (method->is_accessor() ||
      method->is_constant_getter()) {
    return true;
  }
#if INCLUDE_JVMCI
  if (UseJVMCICompiler) {
    AbstractCompiler* comp = CompileBroker::compiler(CompLevel_full_optimization);
    if (TieredCompilation && comp != NULL && comp->is_trivial(method)) {
      return true;
    }
  }
#endif
  if (method->has_loops() || method->code_size() >= 15) {
    return false;
  }
  MethodData* mdo = method->method_data();
  if (mdo != NULL && !mdo->would_profile() &&
      (method->code_size() < 5  || (mdo->num_blocks() < 4))) {
    return true;
  }
  return false;
}

inline CompLevel SimpleThresholdPolicy::comp_level(Method* method) {
  CompiledMethod *nm = method->code();
  if (nm != NULL && nm->is_in_use()) {
    return (CompLevel)nm->comp_level();
  }
  return CompLevel_none;
}

#endif // TIERED

#endif // SHARE_VM_RUNTIME_SIMPLETHRESHOLDPOLICY_INLINE_HPP
