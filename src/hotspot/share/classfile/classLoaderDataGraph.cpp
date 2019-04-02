/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderDataGraph.inline.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/metadataOnStackMark.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/packageEntry.hpp"
#include "code/dependencyContext.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/metaspace.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutex.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"

volatile size_t ClassLoaderDataGraph::_num_array_classes = 0;
volatile size_t ClassLoaderDataGraph::_num_instance_classes = 0;

void ClassLoaderDataGraph::clear_claimed_marks() {
  for (ClassLoaderData* cld = _head; cld != NULL; cld = cld->next()) {
    cld->clear_claim();
  }
}

// Class iterator used by the compiler.  It gets some number of classes at
// a safepoint to decay invocation counters on the methods.
class ClassLoaderDataGraphKlassIteratorStatic {
  ClassLoaderData* _current_loader_data;
  Klass*           _current_class_entry;
 public:

  ClassLoaderDataGraphKlassIteratorStatic() : _current_loader_data(NULL), _current_class_entry(NULL) {}

  InstanceKlass* try_get_next_class() {
    assert(SafepointSynchronize::is_at_safepoint(), "only called at safepoint");
    size_t max_classes = ClassLoaderDataGraph::num_instance_classes();
    assert(max_classes > 0, "should not be called with no instance classes");
    for (size_t i = 0; i < max_classes; ) {

      if (_current_class_entry != NULL) {
        Klass* k = _current_class_entry;
        _current_class_entry = _current_class_entry->next_link();

        if (k->is_instance_klass()) {
          InstanceKlass* ik = InstanceKlass::cast(k);
          i++;  // count all instance classes found
          // Not yet loaded classes are counted in max_classes
          // but only return loaded classes.
          if (ik->is_loaded()) {
            return ik;
          }
        }
      } else {
        // Go to next CLD
        if (_current_loader_data != NULL) {
          _current_loader_data = _current_loader_data->next();
        }
        // Start at the beginning
        if (_current_loader_data == NULL) {
          _current_loader_data = ClassLoaderDataGraph::_head;
        }

        _current_class_entry = _current_loader_data->klasses();
      }
    }
    // Should never be reached unless all instance classes have failed or are not fully loaded.
    // Caller handles NULL.
    return NULL;
  }

  // If the current class for the static iterator is a class being unloaded or
  // deallocated, adjust the current class.
  void adjust_saved_class(ClassLoaderData* cld) {
    if (_current_loader_data == cld) {
      _current_loader_data = cld->next();
      if (_current_loader_data != NULL) {
        _current_class_entry = _current_loader_data->klasses();
      }  // else try_get_next_class will start at the head
    }
  }

  void adjust_saved_class(Klass* klass) {
    if (_current_class_entry == klass) {
      _current_class_entry = klass->next_link();
    }
  }
};

static ClassLoaderDataGraphKlassIteratorStatic static_klass_iterator;

InstanceKlass* ClassLoaderDataGraph::try_get_next_class() {
  assert(SafepointSynchronize::is_at_safepoint(), "only called at safepoint");
  return static_klass_iterator.try_get_next_class();
}

void ClassLoaderDataGraph::adjust_saved_class(ClassLoaderData* cld) {
  return static_klass_iterator.adjust_saved_class(cld);
}

void ClassLoaderDataGraph::adjust_saved_class(Klass* klass) {
  return static_klass_iterator.adjust_saved_class(klass);
}

void ClassLoaderDataGraph::clean_deallocate_lists(bool walk_previous_versions) {
  assert(SafepointSynchronize::is_at_safepoint(), "must only be called at safepoint");
  uint loaders_processed = 0;
  for (ClassLoaderData* cld = _head; cld != NULL; cld = cld->next()) {
    // is_alive check will be necessary for concurrent class unloading.
    if (cld->is_alive()) {
      // clean metaspace
      if (walk_previous_versions) {
        cld->classes_do(InstanceKlass::purge_previous_versions);
      }
      cld->free_deallocate_list();
      loaders_processed++;
    }
  }
  log_debug(class, loader, data)("clean_deallocate_lists: loaders processed %u %s",
                                 loaders_processed, walk_previous_versions ? "walk_previous_versions" : "");
}

