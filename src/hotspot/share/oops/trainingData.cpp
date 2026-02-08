/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotCompressedPointers.hpp"
#include "cds/cdsConfig.hpp"
#include "ci/ciEnv.hpp"
#include "ci/ciMetadata.hpp"
#include "classfile/compactHashtable.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "compiler/compileTask.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/method.hpp"
#include "oops/method.inline.hpp"
#include "oops/methodCounters.hpp"
#include "oops/trainingData.hpp"
#include "runtime/arguments.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "utilities/growableArray.hpp"

TrainingData::TrainingDataSet TrainingData::_training_data_set(1024, 0x3fffffff);
TrainingData::TrainingDataDictionary TrainingData::_archived_training_data_dictionary;
TrainingData::TrainingDataDictionary TrainingData::_archived_training_data_dictionary_for_dumping;
TrainingData::DumptimeTrainingDataDictionary* TrainingData::_dumptime_training_data_dictionary = nullptr;
int TrainingData::TrainingDataLocker::_lock_mode;
volatile bool TrainingData::TrainingDataLocker::_snapshot = false;

MethodTrainingData::MethodTrainingData() {
  // Used by cppVtables.cpp only
  assert(CDSConfig::is_dumping_static_archive() || UseSharedSpaces, "only for CDS");
}

KlassTrainingData::KlassTrainingData() {
  // Used by cppVtables.cpp only
  assert(CDSConfig::is_dumping_static_archive() || UseSharedSpaces, "only for CDS");
}

CompileTrainingData::CompileTrainingData() : _level(-1), _compile_id(-1) {
  // Used by cppVtables.cpp only
  assert(CDSConfig::is_dumping_static_archive() || UseSharedSpaces, "only for CDS");
}

void TrainingData::initialize() {
  // this is a nop if training modes are not enabled
  if (have_data() || need_data()) {
    // Data structures that we have do not currently support iterative training. So you cannot replay
    // and train at the same time. Going forward we may want to adjust iteration/search to enable that.
    guarantee(have_data() != need_data(), "Iterative training is not supported");
    TrainingDataLocker::initialize();
  }
}

static void verify_archived_entry(TrainingData* td, const TrainingData::Key* k) {
  guarantee(TrainingData::Key::can_compute_cds_hash(k), "");
  TrainingData* td1 = TrainingData::lookup_archived_training_data(k);
  guarantee(td == td1, "");
}

void TrainingData::verify() {
  if (TrainingData::have_data() && !TrainingData::assembling_data()) {
    archived_training_data_dictionary()->iterate_all([&](TrainingData* td) {
      if (td->is_KlassTrainingData()) {
        KlassTrainingData* ktd = td->as_KlassTrainingData();
        if (ktd->has_holder() && ktd->holder()->is_loaded()) {
          Key k(ktd->holder());
          verify_archived_entry(td, &k);
        }
        ktd->verify();
      } else if (td->is_MethodTrainingData()) {
        MethodTrainingData* mtd = td->as_MethodTrainingData();
        if (mtd->has_holder() && mtd->holder()->method_holder()->is_loaded()) {
          Key k(mtd->holder());
          verify_archived_entry(td, &k);
        }
        mtd->verify(/*verify_dep_counter*/true);
      }
    });
  }
  if (TrainingData::need_data()) {
    TrainingDataLocker l;
    training_data_set()->iterate([&](TrainingData* td) {
      if (td->is_KlassTrainingData()) {
        KlassTrainingData* ktd = td->as_KlassTrainingData();
        ktd->verify();
      } else if (td->is_MethodTrainingData()) {
        MethodTrainingData* mtd = td->as_MethodTrainingData();
        // During the training run init deps tracking is not setup yet,
        // don't verify it.
        mtd->verify(/*verify_dep_counter*/false);
      }
    });
  }
}

