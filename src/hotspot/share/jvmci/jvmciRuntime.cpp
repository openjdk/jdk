/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "jvm.h"
#include "asm/codeBuffer.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "code/codeCache.hpp"
#include "code/compiledMethod.inline.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/disassembler.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "jvmci/jvmciCompilerToVM.hpp"
#include "jvmci/jvmciCompiler.hpp"
#include "jvmci/jvmciJavaClasses.hpp"
#include "jvmci/jvmciEnv.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/reflection.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/threadSMR.hpp"
#include "utilities/debug.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_G1GC
#include "gc/g1/g1ThreadLocalData.hpp"
#endif // INCLUDE_G1GC

#if defined(_MSC_VER)
#define strtoll _strtoi64
#endif

jobject JVMCIRuntime::_HotSpotJVMCIRuntime_instance = NULL;
bool JVMCIRuntime::_HotSpotJVMCIRuntime_initialized = false;
bool JVMCIRuntime::_well_known_classes_initialized = false;
JVMCIRuntime::CompLevelAdjustment JVMCIRuntime::_comp_level_adjustment = JVMCIRuntime::none;
bool JVMCIRuntime::_shutdown_called = false;

BasicType JVMCIRuntime::kindToBasicType(Handle kind, TRAPS) {
  if (kind.is_null()) {
    THROW_(vmSymbols::java_lang_NullPointerException(), T_ILLEGAL);
  }
  jchar ch = JavaKind::typeChar(kind);
  switch(ch) {
    case 'Z': return T_BOOLEAN;
    case 'B': return T_BYTE;
    case 'S': return T_SHORT;
    case 'C': return T_CHAR;
    case 'I': return T_INT;
    case 'F': return T_FLOAT;
    case 'J': return T_LONG;
    case 'D': return T_DOUBLE;
    case 'A': return T_OBJECT;
    case '-': return T_ILLEGAL;
    default:
      JVMCI_ERROR_(T_ILLEGAL, "unexpected Kind: %c", ch);
  }
}

// Simple helper to see if the caller of a runtime stub which
// entered the VM has been deoptimized

static bool caller_is_deopted() {
  JavaThread* thread = JavaThread::current();
  RegisterMap reg_map(thread, false);
  frame runtime_frame = thread->last_frame();
  frame caller_frame = runtime_frame.sender(&reg_map);
  assert(caller_frame.is_compiled_frame(), "must be compiled");
  return caller_frame.is_deoptimized_frame();
}

// Stress deoptimization
static void deopt_caller() {
  if ( !caller_is_deopted()) {
    JavaThread* thread = JavaThread::current();
    RegisterMap reg_map(thread, false);
    frame runtime_frame = thread->last_frame();
    frame caller_frame = runtime_frame.sender(&reg_map);
    Deoptimization::deoptimize_frame(thread, caller_frame.id(), Deoptimization::Reason_constraint);
    assert(caller_is_deopted(), "Must be deoptimized");
  }
}

// Manages a scope for a JVMCI runtime call that attempts a heap allocation.
// If there is a pending exception upon closing the scope and the runtime
// call is of the variety where allocation failure returns NULL without an
// exception, the following action is taken:
//   1. The pending exception is cleared
//   2. NULL is written to JavaThread::_vm_result
//   3. Checks that an OutOfMemoryError is Universe::out_of_memory_error_retry().
class RetryableAllocationMark: public StackObj {
 private:
  JavaThread* _thread;
 public:
  RetryableAllocationMark(JavaThread* thread, bool activate) {
    if (activate) {
      assert(!thread->in_retryable_allocation(), "retryable allocation scope is non-reentrant");
      _thread = thread;
      _thread->set_in_retryable_allocation(true);
    } else {
      _thread = NULL;
    }
  }
  ~RetryableAllocationMark() {
    if (_thread != NULL) {
      _thread->set_in_retryable_allocation(false);
      JavaThread* THREAD = _thread;
      if (HAS_PENDING_EXCEPTION) {
        oop ex = PENDING_EXCEPTION;
        CLEAR_PENDING_EXCEPTION;
        oop retry_oome = Universe::out_of_memory_error_retry();
        if (ex->is_a(retry_oome->klass()) && retry_oome != ex) {
          ResourceMark rm;
          fatal("Unexpected exception in scope of retryable allocation: " INTPTR_FORMAT " of type %s", p2i(ex), ex->klass()->external_name());
        }
        _thread->set_vm_result(NULL);
      }
    }
  }
};

JRT_BLOCK_ENTRY(void, JVMCIRuntime::new_instance_common(JavaThread* thread, Klass* klass, bool null_on_fail))
  JRT_BLOCK;
  assert(klass->is_klass(), "not a class");
  Handle holder(THREAD, klass->klass_holder()); // keep the klass alive
  InstanceKlass* ik = InstanceKlass::cast(klass);
  {
    RetryableAllocationMark ram(thread, null_on_fail);
    ik->check_valid_for_instantiation(true, CHECK);
    oop obj;
    if (null_on_fail) {
      if (!ik->is_initialized()) {
        // Cannot re-execute class initialization without side effects
        // so return without attempting the initialization
        return;
      }
    } else {
      // make sure klass is initialized
      ik->initialize(CHECK);
    }
    // allocate instance and return via TLS
    obj = ik->allocate_instance(CHECK);
    thread->set_vm_result(obj);
  }
  JRT_BLOCK_END;
  SharedRuntime::on_slowpath_allocation_exit(thread);
