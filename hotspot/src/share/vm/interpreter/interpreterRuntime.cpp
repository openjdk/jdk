/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/codeCacheExtensions.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/disassembler.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "interpreter/linkResolver.hpp"
#include "interpreter/templateTable.hpp"
#include "logging/log.hpp"
#include "memory/oopFactory.hpp"
#include "memory/universe.inline.hpp"
#include "oops/constantPool.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/methodData.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/nativeLookup.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/compilationPolicy.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/fieldDescriptor.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/icache.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/java.hpp"
#include "runtime/jfieldIDWorkaround.hpp"
#include "runtime/osThread.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/threadCritical.hpp"
#include "utilities/events.hpp"
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif

class UnlockFlagSaver {
  private:
    JavaThread* _thread;
    bool _do_not_unlock;
  public:
    UnlockFlagSaver(JavaThread* t) {
      _thread = t;
      _do_not_unlock = t->do_not_unlock_if_synchronized();
      t->set_do_not_unlock_if_synchronized(false);
    }
    ~UnlockFlagSaver() {
      _thread->set_do_not_unlock_if_synchronized(_do_not_unlock);
    }
};

//------------------------------------------------------------------------------------------------------------------------
// State accessors

void InterpreterRuntime::set_bcp_and_mdp(address bcp, JavaThread *thread) {
  last_frame(thread).interpreter_frame_set_bcp(bcp);
  if (ProfileInterpreter) {
    // ProfileTraps uses MDOs independently of ProfileInterpreter.
    // That is why we must check both ProfileInterpreter and mdo != NULL.
    MethodData* mdo = last_frame(thread).interpreter_frame_method()->method_data();
    if (mdo != NULL) {
      NEEDS_CLEANUP;
      last_frame(thread).interpreter_frame_set_mdp(mdo->bci_to_dp(last_frame(thread).interpreter_frame_bci()));
    }
  }
}

//------------------------------------------------------------------------------------------------------------------------
// Constants


IRT_ENTRY(void, InterpreterRuntime::ldc(JavaThread* thread, bool wide))
  // access constant pool
  ConstantPool* pool = method(thread)->constants();
  int index = wide ? get_index_u2(thread, Bytecodes::_ldc_w) : get_index_u1(thread, Bytecodes::_ldc);
  constantTag tag = pool->tag_at(index);

  assert (tag.is_unresolved_klass() || tag.is_klass(), "wrong ldc call");
  Klass* klass = pool->klass_at(index, CHECK);
    oop java_class = klass->java_mirror();
    thread->set_vm_result(java_class);
IRT_END

IRT_ENTRY(void, InterpreterRuntime::resolve_ldc(JavaThread* thread, Bytecodes::Code bytecode)) {
  assert(bytecode == Bytecodes::_fast_aldc ||
         bytecode == Bytecodes::_fast_aldc_w, "wrong bc");
  ResourceMark rm(thread);
  methodHandle m (thread, method(thread));
  Bytecode_loadconstant ldc(m, bci(thread));
  oop result = ldc.resolve_constant(CHECK);
#ifdef ASSERT
  {
    // The bytecode wrappers aren't GC-safe so construct a new one
    Bytecode_loadconstant ldc2(m, bci(thread));
    oop coop = m->constants()->resolved_references()->obj_at(ldc2.cache_index());
    assert(result == coop, "expected result for assembly code");
  }
#endif
  thread->set_vm_result(result);
}
IRT_END


//------------------------------------------------------------------------------------------------------------------------
// Allocation

IRT_ENTRY(void, InterpreterRuntime::_new(JavaThread* thread, ConstantPool* pool, int index))
  Klass* k_oop = pool->klass_at(index, CHECK);
  instanceKlassHandle klass (THREAD, k_oop);

  // Make sure we are not instantiating an abstract klass
  klass->check_valid_for_instantiation(true, CHECK);

  // Make sure klass is initialized
  klass->initialize(CHECK);

  // At this point the class may not be fully initialized
  // because of recursive initialization. If it is fully
  // initialized & has_finalized is not set, we rewrite
  // it into its fast version (Note: no locking is needed
  // here since this is an atomic byte write and can be
  // done more than once).
  //
  // Note: In case of classes with has_finalized we don't
  //       rewrite since that saves us an extra check in
  //       the fast version which then would call the
  //       slow version anyway (and do a call back into
  //       Java).
  //       If we have a breakpoint, then we don't rewrite
  //       because the _breakpoint bytecode would be lost.
  oop obj = klass->allocate_instance(CHECK);
  thread->set_vm_result(obj);
IRT_END


IRT_ENTRY(void, InterpreterRuntime::newarray(JavaThread* thread, BasicType type, jint size))
  oop obj = oopFactory::new_typeArray(type, size, CHECK);
  thread->set_vm_result(obj);
IRT_END


IRT_ENTRY(void, InterpreterRuntime::anewarray(JavaThread* thread, ConstantPool* pool, int index, jint size))
  // Note: no oopHandle for pool & klass needed since they are not used
  //       anymore after new_objArray() and no GC can happen before.
  //       (This may have to change if this code changes!)
  Klass*    klass = pool->klass_at(index, CHECK);
  objArrayOop obj = oopFactory::new_objArray(klass, size, CHECK);
  thread->set_vm_result(obj);
IRT_END


IRT_ENTRY(void, InterpreterRuntime::multianewarray(JavaThread* thread, jint* first_size_address))
  // We may want to pass in more arguments - could make this slightly faster
  ConstantPool* constants = method(thread)->constants();
  int          i = get_index_u2(thread, Bytecodes::_multianewarray);
  Klass* klass = constants->klass_at(i, CHECK);
  int   nof_dims = number_of_dimensions(thread);
  assert(klass->is_klass(), "not a class");
  assert(nof_dims >= 1, "multianewarray rank must be nonzero");

  // We must create an array of jints to pass to multi_allocate.
  ResourceMark rm(thread);
  const int small_dims = 10;
  jint dim_array[small_dims];
  jint *dims = &dim_array[0];
  if (nof_dims > small_dims) {
    dims = (jint*) NEW_RESOURCE_ARRAY(jint, nof_dims);
  }
  for (int index = 0; index < nof_dims; index++) {
    // offset from first_size_address is addressed as local[index]
    int n = Interpreter::local_offset_in_bytes(index)/jintSize;
    dims[index] = first_size_address[n];
  }
  oop obj = ArrayKlass::cast(klass)->multi_allocate(nof_dims, dims, CHECK);
  thread->set_vm_result(obj);
IRT_END


IRT_ENTRY(void, InterpreterRuntime::register_finalizer(JavaThread* thread, oopDesc* obj))
  assert(obj->is_oop(), "must be a valid oop");
  assert(obj->klass()->has_finalizer(), "shouldn't be here otherwise");
  InstanceKlass::register_finalizer(instanceOop(obj), CHECK);
IRT_END


// Quicken instance-of and check-cast bytecodes
IRT_ENTRY(void, InterpreterRuntime::quicken_io_cc(JavaThread* thread))
  // Force resolving; quicken the bytecode
  int which = get_index_u2(thread, Bytecodes::_checkcast);
  ConstantPool* cpool = method(thread)->constants();
  // We'd expect to assert that we're only here to quicken bytecodes, but in a multithreaded
  // program we might have seen an unquick'd bytecode in the interpreter but have another
  // thread quicken the bytecode before we get here.
  // assert( cpool->tag_at(which).is_unresolved_klass(), "should only come here to quicken bytecodes" );
  Klass* klass = cpool->klass_at(which, CHECK);
  thread->set_vm_result_2(klass);
