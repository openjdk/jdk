/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/loaderConstraints.hpp"
#include "classfile/placeholders.hpp"
#include "classfile/resolutionErrors.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "compiler/compileBroker.hpp"
#include "gc/shared/gcLocker.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/filemap.hpp"
#include "memory/oopFactory.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayKlass.hpp"
#include "prims/jvmtiEnvBase.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/arguments.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/fieldType.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/orderAccess.inline.hpp"
#include "runtime/signature.hpp"
#include "services/classLoadingService.hpp"
#include "services/threadService.hpp"
#include "utilities/macros.hpp"
#include "utilities/ticks.hpp"
#if INCLUDE_CDS
#include "classfile/sharedClassUtil.hpp"
#include "classfile/systemDictionaryShared.hpp"
#endif
#if INCLUDE_JVMCI
#include "jvmci/jvmciRuntime.hpp"
#endif
#if INCLUDE_TRACE
#include "trace/tracing.hpp"
#endif

Dictionary*            SystemDictionary::_dictionary          = NULL;
PlaceholderTable*      SystemDictionary::_placeholders        = NULL;
Dictionary*            SystemDictionary::_shared_dictionary   = NULL;
LoaderConstraintTable* SystemDictionary::_loader_constraints  = NULL;
ResolutionErrorTable*  SystemDictionary::_resolution_errors   = NULL;
SymbolPropertyTable*   SystemDictionary::_invoke_method_table = NULL;


int         SystemDictionary::_number_of_modifications = 0;
int         SystemDictionary::_sdgeneration               = 0;
const int   SystemDictionary::_primelist[_prime_array_size] = {1009,2017,4049,5051,10103,
              20201,40423,99991};

oop         SystemDictionary::_system_loader_lock_obj     =  NULL;

Klass*      SystemDictionary::_well_known_klasses[SystemDictionary::WKID_LIMIT]
                                                          =  { NULL /*, NULL...*/ };

Klass*      SystemDictionary::_box_klasses[T_VOID+1]      =  { NULL /*, NULL...*/ };

oop         SystemDictionary::_java_system_loader         =  NULL;

bool        SystemDictionary::_has_loadClassInternal      =  false;
bool        SystemDictionary::_has_checkPackageAccess     =  false;

// lazily initialized klass variables
Klass* volatile SystemDictionary::_abstract_ownable_synchronizer_klass = NULL;


// ----------------------------------------------------------------------------
// Java-level SystemLoader

oop SystemDictionary::java_system_loader() {
  return _java_system_loader;
}

void SystemDictionary::compute_java_system_loader(TRAPS) {
  KlassHandle system_klass(THREAD, WK_KLASS(ClassLoader_klass));
  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result,
                         KlassHandle(THREAD, WK_KLASS(ClassLoader_klass)),
                         vmSymbols::getSystemClassLoader_name(),
                         vmSymbols::void_classloader_signature(),
                         CHECK);

  _java_system_loader = (oop)result.get_jobject();

  CDS_ONLY(SystemDictionaryShared::initialize(CHECK);)
}


ClassLoaderData* SystemDictionary::register_loader(Handle class_loader, TRAPS) {
  if (class_loader() == NULL) return ClassLoaderData::the_null_class_loader_data();
  return ClassLoaderDataGraph::find_or_create(class_loader, THREAD);
}

// ----------------------------------------------------------------------------
// debugging

#ifdef ASSERT

