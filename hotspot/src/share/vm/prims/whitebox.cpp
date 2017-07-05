/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"

#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "prims/whitebox.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"

#ifndef SERIALGC
#include "gc_implementation/g1/concurrentMark.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/heapRegionRemSet.hpp"
#endif // !SERIALGC

bool WhiteBox::_used = false;

// Entry macro to transition from JNI to VM state.

#define WB_ENTRY(result_type, header) JNI_ENTRY(result_type, header)
#define WB_END JNI_END

// Definitions of functions exposed via Whitebox API

WB_ENTRY(jlong, WB_GetObjectAddress(JNIEnv* env, jobject o, jobject obj))
  return (jlong)(void*)JNIHandles::resolve(obj);
WB_END

WB_ENTRY(jint, WB_GetHeapOopSize(JNIEnv* env, jobject o))
  return heapOopSize;
WB_END

#ifndef SERIALGC
WB_ENTRY(jboolean, WB_G1IsHumongous(JNIEnv* env, jobject o, jobject obj))
  G1CollectedHeap* g1 = G1CollectedHeap::heap();
  oop result = JNIHandles::resolve(obj);
  const HeapRegion* hr = g1->heap_region_containing(result);
  return hr->isHumongous();
WB_END

WB_ENTRY(jlong, WB_G1NumFreeRegions(JNIEnv* env, jobject o))
  G1CollectedHeap* g1 = G1CollectedHeap::heap();
  size_t nr = g1->free_regions();
  return (jlong)nr;
WB_END

WB_ENTRY(jboolean, WB_G1InConcurrentMark(JNIEnv* env, jobject o))
  G1CollectedHeap* g1 = G1CollectedHeap::heap();
  ConcurrentMark* cm = g1->concurrent_mark();
  return cm->concurrent_marking_in_progress();
WB_END

WB_ENTRY(jint, WB_G1RegionSize(JNIEnv* env, jobject o))
  return (jint)HeapRegion::GrainBytes;
WB_END
#endif // !SERIALGC

#define CC (char*)

static JNINativeMethod methods[] = {
  {CC"getObjectAddress",   CC"(Ljava/lang/Object;)J", (void*)&WB_GetObjectAddress  },
  {CC"getHeapOopSize",     CC"()I",                   (void*)&WB_GetHeapOopSize    },
#ifndef SERIALGC
  {CC"g1InConcurrentMark", CC"()Z",                   (void*)&WB_G1InConcurrentMark},
  {CC"g1IsHumongous",      CC"(Ljava/lang/Object;)Z", (void*)&WB_G1IsHumongous     },
  {CC"g1NumFreeRegions",   CC"()J",                   (void*)&WB_G1NumFreeRegions  },
  {CC"g1RegionSize",       CC"()I",                   (void*)&WB_G1RegionSize      },
#endif // !SERIALGC
};

#undef CC

JVM_ENTRY(void, JVM_RegisterWhiteBoxMethods(JNIEnv* env, jclass wbclass))
  {
    if (WhiteBoxAPI) {
      // Make sure that wbclass is loaded by the null classloader
      instanceKlassHandle ikh = instanceKlassHandle(JNIHandles::resolve(wbclass)->klass());
      Handle loader(ikh->class_loader());
      if (loader.is_null()) {
        ThreadToNativeFromVM ttnfv(thread); // can't be in VM when we call JNI
        jint result = env->RegisterNatives(wbclass, methods, sizeof(methods)/sizeof(methods[0]));
        if (result == 0) {
          WhiteBox::set_used();
        }
      }
    }
  }
JVM_END