IRT_END


//------------------------------------------------------------------------------------------------------------------------
// Exceptions

void InterpreterRuntime::note_trap_inner(JavaThread* thread, int reason,
                                         methodHandle trap_method, int trap_bci, TRAPS) {
  if (trap_method.not_null()) {
    MethodData* trap_mdo = trap_method->method_data();
    if (trap_mdo == NULL) {
      Method::build_interpreter_method_data(trap_method, THREAD);
      if (HAS_PENDING_EXCEPTION) {
        assert((PENDING_EXCEPTION->is_a(SystemDictionary::OutOfMemoryError_klass())),
               "we expect only an OOM error here");
        CLEAR_PENDING_EXCEPTION;
      }
      trap_mdo = trap_method->method_data();
      // and fall through...
    }
    if (trap_mdo != NULL) {
      // Update per-method count of trap events.  The interpreter
      // is updating the MDO to simulate the effect of compiler traps.
      Deoptimization::update_method_data_from_interpreter(trap_mdo, trap_bci, reason);
    }
  }
}

// Assume the compiler is (or will be) interested in this event.
// If necessary, create an MDO to hold the information, and record it.
void InterpreterRuntime::note_trap(JavaThread* thread, int reason, TRAPS) {
  assert(ProfileTraps, "call me only if profiling");
  methodHandle trap_method(thread, method(thread));
  int trap_bci = trap_method->bci_from(bcp(thread));
  note_trap_inner(thread, reason, trap_method, trap_bci, THREAD);
}

#ifdef CC_INTERP
// As legacy note_trap, but we have more arguments.
IRT_ENTRY(void, InterpreterRuntime::note_trap(JavaThread* thread, int reason, Method *method, int trap_bci))
  methodHandle trap_method(method);
  note_trap_inner(thread, reason, trap_method, trap_bci, THREAD);
IRT_END

// Class Deoptimization is not visible in BytecodeInterpreter, so we need a wrapper
// for each exception.
void InterpreterRuntime::note_nullCheck_trap(JavaThread* thread, Method *method, int trap_bci)
  { if (ProfileTraps) note_trap(thread, Deoptimization::Reason_null_check, method, trap_bci); }
void InterpreterRuntime::note_div0Check_trap(JavaThread* thread, Method *method, int trap_bci)
  { if (ProfileTraps) note_trap(thread, Deoptimization::Reason_div0_check, method, trap_bci); }
void InterpreterRuntime::note_rangeCheck_trap(JavaThread* thread, Method *method, int trap_bci)
  { if (ProfileTraps) note_trap(thread, Deoptimization::Reason_range_check, method, trap_bci); }
void InterpreterRuntime::note_classCheck_trap(JavaThread* thread, Method *method, int trap_bci)
  { if (ProfileTraps) note_trap(thread, Deoptimization::Reason_class_check, method, trap_bci); }
void InterpreterRuntime::note_arrayCheck_trap(JavaThread* thread, Method *method, int trap_bci)
  { if (ProfileTraps) note_trap(thread, Deoptimization::Reason_array_check, method, trap_bci); }
#endif // CC_INTERP


static Handle get_preinitialized_exception(Klass* k, TRAPS) {
  // get klass
  InstanceKlass* klass = InstanceKlass::cast(k);
  assert(klass->is_initialized(),
         "this klass should have been initialized during VM initialization");
  // create instance - do not call constructor since we may have no
  // (java) stack space left (should assert constructor is empty)
  Handle exception;
  oop exception_oop = klass->allocate_instance(CHECK_(exception));
  exception = Handle(THREAD, exception_oop);
  if (StackTraceInThrowable) {
    java_lang_Throwable::fill_in_stack_trace(exception);
  }
  return exception;
}

// Special handling for stack overflow: since we don't have any (java) stack
// space left we use the pre-allocated & pre-initialized StackOverflowError
// klass to create an stack overflow error instance.  We do not call its
// constructor for the same reason (it is empty, anyway).
IRT_ENTRY(void, InterpreterRuntime::throw_StackOverflowError(JavaThread* thread))
  Handle exception = get_preinitialized_exception(
                                 SystemDictionary::StackOverflowError_klass(),
                                 CHECK);
  // Increment counter for hs_err file reporting
  Atomic::inc(&Exceptions::_stack_overflow_errors);
  THROW_HANDLE(exception);
IRT_END

IRT_ENTRY(address, InterpreterRuntime::check_ReservedStackAccess_annotated_methods(JavaThread* thread))
  frame fr = thread->last_frame();
  assert(fr.is_java_frame(), "Must be a Java frame");
  frame activation = SharedRuntime::look_for_reserved_stack_annotated_method(thread, fr);
  if (activation.sp() != NULL) {
    thread->disable_stack_reserved_zone();
    thread->set_reserved_stack_activation((address)activation.unextended_sp());
  }
  return (address)activation.sp();
IRT_END

 IRT_ENTRY(void, InterpreterRuntime::throw_delayed_StackOverflowError(JavaThread* thread))
  Handle exception = get_preinitialized_exception(
                                 SystemDictionary::StackOverflowError_klass(),
                                 CHECK);
  java_lang_Throwable::set_message(exception(),
          Universe::delayed_stack_overflow_error_message());
  // Increment counter for hs_err file reporting
  Atomic::inc(&Exceptions::_stack_overflow_errors);
  THROW_HANDLE(exception);
IRT_END

IRT_ENTRY(void, InterpreterRuntime::create_exception(JavaThread* thread, char* name, char* message))
  // lookup exception klass
  TempNewSymbol s = SymbolTable::new_symbol(name, CHECK);
  if (ProfileTraps) {
    if (s == vmSymbols::java_lang_ArithmeticException()) {
      note_trap(thread, Deoptimization::Reason_div0_check, CHECK);
    } else if (s == vmSymbols::java_lang_NullPointerException()) {
      note_trap(thread, Deoptimization::Reason_null_check, CHECK);
    }
  }
  // create exception
  Handle exception = Exceptions::new_exception(thread, s, message);
  thread->set_vm_result(exception());
IRT_END


IRT_ENTRY(void, InterpreterRuntime::create_klass_exception(JavaThread* thread, char* name, oopDesc* obj))
  ResourceMark rm(thread);
  const char* klass_name = obj->klass()->external_name();
  // lookup exception klass
  TempNewSymbol s = SymbolTable::new_symbol(name, CHECK);
  if (ProfileTraps) {
    note_trap(thread, Deoptimization::Reason_class_check, CHECK);
  }
  // create exception, with klass name as detail message
  Handle exception = Exceptions::new_exception(thread, s, klass_name);
  thread->set_vm_result(exception());
IRT_END


IRT_ENTRY(void, InterpreterRuntime::throw_ArrayIndexOutOfBoundsException(JavaThread* thread, char* name, jint index))
  char message[jintAsStringSize];
  // lookup exception klass
  TempNewSymbol s = SymbolTable::new_symbol(name, CHECK);
  if (ProfileTraps) {
    note_trap(thread, Deoptimization::Reason_range_check, CHECK);
  }
  // create exception
  sprintf(message, "%d", index);
  THROW_MSG(s, message);
