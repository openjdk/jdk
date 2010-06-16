/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_exceptions.cpp.incl"


// Implementation of ThreadShadow
void check_ThreadShadow() {
  const ByteSize offset1 = byte_offset_of(ThreadShadow, _pending_exception);
  const ByteSize offset2 = Thread::pending_exception_offset();
  if (offset1 != offset2) fatal("ThreadShadow::_pending_exception is not positioned correctly");
}


void ThreadShadow::set_pending_exception(oop exception, const char* file, int line) {
  assert(exception != NULL && exception->is_oop(), "invalid exception oop");
  _pending_exception = exception;
  _exception_file    = file;
  _exception_line    = line;
}

void ThreadShadow::clear_pending_exception() {
  if (TraceClearedExceptions) {
    if (_pending_exception != NULL) {
      tty->print_cr("Thread::clear_pending_exception: cleared exception:");
      _pending_exception->print();
    }
  }
  _pending_exception = NULL;
  _exception_file    = NULL;
  _exception_line    = 0;
}
// Implementation of Exceptions

bool Exceptions::special_exception(Thread* thread, const char* file, int line, Handle h_exception) {
  // bootstrapping check
  if (!Universe::is_fully_initialized()) {
   vm_exit_during_initialization(h_exception);
   ShouldNotReachHere();
  }

  if (thread->is_VM_thread()
      || thread->is_Compiler_thread() ) {
    // We do not care what kind of exception we get for the vm-thread or a thread which
    // is compiling.  We just install a dummy exception object
    thread->set_pending_exception(Universe::vm_exception(), file, line);
    return true;
  }

  return false;
}

bool Exceptions::special_exception(Thread* thread, const char* file, int line, symbolHandle h_name, const char* message) {
  // bootstrapping check
  if (!Universe::is_fully_initialized()) {
    if (h_name.is_null()) {
      // atleast an informative message.
      vm_exit_during_initialization("Exception", message);
    } else {
      vm_exit_during_initialization(h_name, message);
    }
    ShouldNotReachHere();
  }

  if (thread->is_VM_thread()
      || thread->is_Compiler_thread() ) {
    // We do not care what kind of exception we get for the vm-thread or a thread which
    // is compiling.  We just install a dummy exception object
    thread->set_pending_exception(Universe::vm_exception(), file, line);
    return true;
  }

  return false;
}

// This method should only be called from generated code,
// therefore the exception oop should be in the oopmap.
void Exceptions::_throw_oop(Thread* thread, const char* file, int line, oop exception) {
  assert(exception != NULL, "exception should not be NULL");
  Handle h_exception = Handle(thread, exception);
  _throw(thread, file, line, h_exception);
}

void Exceptions::_throw(Thread* thread, const char* file, int line, Handle h_exception, const char* message) {
  assert(h_exception() != NULL, "exception should not be NULL");

  // tracing (do this up front - so it works during boot strapping)
  if (TraceExceptions) {
    ttyLocker ttyl;
    ResourceMark rm;
    tty->print_cr("Exception <%s>%s%s (" INTPTR_FORMAT " ) \n"
                  "thrown [%s, line %d]\nfor thread " INTPTR_FORMAT,
                  h_exception->print_value_string(),
                  message ? ": " : "", message ? message : "",
                  (address)h_exception(), file, line, thread);
  }
  // for AbortVMOnException flag
  NOT_PRODUCT(Exceptions::debug_check_abort(h_exception));

  // Check for special boot-strapping/vm-thread handling
  if (special_exception(thread, file, line, h_exception)) return;

  assert(h_exception->is_a(SystemDictionary::Throwable_klass()), "exception is not a subclass of java/lang/Throwable");

  // set the pending exception
  thread->set_pending_exception(h_exception(), file, line);

  // vm log
  Events::log("throw_exception " INTPTR_FORMAT, (address)h_exception());
}


void Exceptions::_throw_msg(Thread* thread, const char* file, int line, symbolHandle h_name, const char* message, Handle h_loader, Handle h_protection_domain) {
  // Check for special boot-strapping/vm-thread handling
  if (special_exception(thread, file, line, h_name, message)) return;
  // Create and throw exception
  Handle h_cause(thread, NULL);
  Handle h_exception = new_exception(thread, h_name, message, h_cause, h_loader, h_protection_domain);
  _throw(thread, file, line, h_exception, message);
}

// Throw an exception with a message and a cause
void Exceptions::_throw_msg_cause(Thread* thread, const char* file, int line, symbolHandle h_name, const char* message, Handle h_cause, Handle h_loader, Handle h_protection_domain) {
  // Check for special boot-strapping/vm-thread handling
  if (special_exception(thread, file, line, h_name, message)) return;
  // Create and throw exception and init cause
  Handle h_exception = new_exception(thread, h_name, message, h_cause, h_loader, h_protection_domain);
  _throw(thread, file, line, h_exception, message);
}

