/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_c1_Compiler.cpp.incl"

volatile int Compiler::_runtimes = uninitialized;

volatile bool Compiler::_compiling = false;


Compiler::Compiler() {
}


Compiler::~Compiler() {
  Unimplemented();
}


void Compiler::initialize() {
  if (_runtimes != initialized) {
    initialize_runtimes( Runtime1::initialize, &_runtimes);
  }
  mark_initialized();
}


void Compiler::compile_method(ciEnv* env, ciMethod* method, int entry_bci) {

  if (!is_initialized()) {
    initialize();
  }
  // invoke compilation
#ifdef TIERED
  // We are thread in native here...
  CompilerThread* thread = CompilerThread::current();
  {
    ThreadInVMfromNative tv(thread);
    MutexLocker only_one (C1_lock, thread);
    while ( _compiling) {
      C1_lock->wait();
    }
    _compiling = true;
  }
#endif // TIERED
  {
    // We are nested here because we need for the destructor
    // of Compilation to occur before we release the any
    // competing compiler thread
    ResourceMark rm;
    Compilation c(this, env, method, entry_bci);
  }
#ifdef TIERED
  {
    ThreadInVMfromNative tv(thread);
    MutexLocker only_one (C1_lock, thread);
    _compiling = false;
    C1_lock->notify();
  }
#endif // TIERED
}


void Compiler::print_timers() {
  Compilation::print_timers();
}
