/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/metadataOnStackMark.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/debugInfoRec.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/bytecodeTracer.hpp"
#include "interpreter/bytecodes.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/oopMapCache.hpp"
#include "memory/gcLocker.hpp"
#include "memory/generation.hpp"
#include "memory/heapInspection.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/oopFactory.hpp"
#include "oops/constMethod.hpp"
#include "oops/methodData.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/methodHandles.hpp"
#include "prims/nativeLookup.hpp"
#include "runtime/arguments.hpp"
#include "runtime/compilationPolicy.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/relocator.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "utilities/quickSort.hpp"
#include "utilities/xmlstream.hpp"


// Implementation of Method

Method* Method::allocate(ClassLoaderData* loader_data,
                         int byte_code_size,
                         AccessFlags access_flags,
                         InlineTableSizes* sizes,
                         ConstMethod::MethodType method_type,
                         TRAPS) {
  assert(!access_flags.is_native() || byte_code_size == 0,
         "native methods should not contain byte codes");
  ConstMethod* cm = ConstMethod::allocate(loader_data,
                                          byte_code_size,
                                          sizes,
                                          method_type,
                                          CHECK_NULL);

  int size = Method::size(access_flags.is_native());

  return new (loader_data, size, false, MetaspaceObj::MethodType, THREAD) Method(cm, access_flags, size);
}

Method::Method(ConstMethod* xconst, AccessFlags access_flags, int size) {
  No_Safepoint_Verifier no_safepoint;
  set_constMethod(xconst);
  set_access_flags(access_flags);
  set_method_size(size);
#ifdef CC_INTERP
  set_result_index(T_VOID);
#endif
  set_intrinsic_id(vmIntrinsics::_none);
  set_jfr_towrite(false);
  set_force_inline(false);
  set_hidden(false);
  set_dont_inline(false);
  set_method_data(NULL);
  set_method_counters(NULL);
  set_vtable_index(Method::garbage_vtable_index);

  // Fix and bury in Method*
  set_interpreter_entry(NULL); // sets i2i entry and from_int
  set_adapter_entry(NULL);
  clear_code(); // from_c/from_i get set to c2i/i2i

  if (access_flags.is_native()) {
    clear_native_function();
    set_signature_handler(NULL);
  }

  NOT_PRODUCT(set_compiled_invocation_count(0);)
}

// Release Method*.  The nmethod will be gone when we get here because
// we've walked the code cache.
void Method::deallocate_contents(ClassLoaderData* loader_data) {
  MetadataFactory::free_metadata(loader_data, constMethod());
  set_constMethod(NULL);
  MetadataFactory::free_metadata(loader_data, method_data());
  set_method_data(NULL);
  MetadataFactory::free_metadata(loader_data, method_counters());
  set_method_counters(NULL);
  // The nmethod will be gone when we get here.
  if (code() != NULL) _code = NULL;
}

address Method::get_i2c_entry() {
  assert(_adapter != NULL, "must have");
  return _adapter->get_i2c_entry();
}

address Method::get_c2i_entry() {
  assert(_adapter != NULL, "must have");
  return _adapter->get_c2i_entry();
}

address Method::get_c2i_unverified_entry() {
  assert(_adapter != NULL, "must have");
  return _adapter->get_c2i_unverified_entry();
}

char* Method::name_and_sig_as_C_string() const {
  return name_and_sig_as_C_string(constants()->pool_holder(), name(), signature());
}

char* Method::name_and_sig_as_C_string(char* buf, int size) const {
  return name_and_sig_as_C_string(constants()->pool_holder(), name(), signature(), buf, size);
}

char* Method::name_and_sig_as_C_string(Klass* klass, Symbol* method_name, Symbol* signature) {
  const char* klass_name = klass->external_name();
  int klass_name_len  = (int)strlen(klass_name);
  int method_name_len = method_name->utf8_length();
  int len             = klass_name_len + 1 + method_name_len + signature->utf8_length();
  char* dest          = NEW_RESOURCE_ARRAY(char, len + 1);
  strcpy(dest, klass_name);
  dest[klass_name_len] = '.';
  strcpy(&dest[klass_name_len + 1], method_name->as_C_string());
  strcpy(&dest[klass_name_len + 1 + method_name_len], signature->as_C_string());
  dest[len] = 0;
  return dest;
}

char* Method::name_and_sig_as_C_string(Klass* klass, Symbol* method_name, Symbol* signature, char* buf, int size) {
  Symbol* klass_name = klass->name();
  klass_name->as_klass_external_name(buf, size);
  int len = (int)strlen(buf);

  if (len < size - 1) {
    buf[len++] = '.';

    method_name->as_C_string(&(buf[len]), size - len);
    len = (int)strlen(buf);

    signature->as_C_string(&(buf[len]), size - len);
  }

  return buf;
}

int Method::fast_exception_handler_bci_for(methodHandle mh, KlassHandle ex_klass, int throw_bci, TRAPS) {
  // exception table holds quadruple entries of the form (beg_bci, end_bci, handler_bci, klass_index)
  // access exception table
  ExceptionTable table(mh());
  int length = table.length();
  // iterate through all entries sequentially
  constantPoolHandle pool(THREAD, mh->constants());
  for (int i = 0; i < length; i ++) {
    //reacquire the table in case a GC happened
    ExceptionTable table(mh());
    int beg_bci = table.start_pc(i);
    int end_bci = table.end_pc(i);
    assert(beg_bci <= end_bci, "inconsistent exception table");
    if (beg_bci <= throw_bci && throw_bci < end_bci) {
      // exception handler bci range covers throw_bci => investigate further
      int handler_bci = table.handler_pc(i);
      int klass_index = table.catch_type_index(i);
      if (klass_index == 0) {
        return handler_bci;
      } else if (ex_klass.is_null()) {
        return handler_bci;
      } else {
        // we know the exception class => get the constraint class
        // this may require loading of the constraint class; if verification
        // fails or some other exception occurs, return handler_bci
        Klass* k = pool->klass_at(klass_index, CHECK_(handler_bci));
        KlassHandle klass = KlassHandle(THREAD, k);
        assert(klass.not_null(), "klass not loaded");
        if (ex_klass->is_subtype_of(klass())) {
          return handler_bci;
        }
      }
    }
  }

  return -1;
}

void Method::mask_for(int bci, InterpreterOopMap* mask) {

  Thread* myThread    = Thread::current();
  methodHandle h_this(myThread, this);
#ifdef ASSERT
  bool has_capability = myThread->is_VM_thread() ||
                        myThread->is_ConcurrentGC_thread() ||
                        myThread->is_GC_task_thread();

  if (!has_capability) {
    if (!VerifyStack && !VerifyLastFrame) {
      // verify stack calls this outside VM thread
      warning("oopmap should only be accessed by the "
              "VM, GC task or CMS threads (or during debugging)");
      InterpreterOopMap local_mask;
      method_holder()->mask_for(h_this, bci, &local_mask);
      local_mask.print();
    }
  }
#endif
  method_holder()->mask_for(h_this, bci, mask);
  return;
}


int Method::bci_from(address bcp) const {
#ifdef ASSERT
  { ResourceMark rm;
  assert(is_native() && bcp == code_base() || contains(bcp) || is_error_reported(),
         err_msg("bcp doesn't belong to this method: bcp: " INTPTR_FORMAT ", method: %s", bcp, name_and_sig_as_C_string()));
  }
#endif
  return bcp - code_base();
}


// Return (int)bcx if it appears to be a valid BCI.
// Return bci_from((address)bcx) if it appears to be a valid BCP.
// Return -1 otherwise.
// Used by profiling code, when invalid data is a possibility.
// The caller is responsible for validating the Method* itself.
int Method::validate_bci_from_bcx(intptr_t bcx) const {
  // keep bci as -1 if not a valid bci
  int bci = -1;
  if (bcx == 0 || (address)bcx == code_base()) {
    // code_size() may return 0 and we allow 0 here
    // the method may be native
    bci = 0;
  } else if (frame::is_bci(bcx)) {
    if (bcx < code_size()) {
      bci = (int)bcx;
    }
  } else if (contains((address)bcx)) {
    bci = (address)bcx - code_base();
  }
  // Assert that if we have dodged any asserts, bci is negative.
  assert(bci == -1 || bci == bci_from(bcp_from(bci)), "sane bci if >=0");
  return bci;
}

address Method::bcp_from(int bci) const {
  assert((is_native() && bci == 0)  || (!is_native() && 0 <= bci && bci < code_size()), err_msg("illegal bci: %d", bci));
  address bcp = code_base() + bci;
  assert(is_native() && bcp == code_base() || contains(bcp), "bcp doesn't belong to this method");
  return bcp;
}


int Method::size(bool is_native) {
  // If native, then include pointers for native_function and signature_handler
  int extra_bytes = (is_native) ? 2*sizeof(address*) : 0;
  int extra_words = align_size_up(extra_bytes, BytesPerWord) / BytesPerWord;
  return align_object_size(header_size() + extra_words);
}


