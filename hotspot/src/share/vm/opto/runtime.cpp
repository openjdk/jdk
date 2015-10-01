/*
 * Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/compiledIC.hpp"
#include "code/icBuffer.hpp"
#include "code/nmethod.hpp"
#include "code/pcDesc.hpp"
#include "code/scopeDesc.hpp"
#include "code/vtableStubs.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/oopMap.hpp"
#include "gc/g1/g1SATBCardTableModRefBS.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcLocker.inline.hpp"
#include "interpreter/bytecode.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/linkResolver.hpp"
#include "memory/oopFactory.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.inline.hpp"
#include "opto/ad.hpp"
#include "opto/addnode.hpp"
#include "opto/callnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/graphKit.hpp"
#include "opto/machnode.hpp"
#include "opto/matcher.hpp"
#include "opto/memnode.hpp"
#include "opto/mulnode.hpp"
#include "opto/runtime.hpp"
#include "opto/subnode.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/fprofiler.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "runtime/threadCritical.hpp"
#include "runtime/vframe.hpp"
#include "runtime/vframeArray.hpp"
#include "runtime/vframe_hp.hpp"
#include "utilities/copy.hpp"
#include "utilities/preserveException.hpp"


// For debugging purposes:
//  To force FullGCALot inside a runtime function, add the following two lines
//
//  Universe::release_fullgc_alot_dummy();
//  MarkSweep::invoke(0, "Debugging");
//
// At command line specify the parameters: -XX:+FullGCALot -XX:FullGCALotStart=100000000




// Compiled code entry points
address OptoRuntime::_new_instance_Java                           = NULL;
address OptoRuntime::_new_array_Java                              = NULL;
address OptoRuntime::_new_array_nozero_Java                       = NULL;
address OptoRuntime::_multianewarray2_Java                        = NULL;
address OptoRuntime::_multianewarray3_Java                        = NULL;
address OptoRuntime::_multianewarray4_Java                        = NULL;
address OptoRuntime::_multianewarray5_Java                        = NULL;
address OptoRuntime::_multianewarrayN_Java                        = NULL;
address OptoRuntime::_g1_wb_pre_Java                              = NULL;
address OptoRuntime::_g1_wb_post_Java                             = NULL;
address OptoRuntime::_vtable_must_compile_Java                    = NULL;
address OptoRuntime::_complete_monitor_locking_Java               = NULL;
address OptoRuntime::_monitor_notify_Java                         = NULL;
address OptoRuntime::_monitor_notifyAll_Java                      = NULL;
address OptoRuntime::_rethrow_Java                                = NULL;

address OptoRuntime::_slow_arraycopy_Java                         = NULL;
address OptoRuntime::_register_finalizer_Java                     = NULL;

ExceptionBlob* OptoRuntime::_exception_blob;

// This should be called in an assertion at the start of OptoRuntime routines
// which are entered from compiled code (all of them)
#ifdef ASSERT
static bool check_compiled_frame(JavaThread* thread) {
  assert(thread->last_frame().is_runtime_frame(), "cannot call runtime directly from compiled code");
  RegisterMap map(thread, false);
  frame caller = thread->last_frame().sender(&map);
  assert(caller.is_compiled_frame(), "not being called from compiled like code");
  return true;
}
#endif // ASSERT


#define gen(env, var, type_func_gen, c_func, fancy_jump, pass_tls, save_arg_regs, return_pc) \
  var = generate_stub(env, type_func_gen, CAST_FROM_FN_PTR(address, c_func), #var, fancy_jump, pass_tls, save_arg_regs, return_pc); \
  if (var == NULL) { return false; }

bool OptoRuntime::generate(ciEnv* env) {

  generate_exception_blob();

  // Note: tls: Means fetching the return oop out of the thread-local storage
  //
  //   variable/name                       type-function-gen              , runtime method                  ,fncy_jp, tls,save_args,retpc
  // -------------------------------------------------------------------------------------------------------------------------------
  gen(env, _new_instance_Java              , new_instance_Type            , new_instance_C                  ,    0 , true , false, false);
  gen(env, _new_array_Java                 , new_array_Type               , new_array_C                     ,    0 , true , false, false);
  gen(env, _new_array_nozero_Java          , new_array_Type               , new_array_nozero_C              ,    0 , true , false, false);
  gen(env, _multianewarray2_Java           , multianewarray2_Type         , multianewarray2_C               ,    0 , true , false, false);
  gen(env, _multianewarray3_Java           , multianewarray3_Type         , multianewarray3_C               ,    0 , true , false, false);
  gen(env, _multianewarray4_Java           , multianewarray4_Type         , multianewarray4_C               ,    0 , true , false, false);
  gen(env, _multianewarray5_Java           , multianewarray5_Type         , multianewarray5_C               ,    0 , true , false, false);
  gen(env, _multianewarrayN_Java           , multianewarrayN_Type         , multianewarrayN_C               ,    0 , true , false, false);
  gen(env, _g1_wb_pre_Java                 , g1_wb_pre_Type               , SharedRuntime::g1_wb_pre        ,    0 , false, false, false);
  gen(env, _g1_wb_post_Java                , g1_wb_post_Type              , SharedRuntime::g1_wb_post       ,    0 , false, false, false);
  gen(env, _complete_monitor_locking_Java  , complete_monitor_enter_Type  , SharedRuntime::complete_monitor_locking_C, 0, false, false, false);
  gen(env, _monitor_notify_Java            , monitor_notify_Type          , monitor_notify_C                ,    0 , false, false, false);
  gen(env, _monitor_notifyAll_Java         , monitor_notify_Type          , monitor_notifyAll_C             ,    0 , false, false, false);
  gen(env, _rethrow_Java                   , rethrow_Type                 , rethrow_C                       ,    2 , true , false, true );

  gen(env, _slow_arraycopy_Java            , slow_arraycopy_Type          , SharedRuntime::slow_arraycopy_C ,    0 , false, false, false);
  gen(env, _register_finalizer_Java        , register_finalizer_Type      , register_finalizer              ,    0 , false, false, false);

  return true;
}

#undef gen


// Helper method to do generation of RunTimeStub's
address OptoRuntime::generate_stub( ciEnv* env,
                                    TypeFunc_generator gen, address C_function,
                                    const char *name, int is_fancy_jump,
                                    bool pass_tls,
                                    bool save_argument_registers,
                                    bool return_pc ) {
  ResourceMark rm;
  Compile C( env, gen, C_function, name, is_fancy_jump, pass_tls, save_argument_registers, return_pc );
  return  C.stub_entry_point();
}

const char* OptoRuntime::stub_name(address entry) {
#ifndef PRODUCT
  CodeBlob* cb = CodeCache::find_blob(entry);
  RuntimeStub* rs =(RuntimeStub *)cb;
  assert(rs != NULL && rs->is_runtime_stub(), "not a runtime stub");
  return rs->name();
#else
  // Fast implementation for product mode (maybe it should be inlined too)
  return "runtime stub";
#endif
}


//=============================================================================
// Opto compiler runtime routines
//=============================================================================


//=============================allocation======================================
// We failed the fast-path allocation.  Now we need to do a scavenge or GC
// and try allocation again.

void OptoRuntime::new_store_pre_barrier(JavaThread* thread) {
  // After any safepoint, just before going back to compiled code,
  // we inform the GC that we will be doing initializing writes to
  // this object in the future without emitting card-marks, so
  // GC may take any compensating steps.
  // NOTE: Keep this code consistent with GraphKit::store_barrier.

  oop new_obj = thread->vm_result();
  if (new_obj == NULL)  return;

  assert(Universe::heap()->can_elide_tlab_store_barriers(),
         "compiler must check this first");
  // GC may decide to give back a safer copy of new_obj.
  new_obj = Universe::heap()->new_store_pre_barrier(thread, new_obj);
  thread->set_vm_result(new_obj);
}

// object allocation
JRT_BLOCK_ENTRY(void, OptoRuntime::new_instance_C(Klass* klass, JavaThread* thread))
  JRT_BLOCK;
#ifndef PRODUCT
  SharedRuntime::_new_instance_ctr++;         // new instance requires GC
#endif
  assert(check_compiled_frame(thread), "incorrect caller");

  // These checks are cheap to make and support reflective allocation.
  int lh = klass->layout_helper();
  if (Klass::layout_helper_needs_slow_path(lh)
      || !InstanceKlass::cast(klass)->is_initialized()) {
    KlassHandle kh(THREAD, klass);
    kh->check_valid_for_instantiation(false, THREAD);
    if (!HAS_PENDING_EXCEPTION) {
      InstanceKlass::cast(kh())->initialize(THREAD);
    }
    if (!HAS_PENDING_EXCEPTION) {
      klass = kh();
    } else {
      klass = NULL;
    }
  }

  if (klass != NULL) {
    // Scavenge and allocate an instance.
    oop result = InstanceKlass::cast(klass)->allocate_instance(THREAD);
    thread->set_vm_result(result);

    // Pass oops back through thread local storage.  Our apparent type to Java
    // is that we return an oop, but we can block on exit from this routine and
    // a GC can trash the oop in C's return register.  The generated stub will
    // fetch the oop from TLS after any possible GC.
  }

  deoptimize_caller_frame(thread, HAS_PENDING_EXCEPTION);
  JRT_BLOCK_END;

  if (GraphKit::use_ReduceInitialCardMarks()) {
    // inform GC that we won't do card marks for initializing writes.
    new_store_pre_barrier(thread);
  }
JRT_END


// array allocation
JRT_BLOCK_ENTRY(void, OptoRuntime::new_array_C(Klass* array_type, int len, JavaThread *thread))
  JRT_BLOCK;
#ifndef PRODUCT
  SharedRuntime::_new_array_ctr++;            // new array requires GC
#endif
  assert(check_compiled_frame(thread), "incorrect caller");

  // Scavenge and allocate an instance.
  oop result;

  if (array_type->oop_is_typeArray()) {
    // The oopFactory likes to work with the element type.
    // (We could bypass the oopFactory, since it doesn't add much value.)
    BasicType elem_type = TypeArrayKlass::cast(array_type)->element_type();
    result = oopFactory::new_typeArray(elem_type, len, THREAD);
  } else {
    // Although the oopFactory likes to work with the elem_type,
    // the compiler prefers the array_type, since it must already have
    // that latter value in hand for the fast path.
    Klass* elem_type = ObjArrayKlass::cast(array_type)->element_klass();
    result = oopFactory::new_objArray(elem_type, len, THREAD);
  }

  // Pass oops back through thread local storage.  Our apparent type to Java
  // is that we return an oop, but we can block on exit from this routine and
  // a GC can trash the oop in C's return register.  The generated stub will
  // fetch the oop from TLS after any possible GC.
  deoptimize_caller_frame(thread, HAS_PENDING_EXCEPTION);
  thread->set_vm_result(result);
  JRT_BLOCK_END;

  if (GraphKit::use_ReduceInitialCardMarks()) {
    // inform GC that we won't do card marks for initializing writes.
    new_store_pre_barrier(thread);
  }
JRT_END

// array allocation without zeroing
JRT_BLOCK_ENTRY(void, OptoRuntime::new_array_nozero_C(Klass* array_type, int len, JavaThread *thread))
  JRT_BLOCK;
#ifndef PRODUCT
  SharedRuntime::_new_array_ctr++;            // new array requires GC
#endif
  assert(check_compiled_frame(thread), "incorrect caller");

  // Scavenge and allocate an instance.
  oop result;

  assert(array_type->oop_is_typeArray(), "should be called only for type array");
  // The oopFactory likes to work with the element type.
  BasicType elem_type = TypeArrayKlass::cast(array_type)->element_type();
  result = oopFactory::new_typeArray_nozero(elem_type, len, THREAD);

  // Pass oops back through thread local storage.  Our apparent type to Java
  // is that we return an oop, but we can block on exit from this routine and
  // a GC can trash the oop in C's return register.  The generated stub will
  // fetch the oop from TLS after any possible GC.
  deoptimize_caller_frame(thread, HAS_PENDING_EXCEPTION);
  thread->set_vm_result(result);
  JRT_BLOCK_END;

  if (GraphKit::use_ReduceInitialCardMarks()) {
    // inform GC that we won't do card marks for initializing writes.
    new_store_pre_barrier(thread);
  }

  oop result = thread->vm_result();
  if ((len > 0) && (result != NULL) &&
      is_deoptimized_caller_frame(thread)) {
    // Zero array here if the caller is deoptimized.
    int size = ((typeArrayOop)result)->object_size();
    BasicType elem_type = TypeArrayKlass::cast(array_type)->element_type();
    const size_t hs = arrayOopDesc::header_size(elem_type);
    // Align to next 8 bytes to avoid trashing arrays's length.
    const size_t aligned_hs = align_object_offset(hs);
    HeapWord* obj = (HeapWord*)result;
    if (aligned_hs > hs) {
      Copy::zero_to_words(obj+hs, aligned_hs-hs);
    }
    // Optimized zeroing.
    Copy::fill_to_aligned_words(obj+aligned_hs, size-aligned_hs);
  }

JRT_END

// Note: multianewarray for one dimension is handled inline by GraphKit::new_array.

// multianewarray for 2 dimensions
JRT_ENTRY(void, OptoRuntime::multianewarray2_C(Klass* elem_type, int len1, int len2, JavaThread *thread))
#ifndef PRODUCT
  SharedRuntime::_multi2_ctr++;                // multianewarray for 1 dimension
#endif
  assert(check_compiled_frame(thread), "incorrect caller");
  assert(elem_type->is_klass(), "not a class");
  jint dims[2];
  dims[0] = len1;
  dims[1] = len2;
  oop obj = ArrayKlass::cast(elem_type)->multi_allocate(2, dims, THREAD);
  deoptimize_caller_frame(thread, HAS_PENDING_EXCEPTION);
  thread->set_vm_result(obj);
JRT_END

// multianewarray for 3 dimensions
JRT_ENTRY(void, OptoRuntime::multianewarray3_C(Klass* elem_type, int len1, int len2, int len3, JavaThread *thread))
#ifndef PRODUCT
  SharedRuntime::_multi3_ctr++;                // multianewarray for 1 dimension
#endif
  assert(check_compiled_frame(thread), "incorrect caller");
  assert(elem_type->is_klass(), "not a class");
  jint dims[3];
  dims[0] = len1;
  dims[1] = len2;
  dims[2] = len3;
  oop obj = ArrayKlass::cast(elem_type)->multi_allocate(3, dims, THREAD);
  deoptimize_caller_frame(thread, HAS_PENDING_EXCEPTION);
  thread->set_vm_result(obj);
JRT_END

// multianewarray for 4 dimensions
JRT_ENTRY(void, OptoRuntime::multianewarray4_C(Klass* elem_type, int len1, int len2, int len3, int len4, JavaThread *thread))
#ifndef PRODUCT
  SharedRuntime::_multi4_ctr++;                // multianewarray for 1 dimension
#endif
  assert(check_compiled_frame(thread), "incorrect caller");
  assert(elem_type->is_klass(), "not a class");
  jint dims[4];
  dims[0] = len1;
  dims[1] = len2;
  dims[2] = len3;
  dims[3] = len4;
  oop obj = ArrayKlass::cast(elem_type)->multi_allocate(4, dims, THREAD);
  deoptimize_caller_frame(thread, HAS_PENDING_EXCEPTION);
  thread->set_vm_result(obj);
JRT_END

// multianewarray for 5 dimensions
JRT_ENTRY(void, OptoRuntime::multianewarray5_C(Klass* elem_type, int len1, int len2, int len3, int len4, int len5, JavaThread *thread))
#ifndef PRODUCT
  SharedRuntime::_multi5_ctr++;                // multianewarray for 1 dimension
#endif
  assert(check_compiled_frame(thread), "incorrect caller");
  assert(elem_type->is_klass(), "not a class");
  jint dims[5];
  dims[0] = len1;
  dims[1] = len2;
  dims[2] = len3;
  dims[3] = len4;
  dims[4] = len5;
  oop obj = ArrayKlass::cast(elem_type)->multi_allocate(5, dims, THREAD);
  deoptimize_caller_frame(thread, HAS_PENDING_EXCEPTION);
  thread->set_vm_result(obj);
JRT_END

JRT_ENTRY(void, OptoRuntime::multianewarrayN_C(Klass* elem_type, arrayOopDesc* dims, JavaThread *thread))
  assert(check_compiled_frame(thread), "incorrect caller");
  assert(elem_type->is_klass(), "not a class");
  assert(oop(dims)->is_typeArray(), "not an array");

  ResourceMark rm;
  jint len = dims->length();
  assert(len > 0, "Dimensions array should contain data");
  jint *j_dims = typeArrayOop(dims)->int_at_addr(0);
  jint *c_dims = NEW_RESOURCE_ARRAY(jint, len);
  Copy::conjoint_jints_atomic(j_dims, c_dims, len);

  oop obj = ArrayKlass::cast(elem_type)->multi_allocate(len, c_dims, THREAD);
  deoptimize_caller_frame(thread, HAS_PENDING_EXCEPTION);
  thread->set_vm_result(obj);
JRT_END

JRT_BLOCK_ENTRY(void, OptoRuntime::monitor_notify_C(oopDesc* obj, JavaThread *thread))

  // Very few notify/notifyAll operations find any threads on the waitset, so
  // the dominant fast-path is to simply return.
  // Relatedly, it's critical that notify/notifyAll be fast in order to
  // reduce lock hold times.
  if (!SafepointSynchronize::is_synchronizing()) {
    if (ObjectSynchronizer::quick_notify(obj, thread, false)) {
      return;
    }
  }

  // This is the case the fast-path above isn't provisioned to handle.
  // The fast-path is designed to handle frequently arising cases in an efficient manner.
  // (The fast-path is just a degenerate variant of the slow-path).
  // Perform the dreaded state transition and pass control into the slow-path.
  JRT_BLOCK;
  Handle h_obj(THREAD, obj);
  ObjectSynchronizer::notify(h_obj, CHECK);
  JRT_BLOCK_END;
JRT_END

JRT_BLOCK_ENTRY(void, OptoRuntime::monitor_notifyAll_C(oopDesc* obj, JavaThread *thread))

  if (!SafepointSynchronize::is_synchronizing() ) {
    if (ObjectSynchronizer::quick_notify(obj, thread, true)) {
      return;
    }
  }

  // This is the case the fast-path above isn't provisioned to handle.
  // The fast-path is designed to handle frequently arising cases in an efficient manner.
  // (The fast-path is just a degenerate variant of the slow-path).
  // Perform the dreaded state transition and pass control into the slow-path.
  JRT_BLOCK;
  Handle h_obj(THREAD, obj);
  ObjectSynchronizer::notifyall(h_obj, CHECK);
  JRT_BLOCK_END;
JRT_END

const TypeFunc *OptoRuntime::new_instance_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL; // Klass to be allocated
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+1, fields);

  // create result type (range)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeRawPtr::NOTNULL; // Returned oop

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

  return TypeFunc::make(domain, range);
}


const TypeFunc *OptoRuntime::athrow_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL; // Klass to be allocated
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+1, fields);

  // create result type (range)
  fields = TypeTuple::fields(0);

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0, fields);

  return TypeFunc::make(domain, range);
}


const TypeFunc *OptoRuntime::new_array_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL;   // element klass
  fields[TypeFunc::Parms+1] = TypeInt::INT;       // array size
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2, fields);

  // create result type (range)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeRawPtr::NOTNULL; // Returned oop

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

  return TypeFunc::make(domain, range);
}

const TypeFunc *OptoRuntime::multianewarray_Type(int ndim) {
  // create input type (domain)
  const int nargs = ndim + 1;
  const Type **fields = TypeTuple::fields(nargs);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL;   // element klass
  for( int i = 1; i < nargs; i++ )
    fields[TypeFunc::Parms + i] = TypeInt::INT;       // array size
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+nargs, fields);

  // create result type (range)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeRawPtr::NOTNULL; // Returned oop
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

  return TypeFunc::make(domain, range);
}

const TypeFunc *OptoRuntime::multianewarray2_Type() {
  return multianewarray_Type(2);
}

const TypeFunc *OptoRuntime::multianewarray3_Type() {
  return multianewarray_Type(3);
}

const TypeFunc *OptoRuntime::multianewarray4_Type() {
  return multianewarray_Type(4);
}

const TypeFunc *OptoRuntime::multianewarray5_Type() {
  return multianewarray_Type(5);
}

const TypeFunc *OptoRuntime::multianewarrayN_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL;   // element klass
  fields[TypeFunc::Parms+1] = TypeInstPtr::NOTNULL;   // array of dim sizes
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2, fields);

  // create result type (range)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeRawPtr::NOTNULL; // Returned oop
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

  return TypeFunc::make(domain, range);
}

const TypeFunc *OptoRuntime::g1_wb_pre_Type() {
  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL; // original field value
  fields[TypeFunc::Parms+1] = TypeRawPtr::NOTNULL; // thread
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2, fields);

  // create result type (range)
  fields = TypeTuple::fields(0);
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0, fields);

  return TypeFunc::make(domain, range);
}

const TypeFunc *OptoRuntime::g1_wb_post_Type() {

  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeRawPtr::NOTNULL;  // Card addr
  fields[TypeFunc::Parms+1] = TypeRawPtr::NOTNULL;  // thread
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2, fields);

  // create result type (range)
  fields = TypeTuple::fields(0);
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms, fields);

  return TypeFunc::make(domain, range);
}

const TypeFunc *OptoRuntime::uncommon_trap_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInt::INT; // trap_reason (deopt reason and action)
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+1, fields);

  // create result type (range)
  fields = TypeTuple::fields(0);
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0, fields);

  return TypeFunc::make(domain, range);
}

//-----------------------------------------------------------------------------
// Monitor Handling
const TypeFunc *OptoRuntime::complete_monitor_enter_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL;  // Object to be Locked
  fields[TypeFunc::Parms+1] = TypeRawPtr::BOTTOM;   // Address of stack location for lock
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2,fields);

  // create result type (range)
  fields = TypeTuple::fields(0);

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0,fields);

  return TypeFunc::make(domain,range);
}


//-----------------------------------------------------------------------------
const TypeFunc *OptoRuntime::complete_monitor_exit_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(3);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL;  // Object to be Locked
  fields[TypeFunc::Parms+1] = TypeRawPtr::BOTTOM;    // Address of stack location for lock - BasicLock
  fields[TypeFunc::Parms+2] = TypeRawPtr::BOTTOM;    // Thread pointer (Self)
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+3, fields);

  // create result type (range)
  fields = TypeTuple::fields(0);

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0, fields);

  return TypeFunc::make(domain, range);
}

const TypeFunc *OptoRuntime::monitor_notify_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL;  // Object to be Locked
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+1, fields);

  // create result type (range)
  fields = TypeTuple::fields(0);
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0, fields);
  return TypeFunc::make(domain, range);
}

const TypeFunc* OptoRuntime::flush_windows_Type() {
  // create input type (domain)
  const Type** fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = NULL; // void
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms, fields);

  // create result type
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = NULL; // void
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms, fields);

  return TypeFunc::make(domain, range);
}

const TypeFunc* OptoRuntime::l2f_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeLong::LONG;
  fields[TypeFunc::Parms+1] = Type::HALF;
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2, fields);

  // create result type (range)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = Type::FLOAT;
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

  return TypeFunc::make(domain, range);
}

const TypeFunc* OptoRuntime::modf_Type() {
  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = Type::FLOAT;
  fields[TypeFunc::Parms+1] = Type::FLOAT;
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2, fields);

  // create result type (range)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = Type::FLOAT;

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

  return TypeFunc::make(domain, range);
}

const TypeFunc *OptoRuntime::Math_D_D_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(2);
  // Symbol* name of class to be loaded
  fields[TypeFunc::Parms+0] = Type::DOUBLE;
  fields[TypeFunc::Parms+1] = Type::HALF;
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2, fields);

  // create result type (range)
  fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = Type::DOUBLE;
  fields[TypeFunc::Parms+1] = Type::HALF;
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+2, fields);

  return TypeFunc::make(domain, range);
}

const TypeFunc* OptoRuntime::Math_DD_D_Type() {
  const Type **fields = TypeTuple::fields(4);
  fields[TypeFunc::Parms+0] = Type::DOUBLE;
  fields[TypeFunc::Parms+1] = Type::HALF;
  fields[TypeFunc::Parms+2] = Type::DOUBLE;
  fields[TypeFunc::Parms+3] = Type::HALF;
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+4, fields);

  // create result type (range)
  fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = Type::DOUBLE;
  fields[TypeFunc::Parms+1] = Type::HALF;
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+2, fields);

  return TypeFunc::make(domain, range);
}

//-------------- currentTimeMillis, currentTimeNanos, etc

const TypeFunc* OptoRuntime::void_long_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(0);
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+0, fields);

  // create result type (range)
  fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeLong::LONG;
  fields[TypeFunc::Parms+1] = Type::HALF;
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+2, fields);

  return TypeFunc::make(domain, range);
}

// arraycopy stub variations:
enum ArrayCopyType {
  ac_fast,                      // void(ptr, ptr, size_t)
  ac_checkcast,                 //  int(ptr, ptr, size_t, size_t, ptr)
  ac_slow,                      // void(ptr, int, ptr, int, int)
  ac_generic                    //  int(ptr, int, ptr, int, int)
};

static const TypeFunc* make_arraycopy_Type(ArrayCopyType act) {
  // create input type (domain)
  int num_args      = (act == ac_fast ? 3 : 5);
  int num_size_args = (act == ac_fast ? 1 : act == ac_checkcast ? 2 : 0);
  int argcnt = num_args;
  LP64_ONLY(argcnt += num_size_args); // halfwords for lengths
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // src
  if (num_size_args == 0) {
    fields[argp++] = TypeInt::INT;      // src_pos
  }
  fields[argp++] = TypePtr::NOTNULL;    // dest
  if (num_size_args == 0) {
    fields[argp++] = TypeInt::INT;      // dest_pos
    fields[argp++] = TypeInt::INT;      // length
  }
  while (num_size_args-- > 0) {
    fields[argp++] = TypeX_X;               // size in whatevers (size_t)
    LP64_ONLY(fields[argp++] = Type::HALF); // other half of long length
  }
  if (act == ac_checkcast) {
    fields[argp++] = TypePtr::NOTNULL;  // super_klass
  }
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding of act");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // create result type if needed
  int retcnt = (act == ac_checkcast || act == ac_generic ? 1 : 0);
  fields = TypeTuple::fields(1);
  if (retcnt == 0)
    fields[TypeFunc::Parms+0] = NULL; // void
  else
    fields[TypeFunc::Parms+0] = TypeInt::INT; // status result, if needed
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms+retcnt, fields);
  return TypeFunc::make(domain, range);
}

const TypeFunc* OptoRuntime::fast_arraycopy_Type() {
  // This signature is simple:  Two base pointers and a size_t.
  return make_arraycopy_Type(ac_fast);
}

const TypeFunc* OptoRuntime::checkcast_arraycopy_Type() {
  // An extension of fast_arraycopy_Type which adds type checking.
  return make_arraycopy_Type(ac_checkcast);
}

const TypeFunc* OptoRuntime::slow_arraycopy_Type() {
  // This signature is exactly the same as System.arraycopy.
  // There are no intptr_t (int/long) arguments.
  return make_arraycopy_Type(ac_slow);
}

const TypeFunc* OptoRuntime::generic_arraycopy_Type() {
  // This signature is like System.arraycopy, except that it returns status.
  return make_arraycopy_Type(ac_generic);
}


const TypeFunc* OptoRuntime::array_fill_Type() {
  const Type** fields;
  int argp = TypeFunc::Parms;
  // create input type (domain): pointer, int, size_t
  fields = TypeTuple::fields(3 LP64_ONLY( + 1));
  fields[argp++] = TypePtr::NOTNULL;
  fields[argp++] = TypeInt::INT;
  fields[argp++] = TypeX_X;               // size in whatevers (size_t)
  LP64_ONLY(fields[argp++] = Type::HALF); // other half of long length
  const TypeTuple *domain = TypeTuple::make(argp, fields);

  // create result type
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = NULL; // void
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms, fields);

  return TypeFunc::make(domain, range);
}

// for aescrypt encrypt/decrypt operations, just three pointers returning void (length is constant)
const TypeFunc* OptoRuntime::aescrypt_block_Type() {
  // create input type (domain)
  int num_args      = 3;
  if (Matcher::pass_original_key_for_aes()) {
    num_args = 4;
  }
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // src
  fields[argp++] = TypePtr::NOTNULL;    // dest
  fields[argp++] = TypePtr::NOTNULL;    // k array
  if (Matcher::pass_original_key_for_aes()) {
    fields[argp++] = TypePtr::NOTNULL;    // original k array
  }
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // no result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = NULL; // void
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

/**
 * int updateBytesCRC32(int crc, byte* b, int len)
 */