IRT_END

IRT_ENTRY(void, InterpreterRuntime::throw_ClassCastException(
  JavaThread* thread, oopDesc* obj))

  ResourceMark rm(thread);
  char* message = SharedRuntime::generate_class_cast_message(
    thread, obj->klass());

  if (ProfileTraps) {
    note_trap(thread, Deoptimization::Reason_class_check, CHECK);
  }

  // create exception
  THROW_MSG(vmSymbols::java_lang_ClassCastException(), message);
IRT_END

// exception_handler_for_exception(...) returns the continuation address,
// the exception oop (via TLS) and sets the bci/bcp for the continuation.
// The exception oop is returned to make sure it is preserved over GC (it
// is only on the stack if the exception was thrown explicitly via athrow).
// During this operation, the expression stack contains the values for the
// bci where the exception happened. If the exception was propagated back
// from a call, the expression stack contains the values for the bci at the
// invoke w/o arguments (i.e., as if one were inside the call).
IRT_ENTRY(address, InterpreterRuntime::exception_handler_for_exception(JavaThread* thread, oopDesc* exception))

  Handle             h_exception(thread, exception);
  methodHandle       h_method   (thread, method(thread));
  constantPoolHandle h_constants(thread, h_method->constants());
  bool               should_repeat;
  int                handler_bci;
  int                current_bci = bci(thread);

  if (thread->frames_to_pop_failed_realloc() > 0) {
    // Allocation of scalar replaced object used in this frame
    // failed. Unconditionally pop the frame.
    thread->dec_frames_to_pop_failed_realloc();
    thread->set_vm_result(h_exception());
    // If the method is synchronized we already unlocked the monitor
    // during deoptimization so the interpreter needs to skip it when
    // the frame is popped.
    thread->set_do_not_unlock_if_synchronized(true);
#ifdef CC_INTERP
    return (address) -1;
#else
    return Interpreter::remove_activation_entry();
#endif
  }

  // Need to do this check first since when _do_not_unlock_if_synchronized
  // is set, we don't want to trigger any classloading which may make calls
  // into java, or surprisingly find a matching exception handler for bci 0
  // since at this moment the method hasn't been "officially" entered yet.
  if (thread->do_not_unlock_if_synchronized()) {
    ResourceMark rm;
    assert(current_bci == 0,  "bci isn't zero for do_not_unlock_if_synchronized");
    thread->set_vm_result(exception);
#ifdef CC_INTERP
    return (address) -1;
#else
    return Interpreter::remove_activation_entry();
#endif
  }

  do {
    should_repeat = false;

    // assertions
#ifdef ASSERT
    assert(h_exception.not_null(), "NULL exceptions should be handled by athrow");
    assert(h_exception->is_oop(), "just checking");
    // Check that exception is a subclass of Throwable, otherwise we have a VerifyError
    if (!(h_exception->is_a(SystemDictionary::Throwable_klass()))) {
      if (ExitVMOnVerifyError) vm_exit(-1);
      ShouldNotReachHere();
    }
#endif

    // tracing
    if (log_is_enabled(Info, exceptions)) {
      ResourceMark rm(thread);
      stringStream tempst;
      tempst.print("interpreter method <%s>\n"
                   " at bci %d for thread " INTPTR_FORMAT,
                   h_method->print_value_string(), current_bci, p2i(thread));
      Exceptions::log_exception(h_exception, tempst);
    }
// Don't go paging in something which won't be used.
//     else if (extable->length() == 0) {
//       // disabled for now - interpreter is not using shortcut yet
//       // (shortcut is not to call runtime if we have no exception handlers)
//       // warning("performance bug: should not call runtime if method has no exception handlers");
//     }
    // for AbortVMOnException flag
    Exceptions::debug_check_abort(h_exception);

    // exception handler lookup
    KlassHandle h_klass(THREAD, h_exception->klass());
    handler_bci = Method::fast_exception_handler_bci_for(h_method, h_klass, current_bci, THREAD);
    if (HAS_PENDING_EXCEPTION) {
      // We threw an exception while trying to find the exception handler.
      // Transfer the new exception to the exception handle which will
      // be set into thread local storage, and do another lookup for an
      // exception handler for this exception, this time starting at the
      // BCI of the exception handler which caused the exception to be
      // thrown (bug 4307310).
      h_exception = Handle(THREAD, PENDING_EXCEPTION);
      CLEAR_PENDING_EXCEPTION;
      if (handler_bci >= 0) {
        current_bci = handler_bci;
        should_repeat = true;
      }
    }
  } while (should_repeat == true);

#if INCLUDE_JVMCI
  if (EnableJVMCI && h_method->method_data() != NULL) {
    ResourceMark rm(thread);
    ProfileData* pdata = h_method->method_data()->allocate_bci_to_data(current_bci, NULL);
    if (pdata != NULL && pdata->is_BitData()) {
      BitData* bit_data = (BitData*) pdata;
      bit_data->set_exception_seen();
    }
  }
#endif

  // notify JVMTI of an exception throw; JVMTI will detect if this is a first
  // time throw or a stack unwinding throw and accordingly notify the debugger
  if (JvmtiExport::can_post_on_exceptions()) {
    JvmtiExport::post_exception_throw(thread, h_method(), bcp(thread), h_exception());
  }

#ifdef CC_INTERP
  address continuation = (address)(intptr_t) handler_bci;
#else
  address continuation = NULL;
#endif
  address handler_pc = NULL;
  if (handler_bci < 0 || !thread->reguard_stack((address) &continuation)) {
    // Forward exception to callee (leaving bci/bcp untouched) because (a) no
    // handler in this method, or (b) after a stack overflow there is not yet
    // enough stack space available to reprotect the stack.
#ifndef CC_INTERP
    continuation = Interpreter::remove_activation_entry();
#endif
#if COMPILER2_OR_JVMCI
    // Count this for compilation purposes
    h_method->interpreter_throwout_increment(THREAD);
#endif
  } else {
    // handler in this method => change bci/bcp to handler bci/bcp and continue there
    handler_pc = h_method->code_base() + handler_bci;
#ifndef CC_INTERP
    set_bcp_and_mdp(handler_pc, thread);
    continuation = Interpreter::dispatch_table(vtos)[*handler_pc];
#endif
  }
  // notify debugger of an exception catch
  // (this is good for exceptions caught in native methods as well)
  if (JvmtiExport::can_post_on_exceptions()) {
    JvmtiExport::notice_unwind_due_to_exception(thread, h_method(), handler_pc, h_exception(), (handler_pc != NULL));
  }

  thread->set_vm_result(h_exception());
  return continuation;
IRT_END


IRT_ENTRY(void, InterpreterRuntime::throw_pending_exception(JavaThread* thread))
  assert(thread->has_pending_exception(), "must only ne called if there's an exception pending");
  // nothing to do - eventually we should remove this code entirely (see comments @ call sites)
IRT_END


IRT_ENTRY(void, InterpreterRuntime::throw_AbstractMethodError(JavaThread* thread))
  THROW(vmSymbols::java_lang_AbstractMethodError());
IRT_END


IRT_ENTRY(void, InterpreterRuntime::throw_IncompatibleClassChangeError(JavaThread* thread))
  THROW(vmSymbols::java_lang_IncompatibleClassChangeError());
IRT_END


//------------------------------------------------------------------------------------------------------------------------
// Fields
//

