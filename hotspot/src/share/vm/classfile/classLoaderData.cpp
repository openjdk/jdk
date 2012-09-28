/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

// A ClassLoaderData identifies the full set of class types that a class
// loader's name resolution strategy produces for a given configuration of the
// class loader.
// Class types in the ClassLoaderData may be defined by from class file binaries
// provided by the class loader, or from other class loader it interacts with
// according to its name resolution strategy.
//
// Class loaders that implement a deterministic name resolution strategy
// (including with respect to their delegation behavior), such as the boot, the
// extension, and the system loaders of the JDK's built-in class loader
// hierarchy, always produce the same linkset for a given configuration.
//
// ClassLoaderData carries information related to a linkset (e.g.,
// metaspace holding its klass definitions).
// The System Dictionary and related data structures (e.g., placeholder table,
// loader constraints table) as well as the runtime representation of classes
// only reference ClassLoaderData.
//
// Instances of java.lang.ClassLoader holds a pointer to a ClassLoaderData that
// that represent the loader's "linking domain" in the JVM.
//
// The bootstrap loader (represented by NULL) also has a ClassLoaderData,
// the singleton class the_null_class_loader_data().

#include "precompiled.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceShared.hpp"
#include "prims/jvmtiRedefineClasses.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/mutex.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/synchronizer.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/ostream.hpp"

ClassLoaderData * ClassLoaderData::_the_null_class_loader_data = NULL;

ClassLoaderData::ClassLoaderData(Handle h_class_loader) : _class_loader(h_class_loader()),
  _metaspace(NULL), _unloading(false), _klasses(NULL),
  _claimed(0), _jmethod_ids(NULL), _handles(NULL),
  _deallocate_list(NULL), _next(NULL),
  _metaspace_lock(new Mutex(Monitor::leaf+1, "Metaspace allocation lock", true)) {
    // empty
}

bool ClassLoaderData::claim() {
  if (_claimed == 1) {
    return false;
  }

  return (int) Atomic::cmpxchg(1, &_claimed, 0) == 0;
}

void ClassLoaderData::oops_do(OopClosure* f, KlassClosure* klass_closure, bool must_claim) {
  if (must_claim && !claim()) {
    return;
  }

  f->do_oop(&_class_loader);
  _handles->oops_do(f);
  if (klass_closure != NULL) {
    classes_do(klass_closure);
  }
}

void ClassLoaderData::classes_do(KlassClosure* klass_closure) {
  for (Klass* k = _klasses; k != NULL; k = k->next_link()) {
    klass_closure->do_klass(k);
  }
}

void ClassLoaderData::classes_do(void f(InstanceKlass*)) {
  for (Klass* k = _klasses; k != NULL; k = k->next_link()) {
    if (k->oop_is_instance()) {
      f(InstanceKlass::cast(k));
    }
  }
}

void ClassLoaderData::record_dependency(Klass* k, TRAPS) {
  ClassLoaderData * const from_cld = this;
  ClassLoaderData * const to_cld = k->class_loader_data();

  // Records dependency between non-null class loaders only.
  if (to_cld->is_the_null_class_loader_data() || from_cld->is_the_null_class_loader_data()) {
    return;
  }

  // Check that this dependency isn't from the same or parent class_loader
  oop to = to_cld->class_loader();
  oop from = from_cld->class_loader();

  oop curr = from;
  while (curr != NULL) {
    if (curr == to) {
      return; // this class loader is in the parent list, no need to add it.
    }
    curr = java_lang_ClassLoader::parent(curr);
  }

  // It's a dependency we won't find through GC, add it. This is relatively rare
  from_cld->add_dependency(to_cld, CHECK);
}

bool ClassLoaderData::has_dependency(ClassLoaderData* dependency) {
  oop loader = dependency->class_loader();

  // Get objArrayOop out of the class_loader oop and see if this dependency
  // is there.  Don't safepoint!  These are all oops.
  // Dependency list is (oop class_loader, objArrayOop next)
  objArrayOop ok = (objArrayOop)java_lang_ClassLoader::dependencies(class_loader());
  while (ok != NULL) {
    if (ok->obj_at(0) == loader) {
      return true;
    }
    ok = (objArrayOop)ok->obj_at(1);
  }
  return false;
}