const TypeFunc* OptoRuntime::updateBytesCRC32_Type() {
  // create input type (domain)
  int num_args      = 3;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypeInt::INT;        // crc
  fields[argp++] = TypePtr::NOTNULL;    // src
  fields[argp++] = TypeInt::INT;        // len
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInt::INT; // crc result
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms+1, fields);
  return TypeFunc::make(domain, range);
}

/**
 * int updateBytesCRC32C(int crc, byte* buf, int len, int* table)
 */
const TypeFunc* OptoRuntime::updateBytesCRC32C_Type() {
  // create input type (domain)
  int num_args      = 4;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypeInt::INT;        // crc
  fields[argp++] = TypePtr::NOTNULL;    // buf
  fields[argp++] = TypeInt::INT;        // len
  fields[argp++] = TypePtr::NOTNULL;    // table
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInt::INT; // crc result
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms+1, fields);
  return TypeFunc::make(domain, range);
}

/**
*  int updateBytesAdler32(int adler, bytes* b, int off, int len)
*/
const TypeFunc* OptoRuntime::updateBytesAdler32_Type() {
  // create input type (domain)
  int num_args      = 3;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypeInt::INT;        // crc
  fields[argp++] = TypePtr::NOTNULL;    // src + offset
  fields[argp++] = TypeInt::INT;        // len
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInt::INT; // crc result
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms+1, fields);
  return TypeFunc::make(domain, range);
}