void InterpreterRuntime::resolve_get_put(JavaThread* thread, Bytecodes::Code bytecode) {
  Thread* THREAD = thread;
  // resolve field
  fieldDescriptor info;
  constantPoolHandle pool(thread, method(thread)->constants());
  bool is_put    = (bytecode == Bytecodes::_putfield  || bytecode == Bytecodes::_nofast_putfield ||
                    bytecode == Bytecodes::_putstatic);
  bool is_static = (bytecode == Bytecodes::_getstatic || bytecode == Bytecodes::_putstatic);

  {
    JvmtiHideSingleStepping jhss(thread);
    LinkResolver::resolve_field_access(info, pool, get_index_u2_cpcache(thread, bytecode),
                                       bytecode, CHECK);
  } // end JvmtiHideSingleStepping

  // check if link resolution caused cpCache to be updated
  ConstantPoolCacheEntry* cp_cache_entry = cache_entry(thread);
  if (cp_cache_entry->is_resolved(bytecode)) return;

  // compute auxiliary field attributes
  TosState state  = as_TosState(info.field_type());

  // We need to delay resolving put instructions on final fields
  // until we actually invoke one. This is required so we throw
  // exceptions at the correct place. If we do not resolve completely
  // in the current pass, leaving the put_code set to zero will
  // cause the next put instruction to reresolve.
  Bytecodes::Code put_code = (Bytecodes::Code)0;

  // We also need to delay resolving getstatic instructions until the
  // class is intitialized.  This is required so that access to the static
  // field will call the initialization function every time until the class
  // is completely initialized ala. in 2.17.5 in JVM Specification.
  InstanceKlass* klass = InstanceKlass::cast(info.field_holder());
  bool uninitialized_static = ((bytecode == Bytecodes::_getstatic || bytecode == Bytecodes::_putstatic) &&
                               !klass->is_initialized());
  Bytecodes::Code get_code = (Bytecodes::Code)0;

  if (!uninitialized_static) {
    get_code = ((is_static) ? Bytecodes::_getstatic : Bytecodes::_getfield);
    if (is_put || !info.access_flags().is_final()) {
      put_code = ((is_static) ? Bytecodes::_putstatic : Bytecodes::_putfield);
    }
  }

  cp_cache_entry->set_field(
    get_code,
    put_code,
    info.field_holder(),
    info.index(),
    info.offset(),
    state,
    info.access_flags().is_final(),
    info.access_flags().is_volatile(),
    pool->pool_holder()
  );
}


//------------------------------------------------------------------------------------------------------------------------
// Synchronization
//
// The interpreter's synchronization code is factored out so that it can
// be shared by method invocation and synchronized blocks.
//%note synchronization_3

//%note monitor_1
IRT_ENTRY_NO_ASYNC(void, InterpreterRuntime::monitorenter(JavaThread* thread, BasicObjectLock* elem))
#ifdef ASSERT
  thread->last_frame().interpreter_frame_verify_monitor(elem);
#endif
  if (PrintBiasedLockingStatistics) {
    Atomic::inc(BiasedLocking::slow_path_entry_count_addr());
  }
  Handle h_obj(thread, elem->obj());
  assert(Universe::heap()->is_in_reserved_or_null(h_obj()),
         "must be NULL or an object");
  if (UseBiasedLocking) {
    // Retry fast entry if bias is revoked to avoid unnecessary inflation
    ObjectSynchronizer::fast_enter(h_obj, elem->lock(), true, CHECK);
  } else {
    ObjectSynchronizer::slow_enter(h_obj, elem->lock(), CHECK);
  }
  assert(Universe::heap()->is_in_reserved_or_null(elem->obj()),
         "must be NULL or an object");
#ifdef ASSERT
  thread->last_frame().interpreter_frame_verify_monitor(elem);
#endif
IRT_END


//%note monitor_1
IRT_ENTRY_NO_ASYNC(void, InterpreterRuntime::monitorexit(JavaThread* thread, BasicObjectLock* elem))
#ifdef ASSERT
  thread->last_frame().interpreter_frame_verify_monitor(elem);
#endif
  Handle h_obj(thread, elem->obj());
  assert(Universe::heap()->is_in_reserved_or_null(h_obj()),
         "must be NULL or an object");
  if (elem == NULL || h_obj()->is_unlocked()) {
    THROW(vmSymbols::java_lang_IllegalMonitorStateException());
  }
  ObjectSynchronizer::slow_exit(h_obj(), elem->lock(), thread);
  // Free entry. This must be done here, since a pending exception might be installed on
  // exit. If it is not cleared, the exception handling code will try to unlock the monitor again.
  elem->set_obj(NULL);
#ifdef ASSERT
  thread->last_frame().interpreter_frame_verify_monitor(elem);
#endif
IRT_END


IRT_ENTRY(void, InterpreterRuntime::throw_illegal_monitor_state_exception(JavaThread* thread))
  THROW(vmSymbols::java_lang_IllegalMonitorStateException());
IRT_END


IRT_ENTRY(void, InterpreterRuntime::new_illegal_monitor_state_exception(JavaThread* thread))
  // Returns an illegal exception to install into the current thread. The
  // pending_exception flag is cleared so normal exception handling does not
  // trigger. Any current installed exception will be overwritten. This
  // method will be called during an exception unwind.

  assert(!HAS_PENDING_EXCEPTION, "no pending exception");
  Handle exception(thread, thread->vm_result());
  assert(exception() != NULL, "vm result should be set");
  thread->set_vm_result(NULL); // clear vm result before continuing (may cause memory leaks and assert failures)
  if (!exception->is_a(SystemDictionary::ThreadDeath_klass())) {
    exception = get_preinitialized_exception(
                       SystemDictionary::IllegalMonitorStateException_klass(),
                       CATCH);
  }
  thread->set_vm_result(exception());
IRT_END


//------------------------------------------------------------------------------------------------------------------------
// Invokes

IRT_ENTRY(Bytecodes::Code, InterpreterRuntime::get_original_bytecode_at(JavaThread* thread, Method* method, address bcp))
  return method->orig_bytecode_at(method->bci_from(bcp));
IRT_END

IRT_ENTRY(void, InterpreterRuntime::set_original_bytecode_at(JavaThread* thread, Method* method, address bcp, Bytecodes::Code new_code))
  method->set_orig_bytecode_at(method->bci_from(bcp), new_code);
IRT_END

IRT_ENTRY(void, InterpreterRuntime::_breakpoint(JavaThread* thread, Method* method, address bcp))
  JvmtiExport::post_raw_breakpoint(thread, method, bcp);
IRT_END

