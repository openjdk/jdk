/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_TRAININGDATA_HPP
#define SHARE_OOPS_TRAININGDATA_HPP

#include "cds/cdsConfig.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/compactHashtable.hpp"
#include "compiler/compiler_globals.hpp"
#include "compiler/compilerDefinitions.hpp"
#include "memory/allocation.hpp"
#include "memory/metaspaceClosure.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/method.hpp"
#include "oops/objArrayKlass.hpp"
#include "runtime/handles.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/resizeableResourceHash.hpp"

class ciEnv;
class ciBaseObject;
class CompileTask;
class CompileTrainingData;
class KlassTrainingData;
class MethodTrainingData;

// Base class for all the training data varieties
class TrainingData : public Metadata {
  friend KlassTrainingData;
  friend MethodTrainingData;
  friend CompileTrainingData;
public:
  // Key is used to insert any TrainingData (TD) object into a hash tables. The key is currently a
  // pointer to a metaspace object the TD is associated with. For example,
  // for KlassTrainingData it's an InstanceKlass, for MethodTrainingData it's a Method.
  // The utility of the these hash tables is to be able to find a TD object for a given metaspace
  // metaspace object.
  class Key {
    mutable Metadata* _meta;
    // These guys can get to my constructors:
    friend TrainingData;
    friend KlassTrainingData;
    friend MethodTrainingData;
    friend CompileTrainingData;

    // The empty key
    Key() : _meta(nullptr) { }
    bool is_empty() const { return _meta == nullptr; }
  public:
    Key(Metadata* meta) : _meta(meta) { }

    static bool can_compute_cds_hash(const Key* const& k);
    static uint cds_hash(const Key* const& k);
    static unsigned hash(const Key* const& k) {
      return primitive_hash(k->meta());
    }
    static bool equals(const Key* const& k1, const Key* const& k2) {
      return k1->meta() == k2->meta();
    }
    static inline bool equals(TrainingData* value, const TrainingData::Key* key, int unused) {
      return equals(value->key(), key);
    }
    int cmp(const Key* that) const {
      auto m1 = this->meta();
      auto m2 = that->meta();
      if (m1 < m2) return -1;
      if (m1 > m2) return +1;
      return 0;
    }
    Metadata* meta() const { return _meta; }
    void metaspace_pointers_do(MetaspaceClosure *iter);
    void make_empty() const { _meta = nullptr; }
  };

  // TrainingDataLocker is used to guard read/write operations on non-MT-safe data structures.
  // It supports recursive locking and a read-only mode (in which case no locks are taken).
  // It is also a part of the TD collection termination protocol (see the "spanshot" field).
  class TrainingDataLocker {
    static volatile bool _snapshot; // If true we're not allocating new training data
    static int _lock_mode;
    const bool _recursive;
    static void lock() {
#if INCLUDE_CDS
      assert(_lock_mode != 0, "Forgot to call TrainingDataLocker::initialize()");
      if (_lock_mode > 0) {
        TrainingData_lock->lock();
      }
#endif
    }
    static void unlock() {
#if INCLUDE_CDS
      if (_lock_mode > 0) {
        TrainingData_lock->unlock();
      }
#endif
    }
    static bool safely_locked() {
#if INCLUDE_CDS
      assert(_lock_mode != 0, "Forgot to call TrainingDataLocker::initialize()");
      if (_lock_mode > 0) {
        return is_self_locked();
      } else {
        return true;
      }
#else
      return true;
#endif
    }
    static bool is_self_locked() {
      return CDS_ONLY(TrainingData_lock->owned_by_self()) NOT_CDS(false);
    }

  public:
    static void snapshot() {
#if INCLUDE_CDS
      assert_locked();
      _snapshot = true;
#endif
    }
    static bool can_add() {
#if INCLUDE_CDS
      assert_locked();
      return !_snapshot;
#else
      return false;
#endif
    }
    static void initialize() {
#if INCLUDE_CDS
      _lock_mode = need_data() ? +1 : -1;   // if -1, we go lock-free
#endif
    }
    static void assert_locked() {
      assert(safely_locked(), "use under TrainingDataLocker");
    }
    static void assert_can_add() {
      assert(can_add(), "Cannot add TrainingData objects");
    }
    TrainingDataLocker() : _recursive(is_self_locked()) {
      if (!_recursive) {
        lock();
      }
    }
    ~TrainingDataLocker() {
      if (!_recursive) {
        unlock();
      }
    }
  };

