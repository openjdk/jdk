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

# include "incls/_precompiled.incl"
# include "incls/_instanceKlass.cpp.incl"

bool instanceKlass::should_be_initialized() const {
  return !is_initialized();
}

klassVtable* instanceKlass::vtable() const {
  return new klassVtable(as_klassOop(), start_of_vtable(), vtable_length() / vtableEntry::size());
}

klassItable* instanceKlass::itable() const {
  return new klassItable(as_klassOop());
}

void instanceKlass::eager_initialize(Thread *thread) {
  if (!EagerInitialization) return;

  if (this->is_not_initialized()) {
    // abort if the the class has a class initializer
    if (this->class_initializer() != NULL) return;

    // abort if it is java.lang.Object (initialization is handled in genesis)
    klassOop super = this->super();
    if (super == NULL) return;

    // abort if the super class should be initialized
    if (!instanceKlass::cast(super)->is_initialized()) return;

    // call body to expose the this pointer
    instanceKlassHandle this_oop(thread, this->as_klassOop());
    eager_initialize_impl(this_oop);
  }
}


void instanceKlass::eager_initialize_impl(instanceKlassHandle this_oop) {
  EXCEPTION_MARK;
  ObjectLocker ol(this_oop, THREAD);

  // abort if someone beat us to the initialization
  if (!this_oop->is_not_initialized()) return;  // note: not equivalent to is_initialized()

  ClassState old_state = this_oop->_init_state;
  link_class_impl(this_oop, true, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    CLEAR_PENDING_EXCEPTION;
    // Abort if linking the class throws an exception.

    // Use a test to avoid redundantly resetting the state if there's
    // no change.  Set_init_state() asserts that state changes make
    // progress, whereas here we might just be spinning in place.
    if( old_state != this_oop->_init_state )
      this_oop->set_init_state (old_state);
  } else {
    // linking successfull, mark class as initialized
    this_oop->set_init_state (fully_initialized);
    // trace
    if (TraceClassInitialization) {
      ResourceMark rm(THREAD);
      tty->print_cr("[Initialized %s without side effects]", this_oop->external_name());
    }
  }
}


// See "The Virtual Machine Specification" section 2.16.5 for a detailed explanation of the class initialization
// process. The step comments refers to the procedure described in that section.
// Note: implementation moved to static method to expose the this pointer.
void instanceKlass::initialize(TRAPS) {
  if (this->should_be_initialized()) {
    HandleMark hm(THREAD);
    instanceKlassHandle this_oop(THREAD, this->as_klassOop());
    initialize_impl(this_oop, CHECK);
    // Note: at this point the class may be initialized
    //       OR it may be in the state of being initialized
    //       in case of recursive initialization!
  } else {
    assert(is_initialized(), "sanity check");
  }
}


bool instanceKlass::verify_code(
    instanceKlassHandle this_oop, bool throw_verifyerror, TRAPS) {
  // 1) Verify the bytecodes
  Verifier::Mode mode =
    throw_verifyerror ? Verifier::ThrowException : Verifier::NoException;
  return Verifier::verify(this_oop, mode, this_oop->should_verify_class(), CHECK_false);
}


// Used exclusively by the shared spaces dump mechanism to prevent
// classes mapped into the shared regions in new VMs from appearing linked.

void instanceKlass::unlink_class() {
  assert(is_linked(), "must be linked");
  _init_state = loaded;
}

void instanceKlass::link_class(TRAPS) {
  assert(is_loaded(), "must be loaded");
  if (!is_linked()) {
    instanceKlassHandle this_oop(THREAD, this->as_klassOop());
    link_class_impl(this_oop, true, CHECK);
  }
}

// Called to verify that a class can link during initialization, without
// throwing a VerifyError.
bool instanceKlass::link_class_or_fail(TRAPS) {
  assert(is_loaded(), "must be loaded");
  if (!is_linked()) {
    instanceKlassHandle this_oop(THREAD, this->as_klassOop());
    link_class_impl(this_oop, false, CHECK_false);
  }
  return is_linked();
}

bool instanceKlass::link_class_impl(
    instanceKlassHandle this_oop, bool throw_verifyerror, TRAPS) {
  // check for error state
  if (this_oop->is_in_error_state()) {
    ResourceMark rm(THREAD);
    THROW_MSG_(vmSymbols::java_lang_NoClassDefFoundError(),
               this_oop->external_name(), false);
  }
  // return if already verified
  if (this_oop->is_linked()) {
    return true;
  }

  // Timing
  // timer handles recursion
  assert(THREAD->is_Java_thread(), "non-JavaThread in link_class_impl");
  JavaThread* jt = (JavaThread*)THREAD;

  // link super class before linking this class
  instanceKlassHandle super(THREAD, this_oop->super());
  if (super.not_null()) {
    if (super->is_interface()) {  // check if super class is an interface
      ResourceMark rm(THREAD);
      Exceptions::fthrow(
        THREAD_AND_LOCATION,
        vmSymbolHandles::java_lang_IncompatibleClassChangeError(),
        "class %s has interface %s as super class",
        this_oop->external_name(),
        super->external_name()
      );
      return false;
    }

    link_class_impl(super, throw_verifyerror, CHECK_false);
  }

  // link all interfaces implemented by this class before linking this class
  objArrayHandle interfaces (THREAD, this_oop->local_interfaces());
  int num_interfaces = interfaces->length();
  for (int index = 0; index < num_interfaces; index++) {
    HandleMark hm(THREAD);
    instanceKlassHandle ih(THREAD, klassOop(interfaces->obj_at(index)));
    link_class_impl(ih, throw_verifyerror, CHECK_false);
  }

  // in case the class is linked in the process of linking its superclasses
  if (this_oop->is_linked()) {
    return true;
  }

  // trace only the link time for this klass that includes
  // the verification time
  PerfClassTraceTime vmtimer(ClassLoader::perf_class_link_time(),
                             ClassLoader::perf_class_link_selftime(),
                             ClassLoader::perf_classes_linked(),
                             jt->get_thread_stat()->perf_recursion_counts_addr(),
                             jt->get_thread_stat()->perf_timers_addr(),
                             PerfClassTraceTime::CLASS_LINK);

  // verification & rewriting
  {
    ObjectLocker ol(this_oop, THREAD);
    // rewritten will have been set if loader constraint error found
    // on an earlier link attempt
    // don't verify or rewrite if already rewritten
    if (!this_oop->is_linked()) {
      if (!this_oop->is_rewritten()) {
        {
          // Timer includes any side effects of class verification (resolution,
          // etc), but not recursive entry into verify_code().
          PerfClassTraceTime timer(ClassLoader::perf_class_verify_time(),
                                   ClassLoader::perf_class_verify_selftime(),
                                   ClassLoader::perf_classes_verified(),
                                   jt->get_thread_stat()->perf_recursion_counts_addr(),
                                   jt->get_thread_stat()->perf_timers_addr(),
                                   PerfClassTraceTime::CLASS_VERIFY);
          bool verify_ok = verify_code(this_oop, throw_verifyerror, THREAD);
          if (!verify_ok) {
            return false;
          }
        }

        // Just in case a side-effect of verify linked this class already
        // (which can sometimes happen since the verifier loads classes
        // using custom class loaders, which are free to initialize things)
        if (this_oop->is_linked()) {
          return true;
        }

        // also sets rewritten
        this_oop->rewrite_class(CHECK_false);
      }

      // Initialize the vtable and interface table after
      // methods have been rewritten since rewrite may
      // fabricate new methodOops.
      // also does loader constraint checking
      if (!this_oop()->is_shared()) {
        ResourceMark rm(THREAD);
        this_oop->vtable()->initialize_vtable(true, CHECK_false);
        this_oop->itable()->initialize_itable(true, CHECK_false);
      }
#ifdef ASSERT
      else {
        ResourceMark rm(THREAD);
        this_oop->vtable()->verify(tty, true);
        // In case itable verification is ever added.
        // this_oop->itable()->verify(tty, true);
      }
#endif
      this_oop->set_init_state(linked);
      if (JvmtiExport::should_post_class_prepare()) {
        Thread *thread = THREAD;
        assert(thread->is_Java_thread(), "thread->is_Java_thread()");
        JvmtiExport::post_class_prepare((JavaThread *) thread, this_oop());
      }
    }
  }
  return true;
}


// Rewrite the byte codes of all of the methods of a class.
// Three cases:
//    During the link of a newly loaded class.
//    During the preloading of classes to be written to the shared spaces.
//      - Rewrite the methods and update the method entry points.
//
//    During the link of a class in the shared spaces.
//      - The methods were already rewritten, update the metho entry points.
//
// The rewriter must be called exactly once. Rewriting must happen after
// verification but before the first method of the class is executed.

void instanceKlass::rewrite_class(TRAPS) {
  assert(is_loaded(), "must be loaded");
  instanceKlassHandle this_oop(THREAD, this->as_klassOop());
  if (this_oop->is_rewritten()) {
    assert(this_oop()->is_shared(), "rewriting an unshared class?");
    return;
  }
  Rewriter::rewrite(this_oop, CHECK); // No exception can happen here
  this_oop->set_rewritten();
}


void instanceKlass::initialize_impl(instanceKlassHandle this_oop, TRAPS) {
  // Make sure klass is linked (verified) before initialization
  // A class could already be verified, since it has been reflected upon.
  this_oop->link_class(CHECK);

  // refer to the JVM book page 47 for description of steps
  // Step 1
  { ObjectLocker ol(this_oop, THREAD);

    Thread *self = THREAD; // it's passed the current thread

    // Step 2
    // If we were to use wait() instead of waitInterruptibly() then
    // we might end up throwing IE from link/symbol resolution sites
    // that aren't expected to throw.  This would wreak havoc.  See 6320309.
    while(this_oop->is_being_initialized() && !this_oop->is_reentrant_initialization(self)) {
      ol.waitUninterruptibly(CHECK);
    }

    // Step 3
    if (this_oop->is_being_initialized() && this_oop->is_reentrant_initialization(self))
      return;

    // Step 4
    if (this_oop->is_initialized())
      return;

    // Step 5
    if (this_oop->is_in_error_state()) {
      ResourceMark rm(THREAD);
      const char* desc = "Could not initialize class ";
      const char* className = this_oop->external_name();
      size_t msglen = strlen(desc) + strlen(className) + 1;
      char* message = NEW_C_HEAP_ARRAY(char, msglen);
      if (NULL == message) {
        // Out of memory: can't create detailed error message
        THROW_MSG(vmSymbols::java_lang_NoClassDefFoundError(), className);
      } else {
        jio_snprintf(message, msglen, "%s%s", desc, className);
        THROW_MSG(vmSymbols::java_lang_NoClassDefFoundError(), message);
      }
    }

    // Step 6
    this_oop->set_init_state(being_initialized);
    this_oop->set_init_thread(self);
  }

  // Step 7
  klassOop super_klass = this_oop->super();
  if (super_klass != NULL && !this_oop->is_interface() && Klass::cast(super_klass)->should_be_initialized()) {
    Klass::cast(super_klass)->initialize(THREAD);

    if (HAS_PENDING_EXCEPTION) {
      Handle e(THREAD, PENDING_EXCEPTION);
      CLEAR_PENDING_EXCEPTION;
      {
        EXCEPTION_MARK;
        this_oop->set_initialization_state_and_notify(initialization_error, THREAD); // Locks object, set state, and notify all waiting threads
        CLEAR_PENDING_EXCEPTION;   // ignore any exception thrown, superclass initialization error is thrown below
      }
      THROW_OOP(e());
    }
  }

  // Step 8
  {
    assert(THREAD->is_Java_thread(), "non-JavaThread in initialize_impl");
    JavaThread* jt = (JavaThread*)THREAD;
    // Timer includes any side effects of class initialization (resolution,
    // etc), but not recursive entry into call_class_initializer().
    PerfClassTraceTime timer(ClassLoader::perf_class_init_time(),
                             ClassLoader::perf_class_init_selftime(),
                             ClassLoader::perf_classes_inited(),
                             jt->get_thread_stat()->perf_recursion_counts_addr(),
                             jt->get_thread_stat()->perf_timers_addr(),
                             PerfClassTraceTime::CLASS_CLINIT);
    this_oop->call_class_initializer(THREAD);
  }

  // Step 9
  if (!HAS_PENDING_EXCEPTION) {
    this_oop->set_initialization_state_and_notify(fully_initialized, CHECK);
    { ResourceMark rm(THREAD);
      debug_only(this_oop->vtable()->verify(tty, true);)
    }
  }
  else {
    // Step 10 and 11
    Handle e(THREAD, PENDING_EXCEPTION);
    CLEAR_PENDING_EXCEPTION;
    {
      EXCEPTION_MARK;
      this_oop->set_initialization_state_and_notify(initialization_error, THREAD);
      CLEAR_PENDING_EXCEPTION;   // ignore any exception thrown, class initialization error is thrown below
    }
    if (e->is_a(SystemDictionary::Error_klass())) {
      THROW_OOP(e());
    } else {
      JavaCallArguments args(e);
      THROW_ARG(vmSymbolHandles::java_lang_ExceptionInInitializerError(),
                vmSymbolHandles::throwable_void_signature(),
                &args);
    }
  }
}


