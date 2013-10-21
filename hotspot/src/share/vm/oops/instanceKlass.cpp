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
#include "classfile/javaClasses.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/verifier.hpp"
#include "classfile/vmSymbols.hpp"
#include "compiler/compileBroker.hpp"
#include "gc_implementation/shared/markSweep.inline.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "interpreter/oopMapCache.hpp"
#include "interpreter/rewriter.hpp"
#include "jvmtifiles/jvmti.h"
#include "memory/genOopClosures.inline.hpp"
#include "memory/heapInspection.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/oopFactory.hpp"
#include "oops/fieldStreams.hpp"
#include "oops/instanceClassLoaderKlass.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/instanceOop.hpp"
#include "oops/klass.inline.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiRedefineClassesTrace.hpp"
#include "prims/jvmtiRedefineClasses.hpp"
#include "prims/methodComparator.hpp"
#include "runtime/fieldDescriptor.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/thread.inline.hpp"
#include "services/classLoadingService.hpp"
#include "services/threadService.hpp"
#include "utilities/dtrace.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/concurrentMarkSweep/cmsOopClosures.inline.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1OopClosures.inline.hpp"
#include "gc_implementation/g1/g1RemSet.inline.hpp"
#include "gc_implementation/g1/heapRegionSeq.inline.hpp"
#include "gc_implementation/parNew/parOopClosures.inline.hpp"
#include "gc_implementation/parallelScavenge/parallelScavengeHeap.inline.hpp"
#include "gc_implementation/parallelScavenge/psPromotionManager.inline.hpp"
#include "gc_implementation/parallelScavenge/psScavenge.inline.hpp"
#include "oops/oop.pcgc.inline.hpp"
#endif // INCLUDE_ALL_GCS
#ifdef COMPILER1
#include "c1/c1_Compiler.hpp"
#endif

#ifdef DTRACE_ENABLED

#ifndef USDT2

HS_DTRACE_PROBE_DECL4(hotspot, class__initialization__required,
  char*, intptr_t, oop, intptr_t);
HS_DTRACE_PROBE_DECL5(hotspot, class__initialization__recursive,
  char*, intptr_t, oop, intptr_t, int);
HS_DTRACE_PROBE_DECL5(hotspot, class__initialization__concurrent,
  char*, intptr_t, oop, intptr_t, int);
HS_DTRACE_PROBE_DECL5(hotspot, class__initialization__erroneous,
  char*, intptr_t, oop, intptr_t, int);
HS_DTRACE_PROBE_DECL5(hotspot, class__initialization__super__failed,
  char*, intptr_t, oop, intptr_t, int);
HS_DTRACE_PROBE_DECL5(hotspot, class__initialization__clinit,
  char*, intptr_t, oop, intptr_t, int);
HS_DTRACE_PROBE_DECL5(hotspot, class__initialization__error,
  char*, intptr_t, oop, intptr_t, int);
HS_DTRACE_PROBE_DECL5(hotspot, class__initialization__end,
  char*, intptr_t, oop, intptr_t, int);

#define DTRACE_CLASSINIT_PROBE(type, clss, thread_type)          \
  {                                                              \
    char* data = NULL;                                           \
    int len = 0;                                                 \
    Symbol* name = (clss)->name();                               \
    if (name != NULL) {                                          \
      data = (char*)name->bytes();                               \
      len = name->utf8_length();                                 \
    }                                                            \
    HS_DTRACE_PROBE4(hotspot, class__initialization__##type,     \
      data, len, SOLARIS_ONLY((void *))(clss)->class_loader(), thread_type);           \
  }

#define DTRACE_CLASSINIT_PROBE_WAIT(type, clss, thread_type, wait) \
  {                                                              \
    char* data = NULL;                                           \
    int len = 0;                                                 \
    Symbol* name = (clss)->name();                               \
    if (name != NULL) {                                          \
      data = (char*)name->bytes();                               \
      len = name->utf8_length();                                 \
    }                                                            \
    HS_DTRACE_PROBE5(hotspot, class__initialization__##type,     \
      data, len, SOLARIS_ONLY((void *))(clss)->class_loader(), thread_type, wait);     \
  }
#else /* USDT2 */

#define HOTSPOT_CLASS_INITIALIZATION_required HOTSPOT_CLASS_INITIALIZATION_REQUIRED
#define HOTSPOT_CLASS_INITIALIZATION_recursive HOTSPOT_CLASS_INITIALIZATION_RECURSIVE
#define HOTSPOT_CLASS_INITIALIZATION_concurrent HOTSPOT_CLASS_INITIALIZATION_CONCURRENT
#define HOTSPOT_CLASS_INITIALIZATION_erroneous HOTSPOT_CLASS_INITIALIZATION_ERRONEOUS
#define HOTSPOT_CLASS_INITIALIZATION_super__failed HOTSPOT_CLASS_INITIALIZATION_SUPER_FAILED
#define HOTSPOT_CLASS_INITIALIZATION_clinit HOTSPOT_CLASS_INITIALIZATION_CLINIT
#define HOTSPOT_CLASS_INITIALIZATION_error HOTSPOT_CLASS_INITIALIZATION_ERROR
#define HOTSPOT_CLASS_INITIALIZATION_end HOTSPOT_CLASS_INITIALIZATION_END
#define DTRACE_CLASSINIT_PROBE(type, clss, thread_type)          \
  {                                                              \
    char* data = NULL;                                           \
    int len = 0;                                                 \
    Symbol* name = (clss)->name();                               \
    if (name != NULL) {                                          \
      data = (char*)name->bytes();                               \
      len = name->utf8_length();                                 \
    }                                                            \
    HOTSPOT_CLASS_INITIALIZATION_##type(                         \
      data, len, (clss)->class_loader(), thread_type);           \
  }

#define DTRACE_CLASSINIT_PROBE_WAIT(type, clss, thread_type, wait) \
  {                                                              \
    char* data = NULL;                                           \
    int len = 0;                                                 \
    Symbol* name = (clss)->name();                               \
    if (name != NULL) {                                          \
      data = (char*)name->bytes();                               \
      len = name->utf8_length();                                 \
    }                                                            \
    HOTSPOT_CLASS_INITIALIZATION_##type(                         \
      data, len, (clss)->class_loader(), thread_type, wait);     \
  }
#endif /* USDT2 */

#else //  ndef DTRACE_ENABLED

#define DTRACE_CLASSINIT_PROBE(type, clss, thread_type)
#define DTRACE_CLASSINIT_PROBE_WAIT(type, clss, thread_type, wait)

#endif //  ndef DTRACE_ENABLED

volatile int InstanceKlass::_total_instanceKlass_count = 0;

InstanceKlass* InstanceKlass::allocate_instance_klass(
                                              ClassLoaderData* loader_data,
                                              int vtable_len,
                                              int itable_len,
                                              int static_field_size,
                                              int nonstatic_oop_map_size,
                                              ReferenceType rt,
                                              AccessFlags access_flags,
                                              Symbol* name,
                                              Klass* super_klass,
                                              bool is_anonymous,
                                              TRAPS) {

  int size = InstanceKlass::size(vtable_len, itable_len, nonstatic_oop_map_size,
                                 access_flags.is_interface(), is_anonymous);

  // Allocation
  InstanceKlass* ik;
  if (rt == REF_NONE) {
    if (name == vmSymbols::java_lang_Class()) {
      ik = new (loader_data, size, THREAD) InstanceMirrorKlass(
        vtable_len, itable_len, static_field_size, nonstatic_oop_map_size, rt,
        access_flags, is_anonymous);
    } else if (name == vmSymbols::java_lang_ClassLoader() ||
          (SystemDictionary::ClassLoader_klass_loaded() &&
          super_klass != NULL &&
          super_klass->is_subtype_of(SystemDictionary::ClassLoader_klass()))) {
      ik = new (loader_data, size, THREAD) InstanceClassLoaderKlass(
        vtable_len, itable_len, static_field_size, nonstatic_oop_map_size, rt,
        access_flags, is_anonymous);
    } else {
      // normal class
      ik = new (loader_data, size, THREAD) InstanceKlass(
        vtable_len, itable_len, static_field_size, nonstatic_oop_map_size, rt,
        access_flags, is_anonymous);
    }
  } else {
    // reference klass
    ik = new (loader_data, size, THREAD) InstanceRefKlass(
        vtable_len, itable_len, static_field_size, nonstatic_oop_map_size, rt,
        access_flags, is_anonymous);
  }

  // Check for pending exception before adding to the loader data and incrementing
  // class count.  Can get OOM here.
  if (HAS_PENDING_EXCEPTION) {
    return NULL;
  }

  // Add all classes to our internal class loader list here,
  // including classes in the bootstrap (NULL) class loader.
  loader_data->add_class(ik);

  Atomic::inc(&_total_instanceKlass_count);
  return ik;
}


// copy method ordering from resource area to Metaspace
void InstanceKlass::copy_method_ordering(intArray* m, TRAPS) {
  if (m != NULL) {
    // allocate a new array and copy contents (memcpy?)
    _method_ordering = MetadataFactory::new_array<int>(class_loader_data(), m->length(), CHECK);
    for (int i = 0; i < m->length(); i++) {
      _method_ordering->at_put(i, m->at(i));
    }
  } else {
    _method_ordering = Universe::the_empty_int_array();
  }
}

// create a new array of vtable_indices for default methods
Array<int>* InstanceKlass::create_new_default_vtable_indices(int len, TRAPS) {
  Array<int>* vtable_indices = MetadataFactory::new_array<int>(class_loader_data(), len, CHECK_NULL);
  assert(default_vtable_indices() == NULL, "only create once");
  set_default_vtable_indices(vtable_indices);
  return vtable_indices;
}

InstanceKlass::InstanceKlass(int vtable_len,
                             int itable_len,
                             int static_field_size,
                             int nonstatic_oop_map_size,
                             ReferenceType rt,
                             AccessFlags access_flags,
                             bool is_anonymous) {
  No_Safepoint_Verifier no_safepoint; // until k becomes parsable

  int iksize = InstanceKlass::size(vtable_len, itable_len, nonstatic_oop_map_size,
                                   access_flags.is_interface(), is_anonymous);

  set_vtable_length(vtable_len);
  set_itable_length(itable_len);
  set_static_field_size(static_field_size);
  set_nonstatic_oop_map_size(nonstatic_oop_map_size);
  set_access_flags(access_flags);
  _misc_flags = 0;  // initialize to zero
  set_is_anonymous(is_anonymous);
  assert(size() == iksize, "wrong size for object");

  set_array_klasses(NULL);
  set_methods(NULL);
  set_method_ordering(NULL);
  set_default_methods(NULL);
  set_default_vtable_indices(NULL);
  set_local_interfaces(NULL);
  set_transitive_interfaces(NULL);
  init_implementor();
  set_fields(NULL, 0);
  set_constants(NULL);
  set_class_loader_data(NULL);
  set_source_file_name_index(0);
  set_source_debug_extension(NULL, 0);
  set_array_name(NULL);
  set_inner_classes(NULL);
  set_static_oop_field_count(0);
  set_nonstatic_field_size(0);
  set_is_marked_dependent(false);
  set_init_state(InstanceKlass::allocated);
  set_init_thread(NULL);
  set_reference_type(rt);
  set_oop_map_cache(NULL);
  set_jni_ids(NULL);
  set_osr_nmethods_head(NULL);
  set_breakpoints(NULL);
  init_previous_versions();
  set_generic_signature_index(0);
  release_set_methods_jmethod_ids(NULL);
  set_annotations(NULL);
  set_jvmti_cached_class_field_map(NULL);
  set_initial_method_idnum(0);
  _dependencies = NULL;
  set_jvmti_cached_class_field_map(NULL);
  set_cached_class_file(NULL);
  set_initial_method_idnum(0);
  set_minor_version(0);
  set_major_version(0);
  NOT_PRODUCT(_verify_count = 0;)

  // initialize the non-header words to zero
  intptr_t* p = (intptr_t*)this;
  for (int index = InstanceKlass::header_size(); index < iksize; index++) {
    p[index] = NULL_WORD;
  }

  // Set temporary value until parseClassFile updates it with the real instance
  // size.
  set_layout_helper(Klass::instance_layout_helper(0, true));
}


void InstanceKlass::deallocate_methods(ClassLoaderData* loader_data,
                                       Array<Method*>* methods) {
  if (methods != NULL && methods != Universe::the_empty_method_array()) {
    for (int i = 0; i < methods->length(); i++) {
      Method* method = methods->at(i);
      if (method == NULL) continue;  // maybe null if error processing
      // Only want to delete methods that are not executing for RedefineClasses.
      // The previous version will point to them so they're not totally dangling
      assert (!method->on_stack(), "shouldn't be called with methods on stack");
      MetadataFactory::free_metadata(loader_data, method);
    }
    MetadataFactory::free_array<Method*>(loader_data, methods);
  }
}