  // A set of TD objects that we collect during the training run.
  class TrainingDataSet {
    friend TrainingData;
    ResizeableResourceHashtable<const Key*, TrainingData*,
                                AnyObj::C_HEAP, MemTag::mtCompiler,
                                &TrainingData::Key::hash,
                                &TrainingData::Key::equals>
      _table;

  public:
    template<typename... Arg>
    TrainingDataSet(Arg... arg)
      : _table(arg...) {
    }
    TrainingData* find(const Key* key) const {
      TrainingDataLocker::assert_locked();
      if (TrainingDataLocker::can_add()) {
        auto res = _table.get(key);
        return res == nullptr ? nullptr : *res;
      }
      return nullptr;
    }
    bool remove(const Key* key) {
      return _table.remove(key);
    }
    TrainingData* install(TrainingData* td) {
      TrainingDataLocker::assert_locked();
      TrainingDataLocker::assert_can_add();
      auto key = td->key();
      if (key->is_empty()) {
        return td;  // unkeyed TD not installed
      }
      bool created = false;
      auto prior = _table.put_if_absent(key, td, &created);
      if (prior == nullptr || *prior == td) {
        return td;
      }
      assert(false, "no pre-existing elements allowed");
      return *prior;
    }
    template<typename Function>
    void iterate(const Function& fn) const { // lambda enabled API
      iterate(const_cast<Function&>(fn));
    }
    template<typename Function>
    void iterate(Function& fn) const { // lambda enabled API
      return _table.iterate_all([&](const TrainingData::Key* k, TrainingData* td) { fn(td); });
    }
    int size() const { return _table.number_of_entries(); }

    void verify() const {
      TrainingDataLocker::assert_locked();
      iterate([&](TrainingData* td) { td->verify(); });
    }
  };

  // A widget to ensure that we visit TD object only once (TD objects can have pointer to
  // other TD object that are sometimes circular).
  class Visitor {
    ResizeableResourceHashtable<TrainingData*, bool> _visited;
  public:
    Visitor(unsigned size) : _visited(size, 0x3fffffff) { }
    bool is_visited(TrainingData* td) {
      return _visited.contains(td);
    }
    void visit(TrainingData* td) {
      bool created;
      _visited.put_if_absent(td, &created);
    }
  };

  typedef OffsetCompactHashtable<const TrainingData::Key*, TrainingData*, TrainingData::Key::equals> TrainingDataDictionary;
private:
  Key _key;

  // just forward all constructor arguments to the embedded key
  template<typename... Arg>
  TrainingData(Arg... arg)
    : _key(arg...) { }

  // Container for recording TD during training run
  static TrainingDataSet _training_data_set;
  // Containter for replaying the training data (read-only, populated from the AOT image)
  static TrainingDataDictionary _archived_training_data_dictionary;
  // Container used for writing the AOT image
  static TrainingDataDictionary _archived_training_data_dictionary_for_dumping;
  class DumpTimeTrainingDataInfo {
    TrainingData* _training_data;
  public:
    DumpTimeTrainingDataInfo() : DumpTimeTrainingDataInfo(nullptr) {}
    DumpTimeTrainingDataInfo(TrainingData* training_data) : _training_data(training_data) {}
    void metaspace_pointers_do(MetaspaceClosure* it) {
      it->push(&_training_data);
    }
    TrainingData* training_data() {
      return _training_data;
    }
  };
  typedef GrowableArrayCHeap<DumpTimeTrainingDataInfo, mtClassShared> DumptimeTrainingDataDictionary;
  // A temporary container that is used to accumulate and filter TD during dumping
  static DumptimeTrainingDataDictionary* _dumptime_training_data_dictionary;