JRT_END

JRT_BLOCK_ENTRY(void, JVMCIRuntime::new_array_common(JavaThread* thread, Klass* array_klass, jint length, bool null_on_fail))
  JRT_BLOCK;
  // Note: no handle for klass needed since they are not used
  //       anymore after new_objArray() and no GC can happen before.
  //       (This may have to change if this code changes!)
  assert(array_klass->is_klass(), "not a class");
  oop obj;
  if (array_klass->is_typeArray_klass()) {
    BasicType elt_type = TypeArrayKlass::cast(array_klass)->element_type();
    RetryableAllocationMark ram(thread, null_on_fail);
    obj = oopFactory::new_typeArray(elt_type, length, CHECK);
  } else {
    Handle holder(THREAD, array_klass->klass_holder()); // keep the klass alive
    Klass* elem_klass = ObjArrayKlass::cast(array_klass)->element_klass();
    RetryableAllocationMark ram(thread, null_on_fail);
    obj = oopFactory::new_objArray(elem_klass, length, CHECK);
  }
  thread->set_vm_result(obj);
  // This is pretty rare but this runtime patch is stressful to deoptimization
  // if we deoptimize here so force a deopt to stress the path.
  if (DeoptimizeALot) {
    static int deopts = 0;
    // Alternate between deoptimizing and raising an error (which will also cause a deopt)
    if (deopts++ % 2 == 0) {
      if (null_on_fail) {
        return;
      } else {
        ResourceMark rm(THREAD);
        THROW(vmSymbols::java_lang_OutOfMemoryError());
      }
    } else {
      deopt_caller();
    }
  }
  JRT_BLOCK_END;
  SharedRuntime::on_slowpath_allocation_exit(thread);
JRT_END

JRT_ENTRY(void, JVMCIRuntime::new_multi_array_common(JavaThread* thread, Klass* klass, int rank, jint* dims, bool null_on_fail))
  assert(klass->is_klass(), "not a class");
  assert(rank >= 1, "rank must be nonzero");
  Handle holder(THREAD, klass->klass_holder()); // keep the klass alive
  RetryableAllocationMark ram(thread, null_on_fail);
  oop obj = ArrayKlass::cast(klass)->multi_allocate(rank, dims, CHECK);
  thread->set_vm_result(obj);
JRT_END

JRT_ENTRY(void, JVMCIRuntime::dynamic_new_array_common(JavaThread* thread, oopDesc* element_mirror, jint length, bool null_on_fail))
  RetryableAllocationMark ram(thread, null_on_fail);
  oop obj = Reflection::reflect_new_array(element_mirror, length, CHECK);
  thread->set_vm_result(obj);
JRT_END

JRT_ENTRY(void, JVMCIRuntime::dynamic_new_instance_common(JavaThread* thread, oopDesc* type_mirror, bool null_on_fail))
  InstanceKlass* klass = InstanceKlass::cast(java_lang_Class::as_Klass(type_mirror));

  if (klass == NULL) {
    ResourceMark rm(THREAD);
    THROW(vmSymbols::java_lang_InstantiationException());
  }
  RetryableAllocationMark ram(thread, null_on_fail);

  // Create new instance (the receiver)
  klass->check_valid_for_instantiation(false, CHECK);

  if (null_on_fail) {
    if (!klass->is_initialized()) {
      // Cannot re-execute class initialization without side effects
      // so return without attempting the initialization
      return;
    }
  } else {
    // Make sure klass gets initialized
    klass->initialize(CHECK);
  }

  oop obj = klass->allocate_instance(CHECK);
  thread->set_vm_result(obj);
JRT_END

extern void vm_exit(int code);

// Enter this method from compiled code handler below. This is where we transition
// to VM mode. This is done as a helper routine so that the method called directly
// from compiled code does not have to transition to VM. This allows the entry
// method to see if the nmethod that we have just looked up a handler for has
// been deoptimized while we were in the vm. This simplifies the assembly code
// cpu directories.
//
// We are entering here from exception stub (via the entry method below)
// If there is a compiled exception handler in this method, we will continue there;
// otherwise we will unwind the stack and continue at the caller of top frame method
// Note: we enter in Java using a special JRT wrapper. This wrapper allows us to
// control the area where we can allow a safepoint. After we exit the safepoint area we can
// check to see if the handler we are going to return is now in a nmethod that has
// been deoptimized. If that is the case we return the deopt blob
// unpack_with_exception entry instead. This makes life for the exception blob easier
// because making that same check and diverting is painful from assembly language.
JRT_ENTRY_NO_ASYNC(static address, exception_handler_for_pc_helper(JavaThread* thread, oopDesc* ex, address pc, CompiledMethod*& cm))
  // Reset method handle flag.
  thread->set_is_method_handle_return(false);

  Handle exception(thread, ex);
  cm = CodeCache::find_compiled(pc);
  assert(cm != NULL, "this is not a compiled method");
  // Adjust the pc as needed/
  if (cm->is_deopt_pc(pc)) {
    RegisterMap map(thread, false);
    frame exception_frame = thread->last_frame().sender(&map);
    // if the frame isn't deopted then pc must not correspond to the caller of last_frame
    assert(exception_frame.is_deoptimized_frame(), "must be deopted");
    pc = exception_frame.pc();
  }
