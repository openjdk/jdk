/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/loaderConstraints.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "oops/klass.inline.hpp"
#include "oops/symbolHandle.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/resourceHash.hpp"

// Overview
//
// The LoaderConstraintTable controls whether two ClassLoaders can resolve the same class name N
// to different InstanceKlasses.
//
//     The design of the algorithm can be found in the OOPSLA'98 paper "Dynamic Class Loading in
//     the Java Virtual Machine" by Sheng Liang and Gilad Bracha.
//
//     To understand the implementation, start with LoaderConstraintTable::{add_entry, check_or_update}
//
// When a class name N is entered into the LoaderConstraintTable, it's mapped to a ConstraintSet which
// contains one or more LoaderConstraints:
//
//   LoaderConstraint_a = { _klass_a, loader_a1, loader_a2, ...}
//   LoaderConstraint_b = { _klass_b, loader_b1, loader_b2, ...}
//   LoaderConstraint_c = { _klass_c, loader_c1, loader_c2, ...}
//   ...
//
// If _klass_<m> is null, when the first loader_<m><n> resolves the name N to a class K,
// we assign _klass_<m> = K.
//
// if _klass_<m> is non-null, when a loader loader_<m><n> tries to resolve the name N to a class K,
// where _klass_<m> != K, a LinkageError is thrown, and the resolution fails.
//
// Management of LoaderConstraints
//
// When the SystemDictionary decides that loader_x and loader_y must resolve the name N to the same class:
// For the name N, find two LoaderConstraints such that:
//
//     - LoaderConstraint_x contains loader_x
//     - LoaderConstraint_y contains loader_y
//
//       (Note that no class loader will appear in more than one LoaderConstraint for
//        each name N, as enforced by the following steps).
//
// If neither LoaderConstraint_x nor LoaderConstraint_y exist, add a new LoaderConstraint that contains
// both loader_x and loader_y.
//
// Otherwise if LoaderConstraint_x exists but LoaderConstraint_y doesn't exist, add loader_y to LoaderConstraint_x,
// or vice versa.
//
// Otherwise if both LoaderConstraints have different values for _klass, a LinkageError is thrown.
//
// Otherwise the two LoaderConstraints are merged into one.

class LoaderConstraint : public CHeapObj<mtClass> {
  InstanceKlass*         _klass;
  // Loader constraints enforce correct linking behavior.
  // Thus, it really operates on ClassLoaderData which represents linking domain,
  // not class loaders.
  GrowableArray<ClassLoaderData*>*  _loaders;                // initiating loaders
 public:
  LoaderConstraint(InstanceKlass* klass, ClassLoaderData* loader1, ClassLoaderData* loader2) :
     _klass(klass) {
    _loaders = new (mtClass) GrowableArray<ClassLoaderData*>(10, mtClass);
    add_loader_data(loader1);
    add_loader_data(loader2);
  }
  LoaderConstraint(const LoaderConstraint& src) = delete;
  LoaderConstraint& operator=(const LoaderConstraint&) = delete;

  ~LoaderConstraint() { delete _loaders; }

  InstanceKlass* klass() const     { return _klass; }
  void set_klass(InstanceKlass* k) { _klass = k; }

  void extend_loader_constraint(Symbol* class_name, ClassLoaderData* loader, InstanceKlass* klass);

  int num_loaders() const { return _loaders->length(); }
  ClassLoaderData* loader_data(int i) { return _loaders->at(i); }
  void add_loader_data(ClassLoaderData* p) { _loaders->push(p); }

  void remove_loader_at(int n) {
    assert(_loaders->at(n)->is_unloading(), "should be unloading");
    _loaders->remove_at(n);
  }
};

// For this class name, these are the set of LoaderConstraints for classes loaded with this name.
class ConstraintSet {                               // copied into hashtable as value
 private:
  GrowableArray<LoaderConstraint*>*  _constraints;   // loader constraints for this class name.

 public:
  ConstraintSet() : _constraints(nullptr) {}
  ConstraintSet(const ConstraintSet&) = delete;
  ConstraintSet& operator=(const ConstraintSet&) = delete;

