/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/symbolTable.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "threadHelper.inline.hpp"
#include "unittest.hpp"

// Helper to avoid interference from the cleanup delay queue by draining it
// immediately after creation.
static TempNewSymbol stable_temp_symbol(Symbol* sym) {
  TempNewSymbol t = sym;
  TempSymbolCleanupDelayer::drain_queue();
  return t;
}

TEST_VM(SymbolTable, temp_new_symbol) {
  // Assert messages assume these symbols are unique, and the refcounts start at
  // one, but code does not rely on this.
  JavaThread* THREAD = JavaThread::current();
  // the thread should be in vm to use locks
  ThreadInVMfromNative ThreadInVMfromNative(THREAD);

  Symbol* abc = SymbolTable::new_symbol("abc");
  int abccount = abc->refcount();
  TempNewSymbol ss = stable_temp_symbol(abc);
  ASSERT_EQ(ss->refcount(), abccount) << "only one abc";
  ASSERT_EQ(ss->refcount(), abc->refcount()) << "should match TempNewSymbol";

  Symbol* efg = SymbolTable::new_symbol("efg");
  Symbol* hij = SymbolTable::new_symbol("hij");
  int efgcount = efg->refcount();
  int hijcount = hij->refcount();

  TempNewSymbol s1 = stable_temp_symbol(efg);
  TempNewSymbol s2 = stable_temp_symbol(hij);
  ASSERT_EQ(s1->refcount(), efgcount) << "one efg";
  ASSERT_EQ(s2->refcount(), hijcount) << "one hij";

  // Assignment operator
  s1 = s2;
  ASSERT_EQ(hij->refcount(), hijcount + 1) << "should be two hij";
  ASSERT_EQ(efg->refcount(), efgcount - 1) << "should be no efg";

  s1 = ss; // s1 is abc
  ASSERT_EQ(s1->refcount(), abccount + 1) << "should be two abc (s1 and ss)";
  ASSERT_EQ(hij->refcount(), hijcount) << "should only have one hij now (s2)";

  s1 = *&s1; // self assignment
  ASSERT_EQ(s1->refcount(), abccount + 1) << "should still be two abc (s1 and ss)";

  TempNewSymbol s3;
  Symbol* klm = SymbolTable::new_symbol("klm");
  int klmcount = klm->refcount();
  s3 = stable_temp_symbol(klm); // assignment
  ASSERT_EQ(s3->refcount(), klmcount) << "only one klm now";

  Symbol* xyz = SymbolTable::new_symbol("xyz");
  int xyzcount = xyz->refcount();
  { // inner scope
    TempNewSymbol s_inner = stable_temp_symbol(xyz);
  }
  ASSERT_EQ(xyz->refcount(), xyzcount - 1)
          << "Should have been decremented by dtor in inner scope";

  // Test overflowing refcount making symbol permanent
  Symbol* bigsym = SymbolTable::new_symbol("bigsym");
  for (int i = 0; i < PERM_REFCOUNT + 100; i++) {
    bigsym->increment_refcount();
  }
  ASSERT_EQ(bigsym->refcount(), PERM_REFCOUNT) << "should not have overflowed";

  // Test that PERM_REFCOUNT is sticky
  for (int i = 0; i < 10; i++) {
    bigsym->decrement_refcount();
  }
  ASSERT_EQ(bigsym->refcount(), PERM_REFCOUNT) << "should be sticky";
}

// TODO: Make two threads one decrementing the refcount and the other trying to increment.
// try_increment_refcount should return false

TEST_VM(SymbolTable, test_symbol_refcount_parallel) {
  constexpr int symbol_name_length = 30;
  char symbol_name[symbol_name_length];
  // Find a symbol where there will probably be only one instance.
  for (int i = 0; i < 100; i++) {
    os::snprintf(symbol_name, symbol_name_length, "some_symbol%d", i);
    TempNewSymbol ts = SymbolTable::new_symbol(symbol_name);
    if (ts->refcount() == 1) {
      EXPECT_TRUE(ts->refcount() == 1) << "Symbol is just created";
      break;  // found a unique symbol
    }
  }

  constexpr int symTestThreadCount = 5;
  auto symbolThread= [&](Thread* _current, int _id) {
    for (int i = 0; i < 1000; i++) {
      TempNewSymbol sym = SymbolTable::new_symbol(symbol_name);
      // Create and destroy new symbol
      EXPECT_TRUE(sym->refcount() != 0) << "Symbol refcount unexpectedly zeroed";
    }
  };
  TestThreadGroup<decltype(symbolThread)> ttg(symbolThread, symTestThreadCount);
  ttg.doit();
  ttg.join();
}