#ifdef ASSERT
  assert(exception.not_null(), "NULL exceptions should be handled by throw_exception");
  // Check that exception is a subclass of Throwable, otherwise we have a VerifyError
  if (!(exception->is_a(SystemDictionary::Throwable_klass()))) {
    if (ExitVMOnVerifyError) vm_exit(-1);
    ShouldNotReachHere();
  }
#endif

  // Check the stack guard pages and reenable them if necessary and there is
  // enough space on the stack to do so.  Use fast exceptions only if the guard
  // pages are enabled.
  bool guard_pages_enabled = thread->stack_guards_enabled();
  if (!guard_pages_enabled) guard_pages_enabled = thread->reguard_stack();

  if (JvmtiExport::can_post_on_exceptions()) {
    // To ensure correct notification of exception catches and throws
    // we have to deoptimize here.  If we attempted to notify the
    // catches and throws during this exception lookup it's possible
    // we could deoptimize on the way out of the VM and end back in
    // the interpreter at the throw site.  This would result in double
    // notifications since the interpreter would also notify about
    // these same catches and throws as it unwound the frame.

    RegisterMap reg_map(thread);
    frame stub_frame = thread->last_frame();
    frame caller_frame = stub_frame.sender(&reg_map);

    // We don't really want to deoptimize the nmethod itself since we
    // can actually continue in the exception handler ourselves but I
    // don't see an easy way to have the desired effect.
    Deoptimization::deoptimize_frame(thread, caller_frame.id(), Deoptimization::Reason_constraint);
    assert(caller_is_deopted(), "Must be deoptimized");

    return SharedRuntime::deopt_blob()->unpack_with_exception_in_tls();
  }

  // ExceptionCache is used only for exceptions at call sites and not for implicit exceptions
  if (guard_pages_enabled) {
    address fast_continuation = cm->handler_for_exception_and_pc(exception, pc);
    if (fast_continuation != NULL) {
      // Set flag if return address is a method handle call site.
      thread->set_is_method_handle_return(cm->is_method_handle_return(pc));
      return fast_continuation;
    }
  }

  // If the stack guard pages are enabled, check whether there is a handler in
  // the current method.  Otherwise (guard pages disabled), force an unwind and
  // skip the exception cache update (i.e., just leave continuation==NULL).
  address continuation = NULL;
  if (guard_pages_enabled) {

    // New exception handling mechanism can support inlined methods
    // with exception handlers since the mappings are from PC to PC

    // debugging support
    // tracing
    if (log_is_enabled(Info, exceptions)) {
      ResourceMark rm;
      stringStream tempst;
      assert(cm->method() != NULL, "Unexpected null method()");
      tempst.print("compiled method <%s>\n"
                   " at PC" INTPTR_FORMAT " for thread " INTPTR_FORMAT,
                   cm->method()->print_value_string(), p2i(pc), p2i(thread));
      Exceptions::log_exception(exception, tempst);
    }
    // for AbortVMOnException flag
    NOT_PRODUCT(Exceptions::debug_check_abort(exception));

    // Clear out the exception oop and pc since looking up an
    // exception handler can cause class loading, which might throw an
    // exception and those fields are expected to be clear during
    // normal bytecode execution.
    thread->clear_exception_oop_and_pc();

    bool recursive_exception = false;
    continuation = SharedRuntime::compute_compiled_exc_handler(cm, pc, exception, false, false, recursive_exception);
    // If an exception was thrown during exception dispatch, the exception oop may have changed
    thread->set_exception_oop(exception());
    thread->set_exception_pc(pc);

    // the exception cache is used only by non-implicit exceptions
    // Update the exception cache only when there didn't happen
    // another exception during the computation of the compiled
    // exception handler. Checking for exception oop equality is not
    // sufficient because some exceptions are pre-allocated and reused.
    if (continuation != NULL && !recursive_exception && !SharedRuntime::deopt_blob()->contains(continuation)) {
      cm->add_handler_for_exception_and_pc(exception, pc, continuation);
    }
  }

  // Set flag if return address is a method handle call site.
  thread->set_is_method_handle_return(cm->is_method_handle_return(pc));

  if (log_is_enabled(Info, exceptions)) {
    ResourceMark rm;
    log_info(exceptions)("Thread " PTR_FORMAT " continuing at PC " PTR_FORMAT
                         " for exception thrown at PC " PTR_FORMAT,
                         p2i(thread), p2i(continuation), p2i(pc));
  }

  return continuation;
JRT_END

// Enter this method from compiled code only if there is a Java exception handler
// in the method handling the exception.
// We are entering here from exception stub. We don't do a normal VM transition here.
// We do it in a helper. This is so we can check to see if the nmethod we have just
// searched for an exception handler has been deoptimized in the meantime.
address JVMCIRuntime::exception_handler_for_pc(JavaThread* thread) {
  oop exception = thread->exception_oop();
  address pc = thread->exception_pc();
  // Still in Java mode
  DEBUG_ONLY(ResetNoHandleMark rnhm);
  CompiledMethod* cm = NULL;
  address continuation = NULL;
  {
    // Enter VM mode by calling the helper
    ResetNoHandleMark rnhm;
    continuation = exception_handler_for_pc_helper(thread, exception, pc, cm);
  }
  // Back in JAVA, use no oops DON'T safepoint

  // Now check to see if the compiled method we were called from is now deoptimized.
  // If so we must return to the deopt blob and deoptimize the nmethod
  if (cm != NULL && caller_is_deopted()) {
    continuation = SharedRuntime::deopt_blob()->unpack_with_exception_in_tls();
  }

  assert(continuation != NULL, "no handler found");
  return continuation;
}