void InstanceKlass::deallocate_interfaces(ClassLoaderData* loader_data,
                                          Klass* super_klass,
                                          Array<Klass*>* local_interfaces,
                                          Array<Klass*>* transitive_interfaces) {
  // Only deallocate transitive interfaces if not empty, same as super class
  // or same as local interfaces.  See code in parseClassFile.
  Array<Klass*>* ti = transitive_interfaces;
  if (ti != Universe::the_empty_klass_array() && ti != local_interfaces) {
    // check that the interfaces don't come from super class
    Array<Klass*>* sti = (super_klass == NULL) ? NULL :
                    InstanceKlass::cast(super_klass)->transitive_interfaces();
    if (ti != sti) {
      MetadataFactory::free_array<Klass*>(loader_data, ti);
    }
  }

  // local interfaces can be empty
  if (local_interfaces != Universe::the_empty_klass_array()) {
    MetadataFactory::free_array<Klass*>(loader_data, local_interfaces);
  }
}

// This function deallocates the metadata and C heap pointers that the
// InstanceKlass points to.
void InstanceKlass::deallocate_contents(ClassLoaderData* loader_data) {

  // Orphan the mirror first, CMS thinks it's still live.
  if (java_mirror() != NULL) {
    java_lang_Class::set_klass(java_mirror(), NULL);
  }

  // Need to take this class off the class loader data list.
  loader_data->remove_class(this);

  // The array_klass for this class is created later, after error handling.
  // For class redefinition, we keep the original class so this scratch class
  // doesn't have an array class.  Either way, assert that there is nothing
  // to deallocate.
  assert(array_klasses() == NULL, "array classes shouldn't be created for this class yet");

  // Release C heap allocated data that this might point to, which includes
  // reference counting symbol names.
  release_C_heap_structures();

  deallocate_methods(loader_data, methods());
  set_methods(NULL);

  if (method_ordering() != Universe::the_empty_int_array()) {
    MetadataFactory::free_array<int>(loader_data, method_ordering());
  }
  set_method_ordering(NULL);

  // default methods can be empty
  if (default_methods() != NULL &&
      default_methods() != Universe::the_empty_method_array()) {
    MetadataFactory::free_array<Method*>(loader_data, default_methods());
  }
  // Do NOT deallocate the default methods, they are owned by superinterfaces.
  set_default_methods(NULL);

  // default methods vtable indices can be empty
  if (default_vtable_indices() != NULL) {
    MetadataFactory::free_array<int>(loader_data, default_vtable_indices());
  }
  set_default_vtable_indices(NULL);


  // This array is in Klass, but remove it with the InstanceKlass since
  // this place would be the only caller and it can share memory with transitive
  // interfaces.
  if (secondary_supers() != Universe::the_empty_klass_array() &&
      secondary_supers() != transitive_interfaces()) {
    MetadataFactory::free_array<Klass*>(loader_data, secondary_supers());
  }
  set_secondary_supers(NULL);

  deallocate_interfaces(loader_data, super(), local_interfaces(), transitive_interfaces());
  set_transitive_interfaces(NULL);
  set_local_interfaces(NULL);

  MetadataFactory::free_array<jushort>(loader_data, fields());
  set_fields(NULL, 0);

  // If a method from a redefined class is using this constant pool, don't
  // delete it, yet.  The new class's previous version will point to this.
  if (constants() != NULL) {
    assert (!constants()->on_stack(), "shouldn't be called if anything is onstack");
    MetadataFactory::free_metadata(loader_data, constants());
    set_constants(NULL);
  }

  if (inner_classes() != Universe::the_empty_short_array()) {
    MetadataFactory::free_array<jushort>(loader_data, inner_classes());
  }
  set_inner_classes(NULL);

  // We should deallocate the Annotations instance
  MetadataFactory::free_metadata(loader_data, annotations());
  set_annotations(NULL);
}

bool InstanceKlass::should_be_initialized() const {
  return !is_initialized();
}

klassVtable* InstanceKlass::vtable() const {
  return new klassVtable(this, start_of_vtable(), vtable_length() / vtableEntry::size());
}

klassItable* InstanceKlass::itable() const {
  return new klassItable(instanceKlassHandle(this));
}

void InstanceKlass::eager_initialize(Thread *thread) {
  if (!EagerInitialization) return;

  if (this->is_not_initialized()) {
    // abort if the the class has a class initializer
    if (this->class_initializer() != NULL) return;

    // abort if it is java.lang.Object (initialization is handled in genesis)
    Klass* super = this->super();
    if (super == NULL) return;

    // abort if the super class should be initialized
    if (!InstanceKlass::cast(super)->is_initialized()) return;

    // call body to expose the this pointer
    instanceKlassHandle this_oop(thread, this);
    eager_initialize_impl(this_oop);
  }
}

// JVMTI spec thinks there are signers and protection domain in the
// instanceKlass.  These accessors pretend these fields are there.
// The hprof specification also thinks these fields are in InstanceKlass.
oop InstanceKlass::protection_domain() const {
  // return the protection_domain from the mirror
  return java_lang_Class::protection_domain(java_mirror());
}

// To remove these from requires an incompatible change and CCC request.
objArrayOop InstanceKlass::signers() const {
  // return the signers from the mirror
  return java_lang_Class::signers(java_mirror());
}

oop InstanceKlass::init_lock() const {
  // return the init lock from the mirror
  return java_lang_Class::init_lock(java_mirror());
}