void ClassLoaderDataGraph::walk_metadata_and_clean_metaspaces() {
  assert(SafepointSynchronize::is_at_safepoint(), "must only be called at safepoint");

  _should_clean_deallocate_lists = false; // assume everything gets cleaned

  // Mark metadata seen on the stack so we can delete unreferenced entries.
  // Walk all metadata, including the expensive code cache walk, only for class redefinition.
  // The MetadataOnStackMark walk during redefinition saves previous versions if it finds old methods
  // on the stack or in the code cache, so we only have to repeat the full walk if
  // they were found at that time.
  // TODO: have redefinition clean old methods out of the code cache.  They still exist in some places.
  bool walk_all_metadata = InstanceKlass::has_previous_versions_and_reset();

  MetadataOnStackMark md_on_stack(walk_all_metadata, /*redefinition_walk*/false);
  clean_deallocate_lists(walk_all_metadata);
}

// GC root of class loader data created.
ClassLoaderData* ClassLoaderDataGraph::_head = NULL;
ClassLoaderData* ClassLoaderDataGraph::_unloading = NULL;
ClassLoaderData* ClassLoaderDataGraph::_saved_unloading = NULL;
ClassLoaderData* ClassLoaderDataGraph::_saved_head = NULL;

bool ClassLoaderDataGraph::_should_purge = false;
bool ClassLoaderDataGraph::_should_clean_deallocate_lists = false;
bool ClassLoaderDataGraph::_safepoint_cleanup_needed = false;
bool ClassLoaderDataGraph::_metaspace_oom = false;

// Add a new class loader data node to the list.  Assign the newly created
// ClassLoaderData into the java/lang/ClassLoader object as a hidden field
ClassLoaderData* ClassLoaderDataGraph::add_to_graph(Handle loader, bool is_unsafe_anonymous) {

  assert_lock_strong(ClassLoaderDataGraph_lock);

  ClassLoaderData* cld;

  // First check if another thread beat us to creating the CLD and installing
  // it into the loader while we were waiting for the lock.
  if (!is_unsafe_anonymous && loader.not_null()) {
    cld = java_lang_ClassLoader::loader_data_acquire(loader());
    if (cld != NULL) {
      return cld;
    }
  }

  // We mustn't GC until we've installed the ClassLoaderData in the Graph since the CLD
  // contains oops in _handles that must be walked.  GC doesn't walk CLD from the
  // loader oop in all collections, particularly young collections.
  NoSafepointVerifier no_safepoints;

  cld = new ClassLoaderData(loader, is_unsafe_anonymous);

  // First install the new CLD to the Graph.
  cld->set_next(_head);
  _head = cld;

  // Next associate with the class_loader.
  if (!is_unsafe_anonymous) {
    // Use OrderAccess, since readers need to get the loader_data only after
    // it's added to the Graph
    java_lang_ClassLoader::release_set_loader_data(loader(), cld);
  }

  // Lastly log, if requested
  LogTarget(Trace, class, loader, data) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    ls.print("create ");
    cld->print_value_on(&ls);
    ls.cr();
  }
  return cld;
}

ClassLoaderData* ClassLoaderDataGraph::add(Handle loader, bool is_unsafe_anonymous) {
  MutexLocker ml(ClassLoaderDataGraph_lock);
  ClassLoaderData* loader_data = add_to_graph(loader, is_unsafe_anonymous);
  return loader_data;
}

void ClassLoaderDataGraph::cld_unloading_do(CLDClosure* cl) {
  assert_locked_or_safepoint_weak(ClassLoaderDataGraph_lock);
  // Only walk the head until any clds not purged from prior unloading
  // (CMS doesn't purge right away).
  for (ClassLoaderData* cld = _unloading; cld != _saved_unloading; cld = cld->next()) {
    assert(cld->is_unloading(), "invariant");
    cl->do_cld(cld);
  }
}

