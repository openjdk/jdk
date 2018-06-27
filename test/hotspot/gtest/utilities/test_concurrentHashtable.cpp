/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/mutex.hpp"
#include "runtime/semaphore.hpp"
#include "runtime/thread.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vm_operations.hpp"
#include "utilities/concurrentHashTable.inline.hpp"
#include "utilities/concurrentHashTableTasks.inline.hpp"
#include "utilitiesHelper.inline.hpp"
#include "unittest.hpp"

// NOTE: On win32 gtest asserts are not mt-safe.
// Amusingly as long as they do not assert they are mt-safe.
#define SIZE_32 5

struct Pointer;

typedef ConcurrentHashTable<uintptr_t, Pointer, mtInternal> SimpleTestTable;
typedef ConcurrentHashTable<uintptr_t, Pointer, mtInternal>::MultiGetHandle SimpleTestGetHandle;

// Simplest working CRPT implementation for the hash-table.
struct Pointer : public SimpleTestTable::BaseConfig {
  static uintx get_hash(const uintptr_t& value, bool* dead_hash) {
    return (uintx)value;
  }
  static const uintptr_t& notfound() {
    static uintptr_t notfound = 0;
    return notfound;
  }
  static void* allocate_node(size_t size, const uintptr_t& value) {
    return ::malloc(size);
  }
  static void free_node(void* memory, const uintptr_t& value) {
    ::free(memory);
  }
};

struct SimpleTestLookup {
  uintptr_t _val;
  SimpleTestLookup(uintptr_t val) : _val(val) {}
  uintx get_hash() {
    return Pointer::get_hash(_val, NULL);
  }
  bool equals(const uintptr_t* value, bool* is_dead) {
    return _val == *value;
  }
};

static void cht_insert(Thread* thr) {
  uintptr_t val = 0x2;
  SimpleTestLookup stl(val);
  SimpleTestTable* cht = new SimpleTestTable();
  EXPECT_TRUE(cht->insert(thr, stl, val)) << "Insert unique value failed.";
  EXPECT_EQ(cht->get_copy(thr, stl), val) << "Getting an existing value failed.";
  EXPECT_TRUE(cht->remove(thr, stl)) << "Removing an existing value failed.";
  EXPECT_FALSE(cht->remove(thr, stl)) << "Removing an already removed item succeeded.";
  EXPECT_NE(cht->get_copy(thr, stl), val) << "Getting a removed value succeeded.";
  delete cht;
}

struct ValVerify {
  uintptr_t _val;
  bool called_get;
  bool called_insert;
  ValVerify(uintptr_t val) : called_get(false), called_insert(false), _val(val) {}
  void operator()(bool inserted, uintptr_t* val) {
    EXPECT_EQ(_val, *val) << "The value inserted is not correct.";
    if (inserted) {
      called_insert = true;
    } else {
      called_get = true;
    }
  }
  void verify(bool get, bool insert) {
    EXPECT_EQ(called_get, get) << "Get unexpected";
    EXPECT_EQ(called_insert, insert) << "Insert unexpected";
  }
};

static void cht_get_insert_helper(Thread* thr, SimpleTestTable* cht, uintptr_t val) {
  {
    SimpleTestLookup stl(val);
    ValVerify vv(val);
    EXPECT_EQ(cht->get_insert(thr, stl, val, vv), false) << "Inserting an unique value failed.";
    vv.verify(false, true);
  }

  {
    SimpleTestLookup stl(val);
    ValVerify vv(val);
    EXPECT_EQ(cht->get_insert(thr, stl, val, vv), true) << "Getting an old value failed.";
    vv.verify(true, false);
  }
}

static void cht_get_insert(Thread* thr) {
  uintptr_t val = 0x2;
  SimpleTestLookup stl(val);
  SimpleTestTable* cht = new SimpleTestTable();

  {
    SCOPED_TRACE("First");
    cht_get_insert_helper(thr, cht, val);
  }
  EXPECT_EQ(cht->get_copy(thr, stl), val) << "Get an old value failed";
  EXPECT_TRUE(cht->remove(thr, stl)) << "Removing existing value failed.";
  EXPECT_NE(cht->get_copy(thr, stl), val) << "Got an already removed item.";

  {
    SCOPED_TRACE("Second");
    cht_get_insert_helper(thr, cht, val);
  }

  delete cht;
}

static bool getinsert_bulkdelete_eval(uintptr_t* val) {
  EXPECT_TRUE(*val > 0 && *val < 4) << "Val wrong for this test.";
  return (*val & 0x1); // Delete all values ending with first bit set.
}

static void getinsert_bulkdelete_del(uintptr_t* val) {
  EXPECT_EQ(*val & 0x1, (uintptr_t)1) << "Deleting wrong value.";
}

static void cht_getinsert_bulkdelete_insert_verified(Thread* thr, SimpleTestTable* cht, uintptr_t val,
                                                     bool verify_expect_get, bool verify_expect_inserted) {
  ValVerify vv(val);
  SimpleTestLookup stl(val);
  EXPECT_EQ(cht->get_insert(thr, stl, val, vv), verify_expect_get) << "Inserting an unique value failed.";
  vv.verify(verify_expect_get, verify_expect_inserted);
}

