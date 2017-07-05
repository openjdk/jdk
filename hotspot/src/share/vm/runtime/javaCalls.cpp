/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_javaCalls.cpp.incl"

// -----------------------------------------------------
// Implementation of JavaCallWrapper

JavaCallWrapper::JavaCallWrapper(methodHandle callee_method, Handle receiver, JavaValue* result, TRAPS) {
  JavaThread* thread = (JavaThread *)THREAD;
  bool clear_pending_exception = true;

  guarantee(thread->is_Java_thread(), "crucial check - the VM thread cannot and must not escape to Java code");
  assert(!thread->owns_locks(), "must release all locks when leaving VM");
  guarantee(!thread->is_Compiler_thread(), "cannot make java calls from the compiler");
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
  THREAD->allow_unhandled_oop(&_callee_method);
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
  f->do_oop((oop*)&_callee_method);
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

// ===== object constructor calls =====

void JavaCalls::call_default_constructor(JavaThread* thread, methodHandle method, Handle receiver, TRAPS) {
  assert(method->name() == vmSymbols::object_initializer_name(),    "Should only be called for default constructor");
  assert(method->signature() == vmSymbols::void_method_signature(), "Should only be called for default constructor");

  instanceKlass* ik = instanceKlass::cast(method->method_holder());
  if (ik->is_initialized() && ik->has_vanilla_constructor()) {
    // safe to skip constructor call
  } else {
    static JavaValue result(T_VOID);
    JavaCallArguments args(receiver);
    call(&result, method, &args, CHECK);
  }
}

// ============ Virtual calls ============

void JavaCalls::call_virtual(JavaValue* result, KlassHandle spec_klass, symbolHandle name, symbolHandle signature, JavaCallArguments* args, TRAPS) {
  CallInfo callinfo;
  Handle receiver = args->receiver();
  KlassHandle recvrKlass(THREAD, receiver.is_null() ? (klassOop)NULL : receiver->klass());
  LinkResolver::resolve_virtual_call(
          callinfo, receiver, recvrKlass, spec_klass, name, signature,
          KlassHandle(), false, true, CHECK);
  methodHandle method = callinfo.selected_method();
  assert(method.not_null(), "should have thrown exception");

  // Invoke the method
  JavaCalls::call(result, method, args, CHECK);
}


void JavaCalls::call_virtual(JavaValue* result, Handle receiver, KlassHandle spec_klass, symbolHandle name, symbolHandle signature, TRAPS) {
  JavaCallArguments args(receiver); // One oop argument
  call_virtual(result, spec_klass, name, signature, &args, CHECK);
}


void JavaCalls::call_virtual(JavaValue* result, Handle receiver, KlassHandle spec_klass, symbolHandle name, symbolHandle signature, Handle arg1, TRAPS) {
  JavaCallArguments args(receiver); // One oop argument
  args.push_oop(arg1);
  call_virtual(result, spec_klass, name, signature, &args, CHECK);
}



void JavaCalls::call_virtual(JavaValue* result, Handle receiver, KlassHandle spec_klass, symbolHandle name, symbolHandle signature, Handle arg1, Handle arg2, TRAPS) {
  JavaCallArguments args(receiver); // One oop argument
  args.push_oop(arg1);
  args.push_oop(arg2);
  call_virtual(result, spec_klass, name, signature, &args, CHECK);
}


// ============ Special calls ============

void JavaCalls::call_special(JavaValue* result, KlassHandle klass, symbolHandle name, symbolHandle signature, JavaCallArguments* args, TRAPS) {
  CallInfo callinfo;
  LinkResolver::resolve_special_call(callinfo, klass, name, signature, KlassHandle(), false, CHECK);
  methodHandle method = callinfo.selected_method();
  assert(method.not_null(), "should have thrown exception");

  // Invoke the method
  JavaCalls::call(result, method, args, CHECK);
}


void JavaCalls::call_special(JavaValue* result, Handle receiver, KlassHandle klass, symbolHandle name, symbolHandle signature, TRAPS) {
  JavaCallArguments args(receiver); // One oop argument
  call_special(result, klass, name, signature, &args, CHECK);
}


void JavaCalls::call_special(JavaValue* result, Handle receiver, KlassHandle klass, symbolHandle name, symbolHandle signature, Handle arg1, TRAPS) {
  JavaCallArguments args(receiver); // One oop argument
  args.push_oop(arg1);
  call_special(result, klass, name, signature, &args, CHECK);
}


void JavaCalls::call_special(JavaValue* result, Handle receiver, KlassHandle klass, symbolHandle name, symbolHandle signature, Handle arg1, Handle arg2, TRAPS) {
  JavaCallArguments args(receiver); // One oop argument
  args.push_oop(arg1);
  args.push_oop(arg2);
  call_special(result, klass, name, signature, &args, CHECK);
}


// ============ Static calls ============

void JavaCalls::call_static(JavaValue* result, KlassHandle klass, symbolHandle name, symbolHandle signature, JavaCallArguments* args, TRAPS) {
  CallInfo callinfo;
  LinkResolver::resolve_static_call(callinfo, klass, name, signature, KlassHandle(), false, true, CHECK);
  methodHandle method = callinfo.selected_method();
  assert(method.not_null(), "should have thrown exception");

  // Invoke the method
  JavaCalls::call(result, method, args, CHECK);
}


void JavaCalls::call_static(JavaValue* result, KlassHandle klass, symbolHandle name, symbolHandle signature, TRAPS) {
  JavaCallArguments args; // No argument
  call_static(result, klass, name, signature, &args, CHECK);
}


void JavaCalls::call_static(JavaValue* result, KlassHandle klass, symbolHandle name, symbolHandle signature, Handle arg1, TRAPS) {
  JavaCallArguments args(arg1); // One oop argument
  call_static(result, klass, name, signature, &args, CHECK);
}


void JavaCalls::call_static(JavaValue* result, KlassHandle klass, symbolHandle name, symbolHandle signature, Handle arg1, Handle arg2, TRAPS) {
  JavaCallArguments args; // One oop argument
  args.push_oop(arg1);
  args.push_oop(arg2);
  call_static(result, klass, name, signature, &args, CHECK);
}


// -------------------------------------------------
// Implementation of JavaCalls (low level)


void JavaCalls::call(JavaValue* result, methodHandle method, JavaCallArguments* args, TRAPS) {
  // Check if we need to wrap a potential OS exception handler around thread
  // This is used for e.g. Win32 structured exception handlers
  assert(THREAD->is_Java_thread(), "only JavaThreads can make JavaCalls");
  // Need to wrap each and everytime, since there might be native code down the
  // stack that has installed its own exception handlers
  os::os_exception_wrapper(call_helper, result, &method, args, THREAD);
}

void JavaCalls::call_helper(JavaValue* result, methodHandle* m, JavaCallArguments* args, TRAPS) {
  methodHandle method = *m;
  JavaThread* thread = (JavaThread*)THREAD;
  assert(thread->is_Java_thread(), "must be called by a java thread");
  assert(method.not_null(), "must have a method to call");
  assert(!SafepointSynchronize::is_at_safepoint(), "call to Java code during VM operation");
  assert(!thread->handle_area()->no_handle_mark_active(), "cannot call out to Java here");


  CHECK_UNHANDLED_OOPS_ONLY(thread->clear_unhandled_oops();)

  // Verify the arguments

  if (CheckJNICalls)  {
    args->verify(method, result->get_type(), thread);
  }
  else debug_only(args->verify(method, result->get_type(), thread));

  // Ignore call if method is empty
  if (method->is_empty_method()) {
    assert(result->get_type() == T_VOID, "an empty method must return a void value");
    return;
  }


#ifdef ASSERT
  { klassOop holder = method->method_holder();
    // A klass might not be initialized since JavaCall's might be used during the executing of
    // the <clinit>. For example, a Thread.start might start executing on an object that is
    // not fully initialized! (bad Java programming style)
    assert(instanceKlass::cast(holder)->is_linked(), "rewritting must have taken place");
  }
#endif


  assert(!thread->is_Compiler_thread(), "cannot compile from the compiler");
  if (CompilationPolicy::mustBeCompiled(method)) {
    CompileBroker::compile_method(method, InvocationEntryBci,
                                  methodHandle(), 0, "mustBeCompiled", CHECK);
  }

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

  // When we reenter Java, we need to reenable the yellow zone which
  // might already be disabled when we are in VM.
  if (thread->stack_yellow_zone_disabled()) {
    thread->reguard_stack();
  }

  // Check that there are shadow pages available before changing thread state
  // to Java
  if (!os::stack_shadow_pages_available(THREAD, method)) {
    // Throw stack overflow exception with preinitialized exception.
    Exceptions::throw_stack_overflow_exception(THREAD, __FILE__, __LINE__);
    return;
  } else {
    // Touch pages checked if the OS needs them to be touched to be mapped.
    os::bang_stack_shadow_pages();
  }

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

intptr_t* JavaCallArguments::parameters() {
  // First convert all handles to oops
  for(int i = 0; i < _size; i++) {
    if (_is_oop[i]) {
      // Handle conversion
      _value[i] = (intptr_t)Handle::raw_resolve((oop *)_value[i]);
    }
    // The parameters are moved to the parameters array to include the tags.
    if (TaggedStackInterpreter) {
      // Tags are interspersed with arguments.  Tags are first.
      int tagged_index = i*2;
      _parameters[tagged_index]   = _is_oop[i] ? frame::TagReference :
                                                 frame::TagValue;
      _parameters[tagged_index+1] = _value[i];
    }
  }
  // Return argument vector
  return TaggedStackInterpreter ? _parameters : _value;
}


class SignatureChekker : public SignatureIterator {
 private:
   bool *_is_oop;
   int   _pos;
   BasicType _return_type;
   intptr_t*   _value;
   Thread* _thread;

 public:
  bool _is_return;

  SignatureChekker(symbolHandle signature, BasicType return_type, bool is_static, bool* is_oop, intptr_t* value, Thread* thread) : SignatureIterator(signature) {
    _is_oop = is_oop;
    _is_return = false;
    _return_type = return_type;
    _pos = 0;
    _value = value;
    _thread = thread;

    if (!is_static) {
      check_value(true); // Receiver must be an oop
    }
  }

  void check_value(bool type) {
    guarantee(_is_oop[_pos++] == type, "signature does not match pushed arguments");
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

    // verify handle and the oop pointed to by handle
    int p = _pos;
    bool bad = false;
    // If argument is oop
    if (_is_oop[p]) {
      intptr_t v = _value[p];
      if (v != 0 ) {
        size_t t = (size_t)v;
        bad = (t < (size_t)os::vm_page_size() ) || !Handle::raw_resolve((oop *)v)->is_oop_or_null(true);
        if (CheckJNICalls && bad) {
          ReportJNIFatalError((JavaThread*)_thread, "Bad JNI oop argument");
        }
      }
      // for the regular debug case.
      assert(!bad, "Bad JNI oop argument");
    }

    check_value(true);
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


void JavaCallArguments::verify(methodHandle method, BasicType return_type,
  Thread *thread) {
  guarantee(method->size_of_parameters() == size_of_parameters(), "wrong no. of arguments pushed");

  // Treat T_OBJECT and T_ARRAY as the same
  if (return_type == T_ARRAY) return_type = T_OBJECT;

  // Check that oop information is correct
  symbolHandle signature (thread,  method->signature());

  SignatureChekker sc(signature, return_type, method->is_static(),_is_oop, _value, thread);
  sc.iterate_parameters();
  sc.check_doing_return(true);
  sc.iterate_returntype();
}

