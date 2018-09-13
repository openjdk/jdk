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

#ifndef SHARE_VM_MEMORY_HEAPSHARED_HPP
#define SHARE_VM_MEMORY_HEAPSHARED_HPP

#include "classfile/systemDictionary.hpp"
#include "memory/allocation.hpp"
#include "memory/universe.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.hpp"
#include "oops/typeArrayKlass.hpp"
#include "utilities/bitMap.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/resourceHash.hpp"

#if INCLUDE_CDS_JAVA_HEAP
// A dump time sub-graph info for Klass _k. It includes the entry points
// (static fields in _k's mirror) of the archived sub-graphs reachable
// from _k's mirror. It also contains a list of Klasses of the objects
// within the sub-graphs.
class KlassSubGraphInfo: public CHeapObj<mtClass> {
 private:
  KlassSubGraphInfo* _next;
  // The class that contains the static field(s) as the entry point(s)
  // of archived object sub-graph(s).
  Klass* _k;
  // A list of classes need to be loaded and initialized before the archived
  // object sub-graphs can be accessed at runtime.
  GrowableArray<Klass*>* _subgraph_object_klasses;
  // A list of _k's static fields as the entry points of archived sub-graphs.
  // For each entry field, it is a pair of field_offset and field_value.
  GrowableArray<juint>*  _subgraph_entry_fields;

 public:
  KlassSubGraphInfo(Klass* k, KlassSubGraphInfo* next) :
    _next(next), _k(k),  _subgraph_object_klasses(NULL),
    _subgraph_entry_fields(NULL) {}
  ~KlassSubGraphInfo() {
    if (_subgraph_object_klasses != NULL) {
      delete _subgraph_object_klasses;
    }
    if (_subgraph_entry_fields != NULL) {
      delete _subgraph_entry_fields;
    }
  };

  KlassSubGraphInfo* next() { return _next; }
  Klass* klass()            { return _k; }
  GrowableArray<Klass*>* subgraph_object_klasses() {
    return _subgraph_object_klasses;
  }
  GrowableArray<juint>*  subgraph_entry_fields() {
    return _subgraph_entry_fields;
  }
  void add_subgraph_entry_field(int static_field_offset, oop v);
  void add_subgraph_object_klass(Klass *orig_k, Klass *relocated_k);
  int num_subgraph_object_klasses() {
    return _subgraph_object_klasses == NULL ? 0 :
           _subgraph_object_klasses->length();
  }
};

// An archived record of object sub-graphs reachable from static
// fields within _k's mirror. The record is reloaded from the archive
// at runtime.
class ArchivedKlassSubGraphInfoRecord {
 private:
  ArchivedKlassSubGraphInfoRecord* _next;
  Klass* _k;

  // contains pairs of field offset and value for each subgraph entry field
  Array<juint>* _entry_field_records;

  // klasses of objects in archived sub-graphs referenced from the entry points
  // (static fields) in the containing class
  Array<Klass*>* _subgraph_object_klasses;
 public:
  ArchivedKlassSubGraphInfoRecord() :
    _next(NULL), _k(NULL), _entry_field_records(NULL), _subgraph_object_klasses(NULL) {}
  void init(KlassSubGraphInfo* info);
  Klass* klass() { return _k; }
  ArchivedKlassSubGraphInfoRecord* next() { return _next; }
  void set_next(ArchivedKlassSubGraphInfoRecord* next) { _next = next; }
  Array<juint>*  entry_field_records() { return _entry_field_records; }
  Array<Klass*>* subgraph_object_klasses() { return _subgraph_object_klasses; }
};
#endif // INCLUDE_CDS_JAVA_HEAP

class HeapShared: AllStatic {
  friend class VerifySharedOopClosure;
 private:
#if INCLUDE_CDS_JAVA_HEAP
  // This is a list of subgraph infos built at dump time while
  // archiving object subgraphs.
  static KlassSubGraphInfo* _subgraph_info_list;

  // Contains a list of ArchivedKlassSubGraphInfoRecords that is stored
  // in the archive file and reloaded at runtime.
  static int _num_archived_subgraph_info_records;
  static Array<ArchivedKlassSubGraphInfoRecord>* _archived_subgraph_info_records;

