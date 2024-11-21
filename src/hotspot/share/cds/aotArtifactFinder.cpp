/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/aotClassLinker.hpp"
#include "cds/aotArtifactFinder.hpp"
#include "cds/aotClassInitializer.hpp"
#include "cds/dumpTimeClassInfo.inline.hpp"
#include "cds/heapShared.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "logging/log.hpp"
#include "memory/metaspaceClosure.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/objArrayKlass.hpp"
#include "utilities/resourceHash.hpp"

GrowableArrayCHeap<InstanceKlass*, mtClassShared>* AOTArtifactFinder::_classes = nullptr;
GrowableArrayCHeap<InstanceKlass*, mtClassShared>* AOTArtifactFinder::_pending_aot_inited_classes = nullptr;

static const int TABLE_SIZE = 15889; // prime number
using ClassesTable = ResourceHashtable<InstanceKlass*, bool, TABLE_SIZE, AnyObj::C_HEAP, mtClassShared>;
static ClassesTable* _seen_classes;
static ClassesTable* _aot_inited_classes;

void AOTArtifactFinder::initialize() {
  _classes = new GrowableArrayCHeap<InstanceKlass*, mtClassShared>();
  _pending_aot_inited_classes = new GrowableArrayCHeap<InstanceKlass*, mtClassShared>();
  _seen_classes = new (mtClass)ClassesTable();
  _aot_inited_classes = new (mtClass)ClassesTable();
}

void AOTArtifactFinder::dispose() {
  delete _classes;
  delete _seen_classes;
  delete _aot_inited_classes;
  delete _pending_aot_inited_classes;
  _classes = nullptr;
  _seen_classes = nullptr;
  _aot_inited_classes = nullptr;
  _pending_aot_inited_classes = nullptr;
}

void AOTArtifactFinder::find_artifacts() {
  // Some classes might have been marked as excluded as a side effect of running
  // AOTConstantPoolResolver. Make sure we check all the remaining ones.
  //
  // Note, if a class is not excluded, it does NOT mean it will be automatically included
  // into the AOT cache -- that will be decided by the code below.
  SystemDictionaryShared::finish_exclusion_checks();

  start_scanning_for_oops();

  // First: add all the classes that are unconditionally included
  if (CDSConfig::is_dumping_heap()) {
    for (int i = T_BOOLEAN; i < T_VOID+1; i++) {
      BasicType bt = (BasicType)i;
      if (!is_reference_type(bt)) {
        oop orig_mirror = Universe::java_mirror(bt);
        oop m = HeapShared::scratch_java_mirror(bt);
        HeapShared::scan_java_mirror(orig_mirror);
        log_trace(cds, heap, mirror)(
            "Archived %s mirror object from " PTR_FORMAT,
            type2name(bt), p2i(m));
        Universe::set_archived_basic_type_mirror_index(bt, HeapShared::append_root(m));

        if (is_java_primitive(bt)) {
          scan_oops_in_array_class(Universe::typeArrayKlass(bt));
        }
      }
    }

    // Special handling
   HeapShared::scan_java_mirror(Universe::fillerArrayKlass()->java_mirror());
  }

  SystemDictionaryShared::dumptime_table()->iterate_all_live_classes([&] (InstanceKlass* ik, DumpTimeClassInfo& info) {
    // FIXME -- split this and add comments
    if (!info.is_excluded() && (!ik->is_hidden() || SystemDictionaryShared::is_registered_lambda_proxy_class(ik))) {
      if (ik->is_hidden() && CDSConfig::is_dumping_invokedynamic()) {
        assert(!SystemDictionaryShared::is_registered_lambda_proxy_class(ik), "only used in legacy lambda proxy support");
      }
      add_class(ik);

      if (AOTClassInitializer::can_archive_initialized_mirror(ik)) {
        add_aot_inited_class(ik);
      }
    }
  });

  // Second: keep scanning until we discover no more class that need to be aot-initialized.
  if (CDSConfig::is_dumping_invokedynamic()) { // rename -- is_dumping_aot_inited_classes
    while (_pending_aot_inited_classes->length() > 0) {
      InstanceKlass* ik = _pending_aot_inited_classes->pop();
      HeapShared::copy_and_rescan_aot_inited_mirror(ik);
    }
  }

  // Third: mark all other classes as excluded
  SystemDictionaryShared::dumptime_table()->iterate_all_live_classes([&] (InstanceKlass* k, DumpTimeClassInfo& info) {
    if (!info.is_excluded() && _seen_classes->get(k) == nullptr) {
      info.set_excluded();
      assert(k->is_hidden(), "must be");
      if (log_is_enabled(Info, cds)) {
        ResourceMark rm;
        log_info(cds)("Skipping %s: Hidden class", k->name()->as_C_string());
      }
    }
  });


  end_scanning_for_oops();
}

void AOTArtifactFinder::start_scanning_for_oops() {
  if (CDSConfig::is_dumping_heap()) {
    HeapShared::start_scanning_for_oops();
  }
}

void AOTArtifactFinder::end_scanning_for_oops() {
  if (CDSConfig::is_dumping_heap()) {
    HeapShared::end_scanning_for_oops();
  }
}

void AOTArtifactFinder::add_aot_inited_class(InstanceKlass* ik) {
  if (CDSConfig::is_initing_classes_at_dump_time()) {
    assert(ik->is_initialized(), "must be");
    add_class(ik);

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

void AOTArtifactFinder::add_class(InstanceKlass* ik) {
  bool created;
  _seen_classes->put_if_absent(ik, &created);
  if (created) {
    _classes->append(ik);
    scan_oops_in_instance_class(ik);
    if (ik->is_hidden() && CDSConfig::is_initing_classes_at_dump_time()) {
      bool succeed = AOTClassLinker::try_add_candidate(ik);
      guarantee(succeed, "All cached hidden classes must be aot-linkable");
      add_aot_inited_class(ik);
    }
  }
}

bool AOTArtifactFinder::is_lambda_proxy_class(InstanceKlass* ik) {
  return false;
}

void AOTArtifactFinder::scan_oops_in_instance_class(InstanceKlass* ik) {
  if (CDSConfig::is_dumping_heap()) {
    HeapShared::scan_java_class(ik);
    scan_oops_in_array_class(ik->array_klasses());
  }
}

void AOTArtifactFinder::scan_oops_in_array_class(ArrayKlass* ak) {
  if (CDSConfig::is_dumping_heap()) {
    while (ak != nullptr) {
      HeapShared::scan_java_class(ak);
      ak = ak->array_klass_or_null();
    }
  }
}

void AOTArtifactFinder::classes_do(MetaspaceClosure* it) {
  for (int i = 0; i < _classes->length(); i++) {
    it->push(_classes->adr_at(i));
  }
}