void InterpreterRuntime::resolve_invoke(JavaThread* thread, Bytecodes::Code bytecode) {
  Thread* THREAD = thread;
  // extract receiver from the outgoing argument list if necessary
  Handle receiver(thread, NULL);
  if (bytecode == Bytecodes::_invokevirtual || bytecode == Bytecodes::_invokeinterface) {
    ResourceMark rm(thread);
    methodHandle m (thread, method(thread));
    Bytecode_invoke call(m, bci(thread));
    Symbol* signature = call.signature();
    receiver = Handle(thread,
                  thread->last_frame().interpreter_callee_receiver(signature));
    assert(Universe::heap()->is_in_reserved_or_null(receiver()),
           "sanity check");
    assert(receiver.is_null() ||
           !Universe::heap()->is_in_reserved(receiver->klass()),
           "sanity check");
  }

  // resolve method
  CallInfo info;
  constantPoolHandle pool(thread, method(thread)->constants());

  {
    JvmtiHideSingleStepping jhss(thread);
    LinkResolver::resolve_invoke(info, receiver, pool,
                                 get_index_u2_cpcache(thread, bytecode), bytecode,
                                 CHECK);
    if (JvmtiExport::can_hotswap_or_post_breakpoint()) {
      int retry_count = 0;
      while (info.resolved_method()->is_old()) {
        // It is very unlikely that method is redefined more than 100 times
        // in the middle of resolve. If it is looping here more than 100 times
        // means then there could be a bug here.
        guarantee((retry_count++ < 100),
                  "Could not resolve to latest version of redefined method");
        // method is redefined in the middle of resolve so re-try.
        LinkResolver::resolve_invoke(info, receiver, pool,
                                     get_index_u2_cpcache(thread, bytecode), bytecode,
                                     CHECK);
      }
    }
  } // end JvmtiHideSingleStepping

  // check if link resolution caused cpCache to be updated
  ConstantPoolCacheEntry* cp_cache_entry = cache_entry(thread);
  if (cp_cache_entry->is_resolved(bytecode)) return;

#ifdef ASSERT
  if (bytecode == Bytecodes::_invokeinterface) {
    if (info.resolved_method()->method_holder() ==
                                            SystemDictionary::Object_klass()) {
      // NOTE: THIS IS A FIX FOR A CORNER CASE in the JVM spec
      // (see also CallInfo::set_interface for details)
      assert(info.call_kind() == CallInfo::vtable_call ||
             info.call_kind() == CallInfo::direct_call, "");
      methodHandle rm = info.resolved_method();
      assert(rm->is_final() || info.has_vtable_index(),
             "should have been set already");
    } else if (!info.resolved_method()->has_itable_index()) {
      // Resolved something like CharSequence.toString.  Use vtable not itable.
      assert(info.call_kind() != CallInfo::itable_call, "");
    } else {
      // Setup itable entry
      assert(info.call_kind() == CallInfo::itable_call, "");
      int index = info.resolved_method()->itable_index();
      assert(info.itable_index() == index, "");
    }
  } else {
    assert(info.call_kind() == CallInfo::direct_call ||
           info.call_kind() == CallInfo::vtable_call, "");
  }
#endif
  switch (info.call_kind()) {
  case CallInfo::direct_call:
    cp_cache_entry->set_direct_call(
      bytecode,
      info.resolved_method());
    break;
  case CallInfo::vtable_call:
    cp_cache_entry->set_vtable_call(
      bytecode,
      info.resolved_method(),
      info.vtable_index());
    break;
  case CallInfo::itable_call:
    cp_cache_entry->set_itable_call(
      bytecode,
      info.resolved_method(),
      info.itable_index());
    break;
  default:  ShouldNotReachHere();
  }
}


// First time execution:  Resolve symbols, create a permanent MethodType object.
void InterpreterRuntime::resolve_invokehandle(JavaThread* thread) {
  Thread* THREAD = thread;
  const Bytecodes::Code bytecode = Bytecodes::_invokehandle;

  // resolve method
  CallInfo info;
  constantPoolHandle pool(thread, method(thread)->constants());
  {
    JvmtiHideSingleStepping jhss(thread);
    LinkResolver::resolve_invoke(info, Handle(), pool,
                                 get_index_u2_cpcache(thread, bytecode), bytecode,
                                 CHECK);
  } // end JvmtiHideSingleStepping

  ConstantPoolCacheEntry* cp_cache_entry = cache_entry(thread);
  cp_cache_entry->set_method_handle(pool, info);
}

// First time execution:  Resolve symbols, create a permanent CallSite object.
void InterpreterRuntime::resolve_invokedynamic(JavaThread* thread) {
  Thread* THREAD = thread;
  const Bytecodes::Code bytecode = Bytecodes::_invokedynamic;

  //TO DO: consider passing BCI to Java.
  //  int caller_bci = method(thread)->bci_from(bcp(thread));

  // resolve method
  CallInfo info;
  constantPoolHandle pool(thread, method(thread)->constants());
  int index = get_index_u4(thread, bytecode);
  {
    JvmtiHideSingleStepping jhss(thread);
    LinkResolver::resolve_invoke(info, Handle(), pool,
                                 index, bytecode, CHECK);
  } // end JvmtiHideSingleStepping

  ConstantPoolCacheEntry* cp_cache_entry = pool->invokedynamic_cp_cache_entry_at(index);
  cp_cache_entry->set_dynamic_call(pool, info);
}

// This function is the interface to the assembly code. It returns the resolved
// cpCache entry.  This doesn't safepoint, but the helper routines safepoint.
// This function will check for redefinition!
IRT_ENTRY(void, InterpreterRuntime::resolve_from_cache(JavaThread* thread, Bytecodes::Code bytecode)) {
  switch (bytecode) {
  case Bytecodes::_getstatic:
  case Bytecodes::_putstatic:
  case Bytecodes::_getfield:
  case Bytecodes::_putfield:
    resolve_get_put(thread, bytecode);
    break;
  case Bytecodes::_invokevirtual:
  case Bytecodes::_invokespecial:
  case Bytecodes::_invokestatic:
  case Bytecodes::_invokeinterface:
    resolve_invoke(thread, bytecode);
    break;
  case Bytecodes::_invokehandle:
    resolve_invokehandle(thread);
    break;
  case Bytecodes::_invokedynamic:
    resolve_invokedynamic(thread);
    break;
  default:
    fatal("unexpected bytecode: %s", Bytecodes::name(bytecode));
    break;
  }
}
IRT_END

//------------------------------------------------------------------------------------------------------------------------
// Miscellaneous


nmethod* InterpreterRuntime::frequency_counter_overflow(JavaThread* thread, address branch_bcp) {
  nmethod* nm = frequency_counter_overflow_inner(thread, branch_bcp);
  assert(branch_bcp != NULL || nm == NULL, "always returns null for non OSR requests");
  if (branch_bcp != NULL && nm != NULL) {
    // This was a successful request for an OSR nmethod.  Because
    // frequency_counter_overflow_inner ends with a safepoint check,
    // nm could have been unloaded so look it up again.  It's unsafe
    // to examine nm directly since it might have been freed and used
    // for something else.
    frame fr = thread->last_frame();
    Method* method =  fr.interpreter_frame_method();
    int bci = method->bci_from(fr.interpreter_frame_bcp());
    nm = method->lookup_osr_nmethod_for(bci, CompLevel_none, false);
  }
#ifndef PRODUCT
  if (TraceOnStackReplacement) {
    if (nm != NULL) {
      tty->print("OSR entry @ pc: " INTPTR_FORMAT ": ", p2i(nm->osr_entry()));
      nm->print();
    }
  }
#endif
  return nm;
}