// Note: implementation moved to static method to expose the this pointer.
void instanceKlass::set_initialization_state_and_notify(ClassState state, TRAPS) {
  instanceKlassHandle kh(THREAD, this->as_klassOop());
  set_initialization_state_and_notify_impl(kh, state, CHECK);
}

void instanceKlass::set_initialization_state_and_notify_impl(instanceKlassHandle this_oop, ClassState state, TRAPS) {
  ObjectLocker ol(this_oop, THREAD);
  this_oop->set_init_state(state);
  ol.notify_all(CHECK);
}

void instanceKlass::add_implementor(klassOop k) {
  assert(Compile_lock->owned_by_self(), "");
  // Filter out my subinterfaces.
  // (Note: Interfaces are never on the subklass list.)
  if (instanceKlass::cast(k)->is_interface()) return;

  // Filter out subclasses whose supers already implement me.
  // (Note: CHA must walk subclasses of direct implementors
  // in order to locate indirect implementors.)
  klassOop sk = instanceKlass::cast(k)->super();
  if (sk != NULL && instanceKlass::cast(sk)->implements_interface(as_klassOop()))
    // We only need to check one immediate superclass, since the
    // implements_interface query looks at transitive_interfaces.
    // Any supers of the super have the same (or fewer) transitive_interfaces.
    return;

  // Update number of implementors
  int i = _nof_implementors++;

  // Record this implementor, if there are not too many already
  if (i < implementors_limit) {
    assert(_implementors[i] == NULL, "should be exactly one implementor");
    oop_store_without_check((oop*)&_implementors[i], k);
  } else if (i == implementors_limit) {
    // clear out the list on first overflow
    for (int i2 = 0; i2 < implementors_limit; i2++)
      oop_store_without_check((oop*)&_implementors[i2], NULL);
  }

  // The implementor also implements the transitive_interfaces
  for (int index = 0; index < local_interfaces()->length(); index++) {
    instanceKlass::cast(klassOop(local_interfaces()->obj_at(index)))->add_implementor(k);
  }
}

void instanceKlass::init_implementor() {
  for (int i = 0; i < implementors_limit; i++)
    oop_store_without_check((oop*)&_implementors[i], NULL);
  _nof_implementors = 0;
}


void instanceKlass::process_interfaces(Thread *thread) {
  // link this class into the implementors list of every interface it implements
  KlassHandle this_as_oop (thread, this->as_klassOop());
  for (int i = local_interfaces()->length() - 1; i >= 0; i--) {
    assert(local_interfaces()->obj_at(i)->is_klass(), "must be a klass");
    instanceKlass* interf = instanceKlass::cast(klassOop(local_interfaces()->obj_at(i)));
    assert(interf->is_interface(), "expected interface");
    interf->add_implementor(this_as_oop());
  }
}

bool instanceKlass::can_be_primary_super_slow() const {
  if (is_interface())
    return false;
  else
    return Klass::can_be_primary_super_slow();
}

objArrayOop instanceKlass::compute_secondary_supers(int num_extra_slots, TRAPS) {
  // The secondaries are the implemented interfaces.
  instanceKlass* ik = instanceKlass::cast(as_klassOop());
  objArrayHandle interfaces (THREAD, ik->transitive_interfaces());
  int num_secondaries = num_extra_slots + interfaces->length();
  if (num_secondaries == 0) {
    return Universe::the_empty_system_obj_array();
  } else if (num_extra_slots == 0) {
    return interfaces();
  } else {
    // a mix of both
    objArrayOop secondaries = oopFactory::new_system_objArray(num_secondaries, CHECK_NULL);
    for (int i = 0; i < interfaces->length(); i++) {
      secondaries->obj_at_put(num_extra_slots+i, interfaces->obj_at(i));
    }
    return secondaries;
  }
}

bool instanceKlass::compute_is_subtype_of(klassOop k) {
  if (Klass::cast(k)->is_interface()) {
    return implements_interface(k);
  } else {
    return Klass::compute_is_subtype_of(k);
  }
}

bool instanceKlass::implements_interface(klassOop k) const {
  if (as_klassOop() == k) return true;
  assert(Klass::cast(k)->is_interface(), "should be an interface class");
  for (int i = 0; i < transitive_interfaces()->length(); i++) {
    if (transitive_interfaces()->obj_at(i) == k) {
      return true;
    }
  }
  return false;
}

objArrayOop instanceKlass::allocate_objArray(int n, int length, TRAPS) {
  if (length < 0) THROW_0(vmSymbols::java_lang_NegativeArraySizeException());
  if (length > arrayOopDesc::max_array_length(T_OBJECT)) {
    report_java_out_of_memory("Requested array size exceeds VM limit");
    THROW_OOP_0(Universe::out_of_memory_error_array_size());
  }
  int size = objArrayOopDesc::object_size(length);
  klassOop ak = array_klass(n, CHECK_NULL);
  KlassHandle h_ak (THREAD, ak);
  objArrayOop o =
    (objArrayOop)CollectedHeap::array_allocate(h_ak, size, length, CHECK_NULL);
  return o;
}

instanceOop instanceKlass::register_finalizer(instanceOop i, TRAPS) {
  if (TraceFinalizerRegistration) {
    tty->print("Registered ");
    i->print_value_on(tty);
    tty->print_cr(" (" INTPTR_FORMAT ") as finalizable", (address)i);
  }
  instanceHandle h_i(THREAD, i);
  // Pass the handle as argument, JavaCalls::call expects oop as jobjects
  JavaValue result(T_VOID);
  JavaCallArguments args(h_i);
  methodHandle mh (THREAD, Universe::finalizer_register_method());
  JavaCalls::call(&result, mh, &args, CHECK_NULL);
  return h_i();
}

instanceOop instanceKlass::allocate_instance(TRAPS) {
  bool has_finalizer_flag = has_finalizer(); // Query before possible GC
  int size = size_helper();  // Query before forming handle.

  KlassHandle h_k(THREAD, as_klassOop());

  instanceOop i;

  i = (instanceOop)CollectedHeap::obj_allocate(h_k, size, CHECK_NULL);
  if (has_finalizer_flag && !RegisterFinalizersAtInit) {
    i = register_finalizer(i, CHECK_NULL);
  }
  return i;
}

instanceOop instanceKlass::allocate_permanent_instance(TRAPS) {
  // Finalizer registration occurs in the Object.<init> constructor
  // and constructors normally aren't run when allocating perm
  // instances so simply disallow finalizable perm objects.  This can
  // be relaxed if a need for it is found.
  assert(!has_finalizer(), "perm objects not allowed to have finalizers");
  int size = size_helper();  // Query before forming handle.
  KlassHandle h_k(THREAD, as_klassOop());
  instanceOop i = (instanceOop)
    CollectedHeap::permanent_obj_allocate(h_k, size, CHECK_NULL);
  return i;
}

void instanceKlass::check_valid_for_instantiation(bool throwError, TRAPS) {
  if (is_interface() || is_abstract()) {
    ResourceMark rm(THREAD);
    THROW_MSG(throwError ? vmSymbols::java_lang_InstantiationError()
              : vmSymbols::java_lang_InstantiationException(), external_name());
  }
  if (as_klassOop() == SystemDictionary::Class_klass()) {
    ResourceMark rm(THREAD);
    THROW_MSG(throwError ? vmSymbols::java_lang_IllegalAccessError()
              : vmSymbols::java_lang_IllegalAccessException(), external_name());
  }
}

klassOop instanceKlass::array_klass_impl(bool or_null, int n, TRAPS) {
  instanceKlassHandle this_oop(THREAD, as_klassOop());
  return array_klass_impl(this_oop, or_null, n, THREAD);
}

klassOop instanceKlass::array_klass_impl(instanceKlassHandle this_oop, bool or_null, int n, TRAPS) {
  if (this_oop->array_klasses() == NULL) {
    if (or_null) return NULL;

    ResourceMark rm;
    JavaThread *jt = (JavaThread *)THREAD;
    {
      // Atomic creation of array_klasses
      MutexLocker mc(Compile_lock, THREAD);   // for vtables
      MutexLocker ma(MultiArray_lock, THREAD);

      // Check if update has already taken place
      if (this_oop->array_klasses() == NULL) {
        objArrayKlassKlass* oakk =
          (objArrayKlassKlass*)Universe::objArrayKlassKlassObj()->klass_part();

        klassOop  k = oakk->allocate_objArray_klass(1, this_oop, CHECK_NULL);
        this_oop->set_array_klasses(k);
      }
    }
  }
  // _this will always be set at this point
  objArrayKlass* oak = (objArrayKlass*)this_oop->array_klasses()->klass_part();
  if (or_null) {
    return oak->array_klass_or_null(n);
  }
  return oak->array_klass(n, CHECK_NULL);
}

klassOop instanceKlass::array_klass_impl(bool or_null, TRAPS) {
  return array_klass_impl(or_null, 1, THREAD);
}

void instanceKlass::call_class_initializer(TRAPS) {
  instanceKlassHandle ik (THREAD, as_klassOop());
  call_class_initializer_impl(ik, THREAD);
}

static int call_class_initializer_impl_counter = 0;   // for debugging

methodOop instanceKlass::class_initializer() {
  return find_method(vmSymbols::class_initializer_name(), vmSymbols::void_method_signature());
}

void instanceKlass::call_class_initializer_impl(instanceKlassHandle this_oop, TRAPS) {
  methodHandle h_method(THREAD, this_oop->class_initializer());
  assert(!this_oop->is_initialized(), "we cannot initialize twice");
  if (TraceClassInitialization) {
    tty->print("%d Initializing ", call_class_initializer_impl_counter++);
    this_oop->name()->print_value();
    tty->print_cr("%s (" INTPTR_FORMAT ")", h_method() == NULL ? "(no method)" : "", (address)this_oop());
  }
  if (h_method() != NULL) {
    JavaCallArguments args; // No arguments
    JavaValue result(T_VOID);
    JavaCalls::call(&result, h_method, &args, CHECK); // Static call (no args)
  }
}


void instanceKlass::mask_for(methodHandle method, int bci,
  InterpreterOopMap* entry_for) {
  // Dirty read, then double-check under a lock.
  if (_oop_map_cache == NULL) {
    // Otherwise, allocate a new one.
    MutexLocker x(OopMapCacheAlloc_lock);
    // First time use. Allocate a cache in C heap
    if (_oop_map_cache == NULL) {
      _oop_map_cache = new OopMapCache();
    }
  }
  // _oop_map_cache is constant after init; lookup below does is own locking.
  _oop_map_cache->lookup(method, bci, entry_for);
}


bool instanceKlass::find_local_field(symbolOop name, symbolOop sig, fieldDescriptor* fd) const {
  const int n = fields()->length();
  for (int i = 0; i < n; i += next_offset ) {
    int name_index = fields()->ushort_at(i + name_index_offset);
    int sig_index  = fields()->ushort_at(i + signature_index_offset);
    symbolOop f_name = constants()->symbol_at(name_index);
    symbolOop f_sig  = constants()->symbol_at(sig_index);
    if (f_name == name && f_sig == sig) {
      fd->initialize(as_klassOop(), i);
      return true;
    }
  }
  return false;
}


void instanceKlass::field_names_and_sigs_iterate(OopClosure* closure) {
  const int n = fields()->length();
  for (int i = 0; i < n; i += next_offset ) {
    int name_index = fields()->ushort_at(i + name_index_offset);
    symbolOop name = constants()->symbol_at(name_index);
    closure->do_oop((oop*)&name);

    int sig_index  = fields()->ushort_at(i + signature_index_offset);
    symbolOop sig = constants()->symbol_at(sig_index);
    closure->do_oop((oop*)&sig);
  }
}


klassOop instanceKlass::find_interface_field(symbolOop name, symbolOop sig, fieldDescriptor* fd) const {
  const int n = local_interfaces()->length();
  for (int i = 0; i < n; i++) {
    klassOop intf1 = klassOop(local_interfaces()->obj_at(i));
    assert(Klass::cast(intf1)->is_interface(), "just checking type");
    // search for field in current interface
    if (instanceKlass::cast(intf1)->find_local_field(name, sig, fd)) {
      assert(fd->is_static(), "interface field must be static");
      return intf1;
    }
    // search for field in direct superinterfaces
    klassOop intf2 = instanceKlass::cast(intf1)->find_interface_field(name, sig, fd);
    if (intf2 != NULL) return intf2;
  }
  // otherwise field lookup fails
  return NULL;
}


