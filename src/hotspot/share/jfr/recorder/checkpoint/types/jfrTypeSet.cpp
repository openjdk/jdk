/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/packageEntry.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/vmClasses.hpp"
#include "jfr/leakprofiler/checkpoint/objectSampleCheckpoint.hpp"
#include "jfr/recorder/checkpoint/types/jfrTypeSet.hpp"
#include "jfr/recorder/checkpoint/types/jfrTypeSetUtils.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdLoadBarrier.inline.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/support/jfrKlassUnloading.hpp"
#include "jfr/utilities/jfrHashtable.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "jfr/writers/jfrTypeWriterHost.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/accessFlags.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/stack.inline.hpp"

typedef const Klass* KlassPtr;
typedef const PackageEntry* PkgPtr;
typedef const ModuleEntry* ModPtr;
typedef const ClassLoaderData* CldPtr;
typedef const Method* MethodPtr;
typedef const Symbol* SymbolPtr;
typedef const JfrSymbolTable::SymbolEntry* SymbolEntryPtr;
typedef const JfrSymbolTable::StringEntry* StringEntryPtr;

static JfrCheckpointWriter* _writer = nullptr;
static JfrCheckpointWriter* _leakp_writer = nullptr;
static JfrArtifactSet* _artifacts = nullptr;
static JfrArtifactClosure* _subsystem_callback = nullptr;
static bool _class_unload = false;
static bool _flushpoint = false;
static bool _initial_type_set = true;

static inline bool flushpoint() {
  return _flushpoint;
}

static inline bool unloading() {
  return _class_unload;
}

static inline bool current_epoch() {
  return flushpoint() || unloading();
}

static inline bool previous_epoch() {
  return !current_epoch();
}

template <typename T>
static inline bool used(const T* ptr) {
  assert(ptr != nullptr, "invariant");
  return current_epoch() ? USED_THIS_EPOCH(ptr) : USED_PREVIOUS_EPOCH(ptr);
}

template <typename T>
static inline bool not_used(const T* ptr) {
  return !used(ptr);
}

template <typename T>
static void do_artifact(const T* ptr) {
  if (used(ptr)) {
    _subsystem_callback->do_artifact(ptr);
  }
}

static traceid mark_symbol(KlassPtr klass, bool leakp) {
  return klass != nullptr ? _artifacts->mark(klass, leakp) : 0;
}

static traceid mark_symbol(Symbol* symbol, bool leakp) {
  return symbol != nullptr ? _artifacts->mark(symbol, leakp) : 0;
}

static traceid get_bootstrap_name(bool leakp) {
  return _artifacts->bootstrap_name(leakp);
}

template <typename T>
static traceid artifact_id(const T* ptr) {
  assert(ptr != nullptr, "invariant");
  return JfrTraceId::load_raw(ptr);
}

template <typename T>
static traceid artifact_tag(const T* ptr, bool leakp) {
  assert(ptr != nullptr, "invariant");
  if (leakp) {
    if (IS_NOT_LEAKP(ptr)) {
      SET_LEAKP(ptr);
    }
    assert(IS_LEAKP(ptr), "invariant");
  }
  if (not_used(ptr)) {
    SET_TRANSIENT(ptr);
  }
  assert(used(ptr), "invariant");
  return artifact_id(ptr);
}

static inline CldPtr get_cld(ModPtr mod) {
  return mod != nullptr ? mod->loader_data() : nullptr;
}

static ClassLoaderData* get_cld(const Klass* klass) {
  assert(klass != nullptr, "invariant");
  if (klass->is_objArray_klass()) {
    klass = ObjArrayKlass::cast(klass)->bottom_klass();
  }
  return klass->is_non_strong_hidden() ? nullptr : klass->class_loader_data();
}

static inline bool should_do_cld_klass(const Klass* cld_klass, bool leakp) {
  return cld_klass != nullptr && _artifacts->should_do_cld_klass(cld_klass, leakp);
}

static inline bool should_enqueue(const Klass* cld_klass) {
  assert(cld_klass != nullptr, "invariant");
  if (unloading() || previous_epoch()) {
    return false;
  }
  CldPtr cld = get_cld(cld_klass);
  return cld != nullptr && !cld->is_unloading();
}

static inline KlassPtr get_cld_klass(CldPtr cld, bool leakp) {
  if (cld == nullptr) {
    return nullptr;
  }
  assert(leakp ? IS_LEAKP(cld) : used(cld), "invariant");
  KlassPtr cld_klass = cld->class_loader_klass();
  if (!should_do_cld_klass(cld_klass, leakp)) {
    return nullptr;
  }
  if (should_enqueue(cld_klass)) {
    // This will enqueue the klass, which is important for
    // reachability when doing clear and reset at rotation.
    JfrTraceId::load(cld_klass);
   } else {
     artifact_tag(cld_klass, leakp);
   }
   return cld_klass;
}

static inline ModPtr get_module(PkgPtr pkg) {
  return pkg != nullptr ? pkg->module() : nullptr;
}

static inline PkgPtr get_package(KlassPtr klass) {
  return klass != nullptr ? klass->package() : nullptr;
}

static inline KlassPtr get_module_cld_klass(KlassPtr klass, bool leakp) {
  assert(klass != nullptr, "invariant");
  return get_cld_klass(get_cld(get_module(get_package(klass))), leakp);
}

static traceid cld_id(CldPtr cld, bool leakp) {
  assert(cld != nullptr, "invariant");
  return artifact_tag(cld, leakp);
}

static traceid module_id(PkgPtr pkg, bool leakp) {
  assert(pkg != nullptr, "invariant");
  ModPtr mod = get_module(pkg);
  if (mod == nullptr) {
    return 0;
  }
  CldPtr cld = get_cld(mod);
  if (cld != nullptr) {
    cld_id(cld, leakp);
  }
  return artifact_tag(mod, leakp);
}

static traceid package_id(KlassPtr klass, bool leakp) {
  assert(klass != nullptr, "invariant");
  PkgPtr pkg = get_package(klass);
  if (pkg == nullptr) {
    return 0;
  }
  // Ensure module and its CLD gets tagged.
  module_id(pkg, leakp);
  return artifact_tag(pkg, leakp);
}

static traceid method_id(KlassPtr klass, MethodPtr method) {
  assert(klass != nullptr, "invariant");
  assert(method != nullptr, "invariant");
  return METHOD_ID(klass, method);
}

