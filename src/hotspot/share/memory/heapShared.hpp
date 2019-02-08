/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_HEAPSHARED_HPP
#define SHARE_MEMORY_HEAPSHARED_HPP

#include "classfile/compactHashtable.hpp"
#include "classfile/systemDictionary.hpp"
#include "memory/allocation.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/universe.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.hpp"
#include "oops/typeArrayKlass.hpp"
#include "utilities/bitMap.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/resourceHash.hpp"

#if INCLUDE_CDS_JAVA_HEAP
struct ArchivableStaticFieldInfo {
  const char* klass_name;
  const char* field_name;
  InstanceKlass* klass;
  int offset;
  BasicType type;
};

// A dump time sub-graph info for Klass _k. It includes the entry points
// (static fields in _k's mirror) of the archived sub-graphs reachable
// from _k's mirror. It also contains a list of Klasses of the objects
// within the sub-graphs.
class KlassSubGraphInfo: public CHeapObj<mtClass> {
 private:
  // The class that contains the static field(s) as the entry point(s)
  // of archived object sub-graph(s).
  Klass* _k;
  // A list of classes need to be loaded and initialized before the archived
  // object sub-graphs can be accessed at runtime.
  GrowableArray<Klass*>* _subgraph_object_klasses;
  // A list of _k's static fields as the entry points of archived sub-graphs.
  // For each entry field, it is a tuple of field_offset, field_value and
  // is_closed_archive flag.
  GrowableArray<juint>*  _subgraph_entry_fields;

 public:
  KlassSubGraphInfo(Klass* k) :
    _k(k),  _subgraph_object_klasses(NULL),
    _subgraph_entry_fields(NULL) {}
  ~KlassSubGraphInfo() {
    if (_subgraph_object_klasses != NULL) {
      delete _subgraph_object_klasses;
    }
    if (_subgraph_entry_fields != NULL) {
      delete _subgraph_entry_fields;
    }
  };

  Klass* klass()            { return _k; }
  GrowableArray<Klass*>* subgraph_object_klasses() {
    return _subgraph_object_klasses;
  }
  GrowableArray<juint>*  subgraph_entry_fields() {
    return _subgraph_entry_fields;
  }
  void add_subgraph_entry_field(int static_field_offset, oop v,
                                bool is_closed_archive);
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
  Klass* _k;

  // contains pairs of field offset and value for each subgraph entry field
  Array<juint>* _entry_field_records;

  // klasses of objects in archived sub-graphs referenced from the entry points
  // (static fields) in the containing class
  Array<Klass*>* _subgraph_object_klasses;
 public:
  ArchivedKlassSubGraphInfoRecord() :
    _k(NULL), _entry_field_records(NULL), _subgraph_object_klasses(NULL) {}
  void init(KlassSubGraphInfo* info);
  Klass* klass() const { return _k; }
  Array<juint>*  entry_field_records() const { return _entry_field_records; }
  Array<Klass*>* subgraph_object_klasses() const { return _subgraph_object_klasses; }
};
#endif // INCLUDE_CDS_JAVA_HEAP

class HeapShared: AllStatic {
  friend class VerifySharedOopClosure;
 private:

#if INCLUDE_CDS_JAVA_HEAP
  static bool _closed_archive_heap_region_mapped;
  static bool _open_archive_heap_region_mapped;
  static bool _archive_heap_region_fixed;

  static bool oop_equals(oop const& p1, oop const& p2) {
    return oopDesc::equals(p1, p2);
  }
  static unsigned oop_hash(oop const& p);

  typedef ResourceHashtable<oop, oop,
      HeapShared::oop_hash,
      HeapShared::oop_equals,
      15889, // prime number
      ResourceObj::C_HEAP> ArchivedObjectCache;
  static ArchivedObjectCache* _archived_object_cache;

  static bool klass_equals(Klass* const& p1, Klass* const& p2) {
    return primitive_equals<Klass*>(p1, p2);
  }

  static unsigned klass_hash(Klass* const& klass) {
    return primitive_hash<address>((address)klass);
  }

  class DumpTimeKlassSubGraphInfoTable
    : public ResourceHashtable<Klass*, KlassSubGraphInfo,
                               HeapShared::klass_hash,
                               HeapShared::klass_equals,
                               137, // prime number
                               ResourceObj::C_HEAP> {
  public:
    int _count;
  };

public: // solaris compiler wants this for RunTimeKlassSubGraphInfoTable
  inline static bool record_equals_compact_hashtable_entry(
       const ArchivedKlassSubGraphInfoRecord* value, const Klass* key, int len_unused) {
    return (value->klass() == key);
  }

private:
  typedef OffsetCompactHashtable<
    const Klass*,
    const ArchivedKlassSubGraphInfoRecord*,
    record_equals_compact_hashtable_entry
    > RunTimeKlassSubGraphInfoTable;

  static DumpTimeKlassSubGraphInfoTable* _dump_time_subgraph_info_table;
  static RunTimeKlassSubGraphInfoTable _run_time_subgraph_info_table;

  static void check_closed_archive_heap_region_object(InstanceKlass* k,
                                                      Thread* THREAD);

  static void archive_object_subgraphs(ArchivableStaticFieldInfo fields[],
                                       int num,
                                       bool is_closed_archive,
                                       Thread* THREAD);

  // Archive object sub-graph starting from the given static field
  // in Klass k's mirror.
  static void archive_reachable_objects_from_static_field(
    InstanceKlass* k, const char* klass_name,
    int field_offset, const char* field_name,
    bool is_closed_archive, TRAPS);

