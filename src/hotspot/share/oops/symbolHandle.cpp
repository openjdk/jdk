/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
#include "oops/symbolHandle.hpp"
#include "runtime/atomic.hpp"

Symbol* volatile TempSymbolCleanupDelayer::_queue[QueueSize] = {};
volatile uint TempSymbolCleanupDelayer::_index = 0;

// Keep this symbol alive for some time to allow for reuse.
// Temp symbols for the same string can often be created in quick succession,
// and this queue allows them to be reused instead of churning.
void TempSymbolCleanupDelayer::delay_cleanup(Symbol* sym) {
  assert(sym != nullptr, "precondition");
  sym->increment_refcount();
  uint i = Atomic::add(&_index, 1u) % QueueSize;
  Symbol* old = Atomic::xchg(&_queue[i], sym);
  Symbol::maybe_decrement_refcount(old);
}

void TempSymbolCleanupDelayer::drain_queue() {
  for (uint i = 0; i < QueueSize; i++) {
    Symbol* sym = Atomic::xchg(&_queue[i], (Symbol*) nullptr);
    Symbol::maybe_decrement_refcount(sym);
  }
}