static void cht_getinsert_bulkdelete(Thread* thr) {
  uintptr_t val1 = 1;
  uintptr_t val2 = 2;
  uintptr_t val3 = 3;
  SimpleTestLookup stl1(val1), stl2(val2), stl3(val3);

  SimpleTestTable* cht = new SimpleTestTable();
  cht_getinsert_bulkdelete_insert_verified(thr, cht, val1, false, true);
  cht_getinsert_bulkdelete_insert_verified(thr, cht, val2, false, true);
  cht_getinsert_bulkdelete_insert_verified(thr, cht, val3, false, true);

  EXPECT_TRUE(cht->remove(thr, stl2)) << "Remove did not find value.";

  cht_getinsert_bulkdelete_insert_verified(thr, cht, val1, true, false); // val1 should be present
  cht_getinsert_bulkdelete_insert_verified(thr, cht, val2, false, true); // val2 should be inserted
  cht_getinsert_bulkdelete_insert_verified(thr, cht, val3, true, false); // val3 should be present

  EXPECT_EQ(cht->get_copy(thr, stl1), val1) << "Get did not find value.";
  EXPECT_EQ(cht->get_copy(thr, stl2), val2) << "Get did not find value.";
  EXPECT_EQ(cht->get_copy(thr, stl3), val3) << "Get did not find value.";

  // Removes all odd values.
  cht->bulk_delete(thr, getinsert_bulkdelete_eval, getinsert_bulkdelete_del);

  EXPECT_EQ(cht->get_copy(thr, stl1), (uintptr_t)0) << "Odd value should not exist.";
  EXPECT_FALSE(cht->remove(thr, stl1)) << "Odd value should not exist.";
  EXPECT_EQ(cht->get_copy(thr, stl2), val2) << "Even value should not have been removed.";
  EXPECT_EQ(cht->get_copy(thr, stl3), (uintptr_t)0) << "Add value should not exists.";
  EXPECT_FALSE(cht->remove(thr, stl3)) << "Odd value should not exists.";

  delete cht;
}

static void cht_getinsert_bulkdelete_task(Thread* thr) {
  uintptr_t val1 = 1;
  uintptr_t val2 = 2;
  uintptr_t val3 = 3;
  SimpleTestLookup stl1(val1), stl2(val2), stl3(val3);

  SimpleTestTable* cht = new SimpleTestTable();
  cht_getinsert_bulkdelete_insert_verified(thr, cht, val1, false, true);
  cht_getinsert_bulkdelete_insert_verified(thr, cht, val2, false, true);
  cht_getinsert_bulkdelete_insert_verified(thr, cht, val3, false, true);

  EXPECT_TRUE(cht->remove(thr, stl2)) << "Remove did not find value.";

  cht_getinsert_bulkdelete_insert_verified(thr, cht, val1, true, false); // val1 should be present
  cht_getinsert_bulkdelete_insert_verified(thr, cht, val2, false, true); // val2 should be inserted
  cht_getinsert_bulkdelete_insert_verified(thr, cht, val3, true, false); // val3 should be present

  EXPECT_EQ(cht->get_copy(thr, stl1), val1) << "Get did not find value.";
  EXPECT_EQ(cht->get_copy(thr, stl2), val2) << "Get did not find value.";
  EXPECT_EQ(cht->get_copy(thr, stl3), val3) << "Get did not find value.";

  // Removes all odd values.
  SimpleTestTable::BulkDeleteTask bdt(cht);
  if (bdt.prepare(thr)) {
    while(bdt.do_task(thr, getinsert_bulkdelete_eval, getinsert_bulkdelete_del)) {
      bdt.pause(thr);
      bdt.cont(thr);
    }
    bdt.done(thr);
  }

  EXPECT_EQ(cht->get_copy(thr, stl1), (uintptr_t)0) << "Odd value should not exist.";
  EXPECT_FALSE(cht->remove(thr, stl1)) << "Odd value should not exist.";
  EXPECT_EQ(cht->get_copy(thr, stl2), val2) << "Even value should not have been removed.";
  EXPECT_EQ(cht->get_copy(thr, stl3), (uintptr_t)0) << "Add value should not exists.";
  EXPECT_FALSE(cht->remove(thr, stl3)) << "Odd value should not exists.";

  delete cht;
}

static void cht_scope(Thread* thr) {
  uintptr_t val = 0x2;
  SimpleTestLookup stl(val);
  SimpleTestTable* cht = new SimpleTestTable();
  EXPECT_TRUE(cht->insert(thr, stl, val)) << "Insert unique value failed.";
  {
    SimpleTestGetHandle get_handle(thr, cht);
    EXPECT_EQ(*get_handle.get(stl), val) << "Getting a pre-existing value failed.";
  }
  // We do remove here to make sure the value-handle 'unlocked' the table when leaving the scope.
  EXPECT_TRUE(cht->remove(thr, stl)) << "Removing a pre-existing value failed.";
  EXPECT_FALSE(cht->get_copy(thr, stl) == val) << "Got a removed value.";
  delete cht;
}