template <typename T>
static s4 get_flags(const T* ptr) {
  assert(ptr != nullptr, "invariant");
  return ptr->access_flags().get_flags();
}

// Same as JVM_GetClassModifiers
static u4 get_primitive_flags() {
  return JVM_ACC_ABSTRACT | JVM_ACC_FINAL | JVM_ACC_PUBLIC;
}

class PackageFieldSelector {
 public:
  typedef PkgPtr TypePtr;
  static TypePtr select(KlassPtr klass) {
    assert(klass != nullptr, "invariant");
    return klass->package();
  }
};

class ModuleFieldSelector {
 public:
  typedef ModPtr TypePtr;
  static TypePtr select(KlassPtr klass) {
    assert(klass != nullptr, "invariant");
    PkgPtr pkg = klass->package();
    if (pkg == nullptr) {
      return nullptr;
    }
    assert(current_epoch() ? IS_SERIALIZED(pkg) : true, "invariant");
    return pkg->module();
  }
};

class KlassCldFieldSelector {
 public:
  typedef CldPtr TypePtr;
  static TypePtr select(KlassPtr klass) {
    assert(klass != nullptr, "invariant");
    return get_cld(klass);
  }
};

class ModuleCldFieldSelector {
 public:
  typedef CldPtr TypePtr;
  static TypePtr select(KlassPtr klass) {
    assert(klass != nullptr, "invariant");
    ModPtr mod = ModuleFieldSelector::select(klass);
    if (mod == nullptr) {
      return nullptr;
    }
    assert(current_epoch() ? IS_SERIALIZED(mod) : true, "invariant");
    return mod->loader_data();
  }
};

template <typename T>
class SerializePredicate {
  bool _class_unload;
 public:
  SerializePredicate(bool class_unload) : _class_unload(class_unload) {}
  bool operator()(T const& value) {
    assert(value != nullptr, "invariant");
    return _class_unload ? true : IS_NOT_SERIALIZED(value);
  }
};

template <>
class SerializePredicate<const Method*> {
  bool _class_unload;
public:
  SerializePredicate(bool class_unload) : _class_unload(class_unload) {}
  bool operator()(const Method* method) {
    assert(method != nullptr, "invariant");
    return _class_unload ? true : METHOD_IS_NOT_SERIALIZED(method);
  }
};

template <typename T>
static void set_serialized(const T* ptr) {
  assert(ptr != nullptr, "invariant");
  if (current_epoch()) {
    CLEAR_THIS_EPOCH_CLEARED_BIT(ptr);
    assert(!IS_THIS_EPOCH_CLEARED_BIT_SET(ptr), "invariant");
  }
  assert(IS_PREVIOUS_EPOCH_CLEARED_BIT_SET(ptr), "invariant");
  SET_SERIALIZED(ptr);
  assert(IS_SERIALIZED(ptr), "invariant");
}

/*
 ***********************    Klasses    *************************
 *
 * When we process a Klass, we need to process its transitive closure.
 *
 * This includes two branches:
 *
 * [1] Klass -> CLD -> class_loader_Klass
 * [2] Klass -> PackageEntry -> ModuleEntry -> CLD -> class_loader_Klass
 *
 *    A Klass viewed as this closure becomes a node in a binary tree:
 *
 *                           Klass
 *                             O
 *                            / \
 *                           /   \
 *                      [1] O     O [2]
 *
 * We write the Klass and tag the artifacts in its closure (subtree)
 * using preorder traversal by recursing the class_loader_Klass(es).
 *
 */

static void do_write_klass(JfrCheckpointWriter* writer, CldPtr cld, KlassPtr klass, bool leakp) {
  assert(writer != nullptr, "invariant");
  assert(_artifacts != nullptr, "invariant");
  assert(klass != nullptr, "invariant");
  writer->write(artifact_id(klass));
  writer->write(cld != nullptr ? cld_id(cld, leakp) : 0);
  writer->write(mark_symbol(klass, leakp));
  writer->write(package_id(klass, leakp));
  writer->write(klass->modifier_flags());
  writer->write<bool>(klass->is_hidden());
  if (leakp) {
    assert(IS_LEAKP(klass), "invariant");
    CLEAR_LEAKP(klass);
    assert(IS_NOT_LEAKP(klass), "invariant");
    return;
  }
  assert(used(klass), "invariant");
  assert(unloading() ? true : IS_NOT_SERIALIZED(klass), "invariant");
  set_serialized(klass);
}

static inline bool should_write_cld_klass(KlassPtr klass, bool leakp) {
  return klass != nullptr && (leakp ? IS_LEAKP(klass) : unloading() ? true : IS_NOT_SERIALIZED(klass));
}

static void write_klass(JfrCheckpointWriter* writer, KlassPtr klass, bool leakp, int& elements) {
  assert(elements >= 0, "invariant");
  ClassLoaderData* cld = get_cld(klass);
  do_write_klass(writer, cld, klass, leakp);
  ++elements;
  if (cld != nullptr) {
    // Write the klass for the direct cld.
    KlassPtr cld_klass = get_cld_klass(cld, leakp);
    if (should_write_cld_klass(cld_klass, leakp)) {
      write_klass(writer, cld_klass, leakp, elements);
    }
  }
  KlassPtr mod_cld_klass = get_module_cld_klass(klass, leakp);
  if (should_write_cld_klass(mod_cld_klass, leakp)) {
    // Write the klass for the module cld.
    write_klass(writer, mod_cld_klass, leakp, elements);
  }
}

/*
 * In C++03, functions used as template parameters must have external linkage;
 * this restriction was removed in C++11. Change back to "static" and
 * rename functions when C++11 becomes available.
 *
 * The weird naming is an effort to decrease the risk of name clashes.
 */
int write__klass(JfrCheckpointWriter* writer, const void* k) {
  assert(k != nullptr, "invariant");
  KlassPtr klass = static_cast<KlassPtr>(k);
  int elements = 0;
  write_klass(writer, klass, false, elements);
  return elements;
}

int write__klass__leakp(JfrCheckpointWriter* writer, const void* k) {
  assert(k != nullptr, "invariant");
  KlassPtr klass = static_cast<KlassPtr>(k);
  int elements = 0;
  write_klass(writer, klass, true, elements);
  return elements;
}

