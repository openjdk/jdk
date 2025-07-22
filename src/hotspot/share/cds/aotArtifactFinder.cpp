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

#include "cds/aotArtifactFinder.hpp"
#include "cds/aotClassInitializer.hpp"
#include "cds/aotClassLinker.hpp"
#include "cds/aotLogging.hpp"
#include "cds/aotReferenceObjSupport.hpp"
#include "cds/dumpTimeClassInfo.inline.hpp"
#include "cds/heapShared.hpp"
#include "cds/lambdaProxyClassDictionary.hpp"
#include "cds/regeneratedClasses.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "logging/log.hpp"
#include "memory/metaspaceClosure.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/trainingData.hpp"
#include "utilities/resourceHash.hpp"

// All the classes that should be included in the AOT cache (in at least the "allocated" state)
static GrowableArrayCHeap<Klass*, mtClassShared>* _all_cached_classes = nullptr;

// This is a stack that tracks all the AOT-inited classes that are waiting to be passed
// to HeapShared::copy_and_rescan_aot_inited_mirror().
static GrowableArrayCHeap<InstanceKlass*, mtClassShared>* _pending_aot_inited_classes = nullptr;

static const int TABLE_SIZE = 15889; // prime number
using ClassesTable = ResourceHashtable<Klass*, bool, TABLE_SIZE, AnyObj::C_HEAP, mtClassShared>;
static ClassesTable* _seen_classes;       // all classes that have been seen by AOTArtifactFinder
static ClassesTable* _aot_inited_classes; // all classes that need to be AOT-initialized.

void AOTArtifactFinder::initialize() {
  _all_cached_classes = new GrowableArrayCHeap<Klass*, mtClassShared>();
  _pending_aot_inited_classes = new GrowableArrayCHeap<InstanceKlass*, mtClassShared>();
  _seen_classes = new (mtClass)ClassesTable();
  _aot_inited_classes = new (mtClass)ClassesTable();
}

void AOTArtifactFinder::dispose() {
  delete _all_cached_classes;
  delete _seen_classes;
  delete _aot_inited_classes;
  delete _pending_aot_inited_classes;
  _all_cached_classes = nullptr;
  _seen_classes = nullptr;
  _aot_inited_classes = nullptr;
  _pending_aot_inited_classes = nullptr;
}

// Find all Klasses and oops that should be included in the AOT cache. See aotArtifactFinder.hpp
void AOTArtifactFinder::find_artifacts() {
  // Some classes might have been marked as excluded as a side effect of running
  // AOTConstantPoolResolver. Make sure we check all the remaining ones.
  //
  // Note, if a class is not excluded, it does NOT mean it will be automatically included
  // into the AOT cache -- that will be decided by the code below.
  SystemDictionaryShared::finish_exclusion_checks();
  AOTReferenceObjSupport::init_keep_alive_objs_table();

  start_scanning_for_oops();

  // Add the primitive array classes
  for (int i = T_BOOLEAN; i < T_VOID+1; i++) {
    BasicType bt = (BasicType)i;
    if (is_java_primitive(bt)) {
      add_cached_type_array_class(Universe::typeArrayKlass(bt));
    }
  }

#if INCLUDE_CDS_JAVA_HEAP
  // Add the mirrors that aren't associated with a Klass
  //    - primitive mirrors (E.g., "int.class" in Java code)
  //    - mirror of fillerArrayKlass
  if (CDSConfig::is_dumping_heap()) {
    for (int i = T_BOOLEAN; i < T_VOID+1; i++) {
      BasicType bt = (BasicType)i;
      if (!is_reference_type(bt)) {
        oop orig_mirror = Universe::java_mirror(bt);
        oop scratch_mirror = HeapShared::scratch_java_mirror(bt);
        HeapShared::scan_java_mirror(orig_mirror);
        log_trace(aot, heap, mirror)(
            "Archived %s mirror object from " PTR_FORMAT,
            type2name(bt), p2i(scratch_mirror));
        Universe::set_archived_basic_type_mirror_index(bt, HeapShared::append_root(scratch_mirror));
      }
    }

    // Universe::fillerArrayKlass() isn't in the class hierarchy, so handle it specially.
    HeapShared::scan_java_mirror(Universe::fillerArrayKlass()->java_mirror());
  }
#endif

  // Add all the InstanceKlasses (and their array classes) that are always included.
  SystemDictionaryShared::dumptime_table()->iterate_all_live_classes([&] (InstanceKlass* ik, DumpTimeClassInfo& info) {
    // Skip "AOT tooling classes" in this block. They will be included in the AOT cache only if
    // - One of their subtypes is included
    // - One of their instances is found by HeapShared.
    if (!info.is_excluded() && !info.is_aot_tooling_class()) {
      bool add = false;
      if (!ik->is_hidden()) {
        // All non-hidden classes are always included into the AOT cache
        add = true;
      } else {
        if (CDSConfig::is_dumping_lambdas_in_legacy_mode()) {
          // Legacy support of lambda proxies -- these are always included into the AOT cache
          if (LambdaProxyClassDictionary::is_registered_lambda_proxy_class(ik)) {
            add = true;
          }
        } else {
          assert(!LambdaProxyClassDictionary::is_registered_lambda_proxy_class(ik),
                 "registered lambda proxies are only for legacy lambda proxy support");
        }
      }

      if (add) {
        add_cached_instance_class(ik);
        if (AOTClassInitializer::can_archive_initialized_mirror(ik)) {
          add_aot_inited_class(ik);
        }
      }
    }
  });

#if INCLUDE_CDS_JAVA_HEAP
  // Keep scanning until we discover no more class that need to be AOT-initialized.
  if (CDSConfig::is_initing_classes_at_dump_time()) {
    while (_pending_aot_inited_classes->length() > 0) {
      InstanceKlass* ik = _pending_aot_inited_classes->pop();
      HeapShared::copy_and_rescan_aot_inited_mirror(ik);
    }
  }
#endif

  // Exclude all the (hidden) classes that have not been discovered by the code above.
  SystemDictionaryShared::dumptime_table()->iterate_all_live_classes([&] (InstanceKlass* k, DumpTimeClassInfo& info) {
    if (!info.is_excluded() && _seen_classes->get(k) == nullptr) {
      info.set_excluded();
      info.set_has_checked_exclusion();
      if (aot_log_is_enabled(Debug, aot)) {
        ResourceMark rm;
        aot_log_debug(aot)("Skipping %s: %s class", k->name()->as_C_string(),
                      k->is_hidden() ? "Unreferenced hidden" : "AOT tooling");
      }
    }
  });

  end_scanning_for_oops();

  TrainingData::cleanup_training_data();
}