struct ChtScan {
  size_t _count;
  ChtScan() : _count(0) {}
  bool operator()(uintptr_t* val) {
    EXPECT_EQ(*val, (uintptr_t)0x2) << "Got an unknown value.";
    EXPECT_EQ(_count, 0u) << "Only one value should be in table.";
    _count++;
    return true; /* continue scan */
  }
};

static void cht_scan(Thread* thr) {
  uintptr_t val = 0x2;
  SimpleTestLookup stl(val);
  ChtScan scan;
  SimpleTestTable* cht = new SimpleTestTable();
  EXPECT_TRUE(cht->insert(thr, stl, val)) << "Insert unique value failed.";
  EXPECT_EQ(cht->try_scan(thr, scan), true) << "Scanning an non-growing/shrinking table should work.";
  EXPECT_TRUE(cht->remove(thr, stl)) << "Removing a pre-existing value failed.";
  EXPECT_FALSE(cht->get_copy(thr, stl) == val) << "Got a removed value.";
  delete cht;
}

struct ChtCountScan {
  size_t _count;
  ChtCountScan() : _count(0) {}
  bool operator()(uintptr_t* val) {
    _count++;
    return true; /* continue scan */
  }
};

static void cht_move_to(Thread* thr) {
  uintptr_t val1 = 0x2;
  uintptr_t val2 = 0xe0000002;
  uintptr_t val3 = 0x3;
  SimpleTestLookup stl1(val1), stl2(val2), stl3(val3);
  SimpleTestTable* from_cht = new SimpleTestTable();
  EXPECT_TRUE(from_cht->insert(thr, stl1, val1)) << "Insert unique value failed.";
  EXPECT_TRUE(from_cht->insert(thr, stl2, val2)) << "Insert unique value failed.";
  EXPECT_TRUE(from_cht->insert(thr, stl3, val3)) << "Insert unique value failed.";

  SimpleTestTable* to_cht = new SimpleTestTable();
  EXPECT_TRUE(from_cht->try_move_nodes_to(thr, to_cht)) << "Moving nodes to new table failed";

  ChtCountScan scan_old;
  EXPECT_TRUE(from_cht->try_scan(thr, scan_old)) << "Scanning table should work.";
  EXPECT_EQ(scan_old._count, (size_t)0) << "All items should be moved";

  ChtCountScan scan_new;
  EXPECT_TRUE(to_cht->try_scan(thr, scan_new)) << "Scanning table should work.";
  EXPECT_EQ(scan_new._count, (size_t)3) << "All items should be moved";
  EXPECT_TRUE(to_cht->get_copy(thr, stl1) == val1) << "Getting an inserted value should work.";
  EXPECT_TRUE(to_cht->get_copy(thr, stl2) == val2) << "Getting an inserted value should work.";
  EXPECT_TRUE(to_cht->get_copy(thr, stl3) == val3) << "Getting an inserted value should work.";
}

static void cht_grow(Thread* thr) {
  uintptr_t val = 0x2;
  uintptr_t val2 = 0x22;
  uintptr_t val3 = 0x222;
  SimpleTestLookup stl(val), stl2(val2), stl3(val3);
  SimpleTestTable* cht = new SimpleTestTable();

  EXPECT_TRUE(cht->insert(thr, stl, val)) << "Insert unique value failed.";
  EXPECT_TRUE(cht->insert(thr, stl2, val2)) << "Insert unique value failed.";
  EXPECT_TRUE(cht->insert(thr, stl3, val3)) << "Insert unique value failed.";
  EXPECT_FALSE(cht->insert(thr, stl3, val3)) << "Insert duplicate value should have failed.";
  EXPECT_TRUE(cht->get_copy(thr, stl) == val) << "Getting an inserted value should work.";
  EXPECT_TRUE(cht->get_copy(thr, stl2) == val2) << "Getting an inserted value should work.";
  EXPECT_TRUE(cht->get_copy(thr, stl3) == val3) << "Getting an inserted value should work.";

  EXPECT_TRUE(cht->remove(thr, stl2)) << "Removing an inserted value should work.";

  EXPECT_TRUE(cht->get_copy(thr, stl) == val) << "Getting an inserted value should work.";
  EXPECT_FALSE(cht->get_copy(thr, stl2) == val2) << "Getting a removed value should have failed.";
  EXPECT_TRUE(cht->get_copy(thr, stl3) == val3) << "Getting an inserted value should work.";


  EXPECT_TRUE(cht->grow(thr)) << "Growing uncontended should not fail.";

  EXPECT_TRUE(cht->get_copy(thr, stl) == val) << "Getting an item after grow failed.";
  EXPECT_FALSE(cht->get_copy(thr, stl2) == val2) << "Getting a removed value after grow should have failed.";
  EXPECT_TRUE(cht->get_copy(thr, stl3) == val3) << "Getting an item after grow failed.";

  EXPECT_TRUE(cht->insert(thr, stl2, val2)) << "Insert unique value failed.";
  EXPECT_TRUE(cht->remove(thr, stl3)) << "Removing an inserted value should work.";

  EXPECT_TRUE(cht->shrink(thr)) << "Shrinking uncontended should not fail.";

  EXPECT_TRUE(cht->get_copy(thr, stl) == val) << "Getting an item after shrink failed.";
  EXPECT_TRUE(cht->get_copy(thr, stl2) == val2) << "Getting an item after shrink failed.";
  EXPECT_FALSE(cht->get_copy(thr, stl3) == val3) << "Getting a removed value after shrink should have failed.";

  delete cht;
}