  static TrainingDataSet* training_data_set() { return &_training_data_set; }
  static TrainingDataDictionary* archived_training_data_dictionary() { return &_archived_training_data_dictionary; }

 public:
  // Returns the key under which this TD is installed, or else
  // Key::EMPTY if it is not installed.
  const Key* key() const { return &_key; }

  static bool have_data() { return AOTReplayTraining;  } // Going to read
  static bool need_data() { return AOTRecordTraining;  } // Going to write
  static bool assembling_data() { return have_data() && CDSConfig::is_dumping_final_static_archive() && CDSConfig::is_dumping_aot_linked_classes(); }

  static bool is_klass_loaded(Klass* k) {
    if (have_data()) {
      // If we're running in AOT mode some classes may not be loaded yet
      if (k->is_objArray_klass()) {
        k = ObjArrayKlass::cast(k)->bottom_klass();
      }
      if (k->is_instance_klass()) {
        return InstanceKlass::cast(k)->is_loaded();
      }
    }
    return true;
  }

  template<typename Function>
  static void iterate(const Function& fn) { iterate(const_cast<Function&>(fn)); }

  template<typename Function>
  static void iterate(Function& fn) { // lambda enabled API
    TrainingDataLocker l;
    if (have_data()) {
      archived_training_data_dictionary()->iterate(fn);
    }
    if (need_data()) {
      training_data_set()->iterate(fn);
    }
  }

  virtual MethodTrainingData*   as_MethodTrainingData()  const { return nullptr; }
  virtual KlassTrainingData*    as_KlassTrainingData()   const { return nullptr; }
  virtual CompileTrainingData*  as_CompileTrainingData() const { return nullptr; }
  bool is_MethodTrainingData()  const { return as_MethodTrainingData()  != nullptr; }
  bool is_KlassTrainingData()   const { return as_KlassTrainingData()   != nullptr; }
  bool is_CompileTrainingData() const { return as_CompileTrainingData() != nullptr; }

  virtual void prepare(Visitor& visitor) = 0;
  virtual void cleanup(Visitor& visitor) = 0;

  static void initialize() NOT_CDS_RETURN;

  static void verify();

  // Widget for recording dependencies, as an N-to-M graph relation,
  // possibly cyclic.
  template<typename E>
  class DepList : public StackObj {
    GrowableArrayCHeap<E, mtCompiler>* _deps_dyn;
    Array<E>*                          _deps;
  public:
    DepList() {
      _deps_dyn = nullptr;
      _deps = nullptr;
    }

    int length() const {
      return (_deps_dyn != nullptr ? _deps_dyn->length()
              : _deps   != nullptr ? _deps->length()
              : 0);
    }
    E* adr_at(int i) const {
      return (_deps_dyn != nullptr ? _deps_dyn->adr_at(i)
              : _deps   != nullptr ? _deps->adr_at(i)
              : nullptr);
    }
    E at(int i) const {
      assert(i >= 0 && i < length(), "oob");
      return *adr_at(i);
    }
    bool append_if_missing(E dep) {
      if (_deps_dyn == nullptr) {
        _deps_dyn = new GrowableArrayCHeap<E, mtCompiler>(10);
        _deps_dyn->append(dep);
        return true;
      } else {
        return _deps_dyn->append_if_missing(dep);
      }
    }
    bool remove_if_existing(E dep) {
      if (_deps_dyn != nullptr) {
        return _deps_dyn->remove_if_existing(dep);
      }
      return false;
    }
    void clear() {
      if (_deps_dyn != nullptr)  {
        _deps_dyn->clear();
      }
    }
    void append(E dep) {
      if (_deps_dyn == nullptr) {
        _deps_dyn = new GrowableArrayCHeap<E, mtCompiler>(10);
      }
      _deps_dyn->append(dep);
    }
    bool contains(E dep) {
      for (int i = 0; i < length(); i++) {
        if (dep == at(i)) {
          return true; // found
        }
      }
      return false; // not found
    }

#if INCLUDE_CDS
    void remove_unshareable_info() {
      _deps_dyn = nullptr;
    }
#endif
    void prepare(ClassLoaderData* loader_data);
    void metaspace_pointers_do(MetaspaceClosure *iter);
  };

