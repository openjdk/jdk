/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/exceptions.hpp"

#if INCLUDE_ALL_GCS
#include "gc_implementation/g1/concurrentMark.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/heapRegionRemSet.hpp"
#endif // INCLUDE_ALL_GCS

#ifdef INCLUDE_NMT
#include "services/memTracker.hpp"
#endif // INCLUDE_NMT

#include "compiler/compileBroker.hpp"
#include "runtime/compilationPolicy.hpp"

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

WB_ENTRY(void, WB_PrintHeapSizes(JNIEnv* env, jobject o)) {
  CollectorPolicy * p = Universe::heap()->collector_policy();
  gclog_or_tty->print_cr("Minimum heap "SIZE_FORMAT" Initial heap "
    SIZE_FORMAT" Maximum heap "SIZE_FORMAT" Min alignment "SIZE_FORMAT" Max alignment "SIZE_FORMAT,
    p->min_heap_byte_size(), p->initial_heap_byte_size(), p->max_heap_byte_size(),
    p->min_alignment(), p->max_alignment());
}
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
// Alloc memory using the test memory type so that we can use that to see if
// NMT picks it up correctly
WB_ENTRY(jlong, WB_NMTMalloc(JNIEnv* env, jobject o, jlong size))
  jlong addr = 0;

  if (MemTracker::is_on() && !MemTracker::shutdown_in_progress()) {
    addr = (jlong)(uintptr_t)os::malloc(size, mtTest);
  }

  return addr;
WB_END

// Free the memory allocated by NMTAllocTest
WB_ENTRY(void, WB_NMTFree(JNIEnv* env, jobject o, jlong mem))
  os::free((void*)(uintptr_t)mem, mtTest);
WB_END

WB_ENTRY(jlong, WB_NMTReserveMemory(JNIEnv* env, jobject o, jlong size))
  jlong addr = 0;

  if (MemTracker::is_on() && !MemTracker::shutdown_in_progress()) {
    addr = (jlong)(uintptr_t)os::reserve_memory(size);
    MemTracker::record_virtual_memory_type((address)addr, mtTest);
  }

  return addr;
WB_END


WB_ENTRY(void, WB_NMTCommitMemory(JNIEnv* env, jobject o, jlong addr, jlong size))
  os::commit_memory((char *)(uintptr_t)addr, size, !ExecMem);
  MemTracker::record_virtual_memory_type((address)(uintptr_t)addr, mtTest);
WB_END

WB_ENTRY(void, WB_NMTUncommitMemory(JNIEnv* env, jobject o, jlong addr, jlong size))
  os::uncommit_memory((char *)(uintptr_t)addr, size);
WB_END

WB_ENTRY(void, WB_NMTReleaseMemory(JNIEnv* env, jobject o, jlong addr, jlong size))
  os::release_memory((char *)(uintptr_t)addr, size);
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

WB_ENTRY(jboolean, WB_IsMethodCompilable(JNIEnv* env, jobject o, jobject method, jint comp_level))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  MutexLockerEx mu(Compile_lock);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  return CompilationPolicy::can_be_compiled(mh, comp_level);
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


WB_ENTRY(void, WB_MakeMethodNotCompilable(JNIEnv* env, jobject o, jobject method, jint comp_level))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  mh->set_not_compilable(comp_level, true /* report */, "WhiteBox");
WB_END

WB_ENTRY(jboolean, WB_TestSetDontInlineMethod(JNIEnv* env, jobject o, jobject method, jboolean value))
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


WB_ENTRY(jboolean, WB_TestSetForceInlineMethod(JNIEnv* env, jobject o, jobject method, jboolean value))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  bool result = mh->force_inline();
  mh->set_force_inline(value == JNI_TRUE);
  return result;
WB_END

WB_ENTRY(jboolean, WB_EnqueueMethodForCompilation(JNIEnv* env, jobject o, jobject method, jint comp_level))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  nmethod* nm = CompileBroker::compile_method(mh, InvocationEntryBci, comp_level, mh, mh->invocation_count(), "WhiteBox", THREAD);
  MutexLockerEx mu(Compile_lock);
  return (mh->queued_for_compilation() || nm != NULL);
