/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/handles.hpp"
#include "jvmci/jvmciJavaClasses.hpp"
#include "jvmci/jvmciCompiler.hpp"
#include "jvmci/jvmciEnv.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "runtime/compilationPolicy.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/handles.inline.hpp"

JVMCICompiler* JVMCICompiler::_instance = NULL;
elapsedTimer JVMCICompiler::_codeInstallTimer;

JVMCICompiler::JVMCICompiler() : AbstractCompiler(compiler_jvmci) {
  _bootstrapping = false;
  _bootstrap_compilation_request_handled = false;
  _methods_compiled = 0;
  assert(_instance == NULL, "only one instance allowed");
  _instance = this;
}

// Initialization
void JVMCICompiler::initialize() {
  if (!UseCompiler || !EnableJVMCI || !UseJVMCICompiler || !should_perform_init()) {
    return;
  }

  set_state(initialized);

  // JVMCI is considered as application code so we need to
  // stop the VM deferring compilation now.
  CompilationPolicy::completed_vm_startup();
}

void JVMCICompiler::bootstrap(TRAPS) {
  if (Arguments::mode() == Arguments::_int) {
    // Nothing to do in -Xint mode
    return;
  }
  _bootstrapping = true;
  ResourceMark rm;
  HandleMark hm;
  if (PrintBootstrap) {
    tty->print("Bootstrapping JVMCI");
  }
  jlong start = os::javaTimeMillis();

  Array<Method*>* objectMethods = SystemDictionary::Object_klass()->methods();
  // Initialize compile queue with a selected set of methods.
  int len = objectMethods->length();
  for (int i = 0; i < len; i++) {
    methodHandle mh = objectMethods->at(i);
    if (!mh->is_native() && !mh->is_static() && !mh->is_initializer()) {
      ResourceMark rm;
      int hot_count = 10; // TODO: what's the appropriate value?
      CompileBroker::compile_method(mh, InvocationEntryBci, CompLevel_full_optimization, mh, hot_count, CompileTask::Reason_Bootstrap, THREAD);
    }
  }

  int qsize;
  bool first_round = true;
  int z = 0;
  do {
    // Loop until there is something in the queue.
    do {
      os::sleep(THREAD, 100, true);
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
    tty->print_cr(" in " JLONG_FORMAT " ms (compiled %d methods)", os::javaTimeMillis() - start, _methods_compiled);
  }
  _bootstrapping = false;
  JVMCIRuntime::bootstrap_finished(CHECK);
}

#define CHECK_EXIT THREAD); \
if (HAS_PENDING_EXCEPTION) { \
  char buf[256]; \
  jio_snprintf(buf, 256, "Uncaught exception at %s:%d", __FILE__, __LINE__); \
  JVMCICompiler::exit_on_pending_exception(PENDING_EXCEPTION, buf); \
  return; \
} \
(void)(0

void JVMCICompiler::compile_method(const methodHandle& method, int entry_bci, JVMCIEnv* env) {
  JVMCI_EXCEPTION_CONTEXT

  bool is_osr = entry_bci != InvocationEntryBci;
  if (_bootstrapping && is_osr) {
      // no OSR compilations during bootstrap - the compiler is just too slow at this point,
      // and we know that there are no endless loops
      return;
  }

  JVMCIRuntime::initialize_well_known_classes(CHECK_EXIT);

  HandleMark hm;
  Handle receiver = JVMCIRuntime::get_HotSpotJVMCIRuntime(CHECK_EXIT);

  JavaValue method_result(T_OBJECT);
  JavaCallArguments args;
  args.push_long((jlong) (address) method());
  JavaCalls::call_static(&method_result, SystemDictionary::HotSpotResolvedJavaMethodImpl_klass(),
                         vmSymbols::fromMetaspace_name(), vmSymbols::method_fromMetaspace_signature(), &args, THREAD);

  JavaValue result(T_OBJECT);
  if (!HAS_PENDING_EXCEPTION) {
    JavaCallArguments args;
    args.push_oop(receiver);
    args.push_oop(Handle(THREAD, (oop)method_result.get_jobject()));
    args.push_int(entry_bci);
    args.push_long((jlong) (address) env);
    args.push_int(env->task()->compile_id());
    JavaCalls::call_special(&result, receiver->klass(),
                            vmSymbols::compileMethod_name(), vmSymbols::compileMethod_signature(), &args, THREAD);
  }

  // An uncaught exception was thrown during compilation.  Generally these
  // should be handled by the Java code in some useful way but if they leak
  // through to here report them instead of dying or silently ignoring them.
  if (HAS_PENDING_EXCEPTION) {
    Handle exception(THREAD, PENDING_EXCEPTION);
    CLEAR_PENDING_EXCEPTION;

    java_lang_Throwable::java_printStackTrace(exception, THREAD);
    if (HAS_PENDING_EXCEPTION) {
      CLEAR_PENDING_EXCEPTION;
    }

    env->set_failure("exception throw", false);
  } else {
    oop result_object = (oop) result.get_jobject();
    if (result_object != NULL) {
      oop failure_message = HotSpotCompilationRequestResult::failureMessage(result_object);
      if (failure_message != NULL) {
        const char* failure_reason = java_lang_String::as_utf8_string(failure_message);
        env->set_failure(failure_reason, HotSpotCompilationRequestResult::retry(result_object) != 0);
      } else {
        if (env->task()->code() == NULL) {
          env->set_failure("no nmethod produced", true);
        } else {
          env->task()->set_num_inlined_bytecodes(HotSpotCompilationRequestResult::inlinedBytecodes(result_object));
          Atomic::inc(&_methods_compiled);
        }
      }
    } else {
      assert(false, "JVMCICompiler.compileMethod should always return non-null");
    }
  }
  if (_bootstrapping) {
    _bootstrap_compilation_request_handled = true;
  }
}

CompLevel JVMCIRuntime::adjust_comp_level(const methodHandle& method, bool is_osr, CompLevel level, JavaThread* thread) {
  if (!thread->adjusting_comp_level()) {
    thread->set_adjusting_comp_level(true);
    level = adjust_comp_level_inner(method, is_osr, level, thread);
    thread->set_adjusting_comp_level(false);
  }
  return level;
}

void JVMCICompiler::exit_on_pending_exception(oop exception, const char* message) {
  JavaThread* THREAD = JavaThread::current();
  CLEAR_PENDING_EXCEPTION;

  static volatile int report_error = 0;
  if (!report_error && Atomic::cmpxchg(1, &report_error, 0) == 0) {
    // Only report an error once
    tty->print_raw_cr(message);
    Handle ex(THREAD, exception);
    java_lang_Throwable::java_printStackTrace(ex, THREAD);
  } else {
    // Allow error reporting thread to print the stack trace.  Windows
    // doesn't allow uninterruptible wait for JavaThreads
    const bool interruptible = true;
    os::sleep(THREAD, 200, interruptible);
  }

  before_exit(THREAD);
  vm_exit(-1);
}

// Compilation entry point for methods
void JVMCICompiler::compile_method(ciEnv* env, ciMethod* target, int entry_bci, DirectiveSet* directive) {
  ShouldNotReachHere();
}

// Print compilation timers and statistics
void JVMCICompiler::print_timers() {
  print_compilation_timers();
}

// Print compilation timers and statistics
void JVMCICompiler::print_compilation_timers() {
  TRACE_jvmci_1("JVMCICompiler::print_timers");
  tty->print_cr("       JVMCI code install time:        %6.3f s",    _codeInstallTimer.seconds());
}