  virtual void metaspace_pointers_do(MetaspaceClosure *iter);

  static void init_dumptime_table(TRAPS);

#if INCLUDE_CDS
  virtual void remove_unshareable_info() {}
  static void iterate_roots(MetaspaceClosure* it);
  static void dump_training_data();
  static void cleanup_training_data();
  static void serialize(SerializeClosure* soc);
  static void print_archived_training_data_on(outputStream* st);
  static TrainingData* lookup_archived_training_data(const Key* k);
#endif

  template<typename TrainingDataType, typename... ArgTypes>
  static TrainingDataType* allocate(ArgTypes... args) {
    assert(need_data() || have_data(), "");
    if (TrainingDataLocker::can_add()) {
      return new (mtClassShared) TrainingDataType(args...);
    }
    return nullptr;
  }
};

// Training data that is associated with an InstanceKlass
class KlassTrainingData : public TrainingData {
  friend TrainingData;
  friend CompileTrainingData;

  // Used by CDS. These classes need to access the private default constructor.
  template <class T> friend class CppVtableTesterA;
  template <class T> friend class CppVtableTesterB;
  template <class T> friend class CppVtableCloner;

  // cross-link to live klass, or null if not loaded or encountered yet
  InstanceKlass* _holder;

  DepList<CompileTrainingData*> _comp_deps; // compiles that depend on me

  KlassTrainingData();
  KlassTrainingData(InstanceKlass* klass);

  int comp_dep_count() const {
    TrainingDataLocker::assert_locked();
    return _comp_deps.length();
  }
  CompileTrainingData* comp_dep(int i) const {
    TrainingDataLocker::assert_locked();
    return _comp_deps.at(i);
  }
  void add_comp_dep(CompileTrainingData* ctd) {
    TrainingDataLocker::assert_locked();
     _comp_deps.append_if_missing(ctd);
  }
  void remove_comp_dep(CompileTrainingData* ctd) {
    TrainingDataLocker::assert_locked();
     _comp_deps.remove_if_existing(ctd);
  }
 public:
  Symbol* name() const {
    precond(has_holder());
    return holder()->name();
  }
  bool has_holder()       const { return _holder != nullptr; }
  InstanceKlass* holder() const { return _holder; }

  static KlassTrainingData* make(InstanceKlass* holder,
                                 bool null_if_not_found = false) NOT_CDS_RETURN_(nullptr);
  static KlassTrainingData* find(InstanceKlass* holder) {
    return make(holder, true);
  }
  virtual KlassTrainingData* as_KlassTrainingData() const { return const_cast<KlassTrainingData*>(this); };

  ClassLoaderData* class_loader_data() {
    assert(has_holder(), "");
    return holder()->class_loader_data();
  }
  void notice_fully_initialized() NOT_CDS_RETURN;

  void print_on(outputStream* st, bool name_only) const;
  virtual void print_on(outputStream* st) const { print_on(st, false); }
  virtual void print_value_on(outputStream* st) const { print_on(st, true); }

  virtual void prepare(Visitor& visitor);
  virtual void cleanup(Visitor& visitor) NOT_CDS_RETURN;

  MetaspaceObj::Type type() const {
    return KlassTrainingDataType;
  }

#if INCLUDE_CDS
  virtual void remove_unshareable_info();
#endif

  void metaspace_pointers_do(MetaspaceClosure *iter);

  int size() const {
    return (int)align_metadata_size(align_up(sizeof(KlassTrainingData), BytesPerWord)/BytesPerWord);
  }

  const char* internal_name() const {
    return "{ klass training data }";
  };

  void verify();

  static KlassTrainingData* allocate(InstanceKlass* holder) {
    return TrainingData::allocate<KlassTrainingData>(holder);
  }