klassOop instanceKlass::find_field(symbolOop name, symbolOop sig, fieldDescriptor* fd) const {
  // search order according to newest JVM spec (5.4.3.2, p.167).
  // 1) search for field in current klass
  if (find_local_field(name, sig, fd)) {
    return as_klassOop();
  }
  // 2) search for field recursively in direct superinterfaces
  { klassOop intf = find_interface_field(name, sig, fd);
    if (intf != NULL) return intf;
  }
  // 3) apply field lookup recursively if superclass exists
  { klassOop supr = super();
    if (supr != NULL) return instanceKlass::cast(supr)->find_field(name, sig, fd);
  }
  // 4) otherwise field lookup fails
  return NULL;
}


klassOop instanceKlass::find_field(symbolOop name, symbolOop sig, bool is_static, fieldDescriptor* fd) const {
  // search order according to newest JVM spec (5.4.3.2, p.167).
  // 1) search for field in current klass
  if (find_local_field(name, sig, fd)) {
    if (fd->is_static() == is_static) return as_klassOop();
  }
  // 2) search for field recursively in direct superinterfaces
  if (is_static) {
    klassOop intf = find_interface_field(name, sig, fd);
    if (intf != NULL) return intf;
  }
  // 3) apply field lookup recursively if superclass exists
  { klassOop supr = super();
    if (supr != NULL) return instanceKlass::cast(supr)->find_field(name, sig, is_static, fd);
  }
  // 4) otherwise field lookup fails
  return NULL;
}


bool instanceKlass::find_local_field_from_offset(int offset, bool is_static, fieldDescriptor* fd) const {
  int length = fields()->length();
  for (int i = 0; i < length; i += next_offset) {
    if (offset_from_fields( i ) == offset) {
      fd->initialize(as_klassOop(), i);
      if (fd->is_static() == is_static) return true;
    }
  }
  return false;
}


bool instanceKlass::find_field_from_offset(int offset, bool is_static, fieldDescriptor* fd) const {
  klassOop klass = as_klassOop();
  while (klass != NULL) {
    if (instanceKlass::cast(klass)->find_local_field_from_offset(offset, is_static, fd)) {
      return true;
    }
    klass = Klass::cast(klass)->super();
  }
  return false;
}


void instanceKlass::methods_do(void f(methodOop method)) {
  int len = methods()->length();
  for (int index = 0; index < len; index++) {
    methodOop m = methodOop(methods()->obj_at(index));
    assert(m->is_method(), "must be method");
    f(m);
  }
}

void instanceKlass::do_local_static_fields(FieldClosure* cl) {
  fieldDescriptor fd;
  int length = fields()->length();
  for (int i = 0; i < length; i += next_offset) {
    fd.initialize(as_klassOop(), i);
    if (fd.is_static()) cl->do_field(&fd);
  }
}


void instanceKlass::do_local_static_fields(void f(fieldDescriptor*, TRAPS), TRAPS) {
  instanceKlassHandle h_this(THREAD, as_klassOop());
  do_local_static_fields_impl(h_this, f, CHECK);
}


void instanceKlass::do_local_static_fields_impl(instanceKlassHandle this_oop, void f(fieldDescriptor* fd, TRAPS), TRAPS) {
  fieldDescriptor fd;
  int length = this_oop->fields()->length();
  for (int i = 0; i < length; i += next_offset) {
    fd.initialize(this_oop(), i);
    if (fd.is_static()) { f(&fd, CHECK); } // Do NOT remove {}! (CHECK macro expands into several statements)
  }
}


static int compare_fields_by_offset(int* a, int* b) {
  return a[0] - b[0];
}

void instanceKlass::do_nonstatic_fields(FieldClosure* cl) {
  instanceKlass* super = superklass();
  if (super != NULL) {
    super->do_nonstatic_fields(cl);
  }
  fieldDescriptor fd;
  int length = fields()->length();
  // In DebugInfo nonstatic fields are sorted by offset.
  int* fields_sorted = NEW_C_HEAP_ARRAY(int, 2*(length+1));
  int j = 0;
  for (int i = 0; i < length; i += next_offset) {
    fd.initialize(as_klassOop(), i);
    if (!fd.is_static()) {
      fields_sorted[j + 0] = fd.offset();
      fields_sorted[j + 1] = i;
      j += 2;
    }
  }
  if (j > 0) {
    length = j;
    // _sort_Fn is defined in growableArray.hpp.
    qsort(fields_sorted, length/2, 2*sizeof(int), (_sort_Fn)compare_fields_by_offset);
    for (int i = 0; i < length; i += 2) {
      fd.initialize(as_klassOop(), fields_sorted[i + 1]);
      assert(!fd.is_static() && fd.offset() == fields_sorted[i], "only nonstatic fields");
      cl->do_field(&fd);
    }
  }
  FREE_C_HEAP_ARRAY(int, fields_sorted);
}


void instanceKlass::array_klasses_do(void f(klassOop k)) {
  if (array_klasses() != NULL)
    arrayKlass::cast(array_klasses())->array_klasses_do(f);
}


void instanceKlass::with_array_klasses_do(void f(klassOop k)) {
  f(as_klassOop());
  array_klasses_do(f);
}

#ifdef ASSERT
static int linear_search(objArrayOop methods, symbolOop name, symbolOop signature) {
  int len = methods->length();
  for (int index = 0; index < len; index++) {
    methodOop m = (methodOop)(methods->obj_at(index));
    assert(m->is_method(), "must be method");
    if (m->signature() == signature && m->name() == name) {
       return index;
    }
  }
  return -1;
}
#endif

methodOop instanceKlass::find_method(symbolOop name, symbolOop signature) const {
  return instanceKlass::find_method(methods(), name, signature);
}

methodOop instanceKlass::find_method(objArrayOop methods, symbolOop name, symbolOop signature) {
  int len = methods->length();
  // methods are sorted, so do binary search
  int l = 0;
  int h = len - 1;
  while (l <= h) {
    int mid = (l + h) >> 1;
    methodOop m = (methodOop)methods->obj_at(mid);
    assert(m->is_method(), "must be method");
    int res = m->name()->fast_compare(name);
    if (res == 0) {
      // found matching name; do linear search to find matching signature
      // first, quick check for common case
      if (m->signature() == signature) return m;
      // search downwards through overloaded methods
      int i;
      for (i = mid - 1; i >= l; i--) {
        methodOop m = (methodOop)methods->obj_at(i);
        assert(m->is_method(), "must be method");
        if (m->name() != name) break;
        if (m->signature() == signature) return m;
      }
      // search upwards
      for (i = mid + 1; i <= h; i++) {
        methodOop m = (methodOop)methods->obj_at(i);
        assert(m->is_method(), "must be method");
        if (m->name() != name) break;
        if (m->signature() == signature) return m;
      }
      // not found
#ifdef ASSERT
      int index = linear_search(methods, name, signature);
      if (index != -1) fatal1("binary search bug: should have found entry %d", index);
#endif
      return NULL;
    } else if (res < 0) {
      l = mid + 1;
    } else {
      h = mid - 1;
    }
  }
#ifdef ASSERT
  int index = linear_search(methods, name, signature);
  if (index != -1) fatal1("binary search bug: should have found entry %d", index);
#endif
  return NULL;
}

methodOop instanceKlass::uncached_lookup_method(symbolOop name, symbolOop signature) const {
  klassOop klass = as_klassOop();
  while (klass != NULL) {
    methodOop method = instanceKlass::cast(klass)->find_method(name, signature);
    if (method != NULL) return method;
    klass = instanceKlass::cast(klass)->super();
  }
  return NULL;
}

// lookup a method in all the interfaces that this class implements
methodOop instanceKlass::lookup_method_in_all_interfaces(symbolOop name,
                                                         symbolOop signature) const {
  objArrayOop all_ifs = instanceKlass::cast(as_klassOop())->transitive_interfaces();
  int num_ifs = all_ifs->length();
  instanceKlass *ik = NULL;
  for (int i = 0; i < num_ifs; i++) {
    ik = instanceKlass::cast(klassOop(all_ifs->obj_at(i)));
    methodOop m = ik->lookup_method(name, signature);
    if (m != NULL) {
      return m;
    }
  }
  return NULL;
}

/* jni_id_for_impl for jfieldIds only */
JNIid* instanceKlass::jni_id_for_impl(instanceKlassHandle this_oop, int offset) {
  MutexLocker ml(JfieldIdCreation_lock);
  // Retry lookup after we got the lock
  JNIid* probe = this_oop->jni_ids() == NULL ? NULL : this_oop->jni_ids()->find(offset);
  if (probe == NULL) {
    // Slow case, allocate new static field identifier
    probe = new JNIid(this_oop->as_klassOop(), offset, this_oop->jni_ids());
    this_oop->set_jni_ids(probe);
  }
  return probe;
}


/* jni_id_for for jfieldIds only */
JNIid* instanceKlass::jni_id_for(int offset) {
  JNIid* probe = jni_ids() == NULL ? NULL : jni_ids()->find(offset);
  if (probe == NULL) {
    probe = jni_id_for_impl(this->as_klassOop(), offset);
  }
  return probe;
}


// Lookup or create a jmethodID.
// This code is called by the VMThread and JavaThreads so the
// locking has to be done very carefully to avoid deadlocks
// and/or other cache consistency problems.
//
jmethodID instanceKlass::get_jmethod_id(instanceKlassHandle ik_h, methodHandle method_h) {
  size_t idnum = (size_t)method_h->method_idnum();
  jmethodID* jmeths = ik_h->methods_jmethod_ids_acquire();
  size_t length = 0;
  jmethodID id = NULL;

  // We use a double-check locking idiom here because this cache is
  // performance sensitive. In the normal system, this cache only
  // transitions from NULL to non-NULL which is safe because we use
  // release_set_methods_jmethod_ids() to advertise the new cache.
  // A partially constructed cache should never be seen by a racing
  // thread. We also use release_store_ptr() to save a new jmethodID
  // in the cache so a partially constructed jmethodID should never be
  // seen either. Cache reads of existing jmethodIDs proceed without a
  // lock, but cache writes of a new jmethodID requires uniqueness and
  // creation of the cache itself requires no leaks so a lock is
  // generally acquired in those two cases.
  //
  // If the RedefineClasses() API has been used, then this cache can
  // grow and we'll have transitions from non-NULL to bigger non-NULL.
  // Cache creation requires no leaks and we require safety between all
  // cache accesses and freeing of the old cache so a lock is generally
  // acquired when the RedefineClasses() API has been used.

  if (jmeths != NULL) {
    // the cache already exists
    if (!ik_h->idnum_can_increment()) {
      // the cache can't grow so we can just get the current values
      get_jmethod_id_length_value(jmeths, idnum, &length, &id);
    } else {
      // cache can grow so we have to be more careful
      if (Threads::number_of_threads() == 0 ||
          SafepointSynchronize::is_at_safepoint()) {
        // we're single threaded or at a safepoint - no locking needed
        get_jmethod_id_length_value(jmeths, idnum, &length, &id);
      } else {
        MutexLocker ml(JmethodIdCreation_lock);
        get_jmethod_id_length_value(jmeths, idnum, &length, &id);
      }
    }
  }
  // implied else:
  // we need to allocate a cache so default length and id values are good

  if (jmeths == NULL ||   // no cache yet
      length <= idnum ||  // cache is too short
      id == NULL) {       // cache doesn't contain entry

    // This function can be called by the VMThread so we have to do all
    // things that might block on a safepoint before grabbing the lock.
    // Otherwise, we can deadlock with the VMThread or have a cache
    // consistency issue. These vars keep track of what we might have
    // to free after the lock is dropped.
    jmethodID  to_dealloc_id     = NULL;
    jmethodID* to_dealloc_jmeths = NULL;

    // may not allocate new_jmeths or use it if we allocate it
    jmethodID* new_jmeths = NULL;
    if (length <= idnum) {
      // allocate a new cache that might be used
      size_t size = MAX2(idnum+1, (size_t)ik_h->idnum_allocated_count());
      new_jmeths = NEW_C_HEAP_ARRAY(jmethodID, size+1);
      memset(new_jmeths, 0, (size+1)*sizeof(jmethodID));
      // cache size is stored in element[0], other elements offset by one
      new_jmeths[0] = (jmethodID)size;
    }

    // allocate a new jmethodID that might be used
    jmethodID new_id = NULL;
    if (method_h->is_old() && !method_h->is_obsolete()) {
      // The method passed in is old (but not obsolete), we need to use the current version
      methodOop current_method = ik_h->method_with_idnum((int)idnum);
      assert(current_method != NULL, "old and but not obsolete, so should exist");
      methodHandle current_method_h(current_method == NULL? method_h() : current_method);
      new_id = JNIHandles::make_jmethod_id(current_method_h);
    } else {
      // It is the current version of the method or an obsolete method,
      // use the version passed in
      new_id = JNIHandles::make_jmethod_id(method_h);
    }

    if (Threads::number_of_threads() == 0 ||
        SafepointSynchronize::is_at_safepoint()) {
      // we're single threaded or at a safepoint - no locking needed
      id = get_jmethod_id_fetch_or_update(ik_h, idnum, new_id, new_jmeths,
                                          &to_dealloc_id, &to_dealloc_jmeths);
    } else {
      MutexLocker ml(JmethodIdCreation_lock);
      id = get_jmethod_id_fetch_or_update(ik_h, idnum, new_id, new_jmeths,
                                          &to_dealloc_id, &to_dealloc_jmeths);
    }

    // The lock has been dropped so we can free resources.
    // Free up either the old cache or the new cache if we allocated one.
    if (to_dealloc_jmeths != NULL) {
      FreeHeap(to_dealloc_jmeths);
    }
    // free up the new ID since it wasn't needed
    if (to_dealloc_id != NULL) {
      JNIHandles::destroy_jmethod_id(to_dealloc_id);
    }
  }
  return id;
}


