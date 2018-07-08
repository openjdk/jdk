/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "classfile/vmSymbols.hpp"
#include "logging/log.hpp"
#include "logging/logMessage.hpp"
#include "logging/logStream.hpp"
#include "memory/heapShared.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/resourceArea.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"

#if INCLUDE_CDS_JAVA_HEAP
KlassSubGraphInfo* HeapShared::_subgraph_info_list = NULL;
int HeapShared::_num_archived_subgraph_info_records = 0;
Array<ArchivedKlassSubGraphInfoRecord>* HeapShared::_archived_subgraph_info_records = NULL;

// Currently there is only one class mirror (ArchivedModuleGraph) with archived
// sub-graphs.
KlassSubGraphInfo* HeapShared::find_subgraph_info(Klass* k) {
  KlassSubGraphInfo* info = _subgraph_info_list;
  while (info != NULL) {
    if (info->klass() == k) {
      return info;
    }
    info = info->next();
  }
  return NULL;
}

// Get the subgraph_info for Klass k. A new subgraph_info is created if
// there is no existing one for k. The subgraph_info records the relocated
// Klass* of the original k.
KlassSubGraphInfo* HeapShared::get_subgraph_info(Klass* k) {
  Klass* relocated_k = MetaspaceShared::get_relocated_klass(k);
  KlassSubGraphInfo* info = find_subgraph_info(relocated_k);
  if (info != NULL) {
    return info;
  }

  info = new KlassSubGraphInfo(relocated_k, _subgraph_info_list);
  _subgraph_info_list = info;
  return info;
}

int HeapShared::num_of_subgraph_infos() {
  int num = 0;
  KlassSubGraphInfo* info = _subgraph_info_list;
  while (info != NULL) {
    num ++;
    info = info->next();
  }
  return num;
}

// Add an entry field to the current KlassSubGraphInfo.
void KlassSubGraphInfo::add_subgraph_entry_field(int static_field_offset, oop v) {
  assert(DumpSharedSpaces, "dump time only");
  if (_subgraph_entry_fields == NULL) {
    _subgraph_entry_fields =
      new(ResourceObj::C_HEAP, mtClass) GrowableArray<juint>(10, true);
  }
  _subgraph_entry_fields->append((juint)static_field_offset);
  _subgraph_entry_fields->append(CompressedOops::encode(v));
}

// Add the Klass* for an object in the current KlassSubGraphInfo's subgraphs.
// Only objects of boot classes can be included in sub-graph.
void KlassSubGraphInfo::add_subgraph_object_klass(Klass* orig_k, Klass *relocated_k) {
  assert(DumpSharedSpaces, "dump time only");
  assert(relocated_k == MetaspaceShared::get_relocated_klass(orig_k),
         "must be the relocated Klass in the shared space");

  if (_subgraph_object_klasses == NULL) {
    _subgraph_object_klasses =
      new(ResourceObj::C_HEAP, mtClass) GrowableArray<Klass*>(50, true);
  }

  assert(relocated_k->is_shared(), "must be a shared class");
  if (relocated_k->is_instance_klass()) {
    assert(InstanceKlass::cast(relocated_k)->is_shared_boot_class(),
          "must be boot class");
    // SystemDictionary::xxx_klass() are not updated, need to check
    // the original Klass*
    if (orig_k == SystemDictionary::String_klass() ||
        orig_k == SystemDictionary::Object_klass()) {
      // Initialized early during VM initialization. No need to be added
      // to the sub-graph object class list.
      return;
    }
  } else if (relocated_k->is_objArray_klass()) {
    Klass* abk = ObjArrayKlass::cast(relocated_k)->bottom_klass();
    if (abk->is_instance_klass()) {
      assert(InstanceKlass::cast(abk)->is_shared_boot_class(),
            "must be boot class");
    }
    if (relocated_k == Universe::objectArrayKlassObj()) {
      // Initialized early during Universe::genesis. No need to be added
      // to the list.
      return;
    }
  } else {
    assert(relocated_k->is_typeArray_klass(), "must be");
    // Primitive type arrays are created early during Universe::genesis.
    return;
  }

  _subgraph_object_klasses->append_if_missing(relocated_k);
}

