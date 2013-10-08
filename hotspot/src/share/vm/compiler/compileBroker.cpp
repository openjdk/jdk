/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "compiler/compileBroker.hpp"
#include "compiler/compileLog.hpp"
#include "compiler/compilerOracle.hpp"
#include "interpreter/linkResolver.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "prims/nativeLookup.hpp"
#include "runtime/arguments.hpp"
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
#ifdef COMPILER2
#include "opto/c2compiler.hpp"
#endif
#ifdef SHARK
#include "shark/sharkCompiler.hpp"
#endif

#ifdef DTRACE_ENABLED

// Only bother with this argument setup if dtrace is available

#ifndef USDT2
HS_DTRACE_PROBE_DECL8(hotspot, method__compile__begin,
  char*, intptr_t, char*, intptr_t, char*, intptr_t, char*, intptr_t);
HS_DTRACE_PROBE_DECL9(hotspot, method__compile__end,
  char*, intptr_t, char*, intptr_t, char*, intptr_t, char*, intptr_t, bool);

#define DTRACE_METHOD_COMPILE_BEGIN_PROBE(method, comp_name)             \
  {                                                                      \
    Symbol* klass_name = (method)->klass_name();                         \
    Symbol* name = (method)->name();                                     \
    Symbol* signature = (method)->signature();                           \
    HS_DTRACE_PROBE8(hotspot, method__compile__begin,                    \
      comp_name, strlen(comp_name),                                      \
      klass_name->bytes(), klass_name->utf8_length(),                    \
      name->bytes(), name->utf8_length(),                                \
      signature->bytes(), signature->utf8_length());                     \
  }

#define DTRACE_METHOD_COMPILE_END_PROBE(method, comp_name, success)      \
  {                                                                      \
    Symbol* klass_name = (method)->klass_name();                         \
    Symbol* name = (method)->name();                                     \
    Symbol* signature = (method)->signature();                           \
    HS_DTRACE_PROBE9(hotspot, method__compile__end,                      \
      comp_name, strlen(comp_name),                                      \
      klass_name->bytes(), klass_name->utf8_length(),                    \
      name->bytes(), name->utf8_length(),                                \
      signature->bytes(), signature->utf8_length(), (success));          \
  }

#else /* USDT2 */

#define DTRACE_METHOD_COMPILE_BEGIN_PROBE(method, comp_name)             \
  {                                                                      \
    Symbol* klass_name = (method)->klass_name();                         \
    Symbol* name = (method)->name();                                     \
    Symbol* signature = (method)->signature();                           \
    HOTSPOT_METHOD_COMPILE_BEGIN(                                        \
      comp_name, strlen(comp_name),                                      \
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
      comp_name, strlen(comp_name),                                      \
      (char *) klass_name->bytes(), klass_name->utf8_length(),           \
      (char *) name->bytes(), name->utf8_length(),                       \
      (char *) signature->bytes(), signature->utf8_length(), (success)); \
  }
#endif /* USDT2 */

#else //  ndef DTRACE_ENABLED

#define DTRACE_METHOD_COMPILE_BEGIN_PROBE(method, comp_name)
#define DTRACE_METHOD_COMPILE_END_PROBE(method, comp_name, success)

#endif // ndef DTRACE_ENABLED

bool CompileBroker::_initialized = false;
volatile bool CompileBroker::_should_block = false;
volatile jint CompileBroker::_should_compile_new_jobs = run_compilation;

// The installed compiler(s)
AbstractCompiler* CompileBroker::_compilers[2];

// These counters are used for assigning id's to each compilation
uint CompileBroker::_compilation_id        = 0;
uint CompileBroker::_osr_compilation_id    = 0;

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

CompileQueue* CompileBroker::_c2_method_queue    = NULL;
CompileQueue* CompileBroker::_c1_method_queue    = NULL;
CompileTask*  CompileBroker::_task_free_list     = NULL;

GrowableArray<CompilerThread*>* CompileBroker::_method_threads = NULL;


class CompilationLog : public StringEventLog {
 public:
  CompilationLog() : StringEventLog("Compilation events") {
  }

  void log_compile(JavaThread* thread, CompileTask* task) {
    StringLogMessage lm;
    stringStream sstr = lm.stream();
    // msg.time_stamp().update_to(tty->time_stamp().ticks());
    task->print_compilation(&sstr, NULL, true);
    log(thread, "%s", (const char*)lm);
  }

  void log_nmethod(JavaThread* thread, nmethod* nm) {
    log(thread, "nmethod %d%s " INTPTR_FORMAT " code ["INTPTR_FORMAT ", " INTPTR_FORMAT "]",
        nm->compile_id(), nm->is_osr_method() ? "%" : "",
        nm, nm->code_begin(), nm->code_end());
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
};

static CompilationLog* _compilation_log = NULL;

void compileBroker_init() {
  if (LogEvents) {
    _compilation_log = new CompilationLog();
  }
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
    MutexLocker notifier(task->lock(), thread);
    task->mark_complete();
    // Notify the waiting thread that the compilation has completed.
    task->lock()->notify_all();
  } else {
    task->mark_complete();

    // By convention, the compiling thread is responsible for
    // recycling a non-blocking CompileTask.
    CompileBroker::free_task(task);
  }
}


