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
# include "incls/_systemDictionary.cpp.incl"


Dictionary*       SystemDictionary::_dictionary = NULL;
PlaceholderTable* SystemDictionary::_placeholders = NULL;
Dictionary*       SystemDictionary::_shared_dictionary = NULL;
LoaderConstraintTable* SystemDictionary::_loader_constraints = NULL;
ResolutionErrorTable* SystemDictionary::_resolution_errors = NULL;
SymbolPropertyTable* SystemDictionary::_invoke_method_table = NULL;


int         SystemDictionary::_number_of_modifications = 0;

oop         SystemDictionary::_system_loader_lock_obj     =  NULL;

klassOop    SystemDictionary::_well_known_klasses[SystemDictionary::WKID_LIMIT]
                                                          =  { NULL /*, NULL...*/ };

klassOop    SystemDictionary::_box_klasses[T_VOID+1]      =  { NULL /*, NULL...*/ };

oop         SystemDictionary::_java_system_loader         =  NULL;

bool        SystemDictionary::_has_loadClassInternal      =  false;
bool        SystemDictionary::_has_checkPackageAccess     =  false;

// lazily initialized klass variables
volatile klassOop    SystemDictionary::_abstract_ownable_synchronizer_klass = NULL;


// ----------------------------------------------------------------------------
// Java-level SystemLoader

oop SystemDictionary::java_system_loader() {
  return _java_system_loader;
}

void SystemDictionary::compute_java_system_loader(TRAPS) {
  KlassHandle system_klass(THREAD, WK_KLASS(classloader_klass));
  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result,
                         KlassHandle(THREAD, WK_KLASS(classloader_klass)),
                         vmSymbolHandles::getSystemClassLoader_name(),
                         vmSymbolHandles::void_classloader_signature(),
                         CHECK);

  _java_system_loader = (oop)result.get_jobject();
}


// ----------------------------------------------------------------------------
// debugging

#ifdef ASSERT

// return true if class_name contains no '.' (internal format is '/')
bool SystemDictionary::is_internal_format(symbolHandle class_name) {
  if (class_name.not_null()) {
    ResourceMark rm;
    char* name = class_name->as_C_string();
    return strchr(name, '.') == NULL;
  } else {
    return true;
  }
}

#endif

// ----------------------------------------------------------------------------
// Parallel class loading check

bool SystemDictionary::is_parallelCapable(Handle class_loader) {
  if (UnsyncloadClass || class_loader.is_null()) return true;
  if (AlwaysLockClassLoader) return false;
  return java_lang_Class::parallelCapable(class_loader());
}
// ----------------------------------------------------------------------------
// Resolving of classes

// Forwards to resolve_or_null

klassOop SystemDictionary::resolve_or_fail(symbolHandle class_name, Handle class_loader, Handle protection_domain, bool throw_error, TRAPS) {
  klassOop klass = resolve_or_null(class_name, class_loader, protection_domain, THREAD);
  if (HAS_PENDING_EXCEPTION || klass == NULL) {
    KlassHandle k_h(THREAD, klass);
    // can return a null klass
    klass = handle_resolution_exception(class_name, class_loader, protection_domain, throw_error, k_h, THREAD);
  }
  return klass;
}

klassOop SystemDictionary::handle_resolution_exception(symbolHandle class_name, Handle class_loader, Handle protection_domain, bool throw_error, KlassHandle klass_h, TRAPS) {
  if (HAS_PENDING_EXCEPTION) {
    // If we have a pending exception we forward it to the caller, unless throw_error is true,
    // in which case we have to check whether the pending exception is a ClassNotFoundException,
    // and if so convert it to a NoClassDefFoundError
    // And chain the original ClassNotFoundException
    if (throw_error && PENDING_EXCEPTION->is_a(SystemDictionary::classNotFoundException_klass())) {
      ResourceMark rm(THREAD);
      assert(klass_h() == NULL, "Should not have result with exception pending");
      Handle e(THREAD, PENDING_EXCEPTION);
      CLEAR_PENDING_EXCEPTION;
      THROW_MSG_CAUSE_0(vmSymbols::java_lang_NoClassDefFoundError(), class_name->as_C_string(), e);
    } else {
      return NULL;
    }
  }
  // Class not found, throw appropriate error or exception depending on value of throw_error
  if (klass_h() == NULL) {
    ResourceMark rm(THREAD);
    if (throw_error) {
      THROW_MSG_0(vmSymbols::java_lang_NoClassDefFoundError(), class_name->as_C_string());
    } else {
      THROW_MSG_0(vmSymbols::java_lang_ClassNotFoundException(), class_name->as_C_string());
    }
  }
  return (klassOop)klass_h();
}


klassOop SystemDictionary::resolve_or_fail(symbolHandle class_name,
                                           bool throw_error, TRAPS)
{
  return resolve_or_fail(class_name, Handle(), Handle(), throw_error, THREAD);
}


// Forwards to resolve_instance_class_or_null

klassOop SystemDictionary::resolve_or_null(symbolHandle class_name, Handle class_loader, Handle protection_domain, TRAPS) {
  assert(!THREAD->is_Compiler_thread(), "Can not load classes with the Compiler thread");
  if (FieldType::is_array(class_name())) {
    return resolve_array_class_or_null(class_name, class_loader, protection_domain, CHECK_NULL);
  } else {
    return resolve_instance_class_or_null(class_name, class_loader, protection_domain, CHECK_NULL);
  }
}

klassOop SystemDictionary::resolve_or_null(symbolHandle class_name, TRAPS) {
  return resolve_or_null(class_name, Handle(), Handle(), THREAD);
}

// Forwards to resolve_instance_class_or_null

klassOop SystemDictionary::resolve_array_class_or_null(symbolHandle class_name,
                                                       Handle class_loader,
                                                       Handle protection_domain,
                                                       TRAPS) {
  assert(FieldType::is_array(class_name()), "must be array");
  jint dimension;
  symbolOop object_key;
  klassOop k = NULL;
  // dimension and object_key are assigned as a side-effect of this call
  BasicType t = FieldType::get_array_info(class_name(),
                                          &dimension,
                                          &object_key,
                                          CHECK_NULL);

  if (t == T_OBJECT) {
    symbolHandle h_key(THREAD, object_key);
    // naked oop "k" is OK here -- we assign back into it
    k = SystemDictionary::resolve_instance_class_or_null(h_key,
                                                         class_loader,
                                                         protection_domain,
                                                         CHECK_NULL);
    if (k != NULL) {
      k = Klass::cast(k)->array_klass(dimension, CHECK_NULL);
    }
  } else {
    k = Universe::typeArrayKlassObj(t);
    k = typeArrayKlass::cast(k)->array_klass(dimension, CHECK_NULL);
  }
  return k;
}


// Must be called for any super-class or super-interface resolution
// during class definition to allow class circularity checking
// super-interface callers:
//    parse_interfaces - for defineClass & jvmtiRedefineClasses
// super-class callers:
//   ClassFileParser - for defineClass & jvmtiRedefineClasses
//   load_shared_class - while loading a class from shared archive
//   resolve_instance_class_or_null:
//     via: handle_parallel_super_load
//      when resolving a class that has an existing placeholder with
//      a saved superclass [i.e. a defineClass is currently in progress]
//      if another thread is trying to resolve the class, it must do
//      super-class checks on its own thread to catch class circularity
// This last call is critical in class circularity checking for cases
// where classloading is delegated to different threads and the
// classloader lock is released.
// Take the case: Base->Super->Base
//   1. If thread T1 tries to do a defineClass of class Base
//    resolve_super_or_fail creates placeholder: T1, Base (super Super)
//   2. resolve_instance_class_or_null does not find SD or placeholder for Super
//    so it tries to load Super
//   3. If we load the class internally, or user classloader uses same thread
//      loadClassFromxxx or defineClass via parseClassFile Super ...
//      3.1 resolve_super_or_fail creates placeholder: T1, Super (super Base)
//      3.3 resolve_instance_class_or_null Base, finds placeholder for Base
//      3.4 calls resolve_super_or_fail Base
//      3.5 finds T1,Base -> throws class circularity
//OR 4. If T2 tries to resolve Super via defineClass Super ...
//      4.1 resolve_super_or_fail creates placeholder: T2, Super (super Base)
//      4.2 resolve_instance_class_or_null Base, finds placeholder for Base (super Super)
//      4.3 calls resolve_super_or_fail Super in parallel on own thread T2
//      4.4 finds T2, Super -> throws class circularity
// Must be called, even if superclass is null, since this is
// where the placeholder entry is created which claims this
// thread is loading this class/classloader.
klassOop SystemDictionary::resolve_super_or_fail(symbolHandle child_name,
                                                 symbolHandle class_name,
                                                 Handle class_loader,
                                                 Handle protection_domain,
                                                 bool is_superclass,
                                                 TRAPS) {

  // Try to get one of the well-known klasses.
  // They are trusted, and do not participate in circularities.
  if (LinkWellKnownClasses) {
    klassOop k = find_well_known_klass(class_name());
    if (k != NULL) {
      return k;
    }
  }

  // Double-check, if child class is already loaded, just return super-class,interface
  // Don't add a placedholder if already loaded, i.e. already in system dictionary
  // Make sure there's a placeholder for the *child* before resolving.
  // Used as a claim that this thread is currently loading superclass/classloader
  // Used here for ClassCircularity checks and also for heap verification
  // (every instanceKlass in the heap needs to be in the system dictionary
  // or have a placeholder).
  // Must check ClassCircularity before checking if super class is already loaded
  //
  // We might not already have a placeholder if this child_name was
  // first seen via resolve_from_stream (jni_DefineClass or JVM_DefineClass);
  // the name of the class might not be known until the stream is actually
  // parsed.
  // Bugs 4643874, 4715493
  // compute_hash can have a safepoint

  unsigned int d_hash = dictionary()->compute_hash(child_name, class_loader);
  int d_index = dictionary()->hash_to_index(d_hash);
  unsigned int p_hash = placeholders()->compute_hash(child_name, class_loader);
  int p_index = placeholders()->hash_to_index(p_hash);
  // can't throw error holding a lock
  bool child_already_loaded = false;
  bool throw_circularity_error = false;
  {
    MutexLocker mu(SystemDictionary_lock, THREAD);
    klassOop childk = find_class(d_index, d_hash, child_name, class_loader);
    klassOop quicksuperk;
    // to support // loading: if child done loading, just return superclass
    // if class_name, & class_loader don't match:
    // if initial define, SD update will give LinkageError
    // if redefine: compare_class_versions will give HIERARCHY_CHANGED
    // so we don't throw an exception here.
    // see: nsk redefclass014 & java.lang.instrument Instrument032
    if ((childk != NULL ) && (is_superclass) &&
       ((quicksuperk = instanceKlass::cast(childk)->super()) != NULL) &&

         ((Klass::cast(quicksuperk)->name() == class_name()) &&
            (Klass::cast(quicksuperk)->class_loader()  == class_loader()))) {
           return quicksuperk;
    } else {
      PlaceholderEntry* probe = placeholders()->get_entry(p_index, p_hash, child_name, class_loader);
      if (probe && probe->check_seen_thread(THREAD, PlaceholderTable::LOAD_SUPER)) {
          throw_circularity_error = true;
      }
    }
    if (!throw_circularity_error) {
      PlaceholderEntry* newprobe = placeholders()->find_and_add(p_index, p_hash, child_name, class_loader, PlaceholderTable::LOAD_SUPER, class_name, THREAD);
    }
  }
  if (throw_circularity_error) {
      ResourceMark rm(THREAD);
      THROW_MSG_0(vmSymbols::java_lang_ClassCircularityError(), child_name->as_C_string());
  }

// java.lang.Object should have been found above
  assert(class_name() != NULL, "null super class for resolving");
  // Resolve the super class or interface, check results on return
  klassOop superk = NULL;
  superk = SystemDictionary::resolve_or_null(class_name,
                                                 class_loader,
                                                 protection_domain,
                                                 THREAD);

  KlassHandle superk_h(THREAD, superk);

  // Note: clean up of placeholders currently in callers of
  // resolve_super_or_fail - either at update_dictionary time
  // or on error
  {
  MutexLocker mu(SystemDictionary_lock, THREAD);
   PlaceholderEntry* probe = placeholders()->get_entry(p_index, p_hash, child_name, class_loader);
   if (probe != NULL) {
      probe->remove_seen_thread(THREAD, PlaceholderTable::LOAD_SUPER);
   }
  }
  if (HAS_PENDING_EXCEPTION || superk_h() == NULL) {
    // can null superk
    superk_h = KlassHandle(THREAD, handle_resolution_exception(class_name, class_loader, protection_domain, true, superk_h, THREAD));
  }

  return superk_h();
}

