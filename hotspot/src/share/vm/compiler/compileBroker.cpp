/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/dependencyContext.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compileLog.hpp"
#include "compiler/compilerOracle.hpp"
#include "compiler/directivesParser.hpp"
#include "interpreter/linkResolver.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "prims/nativeLookup.hpp"
#include "prims/whitebox.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/compilationPolicy.hpp"
#include "runtime/init.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/os.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/sweeper.hpp"
#include "trace/tracing.hpp"
#include "utilities/dtrace.hpp"
#include "utilities/events.hpp"
#ifdef COMPILER1
#include "c1/c1_Compiler.hpp"
#endif
#if INCLUDE_JVMCI
#include "jvmci/jvmciCompiler.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "runtime/vframe.hpp"
#endif
#ifdef COMPILER2
#include "opto/c2compiler.hpp"
#endif
#ifdef SHARK
#include "shark/sharkCompiler.hpp"
#endif

#ifdef DTRACE_ENABLED

// Only bother with this argument setup if dtrace is available

#define DTRACE_METHOD_COMPILE_BEGIN_PROBE(method, comp_name)             \
  {                                                                      \
    Symbol* klass_name = (method)->klass_name();                         \
    Symbol* name = (method)->name();                                     \
    Symbol* signature = (method)->signature();                           \
    HOTSPOT_METHOD_COMPILE_BEGIN(                                        \
      (char *) comp_name, strlen(comp_name),                             \
      (char *) klass_name->bytes(), klass_name->utf8_length(),           \
      (char *) name->bytes(), name->utf8_length(),                       \
      (char *) signature->bytes(), signature->utf8_length());            \
  }

#define DTRACE_METHOD_COMPILE_END_PROBE(method, comp_name, success)      \
  {                                                                      \
    Symbol* klass_name = (method)->klass_name();                         \
    Symbol* name = (method)->name();                                     \
    Symbol* signature = (method)->signature();                           \
    HOTSPOT_METHOD_COMPILE_END(                                          \
      (char *) comp_name, strlen(comp_name),                             \
      (char *) klass_name->bytes(), klass_name->utf8_length(),           \
      (char *) name->bytes(), name->utf8_length(),                       \
      (char *) signature->bytes(), signature->utf8_length(), (success)); \
  }

#else //  ndef DTRACE_ENABLED

#define DTRACE_METHOD_COMPILE_BEGIN_PROBE(method, comp_name)
#define DTRACE_METHOD_COMPILE_END_PROBE(method, comp_name, success)

#endif // ndef DTRACE_ENABLED

bool CompileBroker::_initialized = false;
volatile bool CompileBroker::_should_block = false;
volatile jint CompileBroker::_print_compilation_warning = 0;
volatile jint CompileBroker::_should_compile_new_jobs = run_compilation;

// The installed compiler(s)
AbstractCompiler* CompileBroker::_compilers[2];

// These counters are used to assign an unique ID to each compilation.
volatile jint CompileBroker::_compilation_id     = 0;
volatile jint CompileBroker::_osr_compilation_id = 0;

// Debugging information
int  CompileBroker::_last_compile_type     = no_compile;
int  CompileBroker::_last_compile_level    = CompLevel_none;
char CompileBroker::_last_method_compiled[CompileBroker::name_buffer_length];

// Performance counters
PerfCounter* CompileBroker::_perf_total_compilation = NULL;
PerfCounter* CompileBroker::_perf_osr_compilation = NULL;
PerfCounter* CompileBroker::_perf_standard_compilation = NULL;

PerfCounter* CompileBroker::_perf_total_bailout_count = NULL;
PerfCounter* CompileBroker::_perf_total_invalidated_count = NULL;
PerfCounter* CompileBroker::_perf_total_compile_count = NULL;
PerfCounter* CompileBroker::_perf_total_osr_compile_count = NULL;
PerfCounter* CompileBroker::_perf_total_standard_compile_count = NULL;

PerfCounter* CompileBroker::_perf_sum_osr_bytes_compiled = NULL;
PerfCounter* CompileBroker::_perf_sum_standard_bytes_compiled = NULL;
PerfCounter* CompileBroker::_perf_sum_nmethod_size = NULL;
PerfCounter* CompileBroker::_perf_sum_nmethod_code_size = NULL;

PerfStringVariable* CompileBroker::_perf_last_method = NULL;
PerfStringVariable* CompileBroker::_perf_last_failed_method = NULL;
PerfStringVariable* CompileBroker::_perf_last_invalidated_method = NULL;
PerfVariable*       CompileBroker::_perf_last_compile_type = NULL;
PerfVariable*       CompileBroker::_perf_last_compile_size = NULL;
PerfVariable*       CompileBroker::_perf_last_failed_type = NULL;
PerfVariable*       CompileBroker::_perf_last_invalidated_type = NULL;

// Timers and counters for generating statistics
elapsedTimer CompileBroker::_t_total_compilation;
elapsedTimer CompileBroker::_t_osr_compilation;
elapsedTimer CompileBroker::_t_standard_compilation;
elapsedTimer CompileBroker::_t_invalidated_compilation;
elapsedTimer CompileBroker::_t_bailedout_compilation;

int CompileBroker::_total_bailout_count          = 0;
int CompileBroker::_total_invalidated_count      = 0;
int CompileBroker::_total_compile_count          = 0;
int CompileBroker::_total_osr_compile_count      = 0;
int CompileBroker::_total_standard_compile_count = 0;

int CompileBroker::_sum_osr_bytes_compiled       = 0;
int CompileBroker::_sum_standard_bytes_compiled  = 0;
int CompileBroker::_sum_nmethod_size             = 0;
int CompileBroker::_sum_nmethod_code_size        = 0;

long CompileBroker::_peak_compilation_time       = 0;

CompileQueue* CompileBroker::_c2_compile_queue   = NULL;
CompileQueue* CompileBroker::_c1_compile_queue   = NULL;

class CompilationLog : public StringEventLog {
 public:
  CompilationLog() : StringEventLog("Compilation events") {
  }

  void log_compile(JavaThread* thread, CompileTask* task) {
    StringLogMessage lm;
    stringStream sstr = lm.stream();
    // msg.time_stamp().update_to(tty->time_stamp().ticks());
    task->print(&sstr, NULL, true, false);
    log(thread, "%s", (const char*)lm);
  }

  void log_nmethod(JavaThread* thread, nmethod* nm) {
    log(thread, "nmethod %d%s " INTPTR_FORMAT " code [" INTPTR_FORMAT ", " INTPTR_FORMAT "]",
        nm->compile_id(), nm->is_osr_method() ? "%" : "",
        p2i(nm), p2i(nm->code_begin()), p2i(nm->code_end()));
  }

  void log_failure(JavaThread* thread, CompileTask* task, const char* reason, const char* retry_message) {
    StringLogMessage lm;
    lm.print("%4d   COMPILE SKIPPED: %s", task->compile_id(), reason);
    if (retry_message != NULL) {
      lm.append(" (%s)", retry_message);
    }
    lm.print("\n");
    log(thread, "%s", (const char*)lm);
  }

  void log_metaspace_failure(const char* reason) {
    ResourceMark rm;
    StringLogMessage lm;
    lm.print("%4d   COMPILE PROFILING SKIPPED: %s", -1, reason);
    lm.print("\n");
    log(JavaThread::current(), "%s", (const char*)lm);
  }
};

static CompilationLog* _compilation_log = NULL;

bool compileBroker_init() {
  if (LogEvents) {
    _compilation_log = new CompilationLog();
  }

  // init directives stack, adding default directive
  DirectivesStack::init();

  if (DirectivesParser::has_file()) {
    return DirectivesParser::parse_from_flag();
  } else if (PrintCompilerDirectives) {
    // Print default directive even when no other was added
    DirectivesStack::print(tty);
  }

  return true;
}

CompileTaskWrapper::CompileTaskWrapper(CompileTask* task) {
  CompilerThread* thread = CompilerThread::current();
  thread->set_task(task);
  CompileLog*     log  = thread->log();
  if (log != NULL)  task->log_task_start(log);
}

CompileTaskWrapper::~CompileTaskWrapper() {
  CompilerThread* thread = CompilerThread::current();
  CompileTask* task = thread->task();
  CompileLog*  log  = thread->log();
  if (log != NULL)  task->log_task_done(log);
  thread->set_task(NULL);
  task->set_code_handle(NULL);
  thread->set_env(NULL);
  if (task->is_blocking()) {
    bool free_task = false;
    {
      MutexLocker notifier(task->lock(), thread);
      task->mark_complete();
#if INCLUDE_JVMCI
      if (CompileBroker::compiler(task->comp_level())->is_jvmci() &&
        !task->has_waiter()) {
        // The waiting thread timed out and thus did not free the task.
        free_task = true;
      }
#endif
      if (!free_task) {
        // Notify the waiting thread that the compilation has completed
        // so that it can free the task.
        task->lock()->notify_all();
      }
    }
    if (free_task) {
      // The task can only be freed once the task lock is released.
      CompileTask::free(task);
    }
  } else {
    task->mark_complete();

    // By convention, the compiling thread is responsible for
    // recycling a non-blocking CompileTask.
    CompileTask::free(task);
  }
}

/**
 * Add a CompileTask to a CompileQueue.
 */
void CompileQueue::add(CompileTask* task) {
  assert(MethodCompileQueue_lock->owned_by_self(), "must own lock");

  task->set_next(NULL);
  task->set_prev(NULL);

  if (_last == NULL) {
    // The compile queue is empty.
    assert(_first == NULL, "queue is empty");
    _first = task;
    _last = task;
  } else {
    // Append the task to the queue.
    assert(_last->next() == NULL, "not last");
    _last->set_next(task);
    task->set_prev(_last);
    _last = task;
  }
  ++_size;

  // Mark the method as being in the compile queue.
  task->method()->set_queued_for_compilation();

  if (CIPrintCompileQueue) {
    print_tty();
  }

  if (LogCompilation && xtty != NULL) {
    task->log_task_queued();
  }

  // Notify CompilerThreads that a task is available.
  MethodCompileQueue_lock->notify_all();
}

