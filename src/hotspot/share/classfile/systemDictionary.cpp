/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.hpp"
#include "classfile/classFileParser.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/classLoaderDataGraph.inline.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/classLoadInfo.hpp"
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
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "interpreter/bootstrapInfo.hpp"
#include "jfr/jfrEvents.hpp"
#include "jvm.h"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/access.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/method.inline.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oop.hpp"
#include "oops/oopHandle.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/symbol.hpp"
#include "oops/typeArrayKlass.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "runtime/synchronizer.hpp"
#include "services/classLoadingService.hpp"
#include "services/diagnosticCommand.hpp"
#include "services/finalizerService.hpp"
#include "services/threadService.hpp"
#include "utilities/macros.hpp"
#include "utilities/utf8.hpp"
#if INCLUDE_CDS
#include "classfile/systemDictionaryShared.hpp"
#endif
#if INCLUDE_JFR
#include "jfr/jfr.hpp"
#endif

class InvokeMethodKey : public StackObj {
  private:
    Symbol* _symbol;
    intptr_t _iid;

  public:
    InvokeMethodKey(Symbol* symbol, intptr_t iid) :
        _symbol(symbol),
        _iid(iid) {}

    static bool key_comparison(InvokeMethodKey const &k1, InvokeMethodKey const &k2){
        return k1._symbol == k2._symbol && k1._iid == k2._iid;
    }

    static unsigned int compute_hash(const InvokeMethodKey &k) {
        Symbol* sym = k._symbol;
        intptr_t iid = k._iid;
        unsigned int hash = (unsigned int) sym -> identity_hash();
        return (unsigned int) (hash ^ iid);
    }

};

using InvokeMethodIntrinsicTable = ResourceHashtable<InvokeMethodKey, Method*, 139, AnyObj::C_HEAP, mtClass,
                  InvokeMethodKey::compute_hash, InvokeMethodKey::key_comparison>;
static InvokeMethodIntrinsicTable* _invoke_method_intrinsic_table;
using InvokeMethodTypeTable = ResourceHashtable<SymbolHandle, OopHandle, 139, AnyObj::C_HEAP, mtClass, SymbolHandle::compute_hash>;
static InvokeMethodTypeTable* _invoke_method_type_table;

OopHandle   SystemDictionary::_java_system_loader;
OopHandle   SystemDictionary::_java_platform_loader;

// ----------------------------------------------------------------------------
// Java-level SystemLoader and PlatformLoader
oop SystemDictionary::java_system_loader() {
  return _java_system_loader.resolve();
}

oop SystemDictionary::java_platform_loader() {
  return _java_platform_loader.resolve();
}

void SystemDictionary::compute_java_loaders(TRAPS) {
  if (_java_system_loader.is_empty()) {
    oop system_loader = get_system_class_loader_impl(CHECK);
    _java_system_loader = OopHandle(Universe::vm_global(), system_loader);
  } else {
    // It must have been restored from the archived module graph
    assert(UseSharedSpaces, "must be");
    assert(CDSConfig::is_loading_full_module_graph(), "must be");
    DEBUG_ONLY(
      oop system_loader = get_system_class_loader_impl(CHECK);
      assert(_java_system_loader.resolve() == system_loader, "must be");
    )
 }

  if (_java_platform_loader.is_empty()) {
    oop platform_loader = get_platform_class_loader_impl(CHECK);
    _java_platform_loader = OopHandle(Universe::vm_global(), platform_loader);
  } else {
    // It must have been restored from the archived module graph
    assert(UseSharedSpaces, "must be");
    assert(CDSConfig::is_loading_full_module_graph(), "must be");
    DEBUG_ONLY(
      oop platform_loader = get_platform_class_loader_impl(CHECK);
      assert(_java_platform_loader.resolve() == platform_loader, "must be");
    )
  }
}

oop SystemDictionary::get_system_class_loader_impl(TRAPS) {
  JavaValue result(T_OBJECT);
  InstanceKlass* class_loader_klass = vmClasses::ClassLoader_klass();
  JavaCalls::call_static(&result,
                         class_loader_klass,
                         vmSymbols::getSystemClassLoader_name(),
                         vmSymbols::void_classloader_signature(),
                         CHECK_NULL);
  return result.get_oop();
}

oop SystemDictionary::get_platform_class_loader_impl(TRAPS) {
  JavaValue result(T_OBJECT);
  InstanceKlass* class_loader_klass = vmClasses::ClassLoader_klass();
  JavaCalls::call_static(&result,
                         class_loader_klass,
                         vmSymbols::getPlatformClassLoader_name(),
                         vmSymbols::void_classloader_signature(),
                         CHECK_NULL);
  return result.get_oop();
}

// Helper function
inline ClassLoaderData* class_loader_data(Handle class_loader) {
  return ClassLoaderData::class_loader_data(class_loader());
}

ClassLoaderData* SystemDictionary::register_loader(Handle class_loader, bool create_mirror_cld) {
  if (create_mirror_cld) {
    // Add a new class loader data to the graph.
    return ClassLoaderDataGraph::add(class_loader, true);
  } else {
    return (class_loader() == nullptr) ? ClassLoaderData::the_null_class_loader_data() :
                                      ClassLoaderDataGraph::find_or_create(class_loader);
  }
}

void SystemDictionary::set_system_loader(ClassLoaderData *cld) {
  assert(_java_system_loader.is_empty(), "already set!");
  _java_system_loader = cld->class_loader_handle();

}

void SystemDictionary::set_platform_loader(ClassLoaderData *cld) {
  assert(_java_platform_loader.is_empty(), "already set!");
  _java_platform_loader = cld->class_loader_handle();
}

// ----------------------------------------------------------------------------
// Parallel class loading check

bool is_parallelCapable(Handle class_loader) {
  if (class_loader.is_null()) return true;
  return java_lang_ClassLoader::parallelCapable(class_loader());
}
// ----------------------------------------------------------------------------
// ParallelDefineClass flag does not apply to bootclass loader
bool is_parallelDefine(Handle class_loader) {
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
  if (class_loader == nullptr) {
    return false;
  }
  return (class_loader->klass() == vmClasses::jdk_internal_loader_ClassLoaders_AppClassLoader_klass() ||
         class_loader == _java_system_loader.peek());
}

// Returns true if the passed class loader is the platform class loader.
bool SystemDictionary::is_platform_class_loader(oop class_loader) {
  if (class_loader == nullptr) {
    return false;
  }
  return (class_loader->klass() == vmClasses::jdk_internal_loader_ClassLoaders_PlatformClassLoader_klass());
}

Handle SystemDictionary::get_loader_lock_or_null(Handle class_loader) {
  // If class_loader is null or parallelCapable, the JVM doesn't acquire a lock while loading.
  if (is_parallelCapable(class_loader)) {
    return Handle();
  } else {
    return class_loader;
  }
}

// ----------------------------------------------------------------------------
// Resolving of classes

Symbol* SystemDictionary::class_name_symbol(const char* name, Symbol* exception, TRAPS) {
  if (name == nullptr) {
    THROW_MSG_0(exception, "No class name given");
  }
  if ((int)strlen(name) > Symbol::max_length()) {
    // It's impossible to create this class;  the name cannot fit
    // into the constant pool.
    Exceptions::fthrow(THREAD_AND_LOCATION, exception,
                       "Class name exceeds maximum length of %d: %s",
                       Symbol::max_length(),
                       name);
    return nullptr;
  }
  // Callers should ensure that the name is never an illegal UTF8 string.
  assert(UTF8::is_legal_utf8((const unsigned char*)name, (int)strlen(name), false),
         "Class name is not a valid utf8 string.");

  // Make a new symbol for the class name.
  return SymbolTable::new_symbol(name);
}

#ifdef ASSERT
// Used to verify that class loading succeeded in adding k to the dictionary.
void verify_dictionary_entry(Symbol* class_name, InstanceKlass* k) {
  MutexLocker mu(SystemDictionary_lock);
  ClassLoaderData* loader_data = k->class_loader_data();
  Dictionary* dictionary = loader_data->dictionary();
  assert(class_name == k->name(), "Must be the same");
  InstanceKlass* kk = dictionary->find_class(JavaThread::current(), class_name);
  assert(kk == k, "should be present in dictionary");
}
#endif

static void handle_resolution_exception(Symbol* class_name, bool throw_error, TRAPS) {
  if (HAS_PENDING_EXCEPTION) {
    // If we have a pending exception we forward it to the caller, unless throw_error is true,
    // in which case we have to check whether the pending exception is a ClassNotFoundException,
    // and convert it to a NoClassDefFoundError and chain the original ClassNotFoundException.
    if (throw_error && PENDING_EXCEPTION->is_a(vmClasses::ClassNotFoundException_klass())) {
      ResourceMark rm(THREAD);
      Handle e(THREAD, PENDING_EXCEPTION);
      CLEAR_PENDING_EXCEPTION;
      THROW_MSG_CAUSE(vmSymbols::java_lang_NoClassDefFoundError(), class_name->as_C_string(), e);
    } else {
      return; // the caller will throw the incoming exception
    }
  }
  // If the class is not found, ie, caller has checked that klass is null, throw the appropriate
  // error or exception depending on the value of throw_error.
  ResourceMark rm(THREAD);
  if (throw_error) {
    THROW_MSG(vmSymbols::java_lang_NoClassDefFoundError(), class_name->as_C_string());
  } else {
    THROW_MSG(vmSymbols::java_lang_ClassNotFoundException(), class_name->as_C_string());
  }
}

// Forwards to resolve_or_null

Klass* SystemDictionary::resolve_or_fail(Symbol* class_name, Handle class_loader, Handle protection_domain,
                                         bool throw_error, TRAPS) {
  Klass* klass = resolve_or_null(class_name, class_loader, protection_domain, THREAD);
  // Check for pending exception or null klass, and throw exception
  if (HAS_PENDING_EXCEPTION || klass == nullptr) {
    handle_resolution_exception(class_name, throw_error, CHECK_NULL);
  }
  return klass;
}