void SystemDictionary::validate_protection_domain(instanceKlassHandle klass,
                                                  Handle class_loader,
                                                  Handle protection_domain,
                                                  TRAPS) {
  if(!has_checkPackageAccess()) return;

  // Now we have to call back to java to check if the initating class has access
  JavaValue result(T_VOID);
  if (TraceProtectionDomainVerification) {
    // Print out trace information
    tty->print_cr("Checking package access");
    tty->print(" - class loader:      "); class_loader()->print_value_on(tty);      tty->cr();
    tty->print(" - protection domain: "); protection_domain()->print_value_on(tty); tty->cr();
    tty->print(" - loading:           "); klass()->print_value_on(tty);             tty->cr();
  }

  assert(class_loader() != NULL, "should not have non-null protection domain for null classloader");

  KlassHandle system_loader(THREAD, SystemDictionary::classloader_klass());
  JavaCalls::call_special(&result,
                         class_loader,
                         system_loader,
                         vmSymbolHandles::checkPackageAccess_name(),
                         vmSymbolHandles::class_protectiondomain_signature(),
                         Handle(THREAD, klass->java_mirror()),
                         protection_domain,
                         THREAD);

  if (TraceProtectionDomainVerification) {
    if (HAS_PENDING_EXCEPTION) {
      tty->print_cr(" -> DENIED !!!!!!!!!!!!!!!!!!!!!");
    } else {
     tty->print_cr(" -> granted");
    }
    tty->cr();
  }

  if (HAS_PENDING_EXCEPTION) return;

  // If no exception has been thrown, we have validated the protection domain
  // Insert the protection domain of the initiating class into the set.
  {
    // We recalculate the entry here -- we've called out to java since
    // the last time it was calculated.
    symbolHandle kn(THREAD, klass->name());
    unsigned int d_hash = dictionary()->compute_hash(kn, class_loader);
    int d_index = dictionary()->hash_to_index(d_hash);

    MutexLocker mu(SystemDictionary_lock, THREAD);
    {
      // Note that we have an entry, and entries can be deleted only during GC,
      // so we cannot allow GC to occur while we're holding this entry.

      // We're using a No_Safepoint_Verifier to catch any place where we
      // might potentially do a GC at all.
      // SystemDictionary::do_unloading() asserts that classes are only
      // unloaded at a safepoint.
      No_Safepoint_Verifier nosafepoint;
      dictionary()->add_protection_domain(d_index, d_hash, klass, class_loader,
                                          protection_domain, THREAD);
    }
  }
}

// We only get here if this thread finds that another thread
// has already claimed the placeholder token for the current operation,
// but that other thread either never owned or gave up the
// object lock
// Waits on SystemDictionary_lock to indicate placeholder table updated
// On return, caller must recheck placeholder table state
//
// We only get here if
//  1) custom classLoader, i.e. not bootstrap classloader
//  2) UnsyncloadClass not set
//  3) custom classLoader has broken the class loader objectLock
//     so another thread got here in parallel
//
// lockObject must be held.
// Complicated dance due to lock ordering:
// Must first release the classloader object lock to
// allow initial definer to complete the class definition
// and to avoid deadlock
// Reclaim classloader lock object with same original recursion count
// Must release SystemDictionary_lock after notify, since
// class loader lock must be claimed before SystemDictionary_lock
// to prevent deadlocks
//
// The notify allows applications that did an untimed wait() on
// the classloader object lock to not hang.
void SystemDictionary::double_lock_wait(Handle lockObject, TRAPS) {
  assert_lock_strong(SystemDictionary_lock);

  bool calledholdinglock
      = ObjectSynchronizer::current_thread_holds_lock((JavaThread*)THREAD, lockObject);
  assert(calledholdinglock,"must hold lock for notify");
  assert((!(lockObject() == _system_loader_lock_obj) && !is_parallelCapable(lockObject)), "unexpected double_lock_wait");
  ObjectSynchronizer::notifyall(lockObject, THREAD);
  intptr_t recursions =  ObjectSynchronizer::complete_exit(lockObject, THREAD);
  SystemDictionary_lock->wait();
  SystemDictionary_lock->unlock();
  ObjectSynchronizer::reenter(lockObject, recursions, THREAD);
  SystemDictionary_lock->lock();
}

// If the class in is in the placeholder table, class loading is in progress
// For cases where the application changes threads to load classes, it
// is critical to ClassCircularity detection that we try loading
// the superclass on the same thread internally, so we do parallel
// super class loading here.
// This also is critical in cases where the original thread gets stalled
// even in non-circularity situations.
// Note: only one thread can define the class, but multiple can resolve
// Note: must call resolve_super_or_fail even if null super -
// to force placeholder entry creation for this class for circularity detection
// Caller must check for pending exception
// Returns non-null klassOop if other thread has completed load
// and we are done,
// If return null klassOop and no pending exception, the caller must load the class
instanceKlassHandle SystemDictionary::handle_parallel_super_load(
    symbolHandle name, symbolHandle superclassname, Handle class_loader,
    Handle protection_domain, Handle lockObject, TRAPS) {

  instanceKlassHandle nh = instanceKlassHandle(); // null Handle
  unsigned int d_hash = dictionary()->compute_hash(name, class_loader);
  int d_index = dictionary()->hash_to_index(d_hash);
  unsigned int p_hash = placeholders()->compute_hash(name, class_loader);
  int p_index = placeholders()->hash_to_index(p_hash);

  // superk is not used, resolve_super called for circularity check only
  // This code is reached in two situations. One if this thread
  // is loading the same class twice (e.g. ClassCircularity, or
  // java.lang.instrument).
  // The second is if another thread started the resolve_super first
  // and has not yet finished.
  // In both cases the original caller will clean up the placeholder
  // entry on error.
  klassOop superk = SystemDictionary::resolve_super_or_fail(name,
                                                          superclassname,
                                                          class_loader,
                                                          protection_domain,
                                                          true,
                                                          CHECK_(nh));
  // We don't redefine the class, so we just need to clean up if there
  // was not an error (don't want to modify any system dictionary
  // data structures).
  {
    MutexLocker mu(SystemDictionary_lock, THREAD);
    placeholders()->find_and_remove(p_index, p_hash, name, class_loader, THREAD);
    SystemDictionary_lock->notify_all();
  }

  // parallelCapable class loaders do NOT wait for parallel superclass loads to complete
  // Serial class loaders and bootstrap classloader do wait for superclass loads
 if (!class_loader.is_null() && is_parallelCapable(class_loader)) {
    MutexLocker mu(SystemDictionary_lock, THREAD);
    // Check if classloading completed while we were loading superclass or waiting
    klassOop check = find_class(d_index, d_hash, name, class_loader);
    if (check != NULL) {
      // Klass is already loaded, so just return it
      return(instanceKlassHandle(THREAD, check));
    } else {
      return nh;
    }
  }

  // must loop to both handle other placeholder updates
  // and spurious notifications
  bool super_load_in_progress = true;
  PlaceholderEntry* placeholder;
  while (super_load_in_progress) {
    MutexLocker mu(SystemDictionary_lock, THREAD);
    // Check if classloading completed while we were loading superclass or waiting
    klassOop check = find_class(d_index, d_hash, name, class_loader);
    if (check != NULL) {
      // Klass is already loaded, so just return it
      return(instanceKlassHandle(THREAD, check));
    } else {
      placeholder = placeholders()->get_entry(p_index, p_hash, name, class_loader);
      if (placeholder && placeholder->super_load_in_progress() ){
        // Before UnsyncloadClass:
        // We only get here if the application has released the
        // classloader lock when another thread was in the middle of loading a
        // superclass/superinterface for this class, and now
        // this thread is also trying to load this class.
        // To minimize surprises, the first thread that started to
        // load a class should be the one to complete the loading
        // with the classfile it initially expected.
        // This logic has the current thread wait once it has done
        // all the superclass/superinterface loading it can, until
        // the original thread completes the class loading or fails
        // If it completes we will use the resulting instanceKlass
        // which we will find below in the systemDictionary.
        // We also get here for parallel bootstrap classloader
        if (class_loader.is_null()) {
          SystemDictionary_lock->wait();
        } else {
          double_lock_wait(lockObject, THREAD);
        }
      } else {
        // If not in SD and not in PH, other thread's load must have failed
        super_load_in_progress = false;
      }
    }
  }
  return (nh);
}