/**
 * Empties compilation queue by putting all compilation tasks onto
 * a freelist. Furthermore, the method wakes up all threads that are
 * waiting on a compilation task to finish. This can happen if background
 * compilation is disabled.
 */
void CompileQueue::free_all() {
  MutexLocker mu(MethodCompileQueue_lock);
  CompileTask* next = _first;

  // Iterate over all tasks in the compile queue
  while (next != NULL) {
    CompileTask* current = next;
    next = current->next();
    {
      // Wake up thread that blocks on the compile task.
      MutexLocker ct_lock(current->lock());
      current->lock()->notify();
    }
    // Put the task back on the freelist.
    CompileTask::free(current);
  }
  _first = NULL;

  // Wake up all threads that block on the queue.
  MethodCompileQueue_lock->notify_all();
}

/**
 * Get the next CompileTask from a CompileQueue
 */
CompileTask* CompileQueue::get() {
  // save methods from RedefineClasses across safepoint
  // across MethodCompileQueue_lock below.
  methodHandle save_method;
  methodHandle save_hot_method;

  MutexLocker locker(MethodCompileQueue_lock);
  // If _first is NULL we have no more compile jobs. There are two reasons for
  // having no compile jobs: First, we compiled everything we wanted. Second,
  // we ran out of code cache so compilation has been disabled. In the latter
  // case we perform code cache sweeps to free memory such that we can re-enable
  // compilation.
  while (_first == NULL) {
    // Exit loop if compilation is disabled forever
    if (CompileBroker::is_compilation_disabled_forever()) {
      return NULL;
    }

    // If there are no compilation tasks and we can compile new jobs
    // (i.e., there is enough free space in the code cache) there is
    // no need to invoke the sweeper. As a result, the hotness of methods
    // remains unchanged. This behavior is desired, since we want to keep
    // the stable state, i.e., we do not want to evict methods from the
    // code cache if it is unnecessary.
    // We need a timed wait here, since compiler threads can exit if compilation
    // is disabled forever. We use 5 seconds wait time; the exiting of compiler threads
    // is not critical and we do not want idle compiler threads to wake up too often.
    MethodCompileQueue_lock->wait(!Mutex::_no_safepoint_check_flag, 5*1000);
  }

  if (CompileBroker::is_compilation_disabled_forever()) {
    return NULL;
  }

  CompileTask* task;
  {
    NoSafepointVerifier nsv;
    task = CompilationPolicy::policy()->select_task(this);
  }

  // Save method pointers across unlock safepoint.  The task is removed from
  // the compilation queue, which is walked during RedefineClasses.
  save_method = methodHandle(task->method());
  save_hot_method = methodHandle(task->hot_method());

  remove(task);
  purge_stale_tasks(); // may temporarily release MCQ lock
  return task;
}

// Clean & deallocate stale compile tasks.
// Temporarily releases MethodCompileQueue lock.
void CompileQueue::purge_stale_tasks() {
  assert(MethodCompileQueue_lock->owned_by_self(), "must own lock");
  if (_first_stale != NULL) {
    // Stale tasks are purged when MCQ lock is released,
    // but _first_stale updates are protected by MCQ lock.
    // Once task processing starts and MCQ lock is released,
    // other compiler threads can reuse _first_stale.
    CompileTask* head = _first_stale;
    _first_stale = NULL;
    {
      MutexUnlocker ul(MethodCompileQueue_lock);
      for (CompileTask* task = head; task != NULL; ) {
        CompileTask* next_task = task->next();
        CompileTaskWrapper ctw(task); // Frees the task
        task->set_failure_reason("stale task");
        task = next_task;
      }
    }
  }
}

void CompileQueue::remove(CompileTask* task) {
   assert(MethodCompileQueue_lock->owned_by_self(), "must own lock");
  if (task->prev() != NULL) {
    task->prev()->set_next(task->next());
  } else {
    // max is the first element
    assert(task == _first, "Sanity");
    _first = task->next();
  }

  if (task->next() != NULL) {
    task->next()->set_prev(task->prev());
  } else {
    // max is the last element
    assert(task == _last, "Sanity");
    _last = task->prev();
  }
  --_size;
}

void CompileQueue::remove_and_mark_stale(CompileTask* task) {
  assert(MethodCompileQueue_lock->owned_by_self(), "must own lock");
  remove(task);

  // Enqueue the task for reclamation (should be done outside MCQ lock)
  task->set_next(_first_stale);
  task->set_prev(NULL);
  _first_stale = task;
}

// methods in the compile queue need to be marked as used on the stack
// so that they don't get reclaimed by Redefine Classes
void CompileQueue::mark_on_stack() {
  CompileTask* task = _first;
  while (task != NULL) {
    task->mark_on_stack();
    task = task->next();
  }
}


CompileQueue* CompileBroker::compile_queue(int comp_level) {
  if (is_c2_compile(comp_level)) return _c2_compile_queue;
  if (is_c1_compile(comp_level)) return _c1_compile_queue;
  return NULL;
}


void CompileBroker::print_compile_queues(outputStream* st) {
  MutexLocker locker(MethodCompileQueue_lock);
  if (_c1_compile_queue != NULL) {
    _c1_compile_queue->print(st);
  }
  if (_c2_compile_queue != NULL) {
    _c2_compile_queue->print(st);
  }
}

void CompileQueue::print(outputStream* st) {
  assert(MethodCompileQueue_lock->owned_by_self(), "must own lock");
  st->print_cr("Contents of %s", name());
  st->print_cr("----------------------------");
  CompileTask* task = _first;
  if (task == NULL) {
    st->print_cr("Empty");
  } else {
    while (task != NULL) {
      task->print(st, NULL, true, true);
      task = task->next();
    }
  }
  st->print_cr("----------------------------");
}

void CompileQueue::print_tty() {
  ttyLocker ttyl;
  print(tty);
}

CompilerCounters::CompilerCounters() {
  _current_method[0] = '\0';
  _compile_type = CompileBroker::no_compile;
}

// ------------------------------------------------------------------
// CompileBroker::compilation_init
//
// Initialize the Compilation object
void CompileBroker::compilation_init() {
  _last_method_compiled[0] = '\0';

  // No need to initialize compilation system if we do not use it.
  if (!UseCompiler) {
    return;
  }
#ifndef SHARK
  // Set the interface to the current compiler(s).
  int c1_count = CompilationPolicy::policy()->compiler_count(CompLevel_simple);
  int c2_count = CompilationPolicy::policy()->compiler_count(CompLevel_full_optimization);

#if INCLUDE_JVMCI
  if (EnableJVMCI) {
    // This is creating a JVMCICompiler singleton.
    JVMCICompiler* jvmci = new JVMCICompiler();

    if (UseJVMCICompiler) {
      _compilers[1] = jvmci;
      if (FLAG_IS_DEFAULT(JVMCIThreads)) {
        if (BootstrapJVMCI) {
          // JVMCI will bootstrap so give it more threads
          c2_count = MIN2(32, os::active_processor_count());
        }
      } else {
        c2_count = JVMCIThreads;
      }
      if (FLAG_IS_DEFAULT(JVMCIHostThreads)) {
      } else {
        c1_count = JVMCIHostThreads;
      }
    }
  }
#endif // INCLUDE_JVMCI

#ifdef COMPILER1
  if (c1_count > 0) {
    _compilers[0] = new Compiler();
  }
#endif // COMPILER1

#ifdef COMPILER2
  if (true JVMCI_ONLY( && !UseJVMCICompiler)) {
    if (c2_count > 0) {
      _compilers[1] = new C2Compiler();
    }
  }
#endif // COMPILER2

#else // SHARK
  int c1_count = 0;
  int c2_count = 1;

  _compilers[1] = new SharkCompiler();
#endif // SHARK

  // Start the compiler thread(s) and the sweeper thread
  init_compiler_sweeper_threads(c1_count, c2_count);
  // totalTime performance counter is always created as it is required
  // by the implementation of java.lang.management.CompilationMBean.
  {
    EXCEPTION_MARK;
    _perf_total_compilation =
                 PerfDataManager::create_counter(JAVA_CI, "totalTime",
                                                 PerfData::U_Ticks, CHECK);
  }

  if (UsePerfData) {

    EXCEPTION_MARK;

    // create the jvmstat performance counters
    _perf_osr_compilation =
                 PerfDataManager::create_counter(SUN_CI, "osrTime",
                                                 PerfData::U_Ticks, CHECK);

    _perf_standard_compilation =
                 PerfDataManager::create_counter(SUN_CI, "standardTime",
                                                 PerfData::U_Ticks, CHECK);

    _perf_total_bailout_count =
                 PerfDataManager::create_counter(SUN_CI, "totalBailouts",
                                                 PerfData::U_Events, CHECK);

    _perf_total_invalidated_count =
                 PerfDataManager::create_counter(SUN_CI, "totalInvalidates",
                                                 PerfData::U_Events, CHECK);

    _perf_total_compile_count =
                 PerfDataManager::create_counter(SUN_CI, "totalCompiles",
                                                 PerfData::U_Events, CHECK);
    _perf_total_osr_compile_count =
                 PerfDataManager::create_counter(SUN_CI, "osrCompiles",
                                                 PerfData::U_Events, CHECK);

    _perf_total_standard_compile_count =
                 PerfDataManager::create_counter(SUN_CI, "standardCompiles",
                                                 PerfData::U_Events, CHECK);

    _perf_sum_osr_bytes_compiled =
                 PerfDataManager::create_counter(SUN_CI, "osrBytes",
                                                 PerfData::U_Bytes, CHECK);

    _perf_sum_standard_bytes_compiled =
                 PerfDataManager::create_counter(SUN_CI, "standardBytes",
                                                 PerfData::U_Bytes, CHECK);

    _perf_sum_nmethod_size =
                 PerfDataManager::create_counter(SUN_CI, "nmethodSize",
                                                 PerfData::U_Bytes, CHECK);

    _perf_sum_nmethod_code_size =
                 PerfDataManager::create_counter(SUN_CI, "nmethodCodeSize",
                                                 PerfData::U_Bytes, CHECK);

    _perf_last_method =
                 PerfDataManager::create_string_variable(SUN_CI, "lastMethod",
                                       CompilerCounters::cmname_buffer_length,
                                       "", CHECK);

    _perf_last_failed_method =
            PerfDataManager::create_string_variable(SUN_CI, "lastFailedMethod",
                                       CompilerCounters::cmname_buffer_length,
                                       "", CHECK);

    _perf_last_invalidated_method =
        PerfDataManager::create_string_variable(SUN_CI, "lastInvalidatedMethod",
                                     CompilerCounters::cmname_buffer_length,
                                     "", CHECK);

    _perf_last_compile_type =
             PerfDataManager::create_variable(SUN_CI, "lastType",
                                              PerfData::U_None,
                                              (jlong)CompileBroker::no_compile,
                                              CHECK);

    _perf_last_compile_size =
             PerfDataManager::create_variable(SUN_CI, "lastSize",
                                              PerfData::U_Bytes,
                                              (jlong)CompileBroker::no_compile,
                                              CHECK);


    _perf_last_failed_type =
             PerfDataManager::create_variable(SUN_CI, "lastFailedType",
                                              PerfData::U_None,
                                              (jlong)CompileBroker::no_compile,
                                              CHECK);

    _perf_last_invalidated_type =
         PerfDataManager::create_variable(SUN_CI, "lastInvalidatedType",
                                          PerfData::U_None,
                                          (jlong)CompileBroker::no_compile,
                                          CHECK);
  }

  _initialized = true;
}


