/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/oopFactory.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/handles.hpp"
#include "jvmci/jvmciJavaClasses.hpp"
#include "jvmci/jvmciCompiler.hpp"
#include "jvmci/jvmciEnv.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "runtime/compilationPolicy.hpp"
#include "runtime/globals_extension.hpp"

JVMCICompiler* JVMCICompiler::_instance = NULL;
elapsedTimer JVMCICompiler::_codeInstallTimer;

JVMCICompiler::JVMCICompiler() : AbstractCompiler(jvmci) {
  _bootstrapping = false;
  _methodsCompiled = 0;
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

void JVMCICompiler::bootstrap() {
  if (Arguments::mode() == Arguments::_int) {
    // Nothing to do in -Xint mode
    return;
  }
#ifndef PRODUCT
  // We turn off CompileTheWorld so that compilation requests are not
  // ignored during bootstrap or that JVMCI can be compiled by C1/C2.
  FlagSetting ctwOff(CompileTheWorld, false);
#endif

  JavaThread* THREAD = JavaThread::current();
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
      CompileBroker::compile_method(mh, InvocationEntryBci, CompLevel_full_optimization, mh, hot_count, "bootstrap", THREAD);
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
    } while (first_round && qsize == 0);
    first_round = false;
    if (PrintBootstrap) {
      while (z < (_methodsCompiled / 100)) {
        ++z;
        tty->print_raw(".");
      }
    }
  } while (qsize != 0);

  if (PrintBootstrap) {
    tty->print_cr(" in " JLONG_FORMAT " ms (compiled %d methods)", os::javaTimeMillis() - start, _methodsCompiled);
  }
  _bootstrapping = false;
}

#define CHECK_ABORT THREAD); \
if (HAS_PENDING_EXCEPTION) { \
  char buf[256]; \
  jio_snprintf(buf, 256, "Uncaught exception at %s:%d", __FILE__, __LINE__); \
  JVMCICompiler::abort_on_pending_exception(PENDING_EXCEPTION, buf); \
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

  JVMCIRuntime::initialize_well_known_classes(CHECK_ABORT);

  HandleMark hm;
  Handle receiver = JVMCIRuntime::get_HotSpotJVMCIRuntime(CHECK_ABORT);

  JavaValue method_result(T_OBJECT);
  JavaCallArguments args;
  args.push_long((jlong) (address) method());
  JavaCalls::call_static(&method_result, SystemDictionary::HotSpotResolvedJavaMethodImpl_klass(),
                         vmSymbols::fromMetaspace_name(), vmSymbols::method_fromMetaspace_signature(), &args, THREAD);

  JavaValue result(T_OBJECT);
  if (!HAS_PENDING_EXCEPTION) {
    JavaCallArguments args;
    args.push_oop(receiver);
    args.push_oop((oop)method_result.get_jobject());
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

    env->set_failure("exception throw", false);
  } else {
    oop result_object = (oop) result.get_jobject();
    if (result_object != NULL) {
      oop failure_message = CompilationRequestResult::failureMessage(result_object);
      if (failure_message != NULL) {
        const char* failure_reason = java_lang_String::as_utf8_string(failure_message);
        env->set_failure(failure_reason, CompilationRequestResult::retry(result_object) != 0);
      } else {
        if (env->task()->code() == NULL) {
          env->set_failure("no nmethod produced", true);
        } else {
          env->task()->set_num_inlined_bytecodes(CompilationRequestResult::inlinedBytecodes(result_object));
          _methodsCompiled++;
        }
      }
    } else {
      assert(false, "JVMCICompiler.compileMethod should always return non-null");
    }
  }
}

/**
 * Aborts the VM due to an unexpected exception.
 */
void JVMCICompiler::abort_on_pending_exception(Handle exception, const char* message, bool dump_core) {
  Thread* THREAD = Thread::current();
  CLEAR_PENDING_EXCEPTION;

  java_lang_Throwable::java_printStackTrace(exception, THREAD);

  // Give other aborting threads to also print their stack traces.
  // This can be very useful when debugging class initialization
  // failures.
  assert(THREAD->is_Java_thread(), "compiler threads should be Java threads");
  const bool interruptible = true;
  os::sleep(THREAD, 200, interruptible);

  vm_abort(dump_core);
}

// Compilation entry point for methods
void JVMCICompiler::compile_method(ciEnv* env, ciMethod* target, int entry_bci, DirectiveSet* directive) {
  ShouldNotReachHere();
}

bool JVMCICompiler::is_trivial(Method* method) {
  if (_bootstrapping) {
    return false;
  }
  return JVMCIRuntime::treat_as_trivial(method);
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