// This version creates handles and calls the other version
void Exceptions::_throw_msg(Thread* thread, const char* file, int line,
                            symbolOop name, const char* message) {
  symbolHandle h_name(thread, name);
  Handle       h_loader(thread, NULL);
  Handle       h_protection_domain(thread, NULL);
  Exceptions::_throw_msg(thread, file, line, h_name, message, h_loader, h_protection_domain);
}

// This version already has a handle for name
void Exceptions::_throw_msg(Thread* thread, const char* file, int line,
                            symbolHandle name, const char* message) {
  Handle       h_loader(thread, NULL);
  Handle       h_protection_domain(thread, NULL);
  Exceptions::_throw_msg(thread, file, line, name, message, h_loader, h_protection_domain);
}

// This version already has a handle for name
void Exceptions::_throw_msg_cause(Thread* thread, const char* file, int line,
                            symbolHandle name, const char* message, Handle cause) {
  Handle       h_loader(thread, NULL);
  Handle       h_protection_domain(thread, NULL);
  Exceptions::_throw_msg_cause(thread, file, line, name, message, cause, h_loader, h_protection_domain);
}

void Exceptions::_throw_args(Thread* thread, const char* file, int line, symbolHandle h_name, symbolHandle h_signature, JavaCallArguments *args) {
  // Check for special boot-strapping/vm-thread handling
  if (special_exception(thread, file, line, h_name, NULL)) return;
  // Create and throw exception
  Handle h_loader(thread, NULL);
  Handle h_prot(thread, NULL);
  Handle h_cause(thread, NULL);
  Handle exception = new_exception(thread, h_name, h_signature, args, h_cause, h_loader, h_prot);
  _throw(thread, file, line, exception);
}


void Exceptions::throw_stack_overflow_exception(Thread* THREAD, const char* file, int line) {
  Handle exception;
  if (!THREAD->has_pending_exception()) {
    klassOop k = SystemDictionary::StackOverflowError_klass();
    oop e = instanceKlass::cast(k)->allocate_instance(CHECK);
    exception = Handle(THREAD, e);  // fill_in_stack trace does gc
    if (StackTraceInThrowable) {
      java_lang_Throwable::fill_in_stack_trace(exception);
    }
  } else {
    // if prior exception, throw that one instead
    exception = Handle(THREAD, THREAD->pending_exception());
  }
  _throw_oop(THREAD, file, line, exception());
}

void Exceptions::fthrow(Thread* thread, const char* file, int line, symbolHandle h_name, const char* format, ...) {
  const int max_msg_size = 1024;
  va_list ap;
  va_start(ap, format);
  char msg[max_msg_size];
  vsnprintf(msg, max_msg_size, format, ap);
  msg[max_msg_size-1] = '\0';
  va_end(ap);
  _throw_msg(thread, file, line, h_name, msg);
}

// Creates an exception oop, calls the <init> method with the given signature.
// and returns a Handle
// Initializes the cause if cause non-null
Handle Exceptions::new_exception(Thread *thread, symbolHandle h_name,
                                 symbolHandle signature,
                                 JavaCallArguments *args,
                                 Handle h_cause, Handle h_loader,
                                 Handle h_protection_domain) {
  assert(Universe::is_fully_initialized(),
    "cannot be called during initialization");
  assert(thread->is_Java_thread(), "can only be called by a Java thread");
  assert(!thread->has_pending_exception(), "already has exception");

  Handle h_exception;

  // Resolve exception klass
  klassOop ik = SystemDictionary::resolve_or_fail(h_name, h_loader, h_protection_domain, true, thread);
  instanceKlassHandle klass (thread, ik);

  if (!thread->has_pending_exception()) {
    assert(klass.not_null(), "klass must exist");
    // We are about to create an instance - so make sure that klass is initialized
    klass->initialize(thread);
    if (!thread->has_pending_exception()) {
      // Allocate new exception
      h_exception = klass->allocate_instance_handle(thread);
      if (!thread->has_pending_exception()) {
        JavaValue result(T_VOID);
        args->set_receiver(h_exception);
        // Call constructor
        JavaCalls::call_special(&result, klass,
                                         vmSymbolHandles::object_initializer_name(),
                                         signature,
                                         args,
                                         thread);

      }
    }

    // Future: object initializer should take a cause argument
    if (h_cause() != NULL) {
      assert(h_cause->is_a(SystemDictionary::Throwable_klass()),
          "exception cause is not a subclass of java/lang/Throwable");
      JavaValue result1(T_OBJECT);
      JavaCallArguments args1;
      args1.set_receiver(h_exception);
      args1.push_oop(h_cause);
      JavaCalls::call_virtual(&result1, klass,
                                     vmSymbolHandles::initCause_name(),
                                     vmSymbolHandles::throwable_throwable_signature(),
                                     &args1,
                                     thread);
    }
  }

  // Check if another exception was thrown in the process, if so rethrow that one
  if (thread->has_pending_exception()) {
    h_exception = Handle(thread, thread->pending_exception());
    thread->clear_pending_exception();
  }
  return h_exception;
}