void ClassLoaderData::add_dependency(ClassLoaderData* dependency, TRAPS) {
  // Minimize the number of duplicates in the list.
  if (has_dependency(dependency)) {
    return;
  }

  // Create a new dependency node with fields for (class_loader, next)
  objArrayOop deps = oopFactory::new_objectArray(2, CHECK);
  deps->obj_at_put(0, dependency->class_loader());

  // Add this lock free, using compare and exchange, need barriers for GC
  // Do the barrier first.
  HeapWord* addr = java_lang_ClassLoader::dependencies_addr(class_loader());
  while (true) {
    oop old_dependency = java_lang_ClassLoader::dependencies(class_loader());
    deps->obj_at_put(1, old_dependency);

    oop newold = oopDesc::atomic_compare_exchange_oop((oop)deps, addr, old_dependency, true);
    if (newold == old_dependency) {
      update_barrier_set((void*)addr, (oop)deps);
      // we won the race to add this dependency
      break;
    }
  }
}


void ClassLoaderDataGraph::clear_claimed_marks() {
  for (ClassLoaderData* cld = _head; cld != NULL; cld = cld->next()) {
    cld->clear_claimed();
  }
}

void ClassLoaderData::add_class(Klass* k) {
  MutexLockerEx ml(metaspace_lock(),  Mutex::_no_safepoint_check_flag);
  Klass* old_value = _klasses;
  k->set_next_link(old_value);
  // link the new item into the list
  _klasses = k;

  if (TraceClassLoaderData && k->class_loader_data() != NULL) {
    ResourceMark rm;
    tty->print_cr("[TraceClassLoaderData] Adding k: " PTR_FORMAT " %s to CLD: "
                  PTR_FORMAT " loader: " PTR_FORMAT " %s",
                  k,
                  k->external_name(),
                  k->class_loader_data(),
                  k->class_loader(),
                  k->class_loader() != NULL ? k->class_loader()->klass()->external_name() : "NULL"
      );
  }
}

// This is called by InstanceKlass::deallocate_contents() to remove the
// scratch_class for redefine classes.  We need a lock because there it may not
// be called at a safepoint if there's an error.
void ClassLoaderData::remove_class(Klass* scratch_class) {
  MutexLockerEx ml(metaspace_lock(),  Mutex::_no_safepoint_check_flag);
  Klass* prev = NULL;
  for (Klass* k = _klasses; k != NULL; k = k->next_link()) {
    if (k == scratch_class) {
      if (prev == NULL) {
        _klasses = k->next_link();
      } else {
        Klass* next = k->next_link();
        prev->set_next_link(next);
      }
      return;
    }
    prev = k;
  }
  ShouldNotReachHere();   // should have found this class!!
}

ClassLoaderData::~ClassLoaderData() {
  Metaspace *m = _metaspace;
  if (m != NULL) {
    _metaspace = NULL;
    // release the metaspace
    delete m;
    // release the handles
    if (_handles != NULL) {
      JNIHandleBlock::release_block(_handles);
      _handles = NULL;
    }
  }

  // Clear all the JNI handles for methods
  // These aren't deallocated and are going to look like a leak, but that's
  // needed because we can't really get rid of jmethodIDs because we don't
  // know when native code is going to stop using them.  The spec says that
  // they're "invalid" but existing programs likely rely on their being
  // NULL after class unloading.
  if (_jmethod_ids != NULL) {
    Method::clear_jmethod_ids(this);
  }
  // Delete lock
  delete _metaspace_lock;

  // Delete free list
  if (_deallocate_list != NULL) {
    delete _deallocate_list;
  }
}

Metaspace* ClassLoaderData::metaspace_non_null() {
  // If the metaspace has not been allocated, create a new one.  Might want
  // to create smaller arena for Reflection class loaders also.
  // The reason for the delayed allocation is because some class loaders are
  // simply for delegating with no metadata of their own.
  if (_metaspace == NULL) {
    MutexLockerEx ml(metaspace_lock(),  Mutex::_no_safepoint_check_flag);
    // Check again if metaspace has been allocated while we were getting this lock.
    if (_metaspace != NULL) {
      return _metaspace;
    }
    if (class_loader() == NULL) {
      assert(this == the_null_class_loader_data(), "Must be");
      size_t word_size = Metaspace::first_chunk_word_size();
      set_metaspace(new Metaspace(_metaspace_lock, word_size));
    } else {
      set_metaspace(new Metaspace(_metaspace_lock));  // default size for now.
    }
  }
  return _metaspace;
}

JNIHandleBlock* ClassLoaderData::handles() const           { return _handles; }
void ClassLoaderData::set_handles(JNIHandleBlock* handles) { _handles = handles; }

