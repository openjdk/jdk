/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "code/nmethod.hpp"
#include "compiler/compileBroker.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/linkResolver.hpp"
#include "memory/universe.inline.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jniCheck.hpp"
#include "runtime/compilationPolicy.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/signature.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/thread.inline.hpp"
#if INCLUDE_JVMCI
#include "jvmci/jvmciJavaClasses.hpp"
#include "jvmci/jvmciRuntime.hpp"
#endif

// -----------------------------------------------------
// Implementation of JavaCallWrapper

JavaCallWrapper::JavaCallWrapper(methodHandle callee_method, Handle receiver, JavaValue* result, TRAPS) {
  JavaThread* thread = (JavaThread *)THREAD;
  bool clear_pending_exception = true;

  guarantee(thread->is_Java_thread(), "crucial check - the VM thread cannot and must not escape to Java code");
  assert(!thread->owns_locks(), "must release all locks when leaving VM");
  guarantee(thread->can_call_java(), "cannot make java calls from the native compiler");
  _result   = result;

  // Allocate handle block for Java code. This must be done before we change thread_state to _thread_in_Java_or_stub,
  // since it can potentially block.
  JNIHandleBlock* new_handles = JNIHandleBlock::allocate_block(thread);

  // After this, we are official in JavaCode. This needs to be done before we change any of the thread local
  // info, since we cannot find oops before the new information is set up completely.
  ThreadStateTransition::transition(thread, _thread_in_vm, _thread_in_Java);

  // Make sure that we handle asynchronous stops and suspends _before_ we clear all thread state
  // in JavaCallWrapper::JavaCallWrapper(). This way, we can decide if we need to do any pd actions
  // to prepare for stop/suspend (flush register windows on sparcs, cache sp, or other state).
  if (thread->has_special_runtime_exit_condition()) {
    thread->handle_special_runtime_exit_condition();
    if (HAS_PENDING_EXCEPTION) {
      clear_pending_exception = false;
    }
  }


  // Make sure to set the oop's after the thread transition - since we can block there. No one is GC'ing
  // the JavaCallWrapper before the entry frame is on the stack.
  _callee_method = callee_method();
  _receiver = receiver();

#ifdef CHECK_UNHANDLED_OOPS
  THREAD->allow_unhandled_oop(&_receiver);
#endif // CHECK_UNHANDLED_OOPS

  _thread       = (JavaThread *)thread;
  _handles      = _thread->active_handles();    // save previous handle block & Java frame linkage

  // For the profiler, the last_Java_frame information in thread must always be in
  // legal state. We have no last Java frame if last_Java_sp == NULL so
  // the valid transition is to clear _last_Java_sp and then reset the rest of
  // the (platform specific) state.

  _anchor.copy(_thread->frame_anchor());
  _thread->frame_anchor()->clear();

  debug_only(_thread->inc_java_call_counter());
  _thread->set_active_handles(new_handles);     // install new handle block and reset Java frame linkage

  assert (_thread->thread_state() != _thread_in_native, "cannot set native pc to NULL");

  // clear any pending exception in thread (native calls start with no exception pending)
  if(clear_pending_exception) {
    _thread->clear_pending_exception();
  }

  if (_anchor.last_Java_sp() == NULL) {
    _thread->record_base_of_stack_pointer();
  }
}


JavaCallWrapper::~JavaCallWrapper() {
  assert(_thread == JavaThread::current(), "must still be the same thread");

  // restore previous handle block & Java frame linkage
  JNIHandleBlock *_old_handles = _thread->active_handles();
  _thread->set_active_handles(_handles);

  _thread->frame_anchor()->zap();

  debug_only(_thread->dec_java_call_counter());

  if (_anchor.last_Java_sp() == NULL) {
    _thread->set_base_of_stack_pointer(NULL);
  }


  // Old thread-local info. has been restored. We are not back in the VM.
  ThreadStateTransition::transition_from_java(_thread, _thread_in_vm);

  // State has been restored now make the anchor frame visible for the profiler.
  // Do this after the transition because this allows us to put an assert
  // the Java->vm transition which checks to see that stack is not walkable
  // on sparc/ia64 which will catch violations of the reseting of last_Java_frame
  // invariants (i.e. _flags always cleared on return to Java)

  _thread->frame_anchor()->copy(&_anchor);

  // Release handles after we are marked as being inside the VM again, since this
  // operation might block
  JNIHandleBlock::release_block(_old_handles, _thread);
}