// Convenience method. Calls either the <init>() or <init>(String) method when
// creating a new exception
Handle Exceptions::new_exception(Thread* thread, symbolHandle h_name,
                                 const char* message, Handle h_cause,
                                 Handle h_loader,
                                 Handle h_protection_domain,
                                 ExceptionMsgToUtf8Mode to_utf8_safe) {
  JavaCallArguments args;
  symbolHandle signature;
  if (message == NULL) {
    signature = vmSymbolHandles::void_method_signature();
  } else {
    // We want to allocate storage, but we can't do that if there's
    // a pending exception, so we preserve any pending exception
    // around the allocation.
    // If we get an exception from the allocation, prefer that to
    // the exception we are trying to build, or the pending exception.
    // This is sort of like what PRESERVE_EXCEPTION_MARK does, except
    // for the preferencing and the early returns.
    Handle incoming_exception (thread, NULL);
    if (thread->has_pending_exception()) {
      incoming_exception = Handle(thread, thread->pending_exception());
      thread->clear_pending_exception();
    }
    Handle msg;
    if (to_utf8_safe == safe_to_utf8) {
      // Make a java UTF8 string.
      msg = java_lang_String::create_from_str(message, thread);
    } else {
      // Make a java string keeping the encoding scheme of the original string.
      msg = java_lang_String::create_from_platform_dependent_str(message, thread);
    }
    if (thread->has_pending_exception()) {
      Handle exception(thread, thread->pending_exception());
      thread->clear_pending_exception();
      return exception;
    }
    if (incoming_exception.not_null()) {
      return incoming_exception;
    }
    args.push_oop(msg);
    signature = vmSymbolHandles::string_void_signature();
  }
  return new_exception(thread, h_name, signature, &args, h_cause, h_loader, h_protection_domain);
}

// Another convenience method that creates handles for null class loaders and
// protection domains and null causes.
// If the last parameter 'to_utf8_mode' is safe_to_utf8,
// it means we can safely ignore the encoding scheme of the message string and
// convert it directly to a java UTF8 string. Otherwise, we need to take the
// encoding scheme of the string into account. One thing we should do at some
// point is to push this flag down to class java_lang_String since other
// classes may need similar functionalities.
Handle Exceptions::new_exception(Thread* thread,
                                 symbolOop name,
                                 const char* message,
                                 ExceptionMsgToUtf8Mode to_utf8_safe) {

  symbolHandle h_name(thread, name);
  Handle       h_loader(thread, NULL);
  Handle       h_prot(thread, NULL);
  Handle       h_cause(thread, NULL);
  return Exceptions::new_exception(thread, h_name, message, h_cause, h_loader,
                                   h_prot, to_utf8_safe);
}

// Implementation of ExceptionMark

ExceptionMark::ExceptionMark(Thread*& thread) {
  thread     = Thread::current();
  _thread    = thread;
  if (_thread->has_pending_exception()) {
    oop exception = _thread->pending_exception();
    _thread->clear_pending_exception(); // Needed to avoid infinite recursion
    exception->print();
    fatal("ExceptionMark constructor expects no pending exceptions");
  }
}


ExceptionMark::~ExceptionMark() {
  if (_thread->has_pending_exception()) {
    Handle exception(_thread, _thread->pending_exception());
    _thread->clear_pending_exception(); // Needed to avoid infinite recursion
    if (is_init_completed()) {
      exception->print();
      fatal("ExceptionMark destructor expects no pending exceptions");
    } else {
      vm_exit_during_initialization(exception);
    }
  }
}

// ----------------------------------------------------------------------------------------

#ifndef PRODUCT
// caller frees value_string if necessary
void Exceptions::debug_check_abort(const char *value_string) {
  if (AbortVMOnException != NULL && value_string != NULL &&
      strstr(value_string, AbortVMOnException)) {
    fatal(err_msg("Saw %s, aborting", value_string));
  }
}

void Exceptions::debug_check_abort(Handle exception) {
  if (AbortVMOnException != NULL) {
    ResourceMark rm;
    debug_check_abort(instanceKlass::cast(exception()->klass())->external_name());
  }
}
#endif