klassOop SystemDictionary::resolve_instance_class_or_null(symbolHandle class_name, Handle class_loader, Handle protection_domain, TRAPS) {
  assert(class_name.not_null() && !FieldType::is_array(class_name()), "invalid class name");
  // First check to see if we should remove wrapping L and ;
  symbolHandle name;
  if (FieldType::is_obj(class_name())) {
    ResourceMark rm(THREAD);
    // Ignore wrapping L and ;.
    name = oopFactory::new_symbol_handle(class_name()->as_C_string() + 1, class_name()->utf8_length() - 2, CHECK_NULL);
  } else {
    name = class_name;
  }

  // UseNewReflection
  // Fix for 4474172; see evaluation for more details
  class_loader = Handle(THREAD, java_lang_ClassLoader::non_reflection_class_loader(class_loader()));

  // Do lookup to see if class already exist and the protection domain
  // has the right access
  unsigned int d_hash = dictionary()->compute_hash(name, class_loader);
  int d_index = dictionary()->hash_to_index(d_hash);
  klassOop probe = dictionary()->find(d_index, d_hash, name, class_loader,
                                      protection_domain, THREAD);
  if (probe != NULL) return probe;


  // Non-bootstrap class loaders will call out to class loader and
  // define via jvm/jni_DefineClass which will acquire the
  // class loader object lock to protect against multiple threads
  // defining the class in parallel by accident.
  // This lock must be acquired here so the waiter will find
  // any successful result in the SystemDictionary and not attempt
  // the define
  // ParallelCapable Classloaders and the bootstrap classloader,
  // or all classloaders with UnsyncloadClass do not acquire lock here
  bool DoObjectLock = true;
  if (is_parallelCapable(class_loader)) {
    DoObjectLock = false;
  }

  unsigned int p_hash = placeholders()->compute_hash(name, class_loader);
  int p_index = placeholders()->hash_to_index(p_hash);

  // Class is not in SystemDictionary so we have to do loading.
  // Make sure we are synchronized on the class loader before we proceed
  Handle lockObject = compute_loader_lock_object(class_loader, THREAD);
  check_loader_lock_contention(lockObject, THREAD);
  ObjectLocker ol(lockObject, THREAD, DoObjectLock);

  // Check again (after locking) if class already exist in SystemDictionary
  bool class_has_been_loaded   = false;
  bool super_load_in_progress  = false;
  bool havesupername = false;
  instanceKlassHandle k;
  PlaceholderEntry* placeholder;
  symbolHandle superclassname;

  {
    MutexLocker mu(SystemDictionary_lock, THREAD);
    klassOop check = find_class(d_index, d_hash, name, class_loader);
    if (check != NULL) {
      // Klass is already loaded, so just return it
      class_has_been_loaded = true;
      k = instanceKlassHandle(THREAD, check);
    } else {
      placeholder = placeholders()->get_entry(p_index, p_hash, name, class_loader);
      if (placeholder && placeholder->super_load_in_progress()) {
         super_load_in_progress = true;
         if (placeholder->havesupername() == true) {
           superclassname = symbolHandle(THREAD, placeholder->supername());
           havesupername = true;
         }
      }
    }
  }

  // If the class in is in the placeholder table, class loading is in progress
  if (super_load_in_progress && havesupername==true) {
    k = SystemDictionary::handle_parallel_super_load(name, superclassname,
        class_loader, protection_domain, lockObject, THREAD);
    if (HAS_PENDING_EXCEPTION) {
      return NULL;
    }
    if (!k.is_null()) {
      class_has_been_loaded = true;
    }
  }

  if (!class_has_been_loaded) {

    // add placeholder entry to record loading instance class
    // Five cases:
    // All cases need to prevent modifying bootclasssearchpath
    // in parallel with a classload of same classname
    // Redefineclasses uses existence of the placeholder for the duration
    // of the class load to prevent concurrent redefinition of not completely
    // defined classes.
    // case 1. traditional classloaders that rely on the classloader object lock
    //   - no other need for LOAD_INSTANCE
    // case 2. traditional classloaders that break the classloader object lock
    //    as a deadlock workaround. Detection of this case requires that
    //    this check is done while holding the classloader object lock,
    //    and that lock is still held when calling classloader's loadClass.
    //    For these classloaders, we ensure that the first requestor
    //    completes the load and other requestors wait for completion.
    // case 3. UnsyncloadClass - don't use objectLocker
    //    With this flag, we allow parallel classloading of a
    //    class/classloader pair
    // case4. Bootstrap classloader - don't own objectLocker
    //    This classloader supports parallelism at the classloader level,
    //    but only allows a single load of a class/classloader pair.
    //    No performance benefit and no deadlock issues.
    // case 5. parallelCapable user level classloaders - without objectLocker
    //    Allow parallel classloading of a class/classloader pair
    symbolHandle nullsymbolHandle;
    bool throw_circularity_error = false;
    {
      MutexLocker mu(SystemDictionary_lock, THREAD);
      if (class_loader.is_null() || !is_parallelCapable(class_loader)) {
        PlaceholderEntry* oldprobe = placeholders()->get_entry(p_index, p_hash, name, class_loader);
        if (oldprobe) {
          // only need check_seen_thread once, not on each loop
          // 6341374 java/lang/Instrument with -Xcomp
          if (oldprobe->check_seen_thread(THREAD, PlaceholderTable::LOAD_INSTANCE)) {
            throw_circularity_error = true;
          } else {
            // case 1: traditional: should never see load_in_progress.
            while (!class_has_been_loaded && oldprobe && oldprobe->instance_load_in_progress()) {

              // case 4: bootstrap classloader: prevent futile classloading,
              // wait on first requestor
              if (class_loader.is_null()) {
                SystemDictionary_lock->wait();
              } else {
              // case 2: traditional with broken classloader lock. wait on first
              // requestor.
                double_lock_wait(lockObject, THREAD);
              }
              // Check if classloading completed while we were waiting
              klassOop check = find_class(d_index, d_hash, name, class_loader);
              if (check != NULL) {
                // Klass is already loaded, so just return it
                k = instanceKlassHandle(THREAD, check);
                class_has_been_loaded = true;
              }
              // check if other thread failed to load and cleaned up
              oldprobe = placeholders()->get_entry(p_index, p_hash, name, class_loader);
            }
          }
        }
      }
      // All cases: add LOAD_INSTANCE
      // case 3: UnsyncloadClass || case 5: parallelCapable: allow competing threads to try
      // LOAD_INSTANCE in parallel
      // add placeholder entry even if error - callers will remove on error
      if (!throw_circularity_error && !class_has_been_loaded) {
        PlaceholderEntry* newprobe = placeholders()->find_and_add(p_index, p_hash, name, class_loader, PlaceholderTable::LOAD_INSTANCE, nullsymbolHandle, THREAD);
        // For class loaders that do not acquire the classloader object lock,
        // if they did not catch another thread holding LOAD_INSTANCE,
        // need a check analogous to the acquire ObjectLocker/find_class
        // i.e. now that we hold the LOAD_INSTANCE token on loading this class/CL
        // one final check if the load has already completed
        // class loaders holding the ObjectLock shouldn't find the class here
        klassOop check = find_class(d_index, d_hash, name, class_loader);
        if (check != NULL) {
        // Klass is already loaded, so just return it
          k = instanceKlassHandle(THREAD, check);
          class_has_been_loaded = true;
          newprobe->remove_seen_thread(THREAD, PlaceholderTable::LOAD_INSTANCE);
          placeholders()->find_and_remove(p_index, p_hash, name, class_loader, THREAD);
          SystemDictionary_lock->notify_all();
        }
      }
    }
    // must throw error outside of owning lock
    if (throw_circularity_error) {
      ResourceMark rm(THREAD);
      THROW_MSG_0(vmSymbols::java_lang_ClassCircularityError(), name->as_C_string());
    }

    if (!class_has_been_loaded) {

      // Do actual loading
      k = load_instance_class(name, class_loader, THREAD);

      // For UnsyncloadClass and AllowParallelDefineClass only:
      // If they got a linkageError, check if a parallel class load succeeded.
      // If it did, then for bytecode resolution the specification requires
      // that we return the same result we did for the other thread, i.e. the
      // successfully loaded instanceKlass
      // Should not get here for classloaders that support parallelism
      // with the new cleaner mechanism
      // Bootstrap goes through here to allow for an extra guarantee check
      if (UnsyncloadClass || (class_loader.is_null())) {
        if (k.is_null() && HAS_PENDING_EXCEPTION
          && PENDING_EXCEPTION->is_a(SystemDictionary::linkageError_klass())) {
          MutexLocker mu(SystemDictionary_lock, THREAD);
          klassOop check = find_class(d_index, d_hash, name, class_loader);
          if (check != NULL) {
            // Klass is already loaded, so just use it
            k = instanceKlassHandle(THREAD, check);
            CLEAR_PENDING_EXCEPTION;
            guarantee((!class_loader.is_null()), "dup definition for bootstrap loader?");
          }
        }
      }

      // clean up placeholder entries for success or error
      // This cleans up LOAD_INSTANCE entries
      // It also cleans up LOAD_SUPER entries on errors from
      // calling load_instance_class
      {
        MutexLocker mu(SystemDictionary_lock, THREAD);
        PlaceholderEntry* probe = placeholders()->get_entry(p_index, p_hash, name, class_loader);
        if (probe != NULL) {
          probe->remove_seen_thread(THREAD, PlaceholderTable::LOAD_INSTANCE);
          placeholders()->find_and_remove(p_index, p_hash, name, class_loader, THREAD);
          SystemDictionary_lock->notify_all();
        }
      }

      // If everything was OK (no exceptions, no null return value), and
      // class_loader is NOT the defining loader, do a little more bookkeeping.
      if (!HAS_PENDING_EXCEPTION && !k.is_null() &&
        k->class_loader() != class_loader()) {

        check_constraints(d_index, d_hash, k, class_loader, false, THREAD);

        // Need to check for a PENDING_EXCEPTION again; check_constraints
        // can throw and doesn't use the CHECK macro.
        if (!HAS_PENDING_EXCEPTION) {
          { // Grabbing the Compile_lock prevents systemDictionary updates
            // during compilations.
            MutexLocker mu(Compile_lock, THREAD);
            update_dictionary(d_index, d_hash, p_index, p_hash,
                            k, class_loader, THREAD);
          }
          if (JvmtiExport::should_post_class_load()) {
            Thread *thread = THREAD;
            assert(thread->is_Java_thread(), "thread->is_Java_thread()");
            JvmtiExport::post_class_load((JavaThread *) thread, k());
          }
        }
      }
      if (HAS_PENDING_EXCEPTION || k.is_null()) {
        // On error, clean up placeholders
        {
          MutexLocker mu(SystemDictionary_lock, THREAD);
          placeholders()->find_and_remove(p_index, p_hash, name, class_loader, THREAD);
          SystemDictionary_lock->notify_all();
        }
        return NULL;
      }
    }
  }

#ifdef ASSERT
  {
    Handle loader (THREAD, k->class_loader());
    MutexLocker mu(SystemDictionary_lock, THREAD);
    oop kk = find_class_or_placeholder(name, loader);
    assert(kk == k(), "should be present in dictionary");
  }
#endif

  // return if the protection domain in NULL
  if (protection_domain() == NULL) return k();

  // Check the protection domain has the right access
  {
    MutexLocker mu(SystemDictionary_lock, THREAD);
    // Note that we have an entry, and entries can be deleted only during GC,
    // so we cannot allow GC to occur while we're holding this entry.
    // We're using a No_Safepoint_Verifier to catch any place where we
    // might potentially do a GC at all.
    // SystemDictionary::do_unloading() asserts that classes are only
    // unloaded at a safepoint.
    No_Safepoint_Verifier nosafepoint;
    if (dictionary()->is_valid_protection_domain(d_index, d_hash, name,
                                                 class_loader,
                                                 protection_domain)) {
      return k();
    }
  }

  // Verify protection domain. If it fails an exception is thrown
  validate_protection_domain(k, class_loader, protection_domain, CHECK_(klassOop(NULL)));

  return k();
}


// This routine does not lock the system dictionary.
//
// Since readers don't hold a lock, we must make sure that system
// dictionary entries are only removed at a safepoint (when only one
// thread is running), and are added to in a safe way (all links must
// be updated in an MT-safe manner).
//
// Callers should be aware that an entry could be added just after
// _dictionary->bucket(index) is read here, so the caller will not see
// the new entry.

klassOop SystemDictionary::find(symbolHandle class_name,
                                Handle class_loader,
                                Handle protection_domain,
                                TRAPS) {

  // UseNewReflection
  // The result of this call should be consistent with the result
  // of the call to resolve_instance_class_or_null().
  // See evaluation 6790209 and 4474172 for more details.
  class_loader = Handle(THREAD, java_lang_ClassLoader::non_reflection_class_loader(class_loader()));

  unsigned int d_hash = dictionary()->compute_hash(class_name, class_loader);
  int d_index = dictionary()->hash_to_index(d_hash);

  {
    // Note that we have an entry, and entries can be deleted only during GC,
    // so we cannot allow GC to occur while we're holding this entry.
    // We're using a No_Safepoint_Verifier to catch any place where we
    // might potentially do a GC at all.
    // SystemDictionary::do_unloading() asserts that classes are only
    // unloaded at a safepoint.
    No_Safepoint_Verifier nosafepoint;
    return dictionary()->find(d_index, d_hash, class_name, class_loader,
                              protection_domain, THREAD);
  }
}


// Look for a loaded instance or array klass by name.  Do not do any loading.
// return NULL in case of error.
klassOop SystemDictionary::find_instance_or_array_klass(symbolHandle class_name,
                                                        Handle class_loader,
                                                        Handle protection_domain,
                                                        TRAPS) {
  klassOop k = NULL;
  assert(class_name() != NULL, "class name must be non NULL");

  // Try to get one of the well-known klasses.
  if (LinkWellKnownClasses) {
    k = find_well_known_klass(class_name());
    if (k != NULL) {
      return k;
    }
  }

  if (FieldType::is_array(class_name())) {
    // The name refers to an array.  Parse the name.
    jint dimension;
    symbolOop object_key;

    // dimension and object_key are assigned as a side-effect of this call
    BasicType t = FieldType::get_array_info(class_name(), &dimension,
                                            &object_key, CHECK_(NULL));
    if (t != T_OBJECT) {
      k = Universe::typeArrayKlassObj(t);
    } else {
      symbolHandle h_key(THREAD, object_key);
      k = SystemDictionary::find(h_key, class_loader, protection_domain, THREAD);
    }
    if (k != NULL) {
      k = Klass::cast(k)->array_klass_or_null(dimension);
    }
  } else {
    k = find(class_name, class_loader, protection_domain, THREAD);
  }
  return k;
}

// Quick range check for names of well-known classes:
static symbolOop wk_klass_name_limits[2] = {NULL, NULL};

#ifndef PRODUCT
static int find_wkk_calls, find_wkk_probes, find_wkk_wins;
// counts for "hello world": 3983, 1616, 1075
//  => 60% hit after limit guard, 25% total win rate
#endif

klassOop SystemDictionary::find_well_known_klass(symbolOop class_name) {
  // A bounds-check on class_name will quickly get a negative result.
  NOT_PRODUCT(find_wkk_calls++);
  if (class_name >= wk_klass_name_limits[0] &&
      class_name <= wk_klass_name_limits[1]) {
    NOT_PRODUCT(find_wkk_probes++);
    vmSymbols::SID sid = vmSymbols::find_sid(class_name);
    if (sid != vmSymbols::NO_SID) {
      klassOop k = NULL;
      switch (sid) {
        #define WK_KLASS_CASE(name, symbol, ignore_option) \
        case vmSymbols::VM_SYMBOL_ENUM_NAME(symbol): \
          k = WK_KLASS(name); break;
        WK_KLASSES_DO(WK_KLASS_CASE)
        #undef WK_KLASS_CASE
      }
      NOT_PRODUCT(if (k != NULL)  find_wkk_wins++);
      return k;
    }
  }
  return NULL;
}

// Note: this method is much like resolve_from_stream, but
// updates no supplemental data structures.
// TODO consolidate the two methods with a helper routine?
klassOop SystemDictionary::parse_stream(symbolHandle class_name,
                                        Handle class_loader,
                                        Handle protection_domain,
                                        ClassFileStream* st,
                                        KlassHandle host_klass,
                                        GrowableArray<Handle>* cp_patches,
                                        TRAPS) {
  symbolHandle parsed_name;

  // Parse the stream. Note that we do this even though this klass might
  // already be present in the SystemDictionary, otherwise we would not
  // throw potential ClassFormatErrors.
  //
  // Note: "name" is updated.
  // Further note:  a placeholder will be added for this class when
  //   super classes are loaded (resolve_super_or_fail). We expect this
  //   to be called for all classes but java.lang.Object; and we preload
  //   java.lang.Object through resolve_or_fail, not this path.

  instanceKlassHandle k = ClassFileParser(st).parseClassFile(class_name,
                                                             class_loader,
                                                             protection_domain,
                                                             host_klass,
                                                             cp_patches,
                                                             parsed_name,
                                                             true,
                                                             THREAD);


  // We don't redefine the class, so we just need to clean up whether there
  // was an error or not (don't want to modify any system dictionary
  // data structures).
  // Parsed name could be null if we threw an error before we got far
  // enough along to parse it -- in that case, there is nothing to clean up.
  if (!parsed_name.is_null()) {
    unsigned int p_hash = placeholders()->compute_hash(parsed_name,
                                                       class_loader);
    int p_index = placeholders()->hash_to_index(p_hash);
    {
    MutexLocker mu(SystemDictionary_lock, THREAD);
    placeholders()->find_and_remove(p_index, p_hash, parsed_name, class_loader, THREAD);
    SystemDictionary_lock->notify_all();
    }
  }

  if (host_klass.not_null() && k.not_null()) {
    assert(AnonymousClasses, "");
    // If it's anonymous, initialize it now, since nobody else will.
    k->set_host_klass(host_klass());

    {
      MutexLocker mu_r(Compile_lock, THREAD);

      // Add to class hierarchy, initialize vtables, and do possible
      // deoptimizations.
      add_to_hierarchy(k, CHECK_NULL); // No exception, but can block

      // But, do not add to system dictionary.
    }

    k->eager_initialize(THREAD);

    // notify jvmti
    if (JvmtiExport::should_post_class_load()) {
        assert(THREAD->is_Java_thread(), "thread->is_Java_thread()");
        JvmtiExport::post_class_load((JavaThread *) THREAD, k());
    }
  }

  return k();
}

// Add a klass to the system from a stream (called by jni_DefineClass and
// JVM_DefineClass).
// Note: class_name can be NULL. In that case we do not know the name of
// the class until we have parsed the stream.