// These are functions called by the GC, which require all of the CLDs, including the
// unloading ones.
void ClassLoaderDataGraph::cld_do(CLDClosure* cl) {
  assert_locked_or_safepoint_weak(ClassLoaderDataGraph_lock);
  for (ClassLoaderData* cld = _head;  cld != NULL; cld = cld->_next) {
    cl->do_cld(cld);
  }
}

void ClassLoaderDataGraph::roots_cld_do(CLDClosure* strong, CLDClosure* weak) {
  assert_locked_or_safepoint_weak(ClassLoaderDataGraph_lock);
  for (ClassLoaderData* cld = _head;  cld != NULL; cld = cld->_next) {
    CLDClosure* closure = cld->keep_alive() ? strong : weak;
    if (closure != NULL) {
      closure->do_cld(cld);
    }
  }
}

void ClassLoaderDataGraph::always_strong_cld_do(CLDClosure* cl) {
  assert_locked_or_safepoint_weak(ClassLoaderDataGraph_lock);
  if (ClassUnloading) {
    roots_cld_do(cl, NULL);
  } else {
    cld_do(cl);
  }
}

// Closure for locking and iterating through classes.
LockedClassesDo::LockedClassesDo(classes_do_func_t f) : _function(f) {
  ClassLoaderDataGraph_lock->lock();
}

LockedClassesDo::LockedClassesDo() : _function(NULL) {
  // callers provide their own do_klass
  ClassLoaderDataGraph_lock->lock();
}

LockedClassesDo::~LockedClassesDo() { ClassLoaderDataGraph_lock->unlock(); }


// Iterating over the CLDG needs to be locked because
// unloading can remove entries concurrently soon.
class ClassLoaderDataGraphIterator : public StackObj {
  ClassLoaderData* _next;
  HandleMark       _hm;  // clean up handles when this is done.
  Handle           _holder;
  Thread*          _thread;
  NoSafepointVerifier _nsv; // No safepoints allowed in this scope
                            // unless verifying at a safepoint.

public:
  ClassLoaderDataGraphIterator() : _next(ClassLoaderDataGraph::_head),
     _nsv(true, !SafepointSynchronize::is_at_safepoint()) {
    _thread = Thread::current();
    assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  }

  ClassLoaderData* get_next() {
    ClassLoaderData* cld = _next;
    // Skip already unloaded CLD for concurrent unloading.
    while (cld != NULL && !cld->is_alive()) {
      cld = cld->next();
    }
    if (cld != NULL) {
      // Keep cld that is being returned alive.
      _holder = Handle(_thread, cld->holder_phantom());
      _next = cld->next();
    } else {
      _next = NULL;
    }
    return cld;
  }
};

void ClassLoaderDataGraph::loaded_cld_do(CLDClosure* cl) {
  ClassLoaderDataGraphIterator iter;
  while (ClassLoaderData* cld = iter.get_next()) {
    cl->do_cld(cld);
  }
}

// These functions assume that the caller has locked the ClassLoaderDataGraph_lock
// if they are not calling the function from a safepoint.
void ClassLoaderDataGraph::classes_do(KlassClosure* klass_closure) {
  ClassLoaderDataGraphIterator iter;
  while (ClassLoaderData* cld = iter.get_next()) {
    cld->classes_do(klass_closure);
  }
}

void ClassLoaderDataGraph::classes_do(void f(Klass* const)) {
  ClassLoaderDataGraphIterator iter;
  while (ClassLoaderData* cld = iter.get_next()) {
    cld->classes_do(f);
  }
}

void ClassLoaderDataGraph::methods_do(void f(Method*)) {
  ClassLoaderDataGraphIterator iter;
  while (ClassLoaderData* cld = iter.get_next()) {
    cld->methods_do(f);
  }
}