// ------------------------------------------------------------------
// CompileTask::initialize
void CompileTask::initialize(int compile_id,
                             methodHandle method,
                             int osr_bci,
                             int comp_level,
                             methodHandle hot_method,
                             int hot_count,
                             const char* comment,
                             bool is_blocking) {
  assert(!_lock->is_locked(), "bad locking");

  _compile_id = compile_id;
  _method = method();
  _method_holder = JNIHandles::make_global(method->method_holder()->klass_holder());
  _osr_bci = osr_bci;
  _is_blocking = is_blocking;
  _comp_level = comp_level;
  _num_inlined_bytecodes = 0;

  _is_complete = false;
  _is_success = false;
  _code_handle = NULL;

  _hot_method = NULL;
  _hot_method_holder = NULL;
  _hot_count = hot_count;
  _time_queued = 0;  // tidy
  _comment = comment;

  if (LogCompilation) {
    _time_queued = os::elapsed_counter();
    if (hot_method.not_null()) {
      if (hot_method == method) {
        _hot_method = _method;
      } else {
        _hot_method = hot_method();
        // only add loader or mirror if different from _method_holder
        _hot_method_holder = JNIHandles::make_global(hot_method->method_holder()->klass_holder());
      }
    }
  }

  _next = NULL;
}

// ------------------------------------------------------------------
// CompileTask::code/set_code
nmethod* CompileTask::code() const {
  if (_code_handle == NULL)  return NULL;
  return _code_handle->code();
}
void CompileTask::set_code(nmethod* nm) {
  if (_code_handle == NULL && nm == NULL)  return;
  guarantee(_code_handle != NULL, "");
  _code_handle->set_code(nm);
  if (nm == NULL)  _code_handle = NULL;  // drop the handle also
}

// ------------------------------------------------------------------
// CompileTask::free
void CompileTask::free() {
  set_code(NULL);
  assert(!_lock->is_locked(), "Should not be locked when freed");
  JNIHandles::destroy_global(_method_holder);
  JNIHandles::destroy_global(_hot_method_holder);
}


void CompileTask::mark_on_stack() {
  // Mark these methods as something redefine classes cannot remove.
  _method->set_on_stack(true);
  if (_hot_method != NULL) {
    _hot_method->set_on_stack(true);
  }
}

// ------------------------------------------------------------------
// CompileTask::print
void CompileTask::print() {
  tty->print("<CompileTask compile_id=%d ", _compile_id);
  tty->print("method=");
  _method->print_name(tty);
  tty->print_cr(" osr_bci=%d is_blocking=%s is_complete=%s is_success=%s>",
             _osr_bci, bool_to_str(_is_blocking),
             bool_to_str(_is_complete), bool_to_str(_is_success));
}


// ------------------------------------------------------------------
// CompileTask::print_line_on_error
//
// This function is called by fatal error handler when the thread
// causing troubles is a compiler thread.
//
// Do not grab any lock, do not allocate memory.
//
// Otherwise it's the same as CompileTask::print_line()
//
void CompileTask::print_line_on_error(outputStream* st, char* buf, int buflen) {
  // print compiler name
  st->print("%s:", CompileBroker::compiler_name(comp_level()));
  print_compilation(st);
}

// ------------------------------------------------------------------
// CompileTask::print_line
void CompileTask::print_line() {
  ttyLocker ttyl;  // keep the following output all in one block
  // print compiler name if requested
  if (CIPrintCompilerName) tty->print("%s:", CompileBroker::compiler_name(comp_level()));
  print_compilation();
}


// ------------------------------------------------------------------
// CompileTask::print_compilation_impl
void CompileTask::print_compilation_impl(outputStream* st, Method* method, int compile_id, int comp_level,
                                         bool is_osr_method, int osr_bci, bool is_blocking,
                                         const char* msg, bool short_form) {
  if (!short_form) {
    st->print("%7d ", (int) st->time_stamp().milliseconds());  // print timestamp
  }
  st->print("%4d ", compile_id);    // print compilation number

  // For unloaded methods the transition to zombie occurs after the
  // method is cleared so it's impossible to report accurate
  // information for that case.
  bool is_synchronized = false;
  bool has_exception_handler = false;
  bool is_native = false;
  if (method != NULL) {
    is_synchronized       = method->is_synchronized();
    has_exception_handler = method->has_exception_handler();
    is_native             = method->is_native();
  }
  // method attributes
  const char compile_type   = is_osr_method                   ? '%' : ' ';
  const char sync_char      = is_synchronized                 ? 's' : ' ';
  const char exception_char = has_exception_handler           ? '!' : ' ';
  const char blocking_char  = is_blocking                     ? 'b' : ' ';
  const char native_char    = is_native                       ? 'n' : ' ';

  // print method attributes
  st->print("%c%c%c%c%c ", compile_type, sync_char, exception_char, blocking_char, native_char);

  if (TieredCompilation) {
    if (comp_level != -1)  st->print("%d ", comp_level);
    else                   st->print("- ");
  }
  st->print("     ");  // more indent

  if (method == NULL) {
    st->print("(method)");
  } else {
    method->print_short_name(st);
    if (is_osr_method) {
      st->print(" @ %d", osr_bci);
    }
    if (method->is_native())
      st->print(" (native)");
    else
      st->print(" (%d bytes)", method->code_size());
  }

  if (msg != NULL) {
    st->print("   %s", msg);
  }
  if (!short_form) {
    st->cr();
  }
}