void AOTArtifactFinder::start_scanning_for_oops() {
#if INCLUDE_CDS_JAVA_HEAP
  if (CDSConfig::is_dumping_heap()) {
    HeapShared::start_scanning_for_oops();
  }
#endif
}

void AOTArtifactFinder::end_scanning_for_oops() {
#if INCLUDE_CDS_JAVA_HEAP
  if (CDSConfig::is_dumping_heap()) {
    HeapShared::end_scanning_for_oops();
  }
#endif
}

void AOTArtifactFinder::add_aot_inited_class(InstanceKlass* ik) {
  if (CDSConfig::is_initing_classes_at_dump_time()) {
    if (RegeneratedClasses::is_regenerated_object(ik)) {
      precond(RegeneratedClasses::get_original_object(ik)->is_initialized());
    } else {
      precond(ik->is_initialized());
    }
    add_cached_instance_class(ik);

    bool created;
    _aot_inited_classes->put_if_absent(ik, &created);
    if (created) {
      _pending_aot_inited_classes->push(ik);

      InstanceKlass* s = ik->java_super();
      if (s != nullptr) {
        add_aot_inited_class(s);
      }

      Array<InstanceKlass*>* interfaces = ik->local_interfaces();
      int len = interfaces->length();
      for (int i = 0; i < len; i++) {
        InstanceKlass* intf = interfaces->at(i);
        if (intf->is_initialized()) {
          add_aot_inited_class(intf);
        }
      }
    }
  }
}

void AOTArtifactFinder::append_to_all_cached_classes(Klass* k) {
  precond(!SystemDictionaryShared::should_be_excluded(k));
  _all_cached_classes->append(k);
}

void AOTArtifactFinder::add_cached_instance_class(InstanceKlass* ik) {
  if (CDSConfig::is_dumping_dynamic_archive() && ik->is_shared()) {
    // This class is already included in the base archive. No need to cache
    // it again in the dynamic archive.
    return;
  }

  bool created;
  _seen_classes->put_if_absent(ik, &created);
  if (created) {
    append_to_all_cached_classes(ik);

    // All super types must be added.
    InstanceKlass* s = ik->java_super();
    if (s != nullptr) {
      add_cached_instance_class(s);
    }

    Array<InstanceKlass*>* interfaces = ik->local_interfaces();
    int len = interfaces->length();
    for (int i = 0; i < len; i++) {
      InstanceKlass* intf = interfaces->at(i);
      add_cached_instance_class(intf);
    }

    InstanceKlass* nest_host = ik->nest_host_or_null();
    if (nest_host != nullptr) {
      add_cached_instance_class(nest_host);
    }

    if (CDSConfig::is_dumping_final_static_archive() && ik->defined_by_other_loaders()) {
      // The following are not appliable to unregistered classes
      return;
    }
    scan_oops_in_instance_class(ik);
    if (ik->is_hidden() && CDSConfig::is_initing_classes_at_dump_time()) {
      bool succeed = AOTClassLinker::try_add_candidate(ik);
      guarantee(succeed, "All cached hidden classes must be aot-linkable");
      add_aot_inited_class(ik);
    }
  }
}

void AOTArtifactFinder::add_cached_type_array_class(TypeArrayKlass* tak) {
  bool created;
  _seen_classes->put_if_absent(tak, &created);
  if (created) {
    append_to_all_cached_classes(tak);
    scan_oops_in_array_class(tak);
  }
}

void AOTArtifactFinder::add_cached_class(Klass* k) {
  if (k->is_typeArray_klass()) {
    add_cached_type_array_class(TypeArrayKlass::cast(k));
  } else if (k->is_objArray_klass()) {
    add_cached_class(ObjArrayKlass::cast(k)->element_klass());
  } else {
    add_cached_instance_class(InstanceKlass::cast(k));
  }
}

void AOTArtifactFinder::scan_oops_in_instance_class(InstanceKlass* ik) {
#if INCLUDE_CDS_JAVA_HEAP
  if (CDSConfig::is_dumping_heap()) {
    HeapShared::scan_java_class(ik);
    scan_oops_in_array_class(ik->array_klasses());
  }
#endif
}

void AOTArtifactFinder::scan_oops_in_array_class(ArrayKlass* ak) {
#if INCLUDE_CDS_JAVA_HEAP
  if (CDSConfig::is_dumping_heap()) {
    while (ak != nullptr) {
      HeapShared::scan_java_class(ak);
      ak = ak->array_klass_or_null();
    }
  }
#endif
}

void AOTArtifactFinder::all_cached_classes_do(MetaspaceClosure* it) {
  for (int i = 0; i < _all_cached_classes->length(); i++) {
    it->push(_all_cached_classes->adr_at(i));
  }
}
