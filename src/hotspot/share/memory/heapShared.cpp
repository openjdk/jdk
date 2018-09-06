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
#include "classfile/symbolTable.hpp"
#include "classfile/vmSymbols.hpp"
#include "logging/log.hpp"
#include "logging/logMessage.hpp"
#include "logging/logStream.hpp"
#include "memory/heapShared.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/resourceArea.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "utilities/bitMap.inline.hpp"

#if INCLUDE_CDS_JAVA_HEAP
KlassSubGraphInfo* HeapShared::_subgraph_info_list = NULL;
int HeapShared::_num_archived_subgraph_info_records = 0;
Array<ArchivedKlassSubGraphInfoRecord>* HeapShared::_archived_subgraph_info_records = NULL;

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

address   HeapShared::_narrow_oop_base;
int       HeapShared::_narrow_oop_shift;

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

  if (_k == relocated_k) {
    // Don't add the Klass containing the sub-graph to it's own klass
    // initialization list.
    return;
  }

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

  if (log_is_enabled(Debug, cds, heap)) {
    if (!_subgraph_object_klasses->contains(relocated_k)) {
      ResourceMark rm;
      log_debug(cds, heap)("Adding klass %s", orig_k->external_name());
    }
  }

  _subgraph_object_klasses->append_if_missing(relocated_k);
}