// Common code to fetch the jmethodID from the cache or update the
// cache with the new jmethodID. This function should never do anything
// that causes the caller to go to a safepoint or we can deadlock with
// the VMThread or have cache consistency issues.
//
jmethodID instanceKlass::get_jmethod_id_fetch_or_update(
            instanceKlassHandle ik_h, size_t idnum, jmethodID new_id,
            jmethodID* new_jmeths, jmethodID* to_dealloc_id_p,
            jmethodID** to_dealloc_jmeths_p) {
  assert(new_id != NULL, "sanity check");
  assert(to_dealloc_id_p != NULL, "sanity check");
  assert(to_dealloc_jmeths_p != NULL, "sanity check");
  assert(Threads::number_of_threads() == 0 ||
         SafepointSynchronize::is_at_safepoint() ||
         JmethodIdCreation_lock->owned_by_self(), "sanity check");

  // reacquire the cache - we are locked, single threaded or at a safepoint
  jmethodID* jmeths = ik_h->methods_jmethod_ids_acquire();
  jmethodID  id     = NULL;
  size_t     length = 0;

  if (jmeths == NULL ||                         // no cache yet
      (length = (size_t)jmeths[0]) <= idnum) {  // cache is too short
    if (jmeths != NULL) {
      // copy any existing entries from the old cache
      for (size_t index = 0; index < length; index++) {
        new_jmeths[index+1] = jmeths[index+1];
      }
      *to_dealloc_jmeths_p = jmeths;  // save old cache for later delete
    }
    ik_h->release_set_methods_jmethod_ids(jmeths = new_jmeths);
  } else {
    // fetch jmethodID (if any) from the existing cache
    id = jmeths[idnum+1];
    *to_dealloc_jmeths_p = new_jmeths;  // save new cache for later delete
  }
  if (id == NULL) {
    // No matching jmethodID in the existing cache or we have a new
    // cache or we just grew the cache. This cache write is done here
    // by the first thread to win the foot race because a jmethodID
    // needs to be unique once it is generally available.
    id = new_id;

    // The jmethodID cache can be read while unlocked so we have to
    // make sure the new jmethodID is complete before installing it
    // in the cache.
    OrderAccess::release_store_ptr(&jmeths[idnum+1], id);
  } else {
    *to_dealloc_id_p = new_id; // save new id for later delete
  }
  return id;
}


// Common code to get the jmethodID cache length and the jmethodID
// value at index idnum if there is one.
//
void instanceKlass::get_jmethod_id_length_value(jmethodID* cache,
       size_t idnum, size_t *length_p, jmethodID* id_p) {
  assert(cache != NULL, "sanity check");
  assert(length_p != NULL, "sanity check");
  assert(id_p != NULL, "sanity check");

  // cache size is stored in element[0], other elements offset by one
  *length_p = (size_t)cache[0];
  if (*length_p <= idnum) {  // cache is too short
    *id_p = NULL;
  } else {
    *id_p = cache[idnum+1];  // fetch jmethodID (if any)
  }
}


// Lookup a jmethodID, NULL if not found.  Do no blocking, no allocations, no handles
jmethodID instanceKlass::jmethod_id_or_null(methodOop method) {
  size_t idnum = (size_t)method->method_idnum();
  jmethodID* jmeths = methods_jmethod_ids_acquire();
  size_t length;                                // length assigned as debugging crumb
  jmethodID id = NULL;
  if (jmeths != NULL &&                         // If there is a cache
      (length = (size_t)jmeths[0]) > idnum) {   // and if it is long enough,
    id = jmeths[idnum+1];                       // Look up the id (may be NULL)
  }
  return id;
}


// Cache an itable index
void instanceKlass::set_cached_itable_index(size_t idnum, int index) {
  int* indices = methods_cached_itable_indices_acquire();
  int* to_dealloc_indices = NULL;

  // We use a double-check locking idiom here because this cache is
  // performance sensitive. In the normal system, this cache only
  // transitions from NULL to non-NULL which is safe because we use
  // release_set_methods_cached_itable_indices() to advertise the
  // new cache. A partially constructed cache should never be seen
  // by a racing thread. Cache reads and writes proceed without a
  // lock, but creation of the cache itself requires no leaks so a
  // lock is generally acquired in that case.
  //
  // If the RedefineClasses() API has been used, then this cache can
  // grow and we'll have transitions from non-NULL to bigger non-NULL.
  // Cache creation requires no leaks and we require safety between all
  // cache accesses and freeing of the old cache so a lock is generally
  // acquired when the RedefineClasses() API has been used.

  if (indices == NULL || idnum_can_increment()) {
    // we need a cache or the cache can grow
    MutexLocker ml(JNICachedItableIndex_lock);
    // reacquire the cache to see if another thread already did the work
    indices = methods_cached_itable_indices_acquire();
    size_t length = 0;
    // cache size is stored in element[0], other elements offset by one
    if (indices == NULL || (length = (size_t)indices[0]) <= idnum) {
      size_t size = MAX2(idnum+1, (size_t)idnum_allocated_count());
      int* new_indices = NEW_C_HEAP_ARRAY(int, size+1);
      new_indices[0] = (int)size;
      // copy any existing entries
      size_t i;
      for (i = 0; i < length; i++) {
        new_indices[i+1] = indices[i+1];
      }
      // Set all the rest to -1
      for (i = length; i < size; i++) {
        new_indices[i+1] = -1;
      }
      if (indices != NULL) {
        // We have an old cache to delete so save it for after we
        // drop the lock.
        to_dealloc_indices = indices;
      }
      release_set_methods_cached_itable_indices(indices = new_indices);
    }

    if (idnum_can_increment()) {
      // this cache can grow so we have to write to it safely
      indices[idnum+1] = index;
    }
  } else {
    CHECK_UNHANDLED_OOPS_ONLY(Thread::current()->clear_unhandled_oops());
  }

  if (!idnum_can_increment()) {
    // The cache cannot grow and this JNI itable index value does not
    // have to be unique like a jmethodID. If there is a race to set it,
    // it doesn't matter.
    indices[idnum+1] = index;
  }

  if (to_dealloc_indices != NULL) {
    // we allocated a new cache so free the old one
    FreeHeap(to_dealloc_indices);
  }
}


// Retrieve a cached itable index
int instanceKlass::cached_itable_index(size_t idnum) {
  int* indices = methods_cached_itable_indices_acquire();
  if (indices != NULL && ((size_t)indices[0]) > idnum) {
     // indices exist and are long enough, retrieve possible cached
    return indices[idnum+1];
  }
  return -1;
}


//
// nmethodBucket is used to record dependent nmethods for
// deoptimization.  nmethod dependencies are actually <klass, method>
// pairs but we really only care about the klass part for purposes of
// finding nmethods which might need to be deoptimized.  Instead of
// recording the method, a count of how many times a particular nmethod
// was recorded is kept.  This ensures that any recording errors are
// noticed since an nmethod should be removed as many times are it's
// added.
//
class nmethodBucket {
 private:
  nmethod*       _nmethod;
  int            _count;
  nmethodBucket* _next;

 public:
  nmethodBucket(nmethod* nmethod, nmethodBucket* next) {
    _nmethod = nmethod;
    _next = next;
    _count = 1;
  }
  int count()                             { return _count; }
  int increment()                         { _count += 1; return _count; }
  int decrement()                         { _count -= 1; assert(_count >= 0, "don't underflow"); return _count; }
  nmethodBucket* next()                   { return _next; }
  void set_next(nmethodBucket* b)         { _next = b; }
  nmethod* get_nmethod()                  { return _nmethod; }
};


//
// Walk the list of dependent nmethods searching for nmethods which
// are dependent on the klassOop that was passed in and mark them for
// deoptimization.  Returns the number of nmethods found.
//
int instanceKlass::mark_dependent_nmethods(DepChange& changes) {
  assert_locked_or_safepoint(CodeCache_lock);
  int found = 0;
  nmethodBucket* b = _dependencies;
  while (b != NULL) {
    nmethod* nm = b->get_nmethod();
    // since dependencies aren't removed until an nmethod becomes a zombie,
    // the dependency list may contain nmethods which aren't alive.
    if (nm->is_alive() && !nm->is_marked_for_deoptimization() && nm->check_dependency_on(changes)) {
      if (TraceDependencies) {
        ResourceMark rm;
        tty->print_cr("Marked for deoptimization");
        tty->print_cr("  context = %s", this->external_name());
        changes.print();
        nm->print();
        nm->print_dependencies();
      }
      nm->mark_for_deoptimization();
      found++;
    }
    b = b->next();
  }
  return found;
}


//
// Add an nmethodBucket to the list of dependencies for this nmethod.
// It's possible that an nmethod has multiple dependencies on this klass
// so a count is kept for each bucket to guarantee that creation and
// deletion of dependencies is consistent.
//
void instanceKlass::add_dependent_nmethod(nmethod* nm) {
  assert_locked_or_safepoint(CodeCache_lock);
  nmethodBucket* b = _dependencies;
  nmethodBucket* last = NULL;
  while (b != NULL) {
    if (nm == b->get_nmethod()) {
      b->increment();
      return;
    }
    b = b->next();
  }
  _dependencies = new nmethodBucket(nm, _dependencies);
}


//
// Decrement count of the nmethod in the dependency list and remove
// the bucket competely when the count goes to 0.  This method must
// find a corresponding bucket otherwise there's a bug in the
// recording of dependecies.
//
void instanceKlass::remove_dependent_nmethod(nmethod* nm) {
  assert_locked_or_safepoint(CodeCache_lock);
  nmethodBucket* b = _dependencies;
  nmethodBucket* last = NULL;
  while (b != NULL) {
    if (nm == b->get_nmethod()) {
      if (b->decrement() == 0) {
        if (last == NULL) {
          _dependencies = b->next();
        } else {
          last->set_next(b->next());
        }
        delete b;
      }
      return;
    }
    last = b;
    b = b->next();
  }
#ifdef ASSERT
  tty->print_cr("### %s can't find dependent nmethod:", this->external_name());
  nm->print();
#endif // ASSERT
  ShouldNotReachHere();
}


#ifndef PRODUCT
void instanceKlass::print_dependent_nmethods(bool verbose) {
  nmethodBucket* b = _dependencies;
  int idx = 0;
  while (b != NULL) {
    nmethod* nm = b->get_nmethod();
    tty->print("[%d] count=%d { ", idx++, b->count());
    if (!verbose) {
      nm->print_on(tty, "nmethod");
      tty->print_cr(" } ");
    } else {
      nm->print();
      nm->print_dependencies();
      tty->print_cr("--- } ");
    }
    b = b->next();
  }
}


bool instanceKlass::is_dependent_nmethod(nmethod* nm) {
  nmethodBucket* b = _dependencies;
  while (b != NULL) {
    if (nm == b->get_nmethod()) {
      return true;
    }
    b = b->next();
  }
  return false;
}
#endif //PRODUCT


#ifdef ASSERT
template <class T> void assert_is_in(T *p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop o = oopDesc::decode_heap_oop_not_null(heap_oop);
    assert(Universe::heap()->is_in(o), "should be in heap");
  }
}
template <class T> void assert_is_in_closed_subset(T *p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop o = oopDesc::decode_heap_oop_not_null(heap_oop);
    assert(Universe::heap()->is_in_closed_subset(o), "should be in closed");
  }
}
template <class T> void assert_is_in_reserved(T *p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop o = oopDesc::decode_heap_oop_not_null(heap_oop);
    assert(Universe::heap()->is_in_reserved(o), "should be in reserved");
  }
}
template <class T> void assert_nothing(T *p) {}

#else
template <class T> void assert_is_in(T *p) {}
template <class T> void assert_is_in_closed_subset(T *p) {}
template <class T> void assert_is_in_reserved(T *p) {}
template <class T> void assert_nothing(T *p) {}
#endif // ASSERT

//
// Macros that iterate over areas of oops which are specialized on type of
// oop pointer either narrow or wide, depending on UseCompressedOops
//
// Parameters are:
//   T         - type of oop to point to (either oop or narrowOop)
//   start_p   - starting pointer for region to iterate over
//   count     - number of oops or narrowOops to iterate over
//   do_oop    - action to perform on each oop (it's arbitrary C code which
//               makes it more efficient to put in a macro rather than making
//               it a template function)
//   assert_fn - assert function which is template function because performance
//               doesn't matter when enabled.
#define InstanceKlass_SPECIALIZED_OOP_ITERATE( \
  T, start_p, count, do_oop,                \
  assert_fn)                                \
{                                           \
  T* p         = (T*)(start_p);             \
  T* const end = p + (count);               \
  while (p < end) {                         \
    (assert_fn)(p);                         \
    do_oop;                                 \
    ++p;                                    \
  }                                         \
}