// for cipherBlockChaining calls of aescrypt encrypt/decrypt, four pointers and a length, returning int
const TypeFunc* OptoRuntime::cipherBlockChaining_aescrypt_Type() {
  // create input type (domain)
  int num_args      = 5;
  if (Matcher::pass_original_key_for_aes()) {
    num_args = 6;
  }
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // src
  fields[argp++] = TypePtr::NOTNULL;    // dest
  fields[argp++] = TypePtr::NOTNULL;    // k array
  fields[argp++] = TypePtr::NOTNULL;    // r array
  fields[argp++] = TypeInt::INT;        // src len
  if (Matcher::pass_original_key_for_aes()) {
    fields[argp++] = TypePtr::NOTNULL;    // original k array
  }
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // returning cipher len (int)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInt::INT;
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms+1, fields);
  return TypeFunc::make(domain, range);
}

/*
 * void implCompress(byte[] buf, int ofs)
 */
const TypeFunc* OptoRuntime::sha_implCompress_Type() {
  // create input type (domain)
  int num_args = 2;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL; // buf
  fields[argp++] = TypePtr::NOTNULL; // state
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // no result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = NULL; // void
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

/*
 * int implCompressMultiBlock(byte[] b, int ofs, int limit)
 */
const TypeFunc* OptoRuntime::digestBase_implCompressMB_Type() {
  // create input type (domain)
  int num_args = 4;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL; // buf
  fields[argp++] = TypePtr::NOTNULL; // state
  fields[argp++] = TypeInt::INT;     // ofs
  fields[argp++] = TypeInt::INT;     // limit
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // returning ofs (int)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInt::INT; // ofs
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms+1, fields);
  return TypeFunc::make(domain, range);
}

