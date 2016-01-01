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
 */

#include "precompiled.hpp"
#include "asm/codeBuffer.hpp"
#include "code/codeCache.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/disassembler.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "jvmci/jvmciCompilerToVM.hpp"
#include "jvmci/jvmciCompiler.hpp"
#include "jvmci/jvmciJavaClasses.hpp"
#include "jvmci/jvmciEnv.hpp"
#include "logging/log.hpp"
#include "memory/oopFactory.hpp"
#include "oops/oop.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "prims/jvm.h"
#include "runtime/biasedLocking.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/reflection.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/debug.hpp"
#include "utilities/defaultStream.hpp"

#if defined(_MSC_VER)
#define strtoll _strtoi64
#endif

jobject JVMCIRuntime::_HotSpotJVMCIRuntime_instance = NULL;
bool JVMCIRuntime::_HotSpotJVMCIRuntime_initialized = false;
bool JVMCIRuntime::_well_known_classes_initialized = false;
const char* JVMCIRuntime::_compiler = NULL;
int JVMCIRuntime::_options_count = 0;
SystemProperty** JVMCIRuntime::_options = NULL;
int JVMCIRuntime::_trivial_prefixes_count = 0;
char** JVMCIRuntime::_trivial_prefixes = NULL;
bool JVMCIRuntime::_shutdown_called = false;

static const char* OPTION_PREFIX = "jvmci.option.";
static const size_t OPTION_PREFIX_LEN = strlen(OPTION_PREFIX);

