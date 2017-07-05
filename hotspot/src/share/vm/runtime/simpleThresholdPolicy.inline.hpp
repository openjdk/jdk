/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

template<CompLevel level>
bool SimpleThresholdPolicy::call_predicate_helper(int i, int b, double scale) {
  switch(level) {
  case CompLevel_none:
  case CompLevel_limited_profile:
    return (i > Tier3InvocationThreshold * scale) ||
           (i > Tier3MinInvocationThreshold * scale && i + b > Tier3CompileThreshold * scale);
  case CompLevel_full_profile:
   return (i > Tier4InvocationThreshold * scale) ||
          (i > Tier4MinInvocationThreshold * scale && i + b > Tier4CompileThreshold * scale);
  }
  return true;
}

template<CompLevel level>
bool SimpleThresholdPolicy::loop_predicate_helper(int i, int b, double scale) {
  switch(level) {
  case CompLevel_none:
  case CompLevel_limited_profile:
    return b > Tier3BackEdgeThreshold * scale;
  case CompLevel_full_profile:
    return b > Tier4BackEdgeThreshold * scale;
  }
  return true;
}

// Simple methods are as good being compiled with C1 as C2.
// Determine if a given method is such a case.
bool SimpleThresholdPolicy::is_trivial(Method* method) {
  if (method->is_accessor()) return true;
  if (method->code() != NULL) {
    MethodData* mdo = method->method_data();
    if (mdo != NULL && mdo->num_loops() == 0 &&
        (method->code_size() < 5  || (mdo->num_blocks() < 4) && (method->code_size() < 15))) {
      return !mdo->would_profile();
    }
  }
  return false;
}

#endif // SHARE_VM_RUNTIME_SIMPLETHRESHOLDPOLICY_INLINE_HPP