JRT_ENTRY_NO_ASYNC(void, JVMCIRuntime::monitorenter(JavaThread* thread, oopDesc* obj, BasicLock* lock))
  IF_TRACE_jvmci_3 {
    char type[O_BUFLEN];
    obj->klass()->name()->as_C_string(type, O_BUFLEN);
    markOop mark = obj->mark();
    TRACE_jvmci_3("%s: entered locking slow case with obj=" INTPTR_FORMAT ", type=%s, mark=" INTPTR_FORMAT ", lock=" INTPTR_FORMAT, thread->name(), p2i(obj), type, p2i(mark), p2i(lock));
    tty->flush();
  }
#ifdef ASSERT
  if (PrintBiasedLockingStatistics) {
    Atomic::inc(BiasedLocking::slow_path_entry_count_addr());
  }
#endif
  Handle h_obj(thread, obj);
  if (UseBiasedLocking) {
    // Retry fast entry if bias is revoked to avoid unnecessary inflation
    ObjectSynchronizer::fast_enter(h_obj, lock, true, CHECK);
  } else {
    if (JVMCIUseFastLocking) {
      // When using fast locking, the compiled code has already tried the fast case
      ObjectSynchronizer::slow_enter(h_obj, lock, THREAD);
    } else {
      ObjectSynchronizer::fast_enter(h_obj, lock, false, THREAD);
    }
  }
  TRACE_jvmci_3("%s: exiting locking slow with obj=" INTPTR_FORMAT, thread->name(), p2i(obj));
JRT_END

JRT_LEAF(void, JVMCIRuntime::monitorexit(JavaThread* thread, oopDesc* obj, BasicLock* lock))
  assert(thread == JavaThread::current(), "threads must correspond");
  assert(thread->last_Java_sp(), "last_Java_sp must be set");
  // monitorexit is non-blocking (leaf routine) => no exceptions can be thrown
  EXCEPTION_MARK;

#ifdef DEBUG
  if (!oopDesc::is_oop(obj)) {
    ResetNoHandleMark rhm;
    nmethod* method = thread->last_frame().cb()->as_nmethod_or_null();
    if (method != NULL) {
      tty->print_cr("ERROR in monitorexit in method %s wrong obj " INTPTR_FORMAT, method->name(), p2i(obj));
    }
    thread->print_stack_on(tty);
    assert(false, "invalid lock object pointer dected");
  }
#endif

  if (JVMCIUseFastLocking) {
    // When using fast locking, the compiled code has already tried the fast case
    ObjectSynchronizer::slow_exit(obj, lock, THREAD);
  } else {
    ObjectSynchronizer::fast_exit(obj, lock, THREAD);
  }
  IF_TRACE_jvmci_3 {
    char type[O_BUFLEN];
    obj->klass()->name()->as_C_string(type, O_BUFLEN);
    TRACE_jvmci_3("%s: exited locking slow case with obj=" INTPTR_FORMAT ", type=%s, mark=" INTPTR_FORMAT ", lock=" INTPTR_FORMAT, thread->name(), p2i(obj), type, p2i(obj->mark()), p2i(lock));
    tty->flush();
  }
JRT_END

// Object.notify() fast path, caller does slow path
JRT_LEAF(jboolean, JVMCIRuntime::object_notify(JavaThread *thread, oopDesc* obj))

  // Very few notify/notifyAll operations find any threads on the waitset, so
  // the dominant fast-path is to simply return.
  // Relatedly, it's critical that notify/notifyAll be fast in order to
  // reduce lock hold times.
  if (!SafepointSynchronize::is_synchronizing()) {
    if (ObjectSynchronizer::quick_notify(obj, thread, false)) {
      return true;
    }
  }
  return false; // caller must perform slow path

JRT_END

// Object.notifyAll() fast path, caller does slow path
JRT_LEAF(jboolean, JVMCIRuntime::object_notifyAll(JavaThread *thread, oopDesc* obj))

  if (!SafepointSynchronize::is_synchronizing() ) {
    if (ObjectSynchronizer::quick_notify(obj, thread, true)) {
      return true;
    }
  }
  return false; // caller must perform slow path

JRT_END

JRT_ENTRY(void, JVMCIRuntime::throw_and_post_jvmti_exception(JavaThread* thread, const char* exception, const char* message))
  TempNewSymbol symbol = SymbolTable::new_symbol(exception, CHECK);
  SharedRuntime::throw_and_post_jvmti_exception(thread, symbol, message);
JRT_END

JRT_ENTRY(void, JVMCIRuntime::throw_klass_external_name_exception(JavaThread* thread, const char* exception, Klass* klass))
  ResourceMark rm(thread);
  TempNewSymbol symbol = SymbolTable::new_symbol(exception, CHECK);
  SharedRuntime::throw_and_post_jvmti_exception(thread, symbol, klass->external_name());
JRT_END

JRT_ENTRY(void, JVMCIRuntime::throw_class_cast_exception(JavaThread* thread, const char* exception, Klass* caster_klass, Klass* target_klass))
  ResourceMark rm(thread);
  const char* message = SharedRuntime::generate_class_cast_message(caster_klass, target_klass);
  TempNewSymbol symbol = SymbolTable::new_symbol(exception, CHECK);
  SharedRuntime::throw_and_post_jvmti_exception(thread, symbol, message);