MethodTrainingData* MethodTrainingData::make(const methodHandle& method, bool null_if_not_found, bool use_cache) {
  MethodTrainingData* mtd = nullptr;
  if (!have_data() && !need_data()) {
    return mtd;
  }
  // Try grabbing the cached value first.
  // Cache value is stored in MethodCounters and the following are the
  // possible states:
  // 1. Cached value is method_training_data_sentinel().
  //    This is an initial state and needs a full lookup.
  // 2. Cached value is null.
  //    Lookup failed the last time, if we don't plan to create a new TD object,
  //    i.e. null_if_no_found == true, then just return a null.
  // 3. Cache value is not null.
  //    Return it, the value of training_data_lookup_failed doesn't matter.
  MethodCounters* mcs = method->method_counters();
  if (mcs != nullptr) {
    mtd = mcs->method_training_data();
    if (mtd != nullptr && mtd != mcs->method_training_data_sentinel()) {
      return mtd;
    }
    if (null_if_not_found && mtd == nullptr) {
      assert(mtd == nullptr, "No training data found");
      return nullptr;
    }
  } else if (use_cache) {
    mcs = Method::build_method_counters(Thread::current(), method());
  }

  TrainingData* td = nullptr;

  Key key(method());
  if (have_data()) {
    td = lookup_archived_training_data(&key);
    if (td != nullptr) {
      mtd = td->as_MethodTrainingData();
    } else {
      mtd = nullptr;
    }
    // Cache the pointer to MTD in MethodCounters for faster lookup (could be null if not found)
    method->init_training_data(mtd);
  }

  if (need_data()) {
    TrainingDataLocker l;
    td = training_data_set()->find(&key);
    if (td == nullptr) {
      if (!null_if_not_found) {
        KlassTrainingData* ktd = KlassTrainingData::make(method->method_holder());
        if (ktd == nullptr) {
          return nullptr; // allocation failure
        }
        mtd = MethodTrainingData::allocate(method(), ktd);
        if (mtd == nullptr) {
          return nullptr; // allocation failure
        }
        td = training_data_set()->install(mtd);
        assert(td == mtd, "");
      } else {
        mtd = nullptr;
      }
    } else {
      mtd = td->as_MethodTrainingData();
    }
    // Cache the pointer to MTD in MethodCounters for faster lookup (could be null if not found)
    method->init_training_data(mtd);
  }

  return mtd;
}

void MethodTrainingData::print_on(outputStream* st, bool name_only) const {
  if (has_holder()) {
    _klass->print_on(st, true);
    st->print(".");
    name()->print_symbol_on(st);
    signature()->print_symbol_on(st);
  }
  if (name_only) {
    return;
  }
  if (!has_holder()) {
    st->print("[SYM]");
  }
  if (_level_mask) {
    st->print(" LM%d", _level_mask);
  }
  st->print(" mc=%p mdo=%p", _final_counters, _final_profile);
}

CompileTrainingData* CompileTrainingData::make(CompileTask* task) {
  int level = task->comp_level();
  int compile_id = task->compile_id();
  Thread* thread = Thread::current();
  methodHandle m(thread, task->method());
  if (m->method_holder() == nullptr) {
    return nullptr; // do not record (dynamically generated method)
  }
  MethodTrainingData* mtd = MethodTrainingData::make(m);
  if (mtd == nullptr) {
    return nullptr; // allocation failure
  }
  mtd->notice_compilation(level);

  TrainingDataLocker l;
  CompileTrainingData* ctd = CompileTrainingData::allocate(mtd, level, compile_id);
  if (ctd != nullptr) {
    CompileTrainingData*& last_ctd = mtd->_last_toplevel_compiles[level - 1];
    if (last_ctd != nullptr) {
      assert(mtd->highest_top_level() >= level, "consistency");
      if (last_ctd->compile_id() < compile_id) {
        last_ctd->clear_init_deps();
        last_ctd = ctd;
      }
    } else {
      last_ctd = ctd;
      mtd->notice_toplevel_compilation(level);
    }
  }
  return ctd;
}