Symbol* Method::klass_name() const {
  Klass* k = method_holder();
  assert(k->is_klass(), "must be klass");
  InstanceKlass* ik = (InstanceKlass*) k;
  return ik->name();
}


// Attempt to return method oop to original state.  Clear any pointers
// (to objects outside the shared spaces).  We won't be able to predict
// where they should point in a new JVM.  Further initialize some
// entries now in order allow them to be write protected later.

void Method::remove_unshareable_info() {
  unlink_method();
}


bool Method::was_executed_more_than(int n) {
  // Invocation counter is reset when the Method* is compiled.
  // If the method has compiled code we therefore assume it has
  // be excuted more than n times.
  if (is_accessor() || is_empty_method() || (code() != NULL)) {
    // interpreter doesn't bump invocation counter of trivial methods
    // compiler does not bump invocation counter of compiled methods
    return true;
  }
  else if ((method_counters() != NULL &&
            method_counters()->invocation_counter()->carry()) ||
           (method_data() != NULL &&
            method_data()->invocation_counter()->carry())) {
    // The carry bit is set when the counter overflows and causes
    // a compilation to occur.  We don't know how many times
    // the counter has been reset, so we simply assume it has
    // been executed more than n times.
    return true;
  } else {
    return invocation_count() > n;
  }
}

#ifndef PRODUCT
void Method::print_invocation_count() {
  if (is_static()) tty->print("static ");
  if (is_final()) tty->print("final ");
  if (is_synchronized()) tty->print("synchronized ");
  if (is_native()) tty->print("native ");
  method_holder()->name()->print_symbol_on(tty);
  tty->print(".");
  name()->print_symbol_on(tty);
  signature()->print_symbol_on(tty);

  if (WizardMode) {
    // dump the size of the byte codes
    tty->print(" {%d}", code_size());
  }
  tty->cr();

  tty->print_cr ("  interpreter_invocation_count: %8d ", interpreter_invocation_count());
  tty->print_cr ("  invocation_counter:           %8d ", invocation_count());
  tty->print_cr ("  backedge_counter:             %8d ", backedge_count());
  if (CountCompiledCalls) {
    tty->print_cr ("  compiled_invocation_count: %8d ", compiled_invocation_count());
  }

}
#endif

// Build a MethodData* object to hold information about this method
// collected in the interpreter.
void Method::build_interpreter_method_data(methodHandle method, TRAPS) {
  // Do not profile method if current thread holds the pending list lock,
  // which avoids deadlock for acquiring the MethodData_lock.
  if (InstanceRefKlass::owns_pending_list_lock((JavaThread*)THREAD)) {
    return;
  }

  // Grab a lock here to prevent multiple
  // MethodData*s from being created.
  MutexLocker ml(MethodData_lock, THREAD);
  if (method->method_data() == NULL) {
    ClassLoaderData* loader_data = method->method_holder()->class_loader_data();
    MethodData* method_data = MethodData::allocate(loader_data, method, CHECK);
    method->set_method_data(method_data);
    if (PrintMethodData && (Verbose || WizardMode)) {
      ResourceMark rm(THREAD);
      tty->print("build_interpreter_method_data for ");
      method->print_name(tty);
      tty->cr();
      // At the end of the run, the MDO, full of data, will be dumped.
    }
  }
}

MethodCounters* Method::build_method_counters(Method* m, TRAPS) {
  methodHandle mh(m);
  ClassLoaderData* loader_data = mh->method_holder()->class_loader_data();
  MethodCounters* counters = MethodCounters::allocate(loader_data, CHECK_NULL);
  if (mh->method_counters() == NULL) {
    mh->set_method_counters(counters);
  } else {
    MetadataFactory::free_metadata(loader_data, counters);
  }
  return mh->method_counters();
}

void Method::cleanup_inline_caches() {
  // The current system doesn't use inline caches in the interpreter
  // => nothing to do (keep this method around for future use)
}


int Method::extra_stack_words() {
  // not an inline function, to avoid a header dependency on Interpreter
  return extra_stack_entries() * Interpreter::stackElementSize;
}


void Method::compute_size_of_parameters(Thread *thread) {
  ArgumentSizeComputer asc(signature());
  set_size_of_parameters(asc.size() + (is_static() ? 0 : 1));
}

#ifdef CC_INTERP
void Method::set_result_index(BasicType type)          {
  _result_index = Interpreter::BasicType_as_index(type);
}
#endif

BasicType Method::result_type() const {
  ResultTypeFinder rtf(signature());
  return rtf.type();
}


bool Method::is_empty_method() const {
  return  code_size() == 1
      && *code_base() == Bytecodes::_return;
}


bool Method::is_vanilla_constructor() const {
  // Returns true if this method is a vanilla constructor, i.e. an "<init>" "()V" method
  // which only calls the superclass vanilla constructor and possibly does stores of
  // zero constants to local fields:
  //
  //   aload_0
  //   invokespecial
  //   indexbyte1
  //   indexbyte2
  //
  // followed by an (optional) sequence of:
  //
  //   aload_0
  //   aconst_null / iconst_0 / fconst_0 / dconst_0
  //   putfield
  //   indexbyte1
  //   indexbyte2
  //
  // followed by:
  //
  //   return

  assert(name() == vmSymbols::object_initializer_name(),    "Should only be called for default constructors");
  assert(signature() == vmSymbols::void_method_signature(), "Should only be called for default constructors");
  int size = code_size();
  // Check if size match
  if (size == 0 || size % 5 != 0) return false;
  address cb = code_base();
  int last = size - 1;
  if (cb[0] != Bytecodes::_aload_0 || cb[1] != Bytecodes::_invokespecial || cb[last] != Bytecodes::_return) {
    // Does not call superclass default constructor
    return false;
  }
  // Check optional sequence
  for (int i = 4; i < last; i += 5) {
    if (cb[i] != Bytecodes::_aload_0) return false;
    if (!Bytecodes::is_zero_const(Bytecodes::cast(cb[i+1]))) return false;
    if (cb[i+2] != Bytecodes::_putfield) return false;
  }
  return true;
}


bool Method::compute_has_loops_flag() {
  BytecodeStream bcs(this);
  Bytecodes::Code bc;

  while ((bc = bcs.next()) >= 0) {
    switch( bc ) {
      case Bytecodes::_ifeq:
      case Bytecodes::_ifnull:
      case Bytecodes::_iflt:
      case Bytecodes::_ifle:
      case Bytecodes::_ifne:
      case Bytecodes::_ifnonnull:
      case Bytecodes::_ifgt:
      case Bytecodes::_ifge:
      case Bytecodes::_if_icmpeq:
      case Bytecodes::_if_icmpne:
      case Bytecodes::_if_icmplt:
      case Bytecodes::_if_icmpgt:
      case Bytecodes::_if_icmple:
      case Bytecodes::_if_icmpge:
      case Bytecodes::_if_acmpeq:
      case Bytecodes::_if_acmpne:
      case Bytecodes::_goto:
      case Bytecodes::_jsr:
        if( bcs.dest() < bcs.next_bci() ) _access_flags.set_has_loops();
        break;

      case Bytecodes::_goto_w:
      case Bytecodes::_jsr_w:
        if( bcs.dest_w() < bcs.next_bci() ) _access_flags.set_has_loops();
        break;
    }
  }
  _access_flags.set_loops_flag_init();
  return _access_flags.has_loops();
}

bool Method::is_final_method(AccessFlags class_access_flags) const {
  // or "does_not_require_vtable_entry"
  // default method or overpass can occur, is not final (reuses vtable entry)
  // private methods get vtable entries for backward class compatibility.
  if (is_overpass() || is_default_method())  return false;
  return is_final() || class_access_flags.is_final();
}

bool Method::is_final_method() const {
  return is_final_method(method_holder()->access_flags());
}

bool Method::is_default_method() const {
  if (method_holder() != NULL &&
      method_holder()->is_interface() &&
      !is_abstract()) {
    return true;
  } else {
    return false;
  }
}

bool Method::can_be_statically_bound(AccessFlags class_access_flags) const {
  if (is_final_method(class_access_flags))  return true;
#ifdef ASSERT
  ResourceMark rm;
  bool is_nonv = (vtable_index() == nonvirtual_vtable_index);
  if (class_access_flags.is_interface()) {
      assert(is_nonv == is_static(), err_msg("is_nonv=%s", name_and_sig_as_C_string()));
  }
#endif
  assert(valid_vtable_index() || valid_itable_index(), "method must be linked before we ask this question");
  return vtable_index() == nonvirtual_vtable_index;
}

bool Method::can_be_statically_bound() const {
  return can_be_statically_bound(method_holder()->access_flags());
}

bool Method::is_accessor() const {
  if (code_size() != 5) return false;
  if (size_of_parameters() != 1) return false;
  if (java_code_at(0) != Bytecodes::_aload_0 ) return false;
  if (java_code_at(1) != Bytecodes::_getfield) return false;
  if (java_code_at(4) != Bytecodes::_areturn &&
      java_code_at(4) != Bytecodes::_ireturn ) return false;
  return true;
}