IRT_ENTRY(nmethod*,
          InterpreterRuntime::frequency_counter_overflow_inner(JavaThread* thread, address branch_bcp))
  // use UnlockFlagSaver to clear and restore the _do_not_unlock_if_synchronized
  // flag, in case this method triggers classloading which will call into Java.
  UnlockFlagSaver fs(thread);

  frame fr = thread->last_frame();
  assert(fr.is_interpreted_frame(), "must come from interpreter");
  methodHandle method(thread, fr.interpreter_frame_method());
  const int branch_bci = branch_bcp != NULL ? method->bci_from(branch_bcp) : InvocationEntryBci;
  const int bci = branch_bcp != NULL ? method->bci_from(fr.interpreter_frame_bcp()) : InvocationEntryBci;

  assert(!HAS_PENDING_EXCEPTION, "Should not have any exceptions pending");
  nmethod* osr_nm = CompilationPolicy::policy()->event(method, method, branch_bci, bci, CompLevel_none, NULL, thread);
  assert(!HAS_PENDING_EXCEPTION, "Event handler should not throw any exceptions");

  if (osr_nm != NULL) {
    // We may need to do on-stack replacement which requires that no
    // monitors in the activation are biased because their
    // BasicObjectLocks will need to migrate during OSR. Force
    // unbiasing of all monitors in the activation now (even though
    // the OSR nmethod might be invalidated) because we don't have a
    // safepoint opportunity later once the migration begins.
    if (UseBiasedLocking) {
      ResourceMark rm;
      GrowableArray<Handle>* objects_to_revoke = new GrowableArray<Handle>();
      for( BasicObjectLock *kptr = fr.interpreter_frame_monitor_end();
           kptr < fr.interpreter_frame_monitor_begin();
           kptr = fr.next_monitor_in_interpreter_frame(kptr) ) {
        if( kptr->obj() != NULL ) {
          objects_to_revoke->append(Handle(THREAD, kptr->obj()));
        }
      }
      BiasedLocking::revoke(objects_to_revoke);
    }
  }
  return osr_nm;
IRT_END

IRT_LEAF(jint, InterpreterRuntime::bcp_to_di(Method* method, address cur_bcp))
  assert(ProfileInterpreter, "must be profiling interpreter");
  int bci = method->bci_from(cur_bcp);
  MethodData* mdo = method->method_data();
  if (mdo == NULL)  return 0;
  return mdo->bci_to_di(bci);
IRT_END

IRT_ENTRY(void, InterpreterRuntime::profile_method(JavaThread* thread))
  // use UnlockFlagSaver to clear and restore the _do_not_unlock_if_synchronized
  // flag, in case this method triggers classloading which will call into Java.
  UnlockFlagSaver fs(thread);

  assert(ProfileInterpreter, "must be profiling interpreter");
  frame fr = thread->last_frame();
  assert(fr.is_interpreted_frame(), "must come from interpreter");
  methodHandle method(thread, fr.interpreter_frame_method());
  Method::build_interpreter_method_data(method, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    assert((PENDING_EXCEPTION->is_a(SystemDictionary::OutOfMemoryError_klass())), "we expect only an OOM error here");
    CLEAR_PENDING_EXCEPTION;
    // and fall through...
  }
IRT_END


#ifdef ASSERT
IRT_LEAF(void, InterpreterRuntime::verify_mdp(Method* method, address bcp, address mdp))
  assert(ProfileInterpreter, "must be profiling interpreter");

  MethodData* mdo = method->method_data();
  assert(mdo != NULL, "must not be null");

  int bci = method->bci_from(bcp);

  address mdp2 = mdo->bci_to_dp(bci);
  if (mdp != mdp2) {
    ResourceMark rm;
    ResetNoHandleMark rnm; // In a LEAF entry.
    HandleMark hm;
    tty->print_cr("FAILED verify : actual mdp %p   expected mdp %p @ bci %d", mdp, mdp2, bci);
    int current_di = mdo->dp_to_di(mdp);
    int expected_di  = mdo->dp_to_di(mdp2);
    tty->print_cr("  actual di %d   expected di %d", current_di, expected_di);
    int expected_approx_bci = mdo->data_at(expected_di)->bci();
    int approx_bci = -1;
    if (current_di >= 0) {
      approx_bci = mdo->data_at(current_di)->bci();
    }
    tty->print_cr("  actual bci is %d  expected bci %d", approx_bci, expected_approx_bci);
    mdo->print_on(tty);
    method->print_codes();
  }
  assert(mdp == mdp2, "wrong mdp");
IRT_END
#endif // ASSERT

IRT_ENTRY(void, InterpreterRuntime::update_mdp_for_ret(JavaThread* thread, int return_bci))
  assert(ProfileInterpreter, "must be profiling interpreter");
  ResourceMark rm(thread);
  HandleMark hm(thread);
  frame fr = thread->last_frame();
  assert(fr.is_interpreted_frame(), "must come from interpreter");
  MethodData* h_mdo = fr.interpreter_frame_method()->method_data();

  // Grab a lock to ensure atomic access to setting the return bci and
  // the displacement.  This can block and GC, invalidating all naked oops.
  MutexLocker ml(RetData_lock);

  // ProfileData is essentially a wrapper around a derived oop, so we
  // need to take the lock before making any ProfileData structures.
  ProfileData* data = h_mdo->data_at(h_mdo->dp_to_di(fr.interpreter_frame_mdp()));
  RetData* rdata = data->as_RetData();
  address new_mdp = rdata->fixup_ret(return_bci, h_mdo);
  fr.interpreter_frame_set_mdp(new_mdp);
IRT_END

IRT_ENTRY(MethodCounters*, InterpreterRuntime::build_method_counters(JavaThread* thread, Method* m))
  MethodCounters* mcs = Method::build_method_counters(m, thread);
  if (HAS_PENDING_EXCEPTION) {
    assert((PENDING_EXCEPTION->is_a(SystemDictionary::OutOfMemoryError_klass())), "we expect only an OOM error here");
    CLEAR_PENDING_EXCEPTION;
  }
  return mcs;
IRT_END


IRT_ENTRY(void, InterpreterRuntime::at_safepoint(JavaThread* thread))
  // We used to need an explict preserve_arguments here for invoke bytecodes. However,
  // stack traversal automatically takes care of preserving arguments for invoke, so
  // this is no longer needed.

  // IRT_END does an implicit safepoint check, hence we are guaranteed to block
  // if this is called during a safepoint

  if (JvmtiExport::should_post_single_step()) {
    // We are called during regular safepoints and when the VM is
    // single stepping. If any thread is marked for single stepping,
    // then we may have JVMTI work to do.
    JvmtiExport::at_single_stepping_point(thread, method(thread), bcp(thread));
  }
IRT_END

IRT_ENTRY(void, InterpreterRuntime::post_field_access(JavaThread *thread, oopDesc* obj,
ConstantPoolCacheEntry *cp_entry))

  // check the access_flags for the field in the klass

  InstanceKlass* ik = InstanceKlass::cast(cp_entry->f1_as_klass());
  int index = cp_entry->field_index();
  if ((ik->field_access_flags(index) & JVM_ACC_FIELD_ACCESS_WATCHED) == 0) return;

  bool is_static = (obj == NULL);
  HandleMark hm(thread);

  Handle h_obj;
  if (!is_static) {
    // non-static field accessors have an object, but we need a handle
    h_obj = Handle(thread, obj);
  }
  instanceKlassHandle h_cp_entry_f1(thread, (Klass*)cp_entry->f1_as_klass());
  jfieldID fid = jfieldIDWorkaround::to_jfieldID(h_cp_entry_f1, cp_entry->f2_as_index(), is_static);
  JvmtiExport::post_field_access(thread, method(thread), bcp(thread), h_cp_entry_f1, h_obj, fid);
IRT_END