jobject ClassLoaderData::add_handle(Handle h) {
  MutexLockerEx ml(metaspace_lock(),  Mutex::_no_safepoint_check_flag);
  if (handles() == NULL) {
    set_handles(JNIHandleBlock::allocate_block());
  }
  return handles()->allocate_handle(h());
}

// Add this metadata pointer to be freed when it's safe.  This is only during
// class unloading because Handles might point to this metadata field.
void ClassLoaderData::add_to_deallocate_list(Metadata* m) {
  // Metadata in shared region isn't deleted.
  if (!m->is_shared()) {
    MutexLockerEx ml(metaspace_lock(),  Mutex::_no_safepoint_check_flag);
    if (_deallocate_list == NULL) {
      _deallocate_list = new (ResourceObj::C_HEAP, mtClass) GrowableArray<Metadata*>(100, true);
    }
    _deallocate_list->append_if_missing(m);
  }
}

// Deallocate free metadata on the free list.  How useful the PermGen was!
void ClassLoaderData::free_deallocate_list() {
  // Don't need lock, at safepoint
  assert(SafepointSynchronize::is_at_safepoint(), "only called at safepoint");
  if (_deallocate_list == NULL) {
    return;
  }
  // Go backwards because this removes entries that are freed.
  for (int i = _deallocate_list->length() - 1; i >= 0; i--) {
    Metadata* m = _deallocate_list->at(i);
    if (!m->on_stack()) {
      _deallocate_list->remove_at(i);
      // There are only three types of metadata that we deallocate directly.
      // Cast them so they can be used by the template function.
      if (m->is_method()) {
        MetadataFactory::free_metadata(this, (Method*)m);
      } else if (m->is_constantPool()) {
        MetadataFactory::free_metadata(this, (ConstantPool*)m);
      } else if (m->is_klass()) {
        MetadataFactory::free_metadata(this, (InstanceKlass*)m);
      } else {
        ShouldNotReachHere();
      }
    }
  }
}

#ifndef PRODUCT
void ClassLoaderData::print_loader(ClassLoaderData *loader_data, outputStream* out) {
  oop class_loader = loader_data->class_loader();
  out->print("%s", SystemDictionary::loader_name(class_loader));
}

// Define to dump klasses
#undef CLD_DUMP_KLASSES

void ClassLoaderData::dump(outputStream * const out) {
  ResourceMark rm;
  out->print("ClassLoaderData CLD: "PTR_FORMAT", loader: "PTR_FORMAT", loader_klass: "PTR_FORMAT" %s {",
      this, class_loader(),
      class_loader() != NULL ? class_loader()->klass() : NULL,
      class_loader() != NULL ? class_loader()->klass()->external_name() : "NULL");
  if (claimed()) out->print(" claimed ");
  if (is_unloading()) out->print(" unloading ");
  out->print(" handles " INTPTR_FORMAT, handles());
  out->cr();
  if (metaspace_or_null() != NULL) {
    out->print_cr("metaspace: " PTR_FORMAT, metaspace_or_null());
    metaspace_or_null()->dump(out);
  } else {
    out->print_cr("metaspace: NULL");
  }

#ifdef CLD_DUMP_KLASSES
  if (Verbose) {
    ResourceMark rm;
    Klass* k = _klasses;
    while (k != NULL) {
      out->print_cr("klass "PTR_FORMAT", %s, CT: %d, MUT: %d", k, k->name()->as_C_string(),
          k->has_modified_oops(), k->has_accumulated_modified_oops());
      k = k->next_link();
    }
  }
#endif  // CLD_DUMP_KLASSES
#undef CLD_DUMP_KLASSES
  if (_jmethod_ids != NULL) {
    Method::print_jmethod_ids(this, out);
  }
  out->print_cr("}");
}
#endif // PRODUCT

void ClassLoaderData::verify() {
  oop cl = class_loader();

  guarantee(this == class_loader_data(cl), "Must be the same");
  guarantee(cl != NULL || this == ClassLoaderData::the_null_class_loader_data(), "must be");

  // Verify the integrity of the allocated space.
  if (metaspace_or_null() != NULL) {
    metaspace_or_null()->verify();
  }

  for (Klass* k = _klasses; k != NULL; k = k->next_link()) {
    guarantee(k->class_loader_data() == this, "Must be the same");
    k->verify();
  }
}

// GC root of class loader data created.
ClassLoaderData* ClassLoaderDataGraph::_head = NULL;
ClassLoaderData* ClassLoaderDataGraph::_unloading = NULL;
ClassLoaderData* ClassLoaderDataGraph::_saved_head = NULL;