static int primitives_count = 9;

static const char* primitive_name(KlassPtr type_array_klass) {
  switch (type_array_klass->name()->base()[1]) {
    case JVM_SIGNATURE_BOOLEAN: return "boolean";
    case JVM_SIGNATURE_BYTE: return "byte";
    case JVM_SIGNATURE_CHAR: return "char";
    case JVM_SIGNATURE_SHORT: return "short";
    case JVM_SIGNATURE_INT: return "int";
    case JVM_SIGNATURE_LONG: return "long";
    case JVM_SIGNATURE_FLOAT: return "float";
    case JVM_SIGNATURE_DOUBLE: return "double";
  }
  assert(false, "invalid type array klass");
  return nullptr;
}

static Symbol* primitive_symbol(KlassPtr type_array_klass) {
  if (type_array_klass == nullptr) {
    // void.class
    static Symbol* const void_class_name = SymbolTable::probe("void", 4);
    assert(void_class_name != nullptr, "invariant");
    return void_class_name;
  }
  const char* const primitive_type_str = primitive_name(type_array_klass);
  assert(primitive_type_str != nullptr, "invariant");
  Symbol* const primitive_type_sym = SymbolTable::probe(primitive_type_str,
                                                        (int)strlen(primitive_type_str));
  assert(primitive_type_sym != nullptr, "invariant");
  return primitive_type_sym;
}

static traceid primitive_id(KlassPtr array_klass) {
  if (array_klass == nullptr) {
    // The first klass id is reserved for the void.class.
    return LAST_TYPE_ID + 1;
  }
  // Derive the traceid for a primitive mirror from its associated array klass (+1).
  return JfrTraceId::load_raw(array_klass) + 1;
}

static void write_primitive(JfrCheckpointWriter* writer, KlassPtr type_array_klass) {
  assert(writer != nullptr, "invariant");
  assert(_artifacts != nullptr, "invariant");
  writer->write(primitive_id(type_array_klass));
  writer->write(cld_id(get_cld(Universe::boolArrayKlass()), false));
  writer->write(mark_symbol(primitive_symbol(type_array_klass), false));
  writer->write(package_id(Universe::boolArrayKlass(), false));
  writer->write(get_primitive_flags());
  writer->write<bool>(false);
}

static bool is_initial_typeset_for_chunk() {
  return _initial_type_set && !unloading();
}

// A mirror representing a primitive class (e.g. int.class) has no reified Klass*,
// instead it has an associated TypeArrayKlass* (e.g. int[].class).
// We can use the TypeArrayKlass* as a proxy for deriving the id of the primitive class.
// The exception is the void.class, which has neither a Klass* nor a TypeArrayKlass*.
// It will use a reserved constant.
static void do_primitives() {
  assert(is_initial_typeset_for_chunk(), "invariant");
  write_primitive(_writer, Universe::boolArrayKlass());
  write_primitive(_writer, Universe::byteArrayKlass());
  write_primitive(_writer, Universe::charArrayKlass());
  write_primitive(_writer, Universe::shortArrayKlass());
  write_primitive(_writer, Universe::intArrayKlass());
  write_primitive(_writer, Universe::longArrayKlass());
  write_primitive(_writer, Universe::floatArrayKlass());
  write_primitive(_writer, Universe::doubleArrayKlass());
  write_primitive(_writer, nullptr); // void.class
}

static void do_unloading_klass(Klass* klass) {
  assert(klass != nullptr, "invariant");
  assert(_subsystem_callback != nullptr, "invariant");
  if (JfrKlassUnloading::on_unload(klass)) {
    _subsystem_callback->do_artifact(klass);
  }
}

static void do_klass(Klass* klass) {
  assert(klass != nullptr, "invariant");
  assert(used(klass), "invariant");
  assert(_subsystem_callback != nullptr, "invariant");
  _subsystem_callback->do_artifact(klass);
}

static void do_klasses() {
  if (unloading()) {
    ClassLoaderDataGraph::classes_unloading_do(&do_unloading_klass);
    return;
  }
  if (is_initial_typeset_for_chunk()) {
    // Only write the primitive classes once per chunk.
    do_primitives();
  }
  JfrTraceIdLoadBarrier::do_klasses(&do_klass, previous_epoch());
}

static void do_klass_on_clear(Klass* klass) {
  do_artifact(klass);
}

static void do_all_klasses() {
  ClassLoaderDataGraph::classes_do(&do_klass_on_clear);
}

// KlassWriter.
typedef SerializePredicate<KlassPtr> KlassPredicate;
typedef JfrPredicatedTypeWriterImplHost<KlassPtr, KlassPredicate, write__klass> KlassWriterImpl;
typedef JfrTypeWriterHost<KlassWriterImpl, TYPE_CLASS> KlassWriter;

// Klass registration.
typedef CompositeFunctor<KlassPtr, KlassWriter, KlassArtifactRegistrator> KlassWriterRegistration;
typedef JfrArtifactCallbackHost<KlassPtr, KlassWriterRegistration> KlassCallback;

template <>
class LeakPredicate<const Klass*> {
public:
  LeakPredicate(bool class_unload) {}
  bool operator()(const Klass* klass) {
    assert(klass != nullptr, "invariant");
    return IS_LEAKP(klass);
  }
};

// KlassWriter for leakp. Only used during start or rotation, i.e. the previous epoch.
typedef LeakPredicate<KlassPtr> LeakKlassPredicate;
typedef JfrPredicatedTypeWriterImplHost<KlassPtr, LeakKlassPredicate, write__klass__leakp> LeakKlassWriterImpl;
typedef JfrTypeWriterHost<LeakKlassWriterImpl, TYPE_CLASS> LeakKlassWriter;

// Composite KlassWriter with registration.
typedef CompositeFunctor<KlassPtr, LeakKlassWriter, KlassWriter> CompositeKlassWriter;
typedef CompositeFunctor<KlassPtr, CompositeKlassWriter, KlassArtifactRegistrator> CompositeKlassWriterRegistration;
typedef JfrArtifactCallbackHost<KlassPtr, CompositeKlassWriterRegistration> CompositeKlassCallback;