// ------------------------------------------------------------------
// CompileTask::print_inlining
void CompileTask::print_inlining(outputStream* st, ciMethod* method, int inline_level, int bci, const char* msg) {
  //         1234567
  st->print("        ");     // print timestamp
  //         1234
  st->print("     ");        // print compilation number

  // method attributes
  if (method->is_loaded()) {
    const char sync_char      = method->is_synchronized()        ? 's' : ' ';
    const char exception_char = method->has_exception_handlers() ? '!' : ' ';
    const char monitors_char  = method->has_monitor_bytecodes()  ? 'm' : ' ';

    // print method attributes
    st->print(" %c%c%c  ", sync_char, exception_char, monitors_char);
  } else {
    //         %s!bn
    st->print("      ");     // print method attributes
  }

  if (TieredCompilation) {
    st->print("  ");
  }
  st->print("     ");        // more indent
  st->print("    ");         // initial inlining indent

  for (int i = 0; i < inline_level; i++)  st->print("  ");

  st->print("@ %d  ", bci);  // print bci
  method->print_short_name(st);
  if (method->is_loaded())
    st->print(" (%d bytes)", method->code_size());
  else
    st->print(" (not loaded)");

  if (msg != NULL) {
    st->print("   %s", msg);
  }
  st->cr();
}

// ------------------------------------------------------------------
// CompileTask::print_inline_indent
void CompileTask::print_inline_indent(int inline_level, outputStream* st) {
  //         1234567
  st->print("        ");     // print timestamp
  //         1234
  st->print("     ");        // print compilation number
  //         %s!bn
  st->print("      ");       // print method attributes
  if (TieredCompilation) {
    st->print("  ");
  }
  st->print("     ");        // more indent
  st->print("    ");         // initial inlining indent
  for (int i = 0; i < inline_level; i++)  st->print("  ");
}

// ------------------------------------------------------------------
// CompileTask::print_compilation
void CompileTask::print_compilation(outputStream* st, const char* msg, bool short_form) {
  bool is_osr_method = osr_bci() != InvocationEntryBci;
  print_compilation_impl(st, method(), compile_id(), comp_level(), is_osr_method, osr_bci(), is_blocking(), msg, short_form);
}

// ------------------------------------------------------------------
// CompileTask::log_task
void CompileTask::log_task(xmlStream* log) {
  Thread* thread = Thread::current();
  methodHandle method(thread, this->method());
  ResourceMark rm(thread);

  // <task id='9' method='M' osr_bci='X' level='1' blocking='1' stamp='1.234'>
  log->print(" compile_id='%d'", _compile_id);
  if (_osr_bci != CompileBroker::standard_entry_bci) {
    log->print(" compile_kind='osr'");  // same as nmethod::compile_kind
  } // else compile_kind='c2c'
  if (!method.is_null())  log->method(method);
  if (_osr_bci != CompileBroker::standard_entry_bci) {
    log->print(" osr_bci='%d'", _osr_bci);
  }
  if (_comp_level != CompLevel_highest_tier) {
    log->print(" level='%d'", _comp_level);
  }
  if (_is_blocking) {
    log->print(" blocking='1'");
  }
  log->stamp();
}


// ------------------------------------------------------------------
// CompileTask::log_task_queued
void CompileTask::log_task_queued() {
  Thread* thread = Thread::current();
  ttyLocker ttyl;
  ResourceMark rm(thread);

  xtty->begin_elem("task_queued");
  log_task(xtty);
  if (_comment != NULL) {
    xtty->print(" comment='%s'", _comment);
  }
  if (_hot_method != NULL) {
    methodHandle hot(thread, _hot_method);
    methodHandle method(thread, _method);
    if (hot() != method()) {
      xtty->method(hot);
    }
  }
  if (_hot_count != 0) {
    xtty->print(" hot_count='%d'", _hot_count);
  }
  xtty->end_elem();
}


// ------------------------------------------------------------------
// CompileTask::log_task_start
void CompileTask::log_task_start(CompileLog* log)   {
  log->begin_head("task");
  log_task(log);
  log->end_head();
}


// ------------------------------------------------------------------
// CompileTask::log_task_done
void CompileTask::log_task_done(CompileLog* log) {
  Thread* thread = Thread::current();
  methodHandle method(thread, this->method());
  ResourceMark rm(thread);

  // <task_done ... stamp='1.234'>  </task>
  nmethod* nm = code();
  log->begin_elem("task_done success='%d' nmsize='%d' count='%d'",
                  _is_success, nm == NULL ? 0 : nm->content_size(),
                  method->invocation_count());
  int bec = method->backedge_count();
  if (bec != 0)  log->print(" backedge_count='%d'", bec);
  // Note:  "_is_complete" is about to be set, but is not.
  if (_num_inlined_bytecodes != 0) {
    log->print(" inlined_bytes='%d'", _num_inlined_bytecodes);
  }
  log->stamp();
  log->end_elem();
  log->tail("task");
  log->clear_identities();   // next task will have different CI
  if (log->unflushed_count() > 2000) {
    log->flush();
  }
  log->mark_file_end();
}



// ------------------------------------------------------------------
// CompileQueue::add
//
// Add a CompileTask to a CompileQueue
void CompileQueue::add(CompileTask* task) {
  assert(lock()->owned_by_self(), "must own lock");

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
    print();
  }

  if (LogCompilation && xtty != NULL) {
    task->log_task_queued();
  }

  // Notify CompilerThreads that a task is available.
  lock()->notify_all();
}