bool Method::is_initializer() const {
  return name() == vmSymbols::object_initializer_name() || is_static_initializer();
}

bool Method::has_valid_initializer_flags() const {
  return (is_static() ||
          method_holder()->major_version() < 51);
}

bool Method::is_static_initializer() const {
  // For classfiles version 51 or greater, ensure that the clinit method is
  // static.  Non-static methods with the name "<clinit>" are not static
  // initializers. (older classfiles exempted for backward compatibility)
  return name() == vmSymbols::class_initializer_name() &&
         has_valid_initializer_flags();
}


objArrayHandle Method::resolved_checked_exceptions_impl(Method* this_oop, TRAPS) {
  int length = this_oop->checked_exceptions_length();
  if (length == 0) {  // common case
    return objArrayHandle(THREAD, Universe::the_empty_class_klass_array());
  } else {
    methodHandle h_this(THREAD, this_oop);
    objArrayOop m_oop = oopFactory::new_objArray(SystemDictionary::Class_klass(), length, CHECK_(objArrayHandle()));
    objArrayHandle mirrors (THREAD, m_oop);
    for (int i = 0; i < length; i++) {
      CheckedExceptionElement* table = h_this->checked_exceptions_start(); // recompute on each iteration, not gc safe
      Klass* k = h_this->constants()->klass_at(table[i].class_cp_index, CHECK_(objArrayHandle()));
      assert(k->is_subclass_of(SystemDictionary::Throwable_klass()), "invalid exception class");
      mirrors->obj_at_put(i, k->java_mirror());
    }
    return mirrors;
  }
};


int Method::line_number_from_bci(int bci) const {
  if (bci == SynchronizationEntryBCI) bci = 0;
  assert(bci == 0 || 0 <= bci && bci < code_size(), "illegal bci");
  int best_bci  =  0;
  int best_line = -1;

  if (has_linenumber_table()) {
    // The line numbers are a short array of 2-tuples [start_pc, line_number].
    // Not necessarily sorted and not necessarily one-to-one.
    CompressedLineNumberReadStream stream(compressed_linenumber_table());
    while (stream.read_pair()) {
      if (stream.bci() == bci) {
        // perfect match
        return stream.line();
      } else {
        // update best_bci/line
        if (stream.bci() < bci && stream.bci() >= best_bci) {
          best_bci  = stream.bci();
          best_line = stream.line();
        }
      }
    }
  }
  return best_line;
}


bool Method::is_klass_loaded_by_klass_index(int klass_index) const {
  if( constants()->tag_at(klass_index).is_unresolved_klass() ) {
    Thread *thread = Thread::current();
    Symbol* klass_name = constants()->klass_name_at(klass_index);
    Handle loader(thread, method_holder()->class_loader());
    Handle prot  (thread, method_holder()->protection_domain());
    return SystemDictionary::find(klass_name, loader, prot, thread) != NULL;
  } else {
    return true;
  }
}


bool Method::is_klass_loaded(int refinfo_index, bool must_be_resolved) const {
  int klass_index = constants()->klass_ref_index_at(refinfo_index);
  if (must_be_resolved) {
    // Make sure klass is resolved in constantpool.
    if (constants()->tag_at(klass_index).is_unresolved_klass()) return false;
  }
  return is_klass_loaded_by_klass_index(klass_index);
}


void Method::set_native_function(address function, bool post_event_flag) {
  assert(function != NULL, "use clear_native_function to unregister natives");
  assert(!is_method_handle_intrinsic() || function == SharedRuntime::native_method_throw_unsatisfied_link_error_entry(), "");
  address* native_function = native_function_addr();

  // We can see racers trying to place the same native function into place. Once
  // is plenty.
  address current = *native_function;
  if (current == function) return;
  if (post_event_flag && JvmtiExport::should_post_native_method_bind() &&
      function != NULL) {
    // native_method_throw_unsatisfied_link_error_entry() should only
    // be passed when post_event_flag is false.
    assert(function !=
      SharedRuntime::native_method_throw_unsatisfied_link_error_entry(),
      "post_event_flag mis-match");

    // post the bind event, and possible change the bind function
    JvmtiExport::post_native_method_bind(this, &function);
  }
  *native_function = function;
  // This function can be called more than once. We must make sure that we always
  // use the latest registered method -> check if a stub already has been generated.
  // If so, we have to make it not_entrant.
  nmethod* nm = code(); // Put it into local variable to guard against concurrent updates
  if (nm != NULL) {
    nm->make_not_entrant();
  }
}


bool Method::has_native_function() const {
  if (is_method_handle_intrinsic())
    return false;  // special-cased in SharedRuntime::generate_native_wrapper
  address func = native_function();
  return (func != NULL && func != SharedRuntime::native_method_throw_unsatisfied_link_error_entry());
}


void Method::clear_native_function() {
  // Note: is_method_handle_intrinsic() is allowed here.
  set_native_function(
    SharedRuntime::native_method_throw_unsatisfied_link_error_entry(),
    !native_bind_event_is_interesting);
  clear_code();
}

address Method::critical_native_function() {
  methodHandle mh(this);
  return NativeLookup::lookup_critical_entry(mh);
}


void Method::set_signature_handler(address handler) {
  address* signature_handler =  signature_handler_addr();
  *signature_handler = handler;
}


void Method::print_made_not_compilable(int comp_level, bool is_osr, bool report, const char* reason) {
  if (PrintCompilation && report) {
    ttyLocker ttyl;
    tty->print("made not %scompilable on ", is_osr ? "OSR " : "");
    if (comp_level == CompLevel_all) {
      tty->print("all levels ");
    } else {
      tty->print("levels ");
      for (int i = (int)CompLevel_none; i <= comp_level; i++) {
        tty->print("%d ", i);
      }
    }
    this->print_short_name(tty);
    int size = this->code_size();
    if (size > 0) {
      tty->print(" (%d bytes)", size);
    }
    if (reason != NULL) {
      tty->print("   %s", reason);
    }
    tty->cr();
  }
  if ((TraceDeoptimization || LogCompilation) && (xtty != NULL)) {
    ttyLocker ttyl;
    xtty->begin_elem("make_not_%scompilable thread='" UINTX_FORMAT "'",
                     is_osr ? "osr_" : "", os::current_thread_id());
    if (reason != NULL) {
      xtty->print(" reason=\'%s\'", reason);
    }
    xtty->method(this);
    xtty->stamp();
    xtty->end_elem();
  }
}

bool Method::is_always_compilable() const {
  // Generated adapters must be compiled
  if (is_method_handle_intrinsic() && is_synthetic()) {
    assert(!is_not_c1_compilable(), "sanity check");
    assert(!is_not_c2_compilable(), "sanity check");
    return true;
  }

  return false;
}

bool Method::is_not_compilable(int comp_level) const {
  if (number_of_breakpoints() > 0)
    return true;
  if (is_always_compilable())
    return false;
  if (comp_level == CompLevel_any)
    return is_not_c1_compilable() || is_not_c2_compilable();
  if (is_c1_compile(comp_level))
    return is_not_c1_compilable();
  if (is_c2_compile(comp_level))
    return is_not_c2_compilable();
  return false;
}

// call this when compiler finds that this method is not compilable
void Method::set_not_compilable(int comp_level, bool report, const char* reason) {
  if (is_always_compilable()) {
    // Don't mark a method which should be always compilable
    return;
  }
  print_made_not_compilable(comp_level, /*is_osr*/ false, report, reason);
  if (comp_level == CompLevel_all) {
    set_not_c1_compilable();
    set_not_c2_compilable();
  } else {
    if (is_c1_compile(comp_level))
      set_not_c1_compilable();
    if (is_c2_compile(comp_level))
      set_not_c2_compilable();
  }
  CompilationPolicy::policy()->disable_compilation(this);
  assert(!CompilationPolicy::can_be_compiled(this, comp_level), "sanity check");
}

bool Method::is_not_osr_compilable(int comp_level) const {
  if (is_not_compilable(comp_level))
    return true;
  if (comp_level == CompLevel_any)
    return is_not_c1_osr_compilable() || is_not_c2_osr_compilable();
  if (is_c1_compile(comp_level))
    return is_not_c1_osr_compilable();
  if (is_c2_compile(comp_level))
    return is_not_c2_osr_compilable();
  return false;
}

void Method::set_not_osr_compilable(int comp_level, bool report, const char* reason) {
  print_made_not_compilable(comp_level, /*is_osr*/ true, report, reason);
  if (comp_level == CompLevel_all) {
    set_not_c1_osr_compilable();
    set_not_c2_osr_compilable();
  } else {
    if (is_c1_compile(comp_level))
      set_not_c1_osr_compilable();
    if (is_c2_compile(comp_level))
      set_not_c2_osr_compilable();
  }
  CompilationPolicy::policy()->disable_compilation(this);
  assert(!CompilationPolicy::can_be_osr_compiled(this, comp_level), "sanity check");
}