void CompileTrainingData::dec_init_deps_left_release(KlassTrainingData* ktd) {
  LogStreamHandle(Trace, training) log;
  if (log.is_enabled()) {
    log.print("CTD "); print_on(&log); log.cr();
    log.print("KTD "); ktd->print_on(&log); log.cr();
  }
  assert(ktd!= nullptr && ktd->has_holder(), "");
  assert(_init_deps.contains(ktd), "");
  assert(_init_deps_left > 0, "");

  uint init_deps_left1 = AtomicAccess::sub(&_init_deps_left, 1);

  if (log.is_enabled()) {
    uint init_deps_left2 = compute_init_deps_left();
    log.print("init_deps_left: %d (%d)", init_deps_left1, init_deps_left2);
    ktd->print_on(&log, true);
  }
}

uint CompileTrainingData::compute_init_deps_left(bool count_initialized) {
  int left = 0;
  for (int i = 0; i < _init_deps.length(); i++) {
    KlassTrainingData* ktd = _init_deps.at(i);
    // Ignore symbolic refs and already initialized classes (unless explicitly requested).
    if (ktd->has_holder()) {
      InstanceKlass* holder = ktd->holder();
      if (!ktd->holder()->is_initialized() || count_initialized) {
        ++left;
      } else if (holder->defined_by_other_loaders()) {
        Key k(holder);
        if (CDS_ONLY(!Key::can_compute_cds_hash(&k)) NOT_CDS(true)) {
          ++left;
        }
      }
    }
  }
  return left;
}

void CompileTrainingData::print_on(outputStream* st, bool name_only) const {
  _method->print_on(st, true);
  st->print("#%dL%d", _compile_id, _level);
  if (name_only) {
    return;
  }
  if (_init_deps.length() > 0) {
    if (_init_deps_left > 0) {
      st->print(" udeps=%d", _init_deps_left);
    }
    for (int i = 0, len = _init_deps.length(); i < len; i++) {
      st->print(" dep:");
      _init_deps.at(i)->print_on(st, true);
    }
  }
}

void CompileTrainingData::notice_inlined_method(CompileTask* task,
                                                const methodHandle& method) {
  MethodTrainingData* mtd = MethodTrainingData::make(method);
  if (mtd != nullptr) {
    mtd->notice_compilation(task->comp_level(), true);
  }
}

void CompileTrainingData::notice_jit_observation(ciEnv* env, ciBaseObject* what) {
  // A JIT is starting to look at class k.
  // We could follow the queries that it is making, but it is
  // simpler to assume, conservatively, that the JIT will
  // eventually depend on the initialization state of k.
  CompileTask* task = env->task();
  assert(task != nullptr, "");
  Method* method = task->method();
  if (what->is_metadata()) {
    ciMetadata* md = what->as_metadata();
    if (md->is_loaded() && md->is_instance_klass()) {
      ciInstanceKlass* cik = md->as_instance_klass();

      if (cik->is_initialized()) {
        InstanceKlass* ik = md->as_instance_klass()->get_instanceKlass();
        KlassTrainingData* ktd = KlassTrainingData::make(ik);
        if (ktd == nullptr) {
          // Allocation failure or snapshot in progress
          return;
        }
        // This JIT task is (probably) requesting that ik be initialized,
        // so add him to my _init_deps list.
        TrainingDataLocker l;
        if (l.can_add()) {
          add_init_dep(ktd);
        }
      }
    }
  }
}

void KlassTrainingData::prepare(Visitor& visitor) {
  if (visitor.is_visited(this)) {
    return;
  }
  visitor.visit(this);
  _comp_deps.prepare();
}

void MethodTrainingData::prepare(Visitor& visitor) {
  if (visitor.is_visited(this)) {
    return;
  }
  visitor.visit(this);
  klass()->prepare(visitor);
  if (has_holder()) {
    _final_counters = holder()->method_counters();
    _final_profile  = holder()->method_data();
    assert(_final_profile == nullptr || _final_profile->method() == holder(), "");
    _invocation_count = holder()->invocation_count();
    _backedge_count = holder()->backedge_count();
  }
  for (int i = 0; i < CompLevel_count - 1; i++) {
    CompileTrainingData* ctd = _last_toplevel_compiles[i];
    if (ctd != nullptr) {
      ctd->prepare(visitor);
    }
  }
}