#define InstanceKlass_SPECIALIZED_OOP_REVERSE_ITERATE( \
  T, start_p, count, do_oop,                \
  assert_fn)                                \
{                                           \
  T* const start = (T*)(start_p);           \
  T*       p     = start + (count);         \
  while (start < p) {                       \
    --p;                                    \
    (assert_fn)(p);                         \
    do_oop;                                 \
  }                                         \
}

#define InstanceKlass_SPECIALIZED_BOUNDED_OOP_ITERATE( \
  T, start_p, count, low, high,             \
  do_oop, assert_fn)                        \
{                                           \
  T* const l = (T*)(low);                   \
  T* const h = (T*)(high);                  \
  assert(mask_bits((intptr_t)l, sizeof(T)-1) == 0 && \
         mask_bits((intptr_t)h, sizeof(T)-1) == 0,   \
         "bounded region must be properly aligned"); \
  T* p       = (T*)(start_p);               \
  T* end     = p + (count);                 \
  if (p < l) p = l;                         \
  if (end > h) end = h;                     \
  while (p < end) {                         \
    (assert_fn)(p);                         \
    do_oop;                                 \
    ++p;                                    \
  }                                         \
}


// The following macros call specialized macros, passing either oop or
// narrowOop as the specialization type.  These test the UseCompressedOops
// flag.
#define InstanceKlass_OOP_ITERATE(start_p, count,    \
                                  do_oop, assert_fn) \
{                                                    \
  if (UseCompressedOops) {                           \
    InstanceKlass_SPECIALIZED_OOP_ITERATE(narrowOop, \
      start_p, count,                                \
      do_oop, assert_fn)                             \
  } else {                                           \
    InstanceKlass_SPECIALIZED_OOP_ITERATE(oop,       \
      start_p, count,                                \
      do_oop, assert_fn)                             \
  }                                                  \
}

#define InstanceKlass_BOUNDED_OOP_ITERATE(start_p, count, low, high,    \
                                          do_oop, assert_fn) \
{                                                            \
  if (UseCompressedOops) {                                   \
    InstanceKlass_SPECIALIZED_BOUNDED_OOP_ITERATE(narrowOop, \
      start_p, count,                                        \
      low, high,                                             \
      do_oop, assert_fn)                                     \
  } else {                                                   \
    InstanceKlass_SPECIALIZED_BOUNDED_OOP_ITERATE(oop,       \
      start_p, count,                                        \
      low, high,                                             \
      do_oop, assert_fn)                                     \
  }                                                          \
}

#define InstanceKlass_OOP_MAP_ITERATE(obj, do_oop, assert_fn)            \
{                                                                        \
  /* Compute oopmap block range. The common case                         \
     is nonstatic_oop_map_size == 1. */                                  \
  OopMapBlock* map           = start_of_nonstatic_oop_maps();            \
  OopMapBlock* const end_map = map + nonstatic_oop_map_count();          \
  if (UseCompressedOops) {                                               \
    while (map < end_map) {                                              \
      InstanceKlass_SPECIALIZED_OOP_ITERATE(narrowOop,                   \
        obj->obj_field_addr<narrowOop>(map->offset()), map->count(),     \
        do_oop, assert_fn)                                               \
      ++map;                                                             \
    }                                                                    \
  } else {                                                               \
    while (map < end_map) {                                              \
      InstanceKlass_SPECIALIZED_OOP_ITERATE(oop,                         \
        obj->obj_field_addr<oop>(map->offset()), map->count(),           \
        do_oop, assert_fn)                                               \
      ++map;                                                             \
    }                                                                    \
  }                                                                      \
}

#define InstanceKlass_OOP_MAP_REVERSE_ITERATE(obj, do_oop, assert_fn)    \
{                                                                        \
  OopMapBlock* const start_map = start_of_nonstatic_oop_maps();          \
  OopMapBlock* map             = start_map + nonstatic_oop_map_count();  \
  if (UseCompressedOops) {                                               \
    while (start_map < map) {                                            \
      --map;                                                             \
      InstanceKlass_SPECIALIZED_OOP_REVERSE_ITERATE(narrowOop,           \
        obj->obj_field_addr<narrowOop>(map->offset()), map->count(),     \
        do_oop, assert_fn)                                               \
    }                                                                    \
  } else {                                                               \
    while (start_map < map) {                                            \
      --map;                                                             \
      InstanceKlass_SPECIALIZED_OOP_REVERSE_ITERATE(oop,                 \
        obj->obj_field_addr<oop>(map->offset()), map->count(),           \
        do_oop, assert_fn)                                               \
    }                                                                    \
  }                                                                      \
}

#define InstanceKlass_BOUNDED_OOP_MAP_ITERATE(obj, low, high, do_oop,    \
                                              assert_fn)                 \
{                                                                        \
  /* Compute oopmap block range. The common case is                      \
     nonstatic_oop_map_size == 1, so we accept the                       \
     usually non-existent extra overhead of examining                    \
     all the maps. */                                                    \
  OopMapBlock* map           = start_of_nonstatic_oop_maps();            \
  OopMapBlock* const end_map = map + nonstatic_oop_map_count();          \
  if (UseCompressedOops) {                                               \
    while (map < end_map) {                                              \
      InstanceKlass_SPECIALIZED_BOUNDED_OOP_ITERATE(narrowOop,           \
        obj->obj_field_addr<narrowOop>(map->offset()), map->count(),     \
        low, high,                                                       \
        do_oop, assert_fn)                                               \
      ++map;                                                             \
    }                                                                    \
  } else {                                                               \
    while (map < end_map) {                                              \
      InstanceKlass_SPECIALIZED_BOUNDED_OOP_ITERATE(oop,                 \
        obj->obj_field_addr<oop>(map->offset()), map->count(),           \
        low, high,                                                       \
        do_oop, assert_fn)                                               \
      ++map;                                                             \
    }                                                                    \
  }                                                                      \
}

void instanceKlass::follow_static_fields() {
  InstanceKlass_OOP_ITERATE( \
    start_of_static_fields(), static_oop_field_size(), \
    MarkSweep::mark_and_push(p), \
    assert_is_in_closed_subset)
}

#ifndef SERIALGC
void instanceKlass::follow_static_fields(ParCompactionManager* cm) {
  InstanceKlass_OOP_ITERATE( \
    start_of_static_fields(), static_oop_field_size(), \
    PSParallelCompact::mark_and_push(cm, p), \
    assert_is_in)
}
#endif // SERIALGC

void instanceKlass::adjust_static_fields() {
  InstanceKlass_OOP_ITERATE( \
    start_of_static_fields(), static_oop_field_size(), \
    MarkSweep::adjust_pointer(p), \
    assert_nothing)
}

#ifndef SERIALGC
void instanceKlass::update_static_fields() {
  InstanceKlass_OOP_ITERATE( \
    start_of_static_fields(), static_oop_field_size(), \
    PSParallelCompact::adjust_pointer(p), \
    assert_nothing)
}

void instanceKlass::update_static_fields(HeapWord* beg_addr, HeapWord* end_addr) {
  InstanceKlass_BOUNDED_OOP_ITERATE( \
    start_of_static_fields(), static_oop_field_size(), \
    beg_addr, end_addr, \
    PSParallelCompact::adjust_pointer(p), \
    assert_nothing )
}
#endif // SERIALGC

void instanceKlass::oop_follow_contents(oop obj) {
  assert(obj != NULL, "can't follow the content of NULL object");
  obj->follow_header();
  InstanceKlass_OOP_MAP_ITERATE( \
    obj, \
    MarkSweep::mark_and_push(p), \
    assert_is_in_closed_subset)
}

#ifndef SERIALGC
void instanceKlass::oop_follow_contents(ParCompactionManager* cm,
                                        oop obj) {
  assert(obj != NULL, "can't follow the content of NULL object");
  obj->follow_header(cm);
  InstanceKlass_OOP_MAP_ITERATE( \
    obj, \
    PSParallelCompact::mark_and_push(cm, p), \
    assert_is_in)
}
#endif // SERIALGC

// closure's do_header() method dicates whether the given closure should be
// applied to the klass ptr in the object header.

#define InstanceKlass_OOP_OOP_ITERATE_DEFN(OopClosureType, nv_suffix)        \
                                                                             \
int instanceKlass::oop_oop_iterate##nv_suffix(oop obj, OopClosureType* closure) { \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::ik);\
  /* header */                                                          \
  if (closure->do_header()) {                                           \
    obj->oop_iterate_header(closure);                                   \
  }                                                                     \
  InstanceKlass_OOP_MAP_ITERATE(                                        \
    obj,                                                                \
    SpecializationStats::                                               \
      record_do_oop_call##nv_suffix(SpecializationStats::ik);           \
    (closure)->do_oop##nv_suffix(p),                                    \
    assert_is_in_closed_subset)                                         \
  return size_helper();                                                 \
}

#ifndef SERIALGC
#define InstanceKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN(OopClosureType, nv_suffix) \
                                                                                \
int instanceKlass::oop_oop_iterate_backwards##nv_suffix(oop obj,                \
                                              OopClosureType* closure) {        \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::ik); \
  /* header */                                                                  \
  if (closure->do_header()) {                                                   \
    obj->oop_iterate_header(closure);                                           \
  }                                                                             \
  /* instance variables */                                                      \
  InstanceKlass_OOP_MAP_REVERSE_ITERATE(                                        \
    obj,                                                                        \
    SpecializationStats::record_do_oop_call##nv_suffix(SpecializationStats::ik);\
    (closure)->do_oop##nv_suffix(p),                                            \
    assert_is_in_closed_subset)                                                 \
   return size_helper();                                                        \
}
#endif // !SERIALGC

#define InstanceKlass_OOP_OOP_ITERATE_DEFN_m(OopClosureType, nv_suffix) \
                                                                        \
int instanceKlass::oop_oop_iterate##nv_suffix##_m(oop obj,              \
                                                  OopClosureType* closure, \
                                                  MemRegion mr) {          \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::ik);\
  if (closure->do_header()) {                                            \
    obj->oop_iterate_header(closure, mr);                                \
  }                                                                      \
  InstanceKlass_BOUNDED_OOP_MAP_ITERATE(                                 \
    obj, mr.start(), mr.end(),                                           \
    (closure)->do_oop##nv_suffix(p),                                     \
    assert_is_in_closed_subset)                                          \
  return size_helper();                                                  \
}

ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceKlass_OOP_OOP_ITERATE_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceKlass_OOP_OOP_ITERATE_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceKlass_OOP_OOP_ITERATE_DEFN_m)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceKlass_OOP_OOP_ITERATE_DEFN_m)
#ifndef SERIALGC
ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN)
#endif // !SERIALGC

void instanceKlass::iterate_static_fields(OopClosure* closure) {
    InstanceKlass_OOP_ITERATE( \
      start_of_static_fields(), static_oop_field_size(), \
      closure->do_oop(p), \
      assert_is_in_reserved)
}

void instanceKlass::iterate_static_fields(OopClosure* closure,
                                          MemRegion mr) {
  InstanceKlass_BOUNDED_OOP_ITERATE( \
    start_of_static_fields(), static_oop_field_size(), \
    mr.start(), mr.end(), \
    (closure)->do_oop_v(p), \
    assert_is_in_closed_subset)
}

int instanceKlass::oop_adjust_pointers(oop obj) {
  int size = size_helper();
  InstanceKlass_OOP_MAP_ITERATE( \
    obj, \
    MarkSweep::adjust_pointer(p), \
    assert_is_in)
  obj->adjust_header();
  return size;
}

#ifndef SERIALGC
void instanceKlass::oop_copy_contents(PSPromotionManager* pm, oop obj) {
  assert(!pm->depth_first(), "invariant");
  InstanceKlass_OOP_MAP_REVERSE_ITERATE( \
    obj, \
    if (PSScavenge::should_scavenge(p)) { \
      pm->claim_or_forward_breadth(p); \
    }, \
    assert_nothing )
}

void instanceKlass::oop_push_contents(PSPromotionManager* pm, oop obj) {
  assert(pm->depth_first(), "invariant");
  InstanceKlass_OOP_MAP_REVERSE_ITERATE( \
    obj, \
    if (PSScavenge::should_scavenge(p)) { \
      pm->claim_or_forward_depth(p); \
    }, \
    assert_nothing )
}

int instanceKlass::oop_update_pointers(ParCompactionManager* cm, oop obj) {
  InstanceKlass_OOP_MAP_ITERATE( \
    obj, \
    PSParallelCompact::adjust_pointer(p), \
    assert_nothing)
  return size_helper();
}

