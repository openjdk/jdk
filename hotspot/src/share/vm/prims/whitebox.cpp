/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include <new>

#include "classfile/classLoaderData.hpp"
#include "classfile/stringTable.hpp"
#include "code/codeCache.hpp"
#include "compiler/methodMatcher.hpp"
#include "jvmtifiles/jvmtiEnv.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "prims/wbtestmethods/parserTests.hpp"
#include "prims/whitebox.hpp"
#include "runtime/arguments.hpp"
#include "runtime/compilationPolicy.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/os.hpp"
#include "runtime/sweeper.hpp"
#include "runtime/thread.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/array.hpp"
#include "utilities/debug.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc/g1/concurrentMark.hpp"
#include "gc/g1/concurrentMarkThread.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/parallel/parallelScavengeHeap.inline.hpp"
#include "gc/parallel/adjoiningGenerations.hpp"
#endif // INCLUDE_ALL_GCS
#if INCLUDE_NMT
#include "services/mallocSiteTable.hpp"
#include "services/memTracker.hpp"
#include "utilities/nativeCallStack.hpp"
#endif // INCLUDE_NMT


#define SIZE_T_MAX_VALUE ((size_t) -1)

bool WhiteBox::_used = false;
volatile bool WhiteBox::compilation_locked = false;

class VM_WhiteBoxOperation : public VM_Operation {
 public:
  VM_WhiteBoxOperation()                         { }
  VMOp_Type type()                  const        { return VMOp_WhiteBoxOperation; }
  bool allow_nested_vm_operations() const        { return true; }
};


WB_ENTRY(jlong, WB_GetObjectAddress(JNIEnv* env, jobject o, jobject obj))
  return (jlong)(void*)JNIHandles::resolve(obj);
WB_END

WB_ENTRY(jint, WB_GetHeapOopSize(JNIEnv* env, jobject o))
  return heapOopSize;
WB_END

WB_ENTRY(jint, WB_GetVMPageSize(JNIEnv* env, jobject o))
  return os::vm_page_size();
WB_END

WB_ENTRY(jlong, WB_GetVMAllocationGranularity(JNIEnv* env, jobject o))
  return os::vm_allocation_granularity();
WB_END

WB_ENTRY(jlong, WB_GetVMLargePageSize(JNIEnv* env, jobject o))
  return os::large_page_size();
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

WB_ENTRY(void, WB_AddToBootstrapClassLoaderSearch(JNIEnv* env, jobject o, jstring segment)) {
#if INCLUDE_JVMTI
  ResourceMark rm;
  const char* seg = java_lang_String::as_utf8_string(JNIHandles::resolve_non_null(segment));
  JvmtiEnv* jvmti_env = JvmtiEnv::create_a_jvmti(JVMTI_VERSION);
  jvmtiError err = jvmti_env->AddToBootstrapClassLoaderSearch(seg);
  assert(err == JVMTI_ERROR_NONE, "must not fail");
#endif
}
WB_END

WB_ENTRY(void, WB_AddToSystemClassLoaderSearch(JNIEnv* env, jobject o, jstring segment)) {
#if INCLUDE_JVMTI
  ResourceMark rm;
  const char* seg = java_lang_String::as_utf8_string(JNIHandles::resolve_non_null(segment));
  JvmtiEnv* jvmti_env = JvmtiEnv::create_a_jvmti(JVMTI_VERSION);
  jvmtiError err = jvmti_env->AddToSystemClassLoaderSearch(seg);
  assert(err == JVMTI_ERROR_NONE, "must not fail");
#endif
}
WB_END


WB_ENTRY(jlong, WB_GetCompressedOopsMaxHeapSize(JNIEnv* env, jobject o)) {
  return (jlong)Arguments::max_heap_for_compressed_oops();
}
WB_END

WB_ENTRY(void, WB_PrintHeapSizes(JNIEnv* env, jobject o)) {
  CollectorPolicy * p = Universe::heap()->collector_policy();
  gclog_or_tty->print_cr("Minimum heap " SIZE_FORMAT " Initial heap "
    SIZE_FORMAT " Maximum heap " SIZE_FORMAT " Space alignment " SIZE_FORMAT " Heap alignment " SIZE_FORMAT,
    p->min_heap_byte_size(), p->initial_heap_byte_size(), p->max_heap_byte_size(),
    p->space_alignment(), p->heap_alignment());
}
WB_END

#ifndef PRODUCT
// Forward declaration
void TestReservedSpace_test();
void TestReserveMemorySpecial_test();
void TestVirtualSpace_test();
void TestMetaspaceAux_test();
#endif

WB_ENTRY(void, WB_RunMemoryUnitTests(JNIEnv* env, jobject o))
#ifndef PRODUCT
  TestReservedSpace_test();
  TestReserveMemorySpecial_test();
  TestVirtualSpace_test();
  TestMetaspaceAux_test();
#endif
WB_END

WB_ENTRY(void, WB_ReadFromNoaccessArea(JNIEnv* env, jobject o))
  size_t granularity = os::vm_allocation_granularity();
  ReservedHeapSpace rhs(100 * granularity, granularity, false);
  VirtualSpace vs;
  vs.initialize(rhs, 50 * granularity);

  // Check if constraints are complied
  if (!( UseCompressedOops && rhs.base() != NULL &&
         Universe::narrow_oop_base() != NULL &&
         Universe::narrow_oop_use_implicit_null_checks() )) {
    tty->print_cr("WB_ReadFromNoaccessArea method is useless:\n "
                  "\tUseCompressedOops is %d\n"
                  "\trhs.base() is " PTR_FORMAT "\n"
                  "\tUniverse::narrow_oop_base() is " PTR_FORMAT "\n"
                  "\tUniverse::narrow_oop_use_implicit_null_checks() is %d",
                  UseCompressedOops,
                  p2i(rhs.base()),
                  p2i(Universe::narrow_oop_base()),
                  Universe::narrow_oop_use_implicit_null_checks());
    return;
  }
  tty->print_cr("Reading from no access area... ");
  tty->print_cr("*(vs.low_boundary() - rhs.noaccess_prefix() / 2 ) = %c",
                *(vs.low_boundary() - rhs.noaccess_prefix() / 2 ));
WB_END

static jint wb_stress_virtual_space_resize(size_t reserved_space_size,
                                           size_t magnitude, size_t iterations) {
  size_t granularity = os::vm_allocation_granularity();
  ReservedHeapSpace rhs(reserved_space_size * granularity, granularity, false);
  VirtualSpace vs;
  if (!vs.initialize(rhs, 0)) {
    tty->print_cr("Failed to initialize VirtualSpace. Can't proceed.");
    return 3;
  }

  long seed = os::random();
  tty->print_cr("Random seed is %ld", seed);
  os::init_random(seed);

  for (size_t i = 0; i < iterations; i++) {

    // Whether we will shrink or grow
    bool shrink = os::random() % 2L == 0;

    // Get random delta to resize virtual space
    size_t delta = (size_t)os::random() % magnitude;

    // If we are about to shrink virtual space below zero, then expand instead
    if (shrink && vs.committed_size() < delta) {
      shrink = false;
    }

    // Resizing by delta
    if (shrink) {
      vs.shrink_by(delta);
    } else {
      // If expanding fails expand_by will silently return false
      vs.expand_by(delta, true);
    }
  }
  return 0;
}

WB_ENTRY(jint, WB_StressVirtualSpaceResize(JNIEnv* env, jobject o,
        jlong reserved_space_size, jlong magnitude, jlong iterations))
  tty->print_cr("reservedSpaceSize=" JLONG_FORMAT ", magnitude=" JLONG_FORMAT ", "
                "iterations=" JLONG_FORMAT "\n", reserved_space_size, magnitude,
                iterations);
  if (reserved_space_size < 0 || magnitude < 0 || iterations < 0) {
    tty->print_cr("One of variables printed above is negative. Can't proceed.\n");
    return 1;
  }

  // sizeof(size_t) depends on whether OS is 32bit or 64bit. sizeof(jlong) is
  // always 8 byte. That's why we should avoid overflow in case of 32bit platform.
  if (sizeof(size_t) < sizeof(jlong)) {
    jlong size_t_max_value = (jlong) SIZE_T_MAX_VALUE;
    if (reserved_space_size > size_t_max_value || magnitude > size_t_max_value
        || iterations > size_t_max_value) {
      tty->print_cr("One of variables printed above overflows size_t. Can't proceed.\n");
      return 2;
    }
  }

  return wb_stress_virtual_space_resize((size_t) reserved_space_size,
                                        (size_t) magnitude, (size_t) iterations);