  void initialize(LoaderConstraint* constraint) {
    _constraints = new (mtClass) GrowableArray<LoaderConstraint*>(5, mtClass);
    _constraints->push(constraint);
  }

  ~ConstraintSet() {
    while (!_constraints->is_empty()) {
      delete _constraints->pop();
    }
    delete _constraints;
  }

  int num_constraints() const { return _constraints->length(); }
  LoaderConstraint* constraint_at(int i) const { return _constraints->at(i); }

  void add_constraint(LoaderConstraint* new_constraint) {
    _constraints->push(new_constraint);
  }

  void remove_constraint(LoaderConstraint* constraint) {
    _constraints->remove(constraint);
    delete constraint;
  }
};


using InternalLoaderConstraintTable = ResourceHashtable<SymbolHandle, ConstraintSet, 107, AnyObj::C_HEAP, mtClass, SymbolHandle::compute_hash>;
static InternalLoaderConstraintTable* _loader_constraint_table;

void LoaderConstraint::extend_loader_constraint(Symbol* class_name,
                                                ClassLoaderData* loader,
                                                InstanceKlass* klass) {
  add_loader_data(loader);
  LogTarget(Info, class, loader, constraints) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    lt.print("extending constraint for name %s by adding loader: %s %s",
               class_name->as_C_string(),
               loader->loader_name_and_id(),
               _klass == nullptr ? " and setting class object" : "");
  }
  if (_klass == nullptr) {
    set_klass(klass);
  } else {
    assert(klass == nullptr || _klass == klass, "constraints corrupted");
  }
}

// The loaderConstraintTable must always be accessed with the
// SystemDictionary lock held. This is true even for readers as
// entries in the table could be being dynamically resized.

void LoaderConstraintTable::initialize() {
  _loader_constraint_table = new (mtClass) InternalLoaderConstraintTable();
}

LoaderConstraint* LoaderConstraintTable::find_loader_constraint(
                                    Symbol* name, ClassLoaderData* loader_data) {

  assert_lock_strong(SystemDictionary_lock);
  ConstraintSet* set = _loader_constraint_table->get(name);
  if (set == nullptr) {
    return nullptr;
  }

  for (int i = 0; i < set->num_constraints(); i++) {
    LoaderConstraint* p = set->constraint_at(i);
    for (int i = p->num_loaders() - 1; i >= 0; i--) {
        if (p->loader_data(i) == loader_data &&
            // skip unloaded klasses
            (p->klass() == nullptr ||
             p->klass()->is_loader_alive())) {
          return p;
        }
    }
  }
  return nullptr;
}

// Either add it to an existing entry in the table or make a new one.
void LoaderConstraintTable::add_loader_constraint(Symbol* name, InstanceKlass* klass,
                                                  ClassLoaderData* loader1, ClassLoaderData* loader2) {
  assert_lock_strong(SystemDictionary_lock);
  LoaderConstraint* constraint = new LoaderConstraint(klass, loader1, loader2);

  // The klass may be null if it hasn't been loaded yet, for instance while checking
  // a parameter name to a method call.  We impose this constraint that the
  // class that is eventually loaded must match between these two loaders.
  bool created;
  ConstraintSet* set = _loader_constraint_table->put_if_absent(name, &created);
  if (created) {
    set->initialize(constraint);
  } else {
    set->add_constraint(constraint);
  }
}