static void write_klasses() {
  assert(!_artifacts->has_klass_entries(), "invariant");
  assert(_writer != nullptr, "invariant");
  KlassArtifactRegistrator reg(_artifacts);
  KlassWriter kw(_writer, unloading());
  KlassWriterRegistration kwr(&kw, &reg);
  if (_leakp_writer == nullptr) {
    KlassCallback callback(&_subsystem_callback, &kwr);
    do_klasses();
  } else {
    LeakKlassWriter lkw(_leakp_writer, unloading());
    CompositeKlassWriter ckw(&lkw, &kw);
    CompositeKlassWriterRegistration ckwr(&ckw, &reg);
    CompositeKlassCallback callback(&_subsystem_callback, &ckwr);
    do_klasses();
  }
  if (is_initial_typeset_for_chunk()) {
    // Because the set of primitives is written outside the callback,
    // their count is not automatically incremented.
    kw.add(primitives_count);
  }
  _artifacts->tally(kw);
}

static void write_klasses_on_clear() {
  assert(!_artifacts->has_klass_entries(), "invariant");
  assert(_writer != nullptr, "invariant");
  assert(_leakp_writer != nullptr, "invariant");
  KlassArtifactRegistrator reg(_artifacts);
  KlassWriter kw(_writer, unloading());
  KlassWriterRegistration kwr(&kw, &reg);
  LeakKlassWriter lkw(_leakp_writer, unloading());
  CompositeKlassWriter ckw(&lkw, &kw);
  CompositeKlassWriterRegistration ckwr(&ckw, &reg);
  CompositeKlassCallback callback(&_subsystem_callback, &ckwr);
  do_all_klasses();
  _artifacts->tally(kw);
}

/***** Packages *****/

static int write_package(JfrCheckpointWriter* writer, PkgPtr pkg, bool leakp) {
  assert(writer != nullptr, "invariant");
  assert(_artifacts != nullptr, "invariant");
  assert(pkg != nullptr, "invariant");
  writer->write(artifact_id(pkg));
  writer->write(mark_symbol(pkg->name(), leakp));
  writer->write(module_id(pkg, leakp));
  writer->write((bool)pkg->is_exported());
  return 1;
}

int write__package(JfrCheckpointWriter* writer, const void* p) {
  assert(p != nullptr, "invariant");
  PkgPtr pkg = static_cast<PkgPtr>(p);
  set_serialized(pkg);
  return write_package(writer, pkg, false);
}

int write__package__leakp(JfrCheckpointWriter* writer, const void* p) {
  assert(p != nullptr, "invariant");
  PkgPtr pkg = static_cast<PkgPtr>(p);
  CLEAR_LEAKP(pkg);
  return write_package(writer, pkg, true);
}


// PackageWriter.
typedef SerializePredicate<PkgPtr> PackagePredicate;
typedef JfrPredicatedTypeWriterImplHost<PkgPtr, PackagePredicate, write__package> PackageWriterImpl;
typedef JfrTypeWriterHost<PackageWriterImpl, TYPE_PACKAGE> PackageWriter;
typedef JfrArtifactCallbackHost<PkgPtr, PackageWriter> PackageCallback;

// PackageWriter used during flush or unloading i.e. the current epoch.
typedef KlassToFieldEnvelope<PackageFieldSelector, PackageWriter> KlassPackageWriter;

// PackageWriter with clear. Only used during start or rotation, i.e. the previous epoch.
typedef CompositeFunctor<PkgPtr, PackageWriter, ClearArtifact<PkgPtr> > PackageWriterWithClear;
typedef JfrArtifactCallbackHost<PkgPtr, PackageWriterWithClear> PackageClearCallback;

// PackageWriter for leakp. Only used during start or rotation, i.e. the previous epoch.
typedef LeakPredicate<PkgPtr> LeakPackagePredicate;
typedef JfrPredicatedTypeWriterImplHost<PkgPtr, LeakPackagePredicate, write__package__leakp> LeakPackageWriterImpl;
typedef JfrTypeWriterHost<LeakPackageWriterImpl, TYPE_PACKAGE> LeakPackageWriter;

// Composite PackageWriter with clear. Only used during start or rotation, i.e. the previous epoch.
typedef CompositeFunctor<PkgPtr, LeakPackageWriter, PackageWriter> CompositePackageWriter;
typedef CompositeFunctor<PkgPtr, CompositePackageWriter, ClearArtifact<PkgPtr> > CompositePackageWriterWithClear;
typedef JfrArtifactCallbackHost<PkgPtr, CompositePackageWriterWithClear> CompositePackageClearCallback;

static void do_package(PackageEntry* pkg) {
  do_artifact(pkg);
}

static void do_all_packages() {
  ClassLoaderDataGraph::packages_do(&do_package);
}

static void do_all_packages(PackageWriter& pw) {
  do_all_packages();
  _artifacts->tally(pw);
}

static void do_packages(PackageWriter& pw) {
  KlassPackageWriter kpw(&pw);
  _artifacts->iterate_klasses(kpw);
  _artifacts->tally(pw);
}

static void write_packages_with_leakp(PackageWriter& pw) {
  assert(_writer != nullptr, "invariant");
  assert(_leakp_writer != nullptr, "invariant");
  assert(previous_epoch(), "invariant");
  LeakPackageWriter lpw(_leakp_writer, unloading());
  CompositePackageWriter cpw(&lpw, &pw);
  ClearArtifact<PkgPtr> clear;
  CompositePackageWriterWithClear cpwwc(&cpw, &clear);
  CompositePackageClearCallback callback(&_subsystem_callback, &cpwwc);
  do_all_packages(pw);
}

static void write_packages() {
  assert(_writer != nullptr, "invariant");
  PackageWriter pw(_writer, unloading());
  if (current_epoch()) {
    do_packages(pw);
    return;
  }
  assert(previous_epoch(), "invariant");
  if (_leakp_writer == nullptr) {
    ClearArtifact<PkgPtr> clear;
    PackageWriterWithClear pwwc(&pw, &clear);
    PackageClearCallback callback(&_subsystem_callback, &pwwc);
    do_all_packages(pw);
    return;
  }
  write_packages_with_leakp(pw);
}

static void write_packages_on_clear() {
  assert(_writer != nullptr, "invariant");
  assert(_leakp_writer != nullptr, "invariant");
  assert(previous_epoch(), "invariant");
  PackageWriter pw(_writer, unloading());
  write_packages_with_leakp(pw);
}