// Initialize an archived subgraph_info_record from the given KlassSubGraphInfo.
void ArchivedKlassSubGraphInfoRecord::init(KlassSubGraphInfo* info) {
  _k = info->klass();
  _next = NULL;
  _entry_field_records = NULL;
  _subgraph_klasses = NULL;

  // populate the entry fields
  GrowableArray<juint>* entry_fields = info->subgraph_entry_fields();
  if (entry_fields != NULL) {
    int num_entry_fields = entry_fields->length();
    assert(num_entry_fields % 2 == 0, "sanity");
    _entry_field_records =
      MetaspaceShared::new_ro_array<juint>(num_entry_fields);
    for (int i = 0 ; i < num_entry_fields; i++) {
      _entry_field_records->at_put(i, entry_fields->at(i));
    }
  }

  // the Klasses of the objects in the sub-graphs
  GrowableArray<Klass*>* subgraph_klasses = info->subgraph_object_klasses();
  if (subgraph_klasses != NULL) {
    int num_subgraphs_klasses = subgraph_klasses->length();
    _subgraph_klasses =
      MetaspaceShared::new_ro_array<Klass*>(num_subgraphs_klasses);
    for (int i = 0; i < num_subgraphs_klasses; i++) {
      Klass* subgraph_k = subgraph_klasses->at(i);
      if (log_is_enabled(Info, cds, heap)) {
        ResourceMark rm;
        log_info(cds, heap)(
          "Archived object klass (%d): %s in %s sub-graphs",
          i, subgraph_k->external_name(), _k->external_name());
      }
      _subgraph_klasses->at_put(i, subgraph_k);
    }
  }
}

// Build the records of archived subgraph infos, which include:
// - Entry points to all subgraphs from the containing class mirror. The entry
//   points are static fields in the mirror. For each entry point, the field
//   offset and value are recorded in the sub-graph info. The value are stored
//   back to the corresponding field at runtime.
// - A list of klasses that need to be loaded/initialized before archived
//   java object sub-graph can be accessed at runtime.
//
// The records are saved in the archive file and reloaded at runtime. Currently
// there is only one class mirror (ArchivedModuleGraph) with archived sub-graphs.
//
// Layout of the archived subgraph info records:
//
// records_size | num_records | records*
// ArchivedKlassSubGraphInfoRecord | entry_fields | subgraph_object_klasses
size_t HeapShared::build_archived_subgraph_info_records(int num_records) {
  // remember the start address
  char* start_p = MetaspaceShared::read_only_space_top();

  // now populate the archived subgraph infos, which will be saved in the
  // archive file
  _archived_subgraph_info_records =
    MetaspaceShared::new_ro_array<ArchivedKlassSubGraphInfoRecord>(num_records);
  KlassSubGraphInfo* info = _subgraph_info_list;
  int i = 0;
  while (info != NULL) {
    assert(i < _archived_subgraph_info_records->length(), "sanity");
    ArchivedKlassSubGraphInfoRecord* record =
      _archived_subgraph_info_records->adr_at(i);
    record->init(info);
    info = info->next();
    i ++;
  }

  // _subgraph_info_list is no longer needed
  delete _subgraph_info_list;
  _subgraph_info_list = NULL;

  char* end_p = MetaspaceShared::read_only_space_top();
  size_t records_size = end_p - start_p;
  return records_size;
}

// Write the subgraph info records in the shared _ro region
void HeapShared::write_archived_subgraph_infos() {
  assert(DumpSharedSpaces, "dump time only");

  Array<intptr_t>* records_header = MetaspaceShared::new_ro_array<intptr_t>(3);

  _num_archived_subgraph_info_records = num_of_subgraph_infos();
  size_t records_size = build_archived_subgraph_info_records(
                             _num_archived_subgraph_info_records);

  // Now write the header information:
  // records_size, num_records, _archived_subgraph_info_records
  assert(records_header != NULL, "sanity");
  intptr_t* p = (intptr_t*)(records_header->data());
  *p = (intptr_t)records_size;
  p ++;
  *p = (intptr_t)_num_archived_subgraph_info_records;
  p ++;
  *p = (intptr_t)_archived_subgraph_info_records;
}

char* HeapShared::read_archived_subgraph_infos(char* buffer) {
  Array<intptr_t>* records_header = (Array<intptr_t>*)buffer;
  intptr_t* p = (intptr_t*)(records_header->data());
  size_t records_size = (size_t)(*p);
  p ++;
  _num_archived_subgraph_info_records = *p;
  p ++;
  _archived_subgraph_info_records =
    (Array<ArchivedKlassSubGraphInfoRecord>*)(*p);

  buffer = (char*)_archived_subgraph_info_records + records_size;
  return buffer;
}