static void cht_task_grow(Thread* thr) {
  uintptr_t val = 0x2;
  uintptr_t val2 = 0x22;
  uintptr_t val3 = 0x222;
  SimpleTestLookup stl(val), stl2(val2), stl3(val3);
  SimpleTestTable* cht = new SimpleTestTable();

  EXPECT_TRUE(cht->insert(thr, stl, val)) << "Insert unique value failed.";
  EXPECT_TRUE(cht->insert(thr, stl2, val2)) << "Insert unique value failed.";
  EXPECT_TRUE(cht->insert(thr, stl3, val3)) << "Insert unique value failed.";
  EXPECT_FALSE(cht->insert(thr, stl3, val3)) << "Insert duplicate value should have failed.";
  EXPECT_TRUE(cht->get_copy(thr, stl) == val) << "Getting an inserted value should work.";
  EXPECT_TRUE(cht->get_copy(thr, stl2) == val2) << "Getting an inserted value should work.";
  EXPECT_TRUE(cht->get_copy(thr, stl3) == val3) << "Getting an inserted value should work.";

  EXPECT_TRUE(cht->remove(thr, stl2)) << "Removing an inserted value should work.";

  EXPECT_TRUE(cht->get_copy(thr, stl) == val) << "Getting an inserted value should work.";
  EXPECT_FALSE(cht->get_copy(thr, stl2) == val2) << "Getting a removed value should have failed.";
  EXPECT_TRUE(cht->get_copy(thr, stl3) == val3) << "Getting an inserted value should work.";

  SimpleTestTable::GrowTask gt(cht);
  EXPECT_TRUE(gt.prepare(thr)) << "Growing uncontended should not fail.";
  while(gt.do_task(thr)) { /* grow */  }
  gt.done(thr);

  EXPECT_TRUE(cht->get_copy(thr, stl) == val) << "Getting an item after grow failed.";
  EXPECT_FALSE(cht->get_copy(thr, stl2) == val2) << "Getting a removed value after grow should have failed.";
  EXPECT_TRUE(cht->get_copy(thr, stl3) == val3) << "Getting an item after grow failed.";

  EXPECT_TRUE(cht->insert(thr, stl2, val2)) << "Insert unique value failed.";
  EXPECT_TRUE(cht->remove(thr, stl3)) << "Removing an inserted value should work.";

  EXPECT_TRUE(cht->shrink(thr)) << "Shrinking uncontended should not fail.";

  EXPECT_TRUE(cht->get_copy(thr, stl) == val) << "Getting an item after shrink failed.";
  EXPECT_TRUE(cht->get_copy(thr, stl2) == val2) << "Getting an item after shrink failed.";
  EXPECT_FALSE(cht->get_copy(thr, stl3) == val3) << "Getting a removed value after shrink should have failed.";

  delete cht;
}

TEST_VM(ConcurrentHashTable, basic_insert) {
  nomt_test_doer(cht_insert);
}

TEST_VM(ConcurrentHashTable, basic_get_insert) {
  nomt_test_doer(cht_get_insert);
}

TEST_VM(ConcurrentHashTable, basic_scope) {
  nomt_test_doer(cht_scope);
}

TEST_VM(ConcurrentHashTable, basic_get_insert_bulk_delete) {
  nomt_test_doer(cht_getinsert_bulkdelete);
}

TEST_VM(ConcurrentHashTable, basic_get_insert_bulk_delete_task) {
  nomt_test_doer(cht_getinsert_bulkdelete_task);
}

TEST_VM(ConcurrentHashTable, basic_scan) {
  nomt_test_doer(cht_scan);
}

TEST_VM(ConcurrentHashTable, basic_move_to) {
  nomt_test_doer(cht_move_to);
}

TEST_VM(ConcurrentHashTable, basic_grow) {
  nomt_test_doer(cht_grow);
}

TEST_VM(ConcurrentHashTable, task_grow) {
  nomt_test_doer(cht_task_grow);
}

//#############################################################################################

class TestInterface;

typedef ConcurrentHashTable<uintptr_t, TestInterface, mtInternal> TestTable;
typedef ConcurrentHashTable<uintptr_t, TestInterface, mtInternal>::MultiGetHandle TestGetHandle;

class TestInterface : public TestTable::BaseConfig {
public:
  static uintx get_hash(const uintptr_t& value, bool* dead_hash) {
    return (uintx)(value + 18446744073709551557ul) * 18446744073709551557ul;
  }
  static const uintptr_t& notfound() {
    static uintptr_t notfound = 0;
    return notfound;
  }
};