// Add a new class loader data node to the list.  Assign the newly created
// ClassLoaderData into the java/lang/ClassLoader object as a hidden field
ClassLoaderData* ClassLoaderDataGraph::add(ClassLoaderData** cld_addr, Handle loader_data) {
  // Not assigned a class loader data yet.
  // Create one.
  ClassLoaderData* *list_head = &_head;
  ClassLoaderData* next = _head;
  ClassLoaderData* cld = new ClassLoaderData(loader_data);

  // First, Atomically set it.
  ClassLoaderData* old = (ClassLoaderData*) Atomic::cmpxchg_ptr(cld, cld_addr, NULL);
  if (old != NULL) {
    delete cld;
    // Returns the data.
    return old;
  }

  // We won the race, and therefore the task of adding the data to the list of
  // class loader data
  do {
    cld->set_next(next);
    ClassLoaderData* exchanged = (ClassLoaderData*)Atomic::cmpxchg_ptr(cld, list_head, next);
    if (exchanged == next) {
      if (TraceClassLoaderData) {
        tty->print("[ClassLoaderData: ");
        tty->print("create class loader data "PTR_FORMAT, cld);
        tty->print(" for instance "PTR_FORMAT" of ", cld->class_loader());
        loader_data->klass()->name()->print_symbol_on(tty);
        tty->print_cr("]");
      }
      return cld;
    }
    next = exchanged;
  } while (true);
}

void ClassLoaderDataGraph::oops_do(OopClosure* f, KlassClosure* klass_closure, bool must_claim) {
  for (ClassLoaderData* cld = _head; cld != NULL; cld = cld->next()) {
    cld->oops_do(f, klass_closure, must_claim);
  }
}

void ClassLoaderDataGraph::always_strong_oops_do(OopClosure* f, KlassClosure* klass_closure, bool must_claim) {
  if (ClassUnloading) {
    ClassLoaderData::the_null_class_loader_data()->oops_do(f, klass_closure, must_claim);
  } else {
    ClassLoaderDataGraph::oops_do(f, klass_closure, must_claim);
  }
}

void ClassLoaderDataGraph::classes_do(KlassClosure* klass_closure) {
  for (ClassLoaderData* cld = _head; cld != NULL; cld = cld->next()) {
    cld->classes_do(klass_closure);
  }
}

GrowableArray<ClassLoaderData*>* ClassLoaderDataGraph::new_clds() {
  assert(_head == NULL || _saved_head != NULL, "remember_new_clds(true) not called?");

  GrowableArray<ClassLoaderData*>* array = new GrowableArray<ClassLoaderData*>();

  // The CLDs in [_head, _saved_head] were all added during last call to remember_new_clds(true);
  ClassLoaderData* curr = _head;
  while (curr != _saved_head) {
    if (!curr->claimed()) {
      array->push(curr);

      if (TraceClassLoaderData) {
        tty->print("[ClassLoaderData] found new CLD: ");
        curr->print_value_on(tty);
        tty->cr();
      }
    }

    curr = curr->_next;
  }

  return array;
}

#ifndef PRODUCT
// for debugging and hsfind(x)
bool ClassLoaderDataGraph::contains(address x) {
  // I think we need the _metaspace_lock taken here because the class loader
  // data graph could be changing while we are walking it (new entries added,
  // new entries being unloaded, etc).
  if (DumpSharedSpaces) {
    // There are only two metaspaces to worry about.
    ClassLoaderData* ncld = ClassLoaderData::the_null_class_loader_data();
    return (ncld->ro_metaspace()->contains(x) || ncld->rw_metaspace()->contains(x));
  }

  if (UseSharedSpaces && MetaspaceShared::is_in_shared_space(x)) {
    return true;
  }

  for (ClassLoaderData* cld = _head; cld != NULL; cld = cld->next()) {
    if (cld->metaspace_or_null() != NULL && cld->metaspace_or_null()->contains(x)) {
      return true;
    }
  }

  // Could also be on an unloading list which is okay, ie. still allocated
  // for a little while.
  for (ClassLoaderData* ucld = _unloading; ucld != NULL; ucld = ucld->next()) {
    if (ucld->metaspace_or_null() != NULL && ucld->metaspace_or_null()->contains(x)) {
      return true;
    }
  }
  return false;
}

bool ClassLoaderDataGraph::contains_loader_data(ClassLoaderData* loader_data) {
  for (ClassLoaderData* data = _head; data != NULL; data = data->next()) {
    if (loader_data == data) {
      return true;
    }
  }

  return false;
}
#endif // PRODUCT

