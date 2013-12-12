/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/bytecodeAssembler.hpp"
#include "classfile/defaultMethods.hpp"
#include "classfile/symbolTable.hpp"
#include "memory/allocation.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/signature.hpp"
#include "runtime/thread.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.hpp"
#include "oops/method.hpp"
#include "utilities/accessFlags.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/ostream.hpp"
#include "utilities/pair.hpp"
#include "utilities/resourceHash.hpp"

typedef enum { QUALIFIED, DISQUALIFIED } QualifiedState;

// Because we use an iterative algorithm when iterating over the type
// hierarchy, we can't use traditional scoped objects which automatically do
// cleanup in the destructor when the scope is exited.  PseudoScope (and
// PseudoScopeMark) provides a similar functionality, but for when you want a
// scoped object in non-stack memory (such as in resource memory, as we do
// here).  You've just got to remember to call 'destroy()' on the scope when
// leaving it (and marks have to be explicitly added).
class PseudoScopeMark : public ResourceObj {
 public:
  virtual void destroy() = 0;
};

class PseudoScope : public ResourceObj {
 private:
  GrowableArray<PseudoScopeMark*> _marks;
 public:

  static PseudoScope* cast(void* data) {
    return static_cast<PseudoScope*>(data);
  }

  void add_mark(PseudoScopeMark* psm) {
   _marks.append(psm);
  }

  void destroy() {
    for (int i = 0; i < _marks.length(); ++i) {
      _marks.at(i)->destroy();
    }
  }
};

#ifndef PRODUCT
static void print_slot(outputStream* str, Symbol* name, Symbol* signature) {
  ResourceMark rm;
  str->print("%s%s", name->as_C_string(), signature->as_C_string());
}

static void print_method(outputStream* str, Method* mo, bool with_class=true) {
  ResourceMark rm;
  if (with_class) {
    str->print("%s.", mo->klass_name()->as_C_string());
  }
  print_slot(str, mo->name(), mo->signature());
}
#endif // ndef PRODUCT

/**
 * Perform a depth-first iteration over the class hierarchy, applying
 * algorithmic logic as it goes.
 *
 * This class is one half of the inheritance hierarchy analysis mechanism.
 * It is meant to be used in conjunction with another class, the algorithm,
 * which is indicated by the ALGO template parameter.  This class can be
 * paired with any algorithm class that provides the required methods.
 *
 * This class contains all the mechanics for iterating over the class hierarchy
 * starting at a particular root, without recursing (thus limiting stack growth
 * from this point).  It visits each superclass (if present) and superinterface
 * in a depth-first manner, with callbacks to the ALGO class as each class is
 * encountered (visit()), The algorithm can cut-off further exploration of a
 * particular branch by returning 'false' from a visit() call.
 *
 * The ALGO class, must provide a visit() method, which each of which will be
 * called once for each node in the inheritance tree during the iteration.  In
 * addition, it can provide a memory block via new_node_data(InstanceKlass*),
 * which it can use for node-specific storage (and access via the
 * current_data() and data_at_depth(int) methods).
 *
 * Bare minimum needed to be an ALGO class:
 * class Algo : public HierarchyVisitor<Algo> {
 *   void* new_node_data(InstanceKlass* cls) { return NULL; }
 *   void free_node_data(void* data) { return; }
 *   bool visit() { return true; }
 * };
 */
template <class ALGO>
class HierarchyVisitor : StackObj {
 private:

  class Node : public ResourceObj {
   public:
    InstanceKlass* _class;
    bool _super_was_visited;
    int _interface_index;
    void* _algorithm_data;

    Node(InstanceKlass* cls, void* data, bool visit_super)
        : _class(cls), _super_was_visited(!visit_super),
          _interface_index(0), _algorithm_data(data) {}

    int number_of_interfaces() { return _class->local_interfaces()->length(); }
    int interface_index() { return _interface_index; }
    void set_super_visited() { _super_was_visited = true; }
    void increment_visited_interface() { ++_interface_index; }
    void set_all_interfaces_visited() {
      _interface_index = number_of_interfaces();
    }
    bool has_visited_super() { return _super_was_visited; }
    bool has_visited_all_interfaces() {
      return interface_index() >= number_of_interfaces();
    }
    InstanceKlass* interface_at(int index) {
      return InstanceKlass::cast(_class->local_interfaces()->at(index));
    }
    InstanceKlass* next_super() { return _class->java_super(); }
    InstanceKlass* next_interface() {
      return interface_at(interface_index());
    }
  };

  bool _cancelled;
  GrowableArray<Node*> _path;