class PurgeUnloadedConstraints : public StackObj {
 public:
  bool do_entry(SymbolHandle& name, ConstraintSet& set) {
    LogTarget(Info, class, loader, constraints) lt;
    int len = set.num_constraints();
    for (int i = len - 1; i >= 0; i--) {
      LoaderConstraint* probe = set.constraint_at(i);
      InstanceKlass* klass = probe->klass();
      // Remove klass that is no longer alive
      if (klass != nullptr &&
          !klass->is_loader_alive()) {
        probe->set_klass(nullptr);
        if (lt.is_enabled()) {
          ResourceMark rm;
          lt.print("purging class object from constraint for name %s,"
                     " loader list:",
                     name->as_C_string());
          for (int i = 0; i < probe->num_loaders(); i++) {
            lt.print("    [%d]: %s", i,
                          probe->loader_data(i)->loader_name_and_id());
          }
        }
      }

      // Remove entries no longer alive from loader array
      for (int n = probe->num_loaders() - 1; n >= 0; n--) {
        if (probe->loader_data(n)->is_unloading()) {
          if (lt.is_enabled()) {
            ResourceMark rm;
            lt.print("purging loader %s from constraint for name %s",
                     probe->loader_data(n)->loader_name_and_id(),
                     name->as_C_string());
          }
          probe->remove_loader_at(n);

          if (lt.is_enabled()) {
            ResourceMark rm;
            lt.print("new loader list:");
            for (int i = 0; i < probe->num_loaders(); i++) {
              lt.print("    [%d]: %s", i,
                            probe->loader_data(i)->loader_name_and_id());
            }
          }
        }
      }
      // Check whether the set should be purged
      if (probe->num_loaders() < 2) {
        if (lt.is_enabled()) {
          ResourceMark rm;
          lt.print("purging complete constraint for name %s",
                   name->as_C_string());
        }

        set.remove_constraint(probe);
      } else {
#ifdef ASSERT
        if (probe->klass() != nullptr) {
          assert(probe->klass()->is_loader_alive(), "klass should be live");
        }
#endif
      }
    }
    if (set.num_constraints() == 0) {
      return true;
    }
    // Don't unlink this set
    return false;
  }
};

void LoaderConstraintTable::purge_loader_constraints() {
  assert_locked_or_safepoint(SystemDictionary_lock);
  // Remove unloaded entries from constraint table
  PurgeUnloadedConstraints purge;
  _loader_constraint_table->unlink(&purge);
}

void log_ldr_constraint_msg(Symbol* class_name, const char* reason,
                        ClassLoaderData* loader1, ClassLoaderData* loader2) {
  LogTarget(Info, class, loader, constraints) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    lt.print("Failed to add constraint for name: %s, loader[0]: %s,"
                " loader[1]: %s, Reason: %s",
                  class_name->as_C_string(),
                  loader1->loader_name_and_id(),
                  loader2->loader_name_and_id(),
                  reason);
  }
}

bool LoaderConstraintTable::add_entry(Symbol* class_name,
                                      InstanceKlass* klass1, ClassLoaderData* loader1,
                                      InstanceKlass* klass2, ClassLoaderData* loader2) {

  LogTarget(Info, class, loader, constraints) lt;
  if (klass1 != nullptr && klass2 != nullptr) {
    if (klass1 == klass2) {
      // Same type already loaded in both places.  There is no need for any constraint.
      return true;
    } else {
      log_ldr_constraint_msg(class_name,
                             "The class objects presented by loader[0] and loader[1] "
                             "are different",
                             loader1, loader2);
      return false;
    }
  }

  InstanceKlass* klass = klass1 != nullptr ? klass1 : klass2;
  LoaderConstraint* pp1 = find_loader_constraint(class_name, loader1);
  if (pp1 != nullptr && pp1->klass() != nullptr) {
    if (klass != nullptr) {
      if (klass != pp1->klass()) {
        log_ldr_constraint_msg(class_name,
                               "The class object presented by loader[0] does not match "
                               "the stored class object in the constraint",
                               loader1, loader2);
        return false;
      }
    } else {
      klass = pp1->klass();
    }
  }

  LoaderConstraint* pp2 = find_loader_constraint(class_name, loader2);
  if (pp2 != nullptr && pp2->klass() != nullptr) {
    if (klass != nullptr) {
      if (klass != pp2->klass()) {
        log_ldr_constraint_msg(class_name,
                               "The class object presented by loader[1] does not match "
                               "the stored class object in the constraint",
                               loader1, loader2);
        return false;
      }
    } else {
      klass = pp2->klass();
    }
  }

  if (pp1 == nullptr && pp2 == nullptr) {

    add_loader_constraint(class_name, klass, loader1, loader2);
    if (lt.is_enabled()) {
      ResourceMark rm;
      lt.print("adding new constraint for name: %s, loader[0]: %s,"
                    " loader[1]: %s",
                    class_name->as_C_string(),
                    loader1->loader_name_and_id(),
                    loader2->loader_name_and_id());
    }
  } else if (pp1 == pp2) {
    /* constraint already imposed */
    if (pp1->klass() == nullptr) {
      pp1->set_klass(klass);
      if (lt.is_enabled()) {
        ResourceMark rm;
        lt.print("setting class object in existing constraint for"
                      " name: %s and loader %s",
                      class_name->as_C_string(),
                      loader1->loader_name_and_id());
      }
    } else {
      assert(pp1->klass() == klass, "loader constraints corrupted");
    }
  } else if (pp1 == nullptr) {
    pp2->extend_loader_constraint(class_name, loader1, klass);
  } else if (pp2 == nullptr) {
    pp1->extend_loader_constraint(class_name, loader1, klass);
  } else {
    merge_loader_constraints(class_name, pp1, pp2, klass);
  }

  return true;
}