klassOop SystemDictionary::resolve_from_stream(symbolHandle class_name,
                                               Handle class_loader,
                                               Handle protection_domain,
                                               ClassFileStream* st,
                                               bool verify,
                                               TRAPS) {

  // Classloaders that support parallelism, e.g. bootstrap classloader,
  // or all classloaders with UnsyncloadClass do not acquire lock here
  bool DoObjectLock = true;
  if (is_parallelCapable(class_loader)) {
    DoObjectLock = false;
  }

  // Make sure we are synchronized on the class loader before we proceed
  Handle lockObject = compute_loader_lock_object(class_loader, THREAD);
  check_loader_lock_contention(lockObject, THREAD);
  ObjectLocker ol(lockObject, THREAD, DoObjectLock);

  symbolHandle parsed_name;

  // Parse the stream. Note that we do this even though this klass might
  // already be present in the SystemDictionary, otherwise we would not
  // throw potential ClassFormatErrors.
  //
  // Note: "name" is updated.
  // Further note:  a placeholder will be added for this class when
  //   super classes are loaded (resolve_super_or_fail). We expect this
  //   to be called for all classes but java.lang.Object; and we preload
  //   java.lang.Object through resolve_or_fail, not this path.

  instanceKlassHandle k = ClassFileParser(st).parseClassFile(class_name,
                                                             class_loader,
                                                             protection_domain,
                                                             parsed_name,
                                                             verify,
                                                             THREAD);

  const char* pkg = "java/";
  if (!HAS_PENDING_EXCEPTION &&
      !class_loader.is_null() &&
      !parsed_name.is_null() &&
      !strncmp((const char*)parsed_name->bytes(), pkg, strlen(pkg))) {
    // It is illegal to define classes in the "java." package from
    // JVM_DefineClass or jni_DefineClass unless you're the bootclassloader
    ResourceMark rm(THREAD);
    char* name = parsed_name->as_C_string();
    char* index = strrchr(name, '/');
    *index = '\0'; // chop to just the package name
    while ((index = strchr(name, '/')) != NULL) {
      *index = '.'; // replace '/' with '.' in package name
    }
    const char* fmt = "Prohibited package name: %s";
    size_t len = strlen(fmt) + strlen(name);
    char* message = NEW_RESOURCE_ARRAY(char, len);
    jio_snprintf(message, len, fmt, name);
    Exceptions::_throw_msg(THREAD_AND_LOCATION,
      vmSymbols::java_lang_SecurityException(), message);
  }

  if (!HAS_PENDING_EXCEPTION) {
    assert(!parsed_name.is_null(), "Sanity");
    assert(class_name.is_null() || class_name() == parsed_name(),
           "name mismatch");
    // Verification prevents us from creating names with dots in them, this
    // asserts that that's the case.
    assert(is_internal_format(parsed_name),
           "external class name format used internally");

    // Add class just loaded
    // If a class loader supports parallel classloading handle parallel define requests
    // find_or_define_instance_class may return a different instanceKlass
    if (is_parallelCapable(class_loader)) {
      k = find_or_define_instance_class(class_name, class_loader, k, THREAD);
    } else {
      define_instance_class(k, THREAD);
    }
  }

  // If parsing the class file or define_instance_class failed, we
  // need to remove the placeholder added on our behalf. But we
  // must make sure parsed_name is valid first (it won't be if we had
  // a format error before the class was parsed far enough to
  // find the name).
  if (HAS_PENDING_EXCEPTION && !parsed_name.is_null()) {
    unsigned int p_hash = placeholders()->compute_hash(parsed_name,
                                                       class_loader);
    int p_index = placeholders()->hash_to_index(p_hash);
    {
    MutexLocker mu(SystemDictionary_lock, THREAD);
    placeholders()->find_and_remove(p_index, p_hash, parsed_name, class_loader, THREAD);
    SystemDictionary_lock->notify_all();
    }
    return NULL;
  }

  // Make sure that we didn't leave a place holder in the
  // SystemDictionary; this is only done on success
  debug_only( {
    if (!HAS_PENDING_EXCEPTION) {
      assert(!parsed_name.is_null(), "parsed_name is still null?");
      symbolHandle h_name   (THREAD, k->name());
      Handle h_loader (THREAD, k->class_loader());

      MutexLocker mu(SystemDictionary_lock, THREAD);

      oop check = find_class_or_placeholder(parsed_name, class_loader);
      assert(check == k(), "should be present in the dictionary");

      oop check2 = find_class_or_placeholder(h_name, h_loader);
      assert(check == check2, "name inconsistancy in SystemDictionary");
    }
  } );

  return k();
}


void SystemDictionary::set_shared_dictionary(HashtableBucket* t, int length,
                                             int number_of_entries) {
  assert(length == _nof_buckets * sizeof(HashtableBucket),
         "bad shared dictionary size.");
  _shared_dictionary = new Dictionary(_nof_buckets, t, number_of_entries);
}


// If there is a shared dictionary, then find the entry for the
// given shared system class, if any.

klassOop SystemDictionary::find_shared_class(symbolHandle class_name) {
  if (shared_dictionary() != NULL) {
    unsigned int d_hash = dictionary()->compute_hash(class_name, Handle());
    int d_index = dictionary()->hash_to_index(d_hash);
    return shared_dictionary()->find_shared_class(d_index, d_hash, class_name);
  } else {
    return NULL;
  }
}


// Load a class from the shared spaces (found through the shared system
// dictionary).  Force the superclass and all interfaces to be loaded.
// Update the class definition to include sibling classes and no
// subclasses (yet).  [Classes in the shared space are not part of the
// object hierarchy until loaded.]

instanceKlassHandle SystemDictionary::load_shared_class(
                 symbolHandle class_name, Handle class_loader, TRAPS) {
  instanceKlassHandle ik (THREAD, find_shared_class(class_name));
  return load_shared_class(ik, class_loader, THREAD);
}

// Note well!  Changes to this method may affect oop access order
// in the shared archive.  Please take care to not make changes that
// adversely affect cold start time by changing the oop access order
// that is specified in dump.cpp MarkAndMoveOrderedReadOnly and
// MarkAndMoveOrderedReadWrite closures.
instanceKlassHandle SystemDictionary::load_shared_class(
                 instanceKlassHandle ik, Handle class_loader, TRAPS) {
  assert(class_loader.is_null(), "non-null classloader for shared class?");
  if (ik.not_null()) {
    instanceKlassHandle nh = instanceKlassHandle(); // null Handle
    symbolHandle class_name(THREAD, ik->name());

    // Found the class, now load the superclass and interfaces.  If they
    // are shared, add them to the main system dictionary and reset
    // their hierarchy references (supers, subs, and interfaces).

    if (ik->super() != NULL) {
      symbolHandle cn(THREAD, ik->super()->klass_part()->name());
      resolve_super_or_fail(class_name, cn,
                            class_loader, Handle(), true, CHECK_(nh));
    }

    objArrayHandle interfaces (THREAD, ik->local_interfaces());
    int num_interfaces = interfaces->length();
    for (int index = 0; index < num_interfaces; index++) {
      klassOop k = klassOop(interfaces->obj_at(index));

      // Note: can not use instanceKlass::cast here because
      // interfaces' instanceKlass's C++ vtbls haven't been
      // reinitialized yet (they will be once the interface classes
      // are loaded)
      symbolHandle name (THREAD, k->klass_part()->name());
      resolve_super_or_fail(class_name, name, class_loader, Handle(), false, CHECK_(nh));
    }

    // Adjust methods to recover missing data.  They need addresses for
    // interpreter entry points and their default native method address
    // must be reset.

    // Updating methods must be done under a lock so multiple
    // threads don't update these in parallel
    // Shared classes are all currently loaded by the bootstrap
    // classloader, so this will never cause a deadlock on
    // a custom class loader lock.

    {
      Handle lockObject = compute_loader_lock_object(class_loader, THREAD);
      check_loader_lock_contention(lockObject, THREAD);
      ObjectLocker ol(lockObject, THREAD, true);

      objArrayHandle methods (THREAD, ik->methods());
      int num_methods = methods->length();
      for (int index2 = 0; index2 < num_methods; ++index2) {
        methodHandle m(THREAD, methodOop(methods->obj_at(index2)));
        m()->link_method(m, CHECK_(nh));
      }
    }

    if (TraceClassLoading) {
      ResourceMark rm;
      tty->print("[Loaded %s", ik->external_name());
      tty->print(" from shared objects file");
      tty->print_cr("]");
    }
    // notify a class loaded from shared object
    ClassLoadingService::notify_class_loaded(instanceKlass::cast(ik()),
                                             true /* shared class */);
  }
  return ik;
}

#ifdef KERNEL
// Some classes on the bootstrap class path haven't been installed on the
// system yet.  Call the DownloadManager method to make them appear in the
// bootstrap class path and try again to load the named class.
// Note that with delegation class loaders all classes in another loader will
// first try to call this so it'd better be fast!!
static instanceKlassHandle download_and_retry_class_load(
                                                    symbolHandle class_name,
                                                    TRAPS) {

  klassOop dlm = SystemDictionary::sun_jkernel_DownloadManager_klass();
  instanceKlassHandle nk;

  // If download manager class isn't loaded just return.
  if (dlm == NULL) return nk;

  { HandleMark hm(THREAD);
    ResourceMark rm(THREAD);
    Handle s = java_lang_String::create_from_symbol(class_name, CHECK_(nk));
    Handle class_string = java_lang_String::externalize_classname(s, CHECK_(nk));

    // return value
    JavaValue result(T_OBJECT);

    // Call the DownloadManager.  We assume that it has a lock because
    // multiple classes could be not found and downloaded at the same time.
    // class sun.misc.DownloadManager;
    // public static String getBootClassPathEntryForClass(String className);
    JavaCalls::call_static(&result,
                       KlassHandle(THREAD, dlm),
                       vmSymbolHandles::getBootClassPathEntryForClass_name(),
                       vmSymbolHandles::string_string_signature(),
                       class_string,
                       CHECK_(nk));

    // Get result.string and add to bootclasspath
    assert(result.get_type() == T_OBJECT, "just checking");
    oop obj = (oop) result.get_jobject();
    if (obj == NULL) { return nk; }

    Handle h_obj(THREAD, obj);
    char* new_class_name = java_lang_String::as_platform_dependent_str(h_obj,
                                                                  CHECK_(nk));

    // lock the loader
    // we use this lock because JVMTI does.
    Handle loader_lock(THREAD, SystemDictionary::system_loader_lock());

    ObjectLocker ol(loader_lock, THREAD);
    // add the file to the bootclasspath
    ClassLoader::update_class_path_entry_list(new_class_name, true);
  } // end HandleMark

  if (TraceClassLoading) {
    ClassLoader::print_bootclasspath();
  }
  return ClassLoader::load_classfile(class_name, CHECK_(nk));
}
#endif // KERNEL


instanceKlassHandle SystemDictionary::load_instance_class(symbolHandle class_name, Handle class_loader, TRAPS) {
  instanceKlassHandle nh = instanceKlassHandle(); // null Handle
  if (class_loader.is_null()) {

    // Search the shared system dictionary for classes preloaded into the
    // shared spaces.
    instanceKlassHandle k;
    {
      PerfTraceTime vmtimer(ClassLoader::perf_shared_classload_time());
      k = load_shared_class(class_name, class_loader, THREAD);
    }

    if (k.is_null()) {
      // Use VM class loader
      PerfTraceTime vmtimer(ClassLoader::perf_sys_classload_time());
      k = ClassLoader::load_classfile(class_name, CHECK_(nh));
    }

#ifdef KERNEL
    // If the VM class loader has failed to load the class, call the
    // DownloadManager class to make it magically appear on the classpath
    // and try again.  This is only configured with the Kernel VM.
    if (k.is_null()) {
      k = download_and_retry_class_load(class_name, CHECK_(nh));
    }
#endif // KERNEL

    // find_or_define_instance_class may return a different instanceKlass
    if (!k.is_null()) {
      k = find_or_define_instance_class(class_name, class_loader, k, CHECK_(nh));
    }
    return k;
  } else {
    // Use user specified class loader to load class. Call loadClass operation on class_loader.
    ResourceMark rm(THREAD);

    assert(THREAD->is_Java_thread(), "must be a JavaThread");
    JavaThread* jt = (JavaThread*) THREAD;

    PerfClassTraceTime vmtimer(ClassLoader::perf_app_classload_time(),
                               ClassLoader::perf_app_classload_selftime(),
                               ClassLoader::perf_app_classload_count(),
                               jt->get_thread_stat()->perf_recursion_counts_addr(),
                               jt->get_thread_stat()->perf_timers_addr(),
                               PerfClassTraceTime::CLASS_LOAD);

    Handle s = java_lang_String::create_from_symbol(class_name, CHECK_(nh));
    // Translate to external class name format, i.e., convert '/' chars to '.'
    Handle string = java_lang_String::externalize_classname(s, CHECK_(nh));

    JavaValue result(T_OBJECT);

    KlassHandle spec_klass (THREAD, SystemDictionary::classloader_klass());

    // Call public unsynchronized loadClass(String) directly for all class loaders
    // for parallelCapable class loaders. JDK >=7, loadClass(String, boolean) will
    // acquire a class-name based lock rather than the class loader object lock.
    // JDK < 7 already acquire the class loader lock in loadClass(String, boolean),
    // so the call to loadClassInternal() was not required.
    //
    // UnsyncloadClass flag means both call loadClass(String) and do
    // not acquire the class loader lock even for class loaders that are
    // not parallelCapable. This was a risky transitional
    // flag for diagnostic purposes only. It is risky to call
    // custom class loaders without synchronization.
    // WARNING If a custom class loader does NOT synchronizer findClass, or callers of
    // findClass, the UnsyncloadClass flag risks unexpected timing bugs in the field.
    // Do NOT assume this will be supported in future releases.
    //
    // Added MustCallLoadClassInternal in case we discover in the field
    // a customer that counts on this call
    if (MustCallLoadClassInternal && has_loadClassInternal()) {
      JavaCalls::call_special(&result,
                              class_loader,
                              spec_klass,
                              vmSymbolHandles::loadClassInternal_name(),
                              vmSymbolHandles::string_class_signature(),
                              string,
                              CHECK_(nh));
    } else {
      JavaCalls::call_virtual(&result,
                              class_loader,
                              spec_klass,
                              vmSymbolHandles::loadClass_name(),
                              vmSymbolHandles::string_class_signature(),
                              string,
                              CHECK_(nh));
    }

    assert(result.get_type() == T_OBJECT, "just checking");
    oop obj = (oop) result.get_jobject();

    // Primitive classes return null since forName() can not be
    // used to obtain any of the Class objects representing primitives or void
    if ((obj != NULL) && !(java_lang_Class::is_primitive(obj))) {
      instanceKlassHandle k =
                instanceKlassHandle(THREAD, java_lang_Class::as_klassOop(obj));
      // For user defined Java class loaders, check that the name returned is
      // the same as that requested.  This check is done for the bootstrap
      // loader when parsing the class file.
      if (class_name() == k->name()) {
        return k;
      }
    }
    // Class is not found or has the wrong name, return NULL
    return nh;
  }
}