const TypeFunc* OptoRuntime::multiplyToLen_Type() {
  // create input type (domain)
  int num_args      = 6;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // x
  fields[argp++] = TypeInt::INT;        // xlen
  fields[argp++] = TypePtr::NOTNULL;    // y
  fields[argp++] = TypeInt::INT;        // ylen
  fields[argp++] = TypePtr::NOTNULL;    // z
  fields[argp++] = TypeInt::INT;        // zlen
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // no result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = NULL;
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

const TypeFunc* OptoRuntime::squareToLen_Type() {
  // create input type (domain)
  int num_args      = 4;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // x
  fields[argp++] = TypeInt::INT;        // len
  fields[argp++] = TypePtr::NOTNULL;    // z
  fields[argp++] = TypeInt::INT;        // zlen
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // no result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = NULL;
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

// for mulAdd calls, 2 pointers and 3 ints, returning int
const TypeFunc* OptoRuntime::mulAdd_Type() {
  // create input type (domain)
  int num_args      = 5;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // out
  fields[argp++] = TypePtr::NOTNULL;    // in
  fields[argp++] = TypeInt::INT;        // offset
  fields[argp++] = TypeInt::INT;        // len
  fields[argp++] = TypeInt::INT;        // k
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // returning carry (int)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInt::INT;
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms+1, fields);
  return TypeFunc::make(domain, range);
}

const TypeFunc* OptoRuntime::montgomeryMultiply_Type() {
  // create input type (domain)
  int num_args      = 7;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // a
  fields[argp++] = TypePtr::NOTNULL;    // b
  fields[argp++] = TypePtr::NOTNULL;    // n
  fields[argp++] = TypeInt::INT;        // len
  fields[argp++] = TypeLong::LONG;      // inv
  fields[argp++] = Type::HALF;
  fields[argp++] = TypePtr::NOTNULL;    // result
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypePtr::NOTNULL;

  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

const TypeFunc* OptoRuntime::montgomerySquare_Type() {
  // create input type (domain)
  int num_args      = 6;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // a
  fields[argp++] = TypePtr::NOTNULL;    // n
  fields[argp++] = TypeInt::INT;        // len
  fields[argp++] = TypeLong::LONG;      // inv
  fields[argp++] = Type::HALF;
  fields[argp++] = TypePtr::NOTNULL;    // result
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypePtr::NOTNULL;

  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

// GHASH block processing
const TypeFunc* OptoRuntime::ghash_processBlocks_Type() {
    int argcnt = 4;

    const Type** fields = TypeTuple::fields(argcnt);
    int argp = TypeFunc::Parms;
    fields[argp++] = TypePtr::NOTNULL;    // state
    fields[argp++] = TypePtr::NOTNULL;    // subkeyH
    fields[argp++] = TypePtr::NOTNULL;    // data
    fields[argp++] = TypeInt::INT;        // blocks
    assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
    const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

    // result type needed
    fields = TypeTuple::fields(1);
    fields[TypeFunc::Parms+0] = NULL; // void
    const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
    return TypeFunc::make(domain, range);
}

//------------- Interpreter state access for on stack replacement
const TypeFunc* OptoRuntime::osr_end_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeRawPtr::BOTTOM; // OSR temp buf
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+1, fields);

  // create result type
  fields = TypeTuple::fields(1);
  // fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL; // locked oop
  fields[TypeFunc::Parms+0] = NULL; // void
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

//-------------- methodData update helpers

const TypeFunc* OptoRuntime::profile_receiver_type_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeAryPtr::NOTNULL;    // methodData pointer
  fields[TypeFunc::Parms+1] = TypeInstPtr::BOTTOM;    // receiver oop
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2, fields);

  // create result type
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = NULL; // void
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain,range);
}