IRT_ENTRY(void, InterpreterRuntime::post_field_modification(JavaThread *thread,
  oopDesc* obj, ConstantPoolCacheEntry *cp_entry, jvalue *value))

  Klass* k = (Klass*)cp_entry->f1_as_klass();

  // check the access_flags for the field in the klass
  InstanceKlass* ik = InstanceKlass::cast(k);
  int index = cp_entry->field_index();
  // bail out if field modifications are not watched
  if ((ik->field_access_flags(index) & JVM_ACC_FIELD_MODIFICATION_WATCHED) == 0) return;

  char sig_type = '\0';

  switch(cp_entry->flag_state()) {
    case btos: sig_type = 'Z'; break;
    case ctos: sig_type = 'C'; break;
    case stos: sig_type = 'S'; break;
    case itos: sig_type = 'I'; break;
    case ftos: sig_type = 'F'; break;
    case atos: sig_type = 'L'; break;
    case ltos: sig_type = 'J'; break;
    case dtos: sig_type = 'D'; break;
    default:  ShouldNotReachHere(); return;
  }
  bool is_static = (obj == NULL);

  HandleMark hm(thread);
  instanceKlassHandle h_klass(thread, k);
  jfieldID fid = jfieldIDWorkaround::to_jfieldID(h_klass, cp_entry->f2_as_index(), is_static);
  jvalue fvalue;
#ifdef _LP64
  fvalue = *value;
#else
  // Long/double values are stored unaligned and also noncontiguously with
  // tagged stacks.  We can't just do a simple assignment even in the non-
  // J/D cases because a C++ compiler is allowed to assume that a jvalue is
  // 8-byte aligned, and interpreter stack slots are only 4-byte aligned.
  // We assume that the two halves of longs/doubles are stored in interpreter
  // stack slots in platform-endian order.
  jlong_accessor u;
  jint* newval = (jint*)value;
  u.words[0] = newval[0];
  u.words[1] = newval[Interpreter::stackElementWords]; // skip if tag
  fvalue.j = u.long_value;
#endif // _LP64

  Handle h_obj;
  if (!is_static) {
    // non-static field accessors have an object, but we need a handle
    h_obj = Handle(thread, obj);
  }

  JvmtiExport::post_raw_field_modification(thread, method(thread), bcp(thread), h_klass, h_obj,
                                           fid, sig_type, &fvalue);
IRT_END

IRT_ENTRY(void, InterpreterRuntime::post_method_entry(JavaThread *thread))
  JvmtiExport::post_method_entry(thread, InterpreterRuntime::method(thread), InterpreterRuntime::last_frame(thread));
IRT_END


IRT_ENTRY(void, InterpreterRuntime::post_method_exit(JavaThread *thread))
  JvmtiExport::post_method_exit(thread, InterpreterRuntime::method(thread), InterpreterRuntime::last_frame(thread));
IRT_END

IRT_LEAF(int, InterpreterRuntime::interpreter_contains(address pc))
{
  return (Interpreter::contains(pc) ? 1 : 0);
}
IRT_END


// Implementation of SignatureHandlerLibrary

#ifndef SHARING_FAST_NATIVE_FINGERPRINTS
// Dummy definition (else normalization method is defined in CPU
// dependant code)
uint64_t InterpreterRuntime::normalize_fast_native_fingerprint(uint64_t fingerprint) {
  return fingerprint;
}
#endif

address SignatureHandlerLibrary::set_handler_blob() {
  BufferBlob* handler_blob = BufferBlob::create("native signature handlers", blob_size);
  if (handler_blob == NULL) {
    return NULL;
  }
  address handler = handler_blob->code_begin();
  _handler_blob = handler_blob;
  _handler = handler;
  return handler;
}

void SignatureHandlerLibrary::initialize() {
  if (_fingerprints != NULL) {
    return;
  }
  if (set_handler_blob() == NULL) {
    vm_exit_out_of_memory(blob_size, OOM_MALLOC_ERROR, "native signature handlers");
  }

  BufferBlob* bb = BufferBlob::create("Signature Handler Temp Buffer",
                                      SignatureHandlerLibrary::buffer_size);
  _buffer = bb->code_begin();

  _fingerprints = new(ResourceObj::C_HEAP, mtCode)GrowableArray<uint64_t>(32, true);
  _handlers     = new(ResourceObj::C_HEAP, mtCode)GrowableArray<address>(32, true);
}

address SignatureHandlerLibrary::set_handler(CodeBuffer* buffer) {
  address handler   = _handler;
  int     insts_size = buffer->pure_insts_size();
  if (handler + insts_size > _handler_blob->code_end()) {
    // get a new handler blob
    handler = set_handler_blob();
  }
  if (handler != NULL) {
    memcpy(handler, buffer->insts_begin(), insts_size);
    pd_set_handler(handler);
    ICache::invalidate_range(handler, insts_size);
    _handler = handler + insts_size;
  }
  CodeCacheExtensions::handle_generated_handler(handler, buffer->name(), _handler);
  return handler;
}

void SignatureHandlerLibrary::add(const methodHandle& method) {
  if (method->signature_handler() == NULL) {
    // use slow signature handler if we can't do better
    int handler_index = -1;
    // check if we can use customized (fast) signature handler
    if (UseFastSignatureHandlers && CodeCacheExtensions::support_fast_signature_handlers() && method->size_of_parameters() <= Fingerprinter::max_size_of_parameters) {
      // use customized signature handler
      MutexLocker mu(SignatureHandlerLibrary_lock);
      // make sure data structure is initialized
      initialize();
      // lookup method signature's fingerprint
      uint64_t fingerprint = Fingerprinter(method).fingerprint();
      // allow CPU dependant code to optimize the fingerprints for the fast handler
      fingerprint = InterpreterRuntime::normalize_fast_native_fingerprint(fingerprint);
      handler_index = _fingerprints->find(fingerprint);
      // create handler if necessary
      if (handler_index < 0) {
        ResourceMark rm;
        ptrdiff_t align_offset = (address)
          round_to((intptr_t)_buffer, CodeEntryAlignment) - (address)_buffer;
        CodeBuffer buffer((address)(_buffer + align_offset),
                          SignatureHandlerLibrary::buffer_size - align_offset);
        if (!CodeCacheExtensions::support_dynamic_code()) {
          // we need a name for the signature (for lookups or saving)
          const int SYMBOL_SIZE = 50;
          char *symbolName = NEW_RESOURCE_ARRAY(char, SYMBOL_SIZE);
          // support for named signatures
          jio_snprintf(symbolName, SYMBOL_SIZE,
                       "native_" UINT64_FORMAT, fingerprint);
          buffer.set_name(symbolName);
        }
        InterpreterRuntime::SignatureHandlerGenerator(method, &buffer).generate(fingerprint);
        // copy into code heap
        address handler = set_handler(&buffer);
        if (handler == NULL) {
          // use slow signature handler (without memorizing it in the fingerprints)
        } else {
          // debugging suppport
          if (PrintSignatureHandlers && (handler != Interpreter::slow_signature_handler())) {
            ttyLocker ttyl;
            tty->cr();
            tty->print_cr("argument handler #%d for: %s %s (fingerprint = " UINT64_FORMAT ", %d bytes generated)",
                          _handlers->length(),
                          (method->is_static() ? "static" : "receiver"),
                          method->name_and_sig_as_C_string(),
                          fingerprint,
                          buffer.insts_size());
            if (buffer.insts_size() > 0) {
              // buffer may be empty for pregenerated handlers
              Disassembler::decode(handler, handler + buffer.insts_size());
            }
#ifndef PRODUCT
            address rh_begin = Interpreter::result_handler(method()->result_type());
            if (CodeCache::contains(rh_begin)) {
              // else it might be special platform dependent values
              tty->print_cr(" --- associated result handler ---");
              address rh_end = rh_begin;
              while (*(int*)rh_end != 0) {
                rh_end += sizeof(int);
              }
              Disassembler::decode(rh_begin, rh_end);
            } else {
              tty->print_cr(" associated result handler: " PTR_FORMAT, p2i(rh_begin));
            }
#endif
          }
          // add handler to library
          _fingerprints->append(fingerprint);
          _handlers->append(handler);
          // set handler index
          assert(_fingerprints->length() == _handlers->length(), "sanity check");
          handler_index = _fingerprints->length() - 1;
        }
      }
      // Set handler under SignatureHandlerLibrary_lock
      if (handler_index < 0) {
        // use generic signature handler
        method->set_signature_handler(Interpreter::slow_signature_handler());
      } else {
        // set handler
        method->set_signature_handler(_handlers->at(handler_index));
      }
    } else {
      CHECK_UNHANDLED_OOPS_ONLY(Thread::current()->clear_unhandled_oops());
      // use generic signature handler
      method->set_signature_handler(Interpreter::slow_signature_handler());
    }
  }
#ifdef ASSERT
  int handler_index = -1;
  int fingerprint_index = -2;
  {
    // '_handlers' and '_fingerprints' are 'GrowableArray's and are NOT synchronized
    // in any way if accessed from multiple threads. To avoid races with another
    // thread which may change the arrays in the above, mutex protected block, we
    // have to protect this read access here with the same mutex as well!
    MutexLocker mu(SignatureHandlerLibrary_lock);
    if (_handlers != NULL) {
      handler_index = _handlers->find(method->signature_handler());
      uint64_t fingerprint = Fingerprinter(method).fingerprint();
      fingerprint = InterpreterRuntime::normalize_fast_native_fingerprint(fingerprint);
      fingerprint_index = _fingerprints->find(fingerprint);
    }
  }
  assert(method->signature_handler() == Interpreter::slow_signature_handler() ||
         handler_index == fingerprint_index, "sanity check");
#endif // ASSERT
}