JRT_END

JRT_LEAF(void, JVMCIRuntime::log_object(JavaThread* thread, oopDesc* obj, bool as_string, bool newline))
  ttyLocker ttyl;

  if (obj == NULL) {
    tty->print("NULL");
  } else if (oopDesc::is_oop_or_null(obj, true) && (!as_string || !java_lang_String::is_instance(obj))) {
    if (oopDesc::is_oop_or_null(obj, true)) {
      char buf[O_BUFLEN];
      tty->print("%s@" INTPTR_FORMAT, obj->klass()->name()->as_C_string(buf, O_BUFLEN), p2i(obj));
    } else {
      tty->print(INTPTR_FORMAT, p2i(obj));
    }
  } else {
    ResourceMark rm;
    assert(obj != NULL && java_lang_String::is_instance(obj), "must be");
    char *buf = java_lang_String::as_utf8_string(obj);
    tty->print_raw(buf);
  }
  if (newline) {
    tty->cr();
  }
JRT_END

#if INCLUDE_G1GC

JRT_LEAF(void, JVMCIRuntime::write_barrier_pre(JavaThread* thread, oopDesc* obj))
  G1ThreadLocalData::satb_mark_queue(thread).enqueue(obj);
JRT_END

JRT_LEAF(void, JVMCIRuntime::write_barrier_post(JavaThread* thread, void* card_addr))
  G1ThreadLocalData::dirty_card_queue(thread).enqueue(card_addr);
JRT_END

#endif // INCLUDE_G1GC

JRT_LEAF(jboolean, JVMCIRuntime::validate_object(JavaThread* thread, oopDesc* parent, oopDesc* child))
  bool ret = true;
  if(!Universe::heap()->is_in_closed_subset(parent)) {
    tty->print_cr("Parent Object " INTPTR_FORMAT " not in heap", p2i(parent));
    parent->print();
    ret=false;
  }
  if(!Universe::heap()->is_in_closed_subset(child)) {
    tty->print_cr("Child Object " INTPTR_FORMAT " not in heap", p2i(child));
    child->print();
    ret=false;
  }
  return (jint)ret;
JRT_END

JRT_ENTRY(void, JVMCIRuntime::vm_error(JavaThread* thread, jlong where, jlong format, jlong value))
  ResourceMark rm;
  const char *error_msg = where == 0L ? "<internal JVMCI error>" : (char*) (address) where;
  char *detail_msg = NULL;
  if (format != 0L) {
    const char* buf = (char*) (address) format;
    size_t detail_msg_length = strlen(buf) * 2;
    detail_msg = (char *) NEW_RESOURCE_ARRAY(u_char, detail_msg_length);
    jio_snprintf(detail_msg, detail_msg_length, buf, value);
    report_vm_error(__FILE__, __LINE__, error_msg, "%s", detail_msg);
  } else {
    report_vm_error(__FILE__, __LINE__, error_msg);
  }
JRT_END

JRT_LEAF(oopDesc*, JVMCIRuntime::load_and_clear_exception(JavaThread* thread))
  oop exception = thread->exception_oop();
  assert(exception != NULL, "npe");
  thread->set_exception_oop(NULL);
  thread->set_exception_pc(0);
  return exception;
JRT_END

PRAGMA_DIAG_PUSH
PRAGMA_FORMAT_NONLITERAL_IGNORED
JRT_LEAF(void, JVMCIRuntime::log_printf(JavaThread* thread, const char* format, jlong v1, jlong v2, jlong v3))
  ResourceMark rm;
  tty->print(format, v1, v2, v3);
JRT_END
PRAGMA_DIAG_POP

static void decipher(jlong v, bool ignoreZero) {
  if (v != 0 || !ignoreZero) {
    void* p = (void *)(address) v;
    CodeBlob* cb = CodeCache::find_blob(p);
    if (cb) {
      if (cb->is_nmethod()) {
        char buf[O_BUFLEN];
        tty->print("%s [" INTPTR_FORMAT "+" JLONG_FORMAT "]", cb->as_nmethod_or_null()->method()->name_and_sig_as_C_string(buf, O_BUFLEN), p2i(cb->code_begin()), (jlong)((address)v - cb->code_begin()));
        return;
      }
      cb->print_value_on(tty);
      return;
    }
    if (Universe::heap()->is_in(p)) {
      oop obj = oop(p);
      obj->print_value_on(tty);
      return;
    }
    tty->print(INTPTR_FORMAT " [long: " JLONG_FORMAT ", double %lf, char %c]",p2i((void *)v), (jlong)v, (jdouble)v, (char)v);
  }
}

PRAGMA_DIAG_PUSH
PRAGMA_FORMAT_NONLITERAL_IGNORED
JRT_LEAF(void, JVMCIRuntime::vm_message(jboolean vmError, jlong format, jlong v1, jlong v2, jlong v3))
  ResourceMark rm;
  const char *buf = (const char*) (address) format;
  if (vmError) {
    if (buf != NULL) {
      fatal(buf, v1, v2, v3);
    } else {
      fatal("<anonymous error>");
    }
  } else if (buf != NULL) {
    tty->print(buf, v1, v2, v3);
  } else {
    assert(v2 == 0, "v2 != 0");
    assert(v3 == 0, "v3 != 0");
    decipher(v1, false);
  }