void JavaCallWrapper::oops_do(OopClosure* f) {
  f->do_oop((oop*)&_receiver);
  handles()->oops_do(f);
}


// Helper methods
static BasicType runtime_type_from(JavaValue* result) {
  switch (result->get_type()) {
    case T_BOOLEAN: // fall through
    case T_CHAR   : // fall through
    case T_SHORT  : // fall through
    case T_INT    : // fall through
#ifndef _LP64
    case T_OBJECT : // fall through
    case T_ARRAY  : // fall through
#endif
    case T_BYTE   : // fall through
    case T_VOID   : return T_INT;
    case T_LONG   : return T_LONG;
    case T_FLOAT  : return T_FLOAT;
    case T_DOUBLE : return T_DOUBLE;
#ifdef _LP64
    case T_ARRAY  : // fall through
    case T_OBJECT:  return T_OBJECT;
#endif
  }
  ShouldNotReachHere();
  return T_ILLEGAL;
}

// ============ Virtual calls ============

void JavaCalls::call_virtual(JavaValue* result, KlassHandle spec_klass, Symbol* name, Symbol* signature, JavaCallArguments* args, TRAPS) {
  CallInfo callinfo;
  Handle receiver = args->receiver();
  KlassHandle recvrKlass(THREAD, receiver.is_null() ? (Klass*)NULL : receiver->klass());
  LinkInfo link_info(spec_klass, name, signature);
  LinkResolver::resolve_virtual_call(
          callinfo, receiver, recvrKlass, link_info, true, CHECK);
  methodHandle method = callinfo.selected_method();
  assert(method.not_null(), "should have thrown exception");

  // Invoke the method
  JavaCalls::call(result, method, args, CHECK);
}


void JavaCalls::call_virtual(JavaValue* result, Handle receiver, KlassHandle spec_klass, Symbol* name, Symbol* signature, TRAPS) {
  JavaCallArguments args(receiver); // One oop argument
  call_virtual(result, spec_klass, name, signature, &args, CHECK);
}


void JavaCalls::call_virtual(JavaValue* result, Handle receiver, KlassHandle spec_klass, Symbol* name, Symbol* signature, Handle arg1, TRAPS) {
  JavaCallArguments args(receiver); // One oop argument
  args.push_oop(arg1);
  call_virtual(result, spec_klass, name, signature, &args, CHECK);
}



void JavaCalls::call_virtual(JavaValue* result, Handle receiver, KlassHandle spec_klass, Symbol* name, Symbol* signature, Handle arg1, Handle arg2, TRAPS) {
  JavaCallArguments args(receiver); // One oop argument
  args.push_oop(arg1);
  args.push_oop(arg2);
  call_virtual(result, spec_klass, name, signature, &args, CHECK);
}


// ============ Special calls ============

void JavaCalls::call_special(JavaValue* result, KlassHandle klass, Symbol* name, Symbol* signature, JavaCallArguments* args, TRAPS) {
  CallInfo callinfo;
  LinkInfo link_info(klass, name, signature);
  LinkResolver::resolve_special_call(callinfo, args->receiver(), link_info, CHECK);
  methodHandle method = callinfo.selected_method();
  assert(method.not_null(), "should have thrown exception");

  // Invoke the method
  JavaCalls::call(result, method, args, CHECK);
}


void JavaCalls::call_special(JavaValue* result, Handle receiver, KlassHandle klass, Symbol* name, Symbol* signature, TRAPS) {
  JavaCallArguments args(receiver); // One oop argument
  call_special(result, klass, name, signature, &args, CHECK);
}


void JavaCalls::call_special(JavaValue* result, Handle receiver, KlassHandle klass, Symbol* name, Symbol* signature, Handle arg1, TRAPS) {
  JavaCallArguments args(receiver); // One oop argument
  args.push_oop(arg1);
  call_special(result, klass, name, signature, &args, CHECK);
}