JavaThread* CompileBroker::make_thread(const char* name, CompileQueue* queue, CompilerCounters* counters,
                                       AbstractCompiler* comp, bool compiler_thread, TRAPS) {
  JavaThread* thread = NULL;
  Klass* k = SystemDictionary::resolve_or_fail(vmSymbols::java_lang_Thread(), true, CHECK_0);
  instanceKlassHandle klass (THREAD, k);
  instanceHandle thread_oop = klass->allocate_instance_handle(CHECK_0);
  Handle string = java_lang_String::create_from_str(name, CHECK_0);

  // Initialize thread_oop to put it into the system threadGroup
  Handle thread_group (THREAD,  Universe::system_thread_group());
  JavaValue result(T_VOID);
  JavaCalls::call_special(&result, thread_oop,
                       klass,
                       vmSymbols::object_initializer_name(),
                       vmSymbols::threadgroup_string_void_signature(),
                       thread_group,
                       string,
                       CHECK_0);

  {
    MutexLocker mu(Threads_lock, THREAD);
    if (compiler_thread) {
      thread = new CompilerThread(queue, counters);
    } else {
      thread = new CodeCacheSweeperThread();
    }
    // At this point the new CompilerThread data-races with this startup
    // thread (which I believe is the primoridal thread and NOT the VM
    // thread).  This means Java bytecodes being executed at startup can
    // queue compile jobs which will run at whatever default priority the
    // newly created CompilerThread runs at.


    // At this point it may be possible that no osthread was created for the
    // JavaThread due to lack of memory. We would have to throw an exception
    // in that case. However, since this must work and we do not allow
    // exceptions anyway, check and abort if this fails.

    if (thread == NULL || thread->osthread() == NULL) {
      vm_exit_during_initialization("java.lang.OutOfMemoryError",
                                    os::native_thread_creation_failed_msg());
    }

    java_lang_Thread::set_thread(thread_oop(), thread);

    // Note that this only sets the JavaThread _priority field, which by
    // definition is limited to Java priorities and not OS priorities.
    // The os-priority is set in the CompilerThread startup code itself

    java_lang_Thread::set_priority(thread_oop(), NearMaxPriority);

    // Note that we cannot call os::set_priority because it expects Java
    // priorities and we are *explicitly* using OS priorities so that it's
    // possible to set the compiler thread priority higher than any Java
    // thread.

    int native_prio = CompilerThreadPriority;
    if (native_prio == -1) {
      if (UseCriticalCompilerThreadPriority) {
        native_prio = os::java_to_os_priority[CriticalPriority];
      } else {
        native_prio = os::java_to_os_priority[NearMaxPriority];
      }
    }
    os::set_native_priority(thread, native_prio);

    java_lang_Thread::set_daemon(thread_oop());

    thread->set_threadObj(thread_oop());
    if (compiler_thread) {
      thread->as_CompilerThread()->set_compiler(comp);
    }
    Threads::add(thread);
    Thread::start(thread);
  }

  // Let go of Threads_lock before yielding
  os::naked_yield(); // make sure that the compiler thread is started early (especially helpful on SOLARIS)

  return thread;
}


void CompileBroker::init_compiler_sweeper_threads(int c1_compiler_count, int c2_compiler_count) {
  EXCEPTION_MARK;
#if !defined(ZERO) && !defined(SHARK)
  assert(c2_compiler_count > 0 || c1_compiler_count > 0, "No compilers?");
#endif // !ZERO && !SHARK
  // Initialize the compilation queue
  if (c2_compiler_count > 0) {
    _c2_compile_queue  = new CompileQueue("C2 compile queue");
    _compilers[1]->set_num_compiler_threads(c2_compiler_count);
  }
  if (c1_compiler_count > 0) {
    _c1_compile_queue  = new CompileQueue("C1 compile queue");
    _compilers[0]->set_num_compiler_threads(c1_compiler_count);
  }

  int compiler_count = c1_compiler_count + c2_compiler_count;

  char name_buffer[256];
  const bool compiler_thread = true;
  for (int i = 0; i < c2_compiler_count; i++) {
    // Create a name for our thread.
    sprintf(name_buffer, "%s CompilerThread%d", _compilers[1]->name(), i);
    CompilerCounters* counters = new CompilerCounters();
    // Shark and C2
    make_thread(name_buffer, _c2_compile_queue, counters, _compilers[1], compiler_thread, CHECK);
  }

  for (int i = c2_compiler_count; i < compiler_count; i++) {
    // Create a name for our thread.
    sprintf(name_buffer, "C1 CompilerThread%d", i);
    CompilerCounters* counters = new CompilerCounters();
    // C1
    make_thread(name_buffer, _c1_compile_queue, counters, _compilers[0], compiler_thread, CHECK);
  }

  if (UsePerfData) {
    PerfDataManager::create_constant(SUN_CI, "threads", PerfData::U_Bytes, compiler_count, CHECK);
  }

  if (MethodFlushing) {
    // Initialize the sweeper thread
    make_thread("Sweeper thread", NULL, NULL, NULL, false, CHECK);
  }
}


/**
 * Set the methods on the stack as on_stack so that redefine classes doesn't
 * reclaim them. This method is executed at a safepoint.
 */
void CompileBroker::mark_on_stack() {
  assert(SafepointSynchronize::is_at_safepoint(), "sanity check");
  // Since we are at a safepoint, we do not need a lock to access
  // the compile queues.
  if (_c2_compile_queue != NULL) {
    _c2_compile_queue->mark_on_stack();
  }
  if (_c1_compile_queue != NULL) {
    _c1_compile_queue->mark_on_stack();
  }
}

