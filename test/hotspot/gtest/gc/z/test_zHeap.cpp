/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/vmClasses.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "memory/resourceArea.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/ostream.hpp"
#include "unittest.hpp"

static void check_print_location(uintptr_t address, const char* prefix) {
  SCOPED_TRACE(prefix);
  stringStream st;
  ResourceArea* area = Thread::current()->resource_area();
  const size_t used_before = area->used();
  const bool found = ZHeap::heap()->print_location(&st, address);
  const size_t used_after = area->used();

  EXPECT_TRUE(found);
  EXPECT_THAT(st.base(), testing::HasSubstr(prefix));
  EXPECT_THAT(st.base(), testing::HasSubstr("java.lang.Byte"));
  EXPECT_EQ(used_before, used_after);
}

TEST_VM(ZHeap, print_location_resource_mark) {
  if (!UseZGC) {
    GTEST_SKIP() << "ZGC is required";
  }

  JavaThread* THREAD = JavaThread::current();
  ThreadInVMfromNative invm(THREAD);
  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);
  oop obj = vmClasses::Byte_klass()->allocate_instance(THREAD);
  ASSERT_FALSE(HAS_PENDING_EXCEPTION);
  Handle h_obj(THREAD, obj);

  MutexLocker lock(ClassLoaderDataGraph_lock);
  // Ensure that the resource arena has a chunk before querying its used size.
  (void)NEW_RESOURCE_ARRAY(char, 1);
  const zaddress addr = to_zaddress(h_obj());
  check_print_location(untype(addr), "is a zaddress:");
  check_print_location(untype(ZAddress::store_good(addr)), "is a good zpointer:");
}
