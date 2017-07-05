/*
 * Copyright (c) 1999, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CI_CIUTILITIES_HPP
#define SHARE_VM_CI_CIUTILITIES_HPP

#include "ci/ciEnv.hpp"
#include "runtime/interfaceSupport.hpp"

// The following routines and definitions are used internally in the
// compiler interface.


// Add a ci native entry wrapper?

// Bring the compilation thread into the VM state.
#define VM_ENTRY_MARK                       \
  CompilerThread* thread=CompilerThread::current(); \
  ThreadInVMfromNative __tiv(thread);       \
  ResetNoHandleMark rnhm;                   \
  HandleMarkCleaner __hm(thread);           \
  Thread* THREAD = thread;                  \
  debug_only(VMNativeEntryWrapper __vew;)



// Bring the compilation thread into the VM state.  No handle mark.
#define VM_QUICK_ENTRY_MARK                 \
  CompilerThread* thread=CompilerThread::current(); \
  ThreadInVMfromNative __tiv(thread);       \
/*                                          \
 * [TODO] The NoHandleMark line does nothing but declare a function prototype \
 * The NoHandkeMark constructor is NOT executed. If the ()'s are   \
 * removed, causes the NoHandleMark assert to trigger. \
 * debug_only(NoHandleMark __hm();)         \
 */                                         \
  Thread* THREAD = thread;                  \
  debug_only(VMNativeEntryWrapper __vew;)


#define EXCEPTION_CONTEXT \
  CompilerThread* thread=CompilerThread::current(); \
  Thread* THREAD = thread;


#define CURRENT_ENV                         \
  ciEnv::current()

// where current thread is THREAD
#define CURRENT_THREAD_ENV                  \
  ciEnv::current(thread)

#define IS_IN_VM                            \
  ciEnv::is_in_vm()

#define ASSERT_IN_VM                        \
  assert(IS_IN_VM, "must be in vm state");

#define GUARDED_VM_ENTRY(action)            \
  {if (IS_IN_VM) { action } else { VM_ENTRY_MARK; { action }}}

#define GUARDED_VM_QUICK_ENTRY(action)      \
  {if (IS_IN_VM) { action } else { VM_QUICK_ENTRY_MARK; { action }}}

// Redefine this later.
#define KILL_COMPILE_ON_FATAL_(result)           \
  THREAD);                                       \
  if (HAS_PENDING_EXCEPTION) {                   \
    if (PENDING_EXCEPTION->klass() ==            \
        SystemDictionary::ThreadDeath_klass()) { \
      /* Kill the compilation. */                \
      fatal("unhandled ci exception");           \
      return (result);                           \
    }                                            \
    CLEAR_PENDING_EXCEPTION;                     \
    return (result);                             \
  }                                              \
  (void)(0

#define KILL_COMPILE_ON_ANY                      \
  THREAD);                                       \
  if (HAS_PENDING_EXCEPTION) {                   \
    fatal("unhandled ci exception");             \
    CLEAR_PENDING_EXCEPTION;                     \
  }                                              \
(void)(0


inline const char* bool_to_str(bool b) {
  return ((b) ? "true" : "false");
}

const char* basictype_to_str(BasicType t);
const char  basictype_to_char(BasicType t);

#endif // SHARE_VM_CI_CIUTILITIES_HPP