// Forwards to resolve_array_class_or_null or resolve_instance_class_or_null

Klass* SystemDictionary::resolve_or_null(Symbol* class_name, Handle class_loader, Handle protection_domain, TRAPS) {
  if (Signature::is_array(class_name)) {
    return resolve_array_class_or_null(class_name, class_loader, protection_domain, THREAD);
  } else {
    assert(class_name != nullptr && !Signature::is_array(class_name), "must be");
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
  Klass* k = nullptr;
  BasicType t = ss.type();
  if (ss.has_envelope()) {
    Symbol* obj_class = ss.as_symbol();
    k = SystemDictionary::resolve_instance_class_or_null(obj_class,
                                                         class_loader,
                                                         protection_domain,
                                                         CHECK_NULL);
    if (k != nullptr) {
      k = k->array_klass(ndims, CHECK_NULL);
    }
  } else {
    k = Universe::typeArrayKlassObj(t);
    k = TypeArrayKlass::cast(k)->array_klass(ndims, CHECK_NULL);
  }
  return k;
}

static inline void log_circularity_error(Symbol* name, PlaceholderEntry* probe) {
  LogTarget(Debug, class, load, placeholders) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    ls.print("ClassCircularityError detected for placeholder entry %s", name->as_C_string());
    probe->print_on(&ls);
    ls.cr();
  }
}

// Must be called for any superclass or superinterface resolution
// during class definition to allow class circularity checking
// superinterface callers:
//    parse_interfaces - from defineClass
// superclass callers:
//   ClassFileParser - from defineClass
//   load_shared_class - while loading a class from shared archive
//   resolve_instance_class_or_null:
//     via: handle_parallel_super_load
//      when resolving a class that has an existing placeholder with
//      a saved superclass [i.e. a defineClass is currently in progress]
//      If another thread is trying to resolve the class, it must do
//      superclass checks on its own thread to catch class circularity and
//      to avoid deadlock.
//
// resolve_super_or_fail adds a LOAD_SUPER placeholder to the placeholder table before calling
// resolve_instance_class_or_null. ClassCircularityError is detected when a LOAD_SUPER or LOAD_INSTANCE
// placeholder for the same thread, class, classloader is found.
// This can be seen with logging option: -Xlog:class+load+placeholders=debug.
//
InstanceKlass* SystemDictionary::resolve_super_or_fail(Symbol* class_name,
                                                       Symbol* super_name,
                                                       Handle class_loader,
                                                       Handle protection_domain,
                                                       bool is_superclass,
                                                       TRAPS) {

  assert(super_name != nullptr, "null superclass for resolving");
  assert(!Signature::is_array(super_name), "invalid superclass name");
#if INCLUDE_CDS
  if (CDSConfig::is_dumping_static_archive()) {
    // Special processing for handling UNREGISTERED shared classes.
    InstanceKlass* k = SystemDictionaryShared::lookup_super_for_unregistered_class(class_name,
                           super_name, is_superclass);
    if (k) {
      return k;
    }
  }
#endif // INCLUDE_CDS

  // If klass is already loaded, just return the superclass or superinterface.
  // Make sure there's a placeholder for the class_name before resolving.
  // This is used as a claim that this thread is currently loading superclass/classloader
  // and for ClassCircularity checks.

  ClassLoaderData* loader_data = class_loader_data(class_loader);
  Dictionary* dictionary = loader_data->dictionary();

  // can't throw error holding a lock
  bool throw_circularity_error = false;
  {
    MutexLocker mu(THREAD, SystemDictionary_lock);
    InstanceKlass* klassk = dictionary->find_class(THREAD, class_name);
    InstanceKlass* quicksuperk;
    // To support parallel loading: if class is done loading, just return the superclass
    // if the super_name matches class->super()->name() and if the class loaders match.
    // Otherwise, a LinkageError will be thrown later.
    if (klassk != nullptr && is_superclass &&
        ((quicksuperk = klassk->java_super()) != nullptr) &&
         ((quicksuperk->name() == super_name) &&
            (quicksuperk->class_loader() == class_loader()))) {
           return quicksuperk;
    } else {
      // Must check ClassCircularity before checking if superclass is already loaded.
      PlaceholderEntry* probe = PlaceholderTable::get_entry(class_name, loader_data);
      if (probe && probe->check_seen_thread(THREAD, PlaceholderTable::LOAD_SUPER)) {
          log_circularity_error(class_name, probe);
          throw_circularity_error = true;
      }
    }

    if (!throw_circularity_error) {
      // Be careful not to exit resolve_super without removing this placeholder.
      PlaceholderEntry* newprobe = PlaceholderTable::find_and_add(class_name,
                                                                  loader_data,
                                                                  PlaceholderTable::LOAD_SUPER,
                                                                  super_name, THREAD);
    }
  }

  if (throw_circularity_error) {
      ResourceMark rm(THREAD);
      THROW_MSG_NULL(vmSymbols::java_lang_ClassCircularityError(), class_name->as_C_string());
  }

  // Resolve the superclass or superinterface, check results on return
  InstanceKlass* superk =
    SystemDictionary::resolve_instance_class_or_null(super_name,
                                                     class_loader,
                                                     protection_domain,
                                                     THREAD);

  // Clean up placeholder entry.
  {
    MutexLocker mu(THREAD, SystemDictionary_lock);
    PlaceholderTable::find_and_remove(class_name, loader_data, PlaceholderTable::LOAD_SUPER, THREAD);
    SystemDictionary_lock->notify_all();
  }

  // Check for pending exception or null superk, and throw exception
  if (HAS_PENDING_EXCEPTION || superk == nullptr) {
    handle_resolution_exception(super_name, true, CHECK_NULL);
  }

  return superk;
}

// If the class in is in the placeholder table, class loading is in progress.
// For cases where the application changes threads to load classes, it
// is critical to ClassCircularity detection that we try loading
// the superclass on the new thread internally, so we do parallel
// superclass loading here.  This avoids deadlock for ClassCircularity
// detection for parallelCapable class loaders that lock on a per-class lock.
static void handle_parallel_super_load(Symbol* name,
                                       Symbol* superclassname,
                                       Handle class_loader,
                                       Handle protection_domain, TRAPS) {

  // superk is not used; resolve_super_or_fail is called for circularity check only.
  Klass* superk = SystemDictionary::resolve_super_or_fail(name,
                                                          superclassname,
                                                          class_loader,
                                                          protection_domain,
                                                          true,
                                                          CHECK);
}

// Bootstrap and non-parallel capable class loaders use the LOAD_INSTANCE placeholder to
// wait for parallel class loading and/or to check for circularity error for Xcomp when loading.
static bool needs_load_placeholder(Handle class_loader) {
  return class_loader.is_null() || !is_parallelCapable(class_loader);
}

// Check for other threads loading this class either to throw CCE or wait in the case of the boot loader.
static InstanceKlass* handle_parallel_loading(JavaThread* current,
                                              Symbol* name,
                                              ClassLoaderData* loader_data,
                                              bool must_wait_for_class_loading,
                                              bool* throw_circularity_error) {
  PlaceholderEntry* oldprobe = PlaceholderTable::get_entry(name, loader_data);
  if (oldprobe != nullptr) {
    // -Xcomp calls load_signature_classes which might result in loading
    // a class that's already in the process of loading, so we detect CCE here also.
    // Only need check_seen_thread once, not on each loop
    if (oldprobe->check_seen_thread(current, PlaceholderTable::LOAD_INSTANCE)) {
      log_circularity_error(name, oldprobe);
      *throw_circularity_error = true;
      return nullptr;
    } else if (must_wait_for_class_loading) {
      // Wait until the first thread has finished loading this class. Also wait until all the
      // threads trying to load its superclass have removed their placeholders.
      while (oldprobe != nullptr &&
             (oldprobe->instance_load_in_progress() || oldprobe->super_load_in_progress())) {

        // LOAD_INSTANCE placeholders are used to implement parallel capable class loading
        // for the bootclass loader.
        SystemDictionary_lock->wait();

        // Check if classloading completed while we were waiting
        InstanceKlass* check = loader_data->dictionary()->find_class(current, name);
        if (check != nullptr) {
          // Klass is already loaded, so just return it
          return check;
        }
        // check if other thread failed to load and cleaned up
        oldprobe = PlaceholderTable::get_entry(name, loader_data);
      }
    }
  }
  return nullptr;
}

void SystemDictionary::post_class_load_event(EventClassLoad* event, const InstanceKlass* k, const ClassLoaderData* init_cld) {
  assert(event != nullptr, "invariant");
  assert(k != nullptr, "invariant");
  event->set_loadedClass(k);
  event->set_definingClassLoader(k->class_loader_data());
  event->set_initiatingClassLoader(init_cld);
  event->commit();
}