JRT_LEAF(void, OptoRuntime::profile_receiver_type_C(DataLayout* data, oopDesc* receiver))
  if (receiver == NULL) return;
  Klass* receiver_klass = receiver->klass();

  intptr_t* mdp = ((intptr_t*)(data)) + DataLayout::header_size_in_cells();
  int empty_row = -1;           // free row, if any is encountered

  // ReceiverTypeData* vc = new ReceiverTypeData(mdp);
  for (uint row = 0; row < ReceiverTypeData::row_limit(); row++) {
    // if (vc->receiver(row) == receiver_klass)
    int receiver_off = ReceiverTypeData::receiver_cell_index(row);
    intptr_t row_recv = *(mdp + receiver_off);
    if (row_recv == (intptr_t) receiver_klass) {
      // vc->set_receiver_count(row, vc->receiver_count(row) + DataLayout::counter_increment);
      int count_off = ReceiverTypeData::receiver_count_cell_index(row);
      *(mdp + count_off) += DataLayout::counter_increment;
      return;
    } else if (row_recv == 0) {
      // else if (vc->receiver(row) == NULL)
      empty_row = (int) row;
    }
  }

  if (empty_row != -1) {
    int receiver_off = ReceiverTypeData::receiver_cell_index(empty_row);
    // vc->set_receiver(empty_row, receiver_klass);
    *(mdp + receiver_off) = (intptr_t) receiver_klass;
    // vc->set_receiver_count(empty_row, DataLayout::counter_increment);
    int count_off = ReceiverTypeData::receiver_count_cell_index(empty_row);
    *(mdp + count_off) = DataLayout::counter_increment;
  } else {
    // Receiver did not match any saved receiver and there is no empty row for it.
    // Increment total counter to indicate polymorphic case.
    intptr_t* count_p = (intptr_t*)(((uint8_t*)(data)) + in_bytes(CounterData::count_offset()));
    *count_p += DataLayout::counter_increment;
  }