JRT_END
PRAGMA_DIAG_POP

JRT_LEAF(void, JVMCIRuntime::log_primitive(JavaThread* thread, jchar typeChar, jlong value, jboolean newline))
  union {
      jlong l;
      jdouble d;
      jfloat f;
  } uu;
  uu.l = value;
  switch (typeChar) {
    case 'Z': tty->print(value == 0 ? "false" : "true"); break;
    case 'B': tty->print("%d", (jbyte) value); break;
    case 'C': tty->print("%c", (jchar) value); break;
    case 'S': tty->print("%d", (jshort) value); break;
    case 'I': tty->print("%d", (jint) value); break;
    case 'F': tty->print("%f", uu.f); break;
    case 'J': tty->print(JLONG_FORMAT, value); break;
    case 'D': tty->print("%lf", uu.d); break;
    default: assert(false, "unknown typeChar"); break;
  }
  if (newline) {
    tty->cr();
  }
JRT_END

JRT_ENTRY(jint, JVMCIRuntime::identity_hash_code(JavaThread* thread, oopDesc* obj))
  return (jint) obj->identity_hash();
JRT_END

JRT_ENTRY(jboolean, JVMCIRuntime::thread_is_interrupted(JavaThread* thread, oopDesc* receiver, jboolean clear_interrupted))
  Handle receiverHandle(thread, receiver);
  // A nested ThreadsListHandle may require the Threads_lock which
  // requires thread_in_vm which is why this method cannot be JRT_LEAF.
  ThreadsListHandle tlh;

  JavaThread* receiverThread = java_lang_Thread::thread(receiverHandle());
  if (receiverThread == NULL || (EnableThreadSMRExtraValidityChecks && !tlh.includes(receiverThread))) {
    // The other thread may exit during this process, which is ok so return false.
    return JNI_FALSE;
  } else {
    return (jint) Thread::is_interrupted(receiverThread, clear_interrupted != 0);
  }
JRT_END

JRT_ENTRY(int, JVMCIRuntime::test_deoptimize_call_int(JavaThread* thread, int value))
  deopt_caller();
  return value;
JRT_END

void JVMCIRuntime::force_initialization(TRAPS) {
  JVMCIRuntime::initialize_well_known_classes(CHECK);

  ResourceMark rm;
  TempNewSymbol getCompiler = SymbolTable::new_symbol("getCompiler", CHECK);
  TempNewSymbol sig = SymbolTable::new_symbol("()Ljdk/vm/ci/runtime/JVMCICompiler;", CHECK);
  Handle jvmciRuntime = JVMCIRuntime::get_HotSpotJVMCIRuntime(CHECK);
  JavaValue result(T_OBJECT);
  JavaCalls::call_virtual(&result, jvmciRuntime, HotSpotJVMCIRuntime::klass(), getCompiler, sig, CHECK);
}

// private static JVMCIRuntime JVMCI.initializeRuntime()
JVM_ENTRY(jobject, JVM_GetJVMCIRuntime(JNIEnv *env, jclass c))
  if (!EnableJVMCI) {
    THROW_MSG_NULL(vmSymbols::java_lang_InternalError(), "JVMCI is not enabled")
  }
  JVMCIRuntime::initialize_HotSpotJVMCIRuntime(CHECK_NULL);
  jobject ret = JVMCIRuntime::get_HotSpotJVMCIRuntime_jobject(CHECK_NULL);
  return ret;
JVM_END

Handle JVMCIRuntime::callStatic(const char* className, const char* methodName, const char* signature, JavaCallArguments* args, TRAPS) {
  TempNewSymbol name = SymbolTable::new_symbol(className, CHECK_(Handle()));
  Klass* klass = SystemDictionary::resolve_or_fail(name, true, CHECK_(Handle()));
  TempNewSymbol runtime = SymbolTable::new_symbol(methodName, CHECK_(Handle()));
  TempNewSymbol sig = SymbolTable::new_symbol(signature, CHECK_(Handle()));
  JavaValue result(T_OBJECT);
  if (args == NULL) {
    JavaCalls::call_static(&result, klass, runtime, sig, CHECK_(Handle()));
  } else {
    JavaCalls::call_static(&result, klass, runtime, sig, args, CHECK_(Handle()));
  }
  return Handle(THREAD, (oop)result.get_jobject());
}

Handle JVMCIRuntime::get_HotSpotJVMCIRuntime(TRAPS) {
  initialize_JVMCI(CHECK_(Handle()));
  return Handle(THREAD, JNIHandles::resolve_non_null(_HotSpotJVMCIRuntime_instance));
}

void JVMCIRuntime::initialize_HotSpotJVMCIRuntime(TRAPS) {
  guarantee(!_HotSpotJVMCIRuntime_initialized, "cannot reinitialize HotSpotJVMCIRuntime");
  JVMCIRuntime::initialize_well_known_classes(CHECK);
  // This should only be called in the context of the JVMCI class being initialized
  InstanceKlass* klass = SystemDictionary::JVMCI_klass();
  guarantee(klass->is_being_initialized() && klass->is_reentrant_initialization(THREAD),
         "HotSpotJVMCIRuntime initialization should only be triggered through JVMCI initialization");

  Handle result = callStatic("jdk/vm/ci/hotspot/HotSpotJVMCIRuntime",
                             "runtime",
                             "()Ljdk/vm/ci/hotspot/HotSpotJVMCIRuntime;", NULL, CHECK);
  int adjustment = HotSpotJVMCIRuntime::compilationLevelAdjustment(result);
  assert(adjustment >= JVMCIRuntime::none &&
         adjustment <= JVMCIRuntime::by_full_signature,
         "compilation level adjustment out of bounds");
  _comp_level_adjustment = (CompLevelAdjustment) adjustment;
  _HotSpotJVMCIRuntime_initialized = true;
  _HotSpotJVMCIRuntime_instance = JNIHandles::make_global(result);
}