BasicType JVMCIRuntime::kindToBasicType(Handle kind, TRAPS) {
  if (kind.is_null()) {
    THROW_(vmSymbols::java_lang_NullPointerException(), T_ILLEGAL);
  }
  jchar ch = JavaKind::typeChar(kind);
  switch(ch) {
    case 'z': return T_BOOLEAN;
    case 'b': return T_BYTE;
    case 's': return T_SHORT;
    case 'c': return T_CHAR;
    case 'i': return T_INT;
    case 'f': return T_FLOAT;
    case 'j': return T_LONG;
    case 'd': return T_DOUBLE;
    case 'a': return T_OBJECT;
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

JRT_BLOCK_ENTRY(void, JVMCIRuntime::new_instance(JavaThread* thread, Klass* klass))
  JRT_BLOCK;
  assert(klass->is_klass(), "not a class");
  instanceKlassHandle h(thread, klass);
  h->check_valid_for_instantiation(true, CHECK);
  // make sure klass is initialized
  h->initialize(CHECK);
  // allocate instance and return via TLS
  oop obj = h->allocate_instance(CHECK);
  thread->set_vm_result(obj);
  JRT_BLOCK_END;

  if (ReduceInitialCardMarks) {
    new_store_pre_barrier(thread);
  }
JRT_END

JRT_BLOCK_ENTRY(void, JVMCIRuntime::new_array(JavaThread* thread, Klass* array_klass, jint length))
  JRT_BLOCK;
  // Note: no handle for klass needed since they are not used
  //       anymore after new_objArray() and no GC can happen before.
  //       (This may have to change if this code changes!)
  assert(array_klass->is_klass(), "not a class");
  oop obj;
  if (array_klass->is_typeArray_klass()) {
    BasicType elt_type = TypeArrayKlass::cast(array_klass)->element_type();
    obj = oopFactory::new_typeArray(elt_type, length, CHECK);
  } else {
    Klass* elem_klass = ObjArrayKlass::cast(array_klass)->element_klass();
    obj = oopFactory::new_objArray(elem_klass, length, CHECK);
  }
  thread->set_vm_result(obj);
  // This is pretty rare but this runtime patch is stressful to deoptimization
  // if we deoptimize here so force a deopt to stress the path.
  if (DeoptimizeALot) {
    static int deopts = 0;
    // Alternate between deoptimizing and raising an error (which will also cause a deopt)
    if (deopts++ % 2 == 0) {
      ResourceMark rm(THREAD);
      THROW(vmSymbols::java_lang_OutOfMemoryError());
    } else {
      deopt_caller();
    }
  }
  JRT_BLOCK_END;

  if (ReduceInitialCardMarks) {
    new_store_pre_barrier(thread);
  }
JRT_END

void JVMCIRuntime::new_store_pre_barrier(JavaThread* thread) {
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

JRT_ENTRY(void, JVMCIRuntime::new_multi_array(JavaThread* thread, Klass* klass, int rank, jint* dims))
  assert(klass->is_klass(), "not a class");
  assert(rank >= 1, "rank must be nonzero");
  oop obj = ArrayKlass::cast(klass)->multi_allocate(rank, dims, CHECK);
  thread->set_vm_result(obj);
JRT_END

JRT_ENTRY(void, JVMCIRuntime::dynamic_new_array(JavaThread* thread, oopDesc* element_mirror, jint length))
  oop obj = Reflection::reflect_new_array(element_mirror, length, CHECK);
  thread->set_vm_result(obj);
JRT_END

JRT_ENTRY(void, JVMCIRuntime::dynamic_new_instance(JavaThread* thread, oopDesc* type_mirror))
  instanceKlassHandle klass(THREAD, java_lang_Class::as_Klass(type_mirror));

  if (klass == NULL) {
    ResourceMark rm(THREAD);
    THROW(vmSymbols::java_lang_InstantiationException());
  }

  // Create new instance (the receiver)
  klass->check_valid_for_instantiation(false, CHECK);

  // Make sure klass gets initialized
  klass->initialize(CHECK);

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
JRT_ENTRY_NO_ASYNC(static address, exception_handler_for_pc_helper(JavaThread* thread, oopDesc* ex, address pc, nmethod*& nm))
  // Reset method handle flag.
  thread->set_is_method_handle_return(false);

  Handle exception(thread, ex);
  nm = CodeCache::find_nmethod(pc);
  assert(nm != NULL, "this is not a compiled method");
  // Adjust the pc as needed/
  if (nm->is_deopt_pc(pc)) {
    RegisterMap map(thread, false);
    frame exception_frame = thread->last_frame().sender(&map);
    // if the frame isn't deopted then pc must not correspond to the caller of last_frame
    assert(exception_frame.is_deoptimized_frame(), "must be deopted");
    pc = exception_frame.pc();
  }
#ifdef ASSERT
  assert(exception.not_null(), "NULL exceptions should be handled by throw_exception");
  assert(exception->is_oop(), "just checking");
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
    address fast_continuation = nm->handler_for_exception_and_pc(exception, pc);
    if (fast_continuation != NULL) {
      // Set flag if return address is a method handle call site.
      thread->set_is_method_handle_return(nm->is_method_handle_return(pc));
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
      log_info(exceptions)("Exception <%s> (" INTPTR_FORMAT ") thrown in"
                           " compiled method <%s> at PC " INTPTR_FORMAT
                           " for thread " INTPTR_FORMAT,
                           exception->print_value_string(),
                           p2i((address)exception()),
                           nm->method()->print_value_string(), p2i(pc),
                           p2i(thread));
    }
    // for AbortVMOnException flag
    NOT_PRODUCT(Exceptions::debug_check_abort(exception));

    // Clear out the exception oop and pc since looking up an
    // exception handler can cause class loading, which might throw an
    // exception and those fields are expected to be clear during
    // normal bytecode execution.
    thread->clear_exception_oop_and_pc();

    continuation = SharedRuntime::compute_compiled_exc_handler(nm, pc, exception, false, false);
    // If an exception was thrown during exception dispatch, the exception oop may have changed
    thread->set_exception_oop(exception());
    thread->set_exception_pc(pc);

    // the exception cache is used only by non-implicit exceptions
    if (continuation != NULL && !SharedRuntime::deopt_blob()->contains(continuation)) {
      nm->add_handler_for_exception_and_pc(exception, pc, continuation);
    }
  }

  // Set flag if return address is a method handle call site.
  thread->set_is_method_handle_return(nm->is_method_handle_return(pc));

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
  nmethod* nm = NULL;
  address continuation = NULL;
  {
    // Enter VM mode by calling the helper
    ResetNoHandleMark rnhm;
    continuation = exception_handler_for_pc_helper(thread, exception, pc, nm);
  }
  // Back in JAVA, use no oops DON'T safepoint

  // Now check to see if the compiled method we were called from is now deoptimized.
  // If so we must return to the deopt blob and deoptimize the nmethod
  if (nm != NULL && caller_is_deopted()) {
    continuation = SharedRuntime::deopt_blob()->unpack_with_exception_in_tls();
  }

  assert(continuation != NULL, "no handler found");
  return continuation;
}

JRT_ENTRY(void, JVMCIRuntime::create_null_exception(JavaThread* thread))
  SharedRuntime::throw_and_post_jvmti_exception(thread, vmSymbols::java_lang_NullPointerException());
  thread->set_vm_result(PENDING_EXCEPTION);
  CLEAR_PENDING_EXCEPTION;
JRT_END

JRT_ENTRY(void, JVMCIRuntime::create_out_of_bounds_exception(JavaThread* thread, jint index))
  char message[jintAsStringSize];
  sprintf(message, "%d", index);
  SharedRuntime::throw_and_post_jvmti_exception(thread, vmSymbols::java_lang_ArrayIndexOutOfBoundsException(), message);
  thread->set_vm_result(PENDING_EXCEPTION);
  CLEAR_PENDING_EXCEPTION;
JRT_END

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
  assert(h_obj()->is_oop(), "must be NULL or an object");
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
  if (!obj->is_oop()) {
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

JRT_LEAF(void, JVMCIRuntime::log_object(JavaThread* thread, oopDesc* obj, bool as_string, bool newline))
  ttyLocker ttyl;

  if (obj == NULL) {
    tty->print("NULL");
  } else if (obj->is_oop_or_null(true) && (!as_string || !java_lang_String::is_instance(obj))) {
    if (obj->is_oop_or_null(true)) {
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

JRT_LEAF(void, JVMCIRuntime::write_barrier_pre(JavaThread* thread, oopDesc* obj))
  thread->satb_mark_queue().enqueue(obj);
JRT_END

JRT_LEAF(void, JVMCIRuntime::write_barrier_post(JavaThread* thread, void* card_addr))
  thread->dirty_card_queue().enqueue(card_addr);
JRT_END

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
JRT_LEAF(void, JVMCIRuntime::log_printf(JavaThread* thread, oopDesc* format, jlong v1, jlong v2, jlong v3))
  ResourceMark rm;
  assert(format != NULL && java_lang_String::is_instance(format), "must be");
  char *buf = java_lang_String::as_utf8_string(format);
  tty->print((const char*)buf, v1, v2, v3);
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
    case 'z': tty->print(value == 0 ? "false" : "true"); break;
    case 'b': tty->print("%d", (jbyte) value); break;
    case 'c': tty->print("%c", (jchar) value); break;
    case 's': tty->print("%d", (jshort) value); break;
    case 'i': tty->print("%d", (jint) value); break;
    case 'f': tty->print("%f", uu.f); break;
    case 'j': tty->print(JLONG_FORMAT, value); break;
    case 'd': tty->print("%lf", uu.d); break;
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
  // Ensure that the C++ Thread and OSThread structures aren't freed before we operate.
  // This locking requires thread_in_vm which is why this method cannot be JRT_LEAF.
  Handle receiverHandle(thread, receiver);
  MutexLockerEx ml(thread->threadObj() == (void*)receiver ? NULL : Threads_lock);
  JavaThread* receiverThread = java_lang_Thread::thread(receiverHandle());
  if (receiverThread == NULL) {
    // The other thread may exit during this process, which is ok so return false.
    return JNI_FALSE;
  } else {
    return (jint) Thread::is_interrupted(receiverThread, clear_interrupted != 0);
  }
JRT_END

JRT_ENTRY(jint, JVMCIRuntime::test_deoptimize_call_int(JavaThread* thread, int value))
  deopt_caller();
  return value;
JRT_END

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
  guarantee(!_HotSpotJVMCIRuntime_initialized, "cannot reinitialize HotSpotJVMCIRuntime");

  TempNewSymbol name = SymbolTable::new_symbol(className, CHECK_(Handle()));
  KlassHandle klass = SystemDictionary::resolve_or_fail(name, true, CHECK_(Handle()));
  TempNewSymbol runtime = SymbolTable::new_symbol(methodName, CHECK_(Handle()));
  TempNewSymbol sig = SymbolTable::new_symbol(signature, CHECK_(Handle()));
  JavaValue result(T_OBJECT);
  if (args == NULL) {
    JavaCalls::call_static(&result, klass, runtime, sig, CHECK_(Handle()));
  } else {
    JavaCalls::call_static(&result, klass, runtime, sig, args, CHECK_(Handle()));
  }
  return Handle((oop)result.get_jobject());
}

static bool jvmci_options_file_exists() {
  const char* home = Arguments::get_java_home();
  size_t path_len = strlen(home) + strlen("/lib/jvmci.options") + 1;
  char path[JVM_MAXPATHLEN];
  char sep = os::file_separator()[0];
  jio_snprintf(path, JVM_MAXPATHLEN, "%s%clib%cjvmci.options", home, sep, sep);
  struct stat st;
  return os::stat(path, &st) == 0;
}

void JVMCIRuntime::initialize_HotSpotJVMCIRuntime(TRAPS) {
  if (JNIHandles::resolve(_HotSpotJVMCIRuntime_instance) == NULL) {
#ifdef ASSERT
    // This should only be called in the context of the JVMCI class being initialized
    TempNewSymbol name = SymbolTable::new_symbol("jdk/vm/ci/runtime/JVMCI", CHECK);
    Klass* k = SystemDictionary::resolve_or_null(name, CHECK);
    instanceKlassHandle klass = InstanceKlass::cast(k);
    assert(klass->is_being_initialized() && klass->is_reentrant_initialization(THREAD),
           "HotSpotJVMCIRuntime initialization should only be triggered through JVMCI initialization");
#endif

    bool parseOptionsFile = jvmci_options_file_exists();
    if (_options != NULL || parseOptionsFile) {
      JavaCallArguments args;
      objArrayOop options;
      if (_options != NULL) {
        options = oopFactory::new_objArray(SystemDictionary::String_klass(), _options_count * 2, CHECK);
        for (int i = 0; i < _options_count; i++) {
          SystemProperty* prop = _options[i];
          oop name = java_lang_String::create_oop_from_str(prop->key() + OPTION_PREFIX_LEN, CHECK);
          const char* prop_value = prop->value() != NULL ? prop->value() : "";
          oop value = java_lang_String::create_oop_from_str(prop_value, CHECK);
          options->obj_at_put(i * 2, name);
          options->obj_at_put((i * 2) + 1, value);
        }
      } else {
        options = NULL;
      }
      args.push_oop(options);
      args.push_int(parseOptionsFile);
      callStatic("jdk/vm/ci/options/OptionsParser",
                 "parseOptionsFromVM",
                 "([Ljava/lang/String;Z)Ljava/lang/Boolean;", &args, CHECK);
    }

    if (_compiler != NULL) {
      JavaCallArguments args;
      oop compiler = java_lang_String::create_oop_from_str(_compiler, CHECK);
      args.push_oop(compiler);
      callStatic("jdk/vm/ci/hotspot/HotSpotJVMCICompilerConfig",
                 "selectCompiler",
                 "(Ljava/lang/String;)Ljava/lang/Boolean;", &args, CHECK);
    }

    Handle result = callStatic("jdk/vm/ci/hotspot/HotSpotJVMCIRuntime",
                               "runtime",
                               "()Ljdk/vm/ci/hotspot/HotSpotJVMCIRuntime;", NULL, CHECK);
    objArrayOop trivial_prefixes = HotSpotJVMCIRuntime::trivialPrefixes(result);
    if (trivial_prefixes != NULL) {
      char** prefixes = NEW_C_HEAP_ARRAY(char*, trivial_prefixes->length(), mtCompiler);
      for (int i = 0; i < trivial_prefixes->length(); i++) {
        oop str = trivial_prefixes->obj_at(i);
        if (str == NULL) {
          THROW(vmSymbols::java_lang_NullPointerException());
        } else {
          prefixes[i] = strdup(java_lang_String::as_utf8_string(str));
        }
      }
      _trivial_prefixes = prefixes;
      _trivial_prefixes_count = trivial_prefixes->length();
    }
    _HotSpotJVMCIRuntime_initialized = true;
    _HotSpotJVMCIRuntime_instance = JNIHandles::make_global(result());
  }
}

void JVMCIRuntime::initialize_JVMCI(TRAPS) {
  if (JNIHandles::resolve(_HotSpotJVMCIRuntime_instance) == NULL) {
    callStatic("jdk/vm/ci/runtime/JVMCI",
               "getRuntime",
               "()Ljdk/vm/ci/runtime/JVMCIRuntime;", NULL, CHECK);
  }
  assert(_HotSpotJVMCIRuntime_initialized == true, "what?");
}

void JVMCIRuntime::initialize_well_known_classes(TRAPS) {
  if (JVMCIRuntime::_well_known_classes_initialized == false) {
    SystemDictionary::WKID scan = SystemDictionary::FIRST_JVMCI_WKID;
    SystemDictionary::initialize_wk_klasses_through(SystemDictionary::LAST_JVMCI_WKID, scan, CHECK);
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
#ifndef TARGET_ARCH_sparc
  uintptr_t heap_end = (uintptr_t) Universe::heap()->reserved_region().end();
  uintptr_t allocation_end = heap_end + ((uintptr_t)16) * 1024 * 1024 * 1024;
  guarantee(heap_end < allocation_end, "heap end too close to end of address space (might lead to erroneous TLAB allocations)");
#endif // TARGET_ARCH_sparc
#else
  fatal("check TLAB allocation code for address space conflicts");
#endif

  JVMCIRuntime::initialize_well_known_classes(CHECK);

  {
    ThreadToNativeFromVM trans(thread);

    // Ensure _non_oop_bits is initialized
    Universe::non_oop_word();

    env->RegisterNatives(c2vmClass, CompilerToVM::methods, CompilerToVM::methods_count());
  }
JVM_END

/**
 * Closure for parsing a line from a *.properties file in jre/lib/jvmci/properties.
 * The line must match the regular expression "[^=]+=.*". That is one or more
 * characters other than '=' followed by '=' followed by zero or more characters.
 * Everything before the '=' is the property name and everything after '=' is the value.
 * Lines that start with '#' are treated as comments and ignored.
 * No special processing of whitespace or any escape characters is performed.
 * The last definition of a property "wins" (i.e., it overrides all earlier
 * definitions of the property).
 */
class JVMCIPropertiesFileClosure : public ParseClosure {
  SystemProperty** _plist;
public:
  JVMCIPropertiesFileClosure(SystemProperty** plist) : _plist(plist) {}
  void do_line(char* line) {
    if (line[0] == '#') {
      // skip comment
      return;
    }
    size_t len = strlen(line);
    char* sep = strchr(line, '=');
    if (sep == NULL) {
      warn_and_abort("invalid format: could not find '=' character");
      return;
    }
    if (sep == line) {
      warn_and_abort("invalid format: name cannot be empty");
      return;
    }
    *sep = '\0';
    const char* name = line;
    char* value = sep + 1;
    Arguments::PropertyList_unique_add(_plist, name, value);
  }
};

void JVMCIRuntime::init_system_properties(SystemProperty** plist) {
  char jvmciDir[JVM_MAXPATHLEN];
  const char* fileSep = os::file_separator();
  jio_snprintf(jvmciDir, sizeof(jvmciDir), "%s%slib%sjvmci",
               Arguments::get_java_home(), fileSep, fileSep, fileSep);
  DIR* dir = os::opendir(jvmciDir);
  if (dir != NULL) {
    struct dirent *entry;
    char *dbuf = NEW_C_HEAP_ARRAY(char, os::readdir_buf_size(jvmciDir), mtInternal);
    JVMCIPropertiesFileClosure closure(plist);
    const unsigned suffix_len = (unsigned)strlen(".properties");
    while ((entry = os::readdir(dir, (dirent *) dbuf)) != NULL && !closure.is_aborted()) {
      const char* name = entry->d_name;
      if (strlen(name) > suffix_len && strcmp(name + strlen(name) - suffix_len, ".properties") == 0) {
        char propertiesFilePath[JVM_MAXPATHLEN];
        jio_snprintf(propertiesFilePath, sizeof(propertiesFilePath), "%s%s%s",jvmciDir, fileSep, name);
        JVMCIRuntime::parse_lines(propertiesFilePath, &closure, false);
      }
    }
    FREE_C_HEAP_ARRAY(char, dbuf);
    os::closedir(dir);
  }
}

#define CHECK_WARN_ABORT_(message) THREAD); \
  if (HAS_PENDING_EXCEPTION) { \
    warning(message); \
    char buf[512]; \
    jio_snprintf(buf, 512, "Uncaught exception at %s:%d", __FILE__, __LINE__); \
    JVMCIRuntime::abort_on_pending_exception(PENDING_EXCEPTION, buf); \
    return; \
  } \
  (void)(0

void JVMCIRuntime::save_compiler(const char* compiler) {
  assert(compiler != NULL, "npe");
  assert(_compiler == NULL, "cannot reassign JVMCI compiler");
  _compiler = compiler;
}

void JVMCIRuntime::maybe_print_flags(TRAPS) {
  if (_options != NULL) {
    for (int i = 0; i < _options_count; i++) {
      SystemProperty* p = _options[i];
      const char* name = p->key() + OPTION_PREFIX_LEN;
      if (strcmp(name, "PrintFlags") == 0 || strcmp(name, "ShowFlags") == 0) {
        JVMCIRuntime::initialize_well_known_classes(CHECK);
        HandleMark hm;
        ResourceMark rm;
        JVMCIRuntime::get_HotSpotJVMCIRuntime(CHECK);
        return;
      }
    }
  }
}

void JVMCIRuntime::save_options(SystemProperty* props) {
  int count = 0;
  SystemProperty* first = NULL;
  for (SystemProperty* p = props; p != NULL; p = p->next()) {
    if (strncmp(p->key(), OPTION_PREFIX, OPTION_PREFIX_LEN) == 0) {
      if (first == NULL) {
        first = p;
      }
      count++;
    }
  }
  if (count != 0) {
    _options_count = count;
    _options = NEW_C_HEAP_ARRAY(SystemProperty*, count, mtCompiler);
    _options[0] = first;
    SystemProperty** insert_pos = _options + 1;
    for (SystemProperty* p = first->next(); p != NULL; p = p->next()) {
      if (strncmp(p->key(), OPTION_PREFIX, OPTION_PREFIX_LEN) == 0) {
        *insert_pos = p;
        insert_pos++;
      }
    }
    assert (insert_pos - _options == count, "must be");
  }
}

void JVMCIRuntime::shutdown() {
  if (_HotSpotJVMCIRuntime_instance != NULL) {
    _shutdown_called = true;
    JavaThread* THREAD = JavaThread::current();
    HandleMark hm(THREAD);
    Handle receiver = get_HotSpotJVMCIRuntime(CHECK_ABORT);
    JavaValue result(T_VOID);
    JavaCallArguments args;
    args.push_oop(receiver);
    JavaCalls::call_special(&result, receiver->klass(), vmSymbols::shutdown_method_name(), vmSymbols::void_method_signature(), &args, CHECK_ABORT);
  }
}

bool JVMCIRuntime::treat_as_trivial(Method* method) {
  if (_HotSpotJVMCIRuntime_initialized) {
    oop loader = method->method_holder()->class_loader();
    if (loader == NULL) {
      for (int i = 0; i < _trivial_prefixes_count; i++) {
        if (method->method_holder()->name()->starts_with(_trivial_prefixes[i])) {
          return true;
        }
      }
    }
  }
  return false;
}

void JVMCIRuntime::call_printStackTrace(Handle exception, Thread* thread) {
  assert(exception->is_a(SystemDictionary::Throwable_klass()), "Throwable instance expected");
  JavaValue result(T_VOID);
  JavaCalls::call_virtual(&result,
                          exception,
                          KlassHandle(thread,
                          SystemDictionary::Throwable_klass()),
                          vmSymbols::printStackTrace_name(),
                          vmSymbols::void_method_signature(),
                          thread);
}

void JVMCIRuntime::abort_on_pending_exception(Handle exception, const char* message, bool dump_core) {
  Thread* THREAD = Thread::current();
  CLEAR_PENDING_EXCEPTION;
  tty->print_raw_cr(message);
  call_printStackTrace(exception, THREAD);

  // Give other aborting threads to also print their stack traces.
  // This can be very useful when debugging class initialization
  // failures.
  os::sleep(THREAD, 200, false);

  vm_abort(dump_core);
}

void JVMCIRuntime::parse_lines(char* path, ParseClosure* closure, bool warnStatFailure) {
  struct stat st;
  if (::stat(path, &st) == 0 && (st.st_mode & S_IFREG) == S_IFREG) { // exists & is regular file
    int file_handle = ::open(path, os::default_file_open_flags(), 0);
    if (file_handle != -1) {
      char* buffer = NEW_C_HEAP_ARRAY(char, st.st_size + 1, mtInternal);
      int num_read;
      num_read = (int) ::read(file_handle, (char*) buffer, st.st_size);
      if (num_read == -1) {
        warning("Error reading file %s due to %s", path, strerror(errno));
      } else if (num_read != st.st_size) {
        warning("Only read %d of " SIZE_FORMAT " bytes from %s", num_read, (size_t) st.st_size, path);
      }
      ::close(file_handle);
      closure->set_filename(path);
      if (num_read == st.st_size) {
        buffer[num_read] = '\0';

        char* line = buffer;
        while (line - buffer < num_read && !closure->is_aborted()) {
          // find line end (\r, \n or \r\n)
          char* nextline = NULL;
          char* cr = strchr(line, '\r');
          char* lf = strchr(line, '\n');
          if (cr != NULL && lf != NULL) {
            char* min = MIN2(cr, lf);
            *min = '\0';
            if (lf == cr + 1) {
              nextline = lf + 1;
            } else {
              nextline = min + 1;
            }
          } else if (cr != NULL) {
            *cr = '\0';
            nextline = cr + 1;
          } else if (lf != NULL) {
            *lf = '\0';
            nextline = lf + 1;
          }
          // trim left
          while (*line == ' ' || *line == '\t') line++;
          char* end = line + strlen(line);
          // trim right
          while (end > line && (*(end -1) == ' ' || *(end -1) == '\t')) end--;
          *end = '\0';
          // skip comments and empty lines
          if (*line != '#' && strlen(line) > 0) {
            closure->parse_line(line);
          }
          if (nextline != NULL) {
            line = nextline;
          } else {
            // File without newline at the end
            break;
          }
        }
      }
      FREE_C_HEAP_ARRAY(char, buffer);
    } else {
      warning("Error opening file %s due to %s", path, strerror(errno));
    }
  } else if (warnStatFailure) {
    warning("Could not stat file %s due to %s", path, strerror(errno));
  }
}