WB_END

WB_ENTRY(jboolean, WB_isObjectInOldGen(JNIEnv* env, jobject o, jobject obj))
  oop p = JNIHandles::resolve(obj);
#if INCLUDE_ALL_GCS
  if (UseG1GC) {
    G1CollectedHeap* g1 = G1CollectedHeap::heap();
    const HeapRegion* hr = g1->heap_region_containing(p);
    if (hr == NULL) {
      return false;
    }
    return !(hr->is_young());
  } else if (UseParallelGC) {
    ParallelScavengeHeap* psh = ParallelScavengeHeap::heap();
    return !psh->is_in_young(p);
  }
#endif // INCLUDE_ALL_GCS
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  return !gch->is_in_young(p);
WB_END

WB_ENTRY(jlong, WB_GetObjectSize(JNIEnv* env, jobject o, jobject obj))
  oop p = JNIHandles::resolve(obj);
  return p->size() * HeapWordSize;
WB_END

WB_ENTRY(jlong, WB_GetHeapSpaceAlignment(JNIEnv* env, jobject o))
  size_t alignment = Universe::heap()->collector_policy()->space_alignment();
  return (jlong)alignment;
WB_END

#if INCLUDE_ALL_GCS
WB_ENTRY(jboolean, WB_G1IsHumongous(JNIEnv* env, jobject o, jobject obj))
  G1CollectedHeap* g1 = G1CollectedHeap::heap();
  oop result = JNIHandles::resolve(obj);
  const HeapRegion* hr = g1->heap_region_containing(result);
  return hr->is_humongous();
WB_END

WB_ENTRY(jlong, WB_G1NumMaxRegions(JNIEnv* env, jobject o))
  G1CollectedHeap* g1 = G1CollectedHeap::heap();
  size_t nr = g1->max_regions();
  return (jlong)nr;
WB_END

WB_ENTRY(jlong, WB_G1NumFreeRegions(JNIEnv* env, jobject o))
  G1CollectedHeap* g1 = G1CollectedHeap::heap();
  size_t nr = g1->num_free_regions();
  return (jlong)nr;
WB_END

WB_ENTRY(jboolean, WB_G1InConcurrentMark(JNIEnv* env, jobject o))
  G1CollectedHeap* g1 = G1CollectedHeap::heap();
  return g1->concurrent_mark()->cmThread()->during_cycle();
WB_END

WB_ENTRY(jboolean, WB_G1StartMarkCycle(JNIEnv* env, jobject o))
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  if (!g1h->concurrent_mark()->cmThread()->during_cycle()) {
    g1h->collect(GCCause::_wb_conc_mark);
    return true;
  }
  return false;
WB_END

WB_ENTRY(jint, WB_G1RegionSize(JNIEnv* env, jobject o))
  return (jint)HeapRegion::GrainBytes;
WB_END

WB_ENTRY(jlong, WB_PSVirtualSpaceAlignment(JNIEnv* env, jobject o))
  ParallelScavengeHeap* ps = ParallelScavengeHeap::heap();
  size_t alignment = ps->gens()->virtual_spaces()->alignment();
  return (jlong)alignment;
WB_END

WB_ENTRY(jlong, WB_PSHeapGenerationAlignment(JNIEnv* env, jobject o))
  size_t alignment = ParallelScavengeHeap::heap()->generation_alignment();
  return (jlong)alignment;
WB_END

WB_ENTRY(jobject, WB_G1AuxiliaryMemoryUsage(JNIEnv* env))
  ResourceMark rm(THREAD);
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  MemoryUsage usage = g1h->get_auxiliary_data_memory_usage();
  Handle h = MemoryService::create_MemoryUsage_obj(usage, CHECK_NULL);
  return JNIHandles::make_local(env, h());
WB_END
#endif // INCLUDE_ALL_GCS

#if INCLUDE_NMT
// Alloc memory using the test memory type so that we can use that to see if
// NMT picks it up correctly
WB_ENTRY(jlong, WB_NMTMalloc(JNIEnv* env, jobject o, jlong size))
  jlong addr = 0;
  addr = (jlong)(uintptr_t)os::malloc(size, mtTest);
  return addr;
WB_END

// Alloc memory with pseudo call stack. The test can create psudo malloc
// allocation site to stress the malloc tracking.
WB_ENTRY(jlong, WB_NMTMallocWithPseudoStack(JNIEnv* env, jobject o, jlong size, jint pseudo_stack))
  address pc = (address)(size_t)pseudo_stack;
  NativeCallStack stack(&pc, 1);
  return (jlong)(uintptr_t)os::malloc(size, mtTest, stack);
WB_END

// Free the memory allocated by NMTAllocTest
WB_ENTRY(void, WB_NMTFree(JNIEnv* env, jobject o, jlong mem))
  os::free((void*)(uintptr_t)mem);
WB_END

WB_ENTRY(jlong, WB_NMTReserveMemory(JNIEnv* env, jobject o, jlong size))
  jlong addr = 0;

  addr = (jlong)(uintptr_t)os::reserve_memory(size);
  MemTracker::record_virtual_memory_type((address)addr, mtTest);

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

WB_ENTRY(jboolean, WB_NMTChangeTrackingLevel(JNIEnv* env))
  // Test that we can downgrade NMT levels but not upgrade them.
  if (MemTracker::tracking_level() == NMT_off) {
    MemTracker::transition_to(NMT_off);
    return MemTracker::tracking_level() == NMT_off;
  } else {
    assert(MemTracker::tracking_level() == NMT_detail, "Should start out as detail tracking");
    MemTracker::transition_to(NMT_summary);
    assert(MemTracker::tracking_level() == NMT_summary, "Should be summary now");

    // Can't go to detail once NMT is set to summary.
    MemTracker::transition_to(NMT_detail);
    assert(MemTracker::tracking_level() == NMT_summary, "Should still be summary now");

    // Shutdown sets tracking level to minimal.
    MemTracker::shutdown();
    assert(MemTracker::tracking_level() == NMT_minimal, "Should be minimal now");

    // Once the tracking level is minimal, we cannot increase to summary.
    // The code ignores this request instead of asserting because if the malloc site
    // table overflows in another thread, it tries to change the code to summary.
    MemTracker::transition_to(NMT_summary);
    assert(MemTracker::tracking_level() == NMT_minimal, "Should still be minimal now");

    // Really can never go up to detail, verify that the code would never do this.
    MemTracker::transition_to(NMT_detail);
    assert(MemTracker::tracking_level() == NMT_minimal, "Should still be minimal now");
    return MemTracker::tracking_level() == NMT_minimal;
  }
WB_END

WB_ENTRY(jint, WB_NMTGetHashSize(JNIEnv* env, jobject o))
  int hash_size = MallocSiteTable::hash_buckets();
  assert(hash_size > 0, "NMT hash_size should be > 0");
  return (jint)hash_size;
WB_END
#endif // INCLUDE_NMT

static jmethodID reflected_method_to_jmid(JavaThread* thread, JNIEnv* env, jobject method) {
  assert(method != NULL, "method should not be null");
  ThreadToNativeFromVM ttn(thread);
  return env->FromReflectedMethod(method);
}

// Deoptimizes all compiled frames and makes nmethods not entrant if it's requested
class VM_WhiteBoxDeoptimizeFrames : public VM_WhiteBoxOperation {
 private:
  int _result;
  const bool _make_not_entrant;
 public:
  VM_WhiteBoxDeoptimizeFrames(bool make_not_entrant) :
        _result(0), _make_not_entrant(make_not_entrant) { }
  int  result() const { return _result; }

  void doit() {
    for (JavaThread* t = Threads::first(); t != NULL; t = t->next()) {
      if (t->has_last_Java_frame()) {
        for (StackFrameStream fst(t, UseBiasedLocking); !fst.is_done(); fst.next()) {
          frame* f = fst.current();
          if (f->can_be_deoptimized() && !f->is_deoptimized_frame()) {
            RegisterMap* reg_map = fst.register_map();
            Deoptimization::deoptimize(t, *f, reg_map);
            if (_make_not_entrant) {
                nmethod* nm = CodeCache::find_nmethod(f->pc());
                assert(nm != NULL, "sanity check");
                nm->make_not_entrant();
            }
            ++_result;
          }
        }
      }
    }
  }
};

WB_ENTRY(jint, WB_DeoptimizeFrames(JNIEnv* env, jobject o, jboolean make_not_entrant))
  VM_WhiteBoxDeoptimizeFrames op(make_not_entrant == JNI_TRUE);
  VMThread::execute(&op);
  return op.result();