void CompileTrainingData::prepare(Visitor& visitor) {
  if (visitor.is_visited(this)) {
    return;
  }
  visitor.visit(this);
  method()->prepare(visitor);
  _init_deps.prepare();
  _ci_records.prepare();
}

KlassTrainingData* KlassTrainingData::make(InstanceKlass* holder, bool null_if_not_found) {
  Key key(holder);
  TrainingData* td = CDS_ONLY(have_data() ? lookup_archived_training_data(&key) :) nullptr;
  KlassTrainingData* ktd = nullptr;
  if (td != nullptr) {
    ktd = td->as_KlassTrainingData();
    guarantee(!ktd->has_holder() || ktd->holder() == holder, "");
    if (ktd->has_holder()) {
      return ktd;
    } else {
      ktd = nullptr;
    }
  }
  if (need_data()) {
    TrainingDataLocker l;
    td = training_data_set()->find(&key);
    if (td == nullptr) {
      if (null_if_not_found) {
        return nullptr;
      }
      ktd = KlassTrainingData::allocate(holder);
      if (ktd == nullptr) {
        return nullptr; // allocation failure
      }
      td = training_data_set()->install(ktd);
      assert(ktd == td, "");
    } else {
      ktd = td->as_KlassTrainingData();
      guarantee(ktd->holder() != nullptr, "null holder");
    }
    assert(ktd != nullptr, "");
    guarantee(ktd->holder() == holder, "");
  }
  return ktd;
}

void KlassTrainingData::print_on(outputStream* st, bool name_only) const {
  if (has_holder()) {
    name()->print_symbol_on(st);
    switch (holder()->init_state()) {
      case InstanceKlass::allocated:            st->print("[A]"); break;
      case InstanceKlass::loaded:               st->print("[D]"); break;
      case InstanceKlass::linked:               st->print("[L]"); break;
      case InstanceKlass::being_initialized:    st->print("[i]"); break;
      case InstanceKlass::fully_initialized:                      break;
      case InstanceKlass::initialization_error: st->print("[E]"); break;
      default: fatal("unknown state: %d", holder()->init_state());
    }
    if (holder()->is_interface()) {
      st->print("I");
    }
  } else {
    st->print("[SYM]");
  }
  if (name_only) {
    return;
  }
  if (_comp_deps.length() > 0) {
    for (int i = 0, len = _comp_deps.length(); i < len; i++) {
      st->print(" dep:");
      _comp_deps.at(i)->print_on(st, true);
    }
  }
}

KlassTrainingData::KlassTrainingData(InstanceKlass* klass) : TrainingData(klass) {
  assert(klass != nullptr, "");
  // The OopHandle constructor will allocate a handle. We don't need to ever release it so we don't preserve
  // the handle object.
  OopHandle handle(Universe::vm_global(), klass->java_mirror());
  _holder = klass;
  assert(holder() == klass, "");
}

void KlassTrainingData::notice_fully_initialized() {
  ResourceMark rm;
  assert(has_holder(), "");
  assert(holder()->is_initialized(), "wrong state: %s %s",
         holder()->name()->as_C_string(), holder()->init_state_name());

  TrainingDataLocker l; // Not a real lock if we don't collect the data,
                        // that's why we need the atomic decrement below.
  for (int i = 0; i < comp_dep_count(); i++) {
    comp_dep(i)->dec_init_deps_left_release(this);
  }
  holder()->set_has_init_deps_processed();
}

void TrainingData::init_dumptime_table(TRAPS) {
  precond((!assembling_data() && !need_data()) || need_data() != assembling_data());
  if (assembling_data()) {
    _dumptime_training_data_dictionary = new DumptimeTrainingDataDictionary();
    _archived_training_data_dictionary.iterate_all([&](TrainingData* record) {
      _dumptime_training_data_dictionary->append(record);
    });
  }
  if (need_data()) {
    _dumptime_training_data_dictionary = new DumptimeTrainingDataDictionary();
    TrainingDataLocker l;
    TrainingDataLocker::snapshot();
    ResourceMark rm;
    Visitor visitor(training_data_set()->size());
    training_data_set()->iterate([&](TrainingData* td) {
      td->prepare(visitor);
      if (!td->is_CompileTrainingData()) {
        _dumptime_training_data_dictionary->append(td);
      }
    });
  }

  if (AOTVerifyTrainingData) {
    TrainingData::verify();
  }
}

