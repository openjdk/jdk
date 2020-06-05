/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
#include "aot/aotLoader.hpp"
#include "classfile/classFileParser.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/classLoaderDataGraph.inline.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/klassFactory.hpp"
#include "classfile/loaderConstraints.hpp"
#include "classfile/packageEntry.hpp"
#include "classfile/placeholders.hpp"
#include "classfile/protectionDomainCache.hpp"
#include "classfile/resolutionErrors.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "compiler/compileBroker.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/interpreter.hpp"
#include "jfr/jfrEvents.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/filemap.hpp"
#include "memory/heapShared.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/access.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/method.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/symbol.hpp"
#include "oops/typeArrayKlass.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/arguments.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "services/classLoadingService.hpp"
#include "services/diagnosticCommand.hpp"
#include "services/threadService.hpp"
#include "utilities/macros.hpp"
#include "utilities/utf8.hpp"
#if INCLUDE_CDS
#include "classfile/systemDictionaryShared.hpp"
#endif
#if INCLUDE_JFR
#include "jfr/jfr.hpp"
#endif

PlaceholderTable*      SystemDictionary::_placeholders        = NULL;
LoaderConstraintTable* SystemDictionary::_loader_constraints  = NULL;
ResolutionErrorTable*  SystemDictionary::_resolution_errors   = NULL;
SymbolPropertyTable*   SystemDictionary::_invoke_method_table = NULL;
ProtectionDomainCacheTable*   SystemDictionary::_pd_cache_table = NULL;

InstanceKlass*      SystemDictionary::_well_known_klasses[SystemDictionary::WKID_LIMIT]
                                                          =  { NULL /*, NULL...*/ };

InstanceKlass*      SystemDictionary::_box_klasses[T_VOID+1]      =  { NULL /*, NULL...*/ };


OopHandle   SystemDictionary::_system_loader_lock_obj;
OopHandle   SystemDictionary::_java_system_loader;
OopHandle   SystemDictionary::_java_platform_loader;

// Default ProtectionDomainCacheSize value

const int defaultProtectionDomainCacheSize = 1009;

ClassLoadInfo::ClassLoadInfo() {
  _protection_domain = Handle();
  _unsafe_anonymous_host = NULL;
  _cp_patches = NULL;
  _class_hidden_info._dynamic_nest_host = NULL;
  _class_hidden_info._class_data = Handle();
  _is_hidden = false;
  _is_strong_hidden = false;
  _can_access_vm_annotations = false;
}

ClassLoadInfo::ClassLoadInfo(Handle protection_domain) {
  _protection_domain = protection_domain;
  _unsafe_anonymous_host = NULL;
  _cp_patches = NULL;
  _class_hidden_info._dynamic_nest_host = NULL;
  _class_hidden_info._class_data = Handle();
  _is_hidden = false;
  _is_strong_hidden = false;
  _can_access_vm_annotations = false;
}

ClassLoadInfo::ClassLoadInfo(Handle protection_domain,
                             const InstanceKlass* unsafe_anonymous_host,
                             GrowableArray<Handle>* cp_patches,
                             InstanceKlass* dynamic_nest_host,
                             Handle class_data,
                             bool is_hidden,
                             bool is_strong_hidden,
                             bool can_access_vm_annotations) {
  _protection_domain = protection_domain;
  _unsafe_anonymous_host = unsafe_anonymous_host;
  _cp_patches = cp_patches;
  _class_hidden_info._dynamic_nest_host = dynamic_nest_host;
  _class_hidden_info._class_data = class_data;
  _is_hidden = is_hidden;
  _is_strong_hidden = is_strong_hidden;
  _can_access_vm_annotations = can_access_vm_annotations;
}

// ----------------------------------------------------------------------------
// Java-level SystemLoader and PlatformLoader

oop SystemDictionary::system_loader_lock() {
  return _system_loader_lock_obj.resolve();
}

oop SystemDictionary::java_system_loader() {
  return _java_system_loader.resolve();
}

oop SystemDictionary::java_platform_loader() {
  return _java_platform_loader.resolve();
}

void SystemDictionary::compute_java_loaders(TRAPS) {
  JavaValue result(T_OBJECT);
  InstanceKlass* class_loader_klass = SystemDictionary::ClassLoader_klass();
  JavaCalls::call_static(&result,
                         class_loader_klass,
                         vmSymbols::getSystemClassLoader_name(),
                         vmSymbols::void_classloader_signature(),
                         CHECK);

  _java_system_loader = OopHandle::create((oop)result.get_jobject());

  JavaCalls::call_static(&result,
                         class_loader_klass,
                         vmSymbols::getPlatformClassLoader_name(),
                         vmSymbols::void_classloader_signature(),
                         CHECK);

  _java_platform_loader = OopHandle::create((oop)result.get_jobject());
}

ClassLoaderData* SystemDictionary::register_loader(Handle class_loader, bool create_mirror_cld) {
  if (create_mirror_cld) {
    // Add a new class loader data to the graph.
    return ClassLoaderDataGraph::add(class_loader, true);
  } else {
    return (class_loader() == NULL) ? ClassLoaderData::the_null_class_loader_data() :
                                      ClassLoaderDataGraph::find_or_create(class_loader);
  }
}

// ----------------------------------------------------------------------------
// Parallel class loading check