JRT_END

//-------------------------------------------------------------------------------------
// register policy

bool OptoRuntime::is_callee_saved_register(MachRegisterNumbers reg) {
  assert(reg >= 0 && reg < _last_Mach_Reg, "must be a machine register");
  switch (register_save_policy[reg]) {
    case 'C': return false; //SOC
    case 'E': return true ; //SOE
    case 'N': return false; //NS
    case 'A': return false; //AS
  }
  ShouldNotReachHere();
  return false;
}

//-----------------------------------------------------------------------
// Exceptions
//

static void trace_exception(oop exception_oop, address exception_pc, const char* msg) PRODUCT_RETURN;

// The method is an entry that is always called by a C++ method not
// directly from compiled code. Compiled code will call the C++ method following.
// We can't allow async exception to be installed during  exception processing.
JRT_ENTRY_NO_ASYNC(address, OptoRuntime::handle_exception_C_helper(JavaThread* thread, nmethod* &nm))

  // Do not confuse exception_oop with pending_exception. The exception_oop
  // is only used to pass arguments into the method. Not for general
  // exception handling.  DO NOT CHANGE IT to use pending_exception, since
  // the runtime stubs checks this on exit.
  assert(thread->exception_oop() != NULL, "exception oop is found");
  address handler_address = NULL;

  Handle exception(thread, thread->exception_oop());
  address pc = thread->exception_pc();

  // Clear out the exception oop and pc since looking up an
  // exception handler can cause class loading, which might throw an
  // exception and those fields are expected to be clear during
  // normal bytecode execution.
  thread->clear_exception_oop_and_pc();

  if (TraceExceptions) {
    trace_exception(exception(), pc, "");
  }

  // for AbortVMOnException flag
  NOT_PRODUCT(Exceptions::debug_check_abort(exception));

