/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/interfaceSupport.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "unittest.hpp"

TEST_VM(SymbolTable, temp_new_symbol) {
  // Assert messages assume these symbols are unique, and the refcounts start at
  // one, but code does not rely on this.
  JavaThread* THREAD = JavaThread::current();
  // the thread should be in vm to use locks
  ThreadInVMfromNative ThreadInVMfromNative(THREAD);

  Symbol* abc = SymbolTable::new_symbol("abc", CATCH);
  int abccount = abc->refcount();
  TempNewSymbol ss = abc;
  ASSERT_EQ(ss->refcount(), abccount) << "only one abc";
  ASSERT_EQ(ss->refcount(), abc->refcount()) << "should match TempNewSymbol";

  Symbol* efg = SymbolTable::new_symbol("efg", CATCH);
  Symbol* hij = SymbolTable::new_symbol("hij", CATCH);
  int efgcount = efg->refcount();
  int hijcount = hij->refcount();

  TempNewSymbol s1 = efg;
  TempNewSymbol s2 = hij;
  ASSERT_EQ(s1->refcount(), efgcount) << "one efg";
  ASSERT_EQ(s2->refcount(), hijcount) << "one hij";

  // Assignment operator
  s1 = s2;
  ASSERT_EQ(hij->refcount(), hijcount + 1) << "should be two hij";
  ASSERT_EQ(efg->refcount(), efgcount - 1) << "should be no efg";

  s1 = ss; // s1 is abc
  ASSERT_EQ(s1->refcount(), abccount + 1) << "should be two abc (s1 and ss)";
  ASSERT_EQ(hij->refcount(), hijcount) << "should only have one hij now (s2)";

  s1 = s1; // self assignment
  ASSERT_EQ(s1->refcount(), abccount + 1) << "should still be two abc (s1 and ss)";

  TempNewSymbol s3;
  Symbol* klm = SymbolTable::new_symbol("klm", CATCH);
  int klmcount = klm->refcount();
  s3 = klm; // assignment
  ASSERT_EQ(s3->refcount(), klmcount) << "only one klm now";

  Symbol* xyz = SymbolTable::new_symbol("xyz", CATCH);
  int xyzcount = xyz->refcount();
  { // inner scope
    TempNewSymbol s_inner = xyz;
  }
  ASSERT_EQ(xyz->refcount(), xyzcount - 1)
          << "Should have been decremented by dtor in inner scope";
}