void JavaCalls::call_special(JavaValue* result, Handle receiver, KlassHandle klass, Symbol* name, Symbol* signature, Handle arg1, Handle arg2, TRAPS) {
  JavaCallArguments args(receiver); // One oop argument
  args.push_oop(arg1);
  args.push_oop(arg2);
  call_special(result, klass, name, signature, &args, CHECK);
}


// ============ Static calls ============

void JavaCalls::call_static(JavaValue* result, KlassHandle klass, Symbol* name, Symbol* signature, JavaCallArguments* args, TRAPS) {
  CallInfo callinfo;
  LinkInfo link_info(klass, name, signature);
  LinkResolver::resolve_static_call(callinfo, link_info, true, CHECK);
  methodHandle method = callinfo.selected_method();
  assert(method.not_null(), "should have thrown exception");

  // Invoke the method
  JavaCalls::call(result, method, args, CHECK);
}


void JavaCalls::call_static(JavaValue* result, KlassHandle klass, Symbol* name, Symbol* signature, TRAPS) {
  JavaCallArguments args; // No argument
  call_static(result, klass, name, signature, &args, CHECK);
}


void JavaCalls::call_static(JavaValue* result, KlassHandle klass, Symbol* name, Symbol* signature, Handle arg1, TRAPS) {
  JavaCallArguments args(arg1); // One oop argument
  call_static(result, klass, name, signature, &args, CHECK);
}


void JavaCalls::call_static(JavaValue* result, KlassHandle klass, Symbol* name, Symbol* signature, Handle arg1, Handle arg2, TRAPS) {
  JavaCallArguments args; // One oop argument
  args.push_oop(arg1);
  args.push_oop(arg2);
  call_static(result, klass, name, signature, &args, CHECK);
}


void JavaCalls::call_static(JavaValue* result, KlassHandle klass, Symbol* name, Symbol* signature, Handle arg1, Handle arg2, Handle arg3, TRAPS) {
  JavaCallArguments args; // One oop argument
  args.push_oop(arg1);
  args.push_oop(arg2);
  args.push_oop(arg3);
  call_static(result, klass, name, signature, &args, CHECK);
}

// -------------------------------------------------
// Implementation of JavaCalls (low level)


void JavaCalls::call(JavaValue* result, const methodHandle& method, JavaCallArguments* args, TRAPS) {
  // Check if we need to wrap a potential OS exception handler around thread
  // This is used for e.g. Win32 structured exception handlers
  assert(THREAD->is_Java_thread(), "only JavaThreads can make JavaCalls");
  // Need to wrap each and every time, since there might be native code down the
  // stack that has installed its own exception handlers
  os::os_exception_wrapper(call_helper, result, method, args, THREAD);
}