// Revert to using the interpreter and clear out the nmethod
void Method::clear_code() {

  // this may be NULL if c2i adapters have not been made yet
  // Only should happen at allocate time.
  if (_adapter == NULL) {
    _from_compiled_entry    = NULL;
  } else {
    _from_compiled_entry    = _adapter->get_c2i_entry();
  }
  OrderAccess::storestore();
  _from_interpreted_entry = _i2i_entry;
  OrderAccess::storestore();
  _code = NULL;
}

// Called by class data sharing to remove any entry points (which are not shared)
void Method::unlink_method() {
  _code = NULL;
  _i2i_entry = NULL;
  _from_interpreted_entry = NULL;
  if (is_native()) {
    *native_function_addr() = NULL;
    set_signature_handler(NULL);
  }
  NOT_PRODUCT(set_compiled_invocation_count(0);)
  _adapter = NULL;
  _from_compiled_entry = NULL;

  // In case of DumpSharedSpaces, _method_data should always be NULL.
  //
  // During runtime (!DumpSharedSpaces), when we are cleaning a
  // shared class that failed to load, this->link_method() may
  // have already been called (before an exception happened), so
  // this->_method_data may not be NULL.
  assert(!DumpSharedSpaces || _method_data == NULL, "unexpected method data?");

  set_method_data(NULL);
  set_method_counters(NULL);
}

// Called when the method_holder is getting linked. Setup entrypoints so the method
// is ready to be called from interpreter, compiler, and vtables.
void Method::link_method(methodHandle h_method, TRAPS) {
  // If the code cache is full, we may reenter this function for the
  // leftover methods that weren't linked.
  if (_i2i_entry != NULL) return;

  assert(_adapter == NULL, "init'd to NULL" );
  assert( _code == NULL, "nothing compiled yet" );

  // Setup interpreter entrypoint
  assert(this == h_method(), "wrong h_method()" );
  address entry = Interpreter::entry_for_method(h_method);
  assert(entry != NULL, "interpreter entry must be non-null");
  // Sets both _i2i_entry and _from_interpreted_entry
  set_interpreter_entry(entry);

  // Don't overwrite already registered native entries.
  if (is_native() && !has_native_function()) {
    set_native_function(
      SharedRuntime::native_method_throw_unsatisfied_link_error_entry(),
      !native_bind_event_is_interesting);
  }

  // Setup compiler entrypoint.  This is made eagerly, so we do not need
  // special handling of vtables.  An alternative is to make adapters more
  // lazily by calling make_adapter() from from_compiled_entry() for the
  // normal calls.  For vtable calls life gets more complicated.  When a
  // call-site goes mega-morphic we need adapters in all methods which can be
  // called from the vtable.  We need adapters on such methods that get loaded
  // later.  Ditto for mega-morphic itable calls.  If this proves to be a
  // problem we'll make these lazily later.
  (void) make_adapters(h_method, CHECK);

  // ONLY USE the h_method now as make_adapter may have blocked

}

address Method::make_adapters(methodHandle mh, TRAPS) {
  // Adapters for compiled code are made eagerly here.  They are fairly
  // small (generally < 100 bytes) and quick to make (and cached and shared)
  // so making them eagerly shouldn't be too expensive.
  AdapterHandlerEntry* adapter = AdapterHandlerLibrary::get_adapter(mh);
  if (adapter == NULL ) {
    THROW_MSG_NULL(vmSymbols::java_lang_VirtualMachineError(), "out of space in CodeCache for adapters");
  }

  mh->set_adapter_entry(adapter);
  mh->_from_compiled_entry = adapter->get_c2i_entry();
  return adapter->get_c2i_entry();
}

// The verified_code_entry() must be called when a invoke is resolved
// on this method.

// It returns the compiled code entry point, after asserting not null.
// This function is called after potential safepoints so that nmethod
// or adapter that it points to is still live and valid.
// This function must not hit a safepoint!
address Method::verified_code_entry() {
  debug_only(No_Safepoint_Verifier nsv;)
  assert(_from_compiled_entry != NULL, "must be set");
  return _from_compiled_entry;
}

// Check that if an nmethod ref exists, it has a backlink to this or no backlink at all
// (could be racing a deopt).
// Not inline to avoid circular ref.
bool Method::check_code() const {
  // cached in a register or local.  There's a race on the value of the field.
  nmethod *code = (nmethod *)OrderAccess::load_ptr_acquire(&_code);
  return code == NULL || (code->method() == NULL) || (code->method() == (Method*)this && !code->is_osr_method());
}

// Install compiled code.  Instantly it can execute.
void Method::set_code(methodHandle mh, nmethod *code) {
  assert( code, "use clear_code to remove code" );
  assert( mh->check_code(), "" );

  guarantee(mh->adapter() != NULL, "Adapter blob must already exist!");

  // These writes must happen in this order, because the interpreter will
  // directly jump to from_interpreted_entry which jumps to an i2c adapter
  // which jumps to _from_compiled_entry.
  mh->_code = code;             // Assign before allowing compiled code to exec

  int comp_level = code->comp_level();
  // In theory there could be a race here. In practice it is unlikely
  // and not worth worrying about.
  if (comp_level > mh->highest_comp_level()) {
    mh->set_highest_comp_level(comp_level);
  }

  OrderAccess::storestore();
#ifdef SHARK
  mh->_from_interpreted_entry = code->insts_begin();
#else //!SHARK
  mh->_from_compiled_entry = code->verified_entry_point();
  OrderAccess::storestore();
  // Instantly compiled code can execute.
  if (!mh->is_method_handle_intrinsic())
    mh->_from_interpreted_entry = mh->get_i2c_entry();
#endif //!SHARK
}


bool Method::is_overridden_in(Klass* k) const {
  InstanceKlass* ik = InstanceKlass::cast(k);

  if (ik->is_interface()) return false;

  // If method is an interface, we skip it - except if it
  // is a miranda method
  if (method_holder()->is_interface()) {
    // Check that method is not a miranda method
    if (ik->lookup_method(name(), signature()) == NULL) {
      // No implementation exist - so miranda method
      return false;
    }
    return true;
  }

  assert(ik->is_subclass_of(method_holder()), "should be subklass");
  assert(ik->vtable() != NULL, "vtable should exist");
  if (!has_vtable_index()) {
    return false;
  } else {
    Method* vt_m = ik->method_at_vtable(vtable_index());
    return vt_m != this;
  }
}


// give advice about whether this Method* should be cached or not
bool Method::should_not_be_cached() const {
  if (is_old()) {
    // This method has been redefined. It is either EMCP or obsolete
    // and we don't want to cache it because that would pin the method
    // down and prevent it from being collectible if and when it
    // finishes executing.
    return true;
  }

  // caching this method should be just fine
  return false;
}


/**
 *  Returns true if this is one of the specially treated methods for
 *  security related stack walks (like Reflection.getCallerClass).
 */
bool Method::is_ignored_by_security_stack_walk() const {
  const bool use_new_reflection = JDK_Version::is_gte_jdk14x_version() && UseNewReflection;

  if (intrinsic_id() == vmIntrinsics::_invoke) {
    // This is Method.invoke() -- ignore it
    return true;
  }
  if (use_new_reflection &&
      method_holder()->is_subclass_of(SystemDictionary::reflect_MethodAccessorImpl_klass())) {
    // This is an auxilary frame -- ignore it
    return true;
  }
  if (is_method_handle_intrinsic() || is_compiled_lambda_form()) {
    // This is an internal adapter frame for method handles -- ignore it
    return true;
  }
  return false;
}


// Constant pool structure for invoke methods:
enum {
  _imcp_invoke_name = 1,        // utf8: 'invokeExact', etc.
  _imcp_invoke_signature,       // utf8: (variable Symbol*)
  _imcp_limit
};

// Test if this method is an MH adapter frame generated by Java code.
// Cf. java/lang/invoke/InvokerBytecodeGenerator
bool Method::is_compiled_lambda_form() const {
  return intrinsic_id() == vmIntrinsics::_compiledLambdaForm;
}

// Test if this method is an internal MH primitive method.
bool Method::is_method_handle_intrinsic() const {
  vmIntrinsics::ID iid = intrinsic_id();
  return (MethodHandles::is_signature_polymorphic(iid) &&
          MethodHandles::is_signature_polymorphic_intrinsic(iid));
}

bool Method::has_member_arg() const {
  vmIntrinsics::ID iid = intrinsic_id();
  return (MethodHandles::is_signature_polymorphic(iid) &&
          MethodHandles::has_member_arg(iid));
}