struct TestLookup {
  uintptr_t _val;
  TestLookup(uintptr_t val) : _val(val) {}
  uintx get_hash() {
    return TestInterface::get_hash(_val, NULL);
  }
  bool equals(const uintptr_t* value, bool* is_dead) {
    return _val == *value;
  }
};

class CHTTestThread : public JavaTestThread {
  public:
  uintptr_t _start;
  uintptr_t _stop;
  TestTable *_cht;
  jlong _stop_ms;
  CHTTestThread(uintptr_t start, uintptr_t stop, TestTable* cht, Semaphore* post)
    : JavaTestThread(post), _start(start), _stop(stop), _cht(cht) {}
  virtual void premain() {}
  void main_run() {
    premain();
    _stop_ms = os::javaTimeMillis() + 2000; // 2 seconds max test time
    while (keep_looping() && test_loop()) { /* */ }
    postmain();
  }
  virtual void postmain() {}
  virtual bool keep_looping() {
    return _stop_ms > os::javaTimeMillis();
  };
  virtual bool test_loop() = 0;
  virtual ~CHTTestThread() {}
};

class ValueSaver {
  uintptr_t* _vals;
  size_t _it;
  size_t _size;
 public:
  ValueSaver() : _it(0), _size(1024) {
      _vals = NEW_C_HEAP_ARRAY(uintptr_t, _size, mtInternal);
  }

  bool operator()(uintptr_t* val) {
    _vals[_it++] = *val;
    if (_it == _size) {
      _size *= 2;
      _vals = REALLOC_RESOURCE_ARRAY(uintptr_t, _vals, _size/2, _size);
    }
    return true;
  }

  void check() {
    for (size_t i = 0; i < _it; i++) {
      size_t count = 0;
      for (size_t j = (i + 1u); j < _it; j++) {
        if (_vals[i] == _vals[j]) {
          count++;
        }
      }
      EXPECT_EQ(count, 0u);
    }
  }
};

static void integrity_check(Thread* thr, TestTable* cht)
{
  ValueSaver vs;
  cht->do_scan(thr, vs);
  vs.check();
}

//#############################################################################################
// All threads are working on different items
// This item should only be delete by this thread
// Thus get_unsafe is safe for this test.

class SimpleInserterThread : public CHTTestThread {
public:
  static volatile bool _exit;

  SimpleInserterThread(uintptr_t start, uintptr_t stop, TestTable* cht, Semaphore* post)
    : CHTTestThread(start, stop, cht, post) {};
  virtual ~SimpleInserterThread(){}

  bool keep_looping() {
    return !_exit;
  }

  bool test_loop() {
    bool grow;
    for (uintptr_t v = _start; v <= _stop; v++) {
      TestLookup tl(v);
      EXPECT_TRUE(_cht->insert(this, tl, v, &grow)) << "Inserting an unique value should work.";
    }
    for (uintptr_t v = _start; v <= _stop; v++) {
      TestLookup tl(v);
      EXPECT_TRUE(_cht->get_copy(this, tl) == v) << "Getting an previously inserted value unsafe failed.";
    }
    for (uintptr_t v = _start; v <= _stop; v++) {
      TestLookup tl(v);
      EXPECT_TRUE(_cht->remove(this, tl)) << "Removing an existing value failed.";
    }
    for (uintptr_t v = _start; v <= _stop; v++) {
      TestLookup tl(v);
      EXPECT_TRUE(_cht->get_copy(this, tl) == TestInterface::notfound()) << "Got a removed value.";
    }
    return true;
  }
};

volatile bool SimpleInserterThread::_exit = false;

class RunnerSimpleInserterThread : public CHTTestThread {
public:
  Semaphore _done;

  RunnerSimpleInserterThread(Semaphore* post) : CHTTestThread(0, 0, NULL, post) {
    _cht = new TestTable(SIZE_32, SIZE_32);
  };
  virtual ~RunnerSimpleInserterThread(){}

  void premain() {

    SimpleInserterThread* ins1 = new SimpleInserterThread((uintptr_t)0x100, (uintptr_t) 0x1FF, _cht, &_done);
    SimpleInserterThread* ins2 = new SimpleInserterThread((uintptr_t)0x200, (uintptr_t) 0x2FF, _cht, &_done);
    SimpleInserterThread* ins3 = new SimpleInserterThread((uintptr_t)0x300, (uintptr_t) 0x3FF, _cht, &_done);
    SimpleInserterThread* ins4 = new SimpleInserterThread((uintptr_t)0x400, (uintptr_t) 0x4FF, _cht, &_done);

    for (uintptr_t v = 0x500; v < 0x5FF; v++ ) {
      TestLookup tl(v);
      EXPECT_TRUE(_cht->insert(this, tl, v)) << "Inserting an unique value should work.";
    }

    ins1->doit();
    ins2->doit();
    ins3->doit();
    ins4->doit();

  }

  bool test_loop() {
    for (uintptr_t v = 0x500; v < 0x5FF; v++ ) {
      TestLookup tl(v);
      EXPECT_TRUE(_cht->get_copy(this, tl) == v) << "Getting an previously inserted value unsafe failed.";;
    }
    return true;
  }