  Node* current_top() const { return _path.top(); }
  bool has_more_nodes() const { return !_path.is_empty(); }
  void push(InstanceKlass* cls, void* data) {
    assert(cls != NULL, "Requires a valid instance class");
    Node* node = new Node(cls, data, has_super(cls));
    _path.push(node);
  }
  void pop() { _path.pop(); }

  void reset_iteration() {
    _cancelled = false;
    _path.clear();
  }
  bool is_cancelled() const { return _cancelled; }

  // This code used to skip interface classes because their only
  // superclass was j.l.Object which would be also covered by class
  // superclass hierarchy walks. Now that the starting point can be
  // an interface, we must ensure we catch j.l.Object as the super.
  static bool has_super(InstanceKlass* cls) {
    return cls->super() != NULL;
  }

  Node* node_at_depth(int i) const {
    return (i >= _path.length()) ? NULL : _path.at(_path.length() - i - 1);
  }

 protected:

  // Accessors available to the algorithm
  int current_depth() const { return _path.length() - 1; }

  InstanceKlass* class_at_depth(int i) {
    Node* n = node_at_depth(i);
    return n == NULL ? NULL : n->_class;
  }
  InstanceKlass* current_class() { return class_at_depth(0); }

  void* data_at_depth(int i) {
    Node* n = node_at_depth(i);
    return n == NULL ? NULL : n->_algorithm_data;
  }
  void* current_data() { return data_at_depth(0); }

  void cancel_iteration() { _cancelled = true; }

 public:

  void run(InstanceKlass* root) {
    ALGO* algo = static_cast<ALGO*>(this);

    reset_iteration();

    void* algo_data = algo->new_node_data(root);
    push(root, algo_data);
    bool top_needs_visit = true;

    do {
      Node* top = current_top();
      if (top_needs_visit) {
        if (algo->visit() == false) {
          // algorithm does not want to continue along this path.  Arrange
          // it so that this state is immediately popped off the stack
          top->set_super_visited();
          top->set_all_interfaces_visited();
        }
        top_needs_visit = false;
      }

      if (top->has_visited_super() && top->has_visited_all_interfaces()) {
        algo->free_node_data(top->_algorithm_data);
        pop();
      } else {
        InstanceKlass* next = NULL;
        if (top->has_visited_super() == false) {
          next = top->next_super();
          top->set_super_visited();
        } else {
          next = top->next_interface();
          top->increment_visited_interface();
        }
        assert(next != NULL, "Otherwise we shouldn't be here");
        algo_data = algo->new_node_data(next);
        push(next, algo_data);
        top_needs_visit = true;
      }
    } while (!is_cancelled() && has_more_nodes());
  }
};

#ifndef PRODUCT
class PrintHierarchy : public HierarchyVisitor<PrintHierarchy> {
 public:

  bool visit() {
    InstanceKlass* cls = current_class();
    streamIndentor si(tty, current_depth() * 2);
    tty->indent().print_cr("%s", cls->name()->as_C_string());
    return true;
  }

  void* new_node_data(InstanceKlass* cls) { return NULL; }
  void free_node_data(void* data) { return; }
};
#endif // ndef PRODUCT

// Used to register InstanceKlass objects and all related metadata structures
// (Methods, ConstantPools) as "in-use" by the current thread so that they can't
// be deallocated by class redefinition while we're using them.  The classes are
// de-registered when this goes out of scope.
//
// Once a class is registered, we need not bother with methodHandles or
// constantPoolHandles for it's associated metadata.
class KeepAliveRegistrar : public StackObj {
 private:
  Thread* _thread;
  GrowableArray<ConstantPool*> _keep_alive;

 public:
  KeepAliveRegistrar(Thread* thread) : _thread(thread), _keep_alive(20) {
    assert(thread == Thread::current(), "Must be current thread");
  }

  ~KeepAliveRegistrar() {
    for (int i = _keep_alive.length() - 1; i >= 0; --i) {
      ConstantPool* cp = _keep_alive.at(i);
      int idx = _thread->metadata_handles()->find_from_end(cp);
      assert(idx > 0, "Must be in the list");
      _thread->metadata_handles()->remove_at(idx);
    }
  }

  // Register a class as 'in-use' by the thread.  It's fine to register a class
  // multiple times (though perhaps inefficient)
  void register_class(InstanceKlass* ik) {
    ConstantPool* cp = ik->constants();
    _keep_alive.push(cp);
    _thread->metadata_handles()->push(cp);
  }
};