// SystemDictionary::resolve_instance_class_or_null is the main function for class name resolution.
// After checking if the InstanceKlass already exists, it checks for ClassCircularityError and
// whether the thread must wait for loading in parallel.  It eventually calls load_instance_class,
// which will load the class via the bootstrap loader or call ClassLoader.loadClass().
// This can return null, an exception or an InstanceKlass.
InstanceKlass* SystemDictionary::resolve_instance_class_or_null(Symbol* name,
                                                                Handle class_loader,
                                                                Handle protection_domain,
                                                                TRAPS) {
  // name must be in the form of "java/lang/Object" -- cannot be "Ljava/lang/Object;"
  assert(name != nullptr && !Signature::is_array(name) &&
         !Signature::has_envelope(name), "invalid class name");

  EventClassLoad class_load_start_event;

  HandleMark hm(THREAD);

  // Fix for 4474172; see evaluation for more details
  class_loader = Handle(THREAD, java_lang_ClassLoader::non_reflection_class_loader(class_loader()));
  ClassLoaderData* loader_data = register_loader(class_loader);
  Dictionary* dictionary = loader_data->dictionary();

  // Do lookup to see if class already exists and the protection domain
  // has the right access.
  // This call uses find which checks protection domain already matches
  // All subsequent calls use find_class, and set loaded_class so that
  // before we return a result, we call out to java to check for valid protection domain.
  InstanceKlass* probe = dictionary->find(THREAD, name, protection_domain);
  if (probe != nullptr) return probe;

  // Non-bootstrap class loaders will call out to class loader and
  // define via jvm/jni_DefineClass which will acquire the
  // class loader object lock to protect against multiple threads
  // defining the class in parallel by accident.
  // This lock must be acquired here so the waiter will find
  // any successful result in the SystemDictionary and not attempt
  // the define.
  // ParallelCapable class loaders and the bootstrap classloader
  // do not acquire lock here.
  Handle lockObject = get_loader_lock_or_null(class_loader);
  ObjectLocker ol(lockObject, THREAD);

  bool super_load_in_progress  = false;
  InstanceKlass* loaded_class = nullptr;
  SymbolHandle superclassname; // Keep alive while loading in parallel thread.

  guarantee(THREAD->can_call_java(),
         "can not load classes with compiler thread: class=%s, classloader=%s",
         name->as_C_string(),
         class_loader.is_null() ? "null" : class_loader->klass()->name()->as_C_string());

  // Check again (after locking) if the class already exists in SystemDictionary
  {
    MutexLocker mu(THREAD, SystemDictionary_lock);
    InstanceKlass* check = dictionary->find_class(THREAD, name);
    if (check != nullptr) {
      // InstanceKlass is already loaded, but we still need to check protection domain below.
      loaded_class = check;
    } else {
      PlaceholderEntry* placeholder = PlaceholderTable::get_entry(name, loader_data);
      if (placeholder != nullptr && placeholder->super_load_in_progress()) {
         super_load_in_progress = true;
         superclassname = placeholder->supername();
         assert(superclassname != nullptr, "superclass has to have a name");
      }
    }
  }

  // If the class is in the placeholder table with super_class set,
  // handle superclass loading in progress.
  if (super_load_in_progress) {
    handle_parallel_super_load(name, superclassname,
                               class_loader,
                               protection_domain,
                               CHECK_NULL);
  }

  bool throw_circularity_error = false;
  if (loaded_class == nullptr) {
    bool load_placeholder_added = false;

    // Add placeholder entry to record loading instance class
    // case 1. Bootstrap classloader
    //    This classloader supports parallelism at the classloader level
    //    but only allows a single thread to load a class/classloader pair.
    //    The LOAD_INSTANCE placeholder is the mechanism for mutual exclusion.
    // case 2. parallelCapable user level classloaders
    //    These class loaders lock a per-class object lock when ClassLoader.loadClass()
    //    is called. A LOAD_INSTANCE placeholder isn't used for mutual exclusion.
    // case 3. traditional classloaders that rely on the classloader object lock
    //    There should be no need for need for LOAD_INSTANCE for mutual exclusion,
    //    except the LOAD_INSTANCE placeholder is used to detect CCE for -Xcomp.
    //    TODO: should also be used to detect CCE for parallel capable class loaders but it's not.
    {
      MutexLocker mu(THREAD, SystemDictionary_lock);
      if (needs_load_placeholder(class_loader)) {
        loaded_class = handle_parallel_loading(THREAD,
                                               name,
                                               loader_data,
                                               class_loader.is_null(),
                                               &throw_circularity_error);
      }

      // Recheck if the class has been loaded for all class loader cases and
      // add a LOAD_INSTANCE placeholder while holding the SystemDictionary_lock.
      if (!throw_circularity_error && loaded_class == nullptr) {
        InstanceKlass* check = dictionary->find_class(THREAD, name);
        if (check != nullptr) {
          loaded_class = check;
        } else if (needs_load_placeholder(class_loader)) {
          // Add the LOAD_INSTANCE token. Threads will wait on loading to complete for this thread.
          PlaceholderEntry* newprobe = PlaceholderTable::find_and_add(name, loader_data,
                                                                      PlaceholderTable::LOAD_INSTANCE,
                                                                      nullptr,
                                                                      THREAD);
          load_placeholder_added = true;
        }
      }
    }

    // Must throw error outside of owning lock
    if (throw_circularity_error) {
      assert(!HAS_PENDING_EXCEPTION && !load_placeholder_added, "circularity error cleanup");
      ResourceMark rm(THREAD);
      THROW_MSG_NULL(vmSymbols::java_lang_ClassCircularityError(), name->as_C_string());
    }

    // Be careful when modifying this code: once you have run
    // PlaceholderTable::find_and_add(PlaceholderTable::LOAD_INSTANCE),
    // you need to find_and_remove it before returning.
    // So be careful to not exit with a CHECK_ macro between these calls.

    if (loaded_class == nullptr) {
      // Do actual loading
      loaded_class = load_instance_class(name, class_loader, THREAD);
    }

    if (load_placeholder_added) {
      // clean up placeholder entries for LOAD_INSTANCE success or error
      // This brackets the SystemDictionary updates for both defining
      // and initiating loaders
      MutexLocker mu(THREAD, SystemDictionary_lock);
      PlaceholderTable::find_and_remove(name, loader_data, PlaceholderTable::LOAD_INSTANCE, THREAD);
      SystemDictionary_lock->notify_all();
    }
  }

  if (HAS_PENDING_EXCEPTION || loaded_class == nullptr) {
    return nullptr;
  }

  if (class_load_start_event.should_commit()) {
    post_class_load_event(&class_load_start_event, loaded_class, loader_data);
  }

  // Make sure we have the right class in the dictionary
  DEBUG_ONLY(verify_dictionary_entry(name, loaded_class));

  // Check if the protection domain is present it has the right access
  if (protection_domain() != nullptr) {
    // Verify protection domain. If it fails an exception is thrown
    dictionary->validate_protection_domain(loaded_class, class_loader, protection_domain, CHECK_NULL);
  }

  return loaded_class;
}


// This routine does not lock the system dictionary.
//
// Since readers don't hold a lock, we must make sure that system
// dictionary entries are added to in a safe way (all links must
// be updated in an MT-safe manner). All entries are removed during class
// unloading, when this class loader is no longer referenced.
//
// Callers should be aware that an entry could be added just after
// Dictionary is read here, so the caller will not see
// the new entry.

InstanceKlass* SystemDictionary::find_instance_klass(Thread* current,
                                                     Symbol* class_name,
                                                     Handle class_loader,
                                                     Handle protection_domain) {

  // The result of this call should be consistent with the result
  // of the call to resolve_instance_class_or_null().
  // See evaluation 6790209 and 4474172 for more details.
  oop class_loader_oop = java_lang_ClassLoader::non_reflection_class_loader(class_loader());
  ClassLoaderData* loader_data = ClassLoaderData::class_loader_data_or_null(class_loader_oop);

  if (loader_data == nullptr) {
    // If the ClassLoaderData has not been setup,
    // then the class loader has no entries in the dictionary.
    return nullptr;
  }

  Dictionary* dictionary = loader_data->dictionary();
  return dictionary->find(current, class_name, protection_domain);
}

// Look for a loaded instance or array klass by name.  Do not do any loading.
// return null in case of error.
Klass* SystemDictionary::find_instance_or_array_klass(Thread* current,
                                                      Symbol* class_name,
                                                      Handle class_loader,
                                                      Handle protection_domain) {
  Klass* k = nullptr;
  assert(class_name != nullptr, "class name must be non nullptr");

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
      k = SystemDictionary::find_instance_klass(current, ss.as_symbol(), class_loader, protection_domain);
    }
    if (k != nullptr) {
      k = k->array_klass_or_null(ndims);
    }
  } else {
    k = find_instance_klass(current, class_name, class_loader, protection_domain);
  }
  return k;
}

// Note: this method is much like resolve_class_from_stream, but
// does not publish the classes in the SystemDictionary.
// Handles Lookup.defineClass hidden.
InstanceKlass* SystemDictionary::resolve_hidden_class_from_stream(
                                                     ClassFileStream* st,
                                                     Symbol* class_name,
                                                     Handle class_loader,
                                                     const ClassLoadInfo& cl_info,
                                                     TRAPS) {

  EventClassLoad class_load_start_event;
  ClassLoaderData* loader_data;

  // - for hidden classes that are not strong: create a new CLD that has a class holder and
  //                                           whose loader is the Lookup class's loader.
  // - for hidden class: add the class to the Lookup class's loader's CLD.
  assert (cl_info.is_hidden(), "only used for hidden classes");
  bool create_mirror_cld = !cl_info.is_strong_hidden();
  loader_data = register_loader(class_loader, create_mirror_cld);

  assert(st != nullptr, "invariant");
  assert(st->need_verify(), "invariant");

  // Parse stream and create a klass.
  InstanceKlass* k = KlassFactory::create_from_stream(st,
                                                      class_name,
                                                      loader_data,
                                                      cl_info,
                                                      CHECK_NULL);
  assert(k != nullptr, "no klass created");

  // Hidden classes that are not strong must update ClassLoaderData holder
  // so that they can be unloaded when the mirror is no longer referenced.
  if (!cl_info.is_strong_hidden()) {
    k->class_loader_data()->initialize_holder(Handle(THREAD, k->java_mirror()));
  }

  // Add to class hierarchy, and do possible deoptimizations.
  k->add_to_hierarchy(THREAD);
  // But, do not add to dictionary.

  k->link_class(CHECK_NULL);

  // notify jvmti
  if (JvmtiExport::should_post_class_load()) {
    JvmtiExport::post_class_load(THREAD, k);
  }
  if (class_load_start_event.should_commit()) {
    post_class_load_event(&class_load_start_event, k, loader_data);
  }

  return k;
}

