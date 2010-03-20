/*
 * Copyright 1997-2010 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_sharedRuntime.cpp.incl"
#include <math.h>

HS_DTRACE_PROBE_DECL4(hotspot, object__alloc, Thread*, char*, int, size_t);
HS_DTRACE_PROBE_DECL7(hotspot, method__entry, int,
                      char*, int, char*, int, char*, int);
HS_DTRACE_PROBE_DECL7(hotspot, method__return, int,
                      char*, int, char*, int, char*, int);

// Implementation of SharedRuntime

#ifndef PRODUCT
// For statistics
int SharedRuntime::_ic_miss_ctr = 0;
int SharedRuntime::_wrong_method_ctr = 0;
int SharedRuntime::_resolve_static_ctr = 0;
int SharedRuntime::_resolve_virtual_ctr = 0;
int SharedRuntime::_resolve_opt_virtual_ctr = 0;
int SharedRuntime::_implicit_null_throws = 0;
int SharedRuntime::_implicit_div0_throws = 0;
int SharedRuntime::_throw_null_ctr = 0;

int SharedRuntime::_nof_normal_calls = 0;
int SharedRuntime::_nof_optimized_calls = 0;
int SharedRuntime::_nof_inlined_calls = 0;
int SharedRuntime::_nof_megamorphic_calls = 0;
int SharedRuntime::_nof_static_calls = 0;
int SharedRuntime::_nof_inlined_static_calls = 0;
int SharedRuntime::_nof_interface_calls = 0;
int SharedRuntime::_nof_optimized_interface_calls = 0;
int SharedRuntime::_nof_inlined_interface_calls = 0;
int SharedRuntime::_nof_megamorphic_interface_calls = 0;
int SharedRuntime::_nof_removable_exceptions = 0;

int SharedRuntime::_new_instance_ctr=0;
int SharedRuntime::_new_array_ctr=0;
int SharedRuntime::_multi1_ctr=0;
int SharedRuntime::_multi2_ctr=0;
int SharedRuntime::_multi3_ctr=0;
int SharedRuntime::_multi4_ctr=0;
int SharedRuntime::_multi5_ctr=0;
int SharedRuntime::_mon_enter_stub_ctr=0;
int SharedRuntime::_mon_exit_stub_ctr=0;
int SharedRuntime::_mon_enter_ctr=0;
int SharedRuntime::_mon_exit_ctr=0;
int SharedRuntime::_partial_subtype_ctr=0;
int SharedRuntime::_jbyte_array_copy_ctr=0;
int SharedRuntime::_jshort_array_copy_ctr=0;
int SharedRuntime::_jint_array_copy_ctr=0;
int SharedRuntime::_jlong_array_copy_ctr=0;
int SharedRuntime::_oop_array_copy_ctr=0;
int SharedRuntime::_checkcast_array_copy_ctr=0;
int SharedRuntime::_unsafe_array_copy_ctr=0;
int SharedRuntime::_generic_array_copy_ctr=0;
int SharedRuntime::_slow_array_copy_ctr=0;
int SharedRuntime::_find_handler_ctr=0;
int SharedRuntime::_rethrow_ctr=0;

int     SharedRuntime::_ICmiss_index                    = 0;
int     SharedRuntime::_ICmiss_count[SharedRuntime::maxICmiss_count];
address SharedRuntime::_ICmiss_at[SharedRuntime::maxICmiss_count];

void SharedRuntime::trace_ic_miss(address at) {
  for (int i = 0; i < _ICmiss_index; i++) {
    if (_ICmiss_at[i] == at) {
      _ICmiss_count[i]++;
      return;
    }
  }
  int index = _ICmiss_index++;
  if (_ICmiss_index >= maxICmiss_count) _ICmiss_index = maxICmiss_count - 1;
  _ICmiss_at[index] = at;
  _ICmiss_count[index] = 1;
}

void SharedRuntime::print_ic_miss_histogram() {
  if (ICMissHistogram) {
    tty->print_cr ("IC Miss Histogram:");
    int tot_misses = 0;
    for (int i = 0; i < _ICmiss_index; i++) {
      tty->print_cr("  at: " INTPTR_FORMAT "  nof: %d", _ICmiss_at[i], _ICmiss_count[i]);
      tot_misses += _ICmiss_count[i];
    }
    tty->print_cr ("Total IC misses: %7d", tot_misses);
  }
}
#endif // PRODUCT

#ifndef SERIALGC

// G1 write-barrier pre: executed before a pointer store.
JRT_LEAF(void, SharedRuntime::g1_wb_pre(oopDesc* orig, JavaThread *thread))
  if (orig == NULL) {
    assert(false, "should be optimized out");
    return;
  }
  assert(orig->is_oop(true /* ignore mark word */), "Error");
  // store the original value that was in the field reference
  thread->satb_mark_queue().enqueue(orig);
JRT_END

// G1 write-barrier post: executed after a pointer store.
JRT_LEAF(void, SharedRuntime::g1_wb_post(void* card_addr, JavaThread* thread))
  thread->dirty_card_queue().enqueue(card_addr);
JRT_END

#endif // !SERIALGC


JRT_LEAF(jlong, SharedRuntime::lmul(jlong y, jlong x))
  return x * y;
JRT_END


JRT_LEAF(jlong, SharedRuntime::ldiv(jlong y, jlong x))
  if (x == min_jlong && y == CONST64(-1)) {
    return x;
  } else {
    return x / y;
  }
JRT_END


JRT_LEAF(jlong, SharedRuntime::lrem(jlong y, jlong x))
  if (x == min_jlong && y == CONST64(-1)) {
    return 0;
  } else {
    return x % y;
  }
JRT_END


const juint  float_sign_mask  = 0x7FFFFFFF;
const juint  float_infinity   = 0x7F800000;
const julong double_sign_mask = CONST64(0x7FFFFFFFFFFFFFFF);
const julong double_infinity  = CONST64(0x7FF0000000000000);

JRT_LEAF(jfloat, SharedRuntime::frem(jfloat  x, jfloat  y))
#ifdef _WIN64
  // 64-bit Windows on amd64 returns the wrong values for
  // infinity operands.
  union { jfloat f; juint i; } xbits, ybits;
  xbits.f = x;
  ybits.f = y;
  // x Mod Infinity == x unless x is infinity
  if ( ((xbits.i & float_sign_mask) != float_infinity) &&
       ((ybits.i & float_sign_mask) == float_infinity) ) {
    return x;
  }
#endif
  return ((jfloat)fmod((double)x,(double)y));
JRT_END


JRT_LEAF(jdouble, SharedRuntime::drem(jdouble x, jdouble y))
#ifdef _WIN64
  union { jdouble d; julong l; } xbits, ybits;
  xbits.d = x;
  ybits.d = y;
  // x Mod Infinity == x unless x is infinity
  if ( ((xbits.l & double_sign_mask) != double_infinity) &&
       ((ybits.l & double_sign_mask) == double_infinity) ) {
    return x;
  }
#endif
  return ((jdouble)fmod((double)x,(double)y));
JRT_END


JRT_LEAF(jint, SharedRuntime::f2i(jfloat  x))
  if (g_isnan(x))
    return 0;
  if (x >= (jfloat) max_jint)
    return max_jint;
  if (x <= (jfloat) min_jint)
    return min_jint;
  return (jint) x;
JRT_END


JRT_LEAF(jlong, SharedRuntime::f2l(jfloat  x))
  if (g_isnan(x))
    return 0;
  if (x >= (jfloat) max_jlong)
    return max_jlong;
  if (x <= (jfloat) min_jlong)
    return min_jlong;
  return (jlong) x;
JRT_END


JRT_LEAF(jint, SharedRuntime::d2i(jdouble x))
  if (g_isnan(x))
    return 0;
  if (x >= (jdouble) max_jint)
    return max_jint;
  if (x <= (jdouble) min_jint)
    return min_jint;
  return (jint) x;
JRT_END


JRT_LEAF(jlong, SharedRuntime::d2l(jdouble x))
  if (g_isnan(x))
    return 0;
  if (x >= (jdouble) max_jlong)
    return max_jlong;
  if (x <= (jdouble) min_jlong)
    return min_jlong;
  return (jlong) x;
JRT_END


JRT_LEAF(jfloat, SharedRuntime::d2f(jdouble x))
  return (jfloat)x;
JRT_END


JRT_LEAF(jfloat, SharedRuntime::l2f(jlong x))
  return (jfloat)x;
JRT_END


JRT_LEAF(jdouble, SharedRuntime::l2d(jlong x))
  return (jdouble)x;
JRT_END

// Exception handling accross interpreter/compiler boundaries
//
// exception_handler_for_return_address(...) returns the continuation address.
// The continuation address is the entry point of the exception handler of the
// previous frame depending on the return address.

address SharedRuntime::raw_exception_handler_for_return_address(JavaThread* thread, address return_address) {
  assert(frame::verify_return_pc(return_address), "must be a return pc");

  // the fastest case first
  CodeBlob* blob = CodeCache::find_blob(return_address);
  if (blob != NULL && blob->is_nmethod()) {
    nmethod* code = (nmethod*)blob;
    assert(code != NULL, "nmethod must be present");
    // Check if the return address is a MethodHandle call site.
    thread->set_is_method_handle_exception(code->is_method_handle_return(return_address));
    // native nmethods don't have exception handlers
    assert(!code->is_native_method(), "no exception handler");
    assert(code->header_begin() != code->exception_begin(), "no exception handler");
    if (code->is_deopt_pc(return_address)) {
      return SharedRuntime::deopt_blob()->unpack_with_exception();
    } else {
      return code->exception_begin();
    }
  }

  // Entry code
  if (StubRoutines::returns_to_call_stub(return_address)) {
    return StubRoutines::catch_exception_entry();
  }
  // Interpreted code
  if (Interpreter::contains(return_address)) {
    return Interpreter::rethrow_exception_entry();
  }

  // Compiled code
  if (CodeCache::contains(return_address)) {
    CodeBlob* blob = CodeCache::find_blob(return_address);
    if (blob->is_nmethod()) {
      nmethod* code = (nmethod*)blob;
      assert(code != NULL, "nmethod must be present");
      // Check if the return address is a MethodHandle call site.
      thread->set_is_method_handle_exception(code->is_method_handle_return(return_address));
      assert(code->header_begin() != code->exception_begin(), "no exception handler");
      return code->exception_begin();
    }
    if (blob->is_runtime_stub()) {
      ShouldNotReachHere();   // callers are responsible for skipping runtime stub frames
    }
  }
  guarantee(!VtableStubs::contains(return_address), "NULL exceptions in vtables should have been handled already!");
#ifndef PRODUCT
  { ResourceMark rm;
    tty->print_cr("No exception handler found for exception at " INTPTR_FORMAT " - potential problems:", return_address);
    tty->print_cr("a) exception happened in (new?) code stubs/buffers that is not handled here");
    tty->print_cr("b) other problem");
  }
#endif // PRODUCT
  ShouldNotReachHere();
  return NULL;
}


JRT_LEAF(address, SharedRuntime::exception_handler_for_return_address(JavaThread* thread, address return_address))
  return raw_exception_handler_for_return_address(thread, return_address);
JRT_END


address SharedRuntime::get_poll_stub(address pc) {
  address stub;
  // Look up the code blob
  CodeBlob *cb = CodeCache::find_blob(pc);

  // Should be an nmethod
  assert( cb && cb->is_nmethod(), "safepoint polling: pc must refer to an nmethod" );

  // Look up the relocation information
  assert( ((nmethod*)cb)->is_at_poll_or_poll_return(pc),
    "safepoint polling: type must be poll" );

  assert( ((NativeInstruction*)pc)->is_safepoint_poll(),
    "Only polling locations are used for safepoint");

  bool at_poll_return = ((nmethod*)cb)->is_at_poll_return(pc);
  if (at_poll_return) {
    assert(SharedRuntime::polling_page_return_handler_blob() != NULL,
           "polling page return stub not created yet");
    stub = SharedRuntime::polling_page_return_handler_blob()->instructions_begin();
  } else {
    assert(SharedRuntime::polling_page_safepoint_handler_blob() != NULL,
           "polling page safepoint stub not created yet");
    stub = SharedRuntime::polling_page_safepoint_handler_blob()->instructions_begin();
  }
#ifndef PRODUCT
  if( TraceSafepoint ) {
    char buf[256];
    jio_snprintf(buf, sizeof(buf),
                 "... found polling page %s exception at pc = "
                 INTPTR_FORMAT ", stub =" INTPTR_FORMAT,
                 at_poll_return ? "return" : "loop",
                 (intptr_t)pc, (intptr_t)stub);
    tty->print_raw_cr(buf);
  }
#endif // PRODUCT
  return stub;
}


oop SharedRuntime::retrieve_receiver( symbolHandle sig, frame caller ) {
  assert(caller.is_interpreted_frame(), "");
  int args_size = ArgumentSizeComputer(sig).size() + 1;
  assert(args_size <= caller.interpreter_frame_expression_stack_size(), "receiver must be on interpreter stack");
  oop result = (oop) *caller.interpreter_frame_tos_at(args_size - 1);
  assert(Universe::heap()->is_in(result) && result->is_oop(), "receiver must be an oop");
  return result;
}