  void postmain() {
    SimpleInserterThread::_exit = true;
    for (int i = 0; i < 4; i++) {
      _done.wait();
    }
    for (uintptr_t v = 0x500; v < 0x5FF; v++ ) {
      TestLookup tl(v);
      EXPECT_TRUE(_cht->remove(this, tl)) << "Removing an existing value failed.";
    }
    integrity_check(this, _cht);
    delete _cht;
  }
};


TEST_VM(ConcurrentHashTable, concurrent_simple) {
  SimpleInserterThread::_exit = false;
  mt_test_doer<RunnerSimpleInserterThread>();
}

//#############################################################################################
// In this test we try to get a 'bad' value
class DeleteInserterThread : public CHTTestThread {
public:
  static volatile bool _exit;

  DeleteInserterThread(uintptr_t start, uintptr_t stop, TestTable* cht, Semaphore* post) : CHTTestThread(start, stop, cht, post) {};
  virtual ~DeleteInserterThread(){}

  bool keep_looping() {
    return !_exit;
  }

  bool test_loop() {
    for (uintptr_t v = _start; v <= _stop; v++) {
      TestLookup tl(v);
      _cht->insert(this, tl, v);
    }
    for (uintptr_t v = _start; v <= _stop; v++) {
      TestLookup tl(v);
      _cht->remove(this, tl);
    }
    return true;
  }
};

volatile bool DeleteInserterThread::_exit = true;

class RunnerDeleteInserterThread : public CHTTestThread {
public:
  Semaphore _done;

  RunnerDeleteInserterThread(Semaphore* post) : CHTTestThread(0, 0, NULL, post) {
    _cht = new TestTable(SIZE_32, SIZE_32);
  };
  virtual ~RunnerDeleteInserterThread(){}

  void premain() {
    DeleteInserterThread* ins1 = new DeleteInserterThread((uintptr_t)0x1, (uintptr_t) 0xFFF, _cht, &_done);
    DeleteInserterThread* ins2 = new DeleteInserterThread((uintptr_t)0x1, (uintptr_t) 0xFFF, _cht, &_done);
    DeleteInserterThread* ins3 = new DeleteInserterThread((uintptr_t)0x1, (uintptr_t) 0xFFF, _cht, &_done);
    DeleteInserterThread* ins4 = new DeleteInserterThread((uintptr_t)0x1, (uintptr_t) 0xFFF, _cht, &_done);

    ins1->doit();
    ins2->doit();
    ins3->doit();
    ins4->doit();
  }

  bool test_loop() {
    for (uintptr_t v = 0x1; v < 0xFFF; v++ ) {
      uintptr_t tv;
      if (v & 0x1) {
        TestLookup tl(v);
        tv = _cht->get_copy(this, tl);
      } else {
        TestLookup tl(v);
        TestGetHandle value_handle(this, _cht);
        uintptr_t* tmp = value_handle.get(tl);
        tv = tmp != NULL ? *tmp : 0;
      }
      EXPECT_TRUE(tv == 0 || tv == v) << "Got unknown value.";
    }
    return true;
  }

  void postmain() {
    DeleteInserterThread::_exit = true;
    for (int i = 0; i < 4; i++) {
      _done.wait();
    }
    integrity_check(this, _cht);
    delete _cht;
  }
};

TEST_VM(ConcurrentHashTable, concurrent_deletes) {
  DeleteInserterThread::_exit = false;
  mt_test_doer<RunnerDeleteInserterThread>();
}

//#############################################################################################

#define START_SIZE 13
#define END_SIZE 17
#define START (uintptr_t)0x10000
#define RANGE (uintptr_t)0xFFFF

#define GSTEST_THREAD_COUNT 5


class GSInserterThread: public CHTTestThread {
public:
  static volatile bool _shrink;
  GSInserterThread(uintptr_t start, uintptr_t stop, TestTable* cht, Semaphore* post) : CHTTestThread(start, stop, cht, post) {};
  virtual ~GSInserterThread(){}
  bool keep_looping() {
    return !(_shrink && _cht->get_size_log2(this) == START_SIZE);
  }
  bool test_loop() {
    bool grow;
    for (uintptr_t v = _start; v <= _stop; v++) {
      TestLookup tl(v);
      EXPECT_TRUE(_cht->insert(this, tl, v, &grow)) << "Inserting an unique value should work.";
      if (grow && !_shrink) {
        _cht->grow(this);
      }
    }
    for (uintptr_t v = _start; v <= _stop; v++) {
      TestLookup tl(v);
      EXPECT_TRUE(_cht->get_copy(this, tl) == v) <<  "Getting an previously inserted value unsafe failed.";
    }
    for (uintptr_t v = _start; v <= _stop; v++) {
      TestLookup tl(v);
      EXPECT_TRUE(_cht->remove(this, tl)) << "Removing an existing value failed.";
    }
    if (_shrink) {
      _cht->shrink(this);
    }
    for (uintptr_t v = _start; v <= _stop; v++) {
      TestLookup tl(v);
      EXPECT_FALSE(_cht->get_copy(this, tl) == v)  << "Getting a removed value should have failed.";
    }
    if (!_shrink && _cht->get_size_log2(this) == END_SIZE) {
      _shrink = true;
    }
    return true;
  }
};

