/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "compiler/compilationPolicy.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compileLog.hpp"
#include "compiler/compilerDirectives.hpp"
#include "compiler/compileTask.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "oops/klass.inline.hpp"
#include "oops/method.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/mutexLocker.hpp"

int CompileTask::_active_tasks = 0;

CompileTask::CompileTask(int compile_id,
                         const methodHandle& method,
                         int osr_bci,
                         int comp_level,
                         int hot_count,
                         CompileReason compile_reason,
                         bool is_blocking) {
  Thread* thread = Thread::current();
  _compile_id = compile_id;
  _method = method();
  _method_holder = JNIHandles::make_weak_global(Handle(thread, method->method_holder()->klass_holder()));
  _osr_bci = osr_bci;
  _is_blocking = is_blocking;
  JVMCI_ONLY(_has_waiter = CompileBroker::compiler(comp_level)->is_jvmci();)
  JVMCI_ONLY(_blocking_jvmci_compile_state = nullptr;)
  _comp_level = comp_level;
  _num_inlined_bytecodes = 0;

  _is_complete = false;
  _is_success = false;

  _hot_count = hot_count;
  _time_queued = os::elapsed_counter();
  _time_started = 0;
  _compile_reason = compile_reason;
  _nm_content_size = 0;
  AbstractCompiler* comp = compiler();
  _directive = DirectivesStack::getMatchingDirective(method, comp);
  _nm_insts_size = 0;
  _nm_total_size = 0;
  _failure_reason = nullptr;
  _failure_reason_on_C_heap = false;
  _training_data = nullptr;
  _arena_bytes = 0;

  _next = nullptr;

  Atomic::add(&_active_tasks, 1, memory_order_relaxed);
}

CompileTask::~CompileTask() {
  if (_method_holder != nullptr && JNIHandles::is_weak_global_handle(_method_holder)) {
    JNIHandles::destroy_weak_global(_method_holder);
  } else {
    JNIHandles::destroy_global(_method_holder);
  }
  if (_failure_reason_on_C_heap && _failure_reason != nullptr) {
    os::free((void*) _failure_reason);
    _failure_reason = nullptr;
    _failure_reason_on_C_heap = false;
  }

  if (Atomic::sub(&_active_tasks, 1, memory_order_relaxed) == 0) {
    MonitorLocker wait_ml(CompileTaskWait_lock);
    wait_ml.notify_all();
  }
}

void CompileTask::wait_for_no_active_tasks() {
  MonitorLocker locker(CompileTaskWait_lock);
  while (Atomic::load(&_active_tasks) > 0) {
    locker.wait();
  }
}

/**
 * Returns the compiler for this task.
 */
AbstractCompiler* CompileTask::compiler() const {
  return CompileBroker::compiler(_comp_level);
}

// Replace weak handles by strong handles to avoid unloading during compilation.
CompileTask* CompileTask::select_for_compilation() {
  if (is_unloaded()) {
    // Guard against concurrent class unloading
    return nullptr;
  }
  Thread* thread = Thread::current();
  assert(_method->method_holder()->is_loader_alive(), "should be alive");
  Handle method_holder(thread, _method->method_holder()->klass_holder());
  JNIHandles::destroy_weak_global(_method_holder);
  _method_holder = JNIHandles::make_global(method_holder);
  return this;
}

void CompileTask::mark_on_stack() {
  if (is_unloaded()) {
    return;
  }
  // Mark these methods as something redefine classes cannot remove.
  _method->set_on_stack(true);
}

bool CompileTask::is_unloaded() const {
  return _method_holder != nullptr && JNIHandles::is_weak_global_handle(_method_holder) && JNIHandles::is_weak_global_cleared(_method_holder);
}

// RedefineClasses support
void CompileTask::metadata_do(MetadataClosure* f) {
  if (is_unloaded()) {
    return;
  }
  f->do_metadata(method());
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
  print(tty);
}

