/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmClasses.hpp"
#include "compiler/compileBroker.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/oopStorage.inline.hpp"
#include "jvmci/jniAccessMark.inline.hpp"
#include "jvmci/jvmciCompilerToVM.hpp"
#include "jvmci/jvmciCodeInstaller.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "jvmci/metadataHandles.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/oopFactory.hpp"
#include "memory/universe.hpp"
#include "oops/constantPool.inline.hpp"
#include "oops/klass.inline.hpp"
#include "oops/method.inline.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomic.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/mutex.hpp"
#include "runtime/reflectionUtils.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/synchronizer.hpp"
#if INCLUDE_G1GC
#include "gc/g1/g1BarrierSetRuntime.hpp"
#endif // INCLUDE_G1GC

// Simple helper to see if the caller of a runtime stub which
// entered the VM has been deoptimized

static bool caller_is_deopted() {
  JavaThread* thread = JavaThread::current();
  RegisterMap reg_map(thread,
                      RegisterMap::UpdateMap::skip,
                      RegisterMap::ProcessFrames::include,
                      RegisterMap::WalkContinuation::skip);
  frame runtime_frame = thread->last_frame();
  frame caller_frame = runtime_frame.sender(&reg_map);
  assert(caller_frame.is_compiled_frame(), "must be compiled");
  return caller_frame.is_deoptimized_frame();
}

// Stress deoptimization
static void deopt_caller() {
  if ( !caller_is_deopted()) {
    JavaThread* thread = JavaThread::current();
    RegisterMap reg_map(thread,
                        RegisterMap::UpdateMap::skip,
                        RegisterMap::ProcessFrames::include,
                        RegisterMap::WalkContinuation::skip);
    frame runtime_frame = thread->last_frame();
    frame caller_frame = runtime_frame.sender(&reg_map);
    Deoptimization::deoptimize_frame(thread, caller_frame.id(), Deoptimization::Reason_constraint);
    assert(caller_is_deopted(), "Must be deoptimized");
  }
}

// Manages a scope for a JVMCI runtime call that attempts a heap allocation.
// If there is a pending nonasync exception upon closing the scope and the runtime
// call is of the variety where allocation failure returns null without an
// exception, the following action is taken:
//   1. The pending nonasync exception is cleared
//   2. null is written to JavaThread::_vm_result
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
      _thread = nullptr;
    }
  }
  ~RetryableAllocationMark() {
    if (_thread != nullptr) {
      _thread->set_in_retryable_allocation(false);
      JavaThread* THREAD = _thread; // For exception macros.
      if (HAS_PENDING_EXCEPTION) {
        oop ex = PENDING_EXCEPTION;
        // Do not clear probable async exceptions.
        CLEAR_PENDING_NONASYNC_EXCEPTION;
        oop retry_oome = Universe::out_of_memory_error_retry();
        if (ex->is_a(retry_oome->klass()) && retry_oome != ex) {
          ResourceMark rm;
          fatal("Unexpected exception in scope of retryable allocation: " INTPTR_FORMAT " of type %s", p2i(ex), ex->klass()->external_name());
        }
        _thread->set_vm_result(nullptr);
      }
    }
  }
};

JRT_BLOCK_ENTRY(void, JVMCIRuntime::new_instance_common(JavaThread* current, Klass* klass, bool null_on_fail))
  JRT_BLOCK;
  assert(klass->is_klass(), "not a class");
  Handle holder(current, klass->klass_holder()); // keep the klass alive
  InstanceKlass* h = InstanceKlass::cast(klass);
  {
    RetryableAllocationMark ram(current, null_on_fail);
    h->check_valid_for_instantiation(true, CHECK);
    oop obj;
    if (null_on_fail) {
      if (!h->is_initialized()) {
        // Cannot re-execute class initialization without side effects
        // so return without attempting the initialization
        return;
      }
    } else {
      // make sure klass is initialized
      h->initialize(CHECK);
    }
    // allocate instance and return via TLS
    obj = h->allocate_instance(CHECK);
    current->set_vm_result(obj);
  }
  JRT_BLOCK_END;
  SharedRuntime::on_slowpath_allocation_exit(current);
JRT_END

JRT_BLOCK_ENTRY(void, JVMCIRuntime::new_array_common(JavaThread* current, Klass* array_klass, jint length, bool null_on_fail))
  JRT_BLOCK;
  // Note: no handle for klass needed since they are not used
  //       anymore after new_objArray() and no GC can happen before.
  //       (This may have to change if this code changes!)
  assert(array_klass->is_klass(), "not a class");
  oop obj;
  if (array_klass->is_typeArray_klass()) {
    BasicType elt_type = TypeArrayKlass::cast(array_klass)->element_type();
    RetryableAllocationMark ram(current, null_on_fail);
    obj = oopFactory::new_typeArray(elt_type, length, CHECK);
  } else {
    Handle holder(current, array_klass->klass_holder()); // keep the klass alive
    Klass* elem_klass = ObjArrayKlass::cast(array_klass)->element_klass();
    RetryableAllocationMark ram(current, null_on_fail);
    obj = oopFactory::new_objArray(elem_klass, length, CHECK);
  }
  // This is pretty rare but this runtime patch is stressful to deoptimization
  // if we deoptimize here so force a deopt to stress the path.
  if (DeoptimizeALot) {
    static int deopts = 0;
    // Alternate between deoptimizing and raising an error (which will also cause a deopt)
    if (deopts++ % 2 == 0) {
      if (null_on_fail) {
        // Drop the allocation
        obj = nullptr;
      } else {
        ResourceMark rm(current);
        THROW(vmSymbols::java_lang_OutOfMemoryError());
      }
    } else {
      deopt_caller();
    }
  }
  current->set_vm_result(obj);
  JRT_BLOCK_END;
  SharedRuntime::on_slowpath_allocation_exit(current);
JRT_END

JRT_ENTRY(void, JVMCIRuntime::new_multi_array_common(JavaThread* current, Klass* klass, int rank, jint* dims, bool null_on_fail))
  assert(klass->is_klass(), "not a class");
  assert(rank >= 1, "rank must be nonzero");
  Handle holder(current, klass->klass_holder()); // keep the klass alive
  RetryableAllocationMark ram(current, null_on_fail);
  oop obj = ArrayKlass::cast(klass)->multi_allocate(rank, dims, CHECK);
  current->set_vm_result(obj);
JRT_END

JRT_ENTRY(void, JVMCIRuntime::dynamic_new_array_common(JavaThread* current, oopDesc* element_mirror, jint length, bool null_on_fail))
  RetryableAllocationMark ram(current, null_on_fail);
  oop obj = Reflection::reflect_new_array(element_mirror, length, CHECK);
  current->set_vm_result(obj);
JRT_END

JRT_ENTRY(void, JVMCIRuntime::dynamic_new_instance_common(JavaThread* current, oopDesc* type_mirror, bool null_on_fail))
  InstanceKlass* klass = InstanceKlass::cast(java_lang_Class::as_Klass(type_mirror));

  if (klass == nullptr) {
    ResourceMark rm(current);
    THROW(vmSymbols::java_lang_InstantiationException());
  }
  RetryableAllocationMark ram(current, null_on_fail);

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
  current->set_vm_result(obj);
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
JRT_ENTRY_NO_ASYNC(static address, exception_handler_for_pc_helper(JavaThread* current, oopDesc* ex, address pc, CompiledMethod*& cm))
  // Reset method handle flag.
  current->set_is_method_handle_return(false);

  Handle exception(current, ex);

  // The frame we rethrow the exception to might not have been processed by the GC yet.
  // The stack watermark barrier takes care of detecting that and ensuring the frame
  // has updated oops.
  StackWatermarkSet::after_unwind(current);

  cm = CodeCache::find_compiled(pc);
  assert(cm != nullptr, "this is not a compiled method");
  // Adjust the pc as needed/
  if (cm->is_deopt_pc(pc)) {
    RegisterMap map(current,
                    RegisterMap::UpdateMap::skip,
                    RegisterMap::ProcessFrames::include,
                    RegisterMap::WalkContinuation::skip);
    frame exception_frame = current->last_frame().sender(&map);
    // if the frame isn't deopted then pc must not correspond to the caller of last_frame
    assert(exception_frame.is_deoptimized_frame(), "must be deopted");
    pc = exception_frame.pc();
  }
  assert(exception.not_null(), "null exceptions should be handled by throw_exception");
  assert(oopDesc::is_oop(exception()), "just checking");
  // Check that exception is a subclass of Throwable
  assert(exception->is_a(vmClasses::Throwable_klass()),
         "Exception not subclass of Throwable");

  // debugging support
  // tracing
  if (log_is_enabled(Info, exceptions)) {
    ResourceMark rm;
    stringStream tempst;
    assert(cm->method() != nullptr, "Unexpected null method()");
    tempst.print("JVMCI compiled method <%s>\n"
                 " at PC" INTPTR_FORMAT " for thread " INTPTR_FORMAT,
                 cm->method()->print_value_string(), p2i(pc), p2i(current));
    Exceptions::log_exception(exception, tempst.as_string());
  }
  // for AbortVMOnException flag
  Exceptions::debug_check_abort(exception);

  // Check the stack guard pages and re-enable them if necessary and there is
  // enough space on the stack to do so.  Use fast exceptions only if the guard
  // pages are enabled.
  bool guard_pages_enabled = current->stack_overflow_state()->reguard_stack_if_needed();

  if (JvmtiExport::can_post_on_exceptions()) {
    // To ensure correct notification of exception catches and throws
    // we have to deoptimize here.  If we attempted to notify the
    // catches and throws during this exception lookup it's possible
    // we could deoptimize on the way out of the VM and end back in
    // the interpreter at the throw site.  This would result in double
    // notifications since the interpreter would also notify about
    // these same catches and throws as it unwound the frame.

    RegisterMap reg_map(current,
                        RegisterMap::UpdateMap::include,
                        RegisterMap::ProcessFrames::include,
                        RegisterMap::WalkContinuation::skip);
    frame stub_frame = current->last_frame();
    frame caller_frame = stub_frame.sender(&reg_map);

    // We don't really want to deoptimize the nmethod itself since we
    // can actually continue in the exception handler ourselves but I
    // don't see an easy way to have the desired effect.
    Deoptimization::deoptimize_frame(current, caller_frame.id(), Deoptimization::Reason_constraint);
    assert(caller_is_deopted(), "Must be deoptimized");

    return SharedRuntime::deopt_blob()->unpack_with_exception_in_tls();
  }

  // ExceptionCache is used only for exceptions at call sites and not for implicit exceptions
  if (guard_pages_enabled) {
    address fast_continuation = cm->handler_for_exception_and_pc(exception, pc);
    if (fast_continuation != nullptr) {
      // Set flag if return address is a method handle call site.
      current->set_is_method_handle_return(cm->is_method_handle_return(pc));
      return fast_continuation;
    }
  }

  // If the stack guard pages are enabled, check whether there is a handler in
  // the current method.  Otherwise (guard pages disabled), force an unwind and
  // skip the exception cache update (i.e., just leave continuation==nullptr).
  address continuation = nullptr;
  if (guard_pages_enabled) {

    // New exception handling mechanism can support inlined methods
    // with exception handlers since the mappings are from PC to PC

    // Clear out the exception oop and pc since looking up an
    // exception handler can cause class loading, which might throw an
    // exception and those fields are expected to be clear during
    // normal bytecode execution.
    current->clear_exception_oop_and_pc();

    bool recursive_exception = false;
    continuation = SharedRuntime::compute_compiled_exc_handler(cm, pc, exception, false, false, recursive_exception);
    // If an exception was thrown during exception dispatch, the exception oop may have changed
    current->set_exception_oop(exception());
    current->set_exception_pc(pc);

    // The exception cache is used only for non-implicit exceptions
    // Update the exception cache only when another exception did
    // occur during the computation of the compiled exception handler
    // (e.g., when loading the class of the catch type).
    // Checking for exception oop equality is not
    // sufficient because some exceptions are pre-allocated and reused.
    if (continuation != nullptr && !recursive_exception && !SharedRuntime::deopt_blob()->contains(continuation)) {
      cm->add_handler_for_exception_and_pc(exception, pc, continuation);
    }
  }

  // Set flag if return address is a method handle call site.
  current->set_is_method_handle_return(cm->is_method_handle_return(pc));

  if (log_is_enabled(Info, exceptions)) {
    ResourceMark rm;
    log_info(exceptions)("Thread " PTR_FORMAT " continuing at PC " PTR_FORMAT
                         " for exception thrown at PC " PTR_FORMAT,
                         p2i(current), p2i(continuation), p2i(pc));
  }

  return continuation;