void JavaCalls::call_helper(JavaValue* result, const methodHandle& method, JavaCallArguments* args, TRAPS) {
  // During dumping, Java execution environment is not fully initialized. Also, Java execution
  // may cause undesirable side-effects in the class metadata.
  assert(!DumpSharedSpaces, "must not execute Java bytecodes when dumping");

  JavaThread* thread = (JavaThread*)THREAD;
  assert(thread->is_Java_thread(), "must be called by a java thread");
  assert(method.not_null(), "must have a method to call");
  assert(!SafepointSynchronize::is_at_safepoint(), "call to Java code during VM operation");
  assert(!thread->handle_area()->no_handle_mark_active(), "cannot call out to Java here");


  CHECK_UNHANDLED_OOPS_ONLY(thread->clear_unhandled_oops();)

#if INCLUDE_JVMCI
  // Gets the nmethod (if any) that should be called instead of normal target
  nmethod* alternative_target = args->alternative_target();
  if (alternative_target == NULL) {
#endif
// Verify the arguments

  if (CheckJNICalls)  {
    args->verify(method, result->get_type());
  }
  else debug_only(args->verify(method, result->get_type()));
#if INCLUDE_JVMCI
  }
#else

  // Ignore call if method is empty
  if (method->is_empty_method()) {
    assert(result->get_type() == T_VOID, "an empty method must return a void value");
    return;
  }
#endif

#ifdef ASSERT
  { InstanceKlass* holder = method->method_holder();
    // A klass might not be initialized since JavaCall's might be used during the executing of
    // the <clinit>. For example, a Thread.start might start executing on an object that is
    // not fully initialized! (bad Java programming style)
    assert(holder->is_linked(), "rewriting must have taken place");
  }
#endif

  CompilationPolicy::compile_if_required(method, CHECK);

  // Since the call stub sets up like the interpreter we call the from_interpreted_entry
  // so we can go compiled via a i2c. Otherwise initial entry method will always
  // run interpreted.
  address entry_point = method->from_interpreted_entry();
  if (JvmtiExport::can_post_interpreter_events() && thread->is_interp_only_mode()) {
    entry_point = method->interpreter_entry();
  }

  // Figure out if the result value is an oop or not (Note: This is a different value
  // than result_type. result_type will be T_INT of oops. (it is about size)
  BasicType result_type = runtime_type_from(result);
  bool oop_result_flag = (result->get_type() == T_OBJECT || result->get_type() == T_ARRAY);

  // NOTE: if we move the computation of the result_val_address inside
  // the call to call_stub, the optimizer produces wrong code.
  intptr_t* result_val_address = (intptr_t*)(result->get_value_addr());

  // Find receiver
  Handle receiver = (!method->is_static()) ? args->receiver() : Handle();

  // When we reenter Java, we need to reenable the reserved/yellow zone which
  // might already be disabled when we are in VM.
  if (!thread->stack_guards_enabled()) {
    thread->reguard_stack();
  }

  // Check that there are shadow pages available before changing thread state
  // to Java. Calculate current_stack_pointer here to make sure
  // stack_shadow_pages_available() and bang_stack_shadow_pages() use the same sp.
  address sp = os::current_stack_pointer();
  if (!os::stack_shadow_pages_available(THREAD, method, sp)) {
    // Throw stack overflow exception with preinitialized exception.
    Exceptions::throw_stack_overflow_exception(THREAD, __FILE__, __LINE__, method);
    return;
  } else {
    // Touch pages checked if the OS needs them to be touched to be mapped.
    os::map_stack_shadow_pages(sp);
  }

#if INCLUDE_JVMCI
  if (alternative_target != NULL) {
    if (alternative_target->is_alive()) {
      thread->set_jvmci_alternate_call_target(alternative_target->verified_entry_point());
      entry_point = method->adapter()->get_i2c_entry();
    } else {
      THROW(vmSymbols::jdk_vm_ci_code_InvalidInstalledCodeException());
    }
  }
#endif

  // do call
  { JavaCallWrapper link(method, receiver, result, CHECK);
    { HandleMark hm(thread);  // HandleMark used by HandleMarkCleaner

      StubRoutines::call_stub()(
        (address)&link,
        // (intptr_t*)&(result->_value), // see NOTE above (compiler problem)
        result_val_address,          // see NOTE above (compiler problem)
        result_type,
        method(),
        entry_point,
        args->parameters(),
        args->size_of_parameters(),
        CHECK
      );

      result = link.result();  // circumvent MS C++ 5.0 compiler bug (result is clobbered across call)
      // Preserve oop return value across possible gc points
      if (oop_result_flag) {
        thread->set_vm_result((oop) result->get_jobject());
      }
    }
  } // Exit JavaCallWrapper (can block - potential return oop must be preserved)

  // Check if a thread stop or suspend should be executed
  // The following assert was not realistic.  Thread.stop can set that bit at any moment.
  //assert(!thread->has_special_runtime_exit_condition(), "no async. exceptions should be installed");

  // Restore possible oop return
  if (oop_result_flag) {
    result->set_jobject((jobject)thread->vm_result());
    thread->set_vm_result(NULL);
  }
}


//--------------------------------------------------------------------------------------
// Implementation of JavaCallArguments

inline bool is_value_state_indirect_oop(uint state) {
  assert(state != JavaCallArguments::value_state_oop,
         "Checking for handles after removal");
  assert(state < JavaCallArguments::value_state_limit,
         "Invalid value state %u", state);
  return state != JavaCallArguments::value_state_primitive;
}

inline oop resolve_indirect_oop(intptr_t value, uint state) {
  switch (state) {
  case JavaCallArguments::value_state_handle:
  {
    oop* ptr = reinterpret_cast<oop*>(value);
    return Handle::raw_resolve(ptr);
  }

  case JavaCallArguments::value_state_jobject:
  {
    jobject obj = reinterpret_cast<jobject>(value);
    return JNIHandles::resolve(obj);
  }

  default:
    ShouldNotReachHere();
    return NULL;
  }
}

