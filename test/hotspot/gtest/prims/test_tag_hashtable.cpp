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
    t.add( p, 100);


    JvmtiTagMapEntry* entry = t.find(p);

    ASSERT_TRUE(entry != NULL);
    ASSERT_TRUE(entry->tag() == 100 ) ;


    t.add( p, 110);

    entry  = t.find( p);

    ASSERT_TRUE(entry != NULL);
    ASSERT_TRUE(entry->tag() == 110 ) ;

    t.remove(p);
    entry = t.find(p);

    ASSERT_TRUE(entry == NULL);



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
    t.add(p, 100);


    t.add(q, 200);


    ASSERT_TRUE(!t.is_empty());


    JvmtiTagMapEntry *entry = t.find(p);

    ASSERT_TRUE(entry != NULL);
    ASSERT_TRUE(entry->tag() == 100 ) ;

    entry = t.find(q);

    ASSERT_TRUE(entry != NULL);
    ASSERT_TRUE(entry->tag() == 200 ) ;

    t.remove(q);
    entry = t.find(q);
    ASSERT_TRUE(entry == NULL);

    t.remove(p);
    entry = t.find(p);
    ASSERT_TRUE(entry == NULL) ;

    ASSERT_TRUE(t.is_empty());


    t.rehash();

    t.add(p, 1000);
    t.add(q, 2000);

    EntryClosure ec;
    t.entry_iterate( &ec);
    ASSERT_EQ(ec.count, 2);

    t.clear();
    ASSERT_TRUE(t.is_empty());

    GrowableArray<jlong> deads;
    t.remove_dead_entries(&deads);
}