void SystemDictionary::define_instance_class(instanceKlassHandle k, TRAPS) {

  Handle class_loader_h(THREAD, k->class_loader());

 // for bootstrap and other parallel classloaders don't acquire lock,
 // use placeholder token
 // If a parallelCapable class loader calls define_instance_class instead of
 // find_or_define_instance_class to get here, we have a timing
 // hole with systemDictionary updates and check_constraints
 if (!class_loader_h.is_null() && !is_parallelCapable(class_loader_h)) {
    assert(ObjectSynchronizer::current_thread_holds_lock((JavaThread*)THREAD,
         compute_loader_lock_object(class_loader_h, THREAD)),
         "define called without lock");
  }

  // Check class-loading constraints. Throw exception if violation is detected.
  // Grabs and releases SystemDictionary_lock
  // The check_constraints/find_class call and update_dictionary sequence
  // must be "atomic" for a specific class/classloader pair so we never
  // define two different instanceKlasses for that class/classloader pair.
  // Existing classloaders will call define_instance_class with the
  // classloader lock held
  // Parallel classloaders will call find_or_define_instance_class
  // which will require a token to perform the define class
  symbolHandle name_h(THREAD, k->name());
  unsigned int d_hash = dictionary()->compute_hash(name_h, class_loader_h);
  int d_index = dictionary()->hash_to_index(d_hash);
  check_constraints(d_index, d_hash, k, class_loader_h, true, CHECK);

  // Register class just loaded with class loader (placed in Vector)
  // Note we do this before updating the dictionary, as this can
  // fail with an OutOfMemoryError (if it does, we will *not* put this
  // class in the dictionary and will not update the class hierarchy).
  if (k->class_loader() != NULL) {
    methodHandle m(THREAD, Universe::loader_addClass_method());
    JavaValue result(T_VOID);
    JavaCallArguments args(class_loader_h);
    args.push_oop(Handle(THREAD, k->java_mirror()));
    JavaCalls::call(&result, m, &args, CHECK);
  }

  // Add the new class. We need recompile lock during update of CHA.
  {
    unsigned int p_hash = placeholders()->compute_hash(name_h, class_loader_h);
    int p_index = placeholders()->hash_to_index(p_hash);

    MutexLocker mu_r(Compile_lock, THREAD);

    // Add to class hierarchy, initialize vtables, and do possible
    // deoptimizations.
    add_to_hierarchy(k, CHECK); // No exception, but can block

    // Add to systemDictionary - so other classes can see it.
    // Grabs and releases SystemDictionary_lock
    update_dictionary(d_index, d_hash, p_index, p_hash,
                      k, class_loader_h, THREAD);
  }
  k->eager_initialize(THREAD);

  // notify jvmti
  if (JvmtiExport::should_post_class_load()) {
      assert(THREAD->is_Java_thread(), "thread->is_Java_thread()");
      JvmtiExport::post_class_load((JavaThread *) THREAD, k());

  }
}

// Support parallel classloading
// Initial implementation for bootstrap classloader
// For custom class loaders that support parallel classloading,
// With AllowParallelDefine flag==true, in case they do not synchronize around
// FindLoadedClass/DefineClass, calls, we check for parallel
// loading for them, wait if a defineClass is in progress
// and return the initial requestor's results
// With AllowParallelDefine flag==false, call through to define_instance_class
// which will throw LinkageError: duplicate class definition.
// For better performance, the class loaders should synchronize
// findClass(), i.e. FindLoadedClass/DefineClassIfAbsent or they
// potentially waste time reading and parsing the bytestream.
// Note: VM callers should ensure consistency of k/class_name,class_loader
instanceKlassHandle SystemDictionary::find_or_define_instance_class(symbolHandle class_name, Handle class_loader, instanceKlassHandle k, TRAPS) {

  instanceKlassHandle nh = instanceKlassHandle(); // null Handle
  symbolHandle name_h(THREAD, k->name()); // passed in class_name may be null

  unsigned int d_hash = dictionary()->compute_hash(name_h, class_loader);
  int d_index = dictionary()->hash_to_index(d_hash);

// Hold SD lock around find_class and placeholder creation for DEFINE_CLASS
  unsigned int p_hash = placeholders()->compute_hash(name_h, class_loader);
  int p_index = placeholders()->hash_to_index(p_hash);
  PlaceholderEntry* probe;

  {
    MutexLocker mu(SystemDictionary_lock, THREAD);
    // First check if class already defined
    klassOop check = find_class(d_index, d_hash, name_h, class_loader);
    if (check != NULL) {
      return(instanceKlassHandle(THREAD, check));
    }

    // Acquire define token for this class/classloader
    symbolHandle nullsymbolHandle;
    probe = placeholders()->find_and_add(p_index, p_hash, name_h, class_loader, PlaceholderTable::DEFINE_CLASS, nullsymbolHandle, THREAD);
    // Wait if another thread defining in parallel
    // All threads wait - even those that will throw duplicate class: otherwise
    // caller is surprised by LinkageError: duplicate, but findLoadedClass fails
    // if other thread has not finished updating dictionary
    while (probe->definer() != NULL) {
      SystemDictionary_lock->wait();
    }
    // Only special cases allow parallel defines and can use other thread's results
    // Other cases fall through, and may run into duplicate defines
    // caught by finding an entry in the SystemDictionary
    if ((UnsyncloadClass || AllowParallelDefineClass) && (probe->instanceKlass() != NULL)) {
        probe->remove_seen_thread(THREAD, PlaceholderTable::DEFINE_CLASS);
        placeholders()->find_and_remove(p_index, p_hash, name_h, class_loader, THREAD);
        SystemDictionary_lock->notify_all();
#ifdef ASSERT
        klassOop check = find_class(d_index, d_hash, name_h, class_loader);
        assert(check != NULL, "definer missed recording success");
#endif
        return(instanceKlassHandle(THREAD, probe->instanceKlass()));
    } else {
      // This thread will define the class (even if earlier thread tried and had an error)
      probe->set_definer(THREAD);
    }
  }

  define_instance_class(k, THREAD);

  Handle linkage_exception = Handle(); // null handle

  // definer must notify any waiting threads
  {
    MutexLocker mu(SystemDictionary_lock, THREAD);
    PlaceholderEntry* probe = placeholders()->get_entry(p_index, p_hash, name_h, class_loader);
    assert(probe != NULL, "DEFINE_CLASS placeholder lost?");
    if (probe != NULL) {
      if (HAS_PENDING_EXCEPTION) {
        linkage_exception = Handle(THREAD,PENDING_EXCEPTION);
        CLEAR_PENDING_EXCEPTION;
      } else {
        probe->set_instanceKlass(k());
      }
      probe->set_definer(NULL);
      probe->remove_seen_thread(THREAD, PlaceholderTable::DEFINE_CLASS);
      placeholders()->find_and_remove(p_index, p_hash, name_h, class_loader, THREAD);
      SystemDictionary_lock->notify_all();
    }
  }

  // Can't throw exception while holding lock due to rank ordering
  if (linkage_exception() != NULL) {
    THROW_OOP_(linkage_exception(), nh); // throws exception and returns
  }

  return k;
}
Handle SystemDictionary::compute_loader_lock_object(Handle class_loader, TRAPS) {
  // If class_loader is NULL we synchronize on _system_loader_lock_obj
  if (class_loader.is_null()) {
    return Handle(THREAD, _system_loader_lock_obj);
  } else {
    return class_loader;
  }
}

// This method is added to check how often we have to wait to grab loader
// lock. The results are being recorded in the performance counters defined in
// ClassLoader::_sync_systemLoaderLockContentionRate and
// ClassLoader::_sync_nonSystemLoaderLockConteionRate.
void SystemDictionary::check_loader_lock_contention(Handle loader_lock, TRAPS) {
  if (!UsePerfData) {
    return;
  }

  assert(!loader_lock.is_null(), "NULL lock object");

  if (ObjectSynchronizer::query_lock_ownership((JavaThread*)THREAD, loader_lock)
      == ObjectSynchronizer::owner_other) {
    // contention will likely happen, so increment the corresponding
    // contention counter.
    if (loader_lock() == _system_loader_lock_obj) {
      ClassLoader::sync_systemLoaderLockContentionRate()->inc();
    } else {
      ClassLoader::sync_nonSystemLoaderLockContentionRate()->inc();
    }
  }
}

// ----------------------------------------------------------------------------
// Lookup

klassOop SystemDictionary::find_class(int index, unsigned int hash,
                                      symbolHandle class_name,
                                      Handle class_loader) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert (index == dictionary()->index_for(class_name, class_loader),
          "incorrect index?");

  klassOop k = dictionary()->find_class(index, hash, class_name, class_loader);
  return k;
}


// Basic find on classes in the midst of being loaded
symbolOop SystemDictionary::find_placeholder(int index, unsigned int hash,
                                             symbolHandle class_name,
                                             Handle class_loader) {
  assert_locked_or_safepoint(SystemDictionary_lock);

  return placeholders()->find_entry(index, hash, class_name, class_loader);
}


// Used for assertions and verification only
oop SystemDictionary::find_class_or_placeholder(symbolHandle class_name,
                                                Handle class_loader) {
  #ifndef ASSERT
  guarantee(VerifyBeforeGC   ||
            VerifyDuringGC   ||
            VerifyBeforeExit ||
            VerifyAfterGC, "too expensive");
  #endif
  assert_locked_or_safepoint(SystemDictionary_lock);
  symbolOop class_name_ = class_name();
  oop class_loader_ = class_loader();

  // First look in the loaded class array
  unsigned int d_hash = dictionary()->compute_hash(class_name, class_loader);
  int d_index = dictionary()->hash_to_index(d_hash);
  oop lookup = find_class(d_index, d_hash, class_name, class_loader);

  if (lookup == NULL) {
    // Next try the placeholders
    unsigned int p_hash = placeholders()->compute_hash(class_name,class_loader);
    int p_index = placeholders()->hash_to_index(p_hash);
    lookup = find_placeholder(p_index, p_hash, class_name, class_loader);
  }

  return lookup;
}


// Get the next class in the diictionary.
klassOop SystemDictionary::try_get_next_class() {
  return dictionary()->try_get_next_class();
}


// ----------------------------------------------------------------------------
// Update hierachy. This is done before the new klass has been added to the SystemDictionary. The Recompile_lock
// is held, to ensure that the compiler is not using the class hierachy, and that deoptimization will kick in
// before a new class is used.

void SystemDictionary::add_to_hierarchy(instanceKlassHandle k, TRAPS) {
  assert(k.not_null(), "just checking");
  // Link into hierachy. Make sure the vtables are initialized before linking into
  k->append_to_sibling_list();                    // add to superklass/sibling list
  k->process_interfaces(THREAD);                  // handle all "implements" declarations
  k->set_init_state(instanceKlass::loaded);
  // Now flush all code that depended on old class hierarchy.
  // Note: must be done *after* linking k into the hierarchy (was bug 12/9/97)
  // Also, first reinitialize vtable because it may have gotten out of synch
  // while the new class wasn't connected to the class hierarchy.
  Universe::flush_dependents_on(k);
}


// ----------------------------------------------------------------------------
// GC support

// Following roots during mark-sweep is separated in two phases.
//
// The first phase follows preloaded classes and all other system
// classes, since these will never get unloaded anyway.
//
// The second phase removes (unloads) unreachable classes from the
// system dictionary and follows the remaining classes' contents.

void SystemDictionary::always_strong_oops_do(OopClosure* blk) {
  // Follow preloaded classes/mirrors and system loader object
  blk->do_oop(&_java_system_loader);
  preloaded_oops_do(blk);
  always_strong_classes_do(blk);
}


void SystemDictionary::always_strong_classes_do(OopClosure* blk) {
  // Follow all system classes and temporary placeholders in dictionary
  dictionary()->always_strong_classes_do(blk);

  // Placeholders. These are *always* strong roots, as they
  // represent classes we're actively loading.
  placeholders_do(blk);

  // Visit extra methods
  if (invoke_method_table() != NULL)
    invoke_method_table()->oops_do(blk);

  // Loader constraints. We must keep the symbolOop used in the name alive.
  constraints()->always_strong_classes_do(blk);

  // Resolution errors keep the symbolOop for the error alive
  resolution_errors()->always_strong_classes_do(blk);
}


void SystemDictionary::placeholders_do(OopClosure* blk) {
  placeholders()->oops_do(blk);
}