  static void verify_subgraph_from_static_field(
    InstanceKlass* k, int field_offset) PRODUCT_RETURN;
  static void verify_reachable_objects_from(oop obj, bool is_archived) PRODUCT_RETURN;
  static void verify_subgraph_from(oop orig_obj) PRODUCT_RETURN;

  static KlassSubGraphInfo* get_subgraph_info(Klass *k);

  static void init_subgraph_entry_fields(ArchivableStaticFieldInfo fields[],
                                         int num, Thread* THREAD);

  // Used by decode_from_archive
  static address _narrow_oop_base;
  static int     _narrow_oop_shift;

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

 public:
  static void create_archived_object_cache() {
    _archived_object_cache =
      new (ResourceObj::C_HEAP, mtClass)ArchivedObjectCache();
  }
  static void destroy_archived_object_cache() {
    delete _archived_object_cache;
    _archived_object_cache = NULL;
  }
  static ArchivedObjectCache* archived_object_cache() {
    return _archived_object_cache;
  }

  static oop find_archived_heap_object(oop obj);
  static oop archive_heap_object(oop obj, Thread* THREAD);
  static oop materialize_archived_object(narrowOop v);

  static void archive_klass_objects(Thread* THREAD);

  static void set_archive_heap_region_fixed() {
    _archive_heap_region_fixed = true;
  }
  static bool archive_heap_region_fixed() {
    return _archive_heap_region_fixed;
  }

  static void archive_java_heap_objects(GrowableArray<MemRegion> *closed,
                                        GrowableArray<MemRegion> *open);
  static void copy_closed_archive_heap_objects(GrowableArray<MemRegion> * closed_archive);
  static void copy_open_archive_heap_objects(GrowableArray<MemRegion> * open_archive);

  static oop archive_reachable_objects_from(int level,
                                            KlassSubGraphInfo* subgraph_info,
                                            oop orig_obj,
                                            bool is_closed_archive,
                                            TRAPS);

  static ResourceBitMap calculate_oopmap(MemRegion region);
#endif // INCLUDE_CDS_JAVA_HEAP

 public:
  static bool is_heap_object_archiving_allowed() {
    CDS_JAVA_HEAP_ONLY(return (UseG1GC && UseCompressedOops && UseCompressedClassPointers);)
    NOT_CDS_JAVA_HEAP(return false;)
  }

  static bool is_heap_region(int idx) {
    CDS_JAVA_HEAP_ONLY(return (idx >= MetaspaceShared::first_closed_archive_heap_region &&
                               idx <= MetaspaceShared::last_open_archive_heap_region));
    NOT_CDS_JAVA_HEAP_RETURN_(false);
  }
  static bool is_closed_archive_heap_region(int idx) {
    CDS_JAVA_HEAP_ONLY(return (idx >= MetaspaceShared::first_closed_archive_heap_region &&
                               idx <= MetaspaceShared::last_closed_archive_heap_region));
    NOT_CDS_JAVA_HEAP_RETURN_(false);
  }
  static bool is_open_archive_heap_region(int idx) {
    CDS_JAVA_HEAP_ONLY(return (idx >= MetaspaceShared::first_open_archive_heap_region &&
                               idx <= MetaspaceShared::last_open_archive_heap_region));
    NOT_CDS_JAVA_HEAP_RETURN_(false);
  }

  static void set_closed_archive_heap_region_mapped() {
    CDS_JAVA_HEAP_ONLY(_closed_archive_heap_region_mapped = true);
    NOT_CDS_JAVA_HEAP_RETURN;
  }
  static bool closed_archive_heap_region_mapped() {
    CDS_JAVA_HEAP_ONLY(return _closed_archive_heap_region_mapped);
    NOT_CDS_JAVA_HEAP_RETURN_(false);
  }
  static void set_open_archive_heap_region_mapped() {
    CDS_JAVA_HEAP_ONLY(_open_archive_heap_region_mapped = true);
    NOT_CDS_JAVA_HEAP_RETURN;
  }
  static bool open_archive_heap_region_mapped() {
    CDS_JAVA_HEAP_ONLY(return _open_archive_heap_region_mapped);
    NOT_CDS_JAVA_HEAP_RETURN_(false);
  }

  static void fixup_mapped_heap_regions() NOT_CDS_JAVA_HEAP_RETURN;

  inline static bool is_archived_object(oop p) NOT_CDS_JAVA_HEAP_RETURN_(false);

  static void initialize_from_archived_subgraph(Klass* k) NOT_CDS_JAVA_HEAP_RETURN;

  // NarrowOops stored in the CDS archive may use a different encoding scheme
  // than Universe::narrow_oop_{base,shift} -- see FileMapInfo::map_heap_regions_impl.
  // To decode them, do not use CompressedOops::decode_not_null. Use this
  // function instead.
  inline static oop decode_from_archive(narrowOop v) NOT_CDS_JAVA_HEAP_RETURN_(NULL);

  static void init_narrow_oop_decoding(address base, int shift) NOT_CDS_JAVA_HEAP_RETURN;

  static void patch_archived_heap_embedded_pointers(MemRegion mem, address  oopmap,
                                                    size_t oopmap_in_bits) NOT_CDS_JAVA_HEAP_RETURN;

  static void init_subgraph_entry_fields(Thread* THREAD) NOT_CDS_JAVA_HEAP_RETURN;
  static void write_subgraph_info_table() NOT_CDS_JAVA_HEAP_RETURN;
  static void serialize_subgraph_info_table_header(SerializeClosure* soc) NOT_CDS_JAVA_HEAP_RETURN;
};
#endif // SHARE_MEMORY_HEAPSHARED_HPP
