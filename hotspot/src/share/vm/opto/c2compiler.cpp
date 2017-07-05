/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_c2compiler.cpp.incl"


volatile int C2Compiler::_runtimes = uninitialized;

// register information defined by ADLC
extern const char register_save_policy[];
extern const int  register_save_type[];

const char* C2Compiler::retry_no_subsuming_loads() {
  return "retry without subsuming loads";
}
const char* C2Compiler::retry_no_escape_analysis() {
  return "retry without escape analysis";
}
void C2Compiler::initialize_runtime() {

  // Check assumptions used while running ADLC
  Compile::adlc_verification();
  assert(REG_COUNT <= ConcreteRegisterImpl::number_of_registers, "incompatible register counts");

  for (int i = 0; i < ConcreteRegisterImpl::number_of_registers ; i++ ) {
      OptoReg::vm2opto[i] = OptoReg::Bad;
  }

  for( OptoReg::Name i=OptoReg::Name(0); i<OptoReg::Name(REG_COUNT); i = OptoReg::add(i,1) ) {
    VMReg r = OptoReg::as_VMReg(i);
    if (r->is_valid()) {
      OptoReg::vm2opto[r->value()] = i;
    }
  }

  // Check that runtime and architecture description agree on callee-saved-floats
  bool callee_saved_floats = false;
  for( OptoReg::Name i=OptoReg::Name(0); i<OptoReg::Name(_last_Mach_Reg); i = OptoReg::add(i,1) ) {
    // Is there a callee-saved float or double?
    if( register_save_policy[i] == 'E' /* callee-saved */ &&
       (register_save_type[i] == Op_RegF || register_save_type[i] == Op_RegD) ) {
      callee_saved_floats = true;
    }
  }

  DEBUG_ONLY( Node::init_NodeProperty(); )

  Compile::pd_compiler2_init();

  CompilerThread* thread = CompilerThread::current();

  HandleMark  handle_mark(thread);

  OptoRuntime::generate(thread->env());

}


void C2Compiler::initialize() {

  // This method can only be called once per C2Compiler object
  // The first compiler thread that gets here will initialize the
  // small amount of global state (and runtime stubs) that c2 needs.

  // There is a race possible once at startup and then we're fine

  // Note that this is being called from a compiler thread not the
  // main startup thread.

  if (_runtimes != initialized) {
    initialize_runtimes( initialize_runtime, &_runtimes);
  }

  // Mark this compiler object as ready to roll
  mark_initialized();
}

void C2Compiler::compile_method(ciEnv* env,
                                ciMethod* target,
                                int entry_bci) {
  if (!is_initialized()) {
    initialize();
  }
  bool subsume_loads = SubsumeLoads;
  bool do_escape_analysis = DoEscapeAnalysis &&
    !env->jvmti_can_access_local_variables();
  while (!env->failing()) {
    // Attempt to compile while subsuming loads into machine instructions.
    Compile C(env, this, target, entry_bci, subsume_loads, do_escape_analysis);


    // Check result and retry if appropriate.
    if (C.failure_reason() != NULL) {
      if (C.failure_reason_is(retry_no_subsuming_loads())) {
        assert(subsume_loads, "must make progress");
        subsume_loads = false;
        continue;  // retry
      }
      if (C.failure_reason_is(retry_no_escape_analysis())) {
        assert(do_escape_analysis, "must make progress");
        do_escape_analysis = false;
        continue;  // retry
      }
      // Pass any other failure reason up to the ciEnv.
      // Note that serious, irreversible failures are already logged
      // on the ciEnv via env->record_method_not_compilable().
      env->record_failure(C.failure_reason());
    }
    if (StressRecompilation) {
      if (subsume_loads) {
        subsume_loads = false;
        continue;  // retry
      }
      if (do_escape_analysis) {
        do_escape_analysis = false;
        continue;  // retry
      }
    }

    // No retry; just break the loop.
    break;
  }
}


void C2Compiler::print_timers() {
  // do nothing
}