void SharedRuntime::throw_and_post_jvmti_exception(JavaThread *thread, Handle h_exception) {
  if (JvmtiExport::can_post_on_exceptions()) {
    vframeStream vfst(thread, true);
    methodHandle method = methodHandle(thread, vfst.method());
    address bcp = method()->bcp_from(vfst.bci());
    JvmtiExport::post_exception_throw(thread, method(), bcp, h_exception());
  }
  Exceptions::_throw(thread, __FILE__, __LINE__, h_exception);
}

void SharedRuntime::throw_and_post_jvmti_exception(JavaThread *thread, symbolOop name, const char *message) {
  Handle h_exception = Exceptions::new_exception(thread, name, message);
  throw_and_post_jvmti_exception(thread, h_exception);
}

// The interpreter code to call this tracing function is only
// called/generated when TraceRedefineClasses has the right bits
// set. Since obsolete methods are never compiled, we don't have
// to modify the compilers to generate calls to this function.
//
JRT_LEAF(int, SharedRuntime::rc_trace_method_entry(
    JavaThread* thread, methodOopDesc* method))
  assert(RC_TRACE_IN_RANGE(0x00001000, 0x00002000), "wrong call");

  if (method->is_obsolete()) {
    // We are calling an obsolete method, but this is not necessarily
    // an error. Our method could have been redefined just after we
    // fetched the methodOop from the constant pool.

    // RC_TRACE macro has an embedded ResourceMark
    RC_TRACE_WITH_THREAD(0x00001000, thread,
                         ("calling obsolete method '%s'",
                          method->name_and_sig_as_C_string()));
    if (RC_TRACE_ENABLED(0x00002000)) {
      // this option is provided to debug calls to obsolete methods
      guarantee(false, "faulting at call to an obsolete method.");
    }
  }
  return 0;
JRT_END

// ret_pc points into caller; we are returning caller's exception handler
// for given exception
address SharedRuntime::compute_compiled_exc_handler(nmethod* nm, address ret_pc, Handle& exception,
                                                    bool force_unwind, bool top_frame_only) {
  assert(nm != NULL, "must exist");
  ResourceMark rm;

  ScopeDesc* sd = nm->scope_desc_at(ret_pc);
  // determine handler bci, if any
  EXCEPTION_MARK;

  int handler_bci = -1;
  int scope_depth = 0;
  if (!force_unwind) {
    int bci = sd->bci();
    do {
      bool skip_scope_increment = false;
      // exception handler lookup
      KlassHandle ek (THREAD, exception->klass());
      handler_bci = sd->method()->fast_exception_handler_bci_for(ek, bci, THREAD);
      if (HAS_PENDING_EXCEPTION) {
        // We threw an exception while trying to find the exception handler.
        // Transfer the new exception to the exception handle which will
        // be set into thread local storage, and do another lookup for an
        // exception handler for this exception, this time starting at the
        // BCI of the exception handler which caused the exception to be
        // thrown (bugs 4307310 and 4546590). Set "exception" reference
        // argument to ensure that the correct exception is thrown (4870175).
        exception = Handle(THREAD, PENDING_EXCEPTION);
        CLEAR_PENDING_EXCEPTION;
        if (handler_bci >= 0) {
          bci = handler_bci;
          handler_bci = -1;
          skip_scope_increment = true;
        }
      }
      if (!top_frame_only && handler_bci < 0 && !skip_scope_increment) {
        sd = sd->sender();
        if (sd != NULL) {
          bci = sd->bci();
        }
        ++scope_depth;
      }
    } while (!top_frame_only && handler_bci < 0 && sd != NULL);
  }

  // found handling method => lookup exception handler
  int catch_pco = ret_pc - nm->instructions_begin();

  ExceptionHandlerTable table(nm);
  HandlerTableEntry *t = table.entry_for(catch_pco, handler_bci, scope_depth);
  if (t == NULL && (nm->is_compiled_by_c1() || handler_bci != -1)) {
    // Allow abbreviated catch tables.  The idea is to allow a method
    // to materialize its exceptions without committing to the exact
    // routing of exceptions.  In particular this is needed for adding
    // a synthethic handler to unlock monitors when inlining
    // synchonized methods since the unlock path isn't represented in
    // the bytecodes.
    t = table.entry_for(catch_pco, -1, 0);
  }

  if (t == NULL) {
    tty->print_cr("MISSING EXCEPTION HANDLER for pc " INTPTR_FORMAT " and handler bci %d", ret_pc, handler_bci);
    tty->print_cr("   Exception:");
    exception->print();
    tty->cr();
    tty->print_cr(" Compiled exception table :");
    table.print();
    nm->print_code();
    guarantee(false, "missing exception handler");
    return NULL;
  }

  return nm->instructions_begin() + t->pco();
}

JRT_ENTRY(void, SharedRuntime::throw_AbstractMethodError(JavaThread* thread))
  // These errors occur only at call sites
  throw_and_post_jvmti_exception(thread, vmSymbols::java_lang_AbstractMethodError());
JRT_END

JRT_ENTRY(void, SharedRuntime::throw_IncompatibleClassChangeError(JavaThread* thread))
  // These errors occur only at call sites
  throw_and_post_jvmti_exception(thread, vmSymbols::java_lang_IncompatibleClassChangeError(), "vtable stub");
JRT_END

JRT_ENTRY(void, SharedRuntime::throw_ArithmeticException(JavaThread* thread))
  throw_and_post_jvmti_exception(thread, vmSymbols::java_lang_ArithmeticException(), "/ by zero");
JRT_END

JRT_ENTRY(void, SharedRuntime::throw_NullPointerException(JavaThread* thread))
  throw_and_post_jvmti_exception(thread, vmSymbols::java_lang_NullPointerException());
JRT_END

JRT_ENTRY(void, SharedRuntime::throw_NullPointerException_at_call(JavaThread* thread))
  // This entry point is effectively only used for NullPointerExceptions which occur at inline
  // cache sites (when the callee activation is not yet set up) so we are at a call site
  throw_and_post_jvmti_exception(thread, vmSymbols::java_lang_NullPointerException());
JRT_END

JRT_ENTRY(void, SharedRuntime::throw_StackOverflowError(JavaThread* thread))
  // We avoid using the normal exception construction in this case because
  // it performs an upcall to Java, and we're already out of stack space.
  klassOop k = SystemDictionary::StackOverflowError_klass();
  oop exception_oop = instanceKlass::cast(k)->allocate_instance(CHECK);
  Handle exception (thread, exception_oop);
  if (StackTraceInThrowable) {
    java_lang_Throwable::fill_in_stack_trace(exception);
  }
  throw_and_post_jvmti_exception(thread, exception);
JRT_END

address SharedRuntime::continuation_for_implicit_exception(JavaThread* thread,
                                                           address pc,
                                                           SharedRuntime::ImplicitExceptionKind exception_kind)
{
  address target_pc = NULL;

  if (Interpreter::contains(pc)) {
#ifdef CC_INTERP
    // C++ interpreter doesn't throw implicit exceptions
    ShouldNotReachHere();
#else
    switch (exception_kind) {
      case IMPLICIT_NULL:           return Interpreter::throw_NullPointerException_entry();
      case IMPLICIT_DIVIDE_BY_ZERO: return Interpreter::throw_ArithmeticException_entry();
      case STACK_OVERFLOW:          return Interpreter::throw_StackOverflowError_entry();
      default:                      ShouldNotReachHere();
    }
#endif // !CC_INTERP
  } else {
    switch (exception_kind) {
      case STACK_OVERFLOW: {
        // Stack overflow only occurs upon frame setup; the callee is
        // going to be unwound. Dispatch to a shared runtime stub
        // which will cause the StackOverflowError to be fabricated
        // and processed.
        // For stack overflow in deoptimization blob, cleanup thread.
        if (thread->deopt_mark() != NULL) {
          Deoptimization::cleanup_deopt_info(thread, NULL);
        }
        return StubRoutines::throw_StackOverflowError_entry();
      }

      case IMPLICIT_NULL: {
        if (VtableStubs::contains(pc)) {
          // We haven't yet entered the callee frame. Fabricate an
          // exception and begin dispatching it in the caller. Since
          // the caller was at a call site, it's safe to destroy all
          // caller-saved registers, as these entry points do.
          VtableStub* vt_stub = VtableStubs::stub_containing(pc);

          // If vt_stub is NULL, then return NULL to signal handler to report the SEGV error.
          if (vt_stub == NULL) return NULL;

          if (vt_stub->is_abstract_method_error(pc)) {
            assert(!vt_stub->is_vtable_stub(), "should never see AbstractMethodErrors from vtable-type VtableStubs");
            return StubRoutines::throw_AbstractMethodError_entry();
          } else {
            return StubRoutines::throw_NullPointerException_at_call_entry();
          }
        } else {
          CodeBlob* cb = CodeCache::find_blob(pc);

          // If code blob is NULL, then return NULL to signal handler to report the SEGV error.
          if (cb == NULL) return NULL;

          // Exception happened in CodeCache. Must be either:
          // 1. Inline-cache check in C2I handler blob,
          // 2. Inline-cache check in nmethod, or
          // 3. Implict null exception in nmethod

          if (!cb->is_nmethod()) {
            guarantee(cb->is_adapter_blob() || cb->is_method_handles_adapter_blob(),
                      "exception happened outside interpreter, nmethods and vtable stubs (1)");
            // There is no handler here, so we will simply unwind.
            return StubRoutines::throw_NullPointerException_at_call_entry();
          }

          // Otherwise, it's an nmethod.  Consult its exception handlers.
          nmethod* nm = (nmethod*)cb;
          if (nm->inlinecache_check_contains(pc)) {
            // exception happened inside inline-cache check code
            // => the nmethod is not yet active (i.e., the frame
            // is not set up yet) => use return address pushed by
            // caller => don't push another return address
            return StubRoutines::throw_NullPointerException_at_call_entry();
          }

#ifndef PRODUCT
          _implicit_null_throws++;
#endif
          target_pc = nm->continuation_for_implicit_exception(pc);
          // If there's an unexpected fault, target_pc might be NULL,
          // in which case we want to fall through into the normal
          // error handling code.
        }

        break; // fall through
      }


      case IMPLICIT_DIVIDE_BY_ZERO: {
        nmethod* nm = CodeCache::find_nmethod(pc);
        guarantee(nm != NULL, "must have containing nmethod for implicit division-by-zero exceptions");
#ifndef PRODUCT
        _implicit_div0_throws++;
#endif
        target_pc = nm->continuation_for_implicit_exception(pc);
        // If there's an unexpected fault, target_pc might be NULL,
        // in which case we want to fall through into the normal
        // error handling code.
        break; // fall through
      }

      default: ShouldNotReachHere();
    }

    assert(exception_kind == IMPLICIT_NULL || exception_kind == IMPLICIT_DIVIDE_BY_ZERO, "wrong implicit exception kind");

    // for AbortVMOnException flag
    NOT_PRODUCT(Exceptions::debug_check_abort("java.lang.NullPointerException"));
    if (exception_kind == IMPLICIT_NULL) {
      Events::log("Implicit null exception at " INTPTR_FORMAT " to " INTPTR_FORMAT, pc, target_pc);
    } else {
      Events::log("Implicit division by zero exception at " INTPTR_FORMAT " to " INTPTR_FORMAT, pc, target_pc);
    }
    return target_pc;
  }

  ShouldNotReachHere();
  return NULL;
}


JNI_ENTRY(void, throw_unsatisfied_link_error(JNIEnv* env, ...))
{
  THROW(vmSymbols::java_lang_UnsatisfiedLinkError());
}
JNI_END


address SharedRuntime::native_method_throw_unsatisfied_link_error_entry() {
  return CAST_FROM_FN_PTR(address, &throw_unsatisfied_link_error);
}


#ifndef PRODUCT
JRT_ENTRY(intptr_t, SharedRuntime::trace_bytecode(JavaThread* thread, intptr_t preserve_this_value, intptr_t tos, intptr_t tos2))
  const frame f = thread->last_frame();
  assert(f.is_interpreted_frame(), "must be an interpreted frame");
#ifndef PRODUCT
  methodHandle mh(THREAD, f.interpreter_frame_method());
  BytecodeTracer::trace(mh, f.interpreter_frame_bcp(), tos, tos2);
#endif // !PRODUCT
  return preserve_this_value;
JRT_END
#endif // !PRODUCT


JRT_ENTRY(void, SharedRuntime::yield_all(JavaThread* thread, int attempts))
  os::yield_all(attempts);
JRT_END


JRT_ENTRY_NO_ASYNC(void, SharedRuntime::register_finalizer(JavaThread* thread, oopDesc* obj))
  assert(obj->is_oop(), "must be a valid oop");
  assert(obj->klass()->klass_part()->has_finalizer(), "shouldn't be here otherwise");
  instanceKlass::register_finalizer(instanceOop(obj), CHECK);
JRT_END


jlong SharedRuntime::get_java_tid(Thread* thread) {
  if (thread != NULL) {
    if (thread->is_Java_thread()) {
      oop obj = ((JavaThread*)thread)->threadObj();
      return (obj == NULL) ? 0 : java_lang_Thread::thread_id(obj);
    }
  }
  return 0;
}

/**
 * This function ought to be a void function, but cannot be because
 * it gets turned into a tail-call on sparc, which runs into dtrace bug
 * 6254741.  Once that is fixed we can remove the dummy return value.
 */