void TrainingData::iterate_roots(MetaspaceClosure* it) {
  if (_dumptime_training_data_dictionary != nullptr) {
    for (int i = 0; i < _dumptime_training_data_dictionary->length(); i++) {
      _dumptime_training_data_dictionary->at(i).metaspace_pointers_do(it);
    }
  }
}

void TrainingData::dump_training_data() {
  if (_dumptime_training_data_dictionary != nullptr) {
    CompactHashtableStats stats;
    _archived_training_data_dictionary_for_dumping.reset();
    CompactHashtableWriter writer(_dumptime_training_data_dictionary->length(), &stats);
    for (int i = 0; i < _dumptime_training_data_dictionary->length(); i++) {
      TrainingData* td = _dumptime_training_data_dictionary->at(i).training_data();
#ifdef ASSERT
      for (int j = i+1; j < _dumptime_training_data_dictionary->length(); j++) {
        TrainingData* td1 = _dumptime_training_data_dictionary->at(j).training_data();
        assert(!TrainingData::Key::equals(td1, td->key(), -1), "conflict");
      }
#endif // ASSERT
      td = ArchiveBuilder::current()->get_buffered_addr(td);
      uint hash = TrainingData::Key::cds_hash(td->key());
      writer.add(hash, AOTCompressedPointers::encode_not_null(td));
    }
    writer.dump(&_archived_training_data_dictionary_for_dumping, "training data dictionary");
  }
}

void TrainingData::cleanup_training_data() {
  if (_dumptime_training_data_dictionary != nullptr) {
    ResourceMark rm;
    Visitor visitor(_dumptime_training_data_dictionary->length());
    for (int i = 0; i < _dumptime_training_data_dictionary->length(); i++) {
      TrainingData* td = _dumptime_training_data_dictionary->at(i).training_data();
      td->cleanup(visitor);
    }
    // Throw away all elements with empty keys
    int j = 0;
    for (int i = 0; i < _dumptime_training_data_dictionary->length(); i++) {
      TrainingData* td = _dumptime_training_data_dictionary->at(i).training_data();
      if (td->key()->is_empty()) {
        continue;
      }
      if (i != j) { // no need to copy if it's the same
        _dumptime_training_data_dictionary->at_put(j, td);
      }
      j++;
    }
    _dumptime_training_data_dictionary->trunc_to(j);
  }
}

void KlassTrainingData::cleanup(Visitor& visitor) {
  if (visitor.is_visited(this)) {
    return;
  }
  visitor.visit(this);
  if (has_holder()) {
    bool is_excluded = !holder()->is_loaded();
    if (CDSConfig::is_at_aot_safepoint()) {
      // Check for AOT exclusion only at AOT safe point.
      is_excluded |= SystemDictionaryShared::should_be_excluded(holder());
    }
    if (is_excluded) {
      ResourceMark rm;
      log_debug(aot, training)("Cleanup KTD %s", name()->as_klass_external_name());
      _holder = nullptr;
      key()->make_empty();
    }
  }
  for (int i = 0; i < _comp_deps.length(); i++) {
    _comp_deps.at(i)->cleanup(visitor);
  }
}