// ------------------------------------------------------------------
// CompileQueue::get
//
// Get the next CompileTask from a CompileQueue
CompileTask* CompileQueue::get() {
  NMethodSweeper::possibly_sweep();

  MutexLocker locker(lock());
  // If _first is NULL we have no more compile jobs. There are two reasons for
  // having no compile jobs: First, we compiled everything we wanted. Second,
  // we ran out of code cache so compilation has been disabled. In the latter
  // case we perform code cache sweeps to free memory such that we can re-enable
  // compilation.
  while (_first == NULL) {
    if (UseCodeCacheFlushing && !CompileBroker::should_compile_new_jobs()) {
      // Wait a certain amount of time to possibly do another sweep.
      // We must wait until stack scanning has happened so that we can
      // transition a method's state from 'not_entrant' to 'zombie'.
      long wait_time = NmethodSweepCheckInterval * 1000;
      if (FLAG_IS_DEFAULT(NmethodSweepCheckInterval)) {
        // Only one thread at a time can do sweeping. Scale the
        // wait time according to the number of compiler threads.
        // As a result, the next sweep is likely to happen every 100ms
        // with an arbitrary number of threads that do sweeping.
        wait_time = 100 * CICompilerCount;
      }
      bool timeout = lock()->wait(!Mutex::_no_safepoint_check_flag, wait_time);
      if (timeout) {
        MutexUnlocker ul(lock());
        NMethodSweeper::possibly_sweep();
      }
    } else {
      // If there are no compilation tasks and we can compile new jobs
      // (i.e., there is enough free space in the code cache) there is
      // no need to invoke the sweeper. As a result, the hotness of methods
      // remains unchanged. This behavior is desired, since we want to keep
      // the stable state, i.e., we do not want to evict methods from the
      // code cache if it is unnecessary.
      lock()->wait();
    }
  }
  CompileTask* task = CompilationPolicy::policy()->select_task(this);
  remove(task);
  return task;
}