volatile bool GSInserterThread::_shrink = false;

class GSScannerThread : public CHTTestThread {
public:
  GSScannerThread(uintptr_t start, uintptr_t stop, TestTable* cht, Semaphore* post) : CHTTestThread(start, stop, cht, post) {};
  virtual ~GSScannerThread(){}

  bool operator()(uintptr_t* val) {
    if (*val >= this->_start && *val <= this->_stop) {
      return false;
    }
    // continue scan
    return true;
  }

  bool test_loop() {
    _cht->try_scan(this, *this);
    os::naked_short_sleep(5);
    return true;
  }
};

class RunnerGSInserterThread : public CHTTestThread {
public:
  uintptr_t _start;
  uintptr_t _range;
  Semaphore _done;

  RunnerGSInserterThread(Semaphore* post) : CHTTestThread(0, 0, NULL, post) {
    _cht = new TestTable(START_SIZE, END_SIZE, 2);
  };
  virtual ~RunnerGSInserterThread(){}

  void premain() {
    volatile bool timeout = false;
    _start = START;
    _range = RANGE;
    CHTTestThread* tt[GSTEST_THREAD_COUNT];
    tt[0] = new GSInserterThread(_start, _start + _range, _cht, &_done);
    _start += _range + 1;
    tt[1] = new GSInserterThread(_start, _start + _range, _cht, &_done);
    _start += _range + 1;
    tt[2] = new GSInserterThread(_start, _start + _range, _cht, &_done);
    _start += _range + 1;
    tt[3] = new GSInserterThread(_start, _start + _range, _cht, &_done);
    tt[4] = new GSScannerThread(_start, _start + _range, _cht, &_done);
    _start += _range + 1;


    for (uintptr_t v = _start; v <= (_start + _range); v++ ) {
      TestLookup tl(v);
      EXPECT_TRUE(_cht->insert(this, tl, v)) << "Inserting an unique value should work.";
    }

    for (int i = 0; i < GSTEST_THREAD_COUNT; i++) {
      tt[i]->doit();
    }
  }

  bool test_loop() {
    for (uintptr_t v = _start; v <= (_start + _range); v++ ) {
      TestLookup tl(v);
      EXPECT_TRUE(_cht->get_copy(this, tl) == v) <<  "Getting an previously inserted value unsafe failed.";
    }
    return true;
  }

  void postmain() {
    GSInserterThread::_shrink = true;
    for (uintptr_t v = _start; v <= (_start + _range); v++ ) {
      TestLookup tl(v);
      EXPECT_TRUE(_cht->remove(this, tl)) << "Removing an existing value failed.";
    }
    for (int i = 0; i < GSTEST_THREAD_COUNT; i++) {
      _done.wait();
    }
    EXPECT_TRUE(_cht->get_size_log2(this) == START_SIZE) << "Not at start size.";
    Count cnt;
    _cht->do_scan(this, cnt);
    EXPECT_TRUE(cnt._cnt == 0) << "Items still in table";
    delete _cht;
  }

  struct Count {
    Count() : _cnt(0) {}
    size_t _cnt;
    bool operator()(uintptr_t*) { _cnt++; return true; };
  };
};

TEST_VM(ConcurrentHashTable, concurrent_scan_grow_shrink) {
  GSInserterThread::_shrink = false;
  mt_test_doer<RunnerGSInserterThread>();
}


//#############################################################################################

#define GI_BD_GI_BD_START_SIZE 13
#define GI_BD_END_SIZE 17
#define GI_BD_START (uintptr_t)0x1
#define GI_BD_RANGE (uintptr_t)0x3FFFF

#define GI_BD_TEST_THREAD_COUNT 4


class GI_BD_InserterThread: public CHTTestThread {
public:
  static volatile bool _shrink;
  uintptr_t _br;
  GI_BD_InserterThread(uintptr_t start, uintptr_t stop, TestTable* cht, Semaphore* post, uintptr_t br)
    : CHTTestThread(start, stop, cht, post), _br(br) {};
  virtual ~GI_BD_InserterThread(){}

  bool keep_looping() {
    return !(_shrink && _cht->get_size_log2(this) == GI_BD_GI_BD_START_SIZE);
  }

  bool test_loop() {
    bool grow;
    MyDel del(_br);
    for (uintptr_t v = _start; v <= _stop; v++) {
      ValVerify vv(v);
      TestLookup tl(v);
      _cht->get_insert(this, tl, v, vv, &grow);
      EXPECT_NE(vv.called_get, vv.called_insert) << "Non or both callbacks was called.";
      if (grow && !_shrink) {
        _cht->grow(this);
      }
    }
    if (_shrink) {
      _cht->shrink(this);
    }
    _cht->try_bulk_delete(this, *this, del);
    if (!_shrink && _cht->is_max_size_reached()) {
      _shrink = true;
    }
    _cht->bulk_delete(this, *this, del);
    return true;
  }

  bool operator()(uintptr_t* val) {
    return (*val & _br) == 1;
  }