class KeepAliveVisitor : public HierarchyVisitor<KeepAliveVisitor> {
 private:
  KeepAliveRegistrar* _registrar;

 public:
  KeepAliveVisitor(KeepAliveRegistrar* registrar) : _registrar(registrar) {}

  void* new_node_data(InstanceKlass* cls) { return NULL; }
  void free_node_data(void* data) { return; }

  bool visit() {
    _registrar->register_class(current_class());
    return true;
  }
};


// A method family contains a set of all methods that implement a single
// erased method. As members of the set are collected while walking over the
// hierarchy, they are tagged with a qualification state.  The qualification
// state for an erased method is set to disqualified if there exists a path
// from the root of hierarchy to the method that contains an interleaving
// erased method defined in an interface.

class MethodFamily : public ResourceObj {
 private:

  GrowableArray<Pair<Method*,QualifiedState> > _members;
  ResourceHashtable<Method*, int> _member_index;

  Method* _selected_target;  // Filled in later, if a unique target exists
  Symbol* _exception_message; // If no unique target is found
  Symbol* _exception_name;    // If no unique target is found

  bool contains_method(Method* method) {
    int* lookup = _member_index.get(method);
    return lookup != NULL;
  }

  void add_method(Method* method, QualifiedState state) {
    Pair<Method*,QualifiedState> entry(method, state);
    _member_index.put(method, _members.length());
    _members.append(entry);
  }

  void disqualify_method(Method* method) {
    int* index = _member_index.get(method);
    guarantee(index != NULL && *index >= 0 && *index < _members.length(), "bad index");
    _members.at(*index).second = DISQUALIFIED;
  }

  Symbol* generate_no_defaults_message(TRAPS) const;
  Symbol* generate_conflicts_message(GrowableArray<Method*>* methods, TRAPS) const;

 public:

  MethodFamily()
      : _selected_target(NULL), _exception_message(NULL), _exception_name(NULL) {}

  void set_target_if_empty(Method* m) {
    if (_selected_target == NULL && !m->is_overpass()) {
      _selected_target = m;
    }
  }

  void record_qualified_method(Method* m) {
    // If the method already exists in the set as qualified, this operation is
    // redundant.  If it already exists as disqualified, then we leave it as
    // disqualfied.  Thus we only add to the set if it's not already in the
    // set.
    if (!contains_method(m)) {
      add_method(m, QUALIFIED);
    }
  }

  void record_disqualified_method(Method* m) {
    // If not in the set, add it as disqualified.  If it's already in the set,
    // then set the state to disqualified no matter what the previous state was.
    if (!contains_method(m)) {
      add_method(m, DISQUALIFIED);
    } else {
      disqualify_method(m);
    }
  }

  bool has_target() const { return _selected_target != NULL; }
  bool throws_exception() { return _exception_message != NULL; }

  Method* get_selected_target() { return _selected_target; }
  Symbol* get_exception_message() { return _exception_message; }
  Symbol* get_exception_name() { return _exception_name; }

  // Either sets the target or the exception error message
  void determine_target(InstanceKlass* root, TRAPS) {
    if (has_target() || throws_exception()) {
      return;
    }

    // Qualified methods are maximally-specific methods
    // These include public, instance concrete (=default) and abstract methods
    GrowableArray<Method*> qualified_methods;
    int num_defaults = 0;
    int default_index = -1;
    int qualified_index = -1;
    for (int i = 0; i < _members.length(); ++i) {
      Pair<Method*,QualifiedState> entry = _members.at(i);
      if (entry.second == QUALIFIED) {
        qualified_methods.append(entry.first);
        qualified_index++;
        if (entry.first->is_default_method()) {
          num_defaults++;
          default_index = qualified_index;

        }
      }
    }

    if (qualified_methods.length() == 0) {
      _exception_message = generate_no_defaults_message(CHECK);
      _exception_name = vmSymbols::java_lang_AbstractMethodError();
    // If only one qualified method is default, select that
    } else if (num_defaults == 1) {
        _selected_target = qualified_methods.at(default_index);
    } else if (num_defaults > 1) {
      _exception_message = generate_conflicts_message(&qualified_methods,CHECK);
      _exception_name = vmSymbols::java_lang_IncompatibleClassChangeError();
      if (TraceDefaultMethods) {
        _exception_message->print_value_on(tty);
        tty->print_cr("");
      }
    }
    // leave abstract methods alone, they will be found via normal search path
  }

