/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotCacheAccess.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/runTimeClassInfo.hpp"
#include "code/aotCodeCache.hpp"
#include "compiler/aotCompileBroker.hpp"
#include "compiler/compilationPolicy.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compiler_globals.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.hpp"
#include "oops/trainingData.hpp"
#include "runtime/handles.inline.hpp"

class AOTCompileIterator : StackObj {
private:
  CompLevel _comp_level;
  bool      _for_preload;
  Thread*   _thread;
  GrowableArray<Method*> _methods;

public:
  AOTCompileIterator(CompLevel comp_level, bool for_preload, JavaThread* thread)
  : _comp_level(comp_level), _for_preload(for_preload), _thread(thread) {
    assert(TrainingData::have_data(), "sanity");
  }

  bool include(MethodTrainingData* mtd) {
    Method* m = mtd->holder();
    if (m->is_native() || m->is_abstract() || !m->method_holder()->is_linked()) {
      return false;
    }
    CompilerDirectiveMatcher matcher(methodHandle(_thread, m), _comp_level);
    if (matcher.directive_set()->DontAOTCompileOption) {
      return false;
    }
    int high_top_level = mtd->highest_top_level();
    switch (_comp_level) {
      case CompLevel_simple:
      case CompLevel_full_optimization:
        // For final C1/C2 compilations, we only compile when there was relevant compilation during training.
        return _comp_level == high_top_level;
      case CompLevel_limited_profile:
        // For profiled C1 compilations, generate limited profile when there was limited/full
        // profiled compilation in training.
        return CompLevel_limited_profile <= high_top_level && high_top_level <= CompLevel_full_profile;
      case CompLevel_full_profile:
        // We do not include C1 full profiled methods at this time.
        return false;
      default:
        assert(false, "Missed the case: %d", _comp_level);
    }
    // Do not include methods by default.
    return false;
  }

  void apply(TrainingData* td) {
    if (td->is_MethodTrainingData()) {
      MethodTrainingData* mtd = td->as_MethodTrainingData();
      if (mtd->has_holder() && include(mtd)) {
        _methods.push(mtd->holder());
      }
    }
  }

  static MethodTrainingData* method_training_data(Method* m) {
    if (m->method_holder()->is_loaded()) {
      return MethodTrainingData::find(methodHandle(Thread::current(), m));
    }
    return nullptr;
  }

  // We sort methods by compile ID, presuming the methods that compiled earlier
  // are more important. This only matters for preload code, which is loaded
  // asynchronously; other levels are sorted for better consistency between training
  // runs. Since we can accept methods from multiple levels, we use the compile ID
  // from the lowest level.
  static int compare_methods(Method** m1, Method** m2) {
    int c1 = compile_id(*m1);
    int c2 = compile_id(*m2);
    if (c1 < c2) return -1;
    if (c1 > c2) return +1;
    return 0;
  }

  static int compile_id(Method* m) {
    // Methods without recorded compilations are treated as "compiled last"
    int id = INT_MAX;
    MethodTrainingData* mtd = method_training_data(m);
    if (mtd != nullptr) {
      for (int level = CompLevel_simple; level <= CompilationPolicy::highest_compile_level(); level++) {
        CompileTrainingData* ctd = mtd->last_toplevel_compile(level);
        if (ctd != nullptr) {
          id = MIN2(id, ctd->compile_id());
        }
      }
    }
    return id;
  }

  void schedule_compilations(TRAPS) {
    for (int i = 0; i < _methods.length(); i++) {
      Method* m = _methods.at(i);
      methodHandle mh(THREAD, m);
      assert(mh()->method_holder()->is_linked(), "required");
      if (!AOTCacheAccess::can_generate_aot_code(m)) {
        continue; // Method is not archived
      }
      assert(!HAS_PENDING_EXCEPTION, "");
      CompileBroker::compile_method(mh, InvocationEntryBci, _comp_level,
                                    0, nullptr /*requires_online_comp*/,
                                    _for_preload ? CompileTask::Reason_AOTCompileForPreload : CompileTask::Reason_AOTCompile,
                                    THREAD);
      if (HAS_PENDING_EXCEPTION) {
        CLEAR_PENDING_EXCEPTION;
      }
    }
  }

  void print_compilation_status(ArchiveBuilder* builder) {
    LogStreamHandle(Info, aot, compilation) logi;
    if (!logi.is_enabled()) {
      return;
    }
    int success_count = 0;
    const int log_comp_level = _comp_level + (_for_preload ? 1 : 0);

    for (int i = 0; i < _methods.length(); i++) {
      Method* m = _methods.at(i);

      bool is_success = !m->is_not_compilable(_comp_level);
      if (is_success) {
        success_count++;
      }

      LogStreamHandle(Debug, aot, compilation) log;
      if (log.is_enabled()) {
        ResourceMark rm;
        log.print("[%4d] T%d Compiled %s [%p", i, log_comp_level, m->external_name(), m);
        if (builder != nullptr) {
          Method* requested_m = builder->to_requested(builder->get_buffered_addr(m));
          log.print(" -> %p", requested_m);
        }
        log.print_cr("] {%d} [%d] (%s)", compile_id(m), AOTCodeCache::store_entries_cnt(), (is_success ? "success" : "FAILED"));
      }
    }

    logi.print_cr("AOT Compilation for level %d finished (%d successful out of %d total)",
                  log_comp_level, success_count, _methods.length());
  }

  void compile(ArchiveBuilder* builder, TRAPS) {
    _methods.sort(&compare_methods);
    schedule_compilations(THREAD);
    CompileBroker::wait_for_no_active_tasks();
    print_compilation_status(builder);
  }
};

void AOTCompileBroker::compile_aot_code(ArchiveBuilder* builder, TRAPS) {
  assert(AOTCodeCache::is_dumping_code(), "sanity");
  if (TrainingData::have_data()) {
    ResourceMark rm;
    CompLevel highest_level = CompilationPolicy::highest_compile_level();
    if (highest_level >= CompLevel_full_optimization && ClassInitBarrierMode > 0) {
      AOTCompileIterator aci(CompLevel_full_optimization, true /*for_preload*/, THREAD);
      TrainingData::iterate([&](TrainingData* td) {
        aci.apply(td);
      });
      aci.compile(builder, THREAD);
    }

    for (int level = CompLevel_simple; level <= highest_level; level++) {
      AOTCompileIterator aci((CompLevel)level, false /*for_preload*/, THREAD);
      TrainingData::iterate([&](TrainingData* td) {
        aci.apply(td);
      });
      aci.compile(builder, THREAD);
    }
  }
}
