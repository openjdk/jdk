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
 *
 */

#include "precompiled.hpp"

#ifndef PRODUCT

#include "classfile/altHashing.hpp"
#include "compiler/directivesParser.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcTimer.hpp"
#include "memory/guardedMemory.hpp"
#include "utilities/internalVMTests.hpp"
#include "utilities/json.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#include "utilities/quickSort.hpp"
#if INCLUDE_ALL_GCS
#include "gc/g1/heapRegionRemSet.hpp"
#endif
#if INCLUDE_VM_STRUCTS
#include "runtime/vmStructs.hpp"
#endif

#define run_unit_test(unit_test_function_call)              \
  tty->print_cr("Running test: " #unit_test_function_call); \
  unit_test_function_call

// Forward declaration
void TestDependencyContext_test();
void test_semaphore();
void TestOS_test();
void TestReservedSpace_test();
void TestReserveMemorySpecial_test();
void TestVirtualSpace_test();
void TestMetaspaceAux_test();
void TestMetachunk_test();
void TestVirtualSpaceNode_test();
void TestNewSize_test();
void TestOldSize_test();
void TestKlass_test();
void TestBitMap_test();
void TestAsUtf8();
void Test_linked_list();
void TestResourcehash_test();
void TestChunkedList_test();
void Test_log_length();
void Test_TempNewSymbol();
#if INCLUDE_ALL_GCS
void TestOldFreeSpaceCalculation_test();
void TestG1BiasedArray_test();
void TestBufferingOopClosure_test();
void TestCodeCacheRemSet_test();
void FreeRegionList_test();
void IHOP_test();
void test_memset_with_concurrent_readers() NOT_DEBUG_RETURN;
void TestPredictions_test();
void WorkerDataArray_test();
#endif

void InternalVMTests::run() {
  tty->print_cr("Running internal VM tests");
  run_unit_test(TestDependencyContext_test());
  run_unit_test(test_semaphore());
  run_unit_test(TestOS_test());
  run_unit_test(TestReservedSpace_test());
  run_unit_test(TestReserveMemorySpecial_test());
  run_unit_test(TestVirtualSpace_test());
  run_unit_test(TestMetaspaceAux_test());
  run_unit_test(TestMetachunk_test());
  run_unit_test(TestVirtualSpaceNode_test());
  run_unit_test(GlobalDefinitions::test_globals());
  run_unit_test(GCTimerAllTest::all());
  run_unit_test(arrayOopDesc::test_max_array_length());
  run_unit_test(CollectedHeap::test_is_in());
  run_unit_test(QuickSort::test_quick_sort());
  run_unit_test(GuardedMemory::test_guarded_memory());
  run_unit_test(AltHashing::test_alt_hash());
  run_unit_test(TestNewSize_test());
  run_unit_test(TestOldSize_test());
  run_unit_test(TestKlass_test());
  run_unit_test(TestBitMap_test());
  run_unit_test(TestAsUtf8());
  run_unit_test(TestResourcehash_test());
  run_unit_test(ObjectMonitor::sanity_checks());
  run_unit_test(Test_linked_list());
  run_unit_test(TestChunkedList_test());
  run_unit_test(JSONTest::test());
  run_unit_test(Test_log_length());
  run_unit_test(DirectivesParser::test());
  run_unit_test(Test_TempNewSymbol());
#if INCLUDE_VM_STRUCTS
  run_unit_test(VMStructs::test());
#endif
#if INCLUDE_ALL_GCS
  run_unit_test(TestOldFreeSpaceCalculation_test());
  run_unit_test(TestG1BiasedArray_test());
  run_unit_test(TestBufferingOopClosure_test());
  run_unit_test(TestCodeCacheRemSet_test());
  if (UseG1GC) {
    run_unit_test(FreeRegionList_test());
    run_unit_test(IHOP_test());
  }
  run_unit_test(test_memset_with_concurrent_readers());
  run_unit_test(TestPredictions_test());
  run_unit_test(WorkerDataArray_test());
#endif
  tty->print_cr("All internal VM tests passed");
}

#endif