  bool contains_signature(Symbol* query) {
    for (int i = 0; i < _members.length(); ++i) {
      if (query == _members.at(i).first->signature()) {
        return true;
      }
    }
    return false;
  }

#ifndef PRODUCT
  void print_sig_on(outputStream* str, Symbol* signature, int indent) const {
    streamIndentor si(str, indent * 2);

    str->indent().print_cr("Logical Method %s:", signature->as_C_string());

    streamIndentor si2(str);
    for (int i = 0; i < _members.length(); ++i) {
      str->indent();
      print_method(str, _members.at(i).first);
      if (_members.at(i).second == DISQUALIFIED) {
        str->print(" (disqualified)");
      }
      str->print_cr("");
    }

    if (_selected_target != NULL) {
      print_selected(str, 1);
    }
  }

  void print_selected(outputStream* str, int indent) const {
    assert(has_target(), "Should be called otherwise");
    streamIndentor si(str, indent * 2);
    str->indent().print("Selected method: ");
    print_method(str, _selected_target);
    Klass* method_holder = _selected_target->method_holder();
    if (!method_holder->is_interface()) {
      tty->print(" : in superclass");
    }
    str->print_cr("");
  }

  void print_exception(outputStream* str, int indent) {
    assert(throws_exception(), "Should be called otherwise");
    assert(_exception_name != NULL, "exception_name should be set");
    streamIndentor si(str, indent * 2);
    str->indent().print_cr("%s: %s", _exception_name->as_C_string(), _exception_message->as_C_string());
  }
#endif // ndef PRODUCT
};

Symbol* MethodFamily::generate_no_defaults_message(TRAPS) const {
  return SymbolTable::new_symbol("No qualifying defaults found", CHECK_NULL);
}

Symbol* MethodFamily::generate_conflicts_message(GrowableArray<Method*>* methods, TRAPS) const {
  stringStream ss;
  ss.print("Conflicting default methods:");
  for (int i = 0; i < methods->length(); ++i) {
    Method* method = methods->at(i);
    Symbol* klass = method->klass_name();
    Symbol* name = method->name();
    ss.print(" ");
    ss.write((const char*)klass->bytes(), klass->utf8_length());
    ss.print(".");
    ss.write((const char*)name->bytes(), name->utf8_length());
  }
  return SymbolTable::new_symbol(ss.base(), (int)ss.size(), CHECK_NULL);
}


class StateRestorer;

// StatefulMethodFamily is a wrapper around a MethodFamily that maintains the
// qualification state during hierarchy visitation, and applies that state
// when adding members to the MethodFamily
class StatefulMethodFamily : public ResourceObj {
  friend class StateRestorer;
 private:
  QualifiedState _qualification_state;

  void set_qualification_state(QualifiedState state) {
    _qualification_state = state;
  }

 protected:
  MethodFamily* _method_family;

 public:
  StatefulMethodFamily() {
   _method_family = new MethodFamily();
   _qualification_state = QUALIFIED;
  }

  StatefulMethodFamily(MethodFamily* mf) {
   _method_family = mf;
   _qualification_state = QUALIFIED;
  }

  void set_target_if_empty(Method* m) { _method_family->set_target_if_empty(m); }

  MethodFamily* get_method_family() { return _method_family; }

  StateRestorer* record_method_and_dq_further(Method* mo);
};

class StateRestorer : public PseudoScopeMark {
 private:
  StatefulMethodFamily* _method;
  QualifiedState _state_to_restore;
 public:
  StateRestorer(StatefulMethodFamily* dm, QualifiedState state)
      : _method(dm), _state_to_restore(state) {}
  ~StateRestorer() { destroy(); }
  void restore_state() { _method->set_qualification_state(_state_to_restore); }
  virtual void destroy() { restore_state(); }
};

StateRestorer* StatefulMethodFamily::record_method_and_dq_further(Method* mo) {
  StateRestorer* mark = new StateRestorer(this, _qualification_state);
  if (_qualification_state == QUALIFIED) {
    _method_family->record_qualified_method(mo);
  } else {
    _method_family->record_disqualified_method(mo);
  }
  // Everything found "above"??? this method in the hierarchy walk is set to
  // disqualified
  set_qualification_state(DISQUALIFIED);
  return mark;
}

// Represents a location corresponding to a vtable slot for methods that
// neither the class nor any of it's ancestors provide an implementaion.
// Default methods may be present to fill this slot.
class EmptyVtableSlot : public ResourceObj {
 private:
  Symbol* _name;
  Symbol* _signature;
  int _size_of_parameters;
  MethodFamily* _binding;

 public:
  EmptyVtableSlot(Method* method)
      : _name(method->name()), _signature(method->signature()),
        _size_of_parameters(method->size_of_parameters()), _binding(NULL) {}