void MethodTrainingData::cleanup(Visitor& visitor) {
  if (visitor.is_visited(this)) {
    return;
  }
  visitor.visit(this);
  if (has_holder()) {
    if (CDSConfig::is_at_aot_safepoint() && SystemDictionaryShared::should_be_excluded(holder()->method_holder())) {
      // Check for AOT exclusion only at AOT safe point.
      log_debug(aot, training)("Cleanup MTD %s::%s", name()->as_klass_external_name(), signature()->as_utf8());
      if (_final_profile != nullptr && _final_profile->method() != _holder) {
        log_warning(aot, training)("Stale MDO for  %s::%s", name()->as_klass_external_name(), signature()->as_utf8());
      }
      _final_profile = nullptr;
      _final_counters = nullptr;
      _holder = nullptr;
      key()->make_empty();
    }
  }
  for (int i = 0; i < CompLevel_count - 1; i++) {
    CompileTrainingData* ctd = _last_toplevel_compiles[i];
    if (ctd != nullptr) {
      ctd->cleanup(visitor);
    }
  }
}

void KlassTrainingData::verify() {
  for (int i = 0; i < comp_dep_count(); i++) {
    CompileTrainingData* ctd = comp_dep(i);
    if (!ctd->_init_deps.contains(this)) {
      print_on(tty); tty->cr();
      ctd->print_on(tty); tty->cr();
    }
    guarantee(ctd->_init_deps.contains(this), "");
  }
}

void MethodTrainingData::verify(bool verify_dep_counter) {
  iterate_compiles([&](CompileTrainingData* ctd) {
    ctd->verify(verify_dep_counter);
  });
}

void CompileTrainingData::verify(bool verify_dep_counter) {
  for (int i = 0; i < init_dep_count(); i++) {
    KlassTrainingData* ktd = init_dep(i);
    if (ktd->has_holder() && ktd->holder()->defined_by_other_loaders()) {
      LogStreamHandle(Info, training) log;
      if (log.is_enabled()) {
        ResourceMark rm;
        log.print("CTD "); print_value_on(&log);
        log.print(" depends on unregistered class %s", ktd->holder()->name()->as_C_string());
      }
    }
    if (!ktd->_comp_deps.contains(this)) {
      print_on(tty); tty->cr();
      ktd->print_on(tty); tty->cr();
    }
    guarantee(ktd->_comp_deps.contains(this), "");
  }

  if (verify_dep_counter) {
    int init_deps_left1 = init_deps_left_acquire();
    int init_deps_left2 = compute_init_deps_left();

    bool invariant = (init_deps_left1 >= init_deps_left2);
    if (!invariant) {
      print_on(tty);
      tty->cr();
    }
    guarantee(invariant, "init deps invariant violation: %d >= %d", init_deps_left1, init_deps_left2);
  }
}

void CompileTrainingData::cleanup(Visitor& visitor) {
  if (visitor.is_visited(this)) {
    return;
  }
  visitor.visit(this);
  method()->cleanup(visitor);
}

void TrainingData::serialize(SerializeClosure* soc) {
  if (soc->writing()) {
    _archived_training_data_dictionary_for_dumping.serialize_header(soc);
  } else {
    _archived_training_data_dictionary.serialize_header(soc);
  }
}

class TrainingDataPrinter : StackObj {
  outputStream* _st;
  int _index;
public:
  TrainingDataPrinter(outputStream* st) : _st(st), _index(0) {}
  void do_value(TrainingData* td) {
    const char* type = (td->is_KlassTrainingData()   ? "K" :
                        td->is_MethodTrainingData()  ? "M" :
                        td->is_CompileTrainingData() ? "C" : "?");
    _st->print("%4d: %p %s ", _index++, td, type);
    td->print_on(_st);
    _st->cr();
    if (td->is_KlassTrainingData()) {
      td->as_KlassTrainingData()->iterate_comp_deps([&](CompileTrainingData* ctd) {
        ResourceMark rm;
        _st->print_raw("  C ");
        ctd->print_on(_st);
        _st->cr();
      });
    } else if (td->is_MethodTrainingData()) {
      td->as_MethodTrainingData()->iterate_compiles([&](CompileTrainingData* ctd) {
        ResourceMark rm;
        _st->print_raw("  C ");
        ctd->print_on(_st);
        _st->cr();
      });
    } else if (td->is_CompileTrainingData()) {
      // ?
    }
  }
};