int instanceKlass::oop_update_pointers(ParCompactionManager* cm, oop obj,
                                       HeapWord* beg_addr, HeapWord* end_addr) {
  InstanceKlass_BOUNDED_OOP_MAP_ITERATE( \
    obj, beg_addr, end_addr, \
    PSParallelCompact::adjust_pointer(p), \
    assert_nothing)
  return size_helper();
}

void instanceKlass::copy_static_fields(PSPromotionManager* pm) {
  assert(!pm->depth_first(), "invariant");
  InstanceKlass_OOP_ITERATE( \
    start_of_static_fields(), static_oop_field_size(), \
    if (PSScavenge::should_scavenge(p)) { \
      pm->claim_or_forward_breadth(p); \
    }, \
    assert_nothing )
}

void instanceKlass::push_static_fields(PSPromotionManager* pm) {
  assert(pm->depth_first(), "invariant");
  InstanceKlass_OOP_ITERATE( \
    start_of_static_fields(), static_oop_field_size(), \
    if (PSScavenge::should_scavenge(p)) { \
      pm->claim_or_forward_depth(p); \
    }, \
    assert_nothing )
}

void instanceKlass::copy_static_fields(ParCompactionManager* cm) {
  InstanceKlass_OOP_ITERATE( \
    start_of_static_fields(), static_oop_field_size(), \
    PSParallelCompact::adjust_pointer(p), \
    assert_is_in)
}
#endif // SERIALGC

// This klass is alive but the implementor link is not followed/updated.
// Subklass and sibling links are handled by Klass::follow_weak_klass_links

void instanceKlass::follow_weak_klass_links(
  BoolObjectClosure* is_alive, OopClosure* keep_alive) {
  assert(is_alive->do_object_b(as_klassOop()), "this oop should be live");
  if (ClassUnloading) {
    for (int i = 0; i < implementors_limit; i++) {
      klassOop impl = _implementors[i];
      if (impl == NULL)  break;  // no more in the list
      if (!is_alive->do_object_b(impl)) {
        // remove this guy from the list by overwriting him with the tail
        int lasti = --_nof_implementors;
        assert(lasti >= i && lasti < implementors_limit, "just checking");
        _implementors[i] = _implementors[lasti];
        _implementors[lasti] = NULL;
        --i; // rerun the loop at this index
      }
    }
  } else {
    for (int i = 0; i < implementors_limit; i++) {
      keep_alive->do_oop(&adr_implementors()[i]);
    }
  }
  Klass::follow_weak_klass_links(is_alive, keep_alive);
}

void instanceKlass::remove_unshareable_info() {
  Klass::remove_unshareable_info();
  init_implementor();
}

static void clear_all_breakpoints(methodOop m) {
  m->clear_all_breakpoints();
}

void instanceKlass::release_C_heap_structures() {
  // Deallocate oop map cache
  if (_oop_map_cache != NULL) {
    delete _oop_map_cache;
    _oop_map_cache = NULL;
  }

  // Deallocate JNI identifiers for jfieldIDs
  JNIid::deallocate(jni_ids());
  set_jni_ids(NULL);

  jmethodID* jmeths = methods_jmethod_ids_acquire();
  if (jmeths != (jmethodID*)NULL) {
    release_set_methods_jmethod_ids(NULL);
    FreeHeap(jmeths);
  }

  int* indices = methods_cached_itable_indices_acquire();
  if (indices != (int*)NULL) {
    release_set_methods_cached_itable_indices(NULL);
    FreeHeap(indices);
  }

  // release dependencies
  nmethodBucket* b = _dependencies;
  _dependencies = NULL;
  while (b != NULL) {
    nmethodBucket* next = b->next();
    delete b;
    b = next;
  }

  // Deallocate breakpoint records
  if (breakpoints() != 0x0) {
    methods_do(clear_all_breakpoints);
    assert(breakpoints() == 0x0, "should have cleared breakpoints");
  }

  // deallocate information about previous versions
  if (_previous_versions != NULL) {
    for (int i = _previous_versions->length() - 1; i >= 0; i--) {
      PreviousVersionNode * pv_node = _previous_versions->at(i);
      delete pv_node;
    }
    delete _previous_versions;
    _previous_versions = NULL;
  }

  // deallocate the cached class file
  if (_cached_class_file_bytes != NULL) {
    os::free(_cached_class_file_bytes);
    _cached_class_file_bytes = NULL;
    _cached_class_file_len = 0;
  }
}

const char* instanceKlass::signature_name() const {
  const char* src = (const char*) (name()->as_C_string());
  const int src_length = (int)strlen(src);
  char* dest = NEW_RESOURCE_ARRAY(char, src_length + 3);
  int src_index = 0;
  int dest_index = 0;
  dest[dest_index++] = 'L';
  while (src_index < src_length) {
    dest[dest_index++] = src[src_index++];
  }
  dest[dest_index++] = ';';
  dest[dest_index] = '\0';
  return dest;
}

// different verisons of is_same_class_package
bool instanceKlass::is_same_class_package(klassOop class2) {
  klassOop class1 = as_klassOop();
  oop classloader1 = instanceKlass::cast(class1)->class_loader();
  symbolOop classname1 = Klass::cast(class1)->name();

  if (Klass::cast(class2)->oop_is_objArray()) {
    class2 = objArrayKlass::cast(class2)->bottom_klass();
  }
  oop classloader2;
  if (Klass::cast(class2)->oop_is_instance()) {
    classloader2 = instanceKlass::cast(class2)->class_loader();
  } else {
    assert(Klass::cast(class2)->oop_is_typeArray(), "should be type array");
    classloader2 = NULL;
  }
  symbolOop classname2 = Klass::cast(class2)->name();

  return instanceKlass::is_same_class_package(classloader1, classname1,
                                              classloader2, classname2);
}

bool instanceKlass::is_same_class_package(oop classloader2, symbolOop classname2) {
  klassOop class1 = as_klassOop();
  oop classloader1 = instanceKlass::cast(class1)->class_loader();
  symbolOop classname1 = Klass::cast(class1)->name();

  return instanceKlass::is_same_class_package(classloader1, classname1,
                                              classloader2, classname2);
}

// return true if two classes are in the same package, classloader
// and classname information is enough to determine a class's package
bool instanceKlass::is_same_class_package(oop class_loader1, symbolOop class_name1,
                                          oop class_loader2, symbolOop class_name2) {
  if (class_loader1 != class_loader2) {
    return false;
  } else if (class_name1 == class_name2) {
    return true;                // skip painful bytewise comparison
  } else {
    ResourceMark rm;

    // The symbolOop's are in UTF8 encoding. Since we only need to check explicitly
    // for ASCII characters ('/', 'L', '['), we can keep them in UTF8 encoding.
    // Otherwise, we just compare jbyte values between the strings.
    jbyte *name1 = class_name1->base();
    jbyte *name2 = class_name2->base();

    jbyte *last_slash1 = UTF8::strrchr(name1, class_name1->utf8_length(), '/');
    jbyte *last_slash2 = UTF8::strrchr(name2, class_name2->utf8_length(), '/');

    if ((last_slash1 == NULL) || (last_slash2 == NULL)) {
      // One of the two doesn't have a package.  Only return true
      // if the other one also doesn't have a package.
      return last_slash1 == last_slash2;
    } else {
      // Skip over '['s
      if (*name1 == '[') {
        do {
          name1++;
        } while (*name1 == '[');
        if (*name1 != 'L') {
          // Something is terribly wrong.  Shouldn't be here.
          return false;
        }
      }
      if (*name2 == '[') {
        do {
          name2++;
        } while (*name2 == '[');
        if (*name2 != 'L') {
          // Something is terribly wrong.  Shouldn't be here.
          return false;
        }
      }

      // Check that package part is identical
      int length1 = last_slash1 - name1;
      int length2 = last_slash2 - name2;

      return UTF8::equal(name1, length1, name2, length2);
    }
  }
}

// Returns true iff super_method can be overridden by a method in targetclassname
// See JSL 3rd edition 8.4.6.1
// Assumes name-signature match
// "this" is instanceKlass of super_method which must exist
// note that the instanceKlass of the method in the targetclassname has not always been created yet
bool instanceKlass::is_override(methodHandle super_method, Handle targetclassloader, symbolHandle targetclassname, TRAPS) {
   // Private methods can not be overridden
   if (super_method->is_private()) {
     return false;
   }
   // If super method is accessible, then override
   if ((super_method->is_protected()) ||
       (super_method->is_public())) {
     return true;
   }
   // Package-private methods are not inherited outside of package
   assert(super_method->is_package_private(), "must be package private");
   return(is_same_class_package(targetclassloader(), targetclassname()));
}

/* defined for now in jvm.cpp, for historical reasons *--
klassOop instanceKlass::compute_enclosing_class_impl(instanceKlassHandle self,
                                                     symbolOop& simple_name_result, TRAPS) {
  ...
}
*/

// tell if two classes have the same enclosing class (at package level)
bool instanceKlass::is_same_package_member_impl(instanceKlassHandle class1,
                                                klassOop class2_oop, TRAPS) {
  if (class2_oop == class1->as_klassOop())          return true;
  if (!Klass::cast(class2_oop)->oop_is_instance())  return false;
  instanceKlassHandle class2(THREAD, class2_oop);

  // must be in same package before we try anything else
  if (!class1->is_same_class_package(class2->class_loader(), class2->name()))
    return false;

  // As long as there is an outer1.getEnclosingClass,
  // shift the search outward.
  instanceKlassHandle outer1 = class1;
  for (;;) {
    // As we walk along, look for equalities between outer1 and class2.
    // Eventually, the walks will terminate as outer1 stops
    // at the top-level class around the original class.
    symbolOop ignore_name;
    klassOop next = outer1->compute_enclosing_class(ignore_name, CHECK_false);
    if (next == NULL)  break;
    if (next == class2())  return true;
    outer1 = instanceKlassHandle(THREAD, next);
  }

  // Now do the same for class2.
  instanceKlassHandle outer2 = class2;
  for (;;) {
    symbolOop ignore_name;
    klassOop next = outer2->compute_enclosing_class(ignore_name, CHECK_false);
    if (next == NULL)  break;
    // Might as well check the new outer against all available values.
    if (next == class1())  return true;
    if (next == outer1())  return true;
    outer2 = instanceKlassHandle(THREAD, next);
  }

  // If by this point we have not found an equality between the
  // two classes, we know they are in separate package members.
  return false;
}


jint instanceKlass::compute_modifier_flags(TRAPS) const {
  klassOop k = as_klassOop();
  jint access = access_flags().as_int();

  // But check if it happens to be member class.
  typeArrayOop inner_class_list = inner_classes();
  int length = (inner_class_list == NULL) ? 0 : inner_class_list->length();
  assert (length % instanceKlass::inner_class_next_offset == 0, "just checking");
  if (length > 0) {
    typeArrayHandle inner_class_list_h(THREAD, inner_class_list);
    instanceKlassHandle ik(THREAD, k);
    for (int i = 0; i < length; i += instanceKlass::inner_class_next_offset) {
      int ioff = inner_class_list_h->ushort_at(
                      i + instanceKlass::inner_class_inner_class_info_offset);

      // Inner class attribute can be zero, skip it.
      // Strange but true:  JVM spec. allows null inner class refs.
      if (ioff == 0) continue;

      // only look at classes that are already loaded
      // since we are looking for the flags for our self.
      symbolOop inner_name = ik->constants()->klass_name_at(ioff);
      if ((ik->name() == inner_name)) {
        // This is really a member class.
        access = inner_class_list_h->ushort_at(i + instanceKlass::inner_class_access_flags_offset);
        break;
      }
    }
  }
  // Remember to strip ACC_SUPER bit
  return (access & (~JVM_ACC_SUPER)) & JVM_ACC_WRITTEN_FLAGS;
}

jint instanceKlass::jvmti_class_status() const {
  jint result = 0;

  if (is_linked()) {
    result |= JVMTI_CLASS_STATUS_VERIFIED | JVMTI_CLASS_STATUS_PREPARED;
  }

  if (is_initialized()) {
    assert(is_linked(), "Class status is not consistent");
    result |= JVMTI_CLASS_STATUS_INITIALIZED;
  }
  if (is_in_error_state()) {
    result |= JVMTI_CLASS_STATUS_ERROR;
  }
  return result;
}

methodOop instanceKlass::method_at_itable(klassOop holder, int index, TRAPS) {
  itableOffsetEntry* ioe = (itableOffsetEntry*)start_of_itable();
  int method_table_offset_in_words = ioe->offset()/wordSize;
  int nof_interfaces = (method_table_offset_in_words - itable_offset_in_words())
                       / itableOffsetEntry::size();

  for (int cnt = 0 ; ; cnt ++, ioe ++) {
    // If the interface isn't implemented by the receiver class,
    // the VM should throw IncompatibleClassChangeError.
    if (cnt >= nof_interfaces) {
      THROW_OOP_0(vmSymbols::java_lang_IncompatibleClassChangeError());
    }

    klassOop ik = ioe->interface_klass();
    if (ik == holder) break;
  }

  itableMethodEntry* ime = ioe->first_method_entry(as_klassOop());
  methodOop m = ime[index].method();
  if (m == NULL) {
    THROW_OOP_0(vmSymbols::java_lang_AbstractMethodError());
  }
  return m;
}