// ------------------------------------------------------------------
// CompileTask::print_impl
void CompileTask::print_impl(outputStream* st, Method* method, int compile_id, int comp_level,
                             bool is_osr_method, int osr_bci, bool is_blocking,
                             const char* msg, bool short_form, bool cr,
                             jlong time_queued, jlong time_started) {
  if (!short_form) {
    // Print current time
    st->print(UINT64_FORMAT " ", (uint64_t) tty->time_stamp().milliseconds());
    if (Verbose && time_queued != 0) {
      // Print time in queue and time being processed by compiler thread
      jlong now = os::elapsed_counter();
      st->print("%.0f ", TimeHelper::counter_to_millis(now-time_queued));
      if (time_started != 0) {
        st->print("%.0f ", TimeHelper::counter_to_millis(now-time_started));
      }
    }
  }
  // print compiler name if requested
  if (CIPrintCompilerName) {
    st->print("%s:", CompileBroker::compiler_name(comp_level));
  }
  st->print("%4d ", compile_id);    // print compilation number

  bool is_synchronized = false;
  bool has_exception_handler = false;
  bool is_native = false;
  if (method != nullptr) {
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

  if (method == nullptr) {
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

  if (msg != nullptr) {
    st->print("   %s", msg);
  }
  if (cr) {
    st->cr();
  }
}

// ------------------------------------------------------------------
// CompileTask::print_compilation
void CompileTask::print(outputStream* st, const char* msg, bool short_form, bool cr) {
  bool is_osr_method = osr_bci() != InvocationEntryBci;
  print_impl(st, is_unloaded() ? nullptr : method(), compile_id(), comp_level(), is_osr_method, osr_bci(), is_blocking(), msg, short_form, cr, _time_queued, _time_started);
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
  if (!method.is_null())  log->method(method());
  if (_osr_bci != CompileBroker::standard_entry_bci) {
    log->print(" osr_bci='%d'", _osr_bci);
  }
  if (_comp_level != CompilationPolicy::highest_compile_level()) {
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
  ttyLocker ttyl;
  ResourceMark rm;
  NoSafepointVerifier nsv;

  xtty->begin_elem("task_queued");
  log_task(xtty);
  assert(_compile_reason > CompileTask::Reason_None && _compile_reason < CompileTask::Reason_Count, "Valid values");
  xtty->print(" comment='%s'", reason_name(_compile_reason));

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

  if (!_is_success) {
    assert(_failure_reason != nullptr, "missing");
    const char* reason = _failure_reason != nullptr ? _failure_reason : "unknown";
    log->begin_elem("failure reason='");
    log->text("%s", reason);
    log->print("'");
    log->end_elem();
  }

  // <task_done ... stamp='1.234'>  </task>
  log->begin_elem("task_done success='%d' nmsize='%d' count='%d'",
                  _is_success, _nm_content_size,
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
  log->flush();
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
void CompileTask::print_inlining_inner(outputStream* st, ciMethod* method, int inline_level, int bci, InliningResult result, const char* msg) {
  print_inlining_header(st, method, inline_level, bci);
  print_inlining_inner_message(st, result, msg);
  st->cr();
}

void CompileTask::print_inlining_header(outputStream* st, ciMethod* method, int inline_level, int bci) {
  //         1234567
  st->print("        "); // print timestamp
  //         1234
  st->print("     "); // print compilation number

  // method attributes
  if (method->is_loaded()) {
    const char sync_char = method->is_synchronized() ? 's' : ' ';
    const char exception_char = method->has_exception_handlers() ? '!' : ' ';
    const char monitors_char = method->has_monitor_bytecodes() ? 'm' : ' ';

    // print method attributes
    st->print(" %c%c%c  ", sync_char, exception_char, monitors_char);
  } else {
    //         %s!bn
    st->print("      "); // print method attributes
  }

  if (TieredCompilation) {
    st->print("  ");
  }
  st->print("     "); // more indent
  st->print("    ");  // initial inlining indent

  for (int i = 0; i < inline_level; i++) {
    st->print("  ");
  }

  st->print("@ %d  ", bci); // print bci
  print_inline_inner_method_info(st, method);
}

void CompileTask::print_inline_inner_method_info(outputStream* st, ciMethod* method) {
  method->print_short_name(st);
  if (method->is_loaded()) {
    st->print(" (%d bytes)", method->code_size());
  } else {
    st->print(" (not loaded)");
  }
}

void CompileTask::print_inline_indent(int inline_level, outputStream* st) {
  //         1234567
  st->print("        "); // print timestamp
  //         1234
  st->print("     "); // print compilation number
  //         %s!bn
  st->print("      "); // print method attributes
  if (TieredCompilation) {
    st->print("  ");
  }
  st->print("     "); // more indent
  st->print("    ");  // initial inlining indent
  for (int i = 0; i < inline_level; i++) {
    st->print("  ");
  }
}

void CompileTask::print_inlining_inner_message(outputStream* st, InliningResult result, const char* msg) {
  if (msg != nullptr) {
    st->print("   %s%s", result == InliningResult::SUCCESS ? "" : "failed to inline: ", msg);
  } else if (result == InliningResult::FAILURE) {
    st->print("   %s", "failed to inline");
  }
}

void CompileTask::print_ul(const char* msg){
  LogTarget(Info, jit, compilation) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    print(&ls, msg, /* short form */ true, /* cr */ true);
  }
}

void CompileTask::print_ul(const nmethod* nm, const char* msg) {
  LogTarget(Info, jit, compilation) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    print_impl(&ls, nm->method(), nm->compile_id(),
               nm->comp_level(), nm->is_osr_method(),
               nm->is_osr_method() ? nm->osr_entry_bci() : -1,
               /*is_blocking*/ false,
               msg, /* short form */ true, /* cr */ true);
  }
}

void CompileTask::print_inlining_ul(ciMethod* method, int inline_level, int bci, InliningResult result, const char* msg) {
  LogTarget(Debug, jit, inlining) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    print_inlining_inner(&ls, method, inline_level, bci, result, msg);
  }
}