/***** Modules *****/

static int write_module(JfrCheckpointWriter* writer, ModPtr mod, bool leakp) {
  assert(mod != nullptr, "invariant");
  assert(_artifacts != nullptr, "invariant");
  writer->write(artifact_id(mod));
  writer->write(mark_symbol(mod->name(), leakp));
  writer->write(mark_symbol(mod->version(), leakp));
  writer->write(mark_symbol(mod->location(), leakp));
  writer->write(cld_id(mod->loader_data(), leakp));
  return 1;
}

int write__module(JfrCheckpointWriter* writer, const void* m) {
  assert(m != nullptr, "invariant");
  ModPtr mod = static_cast<ModPtr>(m);
  set_serialized(mod);
  return write_module(writer, mod, false);
}

int write__module__leakp(JfrCheckpointWriter* writer, const void* m) {
  assert(m != nullptr, "invariant");
  ModPtr mod = static_cast<ModPtr>(m);
  CLEAR_LEAKP(mod);
  return write_module(writer, mod, true);
}

// ModuleWriter.
typedef SerializePredicate<ModPtr> ModulePredicate;
typedef JfrPredicatedTypeWriterImplHost<ModPtr, ModulePredicate, write__module> ModuleWriterImpl;
typedef JfrTypeWriterHost<ModuleWriterImpl, TYPE_MODULE> ModuleWriter;
typedef JfrArtifactCallbackHost<ModPtr, ModuleWriter> ModuleCallback;

// ModuleWriter used during flush or unloading i.e. the current epoch.
typedef KlassToFieldEnvelope<ModuleFieldSelector, ModuleWriter> KlassModuleWriter;

// ModuleWriter with clear. Only used during start or rotation, i.e. the previous epoch.
typedef CompositeFunctor<ModPtr, ModuleWriter, ClearArtifact<ModPtr> > ModuleWriterWithClear;
typedef JfrArtifactCallbackHost<ModPtr, ModuleWriterWithClear> ModuleClearCallback;

// ModuleWriter for leakp. Only used during start or rotation, i.e. the previous epoch.
typedef LeakPredicate<ModPtr> LeakModulePredicate;
typedef JfrPredicatedTypeWriterImplHost<ModPtr, LeakModulePredicate, write__module__leakp> LeakModuleWriterImpl;
typedef JfrTypeWriterHost<LeakModuleWriterImpl, TYPE_MODULE> LeakModuleWriter;

// Composite ModuleWriter with clear. Only used during start or rotation, i.e. the previous epoch.
typedef CompositeFunctor<ModPtr, LeakModuleWriter, ModuleWriter> CompositeModuleWriter;
typedef CompositeFunctor<ModPtr, CompositeModuleWriter, ClearArtifact<ModPtr> > CompositeModuleWriterWithClear;
typedef JfrArtifactCallbackHost<ModPtr, CompositeModuleWriterWithClear> CompositeModuleClearCallback;

static void do_module(ModuleEntry* mod) {
  do_artifact(mod);
}

static void do_all_modules() {
  ClassLoaderDataGraph::modules_do(&do_module);
}

static void do_all_modules(ModuleWriter& mw) {
  do_all_modules();
  _artifacts->tally(mw);
}

static void do_modules(ModuleWriter& mw) {
  KlassModuleWriter kmw(&mw);
  _artifacts->iterate_klasses(kmw);
  _artifacts->tally(mw);
}

static void write_modules_with_leakp(ModuleWriter& mw) {
  assert(_writer != nullptr, "invariant");
  assert(_leakp_writer != nullptr, "invariant");
  assert(previous_epoch(), "invariant");
  LeakModuleWriter lmw(_leakp_writer, unloading());
  CompositeModuleWriter cmw(&lmw, &mw);
  ClearArtifact<ModPtr> clear;
  CompositeModuleWriterWithClear cmwwc(&cmw, &clear);
  CompositeModuleClearCallback callback(&_subsystem_callback, &cmwwc);
  do_all_modules(mw);
}

static void write_modules() {
  assert(_writer != nullptr, "invariant");
  ModuleWriter mw(_writer, unloading());
  if (current_epoch()) {
    do_modules(mw);
    return;
  }
  assert(previous_epoch(), "invariant");
  if (_leakp_writer == nullptr) {
    ClearArtifact<ModPtr> clear;
    ModuleWriterWithClear mwwc(&mw, &clear);
    ModuleClearCallback callback(&_subsystem_callback, &mwwc);
    do_all_modules(mw);
    return;
  }
  write_modules_with_leakp(mw);
}

static void write_modules_on_clear() {
  assert(_writer != nullptr, "invariant");
  assert(_leakp_writer != nullptr, "invariant");
  assert(previous_epoch(), "invariant");
  ModuleWriter mw(_writer, unloading());
  write_modules_with_leakp(mw);
}

/***** ClassLoaderData - CLD *****/

static int write_cld(JfrCheckpointWriter* writer, CldPtr cld, bool leakp) {
  assert(cld != nullptr, "invariant");
  // class loader type
  const Klass* class_loader_klass = cld->class_loader_klass();
  if (class_loader_klass == nullptr) {
    // (primordial) boot class loader
    writer->write(artifact_id(cld)); // class loader instance id
    writer->write((traceid)0);  // class loader type id (absence of)
    writer->write(get_bootstrap_name(leakp)); // maps to synthetic name -> "bootstrap"
  } else {
    assert(IS_SERIALIZED(class_loader_klass), "invariant");
    writer->write(artifact_id(cld)); // class loader instance id
    writer->write(artifact_id(class_loader_klass)); // class loader type id
    writer->write(mark_symbol(cld->name(), leakp)); // class loader instance name
  }
  return 1;
}

int write__cld(JfrCheckpointWriter* writer, const void* c) {
  assert(c != nullptr, "invariant");
  CldPtr cld = static_cast<CldPtr>(c);
  set_serialized(cld);
  return write_cld(writer, cld, false);
}

int write__cld__leakp(JfrCheckpointWriter* writer, const void* c) {
  assert(c != nullptr, "invariant");
  CldPtr cld = static_cast<CldPtr>(c);
  CLEAR_LEAKP(cld);
  return write_cld(writer, cld, true);
}