void InstanceKlass::eager_initialize_impl(instanceKlassHandle this_oop) {
  EXCEPTION_MARK;
  oop init_lock = this_oop->init_lock();
  ObjectLocker ol(init_lock, THREAD);

  // abort if someone beat us to the initialization
  if (!this_oop->is_not_initialized()) return;  // note: not equivalent to is_initialized()

  ClassState old_state = this_oop->init_state();
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
void InstanceKlass::initialize(TRAPS) {
  if (this->should_be_initialized()) {
    HandleMark hm(THREAD);
    instanceKlassHandle this_oop(THREAD, this);
    initialize_impl(this_oop, CHECK);
    // Note: at this point the class may be initialized
    //       OR it may be in the state of being initialized
    //       in case of recursive initialization!
  } else {
    assert(is_initialized(), "sanity check");
  }
}


bool InstanceKlass::verify_code(
    instanceKlassHandle this_oop, bool throw_verifyerror, TRAPS) {
  // 1) Verify the bytecodes
  Verifier::Mode mode =
    throw_verifyerror ? Verifier::ThrowException : Verifier::NoException;
  return Verifier::verify(this_oop, mode, this_oop->should_verify_class(), CHECK_false);
}


// Used exclusively by the shared spaces dump mechanism to prevent
// classes mapped into the shared regions in new VMs from appearing linked.

void InstanceKlass::unlink_class() {
  assert(is_linked(), "must be linked");
  _init_state = loaded;
}

void InstanceKlass::link_class(TRAPS) {
  assert(is_loaded(), "must be loaded");
  if (!is_linked()) {
    HandleMark hm(THREAD);
    instanceKlassHandle this_oop(THREAD, this);
    link_class_impl(this_oop, true, CHECK);
  }
}

// Called to verify that a class can link during initialization, without
// throwing a VerifyError.
bool InstanceKlass::link_class_or_fail(TRAPS) {
  assert(is_loaded(), "must be loaded");
  if (!is_linked()) {
    HandleMark hm(THREAD);
    instanceKlassHandle this_oop(THREAD, this);
    link_class_impl(this_oop, false, CHECK_false);
  }
  return is_linked();
}

bool InstanceKlass::link_class_impl(
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
        vmSymbols::java_lang_IncompatibleClassChangeError(),
        "class %s has interface %s as super class",
        this_oop->external_name(),
        super->external_name()
      );
      return false;
    }

    link_class_impl(super, throw_verifyerror, CHECK_false);
  }

  // link all interfaces implemented by this class before linking this class
  Array<Klass*>* interfaces = this_oop->local_interfaces();
  int num_interfaces = interfaces->length();
  for (int index = 0; index < num_interfaces; index++) {
    HandleMark hm(THREAD);
    instanceKlassHandle ih(THREAD, interfaces->at(index));
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
    oop init_lock = this_oop->init_lock();
    ObjectLocker ol(init_lock, THREAD);
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

      // relocate jsrs and link methods after they are all rewritten
      this_oop->link_methods(CHECK_false);

      // Initialize the vtable and interface table after
      // methods have been rewritten since rewrite may
      // fabricate new Method*s.
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
// The rewriter must be called exactly once. Rewriting must happen after
// verification but before the first method of the class is executed.
void InstanceKlass::rewrite_class(TRAPS) {
  assert(is_loaded(), "must be loaded");
  instanceKlassHandle this_oop(THREAD, this);
  if (this_oop->is_rewritten()) {
    assert(this_oop()->is_shared(), "rewriting an unshared class?");
    return;
  }
  Rewriter::rewrite(this_oop, CHECK);
  this_oop->set_rewritten();
}

// Now relocate and link method entry points after class is rewritten.
// This is outside is_rewritten flag. In case of an exception, it can be
// executed more than once.
void InstanceKlass::link_methods(TRAPS) {
  int len = methods()->length();
  for (int i = len-1; i >= 0; i--) {
    methodHandle m(THREAD, methods()->at(i));

    // Set up method entry points for compiler and interpreter    .
    m->link_method(m, CHECK);

    // This is for JVMTI and unrelated to relocator but the last thing we do
#ifdef ASSERT
    if (StressMethodComparator) {
      ResourceMark rm(THREAD);
      static int nmc = 0;
      for (int j = i; j >= 0 && j >= i-4; j--) {
        if ((++nmc % 1000) == 0)  tty->print_cr("Have run MethodComparator %d times...", nmc);
        bool z = MethodComparator::methods_EMCP(m(),
                   methods()->at(j));
        if (j == i && !z) {
          tty->print("MethodComparator FAIL: "); m->print(); m->print_codes();
          assert(z, "method must compare equal to itself");
        }
      }
    }
#endif //ASSERT
  }
}


void InstanceKlass::initialize_impl(instanceKlassHandle this_oop, TRAPS) {
  // Make sure klass is linked (verified) before initialization
  // A class could already be verified, since it has been reflected upon.
  this_oop->link_class(CHECK);

  DTRACE_CLASSINIT_PROBE(required, InstanceKlass::cast(this_oop()), -1);

  bool wait = false;

  // refer to the JVM book page 47 for description of steps
  // Step 1
  {
    oop init_lock = this_oop->init_lock();
    ObjectLocker ol(init_lock, THREAD);

    Thread *self = THREAD; // it's passed the current thread

    // Step 2
    // If we were to use wait() instead of waitInterruptibly() then
    // we might end up throwing IE from link/symbol resolution sites
    // that aren't expected to throw.  This would wreak havoc.  See 6320309.
    while(this_oop->is_being_initialized() && !this_oop->is_reentrant_initialization(self)) {
        wait = true;
      ol.waitUninterruptibly(CHECK);
    }

    // Step 3
    if (this_oop->is_being_initialized() && this_oop->is_reentrant_initialization(self)) {
      DTRACE_CLASSINIT_PROBE_WAIT(recursive, InstanceKlass::cast(this_oop()), -1,wait);
      return;
    }

    // Step 4
    if (this_oop->is_initialized()) {
      DTRACE_CLASSINIT_PROBE_WAIT(concurrent, InstanceKlass::cast(this_oop()), -1,wait);
      return;
    }

    // Step 5
    if (this_oop->is_in_error_state()) {
      DTRACE_CLASSINIT_PROBE_WAIT(erroneous, InstanceKlass::cast(this_oop()), -1,wait);
      ResourceMark rm(THREAD);
      const char* desc = "Could not initialize class ";
      const char* className = this_oop->external_name();
      size_t msglen = strlen(desc) + strlen(className) + 1;
      char* message = NEW_RESOURCE_ARRAY(char, msglen);
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
  Klass* super_klass = this_oop->super();
  if (super_klass != NULL && !this_oop->is_interface() && super_klass->should_be_initialized()) {
    super_klass->initialize(THREAD);

    if (HAS_PENDING_EXCEPTION) {
      Handle e(THREAD, PENDING_EXCEPTION);
      CLEAR_PENDING_EXCEPTION;
      {
        EXCEPTION_MARK;
        this_oop->set_initialization_state_and_notify(initialization_error, THREAD); // Locks object, set state, and notify all waiting threads
        CLEAR_PENDING_EXCEPTION;   // ignore any exception thrown, superclass initialization error is thrown below
      }
      DTRACE_CLASSINIT_PROBE_WAIT(super__failed, InstanceKlass::cast(this_oop()), -1,wait);
      THROW_OOP(e());
    }
  }

  if (this_oop->has_default_methods()) {
    // Step 7.5: initialize any interfaces which have default methods
    for (int i = 0; i < this_oop->local_interfaces()->length(); ++i) {
      Klass* iface = this_oop->local_interfaces()->at(i);
      InstanceKlass* ik = InstanceKlass::cast(iface);
      if (ik->has_default_methods() && ik->should_be_initialized()) {
        ik->initialize(THREAD);

        if (HAS_PENDING_EXCEPTION) {
          Handle e(THREAD, PENDING_EXCEPTION);
          CLEAR_PENDING_EXCEPTION;
          {
            EXCEPTION_MARK;
            // Locks object, set state, and notify all waiting threads
            this_oop->set_initialization_state_and_notify(
                initialization_error, THREAD);

            // ignore any exception thrown, superclass initialization error is
            // thrown below
            CLEAR_PENDING_EXCEPTION;
          }
          DTRACE_CLASSINIT_PROBE_WAIT(
              super__failed, InstanceKlass::cast(this_oop()), -1, wait);
          THROW_OOP(e());
        }
      }
    }
  }

  // Step 8
  {
    assert(THREAD->is_Java_thread(), "non-JavaThread in initialize_impl");
    JavaThread* jt = (JavaThread*)THREAD;
    DTRACE_CLASSINIT_PROBE_WAIT(clinit, InstanceKlass::cast(this_oop()), -1,wait);
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
    DTRACE_CLASSINIT_PROBE_WAIT(error, InstanceKlass::cast(this_oop()), -1,wait);
    if (e->is_a(SystemDictionary::Error_klass())) {
      THROW_OOP(e());
    } else {
      JavaCallArguments args(e);
      THROW_ARG(vmSymbols::java_lang_ExceptionInInitializerError(),
                vmSymbols::throwable_void_signature(),
                &args);
    }
  }
  DTRACE_CLASSINIT_PROBE_WAIT(end, InstanceKlass::cast(this_oop()), -1,wait);
}


// Note: implementation moved to static method to expose the this pointer.
void InstanceKlass::set_initialization_state_and_notify(ClassState state, TRAPS) {
  instanceKlassHandle kh(THREAD, this);
  set_initialization_state_and_notify_impl(kh, state, CHECK);
}

void InstanceKlass::set_initialization_state_and_notify_impl(instanceKlassHandle this_oop, ClassState state, TRAPS) {
  oop init_lock = this_oop->init_lock();
  ObjectLocker ol(init_lock, THREAD);
  this_oop->set_init_state(state);
  ol.notify_all(CHECK);
}

// The embedded _implementor field can only record one implementor.
// When there are more than one implementors, the _implementor field
// is set to the interface Klass* itself. Following are the possible
// values for the _implementor field:
//   NULL                  - no implementor
//   implementor Klass*    - one implementor
//   self                  - more than one implementor
//
// The _implementor field only exists for interfaces.
void InstanceKlass::add_implementor(Klass* k) {
  assert(Compile_lock->owned_by_self(), "");
  assert(is_interface(), "not interface");
  // Filter out my subinterfaces.
  // (Note: Interfaces are never on the subklass list.)
  if (InstanceKlass::cast(k)->is_interface()) return;

  // Filter out subclasses whose supers already implement me.
  // (Note: CHA must walk subclasses of direct implementors
  // in order to locate indirect implementors.)
  Klass* sk = InstanceKlass::cast(k)->super();
  if (sk != NULL && InstanceKlass::cast(sk)->implements_interface(this))
    // We only need to check one immediate superclass, since the
    // implements_interface query looks at transitive_interfaces.
    // Any supers of the super have the same (or fewer) transitive_interfaces.
    return;

  Klass* ik = implementor();
  if (ik == NULL) {
    set_implementor(k);
  } else if (ik != this) {
    // There is already an implementor. Use itself as an indicator of
    // more than one implementors.
    set_implementor(this);
  }

  // The implementor also implements the transitive_interfaces
  for (int index = 0; index < local_interfaces()->length(); index++) {
    InstanceKlass::cast(local_interfaces()->at(index))->add_implementor(k);
  }
}

void InstanceKlass::init_implementor() {
  if (is_interface()) {
    set_implementor(NULL);
  }
}


void InstanceKlass::process_interfaces(Thread *thread) {
  // link this class into the implementors list of every interface it implements
  Klass* this_as_klass_oop = this;
  for (int i = local_interfaces()->length() - 1; i >= 0; i--) {
    assert(local_interfaces()->at(i)->is_klass(), "must be a klass");
    InstanceKlass* interf = InstanceKlass::cast(local_interfaces()->at(i));
    assert(interf->is_interface(), "expected interface");
    interf->add_implementor(this_as_klass_oop);
  }
}

bool InstanceKlass::can_be_primary_super_slow() const {
  if (is_interface())
    return false;
  else
    return Klass::can_be_primary_super_slow();
}

GrowableArray<Klass*>* InstanceKlass::compute_secondary_supers(int num_extra_slots) {
  // The secondaries are the implemented interfaces.
  InstanceKlass* ik = InstanceKlass::cast(this);
  Array<Klass*>* interfaces = ik->transitive_interfaces();
  int num_secondaries = num_extra_slots + interfaces->length();
  if (num_secondaries == 0) {
    // Must share this for correct bootstrapping!
    set_secondary_supers(Universe::the_empty_klass_array());
    return NULL;
  } else if (num_extra_slots == 0) {
    // The secondary super list is exactly the same as the transitive interfaces.
    // Redefine classes has to be careful not to delete this!
    set_secondary_supers(interfaces);
    return NULL;
  } else {
    // Copy transitive interfaces to a temporary growable array to be constructed
    // into the secondary super list with extra slots.
    GrowableArray<Klass*>* secondaries = new GrowableArray<Klass*>(interfaces->length());
    for (int i = 0; i < interfaces->length(); i++) {
      secondaries->push(interfaces->at(i));
    }
    return secondaries;
  }
}

bool InstanceKlass::compute_is_subtype_of(Klass* k) {
  if (k->is_interface()) {
    return implements_interface(k);
  } else {
    return Klass::compute_is_subtype_of(k);
  }
}

bool InstanceKlass::implements_interface(Klass* k) const {
  if (this == k) return true;
  assert(k->is_interface(), "should be an interface class");
  for (int i = 0; i < transitive_interfaces()->length(); i++) {
    if (transitive_interfaces()->at(i) == k) {
      return true;
    }
  }
  return false;
}

objArrayOop InstanceKlass::allocate_objArray(int n, int length, TRAPS) {
  if (length < 0) THROW_0(vmSymbols::java_lang_NegativeArraySizeException());
  if (length > arrayOopDesc::max_array_length(T_OBJECT)) {
    report_java_out_of_memory("Requested array size exceeds VM limit");
    JvmtiExport::post_array_size_exhausted();
    THROW_OOP_0(Universe::out_of_memory_error_array_size());
  }
  int size = objArrayOopDesc::object_size(length);
  Klass* ak = array_klass(n, CHECK_NULL);
  KlassHandle h_ak (THREAD, ak);
  objArrayOop o =
    (objArrayOop)CollectedHeap::array_allocate(h_ak, size, length, CHECK_NULL);
  return o;
}

instanceOop InstanceKlass::register_finalizer(instanceOop i, TRAPS) {
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

instanceOop InstanceKlass::allocate_instance(TRAPS) {
  bool has_finalizer_flag = has_finalizer(); // Query before possible GC
  int size = size_helper();  // Query before forming handle.

  KlassHandle h_k(THREAD, this);

  instanceOop i;

  i = (instanceOop)CollectedHeap::obj_allocate(h_k, size, CHECK_NULL);
  if (has_finalizer_flag && !RegisterFinalizersAtInit) {
    i = register_finalizer(i, CHECK_NULL);
  }
  return i;
}

void InstanceKlass::check_valid_for_instantiation(bool throwError, TRAPS) {
  if (is_interface() || is_abstract()) {
    ResourceMark rm(THREAD);
    THROW_MSG(throwError ? vmSymbols::java_lang_InstantiationError()
              : vmSymbols::java_lang_InstantiationException(), external_name());
  }
  if (this == SystemDictionary::Class_klass()) {
    ResourceMark rm(THREAD);
    THROW_MSG(throwError ? vmSymbols::java_lang_IllegalAccessError()
              : vmSymbols::java_lang_IllegalAccessException(), external_name());
  }
}

Klass* InstanceKlass::array_klass_impl(bool or_null, int n, TRAPS) {
  instanceKlassHandle this_oop(THREAD, this);
  return array_klass_impl(this_oop, or_null, n, THREAD);
}

Klass* InstanceKlass::array_klass_impl(instanceKlassHandle this_oop, bool or_null, int n, TRAPS) {
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
        Klass*    k = ObjArrayKlass::allocate_objArray_klass(this_oop->class_loader_data(), 1, this_oop, CHECK_NULL);
        this_oop->set_array_klasses(k);
      }
    }
  }
  // _this will always be set at this point
  ObjArrayKlass* oak = (ObjArrayKlass*)this_oop->array_klasses();
  if (or_null) {
    return oak->array_klass_or_null(n);
  }
  return oak->array_klass(n, CHECK_NULL);
}

Klass* InstanceKlass::array_klass_impl(bool or_null, TRAPS) {
  return array_klass_impl(or_null, 1, THREAD);
}

void InstanceKlass::call_class_initializer(TRAPS) {
  instanceKlassHandle ik (THREAD, this);
  call_class_initializer_impl(ik, THREAD);
}

static int call_class_initializer_impl_counter = 0;   // for debugging

Method* InstanceKlass::class_initializer() {
  Method* clinit = find_method(
      vmSymbols::class_initializer_name(), vmSymbols::void_method_signature());
  if (clinit != NULL && clinit->has_valid_initializer_flags()) {
    return clinit;
  }
  return NULL;
}

void InstanceKlass::call_class_initializer_impl(instanceKlassHandle this_oop, TRAPS) {
  if (ReplayCompiles &&
      (ReplaySuppressInitializers == 1 ||
       ReplaySuppressInitializers >= 2 && this_oop->class_loader() != NULL)) {
    // Hide the existence of the initializer for the purpose of replaying the compile
    return;
  }

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


void InstanceKlass::mask_for(methodHandle method, int bci,
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


bool InstanceKlass::find_local_field(Symbol* name, Symbol* sig, fieldDescriptor* fd) const {
  for (JavaFieldStream fs(this); !fs.done(); fs.next()) {
    Symbol* f_name = fs.name();
    Symbol* f_sig  = fs.signature();
    if (f_name == name && f_sig == sig) {
      fd->reinitialize(const_cast<InstanceKlass*>(this), fs.index());
      return true;
    }
  }
  return false;
}


Klass* InstanceKlass::find_interface_field(Symbol* name, Symbol* sig, fieldDescriptor* fd) const {
  const int n = local_interfaces()->length();
  for (int i = 0; i < n; i++) {
    Klass* intf1 = local_interfaces()->at(i);
    assert(intf1->is_interface(), "just checking type");
    // search for field in current interface
    if (InstanceKlass::cast(intf1)->find_local_field(name, sig, fd)) {
      assert(fd->is_static(), "interface field must be static");
      return intf1;
    }
    // search for field in direct superinterfaces
    Klass* intf2 = InstanceKlass::cast(intf1)->find_interface_field(name, sig, fd);
    if (intf2 != NULL) return intf2;
  }
  // otherwise field lookup fails
  return NULL;
}


Klass* InstanceKlass::find_field(Symbol* name, Symbol* sig, fieldDescriptor* fd) const {
  // search order according to newest JVM spec (5.4.3.2, p.167).
  // 1) search for field in current klass
  if (find_local_field(name, sig, fd)) {
    return const_cast<InstanceKlass*>(this);
  }
  // 2) search for field recursively in direct superinterfaces
  { Klass* intf = find_interface_field(name, sig, fd);
    if (intf != NULL) return intf;
  }
  // 3) apply field lookup recursively if superclass exists
  { Klass* supr = super();
    if (supr != NULL) return InstanceKlass::cast(supr)->find_field(name, sig, fd);
  }
  // 4) otherwise field lookup fails
  return NULL;
}


Klass* InstanceKlass::find_field(Symbol* name, Symbol* sig, bool is_static, fieldDescriptor* fd) const {
  // search order according to newest JVM spec (5.4.3.2, p.167).
  // 1) search for field in current klass
  if (find_local_field(name, sig, fd)) {
    if (fd->is_static() == is_static) return const_cast<InstanceKlass*>(this);
  }
  // 2) search for field recursively in direct superinterfaces
  if (is_static) {
    Klass* intf = find_interface_field(name, sig, fd);
    if (intf != NULL) return intf;
  }
  // 3) apply field lookup recursively if superclass exists
  { Klass* supr = super();
    if (supr != NULL) return InstanceKlass::cast(supr)->find_field(name, sig, is_static, fd);
  }
  // 4) otherwise field lookup fails
  return NULL;
}


bool InstanceKlass::find_local_field_from_offset(int offset, bool is_static, fieldDescriptor* fd) const {
  for (JavaFieldStream fs(this); !fs.done(); fs.next()) {
    if (fs.offset() == offset) {
      fd->reinitialize(const_cast<InstanceKlass*>(this), fs.index());
      if (fd->is_static() == is_static) return true;
    }
  }
  return false;
}


bool InstanceKlass::find_field_from_offset(int offset, bool is_static, fieldDescriptor* fd) const {
  Klass* klass = const_cast<InstanceKlass*>(this);
  while (klass != NULL) {
    if (InstanceKlass::cast(klass)->find_local_field_from_offset(offset, is_static, fd)) {
      return true;
    }
    klass = klass->super();
  }
  return false;
}


void InstanceKlass::methods_do(void f(Method* method)) {
  int len = methods()->length();
  for (int index = 0; index < len; index++) {
    Method* m = methods()->at(index);
    assert(m->is_method(), "must be method");
    f(m);
  }
}


void InstanceKlass::do_local_static_fields(FieldClosure* cl) {
  for (JavaFieldStream fs(this); !fs.done(); fs.next()) {
    if (fs.access_flags().is_static()) {
      fieldDescriptor& fd = fs.field_descriptor();
      cl->do_field(&fd);
    }
  }
}


void InstanceKlass::do_local_static_fields(void f(fieldDescriptor*, TRAPS), TRAPS) {
  instanceKlassHandle h_this(THREAD, this);
  do_local_static_fields_impl(h_this, f, CHECK);
}


void InstanceKlass::do_local_static_fields_impl(instanceKlassHandle this_oop, void f(fieldDescriptor* fd, TRAPS), TRAPS) {
  for (JavaFieldStream fs(this_oop()); !fs.done(); fs.next()) {
    if (fs.access_flags().is_static()) {
      fieldDescriptor& fd = fs.field_descriptor();
      f(&fd, CHECK);
    }
  }
}


static int compare_fields_by_offset(int* a, int* b) {
  return a[0] - b[0];
}

void InstanceKlass::do_nonstatic_fields(FieldClosure* cl) {
  InstanceKlass* super = superklass();
  if (super != NULL) {
    super->do_nonstatic_fields(cl);
  }
  fieldDescriptor fd;
  int length = java_fields_count();
  // In DebugInfo nonstatic fields are sorted by offset.
  int* fields_sorted = NEW_C_HEAP_ARRAY(int, 2*(length+1), mtClass);
  int j = 0;
  for (int i = 0; i < length; i += 1) {
    fd.reinitialize(this, i);
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
      fd.reinitialize(this, fields_sorted[i + 1]);
      assert(!fd.is_static() && fd.offset() == fields_sorted[i], "only nonstatic fields");
      cl->do_field(&fd);
    }
  }
  FREE_C_HEAP_ARRAY(int, fields_sorted, mtClass);
}


void InstanceKlass::array_klasses_do(void f(Klass* k, TRAPS), TRAPS) {
  if (array_klasses() != NULL)
    ArrayKlass::cast(array_klasses())->array_klasses_do(f, THREAD);
}

void InstanceKlass::array_klasses_do(void f(Klass* k)) {
  if (array_klasses() != NULL)
    ArrayKlass::cast(array_klasses())->array_klasses_do(f);
}

#ifdef ASSERT
static int linear_search(Array<Method*>* methods, Symbol* name, Symbol* signature) {
  int len = methods->length();
  for (int index = 0; index < len; index++) {
    Method* m = methods->at(index);
    assert(m->is_method(), "must be method");
    if (m->signature() == signature && m->name() == name) {
       return index;
    }
  }
  return -1;
}
#endif

static int binary_search(Array<Method*>* methods, Symbol* name) {
  int len = methods->length();
  // methods are sorted, so do binary search
  int l = 0;
  int h = len - 1;
  while (l <= h) {
    int mid = (l + h) >> 1;
    Method* m = methods->at(mid);
    assert(m->is_method(), "must be method");
    int res = m->name()->fast_compare(name);
    if (res == 0) {
      return mid;
    } else if (res < 0) {
      l = mid + 1;
    } else {
      h = mid - 1;
    }
  }
  return -1;
}

// find_method looks up the name/signature in the local methods array
Method* InstanceKlass::find_method(Symbol* name, Symbol* signature) const {
  return InstanceKlass::find_method(methods(), name, signature);
}

// find_method looks up the name/signature in the local methods array
Method* InstanceKlass::find_method(
    Array<Method*>* methods, Symbol* name, Symbol* signature) {
  int hit = find_method_index(methods, name, signature);
  return hit >= 0 ? methods->at(hit): NULL;
}

// Used directly for default_methods to find the index into the
// default_vtable_indices, and indirectly by find_method
// find_method_index looks in the local methods array to return the index
// of the matching name/signature
int InstanceKlass::find_method_index(
    Array<Method*>* methods, Symbol* name, Symbol* signature) {
  int hit = binary_search(methods, name);
  if (hit != -1) {
    Method* m = methods->at(hit);
    // Do linear search to find matching signature.  First, quick check
    // for common case
    if (m->signature() == signature) return hit;
    // search downwards through overloaded methods
    int i;
    for (i = hit - 1; i >= 0; --i) {
        Method* m = methods->at(i);
        assert(m->is_method(), "must be method");
        if (m->name() != name) break;
        if (m->signature() == signature) return i;
    }
    // search upwards
    for (i = hit + 1; i < methods->length(); ++i) {
        Method* m = methods->at(i);
        assert(m->is_method(), "must be method");
        if (m->name() != name) break;
        if (m->signature() == signature) return i;
    }
    // not found
#ifdef ASSERT
    int index = linear_search(methods, name, signature);
    assert(index == -1, err_msg("binary search should have found entry %d", index));
#endif
  }
  return -1;
}
int InstanceKlass::find_method_by_name(Symbol* name, int* end) {
  return find_method_by_name(methods(), name, end);
}

int InstanceKlass::find_method_by_name(
    Array<Method*>* methods, Symbol* name, int* end_ptr) {
  assert(end_ptr != NULL, "just checking");
  int start = binary_search(methods, name);
  int end = start + 1;
  if (start != -1) {
    while (start - 1 >= 0 && (methods->at(start - 1))->name() == name) --start;
    while (end < methods->length() && (methods->at(end))->name() == name) ++end;
    *end_ptr = end;
    return start;
  }
  return -1;
}

// lookup_method searches both the local methods array and all superclasses methods arrays
Method* InstanceKlass::uncached_lookup_method(Symbol* name, Symbol* signature) const {
  Klass* klass = const_cast<InstanceKlass*>(this);
  while (klass != NULL) {
    Method* method = InstanceKlass::cast(klass)->find_method(name, signature);
    if (method != NULL) return method;
    klass = InstanceKlass::cast(klass)->super();
  }
  return NULL;
}

// lookup a method in the default methods list then in all transitive interfaces
// Do NOT return private or static methods
Method* InstanceKlass::lookup_method_in_ordered_interfaces(Symbol* name,
                                                         Symbol* signature) const {
  Method* m = NULL;
  if (default_methods() != NULL) {
    m = find_method(default_methods(), name, signature);
  }
  // Look up interfaces
  if (m == NULL) {
    m = lookup_method_in_all_interfaces(name, signature);
  }
  return m;
}

// lookup a method in all the interfaces that this class implements
// Do NOT return private or static methods, new in JDK8 which are not externally visible
// They should only be found in the initial InterfaceMethodRef
Method* InstanceKlass::lookup_method_in_all_interfaces(Symbol* name,
                                                         Symbol* signature) const {
  Array<Klass*>* all_ifs = transitive_interfaces();
  int num_ifs = all_ifs->length();
  InstanceKlass *ik = NULL;
  for (int i = 0; i < num_ifs; i++) {
    ik = InstanceKlass::cast(all_ifs->at(i));
    Method* m = ik->lookup_method(name, signature);
    if (m != NULL && m->is_public() && !m->is_static()) {
      return m;
    }
  }
  return NULL;
}

/* jni_id_for_impl for jfieldIds only */
JNIid* InstanceKlass::jni_id_for_impl(instanceKlassHandle this_oop, int offset) {
  MutexLocker ml(JfieldIdCreation_lock);
  // Retry lookup after we got the lock
  JNIid* probe = this_oop->jni_ids() == NULL ? NULL : this_oop->jni_ids()->find(offset);
  if (probe == NULL) {
    // Slow case, allocate new static field identifier
    probe = new JNIid(this_oop(), offset, this_oop->jni_ids());
    this_oop->set_jni_ids(probe);
  }
  return probe;
}


/* jni_id_for for jfieldIds only */
JNIid* InstanceKlass::jni_id_for(int offset) {
  JNIid* probe = jni_ids() == NULL ? NULL : jni_ids()->find(offset);
  if (probe == NULL) {
    probe = jni_id_for_impl(this, offset);
  }
  return probe;
}

u2 InstanceKlass::enclosing_method_data(int offset) {
  Array<jushort>* inner_class_list = inner_classes();
  if (inner_class_list == NULL) {
    return 0;
  }
  int length = inner_class_list->length();
  if (length % inner_class_next_offset == 0) {
    return 0;
  } else {
    int index = length - enclosing_method_attribute_size;
    assert(offset < enclosing_method_attribute_size, "invalid offset");
    return inner_class_list->at(index + offset);
  }
}

void InstanceKlass::set_enclosing_method_indices(u2 class_index,
                                                 u2 method_index) {
  Array<jushort>* inner_class_list = inner_classes();
  assert (inner_class_list != NULL, "_inner_classes list is not set up");
  int length = inner_class_list->length();
  if (length % inner_class_next_offset == enclosing_method_attribute_size) {
    int index = length - enclosing_method_attribute_size;
    inner_class_list->at_put(
      index + enclosing_method_class_index_offset, class_index);
    inner_class_list->at_put(
      index + enclosing_method_method_index_offset, method_index);
  }
}

// Lookup or create a jmethodID.
// This code is called by the VMThread and JavaThreads so the
// locking has to be done very carefully to avoid deadlocks
// and/or other cache consistency problems.
//
jmethodID InstanceKlass::get_jmethod_id(instanceKlassHandle ik_h, methodHandle method_h) {
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
      new_jmeths = NEW_C_HEAP_ARRAY(jmethodID, size+1, mtClass);
      memset(new_jmeths, 0, (size+1)*sizeof(jmethodID));
      // cache size is stored in element[0], other elements offset by one
      new_jmeths[0] = (jmethodID)size;
    }

    // allocate a new jmethodID that might be used
    jmethodID new_id = NULL;
    if (method_h->is_old() && !method_h->is_obsolete()) {
      // The method passed in is old (but not obsolete), we need to use the current version
      Method* current_method = ik_h->method_with_idnum((int)idnum);
      assert(current_method != NULL, "old and but not obsolete, so should exist");
      new_id = Method::make_jmethod_id(ik_h->class_loader_data(), current_method);
    } else {
      // It is the current version of the method or an obsolete method,
      // use the version passed in
      new_id = Method::make_jmethod_id(ik_h->class_loader_data(), method_h());
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
      Method::destroy_jmethod_id(ik_h->class_loader_data(), to_dealloc_id);
    }
  }
  return id;
}