WB_END

WB_ENTRY(void, WB_DeoptimizeAll(JNIEnv* env, jobject o))
  MutexLockerEx mu(Compile_lock);
  CodeCache::mark_all_nmethods_for_deoptimization();
  VM_Deoptimize op;
  VMThread::execute(&op);
WB_END

WB_ENTRY(jint, WB_DeoptimizeMethod(JNIEnv* env, jobject o, jobject method, jboolean is_osr))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  int result = 0;
  CHECK_JNI_EXCEPTION_(env, result);
  MutexLockerEx mu(Compile_lock);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  if (is_osr) {
    result += mh->mark_osr_nmethods();
  } else if (mh->code() != NULL) {
    mh->code()->mark_for_deoptimization();
    ++result;
  }
  result += CodeCache::mark_for_deoptimization(mh());
  if (result > 0) {
    VM_Deoptimize op;
    VMThread::execute(&op);
  }
  return result;
WB_END

WB_ENTRY(jboolean, WB_IsMethodCompiled(JNIEnv* env, jobject o, jobject method, jboolean is_osr))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  CHECK_JNI_EXCEPTION_(env, JNI_FALSE);
  MutexLockerEx mu(Compile_lock);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  nmethod* code = is_osr ? mh->lookup_osr_nmethod_for(InvocationEntryBci, CompLevel_none, false) : mh->code();
  if (code == NULL) {
    return JNI_FALSE;
  }
  return (code->is_alive() && !code->is_marked_for_deoptimization());
WB_END

WB_ENTRY(jboolean, WB_IsMethodCompilable(JNIEnv* env, jobject o, jobject method, jint comp_level, jboolean is_osr))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  CHECK_JNI_EXCEPTION_(env, JNI_FALSE);
  MutexLockerEx mu(Compile_lock);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  if (is_osr) {
    return CompilationPolicy::can_be_osr_compiled(mh, comp_level);
  } else {
    return CompilationPolicy::can_be_compiled(mh, comp_level);
  }
WB_END

WB_ENTRY(jboolean, WB_IsMethodQueuedForCompilation(JNIEnv* env, jobject o, jobject method))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  CHECK_JNI_EXCEPTION_(env, JNI_FALSE);
  MutexLockerEx mu(Compile_lock);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  return mh->queued_for_compilation();
WB_END

WB_ENTRY(jboolean, WB_IsIntrinsicAvailable(JNIEnv* env, jobject o, jobject method, jobject compilation_context, jint compLevel))
  if (compLevel < CompLevel_none || compLevel > CompLevel_highest_tier) {
    return false; // Intrinsic is not available on a non-existent compilation level.
  }
  jmethodID method_id, compilation_context_id;
  method_id = reflected_method_to_jmid(thread, env, method);
  CHECK_JNI_EXCEPTION_(env, JNI_FALSE);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(method_id));
  if (compilation_context != NULL) {
    compilation_context_id = reflected_method_to_jmid(thread, env, compilation_context);
    CHECK_JNI_EXCEPTION_(env, JNI_FALSE);
    methodHandle cch(THREAD, Method::checked_resolve_jmethod_id(compilation_context_id));
    return CompileBroker::compiler(compLevel)->is_intrinsic_available(mh, cch);
  } else {
    return CompileBroker::compiler(compLevel)->is_intrinsic_available(mh, NULL);
  }
WB_END

WB_ENTRY(jint, WB_GetMethodCompilationLevel(JNIEnv* env, jobject o, jobject method, jboolean is_osr))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  CHECK_JNI_EXCEPTION_(env, CompLevel_none);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  nmethod* code = is_osr ? mh->lookup_osr_nmethod_for(InvocationEntryBci, CompLevel_none, false) : mh->code();
  return (code != NULL ? code->comp_level() : CompLevel_none);
WB_END

WB_ENTRY(void, WB_MakeMethodNotCompilable(JNIEnv* env, jobject o, jobject method, jint comp_level, jboolean is_osr))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  CHECK_JNI_EXCEPTION(env);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  if (is_osr) {
    mh->set_not_osr_compilable(comp_level, true /* report */, "WhiteBox");
  } else {
    mh->set_not_compilable(comp_level, true /* report */, "WhiteBox");
  }
WB_END

WB_ENTRY(jint, WB_GetMethodEntryBci(JNIEnv* env, jobject o, jobject method))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  CHECK_JNI_EXCEPTION_(env, InvocationEntryBci);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  nmethod* code = mh->lookup_osr_nmethod_for(InvocationEntryBci, CompLevel_none, false);
  return (code != NULL && code->is_osr_method() ? code->osr_entry_bci() : InvocationEntryBci);
WB_END

WB_ENTRY(jboolean, WB_TestSetDontInlineMethod(JNIEnv* env, jobject o, jobject method, jboolean value))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  CHECK_JNI_EXCEPTION_(env, JNI_FALSE);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  bool result = mh->dont_inline();
  mh->set_dont_inline(value == JNI_TRUE);
  return result;
WB_END

WB_ENTRY(jint, WB_GetCompileQueueSize(JNIEnv* env, jobject o, jint comp_level))
  if (comp_level == CompLevel_any) {
    return CompileBroker::queue_size(CompLevel_full_optimization) /* C2 */ +
        CompileBroker::queue_size(CompLevel_full_profile) /* C1 */;
  } else {
    return CompileBroker::queue_size(comp_level);
  }
WB_END

WB_ENTRY(jboolean, WB_TestSetForceInlineMethod(JNIEnv* env, jobject o, jobject method, jboolean value))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  CHECK_JNI_EXCEPTION_(env, JNI_FALSE);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  bool result = mh->force_inline();
  mh->set_force_inline(value == JNI_TRUE);
  return result;
WB_END

WB_ENTRY(jboolean, WB_EnqueueMethodForCompilation(JNIEnv* env, jobject o, jobject method, jint comp_level, jint bci))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  CHECK_JNI_EXCEPTION_(env, JNI_FALSE);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  nmethod* nm = CompileBroker::compile_method(mh, bci, comp_level, mh, mh->invocation_count(), "WhiteBox", THREAD);
  MutexLockerEx mu(Compile_lock);
  return (mh->queued_for_compilation() || nm != NULL);
WB_END


WB_ENTRY(jint, WB_MatchesMethod(JNIEnv* env, jobject o, jobject method, jstring pattern))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  CHECK_JNI_EXCEPTION_(env, JNI_FALSE);

  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));

  ResourceMark rm;
  char* method_str = java_lang_String::as_utf8_string(JNIHandles::resolve_non_null(pattern));

  const char* error_msg = NULL;

  BasicMatcher* m = BasicMatcher::parse_method_pattern(method_str, error_msg);
  if (m == NULL) {
    assert(error_msg != NULL, "Must have error_msg");
    tty->print_cr("Got error: %s", error_msg);
    return -1;
  }

  // Pattern works - now check if it matches
  int result = m->matches(mh);
  delete m;
  assert(result == 0 || result == 1, "Result out of range");
  return result;
WB_END

class AlwaysFalseClosure : public BoolObjectClosure {
 public:
  bool do_object_b(oop p) { return false; }
};

static AlwaysFalseClosure always_false;

WB_ENTRY(void, WB_ClearMethodState(JNIEnv* env, jobject o, jobject method))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  CHECK_JNI_EXCEPTION(env);
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
    MutexLockerEx mu(mdo->extra_data_lock());
    mdo->clean_method_data(&always_false);
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
    mh->set_prev_event_count(0);
    mh->set_prev_time(0);
#endif
  }
WB_END

template <typename T>
static bool GetVMFlag(JavaThread* thread, JNIEnv* env, jstring name, T* value, Flag::Error (*TAt)(const char*, T*, bool, bool)) {
  if (name == NULL) {
    return false;
  }
  ThreadToNativeFromVM ttnfv(thread);   // can't be in VM when we call JNI
  const char* flag_name = env->GetStringUTFChars(name, NULL);
  Flag::Error result = (*TAt)(flag_name, value, true, true);
  env->ReleaseStringUTFChars(name, flag_name);
  return (result == Flag::SUCCESS);
}

template <typename T>
static bool SetVMFlag(JavaThread* thread, JNIEnv* env, jstring name, T* value, Flag::Error (*TAtPut)(const char*, T*, Flag::Flags)) {
  if (name == NULL) {
    return false;
  }
  ThreadToNativeFromVM ttnfv(thread);   // can't be in VM when we call JNI
  const char* flag_name = env->GetStringUTFChars(name, NULL);
  Flag::Error result = (*TAtPut)(flag_name, value, Flag::INTERNAL);
  env->ReleaseStringUTFChars(name, flag_name);
  return (result == Flag::SUCCESS);
}

