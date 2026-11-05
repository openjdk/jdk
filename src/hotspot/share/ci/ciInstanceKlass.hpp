/*
 * Copyright (c) 1999, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CI_CIINSTANCEKLASS_HPP
#define SHARE_CI_CIINSTANCEKLASS_HPP

#include "ci/ciConstantPoolCache.hpp"
#include "ci/ciFlags.hpp"
#include "ci/ciKlass.hpp"
#include "ci/ciSymbol.hpp"
#include "classfile/classFileParser.hpp"
#include "oops/instanceKlass.hpp"

// ciInstanceKlass
//
// This class represents a Klass* in the HotSpot virtual machine
// whose Klass part is an InstanceKlass.  It may or may not
// be loaded.
class ciInstanceKlass : public ciKlass {
  CI_PACKAGE_ACCESS
  friend class ciBytecodeStream;
  friend class ciEnv;
  friend class ciExceptionHandler;
  friend class ciMethod;
  friend class ciField;
  friend class ciReplay;
  friend class CompileTrainingData;

private:
  enum SubklassValue { subklass_unknown, subklass_false, subklass_true };

  jobject                _loader;

  InstanceKlass::ClassState _init_state;           // state of class
  bool                   _is_shared;
  bool                   _has_finalizer;
  bool                   _has_nonstatic_fields;
  bool                   _has_nonstatic_concrete_methods;
  bool                   _is_hidden;
  bool                   _is_record;
  bool                   _trust_final_fields;
  bool                   _has_trusted_loader;

  ciFlags                _flags;
  ciInstanceKlass*       _super;
  ciInstance*            _java_mirror;  // Lazy field, initialized on demand

  ciConstantPoolCache*   _field_cache;  // cached map index->field

  // Fields declared in the bytecode (without nested fields in flat fields),
  // ordered in JavaFieldStream order, with superclasses first (i.e. from lang.java.Object
  // to most derived class).
  const GrowableArray<ciField*>* _declared_nonstatic_fields;

  // Fields laid out in memory (flat fields are expanded into their components). The ciField object
  // for each primitive component has the holder being this ciInstanceKlass or one of its
  // superclasses.
  // Fields are in the same order as in _declared_nonstatic_fields, but flat fields are replaced by
  // the list of their own fields, ordered the same way (hierarchy traversed top-down, in
  // JavaFieldStream order).
  const GrowableArray<ciField*>* _nonstatic_fields;

  int _has_injected_fields; // any non static injected fields? lazily initialized.
  GrowableArray<ciInstanceKlass*>* _transitive_interfaces;

  void compute_injected_fields();
  bool compute_injected_fields_helper();
  GrowableArray<ciInstanceKlass*>* compute_transitive_interfaces(Arena* arena, InstanceKlass* ik);

  ciField* get_nonstatic_field_by_offset(int field_offset);

protected:
  ciInstanceKlass(Klass* k);
  ciInstanceKlass(ciSymbol* name, jobject loader, BasicType bt = T_OBJECT); // for unloaded klasses

  InstanceKlass* get_instanceKlass() const {
    return InstanceKlass::cast(get_Klass());
  }

  oop loader();
  jobject loader_handle();

  const char* type_string() { return "ciInstanceKlass"; }

  bool is_in_package_impl(const char* packagename, int len);

  void print_impl(outputStream* st);

  ciConstantPoolCache* field_cache();

  bool is_shared() { return _is_shared; }

  InstanceKlass::ClassState compute_init_state();
  void compute_nonstatic_fields();
  void compute_nonstatic_fields_impl(const GrowableArray<ciField*>* super_declared_fields, const GrowableArray<ciField*>* super_fields);
  bool compute_has_trusted_loader();

  static InstanceKlass::ClassState compute_init_state(InstanceKlass* ik);

public:
  // Has this klass been initialized?
  bool                   is_initialized() {
    InstanceKlass::ClassState state = compute_init_state();
    return state == InstanceKlass::fully_initialized;
  }
  bool                   is_not_initialized() {
    InstanceKlass::ClassState state = compute_init_state();
    return state < InstanceKlass::being_initialized;
  }
  // Is this klass being initialized?
  bool                   is_being_initialized() {
    InstanceKlass::ClassState state = compute_init_state();
    return state == InstanceKlass::being_initialized;
  }
  // Has this klass been linked?
  bool                   is_linked() {
    InstanceKlass::ClassState state = compute_init_state();
    return state >= InstanceKlass::linked;
  }
  // Is this klass in error state?
  bool                   is_in_error_state() {
    InstanceKlass::ClassState state = compute_init_state();
    return state == InstanceKlass::initialization_error;
  }

  // General klass information.
  ciFlags                flags()          {
    assert(is_loaded(), "must be loaded");
    return _flags;
  }

  // Fetch Klass::access_flags.
  jint                   access_flags() { return flags().as_int(); }

  bool                   has_finalizer()  {
    assert(is_loaded(), "must be loaded");
    return _has_finalizer; }

  jint                   layout_helper_size_in_bytes()  {
    return Klass::layout_helper_size_in_bytes(layout_helper());
  }
  jint                   size_helper()  {
    return (Klass::layout_helper_size_in_bytes(layout_helper())
            >> LogHeapWordSize);
  }
  jint                   has_nonstatic_fields()  {
    assert(is_loaded(), "must be loaded");
    return _has_nonstatic_fields;
  }

  ciInstanceKlass*       super() const {
    return _super;
  }

  bool has_nonstatic_concrete_methods()  {
    assert(is_loaded(), "must be loaded");
    return _has_nonstatic_concrete_methods;
  }

  bool is_hidden() const {
    return _is_hidden;
  }

  bool is_record() const {
    return _is_record;
  }

  bool trust_final_fields() const {
    return _trust_final_fields;
  }

  ciInstanceKlass* get_canonical_holder(int offset);
  ciField* get_field_by_offset(int field_offset, bool is_static);
  ciField* get_field_by_name(ciSymbol* name, ciSymbol* signature, bool is_static);
  // Get field descriptor at field_offset ignoring flattening
  ciField* get_non_flat_field_by_offset(int field_offset);
  // Get the index of the declared field that contains this offset
  int field_index_by_offset(int offset);

  // Total number of nonstatic fields (including inherited)
  int nof_declared_nonstatic_fields() {
    if (_declared_nonstatic_fields == nullptr) {
      compute_nonstatic_fields();
    }
    return _declared_nonstatic_fields->length();
  }
  ciField* get_injected_instance_field_by_name(ciSymbol* name, ciSymbol* signature);
  BasicType get_field_type_by_offset(int field_offset, bool is_static);

  int nof_nonstatic_fields() {
    if (_nonstatic_fields == nullptr) {
      compute_nonstatic_fields();
    }
    return _nonstatic_fields->length();
  }

  bool has_injected_fields() {
    if (_has_injected_fields == -1) {
      compute_injected_fields();
    }
    return _has_injected_fields > 0 ? true : false;
  }

  bool has_object_fields() const;

  ciField* declared_nonstatic_field_at(int i) {
    assert(_declared_nonstatic_fields != nullptr, "should be initialized");
    return _declared_nonstatic_fields->at(i);
  }

  ciField* nonstatic_field_at(int i) {
    assert(_nonstatic_fields != nullptr, "");
    return _nonstatic_fields->at(i);
  }

  int number_of_oop_entries_in_acmp_map() const;
  int number_of_nonoop_entries_in_acmp_map() const;
  AcmpMapSegment get_nonoop_segment_of_acmp_map(int i) const;

  bool has_finalizable_subclass();

  bool has_class_initializer();

  bool contains_field_offset(int offset) const;

  // Get the instance of java.lang.Class corresponding to
  // this klass.  This instance is used for locking of
  // synchronized static methods of this klass.
  ciInstance*            java_mirror();

  // Java access flags
  bool is_public      () { return flags().is_public(); }
  bool is_final       () { return flags().is_final(); }
  bool is_interface   () { return flags().is_interface(); }
  bool is_abstract    () { return flags().is_abstract(); }
  bool is_abstract_value_klass() { return is_abstract() && !flags().is_identity(); }

  ciMethod* find_method(ciSymbol* name, ciSymbol* signature);
  // Note:  To find a method from name and type strings, use ciSymbol::make,
  // but consider adding to vmSymbols.hpp instead.

  virtual bool can_be_value_klass(bool is_exact = false);

  // Is the defining class loader of this class the default loader?
  bool uses_default_loader() const;

  bool is_java_lang_Object() const;

  BasicType box_klass_type() const;
  bool is_box_klass() const;
  bool is_boxed_value_offset(int offset) const;

  // Is this klass in the given package?
  bool is_in_package(const char* packagename) {
    return is_in_package(packagename, (int) strlen(packagename));
  }
  bool is_in_package(const char* packagename, int len);

  // What kind of ciObject is this?
  bool is_instance_klass() const { return true; }

  virtual ciKlass* exact_klass() {
    if (is_loaded() && is_final() && !is_interface()) {
      return this;
    }
    return nullptr;
  }

  bool can_be_instantiated() {
    assert(is_loaded(), "must be loaded");
    return !is_interface() && !is_abstract();
  }

  bool has_trusted_loader() const {
    return _has_trusted_loader;
  }

  GrowableArray<ciInstanceKlass*>* transitive_interfaces() const;

private:
  // The compiler takes advantage of class hierarchy analysis to optimize the value sets of the
  // nodes in the graph. For example, when a value declares that it is a j.u.AbstractList, the
  // compiler can infer that its runtime type must be j.u.ArrayList if j.u.ArrayList is the only
  // concrete subklass of j.u.AbstractList. This is useful for various optimizations such as method
  // devirtualization, or acmp expansion. However, the VM does not freeze when a compilation
  // happens, so concurrent class loading/unloading can change the result of the analysis
  // mid-compilation. For example:
  // - When a Phi is parsed, its declared type P has a unique concrete subklass C1, so we
  //   optimistically assume that the Phi must be a pointer to a C1. However, afterwards, it turns
  //   out that an input of that Phi is a pointer to a C2, which is also a subklass of P but is
  //   only loaded later. This leads to a paradoxical case when the input of a Phi has an unrelated
  //   type to that Phi.
  // - When a call is parsed, its result type P has a single concrete subklass C1, so we
  //   optimistically assume that the output of that call must be a pointer to a C1. However,
  //   afterwards, the call is inlined and the result turns out to be a pointer to a C2, which is
  //   also a subklass of P but is only loaded later. This can lead to various issues as the
  //   nodes consuming the pre-inlining result may see their input type changes unexpectedly.
  //
  // Sometimes, the compiler still works fine, and the compiled code gets discarded due to
  // dependency invalidation, so there is no observable issue. However, this is not always the
  // case:
  // - The compiler might crash due to paradoxical graph shape; method invalidation only happens
  //   after the compilation finishes, which is too late.
  // - CHA result invalidation may not actually result in dependency invalidation, so the
  //   incorrectly compiled code may get executed, and lead to incorrect results. This can happen
  //   when compilation optimizations transform a field access to C1.v incorrectly to a field
  //   access to P.v, which may correspond to a non-existent field. This leads to incorrect
  //   anti-dependence computation, and incorrect scheduling. This case can arise either due to the
  //   compiler not using CHA in a monotonic way, or concurrent class unloading; neither
  //   necessarily causes dependency invalidation.
  //
  // As a result, CHA computation needs to ensure that the compiler has a consistent view of the
  // class hierarchy. The most important invariant is that compatible types need to stay compatible
  // after the compiler uses CHA to optimize them. Less formally, if A is a subklass of B, then
  // TypeOopPtr::make_from_klass(A) should also be a subset of TypeOopPtr::make_from_klass(B). This
  // means:
  // - TypeOopPtr::make_from_klass(A) should return the same value throughout the compilation.
  // - If A says that it has no non-interface subklass, then there should be no non-interface
  //   ciInstanceKlass B subklassing it. Otherwise, TypeOopPtr::make_from_klass(A) would return a
  //   Type representing the set of pointers to an exact A, which does not include
  //   TypeOopPtr::make_from_klass(B).
  // - If A says that all of its concrete implementors are subklasses of A1, B says that all of its
  //   concrete implementors are subklasses of B1, A is a subklass of B, then A1 must be a subklass
  //   of B1.
  //   + This also means that for all klasses C such that A1 is a subklass of C and C is a subklass
  //     of A, C must say that all of its concrete implementors are subklasses of A1.
  //
  // Limitation:
  // - While InstanceKlass::up_cast_abstract takes into account abstract subklasses that do not
  //   have concrete subklasses of their own, InstanceKlass::implementor does not take into account
  //   subinterfaces that do not have non-interface subklasses.
  //   + This means that we cannot use CHA to improve a pointer to an interface, because otherwise,
  //     for a Phi of type I and its input of type J, we may optimize it into a Phi of type C with
  //     an input of unrelated type J, which is paradoxical.
  //   + Note that it is fine to use InstanceKlass::implementor to devirtualize a method call,
  //     because that optimization transforms a virtual call into a guarded direct call with a
  //     receiver type check, which does not undermine the graph integrity regardless of the
  //     optimized type.
  bool _cha_info_computed;

  // Whether this ciInstanceKlass has a proper subklass (a subklass that is not itself) that is not
  // an interface
  SubklassValue _has_subklass;

  // _implementor is the result of trying to find a bound of all concrete subklasses of a class or
  // interface. In addition, it must ensure the invariant that if A is a subklass of B, then
  // A->_implementor must also be a subklass of B->_implementor. The exception is when B is an
  // interface and A is a subinterface of B, see the Limitation section above for more details.
  // This value can be either:
  //   == nullptr: no concrete subklass.
  //   != nullptr: each concrete subklass of this must be a subklass of this value.
  ciInstanceKlass* _implementor;

  void compute_cha_info();
  void update_cha_info_upon_creation();
  bool check_cha_consistency(InstanceKlass* ik);
  static bool verify_superklass_or_interface(ciInstanceKlass* super, InstanceKlass* sub);

public:
  bool has_subklass();
  bool is_leaf_type();
  ciInstanceKlass* unique_concrete_subklass();
  ciInstanceKlass* unique_implementor();

  // Replay support

  // Dump the current state of this klass for compilation replay.
  virtual void dump_replay_data(outputStream* out);

  static void dump_replay_instanceKlass(outputStream* out, InstanceKlass* ik);


  // Return stable class name suitable for replay file.
  const char *replay_name() const;

#ifdef ASSERT
  bool debug_final_field_at(int offset);
  bool debug_stable_field_at(int offset);
#endif
};

#endif // SHARE_CI_CIINSTANCEKLASS_HPP