#ifdef ASSERT
  if (!(exception->is_a(SystemDictionary::Throwable_klass()))) {
    // should throw an exception here
    ShouldNotReachHere();
  }
#endif

  // new exception handling: this method is entered only from adapters
  // exceptions from compiled java methods are handled in compiled code
  // using rethrow node

  nm = CodeCache::find_nmethod(pc);
  assert(nm != NULL, "No NMethod found");
  if (nm->is_native_method()) {
    fatal("Native method should not have path to exception handling");
  } else {
    // we are switching to old paradigm: search for exception handler in caller_frame
    // instead in exception handler of caller_frame.sender()

    if (JvmtiExport::can_post_on_exceptions()) {
      // "Full-speed catching" is not necessary here,
      // since we're notifying the VM on every catch.
      // Force deoptimization and the rest of the lookup
      // will be fine.
      deoptimize_caller_frame(thread);
    }

    // Check the stack guard pages.  If enabled, look for handler in this frame;
    // otherwise, forcibly unwind the frame.
    //
    // 4826555: use default current sp for reguard_stack instead of &nm: it's more accurate.
    bool force_unwind = !thread->reguard_stack();
    bool deopting = false;
    if (nm->is_deopt_pc(pc)) {
      deopting = true;
      RegisterMap map(thread, false);
      frame deoptee = thread->last_frame().sender(&map);
      assert(deoptee.is_deoptimized_frame(), "must be deopted");
      // Adjust the pc back to the original throwing pc
      pc = deoptee.pc();
    }

    // If we are forcing an unwind because of stack overflow then deopt is
    // irrelevant since we are throwing the frame away anyway.

    if (deopting && !force_unwind) {
      handler_address = SharedRuntime::deopt_blob()->unpack_with_exception();
    } else {

      handler_address =
        force_unwind ? NULL : nm->handler_for_exception_and_pc(exception, pc);

      if (handler_address == NULL) {
        Handle original_exception(thread, exception());
        handler_address = SharedRuntime::compute_compiled_exc_handler(nm, pc, exception, force_unwind, true);
        assert (handler_address != NULL, "must have compiled handler");
        // Update the exception cache only when the unwind was not forced
        // and there didn't happen another exception during the computation of the
        // compiled exception handler.
        if (!force_unwind && original_exception() == exception()) {
          nm->add_handler_for_exception_and_pc(exception,pc,handler_address);
        }
      } else {
        assert(handler_address == SharedRuntime::compute_compiled_exc_handler(nm, pc, exception, force_unwind, true), "Must be the same");
      }
    }

    thread->set_exception_pc(pc);
    thread->set_exception_handler_pc(handler_address);

    // Check if the exception PC is a MethodHandle call site.
    thread->set_is_method_handle_return(nm->is_method_handle_return(pc));
  }

  // Restore correct return pc.  Was saved above.
  thread->set_exception_oop(exception());
  return handler_address;

JRT_END

// We are entering here from exception_blob
// If there is a compiled exception handler in this method, we will continue there;
// otherwise we will unwind the stack and continue at the caller of top frame method
// Note we enter without the usual JRT wrapper. We will call a helper routine that
// will do the normal VM entry. We do it this way so that we can see if the nmethod
// we looked up the handler for has been deoptimized in the meantime. If it has been
// we must not use the handler and instead return the deopt blob.
address OptoRuntime::handle_exception_C(JavaThread* thread) {
//
// We are in Java not VM and in debug mode we have a NoHandleMark
//
#ifndef PRODUCT
  SharedRuntime::_find_handler_ctr++;          // find exception handler
#endif
  debug_only(NoHandleMark __hm;)
  nmethod* nm = NULL;
  address handler_address = NULL;
  {
    // Enter the VM

    ResetNoHandleMark rnhm;
    handler_address = handle_exception_C_helper(thread, nm);
  }

  // Back in java: Use no oops, DON'T safepoint

  // Now check to see if the handler we are returning is in a now
  // deoptimized frame

  if (nm != NULL) {
    RegisterMap map(thread, false);
    frame caller = thread->last_frame().sender(&map);
#ifdef ASSERT
    assert(caller.is_compiled_frame(), "must be");
#endif // ASSERT
    if (caller.is_deoptimized_frame()) {
      handler_address = SharedRuntime::deopt_blob()->unpack_with_exception();
    }
  }
  return handler_address;
}

//------------------------------rethrow----------------------------------------
// We get here after compiled code has executed a 'RethrowNode'.  The callee
// is either throwing or rethrowing an exception.  The callee-save registers
// have been restored, synchronized objects have been unlocked and the callee
// stack frame has been removed.  The return address was passed in.
// Exception oop is passed as the 1st argument.  This routine is then called
// from the stub.  On exit, we know where to jump in the caller's code.
// After this C code exits, the stub will pop his frame and end in a jump
// (instead of a return).  We enter the caller's default handler.
//
// This must be JRT_LEAF:
//     - caller will not change its state as we cannot block on exit,
//       therefore raw_exception_handler_for_return_address is all it takes
//       to handle deoptimized blobs
//
// However, there needs to be a safepoint check in the middle!  So compiled
// safepoints are completely watertight.
//
// Thus, it cannot be a leaf since it contains the No_GC_Verifier.
//
// *THIS IS NOT RECOMMENDED PROGRAMMING STYLE*
//
address OptoRuntime::rethrow_C(oopDesc* exception, JavaThread* thread, address ret_pc) {
#ifndef PRODUCT
  SharedRuntime::_rethrow_ctr++;               // count rethrows
#endif
  assert (exception != NULL, "should have thrown a NULLPointerException");
#ifdef ASSERT
  if (!(exception->is_a(SystemDictionary::Throwable_klass()))) {
    // should throw an exception here
    ShouldNotReachHere();
  }
#endif

  thread->set_vm_result(exception);
  // Frame not compiled (handles deoptimization blob)
  return SharedRuntime::raw_exception_handler_for_return_address(thread, ret_pc);
}


const TypeFunc *OptoRuntime::rethrow_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL; // Exception oop
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+1,fields);

  // create result type (range)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL; // Exception oop
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

  return TypeFunc::make(domain, range);
}


void OptoRuntime::deoptimize_caller_frame(JavaThread *thread, bool doit) {
  // Deoptimize the caller before continuing, as the compiled
  // exception handler table may not be valid.
  if (!StressCompiledExceptionHandlers && doit) {
    deoptimize_caller_frame(thread);
  }
}

void OptoRuntime::deoptimize_caller_frame(JavaThread *thread) {
  // Called from within the owner thread, so no need for safepoint
  RegisterMap reg_map(thread);
  frame stub_frame = thread->last_frame();
  assert(stub_frame.is_runtime_frame() || exception_blob()->contains(stub_frame.pc()), "sanity check");
  frame caller_frame = stub_frame.sender(&reg_map);

  // Deoptimize the caller frame.
  Deoptimization::deoptimize_frame(thread, caller_frame.id());
}