// return true if class_name contains no '.' (internal format is '/')
bool SystemDictionary::is_internal_format(Symbol* class_name) {
  if (class_name != NULL) {
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
  return java_lang_ClassLoader::parallelCapable(class_loader());
}
// ----------------------------------------------------------------------------
// ParallelDefineClass flag does not apply to bootclass loader
bool SystemDictionary::is_parallelDefine(Handle class_loader) {
   if (class_loader.is_null()) return false;
   if (AllowParallelDefineClass && java_lang_ClassLoader::parallelCapable(class_loader())) {
     return true;
   }
   return false;
}

/**
 * Returns true if the passed class loader is the extension class loader.
 */
bool SystemDictionary::is_ext_class_loader(Handle class_loader) {
  if (class_loader.is_null()) {
    return false;
  }
  return (class_loader->klass()->name() == vmSymbols::sun_misc_Launcher_ExtClassLoader());
}

// ----------------------------------------------------------------------------
// Resolving of classes

// Forwards to resolve_or_null

Klass* SystemDictionary::resolve_or_fail(Symbol* class_name, Handle class_loader, Handle protection_domain, bool throw_error, TRAPS) {
  Klass* klass = resolve_or_null(class_name, class_loader, protection_domain, THREAD);
  if (HAS_PENDING_EXCEPTION || klass == NULL) {
    KlassHandle k_h(THREAD, klass);
    // can return a null klass
    klass = handle_resolution_exception(class_name, throw_error, k_h, THREAD);
  }
  return klass;
}

Klass* SystemDictionary::handle_resolution_exception(Symbol* class_name,
                                                     bool throw_error,
                                                     KlassHandle klass_h, TRAPS) {
  if (HAS_PENDING_EXCEPTION) {
    // If we have a pending exception we forward it to the caller, unless throw_error is true,
    // in which case we have to check whether the pending exception is a ClassNotFoundException,
    // and if so convert it to a NoClassDefFoundError
    // And chain the original ClassNotFoundException
    if (throw_error && PENDING_EXCEPTION->is_a(SystemDictionary::ClassNotFoundException_klass())) {
      ResourceMark rm(THREAD);
      assert(klass_h() == NULL, "Should not have result with exception pending");
      Handle e(THREAD, PENDING_EXCEPTION);
      CLEAR_PENDING_EXCEPTION;
      THROW_MSG_CAUSE_NULL(vmSymbols::java_lang_NoClassDefFoundError(), class_name->as_C_string(), e);
    } else {
      return NULL;
    }
  }
  // Class not found, throw appropriate error or exception depending on value of throw_error
  if (klass_h() == NULL) {
    ResourceMark rm(THREAD);
    if (throw_error) {
      THROW_MSG_NULL(vmSymbols::java_lang_NoClassDefFoundError(), class_name->as_C_string());
    } else {
      THROW_MSG_NULL(vmSymbols::java_lang_ClassNotFoundException(), class_name->as_C_string());
    }
  }
  return (Klass*)klass_h();
}


Klass* SystemDictionary::resolve_or_fail(Symbol* class_name,
                                           bool throw_error, TRAPS)
{
  return resolve_or_fail(class_name, Handle(), Handle(), throw_error, THREAD);
}


// Forwards to resolve_instance_class_or_null

Klass* SystemDictionary::resolve_or_null(Symbol* class_name, Handle class_loader, Handle protection_domain, TRAPS) {
  assert(THREAD->can_call_java(),
         "can not load classes with compiler thread: class=%s, classloader=%s",
         class_name->as_C_string(),
         class_loader.is_null() ? "null" : class_loader->klass()->name()->as_C_string());
  if (FieldType::is_array(class_name)) {
    return resolve_array_class_or_null(class_name, class_loader, protection_domain, THREAD);
  } else if (FieldType::is_obj(class_name)) {
    ResourceMark rm(THREAD);
    // Ignore wrapping L and ;.
    TempNewSymbol name = SymbolTable::new_symbol(class_name->as_C_string() + 1,
                                   class_name->utf8_length() - 2, CHECK_NULL);
    return resolve_instance_class_or_null(name, class_loader, protection_domain, THREAD);
  } else {
    return resolve_instance_class_or_null(class_name, class_loader, protection_domain, THREAD);
  }
}

Klass* SystemDictionary::resolve_or_null(Symbol* class_name, TRAPS) {
  return resolve_or_null(class_name, Handle(), Handle(), THREAD);
}

// Forwards to resolve_instance_class_or_null

Klass* SystemDictionary::resolve_array_class_or_null(Symbol* class_name,
                                                       Handle class_loader,
                                                       Handle protection_domain,
                                                       TRAPS) {
  assert(FieldType::is_array(class_name), "must be array");
  Klass* k = NULL;
  FieldArrayInfo fd;
  // dimension and object_key in FieldArrayInfo are assigned as a side-effect
  // of this call
  BasicType t = FieldType::get_array_info(class_name, fd, CHECK_NULL);
  if (t == T_OBJECT) {
    // naked oop "k" is OK here -- we assign back into it
    k = SystemDictionary::resolve_instance_class_or_null(fd.object_key(),
                                                         class_loader,
                                                         protection_domain,
                                                         CHECK_NULL);
    if (k != NULL) {
      k = k->array_klass(fd.dimension(), CHECK_NULL);
    }
  } else {
    k = Universe::typeArrayKlassObj(t);
    k = TypeArrayKlass::cast(k)->array_klass(fd.dimension(), CHECK_NULL);
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
Klass* SystemDictionary::resolve_super_or_fail(Symbol* child_name,
                                                 Symbol* class_name,
                                                 Handle class_loader,
                                                 Handle protection_domain,
                                                 bool is_superclass,
                                                 TRAPS) {
  // Double-check, if child class is already loaded, just return super-class,interface
  // Don't add a placedholder if already loaded, i.e. already in system dictionary
  // Make sure there's a placeholder for the *child* before resolving.
  // Used as a claim that this thread is currently loading superclass/classloader
  // Used here for ClassCircularity checks and also for heap verification
  // (every InstanceKlass in the heap needs to be in the system dictionary
  // or have a placeholder).
  // Must check ClassCircularity before checking if super class is already loaded
  //
  // We might not already have a placeholder if this child_name was
  // first seen via resolve_from_stream (jni_DefineClass or JVM_DefineClass);
  // the name of the class might not be known until the stream is actually
  // parsed.
  // Bugs 4643874, 4715493
  // compute_hash can have a safepoint

  ClassLoaderData* loader_data = class_loader_data(class_loader);
  unsigned int d_hash = dictionary()->compute_hash(child_name, loader_data);
  int d_index = dictionary()->hash_to_index(d_hash);
  unsigned int p_hash = placeholders()->compute_hash(child_name, loader_data);
  int p_index = placeholders()->hash_to_index(p_hash);
  // can't throw error holding a lock
  bool child_already_loaded = false;
  bool throw_circularity_error = false;
  {
    MutexLocker mu(SystemDictionary_lock, THREAD);
    Klass* childk = find_class(d_index, d_hash, child_name, loader_data);
    Klass* quicksuperk;
    // to support // loading: if child done loading, just return superclass
    // if class_name, & class_loader don't match:
    // if initial define, SD update will give LinkageError
    // if redefine: compare_class_versions will give HIERARCHY_CHANGED
    // so we don't throw an exception here.
    // see: nsk redefclass014 & java.lang.instrument Instrument032
    if ((childk != NULL ) && (is_superclass) &&
       ((quicksuperk = InstanceKlass::cast(childk)->super()) != NULL) &&

         ((quicksuperk->name() == class_name) &&
            (quicksuperk->class_loader()  == class_loader()))) {
           return quicksuperk;
    } else {
      PlaceholderEntry* probe = placeholders()->get_entry(p_index, p_hash, child_name, loader_data);
      if (probe && probe->check_seen_thread(THREAD, PlaceholderTable::LOAD_SUPER)) {
          throw_circularity_error = true;
      }
    }
    if (!throw_circularity_error) {
      PlaceholderEntry* newprobe = placeholders()->find_and_add(p_index, p_hash, child_name, loader_data, PlaceholderTable::LOAD_SUPER, class_name, THREAD);
    }
  }
  if (throw_circularity_error) {
      ResourceMark rm(THREAD);
      THROW_MSG_NULL(vmSymbols::java_lang_ClassCircularityError(), child_name->as_C_string());
  }

// java.lang.Object should have been found above
  assert(class_name != NULL, "null super class for resolving");
  // Resolve the super class or interface, check results on return
  Klass* superk = SystemDictionary::resolve_or_null(class_name,
                                                 class_loader,
                                                 protection_domain,
                                                 THREAD);

  KlassHandle superk_h(THREAD, superk);

  // Clean up of placeholders moved so that each classloadAction registrar self-cleans up
  // It is no longer necessary to keep the placeholder table alive until update_dictionary
  // or error. GC used to walk the placeholder table as strong roots.
  // The instanceKlass is kept alive because the class loader is on the stack,
  // which keeps the loader_data alive, as well as all instanceKlasses in
  // the loader_data. parseClassFile adds the instanceKlass to loader_data.
  {
    MutexLocker mu(SystemDictionary_lock, THREAD);
    placeholders()->find_and_remove(p_index, p_hash, child_name, loader_data, PlaceholderTable::LOAD_SUPER, THREAD);
    SystemDictionary_lock->notify_all();
  }
  if (HAS_PENDING_EXCEPTION || superk_h() == NULL) {
    // can null superk
    superk_h = KlassHandle(THREAD, handle_resolution_exception(class_name, true, superk_h, THREAD));
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

  KlassHandle system_loader(THREAD, SystemDictionary::ClassLoader_klass());
  JavaCalls::call_special(&result,
                         class_loader,
                         system_loader,
                         vmSymbols::checkPackageAccess_name(),
                         vmSymbols::class_protectiondomain_signature(),
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
    ClassLoaderData* loader_data = class_loader_data(class_loader);

    Symbol*  kn = klass->name();
    unsigned int d_hash = dictionary()->compute_hash(kn, loader_data);
    int d_index = dictionary()->hash_to_index(d_hash);

    MutexLocker mu(SystemDictionary_lock, THREAD);
    {
      // Note that we have an entry, and entries can be deleted only during GC,
      // so we cannot allow GC to occur while we're holding this entry.

      // We're using a No_Safepoint_Verifier to catch any place where we
      // might potentially do a GC at all.
      // Dictionary::do_unloading() asserts that classes in SD are only
      // unloaded at a safepoint. Anonymous classes are not in SD.
      No_Safepoint_Verifier nosafepoint;
      dictionary()->add_protection_domain(d_index, d_hash, klass, loader_data,
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
// Note: must call resolve_super_or_fail even if null super -
// to force placeholder entry creation for this class for circularity detection
// Caller must check for pending exception
// Returns non-null Klass* if other thread has completed load
// and we are done,
// If return null Klass* and no pending exception, the caller must load the class
instanceKlassHandle SystemDictionary::handle_parallel_super_load(
    Symbol* name, Symbol* superclassname, Handle class_loader,
    Handle protection_domain, Handle lockObject, TRAPS) {

  instanceKlassHandle nh = instanceKlassHandle(); // null Handle
  ClassLoaderData* loader_data = class_loader_data(class_loader);
  unsigned int d_hash = dictionary()->compute_hash(name, loader_data);
  int d_index = dictionary()->hash_to_index(d_hash);
  unsigned int p_hash = placeholders()->compute_hash(name, loader_data);
  int p_index = placeholders()->hash_to_index(p_hash);

  // superk is not used, resolve_super called for circularity check only
  // This code is reached in two situations. One if this thread
  // is loading the same class twice (e.g. ClassCircularity, or
  // java.lang.instrument).
  // The second is if another thread started the resolve_super first
  // and has not yet finished.
  // In both cases the original caller will clean up the placeholder
  // entry on error.
  Klass* superk = SystemDictionary::resolve_super_or_fail(name,
                                                          superclassname,
                                                          class_loader,
                                                          protection_domain,
                                                          true,
                                                          CHECK_(nh));

  // parallelCapable class loaders do NOT wait for parallel superclass loads to complete
  // Serial class loaders and bootstrap classloader do wait for superclass loads
 if (!class_loader.is_null() && is_parallelCapable(class_loader)) {
    MutexLocker mu(SystemDictionary_lock, THREAD);
    // Check if classloading completed while we were loading superclass or waiting
    Klass* check = find_class(d_index, d_hash, name, loader_data);
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
    Klass* check = find_class(d_index, d_hash, name, loader_data);
    if (check != NULL) {
      // Klass is already loaded, so just return it
      return(instanceKlassHandle(THREAD, check));
    } else {
      placeholder = placeholders()->get_entry(p_index, p_hash, name, loader_data);
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
        // If it completes we will use the resulting InstanceKlass
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


Klass* SystemDictionary::resolve_instance_class_or_null(Symbol* name,
                                                        Handle class_loader,
                                                        Handle protection_domain,
                                                        TRAPS) {
  assert(name != NULL && !FieldType::is_array(name) &&
         !FieldType::is_obj(name), "invalid class name");

  Ticks class_load_start_time = Ticks::now();

  // Fix for 4474172; see evaluation for more details
  class_loader = Handle(THREAD, java_lang_ClassLoader::non_reflection_class_loader(class_loader()));
  ClassLoaderData *loader_data = register_loader(class_loader, CHECK_NULL);

  // Do lookup to see if class already exist and the protection domain
  // has the right access
  // This call uses find which checks protection domain already matches
  // All subsequent calls use find_class, and set has_loaded_class so that
  // before we return a result we call out to java to check for valid protection domain
  // to allow returning the Klass* and add it to the pd_set if it is valid
  unsigned int d_hash = dictionary()->compute_hash(name, loader_data);
  int d_index = dictionary()->hash_to_index(d_hash);
  Klass* probe = dictionary()->find(d_index, d_hash, name, loader_data,
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

  unsigned int p_hash = placeholders()->compute_hash(name, loader_data);
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
  Symbol* superclassname = NULL;

  {
    MutexLocker mu(SystemDictionary_lock, THREAD);
    Klass* check = find_class(d_index, d_hash, name, loader_data);
    if (check != NULL) {
      // Klass is already loaded, so just return it
      class_has_been_loaded = true;
      k = instanceKlassHandle(THREAD, check);
    } else {
      placeholder = placeholders()->get_entry(p_index, p_hash, name, loader_data);
      if (placeholder && placeholder->super_load_in_progress()) {
         super_load_in_progress = true;
         if (placeholder->havesupername() == true) {
           superclassname = placeholder->supername();
           havesupername = true;
         }
      }
    }
  }

  // If the class is in the placeholder table, class loading is in progress
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

  bool throw_circularity_error = false;
  if (!class_has_been_loaded) {
    bool load_instance_added = false;

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

    {
      MutexLocker mu(SystemDictionary_lock, THREAD);
      if (class_loader.is_null() || !is_parallelCapable(class_loader)) {
        PlaceholderEntry* oldprobe = placeholders()->get_entry(p_index, p_hash, name, loader_data);
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
              Klass* check = find_class(d_index, d_hash, name, loader_data);
              if (check != NULL) {
                // Klass is already loaded, so just return it
                k = instanceKlassHandle(THREAD, check);
                class_has_been_loaded = true;
              }
              // check if other thread failed to load and cleaned up
              oldprobe = placeholders()->get_entry(p_index, p_hash, name, loader_data);
            }
          }
        }
      }
      // All cases: add LOAD_INSTANCE holding SystemDictionary_lock
      // case 3: UnsyncloadClass || case 5: parallelCapable: allow competing threads to try
      // LOAD_INSTANCE in parallel

      if (!throw_circularity_error && !class_has_been_loaded) {
        PlaceholderEntry* newprobe = placeholders()->find_and_add(p_index, p_hash, name, loader_data, PlaceholderTable::LOAD_INSTANCE, NULL, THREAD);
        load_instance_added = true;
        // For class loaders that do not acquire the classloader object lock,
        // if they did not catch another thread holding LOAD_INSTANCE,
        // need a check analogous to the acquire ObjectLocker/find_class
        // i.e. now that we hold the LOAD_INSTANCE token on loading this class/CL
        // one final check if the load has already completed
        // class loaders holding the ObjectLock shouldn't find the class here
        Klass* check = find_class(d_index, d_hash, name, loader_data);
        if (check != NULL) {
        // Klass is already loaded, so return it after checking/adding protection domain
          k = instanceKlassHandle(THREAD, check);
          class_has_been_loaded = true;
        }
      }
    }

    // must throw error outside of owning lock
    if (throw_circularity_error) {
      assert(!HAS_PENDING_EXCEPTION && load_instance_added == false,"circularity error cleanup");
      ResourceMark rm(THREAD);
      THROW_MSG_NULL(vmSymbols::java_lang_ClassCircularityError(), name->as_C_string());
    }

    if (!class_has_been_loaded) {

      // Do actual loading
      k = load_instance_class(name, class_loader, THREAD);

      // For UnsyncloadClass only
      // If they got a linkageError, check if a parallel class load succeeded.
      // If it did, then for bytecode resolution the specification requires
      // that we return the same result we did for the other thread, i.e. the
      // successfully loaded InstanceKlass
      // Should not get here for classloaders that support parallelism
      // with the new cleaner mechanism, even with AllowParallelDefineClass
      // Bootstrap goes through here to allow for an extra guarantee check
      if (UnsyncloadClass || (class_loader.is_null())) {
        if (k.is_null() && HAS_PENDING_EXCEPTION
          && PENDING_EXCEPTION->is_a(SystemDictionary::LinkageError_klass())) {
          MutexLocker mu(SystemDictionary_lock, THREAD);
          Klass* check = find_class(d_index, d_hash, name, loader_data);
          if (check != NULL) {
            // Klass is already loaded, so just use it
            k = instanceKlassHandle(THREAD, check);
            CLEAR_PENDING_EXCEPTION;
            guarantee((!class_loader.is_null()), "dup definition for bootstrap loader?");
          }
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
    } // load_instance_class loop

    if (load_instance_added == true) {
      // clean up placeholder entries for LOAD_INSTANCE success or error
      // This brackets the SystemDictionary updates for both defining
      // and initiating loaders
      MutexLocker mu(SystemDictionary_lock, THREAD);
      placeholders()->find_and_remove(p_index, p_hash, name, loader_data, PlaceholderTable::LOAD_INSTANCE, THREAD);
      SystemDictionary_lock->notify_all();
    }
  }

  if (HAS_PENDING_EXCEPTION || k.is_null()) {
    return NULL;
  }

  post_class_load_event(class_load_start_time, k, class_loader);

#ifdef ASSERT
  {
    ClassLoaderData* loader_data = k->class_loader_data();
    MutexLocker mu(SystemDictionary_lock, THREAD);
    Klass* kk = find_class(name, loader_data);
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
    // Dictionary::do_unloading() asserts that classes in SD are only
    // unloaded at a safepoint. Anonymous classes are not in SD.
    No_Safepoint_Verifier nosafepoint;
    if (dictionary()->is_valid_protection_domain(d_index, d_hash, name,
                                                 loader_data,
                                                 protection_domain)) {
      return k();
    }
  }

  // Verify protection domain. If it fails an exception is thrown
  validate_protection_domain(k, class_loader, protection_domain, CHECK_NULL);

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

Klass* SystemDictionary::find(Symbol* class_name,
                              Handle class_loader,
                              Handle protection_domain,
                              TRAPS) {

  // The result of this call should be consistent with the result
  // of the call to resolve_instance_class_or_null().
  // See evaluation 6790209 and 4474172 for more details.
  class_loader = Handle(THREAD, java_lang_ClassLoader::non_reflection_class_loader(class_loader()));
  ClassLoaderData* loader_data = ClassLoaderData::class_loader_data_or_null(class_loader());

  if (loader_data == NULL) {
    // If the ClassLoaderData has not been setup,
    // then the class loader has no entries in the dictionary.
    return NULL;
  }

  unsigned int d_hash = dictionary()->compute_hash(class_name, loader_data);
  int d_index = dictionary()->hash_to_index(d_hash);

  {
    // Note that we have an entry, and entries can be deleted only during GC,
    // so we cannot allow GC to occur while we're holding this entry.
    // We're using a No_Safepoint_Verifier to catch any place where we
    // might potentially do a GC at all.
    // Dictionary::do_unloading() asserts that classes in SD are only
    // unloaded at a safepoint. Anonymous classes are not in SD.
    No_Safepoint_Verifier nosafepoint;
    return dictionary()->find(d_index, d_hash, class_name, loader_data,
                              protection_domain, THREAD);
  }
}


// Look for a loaded instance or array klass by name.  Do not do any loading.
// return NULL in case of error.
Klass* SystemDictionary::find_instance_or_array_klass(Symbol* class_name,
                                                      Handle class_loader,
                                                      Handle protection_domain,
                                                      TRAPS) {
  Klass* k = NULL;
  assert(class_name != NULL, "class name must be non NULL");

  if (FieldType::is_array(class_name)) {
    // The name refers to an array.  Parse the name.
    // dimension and object_key in FieldArrayInfo are assigned as a
    // side-effect of this call
    FieldArrayInfo fd;
    BasicType t = FieldType::get_array_info(class_name, fd, CHECK_(NULL));
    if (t != T_OBJECT) {
      k = Universe::typeArrayKlassObj(t);
    } else {
      k = SystemDictionary::find(fd.object_key(), class_loader, protection_domain, THREAD);
    }
    if (k != NULL) {
      k = k->array_klass_or_null(fd.dimension());
    }
  } else {
    k = find(class_name, class_loader, protection_domain, THREAD);
  }
  return k;
}

// Note: this method is much like resolve_from_stream, but
// updates no supplemental data structures.
// TODO consolidate the two methods with a helper routine?
Klass* SystemDictionary::parse_stream(Symbol* class_name,
                                      Handle class_loader,
                                      Handle protection_domain,
                                      ClassFileStream* st,
                                      KlassHandle host_klass,
                                      GrowableArray<Handle>* cp_patches,
                                      TRAPS) {
  TempNewSymbol parsed_name = NULL;

  Ticks class_load_start_time = Ticks::now();

  ClassLoaderData* loader_data;
  if (host_klass.not_null()) {
    // Create a new CLD for anonymous class, that uses the same class loader
    // as the host_klass
    guarantee(host_klass->class_loader() == class_loader(), "should be the same");
    guarantee(!DumpSharedSpaces, "must not create anonymous classes when dumping");
    loader_data = ClassLoaderData::anonymous_class_loader_data(class_loader(), CHECK_NULL);
    loader_data->record_dependency(host_klass(), CHECK_NULL);
  } else {
    loader_data = ClassLoaderData::class_loader_data(class_loader());
  }

  // Parse the stream. Note that we do this even though this klass might
  // already be present in the SystemDictionary, otherwise we would not
  // throw potential ClassFormatErrors.
  //
  // Note: "name" is updated.

  instanceKlassHandle k = ClassFileParser(st).parseClassFile(class_name,
                                                             loader_data,
                                                             protection_domain,
                                                             host_klass,
                                                             cp_patches,
                                                             parsed_name,
                                                             true,
                                                             THREAD);


  if (host_klass.not_null() && k.not_null()) {
    // If it's anonymous, initialize it now, since nobody else will.

    {
      MutexLocker mu_r(Compile_lock, THREAD);

      // Add to class hierarchy, initialize vtables, and do possible
      // deoptimizations.
      add_to_hierarchy(k, CHECK_NULL); // No exception, but can block

      // But, do not add to system dictionary.

      // compiled code dependencies need to be validated anyway
      notice_modification();
    }

    // Rewrite and patch constant pool here.
    k->link_class(CHECK_NULL);
    if (cp_patches != NULL) {
      k->constants()->patch_resolved_references(cp_patches);
    }
    k->eager_initialize(CHECK_NULL);

    // notify jvmti
    if (JvmtiExport::should_post_class_load()) {
        assert(THREAD->is_Java_thread(), "thread->is_Java_thread()");
        JvmtiExport::post_class_load((JavaThread *) THREAD, k());
    }

    post_class_load_event(class_load_start_time, k, class_loader);
  }
  assert(host_klass.not_null() || cp_patches == NULL,
         "cp_patches only found with host_klass");

  return k();
}

// Add a klass to the system from a stream (called by jni_DefineClass and
// JVM_DefineClass).
// Note: class_name can be NULL. In that case we do not know the name of
// the class until we have parsed the stream.

Klass* SystemDictionary::resolve_from_stream(Symbol* class_name,
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

  ClassLoaderData* loader_data = register_loader(class_loader, CHECK_NULL);

  // Make sure we are synchronized on the class loader before we proceed
  Handle lockObject = compute_loader_lock_object(class_loader, THREAD);
  check_loader_lock_contention(lockObject, THREAD);
  ObjectLocker ol(lockObject, THREAD, DoObjectLock);

  TempNewSymbol parsed_name = NULL;

  // Parse the stream. Note that we do this even though this klass might
  // already be present in the SystemDictionary, otherwise we would not
  // throw potential ClassFormatErrors.
  //
  // Note: "name" is updated.

  instanceKlassHandle k = ClassFileParser(st).parseClassFile(class_name,
                                                             loader_data,
                                                             protection_domain,
                                                             parsed_name,
                                                             verify,
                                                             THREAD);

  const char* pkg = "java/";
  if (!HAS_PENDING_EXCEPTION &&
      !class_loader.is_null() &&
      parsed_name != NULL &&
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
    assert(parsed_name != NULL, "Sanity");
    assert(class_name == NULL || class_name == parsed_name, "name mismatch");
    // Verification prevents us from creating names with dots in them, this
    // asserts that that's the case.
    assert(is_internal_format(parsed_name),
           "external class name format used internally");

    // Add class just loaded
    // If a class loader supports parallel classloading handle parallel define requests
    // find_or_define_instance_class may return a different InstanceKlass
    if (is_parallelCapable(class_loader)) {
      k = find_or_define_instance_class(class_name, class_loader, k, THREAD);
    } else {
      define_instance_class(k, THREAD);
    }
  }

  // Make sure we have an entry in the SystemDictionary on success
  debug_only( {
    if (!HAS_PENDING_EXCEPTION) {
      assert(parsed_name != NULL, "parsed_name is still null?");
      Symbol*  h_name    = k->name();
      ClassLoaderData *defining_loader_data = k->class_loader_data();

      MutexLocker mu(SystemDictionary_lock, THREAD);

      Klass* check = find_class(parsed_name, loader_data);
      assert(check == k(), "should be present in the dictionary");

      Klass* check2 = find_class(h_name, defining_loader_data);
      assert(check == check2, "name inconsistancy in SystemDictionary");
    }
  } );

  return k();
}

#if INCLUDE_CDS
void SystemDictionary::set_shared_dictionary(HashtableBucket<mtClass>* t, int length,
                                             int number_of_entries) {
  assert(length == _nof_buckets * sizeof(HashtableBucket<mtClass>),
         "bad shared dictionary size.");
  _shared_dictionary = new Dictionary(_nof_buckets, t, number_of_entries);
}


// If there is a shared dictionary, then find the entry for the
// given shared system class, if any.

Klass* SystemDictionary::find_shared_class(Symbol* class_name) {
  if (shared_dictionary() != NULL) {
    unsigned int d_hash = shared_dictionary()->compute_hash(class_name, NULL);
    int d_index = shared_dictionary()->hash_to_index(d_hash);

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
                 Symbol* class_name, Handle class_loader, TRAPS) {
  instanceKlassHandle ik (THREAD, find_shared_class(class_name));
  // Make sure we only return the boot class for the NULL classloader.
  if (ik.not_null() &&
      SharedClassUtil::is_shared_boot_class(ik()) && class_loader.is_null()) {
    Handle protection_domain;
    return load_shared_class(ik, class_loader, protection_domain, THREAD);
  }
  return instanceKlassHandle();
}

instanceKlassHandle SystemDictionary::load_shared_class(instanceKlassHandle ik,
                                                        Handle class_loader,
                                                        Handle protection_domain, TRAPS) {
  if (ik.not_null()) {
    instanceKlassHandle nh = instanceKlassHandle(); // null Handle
    Symbol* class_name = ik->name();

    // Found the class, now load the superclass and interfaces.  If they
    // are shared, add them to the main system dictionary and reset
    // their hierarchy references (supers, subs, and interfaces).

    if (ik->super() != NULL) {
      Symbol*  cn = ik->super()->name();
      resolve_super_or_fail(class_name, cn,
                            class_loader, protection_domain, true, CHECK_(nh));
    }

    Array<Klass*>* interfaces = ik->local_interfaces();
    int num_interfaces = interfaces->length();
    for (int index = 0; index < num_interfaces; index++) {
      Klass* k = interfaces->at(index);

      // Note: can not use InstanceKlass::cast here because
      // interfaces' InstanceKlass's C++ vtbls haven't been
      // reinitialized yet (they will be once the interface classes
      // are loaded)
      Symbol*  name  = k->name();
      resolve_super_or_fail(class_name, name, class_loader, protection_domain, false, CHECK_(nh));
    }

    // Adjust methods to recover missing data.  They need addresses for
    // interpreter entry points and their default native method address
    // must be reset.

    // Updating methods must be done under a lock so multiple
    // threads don't update these in parallel
    //
    // Shared classes are all currently loaded by either the bootstrap or
    // internal parallel class loaders, so this will never cause a deadlock
    // on a custom class loader lock.

    ClassLoaderData* loader_data = ClassLoaderData::class_loader_data(class_loader());
    {
      Handle lockObject = compute_loader_lock_object(class_loader, THREAD);
      check_loader_lock_contention(lockObject, THREAD);
      ObjectLocker ol(lockObject, THREAD, true);
      ik->restore_unshareable_info(loader_data, protection_domain, CHECK_(nh));
    }

    if (TraceClassLoading) {
      ResourceMark rm;
      tty->print("[Loaded %s", ik->external_name());
      tty->print(" from shared objects file");
      if (class_loader.not_null()) {
        tty->print(" by %s", loader_data->loader_name());
      }
      tty->print_cr("]");
    }

    if (DumpLoadedClassList != NULL && classlist_file->is_open()) {
      // Only dump the classes that can be stored into CDS archive
      if (SystemDictionaryShared::is_sharing_possible(loader_data)) {
        ResourceMark rm(THREAD);
        classlist_file->print_cr("%s", ik->name()->as_C_string());
        classlist_file->flush();
      }
    }

    // notify a class loaded from shared object
    ClassLoadingService::notify_class_loaded(InstanceKlass::cast(ik()),
                                             true /* shared class */);
  }
  return ik;
}
#endif // INCLUDE_CDS

instanceKlassHandle SystemDictionary::load_instance_class(Symbol* class_name, Handle class_loader, TRAPS) {
  instanceKlassHandle nh = instanceKlassHandle(); // null Handle
  if (class_loader.is_null()) {

    // Search the shared system dictionary for classes preloaded into the
    // shared spaces.
    instanceKlassHandle k;
    {
#if INCLUDE_CDS
      PerfTraceTime vmtimer(ClassLoader::perf_shared_classload_time());
      k = load_shared_class(class_name, class_loader, THREAD);
#endif
    }

    if (k.is_null()) {
      // Use VM class loader
      PerfTraceTime vmtimer(ClassLoader::perf_sys_classload_time());
      k = ClassLoader::load_classfile(class_name, CHECK_(nh));
    }

    // find_or_define_instance_class may return a different InstanceKlass
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

    KlassHandle spec_klass (THREAD, SystemDictionary::ClassLoader_klass());

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
                              vmSymbols::loadClassInternal_name(),
                              vmSymbols::string_class_signature(),
                              string,
                              CHECK_(nh));
    } else {
      JavaCalls::call_virtual(&result,
                              class_loader,
                              spec_klass,
                              vmSymbols::loadClass_name(),
                              vmSymbols::string_class_signature(),
                              string,
                              CHECK_(nh));
    }

    assert(result.get_type() == T_OBJECT, "just checking");
    oop obj = (oop) result.get_jobject();

    // Primitive classes return null since forName() can not be
    // used to obtain any of the Class objects representing primitives or void
    if ((obj != NULL) && !(java_lang_Class::is_primitive(obj))) {
      instanceKlassHandle k =
                instanceKlassHandle(THREAD, java_lang_Class::as_Klass(obj));
      // For user defined Java class loaders, check that the name returned is
      // the same as that requested.  This check is done for the bootstrap
      // loader when parsing the class file.
      if (class_name == k->name()) {
        return k;
      }
    }
    // Class is not found or has the wrong name, return NULL
    return nh;
  }
}

void SystemDictionary::define_instance_class(instanceKlassHandle k, TRAPS) {

  ClassLoaderData* loader_data = k->class_loader_data();
  Handle class_loader_h(THREAD, loader_data->class_loader());

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
  Symbol*  name_h = k->name();
  unsigned int d_hash = dictionary()->compute_hash(name_h, loader_data);
  int d_index = dictionary()->hash_to_index(d_hash);
  check_constraints(d_index, d_hash, k, class_loader_h, true, CHECK);

  // Register class just loaded with class loader (placed in Vector)
  // Note we do this before updating the dictionary, as this can
  // fail with an OutOfMemoryError (if it does, we will *not* put this
  // class in the dictionary and will not update the class hierarchy).
  // JVMTI FollowReferences needs to find the classes this way.
  if (k->class_loader() != NULL) {
    methodHandle m(THREAD, Universe::loader_addClass_method());
    JavaValue result(T_VOID);
    JavaCallArguments args(class_loader_h);
    args.push_oop(Handle(THREAD, k->java_mirror()));
    JavaCalls::call(&result, m, &args, CHECK);
  }

  // Add the new class. We need recompile lock during update of CHA.
  {
    unsigned int p_hash = placeholders()->compute_hash(name_h, loader_data);
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
// All parallel class loaders, including bootstrap classloader
// lock a placeholder entry for this class/class_loader pair
// to allow parallel defines of different classes for this class loader
// With AllowParallelDefine flag==true, in case they do not synchronize around
// FindLoadedClass/DefineClass, calls, we check for parallel
// loading for them, wait if a defineClass is in progress
// and return the initial requestor's results
// This flag does not apply to the bootstrap classloader.
// With AllowParallelDefine flag==false, call through to define_instance_class
// which will throw LinkageError: duplicate class definition.
// False is the requested default.
// For better performance, the class loaders should synchronize
// findClass(), i.e. FindLoadedClass/DefineClassIfAbsent or they
// potentially waste time reading and parsing the bytestream.
// Note: VM callers should ensure consistency of k/class_name,class_loader
instanceKlassHandle SystemDictionary::find_or_define_instance_class(Symbol* class_name, Handle class_loader, instanceKlassHandle k, TRAPS) {

  instanceKlassHandle nh = instanceKlassHandle(); // null Handle
  Symbol*  name_h = k->name(); // passed in class_name may be null
  ClassLoaderData* loader_data = class_loader_data(class_loader);

  unsigned int d_hash = dictionary()->compute_hash(name_h, loader_data);
  int d_index = dictionary()->hash_to_index(d_hash);

// Hold SD lock around find_class and placeholder creation for DEFINE_CLASS
  unsigned int p_hash = placeholders()->compute_hash(name_h, loader_data);
  int p_index = placeholders()->hash_to_index(p_hash);
  PlaceholderEntry* probe;

  {
    MutexLocker mu(SystemDictionary_lock, THREAD);
    // First check if class already defined
    if (UnsyncloadClass || (is_parallelDefine(class_loader))) {
      Klass* check = find_class(d_index, d_hash, name_h, loader_data);
      if (check != NULL) {
        return(instanceKlassHandle(THREAD, check));
      }
    }

    // Acquire define token for this class/classloader
    probe = placeholders()->find_and_add(p_index, p_hash, name_h, loader_data, PlaceholderTable::DEFINE_CLASS, NULL, THREAD);
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
    if ((UnsyncloadClass || is_parallelDefine(class_loader)) && (probe->instance_klass() != NULL)) {
        placeholders()->find_and_remove(p_index, p_hash, name_h, loader_data, PlaceholderTable::DEFINE_CLASS, THREAD);
        SystemDictionary_lock->notify_all();
#ifdef ASSERT
        Klass* check = find_class(d_index, d_hash, name_h, loader_data);
        assert(check != NULL, "definer missed recording success");
#endif
        return(instanceKlassHandle(THREAD, probe->instance_klass()));
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
    PlaceholderEntry* probe = placeholders()->get_entry(p_index, p_hash, name_h, loader_data);
    assert(probe != NULL, "DEFINE_CLASS placeholder lost?");
    if (probe != NULL) {
      if (HAS_PENDING_EXCEPTION) {
        linkage_exception = Handle(THREAD,PENDING_EXCEPTION);
        CLEAR_PENDING_EXCEPTION;
      } else {
        probe->set_instance_klass(k());
      }
      probe->set_definer(NULL);
      placeholders()->find_and_remove(p_index, p_hash, name_h, loader_data, PlaceholderTable::DEFINE_CLASS, THREAD);
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

Klass* SystemDictionary::find_class(int index, unsigned int hash,
                                      Symbol* class_name,
                                      ClassLoaderData* loader_data) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert (index == dictionary()->index_for(class_name, loader_data),
          "incorrect index?");

  Klass* k = dictionary()->find_class(index, hash, class_name, loader_data);
  return k;
}


// Basic find on classes in the midst of being loaded
Symbol* SystemDictionary::find_placeholder(Symbol* class_name,
                                           ClassLoaderData* loader_data) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  unsigned int p_hash = placeholders()->compute_hash(class_name, loader_data);
  int p_index = placeholders()->hash_to_index(p_hash);
  return placeholders()->find_entry(p_index, p_hash, class_name, loader_data);
}


// Used for assertions and verification only
Klass* SystemDictionary::find_class(Symbol* class_name, ClassLoaderData* loader_data) {
  #ifndef ASSERT
  guarantee(VerifyBeforeGC      ||
            VerifyDuringGC      ||
            VerifyBeforeExit    ||
            VerifyDuringStartup ||
            VerifyAfterGC, "too expensive");
  #endif
  assert_locked_or_safepoint(SystemDictionary_lock);

  // First look in the loaded class array
  unsigned int d_hash = dictionary()->compute_hash(class_name, loader_data);
  int d_index = dictionary()->hash_to_index(d_hash);
  return find_class(d_index, d_hash, class_name, loader_data);
}


// Get the next class in the diictionary.
Klass* SystemDictionary::try_get_next_class() {
  return dictionary()->try_get_next_class();
}


// ----------------------------------------------------------------------------
// Update hierachy. This is done before the new klass has been added to the SystemDictionary. The Recompile_lock
// is held, to ensure that the compiler is not using the class hierachy, and that deoptimization will kick in
// before a new class is used.

void SystemDictionary::add_to_hierarchy(instanceKlassHandle k, TRAPS) {
  assert(k.not_null(), "just checking");
  assert_locked_or_safepoint(Compile_lock);

  // Link into hierachy. Make sure the vtables are initialized before linking into
  k->append_to_sibling_list();                    // add to superklass/sibling list
  k->process_interfaces(THREAD);                  // handle all "implements" declarations
  k->set_init_state(InstanceKlass::loaded);
  // Now flush all code that depended on old class hierarchy.
  // Note: must be done *after* linking k into the hierarchy (was bug 12/9/97)
  // Also, first reinitialize vtable because it may have gotten out of synch
  // while the new class wasn't connected to the class hierarchy.
  CodeCache::flush_dependents_on(k);
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
  roots_oops_do(blk, NULL);
}

void SystemDictionary::always_strong_classes_do(KlassClosure* closure) {
  // Follow all system classes and temporary placeholders in dictionary
  dictionary()->always_strong_classes_do(closure);

  // Placeholders. These represent classes we're actively loading.
  placeholders()->classes_do(closure);
}

// Calculate a "good" systemdictionary size based
// on predicted or current loaded classes count
int SystemDictionary::calculate_systemdictionary_size(int classcount) {
  int newsize = _old_default_sdsize;
  if ((classcount > 0)  && !DumpSharedSpaces) {
    int desiredsize = classcount/_average_depth_goal;
    for (newsize = _primelist[_sdgeneration]; _sdgeneration < _prime_array_size -1;
         newsize = _primelist[++_sdgeneration]) {
      if (desiredsize <=  newsize) {
        break;
      }
    }
  }
  return newsize;
}

#ifdef ASSERT
class VerifySDReachableAndLiveClosure : public OopClosure {
private:
  BoolObjectClosure* _is_alive;

  template <class T> void do_oop_work(T* p) {
    oop obj = oopDesc::load_decode_heap_oop(p);
    guarantee(_is_alive->do_object_b(obj), "Oop in system dictionary must be live");
  }

public:
  VerifySDReachableAndLiveClosure(BoolObjectClosure* is_alive) : OopClosure(), _is_alive(is_alive) { }

  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
};
#endif

// Assumes classes in the SystemDictionary are only unloaded at a safepoint
// Note: anonymous classes are not in the SD.
bool SystemDictionary::do_unloading(BoolObjectClosure* is_alive,
                                    bool clean_previous_versions) {
  // First, mark for unload all ClassLoaderData referencing a dead class loader.
  bool unloading_occurred = ClassLoaderDataGraph::do_unloading(is_alive,
                                                               clean_previous_versions);
  if (unloading_occurred) {
    dictionary()->do_unloading();
    constraints()->purge_loader_constraints();
    resolution_errors()->purge_resolution_errors();
  }
  // Oops referenced by the system dictionary may get unreachable independently
  // of the class loader (eg. cached protection domain oops). So we need to
  // explicitly unlink them here instead of in Dictionary::do_unloading.
  dictionary()->unlink(is_alive);
#ifdef ASSERT
  VerifySDReachableAndLiveClosure cl(is_alive);
  dictionary()->oops_do(&cl);
#endif
  return unloading_occurred;
}

void SystemDictionary::roots_oops_do(OopClosure* strong, OopClosure* weak) {
  strong->do_oop(&_java_system_loader);
  strong->do_oop(&_system_loader_lock_obj);
  CDS_ONLY(SystemDictionaryShared::roots_oops_do(strong);)

  // Adjust dictionary
  dictionary()->roots_oops_do(strong, weak);

  // Visit extra methods
  invoke_method_table()->oops_do(strong);
}

void SystemDictionary::oops_do(OopClosure* f) {
  f->do_oop(&_java_system_loader);
  f->do_oop(&_system_loader_lock_obj);
  CDS_ONLY(SystemDictionaryShared::oops_do(f);)

  // Adjust dictionary
  dictionary()->oops_do(f);

  // Visit extra methods
  invoke_method_table()->oops_do(f);
}

// Extended Class redefinition support.
// If one of these classes is replaced, we need to replace it in these places.
// KlassClosure::do_klass should take the address of a class but we can
// change that later.
void SystemDictionary::preloaded_classes_do(KlassClosure* f) {
  for (int k = (int)FIRST_WKID; k < (int)WKID_LIMIT; k++) {
    f->do_klass(_well_known_klasses[k]);
  }

  {
    for (int i = 0; i < T_VOID+1; i++) {
      if (_box_klasses[i] != NULL) {
        assert(i >= T_BOOLEAN, "checking");
        f->do_klass(_box_klasses[i]);
      }
    }
  }

  FilteredFieldsMap::classes_do(f);
}

void SystemDictionary::lazily_loaded_classes_do(KlassClosure* f) {
  f->do_klass(_abstract_ownable_synchronizer_klass);
}

// Just the classes from defining class loaders
// Don't iterate over placeholders
void SystemDictionary::classes_do(void f(Klass*)) {
  dictionary()->classes_do(f);
}

// Added for initialize_itable_for_klass
//   Just the classes from defining class loaders
// Don't iterate over placeholders
void SystemDictionary::classes_do(void f(Klass*, TRAPS), TRAPS) {
  dictionary()->classes_do(f, CHECK);
}

//   All classes, and their class loaders
// Don't iterate over placeholders
void SystemDictionary::classes_do(void f(Klass*, ClassLoaderData*)) {
  dictionary()->classes_do(f);
}

void SystemDictionary::placeholders_do(void f(Symbol*)) {
  placeholders()->entries_do(f);
}

void SystemDictionary::methods_do(void f(Method*)) {
  dictionary()->methods_do(f);
  invoke_method_table()->methods_do(f);
}

void SystemDictionary::remove_classes_in_error_state() {
  dictionary()->remove_classes_in_error_state();
}

// ----------------------------------------------------------------------------
// Lazily load klasses

void SystemDictionary::load_abstract_ownable_synchronizer_klass(TRAPS) {
  // if multiple threads calling this function, only one thread will load
  // the class.  The other threads will find the loaded version once the
  // class is loaded.
  Klass* aos = _abstract_ownable_synchronizer_klass;
  if (aos == NULL) {
    Klass* k = resolve_or_fail(vmSymbols::java_util_concurrent_locks_AbstractOwnableSynchronizer(), true, CHECK);
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
  _sdgeneration        = 0;
  _dictionary          = new Dictionary(calculate_systemdictionary_size(PredictedLoadedClassCount));
  _placeholders        = new PlaceholderTable(_nof_buckets);
  _number_of_modifications = 0;
  _loader_constraints  = new LoaderConstraintTable(_loader_constraint_size);
  _resolution_errors   = new ResolutionErrorTable(_resolution_error_size);
  _invoke_method_table = new SymbolPropertyTable(_invoke_method_size);

  // Allocate private object used as system class loader lock
  _system_loader_lock_obj = oopFactory::new_intArray(0, CHECK);
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
  Symbol* symbol = vmSymbols::symbol_at((vmSymbols::SID)sid);
  Klass**    klassp = &_well_known_klasses[id];
  bool must_load = (init_opt < SystemDictionary::Opt);
  if ((*klassp) == NULL) {
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
  }

  // move the starting value forward to the limit:
  start_id = limit_id;
}

void SystemDictionary::initialize_preloaded_classes(TRAPS) {
  assert(WK_KLASS(Object_klass) == NULL, "preloaded classes should only be initialized once");
  // Preload commonly used klasses
  WKID scan = FIRST_WKID;
  // first do Object, then String, Class
  if (UseSharedSpaces) {
    initialize_wk_klasses_through(WK_KLASS_ENUM_NAME(Object_klass), scan, CHECK);
    // Initialize the constant pool for the Object_class
    InstanceKlass* ik = InstanceKlass::cast(Object_klass());
    ik->constants()->restore_unshareable_info(CHECK);
    initialize_wk_klasses_through(WK_KLASS_ENUM_NAME(Class_klass), scan, CHECK);
  } else {
    initialize_wk_klasses_through(WK_KLASS_ENUM_NAME(Class_klass), scan, CHECK);
  }

  // Calculate offsets for String and Class classes since they are loaded and
  // can be used after this point.
  java_lang_String::compute_offsets();
  java_lang_Class::compute_offsets();

  // Fixup mirrors for classes loaded before java.lang.Class.
  // These calls iterate over the objects currently in the perm gen
  // so calling them at this point is matters (not before when there
  // are fewer objects and not later after there are more objects
  // in the perm gen.
  Universe::initialize_basic_type_mirrors(CHECK);
  Universe::fixup_mirrors(CHECK);

  // do a bunch more:
  initialize_wk_klasses_through(WK_KLASS_ENUM_NAME(Reference_klass), scan, CHECK);

  // Preload ref klasses and set reference types
  InstanceKlass::cast(WK_KLASS(Reference_klass))->set_reference_type(REF_OTHER);
  InstanceRefKlass::update_nonstatic_oop_maps(WK_KLASS(Reference_klass));

  initialize_wk_klasses_through(WK_KLASS_ENUM_NAME(Cleaner_klass), scan, CHECK);
  InstanceKlass::cast(WK_KLASS(SoftReference_klass))->set_reference_type(REF_SOFT);
  InstanceKlass::cast(WK_KLASS(WeakReference_klass))->set_reference_type(REF_WEAK);
  InstanceKlass::cast(WK_KLASS(FinalReference_klass))->set_reference_type(REF_FINAL);
  InstanceKlass::cast(WK_KLASS(PhantomReference_klass))->set_reference_type(REF_PHANTOM);
  InstanceKlass::cast(WK_KLASS(Cleaner_klass))->set_reference_type(REF_CLEANER);

  // JSR 292 classes
  WKID jsr292_group_start = WK_KLASS_ENUM_NAME(MethodHandle_klass);
  WKID jsr292_group_end   = WK_KLASS_ENUM_NAME(VolatileCallSite_klass);
  initialize_wk_klasses_until(jsr292_group_start, scan, CHECK);
  initialize_wk_klasses_through(jsr292_group_end, scan, CHECK);
  initialize_wk_klasses_until(NOT_JVMCI(WKID_LIMIT) JVMCI_ONLY(FIRST_JVMCI_WKID), scan, CHECK);

  _box_klasses[T_BOOLEAN] = WK_KLASS(Boolean_klass);
  _box_klasses[T_CHAR]    = WK_KLASS(Character_klass);
  _box_klasses[T_FLOAT]   = WK_KLASS(Float_klass);
  _box_klasses[T_DOUBLE]  = WK_KLASS(Double_klass);
  _box_klasses[T_BYTE]    = WK_KLASS(Byte_klass);
  _box_klasses[T_SHORT]   = WK_KLASS(Short_klass);
  _box_klasses[T_INT]     = WK_KLASS(Integer_klass);
  _box_klasses[T_LONG]    = WK_KLASS(Long_klass);
  //_box_klasses[T_OBJECT]  = WK_KLASS(object_klass);
  //_box_klasses[T_ARRAY]   = WK_KLASS(object_klass);

  { // Compute whether we should use loadClass or loadClassInternal when loading classes.
    Method* method = InstanceKlass::cast(ClassLoader_klass())->find_method(vmSymbols::loadClassInternal_name(), vmSymbols::string_class_signature());
    _has_loadClassInternal = (method != NULL);
  }
  { // Compute whether we should use checkPackageAccess or NOT
    Method* method = InstanceKlass::cast(ClassLoader_klass())->find_method(vmSymbols::checkPackageAccess_name(), vmSymbols::class_protectiondomain_signature());
    _has_checkPackageAccess = (method != NULL);
  }
}

// Tells if a given klass is a box (wrapper class, such as java.lang.Integer).
// If so, returns the basic type it holds.  If not, returns T_OBJECT.
BasicType SystemDictionary::box_klass_type(Klass* k) {
  assert(k != NULL, "");
  for (int i = T_BOOLEAN; i < T_VOID+1; i++) {
    if (_box_klasses[i] == k)
      return (BasicType)i;
  }
  return T_OBJECT;
}

// Constraints on class loaders. The details of the algorithm can be
// found in the OOPSLA'98 paper "Dynamic Class Loading in the Java
// Virtual Machine" by Sheng Liang and Gilad Bracha.  The basic idea is
// that the system dictionary needs to maintain a set of contraints that
// must be satisfied by all classes in the dictionary.
// if defining is true, then LinkageError if already in systemDictionary
// if initiating loader, then ok if InstanceKlass matches existing entry

void SystemDictionary::check_constraints(int d_index, unsigned int d_hash,
                                         instanceKlassHandle k,
                                         Handle class_loader, bool defining,
                                         TRAPS) {
  const char *linkage_error = NULL;
  {
    Symbol*  name  = k->name();
    ClassLoaderData *loader_data = class_loader_data(class_loader);

    MutexLocker mu(SystemDictionary_lock, THREAD);

    Klass* check = find_class(d_index, d_hash, name, loader_data);
    if (check != (Klass*)NULL) {
      // if different InstanceKlass - duplicate class definition,
      // else - ok, class loaded by a different thread in parallel,
      // we should only have found it if it was done loading and ok to use
      // system dictionary only holds instance classes, placeholders
      // also holds array classes

      assert(check->oop_is_instance(), "noninstance in systemdictionary");
      if ((defining == true) || (k() != check)) {
        linkage_error = "loader (instance of  %s): attempted  duplicate class "
          "definition for name: \"%s\"";
      } else {
        return;
      }
    }

#ifdef ASSERT
    Symbol* ph_check = find_placeholder(name, loader_data);
    assert(ph_check == NULL || ph_check == name, "invalid symbol");
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
  Symbol*  name  = k->name();
  ClassLoaderData *loader_data = class_loader_data(class_loader);

  {
  MutexLocker mu1(SystemDictionary_lock, THREAD);

  // See whether biased locking is enabled and if so set it for this
  // klass.
  // Note that this must be done past the last potential blocking
  // point / safepoint. We enable biased locking lazily using a
  // VM_Operation to iterate the SystemDictionary and installing the
  // biasable mark word into each InstanceKlass's prototype header.
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

  // Make a new system dictionary entry.
  Klass* sd_check = find_class(d_index, d_hash, name, loader_data);
  if (sd_check == NULL) {
    dictionary()->add_klass(name, loader_data, k);
    notice_modification();
  }
#ifdef ASSERT
  sd_check = find_class(d_index, d_hash, name, loader_data);
  assert (sd_check != NULL, "should have entry in system dictionary");
  // Note: there may be a placeholder entry: for circularity testing
  // or for parallel defines
#endif
    SystemDictionary_lock->notify_all();
  }
}


// Try to find a class name using the loader constraints.  The
// loader constraints might know about a class that isn't fully loaded
// yet and these will be ignored.
Klass* SystemDictionary::find_constrained_instance_or_array_klass(
                    Symbol* class_name, Handle class_loader, TRAPS) {

  // First see if it has been loaded directly.
  // Force the protection domain to be null.  (This removes protection checks.)
  Handle no_protection_domain;
  Klass* klass = find_instance_or_array_klass(class_name, class_loader,
                                              no_protection_domain, CHECK_NULL);
  if (klass != NULL)
    return klass;

  // Now look to see if it has been loaded elsewhere, and is subject to
  // a loader constraint that would require this loader to return the
  // klass that is already loaded.
  if (FieldType::is_array(class_name)) {
    // For array classes, their Klass*s are not kept in the
    // constraint table. The element Klass*s are.
    FieldArrayInfo fd;
    BasicType t = FieldType::get_array_info(class_name, fd, CHECK_(NULL));
    if (t != T_OBJECT) {
      klass = Universe::typeArrayKlassObj(t);
    } else {
      MutexLocker mu(SystemDictionary_lock, THREAD);
      klass = constraints()->find_constrained_klass(fd.object_key(), class_loader);
    }
    // If element class already loaded, allocate array klass
    if (klass != NULL) {
      klass = klass->array_klass_or_null(fd.dimension());
    }
  } else {
    MutexLocker mu(SystemDictionary_lock, THREAD);
    // Non-array classes are easy: simply check the constraint table.
    klass = constraints()->find_constrained_klass(class_name, class_loader);
  }

  return klass;
}


bool SystemDictionary::add_loader_constraint(Symbol* class_name,
                                             Handle class_loader1,
                                             Handle class_loader2,
                                             Thread* THREAD) {
  ClassLoaderData* loader_data1 = class_loader_data(class_loader1);
  ClassLoaderData* loader_data2 = class_loader_data(class_loader2);

  Symbol* constraint_name = NULL;
  if (!FieldType::is_array(class_name)) {
    constraint_name = class_name;
  } else {
    // For array classes, their Klass*s are not kept in the
    // constraint table. The element classes are.
    FieldArrayInfo fd;
    BasicType t = FieldType::get_array_info(class_name, fd, CHECK_(false));
    // primitive types always pass
    if (t != T_OBJECT) {
      return true;
    } else {
      constraint_name = fd.object_key();
    }
  }
  unsigned int d_hash1 = dictionary()->compute_hash(constraint_name, loader_data1);
  int d_index1 = dictionary()->hash_to_index(d_hash1);

  unsigned int d_hash2 = dictionary()->compute_hash(constraint_name, loader_data2);
  int d_index2 = dictionary()->hash_to_index(d_hash2);
  {
  MutexLocker mu_s(SystemDictionary_lock, THREAD);

  // Better never do a GC while we're holding these oops
  No_Safepoint_Verifier nosafepoint;

  Klass* klass1 = find_class(d_index1, d_hash1, constraint_name, loader_data1);
  Klass* klass2 = find_class(d_index2, d_hash2, constraint_name, loader_data2);
  return constraints()->add_entry(constraint_name, klass1, class_loader1,
                                  klass2, class_loader2);
  }
}

// Add entry to resolution error table to record the error when the first
// attempt to resolve a reference to a class has failed.
void SystemDictionary::add_resolution_error(constantPoolHandle pool, int which,
                                            Symbol* error, Symbol* message) {
  unsigned int hash = resolution_errors()->compute_hash(pool, which);
  int index = resolution_errors()->hash_to_index(hash);
  {
    MutexLocker ml(SystemDictionary_lock, Thread::current());
    resolution_errors()->add_entry(index, hash, pool, which, error, message);
  }
}

// Delete a resolution error for RedefineClasses for a constant pool is going away
void SystemDictionary::delete_resolution_error(ConstantPool* pool) {
  resolution_errors()->delete_entry(pool);
}

// Lookup resolution error table. Returns error if found, otherwise NULL.
Symbol* SystemDictionary::find_resolution_error(constantPoolHandle pool, int which,
                                                Symbol** message) {
  unsigned int hash = resolution_errors()->compute_hash(pool, which);
  int index = resolution_errors()->hash_to_index(hash);
  {
    MutexLocker ml(SystemDictionary_lock, Thread::current());
    ResolutionErrorEntry* entry = resolution_errors()->find_entry(index, hash, pool, which);
    if (entry != NULL) {
      *message = entry->message();
      return entry->error();
    } else {
      return NULL;
    }
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
// NULL if no constraint failed.  No exception except OOME is thrown.
// Arrays are not added to the loader constraint table, their elements are.
Symbol* SystemDictionary::check_signature_loaders(Symbol* signature,
                                               Handle loader1, Handle loader2,
                                               bool is_method, TRAPS)  {
  // Nothing to do if loaders are the same.
  if (loader1() == loader2()) {
    return NULL;
  }

  SignatureStream sig_strm(signature, is_method);
  while (!sig_strm.is_done()) {
    if (sig_strm.is_object()) {
      Symbol* sig = sig_strm.as_symbol(CHECK_NULL);
      if (!add_loader_constraint(sig, loader1, loader2, THREAD)) {
        return sig;
      }
    }
    sig_strm.next();
  }
  return NULL;
}


methodHandle SystemDictionary::find_method_handle_intrinsic(vmIntrinsics::ID iid,
                                                            Symbol* signature,
                                                            TRAPS) {
  methodHandle empty;
  assert(MethodHandles::is_signature_polymorphic(iid) &&
         MethodHandles::is_signature_polymorphic_intrinsic(iid) &&
         iid != vmIntrinsics::_invokeGeneric,
         "must be a known MH intrinsic iid=%d: %s", iid, vmIntrinsics::name_at(iid));

  unsigned int hash  = invoke_method_table()->compute_hash(signature, iid);
  int          index = invoke_method_table()->hash_to_index(hash);
  SymbolPropertyEntry* spe = invoke_method_table()->find_entry(index, hash, signature, iid);
  methodHandle m;
  if (spe == NULL || spe->method() == NULL) {
    spe = NULL;
    // Must create lots of stuff here, but outside of the SystemDictionary lock.
    m = Method::make_method_handle_intrinsic(iid, signature, CHECK_(empty));
    if (!Arguments::is_interpreter_only()) {
      // Generate a compiled form of the MH intrinsic.
      AdapterHandlerLibrary::create_native_wrapper(m);
      // Check if have the compiled code.
      if (!m->has_compiled_code()) {
        THROW_MSG_(vmSymbols::java_lang_VirtualMachineError(),
                   "out of space in CodeCache for method handle intrinsic", empty);
      }
    }
    // Now grab the lock.  We might have to throw away the new method,
    // if a racing thread has managed to install one at the same time.
    {
      MutexLocker ml(SystemDictionary_lock, THREAD);
      spe = invoke_method_table()->find_entry(index, hash, signature, iid);
      if (spe == NULL)
        spe = invoke_method_table()->add_entry(index, hash, signature, iid);
      if (spe->method() == NULL)
        spe->set_method(m());
    }
  }

  assert(spe != NULL && spe->method() != NULL, "");
  assert(Arguments::is_interpreter_only() || (spe->method()->has_compiled_code() &&
         spe->method()->code()->entry_point() == spe->method()->from_compiled_entry()),
         "MH intrinsic invariant");
  return spe->method();
}

// Helper for unpacking the return value from linkMethod and linkCallSite.
static methodHandle unpack_method_and_appendix(Handle mname,
                                               KlassHandle accessing_klass,
                                               objArrayHandle appendix_box,
                                               Handle* appendix_result,
                                               TRAPS) {
  methodHandle empty;
  if (mname.not_null()) {
    Metadata* vmtarget = java_lang_invoke_MemberName::vmtarget(mname());
    if (vmtarget != NULL && vmtarget->is_method()) {
      Method* m = (Method*)vmtarget;
      oop appendix = appendix_box->obj_at(0);
      if (TraceMethodHandles) {
    #ifndef PRODUCT
        tty->print("Linked method=" INTPTR_FORMAT ": ", p2i(m));
        m->print();
        if (appendix != NULL) { tty->print("appendix = "); appendix->print(); }
        tty->cr();
    #endif //PRODUCT
      }
      (*appendix_result) = Handle(THREAD, appendix);
      // the target is stored in the cpCache and if a reference to this
      // MethodName is dropped we need a way to make sure the
      // class_loader containing this method is kept alive.
      // FIXME: the appendix might also preserve this dependency.
      ClassLoaderData* this_key = InstanceKlass::cast(accessing_klass())->class_loader_data();
      this_key->record_dependency(m->method_holder(), CHECK_NULL); // Can throw OOM
      return methodHandle(THREAD, m);
    }
  }
  THROW_MSG_(vmSymbols::java_lang_LinkageError(), "bad value from MethodHandleNatives", empty);
  return empty;
}

methodHandle SystemDictionary::find_method_handle_invoker(Symbol* name,
                                                          Symbol* signature,
                                                          KlassHandle accessing_klass,
                                                          Handle *appendix_result,
                                                          Handle *method_type_result,
                                                          TRAPS) {
  methodHandle empty;
  assert(THREAD->can_call_java() ,"");
  Handle method_type =
    SystemDictionary::find_method_handle_type(signature, accessing_klass, CHECK_(empty));

  KlassHandle  mh_klass = SystemDictionary::MethodHandle_klass();
  int ref_kind = JVM_REF_invokeVirtual;
  Handle name_str = StringTable::intern(name, CHECK_(empty));
  objArrayHandle appendix_box = oopFactory::new_objArray(SystemDictionary::Object_klass(), 1, CHECK_(empty));
  assert(appendix_box->obj_at(0) == NULL, "");

  // This should not happen.  JDK code should take care of that.
  if (accessing_klass.is_null() || method_type.is_null()) {
    THROW_MSG_(vmSymbols::java_lang_InternalError(), "bad invokehandle", empty);
  }

  // call java.lang.invoke.MethodHandleNatives::linkMethod(... String, MethodType) -> MemberName
  JavaCallArguments args;
  args.push_oop(accessing_klass()->java_mirror());
  args.push_int(ref_kind);
  args.push_oop(mh_klass()->java_mirror());
  args.push_oop(name_str());
  args.push_oop(method_type());
  args.push_oop(appendix_box());
  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result,
                         SystemDictionary::MethodHandleNatives_klass(),
                         vmSymbols::linkMethod_name(),
                         vmSymbols::linkMethod_signature(),
                         &args, CHECK_(empty));
  Handle mname(THREAD, (oop) result.get_jobject());
  (*method_type_result) = method_type;
  return unpack_method_and_appendix(mname, accessing_klass, appendix_box, appendix_result, THREAD);
}

// Decide if we can globally cache a lookup of this class, to be returned to any client that asks.
// We must ensure that all class loaders everywhere will reach this class, for any client.
// This is a safe bet for public classes in java.lang, such as Object and String.
// We also include public classes in java.lang.invoke, because they appear frequently in system-level method types.
// Out of an abundance of caution, we do not include any other classes, not even for packages like java.util.
static bool is_always_visible_class(oop mirror) {
  Klass* klass = java_lang_Class::as_Klass(mirror);
  if (klass->oop_is_objArray()) {
    klass = ObjArrayKlass::cast(klass)->bottom_klass(); // check element type
  }
  if (klass->oop_is_typeArray()) {
    return true; // primitive array
  }
  assert(klass->oop_is_instance(), "%s", klass->external_name());
  return klass->is_public() &&
         (InstanceKlass::cast(klass)->is_same_class_package(SystemDictionary::Object_klass()) ||       // java.lang
          InstanceKlass::cast(klass)->is_same_class_package(SystemDictionary::MethodHandle_klass()));  // java.lang.invoke
}

// Ask Java code to find or construct a java.lang.invoke.MethodType for the given
// signature, as interpreted relative to the given class loader.
// Because of class loader constraints, all method handle usage must be
// consistent with this loader.
Handle SystemDictionary::find_method_handle_type(Symbol* signature,
                                                 KlassHandle accessing_klass,
                                                 TRAPS) {
  Handle empty;
  vmIntrinsics::ID null_iid = vmIntrinsics::_none;  // distinct from all method handle invoker intrinsics
  unsigned int hash  = invoke_method_table()->compute_hash(signature, null_iid);
  int          index = invoke_method_table()->hash_to_index(hash);
  SymbolPropertyEntry* spe = invoke_method_table()->find_entry(index, hash, signature, null_iid);
  if (spe != NULL && spe->method_type() != NULL) {
    assert(java_lang_invoke_MethodType::is_instance(spe->method_type()), "");
    return Handle(THREAD, spe->method_type());
  } else if (!THREAD->can_call_java()) {
    warning("SystemDictionary::find_method_handle_type called from compiler thread");  // FIXME
    return Handle();  // do not attempt from within compiler, unless it was cached
  }

  Handle class_loader, protection_domain;
  if (accessing_klass.not_null()) {
    class_loader      = Handle(THREAD, InstanceKlass::cast(accessing_klass())->class_loader());
    protection_domain = Handle(THREAD, InstanceKlass::cast(accessing_klass())->protection_domain());
  }
  bool can_be_cached = true;
  int npts = ArgumentCount(signature).size();
  objArrayHandle pts = oopFactory::new_objArray(SystemDictionary::Class_klass(), npts, CHECK_(empty));
  int arg = 0;
  Handle rt; // the return type from the signature
  ResourceMark rm(THREAD);
  for (SignatureStream ss(signature); !ss.is_done(); ss.next()) {
    oop mirror = NULL;
    if (can_be_cached) {
      // Use neutral class loader to lookup candidate classes to be placed in the cache.
      mirror = ss.as_java_mirror(Handle(), Handle(),
                                 SignatureStream::ReturnNull, CHECK_(empty));
      if (mirror == NULL || (ss.is_object() && !is_always_visible_class(mirror))) {
        // Fall back to accessing_klass context.
        can_be_cached = false;
      }
    }
    if (!can_be_cached) {
      // Resolve, throwing a real error if it doesn't work.
      mirror = ss.as_java_mirror(class_loader, protection_domain,
                                 SignatureStream::NCDFError, CHECK_(empty));
    }
    assert(!oopDesc::is_null(mirror), "%s", ss.as_symbol(THREAD)->as_C_string());
    if (ss.at_return_type())
      rt = Handle(THREAD, mirror);
    else
      pts->obj_at_put(arg++, mirror);

    // Check accessibility.
    if (ss.is_object() && accessing_klass.not_null()) {
      Klass* sel_klass = java_lang_Class::as_Klass(mirror);
      mirror = NULL;  // safety
      // Emulate ConstantPool::verify_constant_pool_resolve.
      if (sel_klass->oop_is_objArray())
        sel_klass = ObjArrayKlass::cast(sel_klass)->bottom_klass();
      if (sel_klass->oop_is_instance()) {
        KlassHandle sel_kh(THREAD, sel_klass);
        LinkResolver::check_klass_accessability(accessing_klass, sel_kh, CHECK_(empty));
      }
    }
  }
  assert(arg == npts, "");

  // call java.lang.invoke.MethodHandleNatives::findMethodType(Class rt, Class[] pts) -> MethodType
  JavaCallArguments args(Handle(THREAD, rt()));
  args.push_oop(pts());
  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result,
                         SystemDictionary::MethodHandleNatives_klass(),
                         vmSymbols::findMethodHandleType_name(),
                         vmSymbols::findMethodHandleType_signature(),
                         &args, CHECK_(empty));
  Handle method_type(THREAD, (oop) result.get_jobject());

  if (can_be_cached) {
    // We can cache this MethodType inside the JVM.
    MutexLocker ml(SystemDictionary_lock, THREAD);
    spe = invoke_method_table()->find_entry(index, hash, signature, null_iid);
    if (spe == NULL)
      spe = invoke_method_table()->add_entry(index, hash, signature, null_iid);
    if (spe->method_type() == NULL) {
      spe->set_method_type(method_type());
    }
  }

  // report back to the caller with the MethodType
  return method_type;
}

// Ask Java code to find or construct a method handle constant.
Handle SystemDictionary::link_method_handle_constant(KlassHandle caller,
                                                     int ref_kind, //e.g., JVM_REF_invokeVirtual
                                                     KlassHandle callee,
                                                     Symbol* name_sym,
                                                     Symbol* signature,
                                                     TRAPS) {
  Handle empty;
  Handle name = java_lang_String::create_from_symbol(name_sym, CHECK_(empty));
  Handle type;
  if (signature->utf8_length() > 0 && signature->byte_at(0) == '(') {
    type = find_method_handle_type(signature, caller, CHECK_(empty));
  } else if (caller.is_null()) {
    // This should not happen.  JDK code should take care of that.
    THROW_MSG_(vmSymbols::java_lang_InternalError(), "bad MH constant", empty);
  } else {
    ResourceMark rm(THREAD);
    SignatureStream ss(signature, false);
    if (!ss.is_done()) {
      oop mirror = ss.as_java_mirror(caller->class_loader(), caller->protection_domain(),
                                     SignatureStream::NCDFError, CHECK_(empty));
      type = Handle(THREAD, mirror);
      ss.next();
      if (!ss.is_done())  type = Handle();  // error!
    }
  }
  if (type.is_null()) {
    THROW_MSG_(vmSymbols::java_lang_LinkageError(), "bad signature", empty);
  }

  // call java.lang.invoke.MethodHandleNatives::linkMethodHandleConstant(Class caller, int refKind, Class callee, String name, Object type) -> MethodHandle
  JavaCallArguments args;
  args.push_oop(caller->java_mirror());  // the referring class
  args.push_int(ref_kind);
  args.push_oop(callee->java_mirror());  // the target class
  args.push_oop(name());
  args.push_oop(type());
  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result,
                         SystemDictionary::MethodHandleNatives_klass(),
                         vmSymbols::linkMethodHandleConstant_name(),
                         vmSymbols::linkMethodHandleConstant_signature(),
                         &args, CHECK_(empty));
  return Handle(THREAD, (oop) result.get_jobject());
}

// Ask Java code to find or construct a java.lang.invoke.CallSite for the given
// name and signature, as interpreted relative to the given class loader.
methodHandle SystemDictionary::find_dynamic_call_site_invoker(KlassHandle caller,
                                                              Handle bootstrap_specifier,
                                                              Symbol* name,
                                                              Symbol* type,
                                                              Handle *appendix_result,
                                                              Handle *method_type_result,
                                                              TRAPS) {
  methodHandle empty;
  Handle bsm, info;
  if (java_lang_invoke_MethodHandle::is_instance(bootstrap_specifier())) {
    bsm = bootstrap_specifier;
  } else {
    assert(bootstrap_specifier->is_objArray(), "");
    objArrayHandle args(THREAD, (objArrayOop) bootstrap_specifier());
    int len = args->length();
    assert(len >= 1, "");
    bsm = Handle(THREAD, args->obj_at(0));
    if (len > 1) {
      objArrayOop args1 = oopFactory::new_objArray(SystemDictionary::Object_klass(), len-1, CHECK_(empty));
      for (int i = 1; i < len; i++)
        args1->obj_at_put(i-1, args->obj_at(i));
      info = Handle(THREAD, args1);
    }
  }
  guarantee(java_lang_invoke_MethodHandle::is_instance(bsm()),
            "caller must supply a valid BSM");

  Handle method_name = java_lang_String::create_from_symbol(name, CHECK_(empty));
  Handle method_type = find_method_handle_type(type, caller, CHECK_(empty));

  // This should not happen.  JDK code should take care of that.
  if (caller.is_null() || method_type.is_null()) {
    THROW_MSG_(vmSymbols::java_lang_InternalError(), "bad invokedynamic", empty);
  }

  objArrayHandle appendix_box = oopFactory::new_objArray(SystemDictionary::Object_klass(), 1, CHECK_(empty));
  assert(appendix_box->obj_at(0) == NULL, "");

  // call java.lang.invoke.MethodHandleNatives::linkCallSite(caller, bsm, name, mtype, info, &appendix)
  JavaCallArguments args;
  args.push_oop(caller->java_mirror());
  args.push_oop(bsm());
  args.push_oop(method_name());
  args.push_oop(method_type());
  args.push_oop(info());
  args.push_oop(appendix_box);
  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result,
                         SystemDictionary::MethodHandleNatives_klass(),
                         vmSymbols::linkCallSite_name(),
                         vmSymbols::linkCallSite_signature(),
                         &args, CHECK_(empty));
  Handle mname(THREAD, (oop) result.get_jobject());
  (*method_type_result) = method_type;
  return unpack_method_and_appendix(mname, caller, appendix_box, appendix_result, THREAD);
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
void SystemDictionary::print_shared(bool details) {
  shared_dictionary()->print(details);
}

void SystemDictionary::print(bool details) {
  dictionary()->print(details);

  // Placeholders
  GCMutexLocker mu(SystemDictionary_lock);
  placeholders()->print();

  // loader constraints - print under SD_lock
  constraints()->print();
}


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
  constraints()->verify(dictionary(), placeholders());
}

// utility function for class load event
void SystemDictionary::post_class_load_event(const Ticks& start_time,
                                             instanceKlassHandle k,
                                             Handle initiating_loader) {
#if INCLUDE_TRACE
  EventClassLoad event(UNTIMED);
  if (event.should_commit()) {
    event.set_starttime(start_time);
    event.set_loadedClass(k());
    oop defining_class_loader = k->class_loader();
    event.set_definingClassLoader(defining_class_loader !=  NULL ?
                                    defining_class_loader->klass() : (Klass*)NULL);
    oop class_loader = initiating_loader.is_null() ? (oop)NULL : initiating_loader();
    event.set_initiatingClassLoader(class_loader != NULL ?
                                      class_loader->klass() : (Klass*)NULL);
    event.commit();
  }
#endif // INCLUDE_TRACE
}

