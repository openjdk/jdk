/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotGrowableArray.inline.hpp"
#include "memory/allocation.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "oops/array.hpp"
#include "oops/metadata.hpp"
#include "runtime/javaThread.hpp"
#include "unittest.hpp"

class MyMetaData : public MetaspaceObj {
public:
  MyMetaData* _a;
  MyMetaData* _b;

  MyMetaData() : _a(nullptr), _b(nullptr) {}

  MetaspaceObj::Type type() const {
    return MetaspaceObj::SymbolType; // Just lie. It doesn't matter in this test
  }
  const char* internal_name()  const {
    return "MyMetaData";
  }
  int size() const {
    return align_up((int)sizeof(MyMetaData), wordSize) / wordSize;
  };

  static bool is_read_only_by_default() {
    return true;
  }

  void metaspace_pointers_do(MetaspaceClosure* it) {
    it->push(&_a);
    it->push(&_b);
  }
};

class MyUniqueMetaspaceClosure : public MetaspaceClosure {
  static constexpr int SIZE = 10;
  void* _visited[SIZE];
  int _count;
public:
  MyUniqueMetaspaceClosure() {
    for (int i = 0; i < SIZE; i++) {
      _visited[i] = nullptr;
    }
    _count = 0;
  }

  virtual bool do_ref(Ref* ref, bool read_only) {
    MyMetaData* ptr = (MyMetaData*)ref->obj();
    assert(_count < SIZE, "out of bounds");
    for (int i = 0; i < _count; i++) {
      if (_visited[i] == (void*)ptr) {
        // We have walked this before.
        return false;
      }
    }

    // Found a new pointer. Let's walk it
    _visited[_count++] = (void*)ptr;
    return true; // recurse
  }

  bool has_visited(MyMetaData* p) {
    return has_visited((void*)p);
  }
  bool has_visited(void* p) {
    for (int i = 0; i < SIZE; i++) {
      if (_visited[i] == p) {
        return true;
      }
    }
    return false;
  }
  int visited_count() {
    return _count;
  }
};

// iterate an Array<MyMetaData*>
TEST_VM(MetaspaceClosure, MSOPointerArrayRef) {
  JavaThread* THREAD = JavaThread::current();
  ClassLoaderData* cld = ClassLoaderData::the_null_class_loader_data();
  Array<MyMetaData*>* array = MetadataFactory::new_array<MyMetaData*>(cld, 4, THREAD);
  for (int i = 0; i < array->length(); i++) {
    EXPECT_TRUE(array->at(i) == nullptr) << "should be initialized to null";
  }

  MyMetaData x;
  MyMetaData y;
  MyMetaData z;

  array->at_put(0, &x);
  array->at_put(2, &y);
  y._a = &z;

  MyUniqueMetaspaceClosure closure;
  closure.push(&array);
  closure.finish();

  EXPECT_TRUE(closure.has_visited(array)) << "must be";
  EXPECT_TRUE(closure.has_visited(&x)) << "must be";
  EXPECT_TRUE(closure.has_visited(&y)) << "must be";
  EXPECT_TRUE(closure.has_visited(&z)) << "must be";
}

// iterate an Array<MyMetaData>
TEST_VM(MetaspaceClosure, MSOArrayRef) {
  JavaThread* THREAD = JavaThread::current();
  ClassLoaderData* cld = ClassLoaderData::the_null_class_loader_data();
  Array<MyMetaData>* array = MetadataFactory::new_array<MyMetaData>(cld, 4, THREAD);
  for (int i = 0; i < array->length(); i++) {
    EXPECT_TRUE(array->at(i)._a == nullptr) << "should be initialized to null";
    EXPECT_TRUE(array->at(i)._b == nullptr) << "should be initialized to null";
  }

  MyMetaData x;
  MyMetaData y;
  MyMetaData z;

  array->adr_at(0)->_a = &x;
  array->adr_at(2)->_b = &y;
  y._a = &z;

  MyUniqueMetaspaceClosure closure;
  closure.push(&array);
  closure.finish();

  EXPECT_TRUE(closure.has_visited(array)) << "must be";
  EXPECT_TRUE(closure.has_visited(&x)) << "must be";
  EXPECT_TRUE(closure.has_visited(&y)) << "must be";
  EXPECT_TRUE(closure.has_visited(&z)) << "must be";
}

// iterate an Array<int>
TEST_VM(MetaspaceClosure, OtherArrayRef) {
  JavaThread* THREAD = JavaThread::current();
  ClassLoaderData* cld = ClassLoaderData::the_null_class_loader_data();
  Array<int>* array = MetadataFactory::new_array<int>(cld, 4, THREAD);

  MyUniqueMetaspaceClosure closure;
  closure.push(&array);
  closure.finish();

  EXPECT_TRUE(closure.has_visited(array)) << "must be";
}

// iterate an AOTGrowableArray<MyMetaData*>
TEST_VM(MetaspaceClosure, GrowableArray_MSOPointer) {
  AOTGrowableArray<MyMetaData*>* array = new(mtClass) AOTGrowableArray<MyMetaData*>(2, mtClass);

  MyMetaData x;
  MyMetaData y;
  MyMetaData z;

  array->push(&x);
  array->push(&y);
  y._a = &z;

  MyUniqueMetaspaceClosure closure;
  closure.push(&array);
  closure.finish();

  EXPECT_TRUE(closure.has_visited(array)) << "must be";
  EXPECT_TRUE(closure.has_visited(&x)) << "must be";
  EXPECT_TRUE(closure.has_visited(&y)) << "must be";
  EXPECT_TRUE(closure.has_visited(&z)) << "must be";
}

// iterate an AOTGrowableArray<MyMetaData>
TEST_VM(MetaspaceClosure, GrowableArray_MSO) {
  AOTGrowableArray<MyMetaData>* array = new(mtClass) AOTGrowableArray<MyMetaData>(4, mtClass);

  for (int i = 0; i < array->length(); i++) {
    EXPECT_TRUE(array->at(i)._a == nullptr) << "should be initialized to null";
    EXPECT_TRUE(array->at(i)._b == nullptr) << "should be initialized to null";
  }

  MyMetaData x;
  MyMetaData y;
  MyMetaData z;

  z._a = &x;
  z._b = &y;
  y._a = &z;
  array->push(z);

  MyUniqueMetaspaceClosure closure;
  closure.push(&array);
  closure.finish();

  EXPECT_TRUE(closure.has_visited(array)) << "must be";
  EXPECT_TRUE(closure.has_visited(&x)) << "must be";
  EXPECT_TRUE(closure.has_visited(&y)) << "must be";
  EXPECT_TRUE(closure.has_visited(&z)) << "must be";
}

// iterate an AOTGrowableArray<jlong>
TEST_VM(MetaspaceClosure, GrowableArray_jlong) {
  AOTGrowableArray<jlong>* array = new(mtClass) AOTGrowableArray<jlong>(4, mtClass);

  MyUniqueMetaspaceClosure closure;
  closure.push(&array);
  closure.finish();

  EXPECT_TRUE(closure.has_visited(array)) << "must be";
  EXPECT_TRUE(closure.visited_count() == 2) << "must visit buffer inside GrowableArray";
}