void ClassLoaderDataGraph::modules_do(void f(ModuleEntry*)) {
  assert_locked_or_safepoint(Module_lock);
  ClassLoaderDataGraphIterator iter;
  while (ClassLoaderData* cld = iter.get_next()) {
    cld->modules_do(f);
  }
}

void ClassLoaderDataGraph::modules_unloading_do(void f(ModuleEntry*)) {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  // Only walk the head until any clds not purged from prior unloading
  // (CMS doesn't purge right away).
  for (ClassLoaderData* cld = _unloading; cld != _saved_unloading; cld = cld->next()) {
    assert(cld->is_unloading(), "invariant");
    cld->modules_do(f);
  }
}

void ClassLoaderDataGraph::packages_do(void f(PackageEntry*)) {
  assert_locked_or_safepoint(Module_lock);
  ClassLoaderDataGraphIterator iter;
  while (ClassLoaderData* cld = iter.get_next()) {
    cld->packages_do(f);
  }
}

void ClassLoaderDataGraph::packages_unloading_do(void f(PackageEntry*)) {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  // Only walk the head until any clds not purged from prior unloading
  // (CMS doesn't purge right away).
  for (ClassLoaderData* cld = _unloading; cld != _saved_unloading; cld = cld->next()) {
    assert(cld->is_unloading(), "invariant");
    cld->packages_do(f);
  }
}

void ClassLoaderDataGraph::loaded_classes_do(KlassClosure* klass_closure) {
  ClassLoaderDataGraphIterator iter;
  while (ClassLoaderData* cld = iter.get_next()) {
    cld->loaded_classes_do(klass_closure);
  }
}

// This case can block but cannot do unloading (called from CDS)
void ClassLoaderDataGraph::unlocked_loaded_classes_do(KlassClosure* klass_closure) {
  for (ClassLoaderData* cld = _head; cld != NULL; cld = cld->next()) {
    cld->loaded_classes_do(klass_closure);
  }
}


void ClassLoaderDataGraph::classes_unloading_do(void f(Klass* const)) {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  // Only walk the head until any clds not purged from prior unloading
  // (CMS doesn't purge right away).
  for (ClassLoaderData* cld = _unloading; cld != _saved_unloading; cld = cld->next()) {
    assert(cld->is_unloading(), "invariant");
    cld->classes_do(f);
  }
}

#define FOR_ALL_DICTIONARY(X)   ClassLoaderDataGraphIterator iter; \
                                while (ClassLoaderData* X = iter.get_next()) \
                                  if (X->dictionary() != NULL)

// Walk classes in the loaded class dictionaries in various forms.
// Only walks the classes defined in this class loader.
void ClassLoaderDataGraph::dictionary_classes_do(void f(InstanceKlass*)) {
  FOR_ALL_DICTIONARY(cld) {
    cld->dictionary()->classes_do(f);
  }
}

// Only walks the classes defined in this class loader.
void ClassLoaderDataGraph::dictionary_classes_do(void f(InstanceKlass*, TRAPS), TRAPS) {
  FOR_ALL_DICTIONARY(cld) {
    cld->dictionary()->classes_do(f, CHECK);
  }
}

void ClassLoaderDataGraph::verify_dictionary() {
  FOR_ALL_DICTIONARY(cld) {
    cld->dictionary()->verify();
  }
}

void ClassLoaderDataGraph::print_dictionary(outputStream* st) {
  FOR_ALL_DICTIONARY(cld) {
    st->print("Dictionary for ");
    cld->print_value_on(st);
    st->cr();
    cld->dictionary()->print_on(st);
    st->cr();
  }
}

void ClassLoaderDataGraph::print_dictionary_statistics(outputStream* st) {
  FOR_ALL_DICTIONARY(cld) {
    ResourceMark rm;
    stringStream tempst;
    tempst.print("System Dictionary for %s class loader", cld->loader_name_and_id());
    cld->dictionary()->print_table_statistics(st, tempst.as_string());
  }
}

