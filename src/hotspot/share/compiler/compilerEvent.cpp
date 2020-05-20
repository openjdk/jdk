/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciMethod.hpp"
#include "compiler/compilerEvent.hpp"
#include "jfr/jfr.hpp"
#include "jfr/jfrEvents.hpp"
#include "jfr/metadata/jfrSerializer.hpp"
#include "runtime/semaphore.inline.hpp"
#include "utilities/growableArray.hpp"

// Synchronizes access to phases_names.
class PhaseTypeGuard : public StackObj {
 private:
  static Semaphore _mutex_semaphore;
 public:
  PhaseTypeGuard() {
    _mutex_semaphore.wait();
  }
  ~PhaseTypeGuard() {
    _mutex_semaphore.signal();
  }
};

Semaphore PhaseTypeGuard::_mutex_semaphore(1);

static void write_phases(JfrCheckpointWriter& writer, u4 base_idx, GrowableArray<const char*>* phases) {
  assert(phases != NULL, "invariant");
  assert(phases->is_nonempty(), "invariant");
  const u4 nof_entries = phases->length();
  writer.write_count(nof_entries);
  for (u4 i = 0; i < nof_entries; i++) {
    writer.write_key(base_idx + i);
    writer.write(phases->at(i));
  }
}

static GrowableArray<const char*>* phase_names = NULL;

class CompilerPhaseTypeConstant : public JfrSerializer {
 public:
  void serialize(JfrCheckpointWriter& writer) {
    PhaseTypeGuard guard;
    write_phases(writer, 0, phase_names);
  }
};

// This function provides support for adding dynamic entries to JFR type CompilerPhaseType.
// The mapping for CompilerPhaseType is maintained as growable array phase_names.
// The serializer CompilerPhaseTypeConstant must be registered with JFR at vm init.
// Registration of new phase names creates mapping, serialize it for current chunk and registers its serializer with JFR if it is not already done.
int CompilerEvent::PhaseEvent::register_phases(GrowableArray<const char*>* new_phases) {
  int idx = -1;
  if (new_phases == NULL || new_phases->is_empty()) {
    return idx;
  }
  bool register_jfr_serializer = false;
  {
    PhaseTypeGuard guard;
    if (phase_names == NULL) {
      phase_names = new (ResourceObj::C_HEAP, mtInternal) GrowableArray<const char*>(100, true);
      register_jfr_serializer = true;
    }
    idx = phase_names->length();
    phase_names->appendAll(new_phases);
    guarantee(phase_names->length() < 256, "exceeds maximum supported phases");
  }
  if (register_jfr_serializer) {
    JfrSerializer::register_serializer(TYPE_COMPILERPHASETYPE, false, new CompilerPhaseTypeConstant());
  } else if (Jfr::is_recording()) {
    // serialize new_phases.
    JfrCheckpointWriter writer;
    writer.write_type(TYPE_COMPILERPHASETYPE);
    write_phases(writer, idx, new_phases);
  }
  return idx;
}

void CompilerEvent::CompilationEvent::post(EventCompilation& event, int compile_id, CompilerType compiler_type, Method* method, int compile_level, bool success, bool is_osr, int code_size, int inlined_bytecodes) {
  event.set_compileId(compile_id);
  event.set_compiler(compiler_type);
  event.set_method(method);
  event.set_compileLevel((short)compile_level);
  event.set_succeded(success);
  event.set_isOsr(is_osr);
  event.set_codeSize(code_size);
  event.set_inlinedBytes(inlined_bytecodes);
  event.commit();
}

void CompilerEvent::CompilationFailureEvent::post(EventCompilationFailure& event, int compile_id, const char* reason) {
  event.set_compileId(compile_id);
  event.set_failureMessage(reason);
  event.commit();
}

void CompilerEvent::PhaseEvent::post(EventCompilerPhase& event, const Ticks& start_time, int phase, int compile_id, int level) {
  event.set_starttime(start_time);
  event.set_phase((u1) phase);
  event.set_compileId(compile_id);
  event.set_phaseLevel((short)level);
  event.commit();
}

void CompilerEvent::InlineEvent::post(EventCompilerInlining& event, int compile_id, Method* caller, const JfrStructCalleeMethod& callee, bool success, const char* msg, int bci) {
  event.set_compileId(compile_id);
  event.set_caller(caller);
  event.set_callee(callee);
  event.set_succeeded(success);
  event.set_message(msg);
  event.set_bci(bci);
  event.commit();
}

void CompilerEvent::InlineEvent::post(EventCompilerInlining& event, int compile_id, Method* caller, Method* callee, bool success, const char* msg, int bci) {
  JfrStructCalleeMethod callee_struct;
  callee_struct.set_type(callee->klass_name()->as_utf8());
  callee_struct.set_name(callee->name()->as_utf8());
  callee_struct.set_descriptor(callee->signature()->as_utf8());
  post(event, compile_id, caller, callee_struct, success, msg, bci);
}

void CompilerEvent::InlineEvent::post(EventCompilerInlining& event, int compile_id, Method* caller, ciMethod* callee, bool success, const char* msg, int bci) {
  JfrStructCalleeMethod callee_struct;
  callee_struct.set_type(callee->holder()->name()->as_utf8());
  callee_struct.set_name(callee->name()->as_utf8());
  callee_struct.set_descriptor(callee->signature()->as_symbol()->as_utf8());
  post(event, compile_id, caller, callee_struct, success, msg, bci);
}