void HeapShared::initialize_from_archived_subgraph(Klass* k) {
  if (!MetaspaceShared::open_archive_heap_region_mapped()) {
    return; // nothing to do
  }

  if (_num_archived_subgraph_info_records == 0) {
    return; // no subgraph info records
  }

  // Initialize from archived data. Currently only ArchivedModuleGraph
  // has archived object subgraphs, which is used during VM initialization
  // time when bootstraping the system modules. No lock is needed.
  Thread* THREAD = Thread::current();
  for (int i = 0; i < _archived_subgraph_info_records->length(); i++) {
    ArchivedKlassSubGraphInfoRecord* record = _archived_subgraph_info_records->adr_at(i);
    if (record->klass() == k) {
      int i;
      // Found the archived subgraph info record for the requesting klass.
      // Load/link/initialize the klasses of the objects in the subgraph.
      // NULL class loader is used.
      Array<Klass*>* klasses = record->subgraph_klasses();
      if (klasses != NULL) {
        for (i = 0; i < klasses->length(); i++) {
          Klass* obj_k = klasses->at(i);
          Klass* resolved_k = SystemDictionary::resolve_or_null(
                                                (obj_k)->name(), THREAD);
          if (resolved_k != obj_k) {
            return;
          }
          if ((obj_k)->is_instance_klass()) {
            InstanceKlass* ik = InstanceKlass::cast(obj_k);
            ik->initialize(THREAD);
          } else if ((obj_k)->is_objArray_klass()) {
            ObjArrayKlass* oak = ObjArrayKlass::cast(obj_k);
            oak->initialize(THREAD);
          }
        }
      }

      if (HAS_PENDING_EXCEPTION) {
        CLEAR_PENDING_EXCEPTION;
        // None of the field value will be set if there was an exception.
        // The java code will not see any of the archived objects in the
        // subgraphs referenced from k in this case.
        return;
      }

      // Load the subgraph entry fields from the record and store them back to
      // the corresponding fields within the mirror.
      oop m = k->java_mirror();
      Array<juint>* entry_field_records = record->entry_field_records();
      if (entry_field_records != NULL) {
        int efr_len = entry_field_records->length();
        assert(efr_len % 2 == 0, "sanity");
        for (i = 0; i < efr_len;) {
          int field_offset = entry_field_records->at(i);
          // The object refereced by the field becomes 'known' by GC from this
          // point. All objects in the subgraph reachable from the object are
          // also 'known' by GC.
          oop v = MetaspaceShared::materialize_archived_object(
            CompressedOops::decode(entry_field_records->at(i+1)));
          m->obj_field_put(field_offset, v);
          i += 2;
        }
      }

      // Done. Java code can see the archived sub-graphs referenced from k's
      // mirror after this point.
      return;
    }
  }
}

class WalkOopAndArchiveClosure: public BasicOopIterateClosure {
  int _level;
  KlassSubGraphInfo* _subgraph_info;
  oop _orig_referencing_obj;
  oop _archived_referencing_obj;
 public:
  WalkOopAndArchiveClosure(int level, KlassSubGraphInfo* subgraph_info,
           oop orig, oop archived) : _level(level),
                                     _subgraph_info(subgraph_info),
                                     _orig_referencing_obj(orig),
                                     _archived_referencing_obj(archived) {}
  void do_oop(narrowOop *p) { WalkOopAndArchiveClosure::do_oop_work(p); }
  void do_oop(      oop *p) { WalkOopAndArchiveClosure::do_oop_work(p); }

 protected:
  template <class T> void do_oop_work(T *p) {
    oop obj = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(obj)) {
      // A java.lang.Class instance can not be included in an archived
      // object sub-graph.
      if (java_lang_Class::is_instance(obj)) {
        tty->print("Unknown java.lang.Class object is in the archived sub-graph\n");
        vm_exit(1);
      }

      LogTarget(Debug, cds, heap) log;
      LogStream ls(log);
      outputStream* out = &ls;
      {
        ResourceMark rm;
        log.print("(%d) %s <--- referenced from:  %s",
                  _level, obj->klass()->external_name(),
                  CompressedOops::is_null(_orig_referencing_obj) ?
                         "" : _orig_referencing_obj->klass()->external_name());
        obj->print_on(out);
      }

      if (MetaspaceShared::is_archive_object(obj)) {
        // The current oop is an archived oop, nothing needs to be done
        log.print("--- object is already archived ---");
        return;
      }

      size_t field_delta = pointer_delta(
        p, _orig_referencing_obj, sizeof(char));
      T* new_p = (T*)(address(_archived_referencing_obj) + field_delta);
      oop archived = MetaspaceShared::find_archived_heap_object(obj);
      if (archived != NULL) {
        // There is an archived copy existing, update reference to point
        // to the archived copy
        RawAccess<IS_NOT_NULL>::oop_store(new_p, archived);
        log.print(
          "--- found existing archived copy, store archived " PTR_FORMAT " in " PTR_FORMAT,
          p2i(archived), p2i(new_p));
        return;
      }

      int l = _level + 1;
      Thread* THREAD = Thread::current();
      // Archive the current oop before iterating through its references
      archived = MetaspaceShared::archive_heap_object(obj, THREAD);
      assert(MetaspaceShared::is_archive_object(archived), "must be archived");
      log.print("=== archiving oop " PTR_FORMAT " ==> " PTR_FORMAT,
                 p2i(obj), p2i(archived));

      // Following the references in the current oop and archive any
      // encountered objects during the process
      WalkOopAndArchiveClosure walker(l, _subgraph_info, obj, archived);
      obj->oop_iterate(&walker);

      // Update the reference in the archived copy of the referencing object
      RawAccess<IS_NOT_NULL>::oop_store(new_p, archived);
      log.print("=== store archived " PTR_FORMAT " in " PTR_FORMAT,
                p2i(archived), p2i(new_p));

      // Add the klass to the list of classes that need to be loaded before
      // module system initialization
      Klass *orig_k = obj->klass();
      Klass *relocated_k = archived->klass();
      _subgraph_info->add_subgraph_object_klass(orig_k, relocated_k);
    }
  }
};