// CldWriter.
typedef SerializePredicate<CldPtr> CldPredicate;
typedef JfrPredicatedTypeWriterImplHost<CldPtr, CldPredicate, write__cld> CldWriterImpl;
typedef JfrTypeWriterHost<CldWriterImpl, TYPE_CLASSLOADER> CldWriter;
typedef JfrArtifactCallbackHost<CldPtr, CldWriter> CldCallback;

// CldWriter used during flush or unloading i.e. the current epoch.
typedef KlassToFieldEnvelope<KlassCldFieldSelector, CldWriter> KlassCldWriter;
typedef KlassToFieldEnvelope<ModuleCldFieldSelector, CldWriter> ModuleCldWriter;
typedef CompositeFunctor<KlassPtr, KlassCldWriter, ModuleCldWriter> KlassAndModuleCldWriter;

// CldWriter with clear. Only used during start or rotation, i.e. the previous epoch.
typedef CompositeFunctor<CldPtr, CldWriter, ClearArtifact<CldPtr> > CldWriterWithClear;
typedef JfrArtifactCallbackHost<CldPtr, CldWriterWithClear> CldClearCallback;

// CldWriter for leakp. Only used during start or rotation, i.e. the previous epoch.
typedef LeakPredicate<CldPtr> LeakCldPredicate;
typedef JfrPredicatedTypeWriterImplHost<CldPtr, LeakCldPredicate, write__cld__leakp> LeakCldWriterImpl;
typedef JfrTypeWriterHost<LeakCldWriterImpl, TYPE_CLASSLOADER> LeakCldWriter;

// Composite CldWriter with clear. Only used during start or rotation, i.e. the previous epoch.
typedef CompositeFunctor<CldPtr, LeakCldWriter, CldWriter> CompositeCldWriter;
typedef CompositeFunctor<CldPtr, CompositeCldWriter, ClearArtifact<CldPtr> > CompositeCldWriterWithClear;
typedef JfrArtifactCallbackHost<CldPtr, CompositeCldWriterWithClear> CompositeCldClearCallback;

class CLDCallback : public CLDClosure {
 public:
  void do_cld(ClassLoaderData* cld) {
    assert(cld != nullptr, "invariant");
    if (!cld->has_class_mirror_holder()) {
      do_artifact(cld);
    }
  }
};

static void do_all_clds() {
  CLDCallback cld_cb;
  ClassLoaderDataGraph::loaded_cld_do(&cld_cb);
}

static void do_all_clds(CldWriter& cldw) {
  do_all_clds();
  _artifacts->tally(cldw);
}

static void do_clds(CldWriter& cldw) {
  KlassCldWriter kcw(&cldw);
  ModuleCldWriter mcw(&cldw);
  KlassAndModuleCldWriter kmcw(&kcw, &mcw);
  _artifacts->iterate_klasses(kmcw);
  if (is_initial_typeset_for_chunk()) {
    CldPtr bootloader = get_cld(Universe::boolArrayKlass());
    assert(bootloader != nullptr, "invariant");
    if (IS_NOT_SERIALIZED(bootloader)) {
      write__cld(_writer, bootloader);
      assert(IS_SERIALIZED(bootloader), "invariant");
      cldw.add(1);
    }
  }
  _artifacts->tally(cldw);
}

static void write_clds_with_leakp(CldWriter& cldw) {
  assert(_writer != nullptr, "invariant");
  assert(_leakp_writer != nullptr, "invariant");
  assert(previous_epoch(), "invariant");
  LeakCldWriter lcldw(_leakp_writer, unloading());
  CompositeCldWriter ccldw(&lcldw, &cldw);
  ClearArtifact<CldPtr> clear;
  CompositeCldWriterWithClear ccldwwc(&ccldw, &clear);
  CompositeCldClearCallback callback(&_subsystem_callback, &ccldwwc);
  do_all_clds(cldw);
}

static void write_clds() {
  assert(_writer != nullptr, "invariant");
  CldWriter cldw(_writer, unloading());
  if (current_epoch()) {
    do_clds(cldw);
    return;
  }
  assert(previous_epoch(), "invariant");
  if (_leakp_writer == nullptr) {
    ClearArtifact<CldPtr> clear;
    CldWriterWithClear cldwwc(&cldw, &clear);
    CldClearCallback callback(&_subsystem_callback, &cldwwc);
    do_all_clds(cldw);
    return;
  }
  write_clds_with_leakp(cldw);
}

static void write_clds_on_clear() {
  assert(_writer != nullptr, "invariant");
  assert(_leakp_writer != nullptr, "invariant");
  assert(previous_epoch(), "invariant");
  CldWriter cldw(_writer, unloading());
  write_clds_with_leakp(cldw);
}

/***** Methods *****/

template <>
void set_serialized<Method>(MethodPtr method) {
  assert(method != nullptr, "invariant");
  if (current_epoch()) {
    CLEAR_THIS_EPOCH_METHOD_CLEARED_BIT(method);
    assert(!IS_THIS_EPOCH_METHOD_CLEARED_BIT_SET(method), "invariant");
  }
  assert(unloading() ? true : METHOD_IS_NOT_SERIALIZED(method), "invariant");
  SET_METHOD_SERIALIZED(method);
  assert(IS_PREVIOUS_EPOCH_METHOD_CLEARED_BIT_SET(method), "invariant");
  assert(METHOD_IS_SERIALIZED(method), "invariant");
}

static inline u1 get_visibility(MethodPtr method) {
  assert(method != nullptr, "invariant");
  return const_cast<Method*>(method)->is_hidden() ? (u1)1 : (u1)0;
}

static int write_method(JfrCheckpointWriter* writer, MethodPtr method, bool leakp) {
  assert(writer != nullptr, "invariant");
  assert(method != nullptr, "invariant");
  assert(_artifacts != nullptr, "invariant");
  KlassPtr klass = method->method_holder();
  assert(klass != nullptr, "invariant");
  assert(used(klass), "invariant");
  assert(IS_SERIALIZED(klass), "invariant");
  writer->write(method_id(klass, method));
  writer->write(artifact_id(klass));
  writer->write(mark_symbol(method->name(), leakp));
  writer->write(mark_symbol(method->signature(), leakp));
  writer->write(static_cast<u2>(get_flags(method)));
  writer->write(get_visibility(method));
  return 1;
}