// ------------------------------------------------------------------
// CompileBroker::compile_method
//
// Request compilation of a method.
void CompileBroker::compile_method_base(const methodHandle& method,
                                        int osr_bci,
                                        int comp_level,
                                        const methodHandle& hot_method,
                                        int hot_count,
                                        const char* comment,
                                        Thread* thread) {
  // do nothing if compiler thread(s) is not available
  if (!_initialized) {
    return;
  }

  guarantee(!method->is_abstract(), "cannot compile abstract methods");
  assert(method->method_holder()->is_instance_klass(),
         "sanity check");
  assert(!method->method_holder()->is_not_initialized(),
         "method holder must be initialized");
  assert(!method->is_method_handle_intrinsic(), "do not enqueue these guys");

  if (CIPrintRequests) {
    tty->print("request: ");
    method->print_short_name(tty);
    if (osr_bci != InvocationEntryBci) {
      tty->print(" osr_bci: %d", osr_bci);
    }
    tty->print(" level: %d comment: %s count: %d", comp_level, comment, hot_count);
    if (!hot_method.is_null()) {
      tty->print(" hot: ");
      if (hot_method() != method()) {
          hot_method->print_short_name(tty);
      } else {
        tty->print("yes");
      }
    }
    tty->cr();
  }

  // A request has been made for compilation.  Before we do any
  // real work, check to see if the method has been compiled
  // in the meantime with a definitive result.
  if (compilation_is_complete(method, osr_bci, comp_level)) {
    return;
  }

#ifndef PRODUCT
  if (osr_bci != -1 && !FLAG_IS_DEFAULT(OSROnlyBCI)) {
    if ((OSROnlyBCI > 0) ? (OSROnlyBCI != osr_bci) : (-OSROnlyBCI == osr_bci)) {
      // Positive OSROnlyBCI means only compile that bci.  Negative means don't compile that BCI.
      return;
    }
  }
#endif

  // If this method is already in the compile queue, then
  // we do not block the current thread.
  if (compilation_is_in_queue(method)) {
    // We may want to decay our counter a bit here to prevent
    // multiple denied requests for compilation.  This is an
    // open compilation policy issue. Note: The other possibility,
    // in the case that this is a blocking compile request, is to have
    // all subsequent blocking requesters wait for completion of
    // ongoing compiles. Note that in this case we'll need a protocol
    // for freeing the associated compile tasks. [Or we could have
    // a single static monitor on which all these waiters sleep.]
    return;
  }

  // If the requesting thread is holding the pending list lock
  // then we just return. We can't risk blocking while holding
  // the pending list lock or a 3-way deadlock may occur
  // between the reference handler thread, a GC (instigated
  // by a compiler thread), and compiled method registration.
  if (InstanceRefKlass::owns_pending_list_lock(JavaThread::current())) {
    return;
  }

  if (TieredCompilation) {
    // Tiered policy requires MethodCounters to exist before adding a method to
    // the queue. Create if we don't have them yet.
    method->get_method_counters(thread);
  }

  // Outputs from the following MutexLocker block:
  CompileTask* task     = NULL;
  bool         blocking = false;
  CompileQueue* queue  = compile_queue(comp_level);

  // Acquire our lock.
  {
    MutexLocker locker(MethodCompileQueue_lock, thread);

    // Make sure the method has not slipped into the queues since
    // last we checked; note that those checks were "fast bail-outs".
    // Here we need to be more careful, see 14012000 below.
    if (compilation_is_in_queue(method)) {
      return;
    }

    // We need to check again to see if the compilation has
    // completed.  A previous compilation may have registered
    // some result.
    if (compilation_is_complete(method, osr_bci, comp_level)) {
      return;
    }

    // We now know that this compilation is not pending, complete,
    // or prohibited.  Assign a compile_id to this compilation
    // and check to see if it is in our [Start..Stop) range.
    int compile_id = assign_compile_id(method, osr_bci);
    if (compile_id == 0) {
      // The compilation falls outside the allowed range.
      return;
    }

    // Should this thread wait for completion of the compile?
    blocking = is_compile_blocking();

#if INCLUDE_JVMCI
    if (UseJVMCICompiler) {
      if (blocking) {
        // Don't allow blocking compiles for requests triggered by JVMCI.
        if (thread->is_Compiler_thread()) {
          blocking = false;
        }

        // Don't allow blocking compiles if inside a class initializer or while performing class loading
        vframeStream vfst((JavaThread*) thread);
        for (; !vfst.at_end(); vfst.next()) {
          if (vfst.method()->is_static_initializer() ||
              (vfst.method()->method_holder()->is_subclass_of(SystemDictionary::ClassLoader_klass()) &&
                  vfst.method()->name() == vmSymbols::loadClass_name())) {
            blocking = false;
            break;
          }
        }

        // Don't allow blocking compilation requests to JVMCI
        // if JVMCI itself is not yet initialized
        if (!JVMCIRuntime::is_HotSpotJVMCIRuntime_initialized() && compiler(comp_level)->is_jvmci()) {
          blocking = false;
        }

        // Don't allow blocking compilation requests if we are in JVMCIRuntime::shutdown
        // to avoid deadlock between compiler thread(s) and threads run at shutdown
        // such as the DestroyJavaVM thread.
        if (JVMCIRuntime::shutdown_called()) {
          blocking = false;
        }
      }
    }
#endif // INCLUDE_JVMCI

    // We will enter the compilation in the queue.
    // 14012000: Note that this sets the queued_for_compile bits in
    // the target method. We can now reason that a method cannot be
    // queued for compilation more than once, as follows:
    // Before a thread queues a task for compilation, it first acquires
    // the compile queue lock, then checks if the method's queued bits
    // are set or it has already been compiled. Thus there can not be two
    // instances of a compilation task for the same method on the
    // compilation queue. Consider now the case where the compilation
    // thread has already removed a task for that method from the queue
    // and is in the midst of compiling it. In this case, the
    // queued_for_compile bits must be set in the method (and these
    // will be visible to the current thread, since the bits were set
    // under protection of the compile queue lock, which we hold now.
    // When the compilation completes, the compiler thread first sets
    // the compilation result and then clears the queued_for_compile
    // bits. Neither of these actions are protected by a barrier (or done
    // under the protection of a lock), so the only guarantee we have
    // (on machines with TSO (Total Store Order)) is that these values
    // will update in that order. As a result, the only combinations of
    // these bits that the current thread will see are, in temporal order:
    // <RESULT, QUEUE> :
    //     <0, 1> : in compile queue, but not yet compiled
    //     <1, 1> : compiled but queue bit not cleared
    //     <1, 0> : compiled and queue bit cleared
    // Because we first check the queue bits then check the result bits,
    // we are assured that we cannot introduce a duplicate task.
    // Note that if we did the tests in the reverse order (i.e. check
    // result then check queued bit), we could get the result bit before
    // the compilation completed, and the queue bit after the compilation
    // completed, and end up introducing a "duplicate" (redundant) task.
    // In that case, the compiler thread should first check if a method
    // has already been compiled before trying to compile it.
    // NOTE: in the event that there are multiple compiler threads and
    // there is de-optimization/recompilation, things will get hairy,
    // and in that case it's best to protect both the testing (here) of
    // these bits, and their updating (here and elsewhere) under a
    // common lock.
    task = create_compile_task(queue,
                               compile_id, method,
                               osr_bci, comp_level,
                               hot_method, hot_count, comment,
                               blocking);
  }

  if (blocking) {
    wait_for_completion(task);
  }
}


nmethod* CompileBroker::compile_method(const methodHandle& method, int osr_bci,
                                       int comp_level,
                                       const methodHandle& hot_method, int hot_count,
                                       const char* comment, Thread* THREAD) {
  // make sure arguments make sense
  assert(method->method_holder()->is_instance_klass(), "not an instance method");
  assert(osr_bci == InvocationEntryBci || (0 <= osr_bci && osr_bci < method->code_size()), "bci out of range");
  assert(!method->is_abstract() && (osr_bci == InvocationEntryBci || !method->is_native()), "cannot compile abstract/native methods");
  assert(!method->method_holder()->is_not_initialized(), "method holder must be initialized");
  // allow any levels for WhiteBox
  assert(WhiteBoxAPI || TieredCompilation || comp_level == CompLevel_highest_tier, "only CompLevel_highest_tier must be used in non-tiered");
  // return quickly if possible

  // lock, make sure that the compilation
  // isn't prohibited in a straightforward way.
  AbstractCompiler *comp = CompileBroker::compiler(comp_level);
  if (comp == NULL || !comp->can_compile_method(method) ||
      compilation_is_prohibited(method, osr_bci, comp_level)) {
    return NULL;
  }

  if (osr_bci == InvocationEntryBci) {
    // standard compilation
    nmethod* method_code = method->code();
    if (method_code != NULL) {
      if (compilation_is_complete(method, osr_bci, comp_level)) {
        return method_code;
      }
    }
    if (method->is_not_compilable(comp_level)) {
      return NULL;
    }
  } else {
    // osr compilation
#ifndef TIERED
    // seems like an assert of dubious value
    assert(comp_level == CompLevel_highest_tier,
           "all OSR compiles are assumed to be at a single compilation level");
#endif // TIERED
    // We accept a higher level osr method
    nmethod* nm = method->lookup_osr_nmethod_for(osr_bci, comp_level, false);
    if (nm != NULL) return nm;
    if (method->is_not_osr_compilable(comp_level)) return NULL;
  }

  assert(!HAS_PENDING_EXCEPTION, "No exception should be present");
  // some prerequisites that are compiler specific
  if (comp->is_c2() || comp->is_shark()) {
    method->constants()->resolve_string_constants(CHECK_AND_CLEAR_NULL);
    // Resolve all classes seen in the signature of the method
    // we are compiling.
    Method::load_signature_classes(method, CHECK_AND_CLEAR_NULL);
  }

  // If the method is native, do the lookup in the thread requesting
  // the compilation. Native lookups can load code, which is not
  // permitted during compilation.
  //
  // Note: A native method implies non-osr compilation which is
  //       checked with an assertion at the entry of this method.
  if (method->is_native() && !method->is_method_handle_intrinsic()) {
    bool in_base_library;
    address adr = NativeLookup::lookup(method, in_base_library, THREAD);
    if (HAS_PENDING_EXCEPTION) {
      // In case of an exception looking up the method, we just forget
      // about it. The interpreter will kick-in and throw the exception.
      method->set_not_compilable(); // implies is_not_osr_compilable()
      CLEAR_PENDING_EXCEPTION;
      return NULL;
    }
    assert(method->has_native_function(), "must have native code by now");
  }

  // RedefineClasses() has replaced this method; just return
  if (method->is_old()) {
    return NULL;
  }

  // JVMTI -- post_compile_event requires jmethod_id() that may require
  // a lock the compiling thread can not acquire. Prefetch it here.
  if (JvmtiExport::should_post_compiled_method_load()) {
    method->jmethod_id();
  }

  // do the compilation
  if (method->is_native()) {
    if (!PreferInterpreterNativeStubs || method->is_method_handle_intrinsic()) {
      // The following native methods:
      //
      // java.lang.Float.intBitsToFloat
      // java.lang.Float.floatToRawIntBits
      // java.lang.Double.longBitsToDouble
      // java.lang.Double.doubleToRawLongBits
      //
      // are called through the interpreter even if interpreter native stubs
      // are not preferred (i.e., calling through adapter handlers is preferred).
      // The reason is that on x86_32 signaling NaNs (sNaNs) are not preserved
      // if the version of the methods from the native libraries is called.
      // As the interpreter and the C2-intrinsified version of the methods preserves
      // sNaNs, that would result in an inconsistent way of handling of sNaNs.
      if ((UseSSE >= 1 &&
          (method->intrinsic_id() == vmIntrinsics::_intBitsToFloat ||
           method->intrinsic_id() == vmIntrinsics::_floatToRawIntBits)) ||
          (UseSSE >= 2 &&
           (method->intrinsic_id() == vmIntrinsics::_longBitsToDouble ||
            method->intrinsic_id() == vmIntrinsics::_doubleToRawLongBits))) {
        return NULL;
      }

      // To properly handle the appendix argument for out-of-line calls we are using a small trampoline that
      // pops off the appendix argument and jumps to the target (see gen_special_dispatch in SharedRuntime).
      //
      // Since normal compiled-to-compiled calls are not able to handle such a thing we MUST generate an adapter
      // in this case.  If we can't generate one and use it we can not execute the out-of-line method handle calls.
      AdapterHandlerLibrary::create_native_wrapper(method);
    } else {
      return NULL;
    }
  } else {
    // If the compiler is shut off due to code cache getting full
    // fail out now so blocking compiles dont hang the java thread
    if (!should_compile_new_jobs()) {
      CompilationPolicy::policy()->delay_compilation(method());
      return NULL;
    }
    compile_method_base(method, osr_bci, comp_level, hot_method, hot_count, comment, THREAD);
  }

  // return requested nmethod
  // We accept a higher level osr method
  if (osr_bci == InvocationEntryBci) {
    return method->code();
  }
  return method->lookup_osr_nmethod_for(osr_bci, comp_level, false);
}


