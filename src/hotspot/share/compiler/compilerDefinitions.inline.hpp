/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_COMPILER_COMPILERDEFINITIONS_INLINE_HPP
#define SHARE_COMPILER_COMPILERDEFINITIONS_INLINE_HPP

#include "compiler/compilerDefinitions.hpp"

#include "compiler/compiler_globals.hpp"
#include "compiler/compilerDefinitions.hpp"
#include "runtime/arguments.hpp"

inline bool CompilerConfig::is_interpreter_only() {
  return Arguments::is_interpreter_only() || TieredStopAtLevel == CompLevel_none;
}

inline bool CompilerConfig::is_jvmci_compiler()    { return JVMCI_ONLY(has_jvmci() && UseJVMCICompiler) NOT_JVMCI(false); }
inline bool CompilerConfig::is_jvmci()             { return JVMCI_ONLY(has_jvmci() && EnableJVMCI     ) NOT_JVMCI(false); }

// is_*_only() functions describe situations in which the JVM is in one way or another
// forced to use a particular compiler or their combination. The constraint functions
// deliberately ignore the fact that there may also be methods installed
// through JVMCI (where the JVMCI compiler was invoked not through the broker). Be sure
// to check for those (using is_jvmci()) in situations where it matters.
//

// Is the JVM in a configuration that permits only c1-compiled methods (level 1,2,3)?
inline bool CompilerConfig::is_c1_only() {
  if (!is_interpreter_only() && has_c1()) {
    const bool c1_only = !has_c2() && !is_jvmci_compiler();
    const bool tiered_degraded_to_c1_only = TieredCompilation && TieredStopAtLevel >= CompLevel_simple && TieredStopAtLevel < CompLevel_full_optimization;
    const bool c1_only_compilation_mode = CompilationModeFlag::quick_only();
    return c1_only || tiered_degraded_to_c1_only || c1_only_compilation_mode;
  }
  return false;
}

inline bool CompilerConfig::is_c1_or_interpreter_only_no_jvmci() {
  assert(!is_jvmci_compiler() || is_jvmci(), "JVMCI compiler implies enabled JVMCI");
  return !is_jvmci() && (is_interpreter_only() || is_c1_only());
}

inline bool CompilerConfig::is_c1_only_no_jvmci() {
  return is_c1_only() && !is_jvmci();
}

// Is the JVM in a configuration that permits only c1-compiled methods at level 1?
inline bool CompilerConfig::is_c1_simple_only() {
  if (is_c1_only()) {
    const bool tiered_degraded_to_level_1 = TieredCompilation && TieredStopAtLevel == CompLevel_simple;
    const bool c1_only_compilation_mode = CompilationModeFlag::quick_only();
    const bool tiered_off = !TieredCompilation;
    return tiered_degraded_to_level_1 || c1_only_compilation_mode || tiered_off;
  }
  return false;
}

inline bool CompilerConfig::is_c2_enabled() {
  return has_c2() && !is_interpreter_only() && !is_c1_only() && !is_jvmci_compiler();
}

inline bool CompilerConfig::is_jvmci_compiler_enabled() {
  return is_jvmci_compiler() && !is_interpreter_only() && !is_c1_only();
}
// Is the JVM in a configuration that permits only c2-compiled methods?
inline bool CompilerConfig::is_c2_only() {
  if (is_c2_enabled()) {
    const bool c2_only = !has_c1();
    // There is no JVMCI compiler to replace C2 in the broker, and the user (or ergonomics)
    // is forcing C1 off.
    const bool c2_only_compilation_mode = CompilationModeFlag::high_only();
    const bool tiered_off = !TieredCompilation;
    return c2_only || c2_only_compilation_mode || tiered_off;
  }
  return false;
}

// Is the JVM in a configuration that permits only jvmci-compiled methods?
inline bool CompilerConfig::is_jvmci_compiler_only() {
  if (is_jvmci_compiler_enabled()) {
    const bool jvmci_compiler_only = !has_c1();
    // JVMCI compiler replaced C2 and the user (or ergonomics) is forcing C1 off.
    const bool jvmci_only_compilation_mode = CompilationModeFlag::high_only();
    const bool tiered_off = !TieredCompilation;
    return jvmci_compiler_only || jvmci_only_compilation_mode || tiered_off;
  }
  return false;
}

inline bool CompilerConfig::is_c2_or_jvmci_compiler_only() {
  return is_c2_only() || is_jvmci_compiler_only();
}

// Tiered is basically C1 & (C2 | JVMCI) minus all the odd cases with restrictions.
inline bool CompilerConfig::is_tiered() {
  assert(!is_c1_simple_only() || is_c1_only(), "c1 simple mode must imply c1-only mode");
  return has_tiered() && !is_interpreter_only() && !is_c1_only() && !is_c2_or_jvmci_compiler_only();
}

inline bool CompilerConfig::is_c1_enabled() {
  return has_c1() && !is_interpreter_only() && !is_c2_or_jvmci_compiler_only();
}

inline bool CompilerConfig::is_c1_profiling() {
  const bool c1_only_profiling = is_c1_only() && !is_c1_simple_only();
  const bool tiered = is_tiered();
  return c1_only_profiling || tiered;
}

inline bool CompilerConfig::is_c2_or_jvmci_compiler_enabled() {
  return is_c2_enabled() || is_jvmci_compiler_enabled();
}

#endif // SHARE_COMPILER_COMPILERDEFINITIONS_INLINE_HPP
