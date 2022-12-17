/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "classfile/vmClasses.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compilerDefinitions.inline.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/vmSymbols.hpp"
#include "jvmci/jvmciEnv.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "runtime/arguments.hpp"
#include "runtime/handles.inline.hpp"

JVMCICompiler* JVMCICompiler::_instance = NULL;

JVMCICompiler::JVMCICompiler() : AbstractCompiler(compiler_jvmci) {
  _bootstrapping = false;
  _bootstrap_compilation_request_handled = false;
  _methods_compiled = 0;
  _global_compilation_ticks = 0;
  assert(_instance == NULL, "only one instance allowed");
  _instance = this;
}

JVMCICompiler* JVMCICompiler::instance(bool require_non_null, TRAPS) {
  if (!EnableJVMCI) {
    THROW_MSG_NULL(vmSymbols::java_lang_InternalError(), "JVMCI is not enabled")
  }
  if (_instance == NULL && require_non_null) {
    THROW_MSG_NULL(vmSymbols::java_lang_InternalError(), "The JVMCI compiler instance has not been created");
  }
  return _instance;
}

// Initialization
void JVMCICompiler::initialize() {
  assert(!CompilerConfig::is_c1_or_interpreter_only_no_jvmci(), "JVMCI is launched, it's not c1/interpreter only mode");
  if (!UseCompiler || !EnableJVMCI || !UseJVMCICompiler || !should_perform_init()) {
    return;
  }

  set_state(initialized);
}

void JVMCICompiler::bootstrap(TRAPS) {
  if (Arguments::mode() == Arguments::_int) {
    // Nothing to do in -Xint mode
    return;
  }
  _bootstrapping = true;
  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);
  if (PrintBootstrap) {
    tty->print("Bootstrapping JVMCI");
  }
  jlong start = os::javaTimeNanos();

  Array<Method*>* objectMethods = vmClasses::Object_klass()->methods();
  // Initialize compile queue with a selected set of methods.
  int len = objectMethods->length();
  for (int i = 0; i < len; i++) {
    methodHandle mh(THREAD, objectMethods->at(i));
    if (!mh->is_native() && !mh->is_static() && !mh->is_initializer()) {
      ResourceMark rm;
      int hot_count = 10; // TODO: what's the appropriate value?
      CompileBroker::compile_method(mh, InvocationEntryBci, CompLevel_full_optimization, mh, hot_count, CompileTask::Reason_Bootstrap, CHECK);
    }
  }

  int qsize;
  bool first_round = true;
  int z = 0;
  do {
    // Loop until there is something in the queue.
    do {
      THREAD->sleep(100);
      qsize = CompileBroker::queue_size(CompLevel_full_optimization);
    } while (!_bootstrap_compilation_request_handled && first_round && qsize == 0);
    first_round = false;
    if (PrintBootstrap) {
      while (z < (_methods_compiled / 100)) {
        ++z;
        tty->print_raw(".");
      }
    }
  } while (qsize != 0);

  if (PrintBootstrap) {
    tty->print_cr(" in " JLONG_FORMAT " ms (compiled %d methods)",
                  (jlong)nanos_to_millis(os::javaTimeNanos() - start), _methods_compiled);
  }
  _bootstrapping = false;
  JVMCI::java_runtime()->bootstrap_finished(CHECK);
}

bool JVMCICompiler::force_comp_at_level_simple(const methodHandle& method) {
  if (_bootstrapping) {
    // When bootstrapping, the JVMCI compiler can compile its own methods.
    return false;
  }
  if (UseJVMCINativeLibrary) {
    // This mechanism exists to force compilation of a JVMCI compiler by C1
    // to reduce the compilation time spent on the JVMCI compiler itself. In
    // +UseJVMCINativeLibrary mode, the JVMCI compiler is AOT compiled.
    return false;
  } else {
    JVMCIRuntime* runtime = JVMCI::java_runtime();
    if (runtime != NULL) {
      JVMCIObject receiver = runtime->probe_HotSpotJVMCIRuntime();
      if (receiver.is_null()) {
        return false;
      }
      JVMCIEnv* ignored_env = NULL;
      objArrayHandle excludeModules(JavaThread::current(), HotSpotJVMCI::HotSpotJVMCIRuntime::excludeFromJVMCICompilation(ignored_env, HotSpotJVMCI::resolve(receiver)));
      if (excludeModules.not_null()) {
        ModuleEntry* moduleEntry = method->method_holder()->module();
        for (int i = 0; i < excludeModules->length(); i++) {
          if (excludeModules->obj_at(i) == moduleEntry->module()) {
            return true;
          }
        }
      }
    }
    return false;
  }
}

// Compilation entry point for methods
void JVMCICompiler::compile_method(ciEnv* env, ciMethod* target, int entry_bci, bool install_code, DirectiveSet* directive) {
  ShouldNotReachHere();
}

void JVMCICompiler::stopping_compiler_thread(CompilerThread* current) {
  if (UseJVMCINativeLibrary) {
    JVMCIRuntime* runtime = JVMCI::compiler_runtime(current, false);
    if (runtime != nullptr) {
      MutexUnlocker unlock(CompileThread_lock);
      runtime->detach_thread(current, "stopping idle compiler thread");
    }
  }
}

void JVMCICompiler::on_empty_queue(CompileQueue* queue, CompilerThread* thread) {
  if (UseJVMCINativeLibrary) {
    int delay = JVMCICompilerIdleDelay;
    JVMCIRuntime* runtime = JVMCI::compiler_runtime(thread, false);
    // Don't detach JVMCI compiler threads from their JVMCI
    // runtime during the VM startup grace period
    if (runtime != nullptr && delay > 0 && tty->time_stamp().milliseconds() > DEFAULT_COMPILER_IDLE_DELAY) {
      bool timeout = MethodCompileQueue_lock->wait(delay);
      // Unlock as detaching or repacking can result in a JNI call to shutdown a JavaVM
      // and locks cannot be held when making a VM to native transition.
      MutexUnlocker unlock(MethodCompileQueue_lock);
      if (timeout) {
        runtime->detach_thread(thread, "releasing idle compiler thread");
      } else {
        runtime->repack(thread);
      }
    }
  }
}

// Print compilation timers
void JVMCICompiler::print_timers() {
  tty->print_cr("    JVMCI CompileBroker Time:");
  tty->print_cr("       Compile:        %7.3f s", stats()->total_time());
  _jit_code_installs.print_on(tty, "       Install Code:   ");
  tty->cr();
  tty->print_cr("    JVMCI Hosted Time:");
  _hosted_code_installs.print_on(tty, "       Install Code:   ");
}

void JVMCICompiler::CodeInstallStats::print_on(outputStream* st, const char* prefix) const {
  double time = _timer.seconds();
  st->print_cr("%s%7.3f s (installs: %d, CodeBlob total size: %d, CodeBlob code size: %d)",
      prefix, time, _count, _codeBlobs_size, _codeBlobs_code_size);
}

void JVMCICompiler::CodeInstallStats::on_install(CodeBlob* cb) {
  Atomic::inc(&_count);
  Atomic::add(&_codeBlobs_size, cb->size());
  Atomic::add(&_codeBlobs_code_size, cb->code_size());
}

void JVMCICompiler::inc_methods_compiled() {
  Atomic::inc(&_methods_compiled);
  Atomic::inc(&_global_compilation_ticks);
}

void JVMCICompiler::inc_global_compilation_ticks() {
  Atomic::inc(&_global_compilation_ticks);
}
