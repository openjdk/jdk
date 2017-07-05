/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_SOLARIS_VM_OSTHREAD_SOLARIS_HPP
#define OS_SOLARIS_VM_OSTHREAD_SOLARIS_HPP

// This is embedded via include into the class OSThread
 public:
  typedef thread_t thread_id_t;

 private:
  uint     _lwp_id;            // lwp ID, only used with bound threads
  int      _native_priority;   // Saved native priority when starting
                               // a bound thread
  sigset_t _caller_sigmask;    // Caller's signal mask
  bool     _vm_created_thread; // true if the VM created this thread,
                               // false if primary thread or attached thread
 public:
  uint     lwp_id() const          { return _lwp_id; }
  int      native_priority() const { return _native_priority; }

  // Set and get state of _vm_created_thread flag
  void set_vm_created()           { _vm_created_thread = true; }
  bool is_vm_created()            { return _vm_created_thread; }

  // Methods to save/restore caller's signal mask
  sigset_t  caller_sigmask() const       { return _caller_sigmask; }
  void    set_caller_sigmask(sigset_t sigmask)  { _caller_sigmask = sigmask; }

#ifndef PRODUCT
  // Used for debugging, return a unique integer for each thread.
  int thread_identifier() const   { return _thread_id; }
#endif
#ifdef ASSERT
  // On solaris reposition can fail in two ways:
  // 1: a mismatched pc, because signal is delivered too late, target thread
  //    is resumed.
  // 2: on a timeout where signal is lost, target thread is resumed.
  bool valid_reposition_failure() {
    // only 1 and 2 can happen and we can handle both of them
    return true;
  }
#endif
  void set_lwp_id(uint id)           { _lwp_id = id; }
  void set_native_priority(int prio) { _native_priority = prio; }

 // ***************************************************************
 // interrupt support.  interrupts (using signals) are used to get
 // the thread context (get_thread_pc), to set the thread context
 // (set_thread_pc), and to implement java.lang.Thread.interrupt.
 // ***************************************************************

 public:
  os::SuspendResume sr;

 private:
  ucontext_t* _ucontext;

 public:
  ucontext_t* ucontext() const { return _ucontext; }
  void set_ucontext(ucontext_t* ptr) { _ucontext = ptr; }
  static void SR_handler(Thread* thread, ucontext_t* uc);

 // ***************************************************************
 // java.lang.Thread.interrupt state.
 // ***************************************************************

 private:

  JavaThreadState      _saved_interrupt_thread_state;       // the thread state before a system call -- restored afterward

 public:


  JavaThreadState   saved_interrupt_thread_state()                              { return _saved_interrupt_thread_state; }
  void              set_saved_interrupt_thread_state(JavaThreadState state)     { _saved_interrupt_thread_state = state; }

  static void       handle_spinlock_contention(int tries);                      // Used for thread local eden locking

  // ***************************************************************
  // Platform dependent initialization and cleanup
  // ***************************************************************

private:

  void pd_initialize();
  void pd_destroy();

#endif // OS_SOLARIS_VM_OSTHREAD_SOLARIS_HPP