int SharedRuntime::dtrace_object_alloc(oopDesc* o) {
  return dtrace_object_alloc_base(Thread::current(), o);
}

int SharedRuntime::dtrace_object_alloc_base(Thread* thread, oopDesc* o) {
  assert(DTraceAllocProbes, "wrong call");
  Klass* klass = o->blueprint();
  int size = o->size();
  symbolOop name = klass->name();
  HS_DTRACE_PROBE4(hotspot, object__alloc, get_java_tid(thread),
                   name->bytes(), name->utf8_length(), size * HeapWordSize);
  return 0;
}

JRT_LEAF(int, SharedRuntime::dtrace_method_entry(
    JavaThread* thread, methodOopDesc* method))
  assert(DTraceMethodProbes, "wrong call");
  symbolOop kname = method->klass_name();
  symbolOop name = method->name();
  symbolOop sig = method->signature();
  HS_DTRACE_PROBE7(hotspot, method__entry, get_java_tid(thread),
      kname->bytes(), kname->utf8_length(),
      name->bytes(), name->utf8_length(),
      sig->bytes(), sig->utf8_length());
  return 0;
JRT_END

JRT_LEAF(int, SharedRuntime::dtrace_method_exit(
    JavaThread* thread, methodOopDesc* method))
  assert(DTraceMethodProbes, "wrong call");
  symbolOop kname = method->klass_name();
  symbolOop name = method->name();
  symbolOop sig = method->signature();
  HS_DTRACE_PROBE7(hotspot, method__return, get_java_tid(thread),
      kname->bytes(), kname->utf8_length(),
      name->bytes(), name->utf8_length(),
      sig->bytes(), sig->utf8_length());
  return 0;
JRT_END


// Finds receiver, CallInfo (i.e. receiver method), and calling bytecode)
// for a call current in progress, i.e., arguments has been pushed on stack
// put callee has not been invoked yet.  Used by: resolve virtual/static,
// vtable updates, etc.  Caller frame must be compiled.
Handle SharedRuntime::find_callee_info(JavaThread* thread, Bytecodes::Code& bc, CallInfo& callinfo, TRAPS) {
  ResourceMark rm(THREAD);

  // last java frame on stack (which includes native call frames)
  vframeStream vfst(thread, true);  // Do not skip and javaCalls

  return find_callee_info_helper(thread, vfst, bc, callinfo, CHECK_(Handle()));
}


// Finds receiver, CallInfo (i.e. receiver method), and calling bytecode
// for a call current in progress, i.e., arguments has been pushed on stack
// but callee has not been invoked yet.  Caller frame must be compiled.
Handle SharedRuntime::find_callee_info_helper(JavaThread* thread,
                                              vframeStream& vfst,
                                              Bytecodes::Code& bc,
                                              CallInfo& callinfo, TRAPS) {
  Handle receiver;
  Handle nullHandle;  //create a handy null handle for exception returns

  assert(!vfst.at_end(), "Java frame must exist");

  // Find caller and bci from vframe
  methodHandle caller (THREAD, vfst.method());
  int          bci    = vfst.bci();

  // Find bytecode
  Bytecode_invoke* bytecode = Bytecode_invoke_at(caller, bci);
  bc = bytecode->adjusted_invoke_code();
  int bytecode_index = bytecode->index();

  // Find receiver for non-static call
  if (bc != Bytecodes::_invokestatic) {
    // This register map must be update since we need to find the receiver for
    // compiled frames. The receiver might be in a register.
    RegisterMap reg_map2(thread);
    frame stubFrame   = thread->last_frame();
    // Caller-frame is a compiled frame
    frame callerFrame = stubFrame.sender(&reg_map2);

    methodHandle callee = bytecode->static_target(CHECK_(nullHandle));
    if (callee.is_null()) {
      THROW_(vmSymbols::java_lang_NoSuchMethodException(), nullHandle);
    }
    // Retrieve from a compiled argument list
    receiver = Handle(THREAD, callerFrame.retrieve_receiver(&reg_map2));

    if (receiver.is_null()) {
      THROW_(vmSymbols::java_lang_NullPointerException(), nullHandle);
    }
  }

  // Resolve method. This is parameterized by bytecode.
  constantPoolHandle constants (THREAD, caller->constants());
  assert (receiver.is_null() || receiver->is_oop(), "wrong receiver");
  LinkResolver::resolve_invoke(callinfo, receiver, constants, bytecode_index, bc, CHECK_(nullHandle));

#ifdef ASSERT
  // Check that the receiver klass is of the right subtype and that it is initialized for virtual calls
  if (bc != Bytecodes::_invokestatic && bc != Bytecodes::_invokedynamic) {
    assert(receiver.not_null(), "should have thrown exception");
    KlassHandle receiver_klass (THREAD, receiver->klass());
    klassOop rk = constants->klass_ref_at(bytecode_index, CHECK_(nullHandle));
                            // klass is already loaded
    KlassHandle static_receiver_klass (THREAD, rk);
    assert(receiver_klass->is_subtype_of(static_receiver_klass()), "actual receiver must be subclass of static receiver klass");
    if (receiver_klass->oop_is_instance()) {
      if (instanceKlass::cast(receiver_klass())->is_not_initialized()) {
        tty->print_cr("ERROR: Klass not yet initialized!!");
        receiver_klass.print();
      }
      assert (!instanceKlass::cast(receiver_klass())->is_not_initialized(), "receiver_klass must be initialized");
    }
  }
#endif

  return receiver;
}

methodHandle SharedRuntime::find_callee_method(JavaThread* thread, TRAPS) {
  ResourceMark rm(THREAD);
  // We need first to check if any Java activations (compiled, interpreted)
  // exist on the stack since last JavaCall.  If not, we need
  // to get the target method from the JavaCall wrapper.
  vframeStream vfst(thread, true);  // Do not skip any javaCalls
  methodHandle callee_method;
  if (vfst.at_end()) {
    // No Java frames were found on stack since we did the JavaCall.
    // Hence the stack can only contain an entry_frame.  We need to
    // find the target method from the stub frame.
    RegisterMap reg_map(thread, false);
    frame fr = thread->last_frame();
    assert(fr.is_runtime_frame(), "must be a runtimeStub");
    fr = fr.sender(&reg_map);
    assert(fr.is_entry_frame(), "must be");
    // fr is now pointing to the entry frame.
    callee_method = methodHandle(THREAD, fr.entry_frame_call_wrapper()->callee_method());
    assert(fr.entry_frame_call_wrapper()->receiver() == NULL || !callee_method->is_static(), "non-null receiver for static call??");
  } else {
    Bytecodes::Code bc;
    CallInfo callinfo;
    find_callee_info_helper(thread, vfst, bc, callinfo, CHECK_(methodHandle()));
    callee_method = callinfo.selected_method();
  }
  assert(callee_method()->is_method(), "must be");
  return callee_method;
}

// Resolves a call.
methodHandle SharedRuntime::resolve_helper(JavaThread *thread,
                                           bool is_virtual,
                                           bool is_optimized, TRAPS) {
  methodHandle callee_method;
  callee_method = resolve_sub_helper(thread, is_virtual, is_optimized, THREAD);
  if (JvmtiExport::can_hotswap_or_post_breakpoint()) {
    int retry_count = 0;
    while (!HAS_PENDING_EXCEPTION && callee_method->is_old() &&
           callee_method->method_holder() != SystemDictionary::Object_klass()) {
      // If has a pending exception then there is no need to re-try to
      // resolve this method.
      // If the method has been redefined, we need to try again.
      // Hack: we have no way to update the vtables of arrays, so don't
      // require that java.lang.Object has been updated.

      // It is very unlikely that method is redefined more than 100 times
      // in the middle of resolve. If it is looping here more than 100 times
      // means then there could be a bug here.
      guarantee((retry_count++ < 100),
                "Could not resolve to latest version of redefined method");
      // method is redefined in the middle of resolve so re-try.
      callee_method = resolve_sub_helper(thread, is_virtual, is_optimized, THREAD);
    }
  }
  return callee_method;
}

// Resolves a call.  The compilers generate code for calls that go here
// and are patched with the real destination of the call.
methodHandle SharedRuntime::resolve_sub_helper(JavaThread *thread,
                                           bool is_virtual,
                                           bool is_optimized, TRAPS) {

  ResourceMark rm(thread);
  RegisterMap cbl_map(thread, false);
  frame caller_frame = thread->last_frame().sender(&cbl_map);

  CodeBlob* caller_cb = caller_frame.cb();
  guarantee(caller_cb != NULL && caller_cb->is_nmethod(), "must be called from nmethod");
  nmethod* caller_nm = caller_cb->as_nmethod_or_null();
  // make sure caller is not getting deoptimized
  // and removed before we are done with it.
  // CLEANUP - with lazy deopt shouldn't need this lock
  nmethodLocker caller_lock(caller_nm);


  // determine call info & receiver
  // note: a) receiver is NULL for static calls
  //       b) an exception is thrown if receiver is NULL for non-static calls
  CallInfo call_info;
  Bytecodes::Code invoke_code = Bytecodes::_illegal;
  Handle receiver = find_callee_info(thread, invoke_code,
                                     call_info, CHECK_(methodHandle()));
  methodHandle callee_method = call_info.selected_method();

  assert((!is_virtual && invoke_code == Bytecodes::_invokestatic) ||
         ( is_virtual && invoke_code != Bytecodes::_invokestatic), "inconsistent bytecode");

#ifndef PRODUCT
  // tracing/debugging/statistics
  int *addr = (is_optimized) ? (&_resolve_opt_virtual_ctr) :
                (is_virtual) ? (&_resolve_virtual_ctr) :
                               (&_resolve_static_ctr);
  Atomic::inc(addr);

  if (TraceCallFixup) {
    ResourceMark rm(thread);
    tty->print("resolving %s%s (%s) call to",
      (is_optimized) ? "optimized " : "", (is_virtual) ? "virtual" : "static",
      Bytecodes::name(invoke_code));
    callee_method->print_short_name(tty);
    tty->print_cr(" code: " INTPTR_FORMAT, callee_method->code());
  }
#endif

  // JSR 292
  // If the resolved method is a MethodHandle invoke target the call
  // site must be a MethodHandle call site.
  if (callee_method->is_method_handle_invoke()) {
    assert(caller_nm->is_method_handle_return(caller_frame.pc()), "must be MH call site");
  }

  // Compute entry points. This might require generation of C2I converter
  // frames, so we cannot be holding any locks here. Furthermore, the
  // computation of the entry points is independent of patching the call.  We
  // always return the entry-point, but we only patch the stub if the call has
  // not been deoptimized.  Return values: For a virtual call this is an
  // (cached_oop, destination address) pair. For a static call/optimized
  // virtual this is just a destination address.

  StaticCallInfo static_call_info;
  CompiledICInfo virtual_call_info;

  // Make sure the callee nmethod does not get deoptimized and removed before
  // we are done patching the code.
  nmethod* callee_nm = callee_method->code();
  nmethodLocker nl_callee(callee_nm);
#ifdef ASSERT
  address dest_entry_point = callee_nm == NULL ? 0 : callee_nm->entry_point(); // used below
#endif

  if (is_virtual) {
    assert(receiver.not_null(), "sanity check");
    bool static_bound = call_info.resolved_method()->can_be_statically_bound();
    KlassHandle h_klass(THREAD, receiver->klass());
    CompiledIC::compute_monomorphic_entry(callee_method, h_klass,
                     is_optimized, static_bound, virtual_call_info,
                     CHECK_(methodHandle()));
  } else {
    // static call
    CompiledStaticCall::compute_entry(callee_method, static_call_info);
  }

  // grab lock, check for deoptimization and potentially patch caller
  {
    MutexLocker ml_patch(CompiledIC_lock);

    // Now that we are ready to patch if the methodOop was redefined then
    // don't update call site and let the caller retry.

    if (!callee_method->is_old()) {
#ifdef ASSERT
      // We must not try to patch to jump to an already unloaded method.
      if (dest_entry_point != 0) {
        assert(CodeCache::find_blob(dest_entry_point) != NULL,
               "should not unload nmethod while locked");
      }
#endif
      if (is_virtual) {
        CompiledIC* inline_cache = CompiledIC_before(caller_frame.pc());
        if (inline_cache->is_clean()) {
          inline_cache->set_to_monomorphic(virtual_call_info);
        }
      } else {
        CompiledStaticCall* ssc = compiledStaticCall_before(caller_frame.pc());
        if (ssc->is_clean()) ssc->set(static_call_info);
      }
    }

  } // unlock CompiledIC_lock

  return callee_method;
}


// Inline caches exist only in compiled code
JRT_BLOCK_ENTRY(address, SharedRuntime::handle_wrong_method_ic_miss(JavaThread* thread))
#ifdef ASSERT
  RegisterMap reg_map(thread, false);
  frame stub_frame = thread->last_frame();
  assert(stub_frame.is_runtime_frame(), "sanity check");
  frame caller_frame = stub_frame.sender(&reg_map);
  assert(!caller_frame.is_interpreted_frame() && !caller_frame.is_entry_frame(), "unexpected frame");