// Make an instance of a signature-polymorphic internal MH primitive.
methodHandle Method::make_method_handle_intrinsic(vmIntrinsics::ID iid,
                                                         Symbol* signature,
                                                         TRAPS) {
  ResourceMark rm;
  methodHandle empty;

  KlassHandle holder = SystemDictionary::MethodHandle_klass();
  Symbol* name = MethodHandles::signature_polymorphic_intrinsic_name(iid);
  assert(iid == MethodHandles::signature_polymorphic_name_id(name), "");
  if (TraceMethodHandles) {
    tty->print_cr("make_method_handle_intrinsic MH.%s%s", name->as_C_string(), signature->as_C_string());
  }

  // invariant:   cp->symbol_at_put is preceded by a refcount increment (more usually a lookup)
  name->increment_refcount();
  signature->increment_refcount();

  int cp_length = _imcp_limit;
  ClassLoaderData* loader_data = holder->class_loader_data();
  constantPoolHandle cp;
  {
    ConstantPool* cp_oop = ConstantPool::allocate(loader_data, cp_length, CHECK_(empty));
    cp = constantPoolHandle(THREAD, cp_oop);
  }
  cp->set_pool_holder(InstanceKlass::cast(holder()));
  cp->symbol_at_put(_imcp_invoke_name,       name);
  cp->symbol_at_put(_imcp_invoke_signature,  signature);
  cp->set_has_preresolution();

  // decide on access bits:  public or not?
  int flags_bits = (JVM_ACC_NATIVE | JVM_ACC_SYNTHETIC | JVM_ACC_FINAL);
  bool must_be_static = MethodHandles::is_signature_polymorphic_static(iid);
  if (must_be_static)  flags_bits |= JVM_ACC_STATIC;
  assert((flags_bits & JVM_ACC_PUBLIC) == 0, "do not expose these methods");

  methodHandle m;
  {
    InlineTableSizes sizes;
    Method* m_oop = Method::allocate(loader_data, 0,
                                     accessFlags_from(flags_bits), &sizes,
                                     ConstMethod::NORMAL, CHECK_(empty));
    m = methodHandle(THREAD, m_oop);
  }
  m->set_constants(cp());
  m->set_name_index(_imcp_invoke_name);
  m->set_signature_index(_imcp_invoke_signature);
  assert(MethodHandles::is_signature_polymorphic_name(m->name()), "");
  assert(m->signature() == signature, "");
#ifdef CC_INTERP
  ResultTypeFinder rtf(signature);
  m->set_result_index(rtf.type());
#endif
  m->compute_size_of_parameters(THREAD);
  m->init_intrinsic_id();
  assert(m->is_method_handle_intrinsic(), "");
#ifdef ASSERT
  if (!MethodHandles::is_signature_polymorphic(m->intrinsic_id()))  m->print();
  assert(MethodHandles::is_signature_polymorphic(m->intrinsic_id()), "must be an invoker");
  assert(m->intrinsic_id() == iid, "correctly predicted iid");
#endif //ASSERT

  // Finally, set up its entry points.
  assert(m->can_be_statically_bound(), "");
  m->set_vtable_index(Method::nonvirtual_vtable_index);
  m->link_method(m, CHECK_(empty));

  if (TraceMethodHandles && (Verbose || WizardMode))
    m->print_on(tty);

  return m;
}

Klass* Method::check_non_bcp_klass(Klass* klass) {
  if (klass != NULL && klass->class_loader() != NULL) {
    if (klass->oop_is_objArray())
      klass = ObjArrayKlass::cast(klass)->bottom_klass();
    return klass;
  }
  return NULL;
}


methodHandle Method::clone_with_new_data(methodHandle m, u_char* new_code, int new_code_length,
                                                u_char* new_compressed_linenumber_table, int new_compressed_linenumber_size, TRAPS) {
  // Code below does not work for native methods - they should never get rewritten anyway
  assert(!m->is_native(), "cannot rewrite native methods");
  // Allocate new Method*
  AccessFlags flags = m->access_flags();

  ConstMethod* cm = m->constMethod();
  int checked_exceptions_len = cm->checked_exceptions_length();
  int localvariable_len = cm->localvariable_table_length();
  int exception_table_len = cm->exception_table_length();
  int method_parameters_len = cm->method_parameters_length();
  int method_annotations_len = cm->method_annotations_length();
  int parameter_annotations_len = cm->parameter_annotations_length();
  int type_annotations_len = cm->type_annotations_length();
  int default_annotations_len = cm->default_annotations_length();

  InlineTableSizes sizes(
      localvariable_len,
      new_compressed_linenumber_size,
      exception_table_len,
      checked_exceptions_len,
      method_parameters_len,
      cm->generic_signature_index(),
      method_annotations_len,
      parameter_annotations_len,
      type_annotations_len,
      default_annotations_len,
      0);

  ClassLoaderData* loader_data = m->method_holder()->class_loader_data();
  Method* newm_oop = Method::allocate(loader_data,
                                      new_code_length,
                                      flags,
                                      &sizes,
                                      m->method_type(),
                                      CHECK_(methodHandle()));
  methodHandle newm (THREAD, newm_oop);
  int new_method_size = newm->method_size();

  // Create a shallow copy of Method part, but be careful to preserve the new ConstMethod*
  ConstMethod* newcm = newm->constMethod();
  int new_const_method_size = newm->constMethod()->size();

  memcpy(newm(), m(), sizeof(Method));

  // Create shallow copy of ConstMethod.
  memcpy(newcm, m->constMethod(), sizeof(ConstMethod));

  // Reset correct method/const method, method size, and parameter info
  newm->set_constMethod(newcm);
  newm->constMethod()->set_code_size(new_code_length);
  newm->constMethod()->set_constMethod_size(new_const_method_size);
  newm->set_method_size(new_method_size);
  assert(newm->code_size() == new_code_length, "check");
  assert(newm->method_parameters_length() == method_parameters_len, "check");
  assert(newm->checked_exceptions_length() == checked_exceptions_len, "check");
  assert(newm->exception_table_length() == exception_table_len, "check");
  assert(newm->localvariable_table_length() == localvariable_len, "check");
  // Copy new byte codes
  memcpy(newm->code_base(), new_code, new_code_length);
  // Copy line number table
  if (new_compressed_linenumber_size > 0) {
    memcpy(newm->compressed_linenumber_table(),
           new_compressed_linenumber_table,
           new_compressed_linenumber_size);
  }
  // Copy method_parameters
  if (method_parameters_len > 0) {
    memcpy(newm->method_parameters_start(),
           m->method_parameters_start(),
           method_parameters_len * sizeof(MethodParametersElement));
  }
  // Copy checked_exceptions
  if (checked_exceptions_len > 0) {
    memcpy(newm->checked_exceptions_start(),
           m->checked_exceptions_start(),
           checked_exceptions_len * sizeof(CheckedExceptionElement));
  }
  // Copy exception table
  if (exception_table_len > 0) {
    memcpy(newm->exception_table_start(),
           m->exception_table_start(),
           exception_table_len * sizeof(ExceptionTableElement));
  }
  // Copy local variable number table
  if (localvariable_len > 0) {
    memcpy(newm->localvariable_table_start(),
           m->localvariable_table_start(),
           localvariable_len * sizeof(LocalVariableTableElement));
  }
  // Copy stackmap table
  if (m->has_stackmap_table()) {
    int code_attribute_length = m->stackmap_data()->length();
    Array<u1>* stackmap_data =
      MetadataFactory::new_array<u1>(loader_data, code_attribute_length, 0, CHECK_NULL);
    memcpy((void*)stackmap_data->adr_at(0),
           (void*)m->stackmap_data()->adr_at(0), code_attribute_length);
    newm->set_stackmap_data(stackmap_data);
  }

  // copy annotations over to new method
  newcm->copy_annotations_from(cm);
  return newm;
}

vmSymbols::SID Method::klass_id_for_intrinsics(Klass* holder) {
  // if loader is not the default loader (i.e., != NULL), we can't know the intrinsics
  // because we are not loading from core libraries
  // exception: the AES intrinsics come from lib/ext/sunjce_provider.jar
  // which does not use the class default class loader so we check for its loader here
  InstanceKlass* ik = InstanceKlass::cast(holder);
  if ((ik->class_loader() != NULL) && !SystemDictionary::is_ext_class_loader(ik->class_loader())) {
    return vmSymbols::NO_SID;   // regardless of name, no intrinsics here
  }

  // see if the klass name is well-known:
  Symbol* klass_name = ik->name();
  return vmSymbols::find_sid(klass_name);
}