void JVMCIRuntime::initialize_JVMCI(TRAPS) {
  if (JNIHandles::resolve(_HotSpotJVMCIRuntime_instance) == NULL) {
    callStatic("jdk/vm/ci/runtime/JVMCI",
               "getRuntime",
               "()Ljdk/vm/ci/runtime/JVMCIRuntime;", NULL, CHECK);
  }
  assert(_HotSpotJVMCIRuntime_initialized == true, "what?");
}

bool JVMCIRuntime::can_initialize_JVMCI() {
  // Initializing JVMCI requires the module system to be initialized past phase 3.
  // The JVMCI API itself isn't available until phase 2 and ServiceLoader (which
  // JVMCI initialization requires) isn't usable until after phase 3. Testing
  // whether the system loader is initialized satisfies all these invariants.
  if (SystemDictionary::java_system_loader() == NULL) {
    return false;
  }
  assert(Universe::is_module_initialized(), "must be");
  return true;
}

void JVMCIRuntime::initialize_well_known_classes(TRAPS) {
  if (JVMCIRuntime::_well_known_classes_initialized == false) {
    guarantee(can_initialize_JVMCI(), "VM is not yet sufficiently booted to initialize JVMCI");
    SystemDictionary::WKID scan = SystemDictionary::FIRST_JVMCI_WKID;
    SystemDictionary::resolve_wk_klasses_through(SystemDictionary::LAST_JVMCI_WKID, scan, CHECK);
    JVMCIJavaClasses::compute_offsets(CHECK);
    JVMCIRuntime::_well_known_classes_initialized = true;
  }
}

void JVMCIRuntime::metadata_do(void f(Metadata*)) {
  // For simplicity, the existence of HotSpotJVMCIMetaAccessContext in
  // the SystemDictionary well known classes should ensure the other
  // classes have already been loaded, so make sure their order in the
  // table enforces that.
  assert(SystemDictionary::WK_KLASS_ENUM_NAME(jdk_vm_ci_hotspot_HotSpotResolvedJavaMethodImpl) <
         SystemDictionary::WK_KLASS_ENUM_NAME(jdk_vm_ci_hotspot_HotSpotJVMCIMetaAccessContext), "must be loaded earlier");
  assert(SystemDictionary::WK_KLASS_ENUM_NAME(jdk_vm_ci_hotspot_HotSpotConstantPool) <
         SystemDictionary::WK_KLASS_ENUM_NAME(jdk_vm_ci_hotspot_HotSpotJVMCIMetaAccessContext), "must be loaded earlier");
  assert(SystemDictionary::WK_KLASS_ENUM_NAME(jdk_vm_ci_hotspot_HotSpotResolvedObjectTypeImpl) <
         SystemDictionary::WK_KLASS_ENUM_NAME(jdk_vm_ci_hotspot_HotSpotJVMCIMetaAccessContext), "must be loaded earlier");

  if (HotSpotJVMCIMetaAccessContext::klass() == NULL ||
      !HotSpotJVMCIMetaAccessContext::klass()->is_linked()) {
    // Nothing could be registered yet
    return;
  }

  // WeakReference<HotSpotJVMCIMetaAccessContext>[]
  objArrayOop allContexts = HotSpotJVMCIMetaAccessContext::allContexts();
  if (allContexts == NULL) {
    return;
  }

  // These must be loaded at this point but the linking state doesn't matter.
  assert(SystemDictionary::HotSpotResolvedJavaMethodImpl_klass() != NULL, "must be loaded");
  assert(SystemDictionary::HotSpotConstantPool_klass() != NULL, "must be loaded");
  assert(SystemDictionary::HotSpotResolvedObjectTypeImpl_klass() != NULL, "must be loaded");

  for (int i = 0; i < allContexts->length(); i++) {
    oop ref = allContexts->obj_at(i);
    if (ref != NULL) {
      oop referent = java_lang_ref_Reference::referent(ref);
      if (referent != NULL) {
        // Chunked Object[] with last element pointing to next chunk
        objArrayOop metadataRoots = HotSpotJVMCIMetaAccessContext::metadataRoots(referent);
        while (metadataRoots != NULL) {
          for (int typeIndex = 0; typeIndex < metadataRoots->length() - 1; typeIndex++) {
            oop reference = metadataRoots->obj_at(typeIndex);
            if (reference == NULL) {
              continue;
            }
            oop metadataRoot = java_lang_ref_Reference::referent(reference);
            if (metadataRoot == NULL) {
              continue;
            }
            if (metadataRoot->is_a(SystemDictionary::HotSpotResolvedJavaMethodImpl_klass())) {
              Method* method = CompilerToVM::asMethod(metadataRoot);
              f(method);
            } else if (metadataRoot->is_a(SystemDictionary::HotSpotConstantPool_klass())) {
              ConstantPool* constantPool = CompilerToVM::asConstantPool(metadataRoot);
              f(constantPool);
            } else if (metadataRoot->is_a(SystemDictionary::HotSpotResolvedObjectTypeImpl_klass())) {
              Klass* klass = CompilerToVM::asKlass(metadataRoot);
              f(klass);
            } else {
              metadataRoot->print();
              ShouldNotReachHere();
            }
          }
          metadataRoots = (objArrayOop)metadataRoots->obj_at(metadataRoots->length() - 1);
          assert(metadataRoots == NULL || metadataRoots->is_objArray(), "wrong type");
        }
      }
    }
  }
}