bool OptoRuntime::is_deoptimized_caller_frame(JavaThread *thread) {
  // Called from within the owner thread, so no need for safepoint
  RegisterMap reg_map(thread);
  frame stub_frame = thread->last_frame();
  assert(stub_frame.is_runtime_frame() || exception_blob()->contains(stub_frame.pc()), "sanity check");
  frame caller_frame = stub_frame.sender(&reg_map);
  return caller_frame.is_deoptimized_frame();
}


const TypeFunc *OptoRuntime::register_finalizer_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL;  // oop;          Receiver
  // // The JavaThread* is passed to each routine as the last argument
  // fields[TypeFunc::Parms+1] = TypeRawPtr::NOTNULL;  // JavaThread *; Executing thread
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+1,fields);

  // create result type (range)
  fields = TypeTuple::fields(0);

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0,fields);

  return TypeFunc::make(domain,range);
}


//-----------------------------------------------------------------------------
// Dtrace support.  entry and exit probes have the same signature
const TypeFunc *OptoRuntime::dtrace_method_entry_exit_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeRawPtr::BOTTOM; // Thread-local storage
  fields[TypeFunc::Parms+1] = TypeMetadataPtr::BOTTOM;  // Method*;    Method we are entering
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2,fields);

  // create result type (range)
  fields = TypeTuple::fields(0);

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0,fields);

  return TypeFunc::make(domain,range);
}

const TypeFunc *OptoRuntime::dtrace_object_alloc_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeRawPtr::BOTTOM; // Thread-local storage
  fields[TypeFunc::Parms+1] = TypeInstPtr::NOTNULL;  // oop;    newly allocated object

  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2,fields);

  // create result type (range)
  fields = TypeTuple::fields(0);

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0,fields);

  return TypeFunc::make(domain,range);
}


JRT_ENTRY_NO_ASYNC(void, OptoRuntime::register_finalizer(oopDesc* obj, JavaThread* thread))
  assert(obj->is_oop(), "must be a valid oop");
  assert(obj->klass()->has_finalizer(), "shouldn't be here otherwise");
  InstanceKlass::register_finalizer(instanceOop(obj), CHECK);
JRT_END

//-----------------------------------------------------------------------------

NamedCounter * volatile OptoRuntime::_named_counters = NULL;

//
// dump the collected NamedCounters.
//
void OptoRuntime::print_named_counters() {
  int total_lock_count = 0;
  int eliminated_lock_count = 0;

  NamedCounter* c = _named_counters;
  while (c) {
    if (c->tag() == NamedCounter::LockCounter || c->tag() == NamedCounter::EliminatedLockCounter) {
      int count = c->count();
      if (count > 0) {
        bool eliminated = c->tag() == NamedCounter::EliminatedLockCounter;
        if (Verbose) {
          tty->print_cr("%d %s%s", count, c->name(), eliminated ? " (eliminated)" : "");
        }
        total_lock_count += count;
        if (eliminated) {
          eliminated_lock_count += count;
        }
      }
    } else if (c->tag() == NamedCounter::BiasedLockingCounter) {
      BiasedLockingCounters* blc = ((BiasedLockingNamedCounter*)c)->counters();
      if (blc->nonzero()) {
        tty->print_cr("%s", c->name());
        blc->print_on(tty);
      }
#if INCLUDE_RTM_OPT
    } else if (c->tag() == NamedCounter::RTMLockingCounter) {
      RTMLockingCounters* rlc = ((RTMLockingNamedCounter*)c)->counters();
      if (rlc->nonzero()) {
        tty->print_cr("%s", c->name());
        rlc->print_on(tty);
      }
#endif
    }
    c = c->next();
  }
  if (total_lock_count > 0) {
    tty->print_cr("dynamic locks: %d", total_lock_count);
    if (eliminated_lock_count) {
      tty->print_cr("eliminated locks: %d (%d%%)", eliminated_lock_count,
                    (int)(eliminated_lock_count * 100.0 / total_lock_count));
    }
  }
}

//
//  Allocate a new NamedCounter.  The JVMState is used to generate the
//  name which consists of method@line for the inlining tree.
//

NamedCounter* OptoRuntime::new_named_counter(JVMState* youngest_jvms, NamedCounter::CounterTag tag) {
  int max_depth = youngest_jvms->depth();

  // Visit scopes from youngest to oldest.
  bool first = true;
  stringStream st;
  for (int depth = max_depth; depth >= 1; depth--) {
    JVMState* jvms = youngest_jvms->of_depth(depth);
    ciMethod* m = jvms->has_method() ? jvms->method() : NULL;
    if (!first) {
      st.print(" ");
    } else {
      first = false;
    }
    int bci = jvms->bci();
    if (bci < 0) bci = 0;
    st.print("%s.%s@%d", m->holder()->name()->as_utf8(), m->name()->as_utf8(), bci);
    // To print linenumbers instead of bci use: m->line_number_from_bci(bci)
  }
  NamedCounter* c;
  if (tag == NamedCounter::BiasedLockingCounter) {
    c = new BiasedLockingNamedCounter(st.as_string());
  } else if (tag == NamedCounter::RTMLockingCounter) {
    c = new RTMLockingNamedCounter(st.as_string());
  } else {
    c = new NamedCounter(st.as_string(), tag);
  }

  // atomically add the new counter to the head of the list.  We only
  // add counters so this is safe.
  NamedCounter* head;
  do {
    c->set_next(NULL);
    head = _named_counters;
    c->set_next(head);
  } while (Atomic::cmpxchg_ptr(c, &_named_counters, head) != head);
  return c;
}

//-----------------------------------------------------------------------------
// Non-product code
#ifndef PRODUCT

int trace_exception_counter = 0;
static void trace_exception(oop exception_oop, address exception_pc, const char* msg) {
  ttyLocker ttyl;
  trace_exception_counter++;
  tty->print("%d [Exception (%s): ", trace_exception_counter, msg);
  exception_oop->print_value();
  tty->print(" in ");
  CodeBlob* blob = CodeCache::find_blob(exception_pc);
  if (blob->is_nmethod()) {
    nmethod* nm = blob->as_nmethod_or_null();
    nm->method()->print_value();
  } else if (blob->is_runtime_stub()) {
    tty->print("<runtime-stub>");
  } else {
    tty->print("<unknown>");
  }
  tty->print(" at " INTPTR_FORMAT,  p2i(exception_pc));
  tty->print_cr("]");
}

#endif  // PRODUCT

