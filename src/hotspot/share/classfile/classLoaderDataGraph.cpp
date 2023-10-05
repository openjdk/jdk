/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"

volatile size_t ClassLoaderDataGraph::_num_array_classes = 0;
volatile size_t ClassLoaderDataGraph::_num_instance_classes = 0;

void ClassLoaderDataGraph::clear_claimed_marks() {
  // The claimed marks of the CLDs in the ClassLoaderDataGraph are cleared
  // outside a safepoint and without locking the ClassLoaderDataGraph_lock.
  // This is required to avoid a deadlock between concurrent GC threads and safepointing.
  //
  // We need to make sure that the CLD contents are fully visible to the
  // reader thread. This is accomplished by acquire/release of the _head,
  // and is sufficient.
  //
  // Any ClassLoaderData added after or during walking the list are prepended to
  // _head. Their claim mark need not be handled here.
  for (ClassLoaderData* cld = Atomic::load_acquire(&_head); cld != nullptr; cld = cld->next()) {
    cld->clear_claim();
  }
}

void ClassLoaderDataGraph::clear_claimed_marks(int claim) {
 for (ClassLoaderData* cld = Atomic::load_acquire(&_head); cld != nullptr; cld = cld->next()) {
    cld->clear_claim(claim);
  }
}

void ClassLoaderDataGraph::verify_claimed_marks_cleared(int claim) {
#ifdef ASSERT
 for (ClassLoaderData* cld = Atomic::load_acquire(&_head); cld != nullptr; cld = cld->next()) {
    cld->verify_not_claimed(claim);
  }
#endif
}