// ------------------------------------------------------------------
// CompileBroker::compilation_is_complete
//
// See if compilation of this method is already complete.
bool CompileBroker::compilation_is_complete(const methodHandle& method,
                                            int                 osr_bci,
                                            int                 comp_level) {
  bool is_osr = (osr_bci != standard_entry_bci);
  if (is_osr) {
    if (method->is_not_osr_compilable(comp_level)) {
      return true;
    } else {
      nmethod* result = method->lookup_osr_nmethod_for(osr_bci, comp_level, true);
      return (result != NULL);
    }
  } else {
    if (method->is_not_compilable(comp_level)) {
      return true;
    } else {
      nmethod* result = method->code();
      if (result == NULL) return false;
      return comp_level == result->comp_level();
    }
  }
}


/**
 * See if this compilation is already requested.
 *
 * Implementation note: there is only a single "is in queue" bit
 * for each method.  This means that the check below is overly
 * conservative in the sense that an osr compilation in the queue
 * will block a normal compilation from entering the queue (and vice
 * versa).  This can be remedied by a full queue search to disambiguate
 * cases.  If it is deemed profitable, this may be done.
 */
bool CompileBroker::compilation_is_in_queue(const methodHandle& method) {
  return method->queued_for_compilation();
}

// ------------------------------------------------------------------
// CompileBroker::compilation_is_prohibited
//
// See if this compilation is not allowed.
bool CompileBroker::compilation_is_prohibited(const methodHandle& method, int osr_bci, int comp_level) {
  bool is_native = method->is_native();
  // Some compilers may not support the compilation of natives.
  AbstractCompiler *comp = compiler(comp_level);
  if (is_native &&
      (!CICompileNatives || comp == NULL || !comp->supports_native())) {
    method->set_not_compilable_quietly(comp_level);
    return true;
  }

  bool is_osr = (osr_bci != standard_entry_bci);
  // Some compilers may not support on stack replacement.
  if (is_osr &&
      (!CICompileOSR || comp == NULL || !comp->supports_osr())) {
    method->set_not_osr_compilable(comp_level);
    return true;
  }

  // Breaking the abstraction - directives are only used inside a compilation otherwise.
  DirectiveSet* directive = DirectivesStack::getMatchingDirective(method, comp);
  bool excluded = directive->ExcludeOption;
  DirectivesStack::release(directive);

  // The method may be explicitly excluded by the user.
  double scale;
  if (excluded || (CompilerOracle::has_option_value(method, "CompileThresholdScaling", scale) && scale == 0)) {
    bool quietly = CompilerOracle::should_exclude_quietly();
    if (PrintCompilation && !quietly) {
      // This does not happen quietly...
      ResourceMark rm;
      tty->print("### Excluding %s:%s",
                 method->is_native() ? "generation of native wrapper" : "compile",
                 (method->is_static() ? " static" : ""));
      method->print_short_name(tty);
      tty->cr();
    }
    method->set_not_compilable(comp_level, !quietly, "excluded by CompileCommand");
  }

  return false;
}

/**
 * Generate serialized IDs for compilation requests. If certain debugging flags are used
 * and the ID is not within the specified range, the method is not compiled and 0 is returned.
 * The function also allows to generate separate compilation IDs for OSR compilations.
 */
int CompileBroker::assign_compile_id(const methodHandle& method, int osr_bci) {
#ifdef ASSERT
  bool is_osr = (osr_bci != standard_entry_bci);
  int id;
  if (method->is_native()) {
    assert(!is_osr, "can't be osr");
    // Adapters, native wrappers and method handle intrinsics
    // should be generated always.
    return Atomic::add(1, &_compilation_id);
  } else if (CICountOSR && is_osr) {
    id = Atomic::add(1, &_osr_compilation_id);
    if (CIStartOSR <= id && id < CIStopOSR) {
      return id;
    }
  } else {
    id = Atomic::add(1, &_compilation_id);
    if (CIStart <= id && id < CIStop) {
      return id;
    }
  }

  // Method was not in the appropriate compilation range.
  method->set_not_compilable_quietly();
  return 0;
#else
  // CICountOSR is a develop flag and set to 'false' by default. In a product built,
  // only _compilation_id is incremented.
  return Atomic::add(1, &_compilation_id);
#endif
}

// ------------------------------------------------------------------
// CompileBroker::assign_compile_id_unlocked
//
// Public wrapper for assign_compile_id that acquires the needed locks
uint CompileBroker::assign_compile_id_unlocked(Thread* thread, const methodHandle& method, int osr_bci) {
  MutexLocker locker(MethodCompileQueue_lock, thread);
  return assign_compile_id(method, osr_bci);
}

/**
 * Should the current thread block until this compilation request
 * has been fulfilled?
 */
bool CompileBroker::is_compile_blocking() {
  assert(!InstanceRefKlass::owns_pending_list_lock(JavaThread::current()), "possible deadlock");
  return !BackgroundCompilation;
}


// ------------------------------------------------------------------
// CompileBroker::preload_classes
void CompileBroker::preload_classes(const methodHandle& method, TRAPS) {
  // Move this code over from c1_Compiler.cpp
  ShouldNotReachHere();
}


// ------------------------------------------------------------------
// CompileBroker::create_compile_task
//
// Create a CompileTask object representing the current request for
// compilation.  Add this task to the queue.
CompileTask* CompileBroker::create_compile_task(CompileQueue*       queue,
                                                int                 compile_id,
                                                const methodHandle& method,
                                                int                 osr_bci,
                                                int                 comp_level,
                                                const methodHandle& hot_method,
                                                int                 hot_count,
                                                const char*         comment,
                                                bool                blocking) {
  CompileTask* new_task = CompileTask::allocate();
  new_task->initialize(compile_id, method, osr_bci, comp_level,
                       hot_method, hot_count, comment,
                       blocking);
  queue->add(new_task);
  return new_task;
}

// 1 second should be long enough to complete most JVMCI compilations
// and not too long to stall a blocking JVMCI compilation that
// is trying to acquire a lock held by the app thread that submitted the
// compilation.
static const long BLOCKING_JVMCI_COMPILATION_TIMEOUT = 1000;

/**
 *  Wait for the compilation task to complete.
 */
void CompileBroker::wait_for_completion(CompileTask* task) {
  if (CIPrintCompileQueue) {
    ttyLocker ttyl;
    tty->print_cr("BLOCKING FOR COMPILE");
  }

  assert(task->is_blocking(), "can only wait on blocking task");

  JavaThread* thread = JavaThread::current();
  thread->set_blocked_on_compilation(true);

  methodHandle method(thread, task->method());
  bool free_task;
#if INCLUDE_JVMCI
  if (compiler(task->comp_level())->is_jvmci()) {
    MutexLocker waiter(task->lock(), thread);
    // No need to check if compilation has completed - just
    // rely on the time out. The JVMCI compiler thread will
    // recycle the CompileTask.
    task->lock()->wait(!Mutex::_no_safepoint_check_flag, BLOCKING_JVMCI_COMPILATION_TIMEOUT);
    // If the compilation completes while has_waiter is true then
    // this thread is responsible for freeing the task.  Otherwise
    // the compiler thread will free the task.
    task->clear_waiter();
    free_task = task->is_complete();
  } else
#endif
  {
    MutexLocker waiter(task->lock(), thread);
    free_task = true;
    while (!task->is_complete() && !is_compilation_disabled_forever()) {
      task->lock()->wait();
    }
  }

  thread->set_blocked_on_compilation(false);
  if (free_task) {
    if (is_compilation_disabled_forever()) {
      CompileTask::free(task);
      return;
    }

    // It is harmless to check this status without the lock, because
    // completion is a stable property (until the task object is recycled).
    assert(task->is_complete(), "Compilation should have completed");
    assert(task->code_handle() == NULL, "must be reset");

    // By convention, the waiter is responsible for recycling a
    // blocking CompileTask. Since there is only one waiter ever
    // waiting on a CompileTask, we know that no one else will
    // be using this CompileTask; we can free it.
    CompileTask::free(task);
  }
}

/**
 * Initialize compiler thread(s) + compiler object(s). The postcondition
 * of this function is that the compiler runtimes are initialized and that
 * compiler threads can start compiling.
 */
bool CompileBroker::init_compiler_runtime() {
  CompilerThread* thread = CompilerThread::current();
  AbstractCompiler* comp = thread->compiler();
  // Final sanity check - the compiler object must exist
  guarantee(comp != NULL, "Compiler object must exist");

  int system_dictionary_modification_counter;
  {
    MutexLocker locker(Compile_lock, thread);
    system_dictionary_modification_counter = SystemDictionary::number_of_modifications();
  }

  {
    // Must switch to native to allocate ci_env
    ThreadToNativeFromVM ttn(thread);
    ciEnv ci_env(NULL, system_dictionary_modification_counter);
    // Cache Jvmti state
    ci_env.cache_jvmti_state();
    // Cache DTrace flags
    ci_env.cache_dtrace_flags();

    // Switch back to VM state to do compiler initialization
    ThreadInVMfromNative tv(thread);
    ResetNoHandleMark rnhm;

    if (!comp->is_shark()) {
      // Perform per-thread and global initializations
      comp->initialize();
    }
  }

  if (comp->is_failed()) {
    disable_compilation_forever();
    // If compiler initialization failed, no compiler thread that is specific to a
    // particular compiler runtime will ever start to compile methods.
    shutdown_compiler_runtime(comp, thread);
    return false;
  }

  // C1 specific check
  if (comp->is_c1() && (thread->get_buffer_blob() == NULL)) {
    warning("Initialization of %s thread failed (no space to run compilers)", thread->name());
    return false;
  }

  return true;
}

/**
 * If C1 and/or C2 initialization failed, we shut down all compilation.
 * We do this to keep things simple. This can be changed if it ever turns
 * out to be a problem.
 */