//
// Start from the given static field in a java mirror and archive the
// complete sub-graph of java heap objects that are reached directly
// or indirectly from the starting object by following references.
// Currently, only ArchivedModuleGraph class instance (mirror) has archived
// object subgraphs. Sub-graph archiving restrictions (current):
//
// - All classes of objects in the archived sub-graph (including the
//   entry class) must be boot class only.
// - No java.lang.Class instance (java mirror) can be included inside
//   an archived sub-graph. Mirror can only be the sub-graph entry object.
//
// The Java heap object sub-graph archiving process (see
// WalkOopAndArchiveClosure):
//
// 1) Java object sub-graph archiving starts from a given static field
// within a Class instance (java mirror). If the static field is a
// refererence field and points to a non-null java object, proceed to
// the next step.
//
// 2) Archives the referenced java object. If an archived copy of the
// current object already exists, updates the pointer in the archived
// copy of the referencing object to point to the current archived object.
// Otherwise, proceed to the next step.
//
// 3) Follows all references within the current java object and recursively
// archive the sub-graph of objects starting from each reference.
//
// 4) Updates the pointer in the archived copy of referencing object to
//    point to the current archived object.
//
// 5) The Klass of the current java object is added to the list of Klasses
// for loading and initialzing before any object in the archived graph can
// be accessed at runtime.
//
void HeapShared::archive_reachable_objects_from_static_field(Klass *k,
                                                             int field_offset,
                                                             BasicType field_type,
                                                             TRAPS) {
  assert(DumpSharedSpaces, "dump time only");
  assert(k->is_instance_klass(), "sanity");
  assert(InstanceKlass::cast(k)->is_shared_boot_class(),
         "must be boot class");

  oop m = k->java_mirror();
  oop archived_m = MetaspaceShared::find_archived_heap_object(m);
  if (CompressedOops::is_null(archived_m)) {
    return;
  }

  if (field_type == T_OBJECT) {
    // obtain k's subGraph Info
    KlassSubGraphInfo* subgraph_info = get_subgraph_info(k);

    // get the object referenced by the field
    oop f = m->obj_field(field_offset);
    if (!CompressedOops::is_null(f)) {
      LogTarget(Debug, cds, heap) log;
      LogStream ls(log);
      outputStream* out = &ls;
      log.print("Start from: ");
      f->print_on(out);

      // get the archived copy of the field referenced object
      oop af = MetaspaceShared::archive_heap_object(f, THREAD);
      if (!MetaspaceShared::is_archive_object(f)) {
        WalkOopAndArchiveClosure walker(1, subgraph_info, f, af);
        f->oop_iterate(&walker);
      }

      // The field value is not preserved in the archived mirror.
      // Record the field as a new subGraph entry point. The recorded
      // information is restored from the archive at runtime.
      subgraph_info->add_subgraph_entry_field(field_offset, af);
      Klass *relocated_k = af->klass();
      Klass *orig_k = f->klass();
      subgraph_info->add_subgraph_object_klass(orig_k, relocated_k);
    } else {
      // The field contains null, we still need to record the entry point,
      // so it can be restored at runtime.
      subgraph_info->add_subgraph_entry_field(field_offset, NULL);
    }
  } else {
    ShouldNotReachHere();
  }
}

#define do_module_object_graph(archive_object_graph_do) \
  archive_object_graph_do(SystemDictionary::ArchivedModuleGraph_klass(), jdk_internal_module_ArchivedModuleGraph::archivedSystemModules_offset(), T_OBJECT, CHECK); \
  archive_object_graph_do(SystemDictionary::ArchivedModuleGraph_klass(), jdk_internal_module_ArchivedModuleGraph::archivedModuleFinder_offset(), T_OBJECT, CHECK); \
  archive_object_graph_do(SystemDictionary::ArchivedModuleGraph_klass(), jdk_internal_module_ArchivedModuleGraph::archivedMainModule_offset(), T_OBJECT, CHECK)

void HeapShared::archive_module_graph_objects(Thread* THREAD) {
  do_module_object_graph(archive_reachable_objects_from_static_field);
}
#endif // INCLUDE_CDS_JAVA_HEAP