// Add a klass to the system from a stream (called by jni_DefineClass and
// JVM_DefineClass).
// Note: class_name can be null. In that case we do not know the name of
// the class until we have parsed the stream.
// This function either returns an InstanceKlass or throws an exception.  It does
// not return null without a pending exception.
InstanceKlass* SystemDictionary::resolve_class_from_stream(
                                                     ClassFileStream* st,
                                                     Symbol* class_name,
                                                     Handle class_loader,
                                                     const ClassLoadInfo& cl_info,
                                                     TRAPS) {

  HandleMark hm(THREAD);

  ClassLoaderData* loader_data = register_loader(class_loader);

  // Classloaders that support parallelism, e.g. bootstrap classloader,
  // do not acquire lock here
  Handle lockObject = get_loader_lock_or_null(class_loader);
  ObjectLocker ol(lockObject, THREAD);

  // Parse the stream and create a klass.
  // Note that we do this even though this klass might
  // already be present in the SystemDictionary, otherwise we would not
  // throw potential ClassFormatErrors.
 InstanceKlass* k = nullptr;

#if INCLUDE_CDS
  if (!CDSConfig::is_dumping_static_archive()) {
    k = SystemDictionaryShared::lookup_from_stream(class_name,
                                                   class_loader,
                                                   cl_info.protection_domain(),
                                                   st,
                                                   CHECK_NULL);
  }
#endif

  if (k == nullptr) {
    k = KlassFactory::create_from_stream(st, class_name, loader_data, cl_info, CHECK_NULL);
  }

  assert(k != nullptr, "no klass created");
  Symbol* h_name = k->name();
  assert(class_name == nullptr || class_name == h_name, "name mismatch");

  // Add class just loaded
  // If a class loader supports parallel classloading, handle parallel define requests.
  // find_or_define_instance_class may return a different InstanceKlass,
  // in which case the old k would be deallocated
  if (is_parallelCapable(class_loader)) {
    k = find_or_define_instance_class(h_name, class_loader, k, CHECK_NULL);
  } else {
    define_instance_class(k, class_loader, THREAD);

    // If defining the class throws an exception register 'k' for cleanup.
    if (HAS_PENDING_EXCEPTION) {
      assert(k != nullptr, "Must have an instance klass here!");
      loader_data->add_to_deallocate_list(k);
      return nullptr;
    }
  }

  // Make sure we have an entry in the SystemDictionary on success
  DEBUG_ONLY(verify_dictionary_entry(h_name, k));

  return k;
}

InstanceKlass* SystemDictionary::resolve_from_stream(ClassFileStream* st,
                                                     Symbol* class_name,
                                                     Handle class_loader,
                                                     const ClassLoadInfo& cl_info,
                                                     TRAPS) {
  if (cl_info.is_hidden()) {
    return resolve_hidden_class_from_stream(st, class_name, class_loader, cl_info, CHECK_NULL);
  } else {
    return resolve_class_from_stream(st, class_name, class_loader, cl_info, CHECK_NULL);
  }
}


#if INCLUDE_CDS
// Check if a shared class can be loaded by the specific classloader.
bool SystemDictionary::is_shared_class_visible(Symbol* class_name,
                                               InstanceKlass* ik,
                                               PackageEntry* pkg_entry,
                                               Handle class_loader) {
  assert(!ModuleEntryTable::javabase_moduleEntry()->is_patched(),
         "Cannot use sharing if java.base is patched");

  // (1) Check if we are loading into the same loader as in dump time.

  if (ik->is_shared_boot_class()) {
    if (class_loader() != nullptr) {
      return false;
    }
  } else if (ik->is_shared_platform_class()) {
    if (class_loader() != java_platform_loader()) {
      return false;
    }
  } else if (ik->is_shared_app_class()) {
    if (class_loader() != java_system_loader()) {
      return false;
    }
  } else {
    // ik was loaded by a custom loader during dump time
    if (class_loader_data(class_loader)->is_builtin_class_loader_data()) {
      return false;
    } else {
      return true;
    }
  }

  // (2) Check if we are loading into the same module from the same location as in dump time.

  if (MetaspaceShared::use_optimized_module_handling()) {
    // Class visibility has not changed between dump time and run time, so a class
    // that was visible (and thus archived) during dump time is always visible during runtime.
    assert(SystemDictionary::is_shared_class_visible_impl(class_name, ik, pkg_entry, class_loader),
           "visibility cannot change between dump time and runtime");
    return true;
  }
  return is_shared_class_visible_impl(class_name, ik, pkg_entry, class_loader);
}

bool SystemDictionary::is_shared_class_visible_impl(Symbol* class_name,
                                                    InstanceKlass* ik,
                                                    PackageEntry* pkg_entry,
                                                    Handle class_loader) {
  int scp_index = ik->shared_classpath_index();
  assert(!ik->is_shared_unregistered_class(), "this function should be called for built-in classes only");
  assert(scp_index >= 0, "must be");
  SharedClassPathEntry* scp_entry = FileMapInfo::shared_path(scp_index);
  if (!Universe::is_module_initialized()) {
    assert(scp_entry != nullptr, "must be");
    // At this point, no modules have been defined yet. KlassSubGraphInfo::check_allowed_klass()
    // has restricted the classes can be loaded at this step to be only:
    // [1] scp_entry->is_modules_image(): classes in java.base, or,
    // [2] HeapShared::is_a_test_class_in_unnamed_module(ik): classes in bootstrap/unnamed module
    assert(scp_entry->is_modules_image() || HeapShared::is_a_test_class_in_unnamed_module(ik),
           "only these classes can be loaded before the module system is initialized");
    assert(class_loader.is_null(), "sanity");
    return true;
  }

  if (pkg_entry == nullptr) {
    // We might have looked up pkg_entry before the module system was initialized.
    // Need to reload it now.
    TempNewSymbol pkg_name = ClassLoader::package_from_class_name(class_name);
    if (pkg_name != nullptr) {
      pkg_entry = class_loader_data(class_loader)->packages()->lookup_only(pkg_name);
    }
  }

  ModuleEntry* mod_entry = (pkg_entry == nullptr) ? nullptr : pkg_entry->module();
  bool should_be_in_named_module = (mod_entry != nullptr && mod_entry->is_named());
  bool was_archived_from_named_module = scp_entry->in_named_module();
  bool visible;

  if (was_archived_from_named_module) {
    if (should_be_in_named_module) {
      // Is the module loaded from the same location as during dump time?
      visible = mod_entry->shared_path_index() == scp_index;
      if (visible) {
        assert(!mod_entry->is_patched(), "cannot load archived classes for patched module");
      }
    } else {
      // During dump time, this class was in a named module, but at run time, this class should be
      // in an unnamed module.
      visible = false;
    }
  } else {
    if (should_be_in_named_module) {
      // During dump time, this class was in an unnamed, but at run time, this class should be
      // in a named module.
      visible = false;
    } else {
      visible = true;
    }
  }

  return visible;
}