  Symbol* name() const { return _name; }
  Symbol* signature() const { return _signature; }
  int size_of_parameters() const { return _size_of_parameters; }

  void bind_family(MethodFamily* lm) { _binding = lm; }
  bool is_bound() { return _binding != NULL; }
  MethodFamily* get_binding() { return _binding; }

#ifndef PRODUCT
  void print_on(outputStream* str) const {
    print_slot(str, name(), signature());
  }
#endif // ndef PRODUCT
};

static bool already_in_vtable_slots(GrowableArray<EmptyVtableSlot*>* slots, Method* m) {
  bool found = false;
  for (int j = 0; j < slots->length(); ++j) {
    if (slots->at(j)->name() == m->name() &&
        slots->at(j)->signature() == m->signature() ) {
      found = true;
      break;
    }
  }
  return found;
}

static GrowableArray<EmptyVtableSlot*>* find_empty_vtable_slots(
    InstanceKlass* klass, GrowableArray<Method*>* mirandas, TRAPS) {

  assert(klass != NULL, "Must be valid class");

  GrowableArray<EmptyVtableSlot*>* slots = new GrowableArray<EmptyVtableSlot*>();

  // All miranda methods are obvious candidates
  for (int i = 0; i < mirandas->length(); ++i) {
    Method* m = mirandas->at(i);
    if (!already_in_vtable_slots(slots, m)) {
      slots->append(new EmptyVtableSlot(m));
    }
  }

  // Also any overpasses in our superclasses, that we haven't implemented.
  // (can't use the vtable because it is not guaranteed to be initialized yet)
  InstanceKlass* super = klass->java_super();
  while (super != NULL) {
    for (int i = 0; i < super->methods()->length(); ++i) {
      Method* m = super->methods()->at(i);
      if (m->is_overpass() || m->is_static()) {
        // m is a method that would have been a miranda if not for the
        // default method processing that occurred on behalf of our superclass,
        // so it's a method we want to re-examine in this new context.  That is,
        // unless we have a real implementation of it in the current class.
        Method* impl = klass->lookup_method(m->name(), m->signature());
        if (impl == NULL || impl->is_overpass() || impl->is_static()) {
          if (!already_in_vtable_slots(slots, m)) {
            slots->append(new EmptyVtableSlot(m));
          }
        }
      }
    }

    // also any default methods in our superclasses
    if (super->default_methods() != NULL) {
      for (int i = 0; i < super->default_methods()->length(); ++i) {
        Method* m = super->default_methods()->at(i);
        // m is a method that would have been a miranda if not for the
        // default method processing that occurred on behalf of our superclass,
        // so it's a method we want to re-examine in this new context.  That is,
        // unless we have a real implementation of it in the current class.
        Method* impl = klass->lookup_method(m->name(), m->signature());
        if (impl == NULL || impl->is_overpass() || impl->is_static()) {
          if (!already_in_vtable_slots(slots, m)) {
            slots->append(new EmptyVtableSlot(m));
          }
        }
      }
    }
    super = super->java_super();
  }

#ifndef PRODUCT
  if (TraceDefaultMethods) {
    tty->print_cr("Slots that need filling:");
    streamIndentor si(tty);
    for (int i = 0; i < slots->length(); ++i) {
      tty->indent();
      slots->at(i)->print_on(tty);
      tty->print_cr("");
    }
  }
#endif // ndef PRODUCT
  return slots;
}

// Iterates over the superinterface type hierarchy looking for all methods
// with a specific erased signature.
class FindMethodsByErasedSig : public HierarchyVisitor<FindMethodsByErasedSig> {
 private:
  // Context data
  Symbol* _method_name;
  Symbol* _method_signature;
  StatefulMethodFamily*  _family;

 public:
  FindMethodsByErasedSig(Symbol* name, Symbol* signature) :
      _method_name(name), _method_signature(signature),
      _family(NULL) {}

  void get_discovered_family(MethodFamily** family) {
      if (_family != NULL) {
        *family = _family->get_method_family();
      } else {
        *family = NULL;
      }
  }

  void* new_node_data(InstanceKlass* cls) { return new PseudoScope(); }
  void free_node_data(void* node_data) {
    PseudoScope::cast(node_data)->destroy();
  }