// private static void CompilerToVM.registerNatives()
JVM_ENTRY(void, JVM_RegisterJVMCINatives(JNIEnv *env, jclass c2vmClass))
  if (!EnableJVMCI) {
    THROW_MSG(vmSymbols::java_lang_InternalError(), "JVMCI is not enabled");
  }

#ifdef _LP64
#ifndef SPARC
  uintptr_t heap_end = (uintptr_t) Universe::heap()->reserved_region().end();
  uintptr_t allocation_end = heap_end + ((uintptr_t)16) * 1024 * 1024 * 1024;
  guarantee(heap_end < allocation_end, "heap end too close to end of address space (might lead to erroneous TLAB allocations)");
#endif // !SPARC
#else
  fatal("check TLAB allocation code for address space conflicts");
#endif // _LP64

  JVMCIRuntime::initialize_well_known_classes(CHECK);

  {
    ThreadToNativeFromVM trans(thread);
    env->RegisterNatives(c2vmClass, CompilerToVM::methods, CompilerToVM::methods_count());
  }
JVM_END

void JVMCIRuntime::shutdown(TRAPS) {
  if (_HotSpotJVMCIRuntime_instance != NULL) {
    _shutdown_called = true;
    HandleMark hm(THREAD);
    Handle receiver = get_HotSpotJVMCIRuntime(CHECK);
    JavaValue result(T_VOID);
    JavaCallArguments args;
    args.push_oop(receiver);
    JavaCalls::call_special(&result, receiver->klass(), vmSymbols::shutdown_method_name(), vmSymbols::void_method_signature(), &args, CHECK);
  }
}

CompLevel JVMCIRuntime::adjust_comp_level_inner(const methodHandle& method, bool is_osr, CompLevel level, JavaThread* thread) {
  JVMCICompiler* compiler = JVMCICompiler::instance(false, thread);
  if (compiler != NULL && compiler->is_bootstrapping()) {
    return level;
  }
  if (!is_HotSpotJVMCIRuntime_initialized() || _comp_level_adjustment == JVMCIRuntime::none) {
    // JVMCI cannot participate in compilation scheduling until
    // JVMCI is initialized and indicates it wants to participate.
    return level;
  }

#define CHECK_RETURN THREAD); \
  if (HAS_PENDING_EXCEPTION) { \
    Handle exception(THREAD, PENDING_EXCEPTION); \
    CLEAR_PENDING_EXCEPTION; \
  \
    if (exception->is_a(SystemDictionary::ThreadDeath_klass())) { \
      /* In the special case of ThreadDeath, we need to reset the */ \
      /* pending async exception so that it is propagated.        */ \
      thread->set_pending_async_exception(exception()); \
      return level; \
    } \
    tty->print("Uncaught exception while adjusting compilation level: "); \
    java_lang_Throwable::print(exception(), tty); \
    tty->cr(); \
    java_lang_Throwable::print_stack_trace(exception, tty); \
    if (HAS_PENDING_EXCEPTION) { \
      CLEAR_PENDING_EXCEPTION; \
    } \
    return level; \
  } \
  (void)(0


  Thread* THREAD = thread;
  HandleMark hm;
  Handle receiver = JVMCIRuntime::get_HotSpotJVMCIRuntime(CHECK_RETURN);
  Handle name;
  Handle sig;
  if (_comp_level_adjustment == JVMCIRuntime::by_full_signature) {
    name = java_lang_String::create_from_symbol(method->name(), CHECK_RETURN);
    sig = java_lang_String::create_from_symbol(method->signature(), CHECK_RETURN);
  } else {
    name = Handle();
    sig = Handle();
  }

  JavaValue result(T_INT);
  JavaCallArguments args;
  args.push_oop(receiver);
  args.push_oop(Handle(THREAD, method->method_holder()->java_mirror()));
  args.push_oop(name);
  args.push_oop(sig);
  args.push_int(is_osr);
  args.push_int(level);
  JavaCalls::call_special(&result, receiver->klass(), vmSymbols::adjustCompilationLevel_name(),
                          vmSymbols::adjustCompilationLevel_signature(), &args, CHECK_RETURN);

  int comp_level = result.get_jint();
  if (comp_level < CompLevel_none || comp_level > CompLevel_full_optimization) {
    assert(false, "compilation level out of bounds");
    return level;
  }
  return (CompLevel) comp_level;
#undef CHECK_RETURN
}

void JVMCIRuntime::bootstrap_finished(TRAPS) {
  HandleMark hm(THREAD);
  Handle receiver = get_HotSpotJVMCIRuntime(CHECK);
  JavaValue result(T_VOID);
  JavaCallArguments args;
  args.push_oop(receiver);
  JavaCalls::call_special(&result, receiver->klass(), vmSymbols::bootstrapFinished_method_name(), vmSymbols::void_method_signature(), &args, CHECK);
}