#endif /* ASSERT */

  methodHandle callee_method;
  JRT_BLOCK
    callee_method = SharedRuntime::handle_ic_miss_helper(thread, CHECK_NULL);
    // Return methodOop through TLS
    thread->set_vm_result(callee_method());
  JRT_BLOCK_END
  // return compiled code entry point after potential safepoints
  assert(callee_method->verified_code_entry() != NULL, " Jump to zero!");
  return callee_method->verified_code_entry();
JRT_END


// Handle call site that has been made non-entrant
JRT_BLOCK_ENTRY(address, SharedRuntime::handle_wrong_method(JavaThread* thread))
  // 6243940 We might end up in here if the callee is deoptimized
  // as we race to call it.  We don't want to take a safepoint if
  // the caller was interpreted because the caller frame will look
  // interpreted to the stack walkers and arguments are now
  // "compiled" so it is much better to make this transition
  // invisible to the stack walking code. The i2c path will
  // place the callee method in the callee_target. It is stashed
  // there because if we try and find the callee by normal means a
  // safepoint is possible and have trouble gc'ing the compiled args.
  RegisterMap reg_map(thread, false);
  frame stub_frame = thread->last_frame();
  assert(stub_frame.is_runtime_frame(), "sanity check");
  frame caller_frame = stub_frame.sender(&reg_map);

  // MethodHandle invokes don't have a CompiledIC and should always
  // simply redispatch to the callee_target.
  address   sender_pc = caller_frame.pc();
  CodeBlob* sender_cb = caller_frame.cb();
  nmethod*  sender_nm = sender_cb->as_nmethod_or_null();
  bool is_mh_invoke_via_adapter = false;  // Direct c2c call or via adapter?
  if (sender_nm != NULL && sender_nm->is_method_handle_return(sender_pc)) {
    // If the callee_target is set, then we have come here via an i2c
    // adapter.
    methodOop callee = thread->callee_target();
    if (callee != NULL) {
      assert(callee->is_method(), "sanity");
      is_mh_invoke_via_adapter = true;
    }
  }

  if (caller_frame.is_interpreted_frame() ||
      caller_frame.is_entry_frame()       ||
      is_mh_invoke_via_adapter) {
    methodOop callee = thread->callee_target();
    guarantee(callee != NULL && callee->is_method(), "bad handshake");
    thread->set_vm_result(callee);
    thread->set_callee_target(NULL);
    return callee->get_c2i_entry();
  }

  // Must be compiled to compiled path which is safe to stackwalk
  methodHandle callee_method;
  JRT_BLOCK
    // Force resolving of caller (if we called from compiled frame)
    callee_method = SharedRuntime::reresolve_call_site(thread, CHECK_NULL);
    thread->set_vm_result(callee_method());
  JRT_BLOCK_END
  // return compiled code entry point after potential safepoints
  assert(callee_method->verified_code_entry() != NULL, " Jump to zero!");
  return callee_method->verified_code_entry();
JRT_END


// resolve a static call and patch code
JRT_BLOCK_ENTRY(address, SharedRuntime::resolve_static_call_C(JavaThread *thread ))
  methodHandle callee_method;
  JRT_BLOCK
    callee_method = SharedRuntime::resolve_helper(thread, false, false, CHECK_NULL);
    thread->set_vm_result(callee_method());
  JRT_BLOCK_END
  // return compiled code entry point after potential safepoints
  assert(callee_method->verified_code_entry() != NULL, " Jump to zero!");
  return callee_method->verified_code_entry();
JRT_END


// resolve virtual call and update inline cache to monomorphic
JRT_BLOCK_ENTRY(address, SharedRuntime::resolve_virtual_call_C(JavaThread *thread ))
  methodHandle callee_method;
  JRT_BLOCK
    callee_method = SharedRuntime::resolve_helper(thread, true, false, CHECK_NULL);
    thread->set_vm_result(callee_method());
  JRT_BLOCK_END
  // return compiled code entry point after potential safepoints
  assert(callee_method->verified_code_entry() != NULL, " Jump to zero!");
  return callee_method->verified_code_entry();
JRT_END


// Resolve a virtual call that can be statically bound (e.g., always
// monomorphic, so it has no inline cache).  Patch code to resolved target.
JRT_BLOCK_ENTRY(address, SharedRuntime::resolve_opt_virtual_call_C(JavaThread *thread))
  methodHandle callee_method;
  JRT_BLOCK
    callee_method = SharedRuntime::resolve_helper(thread, true, true, CHECK_NULL);
    thread->set_vm_result(callee_method());
  JRT_BLOCK_END
  // return compiled code entry point after potential safepoints
  assert(callee_method->verified_code_entry() != NULL, " Jump to zero!");
  return callee_method->verified_code_entry();
JRT_END





methodHandle SharedRuntime::handle_ic_miss_helper(JavaThread *thread, TRAPS) {
  ResourceMark rm(thread);
  CallInfo call_info;
  Bytecodes::Code bc;

  // receiver is NULL for static calls. An exception is thrown for NULL
  // receivers for non-static calls
  Handle receiver = find_callee_info(thread, bc, call_info,
                                     CHECK_(methodHandle()));
  // Compiler1 can produce virtual call sites that can actually be statically bound
  // If we fell thru to below we would think that the site was going megamorphic
  // when in fact the site can never miss. Worse because we'd think it was megamorphic
  // we'd try and do a vtable dispatch however methods that can be statically bound
  // don't have vtable entries (vtable_index < 0) and we'd blow up. So we force a
  // reresolution of the  call site (as if we did a handle_wrong_method and not an
  // plain ic_miss) and the site will be converted to an optimized virtual call site
  // never to miss again. I don't believe C2 will produce code like this but if it
  // did this would still be the correct thing to do for it too, hence no ifdef.
  //
  if (call_info.resolved_method()->can_be_statically_bound()) {
    methodHandle callee_method = SharedRuntime::reresolve_call_site(thread, CHECK_(methodHandle()));
    if (TraceCallFixup) {
      RegisterMap reg_map(thread, false);
      frame caller_frame = thread->last_frame().sender(&reg_map);
      ResourceMark rm(thread);
      tty->print("converting IC miss to reresolve (%s) call to", Bytecodes::name(bc));
      callee_method->print_short_name(tty);
      tty->print_cr(" from pc: " INTPTR_FORMAT, caller_frame.pc());
      tty->print_cr(" code: " INTPTR_FORMAT, callee_method->code());
    }
    return callee_method;
  }

  methodHandle callee_method = call_info.selected_method();

  bool should_be_mono = false;

#ifndef PRODUCT
  Atomic::inc(&_ic_miss_ctr);

  // Statistics & Tracing
  if (TraceCallFixup) {
    ResourceMark rm(thread);
    tty->print("IC miss (%s) call to", Bytecodes::name(bc));
    callee_method->print_short_name(tty);
    tty->print_cr(" code: " INTPTR_FORMAT, callee_method->code());
  }

  if (ICMissHistogram) {
    MutexLocker m(VMStatistic_lock);
    RegisterMap reg_map(thread, false);
    frame f = thread->last_frame().real_sender(&reg_map);// skip runtime stub
    // produce statistics under the lock
    trace_ic_miss(f.pc());
  }
#endif

  // install an event collector so that when a vtable stub is created the
  // profiler can be notified via a DYNAMIC_CODE_GENERATED event. The
  // event can't be posted when the stub is created as locks are held
  // - instead the event will be deferred until the event collector goes
  // out of scope.
  JvmtiDynamicCodeEventCollector event_collector;

  // Update inline cache to megamorphic. Skip update if caller has been
  // made non-entrant or we are called from interpreted.
  { MutexLocker ml_patch (CompiledIC_lock);
    RegisterMap reg_map(thread, false);
    frame caller_frame = thread->last_frame().sender(&reg_map);
    CodeBlob* cb = caller_frame.cb();
    if (cb->is_nmethod() && ((nmethod*)cb)->is_in_use()) {
      // Not a non-entrant nmethod, so find inline_cache
      CompiledIC* inline_cache = CompiledIC_before(caller_frame.pc());
      bool should_be_mono = false;
      if (inline_cache->is_optimized()) {
        if (TraceCallFixup) {
          ResourceMark rm(thread);
          tty->print("OPTIMIZED IC miss (%s) call to", Bytecodes::name(bc));
          callee_method->print_short_name(tty);
          tty->print_cr(" code: " INTPTR_FORMAT, callee_method->code());
        }
        should_be_mono = true;
      } else {
        compiledICHolderOop ic_oop = (compiledICHolderOop) inline_cache->cached_oop();
        if ( ic_oop != NULL && ic_oop->is_compiledICHolder()) {

          if (receiver()->klass() == ic_oop->holder_klass()) {
            // This isn't a real miss. We must have seen that compiled code
            // is now available and we want the call site converted to a
            // monomorphic compiled call site.
            // We can't assert for callee_method->code() != NULL because it
            // could have been deoptimized in the meantime
            if (TraceCallFixup) {
              ResourceMark rm(thread);
              tty->print("FALSE IC miss (%s) converting to compiled call to", Bytecodes::name(bc));
              callee_method->print_short_name(tty);
              tty->print_cr(" code: " INTPTR_FORMAT, callee_method->code());
            }
            should_be_mono = true;
          }
        }
      }

      if (should_be_mono) {

        // We have a path that was monomorphic but was going interpreted
        // and now we have (or had) a compiled entry. We correct the IC
        // by using a new icBuffer.
        CompiledICInfo info;
        KlassHandle receiver_klass(THREAD, receiver()->klass());
        inline_cache->compute_monomorphic_entry(callee_method,
                                                receiver_klass,
                                                inline_cache->is_optimized(),
                                                false,
                                                info, CHECK_(methodHandle()));
        inline_cache->set_to_monomorphic(info);
      } else if (!inline_cache->is_megamorphic() && !inline_cache->is_clean()) {
        // Change to megamorphic
        inline_cache->set_to_megamorphic(&call_info, bc, CHECK_(methodHandle()));
      } else {
        // Either clean or megamorphic
      }
    }
  } // Release CompiledIC_lock

  return callee_method;
}

//
// Resets a call-site in compiled code so it will get resolved again.
// This routines handles both virtual call sites, optimized virtual call
// sites, and static call sites. Typically used to change a call sites
// destination from compiled to interpreted.
//
methodHandle SharedRuntime::reresolve_call_site(JavaThread *thread, TRAPS) {
  ResourceMark rm(thread);
  RegisterMap reg_map(thread, false);
  frame stub_frame = thread->last_frame();
  assert(stub_frame.is_runtime_frame(), "must be a runtimeStub");
  frame caller = stub_frame.sender(&reg_map);

  // Do nothing if the frame isn't a live compiled frame.
  // nmethod could be deoptimized by the time we get here
  // so no update to the caller is needed.

  if (caller.is_compiled_frame() && !caller.is_deoptimized_frame()) {

    address pc = caller.pc();
    Events::log("update call-site at pc " INTPTR_FORMAT, pc);

    // Default call_addr is the location of the "basic" call.
    // Determine the address of the call we a reresolving. With
    // Inline Caches we will always find a recognizable call.
    // With Inline Caches disabled we may or may not find a
    // recognizable call. We will always find a call for static
    // calls and for optimized virtual calls. For vanilla virtual
    // calls it depends on the state of the UseInlineCaches switch.
    //
    // With Inline Caches disabled we can get here for a virtual call
    // for two reasons:
    //   1 - calling an abstract method. The vtable for abstract methods
    //       will run us thru handle_wrong_method and we will eventually
    //       end up in the interpreter to throw the ame.
    //   2 - a racing deoptimization. We could be doing a vanilla vtable
    //       call and between the time we fetch the entry address and
    //       we jump to it the target gets deoptimized. Similar to 1
    //       we will wind up in the interprter (thru a c2i with c2).
    //
    address call_addr = NULL;
    {
      // Get call instruction under lock because another thread may be
      // busy patching it.
      MutexLockerEx ml_patch(Patching_lock, Mutex::_no_safepoint_check_flag);
      // Location of call instruction
      if (NativeCall::is_call_before(pc)) {
        NativeCall *ncall = nativeCall_before(pc);
        call_addr = ncall->instruction_address();
      }
    }

    // Check for static or virtual call
    bool is_static_call = false;
    nmethod* caller_nm = CodeCache::find_nmethod(pc);
    // Make sure nmethod doesn't get deoptimized and removed until
    // this is done with it.
    // CLEANUP - with lazy deopt shouldn't need this lock
    nmethodLocker nmlock(caller_nm);

    if (call_addr != NULL) {
      RelocIterator iter(caller_nm, call_addr, call_addr+1);
      int ret = iter.next(); // Get item
      if (ret) {
        assert(iter.addr() == call_addr, "must find call");
        if (iter.type() == relocInfo::static_call_type) {
          is_static_call = true;
        } else {
          assert(iter.type() == relocInfo::virtual_call_type ||
                 iter.type() == relocInfo::opt_virtual_call_type
                , "unexpected relocInfo. type");
        }
      } else {
        assert(!UseInlineCaches, "relocation info. must exist for this address");
      }

      // Cleaning the inline cache will force a new resolve. This is more robust
      // than directly setting it to the new destination, since resolving of calls
      // is always done through the same code path. (experience shows that it
      // leads to very hard to track down bugs, if an inline cache gets updated
      // to a wrong method). It should not be performance critical, since the
      // resolve is only done once.

      MutexLocker ml(CompiledIC_lock);
      //
      // We do not patch the call site if the nmethod has been made non-entrant
      // as it is a waste of time
      //
      if (caller_nm->is_in_use()) {
        if (is_static_call) {
          CompiledStaticCall* ssc= compiledStaticCall_at(call_addr);
          ssc->set_to_clean();
        } else {
          // compiled, dispatched call (which used to call an interpreted method)
          CompiledIC* inline_cache = CompiledIC_at(call_addr);
          inline_cache->set_to_clean();
        }
      }
    }

  }

  methodHandle callee_method = find_callee_method(thread, CHECK_(methodHandle()));


#ifndef PRODUCT
  Atomic::inc(&_wrong_method_ctr);

  if (TraceCallFixup) {
    ResourceMark rm(thread);
    tty->print("handle_wrong_method reresolving call to");
    callee_method->print_short_name(tty);
    tty->print_cr(" code: " INTPTR_FORMAT, callee_method->code());
  }
#endif

  return callee_method;
}

