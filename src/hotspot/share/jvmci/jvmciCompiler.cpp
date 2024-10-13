/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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

JVMCICompiler* JVMCICompiler::_instance = nullptr;

JVMCICompiler::JVMCICompiler() : AbstractCompiler(compiler_jvmci) {
  _bootstrapping = false;
  _bootstrap_compilation_request_handled = false;
  _methods_compiled = 0;
  _ok_upcalls = 0;
  _err_upcalls = 0;
  _disabled = false;
  _global_compilation_ticks = 0;
  assert(_instance == nullptr, "only one instance allowed");
  _instance = this;
}

JVMCICompiler* JVMCICompiler::instance(bool require_non_null, TRAPS) {
  if (!EnableJVMCI) {
    THROW_MSG_NULL(vmSymbols::java_lang_InternalError(), "JVMCI is not enabled")
  }
  if (_instance == nullptr && require_non_null) {
    THROW_MSG_NULL(vmSymbols::java_lang_InternalError(), "The JVMCI compiler instance has not been created");
  }
  return _instance;
}

void compiler_stubs_init(bool in_compiler_thread);

// Initialization
void JVMCICompiler::initialize() {
  assert(!CompilerConfig::is_c1_or_interpreter_only_no_jvmci(), "JVMCI is launched, it's not c1/interpreter only mode");
  if (!UseCompiler || !EnableJVMCI || !UseJVMCICompiler || !should_perform_init()) {
    return;
  }
  compiler_stubs_init(true /* in_compiler_thread */); // generate compiler's intrinsics stubs
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
  if (_disabled) {
    return true;
  }
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
    if (runtime != nullptr) {
      JVMCIObject receiver = runtime->probe_HotSpotJVMCIRuntime();
      if (receiver.is_null()) {
        return false;
      }
      JVMCIEnv* ignored_env = nullptr;
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

bool JVMCICompiler::is_intrinsic_supported(const methodHandle& method) {
  vmIntrinsics::ID id = method->intrinsic_id();
  assert(id != vmIntrinsics::_none, "must be a VM intrinsic");
  JavaThread* thread = JavaThread::current();
  JVMCIEnv jvmciEnv(thread, __FILE__, __LINE__);
  JVMCIRuntime* runtime = JVMCI::compiler_runtime(thread, false);
  return runtime->is_intrinsic_supported(&jvmciEnv, (jint) id);
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

void JVMCICompiler::on_upcall(const char* error, JVMCICompileState* compile_state) {
  if (error != nullptr) {

    Atomic::inc(&_err_upcalls);
    int ok = _ok_upcalls;
    int err = _err_upcalls;
    // If there have been at least 10 upcalls with an error
    // and the number of error upcalls is 10% or more of the
    // number of non-error upcalls, disable JVMCI compilation.
    if (err > 10 && err * 10 > ok && !_disabled) {
      _disabled = true;
      int total = err + ok;
      // Using stringStream instead of err_msg to avoid truncation
      stringStream st;
      st.print("JVMCI compiler disabled "
               "after %d of %d upcalls had errors (Last error: \"%s\"). "
               "Use -Xlog:jit+compilation for more detail.", err, total, error);
      const char* disable_msg = st.freeze();
      log_warning(jit,compilation)("%s", disable_msg);
      if (compile_state != nullptr) {
        const char* disable_error = os::strdup(disable_msg);
        if (disable_error != nullptr) {
          compile_state->set_failure(true, disable_error, true);
          JVMCI_event_1("%s", disable_error);
          return;
        } else {
          // Leave failure reason as set by caller when strdup fails
        }
      }
    }
    JVMCI_event_1("JVMCI upcall had an error: %s", error);
  } else {
    Atomic::inc(&_ok_upcalls);
  }
}

void JVMCICompiler::inc_global_compilation_ticks() {
  Atomic::inc(&_global_compilation_ticks);
}