WB_END

WB_ENTRY(void, WB_ClearMethodState(JNIEnv* env, jobject o, jobject method))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  MutexLockerEx mu(Compile_lock);
  MethodData* mdo = mh->method_data();
  MethodCounters* mcs = mh->method_counters();

  if (mdo != NULL) {
    mdo->init();
    ResourceMark rm;
    int arg_count = mdo->method()->size_of_parameters();
    for (int i = 0; i < arg_count; i++) {
      mdo->set_arg_modified(i, 0);
    }
  }

  mh->clear_not_c1_compilable();
  mh->clear_not_c2_compilable();
  mh->clear_not_c2_osr_compilable();
  NOT_PRODUCT(mh->set_compiled_invocation_count(0));
  if (mcs != NULL) {
    mcs->backedge_counter()->init();
    mcs->invocation_counter()->init();
    mcs->set_interpreter_invocation_count(0);
    mcs->set_interpreter_throwout_count(0);

#ifdef TIERED
    mcs->set_rate(0.0F);
    mh->set_prev_event_count(0, THREAD);
    mh->set_prev_time(0, THREAD);
#endif
  }
WB_END

WB_ENTRY(jboolean, WB_IsInStringTable(JNIEnv* env, jobject o, jstring javaString))
  ResourceMark rm(THREAD);
  int len;
  jchar* name = java_lang_String::as_unicode_string(JNIHandles::resolve(javaString), len, CHECK_false);
  return (StringTable::lookup(name, len) != NULL);
WB_END


WB_ENTRY(void, WB_FullGC(JNIEnv* env, jobject o))
  Universe::heap()->collector_policy()->set_should_clear_all_soft_refs(true);
  Universe::heap()->collect(GCCause::_last_ditch_collection);
WB_END


WB_ENTRY(void, WB_ReadReservedMemory(JNIEnv* env, jobject o))
  // static+volatile in order to force the read to happen
  // (not be eliminated by the compiler)
  static char c;
  static volatile char* p;

  p = os::reserve_memory(os::vm_allocation_granularity(), NULL, 0);
  if (p == NULL) {
    THROW_MSG(vmSymbols::java_lang_OutOfMemoryError(), "Failed to reserve memory");
  }

  c = *p;
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
  {CC"printHeapSizes",     CC"()V",                   (void*)&WB_PrintHeapSizes    },
#if INCLUDE_ALL_GCS
  {CC"g1InConcurrentMark", CC"()Z",                   (void*)&WB_G1InConcurrentMark},
  {CC"g1IsHumongous",      CC"(Ljava/lang/Object;)Z", (void*)&WB_G1IsHumongous     },
  {CC"g1NumFreeRegions",   CC"()J",                   (void*)&WB_G1NumFreeRegions  },
  {CC"g1RegionSize",       CC"()I",                   (void*)&WB_G1RegionSize      },
#endif // INCLUDE_ALL_GCS
#ifdef INCLUDE_NMT
  {CC"NMTMalloc",           CC"(J)J",                 (void*)&WB_NMTMalloc          },
  {CC"NMTFree",             CC"(J)V",                 (void*)&WB_NMTFree            },
  {CC"NMTReserveMemory",    CC"(J)J",                 (void*)&WB_NMTReserveMemory   },
  {CC"NMTCommitMemory",     CC"(JJ)V",                (void*)&WB_NMTCommitMemory    },
  {CC"NMTUncommitMemory",   CC"(JJ)V",                (void*)&WB_NMTUncommitMemory  },
  {CC"NMTReleaseMemory",    CC"(JJ)V",                (void*)&WB_NMTReleaseMemory   },
  {CC"NMTWaitForDataMerge", CC"()Z",                  (void*)&WB_NMTWaitForDataMerge},