// return true if the constraint was updated, false if the constraint is
// violated
bool LoaderConstraintTable::check_or_update(InstanceKlass* k,
                                            ClassLoaderData* loader,
                                            Symbol* name) {
  LogTarget(Info, class, loader, constraints) lt;
  LoaderConstraint* p = find_loader_constraint(name, loader);
  if (p && p->klass() != nullptr && p->klass() != k) {
    if (lt.is_enabled()) {
      ResourceMark rm;
      lt.print("constraint check failed for name %s, loader %s: "
                 "the presented class object differs from that stored",
                 name->as_C_string(),
                 loader->loader_name_and_id());
    }
    return false;
  } else {
    if (p && p->klass() == nullptr) {
      p->set_klass(k);
      if (lt.is_enabled()) {
        ResourceMark rm;
        lt.print("updating constraint for name %s, loader %s, "
                   "by setting class object",
                   name->as_C_string(),
                   loader->loader_name_and_id());
      }
    }
    return true;
  }
}

InstanceKlass* LoaderConstraintTable::find_constrained_klass(Symbol* name,
                                                             ClassLoaderData* loader) {
  LoaderConstraint *p = find_loader_constraint(name, loader);
  if (p != nullptr && p->klass() != nullptr) {
    assert(p->klass()->is_instance_klass(), "sanity");
    if (!p->klass()->is_loaded()) {
      // Only return fully loaded classes.  Classes found through the
      // constraints might still be in the process of loading.
      return nullptr;
    }
    return p->klass();
  }

  // No constraints, or else no klass loaded yet.
  return nullptr;
}

// Removes a class that was added to the table then class loading subsequently failed for this class,
// so we don't have a dangling pointer to InstanceKlass in the LoaderConstraintTable.
void LoaderConstraintTable::remove_failed_loaded_klass(InstanceKlass* klass,
                                                       ClassLoaderData* loader) {

  MutexLocker ml(SystemDictionary_lock);
  Symbol* name = klass->name();
  LoaderConstraint *p = find_loader_constraint(name, loader);
  if (p != nullptr && p->klass() != nullptr && p->klass() == klass) {
    // If this is the klass in the constraint, the error was OOM from the ClassLoader.addClass() call.
    // Other errors during loading (eg. constraint violations) will not have added this klass.
    log_info(class, loader, constraints)("removing klass %s: failed to load", name->as_C_string());
    // We only null out the class, since the constraint for the class name for this loader is still valid as
    // it was added when checking signature loaders for a method or field resolution.
    p->set_klass(nullptr);
  }
}