int write__method(JfrCheckpointWriter* writer, const void* m) {
  assert(m != nullptr, "invariant");
  MethodPtr method = static_cast<MethodPtr>(m);
  set_serialized(method);
  return write_method(writer, method, false);
}

int write__method__leakp(JfrCheckpointWriter* writer, const void* m) {
  assert(m != nullptr, "invariant");
  MethodPtr method = static_cast<MethodPtr>(m);
  CLEAR_LEAKP_METHOD(method);
  return write_method(writer, method, true);
}
template <typename MethodCallback, typename KlassCallback, bool leakp>
class MethodIteratorHost {
 private:
  MethodCallback _method_cb;
  KlassCallback _klass_cb;
  KlassUsedPredicate _klass_used_predicate;
  MethodUsedPredicate _method_used_predicate;
  MethodFlagPredicate<leakp> _method_flag_predicate;
 public:
  MethodIteratorHost(JfrCheckpointWriter* writer) :
    _method_cb(writer, unloading(), false),
    _klass_cb(writer, unloading(), false),
    _klass_used_predicate(current_epoch()),
    _method_used_predicate(current_epoch()),
    _method_flag_predicate(current_epoch()) {}

  bool operator()(KlassPtr klass) {
    if (_method_used_predicate(klass)) {
      const InstanceKlass* ik = InstanceKlass::cast(klass);
      while (ik != nullptr) {
        const int len = ik->methods()->length();
        for (int i = 0; i < len; ++i) {
          MethodPtr method = ik->methods()->at(i);
          if (_method_flag_predicate(method)) {
            _method_cb(method);
          }
        }
        // There can be multiple versions of the same method running
        // due to redefinition. Need to inspect the complete set of methods.
        ik = ik->previous_versions();
      }
    }
    return _klass_used_predicate(klass) ? _klass_cb(klass) : true;
  }

  int count() const { return _method_cb.count(); }
  void add(int count) { _method_cb.add(count); }
};

template <typename T, template <typename> class Impl>
class Wrapper {
  Impl<T> _t;
 public:
  Wrapper(JfrCheckpointWriter*, bool, bool) : _t() {}
  bool operator()(T const& value) {
    return _t(value);
  }
};

template <typename T>
class EmptyStub {
 public:
  bool operator()(T const& value) { return true; }
};

typedef SerializePredicate<MethodPtr> MethodPredicate;
typedef JfrPredicatedTypeWriterImplHost<MethodPtr, MethodPredicate, write__method> MethodWriterImplTarget;
typedef Wrapper<KlassPtr, EmptyStub> KlassCallbackStub;
typedef JfrTypeWriterHost<MethodWriterImplTarget, TYPE_METHOD> MethodWriterImpl;
typedef MethodIteratorHost<MethodWriterImpl, KlassCallbackStub, false> MethodWriter;

typedef LeakPredicate<MethodPtr> LeakMethodPredicate;
typedef JfrPredicatedTypeWriterImplHost<MethodPtr, LeakMethodPredicate, write__method__leakp> LeakMethodWriterImplTarget;
typedef JfrTypeWriterHost<LeakMethodWriterImplTarget, TYPE_METHOD> LeakMethodWriterImpl;
typedef MethodIteratorHost<LeakMethodWriterImpl, KlassCallbackStub, true> LeakMethodWriter;
typedef CompositeFunctor<KlassPtr, LeakMethodWriter, MethodWriter> CompositeMethodWriter;

static void write_methods_with_leakp(MethodWriter& mw) {
  assert(_writer != nullptr, "invariant");
  assert(_leakp_writer != nullptr, "invariant");
  assert(previous_epoch(), "invariant");
  LeakMethodWriter lpmw(_leakp_writer);
  CompositeMethodWriter cmw(&lpmw, &mw);
  _artifacts->iterate_klasses(cmw);
  _artifacts->tally(mw);
}

static void write_methods() {
  assert(_writer != nullptr, "invariant");
  MethodWriter mw(_writer);
  if (_leakp_writer == nullptr) {
    _artifacts->iterate_klasses(mw);
    _artifacts->tally(mw);
    return;
  }
  write_methods_with_leakp(mw);
}

static void write_methods_on_clear() {
  assert(_writer != nullptr, "invariant");
  assert(_leakp_writer != nullptr, "invariant");
  assert(previous_epoch(), "invariant");
  MethodWriter mw(_writer);
  write_methods_with_leakp(mw);
}

template <>
void set_serialized<JfrSymbolTable::SymbolEntry>(SymbolEntryPtr ptr) {
  assert(ptr != nullptr, "invariant");
  ptr->set_serialized();
  assert(ptr->is_serialized(), "invariant");
}

template <>
void set_serialized<JfrSymbolTable::StringEntry>(StringEntryPtr ptr) {
  assert(ptr != nullptr, "invariant");
  ptr->set_serialized();
  assert(ptr->is_serialized(), "invariant");
}

static int write_symbol(JfrCheckpointWriter* writer, SymbolEntryPtr entry, bool leakp) {
  assert(writer != nullptr, "invariant");
  assert(entry != nullptr, "invariant");
  ResourceMark rm;
  writer->write(entry->id());
  writer->write(entry->value()->as_C_string());
  return 1;
}

static int write__symbol(JfrCheckpointWriter* writer, const void* e) {
  assert(e != nullptr, "invariant");
  SymbolEntryPtr entry = static_cast<SymbolEntryPtr>(e);
  set_serialized(entry);
  return write_symbol(writer, entry, false);
}

static int write__symbol__leakp(JfrCheckpointWriter* writer, const void* e) {
  assert(e != nullptr, "invariant");
  SymbolEntryPtr entry = static_cast<SymbolEntryPtr>(e);
  return write_symbol(writer, entry, true);
}

static int write_string(JfrCheckpointWriter* writer, StringEntryPtr entry, bool leakp) {
  assert(writer != nullptr, "invariant");
  assert(entry != nullptr, "invariant");
  writer->write(entry->id());
  writer->write(entry->value());
  return 1;
}

static int write__string(JfrCheckpointWriter* writer, const void* e) {
  assert(e != nullptr, "invariant");
  StringEntryPtr entry = static_cast<StringEntryPtr>(e);
  set_serialized(entry);
  return write_string(writer, entry, false);
}

