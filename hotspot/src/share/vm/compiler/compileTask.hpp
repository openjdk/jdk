/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_COMPILER_COMPILETASK_HPP
#define SHARE_VM_COMPILER_COMPILETASK_HPP

#include "code/nmethod.hpp"
#include "ci/ciMethod.hpp"
#include "compiler/compileLog.hpp"
#include "memory/allocation.inline.hpp"
#include "utilities/xmlstream.hpp"

// CompileTask
//
// An entry in the compile queue.  It represents a pending or current
// compilation.

class CompileTask : public CHeapObj<mtCompiler> {
  friend class VMStructs;
  friend class JVMCIVMStructs;

 public:
  // Different reasons for a compilation
  // The order is important - Reason_Whitebox and higher can not become
  // stale, see CompileTask::can_become_stale()
  // Also mapped to reason_names[]
  enum CompileReason {
      Reason_None,
      Reason_InvocationCount,  // Simple/StackWalk-policy
      Reason_BackedgeCount,    // Simple/StackWalk-policy
      Reason_Tiered,           // Tiered-policy
      Reason_CTW,              // Compile the world
      Reason_Replay,           // ciReplay
      Reason_Whitebox,         // Whitebox API
      Reason_MustBeCompiled,   // Java callHelper, LinkResolver
      Reason_Bootstrap,        // JVMCI bootstrap
      Reason_Count
  };

  static const char* reason_name(CompileTask::CompileReason compile_reason) {
    static const char* reason_names[] = {
      "no_reason",
      "count",
      "backedge_count",
      "tiered",
      "CTW",
      "replay",
      "whitebox",
      "must_be_compiled",
      "bootstrap"
    };
    return reason_names[compile_reason];
  }

 private:
  static CompileTask* _task_free_list;
#ifdef ASSERT
  static int          _num_allocated_tasks;
#endif

  Monitor*     _lock;
  uint         _compile_id;
  Method*      _method;
  jobject      _method_holder;
  int          _osr_bci;
  bool         _is_complete;
  bool         _is_success;
  bool         _is_blocking;
#if INCLUDE_JVMCI
  bool         _has_waiter;
  // Compiler thread for a blocking JVMCI compilation
  CompilerThread* _jvmci_compiler_thread;
#endif
  int          _comp_level;
  int          _num_inlined_bytecodes;
  nmethodLocker* _code_handle;  // holder of eventual result
  CompileTask* _next, *_prev;
  bool         _is_free;
  // Fields used for logging why the compilation was initiated:
  jlong        _time_queued;  // in units of os::elapsed_counter()
  Method*      _hot_method;   // which method actually triggered this task
  jobject      _hot_method_holder;
  int          _hot_count;    // information about its invocation counter
  CompileReason _compile_reason;      // more info about the task
  const char*  _failure_reason;

 public:
  CompileTask() {
    _lock = new Monitor(Mutex::nonleaf+2, "CompileTaskLock");
  }

  void initialize(int compile_id, const methodHandle& method, int osr_bci, int comp_level,
                  const methodHandle& hot_method, int hot_count,
                  CompileTask::CompileReason compile_reason, bool is_blocking);

  static CompileTask* allocate();
  static void         free(CompileTask* task);

  int          compile_id() const                { return _compile_id; }
  Method*      method() const                    { return _method; }
  Method*      hot_method() const                { return _hot_method; }
  int          osr_bci() const                   { return _osr_bci; }
  bool         is_complete() const               { return _is_complete; }
  bool         is_blocking() const               { return _is_blocking; }
  bool         is_success() const                { return _is_success; }
  bool         can_become_stale() const          {
    switch (_compile_reason) {
      case Reason_BackedgeCount:
      case Reason_InvocationCount:
      case Reason_Tiered:
        return !_is_blocking;
    }
    return false;
  }
#if INCLUDE_JVMCI
  bool         has_waiter() const                { return _has_waiter; }
  void         clear_waiter()                    { _has_waiter = false; }
  CompilerThread* jvmci_compiler_thread() const  { return _jvmci_compiler_thread; }
  void         set_jvmci_compiler_thread(CompilerThread* t) {
    assert(is_blocking(), "must be");
    assert((t == NULL) != (_jvmci_compiler_thread == NULL), "must be");
    _jvmci_compiler_thread = t;
  }
#endif

  nmethodLocker* code_handle() const             { return _code_handle; }
  void         set_code_handle(nmethodLocker* l) { _code_handle = l; }
  nmethod*     code() const;                     // _code_handle->code()
  void         set_code(nmethod* nm);            // _code_handle->set_code(nm)

  Monitor*     lock() const                      { return _lock; }

  void         mark_complete()                   { _is_complete = true; }
  void         mark_success()                    { _is_success = true; }

  int          comp_level()                      { return _comp_level;}
  void         set_comp_level(int comp_level)    { _comp_level = comp_level;}

  AbstractCompiler* compiler();

  int          num_inlined_bytecodes() const     { return _num_inlined_bytecodes; }
  void         set_num_inlined_bytecodes(int n)  { _num_inlined_bytecodes = n; }

  CompileTask* next() const                      { return _next; }
  void         set_next(CompileTask* next)       { _next = next; }
  CompileTask* prev() const                      { return _prev; }
  void         set_prev(CompileTask* prev)       { _prev = prev; }
  bool         is_free() const                   { return _is_free; }
  void         set_is_free(bool val)             { _is_free = val; }

  // RedefineClasses support
  void         metadata_do(void f(Metadata*));
  void         mark_on_stack();

private:
  static void  print_impl(outputStream* st, Method* method, int compile_id, int comp_level,
                                      bool is_osr_method = false, int osr_bci = -1, bool is_blocking = false,
                                      const char* msg = NULL, bool short_form = false, bool cr = true);

public:
  void         print(outputStream* st = tty, const char* msg = NULL, bool short_form = false, bool cr = true);
  static void  print(outputStream* st, const nmethod* nm, const char* msg = NULL, bool short_form = false, bool cr = true) {
    print_impl(st, nm->method(), nm->compile_id(), nm->comp_level(),
                           nm->is_osr_method(), nm->is_osr_method() ? nm->osr_entry_bci() : -1, /*is_blocking*/ false,
                           msg, short_form, cr);
  }

  static void  print_inline_indent(int inline_level, outputStream* st = tty);

  void         print_tty();
  void         print_line_on_error(outputStream* st, char* buf, int buflen);

  void         log_task(xmlStream* log);
  void         log_task_queued();
  void         log_task_start(CompileLog* log);
  void         log_task_done(CompileLog* log);

  void         set_failure_reason(const char* reason) {
    _failure_reason = reason;
  }

  bool         check_break_at_flags();

  static void print_inlining_inner(outputStream* st, ciMethod* method, int inline_level, int bci, const char* msg = NULL);
  static void print_inlining_tty(ciMethod* method, int inline_level, int bci, const char* msg = NULL) {
    print_inlining_inner(tty, method, inline_level, bci, msg);
  }
};

#endif // SHARE_VM_COMPILER_COMPILETASK_HPP