bool SystemDictionary::do_unloading(BoolObjectClosure* is_alive) {
  bool result = dictionary()->do_unloading(is_alive);
  constraints()->purge_loader_constraints(is_alive);
  resolution_errors()->purge_resolution_errors(is_alive);
  return result;
}


// The mirrors are scanned by shared_oops_do() which is
// not called by oops_do().  In order to process oops in
// a necessary order, shared_oops_do() is call by
// Universe::oops_do().
void SystemDictionary::oops_do(OopClosure* f) {
  // Adjust preloaded classes and system loader object
  f->do_oop(&_java_system_loader);
  preloaded_oops_do(f);

  lazily_loaded_oops_do(f);

  // Adjust dictionary
  dictionary()->oops_do(f);

  // Visit extra methods
  if (invoke_method_table() != NULL)
    invoke_method_table()->oops_do(f);

  // Partially loaded classes
  placeholders()->oops_do(f);

  // Adjust constraint table
  constraints()->oops_do(f);

  // Adjust resolution error table
  resolution_errors()->oops_do(f);
}


void SystemDictionary::preloaded_oops_do(OopClosure* f) {
  f->do_oop((oop*) &wk_klass_name_limits[0]);
  f->do_oop((oop*) &wk_klass_name_limits[1]);

  for (int k = (int)FIRST_WKID; k < (int)WKID_LIMIT; k++) {
    f->do_oop((oop*) &_well_known_klasses[k]);
  }

  {
    for (int i = 0; i < T_VOID+1; i++) {
      if (_box_klasses[i] != NULL) {
        assert(i >= T_BOOLEAN, "checking");
        f->do_oop((oop*) &_box_klasses[i]);
      }
    }
  }

  // The basic type mirrors would have already been processed in
  // Universe::oops_do(), via a call to shared_oops_do(), so should
  // not be processed again.

  f->do_oop((oop*) &_system_loader_lock_obj);
  FilteredFieldsMap::klasses_oops_do(f);
}

void SystemDictionary::lazily_loaded_oops_do(OopClosure* f) {
  f->do_oop((oop*) &_abstract_ownable_synchronizer_klass);
}

// Just the classes from defining class loaders
// Don't iterate over placeholders
void SystemDictionary::classes_do(void f(klassOop)) {
  dictionary()->classes_do(f);
}

// Added for initialize_itable_for_klass
//   Just the classes from defining class loaders
// Don't iterate over placeholders
void SystemDictionary::classes_do(void f(klassOop, TRAPS), TRAPS) {
  dictionary()->classes_do(f, CHECK);
}

//   All classes, and their class loaders
// Don't iterate over placeholders
void SystemDictionary::classes_do(void f(klassOop, oop)) {
  dictionary()->classes_do(f);
}

//   All classes, and their class loaders
//   (added for helpers that use HandleMarks and ResourceMarks)
// Don't iterate over placeholders
void SystemDictionary::classes_do(void f(klassOop, oop, TRAPS), TRAPS) {
  dictionary()->classes_do(f, CHECK);
}

void SystemDictionary::placeholders_do(void f(symbolOop, oop)) {
  placeholders()->entries_do(f);
}

void SystemDictionary::methods_do(void f(methodOop)) {
  dictionary()->methods_do(f);
  if (invoke_method_table() != NULL)
    invoke_method_table()->methods_do(f);
}

// ----------------------------------------------------------------------------
// Lazily load klasses

void SystemDictionary::load_abstract_ownable_synchronizer_klass(TRAPS) {
  assert(JDK_Version::is_gte_jdk16x_version(), "Must be JDK 1.6 or later");

  // if multiple threads calling this function, only one thread will load
  // the class.  The other threads will find the loaded version once the
  // class is loaded.
  klassOop aos = _abstract_ownable_synchronizer_klass;
  if (aos == NULL) {
    klassOop k = resolve_or_fail(vmSymbolHandles::java_util_concurrent_locks_AbstractOwnableSynchronizer(), true, CHECK);
    // Force a fence to prevent any read before the write completes
    OrderAccess::fence();
    _abstract_ownable_synchronizer_klass = k;
  }
}

// ----------------------------------------------------------------------------
// Initialization

void SystemDictionary::initialize(TRAPS) {
  // Allocate arrays
  assert(dictionary() == NULL,
         "SystemDictionary should only be initialized once");
  _dictionary = new Dictionary(_nof_buckets);
  _placeholders = new PlaceholderTable(_nof_buckets);
  _number_of_modifications = 0;
  _loader_constraints = new LoaderConstraintTable(_loader_constraint_size);
  _resolution_errors = new ResolutionErrorTable(_resolution_error_size);
  // _invoke_method_table is allocated lazily in find_method_handle_invoke()

  // Allocate private object used as system class loader lock
  _system_loader_lock_obj = oopFactory::new_system_objArray(0, CHECK);
  // Initialize basic classes
  initialize_preloaded_classes(CHECK);
}

// Compact table of directions on the initialization of klasses:
static const short wk_init_info[] = {
  #define WK_KLASS_INIT_INFO(name, symbol, option) \
    ( ((int)vmSymbols::VM_SYMBOL_ENUM_NAME(symbol) \
          << SystemDictionary::CEIL_LG_OPTION_LIMIT) \
      | (int)SystemDictionary::option ),
  WK_KLASSES_DO(WK_KLASS_INIT_INFO)
  #undef WK_KLASS_INIT_INFO
  0
};

bool SystemDictionary::initialize_wk_klass(WKID id, int init_opt, TRAPS) {
  assert(id >= (int)FIRST_WKID && id < (int)WKID_LIMIT, "oob");
  int  info = wk_init_info[id - FIRST_WKID];
  int  sid  = (info >> CEIL_LG_OPTION_LIMIT);
  symbolHandle symbol = vmSymbolHandles::symbol_handle_at((vmSymbols::SID)sid);
  klassOop*    klassp = &_well_known_klasses[id];
  bool must_load = (init_opt < SystemDictionary::Opt);
  bool try_load  = true;
  if (init_opt == SystemDictionary::Opt_Kernel) {
#ifndef KERNEL
    try_load = false;
#endif //KERNEL
  }
  if ((*klassp) == NULL && try_load) {
    if (must_load) {
      (*klassp) = resolve_or_fail(symbol, true, CHECK_0); // load required class
    } else {
      (*klassp) = resolve_or_null(symbol,       CHECK_0); // load optional klass
    }
  }
  return ((*klassp) != NULL);
}

void SystemDictionary::initialize_wk_klasses_until(WKID limit_id, WKID &start_id, TRAPS) {
  assert((int)start_id <= (int)limit_id, "IDs are out of order!");
  for (int id = (int)start_id; id < (int)limit_id; id++) {
    assert(id >= (int)FIRST_WKID && id < (int)WKID_LIMIT, "oob");
    int info = wk_init_info[id - FIRST_WKID];
    int sid  = (info >> CEIL_LG_OPTION_LIMIT);
    int opt  = (info & right_n_bits(CEIL_LG_OPTION_LIMIT));

    initialize_wk_klass((WKID)id, opt, CHECK);

    // Update limits, so find_well_known_klass can be very fast:
    symbolOop s = vmSymbols::symbol_at((vmSymbols::SID)sid);
    if (wk_klass_name_limits[1] == NULL) {
      wk_klass_name_limits[0] = wk_klass_name_limits[1] = s;
    } else if (wk_klass_name_limits[1] < s) {
      wk_klass_name_limits[1] = s;
    } else if (wk_klass_name_limits[0] > s) {
      wk_klass_name_limits[0] = s;
    }
  }

  // move the starting value forward to the limit:
  start_id = limit_id;
}


void SystemDictionary::initialize_preloaded_classes(TRAPS) {
  assert(WK_KLASS(object_klass) == NULL, "preloaded classes should only be initialized once");
  // Preload commonly used klasses
  WKID scan = FIRST_WKID;
  // first do Object, String, Class
  initialize_wk_klasses_through(WK_KLASS_ENUM_NAME(class_klass), scan, CHECK);

  debug_only(instanceKlass::verify_class_klass_nonstatic_oop_maps(WK_KLASS(class_klass)));

  // Fixup mirrors for classes loaded before java.lang.Class.
  // These calls iterate over the objects currently in the perm gen
  // so calling them at this point is matters (not before when there
  // are fewer objects and not later after there are more objects
  // in the perm gen.
  Universe::initialize_basic_type_mirrors(CHECK);
  Universe::fixup_mirrors(CHECK);

  // do a bunch more:
  initialize_wk_klasses_through(WK_KLASS_ENUM_NAME(reference_klass), scan, CHECK);

  // Preload ref klasses and set reference types
  instanceKlass::cast(WK_KLASS(reference_klass))->set_reference_type(REF_OTHER);
  instanceRefKlass::update_nonstatic_oop_maps(WK_KLASS(reference_klass));

  initialize_wk_klasses_through(WK_KLASS_ENUM_NAME(phantom_reference_klass), scan, CHECK);
  instanceKlass::cast(WK_KLASS(soft_reference_klass))->set_reference_type(REF_SOFT);
  instanceKlass::cast(WK_KLASS(weak_reference_klass))->set_reference_type(REF_WEAK);
  instanceKlass::cast(WK_KLASS(final_reference_klass))->set_reference_type(REF_FINAL);
  instanceKlass::cast(WK_KLASS(phantom_reference_klass))->set_reference_type(REF_PHANTOM);

  WKID meth_group_start = WK_KLASS_ENUM_NAME(MethodHandle_klass);
  WKID meth_group_end   = WK_KLASS_ENUM_NAME(WrongMethodTypeException_klass);
  initialize_wk_klasses_until(meth_group_start, scan, CHECK);
  if (EnableMethodHandles) {
    initialize_wk_klasses_through(meth_group_end, scan, CHECK);
  }
  if (_well_known_klasses[meth_group_start] == NULL) {
    // Skip the rest of the method handle classes, if MethodHandle is not loaded.
    scan = WKID(meth_group_end+1);
  }
  WKID indy_group_start = WK_KLASS_ENUM_NAME(Linkage_klass);
  WKID indy_group_end   = WK_KLASS_ENUM_NAME(Dynamic_klass);
  initialize_wk_klasses_until(indy_group_start, scan, CHECK);
  if (EnableInvokeDynamic) {
    initialize_wk_klasses_through(indy_group_start, scan, CHECK);
  }
  if (_well_known_klasses[indy_group_start] == NULL) {
    // Skip the rest of the dynamic typing classes, if Linkage is not loaded.
    scan = WKID(indy_group_end+1);
  }

  initialize_wk_klasses_until(WKID_LIMIT, scan, CHECK);

  _box_klasses[T_BOOLEAN] = WK_KLASS(boolean_klass);
  _box_klasses[T_CHAR]    = WK_KLASS(char_klass);
  _box_klasses[T_FLOAT]   = WK_KLASS(float_klass);
  _box_klasses[T_DOUBLE]  = WK_KLASS(double_klass);
  _box_klasses[T_BYTE]    = WK_KLASS(byte_klass);
  _box_klasses[T_SHORT]   = WK_KLASS(short_klass);
  _box_klasses[T_INT]     = WK_KLASS(int_klass);
  _box_klasses[T_LONG]    = WK_KLASS(long_klass);
  //_box_klasses[T_OBJECT]  = WK_KLASS(object_klass);
  //_box_klasses[T_ARRAY]   = WK_KLASS(object_klass);

#ifdef KERNEL
  if (sun_jkernel_DownloadManager_klass() == NULL) {
    warning("Cannot find sun/jkernel/DownloadManager");
  }
#endif // KERNEL

  { // Compute whether we should use loadClass or loadClassInternal when loading classes.
    methodOop method = instanceKlass::cast(classloader_klass())->find_method(vmSymbols::loadClassInternal_name(), vmSymbols::string_class_signature());
    _has_loadClassInternal = (method != NULL);
  }
  { // Compute whether we should use checkPackageAccess or NOT
    methodOop method = instanceKlass::cast(classloader_klass())->find_method(vmSymbols::checkPackageAccess_name(), vmSymbols::class_protectiondomain_signature());
    _has_checkPackageAccess = (method != NULL);
  }
}

// Tells if a given klass is a box (wrapper class, such as java.lang.Integer).
// If so, returns the basic type it holds.  If not, returns T_OBJECT.
BasicType SystemDictionary::box_klass_type(klassOop k) {
  assert(k != NULL, "");
  for (int i = T_BOOLEAN; i < T_VOID+1; i++) {
    if (_box_klasses[i] == k)
      return (BasicType)i;
  }
  return T_OBJECT;
}

KlassHandle SystemDictionaryHandles::box_klass(BasicType t) {
  if (t >= T_BOOLEAN && t <= T_VOID)
    return KlassHandle(&SystemDictionary::_box_klasses[t], true);
  else
    return KlassHandle();
}

// Constraints on class loaders. The details of the algorithm can be
// found in the OOPSLA'98 paper "Dynamic Class Loading in the Java
// Virtual Machine" by Sheng Liang and Gilad Bracha.  The basic idea is
// that the system dictionary needs to maintain a set of contraints that
// must be satisfied by all classes in the dictionary.
// if defining is true, then LinkageError if already in systemDictionary
// if initiating loader, then ok if instanceKlass matches existing entry