void Method::init_intrinsic_id() {
  assert(_intrinsic_id == vmIntrinsics::_none, "do this just once");
  const uintptr_t max_id_uint = right_n_bits((int)(sizeof(_intrinsic_id) * BitsPerByte));
  assert((uintptr_t)vmIntrinsics::ID_LIMIT <= max_id_uint, "else fix size");
  assert(intrinsic_id_size_in_bytes() == sizeof(_intrinsic_id), "");

  // the klass name is well-known:
  vmSymbols::SID klass_id = klass_id_for_intrinsics(method_holder());
  assert(klass_id != vmSymbols::NO_SID, "caller responsibility");

  // ditto for method and signature:
  vmSymbols::SID  name_id = vmSymbols::find_sid(name());
  if (klass_id != vmSymbols::VM_SYMBOL_ENUM_NAME(java_lang_invoke_MethodHandle)
      && name_id == vmSymbols::NO_SID)
    return;
  vmSymbols::SID   sig_id = vmSymbols::find_sid(signature());
  if (klass_id != vmSymbols::VM_SYMBOL_ENUM_NAME(java_lang_invoke_MethodHandle)
      && sig_id == vmSymbols::NO_SID)  return;
  jshort flags = access_flags().as_short();

  vmIntrinsics::ID id = vmIntrinsics::find_id(klass_id, name_id, sig_id, flags);
  if (id != vmIntrinsics::_none) {
    set_intrinsic_id(id);
    return;
  }

  // A few slightly irregular cases:
  switch (klass_id) {
  case vmSymbols::VM_SYMBOL_ENUM_NAME(java_lang_StrictMath):
    // Second chance: check in regular Math.
    switch (name_id) {
    case vmSymbols::VM_SYMBOL_ENUM_NAME(min_name):
    case vmSymbols::VM_SYMBOL_ENUM_NAME(max_name):
    case vmSymbols::VM_SYMBOL_ENUM_NAME(sqrt_name):
      // pretend it is the corresponding method in the non-strict class:
      klass_id = vmSymbols::VM_SYMBOL_ENUM_NAME(java_lang_Math);
      id = vmIntrinsics::find_id(klass_id, name_id, sig_id, flags);
      break;
    }
    break;

  // Signature-polymorphic methods: MethodHandle.invoke*, InvokeDynamic.*.
  case vmSymbols::VM_SYMBOL_ENUM_NAME(java_lang_invoke_MethodHandle):
    if (!is_native())  break;
    id = MethodHandles::signature_polymorphic_name_id(method_holder(), name());
    if (is_static() != MethodHandles::is_signature_polymorphic_static(id))
      id = vmIntrinsics::_none;
    break;
  }

  if (id != vmIntrinsics::_none) {
    // Set up its iid.  It is an alias method.
    set_intrinsic_id(id);
    return;
  }
}

// These two methods are static since a GC may move the Method
bool Method::load_signature_classes(methodHandle m, TRAPS) {
  if (THREAD->is_Compiler_thread()) {
    // There is nothing useful this routine can do from within the Compile thread.
    // Hopefully, the signature contains only well-known classes.
    // We could scan for this and return true/false, but the caller won't care.
    return false;
  }
  bool sig_is_loaded = true;
  Handle class_loader(THREAD, m->method_holder()->class_loader());
  Handle protection_domain(THREAD, m->method_holder()->protection_domain());
  ResourceMark rm(THREAD);
  Symbol*  signature = m->signature();
  for(SignatureStream ss(signature); !ss.is_done(); ss.next()) {
    if (ss.is_object()) {
      Symbol* sym = ss.as_symbol(CHECK_(false));
      Symbol*  name  = sym;
      Klass* klass = SystemDictionary::resolve_or_null(name, class_loader,
                                             protection_domain, THREAD);
      // We are loading classes eagerly. If a ClassNotFoundException or
      // a LinkageError was generated, be sure to ignore it.
      if (HAS_PENDING_EXCEPTION) {
        if (PENDING_EXCEPTION->is_a(SystemDictionary::ClassNotFoundException_klass()) ||
            PENDING_EXCEPTION->is_a(SystemDictionary::LinkageError_klass())) {
          CLEAR_PENDING_EXCEPTION;
        } else {
          return false;
        }
      }
      if( klass == NULL) { sig_is_loaded = false; }
    }
  }
  return sig_is_loaded;
}

bool Method::has_unloaded_classes_in_signature(methodHandle m, TRAPS) {
  Handle class_loader(THREAD, m->method_holder()->class_loader());
  Handle protection_domain(THREAD, m->method_holder()->protection_domain());
  ResourceMark rm(THREAD);
  Symbol*  signature = m->signature();
  for(SignatureStream ss(signature); !ss.is_done(); ss.next()) {
    if (ss.type() == T_OBJECT) {
      Symbol* name = ss.as_symbol_or_null();
      if (name == NULL) return true;
      Klass* klass = SystemDictionary::find(name, class_loader, protection_domain, THREAD);
      if (klass == NULL) return true;
    }
  }
  return false;
}

// Exposed so field engineers can debug VM
void Method::print_short_name(outputStream* st) {
  ResourceMark rm;
#ifdef PRODUCT
  st->print(" %s::", method_holder()->external_name());
#else
  st->print(" %s::", method_holder()->internal_name());
#endif
  name()->print_symbol_on(st);
  if (WizardMode) signature()->print_symbol_on(st);
  else if (MethodHandles::is_signature_polymorphic(intrinsic_id()))
    MethodHandles::print_as_basic_type_signature_on(st, signature(), true);
}

// Comparer for sorting an object array containing
// Method*s.
static int method_comparator(Method* a, Method* b) {
  return a->name()->fast_compare(b->name());
}

// This is only done during class loading, so it is OK to assume method_idnum matches the methods() array
// default_methods also uses this without the ordering for fast find_method
void Method::sort_methods(Array<Method*>* methods, bool idempotent, bool set_idnums) {
  int length = methods->length();
  if (length > 1) {
    {
      No_Safepoint_Verifier nsv;
      QuickSort::sort<Method*>(methods->data(), length, method_comparator, idempotent);
    }
    // Reset method ordering
    if (set_idnums) {
      for (int i = 0; i < length; i++) {
        Method* m = methods->at(i);
        m->set_method_idnum(i);
      }
    }
  }
}

//-----------------------------------------------------------------------------------
// Non-product code unless JVM/TI needs it

#if !defined(PRODUCT) || INCLUDE_JVMTI
class SignatureTypePrinter : public SignatureTypeNames {
 private:
  outputStream* _st;
  bool _use_separator;

  void type_name(const char* name) {
    if (_use_separator) _st->print(", ");
    _st->print(name);
    _use_separator = true;
  }

 public:
  SignatureTypePrinter(Symbol* signature, outputStream* st) : SignatureTypeNames(signature) {
    _st = st;
    _use_separator = false;
  }

  void print_parameters()              { _use_separator = false; iterate_parameters(); }
  void print_returntype()              { _use_separator = false; iterate_returntype(); }
};


void Method::print_name(outputStream* st) {
  Thread *thread = Thread::current();
  ResourceMark rm(thread);
  SignatureTypePrinter sig(signature(), st);
  st->print("%s ", is_static() ? "static" : "virtual");
  sig.print_returntype();
  st->print(" %s.", method_holder()->internal_name());
  name()->print_symbol_on(st);
  st->print("(");
  sig.print_parameters();
  st->print(")");
}
#endif // !PRODUCT || INCLUDE_JVMTI


//-----------------------------------------------------------------------------------
// Non-product code

#ifndef PRODUCT
void Method::print_codes_on(outputStream* st) const {
  print_codes_on(0, code_size(), st);
}

void Method::print_codes_on(int from, int to, outputStream* st) const {
  Thread *thread = Thread::current();
  ResourceMark rm(thread);
  methodHandle mh (thread, (Method*)this);
  BytecodeStream s(mh);
  s.set_interval(from, to);
  BytecodeTracer::set_closure(BytecodeTracer::std_closure());
  while (s.next() >= 0) BytecodeTracer::trace(mh, s.bcp(), st);
}
#endif // not PRODUCT


// Simple compression of line number tables. We use a regular compressed stream, except that we compress deltas
// between (bci,line) pairs since they are smaller. If (bci delta, line delta) fits in (5-bit unsigned, 3-bit unsigned)
// we save it as one byte, otherwise we write a 0xFF escape character and use regular compression. 0x0 is used
// as end-of-stream terminator.

void CompressedLineNumberWriteStream::write_pair_regular(int bci_delta, int line_delta) {
  // bci and line number does not compress into single byte.
  // Write out escape character and use regular compression for bci and line number.
  write_byte((jubyte)0xFF);
  write_signed_int(bci_delta);
  write_signed_int(line_delta);
}

// See comment in method.hpp which explains why this exists.
#if defined(_M_AMD64) && _MSC_VER >= 1400
#pragma optimize("", off)
void CompressedLineNumberWriteStream::write_pair(int bci, int line) {
  write_pair_inline(bci, line);
}
#pragma optimize("", on)
#endif

CompressedLineNumberReadStream::CompressedLineNumberReadStream(u_char* buffer) : CompressedReadStream(buffer) {
  _bci = 0;
  _line = 0;
};


bool CompressedLineNumberReadStream::read_pair() {
  jubyte next = read_byte();
  // Check for terminator
  if (next == 0) return false;
  if (next == 0xFF) {
    // Escape character, regular compression used
    _bci  += read_signed_int();
    _line += read_signed_int();
  } else {
    // Single byte compression used
    _bci  += next >> 3;
    _line += next & 0x7;
  }
  return true;
}


Bytecodes::Code Method::orig_bytecode_at(int bci) const {
  BreakpointInfo* bp = method_holder()->breakpoints();
  for (; bp != NULL; bp = bp->next()) {
    if (bp->match(this, bci)) {
      return bp->orig_bytecode();
    }
  }
  {
    ResourceMark rm;
    fatal(err_msg("no original bytecode found in %s at bci %d", name_and_sig_as_C_string(), bci));
  }
  return Bytecodes::_shouldnotreachhere;
}