// ---------------------------------------------------------------------------
// We are calling the interpreter via a c2i. Normally this would mean that
// we were called by a compiled method. However we could have lost a race
// where we went int -> i2c -> c2i and so the caller could in fact be
// interpreted. If the caller is compiled we attempt to patch the caller
// so he no longer calls into the interpreter.
IRT_LEAF(void, SharedRuntime::fixup_callers_callsite(methodOopDesc* method, address caller_pc))
  methodOop moop(method);

  address entry_point = moop->from_compiled_entry();

  // It's possible that deoptimization can occur at a call site which hasn't
  // been resolved yet, in which case this function will be called from
  // an nmethod that has been patched for deopt and we can ignore the
  // request for a fixup.
  // Also it is possible that we lost a race in that from_compiled_entry
  // is now back to the i2c in that case we don't need to patch and if
  // we did we'd leap into space because the callsite needs to use
  // "to interpreter" stub in order to load up the methodOop. Don't
  // ask me how I know this...

  CodeBlob* cb = CodeCache::find_blob(caller_pc);
  if (!cb->is_nmethod() || entry_point == moop->get_c2i_entry()) {
    return;
  }

  // The check above makes sure this is a nmethod.
  nmethod* nm = cb->as_nmethod_or_null();
  assert(nm, "must be");

  // Don't fixup MethodHandle call sites as c2i/i2c adapters are used
  // to implement MethodHandle actions.
  if (nm->is_method_handle_return(caller_pc)) {
    return;
  }

  // There is a benign race here. We could be attempting to patch to a compiled
  // entry point at the same time the callee is being deoptimized. If that is
  // the case then entry_point may in fact point to a c2i and we'd patch the
  // call site with the same old data. clear_code will set code() to NULL
  // at the end of it. If we happen to see that NULL then we can skip trying
  // to patch. If we hit the window where the callee has a c2i in the
  // from_compiled_entry and the NULL isn't present yet then we lose the race
  // and patch the code with the same old data. Asi es la vida.

  if (moop->code() == NULL) return;

  if (nm->is_in_use()) {

    // Expect to find a native call there (unless it was no-inline cache vtable dispatch)
    MutexLockerEx ml_patch(Patching_lock, Mutex::_no_safepoint_check_flag);
    if (NativeCall::is_call_before(caller_pc + frame::pc_return_offset)) {
      NativeCall *call = nativeCall_before(caller_pc + frame::pc_return_offset);
      //
      // bug 6281185. We might get here after resolving a call site to a vanilla
      // virtual call. Because the resolvee uses the verified entry it may then
      // see compiled code and attempt to patch the site by calling us. This would
      // then incorrectly convert the call site to optimized and its downhill from
      // there. If you're lucky you'll get the assert in the bugid, if not you've
      // just made a call site that could be megamorphic into a monomorphic site
      // for the rest of its life! Just another racing bug in the life of
      // fixup_callers_callsite ...
      //
      RelocIterator iter(cb, call->instruction_address(), call->next_instruction_address());
      iter.next();
      assert(iter.has_current(), "must have a reloc at java call site");
      relocInfo::relocType typ = iter.reloc()->type();
      if ( typ != relocInfo::static_call_type &&
           typ != relocInfo::opt_virtual_call_type &&
           typ != relocInfo::static_stub_type) {
        return;
      }
      address destination = call->destination();
      if (destination != entry_point) {
        CodeBlob* callee = CodeCache::find_blob(destination);
        // callee == cb seems weird. It means calling interpreter thru stub.
        if (callee == cb || callee->is_adapter_blob()) {
          // static call or optimized virtual
          if (TraceCallFixup) {
            tty->print("fixup callsite           at " INTPTR_FORMAT " to compiled code for", caller_pc);
            moop->print_short_name(tty);
            tty->print_cr(" to " INTPTR_FORMAT, entry_point);
          }
          call->set_destination_mt_safe(entry_point);
        } else {
          if (TraceCallFixup) {
            tty->print("failed to fixup callsite at " INTPTR_FORMAT " to compiled code for", caller_pc);
            moop->print_short_name(tty);
            tty->print_cr(" to " INTPTR_FORMAT, entry_point);
          }
          // assert is too strong could also be resolve destinations.
          // assert(InlineCacheBuffer::contains(destination) || VtableStubs::contains(destination), "must be");
        }
      } else {
          if (TraceCallFixup) {
            tty->print("already patched callsite at " INTPTR_FORMAT " to compiled code for", caller_pc);
            moop->print_short_name(tty);
            tty->print_cr(" to " INTPTR_FORMAT, entry_point);
          }
      }
    }
  }

IRT_END


// same as JVM_Arraycopy, but called directly from compiled code
JRT_ENTRY(void, SharedRuntime::slow_arraycopy_C(oopDesc* src,  jint src_pos,
                                                oopDesc* dest, jint dest_pos,
                                                jint length,
                                                JavaThread* thread)) {
#ifndef PRODUCT
  _slow_array_copy_ctr++;
#endif
  // Check if we have null pointers
  if (src == NULL || dest == NULL) {
    THROW(vmSymbols::java_lang_NullPointerException());
  }
  // Do the copy.  The casts to arrayOop are necessary to the copy_array API,
  // even though the copy_array API also performs dynamic checks to ensure
  // that src and dest are truly arrays (and are conformable).
  // The copy_array mechanism is awkward and could be removed, but
  // the compilers don't call this function except as a last resort,
  // so it probably doesn't matter.
  Klass::cast(src->klass())->copy_array((arrayOopDesc*)src,  src_pos,
                                        (arrayOopDesc*)dest, dest_pos,
                                        length, thread);
}
JRT_END

char* SharedRuntime::generate_class_cast_message(
    JavaThread* thread, const char* objName) {

  // Get target class name from the checkcast instruction
  vframeStream vfst(thread, true);
  assert(!vfst.at_end(), "Java frame must exist");
  Bytecode_checkcast* cc = Bytecode_checkcast_at(
    vfst.method()->bcp_from(vfst.bci()));
  Klass* targetKlass = Klass::cast(vfst.method()->constants()->klass_at(
    cc->index(), thread));
  return generate_class_cast_message(objName, targetKlass->external_name());
}

char* SharedRuntime::generate_wrong_method_type_message(JavaThread* thread,
                                                        oopDesc* required,
                                                        oopDesc* actual) {
  assert(EnableMethodHandles, "");
  oop singleKlass = wrong_method_type_is_for_single_argument(thread, required);
  if (singleKlass != NULL) {
    const char* objName = "argument or return value";
    if (actual != NULL) {
      // be flexible about the junk passed in:
      klassOop ak = (actual->is_klass()
                     ? (klassOop)actual
                     : actual->klass());
      objName = Klass::cast(ak)->external_name();
    }
    Klass* targetKlass = Klass::cast(required->is_klass()
                                     ? (klassOop)required
                                     : java_lang_Class::as_klassOop(required));
    return generate_class_cast_message(objName, targetKlass->external_name());
  } else {
    // %%% need to get the MethodType string, without messing around too much
    // Get a signature from the invoke instruction
    const char* mhName = "method handle";
    const char* targetType = "the required signature";
    vframeStream vfst(thread, true);
    if (!vfst.at_end()) {
      Bytecode_invoke* call = Bytecode_invoke_at(vfst.method(), vfst.bci());
      methodHandle target;
      {
        EXCEPTION_MARK;
        target = call->static_target(THREAD);
        if (HAS_PENDING_EXCEPTION) { CLEAR_PENDING_EXCEPTION; }
      }
      if (target.not_null()
          && target->is_method_handle_invoke()
          && required == target->method_handle_type()) {
        targetType = target->signature()->as_C_string();
      }
    }
    klassOop kignore; int fignore;
    methodOop actual_method = MethodHandles::decode_method(actual,
                                                          kignore, fignore);
    if (actual_method != NULL) {
      if (actual_method->name() == vmSymbols::invoke_name())
        mhName = "$";
      else
        mhName = actual_method->signature()->as_C_string();
      if (mhName[0] == '$')
        mhName = actual_method->signature()->as_C_string();
    }
    return generate_class_cast_message(mhName, targetType,
                                       " cannot be called as ");
  }
}

oop SharedRuntime::wrong_method_type_is_for_single_argument(JavaThread* thr,
                                                            oopDesc* required) {
  if (required == NULL)  return NULL;
  if (required->klass() == SystemDictionary::Class_klass())
    return required;
  if (required->is_klass())
    return Klass::cast(klassOop(required))->java_mirror();
  return NULL;
}


char* SharedRuntime::generate_class_cast_message(
    const char* objName, const char* targetKlassName, const char* desc) {
  size_t msglen = strlen(objName) + strlen(desc) + strlen(targetKlassName) + 1;

  char* message = NEW_RESOURCE_ARRAY(char, msglen);
  if (NULL == message) {
    // Shouldn't happen, but don't cause even more problems if it does
    message = const_cast<char*>(objName);
  } else {
    jio_snprintf(message, msglen, "%s%s%s", objName, desc, targetKlassName);
  }
  return message;
}

JRT_LEAF(void, SharedRuntime::reguard_yellow_pages())
  (void) JavaThread::current()->reguard_stack();
JRT_END


// Handles the uncommon case in locking, i.e., contention or an inflated lock.
#ifndef PRODUCT
int SharedRuntime::_monitor_enter_ctr=0;
#endif
JRT_ENTRY_NO_ASYNC(void, SharedRuntime::complete_monitor_locking_C(oopDesc* _obj, BasicLock* lock, JavaThread* thread))
  oop obj(_obj);
#ifndef PRODUCT
  _monitor_enter_ctr++;             // monitor enter slow
#endif
  if (PrintBiasedLockingStatistics) {
    Atomic::inc(BiasedLocking::slow_path_entry_count_addr());
  }
  Handle h_obj(THREAD, obj);
  if (UseBiasedLocking) {
    // Retry fast entry if bias is revoked to avoid unnecessary inflation
    ObjectSynchronizer::fast_enter(h_obj, lock, true, CHECK);
  } else {
    ObjectSynchronizer::slow_enter(h_obj, lock, CHECK);
  }
  assert(!HAS_PENDING_EXCEPTION, "Should have no exception here");
JRT_END

#ifndef PRODUCT
int SharedRuntime::_monitor_exit_ctr=0;
#endif
// Handles the uncommon cases of monitor unlocking in compiled code
JRT_LEAF(void, SharedRuntime::complete_monitor_unlocking_C(oopDesc* _obj, BasicLock* lock))
   oop obj(_obj);
#ifndef PRODUCT
  _monitor_exit_ctr++;              // monitor exit slow
#endif
  Thread* THREAD = JavaThread::current();
  // I'm not convinced we need the code contained by MIGHT_HAVE_PENDING anymore
  // testing was unable to ever fire the assert that guarded it so I have removed it.
  assert(!HAS_PENDING_EXCEPTION, "Do we need code below anymore?");
#undef MIGHT_HAVE_PENDING
#ifdef MIGHT_HAVE_PENDING
  // Save and restore any pending_exception around the exception mark.
  // While the slow_exit must not throw an exception, we could come into
  // this routine with one set.
  oop pending_excep = NULL;
  const char* pending_file;
  int pending_line;
  if (HAS_PENDING_EXCEPTION) {
    pending_excep = PENDING_EXCEPTION;
    pending_file  = THREAD->exception_file();
    pending_line  = THREAD->exception_line();
    CLEAR_PENDING_EXCEPTION;
  }
#endif /* MIGHT_HAVE_PENDING */

  {
    // Exit must be non-blocking, and therefore no exceptions can be thrown.
    EXCEPTION_MARK;
    ObjectSynchronizer::slow_exit(obj, lock, THREAD);
  }

#ifdef MIGHT_HAVE_PENDING
  if (pending_excep != NULL) {
    THREAD->set_pending_exception(pending_excep, pending_file, pending_line);
  }
#endif /* MIGHT_HAVE_PENDING */
JRT_END

#ifndef PRODUCT

