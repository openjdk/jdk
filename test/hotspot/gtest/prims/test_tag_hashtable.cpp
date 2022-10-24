/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "jvmti.h"
#include "memory/oopFactory.hpp"
#include "oops/oop.inline.hpp"
#include "precompiled.hpp"
#include "prims/jvmtiTagMapTable.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/safepoint.hpp"
#include "threadHelper.inline.hpp"
#include "unittest.hpp"
#include "utilities/globalDefinitions.hpp"

class EntryClosure : public JvmtiTagMapEntryClosure
{
 public:
    int count=0;
    void do_entry(JvmtiTagMapEntry* entry)
    {
        count++;
    }
};
TEST_VM(Jvmti_TagMapTable, AddUpdateRemove){
  JavaThread* thr = JavaThread::current();
  ThreadInVMfromNative invm(thr);
  ResourceMark rm(thr);

  oop obj = vmClasses::Byte_klass()->allocate_instance(thr);

  //FlagSetting fs(WizardMode, true);

  HandleMark hm(thr);
  Handle h_obj(thr, obj);

    oop p = vmClasses::Byte_klass()->allocate_instance(thr);
    oop q = vmClasses::Byte_klass()->allocate_instance(thr);
    ASSERT_TRUE(p != NULL);
    ASSERT_TRUE(q != NULL);
    JvmtiTagMapTable t;

    ASSERT_TRUE(t.is_empty());
    int what_is_done = t.add_update_remove( p, 100);
    ASSERT_EQ(what_is_done , JvmtiTagMapTable::AddUpdateRemove::Added);

    JvmtiTagMapEntry entry( p, 0 );
    bool found = t.find(entry, p);

    ASSERT_TRUE(found);
    ASSERT_TRUE(entry.tag() == 100 ) ;


    what_is_done = t.add_update_remove( p, 110);
    ASSERT_EQ(what_is_done , JvmtiTagMapTable::AddUpdateRemove::Updated);

    found = t.find(entry, p);

    ASSERT_TRUE(found);
    ASSERT_TRUE(entry.tag() == 110 ) ;

    what_is_done = t.add_update_remove( p, 0);
    ASSERT_EQ(what_is_done , JvmtiTagMapTable::AddUpdateRemove::Removed);


    found = t.find(entry, p);

    ASSERT_TRUE(!found);



}

TEST_VM(Jvmti_TagMapTable, CallingAllAPI){
  JavaThread* thr = JavaThread::current();
  ThreadInVMfromNative invm(thr);
  ResourceMark rm(thr);

  oop obj = vmClasses::Byte_klass()->allocate_instance(thr);

  //FlagSetting fs(WizardMode, true);

  HandleMark hm(thr);
  Handle h_obj(thr, obj);

    oop p = vmClasses::Byte_klass()->allocate_instance(thr);
    oop q = vmClasses::Byte_klass()->allocate_instance(thr);
    ASSERT_TRUE(p != NULL);
    ASSERT_TRUE(q != NULL);

    JvmtiTagMapTable t;

    ASSERT_TRUE(t.is_empty());
    int what_is_done = t.add_update_remove( p, 100);
    ASSERT_EQ(what_is_done , JvmtiTagMapTable::AddUpdateRemove::Added);

    what_is_done = t.add_update_remove( q, 200);
    ASSERT_EQ(what_is_done , JvmtiTagMapTable::AddUpdateRemove::Added);

    ASSERT_TRUE(!t.is_empty());


    JvmtiTagMapEntry entry( p, 0);
    bool found = t.find(entry, p);

    ASSERT_TRUE(found);
    ASSERT_TRUE(entry.tag() == 100 ) ;

    found = t.find(entry, q);

    ASSERT_TRUE(found);
    ASSERT_TRUE(entry.tag() == 200 ) ;

    t.remove(q);
    found = t.find(entry, q);
    ASSERT_TRUE(!found) ;

    t.remove(p);fflush(stderr);
    found = t.find(entry, p);
    ASSERT_TRUE(!found) ;

    ASSERT_TRUE(t.is_empty());


    t.rehash();

    what_is_done = t.add_update_remove( p, 1000);
    what_is_done = t.add_update_remove( q, 2000);




    EntryClosure ec;
    t.entry_iterate( &ec);
    ASSERT_EQ(ec.count, 2);

    t.clear();
    ASSERT_TRUE(t.is_empty());

    GrowableArray<jlong> deads;
    t.remove_dead_entries(&deads);
}