  template<typename Function>
  void iterate_comp_deps(Function fn) const { // lambda enabled API
    TrainingDataLocker l;
    for (int i = 0; i < comp_dep_count(); i++) {
      fn(comp_dep(i));
    }
  }
};

// Information about particular JIT tasks.
class CompileTrainingData : public TrainingData {
  friend TrainingData;
  friend KlassTrainingData;

  // Used by CDS. These classes need to access the private default constructor.
  template <class T> friend class CppVtableTesterA;
  template <class T> friend class CppVtableTesterB;
  template <class T> friend class CppVtableCloner;

  MethodTrainingData* _method;
  const short _level;
  const int _compile_id;

  // classes that should be initialized before this JIT task runs
  DepList<KlassTrainingData*> _init_deps;
  // Number of uninitialized classes left, when it's 0, all deps are satisfied
  volatile int _init_deps_left;

public:
  // ciRecords is a generic meachanism to memoize CI responses to arbitary queries. For each function we're interested in we record
  // (return_value, argument_values) tuples in a list. Arguments are allowed to have Metaspace pointers in them.
  class ciRecords {
    template <typename... Ts> class Arguments {
    public:
      bool operator==(const Arguments<>&) const { return true; }
      void metaspace_pointers_do(MetaspaceClosure *iter) { }
    };
    template <typename T, typename... Ts> class Arguments<T, Ts...> {
    private:
      T _first;
      Arguments<Ts...> _remaining;

    public:
      constexpr Arguments(const T& first, const Ts&... remaining) noexcept
        : _first(first), _remaining(remaining...) {}
      constexpr Arguments() noexcept : _first(), _remaining() {}
      bool operator==(const Arguments<T, Ts...>& that) const {
        return _first == that._first && _remaining == that._remaining;
      }
      template<typename U = T, ENABLE_IF(std::is_pointer<U>::value && std::is_base_of<MetaspaceObj, typename std::remove_pointer<U>::type>::value)>
      void metaspace_pointers_do(MetaspaceClosure *iter) {
        iter->push(&_first);
        _remaining.metaspace_pointers_do(iter);
      }
      template<typename U = T, ENABLE_IF(!(std::is_pointer<U>::value && std::is_base_of<MetaspaceObj, typename std::remove_pointer<U>::type>::value))>
      void metaspace_pointers_do(MetaspaceClosure *iter) {
        _remaining.metaspace_pointers_do(iter);
      }
    };

    template <typename ReturnType, typename... Args> class ciMemoizedFunction : public StackObj {
    public:
      class OptionalReturnType {
        bool _valid;
        ReturnType _result;
      public:
        OptionalReturnType(bool valid, const ReturnType& result) : _valid(valid), _result(result) {}
        bool is_valid() const { return _valid; }
        ReturnType result() const { return _result; }
      };
    private:
      typedef Arguments<Args...> ArgumentsType;
      class Record : public MetaspaceObj {
        ReturnType    _result;
        ArgumentsType _arguments;
      public:
        Record(const ReturnType& result, const ArgumentsType& arguments) : _result(result), _arguments(arguments) {}
        Record() { }
        ReturnType result() const { return _result; }
        ArgumentsType arguments() const { return _arguments; }
        bool operator==(const Record& that) { return _arguments == that._arguments; }
        void metaspace_pointers_do(MetaspaceClosure *iter) { _arguments.metaspace_pointers_do(iter); }
      };
      DepList<Record> _data;
    public:
      OptionalReturnType find(const Args&... args) {
        ArgumentsType a(args...);
        for (int i = 0; i < _data.length(); i++) {
          if (_data.at(i).arguments() == a) {
            return OptionalReturnType(true, _data.at(i).result());
          }
        }
        return OptionalReturnType(false, ReturnType());
      }
      bool append_if_missing(const ReturnType& result, const Args&... args) {
        return _data.append_if_missing(Record(result, ArgumentsType(args...)));
      }
#if INCLUDE_CDS
      void remove_unshareable_info() { _data.remove_unshareable_info(); }
#endif
      void prepare(ClassLoaderData* loader_data) {
        _data.prepare(loader_data);
      }
      void metaspace_pointers_do(MetaspaceClosure *iter) {
        _data.metaspace_pointers_do(iter);
      }
    };


public:
    // Record CI answers for the InlineSmallCode heuristic. It is importance since the heuristic is non-commutative and we may want to
    // compile methods in a different order than in the training run.
    typedef ciMemoizedFunction<int, MethodTrainingData*> ciMethod__inline_instructions_size_type;
    ciMethod__inline_instructions_size_type ciMethod__inline_instructions_size;
#if INCLUDE_CDS
    void remove_unshareable_info() {
      ciMethod__inline_instructions_size.remove_unshareable_info();
    }
#endif
    void prepare(ClassLoaderData* loader_data) {
      ciMethod__inline_instructions_size.prepare(loader_data);
    }
    void metaspace_pointers_do(MetaspaceClosure *iter) {
      ciMethod__inline_instructions_size.metaspace_pointers_do(iter);
    }
  };

private:
  ciRecords _ci_records;