void SharedRuntime::print_statistics() {
  ttyLocker ttyl;
  if (xtty != NULL)  xtty->head("statistics type='SharedRuntime'");

  if (_monitor_enter_ctr ) tty->print_cr("%5d monitor enter slow",  _monitor_enter_ctr);
  if (_monitor_exit_ctr  ) tty->print_cr("%5d monitor exit slow",   _monitor_exit_ctr);
  if (_throw_null_ctr) tty->print_cr("%5d implicit null throw", _throw_null_ctr);

  SharedRuntime::print_ic_miss_histogram();

  if (CountRemovableExceptions) {
    if (_nof_removable_exceptions > 0) {
      Unimplemented(); // this counter is not yet incremented
      tty->print_cr("Removable exceptions: %d", _nof_removable_exceptions);
    }
  }

  // Dump the JRT_ENTRY counters
  if( _new_instance_ctr ) tty->print_cr("%5d new instance requires GC", _new_instance_ctr);
  if( _new_array_ctr ) tty->print_cr("%5d new array requires GC", _new_array_ctr);
  if( _multi1_ctr ) tty->print_cr("%5d multianewarray 1 dim", _multi1_ctr);
  if( _multi2_ctr ) tty->print_cr("%5d multianewarray 2 dim", _multi2_ctr);
  if( _multi3_ctr ) tty->print_cr("%5d multianewarray 3 dim", _multi3_ctr);
  if( _multi4_ctr ) tty->print_cr("%5d multianewarray 4 dim", _multi4_ctr);
  if( _multi5_ctr ) tty->print_cr("%5d multianewarray 5 dim", _multi5_ctr);

  tty->print_cr("%5d inline cache miss in compiled", _ic_miss_ctr );
  tty->print_cr("%5d wrong method", _wrong_method_ctr );
  tty->print_cr("%5d unresolved static call site", _resolve_static_ctr );
  tty->print_cr("%5d unresolved virtual call site", _resolve_virtual_ctr );
  tty->print_cr("%5d unresolved opt virtual call site", _resolve_opt_virtual_ctr );

  if( _mon_enter_stub_ctr ) tty->print_cr("%5d monitor enter stub", _mon_enter_stub_ctr );
  if( _mon_exit_stub_ctr ) tty->print_cr("%5d monitor exit stub", _mon_exit_stub_ctr );
  if( _mon_enter_ctr ) tty->print_cr("%5d monitor enter slow", _mon_enter_ctr );
  if( _mon_exit_ctr ) tty->print_cr("%5d monitor exit slow", _mon_exit_ctr );
  if( _partial_subtype_ctr) tty->print_cr("%5d slow partial subtype", _partial_subtype_ctr );
  if( _jbyte_array_copy_ctr ) tty->print_cr("%5d byte array copies", _jbyte_array_copy_ctr );
  if( _jshort_array_copy_ctr ) tty->print_cr("%5d short array copies", _jshort_array_copy_ctr );
  if( _jint_array_copy_ctr ) tty->print_cr("%5d int array copies", _jint_array_copy_ctr );
  if( _jlong_array_copy_ctr ) tty->print_cr("%5d long array copies", _jlong_array_copy_ctr );
  if( _oop_array_copy_ctr ) tty->print_cr("%5d oop array copies", _oop_array_copy_ctr );
  if( _checkcast_array_copy_ctr ) tty->print_cr("%5d checkcast array copies", _checkcast_array_copy_ctr );
  if( _unsafe_array_copy_ctr ) tty->print_cr("%5d unsafe array copies", _unsafe_array_copy_ctr );
  if( _generic_array_copy_ctr ) tty->print_cr("%5d generic array copies", _generic_array_copy_ctr );
  if( _slow_array_copy_ctr ) tty->print_cr("%5d slow array copies", _slow_array_copy_ctr );
  if( _find_handler_ctr ) tty->print_cr("%5d find exception handler", _find_handler_ctr );
  if( _rethrow_ctr ) tty->print_cr("%5d rethrow handler", _rethrow_ctr );

  AdapterHandlerLibrary::print_statistics();

  if (xtty != NULL)  xtty->tail("statistics");
}

inline double percent(int x, int y) {
  return 100.0 * x / MAX2(y, 1);
}

class MethodArityHistogram {
 public:
  enum { MAX_ARITY = 256 };
 private:
  static int _arity_histogram[MAX_ARITY];     // histogram of #args
  static int _size_histogram[MAX_ARITY];      // histogram of arg size in words
  static int _max_arity;                      // max. arity seen
  static int _max_size;                       // max. arg size seen

  static void add_method_to_histogram(nmethod* nm) {
    methodOop m = nm->method();
    ArgumentCount args(m->signature());
    int arity   = args.size() + (m->is_static() ? 0 : 1);
    int argsize = m->size_of_parameters();
    arity   = MIN2(arity, MAX_ARITY-1);
    argsize = MIN2(argsize, MAX_ARITY-1);
    int count = nm->method()->compiled_invocation_count();
    _arity_histogram[arity]  += count;
    _size_histogram[argsize] += count;
    _max_arity = MAX2(_max_arity, arity);
    _max_size  = MAX2(_max_size, argsize);
  }

  void print_histogram_helper(int n, int* histo, const char* name) {
    const int N = MIN2(5, n);
    tty->print_cr("\nHistogram of call arity (incl. rcvr, calls to compiled methods only):");
    double sum = 0;
    double weighted_sum = 0;
    int i;
    for (i = 0; i <= n; i++) { sum += histo[i]; weighted_sum += i*histo[i]; }
    double rest = sum;
    double percent = sum / 100;
    for (i = 0; i <= N; i++) {
      rest -= histo[i];
      tty->print_cr("%4d: %7d (%5.1f%%)", i, histo[i], histo[i] / percent);
    }
    tty->print_cr("rest: %7d (%5.1f%%))", (int)rest, rest / percent);
    tty->print_cr("(avg. %s = %3.1f, max = %d)", name, weighted_sum / sum, n);
  }

  void print_histogram() {
    tty->print_cr("\nHistogram of call arity (incl. rcvr, calls to compiled methods only):");
    print_histogram_helper(_max_arity, _arity_histogram, "arity");
    tty->print_cr("\nSame for parameter size (in words):");
    print_histogram_helper(_max_size, _size_histogram, "size");
    tty->cr();
  }

 public:
  MethodArityHistogram() {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    _max_arity = _max_size = 0;
    for (int i = 0; i < MAX_ARITY; i++) _arity_histogram[i] = _size_histogram [i] = 0;
    CodeCache::nmethods_do(add_method_to_histogram);
    print_histogram();
  }
};

int MethodArityHistogram::_arity_histogram[MethodArityHistogram::MAX_ARITY];
int MethodArityHistogram::_size_histogram[MethodArityHistogram::MAX_ARITY];
int MethodArityHistogram::_max_arity;
int MethodArityHistogram::_max_size;

void SharedRuntime::print_call_statistics(int comp_total) {
  tty->print_cr("Calls from compiled code:");
  int total  = _nof_normal_calls + _nof_interface_calls + _nof_static_calls;
  int mono_c = _nof_normal_calls - _nof_optimized_calls - _nof_megamorphic_calls;
  int mono_i = _nof_interface_calls - _nof_optimized_interface_calls - _nof_megamorphic_interface_calls;
  tty->print_cr("\t%9d   (%4.1f%%) total non-inlined   ", total, percent(total, total));
  tty->print_cr("\t%9d   (%4.1f%%) virtual calls       ", _nof_normal_calls, percent(_nof_normal_calls, total));
  tty->print_cr("\t  %9d  (%3.0f%%)   inlined          ", _nof_inlined_calls, percent(_nof_inlined_calls, _nof_normal_calls));
  tty->print_cr("\t  %9d  (%3.0f%%)   optimized        ", _nof_optimized_calls, percent(_nof_optimized_calls, _nof_normal_calls));
  tty->print_cr("\t  %9d  (%3.0f%%)   monomorphic      ", mono_c, percent(mono_c, _nof_normal_calls));
  tty->print_cr("\t  %9d  (%3.0f%%)   megamorphic      ", _nof_megamorphic_calls, percent(_nof_megamorphic_calls, _nof_normal_calls));
  tty->print_cr("\t%9d   (%4.1f%%) interface calls     ", _nof_interface_calls, percent(_nof_interface_calls, total));
  tty->print_cr("\t  %9d  (%3.0f%%)   inlined          ", _nof_inlined_interface_calls, percent(_nof_inlined_interface_calls, _nof_interface_calls));
  tty->print_cr("\t  %9d  (%3.0f%%)   optimized        ", _nof_optimized_interface_calls, percent(_nof_optimized_interface_calls, _nof_interface_calls));
  tty->print_cr("\t  %9d  (%3.0f%%)   monomorphic      ", mono_i, percent(mono_i, _nof_interface_calls));
  tty->print_cr("\t  %9d  (%3.0f%%)   megamorphic      ", _nof_megamorphic_interface_calls, percent(_nof_megamorphic_interface_calls, _nof_interface_calls));
  tty->print_cr("\t%9d   (%4.1f%%) static/special calls", _nof_static_calls, percent(_nof_static_calls, total));
  tty->print_cr("\t  %9d  (%3.0f%%)   inlined          ", _nof_inlined_static_calls, percent(_nof_inlined_static_calls, _nof_static_calls));
  tty->cr();
  tty->print_cr("Note 1: counter updates are not MT-safe.");
  tty->print_cr("Note 2: %% in major categories are relative to total non-inlined calls;");
  tty->print_cr("        %% in nested categories are relative to their category");
  tty->print_cr("        (and thus add up to more than 100%% with inlining)");
  tty->cr();

  MethodArityHistogram h;
}
#endif


// A simple wrapper class around the calling convention information
// that allows sharing of adapters for the same calling convention.
class AdapterFingerPrint : public CHeapObj {
 private:
  union {
    int  _compact[3];
    int* _fingerprint;
  } _value;
  int _length; // A negative length indicates the fingerprint is in the compact form,
               // Otherwise _value._fingerprint is the array.

  // Remap BasicTypes that are handled equivalently by the adapters.
  // These are correct for the current system but someday it might be
  // necessary to make this mapping platform dependent.
  static BasicType adapter_encoding(BasicType in) {
    assert((~0xf & in) == 0, "must fit in 4 bits");
    switch(in) {
      case T_BOOLEAN:
      case T_BYTE:
      case T_SHORT:
      case T_CHAR:
        // There are all promoted to T_INT in the calling convention
        return T_INT;

      case T_OBJECT:
      case T_ARRAY:
        if (!TaggedStackInterpreter) {
#ifdef _LP64
          return T_LONG;
#else
          return T_INT;
#endif
        }
        return T_OBJECT;

      case T_INT:
      case T_LONG:
      case T_FLOAT:
      case T_DOUBLE:
      case T_VOID:
        return in;

      default:
        ShouldNotReachHere();
        return T_CONFLICT;
    }
  }

 public:
  AdapterFingerPrint(int total_args_passed, BasicType* sig_bt) {
    // The fingerprint is based on the BasicType signature encoded
    // into an array of ints with four entries per int.
    int* ptr;
    int len = (total_args_passed + 3) >> 2;
    if (len <= (int)(sizeof(_value._compact) / sizeof(int))) {
      _value._compact[0] = _value._compact[1] = _value._compact[2] = 0;
      // Storing the signature encoded as signed chars hits about 98%
      // of the time.
      _length = -len;
      ptr = _value._compact;
    } else {
      _length = len;
      _value._fingerprint = NEW_C_HEAP_ARRAY(int, _length);
      ptr = _value._fingerprint;
    }

    // Now pack the BasicTypes with 4 per int
    int sig_index = 0;
    for (int index = 0; index < len; index++) {
      int value = 0;
      for (int byte = 0; byte < 4; byte++) {
        if (sig_index < total_args_passed) {
          value = (value << 4) | adapter_encoding(sig_bt[sig_index++]);
        }
      }
      ptr[index] = value;
    }
  }

  ~AdapterFingerPrint() {
    if (_length > 0) {
      FREE_C_HEAP_ARRAY(int, _value._fingerprint);
    }
  }

  int value(int index) {
    if (_length < 0) {
      return _value._compact[index];
    }
    return _value._fingerprint[index];
  }
  int length() {
    if (_length < 0) return -_length;
    return _length;
  }

  bool is_compact() {
    return _length <= 0;
  }

  unsigned int compute_hash() {
    int hash = 0;
    for (int i = 0; i < length(); i++) {
      int v = value(i);
      hash = (hash << 8) ^ v ^ (hash >> 5);
    }
    return (unsigned int)hash;
  }

  const char* as_string() {
    stringStream st;
    for (int i = 0; i < length(); i++) {
      st.print(PTR_FORMAT, value(i));
    }
    return st.as_string();
  }

  bool equals(AdapterFingerPrint* other) {
    if (other->_length != _length) {
      return false;
    }
    if (_length < 0) {
      return _value._compact[0] == other->_value._compact[0] &&
             _value._compact[1] == other->_value._compact[1] &&
             _value._compact[2] == other->_value._compact[2];
    } else {
      for (int i = 0; i < _length; i++) {
        if (_value._fingerprint[i] != other->_value._fingerprint[i]) {
          return false;
        }
      }
    }
    return true;
  }
};


// A hashtable mapping from AdapterFingerPrints to AdapterHandlerEntries
class AdapterHandlerTable : public BasicHashtable {
  friend class AdapterHandlerTableIterator;