TEST_VM_FATAL_ERROR_MSG(SymbolTable, test_symbol_underflow, ".*refcount has gone to zero.*") {
  Symbol* my_symbol = SymbolTable::new_symbol("my_symbol2023");
  EXPECT_TRUE(my_symbol->refcount() == 1) << "Symbol refcount just created is 1";
  my_symbol->decrement_refcount();
  my_symbol->increment_refcount();  // Should crash even in PRODUCT mode
}

TEST_VM(SymbolTable, test_cleanup_leak) {
  // Check that dead entry cleanup doesn't increment refcount of live entry in same bucket.

  // Create symbol and release ref, marking it available for cleanup.
  Symbol* entry1 = SymbolTable::new_symbol("hash_collision_123");
  entry1->decrement_refcount();

  // Create a new symbol in the same bucket, which will notice the dead entry and trigger cleanup.
  // Note: relies on SymbolTable's use of String::hashCode which collides for these two values.
  Symbol* entry2 = SymbolTable::new_symbol("hash_collision_397476851");

  ASSERT_EQ(entry2->refcount(), 1) << "Symbol refcount just created is 1";
}

TEST_VM(SymbolTable, test_cleanup_delay) {
  // Check that new temp symbols have an extra refcount increment, which is then
  // decremented when the queue spills over.

  TempNewSymbol s1 = SymbolTable::new_symbol("temp-s1");
  ASSERT_EQ(s1->refcount(), 2) << "TempNewSymbol refcount just created is 2";

  // Fill up the queue
  constexpr int symbol_name_length = 30;
  char symbol_name[symbol_name_length];
  for (uint i = 1; i < TempSymbolCleanupDelayer::QueueSize; i++) {
    os::snprintf(symbol_name, symbol_name_length, "temp-filler-%d", i);
    TempNewSymbol s = SymbolTable::new_symbol(symbol_name);
    ASSERT_EQ(s->refcount(), 2) << "TempNewSymbol refcount just created is 2";
  }

  // Add one more
  TempNewSymbol spillover = SymbolTable::new_symbol("temp-spillover");
  ASSERT_EQ(spillover->refcount(), 2) << "TempNewSymbol refcount just created is 2";

  // The first symbol should have been removed from the queue and decremented
  ASSERT_EQ(s1->refcount(), 1) << "TempNewSymbol off queue refcount is 1";
}

TEST_VM(SymbolTable, test_cleanup_delay_drain) {
  // Fill up the queue
  constexpr int symbol_name_length = 30;
  char symbol_name[symbol_name_length];
  TempNewSymbol symbols[TempSymbolCleanupDelayer::QueueSize] = {};
  for (uint i = 0; i < TempSymbolCleanupDelayer::QueueSize; i++) {
    os::snprintf(symbol_name, symbol_name_length, "temp-%d", i);
    TempNewSymbol s = SymbolTable::new_symbol(symbol_name);
    symbols[i] = s;
  }

  // While in the queue refcounts are incremented
  for (uint i = 0; i < TempSymbolCleanupDelayer::QueueSize; i++) {
    ASSERT_EQ(symbols[i]->refcount(), 2) << "TempNewSymbol refcount in queue is 2";
  }

  // Draining the queue should decrement the refcounts
  TempSymbolCleanupDelayer::drain_queue();
  for (uint i = 0; i < TempSymbolCleanupDelayer::QueueSize; i++) {
    ASSERT_EQ(symbols[i]->refcount(), 1) << "TempNewSymbol refcount after drain is 1";
  }
}
