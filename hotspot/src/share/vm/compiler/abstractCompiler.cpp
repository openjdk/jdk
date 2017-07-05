//
// Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
// DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
//
// This code is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License version 2 only, as
// published by the Free Software Foundation.
//
// This code is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
// version 2 for more details (a copy is included in the LICENSE file that
// accompanied this code).
//
// You should have received a copy of the GNU General Public License version
// 2 along with this work; if not, write to the Free Software Foundation,
// Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
//
// Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
// or visit www.oracle.com if you need additional information or have any
// questions.
//


#include "precompiled.hpp"
#include "compiler/abstractCompiler.hpp"
#include "runtime/mutexLocker.hpp"
void AbstractCompiler::initialize_runtimes(initializer f, volatile int* state) {
  if (*state != initialized) {

    // We are thread in native here...
    CompilerThread* thread = CompilerThread::current();
    bool do_initialization = false;
    {
      ThreadInVMfromNative tv(thread);
      MutexLocker only_one(CompileThread_lock, thread);
      if ( *state == uninitialized) {
        do_initialization = true;
        *state = initializing;
      } else {
        while (*state == initializing ) {
          CompileThread_lock->wait();
        }
      }
    }
    if (do_initialization) {
      // We can not hold any locks here since JVMTI events may call agents

      // Compiler(s) run as native

      (*f)();

      // To in_vm so we can use the lock

      ThreadInVMfromNative tv(thread);
      MutexLocker only_one(CompileThread_lock, thread);
      assert(*state == initializing, "wrong state");
      *state = initialized;
      CompileThread_lock->notify_all();
    }
  }
}