void CompileBroker::shutdown_compiler_runtime(AbstractCompiler* comp, CompilerThread* thread) {
  // Free buffer blob, if allocated
  if (thread->get_buffer_blob() != NULL) {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    CodeCache::free(thread->get_buffer_blob());
  }

  if (comp->should_perform_shutdown()) {
    // There are two reasons for shutting down the compiler
    // 1) compiler runtime initialization failed
    // 2) The code cache is full and the following flag is set: -XX:-UseCodeCacheFlushing
    warning("%s initialization failed. Shutting down all compilers", comp->name());

    // Only one thread per compiler runtime object enters here
    // Set state to shut down
    comp->set_shut_down();

    // Delete all queued compilation tasks to make compiler threads exit faster.
    if (_c1_compile_queue != NULL) {
      _c1_compile_queue->free_all();
    }

    if (_c2_compile_queue != NULL) {
      _c2_compile_queue->free_all();
    }

    // Set flags so that we continue execution with using interpreter only.
    UseCompiler    = false;
    UseInterpreter = true;

    // We could delete compiler runtimes also. However, there are references to
    // the compiler runtime(s) (e.g.,  nmethod::is_compiled_by_c1()) which then
    // fail. This can be done later if necessary.
  }
}

// ------------------------------------------------------------------
// CompileBroker::compiler_thread_loop
//
// The main loop run by a CompilerThread.
void CompileBroker::compiler_thread_loop() {
  CompilerThread* thread = CompilerThread::current();
  CompileQueue* queue = thread->queue();
  // For the thread that initializes the ciObjectFactory
  // this resource mark holds all the shared objects
  ResourceMark rm;

  // First thread to get here will initialize the compiler interface

  if (!ciObjectFactory::is_initialized()) {
    ASSERT_IN_VM;
    MutexLocker only_one (CompileThread_lock, thread);
    if (!ciObjectFactory::is_initialized()) {
      ciObjectFactory::initialize();
    }
  }

  // Open a log.
  if (LogCompilation) {
    init_compiler_thread_log();
  }
  CompileLog* log = thread->log();
  if (log != NULL) {
    log->begin_elem("start_compile_thread name='%s' thread='" UINTX_FORMAT "' process='%d'",
                    thread->name(),
                    os::current_thread_id(),
                    os::current_process_id());
    log->stamp();
    log->end_elem();
  }

  // If compiler thread/runtime initialization fails, exit the compiler thread
  if (!init_compiler_runtime()) {
    return;
  }

  // Poll for new compilation tasks as long as the JVM runs. Compilation
  // should only be disabled if something went wrong while initializing the
  // compiler runtimes. This, in turn, should not happen. The only known case
  // when compiler runtime initialization fails is if there is not enough free
  // space in the code cache to generate the necessary stubs, etc.
  while (!is_compilation_disabled_forever()) {
    // We need this HandleMark to avoid leaking VM handles.
    HandleMark hm(thread);

    CompileTask* task = queue->get();
    if (task == NULL) {
      continue;
    }

    // Give compiler threads an extra quanta.  They tend to be bursty and
    // this helps the compiler to finish up the job.
    if (CompilerThreadHintNoPreempt) {
      os::hint_no_preempt();
    }

    // Assign the task to the current thread.  Mark this compilation
    // thread as active for the profiler.
    CompileTaskWrapper ctw(task);
    nmethodLocker result_handle;  // (handle for the nmethod produced by this task)
    task->set_code_handle(&result_handle);
    methodHandle method(thread, task->method());

    // Never compile a method if breakpoints are present in it
    if (method()->number_of_breakpoints() == 0) {
      // Compile the method.
      if ((UseCompiler || AlwaysCompileLoopMethods) && CompileBroker::should_compile_new_jobs()) {
        invoke_compiler_on_method(task);
      } else {
        // After compilation is disabled, remove remaining methods from queue
        method->clear_queued_for_compilation();
        task->set_failure_reason("compilation is disabled");
      }
    }
  }

  // Shut down compiler runtime
  shutdown_compiler_runtime(thread->compiler(), thread);
}

// ------------------------------------------------------------------
// CompileBroker::init_compiler_thread_log
//
// Set up state required by +LogCompilation.
void CompileBroker::init_compiler_thread_log() {
    CompilerThread* thread = CompilerThread::current();
    char  file_name[4*K];
    FILE* fp = NULL;
    intx thread_id = os::current_thread_id();
    for (int try_temp_dir = 1; try_temp_dir >= 0; try_temp_dir--) {
      const char* dir = (try_temp_dir ? os::get_temp_directory() : NULL);
      if (dir == NULL) {
        jio_snprintf(file_name, sizeof(file_name), "hs_c" UINTX_FORMAT "_pid%u.log",
                     thread_id, os::current_process_id());
      } else {
        jio_snprintf(file_name, sizeof(file_name),
                     "%s%shs_c" UINTX_FORMAT "_pid%u.log", dir,
                     os::file_separator(), thread_id, os::current_process_id());
      }

      fp = fopen(file_name, "wt");
      if (fp != NULL) {
        if (LogCompilation && Verbose) {
          tty->print_cr("Opening compilation log %s", file_name);
        }
        CompileLog* log = new(ResourceObj::C_HEAP, mtCompiler) CompileLog(file_name, fp, thread_id);
        thread->init_log(log);

        if (xtty != NULL) {
          ttyLocker ttyl;
          // Record any per thread log files
          xtty->elem("thread_logfile thread='" INTX_FORMAT "' filename='%s'", thread_id, file_name);
        }
        return;
      }
    }
    warning("Cannot open log file: %s", file_name);
}

void CompileBroker::log_metaspace_failure() {
  const char* message = "some methods may not be compiled because metaspace "
                        "is out of memory";
  if (_compilation_log != NULL) {
    _compilation_log->log_metaspace_failure(message);
  }
  if (PrintCompilation) {
    tty->print_cr("COMPILE PROFILING SKIPPED: %s", message);
  }
}


// ------------------------------------------------------------------
// CompileBroker::set_should_block
//
// Set _should_block.
// Call this from the VM, with Threads_lock held and a safepoint requested.
void CompileBroker::set_should_block() {
  assert(Threads_lock->owner() == Thread::current(), "must have threads lock");
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint already");
#ifndef PRODUCT
  if (PrintCompilation && (Verbose || WizardMode))
    tty->print_cr("notifying compiler thread pool to block");
#endif
  _should_block = true;
}

// ------------------------------------------------------------------
// CompileBroker::maybe_block
//
// Call this from the compiler at convenient points, to poll for _should_block.
void CompileBroker::maybe_block() {
  if (_should_block) {
#ifndef PRODUCT
    if (PrintCompilation && (Verbose || WizardMode))
      tty->print_cr("compiler thread " INTPTR_FORMAT " poll detects block request", p2i(Thread::current()));
#endif
    ThreadInVMfromNative tivfn(JavaThread::current());
  }
}

// wrapper for CodeCache::print_summary()
static void codecache_print(bool detailed)
{
  ResourceMark rm;
  stringStream s;
  // Dump code cache  into a buffer before locking the tty,
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    CodeCache::print_summary(&s, detailed);
  }
  ttyLocker ttyl;
  tty->print("%s", s.as_string());
}

void CompileBroker::post_compile(CompilerThread* thread, CompileTask* task, EventCompilation& event, bool success, ciEnv* ci_env) {

  if (success) {
    task->mark_success();
    if (ci_env != NULL) {
      task->set_num_inlined_bytecodes(ci_env->num_inlined_bytecodes());
    }
    if (_compilation_log != NULL) {
      nmethod* code = task->code();
      if (code != NULL) {
        _compilation_log->log_nmethod(thread, code);
      }
    }
  }

  // simulate crash during compilation
  assert(task->compile_id() != CICrashAt, "just as planned");
  if (event.should_commit()) {
    event.set_method(task->method());
    event.set_compileID(task->compile_id());
    event.set_compileLevel(task->comp_level());
    event.set_succeded(task->is_success());
    event.set_isOsr(task->osr_bci() != CompileBroker::standard_entry_bci);
    event.set_codeSize((task->code() == NULL) ? 0 : task->code()->total_size());
    event.set_inlinedBytes(task->num_inlined_bytecodes());
    event.commit();
  }
}

int DirectivesStack::_depth = 0;
CompilerDirectives* DirectivesStack::_top = NULL;
CompilerDirectives* DirectivesStack::_bottom = NULL;