  struct MyDel {
    MyDel(uintptr_t &br) : _br(br) {};
    uintptr_t &_br;
    void operator()(uintptr_t* val) {
      EXPECT_EQ((*val & _br), _br) << "Removing an item that should not have been removed.";
    }
  };
};

volatile bool GI_BD_InserterThread::_shrink = false;

class RunnerGI_BD_InserterThread : public CHTTestThread {
public:
  Semaphore _done;
  uintptr_t _start;
  uintptr_t _range;
  RunnerGI_BD_InserterThread(Semaphore* post) : CHTTestThread(0, 0, NULL, post) {
    _cht = new TestTable(GI_BD_GI_BD_START_SIZE, GI_BD_END_SIZE, 2);
  };
  virtual ~RunnerGI_BD_InserterThread(){}

  void premain() {
    _start = GI_BD_START;
    _range = GI_BD_RANGE;
    CHTTestThread* tt[GI_BD_TEST_THREAD_COUNT];
    tt[0] = new GI_BD_InserterThread(_start, _start + _range, _cht, &_done, (uintptr_t)0x1);
    tt[1] = new GI_BD_InserterThread(_start, _start + _range, _cht, &_done, (uintptr_t)0x2);
    tt[2] = new GI_BD_InserterThread(_start, _start + _range, _cht, &_done, (uintptr_t)0x4);
    tt[3] = new GI_BD_InserterThread(_start, _start + _range, _cht, &_done, (uintptr_t)0x8);

    for (uintptr_t v = _start; v <= (_start + _range); v++ ) {
      TestLookup tl(v);
      EXPECT_TRUE(_cht->insert(this, tl, v)) << "Inserting an unique value should work.";
    }

    for (int i =0; i < GI_BD_TEST_THREAD_COUNT; i++) {
      tt[i]->doit();
    }
  }

  bool test_loop() {
    for (uintptr_t v = _start; v <= (_start + _range); v++ ) {
      TestLookup tl(v);
      if (v & 0xF) {
        _cht->get_copy(this, tl);
      } else {
        EXPECT_EQ(_cht->get_copy(this, tl), v) << "Item ending with 0xX0 should never be removed.";
      }
    }
    return true;
  }

  void postmain() {
    GI_BD_InserterThread::_shrink = true;
    for (uintptr_t v = _start; v <= (_start + _range); v++ ) {
      TestLookup tl(v);
      if (v & 0xF) {
        _cht->remove(this, tl);
      } else {
        EXPECT_TRUE(_cht->remove(this, tl)) << "Removing item ending with 0xX0 should always work.";
      }
    }
    for (int i = 0; i < GI_BD_TEST_THREAD_COUNT; i++) {
      _done.wait();
    }
    EXPECT_TRUE(_cht->get_size_log2(this) == GI_BD_GI_BD_START_SIZE) << "We have not shrunk back to start size.";
    delete _cht;
  }
};

TEST_VM(ConcurrentHashTable, concurrent_get_insert_bulk_delete) {
  GI_BD_InserterThread::_shrink = false;
  mt_test_doer<RunnerGI_BD_InserterThread>();
}

//#############################################################################################

class MT_BD_Thread : public JavaTestThread {
  TestTable::BulkDeleteTask* _bd;
  public:
  MT_BD_Thread(Semaphore* post, TestTable::BulkDeleteTask* bd)
    : JavaTestThread(post), _bd(bd){}
  virtual ~MT_BD_Thread() {}
  void main_run() {
    MyDel del;
    while(_bd->do_task(this, *this, del));
  }

  bool operator()(uintptr_t* val) {
    return true;
  }

  struct MyDel {
    void operator()(uintptr_t* val) {
    }
  };
};

class Driver_BD_Thread : public JavaTestThread {
public:
  Semaphore _done;
  Driver_BD_Thread(Semaphore* post) : JavaTestThread(post) {
  };
  virtual ~Driver_BD_Thread(){}

  void main_run() {
    Semaphore done(0);
    TestTable* cht = new TestTable(16, 16, 2);
    for (uintptr_t v = 1; v < 99999; v++ ) {
      TestLookup tl(v);
      EXPECT_TRUE(cht->insert(this, tl, v)) << "Inserting an unique value should work.";
    }
    TestTable::BulkDeleteTask bdt(cht, true /* mt */ );
    EXPECT_TRUE(bdt.prepare(this)) << "Uncontended prepare must work.";

    MT_BD_Thread* tt[4];
    for (int i = 0; i < 4; i++) {
      tt[i] = new MT_BD_Thread(&done, &bdt);
      tt[i]->doit();
    }

    for (uintptr_t v = 1; v < 99999; v++ ) {
      TestLookup tl(v);
      cht->get_copy(this, tl);
    }

    for (int i = 0; i < 4; i++) {
      done.wait();
    }

    bdt.done(this);

    cht->do_scan(this, *this);
  }

  bool operator()(uintptr_t* val) {
    EXPECT_TRUE(false) << "No items should left";
    return true;
  }
};

TEST_VM(ConcurrentHashTable, concurrent_mt_bulk_delete) {
  mt_test_doer<Driver_BD_Thread>();
}