JRT_END

// Enter this method from compiled code only if there is a Java exception handler
// in the method handling the exception.
// We are entering here from exception stub. We don't do a normal VM transition here.
// We do it in a helper. This is so we can check to see if the nmethod we have just
// searched for an exception handler has been deoptimized in the meantime.
address JVMCIRuntime::exception_handler_for_pc(JavaThread* current) {
  oop exception = current->exception_oop();
  address pc = current->exception_pc();
  // Still in Java mode
  DEBUG_ONLY(NoHandleMark nhm);
  CompiledMethod* cm = nullptr;
  address continuation = nullptr;
  {
    // Enter VM mode by calling the helper
    ResetNoHandleMark rnhm;
    continuation = exception_handler_for_pc_helper(current, exception, pc, cm);
  }
  // Back in JAVA, use no oops DON'T safepoint

  // Now check to see if the compiled method we were called from is now deoptimized.
  // If so we must return to the deopt blob and deoptimize the nmethod
  if (cm != nullptr && caller_is_deopted()) {
    continuation = SharedRuntime::deopt_blob()->unpack_with_exception_in_tls();
  }

  assert(continuation != nullptr, "no handler found");
  return continuation;
}

JRT_BLOCK_ENTRY(void, JVMCIRuntime::monitorenter(JavaThread* current, oopDesc* obj, BasicLock* lock))
  SharedRuntime::monitor_enter_helper(obj, lock, current);
JRT_END

JRT_LEAF(void, JVMCIRuntime::monitorexit(JavaThread* current, oopDesc* obj, BasicLock* lock))
  assert(current == JavaThread::current(), "pre-condition");
  assert(current->last_Java_sp(), "last_Java_sp must be set");
  assert(oopDesc::is_oop(obj), "invalid lock object pointer dected");
  SharedRuntime::monitor_exit_helper(obj, lock, current);
JRT_END

// Object.notify() fast path, caller does slow path
JRT_LEAF(jboolean, JVMCIRuntime::object_notify(JavaThread* current, oopDesc* obj))
  assert(current == JavaThread::current(), "pre-condition");

  // Very few notify/notifyAll operations find any threads on the waitset, so
  // the dominant fast-path is to simply return.
  // Relatedly, it's critical that notify/notifyAll be fast in order to
  // reduce lock hold times.
  if (!SafepointSynchronize::is_synchronizing()) {
    if (ObjectSynchronizer::quick_notify(obj, current, false)) {
      return true;
    }
  }
  return false; // caller must perform slow path

JRT_END

// Object.notifyAll() fast path, caller does slow path
JRT_LEAF(jboolean, JVMCIRuntime::object_notifyAll(JavaThread* current, oopDesc* obj))
  assert(current == JavaThread::current(), "pre-condition");

  if (!SafepointSynchronize::is_synchronizing() ) {
    if (ObjectSynchronizer::quick_notify(obj, current, true)) {
      return true;
    }
  }
  return false; // caller must perform slow path

JRT_END

JRT_BLOCK_ENTRY(int, JVMCIRuntime::throw_and_post_jvmti_exception(JavaThread* current, const char* exception, const char* message))
  JRT_BLOCK;
  TempNewSymbol symbol = SymbolTable::new_symbol(exception);
  SharedRuntime::throw_and_post_jvmti_exception(current, symbol, message);
  JRT_BLOCK_END;
  return caller_is_deopted();
JRT_END

JRT_BLOCK_ENTRY(int, JVMCIRuntime::throw_klass_external_name_exception(JavaThread* current, const char* exception, Klass* klass))
  JRT_BLOCK;
  ResourceMark rm(current);
  TempNewSymbol symbol = SymbolTable::new_symbol(exception);
  SharedRuntime::throw_and_post_jvmti_exception(current, symbol, klass->external_name());
  JRT_BLOCK_END;
  return caller_is_deopted();
JRT_END

JRT_BLOCK_ENTRY(int, JVMCIRuntime::throw_class_cast_exception(JavaThread* current, const char* exception, Klass* caster_klass, Klass* target_klass))
  JRT_BLOCK;
  ResourceMark rm(current);
  const char* message = SharedRuntime::generate_class_cast_message(caster_klass, target_klass);
  TempNewSymbol symbol = SymbolTable::new_symbol(exception);
  SharedRuntime::throw_and_post_jvmti_exception(current, symbol, message);
  JRT_BLOCK_END;
  return caller_is_deopted();
JRT_END

class ArgumentPusher : public SignatureIterator {
 protected:
  JavaCallArguments*  _jca;
  jlong _argument;
  bool _pushed;

  jlong next_arg() {
    guarantee(!_pushed, "one argument");
    _pushed = true;
    return _argument;
  }

  float next_float() {
    guarantee(!_pushed, "one argument");
    _pushed = true;
    jvalue v;
    v.i = (jint) _argument;
    return v.f;
  }

  double next_double() {
    guarantee(!_pushed, "one argument");
    _pushed = true;
    jvalue v;
    v.j = _argument;
    return v.d;
  }

  Handle next_object() {
    guarantee(!_pushed, "one argument");
    _pushed = true;
    return Handle(Thread::current(), cast_to_oop(_argument));
  }

 public:
  ArgumentPusher(Symbol* signature, JavaCallArguments*  jca, jlong argument) : SignatureIterator(signature) {
    this->_return_type = T_ILLEGAL;
    _jca = jca;
    _argument = argument;
    _pushed = false;
    do_parameters_on(this);
  }

  void do_type(BasicType type) {
    switch (type) {
      case T_OBJECT:
      case T_ARRAY:   _jca->push_oop(next_object());         break;
      case T_BOOLEAN: _jca->push_int((jboolean) next_arg()); break;
      case T_CHAR:    _jca->push_int((jchar) next_arg());    break;
      case T_SHORT:   _jca->push_int((jint)  next_arg());    break;
      case T_BYTE:    _jca->push_int((jbyte) next_arg());    break;
      case T_INT:     _jca->push_int((jint)  next_arg());    break;
      case T_LONG:    _jca->push_long((jlong) next_arg());   break;
      case T_FLOAT:   _jca->push_float(next_float());        break;
      case T_DOUBLE:  _jca->push_double(next_double());      break;
      default:        fatal("Unexpected type %s", type2name(type));
    }
  }
};


JRT_ENTRY(jlong, JVMCIRuntime::invoke_static_method_one_arg(JavaThread* current, Method* method, jlong argument))
  ResourceMark rm;
  HandleMark hm(current);

  methodHandle mh(current, method);
  if (mh->size_of_parameters() > 1 && !mh->is_static()) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "Invoked method must be static and take at most one argument");
  }

  Symbol* signature = mh->signature();
  JavaCallArguments jca(mh->size_of_parameters());
  ArgumentPusher jap(signature, &jca, argument);
  BasicType return_type = jap.return_type();
  JavaValue result(return_type);
  JavaCalls::call(&result, mh, &jca, CHECK_0);

  if (return_type == T_VOID) {
    return 0;
  } else if (return_type == T_OBJECT || return_type == T_ARRAY) {
    current->set_vm_result(result.get_oop());
    return 0;
  } else {
    jvalue *value = (jvalue *) result.get_value_addr();
    // Narrow the value down if required (Important on big endian machines)
    switch (return_type) {
      case T_BOOLEAN:
        return (jboolean) value->i;
      case T_BYTE:
        return (jbyte) value->i;
      case T_CHAR:
        return (jchar) value->i;
      case T_SHORT:
        return (jshort) value->i;
      case T_INT:
      case T_FLOAT:
        return value->i;
      case T_LONG:
      case T_DOUBLE:
        return value->j;
      default:
        fatal("Unexpected type %s", type2name(return_type));
        return 0;
    }
  }
JRT_END

JRT_LEAF(void, JVMCIRuntime::log_object(JavaThread* thread, oopDesc* obj, bool as_string, bool newline))
  ttyLocker ttyl;

  if (obj == nullptr) {
    tty->print("null");
  } else if (oopDesc::is_oop_or_null(obj, true) && (!as_string || !java_lang_String::is_instance(obj))) {
    if (oopDesc::is_oop_or_null(obj, true)) {
      char buf[O_BUFLEN];
      tty->print("%s@" INTPTR_FORMAT, obj->klass()->name()->as_C_string(buf, O_BUFLEN), p2i(obj));
    } else {
      tty->print(INTPTR_FORMAT, p2i(obj));
    }
  } else {
    ResourceMark rm;
    assert(obj != nullptr && java_lang_String::is_instance(obj), "must be");
    char *buf = java_lang_String::as_utf8_string(obj);
    tty->print_raw(buf);
  }
  if (newline) {
    tty->cr();
  }
JRT_END

#if INCLUDE_G1GC

void JVMCIRuntime::write_barrier_pre(JavaThread* thread, oopDesc* obj) {
  G1BarrierSetRuntime::write_ref_field_pre_entry(obj, thread);
}

void JVMCIRuntime::write_barrier_post(JavaThread* thread, volatile CardValue* card_addr) {
  G1BarrierSetRuntime::write_ref_field_post_entry(card_addr, thread);
}

#endif // INCLUDE_G1GC

JRT_LEAF(jboolean, JVMCIRuntime::validate_object(JavaThread* thread, oopDesc* parent, oopDesc* child))
  bool ret = true;
  if(!Universe::heap()->is_in(parent)) {
    tty->print_cr("Parent Object " INTPTR_FORMAT " not in heap", p2i(parent));
    parent->print();
    ret=false;
  }
  if(!Universe::heap()->is_in(child)) {
    tty->print_cr("Child Object " INTPTR_FORMAT " not in heap", p2i(child));
    child->print();
    ret=false;
  }
  return (jint)ret;
JRT_END

