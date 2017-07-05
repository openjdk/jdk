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
#include "classfile/classLoaderData.hpp"

#include "prims/whitebox.hpp"
#include "prims/wbtestmethods/parserTests.hpp"

#include "runtime/interfaceSupport.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"

#if INCLUDE_ALL_GCS
#include "gc_implementation/g1/concurrentMark.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/heapRegionRemSet.hpp"
#endif // INCLUDE_ALL_GCS

#ifdef INCLUDE_NMT
#include "services/memTracker.hpp"
#endif // INCLUDE_NMT

#include "compiler/compileBroker.hpp"

bool WhiteBox::_used = false;

WB_ENTRY(jlong, WB_GetObjectAddress(JNIEnv* env, jobject o, jobject obj))
  return (jlong)(void*)JNIHandles::resolve(obj);
WB_END

WB_ENTRY(jint, WB_GetHeapOopSize(JNIEnv* env, jobject o))
  return heapOopSize;
WB_END


class WBIsKlassAliveClosure : public KlassClosure {
    Symbol* _name;
    bool _found;
public:
    WBIsKlassAliveClosure(Symbol* name) : _name(name), _found(false) {}

    void do_klass(Klass* k) {
      if (_found) return;
      Symbol* ksym = k->name();
      if (ksym->fast_compare(_name) == 0) {
        _found = true;
      }
    }

    bool found() const {
        return _found;
    }
};

WB_ENTRY(jboolean, WB_IsClassAlive(JNIEnv* env, jobject target, jstring name))
  Handle h_name = JNIHandles::resolve(name);
  if (h_name.is_null()) return false;
  Symbol* sym = java_lang_String::as_symbol(h_name, CHECK_false);
  TempNewSymbol tsym(sym); // Make sure to decrement reference count on sym on return

  WBIsKlassAliveClosure closure(sym);
  ClassLoaderDataGraph::classes_do(&closure);

  return closure.found();
WB_END

#if INCLUDE_ALL_GCS
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
#endif // INCLUDE_ALL_GCS

#ifdef INCLUDE_NMT
// Keep track of the 3 allocations in NMTAllocTest so we can free them later
// on and verify that they're not visible anymore
static void* nmtMtTest1 = NULL, *nmtMtTest2 = NULL, *nmtMtTest3 = NULL;

// Alloc memory using the test memory type so that we can use that to see if
// NMT picks it up correctly
WB_ENTRY(jboolean, WB_NMTAllocTest(JNIEnv* env))
  void *mem;

  if (!MemTracker::is_on() || MemTracker::shutdown_in_progress()) {
    return false;
  }

  // Allocate 2 * 128k + 256k + 1024k and free the 1024k one to make sure we track
  // everything correctly. Total should be 512k held alive.
  nmtMtTest1 = os::malloc(128 * 1024, mtTest);
  mem = os::malloc(1024 * 1024, mtTest);
  nmtMtTest2 = os::malloc(256 * 1024, mtTest);
  os::free(mem, mtTest);
  nmtMtTest3 = os::malloc(128 * 1024, mtTest);

  return true;
WB_END

// Free the memory allocated by NMTAllocTest
WB_ENTRY(jboolean, WB_NMTFreeTestMemory(JNIEnv* env))

  if (nmtMtTest1 == NULL || nmtMtTest2 == NULL || nmtMtTest3 == NULL) {
    return false;
  }

  os::free(nmtMtTest1, mtTest);
  nmtMtTest1 = NULL;
  os::free(nmtMtTest2, mtTest);
  nmtMtTest2 = NULL;
  os::free(nmtMtTest3, mtTest);
  nmtMtTest3 = NULL;

  return true;
WB_END

// Block until the current generation of NMT data to be merged, used to reliably test the NMT feature
WB_ENTRY(jboolean, WB_NMTWaitForDataMerge(JNIEnv* env))

  if (!MemTracker::is_on() || MemTracker::shutdown_in_progress()) {
    return false;
  }

  return MemTracker::wbtest_wait_for_data_merge();
WB_END

#endif // INCLUDE_NMT

static jmethodID reflected_method_to_jmid(JavaThread* thread, JNIEnv* env, jobject method) {
  assert(method != NULL, "method should not be null");
  ThreadToNativeFromVM ttn(thread);
  return env->FromReflectedMethod(method);
}

WB_ENTRY(void, WB_DeoptimizeAll(JNIEnv* env, jobject o))
  MutexLockerEx mu(Compile_lock);
  CodeCache::mark_all_nmethods_for_deoptimization();
  VM_Deoptimize op;
  VMThread::execute(&op);
WB_END

WB_ENTRY(jint, WB_DeoptimizeMethod(JNIEnv* env, jobject o, jobject method))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  MutexLockerEx mu(Compile_lock);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  int result = 0;
  nmethod* code = mh->code();
  if (code != NULL) {
    code->mark_for_deoptimization();
    ++result;
  }
  result += CodeCache::mark_for_deoptimization(mh());
  if (result > 0) {
    VM_Deoptimize op;
    VMThread::execute(&op);
  }
  return result;
WB_END

WB_ENTRY(jboolean, WB_IsMethodCompiled(JNIEnv* env, jobject o, jobject method))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  MutexLockerEx mu(Compile_lock);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  nmethod* code = mh->code();
  if (code == NULL) {
    return JNI_FALSE;
  }
  return (code->is_alive() && !code->is_marked_for_deoptimization());
WB_END

WB_ENTRY(jboolean, WB_IsMethodCompilable(JNIEnv* env, jobject o, jobject method))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  MutexLockerEx mu(Compile_lock);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  return !mh->is_not_compilable();
WB_END

