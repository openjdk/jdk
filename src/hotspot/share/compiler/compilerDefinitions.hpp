/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_COMPILER_COMPILERDEFINITIONS_HPP
#define SHARE_COMPILER_COMPILERDEFINITIONS_HPP

#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

// The (closed set) of concrete compiler classes.
enum CompilerType : u1 {
  compiler_none,
  compiler_c1,
  compiler_c2,
  compiler_jvmci,
  compiler_number_of_types
};

extern const char* compilertype2name_tab[compiler_number_of_types];     // Map CompilerType to its name
inline const char* compilertype2name(CompilerType t) { return (uint)t < compiler_number_of_types ? compilertype2name_tab[t] : "invalid"; }

// Handy constants for deciding which compiler mode to use.
enum MethodCompilation {
  InvocationEntryBci   = -1,     // i.e., not a on-stack replacement compilation
  BeforeBci            = InvocationEntryBci,
  AfterBci             = -2,
  UnwindBci            = -3,
  AfterExceptionBci    = -4,
  UnknownBci           = -5,
  InvalidFrameStateBci = -6
};

// Enumeration to distinguish tiers of compilation
enum CompLevel : s1 {
  CompLevel_any               = -1,        // Used for querying the state
  CompLevel_all               = -1,        // Used for changing the state
  CompLevel_none              = 0,         // Interpreter
  CompLevel_simple            = 1,         // C1
  CompLevel_limited_profile   = 2,         // C1, invocation & backedge counters
  CompLevel_full_profile      = 3,         // C1, invocation & backedge counters + mdo
  CompLevel_full_optimization = 4          // C2 or JVMCI
};

class CompilationModeFlag : AllStatic {
  enum class Mode {
    NORMAL,
    QUICK_ONLY,
    HIGH_ONLY,
    HIGH_ONLY_QUICK_INTERNAL
  };
  static Mode _mode;
  static void print_error();
public:
  static bool initialize();
  static bool normal()                   { return _mode == Mode::NORMAL;                   }
  static bool quick_only()               { return _mode == Mode::QUICK_ONLY;               }
  static bool high_only()                { return _mode == Mode::HIGH_ONLY;                }
  static bool high_only_quick_internal() { return _mode == Mode::HIGH_ONLY_QUICK_INTERNAL; }

  static bool disable_intermediate()     { return high_only() || high_only_quick_internal(); }
  static bool quick_internal()           { return !high_only(); }

  static void set_high_only_quick_internal() { _mode = Mode::HIGH_ONLY_QUICK_INTERNAL; }
  static void set_quick_only()               { _mode = Mode::QUICK_ONLY;               }
  static void set_high_only()                { _mode = Mode::HIGH_ONLY;                }
};

inline bool is_c1_compile(int comp_level) {
  return comp_level > CompLevel_none && comp_level < CompLevel_full_optimization;
}

inline bool is_c2_compile(int comp_level) {
  return comp_level == CompLevel_full_optimization;
}

inline bool is_compile(int comp_level) {
  return is_c1_compile(comp_level) || is_c2_compile(comp_level);
}


// States of Restricted Transactional Memory usage.
enum RTMState {
  NoRTM      = 0x2, // Don't use RTM
  UseRTM     = 0x1, // Use RTM
  ProfileRTM = 0x0  // Use RTM with abort ratio calculation
};

#ifndef INCLUDE_RTM_OPT
#define INCLUDE_RTM_OPT 0
#endif
#if INCLUDE_RTM_OPT
#define RTM_OPT_ONLY(code) code
#else
#define RTM_OPT_ONLY(code)
#endif

class CompilerConfig : public AllStatic {
public:
  // Scale compile thresholds
  // Returns threshold scaled with CompileThresholdScaling
  static intx scaled_compile_threshold(intx threshold, double scale);
  static intx scaled_compile_threshold(intx threshold);
  static intx jvmflag_scaled_compile_threshold(intx threshold);

  // Returns freq_log scaled with CompileThresholdScaling
  static intx scaled_freq_log(intx freq_log, double scale);
  static intx scaled_freq_log(intx freq_log);
  static intx jvmflag_scaled_freq_log(intx freq_log);

  static bool check_args_consistency(bool status);

  static void ergo_initialize();

  // Which compilers are baked in?
  constexpr static bool has_c1()     { return COMPILER1_PRESENT(true) NOT_COMPILER1(false); }
  constexpr static bool has_c2()     { return COMPILER2_PRESENT(true) NOT_COMPILER2(false); }
  constexpr static bool has_jvmci()  { return JVMCI_ONLY(true) NOT_JVMCI(false);            }
  constexpr static bool has_tiered() { return has_c1() && (has_c2() || has_jvmci());        }

  inline static bool is_jvmci_compiler();
  inline static bool is_jvmci();
  inline static bool is_interpreter_only();

  // is_*_only() functions describe situations in which the JVM is in one way or another
  // forced to use a particular compiler or their combination. The constraint functions
  // deliberately ignore the fact that there may also be methods installed
  // through JVMCI (where the JVMCI compiler was invoked not through the broker). Be sure
  // to check for those (using is_jvmci()) in situations where it matters.

  inline static bool is_tiered();

  inline static bool is_c1_enabled();
  inline static bool is_c1_only();
  inline static bool is_c1_simple_only();
  inline static bool is_c1_or_interpreter_only_no_jvmci();
  inline static bool is_c1_only_no_jvmci();
  inline static bool is_c1_profiling();

  inline static bool is_jvmci_compiler_enabled();
  inline static bool is_jvmci_compiler_only();

  inline static bool is_c2_only();
  inline static bool is_c2_enabled();
  inline static bool is_c2_or_jvmci_compiler_only();
  inline static bool is_c2_or_jvmci_compiler_enabled();

private:
  static bool is_compilation_mode_selected();
  static void set_compilation_policy_flags();
  static void set_jvmci_specific_flags();
  static void set_legacy_emulation_flags();
  static void set_client_emulation_mode_flags();
};

#endif // SHARE_COMPILER_COMPILERDEFINITIONS_HPP