GrowableArray<ClassLoaderData*>* ClassLoaderDataGraph::new_clds() {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  assert(_head == NULL || _saved_head != NULL, "remember_new_clds(true) not called?");

  GrowableArray<ClassLoaderData*>* array = new GrowableArray<ClassLoaderData*>();

  // The CLDs in [_head, _saved_head] were all added during last call to remember_new_clds(true);
  ClassLoaderData* curr = _head;
  while (curr != _saved_head) {
    if (!curr->claimed()) {
      array->push(curr);
      LogTarget(Debug, class, loader, data) lt;
      if (lt.is_enabled()) {
        LogStream ls(lt);
        ls.print("found new CLD: ");
        curr->print_value_on(&ls);
        ls.cr();
      }
    }

    curr = curr->_next;
  }

  return array;
}

#ifndef PRODUCT
bool ClassLoaderDataGraph::contains_loader_data(ClassLoaderData* loader_data) {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  for (ClassLoaderData* data = _head; data != NULL; data = data->next()) {
    if (loader_data == data) {
      return true;
    }
  }

  return false;
}
#endif // PRODUCT

bool ClassLoaderDataGraph::is_valid(ClassLoaderData* loader_data) {
  DEBUG_ONLY( if (!VMError::is_error_reported()) { assert_locked_or_safepoint(ClassLoaderDataGraph_lock); } )
  if (loader_data != NULL) {
    if (loader_data == ClassLoaderData::the_null_class_loader_data()) {
      return true;
    }
    for (ClassLoaderData* data = _head; data != NULL; data = data->next()) {
      if (loader_data == data) {
        return true;
      }
    }
  }
  return false;
}

// Move class loader data from main list to the unloaded list for unloading
// and deallocation later.
bool ClassLoaderDataGraph::do_unloading() {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);

  // Indicate whether safepoint cleanup is needed.
  _safepoint_cleanup_needed = true;

  ClassLoaderData* data = _head;
  ClassLoaderData* prev = NULL;
  bool seen_dead_loader = false;
  uint loaders_processed = 0;
  uint loaders_removed = 0;

  // Save previous _unloading pointer for CMS which may add to unloading list before
  // purging and we don't want to rewalk the previously unloaded class loader data.
  _saved_unloading = _unloading;

  data = _head;
  while (data != NULL) {
    if (data->is_alive()) {
      prev = data;
      data = data->next();
      loaders_processed++;
      continue;
    }
    seen_dead_loader = true;
    loaders_removed++;
    ClassLoaderData* dead = data;
    dead->unload();
    data = data->next();
    // Remove from loader list.
    // This class loader data will no longer be found
    // in the ClassLoaderDataGraph.
    if (prev != NULL) {
      prev->set_next(data);
    } else {
      assert(dead == _head, "sanity check");
      _head = data;
    }
    dead->set_next(_unloading);
    _unloading = dead;
  }

  log_debug(class, loader, data)("do_unloading: loaders processed %u, loaders removed %u", loaders_processed, loaders_removed);

  return seen_dead_loader;
}

// There's at least one dead class loader.  Purge refererences of healthy module
// reads lists and package export lists to modules belonging to dead loaders.
void ClassLoaderDataGraph::clean_module_and_package_info() {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);

  ClassLoaderData* data = _head;
  while (data != NULL) {
    // Walk a ModuleEntry's reads, and a PackageEntry's exports
    // lists to determine if there are modules on those lists that are now
    // dead and should be removed.  A module's life cycle is equivalent
    // to its defining class loader's life cycle.  Since a module is
    // considered dead if its class loader is dead, these walks must
    // occur after each class loader's aliveness is determined.
    if (data->packages() != NULL) {
      data->packages()->purge_all_package_exports();
    }
    if (data->modules_defined()) {
      data->modules()->purge_all_module_reads();
    }
    data = data->next();
  }
}