#endif // INCLUDE_NMT
  {CC"deoptimizeAll",      CC"()V",                   (void*)&WB_DeoptimizeAll     },
  {CC"deoptimizeMethod",   CC"(Ljava/lang/reflect/Executable;)I",
                                                      (void*)&WB_DeoptimizeMethod  },
  {CC"isMethodCompiled",   CC"(Ljava/lang/reflect/Executable;)Z",
                                                      (void*)&WB_IsMethodCompiled  },
  {CC"isMethodCompilable", CC"(Ljava/lang/reflect/Executable;I)Z",
                                                      (void*)&WB_IsMethodCompilable},
  {CC"isMethodQueuedForCompilation",
      CC"(Ljava/lang/reflect/Executable;)Z",          (void*)&WB_IsMethodQueuedForCompilation},
  {CC"makeMethodNotCompilable",
      CC"(Ljava/lang/reflect/Executable;I)V",         (void*)&WB_MakeMethodNotCompilable},
  {CC"testSetDontInlineMethod",
      CC"(Ljava/lang/reflect/Executable;Z)Z",         (void*)&WB_TestSetDontInlineMethod},
  {CC"getMethodCompilationLevel",
      CC"(Ljava/lang/reflect/Executable;)I",          (void*)&WB_GetMethodCompilationLevel},
  {CC"getCompileQueuesSize",
      CC"()I",                                        (void*)&WB_GetCompileQueuesSize},
  {CC"testSetForceInlineMethod",
      CC"(Ljava/lang/reflect/Executable;Z)Z",         (void*)&WB_TestSetForceInlineMethod},
  {CC"enqueueMethodForCompilation",
      CC"(Ljava/lang/reflect/Executable;I)Z",         (void*)&WB_EnqueueMethodForCompilation},
  {CC"clearMethodState",
      CC"(Ljava/lang/reflect/Executable;)V",          (void*)&WB_ClearMethodState},
  {CC"isInStringTable",   CC"(Ljava/lang/String;)Z",  (void*)&WB_IsInStringTable  },
  {CC"fullGC",   CC"()V",                             (void*)&WB_FullGC },

  {CC"readReservedMemory", CC"()V",                   (void*)&WB_ReadReservedMemory },
};

#undef CC

JVM_ENTRY(void, JVM_RegisterWhiteBoxMethods(JNIEnv* env, jclass wbclass))
  {
    if (WhiteBoxAPI) {
      // Make sure that wbclass is loaded by the null classloader
      instanceKlassHandle ikh = instanceKlassHandle(JNIHandles::resolve(wbclass)->klass());
      Handle loader(ikh->class_loader());
      if (loader.is_null()) {
        ResourceMark rm;
        ThreadToNativeFromVM ttnfv(thread); // can't be in VM when we call JNI
        bool result = true;
        //  one by one registration natives for exception catching
        jclass exceptionKlass = env->FindClass(vmSymbols::java_lang_NoSuchMethodError()->as_C_string());
        for (int i = 0, n = sizeof(methods) / sizeof(methods[0]); i < n; ++i) {
          if (env->RegisterNatives(wbclass, methods + i, 1) != 0) {
            result = false;
            if (env->ExceptionCheck() && env->IsInstanceOf(env->ExceptionOccurred(), exceptionKlass)) {
              // j.l.NoSuchMethodError is thrown when a method can't be found or a method is not native
              // ignoring the exception
              tty->print_cr("Warning: 'NoSuchMethodError' on register of sun.hotspot.WhiteBox::%s%s", methods[i].name, methods[i].signature);
              env->ExceptionClear();
            } else {
              // register is failed w/o exception or w/ unexpected exception
              tty->print_cr("Warning: unexpected error on register of sun.hotspot.WhiteBox::%s%s. All methods will be unregistered", methods[i].name, methods[i].signature);
              env->UnregisterNatives(wbclass);
              break;
            }
          }
        }

        if (result) {
          WhiteBox::set_used();
        }
      }
    }
  }
JVM_END