WB_ENTRY(jboolean, WB_IsMethodQueuedForCompilation(JNIEnv* env, jobject o, jobject method))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  MutexLockerEx mu(Compile_lock);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  return mh->queued_for_compilation();
WB_END

WB_ENTRY(jint, WB_GetMethodCompilationLevel(JNIEnv* env, jobject o, jobject method))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  nmethod* code = mh->code();
  return (code != NULL ? code->comp_level() : CompLevel_none);
WB_END


WB_ENTRY(void, WB_MakeMethodNotCompilable(JNIEnv* env, jobject o, jobject method))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  mh->set_not_compilable();
WB_END

WB_ENTRY(jboolean, WB_SetDontInlineMethod(JNIEnv* env, jobject o, jobject method, jboolean value))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  bool result = mh->dont_inline();
  mh->set_dont_inline(value == JNI_TRUE);
  return result;
WB_END

WB_ENTRY(jint, WB_GetCompileQueuesSize(JNIEnv* env, jobject o))
  return CompileBroker::queue_size(CompLevel_full_optimization) /* C2 */ +
         CompileBroker::queue_size(CompLevel_full_profile) /* C1 */;
WB_END

WB_ENTRY(jboolean, WB_IsInStringTable(JNIEnv* env, jobject o, jstring javaString))
  ResourceMark rm(THREAD);
  int len;
  jchar* name = java_lang_String::as_unicode_string(JNIHandles::resolve(javaString), len);
  oop found_string = StringTable::the_table()->lookup(name, len);
  if (found_string == NULL) {
        return false;
  }
  return true;
WB_END


WB_ENTRY(void, WB_FullGC(JNIEnv* env, jobject o))
  Universe::heap()->collector_policy()->set_should_clear_all_soft_refs(true);
  Universe::heap()->collect(GCCause::_last_ditch_collection);
WB_END


//Some convenience methods to deal with objects from java
int WhiteBox::offset_for_field(const char* field_name, oop object,
    Symbol* signature_symbol) {
  assert(field_name != NULL && strlen(field_name) > 0, "Field name not valid");
  Thread* THREAD = Thread::current();

  //Get the class of our object
  Klass* arg_klass = object->klass();
  //Turn it into an instance-klass
  InstanceKlass* ik = InstanceKlass::cast(arg_klass);

  //Create symbols to look for in the class
  TempNewSymbol name_symbol = SymbolTable::lookup(field_name, (int) strlen(field_name),
      THREAD);

  //To be filled in with an offset of the field we're looking for
  fieldDescriptor fd;

  Klass* res = ik->find_field(name_symbol, signature_symbol, &fd);
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
  if (string == NULL) {
    return NULL;
  }
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
  {CC"isClassAlive0",      CC"(Ljava/lang/String;)Z", (void*)&WB_IsClassAlive      },
  {CC"parseCommandLine",
      CC"(Ljava/lang/String;[Lsun/hotspot/parser/DiagnosticCommand;)[Ljava/lang/Object;",
      (void*) &WB_ParseCommandLine
  },
#if INCLUDE_ALL_GCS
  {CC"g1InConcurrentMark", CC"()Z",                   (void*)&WB_G1InConcurrentMark},
  {CC"g1IsHumongous",      CC"(Ljava/lang/Object;)Z", (void*)&WB_G1IsHumongous     },
  {CC"g1NumFreeRegions",   CC"()J",                   (void*)&WB_G1NumFreeRegions  },
  {CC"g1RegionSize",       CC"()I",                   (void*)&WB_G1RegionSize      },
#endif // INCLUDE_ALL_GCS
#ifdef INCLUDE_NMT
  {CC"NMTAllocTest",       CC"()Z",                   (void*)&WB_NMTAllocTest      },
  {CC"NMTFreeTestMemory",  CC"()Z",                   (void*)&WB_NMTFreeTestMemory },
  {CC"NMTWaitForDataMerge",CC"()Z",                   (void*)&WB_NMTWaitForDataMerge},
#endif // INCLUDE_NMT
  {CC"deoptimizeAll",      CC"()V",                   (void*)&WB_DeoptimizeAll     },
  {CC"deoptimizeMethod",   CC"(Ljava/lang/reflect/Method;)I",
                                                      (void*)&WB_DeoptimizeMethod  },
  {CC"isMethodCompiled",   CC"(Ljava/lang/reflect/Method;)Z",
                                                      (void*)&WB_IsMethodCompiled  },
  {CC"isMethodCompilable", CC"(Ljava/lang/reflect/Method;)Z",
                                                      (void*)&WB_IsMethodCompilable},
  {CC"isMethodQueuedForCompilation",
      CC"(Ljava/lang/reflect/Method;)Z",              (void*)&WB_IsMethodQueuedForCompilation},
  {CC"makeMethodNotCompilable",
      CC"(Ljava/lang/reflect/Method;)V",              (void*)&WB_MakeMethodNotCompilable},
  {CC"setDontInlineMethod",
      CC"(Ljava/lang/reflect/Method;Z)Z",             (void*)&WB_SetDontInlineMethod},
  {CC"getMethodCompilationLevel",
      CC"(Ljava/lang/reflect/Method;)I",              (void*)&WB_GetMethodCompilationLevel},
  {CC"getCompileQueuesSize",
      CC"()I",                                        (void*)&WB_GetCompileQueuesSize},
  {CC"isInStringTable",   CC"(Ljava/lang/String;)Z",  (void*)&WB_IsInStringTable  },
  {CC"fullGC",   CC"()V",                             (void*)&WB_FullGC },
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