  // Find all methods on this hierarchy that match this
  // method's erased (name, signature)
  bool visit() {
    PseudoScope* scope = PseudoScope::cast(current_data());
    InstanceKlass* iklass = current_class();

    Method* m = iklass->find_method(_method_name, _method_signature);
    // private interface methods are not candidates for default methods
    // invokespecial to private interface methods doesn't use default method logic
    // The overpasses are your supertypes' errors, we do not include them
    // future: take access controls into account for superclass methods
    if (m != NULL && !m->is_static() && !m->is_overpass() &&
         (!iklass->is_interface() || m->is_public())) {
      if (_family == NULL) {
        _family = new StatefulMethodFamily();
      }

      if (iklass->is_interface()) {
        StateRestorer* restorer = _family->record_method_and_dq_further(m);
        scope->add_mark(restorer);
      } else {
        // This is the rule that methods in classes "win" (bad word) over
        // methods in interfaces. This works because of single inheritance
        _family->set_target_if_empty(m);
      }
    }
    return true;
  }

};



static void create_defaults_and_exceptions(
    GrowableArray<EmptyVtableSlot*>* slots, InstanceKlass* klass, TRAPS);

static void generate_erased_defaults(
     InstanceKlass* klass, GrowableArray<EmptyVtableSlot*>* empty_slots,
     EmptyVtableSlot* slot, TRAPS) {

  // sets up a set of methods with the same exact erased signature
  FindMethodsByErasedSig visitor(slot->name(), slot->signature());
  visitor.run(klass);

  MethodFamily* family;
  visitor.get_discovered_family(&family);
  if (family != NULL) {
    family->determine_target(klass, CHECK);
    slot->bind_family(family);
  }
}

static void merge_in_new_methods(InstanceKlass* klass,
    GrowableArray<Method*>* new_methods, TRAPS);
static void create_default_methods( InstanceKlass* klass,
    GrowableArray<Method*>* new_methods, TRAPS);

// This is the guts of the default methods implementation.  This is called just
// after the classfile has been parsed if some ancestor has default methods.
//
// First if finds any name/signature slots that need any implementation (either
// because they are miranda or a superclass's implementation is an overpass
// itself).  For each slot, iterate over the hierarchy, to see if they contain a
// signature that matches the slot we are looking at.
//
// For each slot filled, we generate an overpass method that either calls the
// unique default method candidate using invokespecial, or throws an exception
// (in the case of no default method candidates, or more than one valid
// candidate).  These methods are then added to the class's method list.
// The JVM does not create bridges nor handle generic signatures here.
void DefaultMethods::generate_default_methods(
    InstanceKlass* klass, GrowableArray<Method*>* mirandas, TRAPS) {

  // This resource mark is the bound for all memory allocation that takes
  // place during default method processing.  After this goes out of scope,
  // all (Resource) objects' memory will be reclaimed.  Be careful if adding an
  // embedded resource mark under here as that memory can't be used outside
  // whatever scope it's in.
  ResourceMark rm(THREAD);

  // Keep entire hierarchy alive for the duration of the computation
  KeepAliveRegistrar keepAlive(THREAD);
  KeepAliveVisitor loadKeepAlive(&keepAlive);
  loadKeepAlive.run(klass);

#ifndef PRODUCT
  if (TraceDefaultMethods) {
    ResourceMark rm;  // be careful with these!
    tty->print_cr("%s %s requires default method processing",
        klass->is_interface() ? "Interface" : "Class",
        klass->name()->as_klass_external_name());
    PrintHierarchy printer;
    printer.run(klass);
  }
#endif // ndef PRODUCT

  GrowableArray<EmptyVtableSlot*>* empty_slots =
      find_empty_vtable_slots(klass, mirandas, CHECK);

  for (int i = 0; i < empty_slots->length(); ++i) {
    EmptyVtableSlot* slot = empty_slots->at(i);
#ifndef PRODUCT
    if (TraceDefaultMethods) {
      streamIndentor si(tty, 2);
      tty->indent().print("Looking for default methods for slot ");
      slot->print_on(tty);
      tty->print_cr("");
    }
#endif // ndef PRODUCT

    generate_erased_defaults(klass, empty_slots, slot, CHECK);
 }
#ifndef PRODUCT
  if (TraceDefaultMethods) {
    tty->print_cr("Creating defaults and overpasses...");
  }
#endif // ndef PRODUCT

  create_defaults_and_exceptions(empty_slots, klass, CHECK);

#ifndef PRODUCT
  if (TraceDefaultMethods) {
    tty->print_cr("Default method processing complete");
  }
#endif // ndef PRODUCT
}