// Initialize an archived subgraph_info_record from the given KlassSubGraphInfo.
void ArchivedKlassSubGraphInfoRecord::init(KlassSubGraphInfo* info) {
  _k = info->klass();
  _next = NULL;
  _entry_field_records = NULL;
  _subgraph_object_klasses = NULL;

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
  GrowableArray<Klass*>* subgraph_object_klasses = info->subgraph_object_klasses();
  if (subgraph_object_klasses != NULL) {
    int num_subgraphs_klasses = subgraph_object_klasses->length();
    _subgraph_object_klasses =
      MetaspaceShared::new_ro_array<Klass*>(num_subgraphs_klasses);
    for (int i = 0; i < num_subgraphs_klasses; i++) {
      Klass* subgraph_k = subgraph_object_klasses->at(i);
      if (log_is_enabled(Info, cds, heap)) {
        ResourceMark rm;
        log_info(cds, heap)(
          "Archived object klass %s (%2d) => %s",
          _k->external_name(), i, subgraph_k->external_name());
      }
      _subgraph_object_klasses->at_put(i, subgraph_k);
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
// The records are saved in the archive file and reloaded at runtime.
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

  // Initialize from archived data. Currently this is done only
  // during VM initialization time. No lock is needed.
  Thread* THREAD = Thread::current();
  for (int i = 0; i < _archived_subgraph_info_records->length(); i++) {
    ArchivedKlassSubGraphInfoRecord* record = _archived_subgraph_info_records->adr_at(i);
    if (record->klass() == k) {
      int i;
      // Found the archived subgraph info record for the requesting klass.
      // Load/link/initialize the klasses of the objects in the subgraph.
      // NULL class loader is used.
      Array<Klass*>* klasses = record->subgraph_object_klasses();
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
            entry_field_records->at(i+1));
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
  bool _record_klasses_only;
  KlassSubGraphInfo* _subgraph_info;
  oop _orig_referencing_obj;
  oop _archived_referencing_obj;
  Thread* _thread;
 public:
  WalkOopAndArchiveClosure(int level, bool record_klasses_only,
                           KlassSubGraphInfo* subgraph_info,
                           oop orig, oop archived, TRAPS) :
    _level(level), _record_klasses_only(record_klasses_only),
    _subgraph_info(subgraph_info),
    _orig_referencing_obj(orig), _archived_referencing_obj(archived),
    _thread(THREAD) {}
  void do_oop(narrowOop *p) { WalkOopAndArchiveClosure::do_oop_work(p); }
  void do_oop(      oop *p) { WalkOopAndArchiveClosure::do_oop_work(p); }

 protected:
  template <class T> void do_oop_work(T *p) {
    oop obj = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(obj)) {
      assert(!MetaspaceShared::is_archive_object(obj),
             "original objects must not point to archived objects");

      size_t field_delta = pointer_delta(p, _orig_referencing_obj, sizeof(char));
      T* new_p = (T*)(address(_archived_referencing_obj) + field_delta);
      Thread* THREAD = _thread;

      if (!_record_klasses_only && log_is_enabled(Debug, cds, heap)) {
        ResourceMark rm;
        log_debug(cds, heap)("(%d) %s[" SIZE_FORMAT "] ==> " PTR_FORMAT " size %d %s", _level,
                             _orig_referencing_obj->klass()->external_name(), field_delta,
                             p2i(obj), obj->size() * HeapWordSize, obj->klass()->external_name());
        LogTarget(Trace, cds, heap) log;
        LogStream out(log);
        obj->print_on(&out);
      }

      oop archived = HeapShared::archive_reachable_objects_from(_level + 1, _subgraph_info, obj, THREAD);
      assert(archived != NULL, "VM should have exited with unarchivable objects for _level > 1");
      assert(MetaspaceShared::is_archive_object(archived), "must be");

      if (!_record_klasses_only) {
        // Update the reference in the archived copy of the referencing object.
        log_debug(cds, heap)("(%d) updating oop @[" PTR_FORMAT "] " PTR_FORMAT " ==> " PTR_FORMAT,
                             _level, p2i(new_p), p2i(obj), p2i(archived));
        RawAccess<IS_NOT_NULL>::oop_store(new_p, archived);
      }
    }
  }
};

// (1) If orig_obj has not been archived yet, archive it.
// (2) If orig_obj has not been seen yet (since start_recording_subgraph() was called),
//     trace all  objects that are reachable from it, and make sure these objects are archived.
// (3) Record the klasses of all orig_obj and all reachable objects.
oop HeapShared::archive_reachable_objects_from(int level, KlassSubGraphInfo* subgraph_info, oop orig_obj, TRAPS) {
  assert(orig_obj != NULL, "must be");
  assert(!MetaspaceShared::is_archive_object(orig_obj), "sanity");

  // java.lang.Class instances cannot be included in an archived
  // object sub-graph.
  if (java_lang_Class::is_instance(orig_obj)) {
    log_error(cds, heap)("(%d) Unknown java.lang.Class object is in the archived sub-graph", level);
    vm_exit(1);
  }

  oop archived_obj = MetaspaceShared::find_archived_heap_object(orig_obj);
  if (java_lang_String::is_instance(orig_obj) && archived_obj != NULL) {
    // To save time, don't walk strings that are already archived. They just contain
    // pointers to a type array, whose klass doesn't need to be recorded.
    return archived_obj;
  }

  if (has_been_seen_during_subgraph_recording(orig_obj)) {
    // orig_obj has already been archived and traced. Nothing more to do.
    return archived_obj;
  } else {
    set_has_been_seen_during_subgraph_recording(orig_obj);
  }

  bool record_klasses_only = (archived_obj != NULL);
  if (archived_obj == NULL) {
    ++_num_new_archived_objs;
    archived_obj = MetaspaceShared::archive_heap_object(orig_obj, THREAD);
    if (archived_obj == NULL) {
      // Skip archiving the sub-graph referenced from the current entry field.
      ResourceMark rm;
      log_error(cds, heap)(
        "Cannot archive the sub-graph referenced from %s object ("
        PTR_FORMAT ") size %d, skipped.",
        orig_obj->klass()->external_name(), p2i(orig_obj), orig_obj->size() * HeapWordSize);
      if (level == 1) {
        // Don't archive a subgraph root that's too big. For archives static fields, that's OK
        // as the Java code will take care of initializing this field dynamically.
        return NULL;
      } else {
        // We don't know how to handle an object that has been archived, but some of its reachable
        // objects cannot be archived. Bail out for now. We might need to fix this in the future if
        // we have a real use case.
        vm_exit(1);
      }
    }
  }

  assert(archived_obj != NULL, "must be");
  Klass *orig_k = orig_obj->klass();
  Klass *relocated_k = archived_obj->klass();
  subgraph_info->add_subgraph_object_klass(orig_k, relocated_k);

  WalkOopAndArchiveClosure walker(level, record_klasses_only, subgraph_info, orig_obj, archived_obj, THREAD);
  orig_obj->oop_iterate(&walker);
  return archived_obj;
}

//
// Start from the given static field in a java mirror and archive the
// complete sub-graph of java heap objects that are reached directly
// or indirectly from the starting object by following references.
// Sub-graph archiving restrictions (current):
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
// point to the current archived object.
//
// 5) The Klass of the current java object is added to the list of Klasses
// for loading and initialzing before any object in the archived graph can
// be accessed at runtime.
//
void HeapShared::archive_reachable_objects_from_static_field(InstanceKlass *k,
                                                             const char* klass_name,
                                                             int field_offset,
                                                             const char* field_name,
                                                             TRAPS) {
  assert(DumpSharedSpaces, "dump time only");
  assert(k->is_shared_boot_class(), "must be boot class");

  oop m = k->java_mirror();
  oop archived_m = MetaspaceShared::find_archived_heap_object(m);
  if (CompressedOops::is_null(archived_m)) {
    return;
  }

  KlassSubGraphInfo* subgraph_info = get_subgraph_info(k);
  oop f = m->obj_field(field_offset);

  log_debug(cds, heap)("Start archiving from: %s::%s (" PTR_FORMAT ")", klass_name, field_name, p2i(f));

  if (!CompressedOops::is_null(f)) {
    if (log_is_enabled(Trace, cds, heap)) {
      LogTarget(Trace, cds, heap) log;
      LogStream out(log);
      f->print_on(&out);
    }

    oop af = archive_reachable_objects_from(1, subgraph_info, f, CHECK);

    if (af == NULL) {
      log_error(cds, heap)("Archiving failed %s::%s (some reachable objects cannot be archived)",
                           klass_name, field_name);
    } else {
      // Note: the field value is not preserved in the archived mirror.
      // Record the field as a new subGraph entry point. The recorded
      // information is restored from the archive at runtime.
      subgraph_info->add_subgraph_entry_field(field_offset, af);
      log_info(cds, heap)("Archived field %s::%s => " PTR_FORMAT, klass_name, field_name, p2i(af));
    }
  } else {
    // The field contains null, we still need to record the entry point,
    // so it can be restored at runtime.
    subgraph_info->add_subgraph_entry_field(field_offset, NULL);
  }
}

#ifndef PRODUCT
class VerifySharedOopClosure: public BasicOopIterateClosure {
 private:
  bool _is_archived;

 public:
  VerifySharedOopClosure(bool is_archived) : _is_archived(is_archived) {}

  void do_oop(narrowOop *p) { VerifySharedOopClosure::do_oop_work(p); }
  void do_oop(      oop *p) { VerifySharedOopClosure::do_oop_work(p); }

 protected:
  template <class T> void do_oop_work(T *p) {
    oop obj = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(obj)) {
      HeapShared::verify_reachable_objects_from(obj, _is_archived);
    }
  }
};

void HeapShared::verify_subgraph_from_static_field(InstanceKlass* k, int field_offset) {
  assert(DumpSharedSpaces, "dump time only");
  assert(k->is_shared_boot_class(), "must be boot class");

  oop m = k->java_mirror();
  oop archived_m = MetaspaceShared::find_archived_heap_object(m);
  if (CompressedOops::is_null(archived_m)) {
    return;
  }
  oop f = m->obj_field(field_offset);
  if (!CompressedOops::is_null(f)) {
    verify_subgraph_from(f);
  }
}

void HeapShared::verify_subgraph_from(oop orig_obj) {
  oop archived_obj = MetaspaceShared::find_archived_heap_object(orig_obj);
  if (archived_obj == NULL) {
    // It's OK for the root of a subgraph to be not archived. See comments in
    // archive_reachable_objects_from().
    return;
  }

  // Verify that all objects reachable from orig_obj are archived.
  init_seen_objects_table();
  verify_reachable_objects_from(orig_obj, false);
  delete_seen_objects_table();

  // Note: we could also verify that all objects reachable from the archived
  // copy of orig_obj can only point to archived objects, with:
  //      init_seen_objects_table();
  //      verify_reachable_objects_from(archived_obj, true);
  //      init_seen_objects_table();
  // but that's already done in G1HeapVerifier::verify_archive_regions so we
  // won't do it here.
}

void HeapShared::verify_reachable_objects_from(oop obj, bool is_archived) {
  _num_total_verifications ++;
  if (!has_been_seen_during_subgraph_recording(obj)) {
    set_has_been_seen_during_subgraph_recording(obj);

    if (is_archived) {
      assert(MetaspaceShared::is_archive_object(obj), "must be");
      assert(MetaspaceShared::find_archived_heap_object(obj) == NULL, "must be");
    } else {
      assert(!MetaspaceShared::is_archive_object(obj), "must be");
      assert(MetaspaceShared::find_archived_heap_object(obj) != NULL, "must be");
    }

    VerifySharedOopClosure walker(is_archived);
    obj->oop_iterate(&walker);
  }
}
#endif

HeapShared::SeenObjectsTable* HeapShared::_seen_objects_table = NULL;
int HeapShared::_num_new_walked_objs;
int HeapShared::_num_new_archived_objs;
int HeapShared::_num_old_recorded_klasses;

int HeapShared::_num_total_subgraph_recordings = 0;
int HeapShared::_num_total_walked_objs = 0;
int HeapShared::_num_total_archived_objs = 0;
int HeapShared::_num_total_recorded_klasses = 0;
int HeapShared::_num_total_verifications = 0;

bool HeapShared::has_been_seen_during_subgraph_recording(oop obj) {
  return _seen_objects_table->get(obj) != NULL;
}

void HeapShared::set_has_been_seen_during_subgraph_recording(oop obj) {
  assert(!has_been_seen_during_subgraph_recording(obj), "sanity");
  _seen_objects_table->put(obj, true);
  ++ _num_new_walked_objs;
}

void HeapShared::start_recording_subgraph(InstanceKlass *k, const char* class_name) {
  log_info(cds, heap)("Start recording subgraph(s) for archived fields in %s", class_name);
  init_seen_objects_table();
  _num_new_walked_objs = 0;
  _num_new_archived_objs = 0;
  _num_old_recorded_klasses = get_subgraph_info(k)->num_subgraph_object_klasses();
}

void HeapShared::done_recording_subgraph(InstanceKlass *k, const char* class_name) {
  int num_new_recorded_klasses = get_subgraph_info(k)->num_subgraph_object_klasses() -
    _num_old_recorded_klasses;
  log_info(cds, heap)("Done recording subgraph(s) for archived fields in %s: "
                      "walked %d objs, archived %d new objs, recorded %d classes",
                      class_name, _num_new_walked_objs, _num_new_archived_objs,
                      num_new_recorded_klasses);

  delete_seen_objects_table();

  _num_total_subgraph_recordings ++;
  _num_total_walked_objs      += _num_new_walked_objs;
  _num_total_archived_objs    += _num_new_archived_objs;
  _num_total_recorded_klasses +=  num_new_recorded_klasses;
}

struct ArchivableStaticFieldInfo {
  const char* klass_name;
  const char* field_name;
  InstanceKlass* klass;
  int offset;
  BasicType type;
};

// If you add new entries to this table, you should know what you're doing!
static ArchivableStaticFieldInfo archivable_static_fields[] = {
  {"jdk/internal/module/ArchivedModuleGraph",  "archivedSystemModules"},
  {"jdk/internal/module/ArchivedModuleGraph",  "archivedModuleFinder"},
  {"jdk/internal/module/ArchivedModuleGraph",  "archivedMainModule"},
  {"jdk/internal/module/ArchivedModuleGraph",  "archivedConfiguration"},
  {"java/util/ImmutableCollections$ListN",     "EMPTY_LIST"},
  {"java/util/ImmutableCollections$MapN",      "EMPTY_MAP"},
  {"java/util/ImmutableCollections$SetN",      "EMPTY_SET"},
  {"java/lang/Integer$IntegerCache",           "archivedCache"},
  {"java/lang/module/Configuration",           "EMPTY_CONFIGURATION"},
};

const static int num_archivable_static_fields =
  sizeof(archivable_static_fields) / sizeof(ArchivableStaticFieldInfo);

class ArchivableStaticFieldFinder: public FieldClosure {
  InstanceKlass* _ik;
  Symbol* _field_name;
  bool _found;
  int _offset;
public:
  ArchivableStaticFieldFinder(InstanceKlass* ik, Symbol* field_name) :
    _ik(ik), _field_name(field_name), _found(false), _offset(-1) {}

  virtual void do_field(fieldDescriptor* fd) {
    if (fd->name() == _field_name) {
      assert(!_found, "fields cannot be overloaded");
      assert(fd->field_type() == T_OBJECT || fd->field_type() == T_ARRAY, "can archive only obj or array fields");
      _found = true;
      _offset = fd->offset();
    }
  }
  bool found()     { return _found;  }
  int offset()     { return _offset; }
};

void HeapShared::init_archivable_static_fields(Thread* THREAD) {
  for (int i = 0; i < num_archivable_static_fields; i++) {
    ArchivableStaticFieldInfo* info = &archivable_static_fields[i];
    TempNewSymbol klass_name =  SymbolTable::new_symbol(info->klass_name, THREAD);
    TempNewSymbol field_name =  SymbolTable::new_symbol(info->field_name, THREAD);

    Klass* k = SystemDictionary::resolve_or_null(klass_name, THREAD);
    assert(k != NULL && !HAS_PENDING_EXCEPTION, "class must exist");
    InstanceKlass* ik = InstanceKlass::cast(k);

    ArchivableStaticFieldFinder finder(ik, field_name);
    ik->do_local_static_fields(&finder);
    assert(finder.found(), "field must exist");

    info->klass = ik;
    info->offset = finder.offset();
  }
}

void HeapShared::archive_static_fields(Thread* THREAD) {
  // For each class X that has one or more archived fields:
  // [1] Dump the subgraph of each archived field
  // [2] Create a list of all the class of the objects that can be reached
  //     by any of these static fields.
  //     At runtime, these classes are initialized before X's archived fields
  //     are restored by HeapShared::initialize_from_archived_subgraph().
  int i;
  for (i = 0; i < num_archivable_static_fields; ) {
    ArchivableStaticFieldInfo* info = &archivable_static_fields[i];
    const char* klass_name = info->klass_name;
    start_recording_subgraph(info->klass, klass_name);

    // If you have specified consecutive fields of the same klass in
    // archivable_static_fields[], these will be archived in the same
    // {start_recording_subgraph ... done_recording_subgraph} pass to
    // save time.
    for (; i < num_archivable_static_fields; i++) {
      ArchivableStaticFieldInfo* f = &archivable_static_fields[i];
      if (f->klass_name != klass_name) {
        break;
      }
      archive_reachable_objects_from_static_field(f->klass, f->klass_name,
                                                  f->offset, f->field_name, CHECK);
    }
    done_recording_subgraph(info->klass, klass_name);
  }

  log_info(cds, heap)("Performed subgraph records = %d times", _num_total_subgraph_recordings);
  log_info(cds, heap)("Walked %d objects", _num_total_walked_objs);
  log_info(cds, heap)("Archived %d objects", _num_total_archived_objs);
  log_info(cds, heap)("Recorded %d klasses", _num_total_recorded_klasses);


#ifndef PRODUCT
  for (int i = 0; i < num_archivable_static_fields; i++) {
    ArchivableStaticFieldInfo* f = &archivable_static_fields[i];
    verify_subgraph_from_static_field(f->klass, f->offset);
  }
  log_info(cds, heap)("Verified %d references", _num_total_verifications);
#endif
}

// At dump-time, find the location of all the non-null oop pointers in an archived heap
// region. This way we can quickly relocate all the pointers without using
// BasicOopIterateClosure at runtime.
class FindEmbeddedNonNullPointers: public BasicOopIterateClosure {
  narrowOop* _start;
  BitMap *_oopmap;
  int _num_total_oops;
  int _num_null_oops;
 public:
  FindEmbeddedNonNullPointers(narrowOop* start, BitMap* oopmap)
    : _start(start), _oopmap(oopmap), _num_total_oops(0),  _num_null_oops(0) {}

  virtual bool should_verify_oops(void) {
    return false;
  }
  virtual void do_oop(narrowOop* p) {
    _num_total_oops ++;
    narrowOop v = *p;
    if (!CompressedOops::is_null(v)) {
      size_t idx = p - _start;
      _oopmap->set_bit(idx);
    } else {
      _num_null_oops ++;
    }
  }
  virtual void do_oop(oop *p) {
    ShouldNotReachHere();
  }
  int num_total_oops() const { return _num_total_oops; }
  int num_null_oops()  const { return _num_null_oops; }
};

ResourceBitMap HeapShared::calculate_oopmap(MemRegion region) {
  assert(UseCompressedOops, "must be");
  size_t num_bits = region.byte_size() / sizeof(narrowOop);
  ResourceBitMap oopmap(num_bits);

  HeapWord* p   = region.start();
  HeapWord* end = region.end();
  FindEmbeddedNonNullPointers finder((narrowOop*)p, &oopmap);

  int num_objs = 0;
  while (p < end) {
    oop o = (oop)p;
    o->oop_iterate(&finder);
    p += o->size();
    ++ num_objs;
  }

  log_info(cds, heap)("calculate_oopmap: objects = %6d, embedded oops = %7d, nulls = %7d",
                      num_objs, finder.num_total_oops(), finder.num_null_oops());
  return oopmap;
}

void HeapShared::init_narrow_oop_decoding(address base, int shift) {
  _narrow_oop_base = base;
  _narrow_oop_shift = shift;
}

// Patch all the embedded oop pointers inside an archived heap region,
// to be consistent with the runtime oop encoding.
class PatchEmbeddedPointers: public BitMapClosure {
  narrowOop* _start;

 public:
  PatchEmbeddedPointers(narrowOop* start) : _start(start) {}

  bool do_bit(size_t offset) {
    narrowOop* p = _start + offset;
    narrowOop v = *p;
    assert(!CompressedOops::is_null(v), "null oops should have been filtered out at dump time");
    oop o = HeapShared::decode_with_archived_oop_encoding_mode(v);
    RawAccess<IS_NOT_NULL>::oop_store(p, o);
    return true;
  }
};

void HeapShared::patch_archived_heap_embedded_pointers(MemRegion region, address oopmap,
                                                       size_t oopmap_size_in_bits) {
  BitMapView bm((BitMap::bm_word_t*)oopmap, oopmap_size_in_bits);

#ifndef PRODUCT
  ResourceMark rm;
  ResourceBitMap checkBm = calculate_oopmap(region);
  assert(bm.is_same(checkBm), "sanity");
#endif

  PatchEmbeddedPointers patcher((narrowOop*)region.start());
  bm.iterate(&patcher);
}

#endif // INCLUDE_CDS_JAVA_HEAP