// Move class loader data from main list to the unloaded list for unloading
// and deallocation later.
bool ClassLoaderDataGraph::do_unloading(BoolObjectClosure* is_alive) {
  ClassLoaderData* data = _head;
  ClassLoaderData* prev = NULL;
  bool seen_dead_loader = false;
  // mark metadata seen on the stack and code cache so we can delete
  // unneeded entries.
  bool has_redefined_a_class = JvmtiExport::has_redefined_a_class();
  MetadataOnStackMark md_on_stack;
  while (data != NULL) {
    if (data->class_loader() == NULL || is_alive->do_object_b(data->class_loader())) {
      assert(data->claimed(), "class loader data must have been claimed");
      if (has_redefined_a_class) {
        data->classes_do(InstanceKlass::purge_previous_versions);
      }
      data->free_deallocate_list();
      prev = data;
      data = data->next();
      continue;
    }
    seen_dead_loader = true;
    ClassLoaderData* dead = data;
    dead->mark_for_unload();
    if (TraceClassLoaderData) {
      tty->print("[ClassLoaderData: unload loader data "PTR_FORMAT, dead);
      tty->print(" for instance "PTR_FORMAT" of ", dead->class_loader());
      dead->class_loader()->klass()->name()->print_symbol_on(tty);
      tty->print_cr("]");
    }
    data = data->next();
    // Remove from loader list.
    if (prev != NULL) {
      prev->set_next(data);
    } else {
      assert(dead == _head, "sanity check");
      _head = data;
    }
    dead->set_next(_unloading);
    _unloading = dead;
  }
  return seen_dead_loader;
}

void ClassLoaderDataGraph::purge() {
  ClassLoaderData* list = _unloading;
  _unloading = NULL;
  ClassLoaderData* next = list;
  while (next != NULL) {
    ClassLoaderData* purge_me = next;
    next = purge_me->next();
    delete purge_me;
  }
}

// CDS support

// Global metaspaces for writing information to the shared archive.  When
// application CDS is supported, we may need one per metaspace, so this
// sort of looks like it.
Metaspace* ClassLoaderData::_ro_metaspace = NULL;
Metaspace* ClassLoaderData::_rw_metaspace = NULL;
static bool _shared_metaspaces_initialized = false;

// Initialize shared metaspaces (change to call from somewhere not lazily)
void ClassLoaderData::initialize_shared_metaspaces() {
  assert(DumpSharedSpaces, "only use this for dumping shared spaces");
  assert(this == ClassLoaderData::the_null_class_loader_data(),
         "only supported for null loader data for now");
  assert (!_shared_metaspaces_initialized, "only initialize once");
  MutexLockerEx ml(metaspace_lock(),  Mutex::_no_safepoint_check_flag);
  _ro_metaspace = new Metaspace(_metaspace_lock, SharedReadOnlySize/wordSize);
  _rw_metaspace = new Metaspace(_metaspace_lock, SharedReadWriteSize/wordSize);
  _shared_metaspaces_initialized = true;
}

Metaspace* ClassLoaderData::ro_metaspace() {
  assert(_ro_metaspace != NULL, "should already be initialized");
  return _ro_metaspace;
}

Metaspace* ClassLoaderData::rw_metaspace() {
  assert(_rw_metaspace != NULL, "should already be initialized");
  return _rw_metaspace;
}


ClassLoaderDataGraphMetaspaceIterator::ClassLoaderDataGraphMetaspaceIterator() {
  _data = ClassLoaderDataGraph::_head;
}

ClassLoaderDataGraphMetaspaceIterator::~ClassLoaderDataGraphMetaspaceIterator() {}

#ifndef PRODUCT
// callable from debugger
extern "C" int print_loader_data_graph() {
  ClassLoaderDataGraph::dump_on(tty);
  return 0;
}

void ClassLoaderDataGraph::verify() {
  for (ClassLoaderData* data = _head; data != NULL; data = data->next()) {
    data->verify();
  }
}

void ClassLoaderDataGraph::dump_on(outputStream * const out) {
  for (ClassLoaderData* data = _head; data != NULL; data = data->next()) {
    data->dump(out);
  }
  MetaspaceAux::dump(out);
}

void ClassLoaderData::print_value_on(outputStream* out) const {
  if (class_loader() == NULL) {
    out->print_cr("NULL class_loader");
  } else {
    out->print("class loader "PTR_FORMAT, this);
    class_loader()->print_value_on(out);
  }
}
#endif // PRODUCT
