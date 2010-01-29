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

# include "incls/_precompiled.incl"
# include "incls/_methodOop.cpp.incl"


// Implementation of methodOopDesc

address methodOopDesc::get_i2c_entry() {
  assert(_adapter != NULL, "must have");
  return _adapter->get_i2c_entry();
}

address methodOopDesc::get_c2i_entry() {
  assert(_adapter != NULL, "must have");
  return _adapter->get_c2i_entry();
}

address methodOopDesc::get_c2i_unverified_entry() {
  assert(_adapter != NULL, "must have");
  return _adapter->get_c2i_unverified_entry();
}

char* methodOopDesc::name_and_sig_as_C_string() {
  return name_and_sig_as_C_string(Klass::cast(constants()->pool_holder()), name(), signature());
}

char* methodOopDesc::name_and_sig_as_C_string(char* buf, int size) {
  return name_and_sig_as_C_string(Klass::cast(constants()->pool_holder()), name(), signature(), buf, size);
}

char* methodOopDesc::name_and_sig_as_C_string(Klass* klass, symbolOop method_name, symbolOop signature) {
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

char* methodOopDesc::name_and_sig_as_C_string(Klass* klass, symbolOop method_name, symbolOop signature, char* buf, int size) {
  symbolOop klass_name = klass->name();
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

int  methodOopDesc::fast_exception_handler_bci_for(KlassHandle ex_klass, int throw_bci, TRAPS) {
  // exception table holds quadruple entries of the form (beg_bci, end_bci, handler_bci, klass_index)
  const int beg_bci_offset     = 0;
  const int end_bci_offset     = 1;
  const int handler_bci_offset = 2;
  const int klass_index_offset = 3;
  const int entry_size         = 4;
  // access exception table
  typeArrayHandle table (THREAD, constMethod()->exception_table());
  int length = table->length();
  assert(length % entry_size == 0, "exception table format has changed");
  // iterate through all entries sequentially
  constantPoolHandle pool(THREAD, constants());
  for (int i = 0; i < length; i += entry_size) {
    int beg_bci = table->int_at(i + beg_bci_offset);
    int end_bci = table->int_at(i + end_bci_offset);
    assert(beg_bci <= end_bci, "inconsistent exception table");
    if (beg_bci <= throw_bci && throw_bci < end_bci) {
      // exception handler bci range covers throw_bci => investigate further
      int handler_bci = table->int_at(i + handler_bci_offset);
      int klass_index = table->int_at(i + klass_index_offset);
      if (klass_index == 0) {
        return handler_bci;
      } else if (ex_klass.is_null()) {
        return handler_bci;
      } else {
        // we know the exception class => get the constraint class
        // this may require loading of the constraint class; if verification
        // fails or some other exception occurs, return handler_bci
        klassOop k = pool->klass_at(klass_index, CHECK_(handler_bci));
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

methodOop methodOopDesc::method_from_bcp(address bcp) {
  debug_only(static int count = 0; count++);
  assert(Universe::heap()->is_in_permanent(bcp), "bcp not in perm_gen");
  // TO DO: this may be unsafe in some configurations
  HeapWord* p = Universe::heap()->block_start(bcp);
  assert(Universe::heap()->block_is_obj(p), "must be obj");
  assert(oop(p)->is_constMethod(), "not a method");
  return constMethodOop(p)->method();
}


void methodOopDesc::mask_for(int bci, InterpreterOopMap* mask) {

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
      instanceKlass::cast(method_holder())->mask_for(h_this, bci, &local_mask);
      local_mask.print();
    }
  }
#endif
  instanceKlass::cast(method_holder())->mask_for(h_this, bci, mask);
  return;
}


int methodOopDesc::bci_from(address bcp) const {
  assert(is_native() && bcp == code_base() || contains(bcp) || is_error_reported(), "bcp doesn't belong to this method");
  return bcp - code_base();
}


// Return (int)bcx if it appears to be a valid BCI.
// Return bci_from((address)bcx) if it appears to be a valid BCP.
// Return -1 otherwise.
// Used by profiling code, when invalid data is a possibility.
// The caller is responsible for validating the methodOop itself.
int methodOopDesc::validate_bci_from_bcx(intptr_t bcx) const {
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

address methodOopDesc::bcp_from(int bci) const {
  assert((is_native() && bci == 0)  || (!is_native() && 0 <= bci && bci < code_size()), "illegal bci");
  address bcp = code_base() + bci;
  assert(is_native() && bcp == code_base() || contains(bcp), "bcp doesn't belong to this method");
  return bcp;
}


int methodOopDesc::object_size(bool is_native) {
  // If native, then include pointers for native_function and signature_handler
  int extra_bytes = (is_native) ? 2*sizeof(address*) : 0;
  int extra_words = align_size_up(extra_bytes, BytesPerWord) / BytesPerWord;
  return align_object_size(header_size() + extra_words);
}


symbolOop methodOopDesc::klass_name() const {
  klassOop k = method_holder();
  assert(k->is_klass(), "must be klass");
  instanceKlass* ik = (instanceKlass*) k->klass_part();
  return ik->name();
}


void methodOopDesc::set_interpreter_kind() {
  int kind = Interpreter::method_kind(methodOop(this));
  assert(kind != Interpreter::invalid,
         "interpreter entry must be valid");
  set_interpreter_kind(kind);
}


// Attempt to return method oop to original state.  Clear any pointers
// (to objects outside the shared spaces).  We won't be able to predict
// where they should point in a new JVM.  Further initialize some
// entries now in order allow them to be write protected later.

void methodOopDesc::remove_unshareable_info() {
  unlink_method();
  set_interpreter_kind();
}


bool methodOopDesc::was_executed_more_than(int n) const {
  // Invocation counter is reset when the methodOop is compiled.
  // If the method has compiled code we therefore assume it has
  // be excuted more than n times.
  if (is_accessor() || is_empty_method() || (code() != NULL)) {
    // interpreter doesn't bump invocation counter of trivial methods
    // compiler does not bump invocation counter of compiled methods
    return true;
  } else if (_invocation_counter.carry()) {
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
void methodOopDesc::print_invocation_count() const {
  if (is_static()) tty->print("static ");
  if (is_final()) tty->print("final ");
  if (is_synchronized()) tty->print("synchronized ");
  if (is_native()) tty->print("native ");
  method_holder()->klass_part()->name()->print_symbol_on(tty);
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

// Build a methodDataOop object to hold information about this method
// collected in the interpreter.
void methodOopDesc::build_interpreter_method_data(methodHandle method, TRAPS) {
  // Grab a lock here to prevent multiple
  // methodDataOops from being created.
  MutexLocker ml(MethodData_lock, THREAD);
  if (method->method_data() == NULL) {
    methodDataOop method_data = oopFactory::new_methodData(method, CHECK);
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

void methodOopDesc::cleanup_inline_caches() {
  // The current system doesn't use inline caches in the interpreter
  // => nothing to do (keep this method around for future use)
}


int methodOopDesc::extra_stack_words() {
  // not an inline function, to avoid a header dependency on Interpreter
  return extra_stack_entries() * Interpreter::stackElementSize();
}


void methodOopDesc::compute_size_of_parameters(Thread *thread) {
  symbolHandle h_signature(thread, signature());
  ArgumentSizeComputer asc(h_signature);
  set_size_of_parameters(asc.size() + (is_static() ? 0 : 1));
}

#ifdef CC_INTERP
void methodOopDesc::set_result_index(BasicType type)          {
  _result_index = Interpreter::BasicType_as_index(type);
}
#endif

BasicType methodOopDesc::result_type() const {
  ResultTypeFinder rtf(signature());
  return rtf.type();
}


bool methodOopDesc::is_empty_method() const {
  return  code_size() == 1
      && *code_base() == Bytecodes::_return;
}


bool methodOopDesc::is_vanilla_constructor() const {
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


bool methodOopDesc::compute_has_loops_flag() {
  BytecodeStream bcs(methodOop(this));
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


bool methodOopDesc::is_final_method() const {
  // %%% Should return true for private methods also,
  // since there is no way to override them.
  return is_final() || Klass::cast(method_holder())->is_final();
}


bool methodOopDesc::is_strict_method() const {
  return is_strict();
}


bool methodOopDesc::can_be_statically_bound() const {
  if (is_final_method())  return true;
  return vtable_index() == nonvirtual_vtable_index;
}


bool methodOopDesc::is_accessor() const {
  if (code_size() != 5) return false;
  if (size_of_parameters() != 1) return false;
  methodOop m = (methodOop)this;  // pass to code_at() to avoid method_from_bcp
  if (Bytecodes::java_code_at(code_base()+0, m) != Bytecodes::_aload_0 ) return false;
  if (Bytecodes::java_code_at(code_base()+1, m) != Bytecodes::_getfield) return false;
  if (Bytecodes::java_code_at(code_base()+4, m) != Bytecodes::_areturn &&
      Bytecodes::java_code_at(code_base()+4, m) != Bytecodes::_ireturn ) return false;
  return true;
}


bool methodOopDesc::is_initializer() const {
  return name() == vmSymbols::object_initializer_name() || name() == vmSymbols::class_initializer_name();
}


objArrayHandle methodOopDesc::resolved_checked_exceptions_impl(methodOop this_oop, TRAPS) {
  int length = this_oop->checked_exceptions_length();
  if (length == 0) {  // common case
    return objArrayHandle(THREAD, Universe::the_empty_class_klass_array());
  } else {
    methodHandle h_this(THREAD, this_oop);
    objArrayOop m_oop = oopFactory::new_objArray(SystemDictionary::Class_klass(), length, CHECK_(objArrayHandle()));
    objArrayHandle mirrors (THREAD, m_oop);
    for (int i = 0; i < length; i++) {
      CheckedExceptionElement* table = h_this->checked_exceptions_start(); // recompute on each iteration, not gc safe
      klassOop k = h_this->constants()->klass_at(table[i].class_cp_index, CHECK_(objArrayHandle()));
      assert(Klass::cast(k)->is_subclass_of(SystemDictionary::Throwable_klass()), "invalid exception class");
      mirrors->obj_at_put(i, Klass::cast(k)->java_mirror());
    }
    return mirrors;
  }
};


int methodOopDesc::line_number_from_bci(int bci) const {
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


bool methodOopDesc::is_klass_loaded_by_klass_index(int klass_index) const {
  if( _constants->tag_at(klass_index).is_unresolved_klass() ) {
    Thread *thread = Thread::current();
    symbolHandle klass_name(thread, _constants->klass_name_at(klass_index));
    Handle loader(thread, instanceKlass::cast(method_holder())->class_loader());
    Handle prot  (thread, Klass::cast(method_holder())->protection_domain());
    return SystemDictionary::find(klass_name, loader, prot, thread) != NULL;
  } else {
    return true;
  }
}


bool methodOopDesc::is_klass_loaded(int refinfo_index, bool must_be_resolved) const {
  int klass_index = _constants->klass_ref_index_at(refinfo_index);
  if (must_be_resolved) {
    // Make sure klass is resolved in constantpool.
    if (constants()->tag_at(klass_index).is_unresolved_klass()) return false;
  }
  return is_klass_loaded_by_klass_index(klass_index);
}


void methodOopDesc::set_native_function(address function, bool post_event_flag) {
  assert(function != NULL, "use clear_native_function to unregister natives");
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


bool methodOopDesc::has_native_function() const {
  address func = native_function();
  return (func != NULL && func != SharedRuntime::native_method_throw_unsatisfied_link_error_entry());
}


void methodOopDesc::clear_native_function() {
  set_native_function(
    SharedRuntime::native_method_throw_unsatisfied_link_error_entry(),
    !native_bind_event_is_interesting);
  clear_code();
}


void methodOopDesc::set_signature_handler(address handler) {
  address* signature_handler =  signature_handler_addr();
  *signature_handler = handler;
}


bool methodOopDesc::is_not_compilable(int comp_level) const {
  if (is_method_handle_invoke()) {
    // compilers must recognize this method specially, or not at all
    return true;
  }

  methodDataOop mdo = method_data();
  if (mdo != NULL
      && (uint)mdo->decompile_count() > (uint)PerMethodRecompilationCutoff) {
    // Since (uint)-1 is large, -1 really means 'no cutoff'.
    return true;
  }
#ifdef COMPILER2
  if (is_tier1_compile(comp_level)) {
    if (is_not_tier1_compilable()) {
      return true;
    }
  }
#endif // COMPILER2
  return (_invocation_counter.state() == InvocationCounter::wait_for_nothing)
          || (number_of_breakpoints() > 0);
}

// call this when compiler finds that this method is not compilable
void methodOopDesc::set_not_compilable(int comp_level) {
  if ((TraceDeoptimization || LogCompilation) && (xtty != NULL)) {
    ttyLocker ttyl;
    xtty->begin_elem("make_not_compilable thread='%d'", (int) os::current_thread_id());
    xtty->method(methodOop(this));
    xtty->stamp();
    xtty->end_elem();
  }
#ifdef COMPILER2
  if (is_tier1_compile(comp_level)) {
    set_not_tier1_compilable();
    return;
  }
#endif /* COMPILER2 */
  assert(comp_level == CompLevel_highest_tier, "unexpected compilation level");
  invocation_counter()->set_state(InvocationCounter::wait_for_nothing);
  backedge_counter()->set_state(InvocationCounter::wait_for_nothing);
}

// Revert to using the interpreter and clear out the nmethod
void methodOopDesc::clear_code() {

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
void methodOopDesc::unlink_method() {
  _code = NULL;
  _i2i_entry = NULL;
  _from_interpreted_entry = NULL;
  if (is_native()) {
    *native_function_addr() = NULL;
    set_signature_handler(NULL);
  }
  NOT_PRODUCT(set_compiled_invocation_count(0);)
  invocation_counter()->reset();
  backedge_counter()->reset();
  _adapter = NULL;
  _from_compiled_entry = NULL;
  assert(_method_data == NULL, "unexpected method data?");
  set_method_data(NULL);
  set_interpreter_throwout_count(0);
  set_interpreter_invocation_count(0);
  _highest_tier_compile = CompLevel_none;
}

// Called when the method_holder is getting linked. Setup entrypoints so the method
// is ready to be called from interpreter, compiler, and vtables.
void methodOopDesc::link_method(methodHandle h_method, TRAPS) {
  assert(_i2i_entry == NULL, "should only be called once");
  assert(_adapter == NULL, "init'd to NULL" );
  assert( _code == NULL, "nothing compiled yet" );

  // Setup interpreter entrypoint
  assert(this == h_method(), "wrong h_method()" );
  address entry = Interpreter::entry_for_method(h_method);
  assert(entry != NULL, "interpreter entry must be non-null");
  // Sets both _i2i_entry and _from_interpreted_entry
  set_interpreter_entry(entry);
  if (is_native() && !is_method_handle_invoke()) {
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

address methodOopDesc::make_adapters(methodHandle mh, TRAPS) {
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
address methodOopDesc::verified_code_entry() {
  debug_only(No_Safepoint_Verifier nsv;)
  nmethod *code = (nmethod *)OrderAccess::load_ptr_acquire(&_code);
  if (code == NULL && UseCodeCacheFlushing) {
    nmethod *saved_code = CodeCache::find_and_remove_saved_code(this);
    if (saved_code != NULL) {
      methodHandle method(this);
      assert( ! saved_code->is_osr_method(), "should not get here for osr" );
      set_code( method, saved_code );
    }
  }

  assert(_from_compiled_entry != NULL, "must be set");
  return _from_compiled_entry;
}

// Check that if an nmethod ref exists, it has a backlink to this or no backlink at all
// (could be racing a deopt).
// Not inline to avoid circular ref.
bool methodOopDesc::check_code() const {
  // cached in a register or local.  There's a race on the value of the field.
  nmethod *code = (nmethod *)OrderAccess::load_ptr_acquire(&_code);
  return code == NULL || (code->method() == NULL) || (code->method() == (methodOop)this && !code->is_osr_method());
}

// Install compiled code.  Instantly it can execute.
void methodOopDesc::set_code(methodHandle mh, nmethod *code) {
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
  if (comp_level > mh->highest_tier_compile()) {
    mh->set_highest_tier_compile(comp_level);
  }

  OrderAccess::storestore();
  mh->_from_compiled_entry = code->verified_entry_point();
  OrderAccess::storestore();
  // Instantly compiled code can execute.
  mh->_from_interpreted_entry = mh->get_i2c_entry();

}


bool methodOopDesc::is_overridden_in(klassOop k) const {
  instanceKlass* ik = instanceKlass::cast(k);

  if (ik->is_interface()) return false;

  // If method is an interface, we skip it - except if it
  // is a miranda method
  if (instanceKlass::cast(method_holder())->is_interface()) {
    // Check that method is not a miranda method
    if (ik->lookup_method(name(), signature()) == NULL) {
      // No implementation exist - so miranda method
      return false;
    }
    return true;
  }

  assert(ik->is_subclass_of(method_holder()), "should be subklass");
  assert(ik->vtable() != NULL, "vtable should exist");
  if (vtable_index() == nonvirtual_vtable_index) {
    return false;
  } else {
    methodOop vt_m = ik->method_at_vtable(vtable_index());
    return vt_m != methodOop(this);
  }
}


// give advice about whether this methodOop should be cached or not
bool methodOopDesc::should_not_be_cached() const {
  if (is_old()) {
    // This method has been redefined. It is either EMCP or obsolete
    // and we don't want to cache it because that would pin the method
    // down and prevent it from being collectible if and when it
    // finishes executing.
    return true;
  }

  if (mark()->should_not_be_cached()) {
    // It is either not safe or not a good idea to cache this
    // method at this time because of the state of the embedded
    // markOop. See markOop.cpp for the gory details.
    return true;
  }

  // caching this method should be just fine
  return false;
}

// Constant pool structure for invoke methods:
enum {
  _imcp_invoke_name = 1,        // utf8: 'invoke'
  _imcp_invoke_signature,       // utf8: (variable symbolOop)
  _imcp_method_type_value,      // string: (variable java/dyn/MethodType, sic)
  _imcp_limit
};

oop methodOopDesc::method_handle_type() const {
  if (!is_method_handle_invoke()) { assert(false, "caller resp."); return NULL; }
  oop mt = constants()->resolved_string_at(_imcp_method_type_value);
  assert(mt->klass() == SystemDictionary::MethodType_klass(), "");
  return mt;
}

jint* methodOopDesc::method_type_offsets_chain() {
  static jint pchase[] = { -1, -1, -1 };
  if (pchase[0] == -1) {
    jint step0 = in_bytes(constants_offset());
    jint step1 = (constantPoolOopDesc::header_size() + _imcp_method_type_value) * HeapWordSize;
    // do this in reverse to avoid races:
    OrderAccess::release_store(&pchase[1], step1);
    OrderAccess::release_store(&pchase[0], step0);
  }
  return pchase;
}

//------------------------------------------------------------------------------
// methodOopDesc::is_method_handle_adapter
//
// Tests if this method is an internal adapter frame from the
// MethodHandleCompiler.
bool methodOopDesc::is_method_handle_adapter() const {
  return ((name() == vmSymbols::invoke_name() &&
           method_holder() == SystemDictionary::MethodHandle_klass())
          ||
          method_holder() == SystemDictionary::InvokeDynamic_klass());
}

methodHandle methodOopDesc::make_invoke_method(KlassHandle holder,
                                               symbolHandle signature,
                                               Handle method_type, TRAPS) {
  methodHandle empty;

  assert(holder() == SystemDictionary::MethodHandle_klass(),
         "must be a JSR 292 magic type");

  if (TraceMethodHandles) {
    tty->print("Creating invoke method for ");
    signature->print_value();
    tty->cr();
  }

  constantPoolHandle cp;
  {
    constantPoolOop cp_oop = oopFactory::new_constantPool(_imcp_limit, IsSafeConc, CHECK_(empty));
    cp = constantPoolHandle(THREAD, cp_oop);
  }
  cp->symbol_at_put(_imcp_invoke_name,       vmSymbols::invoke_name());
  cp->symbol_at_put(_imcp_invoke_signature,  signature());
  cp->string_at_put(_imcp_method_type_value, vmSymbols::void_signature());
  cp->set_pool_holder(holder());

  // set up the fancy stuff:
  cp->pseudo_string_at_put(_imcp_method_type_value, method_type());
  methodHandle m;
  {
    int flags_bits = (JVM_MH_INVOKE_BITS | JVM_ACC_PUBLIC | JVM_ACC_FINAL);
    methodOop m_oop = oopFactory::new_method(0, accessFlags_from(flags_bits),
                                             0, 0, 0, IsSafeConc, CHECK_(empty));
    m = methodHandle(THREAD, m_oop);
  }
  m->set_constants(cp());
  m->set_name_index(_imcp_invoke_name);
  m->set_signature_index(_imcp_invoke_signature);
  assert(m->name() == vmSymbols::invoke_name(), "");
  assert(m->signature() == signature(), "");
#ifdef CC_INTERP
  ResultTypeFinder rtf(signature());
  m->set_result_index(rtf.type());
#endif
  m->compute_size_of_parameters(THREAD);
  m->set_exception_table(Universe::the_empty_int_array());

  // Finally, set up its entry points.
  assert(m->method_handle_type() == method_type(), "");
  assert(m->can_be_statically_bound(), "");
  m->set_vtable_index(methodOopDesc::nonvirtual_vtable_index);
  m->link_method(m, CHECK_(empty));

#ifdef ASSERT
  // Make sure the pointer chase works.
  address p = (address) m();
  for (jint* pchase = method_type_offsets_chain(); (*pchase) != -1; pchase++) {
    p = *(address*)(p + (*pchase));
  }
  assert((oop)p == method_type(), "pointer chase is correct");
#endif

  if (TraceMethodHandles && (Verbose || WizardMode))
    m->print_on(tty);

  return m;
}



methodHandle methodOopDesc:: clone_with_new_data(methodHandle m, u_char* new_code, int new_code_length,
                                                u_char* new_compressed_linenumber_table, int new_compressed_linenumber_size, TRAPS) {
  // Code below does not work for native methods - they should never get rewritten anyway
  assert(!m->is_native(), "cannot rewrite native methods");
  // Allocate new methodOop
  AccessFlags flags = m->access_flags();
  int checked_exceptions_len = m->checked_exceptions_length();
  int localvariable_len = m->localvariable_table_length();
  // Allocate newm_oop with the is_conc_safe parameter set
  // to IsUnsafeConc to indicate that newm_oop is not yet
  // safe for concurrent processing by a GC.
  methodOop newm_oop = oopFactory::new_method(new_code_length,
                                              flags,
                                              new_compressed_linenumber_size,
                                              localvariable_len,
                                              checked_exceptions_len,
                                              IsUnsafeConc,
                                              CHECK_(methodHandle()));
  methodHandle newm (THREAD, newm_oop);
  int new_method_size = newm->method_size();
  // Create a shallow copy of methodOopDesc part, but be careful to preserve the new constMethodOop
  constMethodOop newcm = newm->constMethod();
  int new_const_method_size = newm->constMethod()->object_size();

  memcpy(newm(), m(), sizeof(methodOopDesc));
  // Create shallow copy of constMethodOopDesc, but be careful to preserve the methodOop
  // is_conc_safe is set to false because that is the value of
  // is_conc_safe initialzied into newcm and the copy should
  // not overwrite that value.  During the window during which it is
  // tagged as unsafe, some extra work could be needed during precleaning
  // or concurrent marking but those phases will be correct.  Setting and
  // resetting is done in preference to a careful copying into newcm to
  // avoid having to know the precise layout of a constMethodOop.
  m->constMethod()->set_is_conc_safe(false);
  memcpy(newcm, m->constMethod(), sizeof(constMethodOopDesc));
  m->constMethod()->set_is_conc_safe(true);
  // Reset correct method/const method, method size, and parameter info
  newcm->set_method(newm());
  newm->set_constMethod(newcm);
  assert(newcm->method() == newm(), "check");
  newm->constMethod()->set_code_size(new_code_length);
  newm->constMethod()->set_constMethod_size(new_const_method_size);
  newm->set_method_size(new_method_size);
  assert(newm->code_size() == new_code_length, "check");
  assert(newm->checked_exceptions_length() == checked_exceptions_len, "check");
  assert(newm->localvariable_table_length() == localvariable_len, "check");
  // Copy new byte codes
  memcpy(newm->code_base(), new_code, new_code_length);
  // Copy line number table
  if (new_compressed_linenumber_size > 0) {
    memcpy(newm->compressed_linenumber_table(),
           new_compressed_linenumber_table,
           new_compressed_linenumber_size);
  }
  // Copy checked_exceptions
  if (checked_exceptions_len > 0) {
    memcpy(newm->checked_exceptions_start(),
           m->checked_exceptions_start(),
           checked_exceptions_len * sizeof(CheckedExceptionElement));
  }
  // Copy local variable number table
  if (localvariable_len > 0) {
    memcpy(newm->localvariable_table_start(),
           m->localvariable_table_start(),
           localvariable_len * sizeof(LocalVariableTableElement));
  }

  // Only set is_conc_safe to true when changes to newcm are
  // complete.
  newcm->set_is_conc_safe(true);
  return newm;
}

vmSymbols::SID methodOopDesc::klass_id_for_intrinsics(klassOop holder) {
  // if loader is not the default loader (i.e., != NULL), we can't know the intrinsics
  // because we are not loading from core libraries
  if (instanceKlass::cast(holder)->class_loader() != NULL)
    return vmSymbols::NO_SID;   // regardless of name, no intrinsics here

  // see if the klass name is well-known:
  symbolOop klass_name = instanceKlass::cast(holder)->name();
  return vmSymbols::find_sid(klass_name);
}

void methodOopDesc::init_intrinsic_id() {
  assert(_intrinsic_id == vmIntrinsics::_none, "do this just once");
  const uintptr_t max_id_uint = right_n_bits((int)(sizeof(_intrinsic_id) * BitsPerByte));
  assert((uintptr_t)vmIntrinsics::ID_LIMIT <= max_id_uint, "else fix size");

  // the klass name is well-known:
  vmSymbols::SID klass_id = klass_id_for_intrinsics(method_holder());
  assert(klass_id != vmSymbols::NO_SID, "caller responsibility");

  // ditto for method and signature:
  vmSymbols::SID  name_id = vmSymbols::find_sid(name());
  if (name_id  == vmSymbols::NO_SID)  return;
  vmSymbols::SID   sig_id = vmSymbols::find_sid(signature());
  if (sig_id   == vmSymbols::NO_SID)  return;
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
  }

  if (id != vmIntrinsics::_none) {
    // Set up its iid.  It is an alias method.
    set_intrinsic_id(id);
    return;
  }
}

// These two methods are static since a GC may move the methodOopDesc
bool methodOopDesc::load_signature_classes(methodHandle m, TRAPS) {
  bool sig_is_loaded = true;
  Handle class_loader(THREAD, instanceKlass::cast(m->method_holder())->class_loader());
  Handle protection_domain(THREAD, Klass::cast(m->method_holder())->protection_domain());
  symbolHandle signature(THREAD, m->signature());
  for(SignatureStream ss(signature); !ss.is_done(); ss.next()) {
    if (ss.is_object()) {
      symbolOop sym = ss.as_symbol(CHECK_(false));
      symbolHandle name (THREAD, sym);
      klassOop klass = SystemDictionary::resolve_or_null(name, class_loader,
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

bool methodOopDesc::has_unloaded_classes_in_signature(methodHandle m, TRAPS) {
  Handle class_loader(THREAD, instanceKlass::cast(m->method_holder())->class_loader());
  Handle protection_domain(THREAD, Klass::cast(m->method_holder())->protection_domain());
  symbolHandle signature(THREAD, m->signature());
  for(SignatureStream ss(signature); !ss.is_done(); ss.next()) {
    if (ss.type() == T_OBJECT) {
      symbolHandle name(THREAD, ss.as_symbol_or_null());
      if (name() == NULL) return true;
      klassOop klass = SystemDictionary::find(name, class_loader, protection_domain, THREAD);
      if (klass == NULL) return true;
    }
  }
  return false;
}

// Exposed so field engineers can debug VM
void methodOopDesc::print_short_name(outputStream* st) {
  ResourceMark rm;
#ifdef PRODUCT
  st->print(" %s::", method_holder()->klass_part()->external_name());
#else
  st->print(" %s::", method_holder()->klass_part()->internal_name());
#endif
  name()->print_symbol_on(st);
  if (WizardMode) signature()->print_symbol_on(st);
}


extern "C" {
  static int method_compare(methodOop* a, methodOop* b) {
    return (*a)->name()->fast_compare((*b)->name());
  }

  // Prevent qsort from reordering a previous valid sort by
  // considering the address of the methodOops if two methods
  // would otherwise compare as equal.  Required to preserve
  // optimal access order in the shared archive.  Slower than
  // method_compare, only used for shared archive creation.
  static int method_compare_idempotent(methodOop* a, methodOop* b) {
    int i = method_compare(a, b);
    if (i != 0) return i;
    return ( a < b ? -1 : (a == b ? 0 : 1));
  }

  typedef int (*compareFn)(const void*, const void*);
}


// This is only done during class loading, so it is OK to assume method_idnum matches the methods() array
static void reorder_based_on_method_index(objArrayOop methods,
                                          objArrayOop annotations,
                                          GrowableArray<oop>* temp_array) {
  if (annotations == NULL) {
    return;
  }

  int length = methods->length();
  int i;
  // Copy to temp array
  temp_array->clear();
  for (i = 0; i < length; i++) {
    temp_array->append(annotations->obj_at(i));
  }

  // Copy back using old method indices
  for (i = 0; i < length; i++) {
    methodOop m = (methodOop) methods->obj_at(i);
    annotations->obj_at_put(i, temp_array->at(m->method_idnum()));
  }
}


// This is only done during class loading, so it is OK to assume method_idnum matches the methods() array
void methodOopDesc::sort_methods(objArrayOop methods,
                                 objArrayOop methods_annotations,
                                 objArrayOop methods_parameter_annotations,
                                 objArrayOop methods_default_annotations,
                                 bool idempotent) {
  int length = methods->length();
  if (length > 1) {
    bool do_annotations = false;
    if (methods_annotations != NULL ||
        methods_parameter_annotations != NULL ||
        methods_default_annotations != NULL) {
      do_annotations = true;
    }
    if (do_annotations) {
      // Remember current method ordering so we can reorder annotations
      for (int i = 0; i < length; i++) {
        methodOop m = (methodOop) methods->obj_at(i);
        m->set_method_idnum(i);
      }
    }

    // Use a simple bubble sort for small number of methods since
    // qsort requires a functional pointer call for each comparison.
    if (UseCompressedOops || length < 8) {
      bool sorted = true;
      for (int i=length-1; i>0; i--) {
        for (int j=0; j<i; j++) {
          methodOop m1 = (methodOop)methods->obj_at(j);
          methodOop m2 = (methodOop)methods->obj_at(j+1);
          if ((uintptr_t)m1->name() > (uintptr_t)m2->name()) {
            methods->obj_at_put(j, m2);
            methods->obj_at_put(j+1, m1);
            sorted = false;
          }
        }
        if (sorted) break;
          sorted = true;
      }
    } else {
      // XXX This doesn't work for UseCompressedOops because the compare fn
      // will have to decode the methodOop anyway making it not much faster
      // than above.
      compareFn compare = (compareFn) (idempotent ? method_compare_idempotent : method_compare);
      qsort(methods->base(), length, heapOopSize, compare);
    }

    // Sort annotations if necessary
    assert(methods_annotations == NULL           || methods_annotations->length() == methods->length(), "");
    assert(methods_parameter_annotations == NULL || methods_parameter_annotations->length() == methods->length(), "");
    assert(methods_default_annotations == NULL   || methods_default_annotations->length() == methods->length(), "");
    if (do_annotations) {
      ResourceMark rm;
      // Allocate temporary storage
      GrowableArray<oop>* temp_array = new GrowableArray<oop>(length);
      reorder_based_on_method_index(methods, methods_annotations, temp_array);
      reorder_based_on_method_index(methods, methods_parameter_annotations, temp_array);
      reorder_based_on_method_index(methods, methods_default_annotations, temp_array);
    }

    // Reset method ordering
    for (int i = 0; i < length; i++) {
      methodOop m = (methodOop) methods->obj_at(i);
      m->set_method_idnum(i);
    }
  }
}


//-----------------------------------------------------------------------------------
// Non-product code

#ifndef PRODUCT
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
  SignatureTypePrinter(symbolHandle signature, outputStream* st) : SignatureTypeNames(signature) {
    _st = st;
    _use_separator = false;
  }

  void print_parameters()              { _use_separator = false; iterate_parameters(); }
  void print_returntype()              { _use_separator = false; iterate_returntype(); }
};


void methodOopDesc::print_name(outputStream* st) {
  Thread *thread = Thread::current();
  ResourceMark rm(thread);
  SignatureTypePrinter sig(signature(), st);
  st->print("%s ", is_static() ? "static" : "virtual");
  sig.print_returntype();
  st->print(" %s.", method_holder()->klass_part()->internal_name());
  name()->print_symbol_on(st);
  st->print("(");
  sig.print_parameters();
  st->print(")");
}


void methodOopDesc::print_codes_on(outputStream* st) const {
  print_codes_on(0, code_size(), st);
}

void methodOopDesc::print_codes_on(int from, int to, outputStream* st) const {
  Thread *thread = Thread::current();
  ResourceMark rm(thread);
  methodHandle mh (thread, (methodOop)this);
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

// See comment in methodOop.hpp which explains why this exists.
#if defined(_M_AMD64) && MSC_VER >= 1400
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


Bytecodes::Code methodOopDesc::orig_bytecode_at(int bci) {
  BreakpointInfo* bp = instanceKlass::cast(method_holder())->breakpoints();
  for (; bp != NULL; bp = bp->next()) {
    if (bp->match(this, bci)) {
      return bp->orig_bytecode();
    }
  }
  ShouldNotReachHere();
  return Bytecodes::_shouldnotreachhere;
}

void methodOopDesc::set_orig_bytecode_at(int bci, Bytecodes::Code code) {
  assert(code != Bytecodes::_breakpoint, "cannot patch breakpoints this way");
  BreakpointInfo* bp = instanceKlass::cast(method_holder())->breakpoints();
  for (; bp != NULL; bp = bp->next()) {
    if (bp->match(this, bci)) {
      bp->set_orig_bytecode(code);
      // and continue, in case there is more than one
    }
  }
}

void methodOopDesc::set_breakpoint(int bci) {
  instanceKlass* ik = instanceKlass::cast(method_holder());
  BreakpointInfo *bp = new BreakpointInfo(this, bci);
  bp->set_next(ik->breakpoints());
  ik->set_breakpoints(bp);
  // do this last:
  bp->set(this);
}

static void clear_matches(methodOop m, int bci) {
  instanceKlass* ik = instanceKlass::cast(m->method_holder());
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

void methodOopDesc::clear_breakpoint(int bci) {
  assert(bci >= 0, "");
  clear_matches(this, bci);
}

void methodOopDesc::clear_all_breakpoints() {
  clear_matches(this, -1);
}


BreakpointInfo::BreakpointInfo(methodOop m, int bci) {
  _bci = bci;
  _name_index = m->name_index();
  _signature_index = m->signature_index();
  _orig_bytecode = (Bytecodes::Code) *m->bcp_from(_bci);
  if (_orig_bytecode == Bytecodes::_breakpoint)
    _orig_bytecode = m->orig_bytecode_at(_bci);
  _next = NULL;
}

void BreakpointInfo::set(methodOop method) {
#ifdef ASSERT
  {
    Bytecodes::Code code = (Bytecodes::Code) *method->bcp_from(_bci);
    if (code == Bytecodes::_breakpoint)
      code = method->orig_bytecode_at(_bci);
    assert(orig_bytecode() == code, "original bytecode must be the same");
  }
#endif
  *method->bcp_from(_bci) = Bytecodes::_breakpoint;
  method->incr_number_of_breakpoints();
  SystemDictionary::notice_modification();
  {
    // Deoptimize all dependents on this method
    Thread *thread = Thread::current();
    HandleMark hm(thread);
    methodHandle mh(thread, method);
    Universe::flush_dependents_on_method(mh);
  }
}

void BreakpointInfo::clear(methodOop method) {
  *method->bcp_from(_bci) = orig_bytecode();
  assert(method->number_of_breakpoints() > 0, "must not go negative");
  method->decr_number_of_breakpoints();
}