template <typename T>
static jobject box(JavaThread* thread, JNIEnv* env, Symbol* name, Symbol* sig, T value) {
  ResourceMark rm(thread);
  jclass clazz = env->FindClass(name->as_C_string());
  CHECK_JNI_EXCEPTION_(env, NULL);
  jmethodID methodID = env->GetStaticMethodID(clazz,
        vmSymbols::valueOf_name()->as_C_string(),
        sig->as_C_string());
  CHECK_JNI_EXCEPTION_(env, NULL);
  jobject result = env->CallStaticObjectMethod(clazz, methodID, value);
  CHECK_JNI_EXCEPTION_(env, NULL);
  return result;
}

static jobject booleanBox(JavaThread* thread, JNIEnv* env, jboolean value) {
  return box(thread, env, vmSymbols::java_lang_Boolean(), vmSymbols::Boolean_valueOf_signature(), value);
}
static jobject integerBox(JavaThread* thread, JNIEnv* env, jint value) {
  return box(thread, env, vmSymbols::java_lang_Integer(), vmSymbols::Integer_valueOf_signature(), value);
}
static jobject longBox(JavaThread* thread, JNIEnv* env, jlong value) {
  return box(thread, env, vmSymbols::java_lang_Long(), vmSymbols::Long_valueOf_signature(), value);
}
/* static jobject floatBox(JavaThread* thread, JNIEnv* env, jfloat value) {
  return box(thread, env, vmSymbols::java_lang_Float(), vmSymbols::Float_valueOf_signature(), value);
}*/
static jobject doubleBox(JavaThread* thread, JNIEnv* env, jdouble value) {
  return box(thread, env, vmSymbols::java_lang_Double(), vmSymbols::Double_valueOf_signature(), value);
}

static Flag* getVMFlag(JavaThread* thread, JNIEnv* env, jstring name) {
  ThreadToNativeFromVM ttnfv(thread);   // can't be in VM when we call JNI
  const char* flag_name = env->GetStringUTFChars(name, NULL);
  Flag* result = Flag::find_flag(flag_name, strlen(flag_name), true, true);
  env->ReleaseStringUTFChars(name, flag_name);
  return result;
}

WB_ENTRY(jboolean, WB_IsConstantVMFlag(JNIEnv* env, jobject o, jstring name))
  Flag* flag = getVMFlag(thread, env, name);
  return (flag != NULL) && flag->is_constant_in_binary();
WB_END

WB_ENTRY(jboolean, WB_IsLockedVMFlag(JNIEnv* env, jobject o, jstring name))
  Flag* flag = getVMFlag(thread, env, name);
  return (flag != NULL) && !(flag->is_unlocked() || flag->is_unlocker());
WB_END

WB_ENTRY(jobject, WB_GetBooleanVMFlag(JNIEnv* env, jobject o, jstring name))
  bool result;
  if (GetVMFlag <bool> (thread, env, name, &result, &CommandLineFlags::boolAt)) {
    ThreadToNativeFromVM ttnfv(thread);   // can't be in VM when we call JNI
    return booleanBox(thread, env, result);
  }
  return NULL;
WB_END

WB_ENTRY(jobject, WB_GetIntVMFlag(JNIEnv* env, jobject o, jstring name))
  int result;
  if (GetVMFlag <int> (thread, env, name, &result, &CommandLineFlags::intAt)) {
    ThreadToNativeFromVM ttnfv(thread);   // can't be in VM when we call JNI
    return longBox(thread, env, result);
  }
  return NULL;
WB_END

WB_ENTRY(jobject, WB_GetUintVMFlag(JNIEnv* env, jobject o, jstring name))
  uint result;
  if (GetVMFlag <uint> (thread, env, name, &result, &CommandLineFlags::uintAt)) {
    ThreadToNativeFromVM ttnfv(thread);   // can't be in VM when we call JNI
    return longBox(thread, env, result);
  }
  return NULL;
WB_END

WB_ENTRY(jobject, WB_GetIntxVMFlag(JNIEnv* env, jobject o, jstring name))
  intx result;
  if (GetVMFlag <intx> (thread, env, name, &result, &CommandLineFlags::intxAt)) {
    ThreadToNativeFromVM ttnfv(thread);   // can't be in VM when we call JNI
    return longBox(thread, env, result);
  }
  return NULL;
WB_END

WB_ENTRY(jobject, WB_GetUintxVMFlag(JNIEnv* env, jobject o, jstring name))
  uintx result;
  if (GetVMFlag <uintx> (thread, env, name, &result, &CommandLineFlags::uintxAt)) {
    ThreadToNativeFromVM ttnfv(thread);   // can't be in VM when we call JNI
    return longBox(thread, env, result);
  }
  return NULL;
WB_END

WB_ENTRY(jobject, WB_GetUint64VMFlag(JNIEnv* env, jobject o, jstring name))
  uint64_t result;
  if (GetVMFlag <uint64_t> (thread, env, name, &result, &CommandLineFlags::uint64_tAt)) {
    ThreadToNativeFromVM ttnfv(thread);   // can't be in VM when we call JNI
    return longBox(thread, env, result);
  }
  return NULL;
WB_END

WB_ENTRY(jobject, WB_GetSizeTVMFlag(JNIEnv* env, jobject o, jstring name))
  uintx result;
  if (GetVMFlag <size_t> (thread, env, name, &result, &CommandLineFlags::size_tAt)) {
    ThreadToNativeFromVM ttnfv(thread);   // can't be in VM when we call JNI
    return longBox(thread, env, result);
  }
  return NULL;
WB_END

WB_ENTRY(jobject, WB_GetDoubleVMFlag(JNIEnv* env, jobject o, jstring name))
  double result;
  if (GetVMFlag <double> (thread, env, name, &result, &CommandLineFlags::doubleAt)) {
    ThreadToNativeFromVM ttnfv(thread);   // can't be in VM when we call JNI
    return doubleBox(thread, env, result);
  }
  return NULL;
WB_END

WB_ENTRY(jstring, WB_GetStringVMFlag(JNIEnv* env, jobject o, jstring name))
  ccstr ccstrResult;
  if (GetVMFlag <ccstr> (thread, env, name, &ccstrResult, &CommandLineFlags::ccstrAt)) {
    ThreadToNativeFromVM ttnfv(thread);   // can't be in VM when we call JNI
    jstring result = env->NewStringUTF(ccstrResult);
    CHECK_JNI_EXCEPTION_(env, NULL);
    return result;
  }
  return NULL;
WB_END

WB_ENTRY(void, WB_SetBooleanVMFlag(JNIEnv* env, jobject o, jstring name, jboolean value))
  bool result = value == JNI_TRUE ? true : false;
  SetVMFlag <bool> (thread, env, name, &result, &CommandLineFlags::boolAtPut);
WB_END

WB_ENTRY(void, WB_SetIntVMFlag(JNIEnv* env, jobject o, jstring name, jlong value))
  int result = value;
  SetVMFlag <int> (thread, env, name, &result, &CommandLineFlags::intAtPut);
WB_END

WB_ENTRY(void, WB_SetUintVMFlag(JNIEnv* env, jobject o, jstring name, jlong value))
  uint result = value;
  SetVMFlag <uint> (thread, env, name, &result, &CommandLineFlags::uintAtPut);
WB_END

WB_ENTRY(void, WB_SetIntxVMFlag(JNIEnv* env, jobject o, jstring name, jlong value))
  intx result = value;
  SetVMFlag <intx> (thread, env, name, &result, &CommandLineFlags::intxAtPut);
WB_END

WB_ENTRY(void, WB_SetUintxVMFlag(JNIEnv* env, jobject o, jstring name, jlong value))
  uintx result = value;
  SetVMFlag <uintx> (thread, env, name, &result, &CommandLineFlags::uintxAtPut);
WB_END

WB_ENTRY(void, WB_SetUint64VMFlag(JNIEnv* env, jobject o, jstring name, jlong value))
  uint64_t result = value;
  SetVMFlag <uint64_t> (thread, env, name, &result, &CommandLineFlags::uint64_tAtPut);
WB_END

WB_ENTRY(void, WB_SetSizeTVMFlag(JNIEnv* env, jobject o, jstring name, jlong value))
  size_t result = value;
  SetVMFlag <size_t> (thread, env, name, &result, &CommandLineFlags::size_tAtPut);
WB_END

