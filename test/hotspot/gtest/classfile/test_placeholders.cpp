/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderData.hpp"
#include "classfile/placeholders.hpp"
#include "classfile/symbolTable.hpp"
#include "oops/symbol.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "threadHelper.inline.hpp"
#include "unittest.hpp"

// Test that multiple threads calling handle_parallel_super_load don't underflow supername refcount.
TEST_VM(PlaceholderTable, supername) {
  JavaThread* THREAD = JavaThread::current();
  JavaThread* T2 = THREAD;
  // the thread should be in vm to use locks
  ThreadInVMfromNative tivfn(THREAD);

  // Assert messages assume these symbols are unique, and the refcounts start at one.
  Symbol* A = SymbolTable::new_symbol("abc2_8_2023_class");
  Symbol* D = SymbolTable::new_symbol("def2_8_2023_class");
  Symbol* super = SymbolTable::new_symbol("super2_8_2023_supername");
  Symbol* interf = SymbolTable::new_symbol("interface2_8_2023_supername");

  ClassLoaderData* loader_data = ClassLoaderData::the_null_class_loader_data();

  {
    MutexLocker ml(THREAD, SystemDictionary_lock);

    PlaceholderTable::classloadAction super_action = PlaceholderTable::LOAD_SUPER;
    PlaceholderTable::classloadAction define_action = PlaceholderTable::DEFINE_CLASS;

    // DefineClass A and D
    PlaceholderTable::find_and_add(A, loader_data, define_action, nullptr, THREAD);
    PlaceholderTable::find_and_add(D, loader_data, define_action, nullptr, T2);

    // Load interfaces first to get supername replaced
    PlaceholderTable::find_and_add(A, loader_data, super_action, interf, THREAD);
    PlaceholderTable::find_and_remove(A, loader_data, super_action, THREAD);

    PlaceholderTable::find_and_add(D, loader_data, super_action, interf, T2);
    PlaceholderTable::find_and_remove(D, loader_data, super_action, T2);

    ASSERT_EQ(interf->refcount(), 1) << "supername is replaced with null";

    // Add placeholder to the table for loading A and super, and D also loading super
    PlaceholderTable::find_and_add(A, loader_data, super_action, super, THREAD);
    PlaceholderTable::find_and_add(D, loader_data, super_action, super, T2);

    // Another thread comes in and finds A loading Super
    PlaceholderEntry* placeholder = PlaceholderTable::get_entry(A, loader_data);
    SymbolHandle supername = placeholder->supername();

    // Other thread is done before handle_parallel_super_load
    PlaceholderTable::find_and_remove(A, loader_data, super_action, THREAD);

    // if THREAD drops reference to supername (loading failed or class unloaded), we're left with
    // a supername without refcount
    super->decrement_refcount();

    // handle_parallel_super_load (same thread doesn't assert)
    PlaceholderTable::find_and_add(A, loader_data, super_action, supername, T2);

    // Refcount should be 3: one in table for class A, one in table for class D
    // and one locally with SymbolHandle keeping it alive
    placeholder = PlaceholderTable::get_entry(A, loader_data);
    supername = placeholder->supername();
    EXPECT_EQ(super->refcount(), 3) << "super class name refcount should be 3";

    // Second thread's done too
    PlaceholderTable::find_and_remove(D, loader_data, super_action, T2);

    // Other threads are done.
    PlaceholderTable::find_and_remove(A, loader_data, super_action, THREAD);

    // Remove A and D define_class placeholder
    PlaceholderTable::find_and_remove(A, loader_data, define_action, THREAD);
    PlaceholderTable::find_and_remove(D, loader_data, define_action, T2);

    placeholder = PlaceholderTable::get_entry(A, loader_data);
    ASSERT_TRUE(placeholder == nullptr) << "placeholder should be removed";
    placeholder = PlaceholderTable::get_entry(D, loader_data);
    ASSERT_TRUE(placeholder == nullptr) << "placeholder should be removed";

    EXPECT_EQ(super->refcount(), 1) << "super class name refcount should be 1 - kept alive in this scope";
  }

  EXPECT_EQ(A->refcount(), 1) << "first lass name refcount should be 1";
  EXPECT_EQ(D->refcount(), 1) << "second class name refcount should be 1";
  EXPECT_EQ(super->refcount(), 0) << "super class name refcount should be 0 - was unloaded.";

  // clean up temporary symbols
  A->decrement_refcount();
  D->decrement_refcount();
  interf->decrement_refcount();
}