// ------------------------------------------------------------------
// CompileBroker::invoke_compiler_on_method
//
// Compile a method.
//
void CompileBroker::invoke_compiler_on_method(CompileTask* task) {
  if (PrintCompilation) {
    ResourceMark rm;
    task->print_tty();
  }
  elapsedTimer time;

  CompilerThread* thread = CompilerThread::current();
  ResourceMark rm(thread);

  if (LogEvents) {
    _compilation_log->log_compile(thread, task);
  }

  // Common flags.
  uint compile_id = task->compile_id();
  int osr_bci = task->osr_bci();
  bool is_osr = (osr_bci != standard_entry_bci);
  bool should_log = (thread->log() != NULL);
  bool should_break = false;
  int task_level = task->comp_level();

  DirectiveSet* directive;
  {
    // create the handle inside it's own block so it can't
    // accidentally be referenced once the thread transitions to
    // native.  The NoHandleMark before the transition should catch
    // any cases where this occurs in the future.
    methodHandle method(thread, task->method());
    assert(!method->is_native(), "no longer compile natives");

    // Look up matching directives
    directive = DirectivesStack::getMatchingDirective(method, compiler(task_level));

    // Save information about this method in case of failure.
    set_last_compile(thread, method, is_osr, task_level);

    DTRACE_METHOD_COMPILE_BEGIN_PROBE(method, compiler_name(task_level));
  }

  should_break = directive->BreakAtExecuteOption || task->check_break_at_flags();
  if (should_log && !directive->LogOption) {
    should_log = false;
  }

  // Allocate a new set of JNI handles.
  push_jni_handle_block();
  Method* target_handle = task->method();
  int compilable = ciEnv::MethodCompilable;
  AbstractCompiler *comp = compiler(task_level);

  int system_dictionary_modification_counter;
  {
    MutexLocker locker(Compile_lock, thread);
    system_dictionary_modification_counter = SystemDictionary::number_of_modifications();
  }
#if INCLUDE_JVMCI
  if (UseJVMCICompiler && comp != NULL && comp->is_jvmci()) {
    JVMCICompiler* jvmci = (JVMCICompiler*) comp;

    TraceTime t1("compilation", &time);
    EventCompilation event;

    JVMCIEnv env(task, system_dictionary_modification_counter);
    methodHandle method(thread, target_handle);
    jvmci->compile_method(method, osr_bci, &env);

    post_compile(thread, task, event, task->code() != NULL, NULL);
  } else
#endif // INCLUDE_JVMCI
  {

    NoHandleMark  nhm;
    ThreadToNativeFromVM ttn(thread);

    ciEnv ci_env(task, system_dictionary_modification_counter);
    if (should_break) {
      ci_env.set_break_at_compile(true);
    }
    if (should_log) {
      ci_env.set_log(thread->log());
    }
    assert(thread->env() == &ci_env, "set by ci_env");
    // The thread-env() field is cleared in ~CompileTaskWrapper.

    // Cache Jvmti state
    ci_env.cache_jvmti_state();

    // Cache DTrace flags
    ci_env.cache_dtrace_flags();

    ciMethod* target = ci_env.get_method_from_handle(target_handle);

    TraceTime t1("compilation", &time);
    EventCompilation event;

    if (comp == NULL) {
      ci_env.record_method_not_compilable("no compiler", !TieredCompilation);
    } else {
      if (WhiteBoxAPI && WhiteBox::compilation_locked) {
        MonitorLockerEx locker(Compilation_lock, Mutex::_no_safepoint_check_flag);
        while (WhiteBox::compilation_locked) {
          locker.wait(Mutex::_no_safepoint_check_flag);
        }
      }
      comp->compile_method(&ci_env, target, osr_bci, directive);
    }

    if (!ci_env.failing() && task->code() == NULL) {
      //assert(false, "compiler should always document failure");
      // The compiler elected, without comment, not to register a result.
      // Do not attempt further compilations of this method.
      ci_env.record_method_not_compilable("compile failed", !TieredCompilation);
    }

    // Copy this bit to the enclosing block:
    compilable = ci_env.compilable();

    if (ci_env.failing()) {
      task->set_failure_reason(ci_env.failure_reason());
      ci_env.report_failure(ci_env.failure_reason());
      const char* retry_message = ci_env.retry_message();
      if (_compilation_log != NULL) {
        _compilation_log->log_failure(thread, task, ci_env.failure_reason(), retry_message);
      }
      if (PrintCompilation) {
        FormatBufferResource msg = retry_message != NULL ?
            FormatBufferResource("COMPILE SKIPPED: %s (%s)", ci_env.failure_reason(), retry_message) :
            FormatBufferResource("COMPILE SKIPPED: %s",      ci_env.failure_reason());
        task->print(tty, msg);
      }
    }

    post_compile(thread, task, event, !ci_env.failing(), &ci_env);
  }
  DirectivesStack::release(directive);
  pop_jni_handle_block();

  methodHandle method(thread, task->method());

  DTRACE_METHOD_COMPILE_END_PROBE(method, compiler_name(task_level), task->is_success());

  collect_statistics(thread, time, task);

  if (PrintCompilation && PrintCompilation2) {
    tty->print("%7d ", (int) tty->time_stamp().milliseconds());  // print timestamp
    tty->print("%4d ", compile_id);    // print compilation number
    tty->print("%s ", (is_osr ? "%" : " "));
    if (task->code() != NULL) {
      tty->print("size: %d(%d) ", task->code()->total_size(), task->code()->insts_size());
    }
    tty->print_cr("time: %d inlined: %d bytes", (int)time.milliseconds(), task->num_inlined_bytecodes());
  }

  if (PrintCodeCacheOnCompilation)
    codecache_print(/* detailed= */ false);

  // Disable compilation, if required.
  switch (compilable) {
  case ciEnv::MethodCompilable_never:
    if (is_osr)
      method->set_not_osr_compilable_quietly();
    else
      method->set_not_compilable_quietly();
    break;
  case ciEnv::MethodCompilable_not_at_tier:
    if (is_osr)
      method->set_not_osr_compilable_quietly(task_level);
    else
      method->set_not_compilable_quietly(task_level);
    break;
  }

  // Note that the queued_for_compilation bits are cleared without
  // protection of a mutex. [They were set by the requester thread,
  // when adding the task to the compile queue -- at which time the
  // compile queue lock was held. Subsequently, we acquired the compile
  // queue lock to get this task off the compile queue; thus (to belabour
  // the point somewhat) our clearing of the bits must be occurring
  // only after the setting of the bits. See also 14012000 above.
  method->clear_queued_for_compilation();

#ifdef ASSERT
  if (CollectedHeap::fired_fake_oom()) {
    // The current compile received a fake OOM during compilation so
    // go ahead and exit the VM since the test apparently succeeded
    tty->print_cr("*** Shutting down VM after successful fake OOM");
    vm_exit(0);
  }
#endif
}

/**
 * The CodeCache is full. Print warning and disable compilation.
 * Schedule code cache cleaning so compilation can continue later.
 * This function needs to be called only from CodeCache::allocate(),
 * since we currently handle a full code cache uniformly.
 */
void CompileBroker::handle_full_code_cache(int code_blob_type) {
  UseInterpreter = true;
  if (UseCompiler || AlwaysCompileLoopMethods ) {
    if (xtty != NULL) {
      ResourceMark rm;
      stringStream s;
      // Dump code cache state into a buffer before locking the tty,
      // because log_state() will use locks causing lock conflicts.
      CodeCache::log_state(&s);
      // Lock to prevent tearing
      ttyLocker ttyl;
      xtty->begin_elem("code_cache_full");
      xtty->print("%s", s.as_string());
      xtty->stamp();
      xtty->end_elem();
    }

#ifndef PRODUCT
    if (CompileTheWorld || ExitOnFullCodeCache) {
      codecache_print(/* detailed= */ true);
      before_exit(JavaThread::current());
      exit_globals(); // will delete tty
      vm_direct_exit(CompileTheWorld ? 0 : 1);
    }
#endif
    if (UseCodeCacheFlushing) {
      // Since code cache is full, immediately stop new compiles
      if (CompileBroker::set_should_compile_new_jobs(CompileBroker::stop_compilation)) {
        NMethodSweeper::log_sweep("disable_compiler");
      }
    } else {
      disable_compilation_forever();
    }

    CodeCache::report_codemem_full(code_blob_type, should_print_compiler_warning());
  }
}

// ------------------------------------------------------------------
// CompileBroker::set_last_compile
//
// Record this compilation for debugging purposes.
void CompileBroker::set_last_compile(CompilerThread* thread, const methodHandle& method, bool is_osr, int comp_level) {
  ResourceMark rm;
  char* method_name = method->name()->as_C_string();
  strncpy(_last_method_compiled, method_name, CompileBroker::name_buffer_length);
  _last_method_compiled[CompileBroker::name_buffer_length - 1] = '\0'; // ensure null terminated
  char current_method[CompilerCounters::cmname_buffer_length];
  size_t maxLen = CompilerCounters::cmname_buffer_length;

  if (UsePerfData) {
    const char* class_name = method->method_holder()->name()->as_C_string();

    size_t s1len = strlen(class_name);
    size_t s2len = strlen(method_name);

    // check if we need to truncate the string
    if (s1len + s2len + 2 > maxLen) {

      // the strategy is to lop off the leading characters of the
      // class name and the trailing characters of the method name.

      if (s2len + 2 > maxLen) {
        // lop of the entire class name string, let snprintf handle
        // truncation of the method name.
        class_name += s1len; // null string
      }
      else {
        // lop off the extra characters from the front of the class name
        class_name += ((s1len + s2len + 2) - maxLen);
      }
    }

    jio_snprintf(current_method, maxLen, "%s %s", class_name, method_name);
  }

  if (CICountOSR && is_osr) {
    _last_compile_type = osr_compile;
  } else {
    _last_compile_type = normal_compile;
  }
  _last_compile_level = comp_level;

  if (UsePerfData) {
    CompilerCounters* counters = thread->counters();
    counters->set_current_method(current_method);
    counters->set_compile_type((jlong)_last_compile_type);
  }
}


// ------------------------------------------------------------------
// CompileBroker::push_jni_handle_block
//
// Push on a new block of JNI handles.
void CompileBroker::push_jni_handle_block() {
  JavaThread* thread = JavaThread::current();

  // Allocate a new block for JNI handles.
  // Inlined code from jni_PushLocalFrame()
  JNIHandleBlock* java_handles = thread->active_handles();
  JNIHandleBlock* compile_handles = JNIHandleBlock::allocate_block(thread);
  assert(compile_handles != NULL && java_handles != NULL, "should not be NULL");
  compile_handles->set_pop_frame_link(java_handles);  // make sure java handles get gc'd.
  thread->set_active_handles(compile_handles);
}


// ------------------------------------------------------------------
// CompileBroker::pop_jni_handle_block
//
// Pop off the current block of JNI handles.
void CompileBroker::pop_jni_handle_block() {
  JavaThread* thread = JavaThread::current();

  // Release our JNI handle block
  JNIHandleBlock* compile_handles = thread->active_handles();
  JNIHandleBlock* java_handles = compile_handles->pop_frame_link();
  thread->set_active_handles(java_handles);
  compile_handles->set_pop_frame_link(NULL);
  JNIHandleBlock::release_block(compile_handles, thread); // may block
}

// ------------------------------------------------------------------
// CompileBroker::collect_statistics
//
// Collect statistics about the compilation.