WB_ENTRY(void, WB_SetDoubleVMFlag(JNIEnv* env, jobject o, jstring name, jdouble value))
  double result = value;
  SetVMFlag <double> (thread, env, name, &result, &CommandLineFlags::doubleAtPut);
WB_END

WB_ENTRY(void, WB_SetStringVMFlag(JNIEnv* env, jobject o, jstring name, jstring value))
  ThreadToNativeFromVM ttnfv(thread);   // can't be in VM when we call JNI
  const char* ccstrValue = (value == NULL) ? NULL : env->GetStringUTFChars(value, NULL);
  ccstr ccstrResult = ccstrValue;
  bool needFree;
  {
    ThreadInVMfromNative ttvfn(thread); // back to VM
    needFree = SetVMFlag <ccstr> (thread, env, name, &ccstrResult, &CommandLineFlags::ccstrAtPut);
  }
  if (value != NULL) {
    env->ReleaseStringUTFChars(value, ccstrValue);
  }
  if (needFree) {
    FREE_C_HEAP_ARRAY(char, ccstrResult);
  }
WB_END

WB_ENTRY(void, WB_LockCompilation(JNIEnv* env, jobject o, jlong timeout))
  WhiteBox::compilation_locked = true;
WB_END

WB_ENTRY(void, WB_UnlockCompilation(JNIEnv* env, jobject o))
  MonitorLockerEx mo(Compilation_lock, Mutex::_no_safepoint_check_flag);
  WhiteBox::compilation_locked = false;
  mo.notify_all();
WB_END

WB_ENTRY(void, WB_ForceNMethodSweep(JNIEnv* env, jobject o))
  // Force a code cache sweep and block until it finished
  NMethodSweeper::force_sweep();
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
#if INCLUDE_ALL_GCS
  if (UseG1GC) {
    // Needs to be cleared explicitly for G1
    Universe::heap()->collector_policy()->set_should_clear_all_soft_refs(false);
  }
#endif // INCLUDE_ALL_GCS
WB_END

WB_ENTRY(void, WB_YoungGC(JNIEnv* env, jobject o))
  Universe::heap()->collect(GCCause::_wb_young_gc);
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

WB_ENTRY(jstring, WB_GetCPUFeatures(JNIEnv* env, jobject o))
  const char* cpu_features = VM_Version::cpu_features();
  ThreadToNativeFromVM ttn(thread);
  jstring features_string = env->NewStringUTF(cpu_features);

  CHECK_JNI_EXCEPTION_(env, NULL);

  return features_string;
WB_END

int WhiteBox::get_blob_type(const CodeBlob* code) {
  guarantee(WhiteBoxAPI, "internal testing API :: WhiteBox has to be enabled");
  return CodeCache::get_code_heap(code)->code_blob_type();
}

CodeHeap* WhiteBox::get_code_heap(int blob_type) {
  guarantee(WhiteBoxAPI, "internal testing API :: WhiteBox has to be enabled");
  return CodeCache::get_code_heap(blob_type);
}

struct CodeBlobStub {
  CodeBlobStub(const CodeBlob* blob) :
      name(os::strdup(blob->name())),
      size(blob->size()),
      blob_type(WhiteBox::get_blob_type(blob)) { }
  ~CodeBlobStub() { os::free((void*) name); }
  const char* const name;
  const int         size;
  const int         blob_type;
};

static jobjectArray codeBlob2objectArray(JavaThread* thread, JNIEnv* env, CodeBlobStub* cb) {
  jclass clazz = env->FindClass(vmSymbols::java_lang_Object()->as_C_string());
  CHECK_JNI_EXCEPTION_(env, NULL);
  jobjectArray result = env->NewObjectArray(3, clazz, NULL);

  jstring name = env->NewStringUTF(cb->name);
  CHECK_JNI_EXCEPTION_(env, NULL);
  env->SetObjectArrayElement(result, 0, name);

  jobject obj = integerBox(thread, env, cb->size);
  CHECK_JNI_EXCEPTION_(env, NULL);
  env->SetObjectArrayElement(result, 1, obj);

  obj = integerBox(thread, env, cb->blob_type);
  CHECK_JNI_EXCEPTION_(env, NULL);
  env->SetObjectArrayElement(result, 2, obj);

  return result;
}

WB_ENTRY(jobjectArray, WB_GetNMethod(JNIEnv* env, jobject o, jobject method, jboolean is_osr))
  ResourceMark rm(THREAD);
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  CHECK_JNI_EXCEPTION_(env, NULL);
  methodHandle mh(THREAD, Method::checked_resolve_jmethod_id(jmid));
  nmethod* code = is_osr ? mh->lookup_osr_nmethod_for(InvocationEntryBci, CompLevel_none, false) : mh->code();
  jobjectArray result = NULL;
  if (code == NULL) {
    return result;
  }
  int insts_size = code->insts_size();

  ThreadToNativeFromVM ttn(thread);
  jclass clazz = env->FindClass(vmSymbols::java_lang_Object()->as_C_string());
  CHECK_JNI_EXCEPTION_(env, NULL);
  result = env->NewObjectArray(5, clazz, NULL);
  if (result == NULL) {
    return result;
  }

  CodeBlobStub stub(code);
  jobjectArray codeBlob = codeBlob2objectArray(thread, env, &stub);
  env->SetObjectArrayElement(result, 0, codeBlob);

  jobject level = integerBox(thread, env, code->comp_level());
  CHECK_JNI_EXCEPTION_(env, NULL);
  env->SetObjectArrayElement(result, 1, level);

  jbyteArray insts = env->NewByteArray(insts_size);
  CHECK_JNI_EXCEPTION_(env, NULL);
  env->SetByteArrayRegion(insts, 0, insts_size, (jbyte*) code->insts_begin());
  env->SetObjectArrayElement(result, 2, insts);

  jobject id = integerBox(thread, env, code->compile_id());
  CHECK_JNI_EXCEPTION_(env, NULL);
  env->SetObjectArrayElement(result, 3, id);

  jobject address = longBox(thread, env, (jlong) code);
  CHECK_JNI_EXCEPTION_(env, NULL);
  env->SetObjectArrayElement(result, 4, address);

  return result;
WB_END

CodeBlob* WhiteBox::allocate_code_blob(int size, int blob_type) {
  guarantee(WhiteBoxAPI, "internal testing API :: WhiteBox has to be enabled");
  BufferBlob* blob;
  int full_size = CodeBlob::align_code_offset(sizeof(BufferBlob));
  if (full_size < size) {
    full_size += round_to(size - full_size, oopSize);
  }
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    blob = (BufferBlob*) CodeCache::allocate(full_size, blob_type);
    ::new (blob) BufferBlob("WB::DummyBlob", full_size);
  }
  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();
  return blob;
}

WB_ENTRY(jlong, WB_AllocateCodeBlob(JNIEnv* env, jobject o, jint size, jint blob_type))
  if (size < 0) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(),
      err_msg("WB_AllocateCodeBlob: size is negative: " INT32_FORMAT, size));
  }
  return (jlong) WhiteBox::allocate_code_blob(size, blob_type);
WB_END

WB_ENTRY(void, WB_FreeCodeBlob(JNIEnv* env, jobject o, jlong addr))
  if (addr == 0) {
    return;
  }
  BufferBlob::free((BufferBlob*) addr);
WB_END

WB_ENTRY(jobjectArray, WB_GetCodeHeapEntries(JNIEnv* env, jobject o, jint blob_type))
  ResourceMark rm;
  GrowableArray<CodeBlobStub*> blobs;
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    CodeHeap* heap = WhiteBox::get_code_heap(blob_type);
    if (heap == NULL) {
      return NULL;
    }
    for (CodeBlob* cb = (CodeBlob*) heap->first();
         cb != NULL; cb = (CodeBlob*) heap->next(cb)) {
      CodeBlobStub* stub = NEW_RESOURCE_OBJ(CodeBlobStub);
      new (stub) CodeBlobStub(cb);
      blobs.append(stub);
    }
  }
  if (blobs.length() == 0) {
    return NULL;
  }
  ThreadToNativeFromVM ttn(thread);
  jobjectArray result = NULL;
  jclass clazz = env->FindClass(vmSymbols::java_lang_Object()->as_C_string());
  CHECK_JNI_EXCEPTION_(env, NULL);
  result = env->NewObjectArray(blobs.length(), clazz, NULL);
  if (result == NULL) {
    return result;
  }
  int i = 0;
  for (GrowableArrayIterator<CodeBlobStub*> it = blobs.begin();
       it != blobs.end(); ++it) {
    jobjectArray obj = codeBlob2objectArray(thread, env, *it);
    env->SetObjectArrayElement(result, i, obj);
    ++i;
  }
  return result;