void SystemDictionary::check_constraints(int d_index, unsigned int d_hash,
                                         instanceKlassHandle k,
                                         Handle class_loader, bool defining,
                                         TRAPS) {
  const char *linkage_error = NULL;
  {
    symbolHandle name (THREAD, k->name());
    MutexLocker mu(SystemDictionary_lock, THREAD);

    klassOop check = find_class(d_index, d_hash, name, class_loader);
    if (check != (klassOop)NULL) {
      // if different instanceKlass - duplicate class definition,
      // else - ok, class loaded by a different thread in parallel,
      // we should only have found it if it was done loading and ok to use
      // system dictionary only holds instance classes, placeholders
      // also holds array classes

      assert(check->klass_part()->oop_is_instance(), "noninstance in systemdictionary");
      if ((defining == true) || (k() != check)) {
        linkage_error = "loader (instance of  %s): attempted  duplicate class "
          "definition for name: \"%s\"";
      } else {
        return;
      }
    }

#ifdef ASSERT
    unsigned int p_hash = placeholders()->compute_hash(name, class_loader);
    int p_index = placeholders()->hash_to_index(p_hash);
    symbolOop ph_check = find_placeholder(p_index, p_hash, name, class_loader);
    assert(ph_check == NULL || ph_check == name(), "invalid symbol");
#endif

    if (linkage_error == NULL) {
      if (constraints()->check_or_update(k, class_loader, name) == false) {
        linkage_error = "loader constraint violation: loader (instance of %s)"
          " previously initiated loading for a different type with name \"%s\"";
      }
    }
  }

  // Throw error now if needed (cannot throw while holding
  // SystemDictionary_lock because of rank ordering)

  if (linkage_error) {
    ResourceMark rm(THREAD);
    const char* class_loader_name = loader_name(class_loader());
    char* type_name = k->name()->as_C_string();
    size_t buflen = strlen(linkage_error) + strlen(class_loader_name) +
      strlen(type_name);
    char* buf = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, buflen);
    jio_snprintf(buf, buflen, linkage_error, class_loader_name, type_name);
    THROW_MSG(vmSymbols::java_lang_LinkageError(), buf);
  }
}


// Update system dictionary - done after check_constraint and add_to_hierachy
// have been called.
void SystemDictionary::update_dictionary(int d_index, unsigned int d_hash,
                                         int p_index, unsigned int p_hash,
                                         instanceKlassHandle k,
                                         Handle class_loader,
                                         TRAPS) {
  // Compile_lock prevents systemDictionary updates during compilations
  assert_locked_or_safepoint(Compile_lock);
  symbolHandle name (THREAD, k->name());

  {
  MutexLocker mu1(SystemDictionary_lock, THREAD);

  // See whether biased locking is enabled and if so set it for this
  // klass.
  // Note that this must be done past the last potential blocking
  // point / safepoint. We enable biased locking lazily using a
  // VM_Operation to iterate the SystemDictionary and installing the
  // biasable mark word into each instanceKlass's prototype header.
  // To avoid race conditions where we accidentally miss enabling the
  // optimization for one class in the process of being added to the
  // dictionary, we must not safepoint after the test of
  // BiasedLocking::enabled().
  if (UseBiasedLocking && BiasedLocking::enabled()) {
    // Set biased locking bit for all loaded classes; it will be
    // cleared if revocation occurs too often for this type
    // NOTE that we must only do this when the class is initally
    // defined, not each time it is referenced from a new class loader
    if (k->class_loader() == class_loader()) {
      k->set_prototype_header(markOopDesc::biased_locking_prototype());
    }
  }

  // Check for a placeholder. If there, remove it and make a
  // new system dictionary entry.
  placeholders()->find_and_remove(p_index, p_hash, name, class_loader, THREAD);
  klassOop sd_check = find_class(d_index, d_hash, name, class_loader);
  if (sd_check == NULL) {
    dictionary()->add_klass(name, class_loader, k);
    notice_modification();
  }
#ifdef ASSERT
  sd_check = find_class(d_index, d_hash, name, class_loader);
  assert (sd_check != NULL, "should have entry in system dictionary");
// Changed to allow PH to remain to complete class circularity checking
// while only one thread can define a class at one time, multiple
// classes can resolve the superclass for a class at one time,
// and the placeholder is used to track that
//  symbolOop ph_check = find_placeholder(p_index, p_hash, name, class_loader);
//  assert (ph_check == NULL, "should not have a placeholder entry");
#endif
    SystemDictionary_lock->notify_all();
  }
}


klassOop SystemDictionary::find_constrained_instance_or_array_klass(
                    symbolHandle class_name, Handle class_loader, TRAPS) {

  // First see if it has been loaded directly.
  // Force the protection domain to be null.  (This removes protection checks.)
  Handle no_protection_domain;
  klassOop klass = find_instance_or_array_klass(class_name, class_loader,
                                                no_protection_domain, CHECK_NULL);
  if (klass != NULL)
    return klass;

  // Now look to see if it has been loaded elsewhere, and is subject to
  // a loader constraint that would require this loader to return the
  // klass that is already loaded.
  if (FieldType::is_array(class_name())) {
    // For array classes, their klassOops are not kept in the
    // constraint table. The element klassOops are.
    jint dimension;
    symbolOop object_key;
    BasicType t = FieldType::get_array_info(class_name(), &dimension,
                                            &object_key, CHECK_(NULL));
    if (t != T_OBJECT) {
      klass = Universe::typeArrayKlassObj(t);
    } else {
      symbolHandle elem_name(THREAD, object_key);
      MutexLocker mu(SystemDictionary_lock, THREAD);
      klass = constraints()->find_constrained_klass(elem_name, class_loader);
    }
    // If element class already loaded, allocate array klass
    if (klass != NULL) {
      klass = Klass::cast(klass)->array_klass_or_null(dimension);
    }
  } else {
    MutexLocker mu(SystemDictionary_lock, THREAD);
    // Non-array classes are easy: simply check the constraint table.
    klass = constraints()->find_constrained_klass(class_name, class_loader);
  }

  return klass;
}


bool SystemDictionary::add_loader_constraint(symbolHandle class_name,
                                             Handle class_loader1,
                                             Handle class_loader2,
                                             Thread* THREAD) {
  symbolHandle constraint_name;
  if (!FieldType::is_array(class_name())) {
    constraint_name = class_name;
  } else {
    // For array classes, their klassOops are not kept in the
    // constraint table. The element classes are.
    jint dimension;
    symbolOop object_key;
    BasicType t = FieldType::get_array_info(class_name(), &dimension,
                                            &object_key, CHECK_(false));
    // primitive types always pass
    if (t != T_OBJECT) {
      return true;
    } else {
      constraint_name = symbolHandle(THREAD, object_key);
    }
  }
  unsigned int d_hash1 = dictionary()->compute_hash(constraint_name, class_loader1);
  int d_index1 = dictionary()->hash_to_index(d_hash1);

  unsigned int d_hash2 = dictionary()->compute_hash(constraint_name, class_loader2);
  int d_index2 = dictionary()->hash_to_index(d_hash2);
  {
  MutexLocker mu_s(SystemDictionary_lock, THREAD);

  // Better never do a GC while we're holding these oops
  No_Safepoint_Verifier nosafepoint;

  klassOop klass1 = find_class(d_index1, d_hash1, constraint_name, class_loader1);
  klassOop klass2 = find_class(d_index2, d_hash2, constraint_name, class_loader2);
  return constraints()->add_entry(constraint_name, klass1, class_loader1,
                                  klass2, class_loader2);
  }
}

// Add entry to resolution error table to record the error when the first
// attempt to resolve a reference to a class has failed.
void SystemDictionary::add_resolution_error(constantPoolHandle pool, int which, symbolHandle error) {
  unsigned int hash = resolution_errors()->compute_hash(pool, which);
  int index = resolution_errors()->hash_to_index(hash);
  {
    MutexLocker ml(SystemDictionary_lock, Thread::current());
    resolution_errors()->add_entry(index, hash, pool, which, error);
  }
}

// Lookup resolution error table. Returns error if found, otherwise NULL.
symbolOop SystemDictionary::find_resolution_error(constantPoolHandle pool, int which) {
  unsigned int hash = resolution_errors()->compute_hash(pool, which);
  int index = resolution_errors()->hash_to_index(hash);
  {
    MutexLocker ml(SystemDictionary_lock, Thread::current());
    ResolutionErrorEntry* entry = resolution_errors()->find_entry(index, hash, pool, which);
    return (entry != NULL) ? entry->error() : (symbolOop)NULL;
  }
}


// Signature constraints ensure that callers and callees agree about
// the meaning of type names in their signatures.  This routine is the
// intake for constraints.  It collects them from several places:
//
//  * LinkResolver::resolve_method (if check_access is true) requires
//    that the resolving class (the caller) and the defining class of
//    the resolved method (the callee) agree on each type in the
//    method's signature.
//
//  * LinkResolver::resolve_interface_method performs exactly the same
//    checks.
//
//  * LinkResolver::resolve_field requires that the constant pool
//    attempting to link to a field agree with the field's defining
//    class about the type of the field signature.
//
//  * klassVtable::initialize_vtable requires that, when a class
//    overrides a vtable entry allocated by a superclass, that the
//    overriding method (i.e., the callee) agree with the superclass
//    on each type in the method's signature.
//
//  * klassItable::initialize_itable requires that, when a class fills
//    in its itables, for each non-abstract method installed in an
//    itable, the method (i.e., the callee) agree with the interface
//    on each type in the method's signature.
//
// All those methods have a boolean (check_access, checkconstraints)
// which turns off the checks.  This is used from specialized contexts
// such as bootstrapping, dumping, and debugging.
//
// No direct constraint is placed between the class and its
// supertypes.  Constraints are only placed along linked relations
// between callers and callees.  When a method overrides or implements
// an abstract method in a supertype (superclass or interface), the
// constraints are placed as if the supertype were the caller to the
// overriding method.  (This works well, since callers to the
// supertype have already established agreement between themselves and
// the supertype.)  As a result of all this, a class can disagree with
// its supertype about the meaning of a type name, as long as that
// class neither calls a relevant method of the supertype, nor is
// called (perhaps via an override) from the supertype.
//
//
// SystemDictionary::check_signature_loaders(sig, l1, l2)
//
// Make sure all class components (including arrays) in the given
// signature will be resolved to the same class in both loaders.
// Returns the name of the type that failed a loader constraint check, or
// NULL if no constraint failed. The returned C string needs cleaning up
// with a ResourceMark in the caller.  No exception except OOME is thrown.
// Arrays are not added to the loader constraint table, their elements are.
char* SystemDictionary::check_signature_loaders(symbolHandle signature,
                                               Handle loader1, Handle loader2,
                                               bool is_method, TRAPS)  {
  // Nothing to do if loaders are the same.
  if (loader1() == loader2()) {
    return NULL;
  }

  SignatureStream sig_strm(signature, is_method);
  while (!sig_strm.is_done()) {
    if (sig_strm.is_object()) {
      symbolOop s = sig_strm.as_symbol(CHECK_NULL);
      symbolHandle sig (THREAD, s);
      if (!add_loader_constraint(sig, loader1, loader2, THREAD)) {
        return sig()->as_C_string();
      }
    }
    sig_strm.next();
  }
  return NULL;
}


methodOop SystemDictionary::find_method_handle_invoke(symbolHandle signature,
                                                      Handle class_loader,
                                                      Handle protection_domain,
                                                      TRAPS) {
  if (!EnableMethodHandles)  return NULL;
  assert(class_loader.is_null() && protection_domain.is_null(),
         "cannot load specialized versions of MethodHandle.invoke");
  if (invoke_method_table() == NULL) {
    // create this side table lazily
    _invoke_method_table = new SymbolPropertyTable(_invoke_method_size);
  }
  unsigned int hash  = invoke_method_table()->compute_hash(signature);
  int          index = invoke_method_table()->hash_to_index(hash);
  SymbolPropertyEntry* spe = invoke_method_table()->find_entry(index, hash, signature);
  if (spe == NULL || spe->property_oop() == NULL) {
    // Must create lots of stuff here, but outside of the SystemDictionary lock.
    Handle mt = compute_method_handle_type(signature(),
                                           class_loader, protection_domain,
                                           CHECK_NULL);
    KlassHandle  mh_klass = SystemDictionaryHandles::MethodHandle_klass();
    methodHandle m = methodOopDesc::make_invoke_method(mh_klass, signature,
                                                       mt, CHECK_NULL);
    // Now grab the lock.  We might have to throw away the new method,
    // if a racing thread has managed to install one at the same time.
    {
      MutexLocker ml(SystemDictionary_lock, Thread::current());
      spe = invoke_method_table()->find_entry(index, hash, signature);
      if (spe == NULL)
        spe = invoke_method_table()->add_entry(index, hash, signature);
      if (spe->property_oop() == NULL)
        spe->set_property_oop(m());
    }
  }
  methodOop m = (methodOop) spe->property_oop();
  assert(m->is_method(), "");
  return m;
}