  CompileTrainingData();
  CompileTrainingData(MethodTrainingData* mtd,
                      int level,
                      int compile_id)
      : TrainingData(),  // empty key
        _method(mtd), _level(level), _compile_id(compile_id), _init_deps_left(0) { }
public:
  ciRecords& ci_records() { return _ci_records; }
  static CompileTrainingData* make(CompileTask* task) NOT_CDS_RETURN_(nullptr);

  virtual CompileTrainingData* as_CompileTrainingData() const { return const_cast<CompileTrainingData*>(this); };

  MethodTrainingData* method() const { return _method; }

  int level() const { return _level; }

  int compile_id() const { return _compile_id; }

  int init_dep_count() const {
    TrainingDataLocker::assert_locked();
    return _init_deps.length();
  }
  KlassTrainingData* init_dep(int i) const {
    TrainingDataLocker::assert_locked();
    return _init_deps.at(i);
  }
  void add_init_dep(KlassTrainingData* ktd) {
    TrainingDataLocker::assert_locked();
    ktd->add_comp_dep(this);
    _init_deps.append_if_missing(ktd);
  }
  void clear_init_deps() {
    TrainingDataLocker::assert_locked();
    for (int i = 0; i < _init_deps.length(); i++) {
      _init_deps.at(i)->remove_comp_dep(this);
    }
    _init_deps.clear();
  }
  void dec_init_deps_left(KlassTrainingData* ktd);
  int init_deps_left() const {
    return Atomic::load(&_init_deps_left);
  }
  uint compute_init_deps_left(bool count_initialized = false);

  void notice_inlined_method(CompileTask* task, const methodHandle& method) NOT_CDS_RETURN;

  // The JIT looks at classes and objects too and can depend on their state.
  // These simple calls just report the *possibility* of an observation.
  void notice_jit_observation(ciEnv* env, ciBaseObject* what) NOT_CDS_RETURN;

  virtual void prepare(Visitor& visitor);
  virtual void cleanup(Visitor& visitor) NOT_CDS_RETURN;

  void print_on(outputStream* st, bool name_only) const;
  virtual void print_on(outputStream* st) const { print_on(st, false); }
  virtual void print_value_on(outputStream* st) const { print_on(st, true); }

#if INCLUDE_CDS
  virtual void remove_unshareable_info();
#endif

  virtual void metaspace_pointers_do(MetaspaceClosure* iter);
  virtual MetaspaceObj::Type type() const { return CompileTrainingDataType; }

  virtual const char* internal_name() const {
    return "{ compile training data }";
  };

  virtual int size() const {
    return (int)align_metadata_size(align_up(sizeof(CompileTrainingData), BytesPerWord)/BytesPerWord);
  }

  void verify();

  static CompileTrainingData* allocate(MethodTrainingData* mtd, int level, int compile_id) {
    return TrainingData::allocate<CompileTrainingData>(mtd, level, compile_id);
  }
};

// Record information about a method at the time compilation is requested.
class MethodTrainingData : public TrainingData {
  friend TrainingData;
  friend CompileTrainingData;

