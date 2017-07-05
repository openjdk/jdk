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

#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"

#include "classfile/symbolTable.hpp"

#include "prims/whitebox.hpp"
#include "prims/wbtestmethods/parserTests.hpp"

#include "runtime/interfaceSupport.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"

#ifndef SERIALGC
#include "gc_implementation/g1/concurrentMark.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/heapRegionRemSet.hpp"
#endif // !SERIALGC

bool WhiteBox::_used = false;

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

//Some convenience methods to deal with objects from java
int WhiteBox::offset_for_field(const char* field_name, oop object,
    Symbol* signature_symbol) {
  assert(field_name != NULL && strlen(field_name) > 0, "Field name not valid");
  Thread* THREAD = Thread::current();

  //Get the class of our object
  klassOop arg_klass = object->klass();
  //Turn it into an instance-klass
  instanceKlass* ik = instanceKlass::cast(arg_klass);

  //Create symbols to look for in the class
  TempNewSymbol name_symbol = SymbolTable::lookup(field_name, (int) strlen(field_name),
      THREAD);

  //To be filled in with an offset of the field we're looking for
  fieldDescriptor fd;

  klassOop res = ik->find_field(name_symbol, signature_symbol, &fd);
  if (res == NULL) {
    tty->print_cr("Invalid layout of %s at %s", ik->external_name(),
        name_symbol->as_C_string());
    fatal("Invalid layout of preloaded class");
  }

  //fetch the field at the offset we've found
  int dest_offset = fd.offset();

  return dest_offset;
}


const char* WhiteBox::lookup_jstring(const char* field_name, oop object) {
  int offset = offset_for_field(field_name, object,
      vmSymbols::string_signature());
  oop string = object->obj_field(offset);
  const char* ret = java_lang_String::as_utf8_string(string);
  return ret;
}

bool WhiteBox::lookup_bool(const char* field_name, oop object) {
  int offset =
      offset_for_field(field_name, object, vmSymbols::bool_signature());
  bool ret = (object->bool_field(offset) == JNI_TRUE);
  return ret;
}


#define CC (char*)

static JNINativeMethod methods[] = {
  {CC"getObjectAddress",   CC"(Ljava/lang/Object;)J", (void*)&WB_GetObjectAddress  },
  {CC"getHeapOopSize",     CC"()I",                   (void*)&WB_GetHeapOopSize    },
  {CC "parseCommandLine",
      CC "(Ljava/lang/String;[Lsun/hotspot/parser/DiagnosticCommand;)[Ljava/lang/Object;",
      (void*) &WB_ParseCommandLine
  },
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