void CompileQueue::remove(CompileTask* task)
{
   assert(lock()->owned_by_self(), "must own lock");
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

// methods in the compile queue need to be marked as used on the stack
// so that they don't get reclaimed by Redefine Classes
void CompileQueue::mark_on_stack() {
  CompileTask* task = _first;
  while (task != NULL) {
    task->mark_on_stack();
    task = task->next();
  }
}

// ------------------------------------------------------------------
// CompileQueue::print
void CompileQueue::print() {
  tty->print_cr("Contents of %s", name());
  tty->print_cr("----------------------");
  CompileTask* task = _first;
  while (task != NULL) {
    task->print_line();
    task = task->next();
  }
  tty->print_cr("----------------------");
}

CompilerCounters::CompilerCounters(const char* thread_name, int instance, TRAPS) {

  _current_method[0] = '\0';
  _compile_type = CompileBroker::no_compile;

  if (UsePerfData) {
    ResourceMark rm;

    // create the thread instance name space string - don't create an
    // instance subspace if instance is -1 - keeps the adapterThread
    // counters  from having a ".0" namespace.
    const char* thread_i = (instance == -1) ? thread_name :
                      PerfDataManager::name_space(thread_name, instance);


    char* name = PerfDataManager::counter_name(thread_i, "method");
    _perf_current_method =
               PerfDataManager::create_string_variable(SUN_CI, name,
                                                       cmname_buffer_length,
                                                       _current_method, CHECK);

    name = PerfDataManager::counter_name(thread_i, "type");
    _perf_compile_type = PerfDataManager::create_variable(SUN_CI, name,
                                                          PerfData::U_None,
                                                         (jlong)_compile_type,
                                                          CHECK);

    name = PerfDataManager::counter_name(thread_i, "time");
    _perf_time = PerfDataManager::create_counter(SUN_CI, name,
                                                 PerfData::U_Ticks, CHECK);

    name = PerfDataManager::counter_name(thread_i, "compiles");
    _perf_compiles = PerfDataManager::create_counter(SUN_CI, name,
                                                     PerfData::U_Events, CHECK);
  }
}

// ------------------------------------------------------------------
// CompileBroker::compilation_init
//
// Initialize the Compilation object
void CompileBroker::compilation_init() {
  _last_method_compiled[0] = '\0';

#ifndef SHARK
  // Set the interface to the current compiler(s).
  int c1_count = CompilationPolicy::policy()->compiler_count(CompLevel_simple);
  int c2_count = CompilationPolicy::policy()->compiler_count(CompLevel_full_optimization);
#ifdef COMPILER1
  if (c1_count > 0) {
    _compilers[0] = new Compiler();
  }
#endif // COMPILER1

#ifdef COMPILER2
  if (c2_count > 0) {
    _compilers[1] = new C2Compiler();
  }
#endif // COMPILER2

#else // SHARK
  int c1_count = 0;
  int c2_count = 1;

  _compilers[1] = new SharkCompiler();
#endif // SHARK

  // Initialize the CompileTask free list
  _task_free_list = NULL;

  // Start the CompilerThreads
  init_compiler_threads(c1_count, c2_count);
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



// ------------------------------------------------------------------
// CompileBroker::make_compiler_thread
CompilerThread* CompileBroker::make_compiler_thread(const char* name, CompileQueue* queue, CompilerCounters* counters, TRAPS) {
  CompilerThread* compiler_thread = NULL;

  Klass* k =
    SystemDictionary::resolve_or_fail(vmSymbols::java_lang_Thread(),
                                      true, CHECK_0);
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
    compiler_thread = new CompilerThread(queue, counters);
    // At this point the new CompilerThread data-races with this startup
    // thread (which I believe is the primoridal thread and NOT the VM
    // thread).  This means Java bytecodes being executed at startup can
    // queue compile jobs which will run at whatever default priority the
    // newly created CompilerThread runs at.


    // At this point it may be possible that no osthread was created for the
    // JavaThread due to lack of memory. We would have to throw an exception
    // in that case. However, since this must work and we do not allow
    // exceptions anyway, check and abort if this fails.

    if (compiler_thread == NULL || compiler_thread->osthread() == NULL){
      vm_exit_during_initialization("java.lang.OutOfMemoryError",
                                    "unable to create new native thread");
    }

    java_lang_Thread::set_thread(thread_oop(), compiler_thread);

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
    os::set_native_priority(compiler_thread, native_prio);

    java_lang_Thread::set_daemon(thread_oop());

    compiler_thread->set_threadObj(thread_oop());
    Threads::add(compiler_thread);
    Thread::start(compiler_thread);
  }

  // Let go of Threads_lock before yielding
  os::yield(); // make sure that the compiler thread is started early (especially helpful on SOLARIS)

  return compiler_thread;
}


// ------------------------------------------------------------------
// CompileBroker::init_compiler_threads
//
// Initialize the compilation queue
void CompileBroker::init_compiler_threads(int c1_compiler_count, int c2_compiler_count) {
  EXCEPTION_MARK;
#if !defined(ZERO) && !defined(SHARK)
  assert(c2_compiler_count > 0 || c1_compiler_count > 0, "No compilers?");
#endif // !ZERO && !SHARK
  if (c2_compiler_count > 0) {
    _c2_method_queue  = new CompileQueue("C2MethodQueue",  MethodCompileQueue_lock);
  }
  if (c1_compiler_count > 0) {
    _c1_method_queue  = new CompileQueue("C1MethodQueue",  MethodCompileQueue_lock);
  }

  int compiler_count = c1_compiler_count + c2_compiler_count;

  _method_threads =
    new (ResourceObj::C_HEAP, mtCompiler) GrowableArray<CompilerThread*>(compiler_count, true);

  char name_buffer[256];
  for (int i = 0; i < c2_compiler_count; i++) {
    // Create a name for our thread.
    sprintf(name_buffer, "C2 CompilerThread%d", i);
    CompilerCounters* counters = new CompilerCounters("compilerThread", i, CHECK);
    CompilerThread* new_thread = make_compiler_thread(name_buffer, _c2_method_queue, counters, CHECK);
    _method_threads->append(new_thread);
  }

  for (int i = c2_compiler_count; i < compiler_count; i++) {
    // Create a name for our thread.
    sprintf(name_buffer, "C1 CompilerThread%d", i);
    CompilerCounters* counters = new CompilerCounters("compilerThread", i, CHECK);
    CompilerThread* new_thread = make_compiler_thread(name_buffer, _c1_method_queue, counters, CHECK);
    _method_threads->append(new_thread);
  }

  if (UsePerfData) {
    PerfDataManager::create_constant(SUN_CI, "threads", PerfData::U_Bytes,
                                     compiler_count, CHECK);
  }
}


// Set the methods on the stack as on_stack so that redefine classes doesn't
// reclaim them
void CompileBroker::mark_on_stack() {
  if (_c2_method_queue != NULL) {
    _c2_method_queue->mark_on_stack();
  }
  if (_c1_method_queue != NULL) {
    _c1_method_queue->mark_on_stack();
  }
}

// ------------------------------------------------------------------
// CompileBroker::is_idle
bool CompileBroker::is_idle() {
  if (_c2_method_queue != NULL && !_c2_method_queue->is_empty()) {
    return false;
  } else if (_c1_method_queue != NULL && !_c1_method_queue->is_empty()) {
    return false;
  } else {
    int num_threads = _method_threads->length();
    for (int i=0; i<num_threads; i++) {
      if (_method_threads->at(i)->task() != NULL) {
        return false;
      }
    }

    // No pending or active compilations.
    return true;
  }
}


// ------------------------------------------------------------------
// CompileBroker::compile_method
//
// Request compilation of a method.
void CompileBroker::compile_method_base(methodHandle method,
                                        int osr_bci,
                                        int comp_level,
                                        methodHandle hot_method,
                                        int hot_count,
                                        const char* comment,
                                        Thread* thread) {
  // do nothing if compiler thread(s) is not available
  if (!_initialized ) {
    return;
  }

  guarantee(!method->is_abstract(), "cannot compile abstract methods");
  assert(method->method_holder()->oop_is_instance(),
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
    tty->print(" comment: %s count: %d", comment, hot_count);
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
  if (compilation_is_in_queue(method, osr_bci)) {
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

  // Outputs from the following MutexLocker block:
  CompileTask* task     = NULL;
  bool         blocking = false;
  CompileQueue* queue  = compile_queue(comp_level);

  // Acquire our lock.
  {
    MutexLocker locker(queue->lock(), thread);

    // Make sure the method has not slipped into the queues since
    // last we checked; note that those checks were "fast bail-outs".
    // Here we need to be more careful, see 14012000 below.
    if (compilation_is_in_queue(method, osr_bci)) {
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
    uint compile_id = assign_compile_id(method, osr_bci);
    if (compile_id == 0) {
      // The compilation falls outside the allowed range.
      return;
    }

    // Should this thread wait for completion of the compile?
    blocking = is_compile_blocking(method, osr_bci);

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


nmethod* CompileBroker::compile_method(methodHandle method, int osr_bci,
                                       int comp_level,
                                       methodHandle hot_method, int hot_count,
                                       const char* comment, Thread* THREAD) {
  // make sure arguments make sense
  assert(method->method_holder()->oop_is_instance(), "not an instance method");
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
           "all OSR compiles are assumed to be at a single compilation lavel");
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

  // If the compiler is shut off due to code cache getting full
  // fail out now so blocking compiles dont hang the java thread
  if (!should_compile_new_jobs()) {
    CompilationPolicy::policy()->delay_compilation(method());
    return NULL;
  }

  // do the compilation
  if (method->is_native()) {
    if (!PreferInterpreterNativeStubs || method->is_method_handle_intrinsic()) {
      // Acquire our lock.
      int compile_id;
      {
        MutexLocker locker(MethodCompileQueue_lock, THREAD);
        compile_id = assign_compile_id(method, standard_entry_bci);
      }
      (void) AdapterHandlerLibrary::create_native_wrapper(method, compile_id);
    } else {
      return NULL;
    }
  } else {
    compile_method_base(method, osr_bci, comp_level, hot_method, hot_count, comment, THREAD);
  }

  // return requested nmethod
  // We accept a higher level osr method
  return osr_bci  == InvocationEntryBci ? method->code() : method->lookup_osr_nmethod_for(osr_bci, comp_level, false);
}


// ------------------------------------------------------------------
// CompileBroker::compilation_is_complete
//
// See if compilation of this method is already complete.
bool CompileBroker::compilation_is_complete(methodHandle method,
                                            int          osr_bci,
                                            int          comp_level) {
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


// ------------------------------------------------------------------
// CompileBroker::compilation_is_in_queue
//
// See if this compilation is already requested.
//
// Implementation note: there is only a single "is in queue" bit
// for each method.  This means that the check below is overly
// conservative in the sense that an osr compilation in the queue
// will block a normal compilation from entering the queue (and vice
// versa).  This can be remedied by a full queue search to disambiguate
// cases.  If it is deemed profitible, this may be done.
bool CompileBroker::compilation_is_in_queue(methodHandle method,
                                            int          osr_bci) {
  return method->queued_for_compilation();
}

// ------------------------------------------------------------------
// CompileBroker::compilation_is_prohibited
//
// See if this compilation is not allowed.
bool CompileBroker::compilation_is_prohibited(methodHandle method, int osr_bci, int comp_level) {
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

  // The method may be explicitly excluded by the user.
  bool quietly;
  if (CompilerOracle::should_exclude(method, quietly)) {
    if (!quietly) {
      // This does not happen quietly...
      ResourceMark rm;
      tty->print("### Excluding %s:%s",
                 method->is_native() ? "generation of native wrapper" : "compile",
                 (method->is_static() ? " static" : ""));
      method->print_short_name(tty);
      tty->cr();
    }
    method->set_not_compilable(CompLevel_all, !quietly, "excluded by CompilerOracle");
  }

  return false;
}


// ------------------------------------------------------------------
// CompileBroker::assign_compile_id
//
// Assign a serialized id number to this compilation request.  If the
// number falls out of the allowed range, return a 0.  OSR
// compilations may be numbered separately from regular compilations
// if certain debugging flags are used.
uint CompileBroker::assign_compile_id(methodHandle method, int osr_bci) {
  assert(MethodCompileQueue_lock->owner() == Thread::current(),
         "must hold the compilation queue lock");
  bool is_osr = (osr_bci != standard_entry_bci);
  uint id;
  if (CICountOSR && is_osr) {
    id = ++_osr_compilation_id;
    if ((uint)CIStartOSR <= id && id < (uint)CIStopOSR) {
      return id;
    }
  } else {
    id = ++_compilation_id;
    if ((uint)CIStart <= id && id < (uint)CIStop) {
      return id;
    }
  }

  // Method was not in the appropriate compilation range.
  method->set_not_compilable_quietly();
  return 0;
}


// ------------------------------------------------------------------
// CompileBroker::is_compile_blocking
//
// Should the current thread be blocked until this compilation request
// has been fulfilled?
bool CompileBroker::is_compile_blocking(methodHandle method, int osr_bci) {
  assert(!InstanceRefKlass::owns_pending_list_lock(JavaThread::current()), "possible deadlock");
  return !BackgroundCompilation;
}


// ------------------------------------------------------------------
// CompileBroker::preload_classes
void CompileBroker::preload_classes(methodHandle method, TRAPS) {
  // Move this code over from c1_Compiler.cpp
  ShouldNotReachHere();
}


// ------------------------------------------------------------------
// CompileBroker::create_compile_task
//
// Create a CompileTask object representing the current request for
// compilation.  Add this task to the queue.
CompileTask* CompileBroker::create_compile_task(CompileQueue* queue,
                                              int           compile_id,
                                              methodHandle  method,
                                              int           osr_bci,
                                              int           comp_level,
                                              methodHandle  hot_method,
                                              int           hot_count,
                                              const char*   comment,
                                              bool          blocking) {
  CompileTask* new_task = allocate_task();
  new_task->initialize(compile_id, method, osr_bci, comp_level,
                       hot_method, hot_count, comment,
                       blocking);
  queue->add(new_task);
  return new_task;
}


// ------------------------------------------------------------------
// CompileBroker::allocate_task
//
// Allocate a CompileTask, from the free list if possible.
CompileTask* CompileBroker::allocate_task() {
  MutexLocker locker(CompileTaskAlloc_lock);
  CompileTask* task = NULL;
  if (_task_free_list != NULL) {
    task = _task_free_list;
    _task_free_list = task->next();
    task->set_next(NULL);
  } else {
    task = new CompileTask();
    task->set_next(NULL);
  }
  return task;
}


// ------------------------------------------------------------------
// CompileBroker::free_task
//
// Add a task to the free list.
void CompileBroker::free_task(CompileTask* task) {
  MutexLocker locker(CompileTaskAlloc_lock);
  task->free();
  task->set_next(_task_free_list);
  _task_free_list = task;
}


// ------------------------------------------------------------------
// CompileBroker::wait_for_completion
//
// Wait for the given method CompileTask to complete.
void CompileBroker::wait_for_completion(CompileTask* task) {
  if (CIPrintCompileQueue) {
    tty->print_cr("BLOCKING FOR COMPILE");
  }

  assert(task->is_blocking(), "can only wait on blocking task");

  JavaThread *thread = JavaThread::current();
  thread->set_blocked_on_compilation(true);

  methodHandle method(thread, task->method());
  {
    MutexLocker waiter(task->lock(), thread);

    while (!task->is_complete())
      task->lock()->wait();
  }
  // It is harmless to check this status without the lock, because
  // completion is a stable property (until the task object is recycled).
  assert(task->is_complete(), "Compilation should have completed");
  assert(task->code_handle() == NULL, "must be reset");

  thread->set_blocked_on_compilation(false);

  // By convention, the waiter is responsible for recycling a
  // blocking CompileTask. Since there is only one waiter ever
  // waiting on a CompileTask, we know that no one else will
  // be using this CompileTask; we can free it.
  free_task(task);
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

  while (true) {
    {
      // We need this HandleMark to avoid leaking VM handles.
      HandleMark hm(thread);

      if (CodeCache::unallocated_capacity() < CodeCacheMinimumFreeSpace) {
        // the code cache is really full
        handle_full_code_cache();
      }

      CompileTask* task = queue->get();

      // Give compiler threads an extra quanta.  They tend to be bursty and
      // this helps the compiler to finish up the job.
      if( CompilerThreadHintNoPreempt )
        os::hint_no_preempt();

      // trace per thread time and compile statistics
      CompilerCounters* counters = ((CompilerThread*)thread)->counters();
      PerfTraceTimedEvent(counters->time_counter(), counters->compile_counter());

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
#ifdef COMPILER1
          // Allow repeating compilations for the purpose of benchmarking
          // compile speed. This is not useful for customers.
          if (CompilationRepeat != 0) {
            int compile_count = CompilationRepeat;
            while (compile_count > 0) {
              invoke_compiler_on_method(task);
              nmethod* nm = method->code();
              if (nm != NULL) {
                nm->make_zombie();
                method->clear_code();
              }
              compile_count--;
            }
          }
#endif /* COMPILER1 */
          invoke_compiler_on_method(task);
        } else {
          // After compilation is disabled, remove remaining methods from queue
          method->clear_queued_for_compilation();
        }
      }
    }
  }
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

      fp = fopen(file_name, "at");
      if (fp != NULL) {
        if (LogCompilation && Verbose) {
          tty->print_cr("Opening compilation log %s", file_name);
        }
        CompileLog* log = new(ResourceObj::C_HEAP, mtCompiler) CompileLog(file_name, fp, thread_id);
        thread->init_log(log);

        if (xtty != NULL) {
          ttyLocker ttyl;
          // Record any per thread log files
          xtty->elem("thread_logfile thread='%d' filename='%s'", thread_id, file_name);
        }
        return;
      }
    }
    warning("Cannot open log file: %s", file_name);
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
      tty->print_cr("compiler thread " INTPTR_FORMAT " poll detects block request", Thread::current());
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
  tty->print(s.as_string());
}

// ------------------------------------------------------------------
// CompileBroker::invoke_compiler_on_method
//
// Compile a method.
//
void CompileBroker::invoke_compiler_on_method(CompileTask* task) {
  if (PrintCompilation) {
    ResourceMark rm;
    task->print_line();
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
  {
    // create the handle inside it's own block so it can't
    // accidentally be referenced once the thread transitions to
    // native.  The NoHandleMark before the transition should catch
    // any cases where this occurs in the future.
    methodHandle method(thread, task->method());
    should_break = check_break_at(method, compile_id, is_osr);
    if (should_log && !CompilerOracle::should_log(method)) {
      should_log = false;
    }
    assert(!method->is_native(), "no longer compile natives");

    // Save information about this method in case of failure.
    set_last_compile(thread, method, is_osr, task_level);

    DTRACE_METHOD_COMPILE_BEGIN_PROBE(method, compiler_name(task_level));
  }

  // Allocate a new set of JNI handles.
  push_jni_handle_block();
  Method* target_handle = task->method();
  int compilable = ciEnv::MethodCompilable;
  {
    int system_dictionary_modification_counter;
    {
      MutexLocker locker(Compile_lock, thread);
      system_dictionary_modification_counter = SystemDictionary::number_of_modifications();
    }

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

    AbstractCompiler *comp = compiler(task_level);
    if (comp == NULL) {
      ci_env.record_method_not_compilable("no compiler", !TieredCompilation);
    } else {
      comp->compile_method(&ci_env, target, osr_bci);
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
      const char* retry_message = ci_env.retry_message();
      if (_compilation_log != NULL) {
        _compilation_log->log_failure(thread, task, ci_env.failure_reason(), retry_message);
      }
      if (PrintCompilation) {
        FormatBufferResource msg = retry_message != NULL ?
            err_msg_res("COMPILE SKIPPED: %s (%s)", ci_env.failure_reason(), retry_message) :
            err_msg_res("COMPILE SKIPPED: %s",      ci_env.failure_reason());
        task->print_compilation(tty, msg);
      }
    } else {
      task->mark_success();
      task->set_num_inlined_bytecodes(ci_env.num_inlined_bytecodes());
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
      event.set_method(target->get_Method());
      event.set_compileID(compile_id);
      event.set_compileLevel(task->comp_level());
      event.set_succeded(task->is_success());
      event.set_isOsr(is_osr);
      event.set_codeSize((task->code() == NULL) ? 0 : task->code()->total_size());
      event.set_inlinedBytes(task->num_inlined_bytecodes());
      event.commit();
    }
  }
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
  // when adding the task to the complie queue -- at which time the
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

// ------------------------------------------------------------------
// CompileBroker::handle_full_code_cache
//
// The CodeCache is full.  Print out warning and disable compilation or
// try code cache cleaning so compilation can continue later.
void CompileBroker::handle_full_code_cache() {
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
      xtty->print(s.as_string());
      xtty->stamp();
      xtty->end_elem();
    }
    warning("CodeCache is full. Compiler has been disabled.");
    warning("Try increasing the code cache size using -XX:ReservedCodeCacheSize=");

    CodeCache::report_codemem_full();


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

        // Switch to 'vm_state'. This ensures that possibly_sweep() can be called
        // without having to consider the state in which the current thread is.
        ThreadInVMfromUnknown in_vm;
        NMethodSweeper::possibly_sweep();
      }
    } else {
      UseCompiler               = false;
      AlwaysCompileLoopMethods  = false;
    }
  }
  codecache_print(/* detailed= */ true);
}

// ------------------------------------------------------------------
// CompileBroker::set_last_compile
//
// Record this compilation for debugging purposes.
void CompileBroker::set_last_compile(CompilerThread* thread, methodHandle method, bool is_osr, int comp_level) {
  ResourceMark rm;
  char* method_name = method->name()->as_C_string();
  strncpy(_last_method_compiled, method_name, CompileBroker::name_buffer_length);
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
// CompileBroker::check_break_at
//
// Should the compilation break at the current compilation.
bool CompileBroker::check_break_at(methodHandle method, int compile_id, bool is_osr) {
  if (CICountOSR && is_osr && (compile_id == CIBreakAtOSR)) {
    return true;
  } else if( CompilerOracle::should_break_at(method) ) { // break when compiling
    return true;
  } else {
    return (compile_id == CIBreakAt);
  }
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
  if (!success) {
    _total_bailout_count++;
    if (UsePerfData) {
      _perf_last_failed_method->set_value(counters->current_method());
      _perf_last_failed_type->set_value(counters->compile_type());
      _perf_total_bailout_count->inc();
    }
  } else if (code == NULL) {
    if (UsePerfData) {
      _perf_last_invalidated_method->set_value(counters->current_method());
      _perf_last_invalidated_type->set_value(counters->compile_type());
      _perf_total_invalidated_count->inc();
    }
    _total_invalidated_count++;
  } else {
    // Compilation succeeded

    // update compilation ticks - used by the implementation of
    // java.lang.management.CompilationMBean
    _perf_total_compilation->inc(time.ticks());

    _t_total_compilation.add(time);
    _peak_compilation_time = time.milliseconds() > _peak_compilation_time ? time.milliseconds() : _peak_compilation_time;

    if (CITime) {
      if (is_osr) {
        _t_osr_compilation.add(time);
        _sum_osr_bytes_compiled += method->code_size() + task->num_inlined_bytecodes();
      } else {
        _t_standard_compilation.add(time);
        _sum_standard_bytes_compiled += method->code_size() + task->num_inlined_bytecodes();
      }
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

void CompileBroker::print_times() {
  tty->cr();
  tty->print_cr("Accumulated compiler times (for compiled methods only)");
  tty->print_cr("------------------------------------------------");
               //0000000000111111111122222222223333333333444444444455555555556666666666
               //0123456789012345678901234567890123456789012345678901234567890123456789
  tty->print_cr("  Total compilation time   : %6.3f s", CompileBroker::_t_total_compilation.seconds());
  tty->print_cr("    Standard compilation   : %6.3f s, Average : %2.3f",
                CompileBroker::_t_standard_compilation.seconds(),
                CompileBroker::_t_standard_compilation.seconds() / CompileBroker::_total_standard_compile_count);
  tty->print_cr("    On stack replacement   : %6.3f s, Average : %2.3f", CompileBroker::_t_osr_compilation.seconds(), CompileBroker::_t_osr_compilation.seconds() / CompileBroker::_total_osr_compile_count);

  AbstractCompiler *comp = compiler(CompLevel_simple);
  if (comp != NULL) {
    comp->print_timers();
  }
  comp = compiler(CompLevel_full_optimization);
  if (comp != NULL) {
    comp->print_timers();
  }
  tty->cr();
  tty->print_cr("  Total compiled methods   : %6d methods", CompileBroker::_total_compile_count);
  tty->print_cr("    Standard compilation   : %6d methods", CompileBroker::_total_standard_compile_count);
  tty->print_cr("    On stack replacement   : %6d methods", CompileBroker::_total_osr_compile_count);
  int tcb = CompileBroker::_sum_osr_bytes_compiled + CompileBroker::_sum_standard_bytes_compiled;
  tty->print_cr("  Total compiled bytecodes : %6d bytes", tcb);
  tty->print_cr("    Standard compilation   : %6d bytes", CompileBroker::_sum_standard_bytes_compiled);
  tty->print_cr("    On stack replacement   : %6d bytes", CompileBroker::_sum_osr_bytes_compiled);
  int bps = (int)(tcb / CompileBroker::_t_total_compilation.seconds());
  tty->print_cr("  Average compilation speed: %6d bytes/s", bps);
  tty->cr();
  tty->print_cr("  nmethod code size        : %6d bytes", CompileBroker::_sum_nmethod_code_size);
  tty->print_cr("  nmethod total size       : %6d bytes", CompileBroker::_sum_nmethod_size);
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