  // Archive object sub-graph starting from the given static field
  // in Klass k's mirror.
  static void archive_reachable_objects_from_static_field(
    InstanceKlass* k, const char* klass_name,
    int field_offset, const char* field_name, TRAPS);
  static void verify_subgraph_from_static_field(
    InstanceKlass* k, int field_offset) PRODUCT_RETURN;

  static void verify_reachable_objects_from(oop obj, bool is_archived) PRODUCT_RETURN;

  static KlassSubGraphInfo* find_subgraph_info(Klass *k);
  static KlassSubGraphInfo* get_subgraph_info(Klass *k);
  static int num_of_subgraph_infos();

  static size_t build_archived_subgraph_info_records(int num_records);

  // Used by decode_from_archive
  static address _narrow_oop_base;
  static int     _narrow_oop_shift;

  static bool oop_equals(oop const& p1, oop const& p2) {
    return primitive_equals<oop>(p1, p2);
  }

  static unsigned oop_hash(oop const& p) {
    return primitive_hash<address>((address)p);
  }

  typedef ResourceHashtable<oop, bool,
      HeapShared::oop_hash,
      HeapShared::oop_equals,
      15889, // prime number
      ResourceObj::C_HEAP> SeenObjectsTable;

  static SeenObjectsTable *_seen_objects_table;

  static void init_seen_objects_table() {
    assert(_seen_objects_table == NULL, "must be");
    _seen_objects_table = new (ResourceObj::C_HEAP, mtClass)SeenObjectsTable();
  }
  static void delete_seen_objects_table() {
    assert(_seen_objects_table != NULL, "must be");
    delete _seen_objects_table;
    _seen_objects_table = NULL;
  }

  // Statistics (for one round of start_recording_subgraph ... done_recording_subgraph)
  static int _num_new_walked_objs;
  static int _num_new_archived_objs;
  static int _num_old_recorded_klasses;

  // Statistics (for all archived subgraphs)
  static int _num_total_subgraph_recordings;
  static int _num_total_walked_objs;
  static int _num_total_archived_objs;
  static int _num_total_recorded_klasses;
  static int _num_total_verifications;

  static void start_recording_subgraph(InstanceKlass *k, const char* klass_name);
  static void done_recording_subgraph(InstanceKlass *k, const char* klass_name);

  static bool has_been_seen_during_subgraph_recording(oop obj);
  static void set_has_been_seen_during_subgraph_recording(oop obj);
#endif // INCLUDE_CDS_JAVA_HEAP
 public:
  static char* read_archived_subgraph_infos(char* buffer) NOT_CDS_JAVA_HEAP_RETURN_(buffer);
  static void write_archived_subgraph_infos() NOT_CDS_JAVA_HEAP_RETURN;
  static void initialize_from_archived_subgraph(Klass* k) NOT_CDS_JAVA_HEAP_RETURN;

  // NarrowOops stored in the CDS archive may use a different encoding scheme
  // than Universe::narrow_oop_{base,shift} -- see FileMapInfo::map_heap_regions_impl.
  // To decode them, do not use CompressedOops::decode_not_null. Use this
  // function instead.
  inline static oop decode_from_archive(narrowOop v) NOT_CDS_JAVA_HEAP_RETURN_(NULL);

  static void init_narrow_oop_decoding(address base, int shift) NOT_CDS_JAVA_HEAP_RETURN;

  static void patch_archived_heap_embedded_pointers(MemRegion mem, address  oopmap,
                                                    size_t oopmap_in_bits) NOT_CDS_JAVA_HEAP_RETURN;

  static void init_archivable_static_fields(Thread* THREAD) NOT_CDS_JAVA_HEAP_RETURN;
  static void archive_static_fields(Thread* THREAD) NOT_CDS_JAVA_HEAP_RETURN;

#if INCLUDE_CDS_JAVA_HEAP
  static ResourceBitMap calculate_oopmap(MemRegion region);
  static oop archive_reachable_objects_from(int level, KlassSubGraphInfo* subgraph_info, oop orig_obj, TRAPS);
  static void verify_subgraph_from(oop orig_obj) PRODUCT_RETURN;
#endif
};
#endif // SHARE_VM_MEMORY_HEAPSHARED_HPP