static int assemble_method_error(
    BytecodeConstantPool* cp, BytecodeBuffer* buffer, Symbol* errorName, Symbol* message, TRAPS) {

  Symbol* init = vmSymbols::object_initializer_name();
  Symbol* sig = vmSymbols::string_void_signature();

  BytecodeAssembler assem(buffer, cp);

  assem._new(errorName);
  assem.dup();
  assem.load_string(message);
  assem.invokespecial(errorName, init, sig);
  assem.athrow();

  return 3; // max stack size: [ exception, exception, string ]
}

static Method* new_method(
    BytecodeConstantPool* cp, BytecodeBuffer* bytecodes, Symbol* name,
    Symbol* sig, AccessFlags flags, int max_stack, int params,
    ConstMethod::MethodType mt, TRAPS) {

  address code_start = 0;
  int code_length = 0;
  InlineTableSizes sizes;

  if (bytecodes != NULL && bytecodes->length() > 0) {
    code_start = static_cast<address>(bytecodes->adr_at(0));
    code_length = bytecodes->length();
  }

  Method* m = Method::allocate(cp->pool_holder()->class_loader_data(),
                               code_length, flags, &sizes,
                               mt, CHECK_NULL);

  m->set_constants(NULL); // This will get filled in later
  m->set_name_index(cp->utf8(name));
  m->set_signature_index(cp->utf8(sig));
#ifdef CC_INTERP
  ResultTypeFinder rtf(sig);
  m->set_result_index(rtf.type());
#endif
  m->set_size_of_parameters(params);
  m->set_max_stack(max_stack);
  m->set_max_locals(params);
  m->constMethod()->set_stackmap_data(NULL);
  m->set_code(code_start);

  return m;
}

static void switchover_constant_pool(BytecodeConstantPool* bpool,
    InstanceKlass* klass, GrowableArray<Method*>* new_methods, TRAPS) {

  if (new_methods->length() > 0) {
    ConstantPool* cp = bpool->create_constant_pool(CHECK);
    if (cp != klass->constants()) {
      klass->class_loader_data()->add_to_deallocate_list(klass->constants());
      klass->set_constants(cp);
      cp->set_pool_holder(klass);

      for (int i = 0; i < new_methods->length(); ++i) {
        new_methods->at(i)->set_constants(cp);
      }
      for (int i = 0; i < klass->methods()->length(); ++i) {
        Method* mo = klass->methods()->at(i);
        mo->set_constants(cp);
      }
    }
  }
}

// Create default_methods list for the current class.
// With the VM only processing erased signatures, the VM only
// creates an overpass in a conflict case or a case with no candidates.
// This allows virtual methods to override the overpass, but ensures
// that a local method search will find the exception rather than an abstract
// or default method that is not a valid candidate.
static void create_defaults_and_exceptions(
    GrowableArray<EmptyVtableSlot*>* slots,
    InstanceKlass* klass, TRAPS) {

  GrowableArray<Method*> overpasses;
  GrowableArray<Method*> defaults;
  BytecodeConstantPool bpool(klass->constants());

  for (int i = 0; i < slots->length(); ++i) {
    EmptyVtableSlot* slot = slots->at(i);

    if (slot->is_bound()) {
      MethodFamily* method = slot->get_binding();
      BytecodeBuffer buffer;

#ifndef PRODUCT
      if (TraceDefaultMethods) {
        tty->print("for slot: ");
        slot->print_on(tty);
        tty->print_cr("");
        if (method->has_target()) {
          method->print_selected(tty, 1);
        } else if (method->throws_exception()) {
          method->print_exception(tty, 1);
        }
      }
#endif // ndef PRODUCT

      if (method->has_target()) {
        Method* selected = method->get_selected_target();
        if (selected->method_holder()->is_interface()) {
          defaults.push(selected);
        }
      } else if (method->throws_exception()) {
        int max_stack = assemble_method_error(&bpool, &buffer,
           method->get_exception_name(), method->get_exception_message(), CHECK);
        AccessFlags flags = accessFlags_from(
          JVM_ACC_PUBLIC | JVM_ACC_SYNTHETIC | JVM_ACC_BRIDGE);
         Method* m = new_method(&bpool, &buffer, slot->name(), slot->signature(),
          flags, max_stack, slot->size_of_parameters(),
          ConstMethod::OVERPASS, CHECK);
        // We push to the methods list:
        // overpass methods which are exception throwing methods
        if (m != NULL) {
          overpasses.push(m);
        }
      }
    }
  }

#ifndef PRODUCT
  if (TraceDefaultMethods) {
    tty->print_cr("Created %d overpass methods", overpasses.length());
    tty->print_cr("Created %d default  methods", defaults.length());
  }
#endif // ndef PRODUCT

  if (overpasses.length() > 0) {
    switchover_constant_pool(&bpool, klass, &overpasses, CHECK);
    merge_in_new_methods(klass, &overpasses, CHECK);
  }
  if (defaults.length() > 0) {
    create_default_methods(klass, &defaults, CHECK);
  }
}