static int write__string__leakp(JfrCheckpointWriter* writer, const void* e) {
  assert(e != nullptr, "invariant");
  StringEntryPtr entry = static_cast<StringEntryPtr>(e);
  return write_string(writer, entry, true);
}

typedef SymbolPredicate<SymbolEntryPtr, false> SymPredicate;
typedef JfrPredicatedTypeWriterImplHost<SymbolEntryPtr, SymPredicate, write__symbol> SymbolEntryWriterImpl;
typedef JfrTypeWriterHost<SymbolEntryWriterImpl, TYPE_SYMBOL> SymbolEntryWriter;
typedef SymbolPredicate<StringEntryPtr, false> StringPredicate;
typedef JfrPredicatedTypeWriterImplHost<StringEntryPtr, StringPredicate, write__string> StringEntryWriterImpl;
typedef JfrTypeWriterHost<StringEntryWriterImpl, TYPE_SYMBOL> StringEntryWriter;

typedef SymbolPredicate<SymbolEntryPtr, true> LeakSymPredicate;
typedef JfrPredicatedTypeWriterImplHost<SymbolEntryPtr, LeakSymPredicate, write__symbol__leakp> LeakSymbolEntryWriterImpl;
typedef JfrTypeWriterHost<LeakSymbolEntryWriterImpl, TYPE_SYMBOL> LeakSymbolEntryWriter;
typedef CompositeFunctor<SymbolEntryPtr, LeakSymbolEntryWriter, SymbolEntryWriter> CompositeSymbolWriter;
typedef SymbolPredicate<StringEntryPtr, true> LeakStringPredicate;
typedef JfrPredicatedTypeWriterImplHost<StringEntryPtr, LeakStringPredicate, write__string__leakp> LeakStringEntryWriterImpl;
typedef JfrTypeWriterHost<LeakStringEntryWriterImpl, TYPE_SYMBOL> LeakStringEntryWriter;
typedef CompositeFunctor<StringEntryPtr, LeakStringEntryWriter, StringEntryWriter> CompositeStringWriter;

static void write_symbols_with_leakp() {
  assert(_writer != nullptr, "invariant");
  assert(_leakp_writer != nullptr, "invariant");
  assert(previous_epoch(), "invariant");
  SymbolEntryWriter sw(_writer, unloading());
  LeakSymbolEntryWriter lsw(_leakp_writer, unloading());
  CompositeSymbolWriter csw(&lsw, &sw);
  _artifacts->iterate_symbols(csw);
  StringEntryWriter sew(_writer, unloading(), true); // skip header
  LeakStringEntryWriter lsew(_leakp_writer, unloading(), true); // skip header
  CompositeStringWriter csew(&lsew, &sew);
  _artifacts->iterate_strings(csew);
  sw.add(sew.count());
  lsw.add(lsew.count());
  _artifacts->tally(sw);
}

static void write_symbols() {
  assert(_writer != nullptr, "invariant");
  if (_leakp_writer != nullptr) {
    write_symbols_with_leakp();
    return;
  }
  SymbolEntryWriter sw(_writer, unloading());
  _artifacts->iterate_symbols(sw);
  StringEntryWriter sew(_writer, unloading(), true); // skip header
  _artifacts->iterate_strings(sew);
  sw.add(sew.count());
  _artifacts->tally(sw);
}

static void write_symbols_on_clear() {
  assert(_writer != nullptr, "invariant");
  assert(_leakp_writer != nullptr, "invariant");
  assert(previous_epoch(), "invariant");
  write_symbols_with_leakp();
}

typedef Wrapper<KlassPtr, ClearArtifact> ClearKlassBits;
typedef Wrapper<MethodPtr, ClearArtifact> ClearMethodFlag;
typedef MethodIteratorHost<ClearMethodFlag, ClearKlassBits, false> ClearKlassAndMethods;

static void clear_klasses_and_methods() {
  ClearKlassAndMethods clear(_writer);
  _artifacts->iterate_klasses(clear);
}

static size_t teardown() {
  assert(_artifacts != nullptr, "invariant");
  const size_t total_count = _artifacts->total_count();
  if (previous_epoch()) {
    clear_klasses_and_methods();
    JfrKlassUnloading::clear();
    _artifacts->increment_checkpoint_id();
    _initial_type_set = true;
  } else {
    _initial_type_set = false;
  }
  return total_count;
}

static void setup(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer, bool class_unload, bool flushpoint) {
  _writer = writer;
  _leakp_writer = leakp_writer;
  _class_unload = class_unload;
  _flushpoint = flushpoint;
  if (_artifacts == nullptr) {
    _artifacts = new JfrArtifactSet(class_unload);
  } else {
    _artifacts->initialize(class_unload);
  }
  if (!_class_unload) {
    JfrKlassUnloading::sort(previous_epoch());
  }
  assert(_artifacts != nullptr, "invariant");
  assert(!_artifacts->has_klass_entries(), "invariant");
}

/**
 * Write all "tagged" (in-use) constant artifacts and their dependencies.
 */
size_t JfrTypeSet::serialize(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer, bool class_unload, bool flushpoint) {
  assert(writer != nullptr, "invariant");
  ResourceMark rm;
  setup(writer, leakp_writer, class_unload, flushpoint);
  // write order is important because an individual write step
  // might tag an artifact to be written in a subsequent step
  write_klasses();
  write_packages();
  write_modules();
  write_clds();
  write_methods();
  write_symbols();
  return teardown();
}

/**
 * Clear all tags from the previous epoch. Reset support structures.
 */
void JfrTypeSet::clear(JfrCheckpointWriter* writer, JfrCheckpointWriter* leakp_writer) {
  ResourceMark rm;
  setup(writer, leakp_writer, false, false);
  write_klasses_on_clear();
  write_packages_on_clear();
  write_modules_on_clear();
  write_clds_on_clear();
  write_methods_on_clear();
  write_symbols_on_clear();
  teardown();
}

size_t JfrTypeSet::on_unloading_classes(JfrCheckpointWriter* writer) {
  // JfrTraceIdEpoch::has_changed_tag_state_no_reset() is a load-acquire we issue to see side-effects (i.e. tags).
  // The JfrRecorderThread does this as part of normal processing, but with concurrent class unloading, which can
  // happen in arbitrary threads, we invoke it explicitly.
  JfrTraceIdEpoch::has_changed_tag_state_no_reset();
  return serialize(writer, nullptr, true, false);
}