void SignatureHandlerLibrary::add(uint64_t fingerprint, address handler) {
  int handler_index = -1;
  // use customized signature handler
  MutexLocker mu(SignatureHandlerLibrary_lock);
  // make sure data structure is initialized
  initialize();
  fingerprint = InterpreterRuntime::normalize_fast_native_fingerprint(fingerprint);
  handler_index = _fingerprints->find(fingerprint);
  // create handler if necessary
  if (handler_index < 0) {
    if (PrintSignatureHandlers && (handler != Interpreter::slow_signature_handler())) {
      tty->cr();
      tty->print_cr("argument handler #%d at " PTR_FORMAT " for fingerprint " UINT64_FORMAT,
                    _handlers->length(),
                    p2i(handler),
                    fingerprint);
    }
    _fingerprints->append(fingerprint);
    _handlers->append(handler);
  } else {
    if (PrintSignatureHandlers) {
      tty->cr();
      tty->print_cr("duplicate argument handler #%d for fingerprint " UINT64_FORMAT "(old: " PTR_FORMAT ", new : " PTR_FORMAT ")",
                    _handlers->length(),
                    fingerprint,
                    p2i(_handlers->at(handler_index)),
                    p2i(handler));
    }
  }
}


BufferBlob*              SignatureHandlerLibrary::_handler_blob = NULL;
address                  SignatureHandlerLibrary::_handler      = NULL;
GrowableArray<uint64_t>* SignatureHandlerLibrary::_fingerprints = NULL;
GrowableArray<address>*  SignatureHandlerLibrary::_handlers     = NULL;
address                  SignatureHandlerLibrary::_buffer       = NULL;


IRT_ENTRY(void, InterpreterRuntime::prepare_native_call(JavaThread* thread, Method* method))
  methodHandle m(thread, method);
  assert(m->is_native(), "sanity check");
  // lookup native function entry point if it doesn't exist
  bool in_base_library;
  if (!m->has_native_function()) {
    NativeLookup::lookup(m, in_base_library, CHECK);
  }
  // make sure signature handler is installed
  SignatureHandlerLibrary::add(m);
  // The interpreter entry point checks the signature handler first,
  // before trying to fetch the native entry point and klass mirror.
  // We must set the signature handler last, so that multiple processors
  // preparing the same method will be sure to see non-null entry & mirror.
IRT_END

#if defined(IA32) || defined(AMD64) || defined(ARM)
IRT_LEAF(void, InterpreterRuntime::popframe_move_outgoing_args(JavaThread* thread, void* src_address, void* dest_address))
  if (src_address == dest_address) {
    return;
  }
  ResetNoHandleMark rnm; // In a LEAF entry.
  HandleMark hm;
  ResourceMark rm;
  frame fr = thread->last_frame();
  assert(fr.is_interpreted_frame(), "");
  jint bci = fr.interpreter_frame_bci();
  methodHandle mh(thread, fr.interpreter_frame_method());
  Bytecode_invoke invoke(mh, bci);
  ArgumentSizeComputer asc(invoke.signature());
  int size_of_arguments = (asc.size() + (invoke.has_receiver() ? 1 : 0)); // receiver
  Copy::conjoint_jbytes(src_address, dest_address,
                       size_of_arguments * Interpreter::stackElementSize);
IRT_END
#endif

#if INCLUDE_JVMTI
// This is a support of the JVMTI PopFrame interface.
// Make sure it is an invokestatic of a polymorphic intrinsic that has a member_name argument
// and return it as a vm_result so that it can be reloaded in the list of invokestatic parameters.
// The member_name argument is a saved reference (in local#0) to the member_name.
// For backward compatibility with some JDK versions (7, 8) it can also be a direct method handle.
// FIXME: remove DMH case after j.l.i.InvokerBytecodeGenerator code shape is updated.
IRT_ENTRY(void, InterpreterRuntime::member_name_arg_or_null(JavaThread* thread, address member_name,
                                                            Method* method, address bcp))
  Bytecodes::Code code = Bytecodes::code_at(method, bcp);
  if (code != Bytecodes::_invokestatic) {
    return;
  }
  ConstantPool* cpool = method->constants();
  int cp_index = Bytes::get_native_u2(bcp + 1) + ConstantPool::CPCACHE_INDEX_TAG;
  Symbol* cname = cpool->klass_name_at(cpool->klass_ref_index_at(cp_index));
  Symbol* mname = cpool->name_ref_at(cp_index);

  if (MethodHandles::has_member_arg(cname, mname)) {
    oop member_name_oop = (oop) member_name;
    if (java_lang_invoke_DirectMethodHandle::is_instance(member_name_oop)) {
      // FIXME: remove after j.l.i.InvokerBytecodeGenerator code shape is updated.
      member_name_oop = java_lang_invoke_DirectMethodHandle::member(member_name_oop);
    }
    thread->set_vm_result(member_name_oop);
  } else {
    thread->set_vm_result(NULL);
  }
IRT_END
#endif // INCLUDE_JVMTI