// On-stack replacement stuff
void instanceKlass::add_osr_nmethod(nmethod* n) {
  // only one compilation can be active
  NEEDS_CLEANUP
  // This is a short non-blocking critical region, so the no safepoint check is ok.
  OsrList_lock->lock_without_safepoint_check();
  assert(n->is_osr_method(), "wrong kind of nmethod");
  n->set_osr_link(osr_nmethods_head());
  set_osr_nmethods_head(n);
  // Remember to unlock again
  OsrList_lock->unlock();
}


void instanceKlass::remove_osr_nmethod(nmethod* n) {
  // This is a short non-blocking critical region, so the no safepoint check is ok.
  OsrList_lock->lock_without_safepoint_check();
  assert(n->is_osr_method(), "wrong kind of nmethod");
  nmethod* last = NULL;
  nmethod* cur  = osr_nmethods_head();
  // Search for match
  while(cur != NULL && cur != n) {
    last = cur;
    cur = cur->osr_link();
  }
  if (cur == n) {
    if (last == NULL) {
      // Remove first element
      set_osr_nmethods_head(osr_nmethods_head()->osr_link());
    } else {
      last->set_osr_link(cur->osr_link());
    }
  }
  n->set_osr_link(NULL);
  // Remember to unlock again
  OsrList_lock->unlock();
}

nmethod* instanceKlass::lookup_osr_nmethod(const methodOop m, int bci) const {
  // This is a short non-blocking critical region, so the no safepoint check is ok.
  OsrList_lock->lock_without_safepoint_check();
  nmethod* osr = osr_nmethods_head();
  while (osr != NULL) {
    assert(osr->is_osr_method(), "wrong kind of nmethod found in chain");
    if (osr->method() == m &&
        (bci == InvocationEntryBci || osr->osr_entry_bci() == bci)) {
      // Found a match - return it.
      OsrList_lock->unlock();
      return osr;
    }
    osr = osr->osr_link();
  }
  OsrList_lock->unlock();
  return NULL;
}

// -----------------------------------------------------------------------------------------------------
#ifndef PRODUCT

// Printing

#define BULLET  " - "

void FieldPrinter::do_field(fieldDescriptor* fd) {
  _st->print(BULLET);
   if (fd->is_static() || (_obj == NULL)) {
     fd->print_on(_st);
     _st->cr();
   } else {
     fd->print_on_for(_st, _obj);
     _st->cr();
   }
}


void instanceKlass::oop_print_on(oop obj, outputStream* st) {
  Klass::oop_print_on(obj, st);

  if (as_klassOop() == SystemDictionary::String_klass()) {
    typeArrayOop value  = java_lang_String::value(obj);
    juint        offset = java_lang_String::offset(obj);
    juint        length = java_lang_String::length(obj);
    if (value != NULL &&
        value->is_typeArray() &&
        offset          <= (juint) value->length() &&
        offset + length <= (juint) value->length()) {
      st->print(BULLET"string: ");
      Handle h_obj(obj);
      java_lang_String::print(h_obj, st);
      st->cr();
      if (!WizardMode)  return;  // that is enough
    }
  }

  st->print_cr(BULLET"---- fields (total size %d words):", oop_size(obj));
  FieldPrinter print_nonstatic_field(st, obj);
  do_nonstatic_fields(&print_nonstatic_field);

  if (as_klassOop() == SystemDictionary::Class_klass()) {
    st->print(BULLET"signature: ");
    java_lang_Class::print_signature(obj, st);
    st->cr();
    klassOop mirrored_klass = java_lang_Class::as_klassOop(obj);
    st->print(BULLET"fake entry for mirror: ");
    mirrored_klass->print_value_on(st);
    st->cr();
    st->print(BULLET"fake entry resolved_constructor: ");
    methodOop ctor = java_lang_Class::resolved_constructor(obj);
    ctor->print_value_on(st);
    klassOop array_klass = java_lang_Class::array_klass(obj);
    st->cr();
    st->print(BULLET"fake entry for array: ");
    array_klass->print_value_on(st);
    st->cr();
  } else if (as_klassOop() == SystemDictionary::MethodType_klass()) {
    st->print(BULLET"signature: ");
    java_dyn_MethodType::print_signature(obj, st);
    st->cr();
  }
}

void instanceKlass::oop_print_value_on(oop obj, outputStream* st) {
  st->print("a ");
  name()->print_value_on(st);
  obj->print_address_on(st);
  if (as_klassOop() == SystemDictionary::String_klass()
      && java_lang_String::value(obj) != NULL) {
    ResourceMark rm;
    int len = java_lang_String::length(obj);
    int plen = (len < 24 ? len : 12);
    char* str = java_lang_String::as_utf8_string(obj, 0, plen);
    st->print(" = \"%s\"", str);
    if (len > plen)
      st->print("...[%d]", len);
  } else if (as_klassOop() == SystemDictionary::Class_klass()) {
    klassOop k = java_lang_Class::as_klassOop(obj);
    st->print(" = ");
    if (k != NULL) {
      k->print_value_on(st);
    } else {
      const char* tname = type2name(java_lang_Class::primitive_type(obj));
      st->print("%s", tname ? tname : "type?");
    }
  } else if (as_klassOop() == SystemDictionary::MethodType_klass()) {
    st->print(" = ");
    java_dyn_MethodType::print_signature(obj, st);
  } else if (java_lang_boxing_object::is_instance(obj)) {
    st->print(" = ");
    java_lang_boxing_object::print(obj, st);
  }
}

#endif // ndef PRODUCT

const char* instanceKlass::internal_name() const {
  return external_name();
}

// Verification

class VerifyFieldClosure: public OopClosure {
 protected:
  template <class T> void do_oop_work(T* p) {
    guarantee(Universe::heap()->is_in_closed_subset(p), "should be in heap");
    oop obj = oopDesc::load_decode_heap_oop(p);
    if (!obj->is_oop_or_null()) {
      tty->print_cr("Failed: " PTR_FORMAT " -> " PTR_FORMAT, p, (address)obj);
      Universe::print();
      guarantee(false, "boom");
    }
  }
 public:
  virtual void do_oop(oop* p)       { VerifyFieldClosure::do_oop_work(p); }
  virtual void do_oop(narrowOop* p) { VerifyFieldClosure::do_oop_work(p); }
};

void instanceKlass::oop_verify_on(oop obj, outputStream* st) {
  Klass::oop_verify_on(obj, st);
  VerifyFieldClosure blk;
  oop_oop_iterate(obj, &blk);
}

#ifndef PRODUCT

void instanceKlass::verify_class_klass_nonstatic_oop_maps(klassOop k) {
  // This verification code is disabled.  JDK_Version::is_gte_jdk14x_version()
  // cannot be called since this function is called before the VM is
  // able to determine what JDK version is running with.
  // The check below always is false since 1.4.
  return;

  // This verification code temporarily disabled for the 1.4
  // reflection implementation since java.lang.Class now has
  // Java-level instance fields. Should rewrite this to handle this
  // case.
  if (!(JDK_Version::is_gte_jdk14x_version() && UseNewReflection)) {
    // Verify that java.lang.Class instances have a fake oop field added.
    instanceKlass* ik = instanceKlass::cast(k);

    // Check that we have the right class
    static bool first_time = true;
    guarantee(k == SystemDictionary::Class_klass() && first_time, "Invalid verify of maps");
    first_time = false;
    const int extra = java_lang_Class::number_of_fake_oop_fields;
    guarantee(ik->nonstatic_field_size() == extra, "just checking");
    guarantee(ik->nonstatic_oop_map_count() == 1, "just checking");
    guarantee(ik->size_helper() == align_object_size(instanceOopDesc::header_size() + extra), "just checking");

    // Check that the map is (2,extra)
    int offset = java_lang_Class::klass_offset;

    OopMapBlock* map = ik->start_of_nonstatic_oop_maps();
    guarantee(map->offset() == offset && map->count() == (unsigned int) extra,
              "sanity");
  }
}

#endif // ndef PRODUCT

// JNIid class for jfieldIDs only
// Note to reviewers:
// These JNI functions are just moved over to column 1 and not changed
// in the compressed oops workspace.
JNIid::JNIid(klassOop holder, int offset, JNIid* next) {
  _holder = holder;
  _offset = offset;
  _next = next;
  debug_only(_is_static_field_id = false;)
}


JNIid* JNIid::find(int offset) {
  JNIid* current = this;
  while (current != NULL) {
    if (current->offset() == offset) return current;
    current = current->next();
  }
  return NULL;
}

void JNIid::oops_do(OopClosure* f) {
  for (JNIid* cur = this; cur != NULL; cur = cur->next()) {
    f->do_oop(cur->holder_addr());
  }
}

void JNIid::deallocate(JNIid* current) {
  while (current != NULL) {
    JNIid* next = current->next();
    delete current;
    current = next;
  }
}


void JNIid::verify(klassOop holder) {
  int first_field_offset  = instanceKlass::cast(holder)->offset_of_static_fields();
  int end_field_offset;
  end_field_offset = first_field_offset + (instanceKlass::cast(holder)->static_field_size() * wordSize);

  JNIid* current = this;
  while (current != NULL) {
    guarantee(current->holder() == holder, "Invalid klass in JNIid");
#ifdef ASSERT
    int o = current->offset();
    if (current->is_static_field_id()) {
      guarantee(o >= first_field_offset  && o < end_field_offset,  "Invalid static field offset in JNIid");
    }
#endif
    current = current->next();
  }
}


#ifdef ASSERT
void instanceKlass::set_init_state(ClassState state) {
  bool good_state = as_klassOop()->is_shared() ? (_init_state <= state)
                                               : (_init_state < state);
  assert(good_state || state == allocated, "illegal state transition");
  _init_state = state;
}
#endif


// RedefineClasses() support for previous versions:

