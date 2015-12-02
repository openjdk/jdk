/*
 * Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "compiler/compileTask.hpp"
#include "compiler/compileLog.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compilerDirectives.hpp"

CompileTask*  CompileTask::_task_free_list = NULL;
#ifdef ASSERT
int CompileTask::_num_allocated_tasks = 0;
#endif

/**
 * Allocate a CompileTask, from the free list if possible.
 */
CompileTask* CompileTask::allocate() {
  MutexLocker locker(CompileTaskAlloc_lock);
  CompileTask* task = NULL;

  if (_task_free_list != NULL) {
    task = _task_free_list;
    _task_free_list = task->next();
    task->set_next(NULL);
  } else {
    task = new CompileTask();
    DEBUG_ONLY(_num_allocated_tasks++;)
    assert (WhiteBoxAPI || _num_allocated_tasks < 10000, "Leaking compilation tasks?");
    task->set_next(NULL);
    task->set_is_free(true);
  }
  assert(task->is_free(), "Task must be free.");
  task->set_is_free(false);
  return task;
}

/**
* Add a task to the free list.
*/

void CompileTask::free(CompileTask* task) {
 MutexLocker locker(CompileTaskAlloc_lock);
 if (!task->is_free()) {
   task->set_code(NULL);
   assert(!task->lock()->is_locked(), "Should not be locked when freed");
   JNIHandles::destroy_global(task->_method_holder);
   JNIHandles::destroy_global(task->_hot_method_holder);

   task->set_is_free(true);
   task->set_next(_task_free_list);
   _task_free_list = task;
 }
}


void CompileTask::initialize(int compile_id,
                             const methodHandle& method,
                             int osr_bci,
                             int comp_level,
                             const methodHandle& hot_method,
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
  _failure_reason = NULL;

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
//
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

void CompileTask::mark_on_stack() {
  // Mark these methods as something redefine classes cannot remove.
  _method->set_on_stack(true);
  if (_hot_method != NULL) {
    _hot_method->set_on_stack(true);
  }
}

// RedefineClasses support
void CompileTask::metadata_do(void f(Metadata*)) {
  f(method());
  if (hot_method() != NULL && hot_method() != method()) {
    f(hot_method());
  }
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
  print(st);
}

// ------------------------------------------------------------------
// CompileTask::print_tty
void CompileTask::print_tty() {
  ttyLocker ttyl;  // keep the following output all in one block
  // print compiler name if requested
  if (CIPrintCompilerName) tty->print("%s:", CompileBroker::compiler_name(comp_level()));
    print(tty);
}

// ------------------------------------------------------------------
// CompileTask::print_impl
void CompileTask::print_impl(outputStream* st, Method* method, int compile_id, int comp_level,
                                         bool is_osr_method, int osr_bci, bool is_blocking,
                                         const char* msg, bool short_form, bool cr) {
  if (!short_form) {
    st->print("%7d ", (int) st->time_stamp().milliseconds());  // print timestamp
  }
  // print compiler name if requested
  if (CIPrintCompilerName) {
    st->print("%s:", CompileBroker::compiler_name(comp_level));
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
  if (cr) {
    st->cr();
  }
}

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
void CompileTask::print(outputStream* st, const char* msg, bool short_form, bool cr) {
  bool is_osr_method = osr_bci() != InvocationEntryBci;
  print_impl(st, method(), compile_id(), comp_level(), is_osr_method, osr_bci(), is_blocking(), msg, short_form, cr);
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
  // Always print the level in tiered.
  if (_comp_level != CompLevel_highest_tier || TieredCompilation) {
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
// CompileTask::log_task_dequeued
void CompileTask::log_task_dequeued(const char* comment) {
  if (LogCompilation && xtty != NULL) {
    Thread* thread = Thread::current();
    ttyLocker ttyl;
    ResourceMark rm(thread);

    xtty->begin_elem("task_dequeued");
    log_task(xtty);
    if (comment != NULL) {
      xtty->print(" comment='%s'", comment);
    }
    xtty->end_elem();
  }
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

  if (!_is_success) {
    const char* reason = _failure_reason != NULL ? _failure_reason : "unknown";
    log->elem("failure reason='%s'", reason);
  }

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
  log->clear_identities();   // next task will have different CI
  log->tail("task");
  if (log->unflushed_count() > 2000) {
    log->flush();
  }
  log->mark_file_end();
}

// ------------------------------------------------------------------
// CompileTask::check_break_at_flags
bool CompileTask::check_break_at_flags() {
  int compile_id = this->_compile_id;
  bool is_osr = (_osr_bci != CompileBroker::standard_entry_bci);

  if (CICountOSR && is_osr && (compile_id == CIBreakAtOSR)) {
    return true;
  } else {
    return (compile_id == CIBreakAt);
  }
}

// ------------------------------------------------------------------
// CompileTask::print_inlining
void CompileTask::print_inlining_inner(outputStream* st, ciMethod* method, int inline_level, int bci, const char* msg) {
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