void Method::set_orig_bytecode_at(int bci, Bytecodes::Code code) {
  assert(code != Bytecodes::_breakpoint, "cannot patch breakpoints this way");
  BreakpointInfo* bp = method_holder()->breakpoints();
  for (; bp != NULL; bp = bp->next()) {
    if (bp->match(this, bci)) {
      bp->set_orig_bytecode(code);
      // and continue, in case there is more than one
    }
  }
}

void Method::set_breakpoint(int bci) {
  InstanceKlass* ik = method_holder();
  BreakpointInfo *bp = new BreakpointInfo(this, bci);
  bp->set_next(ik->breakpoints());
  ik->set_breakpoints(bp);
  // do this last:
  bp->set(this);
}

static void clear_matches(Method* m, int bci) {
  InstanceKlass* ik = m->method_holder();
  BreakpointInfo* prev_bp = NULL;
  BreakpointInfo* next_bp;
  for (BreakpointInfo* bp = ik->breakpoints(); bp != NULL; bp = next_bp) {
    next_bp = bp->next();
    // bci value of -1 is used to delete all breakpoints in method m (ex: clear_all_breakpoint).
    if (bci >= 0 ? bp->match(m, bci) : bp->match(m)) {
      // do this first:
      bp->clear(m);
      // unhook it
      if (prev_bp != NULL)
        prev_bp->set_next(next_bp);
      else
        ik->set_breakpoints(next_bp);
      delete bp;
      // When class is redefined JVMTI sets breakpoint in all versions of EMCP methods
      // at same location. So we have multiple matching (method_index and bci)
      // BreakpointInfo nodes in BreakpointInfo list. We should just delete one
      // breakpoint for clear_breakpoint request and keep all other method versions
      // BreakpointInfo for future clear_breakpoint request.
      // bcivalue of -1 is used to clear all breakpoints (see clear_all_breakpoints)
      // which is being called when class is unloaded. We delete all the Breakpoint
      // information for all versions of method. We may not correctly restore the original
      // bytecode in all method versions, but that is ok. Because the class is being unloaded
      // so these methods won't be used anymore.
      if (bci >= 0) {
        break;
      }
    } else {
      // This one is a keeper.
      prev_bp = bp;
    }
  }
}

void Method::clear_breakpoint(int bci) {
  assert(bci >= 0, "");
  clear_matches(this, bci);
}

void Method::clear_all_breakpoints() {
  clear_matches(this, -1);
}


int Method::invocation_count() {
  MethodCounters *mcs = method_counters();
  if (TieredCompilation) {
    MethodData* const mdo = method_data();
    if (((mcs != NULL) ? mcs->invocation_counter()->carry() : false) ||
        ((mdo != NULL) ? mdo->invocation_counter()->carry() : false)) {
      return InvocationCounter::count_limit;
    } else {
      return ((mcs != NULL) ? mcs->invocation_counter()->count() : 0) +
             ((mdo != NULL) ? mdo->invocation_counter()->count() : 0);
    }
  } else {
    return (mcs == NULL) ? 0 : mcs->invocation_counter()->count();
  }
}

int Method::backedge_count() {
  MethodCounters *mcs = method_counters();
  if (TieredCompilation) {
    MethodData* const mdo = method_data();
    if (((mcs != NULL) ? mcs->backedge_counter()->carry() : false) ||
        ((mdo != NULL) ? mdo->backedge_counter()->carry() : false)) {
      return InvocationCounter::count_limit;
    } else {
      return ((mcs != NULL) ? mcs->backedge_counter()->count() : 0) +
             ((mdo != NULL) ? mdo->backedge_counter()->count() : 0);
    }
  } else {
    return (mcs == NULL) ? 0 : mcs->backedge_counter()->count();
  }
}

int Method::highest_comp_level() const {
  const MethodData* mdo = method_data();
  if (mdo != NULL) {
    return mdo->highest_comp_level();
  } else {
    return CompLevel_none;
  }
}

int Method::highest_osr_comp_level() const {
  const MethodData* mdo = method_data();
  if (mdo != NULL) {
    return mdo->highest_osr_comp_level();
  } else {
    return CompLevel_none;
  }
}

void Method::set_highest_comp_level(int level) {
  MethodData* mdo = method_data();
  if (mdo != NULL) {
    mdo->set_highest_comp_level(level);
  }
}

void Method::set_highest_osr_comp_level(int level) {
  MethodData* mdo = method_data();
  if (mdo != NULL) {
    mdo->set_highest_osr_comp_level(level);
  }
}

BreakpointInfo::BreakpointInfo(Method* m, int bci) {
  _bci = bci;
  _name_index = m->name_index();
  _signature_index = m->signature_index();
  _orig_bytecode = (Bytecodes::Code) *m->bcp_from(_bci);
  if (_orig_bytecode == Bytecodes::_breakpoint)
    _orig_bytecode = m->orig_bytecode_at(_bci);
  _next = NULL;
}

void BreakpointInfo::set(Method* method) {
#ifdef ASSERT
  {
    Bytecodes::Code code = (Bytecodes::Code) *method->bcp_from(_bci);
    if (code == Bytecodes::_breakpoint)
      code = method->orig_bytecode_at(_bci);
    assert(orig_bytecode() == code, "original bytecode must be the same");
  }
#endif
  Thread *thread = Thread::current();
  *method->bcp_from(_bci) = Bytecodes::_breakpoint;
  method->incr_number_of_breakpoints(thread);
  SystemDictionary::notice_modification();
  {
    // Deoptimize all dependents on this method
    HandleMark hm(thread);
    methodHandle mh(thread, method);
    Universe::flush_dependents_on_method(mh);
  }
}

void BreakpointInfo::clear(Method* method) {
  *method->bcp_from(_bci) = orig_bytecode();
  assert(method->number_of_breakpoints() > 0, "must not go negative");
  method->decr_number_of_breakpoints(Thread::current());
}

// jmethodID handling

// This is a block allocating object, sort of like JNIHandleBlock, only a
// lot simpler.  There aren't many of these, they aren't long, they are rarely
// deleted and so we can do some suboptimal things.
// It's allocated on the CHeap because once we allocate a jmethodID, we can
// never get rid of it.
// It would be nice to be able to parameterize the number of methods for
// the null_class_loader but then we'd have to turn this and ClassLoaderData
// into templates.

// I feel like this brain dead class should exist somewhere in the STL

class JNIMethodBlock : public CHeapObj<mtClass> {
  enum { number_of_methods = 8 };

  Method*         _methods[number_of_methods];
  int             _top;
  JNIMethodBlock* _next;
 public:
  static Method* const _free_method;

  JNIMethodBlock() : _next(NULL), _top(0) {
    for (int i = 0; i< number_of_methods; i++) _methods[i] = _free_method;
  }

  Method** add_method(Method* m) {
    if (_top < number_of_methods) {
      // top points to the next free entry.
      int i = _top;
      _methods[i] = m;
      _top++;
      return &_methods[i];
    } else if (_top == number_of_methods) {
      // if the next free entry ran off the block see if there's a free entry
      for (int i = 0; i< number_of_methods; i++) {
        if (_methods[i] == _free_method) {
          _methods[i] = m;
          return &_methods[i];
        }
      }
      // Only check each block once for frees.  They're very unlikely.
      // Increment top past the end of the block.
      _top++;
    }
    // need to allocate a next block.
    if (_next == NULL) {
      _next = new JNIMethodBlock();
    }
    return _next->add_method(m);
  }

  bool contains(Method** m) {
    for (JNIMethodBlock* b = this; b != NULL; b = b->_next) {
      for (int i = 0; i< number_of_methods; i++) {
        if (&(b->_methods[i]) == m) {
          return true;
        }
      }
    }
    return false;  // not found
  }

  // Doesn't really destroy it, just marks it as free so it can be reused.
  void destroy_method(Method** m) {
#ifdef ASSERT
    assert(contains(m), "should be a methodID");
#endif // ASSERT
    *m = _free_method;
  }

  // During class unloading the methods are cleared, which is different
  // than freed.
  void clear_all_methods() {
    for (JNIMethodBlock* b = this; b != NULL; b = b->_next) {
      for (int i = 0; i< number_of_methods; i++) {
        _methods[i] = NULL;
      }
    }
  }
#ifndef PRODUCT
  int count_methods() {
    // count all allocated methods
    int count = 0;
    for (JNIMethodBlock* b = this; b != NULL; b = b->_next) {
      for (int i = 0; i< number_of_methods; i++) {
        if (_methods[i] != _free_method) count++;
      }
    }
    return count;
  }
#endif // PRODUCT
};

// Something that can't be mistaken for an address or a markOop
Method* const JNIMethodBlock::_free_method = (Method*)55;