bool SystemDictionary::is_parallelCapable(Handle class_loader) {
  if (class_loader.is_null()) return true;
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

// Returns true if the passed class loader is the builtin application class loader
// or a custom system class loader. A customer system class loader can be
// specified via -Djava.system.class.loader.
bool SystemDictionary::is_system_class_loader(oop class_loader) {
  if (class_loader == NULL) {
    return false;
  }
  return (class_loader->klass() == SystemDictionary::jdk_internal_loader_ClassLoaders_AppClassLoader_klass() ||
         class_loader == _java_system_loader.peek());
}

// Returns true if the passed class loader is the platform class loader.
bool SystemDictionary::is_platform_class_loader(oop class_loader) {
  if (class_loader == NULL) {
    return false;
  }
  return (class_loader->klass() == SystemDictionary::jdk_internal_loader_ClassLoaders_PlatformClassLoader_klass());
}

// ----------------------------------------------------------------------------
// Resolving of classes

Symbol* SystemDictionary::class_name_symbol(const char* name, Symbol* exception, TRAPS) {
  if (name == NULL) {
    THROW_MSG_0(exception, "No class name given");
  }
  if ((int)strlen(name) > Symbol::max_length()) {
    // It's impossible to create this class;  the name cannot fit
    // into the constant pool.
    Exceptions::fthrow(THREAD_AND_LOCATION, exception,
                       "Class name exceeds maximum length of %d: %s",
                       Symbol::max_length(),
                       name);
    return NULL;
  }
  // Callers should ensure that the name is never an illegal UTF8 string.
  assert(UTF8::is_legal_utf8((const unsigned char*)name, (int)strlen(name), false),
         "Class name is not a valid utf8 string.");

  // Make a new symbol for the class name.
  return SymbolTable::new_symbol(name);
}

// Forwards to resolve_or_null

Klass* SystemDictionary::resolve_or_fail(Symbol* class_name, Handle class_loader, Handle protection_domain, bool throw_error, TRAPS) {
  Klass* klass = resolve_or_null(class_name, class_loader, protection_domain, THREAD);
  if (HAS_PENDING_EXCEPTION || klass == NULL) {
    // can return a null klass
    klass = handle_resolution_exception(class_name, throw_error, klass, THREAD);
  }
  return klass;
}

Klass* SystemDictionary::handle_resolution_exception(Symbol* class_name,
                                                     bool throw_error,
                                                     Klass* klass, TRAPS) {
  if (HAS_PENDING_EXCEPTION) {
    // If we have a pending exception we forward it to the caller, unless throw_error is true,
    // in which case we have to check whether the pending exception is a ClassNotFoundException,
    // and if so convert it to a NoClassDefFoundError
    // And chain the original ClassNotFoundException
    if (throw_error && PENDING_EXCEPTION->is_a(SystemDictionary::ClassNotFoundException_klass())) {
      ResourceMark rm(THREAD);
      assert(klass == NULL, "Should not have result with exception pending");
      Handle e(THREAD, PENDING_EXCEPTION);
      CLEAR_PENDING_EXCEPTION;
      THROW_MSG_CAUSE_NULL(vmSymbols::java_lang_NoClassDefFoundError(), class_name->as_C_string(), e);
    } else {
      return NULL;
    }
  }
  // Class not found, throw appropriate error or exception depending on value of throw_error
  if (klass == NULL) {
    ResourceMark rm(THREAD);
    if (throw_error) {
      THROW_MSG_NULL(vmSymbols::java_lang_NoClassDefFoundError(), class_name->as_C_string());
    } else {
      THROW_MSG_NULL(vmSymbols::java_lang_ClassNotFoundException(), class_name->as_C_string());
    }
  }
  return klass;
}


Klass* SystemDictionary::resolve_or_fail(Symbol* class_name,
                                           bool throw_error, TRAPS)
{
  return resolve_or_fail(class_name, Handle(), Handle(), throw_error, THREAD);
}


// Forwards to resolve_array_class_or_null or resolve_instance_class_or_null

Klass* SystemDictionary::resolve_or_null(Symbol* class_name, Handle class_loader, Handle protection_domain, TRAPS) {
  if (Signature::is_array(class_name)) {
    return resolve_array_class_or_null(class_name, class_loader, protection_domain, THREAD);
  } else {
    return resolve_instance_class_or_null_helper(class_name, class_loader, protection_domain, THREAD);
  }
}

// name may be in the form of "java/lang/Object" or "Ljava/lang/Object;"
InstanceKlass* SystemDictionary::resolve_instance_class_or_null_helper(Symbol* class_name,
                                                                       Handle class_loader,
                                                                       Handle protection_domain,
                                                                       TRAPS) {
  assert(class_name != NULL && !Signature::is_array(class_name), "must be");
  if (Signature::has_envelope(class_name)) {
    ResourceMark rm(THREAD);
    // Ignore wrapping L and ;.
    TempNewSymbol name = SymbolTable::new_symbol(class_name->as_C_string() + 1,
                                                 class_name->utf8_length() - 2);
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
  assert(Signature::is_array(class_name), "must be array");
  ResourceMark rm(THREAD);
  SignatureStream ss(class_name, false);
  int ndims = ss.skip_array_prefix();  // skip all '['s
  Klass* k = NULL;
  BasicType t = ss.type();
  if (ss.has_envelope()) {
    Symbol* obj_class = ss.as_symbol();
    k = SystemDictionary::resolve_instance_class_or_null(obj_class,
                                                         class_loader,
                                                         protection_domain,
                                                         CHECK_NULL);
    if (k != NULL) {
      k = k->array_klass(ndims, CHECK_NULL);
    }
  } else {
    k = Universe::typeArrayKlassObj(t);
    k = TypeArrayKlass::cast(k)->array_klass(ndims, CHECK_NULL);
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
// Be careful when modifying this code: once you have run
// placeholders()->find_and_add(PlaceholderTable::LOAD_SUPER),
// you need to find_and_remove it before returning.
// So be careful to not exit with a CHECK_ macro betweeen these calls.
InstanceKlass* SystemDictionary::resolve_super_or_fail(Symbol* child_name,
                                                       Symbol* super_name,
                                                       Handle class_loader,
                                                       Handle protection_domain,
                                                       bool is_superclass,
                                                       TRAPS) {
  assert(!Signature::is_array(super_name), "invalid super class name");
#if INCLUDE_CDS
  if (DumpSharedSpaces) {
    // Special processing for handling UNREGISTERED shared classes.
    InstanceKlass* k = SystemDictionaryShared::dump_time_resolve_super_or_fail(child_name,
        super_name, class_loader, protection_domain, is_superclass, CHECK_NULL);
    if (k) {
      return k;
    }
  }
#endif // INCLUDE_CDS

  // Double-check, if child class is already loaded, just return super-class,interface
  // Don't add a placedholder if already loaded, i.e. already in appropriate class loader
  // dictionary.
  // Make sure there's a placeholder for the *child* before resolving.
  // Used as a claim that this thread is currently loading superclass/classloader
  // Used here for ClassCircularity checks and also for heap verification
  // (every InstanceKlass needs to be in its class loader dictionary or have a placeholder).
  // Must check ClassCircularity before checking if super class is already loaded.
  //
  // We might not already have a placeholder if this child_name was
  // first seen via resolve_from_stream (jni_DefineClass or JVM_DefineClass);
  // the name of the class might not be known until the stream is actually
  // parsed.
  // Bugs 4643874, 4715493

  ClassLoaderData* loader_data = class_loader_data(class_loader);
  Dictionary* dictionary = loader_data->dictionary();
  unsigned int d_hash = dictionary->compute_hash(child_name);
  unsigned int p_hash = placeholders()->compute_hash(child_name);
  int p_index = placeholders()->hash_to_index(p_hash);
  // can't throw error holding a lock
  bool child_already_loaded = false;
  bool throw_circularity_error = false;
  {
    MutexLocker mu(THREAD, SystemDictionary_lock);
    InstanceKlass* childk = find_class(d_hash, child_name, dictionary);
    InstanceKlass* quicksuperk;
    // to support // loading: if child done loading, just return superclass
    // if super_name, & class_loader don't match:
    // if initial define, SD update will give LinkageError
    // if redefine: compare_class_versions will give HIERARCHY_CHANGED
    // so we don't throw an exception here.
    // see: nsk redefclass014 & java.lang.instrument Instrument032
    if ((childk != NULL ) && (is_superclass) &&
        ((quicksuperk = childk->java_super()) != NULL) &&
         ((quicksuperk->name() == super_name) &&
            (quicksuperk->class_loader() == class_loader()))) {
           return quicksuperk;
    } else {
      PlaceholderEntry* probe = placeholders()->get_entry(p_index, p_hash, child_name, loader_data);
      if (probe && probe->check_seen_thread(THREAD, PlaceholderTable::LOAD_SUPER)) {
          throw_circularity_error = true;
      }
    }
    if (!throw_circularity_error) {
      // Be careful not to exit resolve_super
      PlaceholderEntry* newprobe = placeholders()->find_and_add(p_index, p_hash, child_name, loader_data, PlaceholderTable::LOAD_SUPER, super_name, THREAD);
    }
  }
  if (throw_circularity_error) {
      ResourceMark rm(THREAD);
      THROW_MSG_NULL(vmSymbols::java_lang_ClassCircularityError(), child_name->as_C_string());
  }

// java.lang.Object should have been found above
  assert(super_name != NULL, "null super class for resolving");
  // Resolve the super class or interface, check results on return
  InstanceKlass* superk =
    SystemDictionary::resolve_instance_class_or_null_helper(super_name,
                                                            class_loader,
                                                            protection_domain,
                                                            THREAD);

  // Clean up of placeholders moved so that each classloadAction registrar self-cleans up
  // It is no longer necessary to keep the placeholder table alive until update_dictionary
  // or error. GC used to walk the placeholder table as strong roots.
  // The instanceKlass is kept alive because the class loader is on the stack,
  // which keeps the loader_data alive, as well as all instanceKlasses in
  // the loader_data. parseClassFile adds the instanceKlass to loader_data.
  {
    MutexLocker mu(THREAD, SystemDictionary_lock);
    placeholders()->find_and_remove(p_index, p_hash, child_name, loader_data, PlaceholderTable::LOAD_SUPER, THREAD);
    SystemDictionary_lock->notify_all();
  }
  if (HAS_PENDING_EXCEPTION || superk == NULL) {
    // can null superk
    Klass* k = handle_resolution_exception(super_name, true, superk, THREAD);
    assert(k == NULL || k == superk, "must be");
    if (k == NULL) {
      superk = NULL;
    }
  }

  return superk;
}

void SystemDictionary::validate_protection_domain(InstanceKlass* klass,
                                                  Handle class_loader,
                                                  Handle protection_domain,
                                                  TRAPS) {
  // Now we have to call back to java to check if the initating class has access
  JavaValue result(T_VOID);
  LogTarget(Debug, protectiondomain) lt;
  if (lt.is_enabled()) {
    ResourceMark rm(THREAD);
    // Print out trace information
    LogStream ls(lt);
    ls.print_cr("Checking package access");
    if (class_loader() != NULL) {
      ls.print("class loader: ");
      class_loader()->print_value_on(&ls);
    } else {
      ls.print_cr("class loader: NULL");
    }
    if (protection_domain() != NULL) {
      ls.print(" protection domain: ");
      protection_domain()->print_value_on(&ls);
    } else {
      ls.print_cr(" protection domain: NULL");
    }
    ls.print(" loading: "); klass->print_value_on(&ls);
    ls.cr();
  }

  // This handle and the class_loader handle passed in keeps this class from
  // being unloaded through several GC points.
  // The class_loader handle passed in is the initiating loader.
  Handle mirror(THREAD, klass->java_mirror());

  InstanceKlass* system_loader = SystemDictionary::ClassLoader_klass();
  JavaCalls::call_special(&result,
                         class_loader,
                         system_loader,
                         vmSymbols::checkPackageAccess_name(),
                         vmSymbols::class_protectiondomain_signature(),
                         mirror,
                         protection_domain,
                         THREAD);

  if (HAS_PENDING_EXCEPTION) {
    log_debug(protectiondomain)("DENIED !!!!!!!!!!!!!!!!!!!!!");
  } else {
   log_debug(protectiondomain)("granted");
  }

  if (HAS_PENDING_EXCEPTION) return;

  // If no exception has been thrown, we have validated the protection domain
  // Insert the protection domain of the initiating class into the set.
  {
    ClassLoaderData* loader_data = class_loader_data(class_loader);
    Dictionary* dictionary = loader_data->dictionary();

    Symbol*  kn = klass->name();
    unsigned int d_hash = dictionary->compute_hash(kn);

    MutexLocker mu(THREAD, SystemDictionary_lock);
    int d_index = dictionary->hash_to_index(d_hash);
    dictionary->add_protection_domain(d_index, d_hash, klass,
                                      protection_domain, THREAD);
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
//  2) custom classLoader has broken the class loader objectLock
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
  assert((lockObject() != _system_loader_lock_obj.resolve() &&
         !is_parallelCapable(lockObject)), "unexpected double_lock_wait");
  ObjectSynchronizer::notifyall(lockObject, THREAD);
  intx recursions =  ObjectSynchronizer::complete_exit(lockObject, THREAD);
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
InstanceKlass* SystemDictionary::handle_parallel_super_load(
    Symbol* name, Symbol* superclassname, Handle class_loader,
    Handle protection_domain, Handle lockObject, TRAPS) {

  ClassLoaderData* loader_data = class_loader_data(class_loader);
  Dictionary* dictionary = loader_data->dictionary();
  unsigned int d_hash = dictionary->compute_hash(name);
  unsigned int p_hash = placeholders()->compute_hash(name);
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
                                                          CHECK_NULL);

  // parallelCapable class loaders do NOT wait for parallel superclass loads to complete
  // Serial class loaders and bootstrap classloader do wait for superclass loads
 if (!class_loader.is_null() && is_parallelCapable(class_loader)) {
    MutexLocker mu(THREAD, SystemDictionary_lock);
    // Check if classloading completed while we were loading superclass or waiting
    return find_class(d_hash, name, dictionary);
  }

  // must loop to both handle other placeholder updates
  // and spurious notifications
  bool super_load_in_progress = true;
  PlaceholderEntry* placeholder;
  while (super_load_in_progress) {
    MutexLocker mu(THREAD, SystemDictionary_lock);
    // Check if classloading completed while we were loading superclass or waiting
    InstanceKlass* check = find_class(d_hash, name, dictionary);
    if (check != NULL) {
      // Klass is already loaded, so just return it
      return check;
    } else {
      placeholder = placeholders()->get_entry(p_index, p_hash, name, loader_data);
      if (placeholder && placeholder->super_load_in_progress() ){
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
  return NULL;
}

static void post_class_load_event(EventClassLoad* event, const InstanceKlass* k, const ClassLoaderData* init_cld) {
  assert(event != NULL, "invariant");
  assert(k != NULL, "invariant");
  assert(event->should_commit(), "invariant");
  event->set_loadedClass(k);
  event->set_definingClassLoader(k->class_loader_data());
  event->set_initiatingClassLoader(init_cld);
  event->commit();
}


// Be careful when modifying this code: once you have run
// placeholders()->find_and_add(PlaceholderTable::LOAD_INSTANCE),
// you need to find_and_remove it before returning.
// So be careful to not exit with a CHECK_ macro betweeen these calls.
//
// name must be in the form of "java/lang/Object" -- cannot be "Ljava/lang/Object;"
InstanceKlass* SystemDictionary::resolve_instance_class_or_null(Symbol* name,
                                                                Handle class_loader,
                                                                Handle protection_domain,
                                                                TRAPS) {
  assert(name != NULL && !Signature::is_array(name) &&
         !Signature::has_envelope(name), "invalid class name");

  EventClassLoad class_load_start_event;

  HandleMark hm(THREAD);

  // Fix for 4474172; see evaluation for more details
  class_loader = Handle(THREAD, java_lang_ClassLoader::non_reflection_class_loader(class_loader()));
  ClassLoaderData* loader_data = register_loader(class_loader);
  Dictionary* dictionary = loader_data->dictionary();
  unsigned int d_hash = dictionary->compute_hash(name);

  // Do lookup to see if class already exist and the protection domain
  // has the right access
  // This call uses find which checks protection domain already matches
  // All subsequent calls use find_class, and set has_loaded_class so that
  // before we return a result we call out to java to check for valid protection domain
  // to allow returning the Klass* and add it to the pd_set if it is valid
  {
    InstanceKlass* probe = dictionary->find(d_hash, name, protection_domain);
    if (probe != NULL) return probe;
  }

  // Non-bootstrap class loaders will call out to class loader and
  // define via jvm/jni_DefineClass which will acquire the
  // class loader object lock to protect against multiple threads
  // defining the class in parallel by accident.
  // This lock must be acquired here so the waiter will find
  // any successful result in the SystemDictionary and not attempt
  // the define.
  // ParallelCapable Classloaders and the bootstrap classloader
  // do not acquire lock here.
  bool DoObjectLock = true;
  if (is_parallelCapable(class_loader)) {
    DoObjectLock = false;
  }

  unsigned int p_hash = placeholders()->compute_hash(name);
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
  InstanceKlass* k = NULL;
  PlaceholderEntry* placeholder;
  Symbol* superclassname = NULL;

  assert(THREAD->can_call_java(),
         "can not load classes with compiler thread: class=%s, classloader=%s",
         name->as_C_string(),
         class_loader.is_null() ? "null" : class_loader->klass()->name()->as_C_string());
  {
    MutexLocker mu(THREAD, SystemDictionary_lock);
    InstanceKlass* check = find_class(d_hash, name, dictionary);
    if (check != NULL) {
      // InstanceKlass is already loaded, so just return it
      class_has_been_loaded = true;
      k = check;
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
    k = handle_parallel_super_load(name,
                                   superclassname,
                                   class_loader,
                                   protection_domain,
                                   lockObject, THREAD);
    if (HAS_PENDING_EXCEPTION) {
      return NULL;
    }
    if (k != NULL) {
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
    // case 3. Bootstrap classloader - don't own objectLocker
    //    This classloader supports parallelism at the classloader level,
    //    but only allows a single load of a class/classloader pair.
    //    No performance benefit and no deadlock issues.
    // case 4. parallelCapable user level classloaders - without objectLocker
    //    Allow parallel classloading of a class/classloader pair

    {
      MutexLocker mu(THREAD, SystemDictionary_lock);
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

              // case 3: bootstrap classloader: prevent futile classloading,
              // wait on first requestor
              if (class_loader.is_null()) {
                SystemDictionary_lock->wait();
              } else {
              // case 2: traditional with broken classloader lock. wait on first
              // requestor.
                double_lock_wait(lockObject, THREAD);
              }
              // Check if classloading completed while we were waiting
              InstanceKlass* check = find_class(d_hash, name, dictionary);
              if (check != NULL) {
                // Klass is already loaded, so just return it
                k = check;
                class_has_been_loaded = true;
              }
              // check if other thread failed to load and cleaned up
              oldprobe = placeholders()->get_entry(p_index, p_hash, name, loader_data);
            }
          }
        }
      }
      // All cases: add LOAD_INSTANCE holding SystemDictionary_lock
      // case 4: parallelCapable: allow competing threads to try
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
        InstanceKlass* check = find_class(d_hash, name, dictionary);
        if (check != NULL) {
          // Klass is already loaded, so return it after checking/adding protection domain
          k = check;
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

      // If everything was OK (no exceptions, no null return value), and
      // class_loader is NOT the defining loader, do a little more bookkeeping.
      if (!HAS_PENDING_EXCEPTION && k != NULL &&
        k->class_loader() != class_loader()) {

        check_constraints(d_hash, k, class_loader, false, THREAD);

        // Need to check for a PENDING_EXCEPTION again; check_constraints
        // can throw but we may have to remove entry from the placeholder table below.
        if (!HAS_PENDING_EXCEPTION) {
          // Record dependency for non-parent delegation.
          // This recording keeps the defining class loader of the klass (k) found
          // from being unloaded while the initiating class loader is loaded
          // even if the reference to the defining class loader is dropped
          // before references to the initiating class loader.
          loader_data->record_dependency(k);

          { // Grabbing the Compile_lock prevents systemDictionary updates
            // during compilations.
            MutexLocker mu(THREAD, Compile_lock);
            update_dictionary(d_hash, p_index, p_hash,
              k, class_loader, THREAD);
          }

          if (JvmtiExport::should_post_class_load()) {
            Thread *thread = THREAD;
            assert(thread->is_Java_thread(), "thread->is_Java_thread()");
            JvmtiExport::post_class_load((JavaThread *) thread, k);
          }
        }
      }
    } // load_instance_class

    if (load_instance_added == true) {
      // clean up placeholder entries for LOAD_INSTANCE success or error
      // This brackets the SystemDictionary updates for both defining
      // and initiating loaders
      MutexLocker mu(THREAD, SystemDictionary_lock);
      placeholders()->find_and_remove(p_index, p_hash, name, loader_data, PlaceholderTable::LOAD_INSTANCE, THREAD);
      SystemDictionary_lock->notify_all();
    }
  }

  if (HAS_PENDING_EXCEPTION || k == NULL) {
    return NULL;
  }
  if (class_load_start_event.should_commit()) {
    post_class_load_event(&class_load_start_event, k, loader_data);
  }
#ifdef ASSERT
  {
    ClassLoaderData* loader_data = k->class_loader_data();
    MutexLocker mu(THREAD, SystemDictionary_lock);
    InstanceKlass* kk = find_class(name, loader_data);
    assert(kk == k, "should be present in dictionary");
  }
#endif

  // return if the protection domain in NULL
  if (protection_domain() == NULL) return k;

  // Check the protection domain has the right access
  if (dictionary->is_valid_protection_domain(d_hash, name,
                                             protection_domain)) {
    return k;
  }

  // Verify protection domain. If it fails an exception is thrown
  validate_protection_domain(k, class_loader, protection_domain, CHECK_NULL);

  return k;
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

  Dictionary* dictionary = loader_data->dictionary();
  unsigned int d_hash = dictionary->compute_hash(class_name);
  return dictionary->find(d_hash, class_name,
                          protection_domain);
}


// Look for a loaded instance or array klass by name.  Do not do any loading.
// return NULL in case of error.
Klass* SystemDictionary::find_instance_or_array_klass(Symbol* class_name,
                                                      Handle class_loader,
                                                      Handle protection_domain,
                                                      TRAPS) {
  Klass* k = NULL;
  assert(class_name != NULL, "class name must be non NULL");

  if (Signature::is_array(class_name)) {
    // The name refers to an array.  Parse the name.
    // dimension and object_key in FieldArrayInfo are assigned as a
    // side-effect of this call
    SignatureStream ss(class_name, false);
    int ndims = ss.skip_array_prefix();  // skip all '['s
    BasicType t = ss.type();
    if (t != T_OBJECT) {
      k = Universe::typeArrayKlassObj(t);
    } else {
      k = SystemDictionary::find(ss.as_symbol(), class_loader, protection_domain, THREAD);
    }
    if (k != NULL) {
      k = k->array_klass_or_null(ndims);
    }
  } else {
    k = find(class_name, class_loader, protection_domain, THREAD);
  }
  return k;
}

// Note: this method is much like resolve_from_stream, but
// does not publish the classes via the SystemDictionary.
// Handles Lookup.defineClass hidden, unsafe_DefineAnonymousClass
// and redefineclasses. RedefinedClasses do not add to the class hierarchy.
InstanceKlass* SystemDictionary::parse_stream(Symbol* class_name,
                                              Handle class_loader,
                                              ClassFileStream* st,
                                              const ClassLoadInfo& cl_info,
                                              TRAPS) {

  EventClassLoad class_load_start_event;
  ClassLoaderData* loader_data;
  bool is_unsafe_anon_class = cl_info.unsafe_anonymous_host() != NULL;

  // - for unsafe anonymous class: create a new CLD whith a class holder that uses
  //                               the same class loader as the unsafe_anonymous_host.
  // - for hidden classes that are not strong: create a new CLD that has a class holder and
  //                                           whose loader is the Lookup class's loader.
  // - for hidden class: add the class to the Lookup class's loader's CLD.
  if (is_unsafe_anon_class || cl_info.is_hidden()) {
    guarantee(!is_unsafe_anon_class || cl_info.unsafe_anonymous_host()->class_loader() == class_loader(),
              "should be NULL or the same");
    bool create_mirror_cld = is_unsafe_anon_class || !cl_info.is_strong_hidden();
    loader_data = register_loader(class_loader, create_mirror_cld);
  } else {
    loader_data = ClassLoaderData::class_loader_data(class_loader());
  }

  assert(st != NULL, "invariant");
  assert(st->need_verify(), "invariant");

  // Parse stream and create a klass.
  // Note that we do this even though this klass might
  // already be present in the SystemDictionary, otherwise we would not
  // throw potential ClassFormatErrors.

  InstanceKlass* k = KlassFactory::create_from_stream(st,
                                                      class_name,
                                                      loader_data,
                                                      cl_info,
                                                      CHECK_NULL);

  if ((cl_info.is_hidden() || is_unsafe_anon_class) && k != NULL) {
    // Hidden classes that are not strong and unsafe anonymous classes must update
    // ClassLoaderData holder so that they can be unloaded when the mirror is no
    // longer referenced.
    if (!cl_info.is_strong_hidden() || is_unsafe_anon_class) {
      k->class_loader_data()->initialize_holder(Handle(THREAD, k->java_mirror()));
    }

    {
      MutexLocker mu_r(THREAD, Compile_lock);

      // Add to class hierarchy, initialize vtables, and do possible
      // deoptimizations.
      add_to_hierarchy(k, CHECK_NULL); // No exception, but can block
      // But, do not add to dictionary.
    }

    // Rewrite and patch constant pool here.
    k->link_class(CHECK_NULL);
    if (cl_info.cp_patches() != NULL) {
      k->constants()->patch_resolved_references(cl_info.cp_patches());
    }

    // If it's anonymous, initialize it now, since nobody else will.
    if (is_unsafe_anon_class) {
      k->eager_initialize(CHECK_NULL);
    }

    // notify jvmti
    if (JvmtiExport::should_post_class_load()) {
        assert(THREAD->is_Java_thread(), "thread->is_Java_thread()");
        JvmtiExport::post_class_load((JavaThread *) THREAD, k);
    }
    if (class_load_start_event.should_commit()) {
      post_class_load_event(&class_load_start_event, k, loader_data);
    }
  }
  assert(is_unsafe_anon_class || NULL == cl_info.cp_patches(),
         "cp_patches only found with unsafe_anonymous_host");

  return k;
}

// Add a klass to the system from a stream (called by jni_DefineClass and
// JVM_DefineClass).
// Note: class_name can be NULL. In that case we do not know the name of
// the class until we have parsed the stream.

InstanceKlass* SystemDictionary::resolve_from_stream(Symbol* class_name,
                                                     Handle class_loader,
                                                     Handle protection_domain,
                                                     ClassFileStream* st,
                                                     TRAPS) {

  HandleMark hm(THREAD);

  // Classloaders that support parallelism, e.g. bootstrap classloader,
  // do not acquire lock here
  bool DoObjectLock = true;
  if (is_parallelCapable(class_loader)) {
    DoObjectLock = false;
  }

  ClassLoaderData* loader_data = register_loader(class_loader);

  // Make sure we are synchronized on the class loader before we proceed
  Handle lockObject = compute_loader_lock_object(class_loader, THREAD);
  check_loader_lock_contention(lockObject, THREAD);
  ObjectLocker ol(lockObject, THREAD, DoObjectLock);

  assert(st != NULL, "invariant");

  // Parse the stream and create a klass.
  // Note that we do this even though this klass might
  // already be present in the SystemDictionary, otherwise we would not
  // throw potential ClassFormatErrors.
 InstanceKlass* k = NULL;

#if INCLUDE_CDS
  if (!DumpSharedSpaces) {
    k = SystemDictionaryShared::lookup_from_stream(class_name,
                                                   class_loader,
                                                   protection_domain,
                                                   st,
                                                   CHECK_NULL);
  }
#endif

  if (k == NULL) {
    if (st->buffer() == NULL) {
      return NULL;
    }
    ClassLoadInfo cl_info(protection_domain);
    k = KlassFactory::create_from_stream(st, class_name, loader_data, cl_info, CHECK_NULL);
  }

  assert(k != NULL, "no klass created");
  Symbol* h_name = k->name();
  assert(class_name == NULL || class_name == h_name, "name mismatch");

  // Add class just loaded
  // If a class loader supports parallel classloading handle parallel define requests
  // find_or_define_instance_class may return a different InstanceKlass
  if (is_parallelCapable(class_loader)) {
    InstanceKlass* defined_k = find_or_define_instance_class(h_name, class_loader, k, THREAD);
    if (!HAS_PENDING_EXCEPTION && defined_k != k) {
      // If a parallel capable class loader already defined this class, register 'k' for cleanup.
      assert(defined_k != NULL, "Should have a klass if there's no exception");
      loader_data->add_to_deallocate_list(k);
      k = defined_k;
    }
  } else {
    define_instance_class(k, THREAD);
  }

  // If defining the class throws an exception register 'k' for cleanup.
  if (HAS_PENDING_EXCEPTION) {
    assert(k != NULL, "Must have an instance klass here!");
    loader_data->add_to_deallocate_list(k);
    return NULL;
  }

  // Make sure we have an entry in the SystemDictionary on success
  debug_only( {
    MutexLocker mu(THREAD, SystemDictionary_lock);

    Klass* check = find_class(h_name, k->class_loader_data());
    assert(check == k, "should be present in the dictionary");
  } );

  return k;
}

#if INCLUDE_CDS
// Load a class for boot loader from the shared spaces. This also
// forces the super class and all interfaces to be loaded.
InstanceKlass* SystemDictionary::load_shared_boot_class(Symbol* class_name,
                                                        PackageEntry* pkg_entry,
                                                        TRAPS) {
  InstanceKlass* ik = SystemDictionaryShared::find_builtin_class(class_name);
  if (ik != NULL && ik->is_shared_boot_class()) {
    return load_shared_class(ik, Handle(), Handle(), NULL, pkg_entry, THREAD);
  }
  return NULL;
}

// Check if a shared class can be loaded by the specific classloader:
//
// NULL classloader:
//   - Module class from "modules" jimage. ModuleEntry must be defined in the classloader.
//   - Class from -Xbootclasspath/a. The class has no defined PackageEntry, or must
//     be defined in an unnamed module.
bool SystemDictionary::is_shared_class_visible(Symbol* class_name,
                                               InstanceKlass* ik,
                                               PackageEntry* pkg_entry,
                                               Handle class_loader, TRAPS) {
  assert(!ModuleEntryTable::javabase_moduleEntry()->is_patched(),
         "Cannot use sharing if java.base is patched");
  ResourceMark rm(THREAD);
  int path_index = ik->shared_classpath_index();
  ClassLoaderData* loader_data = class_loader_data(class_loader);
  if (path_index < 0) {
    // path_index < 0 indicates that the class is intended for a custom loader
    // and should not be loaded by boot/platform/app loaders
    if (loader_data->is_builtin_class_loader_data()) {
      return false;
    } else {
      return true;
    }
  }
  SharedClassPathEntry* ent =
            (SharedClassPathEntry*)FileMapInfo::shared_path(path_index);
  if (!Universe::is_module_initialized()) {
    assert(ent != NULL && ent->is_modules_image(),
           "Loading non-bootstrap classes before the module system is initialized");
    assert(class_loader.is_null(), "sanity");
    return true;
  }
  // Get the pkg_entry from the classloader
  ModuleEntry* mod_entry = NULL;
  TempNewSymbol pkg_name = pkg_entry != NULL ? pkg_entry->name() :
                                               ClassLoader::package_from_class_name(class_name);
  if (pkg_name != NULL) {
    if (loader_data != NULL) {
      if (pkg_entry != NULL) {
        mod_entry = pkg_entry->module();
        // If the archived class is from a module that has been patched at runtime,
        // the class cannot be loaded from the archive.
        if (mod_entry != NULL && mod_entry->is_patched()) {
          return false;
        }
      }
    }
  }

  if (class_loader.is_null()) {
    assert(ent != NULL, "Shared class for NULL classloader must have valid SharedClassPathEntry");
    // The NULL classloader can load archived class originated from the
    // "modules" jimage and the -Xbootclasspath/a. For class from the
    // "modules" jimage, the PackageEntry/ModuleEntry must be defined
    // by the NULL classloader.
    if (mod_entry != NULL) {
      // PackageEntry/ModuleEntry is found in the classloader. Check if the
      // ModuleEntry's location agrees with the archived class' origination.
      if (ent->is_modules_image() && mod_entry->location()->starts_with("jrt:")) {
        return true; // Module class from the "module" jimage
      }
    }

    // If the archived class is not from the "module" jimage, the class can be
    // loaded by the NULL classloader if
    //
    // 1. the class is from the unamed package
    // 2. or, the class is not from a module defined in the NULL classloader
    // 3. or, the class is from an unamed module
    if (!ent->is_modules_image() && ik->is_shared_boot_class()) {
      // the class is from the -Xbootclasspath/a
      if (pkg_name == NULL ||
          pkg_entry == NULL ||
          pkg_entry->in_unnamed_module()) {
        assert(mod_entry == NULL ||
               mod_entry == loader_data->unnamed_module(),
               "the unnamed module is not defined in the classloader");
        return true;
      }
    }
    return false;
  } else {
    bool res = SystemDictionaryShared::is_shared_class_visible_for_classloader(
              ik, class_loader, pkg_name, pkg_entry, mod_entry, CHECK_(false));
    return res;
  }
}

bool SystemDictionary::check_shared_class_super_type(InstanceKlass* child, InstanceKlass* super_type,
                                                     Handle class_loader,  Handle protection_domain,
                                                     bool is_superclass, TRAPS) {
  assert(super_type->is_shared(), "must be");

  Klass *found = resolve_super_or_fail(child->name(), super_type->name(),
                                       class_loader, protection_domain, is_superclass, CHECK_0);
  if (found == super_type) {
    return true;
  } else {
    // The dynamically resolved super type is not the same as the one we used during dump time,
    // so we cannot use the child class.
    return false;
  }
}

bool SystemDictionary::check_shared_class_super_types(InstanceKlass* ik, Handle class_loader,
                                                      Handle protection_domain, TRAPS) {
  // Check the superclass and interfaces. They must be the same
  // as in dump time, because the layout of <ik> depends on
  // the specific layout of ik->super() and ik->local_interfaces().
  //
  // If unexpected superclass or interfaces are found, we cannot
  // load <ik> from the shared archive.

  if (ik->super() != NULL &&
      !check_shared_class_super_type(ik, InstanceKlass::cast(ik->super()),
                                     class_loader, protection_domain, true, THREAD)) {
    return false;
  }

  Array<InstanceKlass*>* interfaces = ik->local_interfaces();
  int num_interfaces = interfaces->length();
  for (int index = 0; index < num_interfaces; index++) {
    if (!check_shared_class_super_type(ik, interfaces->at(index), class_loader, protection_domain, false, THREAD)) {
      return false;
    }
  }

  return true;
}

InstanceKlass* SystemDictionary::load_shared_class(InstanceKlass* ik,
                                                   Handle class_loader,
                                                   Handle protection_domain,
                                                   const ClassFileStream *cfs,
                                                   PackageEntry* pkg_entry,
                                                   TRAPS) {
  assert(ik != NULL, "sanity");
  assert(!ik->is_unshareable_info_restored(), "shared class can be loaded only once");
  Symbol* class_name = ik->name();

  bool visible = is_shared_class_visible(
                          class_name, ik, pkg_entry, class_loader, CHECK_NULL);
  if (!visible) {
    return NULL;
  }

  if (!check_shared_class_super_types(ik, class_loader, protection_domain, THREAD)) {
    return NULL;
  }

  InstanceKlass* new_ik = KlassFactory::check_shared_class_file_load_hook(
      ik, class_name, class_loader, protection_domain, cfs, CHECK_NULL);
  if (new_ik != NULL) {
    // The class is changed by CFLH. Return the new class. The shared class is
    // not used.
    return new_ik;
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
    HandleMark hm(THREAD);
    Handle lockObject = compute_loader_lock_object(class_loader, THREAD);
    check_loader_lock_contention(lockObject, THREAD);
    ObjectLocker ol(lockObject, THREAD, true);
    // prohibited package check assumes all classes loaded from archive call
    // restore_unshareable_info which calls ik->set_package()
    ik->restore_unshareable_info(loader_data, protection_domain, pkg_entry, CHECK_NULL);
  }

  load_shared_class_misc(ik, loader_data, CHECK_NULL);
  return ik;
}

void SystemDictionary::load_shared_class_misc(InstanceKlass* ik, ClassLoaderData* loader_data, TRAPS) {
  ik->print_class_load_logging(loader_data, NULL, NULL);

  // For boot loader, ensure that GetSystemPackage knows that a class in this
  // package was loaded.
  if (loader_data->is_the_null_class_loader_data()) {
    int path_index = ik->shared_classpath_index();
    ik->set_classpath_index(path_index, THREAD);
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
  ClassLoadingService::notify_class_loaded(ik, true /* shared class */);

  ik->set_has_passed_fingerprint_check(false);
  if (UseAOT && ik->supers_have_passed_fingerprint_checks()) {
    uint64_t aot_fp = AOTLoader::get_saved_fingerprint(ik);
    uint64_t cds_fp = ik->get_stored_fingerprint();
    if (aot_fp != 0 && aot_fp == cds_fp) {
      // This class matches with a class saved in an AOT library
      ik->set_has_passed_fingerprint_check(true);
    } else {
      if (log_is_enabled(Info, class, fingerprint)) {
        ResourceMark rm(THREAD);
        log_info(class, fingerprint)("%s :  expected = " PTR64_FORMAT " actual = " PTR64_FORMAT, ik->external_name(), aot_fp, cds_fp);
      }
    }
  }
}

void SystemDictionary::quick_resolve(InstanceKlass* klass, ClassLoaderData* loader_data, Handle domain, TRAPS) {
  assert(!Universe::is_fully_initialized(), "We can make short cuts only during VM initialization");
  assert(klass->is_shared(), "Must be shared class");
  if (klass->class_loader_data() != NULL) {
    return;
  }

  // add super and interfaces first
  Klass* super = klass->super();
  if (super != NULL && super->class_loader_data() == NULL) {
    assert(super->is_instance_klass(), "Super should be instance klass");
    quick_resolve(InstanceKlass::cast(super), loader_data, domain, CHECK);
  }

  Array<InstanceKlass*>* ifs = klass->local_interfaces();
  for (int i = 0; i < ifs->length(); i++) {
    InstanceKlass* ik = ifs->at(i);
    if (ik->class_loader_data()  == NULL) {
      quick_resolve(ik, loader_data, domain, CHECK);
    }
  }

  klass->restore_unshareable_info(loader_data, domain, NULL, THREAD);
  load_shared_class_misc(klass, loader_data, CHECK);
  Dictionary* dictionary = loader_data->dictionary();
  unsigned int hash = dictionary->compute_hash(klass->name());
  dictionary->add_klass(hash, klass->name(), klass);
  add_to_hierarchy(klass, CHECK);
  assert(klass->is_loaded(), "Must be in at least loaded state");
}
#endif // INCLUDE_CDS

InstanceKlass* SystemDictionary::load_instance_class(Symbol* class_name, Handle class_loader, TRAPS) {

  if (class_loader.is_null()) {
    ResourceMark rm(THREAD);
    PackageEntry* pkg_entry = NULL;
    bool search_only_bootloader_append = false;
    ClassLoaderData *loader_data = class_loader_data(class_loader);

    // Find the package in the boot loader's package entry table.
    TempNewSymbol pkg_name = ClassLoader::package_from_class_name(class_name);
    if (pkg_name != NULL) {
      pkg_entry = loader_data->packages()->lookup_only(pkg_name);
    }

    // Prior to attempting to load the class, enforce the boot loader's
    // visibility boundaries.
    if (!Universe::is_module_initialized()) {
      // During bootstrapping, prior to module initialization, any
      // class attempting to be loaded must be checked against the
      // java.base packages in the boot loader's PackageEntryTable.
      // No class outside of java.base is allowed to be loaded during
      // this bootstrapping window.
      if (pkg_entry == NULL || pkg_entry->in_unnamed_module()) {
        // Class is either in the unnamed package or in
        // a named package within the unnamed module.  Either
        // case is outside of java.base, do not attempt to
        // load the class post java.base definition.  If
        // java.base has not been defined, let the class load
        // and its package will be checked later by
        // ModuleEntryTable::verify_javabase_packages.
        if (ModuleEntryTable::javabase_defined()) {
          return NULL;
        }
      } else {
        // Check that the class' package is defined within java.base.
        ModuleEntry* mod_entry = pkg_entry->module();
        Symbol* mod_entry_name = mod_entry->name();
        if (mod_entry_name->fast_compare(vmSymbols::java_base()) != 0) {
          return NULL;
        }
      }
    } else {
      // After the module system has been initialized, check if the class'
      // package is in a module defined to the boot loader.
      if (pkg_name == NULL || pkg_entry == NULL || pkg_entry->in_unnamed_module()) {
        // Class is either in the unnamed package, in a named package
        // within a module not defined to the boot loader or in a
        // a named package within the unnamed module.  In all cases,
        // limit visibility to search for the class only in the boot
        // loader's append path.
        if (!ClassLoader::has_bootclasspath_append()) {
           // If there is no bootclasspath append entry, no need to continue
           // searching.
           return NULL;
        }
        search_only_bootloader_append = true;
      }
    }

    // Prior to bootstrapping's module initialization, never load a class outside
    // of the boot loader's module path
    assert(Universe::is_module_initialized() ||
           !search_only_bootloader_append,
           "Attempt to load a class outside of boot loader's module path");

    // Search for classes in the CDS archive.
    InstanceKlass* k = NULL;
    {
#if INCLUDE_CDS
      PerfTraceTime vmtimer(ClassLoader::perf_shared_classload_time());
      k = load_shared_boot_class(class_name, pkg_entry, THREAD);
#endif
    }

    if (k == NULL) {
      // Use VM class loader
      PerfTraceTime vmtimer(ClassLoader::perf_sys_classload_time());
      k = ClassLoader::load_class(class_name, search_only_bootloader_append, CHECK_NULL);
    }

    // find_or_define_instance_class may return a different InstanceKlass
    if (k != NULL) {
      InstanceKlass* defined_k =
        find_or_define_instance_class(class_name, class_loader, k, THREAD);
      if (!HAS_PENDING_EXCEPTION && defined_k != k) {
        // If a parallel capable class loader already defined this class, register 'k' for cleanup.
        assert(defined_k != NULL, "Should have a klass if there's no exception");
        loader_data->add_to_deallocate_list(k);
        k = defined_k;
      } else if (HAS_PENDING_EXCEPTION) {
        loader_data->add_to_deallocate_list(k);
        return NULL;
      }
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

    Handle s = java_lang_String::create_from_symbol(class_name, CHECK_NULL);
    // Translate to external class name format, i.e., convert '/' chars to '.'
    Handle string = java_lang_String::externalize_classname(s, CHECK_NULL);

    JavaValue result(T_OBJECT);

    InstanceKlass* spec_klass = SystemDictionary::ClassLoader_klass();

    // Call public unsynchronized loadClass(String) directly for all class loaders.
    // For parallelCapable class loaders, JDK >=7, loadClass(String, boolean) will
    // acquire a class-name based lock rather than the class loader object lock.
    // JDK < 7 already acquire the class loader lock in loadClass(String, boolean).
    JavaCalls::call_virtual(&result,
                            class_loader,
                            spec_klass,
                            vmSymbols::loadClass_name(),
                            vmSymbols::string_class_signature(),
                            string,
                            CHECK_NULL);

    assert(result.get_type() == T_OBJECT, "just checking");
    oop obj = (oop) result.get_jobject();

    // Primitive classes return null since forName() can not be
    // used to obtain any of the Class objects representing primitives or void
    if ((obj != NULL) && !(java_lang_Class::is_primitive(obj))) {
      InstanceKlass* k = InstanceKlass::cast(java_lang_Class::as_Klass(obj));
      // For user defined Java class loaders, check that the name returned is
      // the same as that requested.  This check is done for the bootstrap
      // loader when parsing the class file.
      if (class_name == k->name()) {
        return k;
      }
    }
    // Class is not found or has the wrong name, return NULL
    return NULL;
  }
}

static void post_class_define_event(InstanceKlass* k, const ClassLoaderData* def_cld) {
  EventClassDefine event;
  if (event.should_commit()) {
    event.set_definedClass(k);
    event.set_definingClassLoader(def_cld);
    event.commit();
  }
}

void SystemDictionary::define_instance_class(InstanceKlass* k, TRAPS) {

  HandleMark hm(THREAD);
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
  Dictionary* dictionary = loader_data->dictionary();
  unsigned int d_hash = dictionary->compute_hash(name_h);
  check_constraints(d_hash, k, class_loader_h, true, CHECK);

  // Register class just loaded with class loader (placed in ArrayList)
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
    unsigned int p_hash = placeholders()->compute_hash(name_h);
    int p_index = placeholders()->hash_to_index(p_hash);

    MutexLocker mu_r(THREAD, Compile_lock);

    // Add to class hierarchy, initialize vtables, and do possible
    // deoptimizations.
    add_to_hierarchy(k, CHECK); // No exception, but can block

    // Add to systemDictionary - so other classes can see it.
    // Grabs and releases SystemDictionary_lock
    update_dictionary(d_hash, p_index, p_hash,
                      k, class_loader_h, THREAD);
  }
  k->eager_initialize(THREAD);

  // notify jvmti
  if (JvmtiExport::should_post_class_load()) {
      assert(THREAD->is_Java_thread(), "thread->is_Java_thread()");
      JvmtiExport::post_class_load((JavaThread *) THREAD, k);

  }
  post_class_define_event(k, loader_data);
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
// Be careful when modifying this code: once you have run
// placeholders()->find_and_add(PlaceholderTable::DEFINE_CLASS),
// you need to find_and_remove it before returning.
// So be careful to not exit with a CHECK_ macro betweeen these calls.
InstanceKlass* SystemDictionary::find_or_define_instance_class(Symbol* class_name, Handle class_loader,
                                                               InstanceKlass* k, TRAPS) {

  Symbol*  name_h = k->name(); // passed in class_name may be null
  ClassLoaderData* loader_data = class_loader_data(class_loader);
  Dictionary* dictionary = loader_data->dictionary();

  unsigned int d_hash = dictionary->compute_hash(name_h);

  // Hold SD lock around find_class and placeholder creation for DEFINE_CLASS
  unsigned int p_hash = placeholders()->compute_hash(name_h);
  int p_index = placeholders()->hash_to_index(p_hash);
  PlaceholderEntry* probe;

  {
    MutexLocker mu(THREAD, SystemDictionary_lock);
    // First check if class already defined
    if (is_parallelDefine(class_loader)) {
      InstanceKlass* check = find_class(d_hash, name_h, dictionary);
      if (check != NULL) {
        return check;
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
    if (is_parallelDefine(class_loader) && (probe->instance_klass() != NULL)) {
        placeholders()->find_and_remove(p_index, p_hash, name_h, loader_data, PlaceholderTable::DEFINE_CLASS, THREAD);
        SystemDictionary_lock->notify_all();
#ifdef ASSERT
        InstanceKlass* check = find_class(d_hash, name_h, dictionary);
        assert(check != NULL, "definer missed recording success");
#endif
        return probe->instance_klass();
    } else {
      // This thread will define the class (even if earlier thread tried and had an error)
      probe->set_definer(THREAD);
    }
  }

  define_instance_class(k, THREAD);

  Handle linkage_exception = Handle(); // null handle

  // definer must notify any waiting threads
  {
    MutexLocker mu(THREAD, SystemDictionary_lock);
    PlaceholderEntry* probe = placeholders()->get_entry(p_index, p_hash, name_h, loader_data);
    assert(probe != NULL, "DEFINE_CLASS placeholder lost?");
    if (probe != NULL) {
      if (HAS_PENDING_EXCEPTION) {
        linkage_exception = Handle(THREAD,PENDING_EXCEPTION);
        CLEAR_PENDING_EXCEPTION;
      } else {
        probe->set_instance_klass(k);
      }
      probe->set_definer(NULL);
      placeholders()->find_and_remove(p_index, p_hash, name_h, loader_data, PlaceholderTable::DEFINE_CLASS, THREAD);
      SystemDictionary_lock->notify_all();
    }
  }

  // Can't throw exception while holding lock due to rank ordering
  if (linkage_exception() != NULL) {
    THROW_OOP_(linkage_exception(), NULL); // throws exception and returns
  }

  return k;
}

Handle SystemDictionary::compute_loader_lock_object(Handle class_loader, TRAPS) {
  // If class_loader is NULL we synchronize on _system_loader_lock_obj
  if (class_loader.is_null()) {
    return Handle(THREAD, _system_loader_lock_obj.resolve());
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
    if (loader_lock() == _system_loader_lock_obj.resolve()) {
      ClassLoader::sync_systemLoaderLockContentionRate()->inc();
    } else {
      ClassLoader::sync_nonSystemLoaderLockContentionRate()->inc();
    }
  }
}

// ----------------------------------------------------------------------------
// Lookup

InstanceKlass* SystemDictionary::find_class(unsigned int hash,
                                            Symbol* class_name,
                                            Dictionary* dictionary) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  int index = dictionary->hash_to_index(hash);
  return dictionary->find_class(index, hash, class_name);
}


// Basic find on classes in the midst of being loaded
Symbol* SystemDictionary::find_placeholder(Symbol* class_name,
                                           ClassLoaderData* loader_data) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  unsigned int p_hash = placeholders()->compute_hash(class_name);
  int p_index = placeholders()->hash_to_index(p_hash);
  return placeholders()->find_entry(p_index, p_hash, class_name, loader_data);
}


// Used for assertions and verification only
// Precalculating the hash and index is an optimization because there are many lookups
// before adding the class.
InstanceKlass* SystemDictionary::find_class(Symbol* class_name, ClassLoaderData* loader_data) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  #ifndef ASSERT
  guarantee(VerifyBeforeGC      ||
            VerifyDuringGC      ||
            VerifyBeforeExit    ||
            VerifyDuringStartup ||
            VerifyAfterGC, "too expensive");
  #endif

  Dictionary* dictionary = loader_data->dictionary();
  unsigned int d_hash = dictionary->compute_hash(class_name);
  return find_class(d_hash, class_name, dictionary);
}


// ----------------------------------------------------------------------------
// Update hierachy. This is done before the new klass has been added to the SystemDictionary. The Recompile_lock
// is held, to ensure that the compiler is not using the class hierachy, and that deoptimization will kick in
// before a new class is used.

void SystemDictionary::add_to_hierarchy(InstanceKlass* k, TRAPS) {
  assert(k != NULL, "just checking");
  if (Universe::is_fully_initialized()) {
    assert_locked_or_safepoint(Compile_lock);
  }

  k->set_init_state(InstanceKlass::loaded);
  // make sure init_state store is already done.
  // The compiler reads the hierarchy outside of the Compile_lock.
  // Access ordering is used to add to hierarchy.

  // Link into hierachy.
  k->append_to_sibling_list();                    // add to superklass/sibling list
  k->process_interfaces(THREAD);                  // handle all "implements" declarations

  // Now flush all code that depended on old class hierarchy.
  // Note: must be done *after* linking k into the hierarchy (was bug 12/9/97)
  if (Universe::is_fully_initialized()) {
    CodeCache::flush_dependents_on(k);
  }
}

// ----------------------------------------------------------------------------
// GC support

// Assumes classes in the SystemDictionary are only unloaded at a safepoint
// Note: anonymous classes are not in the SD.
bool SystemDictionary::do_unloading(GCTimer* gc_timer) {

  bool unloading_occurred;
  bool is_concurrent = !SafepointSynchronize::is_at_safepoint();
  {
    GCTraceTime(Debug, gc, phases) t("ClassLoaderData", gc_timer);
    assert_locked_or_safepoint(ClassLoaderDataGraph_lock);  // caller locks.
    // First, mark for unload all ClassLoaderData referencing a dead class loader.
    unloading_occurred = ClassLoaderDataGraph::do_unloading();
    if (unloading_occurred) {
      MutexLocker ml2(is_concurrent ? Module_lock : NULL);
      JFR_ONLY(Jfr::on_unloading_classes();)

      MutexLocker ml1(is_concurrent ? SystemDictionary_lock : NULL);
      ClassLoaderDataGraph::clean_module_and_package_info();
      constraints()->purge_loader_constraints();
      resolution_errors()->purge_resolution_errors();
    }
  }

  GCTraceTime(Debug, gc, phases) t("Trigger cleanups", gc_timer);

  if (unloading_occurred) {
    SymbolTable::trigger_cleanup();

    // Oops referenced by the protection domain cache table may get unreachable independently
    // of the class loader (eg. cached protection domain oops). So we need to
    // explicitly unlink them here.
    // All protection domain oops are linked to the caller class, so if nothing
    // unloads, this is not needed.
    _pd_cache_table->trigger_cleanup();
  }

  return unloading_occurred;
}

// CDS: scan and relocate all classes referenced by _well_known_klasses[].
void SystemDictionary::well_known_klasses_do(MetaspaceClosure* it) {
  for (int id = FIRST_WKID; id < WKID_LIMIT; id++) {
    it->push(well_known_klass_addr((WKID)id));
  }
}

void SystemDictionary::methods_do(void f(Method*)) {
  // Walk methods in loaded classes
  MutexLocker ml(ClassLoaderDataGraph_lock);
  ClassLoaderDataGraph::methods_do(f);
  // Walk method handle intrinsics
  invoke_method_table()->methods_do(f);
}

// ----------------------------------------------------------------------------
// Initialization

void SystemDictionary::initialize(TRAPS) {
  // Allocate arrays
  _placeholders        = new PlaceholderTable(_placeholder_table_size);
  _loader_constraints  = new LoaderConstraintTable(_loader_constraint_size);
  _resolution_errors   = new ResolutionErrorTable(_resolution_error_size);
  _invoke_method_table = new SymbolPropertyTable(_invoke_method_size);
  _pd_cache_table = new ProtectionDomainCacheTable(defaultProtectionDomainCacheSize);

  // Allocate private object used as system class loader lock
  oop lock_obj = oopFactory::new_intArray(0, CHECK);
  _system_loader_lock_obj = OopHandle::create(lock_obj);

  // Initialize basic classes
  resolve_well_known_classes(CHECK);
}

// Compact table of directions on the initialization of klasses:
static const short wk_init_info[] = {
  #define WK_KLASS_INIT_INFO(name, symbol) \
    ((short)vmSymbols::VM_SYMBOL_ENUM_NAME(symbol)),

  WK_KLASSES_DO(WK_KLASS_INIT_INFO)
  #undef WK_KLASS_INIT_INFO
  0
};

#ifdef ASSERT
bool SystemDictionary::is_well_known_klass(Symbol* class_name) {
  int sid;
  for (int i = 0; (sid = wk_init_info[i]) != 0; i++) {
    Symbol* symbol = vmSymbols::symbol_at((vmSymbols::SID)sid);
    if (class_name == symbol) {
      return true;
    }
  }
  return false;
}
#endif

bool SystemDictionary::resolve_wk_klass(WKID id, TRAPS) {
  assert(id >= (int)FIRST_WKID && id < (int)WKID_LIMIT, "oob");
  int sid = wk_init_info[id - FIRST_WKID];
  Symbol* symbol = vmSymbols::symbol_at((vmSymbols::SID)sid);
  InstanceKlass** klassp = &_well_known_klasses[id];

#if INCLUDE_CDS
  if (UseSharedSpaces && !JvmtiExport::should_post_class_prepare()) {
    InstanceKlass* k = *klassp;
    assert(k->is_shared_boot_class(), "must be");

    ClassLoaderData* loader_data = ClassLoaderData::the_null_class_loader_data();
    quick_resolve(k, loader_data, Handle(), CHECK_false);
    return true;
  }
#endif // INCLUDE_CDS

  if (!is_wk_klass_loaded(*klassp)) {
    Klass* k = resolve_or_fail(symbol, true, CHECK_false);
    (*klassp) = InstanceKlass::cast(k);
  }
  return ((*klassp) != NULL);
}

void SystemDictionary::resolve_wk_klasses_until(WKID limit_id, WKID &start_id, TRAPS) {
  assert((int)start_id <= (int)limit_id, "IDs are out of order!");
  for (int id = (int)start_id; id < (int)limit_id; id++) {
    assert(id >= (int)FIRST_WKID && id < (int)WKID_LIMIT, "oob");
    resolve_wk_klass((WKID)id, CHECK);
  }

  // move the starting value forward to the limit:
  start_id = limit_id;
}

void SystemDictionary::resolve_well_known_classes(TRAPS) {
  assert(!Object_klass_loaded(), "well-known classes should only be initialized once");

  // Create the ModuleEntry for java.base.  This call needs to be done here,
  // after vmSymbols::initialize() is called but before any classes are pre-loaded.
  ClassLoader::classLoader_init2(CHECK);

  // Preload commonly used klasses
  WKID scan = FIRST_WKID;
  // first do Object, then String, Class
#if INCLUDE_CDS
  if (UseSharedSpaces) {
    resolve_wk_klasses_through(WK_KLASS_ENUM_NAME(Object_klass), scan, CHECK);

    // It's unsafe to access the archived heap regions before they
    // are fixed up, so we must do the fixup as early as possible
    // before the archived java objects are accessed by functions
    // such as java_lang_Class::restore_archived_mirror and
    // ConstantPool::restore_unshareable_info (restores the archived
    // resolved_references array object).
    //
    // HeapShared::fixup_mapped_heap_regions() fills the empty
    // spaces in the archived heap regions and may use
    // SystemDictionary::Object_klass(), so we can do this only after
    // Object_klass is resolved. See the above resolve_wk_klasses_through()
    // call. No mirror objects are accessed/restored in the above call.
    // Mirrors are restored after java.lang.Class is loaded.
    HeapShared::fixup_mapped_heap_regions();

    // Initialize the constant pool for the Object_class
    assert(Object_klass()->is_shared(), "must be");
    Object_klass()->constants()->restore_unshareable_info(CHECK);
    resolve_wk_klasses_through(WK_KLASS_ENUM_NAME(Class_klass), scan, CHECK);
  } else
#endif
  {
    resolve_wk_klasses_through(WK_KLASS_ENUM_NAME(Class_klass), scan, CHECK);
  }

  assert(WK_KLASS(Object_klass) != NULL, "well-known classes should now be initialized");

  java_lang_Object::register_natives(CHECK);

  // Calculate offsets for String and Class classes since they are loaded and
  // can be used after this point.
  java_lang_String::compute_offsets();
  java_lang_Class::compute_offsets();

  // Fixup mirrors for classes loaded before java.lang.Class.
  Universe::initialize_basic_type_mirrors(CHECK);
  Universe::fixup_mirrors(CHECK);

  // do a bunch more:
  resolve_wk_klasses_through(WK_KLASS_ENUM_NAME(Reference_klass), scan, CHECK);

  // The offsets for jlr.Reference must be computed before
  // InstanceRefKlass::update_nonstatic_oop_maps is called. That function uses
  // the offsets to remove the referent and discovered fields from the oop maps,
  // as they are treated in a special way by the GC. Removing these oops from the
  // oop maps must be done before the usual subclasses of jlr.Reference are loaded.
  java_lang_ref_Reference::compute_offsets();

  // Preload ref klasses and set reference types
  WK_KLASS(Reference_klass)->set_reference_type(REF_OTHER);
  InstanceRefKlass::update_nonstatic_oop_maps(WK_KLASS(Reference_klass));

  resolve_wk_klasses_through(WK_KLASS_ENUM_NAME(PhantomReference_klass), scan, CHECK);
  WK_KLASS(SoftReference_klass)->set_reference_type(REF_SOFT);
  WK_KLASS(WeakReference_klass)->set_reference_type(REF_WEAK);
  WK_KLASS(FinalReference_klass)->set_reference_type(REF_FINAL);
  WK_KLASS(PhantomReference_klass)->set_reference_type(REF_PHANTOM);

  // JSR 292 classes
  WKID jsr292_group_start = WK_KLASS_ENUM_NAME(MethodHandle_klass);
  WKID jsr292_group_end   = WK_KLASS_ENUM_NAME(VolatileCallSite_klass);
  resolve_wk_klasses_until(jsr292_group_start, scan, CHECK);
  resolve_wk_klasses_through(jsr292_group_end, scan, CHECK);
  WKID last = WKID_LIMIT;
  resolve_wk_klasses_until(last, scan, CHECK);

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

#ifdef ASSERT
  if (UseSharedSpaces) {
    JVMTI_ONLY(assert(JvmtiExport::is_early_phase(),
                      "All well known classes must be resolved in JVMTI early phase"));
    for (int i = FIRST_WKID; i < last; i++) {
      InstanceKlass* k = _well_known_klasses[i];
      assert(k->is_shared(), "must not be replaced by JVMTI class file load hook");
    }
  }
#endif
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
// that the dictionary needs to maintain a set of contraints that
// must be satisfied by all classes in the dictionary.
// if defining is true, then LinkageError if already in dictionary
// if initiating loader, then ok if InstanceKlass matches existing entry

void SystemDictionary::check_constraints(unsigned int d_hash,
                                         InstanceKlass* k,
                                         Handle class_loader,
                                         bool defining,
                                         TRAPS) {
  ResourceMark rm(THREAD);
  stringStream ss;
  bool throwException = false;

  {
    Symbol *name = k->name();
    ClassLoaderData *loader_data = class_loader_data(class_loader);

    MutexLocker mu(THREAD, SystemDictionary_lock);

    InstanceKlass* check = find_class(d_hash, name, loader_data->dictionary());
    if (check != NULL) {
      // If different InstanceKlass - duplicate class definition,
      // else - ok, class loaded by a different thread in parallel.
      // We should only have found it if it was done loading and ok to use.
      // The dictionary only holds instance classes, placeholders
      // also hold array classes.

      assert(check->is_instance_klass(), "noninstance in systemdictionary");
      if ((defining == true) || (k != check)) {
        throwException = true;
        ss.print("loader %s", loader_data->loader_name_and_id());
        ss.print(" attempted duplicate %s definition for %s. (%s)",
                 k->external_kind(), k->external_name(), k->class_in_module_of_loader(false, true));
      } else {
        return;
      }
    }

#ifdef ASSERT
    Symbol* ph_check = find_placeholder(name, loader_data);
    assert(ph_check == NULL || ph_check == name, "invalid symbol");
#endif

    if (throwException == false) {
      if (constraints()->check_or_update(k, class_loader, name) == false) {
        throwException = true;
        ss.print("loader constraint violation: loader %s", loader_data->loader_name_and_id());
        ss.print(" wants to load %s %s.",
                 k->external_kind(), k->external_name());
        Klass *existing_klass = constraints()->find_constrained_klass(name, class_loader);
        if (existing_klass != NULL && existing_klass->class_loader() != class_loader()) {
          ss.print(" A different %s with the same name was previously loaded by %s. (%s)",
                   existing_klass->external_kind(),
                   existing_klass->class_loader_data()->loader_name_and_id(),
                   existing_klass->class_in_module_of_loader(false, true));
        } else {
          ss.print(" (%s)", k->class_in_module_of_loader(false, true));
        }
      }
    }
  }

  // Throw error now if needed (cannot throw while holding
  // SystemDictionary_lock because of rank ordering)
  if (throwException == true) {
    THROW_MSG(vmSymbols::java_lang_LinkageError(), ss.as_string());
  }
}

// Update class loader data dictionary - done after check_constraint and add_to_hierachy
// have been called.
void SystemDictionary::update_dictionary(unsigned int d_hash,
                                         int p_index, unsigned int p_hash,
                                         InstanceKlass* k,
                                         Handle class_loader,
                                         TRAPS) {
  // Compile_lock prevents systemDictionary updates during compilations
  assert_locked_or_safepoint(Compile_lock);
  Symbol*  name  = k->name();
  ClassLoaderData *loader_data = class_loader_data(class_loader);

  {
    MutexLocker mu1(THREAD, SystemDictionary_lock);

    // Make a new dictionary entry.
    Dictionary* dictionary = loader_data->dictionary();
    InstanceKlass* sd_check = find_class(d_hash, name, dictionary);
    if (sd_check == NULL) {
      dictionary->add_klass(d_hash, name, k);
    }
  #ifdef ASSERT
    sd_check = find_class(d_hash, name, dictionary);
    assert (sd_check != NULL, "should have entry in dictionary");
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
  if (Signature::is_array(class_name)) {
    // For array classes, their Klass*s are not kept in the
    // constraint table. The element Klass*s are.
    SignatureStream ss(class_name, false);
    int ndims = ss.skip_array_prefix();  // skip all '['s
    BasicType t = ss.type();
    if (t != T_OBJECT) {
      klass = Universe::typeArrayKlassObj(t);
    } else {
      MutexLocker mu(THREAD, SystemDictionary_lock);
      klass = constraints()->find_constrained_klass(ss.as_symbol(), class_loader);
    }
    // If element class already loaded, allocate array klass
    if (klass != NULL) {
      klass = klass->array_klass_or_null(ndims);
    }
  } else {
    MutexLocker mu(THREAD, SystemDictionary_lock);
    // Non-array classes are easy: simply check the constraint table.
    klass = constraints()->find_constrained_klass(class_name, class_loader);
  }

  return klass;
}

bool SystemDictionary::add_loader_constraint(Symbol* class_name,
                                             Klass* klass_being_linked,
                                             Handle class_loader1,
                                             Handle class_loader2,
                                             Thread* THREAD) {
  ClassLoaderData* loader_data1 = class_loader_data(class_loader1);
  ClassLoaderData* loader_data2 = class_loader_data(class_loader2);

  Symbol* constraint_name = NULL;

  if (!Signature::is_array(class_name)) {
    constraint_name = class_name;
  } else {
    // For array classes, their Klass*s are not kept in the
    // constraint table. The element classes are.
    SignatureStream ss(class_name, false);
    ss.skip_array_prefix();  // skip all '['s
    if (!ss.has_envelope()) {
      return true;     // primitive types always pass
    }
    constraint_name = ss.as_symbol();
    // Increment refcount to keep constraint_name alive after
    // SignatureStream is destructed. It will be decremented below
    // before returning.
    constraint_name->increment_refcount();
  }

  Dictionary* dictionary1 = loader_data1->dictionary();
  unsigned int d_hash1 = dictionary1->compute_hash(constraint_name);

  Dictionary* dictionary2 = loader_data2->dictionary();
  unsigned int d_hash2 = dictionary2->compute_hash(constraint_name);

  {
    MutexLocker mu_s(THREAD, SystemDictionary_lock);
    InstanceKlass* klass1 = find_class(d_hash1, constraint_name, dictionary1);
    InstanceKlass* klass2 = find_class(d_hash2, constraint_name, dictionary2);
    bool result = constraints()->add_entry(constraint_name, klass1, class_loader1,
                                           klass2, class_loader2);
#if INCLUDE_CDS
    if (Arguments::is_dumping_archive() && klass_being_linked != NULL &&
        !klass_being_linked->is_shared()) {
         SystemDictionaryShared::record_linking_constraint(constraint_name,
                                     InstanceKlass::cast(klass_being_linked),
                                     class_loader1, class_loader2, THREAD);
    }
#endif // INCLUDE_CDS
    if (Signature::is_array(class_name)) {
      constraint_name->decrement_refcount();
    }
    return result;
  }
}

// Add entry to resolution error table to record the error when the first
// attempt to resolve a reference to a class has failed.
void SystemDictionary::add_resolution_error(const constantPoolHandle& pool, int which,
                                            Symbol* error, Symbol* message) {
  unsigned int hash = resolution_errors()->compute_hash(pool, which);
  int index = resolution_errors()->hash_to_index(hash);
  {
    MutexLocker ml(Thread::current(), SystemDictionary_lock);
    resolution_errors()->add_entry(index, hash, pool, which, error, message);
  }
}

// Delete a resolution error for RedefineClasses for a constant pool is going away
void SystemDictionary::delete_resolution_error(ConstantPool* pool) {
  resolution_errors()->delete_entry(pool);
}

// Lookup resolution error table. Returns error if found, otherwise NULL.
Symbol* SystemDictionary::find_resolution_error(const constantPoolHandle& pool, int which,
                                                Symbol** message) {
  unsigned int hash = resolution_errors()->compute_hash(pool, which);
  int index = resolution_errors()->hash_to_index(hash);
  {
    MutexLocker ml(Thread::current(), SystemDictionary_lock);
    ResolutionErrorEntry* entry = resolution_errors()->find_entry(index, hash, pool, which);
    if (entry != NULL) {
      *message = entry->message();
      return entry->error();
    } else {
      return NULL;
    }
  }
}

// Add an entry to resolution error table to record an error in resolving or
// validating a nest host. This is used to construct informative error
// messages when IllegalAccessError's occur. If an entry already exists it will
// be updated with the nest host error message.
void SystemDictionary::add_nest_host_error(const constantPoolHandle& pool,
                                           int which,
                                           const char* message) {
  unsigned int hash = resolution_errors()->compute_hash(pool, which);
  int index = resolution_errors()->hash_to_index(hash);
  {
    MutexLocker ml(Thread::current(), SystemDictionary_lock);
    ResolutionErrorEntry* entry = resolution_errors()->find_entry(index, hash, pool, which);
    if (entry != NULL) {
      assert(entry->nest_host_error() == NULL, "Nest host error message already set!");
      entry->set_nest_host_error(message);
    } else {
      resolution_errors()->add_entry(index, hash, pool, which, message);
    }
  }
}

// Lookup any nest host error
const char* SystemDictionary::find_nest_host_error(const constantPoolHandle& pool, int which) {
  unsigned int hash = resolution_errors()->compute_hash(pool, which);
  int index = resolution_errors()->hash_to_index(hash);
  {
    MutexLocker ml(Thread::current(), SystemDictionary_lock);
    ResolutionErrorEntry* entry = resolution_errors()->find_entry(index, hash, pool, which);
    if (entry != NULL) {
      return entry->nest_host_error();
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
// SystemDictionary::check_signature_loaders(sig, klass_being_linked, l1, l2)
//
// Make sure all class components (including arrays) in the given
// signature will be resolved to the same class in both loaders.
// Returns the name of the type that failed a loader constraint check, or
// NULL if no constraint failed.  No exception except OOME is thrown.
// Arrays are not added to the loader constraint table, their elements are.
Symbol* SystemDictionary::check_signature_loaders(Symbol* signature,
                                               Klass* klass_being_linked,
                                               Handle loader1, Handle loader2,
                                               bool is_method, TRAPS)  {
  // Nothing to do if loaders are the same.
  if (loader1() == loader2()) {
    return NULL;
  }

  for (SignatureStream ss(signature, is_method); !ss.is_done(); ss.next()) {
    if (ss.is_reference()) {
      Symbol* sig = ss.as_symbol();
      // Note: In the future, if template-like types can take
      // arguments, we will want to recognize them and dig out class
      // names hiding inside the argument lists.
      if (!add_loader_constraint(sig, klass_being_linked, loader1, loader2, THREAD)) {
        return sig;
      }
    }
  }
  return NULL;
}

Method* SystemDictionary::find_method_handle_intrinsic(vmIntrinsics::ID iid,
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
    m = Method::make_method_handle_intrinsic(iid, signature, CHECK_NULL);
    if (!Arguments::is_interpreter_only()) {
      // Generate a compiled form of the MH intrinsic.
      AdapterHandlerLibrary::create_native_wrapper(m);
      // Check if have the compiled code.
      if (!m->has_compiled_code()) {
        THROW_MSG_NULL(vmSymbols::java_lang_VirtualMachineError(),
                       "Out of space in CodeCache for method handle intrinsic");
      }
    }
    // Now grab the lock.  We might have to throw away the new method,
    // if a racing thread has managed to install one at the same time.
    {
      MutexLocker ml(THREAD, SystemDictionary_lock);
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
static Method* unpack_method_and_appendix(Handle mname,
                                          Klass* accessing_klass,
                                          objArrayHandle appendix_box,
                                          Handle* appendix_result,
                                          TRAPS) {
  if (mname.not_null()) {
    Method* m = java_lang_invoke_MemberName::vmtarget(mname());
    if (m != NULL) {
      oop appendix = appendix_box->obj_at(0);
      LogTarget(Info, methodhandles) lt;
      if (lt.develop_is_enabled()) {
        ResourceMark rm(THREAD);
        LogStream ls(lt);
        ls.print("Linked method=" INTPTR_FORMAT ": ", p2i(m));
        m->print_on(&ls);
        if (appendix != NULL) { ls.print("appendix = "); appendix->print_on(&ls); }
        ls.cr();
      }

      (*appendix_result) = Handle(THREAD, appendix);
      // the target is stored in the cpCache and if a reference to this
      // MemberName is dropped we need a way to make sure the
      // class_loader containing this method is kept alive.
      methodHandle mh(THREAD, m); // record_dependency can safepoint.
      ClassLoaderData* this_key = accessing_klass->class_loader_data();
      this_key->record_dependency(m->method_holder());
      return mh();
    }
  }
  THROW_MSG_NULL(vmSymbols::java_lang_LinkageError(), "bad value from MethodHandleNatives");
}

Method* SystemDictionary::find_method_handle_invoker(Klass* klass,
                                                     Symbol* name,
                                                     Symbol* signature,
                                                          Klass* accessing_klass,
                                                          Handle *appendix_result,
                                                          TRAPS) {
  assert(THREAD->can_call_java() ,"");
  Handle method_type =
    SystemDictionary::find_method_handle_type(signature, accessing_klass, CHECK_NULL);

  int ref_kind = JVM_REF_invokeVirtual;
  oop name_oop = StringTable::intern(name, CHECK_NULL);
  Handle name_str (THREAD, name_oop);
  objArrayHandle appendix_box = oopFactory::new_objArray_handle(SystemDictionary::Object_klass(), 1, CHECK_NULL);
  assert(appendix_box->obj_at(0) == NULL, "");

  // This should not happen.  JDK code should take care of that.
  if (accessing_klass == NULL || method_type.is_null()) {
    THROW_MSG_NULL(vmSymbols::java_lang_InternalError(), "bad invokehandle");
  }

  // call java.lang.invoke.MethodHandleNatives::linkMethod(... String, MethodType) -> MemberName
  JavaCallArguments args;
  args.push_oop(Handle(THREAD, accessing_klass->java_mirror()));
  args.push_int(ref_kind);
  args.push_oop(Handle(THREAD, klass->java_mirror()));
  args.push_oop(name_str);
  args.push_oop(method_type);
  args.push_oop(appendix_box);
  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result,
                         SystemDictionary::MethodHandleNatives_klass(),
                         vmSymbols::linkMethod_name(),
                         vmSymbols::linkMethod_signature(),
                         &args, CHECK_NULL);
  Handle mname(THREAD, (oop) result.get_jobject());
  return unpack_method_and_appendix(mname, accessing_klass, appendix_box, appendix_result, THREAD);
}

// Decide if we can globally cache a lookup of this class, to be returned to any client that asks.
// We must ensure that all class loaders everywhere will reach this class, for any client.
// This is a safe bet for public classes in java.lang, such as Object and String.
// We also include public classes in java.lang.invoke, because they appear frequently in system-level method types.
// Out of an abundance of caution, we do not include any other classes, not even for packages like java.util.
static bool is_always_visible_class(oop mirror) {
  Klass* klass = java_lang_Class::as_Klass(mirror);
  if (klass->is_objArray_klass()) {
    klass = ObjArrayKlass::cast(klass)->bottom_klass(); // check element type
  }
  if (klass->is_typeArray_klass()) {
    return true; // primitive array
  }
  assert(klass->is_instance_klass(), "%s", klass->external_name());
  return klass->is_public() &&
         (InstanceKlass::cast(klass)->is_same_class_package(SystemDictionary::Object_klass()) ||       // java.lang
          InstanceKlass::cast(klass)->is_same_class_package(SystemDictionary::MethodHandle_klass()));  // java.lang.invoke
}

// Find or construct the Java mirror (java.lang.Class instance) for
// the given field type signature, as interpreted relative to the
// given class loader.  Handles primitives, void, references, arrays,
// and all other reflectable types, except method types.
// N.B.  Code in reflection should use this entry point.
Handle SystemDictionary::find_java_mirror_for_type(Symbol* signature,
                                                   Klass* accessing_klass,
                                                   Handle class_loader,
                                                   Handle protection_domain,
                                                   SignatureStream::FailureMode failure_mode,
                                                   TRAPS) {
  assert(accessing_klass == NULL || (class_loader.is_null() && protection_domain.is_null()),
         "one or the other, or perhaps neither");

  // What we have here must be a valid field descriptor,
  // and all valid field descriptors are supported.
  // Produce the same java.lang.Class that reflection reports.
  if (accessing_klass != NULL) {
    class_loader      = Handle(THREAD, accessing_klass->class_loader());
    protection_domain = Handle(THREAD, accessing_klass->protection_domain());
  }
  ResolvingSignatureStream ss(signature, class_loader, protection_domain, false);
  oop mirror_oop = ss.as_java_mirror(failure_mode, CHECK_NH);
  if (mirror_oop == NULL) {
    return Handle();  // report failure this way
  }
  Handle mirror(THREAD, mirror_oop);

  if (accessing_klass != NULL) {
    // Check accessibility, emulating ConstantPool::verify_constant_pool_resolve.
    Klass* sel_klass = java_lang_Class::as_Klass(mirror());
    if (sel_klass != NULL) {
      LinkResolver::check_klass_accessibility(accessing_klass, sel_klass, CHECK_NH);
    }
  }
  return mirror;
}


// Ask Java code to find or construct a java.lang.invoke.MethodType for the given
// signature, as interpreted relative to the given class loader.
// Because of class loader constraints, all method handle usage must be
// consistent with this loader.
Handle SystemDictionary::find_method_handle_type(Symbol* signature,
                                                 Klass* accessing_klass,
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
  if (accessing_klass != NULL) {
    class_loader      = Handle(THREAD, accessing_klass->class_loader());
    protection_domain = Handle(THREAD, accessing_klass->protection_domain());
  }
  bool can_be_cached = true;
  int npts = ArgumentCount(signature).size();
  objArrayHandle pts = oopFactory::new_objArray_handle(SystemDictionary::Class_klass(), npts, CHECK_(empty));
  int arg = 0;
  Handle rt; // the return type from the signature
  ResourceMark rm(THREAD);
  for (SignatureStream ss(signature); !ss.is_done(); ss.next()) {
    oop mirror = NULL;
    if (can_be_cached) {
      // Use neutral class loader to lookup candidate classes to be placed in the cache.
      mirror = ss.as_java_mirror(Handle(), Handle(),
                                 SignatureStream::ReturnNull, CHECK_(empty));
      if (mirror == NULL || (ss.is_reference() && !is_always_visible_class(mirror))) {
        // Fall back to accessing_klass context.
        can_be_cached = false;
      }
    }
    if (!can_be_cached) {
      // Resolve, throwing a real error if it doesn't work.
      mirror = ss.as_java_mirror(class_loader, protection_domain,
                                 SignatureStream::NCDFError, CHECK_(empty));
    }
    assert(mirror != NULL, "%s", ss.as_symbol()->as_C_string());
    if (ss.at_return_type())
      rt = Handle(THREAD, mirror);
    else
      pts->obj_at_put(arg++, mirror);

    // Check accessibility.
    if (!java_lang_Class::is_primitive(mirror) && accessing_klass != NULL) {
      Klass* sel_klass = java_lang_Class::as_Klass(mirror);
      mirror = NULL;  // safety
      // Emulate ConstantPool::verify_constant_pool_resolve.
      LinkResolver::check_klass_accessibility(accessing_klass, sel_klass, CHECK_(empty));
    }
  }
  assert(arg == npts, "");

  // call java.lang.invoke.MethodHandleNatives::findMethodHandleType(Class rt, Class[] pts) -> MethodType
  JavaCallArguments args(Handle(THREAD, rt()));
  args.push_oop(pts);
  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result,
                         SystemDictionary::MethodHandleNatives_klass(),
                         vmSymbols::findMethodHandleType_name(),
                         vmSymbols::findMethodHandleType_signature(),
                         &args, CHECK_(empty));
  Handle method_type(THREAD, (oop) result.get_jobject());

  if (can_be_cached) {
    // We can cache this MethodType inside the JVM.
    MutexLocker ml(THREAD, SystemDictionary_lock);
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

Handle SystemDictionary::find_field_handle_type(Symbol* signature,
                                                Klass* accessing_klass,
                                                TRAPS) {
  Handle empty;
  ResourceMark rm(THREAD);
  SignatureStream ss(signature, /*is_method=*/ false);
  if (!ss.is_done()) {
    Handle class_loader, protection_domain;
    if (accessing_klass != NULL) {
      class_loader      = Handle(THREAD, accessing_klass->class_loader());
      protection_domain = Handle(THREAD, accessing_klass->protection_domain());
    }
    oop mirror = ss.as_java_mirror(class_loader, protection_domain, SignatureStream::NCDFError, CHECK_(empty));
    ss.next();
    if (ss.is_done()) {
      return Handle(THREAD, mirror);
    }
  }
  return empty;
}

// Ask Java code to find or construct a method handle constant.
Handle SystemDictionary::link_method_handle_constant(Klass* caller,
                                                     int ref_kind, //e.g., JVM_REF_invokeVirtual
                                                     Klass* callee,
                                                     Symbol* name,
                                                     Symbol* signature,
                                                     TRAPS) {
  Handle empty;
  if (caller == NULL) {
    THROW_MSG_(vmSymbols::java_lang_InternalError(), "bad MH constant", empty);
  }
  Handle name_str      = java_lang_String::create_from_symbol(name,      CHECK_(empty));
  Handle signature_str = java_lang_String::create_from_symbol(signature, CHECK_(empty));

  // Put symbolic info from the MH constant into freshly created MemberName and resolve it.
  Handle mname = MemberName_klass()->allocate_instance_handle(CHECK_(empty));
  java_lang_invoke_MemberName::set_clazz(mname(), callee->java_mirror());
  java_lang_invoke_MemberName::set_name (mname(), name_str());
  java_lang_invoke_MemberName::set_type (mname(), signature_str());
  java_lang_invoke_MemberName::set_flags(mname(), MethodHandles::ref_kind_to_flags(ref_kind));

  if (ref_kind == JVM_REF_invokeVirtual &&
      MethodHandles::is_signature_polymorphic_public_name(callee, name)) {
    // Skip resolution for public signature polymorphic methods such as
    // j.l.i.MethodHandle.invoke()/invokeExact() and those on VarHandle
    // They require appendix argument which MemberName resolution doesn't handle.
    // There's special logic on JDK side to handle them
    // (see MethodHandles.linkMethodHandleConstant() and MethodHandles.findVirtualForMH()).
  } else {
    MethodHandles::resolve_MemberName(mname, caller, /*speculative_resolve*/false, CHECK_(empty));
  }

  // After method/field resolution succeeded, it's safe to resolve MH signature as well.
  Handle type = MethodHandles::resolve_MemberName_type(mname, caller, CHECK_(empty));

  // call java.lang.invoke.MethodHandleNatives::linkMethodHandleConstant(Class caller, int refKind, Class callee, String name, Object type) -> MethodHandle
  JavaCallArguments args;
  args.push_oop(Handle(THREAD, caller->java_mirror()));  // the referring class
  args.push_int(ref_kind);
  args.push_oop(Handle(THREAD, callee->java_mirror()));  // the target class
  args.push_oop(name_str);
  args.push_oop(type);
  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result,
                         SystemDictionary::MethodHandleNatives_klass(),
                         vmSymbols::linkMethodHandleConstant_name(),
                         vmSymbols::linkMethodHandleConstant_signature(),
                         &args, CHECK_(empty));
  return Handle(THREAD, (oop) result.get_jobject());
}

// Ask Java to run a bootstrap method, in order to create a dynamic call site
// while linking an invokedynamic op, or compute a constant for Dynamic_info CP entry
// with linkage results being stored back into the bootstrap specifier.
void SystemDictionary::invoke_bootstrap_method(BootstrapInfo& bootstrap_specifier, TRAPS) {
  // Resolve the bootstrap specifier, its name, type, and static arguments
  bootstrap_specifier.resolve_bsm(CHECK);

  // This should not happen.  JDK code should take care of that.
  if (bootstrap_specifier.caller() == NULL || bootstrap_specifier.type_arg().is_null()) {
    THROW_MSG(vmSymbols::java_lang_InternalError(), "Invalid bootstrap method invocation with no caller or type argument");
  }

  bool is_indy = bootstrap_specifier.is_method_call();
  objArrayHandle appendix_box;
  if (is_indy) {
    // Some method calls may require an appendix argument.  Arrange to receive it.
    appendix_box = oopFactory::new_objArray_handle(SystemDictionary::Object_klass(), 1, CHECK);
    assert(appendix_box->obj_at(0) == NULL, "");
  }

  // call condy: java.lang.invoke.MethodHandleNatives::linkDynamicConstant(caller, condy_index, bsm, type, info)
  //       indy: java.lang.invoke.MethodHandleNatives::linkCallSite(caller, indy_index, bsm, name, mtype, info, &appendix)
  JavaCallArguments args;
  args.push_oop(Handle(THREAD, bootstrap_specifier.caller_mirror()));
  args.push_int(bootstrap_specifier.bss_index());
  args.push_oop(bootstrap_specifier.bsm());
  args.push_oop(bootstrap_specifier.name_arg());
  args.push_oop(bootstrap_specifier.type_arg());
  args.push_oop(bootstrap_specifier.arg_values());
  if (is_indy) {
    args.push_oop(appendix_box);
  }
  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result,
                         SystemDictionary::MethodHandleNatives_klass(),
                         is_indy ? vmSymbols::linkCallSite_name() : vmSymbols::linkDynamicConstant_name(),
                         is_indy ? vmSymbols::linkCallSite_signature() : vmSymbols::linkDynamicConstant_signature(),
                         &args, CHECK);

  Handle value(THREAD, (oop) result.get_jobject());
  if (is_indy) {
    Handle appendix;
    Method* method = unpack_method_and_appendix(value,
                                                bootstrap_specifier.caller(),
                                                appendix_box,
                                                &appendix, CHECK);
    methodHandle mh(THREAD, method);
    bootstrap_specifier.set_resolved_method(mh, appendix);
  } else {
    bootstrap_specifier.set_resolved_value(value);
  }

  // sanity check
  assert(bootstrap_specifier.is_resolved() ||
         (bootstrap_specifier.is_method_call() &&
          bootstrap_specifier.resolved_method().not_null()), "bootstrap method call failed");
}

// Protection domain cache table handling

ProtectionDomainCacheEntry* SystemDictionary::cache_get(Handle protection_domain) {
  return _pd_cache_table->get(protection_domain);
}

// ----------------------------------------------------------------------------

void SystemDictionary::print_on(outputStream *st) {
  CDS_ONLY(SystemDictionaryShared::print_on(st));
  GCMutexLocker mu(SystemDictionary_lock);

  ClassLoaderDataGraph::print_dictionary(st);

  // Placeholders
  placeholders()->print_on(st);
  st->cr();

  // loader constraints - print under SD_lock
  constraints()->print_on(st);
  st->cr();

  _pd_cache_table->print_on(st);
  st->cr();
}

void SystemDictionary::print() { print_on(tty); }

void SystemDictionary::verify() {
  guarantee(constraints() != NULL,
            "Verify of loader constraints failed");
  guarantee(placeholders()->number_of_entries() >= 0,
            "Verify of placeholders failed");

  GCMutexLocker mu(SystemDictionary_lock);

  // Verify dictionary
  ClassLoaderDataGraph::verify_dictionary();

  placeholders()->verify();

  // Verify constraint table
  guarantee(constraints() != NULL, "Verify of loader constraints failed");
  constraints()->verify(placeholders());

  _pd_cache_table->verify();
}

void SystemDictionary::dump(outputStream *st, bool verbose) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  if (verbose) {
    print_on(st);
  } else {
    CDS_ONLY(SystemDictionaryShared::print_table_statistics(st));
    ClassLoaderDataGraph::print_table_statistics(st);
    placeholders()->print_table_statistics(st, "Placeholder Table");
    constraints()->print_table_statistics(st, "LoaderConstraints Table");
    pd_cache_table()->print_table_statistics(st, "ProtectionDomainCache Table");
  }
}

TableStatistics SystemDictionary::placeholders_statistics() {
  MutexLocker ml(SystemDictionary_lock);
  return placeholders()->statistics_calculate();
}

TableStatistics SystemDictionary::loader_constraints_statistics() {
  MutexLocker ml(SystemDictionary_lock);
  return constraints()->statistics_calculate();
}

TableStatistics SystemDictionary::protection_domain_cache_statistics() {
  MutexLocker ml(SystemDictionary_lock);
  return pd_cache_table()->statistics_calculate();
}

// Utility for dumping dictionaries.
SystemDictionaryDCmd::SystemDictionaryDCmd(outputStream* output, bool heap) :
                                 DCmdWithParser(output, heap),
  _verbose("-verbose", "Dump the content of each dictionary entry for all class loaders",
           "BOOLEAN", false, "false") {
  _dcmdparser.add_dcmd_option(&_verbose);
}

void SystemDictionaryDCmd::execute(DCmdSource source, TRAPS) {
  VM_DumpHashtable dumper(output(), VM_DumpHashtable::DumpSysDict,
                         _verbose.value());
  VMThread::execute(&dumper);
}

int SystemDictionaryDCmd::num_arguments() {
  ResourceMark rm;
  SystemDictionaryDCmd* dcmd = new SystemDictionaryDCmd(NULL, false);
  if (dcmd != NULL) {
    DCmdMark mark(dcmd);
    return dcmd->_dcmdparser.num_arguments();
  } else {
    return 0;
  }
}
