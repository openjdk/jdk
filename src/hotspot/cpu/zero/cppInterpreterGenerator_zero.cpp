/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2007, 2008, 2009, 2010, 2011 Red Hat, Inc.
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

#include "precompiled.hpp"
#include "asm/assembler.hpp"
#include "interpreter/bytecodeHistogram.hpp"
#include "interpreter/cppInterpreterGenerator.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "oops/method.hpp"
#include "runtime/arguments.hpp"
#include "interpreter/cppInterpreter.hpp"

address CppInterpreterGenerator::generate_slow_signature_handler() {
  _masm->advance(1);
  return (address) InterpreterRuntime::slow_signature_handler;
}

address CppInterpreterGenerator::generate_math_entry(
    AbstractInterpreter::MethodKind kind) {
  if (!InlineIntrinsics)
    return NULL;

  Unimplemented();
  return NULL;
}

address CppInterpreterGenerator::generate_abstract_entry() {
  return generate_entry((address) ShouldNotCallThisEntry());
}

address CppInterpreterGenerator::generate_empty_entry() {
  if (!UseFastEmptyMethods)
    return NULL;

  return generate_entry((address) CppInterpreter::empty_entry);
}

address CppInterpreterGenerator::generate_accessor_entry() {
  if (!UseFastAccessorMethods)
    return NULL;

  return generate_entry((address) CppInterpreter::accessor_entry);
}

address CppInterpreterGenerator::generate_Reference_get_entry(void) {
#if INCLUDE_G1GC
  if (UseG1GC) {
    // We need to generate have a routine that generates code to:
    //   * load the value in the referent field
    //   * passes that value to the pre-barrier.
    //
    // In the case of G1 this will record the value of the
    // referent in an SATB buffer if marking is active.
    // This will cause concurrent marking to mark the referent
    // field as live.
    Unimplemented();
  }
#endif // INCLUDE_G1GC

  // If G1 is not enabled then attempt to go through the normal entry point
  // Reference.get could be instrumented by jvmti
  return NULL;
}

address CppInterpreterGenerator::generate_native_entry(bool synchronized) {
  return generate_entry((address) CppInterpreter::native_entry);
}

address CppInterpreterGenerator::generate_normal_entry(bool synchronized) {
  return generate_entry((address) CppInterpreter::normal_entry);
}