JRT_ENTRY(void, JVMCIRuntime::vm_error(JavaThread* current, jlong where, jlong format, jlong value))
  ResourceMark rm(current);
  const char *error_msg = where == 0L ? "<internal JVMCI error>" : (char*) (address) where;
  char *detail_msg = nullptr;
  if (format != 0L) {
    const char* buf = (char*) (address) format;
    size_t detail_msg_length = strlen(buf) * 2;
    detail_msg = (char *) NEW_RESOURCE_ARRAY(u_char, detail_msg_length);
    jio_snprintf(detail_msg, detail_msg_length, buf, value);
  }
  report_vm_error(__FILE__, __LINE__, error_msg, "%s", detail_msg);
JRT_END

JRT_LEAF(oopDesc*, JVMCIRuntime::load_and_clear_exception(JavaThread* thread))
  oop exception = thread->exception_oop();
  assert(exception != nullptr, "npe");
  thread->set_exception_oop(nullptr);
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
      oop obj = cast_to_oop(p);
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
    if (buf != nullptr) {
      fatal(buf, v1, v2, v3);
    } else {
      fatal("<anonymous error>");
    }
  } else if (buf != nullptr) {
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

JRT_ENTRY(jint, JVMCIRuntime::identity_hash_code(JavaThread* current, oopDesc* obj))
  return (jint) obj->identity_hash();
JRT_END

JRT_ENTRY(jint, JVMCIRuntime::test_deoptimize_call_int(JavaThread* current, int value))
  deopt_caller();
  return (jint) value;
JRT_END


// Implementation of JVMCI.initializeRuntime()
// When called from libjvmci, `libjvmciOrHotspotEnv` is a libjvmci env so use JVM_ENTRY_NO_ENV.
JVM_ENTRY_NO_ENV(jobject, JVM_GetJVMCIRuntime(JNIEnv *libjvmciOrHotspotEnv, jclass c))
  JVMCIENV_FROM_JNI(thread, libjvmciOrHotspotEnv);
  if (!EnableJVMCI) {
    JVMCI_THROW_MSG_NULL(InternalError, "JVMCI is not enabled");
  }
  JVMCIENV->runtime()->initialize_HotSpotJVMCIRuntime(JVMCI_CHECK_NULL);
  JVMCIObject runtime = JVMCIENV->runtime()->get_HotSpotJVMCIRuntime(JVMCI_CHECK_NULL);
  return JVMCIENV->get_jobject(runtime);
JVM_END

// Implementation of Services.readSystemPropertiesInfo(int[] offsets)
// When called from libjvmci, `env` is a libjvmci env so use JVM_ENTRY_NO_ENV.
JVM_ENTRY_NO_ENV(jlong, JVM_ReadSystemPropertiesInfo(JNIEnv *env, jclass c, jintArray offsets_handle))
  JVMCIENV_FROM_JNI(thread, env);
  if (!EnableJVMCI) {
    JVMCI_THROW_MSG_0(InternalError, "JVMCI is not enabled");
  }
  JVMCIPrimitiveArray offsets = JVMCIENV->wrap(offsets_handle);
  JVMCIENV->put_int_at(offsets, 0, SystemProperty::next_offset_in_bytes());
  JVMCIENV->put_int_at(offsets, 1, SystemProperty::key_offset_in_bytes());
  JVMCIENV->put_int_at(offsets, 2, PathString::value_offset_in_bytes());

  return (jlong) Arguments::system_properties();
JVM_END


void JVMCIRuntime::call_getCompiler(TRAPS) {
  JVMCIENV_FROM_THREAD(THREAD);
  JVMCIENV->check_init(CHECK);
  JVMCIObject jvmciRuntime = JVMCIRuntime::get_HotSpotJVMCIRuntime(JVMCI_CHECK);
  initialize(JVMCI_CHECK);
  JVMCIENV->call_HotSpotJVMCIRuntime_getCompiler(jvmciRuntime, JVMCI_CHECK);
}

void JVMCINMethodData::initialize(int nmethod_mirror_index,
                                  int nmethod_entry_patch_offset,
                                  const char* nmethod_mirror_name,
                                  FailedSpeculation** failed_speculations)
{
  _failed_speculations = failed_speculations;
  _nmethod_mirror_index = nmethod_mirror_index;
  _nmethod_entry_patch_offset = nmethod_entry_patch_offset;
  if (nmethod_mirror_name != nullptr) {
    _has_name = true;
    char* dest = (char*) name();
    strcpy(dest, nmethod_mirror_name);
  } else {
    _has_name = false;
  }
}

void JVMCINMethodData::copy(JVMCINMethodData* data) {
  initialize(data->_nmethod_mirror_index, data->_nmethod_entry_patch_offset, data->name(), data->_failed_speculations);
}

void JVMCINMethodData::add_failed_speculation(nmethod* nm, jlong speculation) {
  jlong index = speculation >> JVMCINMethodData::SPECULATION_LENGTH_BITS;
  guarantee(index >= 0 && index <= max_jint, "Encoded JVMCI speculation index is not a positive Java int: " INTPTR_FORMAT, index);
  int length = speculation & JVMCINMethodData::SPECULATION_LENGTH_MASK;
  if (index + length > (uint) nm->speculations_size()) {
    fatal(INTPTR_FORMAT "[index: " JLONG_FORMAT ", length: %d out of bounds wrt encoded speculations of length %u", speculation, index, length, nm->speculations_size());
  }
  address data = nm->speculations_begin() + index;
  FailedSpeculation::add_failed_speculation(nm, _failed_speculations, data, length);
}

oop JVMCINMethodData::get_nmethod_mirror(nmethod* nm, bool phantom_ref) {
  if (_nmethod_mirror_index == -1) {
    return nullptr;
  }
  if (phantom_ref) {
    return nm->oop_at_phantom(_nmethod_mirror_index);
  } else {
    return nm->oop_at(_nmethod_mirror_index);
  }
}

void JVMCINMethodData::set_nmethod_mirror(nmethod* nm, oop new_mirror) {
  guarantee(_nmethod_mirror_index != -1, "cannot set JVMCI mirror for nmethod");
  oop* addr = nm->oop_addr_at(_nmethod_mirror_index);
  guarantee(new_mirror != nullptr, "use clear_nmethod_mirror to clear the mirror");
  guarantee(*addr == nullptr, "cannot overwrite non-null mirror");

  *addr = new_mirror;

  // Since we've patched some oops in the nmethod,
  // (re)register it with the heap.
  MutexLocker ml(CodeCache_lock, Mutex::_no_safepoint_check_flag);
  Universe::heap()->register_nmethod(nm);
}

void JVMCINMethodData::invalidate_nmethod_mirror(nmethod* nm) {
  oop nmethod_mirror = get_nmethod_mirror(nm, /* phantom_ref */ false);
  if (nmethod_mirror == nullptr) {
    return;
  }

  // Update the values in the mirror if it still refers to nm.
  // We cannot use JVMCIObject to wrap the mirror as this is called
  // during GC, forbidding the creation of JNIHandles.
  JVMCIEnv* jvmciEnv = nullptr;
  nmethod* current = (nmethod*) HotSpotJVMCI::InstalledCode::address(jvmciEnv, nmethod_mirror);
  if (nm == current) {
    if (nm->is_unloading()) {
      // Break the link from the mirror to nm such that
      // future invocations via the mirror will result in
      // an InvalidInstalledCodeException.
      HotSpotJVMCI::InstalledCode::set_address(jvmciEnv, nmethod_mirror, 0);
      HotSpotJVMCI::InstalledCode::set_entryPoint(jvmciEnv, nmethod_mirror, 0);
      HotSpotJVMCI::HotSpotInstalledCode::set_codeStart(jvmciEnv, nmethod_mirror, 0);
    } else if (nm->is_not_entrant()) {
      // Zero the entry point so any new invocation will fail but keep
      // the address link around that so that existing activations can
      // be deoptimized via the mirror (i.e. JVMCIEnv::invalidate_installed_code).
      HotSpotJVMCI::InstalledCode::set_entryPoint(jvmciEnv, nmethod_mirror, 0);
      HotSpotJVMCI::HotSpotInstalledCode::set_codeStart(jvmciEnv, nmethod_mirror, 0);
    }
  }

  if (_nmethod_mirror_index != -1 && nm->is_unloading()) {
    // Drop the reference to the nmethod mirror object but don't clear the actual oop reference.  Otherwise
    // it would appear that the nmethod didn't need to be unloaded in the first place.
    _nmethod_mirror_index = -1;
  }
}

// Handles to objects in the Hotspot heap.
static OopStorage* object_handles() {
  return Universe::vm_global();
}

jlong JVMCIRuntime::make_oop_handle(const Handle& obj) {
  assert(!Universe::heap()->is_gc_active(), "can't extend the root set during GC");
  assert(oopDesc::is_oop(obj()), "not an oop");

  oop* ptr = OopHandle(object_handles(), obj()).ptr_raw();
  MutexLocker ml(_lock);
  _oop_handles.append(ptr);
  return reinterpret_cast<jlong>(ptr);
}

#ifdef ASSERT
bool JVMCIRuntime::is_oop_handle(jlong handle) {
  const oop* ptr = (oop*) handle;
  return object_handles()->allocation_status(ptr) == OopStorage::ALLOCATED_ENTRY;
}
#endif

int JVMCIRuntime::release_and_clear_oop_handles() {
  guarantee(_num_attached_threads == cannot_be_attached, "only call during JVMCI runtime shutdown");
  int released = release_cleared_oop_handles();
  if (_oop_handles.length() != 0) {
    for (int i = 0; i < _oop_handles.length(); i++) {
      oop* oop_ptr = _oop_handles.at(i);
      guarantee(oop_ptr != nullptr, "release_cleared_oop_handles left null entry in _oop_handles");
      guarantee(*oop_ptr != nullptr, "unexpected cleared handle");
      // Satisfy OopHandles::release precondition that all
      // handles being released are null.
      NativeAccess<>::oop_store(oop_ptr, (oop) nullptr);
    }

    // Do the bulk release
    object_handles()->release(_oop_handles.adr_at(0), _oop_handles.length());
    released += _oop_handles.length();
  }
  _oop_handles.clear();
  return released;
}

static bool is_referent_non_null(oop* handle) {
  return handle != nullptr && *handle != nullptr;
}

// Swaps the elements in `array` at index `a` and index `b`
static void swap(GrowableArray<oop*>* array, int a, int b) {
  oop* tmp = array->at(a);
  array->at_put(a, array->at(b));
  array->at_put(b, tmp);
}

int JVMCIRuntime::release_cleared_oop_handles() {
  // Despite this lock, it's possible for another thread
  // to clear a handle's referent concurrently (e.g., a thread
  // executing IndirectHotSpotObjectConstantImpl.clear()).
  // This is benign - it means there can still be cleared
  // handles in _oop_handles when this method returns.
  MutexLocker ml(_lock);

  int next = 0;
  if (_oop_handles.length() != 0) {
    // Key for _oop_handles contents in example below:
    //   H: handle with non-null referent
    //   h: handle with clear (i.e., null) referent
    //   -: null entry

    // Shuffle all handles with non-null referents to the front of the list
    // Example: Before: 0HHh-Hh-
    //           After: HHHh--h-
    for (int i = 0; i < _oop_handles.length(); i++) {
      oop* handle = _oop_handles.at(i);
      if (is_referent_non_null(handle)) {
        if (i != next && !is_referent_non_null(_oop_handles.at(next))) {
          // Swap elements at index `next` and `i`
          swap(&_oop_handles, next, i);
        }
        next++;
      }
    }

    // `next` is now the index of the first null handle or handle with a null referent
    int num_alive = next;

    // Shuffle all null handles to the end of the list
    // Example: Before: HHHh--h-
    //           After: HHHhh---
    //       num_alive: 3
    for (int i = next; i < _oop_handles.length(); i++) {
      oop* handle = _oop_handles.at(i);
      if (handle != nullptr) {
        if (i != next && _oop_handles.at(next) == nullptr) {
          // Swap elements at index `next` and `i`
          swap(&_oop_handles, next, i);
        }
        next++;
      }
    }
    if (next != num_alive) {
      int to_release = next - num_alive;

      // `next` is now the index of the first null handle
      // Example: to_release: 2

      // Bulk release the handles with a null referent
      object_handles()->release(_oop_handles.adr_at(num_alive), to_release);

      // Truncate oop handles to only those with a non-null referent
      JVMCI_event_2("compacted oop handles in JVMCI runtime %d from %d to %d", _id, _oop_handles.length(), num_alive);
      _oop_handles.trunc_to(num_alive);
      // Example: HHH

      return to_release;
    }
  }
  return 0;
}

jmetadata JVMCIRuntime::allocate_handle(const methodHandle& handle) {
  MutexLocker ml(_lock);
  return _metadata_handles->allocate_handle(handle);
}

jmetadata JVMCIRuntime::allocate_handle(const constantPoolHandle& handle) {
  MutexLocker ml(_lock);
  return _metadata_handles->allocate_handle(handle);
}

void JVMCIRuntime::release_handle(jmetadata handle) {
  MutexLocker ml(_lock);
  _metadata_handles->chain_free_list(handle);
}

// Function for redirecting shared library JavaVM output to tty
static void _log(const char* buf, size_t count) {
  tty->write((char*) buf, count);
}

// Function for redirecting shared library JavaVM fatal error data to a log file.
// The log file is opened on first call to this function.
static void _fatal_log(const char* buf, size_t count) {
  JVMCI::fatal_log(buf, count);
}

// Function for shared library JavaVM to flush tty
static void _flush_log() {
  tty->flush();
}

// Function for shared library JavaVM to exit HotSpot on a fatal error
static void _fatal() {
  Thread* thread = Thread::current_or_null_safe();
  if (thread != nullptr && thread->is_Java_thread()) {
    JavaThread* jthread = JavaThread::cast(thread);
    JVMCIRuntime* runtime = jthread->libjvmci_runtime();
    if (runtime != nullptr) {
      int javaVM_id = runtime->get_shared_library_javavm_id();
      fatal("Fatal error in JVMCI shared library JavaVM[%d] owned by JVMCI runtime %d", javaVM_id, runtime->id());
    }
  }
  intx current_thread_id = os::current_thread_id();
  fatal("thread " INTX_FORMAT ": Fatal error in JVMCI shared library", current_thread_id);
}

JVMCIRuntime::JVMCIRuntime(JVMCIRuntime* next, int id, bool for_compile_broker) :
  _init_state(uninitialized),
  _shared_library_javavm(nullptr),
  _shared_library_javavm_id(0),
  _id(id),
  _next(next),
  _metadata_handles(new MetadataHandles()),
  _oop_handles(100, mtJVMCI),
  _num_attached_threads(0),
  _for_compile_broker(for_compile_broker)
{
  if (id == -1) {
    _lock = JVMCIRuntime_lock;
  } else {
    stringStream lock_name;
    lock_name.print("%s@%d", JVMCIRuntime_lock->name(), id);
    Mutex::Rank lock_rank = DEBUG_ONLY(JVMCIRuntime_lock->rank()) NOT_DEBUG(Mutex::safepoint);
    _lock = new PaddedMonitor(lock_rank, lock_name.as_string(/*c_heap*/true));
  }
  JVMCI_event_1("created new %s JVMCI runtime %d (" PTR_FORMAT ")",
      id == -1 ? "Java" : for_compile_broker ? "CompileBroker" : "Compiler", id, p2i(this));
}

JVMCIRuntime* JVMCIRuntime::select_runtime_in_shutdown(JavaThread* thread) {
  assert(JVMCI_lock->owner() == thread, "must be");
  // When shutting down, use the first available runtime.
  for (JVMCIRuntime* runtime = JVMCI::_compiler_runtimes; runtime != nullptr; runtime = runtime->_next) {
    if (runtime->_num_attached_threads != cannot_be_attached) {
      runtime->pre_attach_thread(thread);
      JVMCI_event_1("using pre-existing JVMCI runtime %d in shutdown", runtime->id());
      return runtime;
    }
  }
  // Lazily initialize JVMCI::_shutdown_compiler_runtime. Safe to
  // do here since JVMCI_lock is locked.
  if (JVMCI::_shutdown_compiler_runtime == nullptr) {
    JVMCI::_shutdown_compiler_runtime = new JVMCIRuntime(nullptr, -2, true);
  }
  JVMCIRuntime* runtime = JVMCI::_shutdown_compiler_runtime;
  JVMCI_event_1("using reserved shutdown JVMCI runtime %d", runtime->id());
  return runtime;
}

JVMCIRuntime* JVMCIRuntime::select_runtime(JavaThread* thread, JVMCIRuntime* skip, int* count) {
  assert(JVMCI_lock->owner() == thread, "must be");
  bool for_compile_broker = thread->is_Compiler_thread();
  for (JVMCIRuntime* runtime = JVMCI::_compiler_runtimes; runtime != nullptr; runtime = runtime->_next) {
    if (count != nullptr) {
      (*count)++;
    }
    if (for_compile_broker == runtime->_for_compile_broker) {
      int count = runtime->_num_attached_threads;
      if (count == cannot_be_attached || runtime == skip) {
        // Cannot attach to rt
        continue;
      }
      // If selecting for repacking, ignore a runtime without an existing JavaVM
      if (skip != nullptr && !runtime->has_shared_library_javavm()) {
        continue;
      }

      // Select first runtime with sufficient capacity
      if (count < (int) JVMCIThreadsPerNativeLibraryRuntime) {
        runtime->pre_attach_thread(thread);
        return runtime;
      }
    }
  }
  return nullptr;
}

JVMCIRuntime* JVMCIRuntime::select_or_create_runtime(JavaThread* thread) {
  assert(JVMCI_lock->owner() == thread, "must be");
  int id = 0;
  JVMCIRuntime* runtime;
  if (JVMCI::using_singleton_shared_library_runtime()) {
    runtime = JVMCI::_compiler_runtimes;
    guarantee(runtime != nullptr, "must be");
    while (runtime->_num_attached_threads == cannot_be_attached) {
      // Since there is only a singleton JVMCIRuntime, we
      // need to wait for it to be available for attaching.
      JVMCI_lock->wait();
    }
    runtime->pre_attach_thread(thread);
  } else {
    runtime = select_runtime(thread, nullptr, &id);
  }
  if (runtime == nullptr) {
    runtime = new JVMCIRuntime(JVMCI::_compiler_runtimes, id, thread->is_Compiler_thread());
    JVMCI::_compiler_runtimes = runtime;
    runtime->pre_attach_thread(thread);
  }
  return runtime;
}

JVMCIRuntime* JVMCIRuntime::for_thread(JavaThread* thread) {
  assert(thread->libjvmci_runtime() == nullptr, "must be");
  // Find the runtime with fewest attached threads
  JVMCIRuntime* runtime = nullptr;
  {
    MutexLocker locker(JVMCI_lock);
    runtime = JVMCI::in_shutdown() ? select_runtime_in_shutdown(thread) : select_or_create_runtime(thread);
  }
  runtime->attach_thread(thread);
  return runtime;
}

const char* JVMCIRuntime::attach_shared_library_thread(JavaThread* thread, JavaVM* javaVM) {
  MutexLocker locker(JVMCI_lock);
  for (JVMCIRuntime* runtime = JVMCI::_compiler_runtimes; runtime != nullptr; runtime = runtime->_next) {
    if (runtime->_shared_library_javavm == javaVM) {
      if (runtime->_num_attached_threads == cannot_be_attached) {
        return "Cannot attach to JVMCI runtime that is shutting down";
      }
      runtime->pre_attach_thread(thread);
      runtime->attach_thread(thread);
      return nullptr;
    }
  }
  return "Cannot find JVMCI runtime";
}

void JVMCIRuntime::pre_attach_thread(JavaThread* thread) {
  assert(JVMCI_lock->owner() == thread, "must be");
  _num_attached_threads++;
}

void JVMCIRuntime::attach_thread(JavaThread* thread) {
  assert(thread->libjvmci_runtime() == nullptr, "must be");
  thread->set_libjvmci_runtime(this);
  guarantee(this == JVMCI::_shutdown_compiler_runtime ||
            _num_attached_threads > 0,
            "missing reservation in JVMCI runtime %d: _num_attached_threads=%d", _id, _num_attached_threads);
  JVMCI_event_1("attached to JVMCI runtime %d%s", _id, JVMCI::in_shutdown() ? " [in JVMCI shutdown]" : "");
}

void JVMCIRuntime::repack(JavaThread* thread) {
  JVMCIRuntime* new_runtime = nullptr;
  {
    MutexLocker locker(JVMCI_lock);
    if (JVMCI::using_singleton_shared_library_runtime() || _num_attached_threads != 1 || JVMCI::in_shutdown()) {
      return;
    }
    new_runtime = select_runtime(thread, this, nullptr);
  }
  if (new_runtime != nullptr) {
    JVMCI_event_1("Moving thread from JVMCI runtime %d to JVMCI runtime %d (%d attached)", _id, new_runtime->_id, new_runtime->_num_attached_threads - 1);
    detach_thread(thread, "moving thread to another JVMCI runtime");
    new_runtime->attach_thread(thread);
  }
}

bool JVMCIRuntime::detach_thread(JavaThread* thread, const char* reason, bool can_destroy_javavm) {
  if (this == JVMCI::_shutdown_compiler_runtime || JVMCI::in_shutdown()) {
    // Do minimal work when shutting down JVMCI
    thread->set_libjvmci_runtime(nullptr);
    return false;
  }
  bool should_shutdown;
  bool destroyed_javavm = false;
  {
    MutexLocker locker(JVMCI_lock);
    _num_attached_threads--;
    JVMCI_event_1("detaching from JVMCI runtime %d: %s (%d other threads still attached)", _id, reason, _num_attached_threads);
    should_shutdown = _num_attached_threads == 0 && !JVMCI::in_shutdown();
    if (should_shutdown && !can_destroy_javavm) {
      // If it's not possible to destroy the JavaVM on this thread then the VM must
      // not be shutdown. This can happen when a shared library thread is the last
      // thread to detach from a shared library JavaVM (e.g. GraalServiceThread).
      JVMCI_event_1("Cancelled shut down of JVMCI runtime %d", _id);
      should_shutdown = false;
    }
    if (should_shutdown) {
      // Prevent other threads from attaching to this runtime
      // while it is shutting down and destroying its JavaVM
      _num_attached_threads = cannot_be_attached;
    }
  }
  if (should_shutdown) {
    // Release the JavaVM resources associated with this
    // runtime once there are no threads attached to it.
    shutdown();
    if (can_destroy_javavm) {
      destroyed_javavm = destroy_shared_library_javavm();
      if (destroyed_javavm) {
        // Can release all handles now that there's no code executing
        // that could be using them. Handles for the Java JVMCI runtime
        // are never released as we cannot guarantee all compiler threads
        // using it have been stopped.
        int released = release_and_clear_oop_handles();
        JVMCI_event_1("releasing handles for JVMCI runtime %d: oop handles=%d, metadata handles={total=%d, live=%d, blocks=%d}",
            _id,
            released,
            _metadata_handles->num_handles(),
            _metadata_handles->num_handles() - _metadata_handles->num_free_handles(),
            _metadata_handles->num_blocks());

        // No need to acquire _lock since this is the only thread accessing this runtime
        _metadata_handles->clear();
      }
    }
    // Allow other threads to attach to this runtime now
    MutexLocker locker(JVMCI_lock);
    _num_attached_threads = 0;
    if (JVMCI::using_singleton_shared_library_runtime()) {
      // Notify any thread waiting to attach to the
      // singleton JVMCIRuntime
      JVMCI_lock->notify();
    }
  }
  thread->set_libjvmci_runtime(nullptr);
  JVMCI_event_1("detached from JVMCI runtime %d", _id);
  return destroyed_javavm;
}

JNIEnv* JVMCIRuntime::init_shared_library_javavm(int* create_JavaVM_err, const char** err_msg) {
  MutexLocker locker(_lock);
  JavaVM* javaVM = _shared_library_javavm;
  if (javaVM == nullptr) {
#ifdef ASSERT
    const char* val = Arguments::PropertyList_get_value(Arguments::system_properties(), "test.jvmci.forceEnomemOnLibjvmciInit");
    if (val != nullptr && strcmp(val, "true") == 0) {
      *create_JavaVM_err = JNI_ENOMEM;
      return nullptr;
    }
#endif

    char* sl_path;
    void* sl_handle = JVMCI::get_shared_library(sl_path, true);

    jint (*JNI_CreateJavaVM)(JavaVM **pvm, void **penv, void *args);
    typedef jint (*JNI_CreateJavaVM_t)(JavaVM **pvm, void **penv, void *args);

    JNI_CreateJavaVM = CAST_TO_FN_PTR(JNI_CreateJavaVM_t, os::dll_lookup(sl_handle, "JNI_CreateJavaVM"));
    if (JNI_CreateJavaVM == nullptr) {
      fatal("Unable to find JNI_CreateJavaVM in %s", sl_path);
    }

    ResourceMark rm;
    JavaVMInitArgs vm_args;
    vm_args.version = JNI_VERSION_1_2;
    vm_args.ignoreUnrecognized = JNI_TRUE;
    JavaVMOption options[6];
    jlong javaVM_id = 0;

    // Protocol: JVMCI shared library JavaVM should support a non-standard "_javavm_id"
    // option whose extraInfo info field is a pointer to which a unique id for the
    // JavaVM should be written.
    options[0].optionString = (char*) "_javavm_id";
    options[0].extraInfo = &javaVM_id;

    options[1].optionString = (char*) "_log";
    options[1].extraInfo = (void*) _log;
    options[2].optionString = (char*) "_flush_log";
    options[2].extraInfo = (void*) _flush_log;
    options[3].optionString = (char*) "_fatal";
    options[3].extraInfo = (void*) _fatal;
    options[4].optionString = (char*) "_fatal_log";
    options[4].extraInfo = (void*) _fatal_log;
    options[5].optionString = (char*) "_createvm_errorstr";
    options[5].extraInfo = (void*) err_msg;

    vm_args.version = JNI_VERSION_1_2;
    vm_args.options = options;
    vm_args.nOptions = sizeof(options) / sizeof(JavaVMOption);

    JNIEnv* env = nullptr;
    int result = (*JNI_CreateJavaVM)(&javaVM, (void**) &env, &vm_args);
    if (result == JNI_OK) {
      guarantee(env != nullptr, "missing env");
      _shared_library_javavm_id = javaVM_id;
      _shared_library_javavm = javaVM;
      JVMCI_event_1("created JavaVM[%ld]@" PTR_FORMAT " for JVMCI runtime %d", javaVM_id, p2i(javaVM), _id);
      return env;
    } else {
      *create_JavaVM_err = result;
    }
  }
  return nullptr;
}

void JVMCIRuntime::init_JavaVM_info(jlongArray info, JVMCI_TRAPS) {
  if (info != nullptr) {
    typeArrayOop info_oop = (typeArrayOop) JNIHandles::resolve(info);
    if (info_oop->length() < 4) {
      JVMCI_THROW_MSG(ArrayIndexOutOfBoundsException, err_msg("%d < 4", info_oop->length()));
    }
    JavaVM* javaVM = _shared_library_javavm;
    info_oop->long_at_put(0, (jlong) (address) javaVM);
    info_oop->long_at_put(1, (jlong) (address) javaVM->functions->reserved0);
    info_oop->long_at_put(2, (jlong) (address) javaVM->functions->reserved1);
    info_oop->long_at_put(3, (jlong) (address) javaVM->functions->reserved2);
  }
}

#define JAVAVM_CALL_BLOCK                                             \
  guarantee(thread != nullptr && _shared_library_javavm != nullptr, "npe"); \
  ThreadToNativeFromVM ttnfv(thread);                                 \
  JavaVM* javavm = _shared_library_javavm;

jint JVMCIRuntime::AttachCurrentThread(JavaThread* thread, void **penv, void *args) {
  JAVAVM_CALL_BLOCK
  return javavm->AttachCurrentThread(penv, args);
}

jint JVMCIRuntime::AttachCurrentThreadAsDaemon(JavaThread* thread, void **penv, void *args) {
  JAVAVM_CALL_BLOCK
  return javavm->AttachCurrentThreadAsDaemon(penv, args);
}

jint JVMCIRuntime::DetachCurrentThread(JavaThread* thread) {
  JAVAVM_CALL_BLOCK
  return javavm->DetachCurrentThread();
}

jint JVMCIRuntime::GetEnv(JavaThread* thread, void **penv, jint version) {
  JAVAVM_CALL_BLOCK
  return javavm->GetEnv(penv, version);
}
#undef JAVAVM_CALL_BLOCK                                             \

void JVMCIRuntime::initialize_HotSpotJVMCIRuntime(JVMCI_TRAPS) {
  if (is_HotSpotJVMCIRuntime_initialized()) {
    if (JVMCIENV->is_hotspot() && UseJVMCINativeLibrary) {
      JVMCI_THROW_MSG(InternalError, "JVMCI has already been enabled in the JVMCI shared library");
    }
  }

  initialize(JVMCI_CHECK);

  // This should only be called in the context of the JVMCI class being initialized
  JVMCIObject result = JVMCIENV->call_HotSpotJVMCIRuntime_runtime(JVMCI_CHECK);
  result = JVMCIENV->make_global(result);

  OrderAccess::storestore();  // Ensure handle is fully constructed before publishing
  _HotSpotJVMCIRuntime_instance = result;

  JVMCI::_is_initialized = true;
}

JVMCIRuntime::InitState JVMCIRuntime::_shared_library_javavm_refs_init_state = JVMCIRuntime::uninitialized;
JVMCIRuntime::InitState JVMCIRuntime::_hotspot_javavm_refs_init_state = JVMCIRuntime::uninitialized;

class JavaVMRefsInitialization: public StackObj {
  JVMCIRuntime::InitState *_state;
  int _id;
 public:
  JavaVMRefsInitialization(JVMCIRuntime::InitState *state, int id) {
    _state = state;
    _id = id;
    // All classes, methods and fields in the JVMCI shared library
    // are in the read-only part of the image. As such, these
    // values (and any global handle derived from them via NewGlobalRef)
    // are the same for all JavaVM instances created in the
    // shared library which means they only need to be initialized
    // once. In non-product mode, we check this invariant.
    // See com.oracle.svm.jni.JNIImageHeapHandles.
    // The same is true for Klass* and field offsets in HotSpotJVMCI.
    if (*state == JVMCIRuntime::uninitialized DEBUG_ONLY( || true)) {
      *state = JVMCIRuntime::being_initialized;
      JVMCI_event_1("initializing JavaVM references in JVMCI runtime %d", id);
    } else {
      while (*state != JVMCIRuntime::fully_initialized) {
        JVMCI_event_1("waiting for JavaVM references initialization in JVMCI runtime %d", id);
        JVMCI_lock->wait();
      }
      JVMCI_event_1("done waiting for JavaVM references initialization in JVMCI runtime %d", id);
    }
  }

  ~JavaVMRefsInitialization() {
    if (*_state == JVMCIRuntime::being_initialized) {
      *_state = JVMCIRuntime::fully_initialized;
      JVMCI_event_1("initialized JavaVM references in JVMCI runtime %d", _id);
      JVMCI_lock->notify_all();
    }
  }

  bool should_init() {
    return *_state == JVMCIRuntime::being_initialized;
  }
};

void JVMCIRuntime::initialize(JVMCI_TRAPS) {
  // Check first without _lock
  if (_init_state == fully_initialized) {
    return;
  }

  JavaThread* THREAD = JavaThread::current();

  MutexLocker locker(_lock);
  // Check again under _lock
  if (_init_state == fully_initialized) {
    return;
  }

  while (_init_state == being_initialized) {
    JVMCI_event_1("waiting for initialization of JVMCI runtime %d", _id);
    _lock->wait();
    if (_init_state == fully_initialized) {
      JVMCI_event_1("done waiting for initialization of JVMCI runtime %d", _id);
      return;
    }
  }

  JVMCI_event_1("initializing JVMCI runtime %d", _id);
  _init_state = being_initialized;

  {
    MutexUnlocker unlock(_lock);

    HandleMark hm(THREAD);
    ResourceMark rm(THREAD);
    {
      MutexLocker lock_jvmci(JVMCI_lock);
      if (JVMCIENV->is_hotspot()) {
        JavaVMRefsInitialization initialization(&_hotspot_javavm_refs_init_state, _id);
        if (initialization.should_init()) {
          MutexUnlocker unlock_jvmci(JVMCI_lock);
          HotSpotJVMCI::compute_offsets(CHECK_EXIT);
        }
      } else {
        JavaVMRefsInitialization initialization(&_shared_library_javavm_refs_init_state, _id);
        if (initialization.should_init()) {
          MutexUnlocker unlock_jvmci(JVMCI_lock);
          JNIAccessMark jni(JVMCIENV, THREAD);

          JNIJVMCI::initialize_ids(jni.env());
          if (jni()->ExceptionCheck()) {
            jni()->ExceptionDescribe();
            fatal("JNI exception during init");
          }
          // _lock is re-locked at this point
        }
      }
    }

    if (!JVMCIENV->is_hotspot()) {
      JNIAccessMark jni(JVMCIENV, THREAD);
      JNIJVMCI::register_natives(jni.env());
    }
    create_jvmci_primitive_type(T_BOOLEAN, JVMCI_CHECK_EXIT_((void)0));
    create_jvmci_primitive_type(T_BYTE, JVMCI_CHECK_EXIT_((void)0));
    create_jvmci_primitive_type(T_CHAR, JVMCI_CHECK_EXIT_((void)0));
    create_jvmci_primitive_type(T_SHORT, JVMCI_CHECK_EXIT_((void)0));
    create_jvmci_primitive_type(T_INT, JVMCI_CHECK_EXIT_((void)0));
    create_jvmci_primitive_type(T_LONG, JVMCI_CHECK_EXIT_((void)0));
    create_jvmci_primitive_type(T_FLOAT, JVMCI_CHECK_EXIT_((void)0));
    create_jvmci_primitive_type(T_DOUBLE, JVMCI_CHECK_EXIT_((void)0));
    create_jvmci_primitive_type(T_VOID, JVMCI_CHECK_EXIT_((void)0));

    DEBUG_ONLY(CodeInstaller::verify_bci_constants(JVMCIENV);)
  }

  _init_state = fully_initialized;
  JVMCI_event_1("initialized JVMCI runtime %d", _id);
  _lock->notify_all();
}

JVMCIObject JVMCIRuntime::create_jvmci_primitive_type(BasicType type, JVMCI_TRAPS) {
  JavaThread* THREAD = JavaThread::current(); // For exception macros.
  // These primitive types are long lived and are created before the runtime is fully set up
  // so skip registering them for scanning.
  JVMCIObject mirror = JVMCIENV->get_object_constant(java_lang_Class::primitive_mirror(type), false, true);
  if (JVMCIENV->is_hotspot()) {
    JavaValue result(T_OBJECT);
    JavaCallArguments args;
    args.push_oop(Handle(THREAD, HotSpotJVMCI::resolve(mirror)));
    args.push_int(type2char(type));
    JavaCalls::call_static(&result, HotSpotJVMCI::HotSpotResolvedPrimitiveType::klass(), vmSymbols::fromMetaspace_name(), vmSymbols::primitive_fromMetaspace_signature(), &args, CHECK_(JVMCIObject()));

    return JVMCIENV->wrap(JNIHandles::make_local(result.get_oop()));
  } else {
    JNIAccessMark jni(JVMCIENV);
    jobject result = jni()->CallStaticObjectMethod(JNIJVMCI::HotSpotResolvedPrimitiveType::clazz(),
                                           JNIJVMCI::HotSpotResolvedPrimitiveType_fromMetaspace_method(),
                                           mirror.as_jobject(), type2char(type));
    if (jni()->ExceptionCheck()) {
      return JVMCIObject();
    }
    return JVMCIENV->wrap(result);
  }
}

void JVMCIRuntime::initialize_JVMCI(JVMCI_TRAPS) {
  if (!is_HotSpotJVMCIRuntime_initialized()) {
    initialize(JVMCI_CHECK);
    JVMCIENV->call_JVMCI_getRuntime(JVMCI_CHECK);
    guarantee(_HotSpotJVMCIRuntime_instance.is_non_null(), "NPE in JVMCI runtime %d", _id);
  }
}

JVMCIObject JVMCIRuntime::get_HotSpotJVMCIRuntime(JVMCI_TRAPS) {
  initialize(JVMCI_CHECK_(JVMCIObject()));
  initialize_JVMCI(JVMCI_CHECK_(JVMCIObject()));
  return _HotSpotJVMCIRuntime_instance;
}

// Implementation of CompilerToVM.registerNatives()
// When called from libjvmci, `libjvmciOrHotspotEnv` is a libjvmci env so use JVM_ENTRY_NO_ENV.
JVM_ENTRY_NO_ENV(void, JVM_RegisterJVMCINatives(JNIEnv *libjvmciOrHotspotEnv, jclass c2vmClass))
  JVMCIENV_FROM_JNI(thread, libjvmciOrHotspotEnv);

  if (!EnableJVMCI) {
    JVMCI_THROW_MSG(InternalError, "JVMCI is not enabled");
  }

  JVMCIENV->runtime()->initialize(JVMCIENV);

  {
    ResourceMark rm(thread);
    HandleMark hm(thread);
    ThreadToNativeFromVM trans(thread);

    // Ensure _non_oop_bits is initialized
    Universe::non_oop_word();
    JNIEnv *env = libjvmciOrHotspotEnv;
    if (JNI_OK != env->RegisterNatives(c2vmClass, CompilerToVM::methods, CompilerToVM::methods_count())) {
      if (!env->ExceptionCheck()) {
        for (int i = 0; i < CompilerToVM::methods_count(); i++) {
          if (JNI_OK != env->RegisterNatives(c2vmClass, CompilerToVM::methods + i, 1)) {
            guarantee(false, "Error registering JNI method %s%s", CompilerToVM::methods[i].name, CompilerToVM::methods[i].signature);
            break;
          }
        }
      } else {
        env->ExceptionDescribe();
      }
      guarantee(false, "Failed registering CompilerToVM native methods");
    }
  }
JVM_END


void JVMCIRuntime::shutdown() {
  if (_HotSpotJVMCIRuntime_instance.is_non_null()) {
    JVMCI_event_1("shutting down HotSpotJVMCIRuntime for JVMCI runtime %d", _id);
    JVMCIEnv __stack_jvmci_env__(JavaThread::current(), _HotSpotJVMCIRuntime_instance.is_hotspot(),__FILE__, __LINE__);
    JVMCIEnv* JVMCIENV = &__stack_jvmci_env__;
    if (JVMCIENV->init_error() == JNI_OK) {
      JVMCIENV->call_HotSpotJVMCIRuntime_shutdown(_HotSpotJVMCIRuntime_instance);
    } else {
      JVMCI_event_1("Error in JVMCIEnv for shutdown (err: %d)", JVMCIENV->init_error());
    }
    if (_num_attached_threads == cannot_be_attached) {
      // Only when no other threads are attached to this runtime
      // is it safe to reset these fields.
      _HotSpotJVMCIRuntime_instance = JVMCIObject();
      _init_state = uninitialized;
      JVMCI_event_1("shut down JVMCI runtime %d", _id);
    }
  }
}

bool JVMCIRuntime::destroy_shared_library_javavm() {
  guarantee(_num_attached_threads == cannot_be_attached,
      "cannot destroy JavaVM for JVMCI runtime %d with %d attached threads", _id, _num_attached_threads);
  JavaVM* javaVM;
  int javaVM_id = _shared_library_javavm_id;
  {
    // Exactly one thread can destroy the JavaVM
    // and release the handle to it.
    MutexLocker only_one(_lock);
    javaVM = _shared_library_javavm;
    if (javaVM != nullptr) {
      _shared_library_javavm = nullptr;
      _shared_library_javavm_id = 0;
    }
  }
  if (javaVM != nullptr) {
    int result;
    {
      // Must transition into native before calling into libjvmci
      ThreadToNativeFromVM ttnfv(JavaThread::current());
      result = javaVM->DestroyJavaVM();
    }
    if (result == JNI_OK) {
      JVMCI_event_1("destroyed JavaVM[%d]@" PTR_FORMAT " for JVMCI runtime %d", javaVM_id, p2i(javaVM), _id);
    } else {
      warning("Non-zero result (%d) when calling JNI_DestroyJavaVM on JavaVM[%d]@" PTR_FORMAT, result, javaVM_id, p2i(javaVM));
    }
    return true;
  }
  return false;
}

void JVMCIRuntime::bootstrap_finished(TRAPS) {
  if (_HotSpotJVMCIRuntime_instance.is_non_null()) {
    JVMCIENV_FROM_THREAD(THREAD);
    JVMCIENV->check_init(CHECK);
    JVMCIENV->call_HotSpotJVMCIRuntime_bootstrapFinished(_HotSpotJVMCIRuntime_instance, JVMCIENV);
  }
}

void JVMCIRuntime::describe_pending_hotspot_exception(JavaThread* THREAD) {
  if (HAS_PENDING_EXCEPTION) {
    Handle exception(THREAD, PENDING_EXCEPTION);
    CLEAR_PENDING_EXCEPTION;
    java_lang_Throwable::print_stack_trace(exception, tty);

    // Clear and ignore any exceptions raised during printing
    CLEAR_PENDING_EXCEPTION;
  }
}


void JVMCIRuntime::fatal_exception(JVMCIEnv* JVMCIENV, const char* message) {
  JavaThread* THREAD = JavaThread::current(); // For exception macros.

  static volatile int report_error = 0;
  if (!report_error && Atomic::cmpxchg(&report_error, 0, 1) == 0) {
    // Only report an error once
    tty->print_raw_cr(message);
    if (JVMCIENV != nullptr) {
      JVMCIENV->describe_pending_exception(tty);
    } else {
      describe_pending_hotspot_exception(THREAD);
    }
  } else {
    // Allow error reporting thread time to print the stack trace.
    THREAD->sleep(200);
  }
  fatal("Fatal JVMCI exception (see JVMCI Events for stack trace): %s", message);
}

// ------------------------------------------------------------------
// Note: the logic of this method should mirror the logic of
// constantPoolOopDesc::verify_constant_pool_resolve.
bool JVMCIRuntime::check_klass_accessibility(Klass* accessing_klass, Klass* resolved_klass) {
  if (accessing_klass->is_objArray_klass()) {
    accessing_klass = ObjArrayKlass::cast(accessing_klass)->bottom_klass();
  }
  if (!accessing_klass->is_instance_klass()) {
    return true;
  }

  if (resolved_klass->is_objArray_klass()) {
    // Find the element klass, if this is an array.
    resolved_klass = ObjArrayKlass::cast(resolved_klass)->bottom_klass();
  }
  if (resolved_klass->is_instance_klass()) {
    Reflection::VerifyClassAccessResults result =
      Reflection::verify_class_access(accessing_klass, InstanceKlass::cast(resolved_klass), true);
    return result == Reflection::ACCESS_OK;
  }
  return true;
}

// ------------------------------------------------------------------
Klass* JVMCIRuntime::get_klass_by_name_impl(Klass*& accessing_klass,
                                          const constantPoolHandle& cpool,
                                          Symbol* sym,
                                          bool require_local) {
  JVMCI_EXCEPTION_CONTEXT;

  // Now we need to check the SystemDictionary
  if (sym->char_at(0) == JVM_SIGNATURE_CLASS &&
      sym->char_at(sym->utf8_length()-1) == JVM_SIGNATURE_ENDCLASS) {
    // This is a name from a signature.  Strip off the trimmings.
    // Call recursive to keep scope of strippedsym.
    TempNewSymbol strippedsym = SymbolTable::new_symbol(sym->as_utf8()+1,
                                                        sym->utf8_length()-2);
    return get_klass_by_name_impl(accessing_klass, cpool, strippedsym, require_local);
  }

  Handle loader;
  Handle domain;
  if (accessing_klass != nullptr) {
    loader = Handle(THREAD, accessing_klass->class_loader());
    domain = Handle(THREAD, accessing_klass->protection_domain());
  }

  Klass* found_klass = require_local ?
                         SystemDictionary::find_instance_or_array_klass(THREAD, sym, loader, domain) :
                         SystemDictionary::find_constrained_instance_or_array_klass(THREAD, sym, loader);

  // If we fail to find an array klass, look again for its element type.
  // The element type may be available either locally or via constraints.
  // In either case, if we can find the element type in the system dictionary,
  // we must build an array type around it.  The CI requires array klasses
  // to be loaded if their element klasses are loaded, except when memory
  // is exhausted.
  if (sym->char_at(0) == JVM_SIGNATURE_ARRAY &&
      (sym->char_at(1) == JVM_SIGNATURE_ARRAY || sym->char_at(1) == JVM_SIGNATURE_CLASS)) {
    // We have an unloaded array.
    // Build it on the fly if the element class exists.
    TempNewSymbol elem_sym = SymbolTable::new_symbol(sym->as_utf8()+1,
                                                     sym->utf8_length()-1);

    // Get element Klass recursively.
    Klass* elem_klass =
      get_klass_by_name_impl(accessing_klass,
                             cpool,
                             elem_sym,
                             require_local);
    if (elem_klass != nullptr) {
      // Now make an array for it
      return elem_klass->array_klass(THREAD);
    }
  }

  if (found_klass == nullptr && !cpool.is_null() && cpool->has_preresolution()) {
    // Look inside the constant pool for pre-resolved class entries.
    for (int i = cpool->length() - 1; i >= 1; i--) {
      if (cpool->tag_at(i).is_klass()) {
        Klass*  kls = cpool->resolved_klass_at(i);
        if (kls->name() == sym) {
          return kls;
        }
      }
    }
  }

  return found_klass;
}

// ------------------------------------------------------------------
Klass* JVMCIRuntime::get_klass_by_name(Klass* accessing_klass,
                                  Symbol* klass_name,
                                  bool require_local) {
  ResourceMark rm;
  constantPoolHandle cpool;
  return get_klass_by_name_impl(accessing_klass,
                                                 cpool,
                                                 klass_name,
                                                 require_local);
}

// ------------------------------------------------------------------
// Implementation of get_klass_by_index.
Klass* JVMCIRuntime::get_klass_by_index_impl(const constantPoolHandle& cpool,
                                        int index,
                                        bool& is_accessible,
                                        Klass* accessor) {
  JVMCI_EXCEPTION_CONTEXT;
  Klass* klass = ConstantPool::klass_at_if_loaded(cpool, index);
  Symbol* klass_name = nullptr;
  if (klass == nullptr) {
    klass_name = cpool->klass_name_at(index);
  }

  if (klass == nullptr) {
    // Not found in constant pool.  Use the name to do the lookup.
    Klass* k = get_klass_by_name_impl(accessor,
                                        cpool,
                                        klass_name,
                                        false);
    // Calculate accessibility the hard way.
    if (k == nullptr) {
      is_accessible = false;
    } else if (k->class_loader() != accessor->class_loader() &&
               get_klass_by_name_impl(accessor, cpool, k->name(), true) == nullptr) {
      // Loaded only remotely.  Not linked yet.
      is_accessible = false;
    } else {
      // Linked locally, and we must also check public/private, etc.
      is_accessible = check_klass_accessibility(accessor, k);
    }
    if (!is_accessible) {
      return nullptr;
    }
    return k;
  }

  // It is known to be accessible, since it was found in the constant pool.
  is_accessible = true;
  return klass;
}

// ------------------------------------------------------------------
// Get a klass from the constant pool.
Klass* JVMCIRuntime::get_klass_by_index(const constantPoolHandle& cpool,
                                   int index,
                                   bool& is_accessible,
                                   Klass* accessor) {
  ResourceMark rm;
  Klass* result = get_klass_by_index_impl(cpool, index, is_accessible, accessor);
  return result;
}

// ------------------------------------------------------------------
// Perform an appropriate method lookup based on accessor, holder,
// name, signature, and bytecode.
Method* JVMCIRuntime::lookup_method(InstanceKlass* accessor,
                                    Klass*        holder,
                                    Symbol*       name,
                                    Symbol*       sig,
                                    Bytecodes::Code bc,
                                    constantTag   tag) {
  // Accessibility checks are performed in JVMCIEnv::get_method_by_index_impl().
  assert(check_klass_accessibility(accessor, holder), "holder not accessible");

  LinkInfo link_info(holder, name, sig, accessor,
                     LinkInfo::AccessCheck::required,
                     LinkInfo::LoaderConstraintCheck::required,
                     tag);
  switch (bc) {
    case Bytecodes::_invokestatic:
      return LinkResolver::resolve_static_call_or_null(link_info);
    case Bytecodes::_invokespecial:
      return LinkResolver::resolve_special_call_or_null(link_info);
    case Bytecodes::_invokeinterface:
      return LinkResolver::linktime_resolve_interface_method_or_null(link_info);
    case Bytecodes::_invokevirtual:
      return LinkResolver::linktime_resolve_virtual_method_or_null(link_info);
    default:
      fatal("Unhandled bytecode: %s", Bytecodes::name(bc));
      return nullptr; // silence compiler warnings
  }
}


// ------------------------------------------------------------------
Method* JVMCIRuntime::get_method_by_index_impl(const constantPoolHandle& cpool,
                                               int index, Bytecodes::Code bc,
                                               InstanceKlass* accessor) {
  if (bc == Bytecodes::_invokedynamic) {
    int indy_index = cpool->decode_invokedynamic_index(index);
    if (cpool->resolved_indy_entry_at(indy_index)->is_resolved()) {
      return cpool->resolved_indy_entry_at(indy_index)->method();
    }

    return nullptr;
  }

  int holder_index = cpool->klass_ref_index_at(index, bc);
  bool holder_is_accessible;
  Klass* holder = get_klass_by_index_impl(cpool, holder_index, holder_is_accessible, accessor);

  // Get the method's name and signature.
  Symbol* name_sym = cpool->name_ref_at(index, bc);
  Symbol* sig_sym  = cpool->signature_ref_at(index, bc);

  if (cpool->has_preresolution()
      || ((holder == vmClasses::MethodHandle_klass() || holder == vmClasses::VarHandle_klass()) &&
          MethodHandles::is_signature_polymorphic_name(holder, name_sym))) {
    // Short-circuit lookups for JSR 292-related call sites.
    // That is, do not rely only on name-based lookups, because they may fail
    // if the names are not resolvable in the boot class loader (7056328).
    switch (bc) {
    case Bytecodes::_invokevirtual:
    case Bytecodes::_invokeinterface:
    case Bytecodes::_invokespecial:
    case Bytecodes::_invokestatic:
      {
        Method* m = ConstantPool::method_at_if_loaded(cpool, index);
        if (m != nullptr) {
          return m;
        }
      }
      break;
    default:
      break;
    }
  }

  if (holder_is_accessible) { // Our declared holder is loaded.
    constantTag tag = cpool->tag_ref_at(index, bc);
    Method* m = lookup_method(accessor, holder, name_sym, sig_sym, bc, tag);
    if (m != nullptr) {
      // We found the method.
      return m;
    }
  }

  // Either the declared holder was not loaded, or the method could
  // not be found.

  return nullptr;
}

// ------------------------------------------------------------------
InstanceKlass* JVMCIRuntime::get_instance_klass_for_declared_method_holder(Klass* method_holder) {
  // For the case of <array>.clone(), the method holder can be an ArrayKlass*
  // instead of an InstanceKlass*.  For that case simply pretend that the
  // declared holder is Object.clone since that's where the call will bottom out.
  if (method_holder->is_instance_klass()) {
    return InstanceKlass::cast(method_holder);
  } else if (method_holder->is_array_klass()) {
    return vmClasses::Object_klass();
  } else {
    ShouldNotReachHere();
  }
  return nullptr;
}


// ------------------------------------------------------------------
Method* JVMCIRuntime::get_method_by_index(const constantPoolHandle& cpool,
                                     int index, Bytecodes::Code bc,
                                     InstanceKlass* accessor) {
  ResourceMark rm;
  return get_method_by_index_impl(cpool, index, bc, accessor);
}

// ------------------------------------------------------------------
// Check for changes to the system dictionary during compilation
// class loads, evolution, breakpoints
JVMCI::CodeInstallResult JVMCIRuntime::validate_compile_task_dependencies(Dependencies* dependencies,
                                                                          JVMCICompileState* compile_state,
                                                                          char** failure_detail,
                                                                          bool& failing_dep_is_call_site)
{
  failing_dep_is_call_site = false;
  // If JVMTI capabilities were enabled during compile, the compilation is invalidated.
  if (compile_state != nullptr && compile_state->jvmti_state_changed()) {
    *failure_detail = (char*) "Jvmti state change during compilation invalidated dependencies";
    return JVMCI::dependencies_failed;
  }

  CompileTask* task = compile_state == nullptr ? nullptr : compile_state->task();
  Dependencies::DepType result = dependencies->validate_dependencies(task, failure_detail);

  if (result == Dependencies::end_marker) {
    return JVMCI::ok;
  }
  if (result == Dependencies::call_site_target_value) {
    failing_dep_is_call_site = true;
  }
  return JVMCI::dependencies_failed;
}

// Called after an upcall to `function` while compiling `method`.
// If an exception occurred, it is cleared, the compilation state
// is updated with the failure and this method returns true.
// Otherwise, it returns false.
static bool after_compiler_upcall(JVMCIEnv* JVMCIENV, JVMCICompiler* compiler, const methodHandle& method, const char* function) {
  if (JVMCIENV->has_pending_exception()) {
    ResourceMark rm;
    bool reason_on_C_heap = true;
    const char* pending_string = nullptr;
    const char* pending_stack_trace = nullptr;
    JVMCIENV->pending_exception_as_string(&pending_string, &pending_stack_trace);
    if (pending_string == nullptr) pending_string = "null";
    const char* failure_reason = os::strdup(err_msg("uncaught exception in %s [%s]", function, pending_string), mtJVMCI);
    if (failure_reason == nullptr) {
      failure_reason = "uncaught exception";
      reason_on_C_heap = false;
    }
    JVMCI_event_1("%s", failure_reason);
    Log(jit, compilation) log;
    if (log.is_info()) {
      log.info("%s while compiling %s", failure_reason, method->name_and_sig_as_C_string());
      if (pending_stack_trace != nullptr) {
        LogStream ls(log.info());
        ls.print_raw_cr(pending_stack_trace);
      }
    }
    JVMCICompileState* compile_state = JVMCIENV->compile_state();
    compile_state->set_failure(true, failure_reason, reason_on_C_heap);
    compiler->on_upcall(failure_reason, compile_state);
    return true;
  }
  return false;
}

void JVMCIRuntime::compile_method(JVMCIEnv* JVMCIENV, JVMCICompiler* compiler, const methodHandle& method, int entry_bci) {
  JVMCI_EXCEPTION_CONTEXT

  JVMCICompileState* compile_state = JVMCIENV->compile_state();

  bool is_osr = entry_bci != InvocationEntryBci;
  if (compiler->is_bootstrapping() && is_osr) {
    // no OSR compilations during bootstrap - the compiler is just too slow at this point,
    // and we know that there are no endless loops
    compile_state->set_failure(true, "No OSR during bootstrap");
    return;
  }
  if (JVMCI::in_shutdown()) {
    if (UseJVMCINativeLibrary) {
      JVMCIRuntime *runtime = JVMCI::compiler_runtime(thread, false);
      if (runtime != nullptr) {
        runtime->detach_thread(thread, "JVMCI shutdown pre-empted compilation");
      }
    }
    compile_state->set_failure(false, "Avoiding compilation during shutdown");
    return;
  }

  HandleMark hm(thread);
  JVMCIObject receiver = get_HotSpotJVMCIRuntime(JVMCIENV);
  if (after_compiler_upcall(JVMCIENV, compiler, method, "get_HotSpotJVMCIRuntime")) {
    return;
  }
  JVMCIObject jvmci_method = JVMCIENV->get_jvmci_method(method, JVMCIENV);
  if (after_compiler_upcall(JVMCIENV, compiler, method, "get_jvmci_method")) {
    return;
  }

  JVMCIObject result_object = JVMCIENV->call_HotSpotJVMCIRuntime_compileMethod(receiver, jvmci_method, entry_bci,
                                                                     (jlong) compile_state, compile_state->task()->compile_id());
#ifdef ASSERT
  if (JVMCIENV->has_pending_exception()) {
    const char* val = Arguments::PropertyList_get_value(Arguments::system_properties(), "test.jvmci.compileMethodExceptionIsFatal");
    if (val != nullptr && strcmp(val, "true") == 0) {
      fatal_exception(JVMCIENV, "testing JVMCI fatal exception handling");
    }
  }
#endif

  if (after_compiler_upcall(JVMCIENV, compiler, method, "call_HotSpotJVMCIRuntime_compileMethod")) {
    return;
  }
  compiler->on_upcall(nullptr);
  guarantee(result_object.is_non_null(), "call_HotSpotJVMCIRuntime_compileMethod returned null");
  JVMCIObject failure_message = JVMCIENV->get_HotSpotCompilationRequestResult_failureMessage(result_object);
  if (failure_message.is_non_null()) {
    // Copy failure reason into resource memory first ...
    const char* failure_reason = JVMCIENV->as_utf8_string(failure_message);
    // ... and then into the C heap.
    failure_reason = os::strdup(failure_reason, mtJVMCI);
    bool retryable = JVMCIENV->get_HotSpotCompilationRequestResult_retry(result_object) != 0;
    compile_state->set_failure(retryable, failure_reason, true);
  } else {
    if (!compile_state->task()->is_success()) {
      compile_state->set_failure(true, "no nmethod produced");
    } else {
      compile_state->task()->set_num_inlined_bytecodes(JVMCIENV->get_HotSpotCompilationRequestResult_inlinedBytecodes(result_object));
      compiler->inc_methods_compiled();
    }
  }
  if (compiler->is_bootstrapping()) {
    compiler->set_bootstrap_compilation_request_handled();
  }
}

bool JVMCIRuntime::is_gc_supported(JVMCIEnv* JVMCIENV, CollectedHeap::Name name) {
  JVMCI_EXCEPTION_CONTEXT

  JVMCIObject receiver = get_HotSpotJVMCIRuntime(JVMCIENV);
  if (JVMCIENV->has_pending_exception()) {
    fatal_exception(JVMCIENV, "Exception during HotSpotJVMCIRuntime initialization");
  }
  return JVMCIENV->call_HotSpotJVMCIRuntime_isGCSupported(receiver, (int) name);
}

// ------------------------------------------------------------------
JVMCI::CodeInstallResult JVMCIRuntime::register_method(JVMCIEnv* JVMCIENV,
                                                       const methodHandle& method,
                                                       nmethod*& nm,
                                                       int entry_bci,
                                                       CodeOffsets* offsets,
                                                       int orig_pc_offset,
                                                       CodeBuffer* code_buffer,
                                                       int frame_words,
                                                       OopMapSet* oop_map_set,
                                                       ExceptionHandlerTable* handler_table,
                                                       ImplicitExceptionTable* implicit_exception_table,
                                                       AbstractCompiler* compiler,
                                                       DebugInformationRecorder* debug_info,
                                                       Dependencies* dependencies,
                                                       int compile_id,
                                                       bool has_monitors,
                                                       bool has_unsafe_access,
                                                       bool has_wide_vector,
                                                       JVMCIObject compiled_code,
                                                       JVMCIObject nmethod_mirror,
                                                       FailedSpeculation** failed_speculations,
                                                       char* speculations,
                                                       int speculations_len,
                                                       int nmethod_entry_patch_offset) {
  JVMCI_EXCEPTION_CONTEXT;
  CompLevel comp_level = CompLevel_full_optimization;
  char* failure_detail = nullptr;

  bool install_default = JVMCIENV->get_HotSpotNmethod_isDefault(nmethod_mirror) != 0;
  assert(JVMCIENV->isa_HotSpotNmethod(nmethod_mirror), "must be");
  JVMCIObject name = JVMCIENV->get_InstalledCode_name(nmethod_mirror);
  const char* nmethod_mirror_name = name.is_null() ? nullptr : JVMCIENV->as_utf8_string(name);
  int nmethod_mirror_index;
  if (!install_default) {
    // Reserve or initialize mirror slot in the oops table.
    OopRecorder* oop_recorder = debug_info->oop_recorder();
    nmethod_mirror_index = oop_recorder->allocate_oop_index(nmethod_mirror.is_hotspot() ? nmethod_mirror.as_jobject() : nullptr);
  } else {
    // A default HotSpotNmethod mirror is never tracked by the nmethod
    nmethod_mirror_index = -1;
  }

  JVMCI::CodeInstallResult result(JVMCI::ok);

  // We require method counters to store some method state (max compilation levels) required by the compilation policy.
  if (method->get_method_counters(THREAD) == nullptr) {
    result = JVMCI::cache_full;
    failure_detail = (char*) "can't create method counters";
  }

  if (result == JVMCI::ok) {
    // Check if memory should be freed before allocation
    CodeCache::gc_on_allocation();

    // To prevent compile queue updates.
    MutexLocker locker(THREAD, MethodCompileQueue_lock);

    // Prevent InstanceKlass::add_to_hierarchy from running
    // and invalidating our dependencies until we install this method.
    MutexLocker ml(Compile_lock);

    // Encode the dependencies now, so we can check them right away.
    dependencies->encode_content_bytes();

    // Record the dependencies for the current compile in the log
    if (LogCompilation) {
      for (Dependencies::DepStream deps(dependencies); deps.next(); ) {
        deps.log_dependency();
      }
    }

    // Check for {class loads, evolution, breakpoints} during compilation
    JVMCICompileState* compile_state = JVMCIENV->compile_state();
    bool failing_dep_is_call_site;
    result = validate_compile_task_dependencies(dependencies, compile_state, &failure_detail, failing_dep_is_call_site);
    if (result != JVMCI::ok) {
      // While not a true deoptimization, it is a preemptive decompile.
      MethodData* mdp = method()->method_data();
      if (mdp != nullptr && !failing_dep_is_call_site) {
        mdp->inc_decompile_count();
#ifdef ASSERT
        if (mdp->decompile_count() > (uint)PerMethodRecompilationCutoff) {
          ResourceMark m;
          tty->print_cr("WARN: endless recompilation of %s. Method was set to not compilable.", method()->name_and_sig_as_C_string());
        }
#endif
      }

      // All buffers in the CodeBuffer are allocated in the CodeCache.
      // If the code buffer is created on each compile attempt
      // as in C2, then it must be freed.
      //code_buffer->free_blob();
    } else {
      JVMCINMethodData* data = JVMCINMethodData::create(nmethod_mirror_index,
                                                        nmethod_entry_patch_offset,
                                                        nmethod_mirror_name,
                                                        failed_speculations);
      nm =  nmethod::new_nmethod(method,
                                 compile_id,
                                 entry_bci,
                                 offsets,
                                 orig_pc_offset,
                                 debug_info, dependencies, code_buffer,
                                 frame_words, oop_map_set,
                                 handler_table, implicit_exception_table,
                                 compiler, comp_level,
                                 speculations, speculations_len, data);


      // Free codeBlobs
      if (nm == nullptr) {
        // The CodeCache is full.  Print out warning and disable compilation.
        {
          MutexUnlocker ml(Compile_lock);
          MutexUnlocker locker(MethodCompileQueue_lock);
          CompileBroker::handle_full_code_cache(CodeCache::get_code_blob_type(comp_level));
        }
        result = JVMCI::cache_full;
      } else {
        nm->set_has_unsafe_access(has_unsafe_access);
        nm->set_has_wide_vectors(has_wide_vector);
        nm->set_has_monitors(has_monitors);

        JVMCINMethodData* data = nm->jvmci_nmethod_data();
        assert(data != nullptr, "must be");
        if (install_default) {
          assert(!nmethod_mirror.is_hotspot() || data->get_nmethod_mirror(nm, /* phantom_ref */ false) == nullptr, "must be");
          if (entry_bci == InvocationEntryBci) {
            // If there is an old version we're done with it
            CompiledMethod* old = method->code();
            if (TraceMethodReplacement && old != nullptr) {
              ResourceMark rm;
              char *method_name = method->name_and_sig_as_C_string();
              tty->print_cr("Replacing method %s", method_name);
            }
            if (old != nullptr ) {
              old->make_not_entrant();
            }

            LogTarget(Info, nmethod, install) lt;
            if (lt.is_enabled()) {
              ResourceMark rm;
              char *method_name = method->name_and_sig_as_C_string();
              lt.print("Installing method (%d) %s [entry point: %p]",
                        comp_level, method_name, nm->entry_point());
            }
            // Allow the code to be executed
            MutexLocker ml(CompiledMethod_lock, Mutex::_no_safepoint_check_flag);
            if (nm->make_in_use()) {
              method->set_code(method, nm);
            } else {
              result = JVMCI::nmethod_reclaimed;
            }
          } else {
            LogTarget(Info, nmethod, install) lt;
            if (lt.is_enabled()) {
              ResourceMark rm;
              char *method_name = method->name_and_sig_as_C_string();
              lt.print("Installing osr method (%d) %s @ %d",
                        comp_level, method_name, entry_bci);
            }
            MutexLocker ml(CompiledMethod_lock, Mutex::_no_safepoint_check_flag);
            if (nm->make_in_use()) {
              InstanceKlass::cast(method->method_holder())->add_osr_nmethod(nm);
            } else {
              result = JVMCI::nmethod_reclaimed;
            }
          }
        } else {
          assert(!nmethod_mirror.is_hotspot() || data->get_nmethod_mirror(nm, /* phantom_ref */ false) == HotSpotJVMCI::resolve(nmethod_mirror), "must be");
          MutexLocker ml(CompiledMethod_lock, Mutex::_no_safepoint_check_flag);
          if (!nm->make_in_use()) {
            result = JVMCI::nmethod_reclaimed;
          }
        }
      }
    }
  }

  // String creation must be done outside lock
  if (failure_detail != nullptr) {
    // A failure to allocate the string is silently ignored.
    JVMCIObject message = JVMCIENV->create_string(failure_detail, JVMCIENV);
    JVMCIENV->set_HotSpotCompiledNmethod_installationFailureMessage(compiled_code, message);
  }

  if (result == JVMCI::ok) {
    JVMCICompileState* state = JVMCIENV->compile_state();
    if (state != nullptr) {
      // Compilation succeeded, post what we know about it
      nm->post_compiled_method(state->task());
    }
  }

  return result;
}

void JVMCIRuntime::post_compile(JavaThread* thread) {
  if (UseJVMCINativeLibrary && JVMCI::one_shared_library_javavm_per_compilation()) {
    if (thread->libjvmci_runtime() != nullptr) {
      detach_thread(thread, "single use JavaVM");
    } else {
      // JVMCI shutdown may have already detached the thread
    }
  }
}