void CompileBroker::collect_statistics(CompilerThread* thread, elapsedTimer time, CompileTask* task) {
  bool success = task->is_success();
  methodHandle method (thread, task->method());
  uint compile_id = task->compile_id();
  bool is_osr = (task->osr_bci() != standard_entry_bci);
  nmethod* code = task->code();
  CompilerCounters* counters = thread->counters();

  assert(code == NULL || code->is_locked_by_vm(), "will survive the MutexLocker");
  MutexLocker locker(CompileStatistics_lock);

  // _perf variables are production performance counters which are
  // updated regardless of the setting of the CITime and CITimeEach flags
  //

  // account all time, including bailouts and failures in this counter;
  // C1 and C2 counters are counting both successful and unsuccessful compiles
  _t_total_compilation.add(time);

  if (!success) {
    _total_bailout_count++;
    if (UsePerfData) {
      _perf_last_failed_method->set_value(counters->current_method());
      _perf_last_failed_type->set_value(counters->compile_type());
      _perf_total_bailout_count->inc();
    }
    _t_bailedout_compilation.add(time);
  } else if (code == NULL) {
    if (UsePerfData) {
      _perf_last_invalidated_method->set_value(counters->current_method());
      _perf_last_invalidated_type->set_value(counters->compile_type());
      _perf_total_invalidated_count->inc();
    }
    _total_invalidated_count++;
    _t_invalidated_compilation.add(time);
  } else {
    // Compilation succeeded

    // update compilation ticks - used by the implementation of
    // java.lang.management.CompilationMBean
    _perf_total_compilation->inc(time.ticks());
    _peak_compilation_time = time.milliseconds() > _peak_compilation_time ? time.milliseconds() : _peak_compilation_time;

    if (CITime) {
      int bytes_compiled = method->code_size() + task->num_inlined_bytecodes();
      JVMCI_ONLY(CompilerStatistics* stats = compiler(task->comp_level())->stats();)
      if (is_osr) {
        _t_osr_compilation.add(time);
        _sum_osr_bytes_compiled += bytes_compiled;
        JVMCI_ONLY(stats->_osr.update(time, bytes_compiled);)
      } else {
        _t_standard_compilation.add(time);
        _sum_standard_bytes_compiled += method->code_size() + task->num_inlined_bytecodes();
        JVMCI_ONLY(stats->_standard.update(time, bytes_compiled);)
      }
      JVMCI_ONLY(stats->_nmethods_size += code->total_size();)
      JVMCI_ONLY(stats->_nmethods_code_size += code->insts_size();)
    }

    if (UsePerfData) {
      // save the name of the last method compiled
      _perf_last_method->set_value(counters->current_method());
      _perf_last_compile_type->set_value(counters->compile_type());
      _perf_last_compile_size->set_value(method->code_size() +
                                         task->num_inlined_bytecodes());
      if (is_osr) {
        _perf_osr_compilation->inc(time.ticks());
        _perf_sum_osr_bytes_compiled->inc(method->code_size() + task->num_inlined_bytecodes());
      } else {
        _perf_standard_compilation->inc(time.ticks());
        _perf_sum_standard_bytes_compiled->inc(method->code_size() + task->num_inlined_bytecodes());
      }
    }

    if (CITimeEach) {
      float bytes_per_sec = 1.0 * (method->code_size() + task->num_inlined_bytecodes()) / time.seconds();
      tty->print_cr("%3d   seconds: %f bytes/sec : %f (bytes %d + %d inlined)",
                    compile_id, time.seconds(), bytes_per_sec, method->code_size(), task->num_inlined_bytecodes());
    }

    // Collect counts of successful compilations
    _sum_nmethod_size      += code->total_size();
    _sum_nmethod_code_size += code->insts_size();
    _total_compile_count++;

    if (UsePerfData) {
      _perf_sum_nmethod_size->inc(     code->total_size());
      _perf_sum_nmethod_code_size->inc(code->insts_size());
      _perf_total_compile_count->inc();
    }

    if (is_osr) {
      if (UsePerfData) _perf_total_osr_compile_count->inc();
      _total_osr_compile_count++;
    } else {
      if (UsePerfData) _perf_total_standard_compile_count->inc();
      _total_standard_compile_count++;
    }
  }
  // set the current method for the thread to null
  if (UsePerfData) counters->set_current_method("");
}

const char* CompileBroker::compiler_name(int comp_level) {
  AbstractCompiler *comp = CompileBroker::compiler(comp_level);
  if (comp == NULL) {
    return "no compiler";
  } else {
    return (comp->name());
  }
}

#if INCLUDE_JVMCI
void CompileBroker::print_times(AbstractCompiler* comp) {
  CompilerStatistics* stats = comp->stats();
  tty->print_cr("  %s {speed: %d bytes/s; standard: %6.3f s, %d bytes, %d methods; osr: %6.3f s, %d bytes, %d methods; nmethods_size: %d bytes; nmethods_code_size: %d bytes}",
                comp->name(), stats->bytes_per_second(),
                stats->_standard._time.seconds(), stats->_standard._bytes, stats->_standard._count,
                stats->_osr._time.seconds(), stats->_osr._bytes, stats->_osr._count,
                stats->_nmethods_size, stats->_nmethods_code_size);
  comp->print_timers();
}
#endif // INCLUDE_JVMCI

void CompileBroker::print_times(bool per_compiler, bool aggregate) {
#if INCLUDE_JVMCI
  elapsedTimer standard_compilation;
  elapsedTimer total_compilation;
  elapsedTimer osr_compilation;

  int standard_bytes_compiled = 0;
  int osr_bytes_compiled = 0;

  int standard_compile_count = 0;
  int osr_compile_count = 0;
  int total_compile_count = 0;

  int nmethods_size = 0;
  int nmethods_code_size = 0;
  bool printedHeader = false;

  for (unsigned int i = 0; i < sizeof(_compilers) / sizeof(AbstractCompiler*); i++) {
    AbstractCompiler* comp = _compilers[i];
    if (comp != NULL) {
      if (per_compiler && aggregate && !printedHeader) {
        printedHeader = true;
        tty->cr();
        tty->print_cr("Individual compiler times (for compiled methods only)");
        tty->print_cr("------------------------------------------------");
        tty->cr();
      }
      CompilerStatistics* stats = comp->stats();

      standard_compilation.add(stats->_standard._time);
      osr_compilation.add(stats->_osr._time);

      standard_bytes_compiled += stats->_standard._bytes;
      osr_bytes_compiled += stats->_osr._bytes;

      standard_compile_count += stats->_standard._count;
      osr_compile_count += stats->_osr._count;

      nmethods_size += stats->_nmethods_size;
      nmethods_code_size += stats->_nmethods_code_size;

      if (per_compiler) {
        print_times(comp);
      }
    }
  }
  total_compile_count = osr_compile_count + standard_compile_count;
  total_compilation.add(osr_compilation);
  total_compilation.add(standard_compilation);

  // In hosted mode, print the JVMCI compiler specific counters manually.
  if (!UseJVMCICompiler) {
    JVMCICompiler::print_compilation_timers();
  }
#else // INCLUDE_JVMCI
  elapsedTimer standard_compilation = CompileBroker::_t_standard_compilation;
  elapsedTimer osr_compilation = CompileBroker::_t_osr_compilation;
  elapsedTimer total_compilation = CompileBroker::_t_total_compilation;

  int standard_bytes_compiled = CompileBroker::_sum_standard_bytes_compiled;
  int osr_bytes_compiled = CompileBroker::_sum_osr_bytes_compiled;

  int standard_compile_count = CompileBroker::_total_standard_compile_count;
  int osr_compile_count = CompileBroker::_total_osr_compile_count;
  int total_compile_count = CompileBroker::_total_compile_count;

  int nmethods_size = CompileBroker::_sum_nmethod_code_size;
  int nmethods_code_size = CompileBroker::_sum_nmethod_size;
#endif // INCLUDE_JVMCI

  if (!aggregate) {
    return;
  }
  tty->cr();
  tty->print_cr("Accumulated compiler times");
  tty->print_cr("----------------------------------------------------------");
               //0000000000111111111122222222223333333333444444444455555555556666666666
               //0123456789012345678901234567890123456789012345678901234567890123456789
  tty->print_cr("  Total compilation time   : %7.3f s", total_compilation.seconds());
  tty->print_cr("    Standard compilation   : %7.3f s, Average : %2.3f s",
                standard_compilation.seconds(),
                standard_compilation.seconds() / standard_compile_count);
  tty->print_cr("    Bailed out compilation : %7.3f s, Average : %2.3f s",
                CompileBroker::_t_bailedout_compilation.seconds(),
                CompileBroker::_t_bailedout_compilation.seconds() / CompileBroker::_total_bailout_count);
  tty->print_cr("    On stack replacement   : %7.3f s, Average : %2.3f s",
                osr_compilation.seconds(),
                osr_compilation.seconds() / osr_compile_count);
  tty->print_cr("    Invalidated            : %7.3f s, Average : %2.3f s",
                CompileBroker::_t_invalidated_compilation.seconds(),
                CompileBroker::_t_invalidated_compilation.seconds() / CompileBroker::_total_invalidated_count);

  AbstractCompiler *comp = compiler(CompLevel_simple);
  if (comp != NULL) {
    tty->cr();
    comp->print_timers();
  }
  comp = compiler(CompLevel_full_optimization);
  if (comp != NULL) {
    tty->cr();
    comp->print_timers();
  }
  tty->cr();
  tty->print_cr("  Total compiled methods    : %8d methods", total_compile_count);
  tty->print_cr("    Standard compilation    : %8d methods", standard_compile_count);
  tty->print_cr("    On stack replacement    : %8d methods", osr_compile_count);
  int tcb = osr_bytes_compiled + standard_bytes_compiled;
  tty->print_cr("  Total compiled bytecodes  : %8d bytes", tcb);
  tty->print_cr("    Standard compilation    : %8d bytes", standard_bytes_compiled);
  tty->print_cr("    On stack replacement    : %8d bytes", osr_bytes_compiled);
  double tcs = total_compilation.seconds();
  int bps = tcs == 0.0 ? 0 : (int)(tcb / tcs);
  tty->print_cr("  Average compilation speed : %8d bytes/s", bps);
  tty->cr();
  tty->print_cr("  nmethod code size         : %8d bytes", nmethods_code_size);
  tty->print_cr("  nmethod total size        : %8d bytes", nmethods_size);
}

// Debugging output for failure
void CompileBroker::print_last_compile() {
  if ( _last_compile_level != CompLevel_none &&
       compiler(_last_compile_level) != NULL &&
       _last_method_compiled != NULL &&
       _last_compile_type != no_compile) {
    if (_last_compile_type == osr_compile) {
      tty->print_cr("Last parse:  [osr]%d+++(%d) %s",
                    _osr_compilation_id, _last_compile_level, _last_method_compiled);
    } else {
      tty->print_cr("Last parse:  %d+++(%d) %s",
                    _compilation_id, _last_compile_level, _last_method_compiled);
    }
  }
}


void CompileBroker::print_compiler_threads_on(outputStream* st) {
#ifndef PRODUCT
  st->print_cr("Compiler thread printing unimplemented.");
  st->cr();
#endif
}