WB_END

WB_ENTRY(jint, WB_GetCompilationActivityMode(JNIEnv* env, jobject o))
  return CompileBroker::get_compilation_activity_mode();
WB_END

WB_ENTRY(jobjectArray, WB_GetCodeBlob(JNIEnv* env, jobject o, jlong addr))
  if (addr == 0) {
    THROW_MSG_NULL(vmSymbols::java_lang_NullPointerException(),
      "WB_GetCodeBlob: addr is null");
  }
  ThreadToNativeFromVM ttn(thread);
  CodeBlobStub stub((CodeBlob*) addr);
  return codeBlob2objectArray(thread, env, &stub);
WB_END

WB_ENTRY(jlong, WB_GetMethodData(JNIEnv* env, jobject wv, jobject method))
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  CHECK_JNI_EXCEPTION_(env, 0);
  methodHandle mh(thread, Method::checked_resolve_jmethod_id(jmid));
  return (jlong) mh->method_data();
WB_END

WB_ENTRY(jlong, WB_GetThreadStackSize(JNIEnv* env, jobject o))
  return (jlong) Thread::current()->stack_size();
WB_END

WB_ENTRY(jlong, WB_GetThreadRemainingStackSize(JNIEnv* env, jobject o))
  JavaThread* t = JavaThread::current();
  return (jlong) t->stack_available(os::current_stack_pointer()) - (jlong) StackShadowPages * os::vm_page_size();
WB_END


int WhiteBox::array_bytes_to_length(size_t bytes) {
  return Array<u1>::bytes_to_length(bytes);
}

WB_ENTRY(jlong, WB_AllocateMetaspace(JNIEnv* env, jobject wb, jobject class_loader, jlong size))
  if (size < 0) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(),
        err_msg("WB_AllocateMetaspace: size is negative: " JLONG_FORMAT, size));
  }

  oop class_loader_oop = JNIHandles::resolve(class_loader);
  ClassLoaderData* cld = class_loader_oop != NULL
      ? java_lang_ClassLoader::loader_data(class_loader_oop)
      : ClassLoaderData::the_null_class_loader_data();

  void* metadata = MetadataFactory::new_writeable_array<u1>(cld, WhiteBox::array_bytes_to_length((size_t)size), thread);

  return (jlong)(uintptr_t)metadata;
WB_END

WB_ENTRY(void, WB_FreeMetaspace(JNIEnv* env, jobject wb, jobject class_loader, jlong addr, jlong size))
  oop class_loader_oop = JNIHandles::resolve(class_loader);
  ClassLoaderData* cld = class_loader_oop != NULL
      ? java_lang_ClassLoader::loader_data(class_loader_oop)
      : ClassLoaderData::the_null_class_loader_data();

  MetadataFactory::free_array(cld, (Array<u1>*)(uintptr_t)addr);
WB_END

WB_ENTRY(jlong, WB_IncMetaspaceCapacityUntilGC(JNIEnv* env, jobject wb, jlong inc))
  if (inc < 0) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(),
        err_msg("WB_IncMetaspaceCapacityUntilGC: inc is negative: " JLONG_FORMAT, inc));
  }

  jlong max_size_t = (jlong) ((size_t) -1);
  if (inc > max_size_t) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(),
        err_msg("WB_IncMetaspaceCapacityUntilGC: inc does not fit in size_t: " JLONG_FORMAT, inc));
  }

  size_t new_cap_until_GC = 0;
  size_t aligned_inc = align_size_down((size_t) inc, Metaspace::commit_alignment());
  bool success = MetaspaceGC::inc_capacity_until_GC(aligned_inc, &new_cap_until_GC);
  if (!success) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalStateException(),
                "WB_IncMetaspaceCapacityUntilGC: could not increase capacity until GC "
                "due to contention with another thread");
  }
  return (jlong) new_cap_until_GC;
WB_END

WB_ENTRY(jlong, WB_MetaspaceCapacityUntilGC(JNIEnv* env, jobject wb))
  return (jlong) MetaspaceGC::capacity_until_GC();
WB_END



WB_ENTRY(void, WB_AssertMatchingSafepointCalls(JNIEnv* env, jobject o, jboolean mutexSafepointValue, jboolean attemptedNoSafepointValue))
  Monitor::SafepointCheckRequired sfpt_check_required = mutexSafepointValue ?
                                           Monitor::_safepoint_check_always :
                                           Monitor::_safepoint_check_never;
  MutexLockerEx ml(new Mutex(Mutex::leaf, "SFPT_Test_lock", true, sfpt_check_required),
                   attemptedNoSafepointValue == JNI_TRUE);
WB_END

WB_ENTRY(jboolean, WB_IsMonitorInflated(JNIEnv* env, jobject wb, jobject obj))
  oop obj_oop = JNIHandles::resolve(obj);
  return (jboolean) obj_oop->mark()->has_monitor();
WB_END

WB_ENTRY(void, WB_ForceSafepoint(JNIEnv* env, jobject wb))
  VM_ForceSafepoint force_safepoint_op;
  VMThread::execute(&force_safepoint_op);
WB_END

WB_ENTRY(long, WB_GetConstantPool(JNIEnv* env, jobject wb, jclass klass))
  instanceKlassHandle ikh(java_lang_Class::as_Klass(JNIHandles::resolve(klass)));
  return (long) ikh->constants();
WB_END

template <typename T>
static bool GetMethodOption(JavaThread* thread, JNIEnv* env, jobject method, jstring name, T* value) {
  assert(value != NULL, "sanity");
  if (method == NULL || name == NULL) {
    return false;
  }
  jmethodID jmid = reflected_method_to_jmid(thread, env, method);
  CHECK_JNI_EXCEPTION_(env, false);
  methodHandle mh(thread, Method::checked_resolve_jmethod_id(jmid));
  // can't be in VM when we call JNI
  ThreadToNativeFromVM ttnfv(thread);
  const char* flag_name = env->GetStringUTFChars(name, NULL);
  bool result =  CompilerOracle::has_option_value(mh, flag_name, *value);
  env->ReleaseStringUTFChars(name, flag_name);
  return result;
}

WB_ENTRY(jobject, WB_GetMethodBooleaneOption(JNIEnv* env, jobject wb, jobject method, jstring name))
  bool result;
  if (GetMethodOption<bool> (thread, env, method, name, &result)) {
    // can't be in VM when we call JNI
    ThreadToNativeFromVM ttnfv(thread);
    return booleanBox(thread, env, result);
  }
  return NULL;
WB_END

WB_ENTRY(jobject, WB_GetMethodIntxOption(JNIEnv* env, jobject wb, jobject method, jstring name))
  intx result;
  if (GetMethodOption <intx> (thread, env, method, name, &result)) {
    // can't be in VM when we call JNI
    ThreadToNativeFromVM ttnfv(thread);
    return longBox(thread, env, result);
  }
  return NULL;
WB_END

WB_ENTRY(jobject, WB_GetMethodUintxOption(JNIEnv* env, jobject wb, jobject method, jstring name))
  uintx result;
  if (GetMethodOption <uintx> (thread, env, method, name, &result)) {
    // can't be in VM when we call JNI
    ThreadToNativeFromVM ttnfv(thread);
    return longBox(thread, env, result);
  }
  return NULL;
WB_END

WB_ENTRY(jobject, WB_GetMethodDoubleOption(JNIEnv* env, jobject wb, jobject method, jstring name))
  double result;
  if (GetMethodOption <double> (thread, env, method, name, &result)) {
    // can't be in VM when we call JNI
    ThreadToNativeFromVM ttnfv(thread);
    return doubleBox(thread, env, result);
  }
  return NULL;
WB_END

WB_ENTRY(jobject, WB_GetMethodStringOption(JNIEnv* env, jobject wb, jobject method, jstring name))
  ccstr ccstrResult;
  if (GetMethodOption <ccstr> (thread, env, method, name, &ccstrResult)) {
    // can't be in VM when we call JNI
    ThreadToNativeFromVM ttnfv(thread);
    jstring result = env->NewStringUTF(ccstrResult);
    CHECK_JNI_EXCEPTION_(env, NULL);
    return result;
  }
  return NULL;
WB_END

WB_ENTRY(jboolean, WB_IsShared(JNIEnv* env, jobject wb, jobject obj))
  oop obj_oop = JNIHandles::resolve(obj);
  return MetaspaceShared::is_in_shared_space((void*)obj_oop);