static void create_default_methods( InstanceKlass* klass,
    GrowableArray<Method*>* new_methods, TRAPS) {

  int new_size = new_methods->length();
  Array<Method*>* total_default_methods = MetadataFactory::new_array<Method*>(
      klass->class_loader_data(), new_size, NULL, CHECK);
  for (int index = 0; index < new_size; index++ ) {
    total_default_methods->at_put(index, new_methods->at(index));
  }
  Method::sort_methods(total_default_methods, false, false);

  klass->set_default_methods(total_default_methods);
}

static void sort_methods(GrowableArray<Method*>* methods) {
  // Note that this must sort using the same key as is used for sorting
  // methods in InstanceKlass.
  bool sorted = true;
  for (int i = methods->length() - 1; i > 0; --i) {
    for (int j = 0; j < i; ++j) {
      Method* m1 = methods->at(j);
      Method* m2 = methods->at(j + 1);
      if ((uintptr_t)m1->name() > (uintptr_t)m2->name()) {
        methods->at_put(j, m2);
        methods->at_put(j + 1, m1);
        sorted = false;
      }
    }
    if (sorted) break;
    sorted = true;
  }
#ifdef ASSERT
  uintptr_t prev = 0;
  for (int i = 0; i < methods->length(); ++i) {
    Method* mh = methods->at(i);
    uintptr_t nv = (uintptr_t)mh->name();
    assert(nv >= prev, "Incorrect overpass method ordering");
    prev = nv;
  }
#endif
}

static void merge_in_new_methods(InstanceKlass* klass,
    GrowableArray<Method*>* new_methods, TRAPS) {

  enum { ANNOTATIONS, PARAMETERS, DEFAULTS, NUM_ARRAYS };

  Array<Method*>* original_methods = klass->methods();
  Array<int>* original_ordering = klass->method_ordering();
  Array<int>* merged_ordering = Universe::the_empty_int_array();

  int new_size = klass->methods()->length() + new_methods->length();

  Array<Method*>* merged_methods = MetadataFactory::new_array<Method*>(
      klass->class_loader_data(), new_size, NULL, CHECK);

  if (original_ordering != NULL && original_ordering->length() > 0) {
    merged_ordering = MetadataFactory::new_array<int>(
        klass->class_loader_data(), new_size, CHECK);
  }
  int method_order_index = klass->methods()->length();

  sort_methods(new_methods);

  // Perform grand merge of existing methods and new methods
  int orig_idx = 0;
  int new_idx = 0;

  for (int i = 0; i < new_size; ++i) {
    Method* orig_method = NULL;
    Method* new_method = NULL;
    if (orig_idx < original_methods->length()) {
      orig_method = original_methods->at(orig_idx);
    }
    if (new_idx < new_methods->length()) {
      new_method = new_methods->at(new_idx);
    }

    if (orig_method != NULL &&
        (new_method == NULL || orig_method->name() < new_method->name())) {
      merged_methods->at_put(i, orig_method);
      original_methods->at_put(orig_idx, NULL);
      if (merged_ordering->length() > 0) {
        merged_ordering->at_put(i, original_ordering->at(orig_idx));
      }
      ++orig_idx;
    } else {
      merged_methods->at_put(i, new_method);
      if (merged_ordering->length() > 0) {
        merged_ordering->at_put(i, method_order_index++);
      }
      ++new_idx;
    }
    // update idnum for new location
    merged_methods->at(i)->set_method_idnum(i);
  }

  // Verify correct order
#ifdef ASSERT
  uintptr_t prev = 0;
  for (int i = 0; i < merged_methods->length(); ++i) {
    Method* mo = merged_methods->at(i);
    uintptr_t nv = (uintptr_t)mo->name();
    assert(nv >= prev, "Incorrect method ordering");
    prev = nv;
  }
#endif

  // Replace klass methods with new merged lists
  klass->set_methods(merged_methods);
  klass->set_initial_method_idnum(new_size);

  ClassLoaderData* cld = klass->class_loader_data();
  if (original_methods ->length() > 0) {
    MetadataFactory::free_array(cld, original_methods);
  }
  if (original_ordering->length() > 0) {
    klass->set_method_ordering(merged_ordering);
    MetadataFactory::free_array(cld, original_ordering);
  }
}