void TrainingData::print_archived_training_data_on(outputStream* st) {
  st->print_cr("Archived TrainingData Dictionary");
  TrainingDataPrinter tdp(st);
  TrainingDataLocker::initialize();
  _archived_training_data_dictionary.iterate_all(&tdp);
}

void TrainingData::Key::metaspace_pointers_do(MetaspaceClosure *iter) {
  iter->push(const_cast<Metadata**>(&_meta));
}

void TrainingData::metaspace_pointers_do(MetaspaceClosure* iter) {
  _key.metaspace_pointers_do(iter);
}

bool TrainingData::Key::can_compute_cds_hash(const Key* const& k) {
  return k->meta() == nullptr || MetaspaceObj::in_aot_cache(k->meta());
}

uint TrainingData::Key::cds_hash(const Key* const& k) {
  return SystemDictionaryShared::hash_for_shared_dictionary((address)k->meta());
}

TrainingData* TrainingData::lookup_archived_training_data(const Key* k) {
  // For this to work, all components of the key must be in shared metaspace.
  if (!TrainingData::Key::can_compute_cds_hash(k) || _archived_training_data_dictionary.empty()) {
    return nullptr;
  }
  uint hash = TrainingData::Key::cds_hash(k);
  TrainingData* td = _archived_training_data_dictionary.lookup(k, hash, -1 /*unused*/);
  if (td != nullptr) {
    if ((td->is_KlassTrainingData()  && td->as_KlassTrainingData()->has_holder()) ||
        (td->is_MethodTrainingData() && td->as_MethodTrainingData()->has_holder())) {
      return td;
    } else {
      ShouldNotReachHere();
    }
  }
  return nullptr;
}

template <typename T>
void TrainingData::DepList<T>::metaspace_pointers_do(MetaspaceClosure* iter) {
  iter->push(&_deps);
}

void KlassTrainingData::metaspace_pointers_do(MetaspaceClosure* iter) {
  log_trace(aot, training)("Iter(KlassTrainingData): %p", this);
  TrainingData::metaspace_pointers_do(iter);
  _comp_deps.metaspace_pointers_do(iter);
  iter->push(&_holder);
}

void MethodTrainingData::metaspace_pointers_do(MetaspaceClosure* iter) {
  log_trace(aot, training)("Iter(MethodTrainingData): %p", this);
  TrainingData::metaspace_pointers_do(iter);
  iter->push(&_klass);
  iter->push((Method**)&_holder);
  for (int i = 0; i < CompLevel_count - 1; i++) {
    iter->push(&_last_toplevel_compiles[i]);
  }
  iter->push(&_final_profile);
  iter->push(&_final_counters);
}

void CompileTrainingData::metaspace_pointers_do(MetaspaceClosure* iter) {
  log_trace(aot, training)("Iter(CompileTrainingData): %p", this);
  TrainingData::metaspace_pointers_do(iter);
  _init_deps.metaspace_pointers_do(iter);
  _ci_records.metaspace_pointers_do(iter);
  iter->push(&_method);
}

template <typename T>
void TrainingData::DepList<T>::prepare() {
  if (_deps == nullptr && _deps_dyn != nullptr) {
    int len = _deps_dyn->length();
    _deps = MetadataFactory::new_array_from_c_heap<T>(len, mtClassShared);
    for (int i = 0; i < len; i++) {
      _deps->at_put(i, _deps_dyn->at(i)); // copy
    }
  }
}

void KlassTrainingData::remove_unshareable_info() {
  TrainingData::remove_unshareable_info();
  _comp_deps.remove_unshareable_info();
}

void MethodTrainingData::remove_unshareable_info() {
  TrainingData::remove_unshareable_info();
  if (_final_counters != nullptr) {
    _final_counters->remove_unshareable_info();
  }
  if (_final_profile != nullptr) {
    _final_profile->remove_unshareable_info();
  }
}

void CompileTrainingData::remove_unshareable_info() {
  TrainingData::remove_unshareable_info();
  _init_deps.remove_unshareable_info();
  _ci_records.remove_unshareable_info();
  _init_deps_left = compute_init_deps_left(true);
}