// Ask Java code to find or construct a java.dyn.MethodType for the given
// signature, as interpreted relative to the given class loader.
// Because of class loader constraints, all method handle usage must be
// consistent with this loader.
Handle SystemDictionary::compute_method_handle_type(symbolHandle signature,
                                                    Handle class_loader,
                                                    Handle protection_domain,
                                                    TRAPS) {
  Handle empty;
  int npts = ArgumentCount(signature()).size();
  objArrayHandle pts = oopFactory::new_objArray(SystemDictionary::class_klass(), npts, CHECK_(empty));
  int arg = 0;
  Handle rt;                            // the return type from the signature
  for (SignatureStream ss(signature()); !ss.is_done(); ss.next()) {
    oop mirror;
    if (!ss.is_object()) {
      mirror = Universe::java_mirror(ss.type());
    } else {
      symbolOop    name_oop = ss.as_symbol(CHECK_(empty));
      symbolHandle name(THREAD, name_oop);
      klassOop klass = resolve_or_fail(name,
                                       class_loader, protection_domain,
                                       true, CHECK_(empty));
      mirror = Klass::cast(klass)->java_mirror();
    }
    if (ss.at_return_type())
      rt = Handle(THREAD, mirror);
    else
      pts->obj_at_put(arg++, mirror);
  }
  assert(arg == npts, "");

  // call MethodType java.dyn.MethodType::makeImpl(Class rt, Class[] pts, false, true)
  bool varargs = false, trusted = true;
  JavaCallArguments args(Handle(THREAD, rt()));
  args.push_oop(pts());
  args.push_int(false);
  args.push_int(trusted);
  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result,
                         SystemDictionary::MethodType_klass(),
                         vmSymbols::makeImpl_name(), vmSymbols::makeImpl_signature(),
                         &args, CHECK_(empty));
  return Handle(THREAD, (oop) result.get_jobject());
}


// Ask Java code to find or construct a java.dyn.CallSite for the given
// name and signature, as interpreted relative to the given class loader.
Handle SystemDictionary::make_dynamic_call_site(KlassHandle caller,
                                                int caller_method_idnum,
                                                int caller_bci,
                                                symbolHandle name,
                                                methodHandle mh_invdyn,
                                                TRAPS) {
  Handle empty;
  // call sun.dyn.CallSiteImpl::makeSite(caller, name, mtype, cmid, cbci)
  oop name_str_oop = StringTable::intern(name(), CHECK_(empty)); // not a handle!
  JavaCallArguments args(Handle(THREAD, caller->java_mirror()));
  args.push_oop(name_str_oop);
  args.push_oop(mh_invdyn->method_handle_type());
  args.push_int(caller_method_idnum);
  args.push_int(caller_bci);
  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result,
                         SystemDictionary::CallSiteImpl_klass(),
                         vmSymbols::makeSite_name(), vmSymbols::makeSite_signature(),
                         &args, CHECK_(empty));
  oop call_site_oop = (oop) result.get_jobject();
  assert(call_site_oop->is_oop()
         /*&& sun_dyn_CallSiteImpl::is_instance(call_site_oop)*/, "must be sane");
  sun_dyn_CallSiteImpl::set_vmmethod(call_site_oop, mh_invdyn());
  if (TraceMethodHandles) {
    tty->print_cr("Linked invokedynamic bci=%d site="INTPTR_FORMAT":", caller_bci, call_site_oop);
    call_site_oop->print();
    tty->cr();
  }
  return call_site_oop;
}

Handle SystemDictionary::find_bootstrap_method(KlassHandle caller,
                                               KlassHandle search_bootstrap_klass,
                                               TRAPS) {
  Handle empty;
  if (!caller->oop_is_instance())  return empty;

  instanceKlassHandle ik(THREAD, caller());

  if (ik->bootstrap_method() != NULL) {
    return Handle(THREAD, ik->bootstrap_method());
  }

  // call java.dyn.Linkage::findBootstrapMethod(caller, sbk)
  JavaCallArguments args(Handle(THREAD, ik->java_mirror()));
  if (search_bootstrap_klass.is_null())
    args.push_oop(Handle());
  else
    args.push_oop(search_bootstrap_klass->java_mirror());
  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result,
                         SystemDictionary::Linkage_klass(),
                         vmSymbols::findBootstrapMethod_name(),
                         vmSymbols::findBootstrapMethod_signature(),
                         &args, CHECK_(empty));
  oop boot_method_oop = (oop) result.get_jobject();

  if (boot_method_oop != NULL) {
    assert(boot_method_oop->is_oop()
           && java_dyn_MethodHandle::is_instance(boot_method_oop), "must be sane");
    // probably no race conditions, but let's be careful:
    if (Atomic::cmpxchg_ptr(boot_method_oop, ik->adr_bootstrap_method(), NULL) == NULL)
      ik->set_bootstrap_method(boot_method_oop);
    else
      boot_method_oop = ik->bootstrap_method();
  } else {
    boot_method_oop = ik->bootstrap_method();
  }

  return Handle(THREAD, boot_method_oop);
}

// Since the identity hash code for symbols changes when the symbols are
// moved from the regular perm gen (hash in the mark word) to the shared
// spaces (hash is the address), the classes loaded into the dictionary
// may be in the wrong buckets.

void SystemDictionary::reorder_dictionary() {
  dictionary()->reorder_dictionary();
}


void SystemDictionary::copy_buckets(char** top, char* end) {
  dictionary()->copy_buckets(top, end);
}


void SystemDictionary::copy_table(char** top, char* end) {
  dictionary()->copy_table(top, end);
}


void SystemDictionary::reverse() {
  dictionary()->reverse();
}

int SystemDictionary::number_of_classes() {
  return dictionary()->number_of_entries();
}


// ----------------------------------------------------------------------------
#ifndef PRODUCT

void SystemDictionary::print() {
  dictionary()->print();

  // Placeholders
  GCMutexLocker mu(SystemDictionary_lock);
  placeholders()->print();

  // loader constraints - print under SD_lock
  constraints()->print();
}

#endif

void SystemDictionary::verify() {
  guarantee(dictionary() != NULL, "Verify of system dictionary failed");
  guarantee(constraints() != NULL,
            "Verify of loader constraints failed");
  guarantee(dictionary()->number_of_entries() >= 0 &&
            placeholders()->number_of_entries() >= 0,
            "Verify of system dictionary failed");

  // Verify dictionary
  dictionary()->verify();

  GCMutexLocker mu(SystemDictionary_lock);
  placeholders()->verify();

  // Verify constraint table
  guarantee(constraints() != NULL, "Verify of loader constraints failed");
  constraints()->verify(dictionary());
}


void SystemDictionary::verify_obj_klass_present(Handle obj,
                                                symbolHandle class_name,
                                                Handle class_loader) {
  GCMutexLocker mu(SystemDictionary_lock);
  oop probe = find_class_or_placeholder(class_name, class_loader);
  if (probe == NULL) {
    probe = SystemDictionary::find_shared_class(class_name);
  }
  guarantee(probe != NULL &&
            (!probe->is_klass() || probe == obj()),
                     "Loaded klasses should be in SystemDictionary");
}

#ifndef PRODUCT

// statistics code
class ClassStatistics: AllStatic {
 private:
  static int nclasses;        // number of classes
  static int nmethods;        // number of methods
  static int nmethoddata;     // number of methodData
  static int class_size;      // size of class objects in words
  static int method_size;     // size of method objects in words
  static int debug_size;      // size of debug info in methods
  static int methoddata_size; // size of methodData objects in words

  static void do_class(klassOop k) {
    nclasses++;
    class_size += k->size();
    if (k->klass_part()->oop_is_instance()) {
      instanceKlass* ik = (instanceKlass*)k->klass_part();
      class_size += ik->methods()->size();
      class_size += ik->constants()->size();
      class_size += ik->local_interfaces()->size();
      class_size += ik->transitive_interfaces()->size();
      // We do not have to count implementors, since we only store one!
      class_size += ik->fields()->size();
    }
  }

  static void do_method(methodOop m) {
    nmethods++;
    method_size += m->size();
    // class loader uses same objArray for empty vectors, so don't count these
    if (m->exception_table()->length() != 0)   method_size += m->exception_table()->size();
    if (m->has_stackmap_table()) {
      method_size += m->stackmap_data()->size();
    }

    methodDataOop mdo = m->method_data();
    if (mdo != NULL) {
      nmethoddata++;
      methoddata_size += mdo->size();
    }
  }

 public:
  static void print() {
    SystemDictionary::classes_do(do_class);
    SystemDictionary::methods_do(do_method);
    tty->print_cr("Class statistics:");
    tty->print_cr("%d classes (%d bytes)", nclasses, class_size * oopSize);
    tty->print_cr("%d methods (%d bytes = %d base + %d debug info)", nmethods,
                  (method_size + debug_size) * oopSize, method_size * oopSize, debug_size * oopSize);
    tty->print_cr("%d methoddata (%d bytes)", nmethoddata, methoddata_size * oopSize);
  }
};


int ClassStatistics::nclasses        = 0;
int ClassStatistics::nmethods        = 0;
int ClassStatistics::nmethoddata     = 0;
int ClassStatistics::class_size      = 0;
int ClassStatistics::method_size     = 0;
int ClassStatistics::debug_size      = 0;
int ClassStatistics::methoddata_size = 0;

void SystemDictionary::print_class_statistics() {
  ResourceMark rm;
  ClassStatistics::print();
}


class MethodStatistics: AllStatic {
 public:
  enum {
    max_parameter_size = 10
  };
 private:

  static int _number_of_methods;
  static int _number_of_final_methods;
  static int _number_of_static_methods;
  static int _number_of_native_methods;
  static int _number_of_synchronized_methods;
  static int _number_of_profiled_methods;
  static int _number_of_bytecodes;
  static int _parameter_size_profile[max_parameter_size];
  static int _bytecodes_profile[Bytecodes::number_of_java_codes];

  static void initialize() {
    _number_of_methods        = 0;
    _number_of_final_methods  = 0;
    _number_of_static_methods = 0;
    _number_of_native_methods = 0;
    _number_of_synchronized_methods = 0;
    _number_of_profiled_methods = 0;
    _number_of_bytecodes      = 0;
    for (int i = 0; i < max_parameter_size             ; i++) _parameter_size_profile[i] = 0;
    for (int j = 0; j < Bytecodes::number_of_java_codes; j++) _bytecodes_profile     [j] = 0;
  };

  static void do_method(methodOop m) {
    _number_of_methods++;
    // collect flag info
    if (m->is_final()       ) _number_of_final_methods++;
    if (m->is_static()      ) _number_of_static_methods++;
    if (m->is_native()      ) _number_of_native_methods++;
    if (m->is_synchronized()) _number_of_synchronized_methods++;
    if (m->method_data() != NULL) _number_of_profiled_methods++;
    // collect parameter size info (add one for receiver, if any)
    _parameter_size_profile[MIN2(m->size_of_parameters() + (m->is_static() ? 0 : 1), max_parameter_size - 1)]++;
    // collect bytecodes info
    {
      Thread *thread = Thread::current();
      HandleMark hm(thread);
      BytecodeStream s(methodHandle(thread, m));
      Bytecodes::Code c;
      while ((c = s.next()) >= 0) {
        _number_of_bytecodes++;
        _bytecodes_profile[c]++;
      }
    }
  }

 public:
  static void print() {
    initialize();
    SystemDictionary::methods_do(do_method);
    // generate output
    tty->cr();
    tty->print_cr("Method statistics (static):");
    // flag distribution
    tty->cr();
    tty->print_cr("%6d final        methods  %6.1f%%", _number_of_final_methods       , _number_of_final_methods        * 100.0F / _number_of_methods);
    tty->print_cr("%6d static       methods  %6.1f%%", _number_of_static_methods      , _number_of_static_methods       * 100.0F / _number_of_methods);
    tty->print_cr("%6d native       methods  %6.1f%%", _number_of_native_methods      , _number_of_native_methods       * 100.0F / _number_of_methods);
    tty->print_cr("%6d synchronized methods  %6.1f%%", _number_of_synchronized_methods, _number_of_synchronized_methods * 100.0F / _number_of_methods);
    tty->print_cr("%6d profiled     methods  %6.1f%%", _number_of_profiled_methods, _number_of_profiled_methods * 100.0F / _number_of_methods);
    // parameter size profile
    tty->cr();
    { int tot = 0;
      int avg = 0;
      for (int i = 0; i < max_parameter_size; i++) {
        int n = _parameter_size_profile[i];
        tot += n;
        avg += n*i;
        tty->print_cr("parameter size = %1d: %6d methods  %5.1f%%", i, n, n * 100.0F / _number_of_methods);
      }
      assert(tot == _number_of_methods, "should be the same");
      tty->print_cr("                    %6d methods  100.0%%", _number_of_methods);
      tty->print_cr("(average parameter size = %3.1f including receiver, if any)", (float)avg / _number_of_methods);
    }
    // bytecodes profile
    tty->cr();
    { int tot = 0;
      for (int i = 0; i < Bytecodes::number_of_java_codes; i++) {
        if (Bytecodes::is_defined(i)) {
          Bytecodes::Code c = Bytecodes::cast(i);
          int n = _bytecodes_profile[c];
          tot += n;
          tty->print_cr("%9d  %7.3f%%  %s", n, n * 100.0F / _number_of_bytecodes, Bytecodes::name(c));
        }
      }
      assert(tot == _number_of_bytecodes, "should be the same");
      tty->print_cr("%9d  100.000%%", _number_of_bytecodes);
    }
    tty->cr();
  }
};

int MethodStatistics::_number_of_methods;
int MethodStatistics::_number_of_final_methods;
int MethodStatistics::_number_of_static_methods;
int MethodStatistics::_number_of_native_methods;
int MethodStatistics::_number_of_synchronized_methods;
int MethodStatistics::_number_of_profiled_methods;
int MethodStatistics::_number_of_bytecodes;
int MethodStatistics::_parameter_size_profile[MethodStatistics::max_parameter_size];
int MethodStatistics::_bytecodes_profile[Bytecodes::number_of_java_codes];


void SystemDictionary::print_method_statistics() {
  MethodStatistics::print();
}

#endif // PRODUCT