WB_END

WB_ENTRY(jboolean, WB_AreSharedStringsIgnored(JNIEnv* env))
  return StringTable::shared_string_ignored();
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
    vm_exit_during_initialization("Invalid layout of preloaded class: use -XX:+TraceClassLoading to see the origin of the problem class");
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

void WhiteBox::register_methods(JNIEnv* env, jclass wbclass, JavaThread* thread, JNINativeMethod* method_array, int method_count) {
  ResourceMark rm;
  ThreadToNativeFromVM ttnfv(thread); // can't be in VM when we call JNI

  //  one by one registration natives for exception catching
  jclass no_such_method_error_klass = env->FindClass(vmSymbols::java_lang_NoSuchMethodError()->as_C_string());
  CHECK_JNI_EXCEPTION(env);
  for (int i = 0, n = method_count; i < n; ++i) {
    // Skip dummy entries
    if (method_array[i].fnPtr == NULL) continue;
    if (env->RegisterNatives(wbclass, &method_array[i], 1) != 0) {
      jthrowable throwable_obj = env->ExceptionOccurred();
      if (throwable_obj != NULL) {
        env->ExceptionClear();
        if (env->IsInstanceOf(throwable_obj, no_such_method_error_klass)) {
          // NoSuchMethodError is thrown when a method can't be found or a method is not native.
          // Ignoring the exception since it is not preventing use of other WhiteBox methods.
          tty->print_cr("Warning: 'NoSuchMethodError' on register of sun.hotspot.WhiteBox::%s%s",
              method_array[i].name, method_array[i].signature);
        }
      } else {
        // Registration failed unexpectedly.
        tty->print_cr("Warning: unexpected error on register of sun.hotspot.WhiteBox::%s%s. All methods will be unregistered",
            method_array[i].name, method_array[i].signature);
        env->UnregisterNatives(wbclass);
        break;
      }
    }
  }
}

#define CC (char*)