// Add a method id to the jmethod_ids
jmethodID Method::make_jmethod_id(ClassLoaderData* loader_data, Method* m) {
  ClassLoaderData* cld = loader_data;

  if (!SafepointSynchronize::is_at_safepoint()) {
    // Have to add jmethod_ids() to class loader data thread-safely.
    // Also have to add the method to the list safely, which the cld lock
    // protects as well.
    MutexLockerEx ml(cld->metaspace_lock(),  Mutex::_no_safepoint_check_flag);
    if (cld->jmethod_ids() == NULL) {
      cld->set_jmethod_ids(new JNIMethodBlock());
    }
    // jmethodID is a pointer to Method*
    return (jmethodID)cld->jmethod_ids()->add_method(m);
  } else {
    // At safepoint, we are single threaded and can set this.
    if (cld->jmethod_ids() == NULL) {
      cld->set_jmethod_ids(new JNIMethodBlock());
    }
    // jmethodID is a pointer to Method*
    return (jmethodID)cld->jmethod_ids()->add_method(m);
  }
}

// Mark a jmethodID as free.  This is called when there is a data race in
// InstanceKlass while creating the jmethodID cache.
void Method::destroy_jmethod_id(ClassLoaderData* loader_data, jmethodID m) {
  ClassLoaderData* cld = loader_data;
  Method** ptr = (Method**)m;
  assert(cld->jmethod_ids() != NULL, "should have method handles");
  cld->jmethod_ids()->destroy_method(ptr);
}

void Method::change_method_associated_with_jmethod_id(jmethodID jmid, Method* new_method) {
  // Can't assert the method_holder is the same because the new method has the
  // scratch method holder.
  assert(resolve_jmethod_id(jmid)->method_holder()->class_loader()
           == new_method->method_holder()->class_loader(),
         "changing to a different class loader");
  // Just change the method in place, jmethodID pointer doesn't change.
  *((Method**)jmid) = new_method;
}

bool Method::is_method_id(jmethodID mid) {
  Method* m = resolve_jmethod_id(mid);
  assert(m != NULL, "should be called with non-null method");
  InstanceKlass* ik = m->method_holder();
  ClassLoaderData* cld = ik->class_loader_data();
  if (cld->jmethod_ids() == NULL) return false;
  return (cld->jmethod_ids()->contains((Method**)mid));
}

Method* Method::checked_resolve_jmethod_id(jmethodID mid) {
  if (mid == NULL) return NULL;
  Method* o = resolve_jmethod_id(mid);
  if (o == NULL || o == JNIMethodBlock::_free_method || !((Metadata*)o)->is_method()) {
    return NULL;
  }
  return o;
};

void Method::set_on_stack(const bool value) {
  // Set both the method itself and its constant pool.  The constant pool
  // on stack means some method referring to it is also on the stack.
  _access_flags.set_on_stack(value);
  constants()->set_on_stack(value);
  if (value) MetadataOnStackMark::record(this);
}

// Called when the class loader is unloaded to make all methods weak.
void Method::clear_jmethod_ids(ClassLoaderData* loader_data) {
  loader_data->jmethod_ids()->clear_all_methods();
}


// Check that this pointer is valid by checking that the vtbl pointer matches
bool Method::is_valid_method() const {
  if (this == NULL) {
    return false;
  } else if (!is_metaspace_object()) {
    return false;
  } else {
    Method m;
    // This assumes that the vtbl pointer is the first word of a C++ object.
    // This assumption is also in universe.cpp patch_klass_vtble
    void* vtbl2 = dereference_vptr((void*)&m);
    void* this_vtbl = dereference_vptr((void*)this);
    return vtbl2 == this_vtbl;
  }
}

#ifndef PRODUCT
void Method::print_jmethod_ids(ClassLoaderData* loader_data, outputStream* out) {
  out->print_cr("jni_method_id count = %d", loader_data->jmethod_ids()->count_methods());
}
#endif // PRODUCT


// Printing

#ifndef PRODUCT

void Method::print_on(outputStream* st) const {
  ResourceMark rm;
  assert(is_method(), "must be method");
  st->print_cr(internal_name());
  // get the effect of PrintOopAddress, always, for methods:
  st->print_cr(" - this oop:          "INTPTR_FORMAT, (intptr_t)this);
  st->print   (" - method holder:     "); method_holder()->print_value_on(st); st->cr();
  st->print   (" - constants:         "INTPTR_FORMAT" ", (address)constants());
  constants()->print_value_on(st); st->cr();
  st->print   (" - access:            0x%x  ", access_flags().as_int()); access_flags().print_on(st); st->cr();
  st->print   (" - name:              ");    name()->print_value_on(st); st->cr();
  st->print   (" - signature:         ");    signature()->print_value_on(st); st->cr();
  st->print_cr(" - max stack:         %d",   max_stack());
  st->print_cr(" - max locals:        %d",   max_locals());
  st->print_cr(" - size of params:    %d",   size_of_parameters());
  st->print_cr(" - method size:       %d",   method_size());
  if (intrinsic_id() != vmIntrinsics::_none)
    st->print_cr(" - intrinsic id:      %d %s", intrinsic_id(), vmIntrinsics::name_at(intrinsic_id()));
  if (highest_comp_level() != CompLevel_none)
    st->print_cr(" - highest level:     %d", highest_comp_level());
  st->print_cr(" - vtable index:      %d",   _vtable_index);
  st->print_cr(" - i2i entry:         " INTPTR_FORMAT, interpreter_entry());
  st->print(   " - adapters:          ");
  AdapterHandlerEntry* a = ((Method*)this)->adapter();
  if (a == NULL)
    st->print_cr(INTPTR_FORMAT, a);
  else
    a->print_adapter_on(st);
  st->print_cr(" - compiled entry     " INTPTR_FORMAT, from_compiled_entry());
  st->print_cr(" - code size:         %d",   code_size());
  if (code_size() != 0) {
    st->print_cr(" - code start:        " INTPTR_FORMAT, code_base());
    st->print_cr(" - code end (excl):   " INTPTR_FORMAT, code_base() + code_size());
  }
  if (method_data() != NULL) {
    st->print_cr(" - method data:       " INTPTR_FORMAT, (address)method_data());
  }
  st->print_cr(" - checked ex length: %d",   checked_exceptions_length());
  if (checked_exceptions_length() > 0) {
    CheckedExceptionElement* table = checked_exceptions_start();
    st->print_cr(" - checked ex start:  " INTPTR_FORMAT, table);
    if (Verbose) {
      for (int i = 0; i < checked_exceptions_length(); i++) {
        st->print_cr("   - throws %s", constants()->printable_name_at(table[i].class_cp_index));
      }
    }
  }
  if (has_linenumber_table()) {
    u_char* table = compressed_linenumber_table();
    st->print_cr(" - linenumber start:  " INTPTR_FORMAT, table);
    if (Verbose) {
      CompressedLineNumberReadStream stream(table);
      while (stream.read_pair()) {
        st->print_cr("   - line %d: %d", stream.line(), stream.bci());
      }
    }
  }
  st->print_cr(" - localvar length:   %d",   localvariable_table_length());
  if (localvariable_table_length() > 0) {
    LocalVariableTableElement* table = localvariable_table_start();
    st->print_cr(" - localvar start:    " INTPTR_FORMAT, table);
    if (Verbose) {
      for (int i = 0; i < localvariable_table_length(); i++) {
        int bci = table[i].start_bci;
        int len = table[i].length;
        const char* name = constants()->printable_name_at(table[i].name_cp_index);
        const char* desc = constants()->printable_name_at(table[i].descriptor_cp_index);
        int slot = table[i].slot;
        st->print_cr("   - %s %s bci=%d len=%d slot=%d", desc, name, bci, len, slot);
      }
    }
  }
  if (code() != NULL) {
    st->print   (" - compiled code: ");
    code()->print_value_on(st);
  }
  if (is_native()) {
    st->print_cr(" - native function:   " INTPTR_FORMAT, native_function());
    st->print_cr(" - signature handler: " INTPTR_FORMAT, signature_handler());
  }
}

#endif //PRODUCT

void Method::print_value_on(outputStream* st) const {
  assert(is_method(), "must be method");
  st->print(internal_name());
  print_address_on(st);
  st->print(" ");
  name()->print_value_on(st);
  st->print(" ");
  signature()->print_value_on(st);
  st->print(" in ");
  method_holder()->print_value_on(st);
  if (WizardMode) st->print("#%d", _vtable_index);
  if (WizardMode) st->print("[%d,%d]", size_of_parameters(), max_locals());
  if (WizardMode && code() != NULL) st->print(" ((nmethod*)%p)", code());
}

#if INCLUDE_SERVICES
// Size Statistics
void Method::collect_statistics(KlassSizeStats *sz) const {
  int mysize = sz->count(this);
  sz->_method_bytes += mysize;
  sz->_method_all_bytes += mysize;
  sz->_rw_bytes += mysize;

  if (constMethod()) {
    constMethod()->collect_statistics(sz);
  }
  if (method_data()) {
    method_data()->collect_statistics(sz);
  }
}
#endif // INCLUDE_SERVICES

// Verification

void Method::verify_on(outputStream* st) {
  guarantee(is_method(), "object must be method");
  guarantee(constants()->is_constantPool(), "should be constant pool");
  guarantee(constMethod()->is_constMethod(), "should be ConstMethod*");
  MethodData* md = method_data();
  guarantee(md == NULL ||
      md->is_methodData(), "should be method data");
}