void LoaderConstraintTable::merge_loader_constraints(Symbol* class_name,
                                                     LoaderConstraint* p1,
                                                     LoaderConstraint* p2,
                                                     InstanceKlass* klass) {

  // Copy into the longer of the constraints.
  LoaderConstraint* dest = p1->num_loaders() <= p2->num_loaders() ? p2 : p1;
  LoaderConstraint* src = dest == p1 ? p2 : p1;

  for (int i = 0; i < src->num_loaders(); i++) {
    // We don't seem to care about duplicates.
    dest->add_loader_data(src->loader_data(i));
  }

  LogTarget(Info, class, loader, constraints) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    lt.print("merged constraints for name %s, new loader list:", class_name->as_C_string());

    for (int i = 0; i < dest->num_loaders(); i++) {
      lt.print("    [%d]: %s", i, dest->loader_data(i)->loader_name_and_id());
    }
    if (dest->klass() == nullptr) {
      lt.print("... and setting class object");
    }
  }

  // dest->klass() will hold null if klass, src->klass(), and old
  // dest->klass() are all null.  In addition, all three must have
  // matching non-null values, otherwise either the constraints would
  // have been violated, or the constraints had been corrupted (and an
  // assertion would fail).
  if (src->klass() != nullptr) {
    assert(src->klass() == klass, "constraints corrupted");
  }
  if (dest->klass() == nullptr) {
    dest->set_klass(klass);
  } else {
    assert(dest->klass() == klass, "constraints corrupted");
  }

  // Remove src from set
  ConstraintSet* set = _loader_constraint_table->get(class_name);
  set->remove_constraint(src);
}

void LoaderConstraintTable::verify() {
  Thread* thread = Thread::current();
  auto check = [&] (SymbolHandle& key, ConstraintSet& set) {
    // foreach constraint in the set, check the klass is in the dictionary or placeholder table.
    int len = set.num_constraints();
    for (int i = 0; i < len; i++) {
      LoaderConstraint* probe = set.constraint_at(i);
      if (probe->klass() != nullptr) {
        InstanceKlass* ik = probe->klass();
        guarantee(key == ik->name(), "name should match");
        Symbol* name = ik->name();
        ClassLoaderData* loader_data = ik->class_loader_data();
        Dictionary* dictionary = loader_data->dictionary();
        InstanceKlass* k = dictionary->find_class(thread, name);
        if (k != nullptr) {
          // We found the class in the dictionary, so we should
          // make sure that the Klass* matches what we already have.
          guarantee(k == probe->klass(), "klass should be in dictionary");
          // If we don't find the class in the dictionary, it is
          // in the process of loading and may or may not be in the placeholder table.
        }
      }
      for (int n = 0; n< probe->num_loaders(); n++) {
        assert(ClassLoaderDataGraph::contains_loader_data(probe->loader_data(n)), "The loader is missing");
      }
    }
  };
  assert_locked_or_safepoint(SystemDictionary_lock);
  _loader_constraint_table->iterate_all(check);
}

void LoaderConstraintTable::print_table_statistics(outputStream* st) {
  auto size = [&] (SymbolHandle& key, ConstraintSet& set) {
    // sizeof set is included in the size of the hashtable node
    int sum = 0;
    int len = set.num_constraints();
    for (int i = 0; i < len; i++) {
      LoaderConstraint* probe = set.constraint_at(i);
      sum += (int)(sizeof(*probe) + (probe->num_loaders() * sizeof(ClassLoaderData*)));
    }
    return sum;
  };
  TableStatistics ts = _loader_constraint_table->statistics_calculate(size);
  ts.print(st, "LoaderConstraintTable");
}

// Called with the system dictionary lock held
void LoaderConstraintTable::print_on(outputStream* st) {
  auto printer = [&] (SymbolHandle& key, ConstraintSet& set) {
    int len = set.num_constraints();
    for (int i = 0; i < len; i++) {
      LoaderConstraint* probe = set.constraint_at(i);
      st->print("Symbol: %s loaders:", key->as_C_string());
      for (int n = 0; n < probe->num_loaders(); n++) {
        st->cr();
        st->print("    ");
        probe->loader_data(n)->print_value_on(st);
      }
      st->cr();
    }
  };
  assert_locked_or_safepoint(SystemDictionary_lock);
  ResourceMark rm;
  st->print_cr("Java loader constraints (table_size=%d, constraints=%d)",
               _loader_constraint_table->table_size(), _loader_constraint_table->number_of_entries());
  _loader_constraint_table->iterate_all(printer);
}

void LoaderConstraintTable::print() { print_on(tty); }