bool SystemDictionary::check_shared_class_super_type(InstanceKlass* klass, InstanceKlass* super_type,
                                                     Handle class_loader,  Handle protection_domain,
                                                     bool is_superclass, TRAPS) {
  assert(super_type->is_shared(), "must be");

  // Quick check if the super type has been already loaded.
  // + Don't do it for unregistered classes -- they can be unloaded so
  //   super_type->class_loader_data() could be stale.
  // + Don't check if loader data is null, ie. the super_type isn't fully loaded.
  if (!super_type->is_shared_unregistered_class() && super_type->class_loader_data() != nullptr) {
    // Check if the superclass is loaded by the current class_loader
    Symbol* name = super_type->name();
    InstanceKlass* check = find_instance_klass(THREAD, name, class_loader, protection_domain);
    if (check == super_type) {
      return true;
    }
  }

  Klass *found = resolve_super_or_fail(klass->name(), super_type->name(),
                                       class_loader, protection_domain, is_superclass, CHECK_0);
  if (found == super_type) {
    return true;
  } else {
    // The dynamically resolved super type is not the same as the one we used during dump time,
    // so we cannot use the class.
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

  if (ik->super() != nullptr &&
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

InstanceKlass* SystemDictionary::load_shared_lambda_proxy_class(InstanceKlass* ik,
                                                                Handle class_loader,
                                                                Handle protection_domain,
                                                                PackageEntry* pkg_entry,
                                                                TRAPS) {
  InstanceKlass* shared_nest_host = SystemDictionaryShared::get_shared_nest_host(ik);
  assert(shared_nest_host->is_shared(), "nest host must be in CDS archive");
  Symbol* cn = shared_nest_host->name();
  Klass *s = resolve_or_fail(cn, class_loader, protection_domain, true, CHECK_NULL);
  if (s != shared_nest_host) {
    // The dynamically resolved nest_host is not the same as the one we used during dump time,
    // so we cannot use ik.
    return nullptr;
  } else {
    assert(s->is_shared(), "must be");
  }

  InstanceKlass* loaded_ik = load_shared_class(ik, class_loader, protection_domain, nullptr, pkg_entry, CHECK_NULL);

  if (loaded_ik != nullptr) {
    assert(shared_nest_host->is_same_class_package(ik),
           "lambda proxy class and its nest host must be in the same package");
    // The lambda proxy class and its nest host have the same class loader and class loader data,
    // as verified in SystemDictionaryShared::add_lambda_proxy_class()
    assert(shared_nest_host->class_loader() == class_loader(), "mismatched class loader");
    assert(shared_nest_host->class_loader_data() == class_loader_data(class_loader), "mismatched class loader data");
    ik->set_nest_host(shared_nest_host);
  }

  return loaded_ik;
}

InstanceKlass* SystemDictionary::load_shared_class(InstanceKlass* ik,
                                                   Handle class_loader,
                                                   Handle protection_domain,
                                                   const ClassFileStream *cfs,
                                                   PackageEntry* pkg_entry,
                                                   TRAPS) {
  assert(ik != nullptr, "sanity");
  assert(!ik->is_unshareable_info_restored(), "shared class can be restored only once");
  assert(Atomic::add(&ik->_shared_class_load_count, 1) == 1, "shared class loaded more than once");
  Symbol* class_name = ik->name();

  if (!is_shared_class_visible(class_name, ik, pkg_entry, class_loader)) {
    return nullptr;
  }

  if (!check_shared_class_super_types(ik, class_loader, protection_domain, THREAD)) {
    return nullptr;
  }

  InstanceKlass* new_ik = nullptr;
  // CFLH check is skipped for VM hidden classes (see KlassFactory::create_from_stream).
  // It will be skipped for shared VM hidden lambda proxy classes.
  if (!SystemDictionaryShared::is_hidden_lambda_proxy(ik)) {
    new_ik = KlassFactory::check_shared_class_file_load_hook(
      ik, class_name, class_loader, protection_domain, cfs, CHECK_NULL);
  }
  if (new_ik != nullptr) {
    // The class is changed by CFLH. Return the new class. The shared class is
    // not used.
    return new_ik;
  }

  // Adjust methods to recover missing data.  They need addresses for
  // interpreter entry points and their default native method address
  // must be reset.

  // Shared classes are all currently loaded by either the bootstrap or
  // internal parallel class loaders, so this will never cause a deadlock
  // on a custom class loader lock.
  // Since this class is already locked with parallel capable class
  // loaders, including the bootstrap loader via the placeholder table,
  // this lock is currently a nop.

  ClassLoaderData* loader_data = class_loader_data(class_loader);
  {
    HandleMark hm(THREAD);
    Handle lockObject = get_loader_lock_or_null(class_loader);
    ObjectLocker ol(lockObject, THREAD);
    // prohibited package check assumes all classes loaded from archive call
    // restore_unshareable_info which calls ik->set_package()
    ik->restore_unshareable_info(loader_data, protection_domain, pkg_entry, CHECK_NULL);
  }

  load_shared_class_misc(ik, loader_data);
  return ik;
}

void SystemDictionary::load_shared_class_misc(InstanceKlass* ik, ClassLoaderData* loader_data) {
  ik->print_class_load_logging(loader_data, nullptr, nullptr);

  // For boot loader, ensure that GetSystemPackage knows that a class in this
  // package was loaded.
  if (loader_data->is_the_null_class_loader_data()) {
    s2 path_index = ik->shared_classpath_index();
    ik->set_classpath_index(path_index);
  }

  // notify a class loaded from shared object
  ClassLoadingService::notify_class_loaded(ik, true /* shared class */);
}

#endif // INCLUDE_CDS

InstanceKlass* SystemDictionary::load_instance_class_impl(Symbol* class_name, Handle class_loader, TRAPS) {

  if (class_loader.is_null()) {
    ResourceMark rm(THREAD);
    PackageEntry* pkg_entry = nullptr;
    bool search_only_bootloader_append = false;

    // Find the package in the boot loader's package entry table.
    TempNewSymbol pkg_name = ClassLoader::package_from_class_name(class_name);
    if (pkg_name != nullptr) {
      pkg_entry = class_loader_data(class_loader)->packages()->lookup_only(pkg_name);
    }

    // Prior to attempting to load the class, enforce the boot loader's
    // visibility boundaries.
    if (!Universe::is_module_initialized()) {
      // During bootstrapping, prior to module initialization, any
      // class attempting to be loaded must be checked against the
      // java.base packages in the boot loader's PackageEntryTable.
      // No class outside of java.base is allowed to be loaded during
      // this bootstrapping window.
      if (pkg_entry == nullptr || pkg_entry->in_unnamed_module()) {
        // Class is either in the unnamed package or in
        // a named package within the unnamed module.  Either
        // case is outside of java.base, do not attempt to
        // load the class post java.base definition.  If
        // java.base has not been defined, let the class load
        // and its package will be checked later by
        // ModuleEntryTable::verify_javabase_packages.
        if (ModuleEntryTable::javabase_defined()) {
          return nullptr;
        }
      } else {
        // Check that the class' package is defined within java.base.
        ModuleEntry* mod_entry = pkg_entry->module();
        Symbol* mod_entry_name = mod_entry->name();
        if (mod_entry_name->fast_compare(vmSymbols::java_base()) != 0) {
          return nullptr;
        }
      }
    } else {
      // After the module system has been initialized, check if the class'
      // package is in a module defined to the boot loader.
      if (pkg_name == nullptr || pkg_entry == nullptr || pkg_entry->in_unnamed_module()) {
        // Class is either in the unnamed package, in a named package
        // within a module not defined to the boot loader or in a
        // a named package within the unnamed module.  In all cases,
        // limit visibility to search for the class only in the boot
        // loader's append path.
        if (!ClassLoader::has_bootclasspath_append()) {
           // If there is no bootclasspath append entry, no need to continue
           // searching.
           return nullptr;
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
    InstanceKlass* k = nullptr;

#if INCLUDE_CDS
    if (UseSharedSpaces)
    {
      PerfTraceTime vmtimer(ClassLoader::perf_shared_classload_time());
      InstanceKlass* ik = SystemDictionaryShared::find_builtin_class(class_name);
      if (ik != nullptr && ik->is_shared_boot_class() && !ik->shared_loading_failed()) {
        SharedClassLoadingMark slm(THREAD, ik);
        k = load_shared_class(ik, class_loader, Handle(), nullptr,  pkg_entry, CHECK_NULL);
      }
    }
#endif

    if (k == nullptr) {
      // Use VM class loader
      PerfTraceTime vmtimer(ClassLoader::perf_sys_classload_time());
      k = ClassLoader::load_class(class_name, search_only_bootloader_append, CHECK_NULL);
    }

    // find_or_define_instance_class may return a different InstanceKlass
    if (k != nullptr) {
      CDS_ONLY(SharedClassLoadingMark slm(THREAD, k);)
      k = find_or_define_instance_class(class_name, class_loader, k, CHECK_NULL);
    }
    return k;
  } else {
    // Use user specified class loader to load class. Call loadClass operation on class_loader.
    ResourceMark rm(THREAD);

    JavaThread* jt = THREAD;

    PerfClassTraceTime vmtimer(ClassLoader::perf_app_classload_time(),
                               ClassLoader::perf_app_classload_selftime(),
                               ClassLoader::perf_app_classload_count(),
                               jt->get_thread_stat()->perf_recursion_counts_addr(),
                               jt->get_thread_stat()->perf_timers_addr(),
                               PerfClassTraceTime::CLASS_LOAD);

    // Translate to external class name format, i.e., convert '/' chars to '.'
    Handle string = java_lang_String::externalize_classname(class_name, CHECK_NULL);

    JavaValue result(T_OBJECT);

    InstanceKlass* spec_klass = vmClasses::ClassLoader_klass();

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
    oop obj = result.get_oop();

    // Primitive classes return null since forName() can not be
    // used to obtain any of the Class objects representing primitives or void
    if ((obj != nullptr) && !(java_lang_Class::is_primitive(obj))) {
      InstanceKlass* k = InstanceKlass::cast(java_lang_Class::as_Klass(obj));
      // For user defined Java class loaders, check that the name returned is
      // the same as that requested.  This check is done for the bootstrap
      // loader when parsing the class file.
      if (class_name == k->name()) {
        return k;
      }
    }
    // Class is not found or has the wrong name, return null
    return nullptr;
  }
}

InstanceKlass* SystemDictionary::load_instance_class(Symbol* name,
                                                     Handle class_loader,
                                                     TRAPS) {

  InstanceKlass* loaded_class = load_instance_class_impl(name, class_loader, CHECK_NULL);

  // If everything was OK (no exceptions, no null return value), and
  // class_loader is NOT the defining loader, do a little more bookkeeping.
  if (loaded_class != nullptr &&
      loaded_class->class_loader() != class_loader()) {

    ClassLoaderData* loader_data = class_loader_data(class_loader);
    check_constraints(loaded_class, loader_data, false, CHECK_NULL);

    // Record dependency for non-parent delegation.
    // This recording keeps the defining class loader of the klass (loaded_class) found
    // from being unloaded while the initiating class loader is loaded
    // even if the reference to the defining class loader is dropped
    // before references to the initiating class loader.
    loader_data->record_dependency(loaded_class);

    update_dictionary(THREAD, loaded_class, loader_data);

    if (JvmtiExport::should_post_class_load()) {
      JvmtiExport::post_class_load(THREAD, loaded_class);
    }
  }
  return loaded_class;
}

static void post_class_define_event(InstanceKlass* k, const ClassLoaderData* def_cld) {
  EventClassDefine event;
  if (event.should_commit()) {
    event.set_definedClass(k);
    event.set_definingClassLoader(def_cld);
    event.commit();
  }
}

void SystemDictionary::define_instance_class(InstanceKlass* k, Handle class_loader, TRAPS) {

  ClassLoaderData* loader_data = k->class_loader_data();
  assert(loader_data->class_loader() == class_loader(), "they must be the same");

  // Bootstrap and other parallel classloaders don't acquire a lock,
  // they use placeholder token.
  // If a parallelCapable class loader calls define_instance_class instead of
  // find_or_define_instance_class to get here, we have a timing
  // hole with systemDictionary updates and check_constraints
  if (!is_parallelCapable(class_loader)) {
    assert(ObjectSynchronizer::current_thread_holds_lock(THREAD,
           get_loader_lock_or_null(class_loader)),
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
  check_constraints(k, loader_data, true, CHECK);

  // Register class just loaded with class loader (placed in ArrayList)
  // Note we do this before updating the dictionary, as this can
  // fail with an OutOfMemoryError (if it does, we will *not* put this
  // class in the dictionary and will not update the class hierarchy).
  // JVMTI FollowReferences needs to find the classes this way.
  if (k->class_loader() != nullptr) {
    methodHandle m(THREAD, Universe::loader_addClass_method());
    JavaValue result(T_VOID);
    JavaCallArguments args(class_loader);
    args.push_oop(Handle(THREAD, k->java_mirror()));
    JavaCalls::call(&result, m, &args, CHECK);
  }

  // Add to class hierarchy, and do possible deoptimizations.
  k->add_to_hierarchy(THREAD);

  // Add to systemDictionary - so other classes can see it.
  // Grabs and releases SystemDictionary_lock
  update_dictionary(THREAD, k, loader_data);

  // notify jvmti
  if (JvmtiExport::should_post_class_load()) {
    JvmtiExport::post_class_load(THREAD, k);
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
// PlaceholderTable::find_and_add(PlaceholderTable::DEFINE_CLASS),
// you need to find_and_remove it before returning.
// So be careful to not exit with a CHECK_ macro between these calls.
InstanceKlass* SystemDictionary::find_or_define_helper(Symbol* class_name, Handle class_loader,
                                                       InstanceKlass* k, TRAPS) {

  Symbol* name_h = k->name();
  ClassLoaderData* loader_data = class_loader_data(class_loader);
  Dictionary* dictionary = loader_data->dictionary();

  // Hold SD lock around find_class and placeholder creation for DEFINE_CLASS
  {
    MutexLocker mu(THREAD, SystemDictionary_lock);
    // First check if class already defined
    if (is_parallelDefine(class_loader)) {
      InstanceKlass* check = dictionary->find_class(THREAD, name_h);
      if (check != nullptr) {
        return check;
      }
    }

    // Acquire define token for this class/classloader
    PlaceholderEntry* probe = PlaceholderTable::find_and_add(name_h, loader_data,
                                                             PlaceholderTable::DEFINE_CLASS, nullptr, THREAD);
    // Wait if another thread defining in parallel
    // All threads wait - even those that will throw duplicate class: otherwise
    // caller is surprised by LinkageError: duplicate, but findLoadedClass fails
    // if other thread has not finished updating dictionary
    while (probe->definer() != nullptr) {
      SystemDictionary_lock->wait();
    }
    // Only special cases allow parallel defines and can use other thread's results
    // Other cases fall through, and may run into duplicate defines
    // caught by finding an entry in the SystemDictionary
    if (is_parallelDefine(class_loader) && (probe->instance_klass() != nullptr)) {
      InstanceKlass* ik = probe->instance_klass();
      PlaceholderTable::find_and_remove(name_h, loader_data, PlaceholderTable::DEFINE_CLASS, THREAD);
      SystemDictionary_lock->notify_all();
#ifdef ASSERT
      InstanceKlass* check = dictionary->find_class(THREAD, name_h);
      assert(check != nullptr, "definer missed recording success");
#endif
      return ik;
    } else {
      // This thread will define the class (even if earlier thread tried and had an error)
      probe->set_definer(THREAD);
    }
  }

  define_instance_class(k, class_loader, THREAD);

  // definer must notify any waiting threads
  {
    MutexLocker mu(THREAD, SystemDictionary_lock);
    PlaceholderEntry* probe = PlaceholderTable::get_entry(name_h, loader_data);
    assert(probe != nullptr, "DEFINE_CLASS placeholder lost?");
    if (!HAS_PENDING_EXCEPTION) {
      probe->set_instance_klass(k);
    }
    probe->set_definer(nullptr);
    PlaceholderTable::find_and_remove(name_h, loader_data, PlaceholderTable::DEFINE_CLASS, THREAD);
    SystemDictionary_lock->notify_all();
  }

  return HAS_PENDING_EXCEPTION ? nullptr : k;
}

// If a class loader supports parallel classloading handle parallel define requests.
// find_or_define_instance_class may return a different InstanceKlass
InstanceKlass* SystemDictionary::find_or_define_instance_class(Symbol* class_name, Handle class_loader,
                                                               InstanceKlass* k, TRAPS) {
  InstanceKlass* defined_k = find_or_define_helper(class_name, class_loader, k, THREAD);
  // Clean up original InstanceKlass if duplicate or error
  if (!HAS_PENDING_EXCEPTION && defined_k != k) {
    // If a parallel capable class loader already defined this class, register 'k' for cleanup.
    assert(defined_k != nullptr, "Should have a klass if there's no exception");
    k->class_loader_data()->add_to_deallocate_list(k);
  } else if (HAS_PENDING_EXCEPTION) {
    // Remove this InstanceKlass from the LoaderConstraintTable if added.
    LoaderConstraintTable::remove_failed_loaded_klass(k, class_loader_data(class_loader));
    assert(defined_k == nullptr, "Should not have a klass if there's an exception");
    k->class_loader_data()->add_to_deallocate_list(k);
  }
  return defined_k;
}


// ----------------------------------------------------------------------------
// GC support

// Assumes classes in the SystemDictionary are only unloaded at a safepoint
bool SystemDictionary::do_unloading(GCTimer* gc_timer) {

  bool unloading_occurred;
  bool is_concurrent = !SafepointSynchronize::is_at_safepoint();
  {
    GCTraceTime(Debug, gc, phases) t("ClassLoaderData", gc_timer);
    assert_locked_or_safepoint(ClassLoaderDataGraph_lock);  // caller locks.
    // First, mark for unload all ClassLoaderData referencing a dead class loader.
    unloading_occurred = ClassLoaderDataGraph::do_unloading();
    if (unloading_occurred) {
      ConditionalMutexLocker ml2(Module_lock, is_concurrent);
      JFR_ONLY(Jfr::on_unloading_classes();)
      MANAGEMENT_ONLY(FinalizerService::purge_unloaded();)
      ConditionalMutexLocker ml1(SystemDictionary_lock, is_concurrent);
      ClassLoaderDataGraph::clean_module_and_package_info();
      LoaderConstraintTable::purge_loader_constraints();
      ResolutionErrorTable::purge_resolution_errors();
    }
  }

  GCTraceTime(Debug, gc, phases) t("Trigger cleanups", gc_timer);

  if (unloading_occurred) {
    SymbolTable::trigger_cleanup();

    if (java_lang_System::allow_security_manager()) {
      // Oops referenced by the protection domain cache table may get unreachable independently
      // of the class loader (eg. cached protection domain oops). So we need to
      // explicitly unlink them here.
      // All protection domain oops are linked to the caller class, so if nothing
      // unloads, this is not needed.
      ProtectionDomainCacheTable::trigger_cleanup();
    } else {
      assert(ProtectionDomainCacheTable::number_of_entries() == 0, "should be empty");
    }

    ConditionalMutexLocker ml(ClassInitError_lock, is_concurrent);
    InstanceKlass::clean_initialization_error_table();
  }

  return unloading_occurred;
}

void SystemDictionary::methods_do(void f(Method*)) {
  // Walk methods in loaded classes

  {
    MutexLocker ml(ClassLoaderDataGraph_lock);
    ClassLoaderDataGraph::methods_do(f);
  }

  auto doit = [&] (InvokeMethodKey key, Method* method) {
    if (method != nullptr) {
      f(method);
    }
  };

  {
    MutexLocker ml(InvokeMethodIntrinsicTable_lock);
    _invoke_method_intrinsic_table->iterate_all(doit);
  }

}

// ----------------------------------------------------------------------------
// Initialization

void SystemDictionary::initialize(TRAPS) {
  _invoke_method_intrinsic_table = new (mtClass) InvokeMethodIntrinsicTable();
  _invoke_method_type_table = new (mtClass) InvokeMethodTypeTable();
  ResolutionErrorTable::initialize();
  LoaderConstraintTable::initialize();
  PlaceholderTable::initialize();
  ProtectionDomainCacheTable::initialize();
#if INCLUDE_CDS
  SystemDictionaryShared::initialize();
#endif
  // Resolve basic classes
  vmClasses::resolve_all(CHECK);
  // Resolve classes used by archived heap objects
  if (UseSharedSpaces) {
    HeapShared::resolve_classes(THREAD);
  }
}

// Constraints on class loaders. The details of the algorithm can be
// found in the OOPSLA'98 paper "Dynamic Class Loading in the Java
// Virtual Machine" by Sheng Liang and Gilad Bracha.  The basic idea is
// that the dictionary needs to maintain a set of constraints that
// must be satisfied by all classes in the dictionary.
// if defining is true, then LinkageError if already in dictionary
// if initiating loader, then ok if InstanceKlass matches existing entry

void SystemDictionary::check_constraints(InstanceKlass* k,
                                         ClassLoaderData* loader_data,
                                         bool defining,
                                         TRAPS) {
  ResourceMark rm(THREAD);
  stringStream ss;
  bool throwException = false;

  {
    Symbol* name = k->name();

    MutexLocker mu(THREAD, SystemDictionary_lock);

    InstanceKlass* check = loader_data->dictionary()->find_class(THREAD, name);
    if (check != nullptr) {
      // If different InstanceKlass - duplicate class definition,
      // else - ok, class loaded by a different thread in parallel.
      // We should only have found it if it was done loading and ok to use.

      if ((defining == true) || (k != check)) {
        throwException = true;
        ss.print("loader %s", loader_data->loader_name_and_id());
        ss.print(" attempted duplicate %s definition for %s. (%s)",
                 k->external_kind(), k->external_name(), k->class_in_module_of_loader(false, true));
      } else {
        return;
      }
    }

    if (throwException == false) {
      if (LoaderConstraintTable::check_or_update(k, loader_data, name) == false) {
        throwException = true;
        ss.print("loader constraint violation: loader %s", loader_data->loader_name_and_id());
        ss.print(" wants to load %s %s.",
                 k->external_kind(), k->external_name());
        Klass *existing_klass = LoaderConstraintTable::find_constrained_klass(name, loader_data);
        if (existing_klass != nullptr && existing_klass->class_loader_data() != loader_data) {
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

// Update class loader data dictionary - done after check_constraint and add_to_hierarchy
// have been called.
void SystemDictionary::update_dictionary(JavaThread* current,
                                         InstanceKlass* k,
                                         ClassLoaderData* loader_data) {
  MonitorLocker mu1(SystemDictionary_lock);

  // Make a new dictionary entry.
  Symbol* name  = k->name();
  Dictionary* dictionary = loader_data->dictionary();
  InstanceKlass* sd_check = dictionary->find_class(current, name);
  if (sd_check == nullptr) {
    dictionary->add_klass(current, name, k);
  }
  mu1.notify_all();
}


// Try to find a class name using the loader constraints.  The
// loader constraints might know about a class that isn't fully loaded
// yet and these will be ignored.
Klass* SystemDictionary::find_constrained_instance_or_array_klass(
                    Thread* current, Symbol* class_name, Handle class_loader) {

  // First see if it has been loaded directly.
  // Force the protection domain to be null.  (This removes protection checks.)
  Handle no_protection_domain;
  Klass* klass = find_instance_or_array_klass(current, class_name, class_loader,
                                              no_protection_domain);
  if (klass != nullptr)
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
      MutexLocker mu(current, SystemDictionary_lock);
      klass = LoaderConstraintTable::find_constrained_klass(ss.as_symbol(), class_loader_data(class_loader));
    }
    // If element class already loaded, allocate array klass
    if (klass != nullptr) {
      klass = klass->array_klass_or_null(ndims);
    }
  } else {
    MutexLocker mu(current, SystemDictionary_lock);
    // Non-array classes are easy: simply check the constraint table.
    klass = LoaderConstraintTable::find_constrained_klass(class_name, class_loader_data(class_loader));
  }

  return klass;
}

bool SystemDictionary::add_loader_constraint(Symbol* class_name,
                                             Klass* klass_being_linked,
                                             Handle class_loader1,
                                             Handle class_loader2) {
  ClassLoaderData* loader_data1 = class_loader_data(class_loader1);
  ClassLoaderData* loader_data2 = class_loader_data(class_loader2);

  Symbol* constraint_name = nullptr;

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
  Dictionary* dictionary2 = loader_data2->dictionary();

  JavaThread* current = JavaThread::current();
  {
    MutexLocker mu_s(SystemDictionary_lock);
    InstanceKlass* klass1 = dictionary1->find_class(current, constraint_name);
    InstanceKlass* klass2 = dictionary2->find_class(current, constraint_name);
    bool result = LoaderConstraintTable::add_entry(constraint_name, klass1, loader_data1,
                                                   klass2, loader_data2);
#if INCLUDE_CDS
    if (CDSConfig::is_dumping_archive() && klass_being_linked != nullptr &&
        !klass_being_linked->is_shared()) {
         SystemDictionaryShared::record_linking_constraint(constraint_name,
                                     InstanceKlass::cast(klass_being_linked),
                                     class_loader1, class_loader2);
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
                                            Symbol* error, Symbol* message,
                                            Symbol* cause, Symbol* cause_msg) {
  {
    MutexLocker ml(Thread::current(), SystemDictionary_lock);
    ResolutionErrorEntry* entry = ResolutionErrorTable::find_entry(pool, which);
    if (entry == nullptr) {
      ResolutionErrorTable::add_entry(pool, which, error, message, cause, cause_msg);
    }
  }
}

// Delete a resolution error for RedefineClasses for a constant pool is going away
void SystemDictionary::delete_resolution_error(ConstantPool* pool) {
  ResolutionErrorTable::delete_entry(pool);
}

// Lookup resolution error table. Returns error if found, otherwise null.
Symbol* SystemDictionary::find_resolution_error(const constantPoolHandle& pool, int which,
                                                Symbol** message, Symbol** cause, Symbol** cause_msg) {

  {
    MutexLocker ml(Thread::current(), SystemDictionary_lock);
    ResolutionErrorEntry* entry = ResolutionErrorTable::find_entry(pool, which);
    if (entry != nullptr) {
      *message = entry->message();
      *cause = entry->cause();
      *cause_msg = entry->cause_msg();
      return entry->error();
    } else {
      return nullptr;
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
  {
    MutexLocker ml(Thread::current(), SystemDictionary_lock);
    ResolutionErrorEntry* entry = ResolutionErrorTable::find_entry(pool, which);
    if (entry != nullptr && entry->nest_host_error() == nullptr) {
      // An existing entry means we had a true resolution failure (LinkageError) with our nest host, but we
      // still want to add the error message for the higher-level access checks to report. We should
      // only reach here under the same error condition, so we can ignore the potential race with setting
      // the message. If we see it is already set then we can ignore it.
      entry->set_nest_host_error(message);
    } else {
      ResolutionErrorTable::add_entry(pool, which, message);
    }
  }
}

// Lookup any nest host error
const char* SystemDictionary::find_nest_host_error(const constantPoolHandle& pool, int which) {
  {
    MutexLocker ml(Thread::current(), SystemDictionary_lock);
    ResolutionErrorEntry* entry = ResolutionErrorTable::find_entry(pool, which);
    if (entry != nullptr) {
      return entry->nest_host_error();
    } else {
      return nullptr;
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
// null if no constraint failed.  No exception except OOME is thrown.
// Arrays are not added to the loader constraint table, their elements are.
Symbol* SystemDictionary::check_signature_loaders(Symbol* signature,
                                                  Klass* klass_being_linked,
                                                  Handle loader1, Handle loader2,
                                                  bool is_method)  {
  // Nothing to do if loaders are the same.
  if (loader1() == loader2()) {
    return nullptr;
  }

  for (SignatureStream ss(signature, is_method); !ss.is_done(); ss.next()) {
    if (ss.is_reference()) {
      Symbol* sig = ss.as_symbol();
      // Note: In the future, if template-like types can take
      // arguments, we will want to recognize them and dig out class
      // names hiding inside the argument lists.
      if (!add_loader_constraint(sig, klass_being_linked, loader1, loader2)) {
        return sig;
      }
    }
  }
  return nullptr;
}

Method* SystemDictionary::find_method_handle_intrinsic(vmIntrinsicID iid,
                                                       Symbol* signature,
                                                       TRAPS) {

  const int iid_as_int = vmIntrinsics::as_int(iid);
  assert(MethodHandles::is_signature_polymorphic(iid) &&
         MethodHandles::is_signature_polymorphic_intrinsic(iid) &&
         iid != vmIntrinsics::_invokeGeneric,
         "must be a known MH intrinsic iid=%d: %s", iid_as_int, vmIntrinsics::name_at(iid));

  InvokeMethodKey key(signature, iid_as_int);
  Method** met = nullptr;

  // We only want one entry in the table for this (signature/id, method) pair but the code
  // to create the intrinsic method needs to be outside the lock.
  // The first thread claims the entry by adding the key and the other threads wait, until the
  // Method has been added as the value.
  {
    MonitorLocker ml(THREAD, InvokeMethodIntrinsicTable_lock);
    while (true) {
      bool created;
      met = _invoke_method_intrinsic_table->put_if_absent(key, &created);
      assert(met != nullptr, "either created or found");
      if (*met != nullptr) {
        return *met;
      } else if (created) {
        // The current thread won the race and will try to create the full entry.
        break;
      } else {
        // Another thread beat us to it, so wait for them to complete
        // and return *met; or if they hit an error we get another try.
        ml.wait();
        // Note it is not safe to read *met here as that entry could have
        // been deleted, so we must loop and try put_if_absent again.
      }
    }
  }

  methodHandle m = Method::make_method_handle_intrinsic(iid, signature, THREAD);
  bool throw_error = HAS_PENDING_EXCEPTION;
  if (!throw_error && (!Arguments::is_interpreter_only() || iid == vmIntrinsics::_linkToNative)) {
    // Generate a compiled form of the MH intrinsic
    // linkToNative doesn't have interpreter-specific implementation, so always has to go through compiled version.
    AdapterHandlerLibrary::create_native_wrapper(m);
    // Check if have the compiled code.
    throw_error = (!m->has_compiled_code());
  }

  {
    MonitorLocker ml(THREAD, InvokeMethodIntrinsicTable_lock);
    if (throw_error) {
      // Remove the entry and let another thread try, or get the same exception.
      bool removed = _invoke_method_intrinsic_table->remove(key);
      assert(removed, "must be the owner");
      ml.notify_all();
    } else {
      signature->make_permanent(); // The signature is never unloaded.
      assert(Arguments::is_interpreter_only() || (m->has_compiled_code() &&
             m->code()->entry_point() == m->from_compiled_entry()),
             "MH intrinsic invariant");
      *met = m(); // insert the element
      ml.notify_all();
      return m();
    }
  }

  // Throw VirtualMachineError or the pending exception in the JavaThread
  if (throw_error && !HAS_PENDING_EXCEPTION) {
    THROW_MSG_NULL(vmSymbols::java_lang_VirtualMachineError(),
                   "Out of space in CodeCache for method handle intrinsic");
  }
  return nullptr;
}

// Helper for unpacking the return value from linkMethod and linkCallSite.
static Method* unpack_method_and_appendix(Handle mname,
                                          Klass* accessing_klass,
                                          objArrayHandle appendix_box,
                                          Handle* appendix_result,
                                          TRAPS) {
  if (mname.not_null()) {
    Method* m = java_lang_invoke_MemberName::vmtarget(mname());
    if (m != nullptr) {
      oop appendix = appendix_box->obj_at(0);
      LogTarget(Info, methodhandles) lt;
      if (lt.develop_is_enabled()) {
        ResourceMark rm(THREAD);
        LogStream ls(lt);
        ls.print("Linked method=" INTPTR_FORMAT ": ", p2i(m));
        m->print_on(&ls);
        if (appendix != nullptr) { ls.print("appendix = "); appendix->print_on(&ls); }
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
                                                     Handle* appendix_result,
                                                     TRAPS) {
  guarantee(THREAD->can_call_java(), "");
  Handle method_type =
    SystemDictionary::find_method_handle_type(signature, accessing_klass, CHECK_NULL);

  int ref_kind = JVM_REF_invokeVirtual;
  oop name_oop = StringTable::intern(name, CHECK_NULL);
  Handle name_str (THREAD, name_oop);
  objArrayHandle appendix_box = oopFactory::new_objArray_handle(vmClasses::Object_klass(), 1, CHECK_NULL);
  assert(appendix_box->obj_at(0) == nullptr, "");

  // This should not happen.  JDK code should take care of that.
  if (accessing_klass == nullptr || method_type.is_null()) {
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
                         vmClasses::MethodHandleNatives_klass(),
                         vmSymbols::linkMethod_name(),
                         vmSymbols::linkMethod_signature(),
                         &args, CHECK_NULL);
  Handle mname(THREAD, result.get_oop());
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
         (InstanceKlass::cast(klass)->is_same_class_package(vmClasses::Object_klass()) ||       // java.lang
          InstanceKlass::cast(klass)->is_same_class_package(vmClasses::MethodHandle_klass()));  // java.lang.invoke
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
  assert(accessing_klass == nullptr || (class_loader.is_null() && protection_domain.is_null()),
         "one or the other, or perhaps neither");

  // What we have here must be a valid field descriptor,
  // and all valid field descriptors are supported.
  // Produce the same java.lang.Class that reflection reports.
  if (accessing_klass != nullptr) {
    class_loader      = Handle(THREAD, accessing_klass->class_loader());
    protection_domain = Handle(THREAD, accessing_klass->protection_domain());
  }
  ResolvingSignatureStream ss(signature, class_loader, protection_domain, false);
  oop mirror_oop = ss.as_java_mirror(failure_mode, CHECK_NH);
  if (mirror_oop == nullptr) {
    return Handle();  // report failure this way
  }
  Handle mirror(THREAD, mirror_oop);

  if (accessing_klass != nullptr) {
    // Check accessibility, emulating ConstantPool::verify_constant_pool_resolve.
    Klass* sel_klass = java_lang_Class::as_Klass(mirror());
    if (sel_klass != nullptr) {
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
  OopHandle* o;
  {
    MutexLocker ml(THREAD, InvokeMethodTypeTable_lock);
    o = _invoke_method_type_table->get(signature);
  }

  if (o != nullptr) {
    oop mt = o->resolve();
    assert(java_lang_invoke_MethodType::is_instance(mt), "");
    return Handle(THREAD, mt);
  } else if (!THREAD->can_call_java()) {
    warning("SystemDictionary::find_method_handle_type called from compiler thread");  // FIXME
    return Handle();  // do not attempt from within compiler, unless it was cached
  }

  Handle class_loader, protection_domain;
  if (accessing_klass != nullptr) {
    class_loader      = Handle(THREAD, accessing_klass->class_loader());
    protection_domain = Handle(THREAD, accessing_klass->protection_domain());
  }
  bool can_be_cached = true;
  int npts = ArgumentCount(signature).size();
  objArrayHandle pts = oopFactory::new_objArray_handle(vmClasses::Class_klass(), npts, CHECK_(empty));
  int arg = 0;
  Handle rt; // the return type from the signature
  ResourceMark rm(THREAD);
  for (SignatureStream ss(signature); !ss.is_done(); ss.next()) {
    oop mirror = nullptr;
    if (can_be_cached) {
      // Use neutral class loader to lookup candidate classes to be placed in the cache.
      mirror = ss.as_java_mirror(Handle(), Handle(),
                                 SignatureStream::ReturnNull, CHECK_(empty));
      if (mirror == nullptr || (ss.is_reference() && !is_always_visible_class(mirror))) {
        // Fall back to accessing_klass context.
        can_be_cached = false;
      }
    }
    if (!can_be_cached) {
      // Resolve, throwing a real error if it doesn't work.
      mirror = ss.as_java_mirror(class_loader, protection_domain,
                                 SignatureStream::NCDFError, CHECK_(empty));
    }
    assert(mirror != nullptr, "%s", ss.as_symbol()->as_C_string());
    if (ss.at_return_type())
      rt = Handle(THREAD, mirror);
    else
      pts->obj_at_put(arg++, mirror);

    // Check accessibility.
    if (!java_lang_Class::is_primitive(mirror) && accessing_klass != nullptr) {
      Klass* sel_klass = java_lang_Class::as_Klass(mirror);
      mirror = nullptr;  // safety
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
                         vmClasses::MethodHandleNatives_klass(),
                         vmSymbols::findMethodHandleType_name(),
                         vmSymbols::findMethodHandleType_signature(),
                         &args, CHECK_(empty));
  Handle method_type(THREAD, result.get_oop());

  if (can_be_cached) {
    // We can cache this MethodType inside the JVM.
    MutexLocker ml(THREAD, InvokeMethodTypeTable_lock);
    bool created = false;
    assert(method_type != nullptr, "unexpected null");
    OopHandle* h = _invoke_method_type_table->get(signature);
    if (h == nullptr) {
      signature->make_permanent(); // The signature is never unloaded.
      OopHandle elem = OopHandle(Universe::vm_global(), method_type());
      bool created = _invoke_method_type_table->put(signature, elem);
      assert(created, "better be created");
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
    if (accessing_klass != nullptr) {
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
  if (caller == nullptr) {
    THROW_MSG_(vmSymbols::java_lang_InternalError(), "bad MH constant", empty);
  }
  Handle name_str      = java_lang_String::create_from_symbol(name,      CHECK_(empty));
  Handle signature_str = java_lang_String::create_from_symbol(signature, CHECK_(empty));

  // Put symbolic info from the MH constant into freshly created MemberName and resolve it.
  Handle mname = vmClasses::MemberName_klass()->allocate_instance_handle(CHECK_(empty));
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
    MethodHandles::resolve_MemberName(mname, caller, 0, false /*speculative_resolve*/, CHECK_(empty));
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
                         vmClasses::MethodHandleNatives_klass(),
                         vmSymbols::linkMethodHandleConstant_name(),
                         vmSymbols::linkMethodHandleConstant_signature(),
                         &args, CHECK_(empty));
  return Handle(THREAD, result.get_oop());
}

// Ask Java to run a bootstrap method, in order to create a dynamic call site
// while linking an invokedynamic op, or compute a constant for Dynamic_info CP entry
// with linkage results being stored back into the bootstrap specifier.
void SystemDictionary::invoke_bootstrap_method(BootstrapInfo& bootstrap_specifier, TRAPS) {
  // Resolve the bootstrap specifier, its name, type, and static arguments
  bootstrap_specifier.resolve_bsm(CHECK);

  // This should not happen.  JDK code should take care of that.
  if (bootstrap_specifier.caller() == nullptr || bootstrap_specifier.type_arg().is_null()) {
    THROW_MSG(vmSymbols::java_lang_InternalError(), "Invalid bootstrap method invocation with no caller or type argument");
  }

  bool is_indy = bootstrap_specifier.is_method_call();
  objArrayHandle appendix_box;
  if (is_indy) {
    // Some method calls may require an appendix argument.  Arrange to receive it.
    appendix_box = oopFactory::new_objArray_handle(vmClasses::Object_klass(), 1, CHECK);
    assert(appendix_box->obj_at(0) == nullptr, "");
  }

  // call condy: java.lang.invoke.MethodHandleNatives::linkDynamicConstant(caller, bsm, type, info)
  //       indy: java.lang.invoke.MethodHandleNatives::linkCallSite(caller, bsm, name, mtype, info, &appendix)
  JavaCallArguments args;
  args.push_oop(Handle(THREAD, bootstrap_specifier.caller_mirror()));
  args.push_oop(bootstrap_specifier.bsm());
  args.push_oop(bootstrap_specifier.name_arg());
  args.push_oop(bootstrap_specifier.type_arg());
  args.push_oop(bootstrap_specifier.arg_values());
  if (is_indy) {
    args.push_oop(appendix_box);
  }
  JavaValue result(T_OBJECT);
  JavaCalls::call_static(&result,
                         vmClasses::MethodHandleNatives_klass(),
                         is_indy ? vmSymbols::linkCallSite_name() : vmSymbols::linkDynamicConstant_name(),
                         is_indy ? vmSymbols::linkCallSite_signature() : vmSymbols::linkDynamicConstant_signature(),
                         &args, CHECK);

  Handle value(THREAD, result.get_oop());
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


bool SystemDictionary::is_nonpublic_Object_method(Method* m) {
  assert(m != nullptr, "Unexpected nullptr Method*");
  return !m->is_public() && m->method_holder() == vmClasses::Object_klass();
}

// ----------------------------------------------------------------------------

void SystemDictionary::print_on(outputStream *st) {
  CDS_ONLY(SystemDictionaryShared::print_on(st));
  GCMutexLocker mu(SystemDictionary_lock);

  ClassLoaderDataGraph::print_dictionary(st);

  // Placeholders
  PlaceholderTable::print_on(st);
  st->cr();

  // loader constraints - print under SD_lock
  LoaderConstraintTable::print_on(st);
  st->cr();

  ProtectionDomainCacheTable::print_on(st);
  st->cr();
}

void SystemDictionary::print() { print_on(tty); }

void SystemDictionary::verify() {

  GCMutexLocker mu(SystemDictionary_lock);

  // Verify dictionary
  ClassLoaderDataGraph::verify_dictionary();

  // Verify constraint table
  LoaderConstraintTable::verify();

  // Verify protection domain table
  ProtectionDomainCacheTable::verify();
}

void SystemDictionary::dump(outputStream *st, bool verbose) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  if (verbose) {
    print_on(st);
  } else {
    CDS_ONLY(SystemDictionaryShared::print_table_statistics(st));
    ClassLoaderDataGraph::print_table_statistics(st);
    LoaderConstraintTable::print_table_statistics(st);
    ProtectionDomainCacheTable::print_table_statistics(st);
  }
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