// Common code to fetch the jmethodID from the cache or update the
// cache with the new jmethodID. This function should never do anything
// that causes the caller to go to a safepoint or we can deadlock with
// the VMThread or have cache consistency issues.
//
jmethodID InstanceKlass::get_jmethod_id_fetch_or_update(
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
void InstanceKlass::get_jmethod_id_length_value(jmethodID* cache,
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
jmethodID InstanceKlass::jmethod_id_or_null(Method* method) {
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


//
// Walk the list of dependent nmethods searching for nmethods which
// are dependent on the changes that were passed in and mark them for
// deoptimization.  Returns the number of nmethods found.
//
int InstanceKlass::mark_dependent_nmethods(DepChange& changes) {
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
void InstanceKlass::add_dependent_nmethod(nmethod* nm) {
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
void InstanceKlass::remove_dependent_nmethod(nmethod* nm) {
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
void InstanceKlass::print_dependent_nmethods(bool verbose) {
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


bool InstanceKlass::is_dependent_nmethod(nmethod* nm) {
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


// Garbage collection

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
    assert(Universe::heap()->is_in_closed_subset(o),
           err_msg("should be in closed *p " INTPTR_FORMAT " " INTPTR_FORMAT, (address)p, (address)o));
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

void InstanceKlass::oop_follow_contents(oop obj) {
  assert(obj != NULL, "can't follow the content of NULL object");
  MarkSweep::follow_klass(obj->klass());
  InstanceKlass_OOP_MAP_ITERATE( \
    obj, \
    MarkSweep::mark_and_push(p), \
    assert_is_in_closed_subset)
}

#if INCLUDE_ALL_GCS
void InstanceKlass::oop_follow_contents(ParCompactionManager* cm,
                                        oop obj) {
  assert(obj != NULL, "can't follow the content of NULL object");
  PSParallelCompact::follow_klass(cm, obj->klass());
  // Only mark the header and let the scan of the meta-data mark
  // everything else.
  InstanceKlass_OOP_MAP_ITERATE( \
    obj, \
    PSParallelCompact::mark_and_push(cm, p), \
    assert_is_in)
}
#endif // INCLUDE_ALL_GCS

// closure's do_metadata() method dictates whether the given closure should be
// applied to the klass ptr in the object header.

#define if_do_metadata_checked(closure, nv_suffix)                    \
  /* Make sure the non-virtual and the virtual versions match. */     \
  assert(closure->do_metadata##nv_suffix() == closure->do_metadata(), \
      "Inconsistency in do_metadata");                                \
  if (closure->do_metadata##nv_suffix())

#define InstanceKlass_OOP_OOP_ITERATE_DEFN(OopClosureType, nv_suffix)        \
                                                                             \
int InstanceKlass::oop_oop_iterate##nv_suffix(oop obj, OopClosureType* closure) { \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::ik);\
  /* header */                                                          \
  if_do_metadata_checked(closure, nv_suffix) {                          \
    closure->do_klass##nv_suffix(obj->klass());                         \
  }                                                                     \
  InstanceKlass_OOP_MAP_ITERATE(                                        \
    obj,                                                                \
    SpecializationStats::                                               \
      record_do_oop_call##nv_suffix(SpecializationStats::ik);           \
    (closure)->do_oop##nv_suffix(p),                                    \
    assert_is_in_closed_subset)                                         \
  return size_helper();                                                 \
}

#if INCLUDE_ALL_GCS
#define InstanceKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN(OopClosureType, nv_suffix) \
                                                                                \
int InstanceKlass::oop_oop_iterate_backwards##nv_suffix(oop obj,                \
                                              OopClosureType* closure) {        \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::ik); \
  /* header */                                                                  \
  if_do_metadata_checked(closure, nv_suffix) {                                  \
    closure->do_klass##nv_suffix(obj->klass());                                 \
  }                                                                             \
  /* instance variables */                                                      \
  InstanceKlass_OOP_MAP_REVERSE_ITERATE(                                        \
    obj,                                                                        \
    SpecializationStats::record_do_oop_call##nv_suffix(SpecializationStats::ik);\
    (closure)->do_oop##nv_suffix(p),                                            \
    assert_is_in_closed_subset)                                                 \
   return size_helper();                                                        \
}
#endif // INCLUDE_ALL_GCS

#define InstanceKlass_OOP_OOP_ITERATE_DEFN_m(OopClosureType, nv_suffix) \
                                                                        \
int InstanceKlass::oop_oop_iterate##nv_suffix##_m(oop obj,              \
                                                  OopClosureType* closure, \
                                                  MemRegion mr) {          \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::ik);\
  if_do_metadata_checked(closure, nv_suffix) {                           \
    if (mr.contains(obj)) {                                              \
      closure->do_klass##nv_suffix(obj->klass());                        \
    }                                                                    \
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
#if INCLUDE_ALL_GCS
ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceKlass_OOP_OOP_ITERATE_BACKWARDS_DEFN)
#endif // INCLUDE_ALL_GCS

int InstanceKlass::oop_adjust_pointers(oop obj) {
  int size = size_helper();
  InstanceKlass_OOP_MAP_ITERATE( \
    obj, \
    MarkSweep::adjust_pointer(p), \
    assert_is_in)
  MarkSweep::adjust_klass(obj->klass());
  return size;
}

#if INCLUDE_ALL_GCS
void InstanceKlass::oop_push_contents(PSPromotionManager* pm, oop obj) {
  InstanceKlass_OOP_MAP_REVERSE_ITERATE( \
    obj, \
    if (PSScavenge::should_scavenge(p)) { \
      pm->claim_or_forward_depth(p); \
    }, \
    assert_nothing )
}

int InstanceKlass::oop_update_pointers(ParCompactionManager* cm, oop obj) {
  int size = size_helper();
  InstanceKlass_OOP_MAP_ITERATE( \
    obj, \
    PSParallelCompact::adjust_pointer(p), \
    assert_is_in)
  obj->update_header(cm);
  return size;
}

#endif // INCLUDE_ALL_GCS

void InstanceKlass::clean_implementors_list(BoolObjectClosure* is_alive) {
  assert(is_loader_alive(is_alive), "this klass should be live");
  if (is_interface()) {
    if (ClassUnloading) {
      Klass* impl = implementor();
      if (impl != NULL) {
        if (!impl->is_loader_alive(is_alive)) {
          // remove this guy
          Klass** klass = adr_implementor();
          assert(klass != NULL, "null klass");
          if (klass != NULL) {
            *klass = NULL;
          }
        }
      }
    }
  }
}

void InstanceKlass::clean_method_data(BoolObjectClosure* is_alive) {
  for (int m = 0; m < methods()->length(); m++) {
    MethodData* mdo = methods()->at(m)->method_data();
    if (mdo != NULL) {
      for (ProfileData* data = mdo->first_data();
           mdo->is_valid(data);
           data = mdo->next_data(data)) {
        data->clean_weak_klass_links(is_alive);
      }
    }
  }
}


static void remove_unshareable_in_class(Klass* k) {
  // remove klass's unshareable info
  k->remove_unshareable_info();
}

void InstanceKlass::remove_unshareable_info() {
  Klass::remove_unshareable_info();
  // Unlink the class
  if (is_linked()) {
    unlink_class();
  }
  init_implementor();

  constants()->remove_unshareable_info();

  for (int i = 0; i < methods()->length(); i++) {
    Method* m = methods()->at(i);
    m->remove_unshareable_info();
  }

  // do array classes also.
  array_klasses_do(remove_unshareable_in_class);
}

void restore_unshareable_in_class(Klass* k, TRAPS) {
  k->restore_unshareable_info(CHECK);
}

void InstanceKlass::restore_unshareable_info(TRAPS) {
  Klass::restore_unshareable_info(CHECK);
  instanceKlassHandle ik(THREAD, this);

  Array<Method*>* methods = ik->methods();
  int num_methods = methods->length();
  for (int index2 = 0; index2 < num_methods; ++index2) {
    methodHandle m(THREAD, methods->at(index2));
    m()->link_method(m, CHECK);
    // restore method's vtable by calling a virtual function
    m->restore_vtable();
  }
  if (JvmtiExport::has_redefined_a_class()) {
    // Reinitialize vtable because RedefineClasses may have changed some
    // entries in this vtable for super classes so the CDS vtable might
    // point to old or obsolete entries.  RedefineClasses doesn't fix up
    // vtables in the shared system dictionary, only the main one.
    // It also redefines the itable too so fix that too.
    ResourceMark rm(THREAD);
    ik->vtable()->initialize_vtable(false, CHECK);
    ik->itable()->initialize_itable(false, CHECK);
  }

  // restore constant pool resolved references
  ik->constants()->restore_unshareable_info(CHECK);

  ik->array_klasses_do(restore_unshareable_in_class, CHECK);
}

static void clear_all_breakpoints(Method* m) {
  m->clear_all_breakpoints();
}


void InstanceKlass::notify_unload_class(InstanceKlass* ik) {
  // notify the debugger
  if (JvmtiExport::should_post_class_unload()) {
    JvmtiExport::post_class_unload(ik);
  }

  // notify ClassLoadingService of class unload
  ClassLoadingService::notify_class_unloaded(ik);
}

void InstanceKlass::release_C_heap_structures(InstanceKlass* ik) {
  // Clean up C heap
  ik->release_C_heap_structures();
  ik->constants()->release_C_heap_structures();
}

void InstanceKlass::release_C_heap_structures() {

  // Can't release the constant pool here because the constant pool can be
  // deallocated separately from the InstanceKlass for default methods and
  // redefine classes.

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

  // Deallocate MemberNameTable
  {
    Mutex* lock_or_null = SafepointSynchronize::is_at_safepoint() ? NULL : MemberNameTable_lock;
    MutexLockerEx ml(lock_or_null, Mutex::_no_safepoint_check_flag);
    MemberNameTable* mnt = member_names();
    if (mnt != NULL) {
      delete mnt;
      set_member_names(NULL);
    }
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
  if (_cached_class_file != NULL) {
    os::free(_cached_class_file, mtClass);
    _cached_class_file = NULL;
  }

  // Decrement symbol reference counts associated with the unloaded class.
  if (_name != NULL) _name->decrement_refcount();
  // unreference array name derived from this class name (arrays of an unloaded
  // class can't be referenced anymore).
  if (_array_name != NULL)  _array_name->decrement_refcount();
  if (_source_debug_extension != NULL) FREE_C_HEAP_ARRAY(char, _source_debug_extension, mtClass);

  assert(_total_instanceKlass_count >= 1, "Sanity check");
  Atomic::dec(&_total_instanceKlass_count);
}

void InstanceKlass::set_source_debug_extension(char* array, int length) {
  if (array == NULL) {
    _source_debug_extension = NULL;
  } else {
    // Adding one to the attribute length in order to store a null terminator
    // character could cause an overflow because the attribute length is
    // already coded with an u4 in the classfile, but in practice, it's
    // unlikely to happen.
    assert((length+1) > length, "Overflow checking");
    char* sde = NEW_C_HEAP_ARRAY(char, (length + 1), mtClass);
    for (int i = 0; i < length; i++) {
      sde[i] = array[i];
    }
    sde[length] = '\0';
    _source_debug_extension = sde;
  }
}

address InstanceKlass::static_field_addr(int offset) {
  return (address)(offset + InstanceMirrorKlass::offset_of_static_fields() + cast_from_oop<intptr_t>(java_mirror()));
}


const char* InstanceKlass::signature_name() const {
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
bool InstanceKlass::is_same_class_package(Klass* class2) {
  Klass* class1 = this;
  oop classloader1 = InstanceKlass::cast(class1)->class_loader();
  Symbol* classname1 = class1->name();

  if (class2->oop_is_objArray()) {
    class2 = ObjArrayKlass::cast(class2)->bottom_klass();
  }
  oop classloader2;
  if (class2->oop_is_instance()) {
    classloader2 = InstanceKlass::cast(class2)->class_loader();
  } else {
    assert(class2->oop_is_typeArray(), "should be type array");
    classloader2 = NULL;
  }
  Symbol* classname2 = class2->name();

  return InstanceKlass::is_same_class_package(classloader1, classname1,
                                              classloader2, classname2);
}

bool InstanceKlass::is_same_class_package(oop classloader2, Symbol* classname2) {
  Klass* class1 = this;
  oop classloader1 = InstanceKlass::cast(class1)->class_loader();
  Symbol* classname1 = class1->name();

  return InstanceKlass::is_same_class_package(classloader1, classname1,
                                              classloader2, classname2);
}

// return true if two classes are in the same package, classloader
// and classname information is enough to determine a class's package
bool InstanceKlass::is_same_class_package(oop class_loader1, Symbol* class_name1,
                                          oop class_loader2, Symbol* class_name2) {
  if (class_loader1 != class_loader2) {
    return false;
  } else if (class_name1 == class_name2) {
    return true;                // skip painful bytewise comparison
  } else {
    ResourceMark rm;

    // The Symbol*'s are in UTF8 encoding. Since we only need to check explicitly
    // for ASCII characters ('/', 'L', '['), we can keep them in UTF8 encoding.
    // Otherwise, we just compare jbyte values between the strings.
    const jbyte *name1 = class_name1->base();
    const jbyte *name2 = class_name2->base();

    const jbyte *last_slash1 = UTF8::strrchr(name1, class_name1->utf8_length(), '/');
    const jbyte *last_slash2 = UTF8::strrchr(name2, class_name2->utf8_length(), '/');

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
// "this" is InstanceKlass of super_method which must exist
// note that the InstanceKlass of the method in the targetclassname has not always been created yet
bool InstanceKlass::is_override(methodHandle super_method, Handle targetclassloader, Symbol* targetclassname, TRAPS) {
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
   return(is_same_class_package(targetclassloader(), targetclassname));
}

/* defined for now in jvm.cpp, for historical reasons *--
Klass* InstanceKlass::compute_enclosing_class_impl(instanceKlassHandle self,
                                                     Symbol*& simple_name_result, TRAPS) {
  ...
}
*/

// tell if two classes have the same enclosing class (at package level)
bool InstanceKlass::is_same_package_member_impl(instanceKlassHandle class1,
                                                Klass* class2_oop, TRAPS) {
  if (class2_oop == class1())                       return true;
  if (!class2_oop->oop_is_instance())  return false;
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
    bool ignore_inner_is_member;
    Klass* next = outer1->compute_enclosing_class(&ignore_inner_is_member,
                                                    CHECK_false);
    if (next == NULL)  break;
    if (next == class2())  return true;
    outer1 = instanceKlassHandle(THREAD, next);
  }

  // Now do the same for class2.
  instanceKlassHandle outer2 = class2;
  for (;;) {
    bool ignore_inner_is_member;
    Klass* next = outer2->compute_enclosing_class(&ignore_inner_is_member,
                                                    CHECK_false);
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


jint InstanceKlass::compute_modifier_flags(TRAPS) const {
  jint access = access_flags().as_int();

  // But check if it happens to be member class.
  instanceKlassHandle ik(THREAD, this);
  InnerClassesIterator iter(ik);
  for (; !iter.done(); iter.next()) {
    int ioff = iter.inner_class_info_index();
    // Inner class attribute can be zero, skip it.
    // Strange but true:  JVM spec. allows null inner class refs.
    if (ioff == 0) continue;

    // only look at classes that are already loaded
    // since we are looking for the flags for our self.
    Symbol* inner_name = ik->constants()->klass_name_at(ioff);
    if ((ik->name() == inner_name)) {
      // This is really a member class.
      access = iter.inner_access_flags();
      break;
    }
  }
  // Remember to strip ACC_SUPER bit
  return (access & (~JVM_ACC_SUPER)) & JVM_ACC_WRITTEN_FLAGS;
}

jint InstanceKlass::jvmti_class_status() const {
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

Method* InstanceKlass::method_at_itable(Klass* holder, int index, TRAPS) {
  itableOffsetEntry* ioe = (itableOffsetEntry*)start_of_itable();
  int method_table_offset_in_words = ioe->offset()/wordSize;
  int nof_interfaces = (method_table_offset_in_words - itable_offset_in_words())
                       / itableOffsetEntry::size();

  for (int cnt = 0 ; ; cnt ++, ioe ++) {
    // If the interface isn't implemented by the receiver class,
    // the VM should throw IncompatibleClassChangeError.
    if (cnt >= nof_interfaces) {
      THROW_NULL(vmSymbols::java_lang_IncompatibleClassChangeError());
    }

    Klass* ik = ioe->interface_klass();
    if (ik == holder) break;
  }

  itableMethodEntry* ime = ioe->first_method_entry(this);
  Method* m = ime[index].method();
  if (m == NULL) {
    THROW_NULL(vmSymbols::java_lang_AbstractMethodError());
  }
  return m;
}


#if INCLUDE_JVMTI
// update default_methods for redefineclasses for methods that are
// not yet in the vtable due to concurrent subclass define and superinterface
// redefinition
// Note: those in the vtable, should have been updated via adjust_method_entries
void InstanceKlass::adjust_default_methods(Method** old_methods, Method** new_methods,
                                           int methods_length, bool* trace_name_printed) {
  // search the default_methods for uses of either obsolete or EMCP methods
  if (default_methods() != NULL) {
    for (int j = 0; j < methods_length; j++) {
      Method* old_method = old_methods[j];
      Method* new_method = new_methods[j];

      for (int index = 0; index < default_methods()->length(); index ++) {
        if (default_methods()->at(index) == old_method) {
          default_methods()->at_put(index, new_method);
          if (RC_TRACE_IN_RANGE(0x00100000, 0x00400000)) {
            if (!(*trace_name_printed)) {
              // RC_TRACE_MESG macro has an embedded ResourceMark
              RC_TRACE_MESG(("adjust: klassname=%s default methods from name=%s",
                             external_name(),
                             old_method->method_holder()->external_name()));
              *trace_name_printed = true;
            }
            RC_TRACE(0x00100000, ("default method update: %s(%s) ",
                                  new_method->name()->as_C_string(),
                                  new_method->signature()->as_C_string()));
          }
        }
      }
    }
  }
}
#endif // INCLUDE_JVMTI

// On-stack replacement stuff
void InstanceKlass::add_osr_nmethod(nmethod* n) {
  // only one compilation can be active
  NEEDS_CLEANUP
  // This is a short non-blocking critical region, so the no safepoint check is ok.
  OsrList_lock->lock_without_safepoint_check();
  assert(n->is_osr_method(), "wrong kind of nmethod");
  n->set_osr_link(osr_nmethods_head());
  set_osr_nmethods_head(n);
  // Raise the highest osr level if necessary
  if (TieredCompilation) {
    Method* m = n->method();
    m->set_highest_osr_comp_level(MAX2(m->highest_osr_comp_level(), n->comp_level()));
  }
  // Remember to unlock again
  OsrList_lock->unlock();

  // Get rid of the osr methods for the same bci that have lower levels.
  if (TieredCompilation) {
    for (int l = CompLevel_limited_profile; l < n->comp_level(); l++) {
      nmethod *inv = lookup_osr_nmethod(n->method(), n->osr_entry_bci(), l, true);
      if (inv != NULL && inv->is_in_use()) {
        inv->make_not_entrant();
      }
    }
  }
}


void InstanceKlass::remove_osr_nmethod(nmethod* n) {
  // This is a short non-blocking critical region, so the no safepoint check is ok.
  OsrList_lock->lock_without_safepoint_check();
  assert(n->is_osr_method(), "wrong kind of nmethod");
  nmethod* last = NULL;
  nmethod* cur  = osr_nmethods_head();
  int max_level = CompLevel_none;  // Find the max comp level excluding n
  Method* m = n->method();
  // Search for match
  while(cur != NULL && cur != n) {
    if (TieredCompilation) {
      // Find max level before n
      max_level = MAX2(max_level, cur->comp_level());
    }
    last = cur;
    cur = cur->osr_link();
  }
  nmethod* next = NULL;
  if (cur == n) {
    next = cur->osr_link();
    if (last == NULL) {
      // Remove first element
      set_osr_nmethods_head(next);
    } else {
      last->set_osr_link(next);
    }
  }
  n->set_osr_link(NULL);
  if (TieredCompilation) {
    cur = next;
    while (cur != NULL) {
      // Find max level after n
      max_level = MAX2(max_level, cur->comp_level());
      cur = cur->osr_link();
    }
    m->set_highest_osr_comp_level(max_level);
  }
  // Remember to unlock again
  OsrList_lock->unlock();
}

nmethod* InstanceKlass::lookup_osr_nmethod(const Method* m, int bci, int comp_level, bool match_level) const {
  // This is a short non-blocking critical region, so the no safepoint check is ok.
  OsrList_lock->lock_without_safepoint_check();
  nmethod* osr = osr_nmethods_head();
  nmethod* best = NULL;
  while (osr != NULL) {
    assert(osr->is_osr_method(), "wrong kind of nmethod found in chain");
    // There can be a time when a c1 osr method exists but we are waiting
    // for a c2 version. When c2 completes its osr nmethod we will trash
    // the c1 version and only be able to find the c2 version. However
    // while we overflow in the c1 code at back branches we don't want to
    // try and switch to the same code as we are already running

    if (osr->method() == m &&
        (bci == InvocationEntryBci || osr->osr_entry_bci() == bci)) {
      if (match_level) {
        if (osr->comp_level() == comp_level) {
          // Found a match - return it.
          OsrList_lock->unlock();
          return osr;
        }
      } else {
        if (best == NULL || (osr->comp_level() > best->comp_level())) {
          if (osr->comp_level() == CompLevel_highest_tier) {
            // Found the best possible - return it.
            OsrList_lock->unlock();
            return osr;
          }
          best = osr;
        }
      }
    }
    osr = osr->osr_link();
  }
  OsrList_lock->unlock();
  if (best != NULL && best->comp_level() >= comp_level && match_level == false) {
    return best;
  }
  return NULL;
}

void InstanceKlass::add_member_name(int index, Handle mem_name) {
  jweak mem_name_wref = JNIHandles::make_weak_global(mem_name);
  MutexLocker ml(MemberNameTable_lock);
  assert(0 <= index && index < idnum_allocated_count(), "index is out of bounds");
  DEBUG_ONLY(No_Safepoint_Verifier nsv);

  if (_member_names == NULL) {
    _member_names = new (ResourceObj::C_HEAP, mtClass) MemberNameTable(idnum_allocated_count());
  }
  _member_names->add_member_name(index, mem_name_wref);
}

oop InstanceKlass::get_member_name(int index) {
  MutexLocker ml(MemberNameTable_lock);
  assert(0 <= index && index < idnum_allocated_count(), "index is out of bounds");
  DEBUG_ONLY(No_Safepoint_Verifier nsv);

  if (_member_names == NULL) {
    return NULL;
  }
  oop mem_name =_member_names->get_member_name(index);
  return mem_name;
}

// -----------------------------------------------------------------------------------------------------
// Printing

#ifndef PRODUCT

#define BULLET  " - "

static const char* state_names[] = {
  "allocated", "loaded", "linked", "being_initialized", "fully_initialized", "initialization_error"
};

static void print_vtable(intptr_t* start, int len, outputStream* st) {
  for (int i = 0; i < len; i++) {
    intptr_t e = start[i];
    st->print("%d : " INTPTR_FORMAT, i, e);
    if (e != 0 && ((Metadata*)e)->is_metaspace_object()) {
      st->print(" ");
      ((Metadata*)e)->print_value_on(st);
    }
    st->cr();
  }
}

void InstanceKlass::print_on(outputStream* st) const {
  assert(is_klass(), "must be klass");
  Klass::print_on(st);

  st->print(BULLET"instance size:     %d", size_helper());                        st->cr();
  st->print(BULLET"klass size:        %d", size());                               st->cr();
  st->print(BULLET"access:            "); access_flags().print_on(st);            st->cr();
  st->print(BULLET"state:             "); st->print_cr(state_names[_init_state]);
  st->print(BULLET"name:              "); name()->print_value_on(st);             st->cr();
  st->print(BULLET"super:             "); super()->print_value_on_maybe_null(st); st->cr();
  st->print(BULLET"sub:               ");
  Klass* sub = subklass();
  int n;
  for (n = 0; sub != NULL; n++, sub = sub->next_sibling()) {
    if (n < MaxSubklassPrintSize) {
      sub->print_value_on(st);
      st->print("   ");
    }
  }
  if (n >= MaxSubklassPrintSize) st->print("(%d more klasses...)", n - MaxSubklassPrintSize);
  st->cr();

  if (is_interface()) {
    st->print_cr(BULLET"nof implementors:  %d", nof_implementors());
    if (nof_implementors() == 1) {
      st->print_cr(BULLET"implementor:    ");
      st->print("   ");
      implementor()->print_value_on(st);
      st->cr();
    }
  }

  st->print(BULLET"arrays:            "); array_klasses()->print_value_on_maybe_null(st); st->cr();
  st->print(BULLET"methods:           "); methods()->print_value_on(st);                  st->cr();
  if (Verbose || WizardMode) {
    Array<Method*>* method_array = methods();
    for (int i = 0; i < method_array->length(); i++) {
      st->print("%d : ", i); method_array->at(i)->print_value(); st->cr();
    }
  }
  st->print(BULLET"method ordering:   "); method_ordering()->print_value_on(st);      st->cr();
  st->print(BULLET"default_methods:   "); default_methods()->print_value_on(st);      st->cr();
  if (Verbose && default_methods() != NULL) {
    Array<Method*>* method_array = default_methods();
    for (int i = 0; i < method_array->length(); i++) {
      st->print("%d : ", i); method_array->at(i)->print_value(); st->cr();
    }
  }
  if (default_vtable_indices() != NULL) {
    st->print(BULLET"default vtable indices:   "); default_vtable_indices()->print_value_on(st);       st->cr();
  }
  st->print(BULLET"local interfaces:  "); local_interfaces()->print_value_on(st);      st->cr();
  st->print(BULLET"trans. interfaces: "); transitive_interfaces()->print_value_on(st); st->cr();
  st->print(BULLET"constants:         "); constants()->print_value_on(st);         st->cr();
  if (class_loader_data() != NULL) {
    st->print(BULLET"class loader data:  ");
    class_loader_data()->print_value_on(st);
    st->cr();
  }
  st->print(BULLET"host class:        "); host_klass()->print_value_on_maybe_null(st); st->cr();
  if (source_file_name() != NULL) {
    st->print(BULLET"source file:       ");
    source_file_name()->print_value_on(st);
    st->cr();
  }
  if (source_debug_extension() != NULL) {
    st->print(BULLET"source debug extension:       ");
    st->print("%s", source_debug_extension());
    st->cr();
  }
  st->print(BULLET"class annotations:       "); class_annotations()->print_value_on(st); st->cr();
  st->print(BULLET"class type annotations:  "); class_type_annotations()->print_value_on(st); st->cr();
  st->print(BULLET"field annotations:       "); fields_annotations()->print_value_on(st); st->cr();
  st->print(BULLET"field type annotations:  "); fields_type_annotations()->print_value_on(st); st->cr();
  {
    bool have_pv = false;
    PreviousVersionWalker pvw(Thread::current(), (InstanceKlass*)this);
    for (PreviousVersionNode * pv_node = pvw.next_previous_version();
         pv_node != NULL; pv_node = pvw.next_previous_version()) {
      if (!have_pv)
        st->print(BULLET"previous version:  ");
      have_pv = true;
      pv_node->prev_constant_pool()->print_value_on(st);
    }
    if (have_pv) st->cr();
  } // pvw is cleaned up

  if (generic_signature() != NULL) {
    st->print(BULLET"generic signature: ");
    generic_signature()->print_value_on(st);
    st->cr();
  }
  st->print(BULLET"inner classes:     "); inner_classes()->print_value_on(st);     st->cr();
  st->print(BULLET"java mirror:       "); java_mirror()->print_value_on(st);       st->cr();
  st->print(BULLET"vtable length      %d  (start addr: " INTPTR_FORMAT ")", vtable_length(), start_of_vtable());  st->cr();
  if (vtable_length() > 0 && (Verbose || WizardMode))  print_vtable(start_of_vtable(), vtable_length(), st);
  st->print(BULLET"itable length      %d (start addr: " INTPTR_FORMAT ")", itable_length(), start_of_itable()); st->cr();
  if (itable_length() > 0 && (Verbose || WizardMode))  print_vtable(start_of_itable(), itable_length(), st);
  st->print_cr(BULLET"---- static fields (%d words):", static_field_size());
  FieldPrinter print_static_field(st);
  ((InstanceKlass*)this)->do_local_static_fields(&print_static_field);
  st->print_cr(BULLET"---- non-static fields (%d words):", nonstatic_field_size());
  FieldPrinter print_nonstatic_field(st);
  ((InstanceKlass*)this)->do_nonstatic_fields(&print_nonstatic_field);

  st->print(BULLET"non-static oop maps: ");
  OopMapBlock* map     = start_of_nonstatic_oop_maps();
  OopMapBlock* end_map = map + nonstatic_oop_map_count();
  while (map < end_map) {
    st->print("%d-%d ", map->offset(), map->offset() + heapOopSize*(map->count() - 1));
    map++;
  }
  st->cr();
}

#endif //PRODUCT

void InstanceKlass::print_value_on(outputStream* st) const {
  assert(is_klass(), "must be klass");
  if (Verbose || WizardMode)  access_flags().print_on(st);
  name()->print_value_on(st);
}

#ifndef PRODUCT

void FieldPrinter::do_field(fieldDescriptor* fd) {
  _st->print(BULLET);
   if (_obj == NULL) {
     fd->print_on(_st);
     _st->cr();
   } else {
     fd->print_on_for(_st, _obj);
     _st->cr();
   }
}


void InstanceKlass::oop_print_on(oop obj, outputStream* st) {
  Klass::oop_print_on(obj, st);

  if (this == SystemDictionary::String_klass()) {
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
  FieldPrinter print_field(st, obj);
  do_nonstatic_fields(&print_field);

  if (this == SystemDictionary::Class_klass()) {
    st->print(BULLET"signature: ");
    java_lang_Class::print_signature(obj, st);
    st->cr();
    Klass* mirrored_klass = java_lang_Class::as_Klass(obj);
    st->print(BULLET"fake entry for mirror: ");
    mirrored_klass->print_value_on_maybe_null(st);
    st->cr();
    Klass* array_klass = java_lang_Class::array_klass(obj);
    st->print(BULLET"fake entry for array: ");
    array_klass->print_value_on_maybe_null(st);
    st->cr();
    st->print_cr(BULLET"fake entry for oop_size: %d", java_lang_Class::oop_size(obj));
    st->print_cr(BULLET"fake entry for static_oop_field_count: %d", java_lang_Class::static_oop_field_count(obj));
    Klass* real_klass = java_lang_Class::as_Klass(obj);
    if (real_klass != NULL && real_klass->oop_is_instance()) {
      InstanceKlass::cast(real_klass)->do_local_static_fields(&print_field);
    }
  } else if (this == SystemDictionary::MethodType_klass()) {
    st->print(BULLET"signature: ");
    java_lang_invoke_MethodType::print_signature(obj, st);
    st->cr();
  }
}

#endif //PRODUCT

void InstanceKlass::oop_print_value_on(oop obj, outputStream* st) {
  st->print("a ");
  name()->print_value_on(st);
  obj->print_address_on(st);
  if (this == SystemDictionary::String_klass()
      && java_lang_String::value(obj) != NULL) {
    ResourceMark rm;
    int len = java_lang_String::length(obj);
    int plen = (len < 24 ? len : 12);
    char* str = java_lang_String::as_utf8_string(obj, 0, plen);
    st->print(" = \"%s\"", str);
    if (len > plen)
      st->print("...[%d]", len);
  } else if (this == SystemDictionary::Class_klass()) {
    Klass* k = java_lang_Class::as_Klass(obj);
    st->print(" = ");
    if (k != NULL) {
      k->print_value_on(st);
    } else {
      const char* tname = type2name(java_lang_Class::primitive_type(obj));
      st->print("%s", tname ? tname : "type?");
    }
  } else if (this == SystemDictionary::MethodType_klass()) {
    st->print(" = ");
    java_lang_invoke_MethodType::print_signature(obj, st);
  } else if (java_lang_boxing_object::is_instance(obj)) {
    st->print(" = ");
    java_lang_boxing_object::print(obj, st);
  } else if (this == SystemDictionary::LambdaForm_klass()) {
    oop vmentry = java_lang_invoke_LambdaForm::vmentry(obj);
    if (vmentry != NULL) {
      st->print(" => ");
      vmentry->print_value_on(st);
    }
  } else if (this == SystemDictionary::MemberName_klass()) {
    Metadata* vmtarget = java_lang_invoke_MemberName::vmtarget(obj);
    if (vmtarget != NULL) {
      st->print(" = ");
      vmtarget->print_value_on(st);
    } else {
      java_lang_invoke_MemberName::clazz(obj)->print_value_on(st);
      st->print(".");
      java_lang_invoke_MemberName::name(obj)->print_value_on(st);
    }
  }
}

const char* InstanceKlass::internal_name() const {
  return external_name();
}

#if INCLUDE_SERVICES
// Size Statistics
void InstanceKlass::collect_statistics(KlassSizeStats *sz) const {
  Klass::collect_statistics(sz);

  sz->_inst_size  = HeapWordSize * size_helper();
  sz->_vtab_bytes = HeapWordSize * align_object_offset(vtable_length());
  sz->_itab_bytes = HeapWordSize * align_object_offset(itable_length());
  sz->_nonstatic_oopmap_bytes = HeapWordSize *
        ((is_interface() || is_anonymous()) ?
         align_object_offset(nonstatic_oop_map_size()) :
         nonstatic_oop_map_size());

  int n = 0;
  n += (sz->_methods_array_bytes         = sz->count_array(methods()));
  n += (sz->_method_ordering_bytes       = sz->count_array(method_ordering()));
  n += (sz->_local_interfaces_bytes      = sz->count_array(local_interfaces()));
  n += (sz->_transitive_interfaces_bytes = sz->count_array(transitive_interfaces()));
  n += (sz->_fields_bytes                = sz->count_array(fields()));
  n += (sz->_inner_classes_bytes         = sz->count_array(inner_classes()));
  sz->_ro_bytes += n;

  const ConstantPool* cp = constants();
  if (cp) {
    cp->collect_statistics(sz);
  }

  const Annotations* anno = annotations();
  if (anno) {
    anno->collect_statistics(sz);
  }

  const Array<Method*>* methods_array = methods();
  if (methods()) {
    for (int i = 0; i < methods_array->length(); i++) {
      Method* method = methods_array->at(i);
      if (method) {
        sz->_method_count ++;
        method->collect_statistics(sz);
      }
    }
  }
}
#endif // INCLUDE_SERVICES

// Verification

class VerifyFieldClosure: public OopClosure {
 protected:
  template <class T> void do_oop_work(T* p) {
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

void InstanceKlass::verify_on(outputStream* st, bool check_dictionary) {
#ifndef PRODUCT
  // Avoid redundant verifies, this really should be in product.
  if (_verify_count == Universe::verify_count()) return;
  _verify_count = Universe::verify_count();
#endif

  // Verify Klass
  Klass::verify_on(st, check_dictionary);

  // Verify that klass is present in SystemDictionary if not already
  // verifying the SystemDictionary.
  if (is_loaded() && !is_anonymous() && check_dictionary) {
    Symbol* h_name = name();
    SystemDictionary::verify_obj_klass_present(h_name, class_loader_data());
  }

  // Verify vtables
  if (is_linked()) {
    ResourceMark rm;
    // $$$ This used to be done only for m/s collections.  Doing it
    // always seemed a valid generalization.  (DLD -- 6/00)
    vtable()->verify(st);
  }

  // Verify first subklass
  if (subklass_oop() != NULL) {
    guarantee(subklass_oop()->is_klass(), "should be klass");
  }

  // Verify siblings
  Klass* super = this->super();
  Klass* sib = next_sibling();
  if (sib != NULL) {
    if (sib == this) {
      fatal(err_msg("subclass points to itself " PTR_FORMAT, sib));
    }

    guarantee(sib->is_klass(), "should be klass");
    guarantee(sib->super() == super, "siblings should have same superklass");
  }

  // Verify implementor fields
  Klass* im = implementor();
  if (im != NULL) {
    guarantee(is_interface(), "only interfaces should have implementor set");
    guarantee(im->is_klass(), "should be klass");
    guarantee(!im->is_interface() || im == this,
      "implementors cannot be interfaces");
  }

  // Verify local interfaces
  if (local_interfaces()) {
    Array<Klass*>* local_interfaces = this->local_interfaces();
    for (int j = 0; j < local_interfaces->length(); j++) {
      Klass* e = local_interfaces->at(j);
      guarantee(e->is_klass() && e->is_interface(), "invalid local interface");
    }
  }

  // Verify transitive interfaces
  if (transitive_interfaces() != NULL) {
    Array<Klass*>* transitive_interfaces = this->transitive_interfaces();
    for (int j = 0; j < transitive_interfaces->length(); j++) {
      Klass* e = transitive_interfaces->at(j);
      guarantee(e->is_klass() && e->is_interface(), "invalid transitive interface");
    }
  }

  // Verify methods
  if (methods() != NULL) {
    Array<Method*>* methods = this->methods();
    for (int j = 0; j < methods->length(); j++) {
      guarantee(methods->at(j)->is_method(), "non-method in methods array");
    }
    for (int j = 0; j < methods->length() - 1; j++) {
      Method* m1 = methods->at(j);
      Method* m2 = methods->at(j + 1);
      guarantee(m1->name()->fast_compare(m2->name()) <= 0, "methods not sorted correctly");
    }
  }

  // Verify method ordering
  if (method_ordering() != NULL) {
    Array<int>* method_ordering = this->method_ordering();
    int length = method_ordering->length();
    if (JvmtiExport::can_maintain_original_method_order() ||
        ((UseSharedSpaces || DumpSharedSpaces) && length != 0)) {
      guarantee(length == methods()->length(), "invalid method ordering length");
      jlong sum = 0;
      for (int j = 0; j < length; j++) {
        int original_index = method_ordering->at(j);
        guarantee(original_index >= 0, "invalid method ordering index");
        guarantee(original_index < length, "invalid method ordering index");
        sum += original_index;
      }
      // Verify sum of indices 0,1,...,length-1
      guarantee(sum == ((jlong)length*(length-1))/2, "invalid method ordering sum");
    } else {
      guarantee(length == 0, "invalid method ordering length");
    }
  }

  // Verify default methods
  if (default_methods() != NULL) {
    Array<Method*>* methods = this->default_methods();
    for (int j = 0; j < methods->length(); j++) {
      guarantee(methods->at(j)->is_method(), "non-method in methods array");
    }
    for (int j = 0; j < methods->length() - 1; j++) {
      Method* m1 = methods->at(j);
      Method* m2 = methods->at(j + 1);
      guarantee(m1->name()->fast_compare(m2->name()) <= 0, "methods not sorted correctly");
    }
  }

  // Verify JNI static field identifiers
  if (jni_ids() != NULL) {
    jni_ids()->verify(this);
  }

  // Verify other fields
  if (array_klasses() != NULL) {
    guarantee(array_klasses()->is_klass(), "should be klass");
  }
  if (constants() != NULL) {
    guarantee(constants()->is_constantPool(), "should be constant pool");
  }
  const Klass* host = host_klass();
  if (host != NULL) {
    guarantee(host->is_klass(), "should be klass");
  }
}

void InstanceKlass::oop_verify_on(oop obj, outputStream* st) {
  Klass::oop_verify_on(obj, st);
  VerifyFieldClosure blk;
  obj->oop_iterate_no_header(&blk);
}


// JNIid class for jfieldIDs only
// Note to reviewers:
// These JNI functions are just moved over to column 1 and not changed
// in the compressed oops workspace.
JNIid::JNIid(Klass* holder, int offset, JNIid* next) {
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

void JNIid::deallocate(JNIid* current) {
  while (current != NULL) {
    JNIid* next = current->next();
    delete current;
    current = next;
  }
}


void JNIid::verify(Klass* holder) {
  int first_field_offset  = InstanceMirrorKlass::offset_of_static_fields();
  int end_field_offset;
  end_field_offset = first_field_offset + (InstanceKlass::cast(holder)->static_field_size() * wordSize);

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
void InstanceKlass::set_init_state(ClassState state) {
  bool good_state = is_shared() ? (_init_state <= state)
                                               : (_init_state < state);
  assert(good_state || state == allocated, "illegal state transition");
  _init_state = (u1)state;
}
#endif


// RedefineClasses() support for previous versions:

// Purge previous versions
static void purge_previous_versions_internal(InstanceKlass* ik, int emcp_method_count) {
  if (ik->previous_versions() != NULL) {
    // This klass has previous versions so see what we can cleanup
    // while it is safe to do so.

    int deleted_count = 0;    // leave debugging breadcrumbs
    int live_count = 0;
    ClassLoaderData* loader_data = ik->class_loader_data() == NULL ?
                       ClassLoaderData::the_null_class_loader_data() :
                       ik->class_loader_data();

    // RC_TRACE macro has an embedded ResourceMark
    RC_TRACE(0x00000200, ("purge: %s: previous version length=%d",
      ik->external_name(), ik->previous_versions()->length()));

    for (int i = ik->previous_versions()->length() - 1; i >= 0; i--) {
      // check the previous versions array
      PreviousVersionNode * pv_node = ik->previous_versions()->at(i);
      ConstantPool* cp_ref = pv_node->prev_constant_pool();
      assert(cp_ref != NULL, "cp ref was unexpectedly cleared");

      ConstantPool* pvcp = cp_ref;
      if (!pvcp->on_stack()) {
        // If the constant pool isn't on stack, none of the methods
        // are executing.  Delete all the methods, the constant pool and
        // and this previous version node.
        GrowableArray<Method*>* method_refs = pv_node->prev_EMCP_methods();
        if (method_refs != NULL) {
          for (int j = method_refs->length() - 1; j >= 0; j--) {
            Method* method = method_refs->at(j);
            assert(method != NULL, "method ref was unexpectedly cleared");
            method_refs->remove_at(j);
            // method will be freed with associated class.
          }
        }
        // Remove the constant pool
        delete pv_node;
        // Since we are traversing the array backwards, we don't have to
        // do anything special with the index.
        ik->previous_versions()->remove_at(i);
        deleted_count++;
        continue;
      } else {
        RC_TRACE(0x00000200, ("purge: previous version @%d is alive", i));
        assert(pvcp->pool_holder() != NULL, "Constant pool with no holder");
        guarantee (!loader_data->is_unloading(), "unloaded classes can't be on the stack");
        live_count++;
      }

      // At least one method is live in this previous version, clean out
      // the others or mark them as obsolete.
      GrowableArray<Method*>* method_refs = pv_node->prev_EMCP_methods();
      if (method_refs != NULL) {
        RC_TRACE(0x00000200, ("purge: previous methods length=%d",
          method_refs->length()));
        for (int j = method_refs->length() - 1; j >= 0; j--) {
          Method* method = method_refs->at(j);
          assert(method != NULL, "method ref was unexpectedly cleared");

          // Remove the emcp method if it's not executing
          // If it's been made obsolete by a redefinition of a non-emcp
          // method, mark it as obsolete but leave it to clean up later.
          if (!method->on_stack()) {
            method_refs->remove_at(j);
          } else if (emcp_method_count == 0) {
            method->set_is_obsolete();
          } else {
            // RC_TRACE macro has an embedded ResourceMark
            RC_TRACE(0x00000200,
              ("purge: %s(%s): prev method @%d in version @%d is alive",
              method->name()->as_C_string(),
              method->signature()->as_C_string(), j, i));
          }
        }
      }
    }
    assert(ik->previous_versions()->length() == live_count, "sanity check");
    RC_TRACE(0x00000200,
      ("purge: previous version stats: live=%d, deleted=%d", live_count,
      deleted_count));
  }
}

// External interface for use during class unloading.
void InstanceKlass::purge_previous_versions(InstanceKlass* ik) {
  // Call with >0 emcp methods since they are not currently being redefined.
  purge_previous_versions_internal(ik, 1);
}


// Potentially add an information node that contains pointers to the
// interesting parts of the previous version of the_class.
// This is also where we clean out any unused references.
// Note that while we delete nodes from the _previous_versions
// array, we never delete the array itself until the klass is
// unloaded. The has_been_redefined() query depends on that fact.
//
void InstanceKlass::add_previous_version(instanceKlassHandle ikh,
       BitMap* emcp_methods, int emcp_method_count) {
  assert(Thread::current()->is_VM_thread(),
         "only VMThread can add previous versions");

  if (_previous_versions == NULL) {
    // This is the first previous version so make some space.
    // Start with 2 elements under the assumption that the class
    // won't be redefined much.
    _previous_versions =  new (ResourceObj::C_HEAP, mtClass)
                            GrowableArray<PreviousVersionNode *>(2, true);
  }

  ConstantPool* cp_ref = ikh->constants();

  // RC_TRACE macro has an embedded ResourceMark
  RC_TRACE(0x00000400, ("adding previous version ref for %s @%d, EMCP_cnt=%d "
                        "on_stack=%d",
    ikh->external_name(), _previous_versions->length(), emcp_method_count,
    cp_ref->on_stack()));

  // If the constant pool for this previous version of the class
  // is not marked as being on the stack, then none of the methods
  // in this previous version of the class are on the stack so
  // we don't need to create a new PreviousVersionNode. However,
  // we still need to examine older previous versions below.
  Array<Method*>* old_methods = ikh->methods();

  if (cp_ref->on_stack()) {
    PreviousVersionNode * pv_node = NULL;
    if (emcp_method_count == 0) {
      // non-shared ConstantPool gets a reference
      pv_node = new PreviousVersionNode(cp_ref, NULL);
      RC_TRACE(0x00000400,
          ("add: all methods are obsolete; flushing any EMCP refs"));
    } else {
      int local_count = 0;
      GrowableArray<Method*>* method_refs = new (ResourceObj::C_HEAP, mtClass)
          GrowableArray<Method*>(emcp_method_count, true);
      for (int i = 0; i < old_methods->length(); i++) {
        if (emcp_methods->at(i)) {
            // this old method is EMCP. Save it only if it's on the stack
            Method* old_method = old_methods->at(i);
            if (old_method->on_stack()) {
              method_refs->append(old_method);
            }
          if (++local_count >= emcp_method_count) {
            // no more EMCP methods so bail out now
            break;
          }
        }
      }
      // non-shared ConstantPool gets a reference
      pv_node = new PreviousVersionNode(cp_ref, method_refs);
    }
    // append new previous version.
    _previous_versions->append(pv_node);
  }

  // Since the caller is the VMThread and we are at a safepoint, this
  // is a good time to clear out unused references.

  RC_TRACE(0x00000400, ("add: previous version length=%d",
    _previous_versions->length()));

  // Purge previous versions not executing on the stack
  purge_previous_versions_internal(this, emcp_method_count);

  int obsolete_method_count = old_methods->length() - emcp_method_count;

  if (emcp_method_count != 0 && obsolete_method_count != 0 &&
      _previous_versions->length() > 0) {
    // We have a mix of obsolete and EMCP methods so we have to
    // clear out any matching EMCP method entries the hard way.
    int local_count = 0;
    for (int i = 0; i < old_methods->length(); i++) {
      if (!emcp_methods->at(i)) {
        // only obsolete methods are interesting
        Method* old_method = old_methods->at(i);
        Symbol* m_name = old_method->name();
        Symbol* m_signature = old_method->signature();

        // we might not have added the last entry
        for (int j = _previous_versions->length() - 1; j >= 0; j--) {
          // check the previous versions array for non executing obsolete methods
          PreviousVersionNode * pv_node = _previous_versions->at(j);

          GrowableArray<Method*>* method_refs = pv_node->prev_EMCP_methods();
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
            Method* method = method_refs->at(k);

            if (!method->is_obsolete() &&
                method->name() == m_name &&
                method->signature() == m_signature) {
              // The current RedefineClasses() call has made all EMCP
              // versions of this method obsolete so mark it as obsolete
              // and remove the reference.
              RC_TRACE(0x00000400,
                ("add: %s(%s): flush obsolete method @%d in version @%d",
                m_name->as_C_string(), m_signature->as_C_string(), k, j));

              method->set_is_obsolete();
              // Leave obsolete methods on the previous version list to
              // clean up later.
              break;
            }
          }

          // The previous loop may not find a matching EMCP method, but
          // that doesn't mean that we can optimize and not go any
          // further back in the PreviousVersion generations. The EMCP
          // method for this generation could have already been deleted,
          // but there still may be an older EMCP method that has not
          // been deleted.
        }

        if (++local_count >= obsolete_method_count) {
          // no more obsolete methods so bail out now
          break;
        }
      }
    }
  }
} // end add_previous_version()


// Determine if InstanceKlass has a previous version.
bool InstanceKlass::has_previous_version() const {
  return (_previous_versions != NULL && _previous_versions->length() > 0);
} // end has_previous_version()


Method* InstanceKlass::method_with_idnum(int idnum) {
  Method* m = NULL;
  if (idnum < methods()->length()) {
    m = methods()->at(idnum);
  }
  if (m == NULL || m->method_idnum() != idnum) {
    for (int index = 0; index < methods()->length(); ++index) {
      m = methods()->at(index);
      if (m->method_idnum() == idnum) {
        return m;
      }
    }
    // None found, return null for the caller to handle.
    return NULL;
  }
  return m;
}

jint InstanceKlass::get_cached_class_file_len() {
  return VM_RedefineClasses::get_cached_class_file_len(_cached_class_file);
}

unsigned char * InstanceKlass::get_cached_class_file_bytes() {
  return VM_RedefineClasses::get_cached_class_file_bytes(_cached_class_file);
}


// Construct a PreviousVersionNode entry for the array hung off
// the InstanceKlass.
PreviousVersionNode::PreviousVersionNode(ConstantPool* prev_constant_pool,
  GrowableArray<Method*>* prev_EMCP_methods) {

  _prev_constant_pool = prev_constant_pool;
  _prev_EMCP_methods = prev_EMCP_methods;
}


// Destroy a PreviousVersionNode
PreviousVersionNode::~PreviousVersionNode() {
  if (_prev_constant_pool != NULL) {
    _prev_constant_pool = NULL;
  }

  if (_prev_EMCP_methods != NULL) {
    delete _prev_EMCP_methods;
  }
}

// Construct a helper for walking the previous versions array
PreviousVersionWalker::PreviousVersionWalker(Thread* thread, InstanceKlass *ik) {
  _thread = thread;
  _previous_versions = ik->previous_versions();
  _current_index = 0;
  _current_p = NULL;
  _current_constant_pool_handle = constantPoolHandle(thread, ik->constants());
}


// Return the interesting information for the next previous version
// of the klass. Returns NULL if there are no more previous versions.
PreviousVersionNode* PreviousVersionWalker::next_previous_version() {
  if (_previous_versions == NULL) {
    // no previous versions so nothing to return
    return NULL;
  }

  _current_p = NULL;  // reset to NULL
  _current_constant_pool_handle = NULL;

  int length = _previous_versions->length();

  while (_current_index < length) {
    PreviousVersionNode * pv_node = _previous_versions->at(_current_index++);

    // Save a handle to the constant pool for this previous version,
    // which keeps all the methods from being deallocated.
    _current_constant_pool_handle = constantPoolHandle(_thread, pv_node->prev_constant_pool());
    _current_p = pv_node;
    return pv_node;
  }

  return NULL;
} // end next_previous_version()