void ClassLoaderDataGraph::purge() {
  ClassLoaderData* list = _unloading;
  _unloading = NULL;
  ClassLoaderData* next = list;
  bool classes_unloaded = false;
  while (next != NULL) {
    ClassLoaderData* purge_me = next;
    next = purge_me->next();
    delete purge_me;
    classes_unloaded = true;
  }
  if (classes_unloaded) {
    Metaspace::purge();
    set_metaspace_oom(false);
  }
  DependencyContext::purge_dependency_contexts();
}

int ClassLoaderDataGraph::resize_if_needed() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint!");
  int resized = 0;
  if (Dictionary::does_any_dictionary_needs_resizing()) {
    FOR_ALL_DICTIONARY(cld) {
      if (cld->dictionary()->resize_if_needed()) {
        resized++;
      }
    }
  }
  return resized;
}

ClassLoaderDataGraphKlassIteratorAtomic::ClassLoaderDataGraphKlassIteratorAtomic()
    : _next_klass(NULL) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint!");
  ClassLoaderData* cld = ClassLoaderDataGraph::_head;
  Klass* klass = NULL;

  // Find the first klass in the CLDG.
  while (cld != NULL) {
    assert_locked_or_safepoint(cld->metaspace_lock());
    klass = cld->_klasses;
    if (klass != NULL) {
      _next_klass = klass;
      return;
    }
    cld = cld->next();
  }
}

Klass* ClassLoaderDataGraphKlassIteratorAtomic::next_klass_in_cldg(Klass* klass) {
  Klass* next = klass->next_link();
  if (next != NULL) {
    return next;
  }

  // No more klasses in the current CLD. Time to find a new CLD.
  ClassLoaderData* cld = klass->class_loader_data();
  assert_locked_or_safepoint(cld->metaspace_lock());
  while (next == NULL) {
    cld = cld->next();
    if (cld == NULL) {
      break;
    }
    next = cld->_klasses;
  }

  return next;
}

Klass* ClassLoaderDataGraphKlassIteratorAtomic::next_klass() {
  Klass* head = _next_klass;

  while (head != NULL) {
    Klass* next = next_klass_in_cldg(head);

    Klass* old_head = Atomic::cmpxchg(next, &_next_klass, head);

    if (old_head == head) {
      return head; // Won the CAS.
    }

    head = old_head;
  }

  // Nothing more for the iterator to hand out.
  assert(head == NULL, "head is " PTR_FORMAT ", expected not null:", p2i(head));
  return NULL;
}

ClassLoaderDataGraphMetaspaceIterator::ClassLoaderDataGraphMetaspaceIterator() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint!");
  _data = ClassLoaderDataGraph::_head;
}

ClassLoaderDataGraphMetaspaceIterator::~ClassLoaderDataGraphMetaspaceIterator() {}

ClassLoaderMetaspace* ClassLoaderDataGraphMetaspaceIterator::get_next() {
  assert(_data != NULL, "Should not be NULL in call to the iterator");
  ClassLoaderMetaspace* result = _data->metaspace_or_null();
  _data = _data->next();
  // This result might be NULL for class loaders without metaspace
  // yet.  It would be nice to return only non-null results but
  // there is no guarantee that there will be a non-null result
  // down the list so the caller is going to have to check.
  return result;
}

#ifndef PRODUCT
// callable from debugger
extern "C" int print_loader_data_graph() {
  ResourceMark rm;
  ClassLoaderDataGraph::print_on(tty);
  return 0;
}

void ClassLoaderDataGraph::verify() {
  ClassLoaderDataGraphIterator iter;
  while (ClassLoaderData* cld = iter.get_next()) {
    cld->verify();
  }
}

void ClassLoaderDataGraph::print_on(outputStream * const out) {
  ClassLoaderDataGraphIterator iter;
  while (ClassLoaderData* cld = iter.get_next()) {
    cld->print_on(out);
  }
}
#endif // PRODUCT