intptr_t* JavaCallArguments::parameters() {
  // First convert all handles to oops
  for(int i = 0; i < _size; i++) {
    uint state = _value_state[i];
    assert(state != value_state_oop, "Multiple handle conversions");
    if (is_value_state_indirect_oop(state)) {
      oop obj = resolve_indirect_oop(_value[i], state);
      _value[i] = cast_from_oop<intptr_t>(obj);
      _value_state[i] = value_state_oop;
    }
  }
  // Return argument vector
  return _value;
}


class SignatureChekker : public SignatureIterator {
 private:
   int _pos;
   BasicType _return_type;
   u_char* _value_state;
   intptr_t* _value;

 public:
  bool _is_return;

  SignatureChekker(Symbol* signature,
                   BasicType return_type,
                   bool is_static,
                   u_char* value_state,
                   intptr_t* value) :
    SignatureIterator(signature),
    _pos(0),
    _return_type(return_type),
    _value_state(value_state),
    _value(value),
    _is_return(false)
  {
    if (!is_static) {
      check_value(true); // Receiver must be an oop
    }
  }

  void check_value(bool type) {
    uint state = _value_state[_pos++];
    if (type) {
      guarantee(is_value_state_indirect_oop(state),
                "signature does not match pushed arguments: %u at %d",
                state, _pos - 1);
    } else {
      guarantee(state == JavaCallArguments::value_state_primitive,
                "signature does not match pushed arguments: %u at %d",
                state, _pos - 1);
    }
  }

  void check_doing_return(bool state) { _is_return = state; }

  void check_return_type(BasicType t) {
    guarantee(_is_return && t == _return_type, "return type does not match");
  }

  void check_int(BasicType t) {
    if (_is_return) {
      check_return_type(t);
      return;
    }
    check_value(false);
  }

  void check_double(BasicType t) { check_long(t); }

  void check_long(BasicType t) {
    if (_is_return) {
      check_return_type(t);
      return;
    }

    check_value(false);
    check_value(false);
  }

  void check_obj(BasicType t) {
    if (_is_return) {
      check_return_type(t);
      return;
    }

    intptr_t v = _value[_pos];
    if (v != 0) {
      // v is a "handle" referring to an oop, cast to integral type.
      // There shouldn't be any handles in very low memory.
      guarantee((size_t)v >= (size_t)os::vm_page_size(),
                "Bad JNI oop argument %d: " PTR_FORMAT, _pos, v);
      // Verify the pointee.
      oop vv = resolve_indirect_oop(v, _value_state[_pos]);
      guarantee(vv->is_oop_or_null(true),
                "Bad JNI oop argument %d: " PTR_FORMAT " -> " PTR_FORMAT,
                _pos, v, p2i(vv));
    }

    check_value(true);          // Verify value state.
  }

  void do_bool()                       { check_int(T_BOOLEAN);       }
  void do_char()                       { check_int(T_CHAR);          }
  void do_float()                      { check_int(T_FLOAT);         }
  void do_double()                     { check_double(T_DOUBLE);     }
  void do_byte()                       { check_int(T_BYTE);          }
  void do_short()                      { check_int(T_SHORT);         }
  void do_int()                        { check_int(T_INT);           }
  void do_long()                       { check_long(T_LONG);         }
  void do_void()                       { check_return_type(T_VOID);  }
  void do_object(int begin, int end)   { check_obj(T_OBJECT);        }
  void do_array(int begin, int end)    { check_obj(T_OBJECT);        }
};


void JavaCallArguments::verify(const methodHandle& method, BasicType return_type) {
  guarantee(method->size_of_parameters() == size_of_parameters(), "wrong no. of arguments pushed");

  // Treat T_OBJECT and T_ARRAY as the same
  if (return_type == T_ARRAY) return_type = T_OBJECT;

  // Check that oop information is correct
  Symbol* signature = method->signature();

  SignatureChekker sc(signature,
                      return_type,
                      method->is_static(),
                      _value_state,
                      _value);
  sc.iterate_parameters();
  sc.check_doing_return(true);
  sc.iterate_returntype();
}