static JNINativeMethod methods[] = {
  {CC"getObjectAddress0",                CC"(Ljava/lang/Object;)J", (void*)&WB_GetObjectAddress  },
  {CC"getObjectSize0",                   CC"(Ljava/lang/Object;)J", (void*)&WB_GetObjectSize     },
  {CC"isObjectInOldGen0",                CC"(Ljava/lang/Object;)Z", (void*)&WB_isObjectInOldGen  },
  {CC"getHeapOopSize",                   CC"()I",                   (void*)&WB_GetHeapOopSize    },
  {CC"getVMPageSize",                    CC"()I",                   (void*)&WB_GetVMPageSize     },
  {CC"getVMAllocationGranularity",       CC"()J",                   (void*)&WB_GetVMAllocationGranularity },
  {CC"getVMLargePageSize",               CC"()J",                   (void*)&WB_GetVMLargePageSize},
  {CC"getHeapSpaceAlignment",            CC"()J",                   (void*)&WB_GetHeapSpaceAlignment},
  {CC"isClassAlive0",                    CC"(Ljava/lang/String;)Z", (void*)&WB_IsClassAlive      },
  {CC"parseCommandLine0",
      CC"(Ljava/lang/String;C[Lsun/hotspot/parser/DiagnosticCommand;)[Ljava/lang/Object;",
      (void*) &WB_ParseCommandLine
  },
  {CC"addToBootstrapClassLoaderSearch0", CC"(Ljava/lang/String;)V",
                                                      (void*)&WB_AddToBootstrapClassLoaderSearch},
  {CC"addToSystemClassLoaderSearch0",    CC"(Ljava/lang/String;)V",
                                                      (void*)&WB_AddToSystemClassLoaderSearch},
  {CC"getCompressedOopsMaxHeapSize", CC"()J",
      (void*)&WB_GetCompressedOopsMaxHeapSize},
  {CC"printHeapSizes",     CC"()V",                   (void*)&WB_PrintHeapSizes    },
  {CC"runMemoryUnitTests", CC"()V",                   (void*)&WB_RunMemoryUnitTests},
  {CC"readFromNoaccessArea",CC"()V",                  (void*)&WB_ReadFromNoaccessArea},
  {CC"stressVirtualSpaceResize",CC"(JJJ)I",           (void*)&WB_StressVirtualSpaceResize},
#if INCLUDE_ALL_GCS
  {CC"g1InConcurrentMark", CC"()Z",                   (void*)&WB_G1InConcurrentMark},
  {CC"g1IsHumongous0",      CC"(Ljava/lang/Object;)Z", (void*)&WB_G1IsHumongous     },
  {CC"g1NumMaxRegions",    CC"()J",                   (void*)&WB_G1NumMaxRegions  },
  {CC"g1NumFreeRegions",   CC"()J",                   (void*)&WB_G1NumFreeRegions  },
  {CC"g1RegionSize",       CC"()I",                   (void*)&WB_G1RegionSize      },
  {CC"g1StartConcMarkCycle",       CC"()Z",           (void*)&WB_G1StartMarkCycle  },
  {CC"g1AuxiliaryMemoryUsage", CC"()Ljava/lang/management/MemoryUsage;",
                                                      (void*)&WB_G1AuxiliaryMemoryUsage  },
  {CC"psVirtualSpaceAlignment",CC"()J",               (void*)&WB_PSVirtualSpaceAlignment},
  {CC"psHeapGenerationAlignment",CC"()J",             (void*)&WB_PSHeapGenerationAlignment},
#endif // INCLUDE_ALL_GCS
#if INCLUDE_NMT
  {CC"NMTMalloc",           CC"(J)J",                 (void*)&WB_NMTMalloc          },
  {CC"NMTMallocWithPseudoStack", CC"(JI)J",           (void*)&WB_NMTMallocWithPseudoStack},
  {CC"NMTFree",             CC"(J)V",                 (void*)&WB_NMTFree            },
  {CC"NMTReserveMemory",    CC"(J)J",                 (void*)&WB_NMTReserveMemory   },
  {CC"NMTCommitMemory",     CC"(JJ)V",                (void*)&WB_NMTCommitMemory    },
  {CC"NMTUncommitMemory",   CC"(JJ)V",                (void*)&WB_NMTUncommitMemory  },
  {CC"NMTReleaseMemory",    CC"(JJ)V",                (void*)&WB_NMTReleaseMemory   },
  {CC"NMTChangeTrackingLevel", CC"()Z",               (void*)&WB_NMTChangeTrackingLevel},
  {CC"NMTGetHashSize",      CC"()I",                  (void*)&WB_NMTGetHashSize     },
#endif // INCLUDE_NMT
  {CC"deoptimizeFrames",   CC"(Z)I",                  (void*)&WB_DeoptimizeFrames  },
  {CC"deoptimizeAll",      CC"()V",                   (void*)&WB_DeoptimizeAll     },
    {CC"deoptimizeMethod0",   CC"(Ljava/lang/reflect/Executable;Z)I",
                                                        (void*)&WB_DeoptimizeMethod  },
  {CC"isMethodCompiled0",   CC"(Ljava/lang/reflect/Executable;Z)Z",
                                                      (void*)&WB_IsMethodCompiled  },
  {CC"isMethodCompilable0", CC"(Ljava/lang/reflect/Executable;IZ)Z",
                                                      (void*)&WB_IsMethodCompilable},
  {CC"isMethodQueuedForCompilation0",
      CC"(Ljava/lang/reflect/Executable;)Z",          (void*)&WB_IsMethodQueuedForCompilation},
  {CC"isIntrinsicAvailable0",
      CC"(Ljava/lang/reflect/Executable;Ljava/lang/reflect/Executable;I)Z",
                                                      (void*)&WB_IsIntrinsicAvailable},
  {CC"makeMethodNotCompilable0",
      CC"(Ljava/lang/reflect/Executable;IZ)V",        (void*)&WB_MakeMethodNotCompilable},
  {CC"testSetDontInlineMethod0",
      CC"(Ljava/lang/reflect/Executable;Z)Z",         (void*)&WB_TestSetDontInlineMethod},
  {CC"getMethodCompilationLevel0",
      CC"(Ljava/lang/reflect/Executable;Z)I",         (void*)&WB_GetMethodCompilationLevel},
  {CC"getMethodEntryBci0",
      CC"(Ljava/lang/reflect/Executable;)I",          (void*)&WB_GetMethodEntryBci},
  {CC"getCompileQueueSize",
      CC"(I)I",                                       (void*)&WB_GetCompileQueueSize},
  {CC"testSetForceInlineMethod0",
      CC"(Ljava/lang/reflect/Executable;Z)Z",         (void*)&WB_TestSetForceInlineMethod},
  {CC"enqueueMethodForCompilation0",
      CC"(Ljava/lang/reflect/Executable;II)Z",        (void*)&WB_EnqueueMethodForCompilation},
  {CC"clearMethodState0",
      CC"(Ljava/lang/reflect/Executable;)V",          (void*)&WB_ClearMethodState},
  {CC"lockCompilation",    CC"()V",                   (void*)&WB_LockCompilation},
  {CC"unlockCompilation",  CC"()V",                   (void*)&WB_UnlockCompilation},
  {CC"matchesMethod",
      CC"(Ljava/lang/reflect/Executable;Ljava/lang/String;)I",
                                                      (void*)&WB_MatchesMethod},
  {CC"isConstantVMFlag",   CC"(Ljava/lang/String;)Z", (void*)&WB_IsConstantVMFlag},
  {CC"isLockedVMFlag",     CC"(Ljava/lang/String;)Z", (void*)&WB_IsLockedVMFlag},
  {CC"setBooleanVMFlag",   CC"(Ljava/lang/String;Z)V",(void*)&WB_SetBooleanVMFlag},
  {CC"setIntVMFlag",       CC"(Ljava/lang/String;J)V",(void*)&WB_SetIntVMFlag},
  {CC"setUintVMFlag",      CC"(Ljava/lang/String;J)V",(void*)&WB_SetUintVMFlag},
  {CC"setIntxVMFlag",      CC"(Ljava/lang/String;J)V",(void*)&WB_SetIntxVMFlag},
  {CC"setUintxVMFlag",     CC"(Ljava/lang/String;J)V",(void*)&WB_SetUintxVMFlag},
  {CC"setUint64VMFlag",    CC"(Ljava/lang/String;J)V",(void*)&WB_SetUint64VMFlag},
  {CC"setSizeTVMFlag",     CC"(Ljava/lang/String;J)V",(void*)&WB_SetSizeTVMFlag},
  {CC"setDoubleVMFlag",    CC"(Ljava/lang/String;D)V",(void*)&WB_SetDoubleVMFlag},
  {CC"setStringVMFlag",    CC"(Ljava/lang/String;Ljava/lang/String;)V",
                                                      (void*)&WB_SetStringVMFlag},
  {CC"getBooleanVMFlag",   CC"(Ljava/lang/String;)Ljava/lang/Boolean;",
                                                      (void*)&WB_GetBooleanVMFlag},
  {CC"getIntVMFlag",       CC"(Ljava/lang/String;)Ljava/lang/Long;",
                                                      (void*)&WB_GetIntVMFlag},
  {CC"getUintVMFlag",      CC"(Ljava/lang/String;)Ljava/lang/Long;",
                                                      (void*)&WB_GetUintVMFlag},
  {CC"getIntxVMFlag",      CC"(Ljava/lang/String;)Ljava/lang/Long;",
                                                      (void*)&WB_GetIntxVMFlag},
  {CC"getUintxVMFlag",     CC"(Ljava/lang/String;)Ljava/lang/Long;",
                                                      (void*)&WB_GetUintxVMFlag},
  {CC"getUint64VMFlag",    CC"(Ljava/lang/String;)Ljava/lang/Long;",
                                                      (void*)&WB_GetUint64VMFlag},
  {CC"getSizeTVMFlag",     CC"(Ljava/lang/String;)Ljava/lang/Long;",
                                                      (void*)&WB_GetSizeTVMFlag},
  {CC"getDoubleVMFlag",    CC"(Ljava/lang/String;)Ljava/lang/Double;",
                                                      (void*)&WB_GetDoubleVMFlag},
  {CC"getStringVMFlag",    CC"(Ljava/lang/String;)Ljava/lang/String;",
                                                      (void*)&WB_GetStringVMFlag},
  {CC"isInStringTable",    CC"(Ljava/lang/String;)Z", (void*)&WB_IsInStringTable  },
  {CC"fullGC",   CC"()V",                             (void*)&WB_FullGC },
  {CC"youngGC",  CC"()V",                             (void*)&WB_YoungGC },
  {CC"readReservedMemory", CC"()V",                   (void*)&WB_ReadReservedMemory },
  {CC"allocateMetaspace",
     CC"(Ljava/lang/ClassLoader;J)J",                 (void*)&WB_AllocateMetaspace },
  {CC"freeMetaspace",
     CC"(Ljava/lang/ClassLoader;JJ)V",                (void*)&WB_FreeMetaspace },
  {CC"incMetaspaceCapacityUntilGC", CC"(J)J",         (void*)&WB_IncMetaspaceCapacityUntilGC },
  {CC"metaspaceCapacityUntilGC", CC"()J",             (void*)&WB_MetaspaceCapacityUntilGC },
  {CC"getCPUFeatures",     CC"()Ljava/lang/String;",  (void*)&WB_GetCPUFeatures     },
  {CC"getNMethod0",         CC"(Ljava/lang/reflect/Executable;Z)[Ljava/lang/Object;",
                                                      (void*)&WB_GetNMethod         },
  {CC"forceNMethodSweep",  CC"()V",                   (void*)&WB_ForceNMethodSweep  },
  {CC"allocateCodeBlob",   CC"(II)J",                 (void*)&WB_AllocateCodeBlob   },
  {CC"freeCodeBlob",       CC"(J)V",                  (void*)&WB_FreeCodeBlob       },
  {CC"getCodeHeapEntries", CC"(I)[Ljava/lang/Object;",(void*)&WB_GetCodeHeapEntries },
  {CC"getCompilationActivityMode",
                           CC"()I",                   (void*)&WB_GetCompilationActivityMode},
  {CC"getMethodData0",     CC"(Ljava/lang/reflect/Executable;)J",
                                                      (void*)&WB_GetMethodData      },
  {CC"getCodeBlob",        CC"(J)[Ljava/lang/Object;",(void*)&WB_GetCodeBlob        },
  {CC"getThreadStackSize", CC"()J",                   (void*)&WB_GetThreadStackSize },
  {CC"getThreadRemainingStackSize", CC"()J",          (void*)&WB_GetThreadRemainingStackSize },
  {CC"assertMatchingSafepointCalls", CC"(ZZ)V",       (void*)&WB_AssertMatchingSafepointCalls },
  {CC"isMonitorInflated0", CC"(Ljava/lang/Object;)Z", (void*)&WB_IsMonitorInflated  },
  {CC"forceSafepoint",     CC"()V",                   (void*)&WB_ForceSafepoint     },
  {CC"getConstantPool0",   CC"(Ljava/lang/Class;)J",  (void*)&WB_GetConstantPool    },
  {CC"getMethodBooleanOption",
      CC"(Ljava/lang/reflect/Executable;Ljava/lang/String;)Ljava/lang/Boolean;",
                                                      (void*)&WB_GetMethodBooleaneOption},
  {CC"getMethodIntxOption",
      CC"(Ljava/lang/reflect/Executable;Ljava/lang/String;)Ljava/lang/Long;",
                                                      (void*)&WB_GetMethodIntxOption},
  {CC"getMethodUintxOption",
      CC"(Ljava/lang/reflect/Executable;Ljava/lang/String;)Ljava/lang/Long;",
                                                      (void*)&WB_GetMethodUintxOption},
  {CC"getMethodDoubleOption",
      CC"(Ljava/lang/reflect/Executable;Ljava/lang/String;)Ljava/lang/Double;",
                                                      (void*)&WB_GetMethodDoubleOption},
  {CC"getMethodStringOption",
      CC"(Ljava/lang/reflect/Executable;Ljava/lang/String;)Ljava/lang/String;",
                                                      (void*)&WB_GetMethodStringOption},
  {CC"isShared",           CC"(Ljava/lang/Object;)Z", (void*)&WB_IsShared },
  {CC"areSharedStringsIgnored",           CC"()Z",    (void*)&WB_AreSharedStringsIgnored },
};

#undef CC

JVM_ENTRY(void, JVM_RegisterWhiteBoxMethods(JNIEnv* env, jclass wbclass))
  {
    if (WhiteBoxAPI) {
      // Make sure that wbclass is loaded by the null classloader
      instanceKlassHandle ikh = instanceKlassHandle(JNIHandles::resolve(wbclass)->klass());
      Handle loader(ikh->class_loader());
      if (loader.is_null()) {
        WhiteBox::register_methods(env, wbclass, thread, methods, sizeof(methods) / sizeof(methods[0]));
        WhiteBox::register_extended(env, wbclass, thread);
        WhiteBox::set_used();
      }
    }
  }
JVM_END