// Add an information node that contains weak references to the
// interesting parts of the previous version of the_class.
// This is also where we clean out any unused weak references.
// Note that while we delete nodes from the _previous_versions
// array, we never delete the array itself until the klass is
// unloaded. The has_been_redefined() query depends on that fact.
//
void instanceKlass::add_previous_version(instanceKlassHandle ikh,
       BitMap* emcp_methods, int emcp_method_count) {
  assert(Thread::current()->is_VM_thread(),
         "only VMThread can add previous versions");

  if (_previous_versions == NULL) {
    // This is the first previous version so make some space.
    // Start with 2 elements under the assumption that the class
    // won't be redefined much.
    _previous_versions =  new (ResourceObj::C_HEAP)
                            GrowableArray<PreviousVersionNode *>(2, true);
  }

  // RC_TRACE macro has an embedded ResourceMark
  RC_TRACE(0x00000100, ("adding previous version ref for %s @%d, EMCP_cnt=%d",
    ikh->external_name(), _previous_versions->length(), emcp_method_count));
  constantPoolHandle cp_h(ikh->constants());
  jobject cp_ref;
  if (cp_h->is_shared()) {
    // a shared ConstantPool requires a regular reference; a weak
    // reference would be collectible
    cp_ref = JNIHandles::make_global(cp_h);
  } else {
    cp_ref = JNIHandles::make_weak_global(cp_h);
  }
  PreviousVersionNode * pv_node = NULL;
  objArrayOop old_methods = ikh->methods();

  if (emcp_method_count == 0) {
    // non-shared ConstantPool gets a weak reference
    pv_node = new PreviousVersionNode(cp_ref, !cp_h->is_shared(), NULL);
    RC_TRACE(0x00000400,
      ("add: all methods are obsolete; flushing any EMCP weak refs"));
  } else {
    int local_count = 0;
    GrowableArray<jweak>* method_refs = new (ResourceObj::C_HEAP)
      GrowableArray<jweak>(emcp_method_count, true);
    for (int i = 0; i < old_methods->length(); i++) {
      if (emcp_methods->at(i)) {
        // this old method is EMCP so save a weak ref
        methodOop old_method = (methodOop) old_methods->obj_at(i);
        methodHandle old_method_h(old_method);
        jweak method_ref = JNIHandles::make_weak_global(old_method_h);
        method_refs->append(method_ref);
        if (++local_count >= emcp_method_count) {
          // no more EMCP methods so bail out now
          break;
        }
      }
    }
    // non-shared ConstantPool gets a weak reference
    pv_node = new PreviousVersionNode(cp_ref, !cp_h->is_shared(), method_refs);
  }

  _previous_versions->append(pv_node);

  // Using weak references allows the interesting parts of previous
  // classes to be GC'ed when they are no longer needed. Since the
  // caller is the VMThread and we are at a safepoint, this is a good
  // time to clear out unused weak references.

  RC_TRACE(0x00000400, ("add: previous version length=%d",
    _previous_versions->length()));

  // skip the last entry since we just added it
  for (int i = _previous_versions->length() - 2; i >= 0; i--) {
    // check the previous versions array for a GC'ed weak refs
    pv_node = _previous_versions->at(i);
    cp_ref = pv_node->prev_constant_pool();
    assert(cp_ref != NULL, "cp ref was unexpectedly cleared");
    if (cp_ref == NULL) {
      delete pv_node;
      _previous_versions->remove_at(i);
      // Since we are traversing the array backwards, we don't have to
      // do anything special with the index.
      continue;  // robustness
    }

    constantPoolOop cp = (constantPoolOop)JNIHandles::resolve(cp_ref);
    if (cp == NULL) {
      // this entry has been GC'ed so remove it
      delete pv_node;
      _previous_versions->remove_at(i);
      // Since we are traversing the array backwards, we don't have to
      // do anything special with the index.
      continue;
    } else {
      RC_TRACE(0x00000400, ("add: previous version @%d is alive", i));
    }

    GrowableArray<jweak>* method_refs = pv_node->prev_EMCP_methods();
    if (method_refs != NULL) {
      RC_TRACE(0x00000400, ("add: previous methods length=%d",
        method_refs->length()));
      for (int j = method_refs->length() - 1; j >= 0; j--) {
        jweak method_ref = method_refs->at(j);
        assert(method_ref != NULL, "weak method ref was unexpectedly cleared");
        if (method_ref == NULL) {
          method_refs->remove_at(j);
          // Since we are traversing the array backwards, we don't have to
          // do anything special with the index.
          continue;  // robustness
        }

        methodOop method = (methodOop)JNIHandles::resolve(method_ref);
        if (method == NULL || emcp_method_count == 0) {
          // This method entry has been GC'ed or the current
          // RedefineClasses() call has made all methods obsolete
          // so remove it.
          JNIHandles::destroy_weak_global(method_ref);
          method_refs->remove_at(j);
        } else {
          // RC_TRACE macro has an embedded ResourceMark
          RC_TRACE(0x00000400,
            ("add: %s(%s): previous method @%d in version @%d is alive",
            method->name()->as_C_string(), method->signature()->as_C_string(),
            j, i));
        }
      }
    }
  }

  int obsolete_method_count = old_methods->length() - emcp_method_count;

  if (emcp_method_count != 0 && obsolete_method_count != 0 &&
      _previous_versions->length() > 1) {
    // We have a mix of obsolete and EMCP methods. If there is more
    // than the previous version that we just added, then we have to
    // clear out any matching EMCP method entries the hard way.
    int local_count = 0;
    for (int i = 0; i < old_methods->length(); i++) {
      if (!emcp_methods->at(i)) {
        // only obsolete methods are interesting
        methodOop old_method = (methodOop) old_methods->obj_at(i);
        symbolOop m_name = old_method->name();
        symbolOop m_signature = old_method->signature();

        // skip the last entry since we just added it
        for (int j = _previous_versions->length() - 2; j >= 0; j--) {
          // check the previous versions array for a GC'ed weak refs
          pv_node = _previous_versions->at(j);
          cp_ref = pv_node->prev_constant_pool();
          assert(cp_ref != NULL, "cp ref was unexpectedly cleared");
          if (cp_ref == NULL) {
            delete pv_node;
            _previous_versions->remove_at(j);
            // Since we are traversing the array backwards, we don't have to
            // do anything special with the index.
            continue;  // robustness
          }

          constantPoolOop cp = (constantPoolOop)JNIHandles::resolve(cp_ref);
          if (cp == NULL) {
            // this entry has been GC'ed so remove it
            delete pv_node;
            _previous_versions->remove_at(j);
            // Since we are traversing the array backwards, we don't have to
            // do anything special with the index.
            continue;
          }

          GrowableArray<jweak>* method_refs = pv_node->prev_EMCP_methods();
          if (method_refs == NULL) {
            // We have run into a PreviousVersion generation where
            // all methods were made obsolete during that generation's
            // RedefineClasses() operation. At the time of that
            // operation, all EMCP methods were flushed so we don't
            // have to go back any further.
            //
            // A NULL method_refs is different than an empty method_refs.
            // We cannot infer any optimizations about older generations
            // from an empty method_refs for the current generation.
            break;
          }

          for (int k = method_refs->length() - 1; k >= 0; k--) {
            jweak method_ref = method_refs->at(k);
            assert(method_ref != NULL,
              "weak method ref was unexpectedly cleared");
            if (method_ref == NULL) {
              method_refs->remove_at(k);
              // Since we are traversing the array backwards, we don't
              // have to do anything special with the index.
              continue;  // robustness
            }

            methodOop method = (methodOop)JNIHandles::resolve(method_ref);
            if (method == NULL) {
              // this method entry has been GC'ed so skip it
              JNIHandles::destroy_weak_global(method_ref);
              method_refs->remove_at(k);
              continue;
            }

            if (method->name() == m_name &&
                method->signature() == m_signature) {
              // The current RedefineClasses() call has made all EMCP
              // versions of this method obsolete so mark it as obsolete
              // and remove the weak ref.
              RC_TRACE(0x00000400,
                ("add: %s(%s): flush obsolete method @%d in version @%d",
                m_name->as_C_string(), m_signature->as_C_string(), k, j));

              method->set_is_obsolete();
              JNIHandles::destroy_weak_global(method_ref);
              method_refs->remove_at(k);
              break;
            }
          }

          // The previous loop may not find a matching EMCP method, but
          // that doesn't mean that we can optimize and not go any
          // further back in the PreviousVersion generations. The EMCP
          // method for this generation could have already been GC'ed,
          // but there still may be an older EMCP method that has not
          // been GC'ed.
        }

        if (++local_count >= obsolete_method_count) {
          // no more obsolete methods so bail out now
          break;
        }
      }
    }
  }
} // end add_previous_version()


// Determine if instanceKlass has a previous version.
bool instanceKlass::has_previous_version() const {
  if (_previous_versions == NULL) {
    // no previous versions array so answer is easy
    return false;
  }

  for (int i = _previous_versions->length() - 1; i >= 0; i--) {
    // Check the previous versions array for an info node that hasn't
    // been GC'ed
    PreviousVersionNode * pv_node = _previous_versions->at(i);

    jobject cp_ref = pv_node->prev_constant_pool();
    assert(cp_ref != NULL, "cp reference was unexpectedly cleared");
    if (cp_ref == NULL) {
      continue;  // robustness
    }

    constantPoolOop cp = (constantPoolOop)JNIHandles::resolve(cp_ref);
    if (cp != NULL) {
      // we have at least one previous version
      return true;
    }

    // We don't have to check the method refs. If the constant pool has
    // been GC'ed then so have the methods.
  }

  // all of the underlying nodes' info has been GC'ed
  return false;
} // end has_previous_version()

methodOop instanceKlass::method_with_idnum(int idnum) {
  methodOop m = NULL;
  if (idnum < methods()->length()) {
    m = (methodOop) methods()->obj_at(idnum);
  }
  if (m == NULL || m->method_idnum() != idnum) {
    for (int index = 0; index < methods()->length(); ++index) {
      m = (methodOop) methods()->obj_at(index);
      if (m->method_idnum() == idnum) {
        return m;
      }
    }
  }
  return m;
}


// Set the annotation at 'idnum' to 'anno'.
// We don't want to create or extend the array if 'anno' is NULL, since that is the
// default value.  However, if the array exists and is long enough, we must set NULL values.
void instanceKlass::set_methods_annotations_of(int idnum, typeArrayOop anno, objArrayOop* md_p) {
  objArrayOop md = *md_p;
  if (md != NULL && md->length() > idnum) {
    md->obj_at_put(idnum, anno);
  } else if (anno != NULL) {
    // create the array
    int length = MAX2(idnum+1, (int)_idnum_allocated_count);
    md = oopFactory::new_system_objArray(length, Thread::current());
    if (*md_p != NULL) {
      // copy the existing entries
      for (int index = 0; index < (*md_p)->length(); index++) {
        md->obj_at_put(index, (*md_p)->obj_at(index));
      }
    }
    set_annotations(md, md_p);
    md->obj_at_put(idnum, anno);
  } // if no array and idnum isn't included there is nothing to do
}

// Construct a PreviousVersionNode entry for the array hung off
// the instanceKlass.
PreviousVersionNode::PreviousVersionNode(jobject prev_constant_pool,
  bool prev_cp_is_weak, GrowableArray<jweak>* prev_EMCP_methods) {

  _prev_constant_pool = prev_constant_pool;
  _prev_cp_is_weak = prev_cp_is_weak;
  _prev_EMCP_methods = prev_EMCP_methods;
}


// Destroy a PreviousVersionNode
PreviousVersionNode::~PreviousVersionNode() {
  if (_prev_constant_pool != NULL) {
    if (_prev_cp_is_weak) {
      JNIHandles::destroy_weak_global(_prev_constant_pool);
    } else {
      JNIHandles::destroy_global(_prev_constant_pool);
    }
  }

  if (_prev_EMCP_methods != NULL) {
    for (int i = _prev_EMCP_methods->length() - 1; i >= 0; i--) {
      jweak method_ref = _prev_EMCP_methods->at(i);
      if (method_ref != NULL) {
        JNIHandles::destroy_weak_global(method_ref);
      }
    }
    delete _prev_EMCP_methods;
  }
}


// Construct a PreviousVersionInfo entry
PreviousVersionInfo::PreviousVersionInfo(PreviousVersionNode *pv_node) {
  _prev_constant_pool_handle = constantPoolHandle();  // NULL handle
  _prev_EMCP_method_handles = NULL;

  jobject cp_ref = pv_node->prev_constant_pool();
  assert(cp_ref != NULL, "constant pool ref was unexpectedly cleared");
  if (cp_ref == NULL) {
    return;  // robustness
  }

  constantPoolOop cp = (constantPoolOop)JNIHandles::resolve(cp_ref);
  if (cp == NULL) {
    // Weak reference has been GC'ed. Since the constant pool has been
    // GC'ed, the methods have also been GC'ed.
    return;
  }

  // make the constantPoolOop safe to return
  _prev_constant_pool_handle = constantPoolHandle(cp);

  GrowableArray<jweak>* method_refs = pv_node->prev_EMCP_methods();
  if (method_refs == NULL) {
    // the instanceKlass did not have any EMCP methods
    return;
  }

  _prev_EMCP_method_handles = new GrowableArray<methodHandle>(10);

  int n_methods = method_refs->length();
  for (int i = 0; i < n_methods; i++) {
    jweak method_ref = method_refs->at(i);
    assert(method_ref != NULL, "weak method ref was unexpectedly cleared");
    if (method_ref == NULL) {
      continue;  // robustness
    }

    methodOop method = (methodOop)JNIHandles::resolve(method_ref);
    if (method == NULL) {
      // this entry has been GC'ed so skip it
      continue;
    }

    // make the methodOop safe to return
    _prev_EMCP_method_handles->append(methodHandle(method));
  }
}


// Destroy a PreviousVersionInfo
PreviousVersionInfo::~PreviousVersionInfo() {
  // Since _prev_EMCP_method_handles is not C-heap allocated, we
  // don't have to delete it.
}


// Construct a helper for walking the previous versions array
PreviousVersionWalker::PreviousVersionWalker(instanceKlass *ik) {
  _previous_versions = ik->previous_versions();
  _current_index = 0;
  // _hm needs no initialization
  _current_p = NULL;
}


// Destroy a PreviousVersionWalker
PreviousVersionWalker::~PreviousVersionWalker() {
  // Delete the current info just in case the caller didn't walk to
  // the end of the previous versions list. No harm if _current_p is
  // already NULL.
  delete _current_p;

  // When _hm is destroyed, all the Handles returned in
  // PreviousVersionInfo objects will be destroyed.
  // Also, after this destructor is finished it will be
  // safe to delete the GrowableArray allocated in the
  // PreviousVersionInfo objects.
}


// Return the interesting information for the next previous version
// of the klass. Returns NULL if there are no more previous versions.
PreviousVersionInfo* PreviousVersionWalker::next_previous_version() {
  if (_previous_versions == NULL) {
    // no previous versions so nothing to return
    return NULL;
  }

  delete _current_p;  // cleanup the previous info for the caller
  _current_p = NULL;  // reset to NULL so we don't delete same object twice

  int length = _previous_versions->length();

  while (_current_index < length) {
    PreviousVersionNode * pv_node = _previous_versions->at(_current_index++);
    PreviousVersionInfo * pv_info = new (ResourceObj::C_HEAP)
                                          PreviousVersionInfo(pv_node);

    constantPoolHandle cp_h = pv_info->prev_constant_pool_handle();
    if (cp_h.is_null()) {
      delete pv_info;

      // The underlying node's info has been GC'ed so try the next one.
      // We don't have to check the methods. If the constant pool has
      // GC'ed then so have the methods.
      continue;
    }

    // Found a node with non GC'ed info so return it. The caller will
    // need to delete pv_info when they are done with it.
    _current_p = pv_info;
    return pv_info;
  }

  // all of the underlying nodes' info has been GC'ed
  return NULL;
} // end next_previous_version()
