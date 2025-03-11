/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_INTERPRETER_BYTECODE_INLINE_HPP
#define SHARE_INTERPRETER_BYTECODE_INLINE_HPP

#include "interpreter/bytecode.hpp"

#include "oops/cpCache.inline.hpp"
#include "prims/methodHandles.hpp"

inline bool Bytecode_invoke::has_appendix() {
  if (invoke_code() == Bytecodes::_invokedynamic) {
    return resolved_indy_entry()->has_appendix();
  } else {
    return resolved_method_entry()->has_appendix();
  }
}

inline bool Bytecode_invoke::has_member_arg() const {
  // NOTE: We could resolve the call and use the resolved adapter method here, but this function
  // is used by deoptimization, where resolving could lead to problems, so we avoid that here
  // by doing things symbolically.
  //
  // invokedynamic instructions don't have a class but obviously don't have a MemberName appendix.
  return !is_invokedynamic() && MethodHandles::has_member_arg(klass(), name());
}

#endif // SHARE_INTERPRETER_BYTECODE_INLINE_HPP