  // Used by CDS. These classes need to access the private default constructor.
  template <class T> friend class CppVtableTesterA;
  template <class T> friend class CppVtableTesterB;
  template <class T> friend class CppVtableCloner;

  KlassTrainingData* _klass;
  Method* _holder;
  CompileTrainingData* _last_toplevel_compiles[CompLevel_count - 1];
  int _highest_top_level;
  int _level_mask;  // bit-set of all possible levels
  bool _was_toplevel;
  // metadata snapshots of final state:
  MethodCounters* _final_counters;
  MethodData*     _final_profile;

  MethodTrainingData();
  MethodTrainingData(Method* method, KlassTrainingData* ktd) : TrainingData(method) {
    _klass = ktd;
    _holder = method;
    for (int i = 0; i < CompLevel_count - 1; i++) {
      _last_toplevel_compiles[i] = nullptr;
    }
    _highest_top_level = CompLevel_none;
    _level_mask = 0;
    _was_toplevel = false;
  }

  static int level_mask(int level) {
    return ((level & 0xF) != level ? 0 : 1 << level);
  }

 public:
  KlassTrainingData* klass()  const { return _klass; }
  bool has_holder()           const { return _holder != nullptr; }
  Method* holder()            const { return _holder; }
  bool only_inlined()         const { return !_was_toplevel; }
  bool saw_level(CompLevel l) const { return (_level_mask & level_mask(l)) != 0; }
  int highest_top_level()     const { return _highest_top_level; }
  MethodData* final_profile() const { return _final_profile; }

  Symbol* name() const {
    precond(has_holder());
    return holder()->name();
  }
  Symbol* signature() const {
    precond(has_holder());
    return holder()->signature();
  }

  CompileTrainingData* last_toplevel_compile(int level) const {
    if (level > CompLevel_none) {
      return _last_toplevel_compiles[level - 1];
    }
    return nullptr;
  }

  void notice_compilation(int level, bool inlined = false) {
    if (!inlined) {
      _was_toplevel = true;
    }
    _level_mask |= level_mask(level);
  }

  void notice_toplevel_compilation(int level) {
    _highest_top_level = MAX2(_highest_top_level, level);
  }

  static MethodTrainingData* make(const methodHandle& method,
                                  bool null_if_not_found = false,
                                  bool use_cache = true) NOT_CDS_RETURN_(nullptr);
  static MethodTrainingData* find_fast(const methodHandle& method) { return make(method, true, true); }
  static MethodTrainingData* find(const methodHandle& method) { return make(method, true, false); }

  virtual MethodTrainingData* as_MethodTrainingData() const {
    return const_cast<MethodTrainingData*>(this);
  };

  void print_on(outputStream* st, bool name_only) const;
  virtual void print_on(outputStream* st) const { print_on(st, false); }
  virtual void print_value_on(outputStream* st) const { print_on(st, true); }

  virtual void prepare(Visitor& visitor);
  virtual void cleanup(Visitor& visitor) NOT_CDS_RETURN;

  template<typename Function>
  void iterate_compiles(Function fn) const { // lambda enabled API
    for (int i = 0; i < CompLevel_count - 1; i++) {
      CompileTrainingData* ctd = _last_toplevel_compiles[i];
      if (ctd != nullptr) {
        fn(ctd);
      }
    }
  }

  virtual void metaspace_pointers_do(MetaspaceClosure* iter);
  virtual MetaspaceObj::Type type() const { return MethodTrainingDataType; }

#if INCLUDE_CDS
  virtual void remove_unshareable_info();
#endif

  virtual int size() const {
    return (int)align_metadata_size(align_up(sizeof(MethodTrainingData), BytesPerWord)/BytesPerWord);
  }

  virtual const char* internal_name() const {
    return "{ method training data }";
  };

  void verify();

  static MethodTrainingData* allocate(Method* m, KlassTrainingData* ktd) {
    return TrainingData::allocate<MethodTrainingData>(m, ktd);
  }
};
#endif // SHARE_OOPS_TRAININGDATA_HPP