void ClassLoaderDataGraph::clean_deallocate_lists(bool walk_previous_versions) {
  assert(SafepointSynchronize::is_at_safepoint(), "must only be called at safepoint");
  uint loaders_processed = 0;
  for (ClassLoaderData* cld = _head; cld != nullptr; cld = cld->next()) {
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

void ClassLoaderDataGraph::safepoint_and_clean_metaspaces() {
  // Safepoint and mark all metadata with MetadataOnStackMark and then deallocate unused bits of metaspace.
  // This needs to be exclusive to Redefinition, so needs to be a safepoint.
  VM_CleanClassLoaderDataMetaspaces op;
  VMThread::execute(&op);
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
  bool walk_all_metadata = InstanceKlass::should_clean_previous_versions_and_reset();

  MetadataOnStackMark md_on_stack(walk_all_metadata, /*redefinition_walk*/false);
  clean_deallocate_lists(walk_all_metadata);
}

// List head of all class loader data.
ClassLoaderData* volatile ClassLoaderDataGraph::_head = nullptr;
ClassLoaderData* ClassLoaderDataGraph::_unloading_head = nullptr;

bool ClassLoaderDataGraph::_should_clean_deallocate_lists = false;
bool ClassLoaderDataGraph::_safepoint_cleanup_needed = false;
bool ClassLoaderDataGraph::_metaspace_oom = false;

// Add a new class loader data node to the list.  Assign the newly created
// ClassLoaderData into the java/lang/ClassLoader object as a hidden field
ClassLoaderData* ClassLoaderDataGraph::add_to_graph(Handle loader, bool has_class_mirror_holder) {

  assert_lock_strong(ClassLoaderDataGraph_lock);

  ClassLoaderData* cld;

  // First check if another thread beat us to creating the CLD and installing
  // it into the loader while we were waiting for the lock.
  if (!has_class_mirror_holder && loader.not_null()) {
    cld = java_lang_ClassLoader::loader_data_acquire(loader());
    if (cld != nullptr) {
      return cld;
    }
  }

  // We mustn't GC until we've installed the ClassLoaderData in the Graph since the CLD
  // contains oops in _handles that must be walked.  GC doesn't walk CLD from the
  // loader oop in all collections, particularly young collections.
  NoSafepointVerifier no_safepoints;

  cld = new ClassLoaderData(loader, has_class_mirror_holder);

  // First install the new CLD to the Graph.
  cld->set_next(_head);
  Atomic::release_store(&_head, cld);

  // Next associate with the class_loader.
  if (!has_class_mirror_holder) {
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

ClassLoaderData* ClassLoaderDataGraph::add(Handle loader, bool has_class_mirror_holder) {
  MutexLocker ml(ClassLoaderDataGraph_lock);
  ClassLoaderData* loader_data = add_to_graph(loader, has_class_mirror_holder);
  return loader_data;
}

inline void assert_is_safepoint_or_gc() {
  assert(SafepointSynchronize::is_at_safepoint() ||
         Thread::current()->is_ConcurrentGC_thread() ||
         Thread::current()->is_Worker_thread(),
         "Must be called by safepoint or GC");
}

// These are functions called by the GC, which require all of the CLDs, including not yet unlinked CLDs.
void ClassLoaderDataGraph::cld_do(CLDClosure* cl) {
  assert_is_safepoint_or_gc();
  for (ClassLoaderData* cld = Atomic::load_acquire(&_head);  cld != nullptr; cld = cld->next()) {
    cl->do_cld(cld);
  }
}

void ClassLoaderDataGraph::roots_cld_do(CLDClosure* strong, CLDClosure* weak) {
  assert_is_safepoint_or_gc();
  for (ClassLoaderData* cld = Atomic::load_acquire(&_head);  cld != nullptr; cld = cld->next()) {
    CLDClosure* closure = cld->keep_alive() ? strong : weak;
    if (closure != nullptr) {
      closure->do_cld(cld);
    }
  }
}

void ClassLoaderDataGraph::always_strong_cld_do(CLDClosure* cl) {
  assert_is_safepoint_or_gc();
  if (ClassUnloading) {
    roots_cld_do(cl, nullptr);
  } else {
    cld_do(cl);
  }
}

// Closure for locking and iterating through classes. Only lock outside of safepoint.
LockedClassesDo::LockedClassesDo(classes_do_func_t f) : _function(f),
  _do_lock(!SafepointSynchronize::is_at_safepoint()) {
  if (_do_lock) {
    ClassLoaderDataGraph_lock->lock();
  }
}

LockedClassesDo::LockedClassesDo() : _function(nullptr),
  _do_lock(!SafepointSynchronize::is_at_safepoint()) {
  // callers provide their own do_klass
  if (_do_lock) {
    ClassLoaderDataGraph_lock->lock();
  }
}

LockedClassesDo::~LockedClassesDo() {
  if (_do_lock) {
    ClassLoaderDataGraph_lock->unlock();
  }
}


// Iterating over the CLDG needs to be locked because
// unloading can remove entries concurrently soon.
template <bool keep_alive = true>
class ClassLoaderDataGraphIteratorBase : public StackObj {
  ClassLoaderData* _next;
  Thread*          _thread;
  HandleMark       _hm;  // clean up handles when this is done.
  NoSafepointVerifier _nsv; // No safepoints allowed in this scope
                            // unless verifying at a safepoint.

public:
  ClassLoaderDataGraphIteratorBase() : _next(ClassLoaderDataGraph::_head), _thread(Thread::current()), _hm(_thread) {
    if (keep_alive) {
      assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
    } else {
      assert_at_safepoint();
    }
  }

  ClassLoaderData* get_next() {
    ClassLoaderData* cld = _next;
    // Skip already unloaded CLD for concurrent unloading.
    while (cld != nullptr && !cld->is_alive()) {
      cld = cld->next();
    }
    if (cld != nullptr) {
      if (keep_alive) {
        // Keep cld that is being returned alive.
        Handle(_thread, cld->holder());
      }
      _next = cld->next();
    } else {
      _next = nullptr;
    }
    return cld;
  }
};

using ClassLoaderDataGraphIterator = ClassLoaderDataGraphIteratorBase<true /* keep_alive */>;
using ClassLoaderDataGraphIteratorNoKeepAlive = ClassLoaderDataGraphIteratorBase<false /* keep_alive */>;

void ClassLoaderDataGraph::loaded_cld_do(CLDClosure* cl) {
  ClassLoaderDataGraphIterator iter;
  while (ClassLoaderData* cld = iter.get_next()) {
    cl->do_cld(cld);
  }
}

void ClassLoaderDataGraph::loaded_cld_do_no_keepalive(CLDClosure* cl) {
  ClassLoaderDataGraphIteratorNoKeepAlive iter;
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

void ClassLoaderDataGraph::packages_do(void f(PackageEntry*)) {
  assert_locked_or_safepoint(Module_lock);
  ClassLoaderDataGraphIterator iter;
  while (ClassLoaderData* cld = iter.get_next()) {
    cld->packages_do(f);
  }
}

void ClassLoaderDataGraph::loaded_classes_do(KlassClosure* klass_closure) {
  ClassLoaderDataGraphIterator iter;
  while (ClassLoaderData* cld = iter.get_next()) {
    cld->loaded_classes_do(klass_closure);
  }
}

void ClassLoaderDataGraph::classes_unloading_do(void f(Klass* const)) {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  for (ClassLoaderData* cld = _unloading_head; cld != nullptr; cld = cld->unloading_next()) {
    assert(cld->is_unloading(), "invariant");
    cld->classes_do(f);
  }
}

void ClassLoaderDataGraph::verify_dictionary() {
  ClassLoaderDataGraphIteratorNoKeepAlive iter;
  while (ClassLoaderData* cld = iter.get_next()) {
    if (cld->dictionary() != nullptr) {
      cld->dictionary()->verify();
    }
  }
}

#define FOR_ALL_DICTIONARY(X)   ClassLoaderDataGraphIterator iter; \
                                while (ClassLoaderData* X = iter.get_next()) \
                                  if (X->dictionary() != nullptr)

void ClassLoaderDataGraph::print_dictionary(outputStream* st) {
  FOR_ALL_DICTIONARY(cld) {
    st->print("Dictionary for ");
    cld->print_value_on(st);
    st->cr();
    cld->dictionary()->print_on(st);
    st->cr();
  }
}

void ClassLoaderDataGraph::print_table_statistics(outputStream* st) {
  FOR_ALL_DICTIONARY(cld) {
    ResourceMark rm; // loader_name_and_id
    stringStream tempst;
    tempst.print("System Dictionary for %s class loader", cld->loader_name_and_id());
    cld->dictionary()->print_table_statistics(st, tempst.freeze());
  }
}

#ifndef PRODUCT
bool ClassLoaderDataGraph::contains_loader_data(ClassLoaderData* loader_data) {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  for (ClassLoaderData* data = _head; data != nullptr; data = data->next()) {
    if (loader_data == data) {
      return true;
    }
  }

  return false;
}
#endif // PRODUCT

bool ClassLoaderDataGraph::is_valid(ClassLoaderData* loader_data) {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  if (loader_data != nullptr) {
    if (loader_data == ClassLoaderData::the_null_class_loader_data()) {
      return true;
    }
    for (ClassLoaderData* data = _head; data != nullptr; data = data->next()) {
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

  ClassLoaderData* prev = nullptr;
  uint loaders_processed = 0;
  uint loaders_removed = 0;

  for (ClassLoaderData* data = _head; data != nullptr; data = data->next()) {
    if (data->is_alive()) {
      prev = data;
      loaders_processed++;
    } else {
      // Found dead CLD.
      loaders_removed++;
      data->unload();

      // Move dead CLD to unloading list.
      if (prev != nullptr) {
        prev->unlink_next();
      } else {
        assert(data == _head, "sanity check");
        // The GC might be walking this concurrently
        Atomic::store(&_head, data->next());
      }
      data->set_unloading_next(_unloading_head);
      _unloading_head = data;
    }
  }

  log_debug(class, loader, data)("do_unloading: loaders processed %u, loaders removed %u", loaders_processed, loaders_removed);

  return loaders_removed != 0;
}

// There's at least one dead class loader.  Purge refererences of healthy module
// reads lists and package export lists to modules belonging to dead loaders.
void ClassLoaderDataGraph::clean_module_and_package_info() {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);

  ClassLoaderData* data = _head;
  while (data != nullptr) {
    // Walk a ModuleEntry's reads, and a PackageEntry's exports
    // lists to determine if there are modules on those lists that are now
    // dead and should be removed.  A module's life cycle is equivalent
    // to its defining class loader's life cycle.  Since a module is
    // considered dead if its class loader is dead, these walks must
    // occur after each class loader's aliveness is determined.
    if (data->packages() != nullptr) {
      data->packages()->purge_all_package_exports();
    }
    if (data->modules_defined()) {
      data->modules()->purge_all_module_reads();
    }
    data = data->next();
  }
}

void ClassLoaderDataGraph::purge(bool at_safepoint) {
  ClassLoaderData* list = _unloading_head;
  _unloading_head = nullptr;
  ClassLoaderData* next = list;
  bool classes_unloaded = false;
  while (next != nullptr) {
    ClassLoaderData* purge_me = next;
    next = purge_me->unloading_next();
    delete purge_me;
    classes_unloaded = true;
  }

  Metaspace::purge(classes_unloaded);
  if (classes_unloaded) {
    set_metaspace_oom(false);
  }

  DependencyContext::purge_dependency_contexts();

  // If we're purging metadata at a safepoint, clean remaining
  // metaspaces if we need to.
  if (at_safepoint) {
    _safepoint_cleanup_needed = true; // tested and reset next.
    if (should_clean_metaspaces_and_reset()) {
      walk_metadata_and_clean_metaspaces();
    }
  } else {
    // Tell service thread this is a good time to check to see if we should
    // clean loaded CLDGs. This causes another safepoint.
    MutexLocker ml(Service_lock, Mutex::_no_safepoint_check_flag);
    _safepoint_cleanup_needed = true;
    Service_lock->notify_all();
  }
}

ClassLoaderDataGraphKlassIteratorAtomic::ClassLoaderDataGraphKlassIteratorAtomic()
    : _next_klass(nullptr) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint!");
  ClassLoaderData* cld = ClassLoaderDataGraph::_head;
  Klass* klass = nullptr;

  // Find the first klass in the CLDG.
  while (cld != nullptr) {
    assert_locked_or_safepoint(cld->metaspace_lock());
    klass = cld->_klasses;
    if (klass != nullptr) {
      _next_klass = klass;
      return;
    }
    cld = cld->next();
  }
}

Klass* ClassLoaderDataGraphKlassIteratorAtomic::next_klass_in_cldg(Klass* klass) {
  Klass* next = klass->next_link();
  if (next != nullptr) {
    return next;
  }

  // No more klasses in the current CLD. Time to find a new CLD.
  ClassLoaderData* cld = klass->class_loader_data();
  assert_locked_or_safepoint(cld->metaspace_lock());
  while (next == nullptr) {
    cld = cld->next();
    if (cld == nullptr) {
      break;
    }
    next = cld->_klasses;
  }

  return next;
}

Klass* ClassLoaderDataGraphKlassIteratorAtomic::next_klass() {
  Klass* head = _next_klass;

  while (head != nullptr) {
    Klass* next = next_klass_in_cldg(head);

    Klass* old_head = Atomic::cmpxchg(&_next_klass, head, next);

    if (old_head == head) {
      return head; // Won the CAS.
    }

    head = old_head;
  }

  // Nothing more for the iterator to hand out.
  assert(head == nullptr, "head is " PTR_FORMAT ", expected not null:", p2i(head));
  return nullptr;
}

void ClassLoaderDataGraph::verify() {
  ClassLoaderDataGraphIteratorNoKeepAlive iter;
  while (ClassLoaderData* cld = iter.get_next()) {
    cld->verify();
  }
}

#ifndef PRODUCT
// callable from debugger
extern "C" int print_loader_data_graph() {
  ResourceMark rm;
  MutexLocker ml(ClassLoaderDataGraph_lock);
  ClassLoaderDataGraph::print_on(tty);
  return 0;
}

void ClassLoaderDataGraph::print_on(outputStream * const out) {
  ClassLoaderDataGraphIterator iter;
  while (ClassLoaderData* cld = iter.get_next()) {
    cld->print_on(out);
  }
}
#endif // PRODUCT

void ClassLoaderDataGraph::print() { print_on(tty); }