 private:

#ifndef PRODUCT
  static int _lookups; // number of calls to lookup
  static int _buckets; // number of buckets checked
  static int _equals;  // number of buckets checked with matching hash
  static int _hits;    // number of successful lookups
  static int _compact; // number of equals calls with compact signature
#endif

  AdapterHandlerEntry* bucket(int i) {
    return (AdapterHandlerEntry*)BasicHashtable::bucket(i);
  }

 public:
  AdapterHandlerTable()
    : BasicHashtable(293, sizeof(AdapterHandlerEntry)) { }

  // Create a new entry suitable for insertion in the table
  AdapterHandlerEntry* new_entry(AdapterFingerPrint* fingerprint, address i2c_entry, address c2i_entry, address c2i_unverified_entry) {
    AdapterHandlerEntry* entry = (AdapterHandlerEntry*)BasicHashtable::new_entry(fingerprint->compute_hash());
    entry->init(fingerprint, i2c_entry, c2i_entry, c2i_unverified_entry);
    return entry;
  }

  // Insert an entry into the table
  void add(AdapterHandlerEntry* entry) {
    int index = hash_to_index(entry->hash());
    add_entry(index, entry);
  }

  void free_entry(AdapterHandlerEntry* entry) {
    entry->deallocate();
    BasicHashtable::free_entry(entry);
  }

  // Find a entry with the same fingerprint if it exists
  AdapterHandlerEntry* lookup(int total_args_passed, BasicType* sig_bt) {
    NOT_PRODUCT(_lookups++);
    AdapterFingerPrint fp(total_args_passed, sig_bt);
    unsigned int hash = fp.compute_hash();
    int index = hash_to_index(hash);
    for (AdapterHandlerEntry* e = bucket(index); e != NULL; e = e->next()) {
      NOT_PRODUCT(_buckets++);
      if (e->hash() == hash) {
        NOT_PRODUCT(_equals++);
        if (fp.equals(e->fingerprint())) {
#ifndef PRODUCT
          if (fp.is_compact()) _compact++;
          _hits++;
#endif
          return e;
        }
      }
    }
    return NULL;
  }

#ifndef PRODUCT
  void print_statistics() {
    ResourceMark rm;
    int longest = 0;
    int empty = 0;
    int total = 0;
    int nonempty = 0;
    for (int index = 0; index < table_size(); index++) {
      int count = 0;
      for (AdapterHandlerEntry* e = bucket(index); e != NULL; e = e->next()) {
        count++;
      }
      if (count != 0) nonempty++;
      if (count == 0) empty++;
      if (count > longest) longest = count;
      total += count;
    }
    tty->print_cr("AdapterHandlerTable: empty %d longest %d total %d average %f",
                  empty, longest, total, total / (double)nonempty);
    tty->print_cr("AdapterHandlerTable: lookups %d buckets %d equals %d hits %d compact %d",
                  _lookups, _buckets, _equals, _hits, _compact);
  }
#endif
};


#ifndef PRODUCT

int AdapterHandlerTable::_lookups;
int AdapterHandlerTable::_buckets;
int AdapterHandlerTable::_equals;
int AdapterHandlerTable::_hits;
int AdapterHandlerTable::_compact;

class AdapterHandlerTableIterator : public StackObj {
 private:
  AdapterHandlerTable* _table;
  int _index;
  AdapterHandlerEntry* _current;

  void scan() {
    while (_index < _table->table_size()) {
      AdapterHandlerEntry* a = _table->bucket(_index);
      if (a != NULL) {
        _current = a;
        return;
      }
      _index++;
    }
  }

 public:
  AdapterHandlerTableIterator(AdapterHandlerTable* table): _table(table), _index(0), _current(NULL) {
    scan();
  }
  bool has_next() {
    return _current != NULL;
  }
  AdapterHandlerEntry* next() {
    if (_current != NULL) {
      AdapterHandlerEntry* result = _current;
      _current = _current->next();
      if (_current == NULL) scan();
      return result;
    } else {
      return NULL;
    }
  }
};
#endif


// ---------------------------------------------------------------------------
// Implementation of AdapterHandlerLibrary
AdapterHandlerTable* AdapterHandlerLibrary::_adapters = NULL;
AdapterHandlerEntry* AdapterHandlerLibrary::_abstract_method_handler = NULL;
const int AdapterHandlerLibrary_size = 16*K;
BufferBlob* AdapterHandlerLibrary::_buffer = NULL;

BufferBlob* AdapterHandlerLibrary::buffer_blob() {
  // Should be called only when AdapterHandlerLibrary_lock is active.
  if (_buffer == NULL) // Initialize lazily
      _buffer = BufferBlob::create("adapters", AdapterHandlerLibrary_size);
  return _buffer;
}

void AdapterHandlerLibrary::initialize() {
  if (_adapters != NULL) return;
  _adapters = new AdapterHandlerTable();

  // Create a special handler for abstract methods.  Abstract methods
  // are never compiled so an i2c entry is somewhat meaningless, but
  // fill it in with something appropriate just in case.  Pass handle
  // wrong method for the c2i transitions.
  address wrong_method = SharedRuntime::get_handle_wrong_method_stub();
  _abstract_method_handler = AdapterHandlerLibrary::new_entry(new AdapterFingerPrint(0, NULL),
                                                              StubRoutines::throw_AbstractMethodError_entry(),
                                                              wrong_method, wrong_method);
}

AdapterHandlerEntry* AdapterHandlerLibrary::new_entry(AdapterFingerPrint* fingerprint,
                                                      address i2c_entry,
                                                      address c2i_entry,
                                                      address c2i_unverified_entry) {
  return _adapters->new_entry(fingerprint, i2c_entry, c2i_entry, c2i_unverified_entry);
}

AdapterHandlerEntry* AdapterHandlerLibrary::get_adapter(methodHandle method) {
  // Use customized signature handler.  Need to lock around updates to
  // the AdapterHandlerTable (it is not safe for concurrent readers
  // and a single writer: this could be fixed if it becomes a
  // problem).

  // Get the address of the ic_miss handlers before we grab the
  // AdapterHandlerLibrary_lock. This fixes bug 6236259 which
  // was caused by the initialization of the stubs happening
  // while we held the lock and then notifying jvmti while
  // holding it. This just forces the initialization to be a little
  // earlier.
  address ic_miss = SharedRuntime::get_ic_miss_stub();
  assert(ic_miss != NULL, "must have handler");

  ResourceMark rm;

  NOT_PRODUCT(int code_size);
  AdapterBlob* B = NULL;
  AdapterHandlerEntry* entry = NULL;
  AdapterFingerPrint* fingerprint = NULL;
  {
    MutexLocker mu(AdapterHandlerLibrary_lock);
    // make sure data structure is initialized
    initialize();

    if (method->is_abstract()) {
      return _abstract_method_handler;
    }

    // Fill in the signature array, for the calling-convention call.
    int total_args_passed = method->size_of_parameters(); // All args on stack

    BasicType* sig_bt = NEW_RESOURCE_ARRAY(BasicType, total_args_passed);
    VMRegPair* regs   = NEW_RESOURCE_ARRAY(VMRegPair, total_args_passed);
    int i = 0;
    if (!method->is_static())  // Pass in receiver first
      sig_bt[i++] = T_OBJECT;
    for (SignatureStream ss(method->signature()); !ss.at_return_type(); ss.next()) {
      sig_bt[i++] = ss.type();  // Collect remaining bits of signature
      if (ss.type() == T_LONG || ss.type() == T_DOUBLE)
        sig_bt[i++] = T_VOID;   // Longs & doubles take 2 Java slots
    }
    assert(i == total_args_passed, "");

    // Lookup method signature's fingerprint
    entry = _adapters->lookup(total_args_passed, sig_bt);

#ifdef ASSERT
    AdapterHandlerEntry* shared_entry = NULL;
    if (VerifyAdapterSharing && entry != NULL) {
      shared_entry = entry;
      entry = NULL;
    }
#endif

    if (entry != NULL) {
      return entry;
    }

    // Get a description of the compiled java calling convention and the largest used (VMReg) stack slot usage
    int comp_args_on_stack = SharedRuntime::java_calling_convention(sig_bt, regs, total_args_passed, false);

    // Make a C heap allocated version of the fingerprint to store in the adapter
    fingerprint = new AdapterFingerPrint(total_args_passed, sig_bt);

    // Create I2C & C2I handlers

    BufferBlob* buf = buffer_blob(); // the temporary code buffer in CodeCache
    if (buf != NULL) {
      CodeBuffer buffer(buf->instructions_begin(), buf->instructions_size());
      short buffer_locs[20];
      buffer.insts()->initialize_shared_locs((relocInfo*)buffer_locs,
                                             sizeof(buffer_locs)/sizeof(relocInfo));
      MacroAssembler _masm(&buffer);

      entry = SharedRuntime::generate_i2c2i_adapters(&_masm,
                                                     total_args_passed,
                                                     comp_args_on_stack,
                                                     sig_bt,
                                                     regs,
                                                     fingerprint);

#ifdef ASSERT
      if (VerifyAdapterSharing) {
        if (shared_entry != NULL) {
          assert(shared_entry->compare_code(buf->instructions_begin(), buffer.code_size(), total_args_passed, sig_bt),
                 "code must match");
          // Release the one just created and return the original
          _adapters->free_entry(entry);
          return shared_entry;
        } else  {
          entry->save_code(buf->instructions_begin(), buffer.code_size(), total_args_passed, sig_bt);
        }
      }
#endif

      B = AdapterBlob::create(&buffer);
      NOT_PRODUCT(code_size = buffer.code_size());
    }
    if (B == NULL) {
      // CodeCache is full, disable compilation
      // Ought to log this but compile log is only per compile thread
      // and we're some non descript Java thread.
      MutexUnlocker mu(AdapterHandlerLibrary_lock);
      CompileBroker::handle_full_code_cache();
      return NULL; // Out of CodeCache space
    }
    entry->relocate(B->instructions_begin());
#ifndef PRODUCT
    // debugging suppport
    if (PrintAdapterHandlers) {
      tty->cr();
      tty->print_cr("i2c argument handler #%d for: %s %s (fingerprint = %s, %d bytes generated)",
                    _adapters->number_of_entries(), (method->is_static() ? "static" : "receiver"),
                    method->signature()->as_C_string(), fingerprint->as_string(), code_size );
      tty->print_cr("c2i argument handler starts at %p",entry->get_c2i_entry());
      Disassembler::decode(entry->get_i2c_entry(), entry->get_i2c_entry() + code_size);
    }
#endif

    _adapters->add(entry);
  }
  // Outside of the lock
  if (B != NULL) {
    char blob_id[256];
    jio_snprintf(blob_id,
                 sizeof(blob_id),
                 "%s(%s)@" PTR_FORMAT,
                 B->name(),
                 fingerprint->as_string(),
                 B->instructions_begin());
    VTune::register_stub(blob_id, B->instructions_begin(), B->instructions_end());
    Forte::register_stub(blob_id, B->instructions_begin(), B->instructions_end());

    if (JvmtiExport::should_post_dynamic_code_generated()) {
      JvmtiExport::post_dynamic_code_generated(blob_id,
                                               B->instructions_begin(),
                                               B->instructions_end());
    }
  }
  return entry;
}

void AdapterHandlerEntry::relocate(address new_base) {
    ptrdiff_t delta = new_base - _i2c_entry;
    _i2c_entry += delta;
    _c2i_entry += delta;
    _c2i_unverified_entry += delta;
}


void AdapterHandlerEntry::deallocate() {
  delete _fingerprint;
#ifdef ASSERT
  if (_saved_code) FREE_C_HEAP_ARRAY(unsigned char, _saved_code);
  if (_saved_sig)  FREE_C_HEAP_ARRAY(Basictype, _saved_sig);
#endif
}


#ifdef ASSERT
// Capture the code before relocation so that it can be compared
// against other versions.  If the code is captured after relocation
// then relative instructions won't be equivalent.
void AdapterHandlerEntry::save_code(unsigned char* buffer, int length, int total_args_passed, BasicType* sig_bt) {
  _saved_code = NEW_C_HEAP_ARRAY(unsigned char, length);
  _code_length = length;
  memcpy(_saved_code, buffer, length);
  _total_args_passed = total_args_passed;
  _saved_sig = NEW_C_HEAP_ARRAY(BasicType, _total_args_passed);
  memcpy(_saved_sig, sig_bt, _total_args_passed * sizeof(BasicType));
}


bool AdapterHandlerEntry::compare_code(unsigned char* buffer, int length, int total_args_passed, BasicType* sig_bt) {
  if (length != _code_length) {
    return false;
  }
  for (int i = 0; i < length; i++) {
    if (buffer[i] != _saved_code[i]) {
      return false;
    }
  }
  return true;
}
#endif


// Create a native wrapper for this native method.  The wrapper converts the
// java compiled calling convention to the native convention, handlizes
// arguments, and transitions to native.  On return from the native we transition
// back to java blocking if a safepoint is in progress.
nmethod *AdapterHandlerLibrary::create_native_wrapper(methodHandle method) {
  ResourceMark rm;
  nmethod* nm = NULL;

  if (PrintCompilation) {
    ttyLocker ttyl;
    tty->print("---   n%s ", (method->is_synchronized() ? "s" : " "));
    method->print_short_name(tty);
    if (method->is_static()) {
      tty->print(" (static)");
    }
    tty->cr();
  }

  assert(method->has_native_function(), "must have something valid to call!");

  {
    // perform the work while holding the lock, but perform any printing outside the lock
    MutexLocker mu(AdapterHandlerLibrary_lock);
    // See if somebody beat us to it
    nm = method->code();
    if (nm) {
      return nm;
    }

    ResourceMark rm;

    BufferBlob*  buf = buffer_blob(); // the temporary code buffer in CodeCache
    if (buf != NULL) {
      CodeBuffer buffer(buf->instructions_begin(), buf->instructions_size());
      double locs_buf[20];
      buffer.insts()->initialize_shared_locs((relocInfo*)locs_buf, sizeof(locs_buf) / sizeof(relocInfo));
      MacroAssembler _masm(&buffer);

      // Fill in the signature array, for the calling-convention call.
      int total_args_passed = method->size_of_parameters();

      BasicType* sig_bt = NEW_RESOURCE_ARRAY(BasicType,total_args_passed);
      VMRegPair*   regs = NEW_RESOURCE_ARRAY(VMRegPair,total_args_passed);
      int i=0;
      if( !method->is_static() )  // Pass in receiver first
        sig_bt[i++] = T_OBJECT;
      SignatureStream ss(method->signature());
      for( ; !ss.at_return_type(); ss.next()) {
        sig_bt[i++] = ss.type();  // Collect remaining bits of signature
        if( ss.type() == T_LONG || ss.type() == T_DOUBLE )
          sig_bt[i++] = T_VOID;   // Longs & doubles take 2 Java slots
      }
      assert( i==total_args_passed, "" );
      BasicType ret_type = ss.type();

      // Now get the compiled-Java layout as input arguments
      int comp_args_on_stack;
      comp_args_on_stack = SharedRuntime::java_calling_convention(sig_bt, regs, total_args_passed, false);

      // Generate the compiled-to-native wrapper code
      nm = SharedRuntime::generate_native_wrapper(&_masm,
                                                  method,
                                                  total_args_passed,
                                                  comp_args_on_stack,
                                                  sig_bt,regs,
                                                  ret_type);
    }
  }

  // Must unlock before calling set_code
  // Install the generated code.
  if (nm != NULL) {
    method->set_code(method, nm);
    nm->post_compiled_method_load_event();
  } else {
    // CodeCache is full, disable compilation
    // Ought to log this but compile log is only per compile thread
    // and we're some non descript Java thread.
    MutexUnlocker mu(AdapterHandlerLibrary_lock);
    CompileBroker::handle_full_code_cache();
  }
  return nm;
}

#ifdef HAVE_DTRACE_H
// Create a dtrace nmethod for this method.  The wrapper converts the
// java compiled calling convention to the native convention, makes a dummy call
// (actually nops for the size of the call instruction, which become a trap if
// probe is enabled). The returns to the caller. Since this all looks like a
// leaf no thread transition is needed.

nmethod *AdapterHandlerLibrary::create_dtrace_nmethod(methodHandle method) {
  ResourceMark rm;
  nmethod* nm = NULL;

  if (PrintCompilation) {
    ttyLocker ttyl;
    tty->print("---   n%s  ");
    method->print_short_name(tty);
    if (method->is_static()) {
      tty->print(" (static)");
    }
    tty->cr();
  }

  {
    // perform the work while holding the lock, but perform any printing
    // outside the lock
    MutexLocker mu(AdapterHandlerLibrary_lock);
    // See if somebody beat us to it
    nm = method->code();
    if (nm) {
      return nm;
    }

    ResourceMark rm;

    BufferBlob*  buf = buffer_blob(); // the temporary code buffer in CodeCache
    if (buf != NULL) {
      CodeBuffer buffer(buf->instructions_begin(), buf->instructions_size());
      // Need a few relocation entries
      double locs_buf[20];
      buffer.insts()->initialize_shared_locs(
        (relocInfo*)locs_buf, sizeof(locs_buf) / sizeof(relocInfo));
      MacroAssembler _masm(&buffer);

      // Generate the compiled-to-native wrapper code
      nm = SharedRuntime::generate_dtrace_nmethod(&_masm, method);
    }
  }
  return nm;
}

// the dtrace method needs to convert java lang string to utf8 string.
void SharedRuntime::get_utf(oopDesc* src, address dst) {
  typeArrayOop jlsValue  = java_lang_String::value(src);
  int          jlsOffset = java_lang_String::offset(src);
  int          jlsLen    = java_lang_String::length(src);
  jchar*       jlsPos    = (jlsLen == 0) ? NULL :
                                           jlsValue->char_at_addr(jlsOffset);
  (void) UNICODE::as_utf8(jlsPos, jlsLen, (char *)dst, max_dtrace_string_size);
}
#endif // ndef HAVE_DTRACE_H

// -------------------------------------------------------------------------
// Java-Java calling convention
// (what you use when Java calls Java)

//------------------------------name_for_receiver----------------------------------
// For a given signature, return the VMReg for parameter 0.
VMReg SharedRuntime::name_for_receiver() {
  VMRegPair regs;
  BasicType sig_bt = T_OBJECT;
  (void) java_calling_convention(&sig_bt, &regs, 1, true);
  // Return argument 0 register.  In the LP64 build pointers
  // take 2 registers, but the VM wants only the 'main' name.
  return regs.first();
}

VMRegPair *SharedRuntime::find_callee_arguments(symbolOop sig, bool has_receiver, int* arg_size) {
  // This method is returning a data structure allocating as a
  // ResourceObject, so do not put any ResourceMarks in here.
  char *s = sig->as_C_string();
  int len = (int)strlen(s);
  *s++; len--;                  // Skip opening paren
  char *t = s+len;
  while( *(--t) != ')' ) ;      // Find close paren

  BasicType *sig_bt = NEW_RESOURCE_ARRAY( BasicType, 256 );
  VMRegPair *regs = NEW_RESOURCE_ARRAY( VMRegPair, 256 );
  int cnt = 0;
  if (has_receiver) {
    sig_bt[cnt++] = T_OBJECT; // Receiver is argument 0; not in signature
  }

  while( s < t ) {
    switch( *s++ ) {            // Switch on signature character
    case 'B': sig_bt[cnt++] = T_BYTE;    break;
    case 'C': sig_bt[cnt++] = T_CHAR;    break;
    case 'D': sig_bt[cnt++] = T_DOUBLE;  sig_bt[cnt++] = T_VOID; break;
    case 'F': sig_bt[cnt++] = T_FLOAT;   break;
    case 'I': sig_bt[cnt++] = T_INT;     break;
    case 'J': sig_bt[cnt++] = T_LONG;    sig_bt[cnt++] = T_VOID; break;
    case 'S': sig_bt[cnt++] = T_SHORT;   break;
    case 'Z': sig_bt[cnt++] = T_BOOLEAN; break;
    case 'V': sig_bt[cnt++] = T_VOID;    break;
    case 'L':                   // Oop
      while( *s++ != ';'  ) ;   // Skip signature
      sig_bt[cnt++] = T_OBJECT;
      break;
    case '[': {                 // Array
      do {                      // Skip optional size
        while( *s >= '0' && *s <= '9' ) s++;
      } while( *s++ == '[' );   // Nested arrays?
      // Skip element type
      if( s[-1] == 'L' )
        while( *s++ != ';'  ) ; // Skip signature
      sig_bt[cnt++] = T_ARRAY;
      break;
    }
    default : ShouldNotReachHere();
    }
  }
  assert( cnt < 256, "grow table size" );

  int comp_args_on_stack;
  comp_args_on_stack = java_calling_convention(sig_bt, regs, cnt, true);

  // the calling convention doesn't count out_preserve_stack_slots so
  // we must add that in to get "true" stack offsets.

  if (comp_args_on_stack) {
    for (int i = 0; i < cnt; i++) {
      VMReg reg1 = regs[i].first();
      if( reg1->is_stack()) {
        // Yuck
        reg1 = reg1->bias(out_preserve_stack_slots());
      }
      VMReg reg2 = regs[i].second();
      if( reg2->is_stack()) {
        // Yuck
        reg2 = reg2->bias(out_preserve_stack_slots());
      }
      regs[i].set_pair(reg2, reg1);
    }
  }

  // results
  *arg_size = cnt;
  return regs;
}

// OSR Migration Code
//
// This code is used convert interpreter frames into compiled frames.  It is
// called from very start of a compiled OSR nmethod.  A temp array is
// allocated to hold the interesting bits of the interpreter frame.  All
// active locks are inflated to allow them to move.  The displaced headers and
// active interpeter locals are copied into the temp buffer.  Then we return
// back to the compiled code.  The compiled code then pops the current
// interpreter frame off the stack and pushes a new compiled frame.  Then it
// copies the interpreter locals and displaced headers where it wants.
// Finally it calls back to free the temp buffer.
//
// All of this is done NOT at any Safepoint, nor is any safepoint or GC allowed.

JRT_LEAF(intptr_t*, SharedRuntime::OSR_migration_begin( JavaThread *thread) )

#ifdef IA64
  ShouldNotReachHere(); // NYI
#endif /* IA64 */

  //
  // This code is dependent on the memory layout of the interpreter local
  // array and the monitors. On all of our platforms the layout is identical
  // so this code is shared. If some platform lays the their arrays out
  // differently then this code could move to platform specific code or
  // the code here could be modified to copy items one at a time using
  // frame accessor methods and be platform independent.

  frame fr = thread->last_frame();
  assert( fr.is_interpreted_frame(), "" );
  assert( fr.interpreter_frame_expression_stack_size()==0, "only handle empty stacks" );

  // Figure out how many monitors are active.
  int active_monitor_count = 0;
  for( BasicObjectLock *kptr = fr.interpreter_frame_monitor_end();
       kptr < fr.interpreter_frame_monitor_begin();
       kptr = fr.next_monitor_in_interpreter_frame(kptr) ) {
    if( kptr->obj() != NULL ) active_monitor_count++;
  }

  // QQQ we could place number of active monitors in the array so that compiled code
  // could double check it.

  methodOop moop = fr.interpreter_frame_method();
  int max_locals = moop->max_locals();
  // Allocate temp buffer, 1 word per local & 2 per active monitor
  int buf_size_words = max_locals + active_monitor_count*2;
  intptr_t *buf = NEW_C_HEAP_ARRAY(intptr_t,buf_size_words);

  // Copy the locals.  Order is preserved so that loading of longs works.
  // Since there's no GC I can copy the oops blindly.
  assert( sizeof(HeapWord)==sizeof(intptr_t), "fix this code");
  if (TaggedStackInterpreter) {
    for (int i = 0; i < max_locals; i++) {
      // copy only each local separately to the buffer avoiding the tag
      buf[i] = *fr.interpreter_frame_local_at(max_locals-i-1);
    }
  } else {
    Copy::disjoint_words(
                       (HeapWord*)fr.interpreter_frame_local_at(max_locals-1),
                       (HeapWord*)&buf[0],
                       max_locals);
  }

  // Inflate locks.  Copy the displaced headers.  Be careful, there can be holes.
  int i = max_locals;
  for( BasicObjectLock *kptr2 = fr.interpreter_frame_monitor_end();
       kptr2 < fr.interpreter_frame_monitor_begin();
       kptr2 = fr.next_monitor_in_interpreter_frame(kptr2) ) {
    if( kptr2->obj() != NULL) {         // Avoid 'holes' in the monitor array
      BasicLock *lock = kptr2->lock();
      // Inflate so the displaced header becomes position-independent
      if (lock->displaced_header()->is_unlocked())
        ObjectSynchronizer::inflate_helper(kptr2->obj());
      // Now the displaced header is free to move
      buf[i++] = (intptr_t)lock->displaced_header();
      buf[i++] = (intptr_t)kptr2->obj();
    }
  }
  assert( i - max_locals == active_monitor_count*2, "found the expected number of monitors" );

  return buf;
JRT_END

JRT_LEAF(void, SharedRuntime::OSR_migration_end( intptr_t* buf) )
  FREE_C_HEAP_ARRAY(intptr_t,buf);
JRT_END

#ifndef PRODUCT
bool AdapterHandlerLibrary::contains(CodeBlob* b) {
  AdapterHandlerTableIterator iter(_adapters);
  while (iter.has_next()) {
    AdapterHandlerEntry* a = iter.next();
    if ( b == CodeCache::find_blob(a->get_i2c_entry()) ) return true;
  }
  return false;
}

void AdapterHandlerLibrary::print_handler(CodeBlob* b) {
  AdapterHandlerTableIterator iter(_adapters);
  while (iter.has_next()) {
    AdapterHandlerEntry* a = iter.next();
    if ( b == CodeCache::find_blob(a->get_i2c_entry()) ) {
      tty->print("Adapter for signature: ");
      tty->print_cr("%s i2c: " INTPTR_FORMAT " c2i: " INTPTR_FORMAT " c2iUV: " INTPTR_FORMAT,
                    a->fingerprint()->as_string(),
                    a->get_i2c_entry(), a->get_c2i_entry(), a->get_c2i_unverified_entry());
      return;
    }
  }
  assert(false, "Should have found handler");
}

void AdapterHandlerLibrary::print_statistics() {
  _adapters->print_statistics();
}

#endif /* PRODUCT */
