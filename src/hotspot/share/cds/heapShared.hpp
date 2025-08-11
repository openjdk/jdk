/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_HEAPSHARED_HPP
#define SHARE_CDS_HEAPSHARED_HPP

#include "cds/dumpTimeClassInfo.hpp"
#include "cds/metaspaceShared.hpp"
#include "classfile/compactHashtable.hpp"
#include "classfile/javaClasses.hpp"
#include "gc/shared/gc_globals.hpp"
#include "memory/allocation.hpp"
#include "memory/allStatic.hpp"
#include "oops/compressedOops.hpp"
#include "oops/oop.hpp"
#include "oops/oopHandle.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/resourceHash.hpp"

#if INCLUDE_CDS_JAVA_HEAP
class DumpedInternedStrings;
class FileMapInfo;
class KlassSubGraphInfo;
class MetaspaceObjToOopHandleTable;
class ResourceBitMap;

struct ArchivableStaticFieldInfo;
class ArchiveHeapInfo;

#define ARCHIVED_BOOT_LAYER_CLASS "jdk/internal/module/ArchivedBootLayer"
#define ARCHIVED_BOOT_LAYER_FIELD "archivedBootLayer"

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
  // For each entry field, it is a tuple of field_offset, field_value
  GrowableArray<int>* _subgraph_entry_fields;

  // Does this KlassSubGraphInfo belong to the archived full module graph
  bool _is_full_module_graph;

  // Does this KlassSubGraphInfo references any classes that were loaded while
  // JvmtiExport::is_early_phase()!=true. If so, this KlassSubGraphInfo cannot be
  // used at runtime if JVMTI ClassFileLoadHook is enabled.
  bool _has_non_early_klasses;
  static bool is_non_early_klass(Klass* k);
  static void check_allowed_klass(InstanceKlass* ik);
 public:
  KlassSubGraphInfo(Klass* k, bool is_full_module_graph) :
    _k(k),  _subgraph_object_klasses(nullptr),
    _subgraph_entry_fields(nullptr),
    _is_full_module_graph(is_full_module_graph),
    _has_non_early_klasses(false) {}

  ~KlassSubGraphInfo() {
    if (_subgraph_object_klasses != nullptr) {
      delete _subgraph_object_klasses;
    }
    if (_subgraph_entry_fields != nullptr) {
      delete _subgraph_entry_fields;
    }
  };

  Klass* klass()            { return _k; }
  GrowableArray<Klass*>* subgraph_object_klasses() {
    return _subgraph_object_klasses;
  }
  GrowableArray<int>* subgraph_entry_fields() {
    return _subgraph_entry_fields;
  }
  void add_subgraph_entry_field(int static_field_offset, oop v);
  void add_subgraph_object_klass(Klass *orig_k);
  int num_subgraph_object_klasses() {
    return _subgraph_object_klasses == nullptr ? 0 :
           _subgraph_object_klasses->length();
  }
  bool is_full_module_graph() const { return _is_full_module_graph; }
  bool has_non_early_klasses() const { return _has_non_early_klasses; }
};

// An archived record of object sub-graphs reachable from static
// fields within _k's mirror. The record is reloaded from the archive
// at runtime.
class ArchivedKlassSubGraphInfoRecord {
 private:
  Klass* _k;
  bool _is_full_module_graph;
  bool _has_non_early_klasses;

  // contains pairs of field offset and value for each subgraph entry field
  Array<int>* _entry_field_records;

  // klasses of objects in archived sub-graphs referenced from the entry points
  // (static fields) in the containing class
  Array<Klass*>* _subgraph_object_klasses;
 public:
  ArchivedKlassSubGraphInfoRecord() :
    _k(nullptr), _entry_field_records(nullptr), _subgraph_object_klasses(nullptr) {}
  void init(KlassSubGraphInfo* info);
  Klass* klass() const { return _k; }
  Array<int>* entry_field_records() const { return _entry_field_records; }
  Array<Klass*>* subgraph_object_klasses() const { return _subgraph_object_klasses; }
  bool is_full_module_graph() const { return _is_full_module_graph; }
  bool has_non_early_klasses() const { return _has_non_early_klasses; }
};
#endif // INCLUDE_CDS_JAVA_HEAP

struct LoadedArchiveHeapRegion;

class HeapShared: AllStatic {
  friend class VerifySharedOopClosure;

public:
  static bool is_subgraph_root_class(InstanceKlass* ik);

  // Scratch objects for archiving Klass::java_mirror()
  static oop scratch_java_mirror(BasicType t)     NOT_CDS_JAVA_HEAP_RETURN_(nullptr);
  static oop scratch_java_mirror(Klass* k)        NOT_CDS_JAVA_HEAP_RETURN_(nullptr);
  static oop scratch_java_mirror(oop java_mirror) NOT_CDS_JAVA_HEAP_RETURN_(nullptr);
  static bool is_archived_boot_layer_available(JavaThread* current) NOT_CDS_JAVA_HEAP_RETURN_(false);

private:
#if INCLUDE_CDS_JAVA_HEAP
  static DumpedInternedStrings *_dumped_interned_strings;

  // statistics
  constexpr static int ALLOC_STAT_SLOTS = 16;
  static size_t _alloc_count[ALLOC_STAT_SLOTS];
  static size_t _alloc_size[ALLOC_STAT_SLOTS];
  static size_t _total_obj_count;
  static size_t _total_obj_size; // in HeapWords

  static void count_allocation(size_t size);
  static void print_stats();
public:
  static void debug_trace();
  static unsigned oop_hash(oop const& p);
  static unsigned string_oop_hash(oop const& string) {
    return java_lang_String::hash_code(string);
  }

  class CopyKlassSubGraphInfoToArchive;

  class CachedOopInfo {
    // Used by CDSHeapVerifier.
    oop _orig_referrer;

    // The location of this object inside ArchiveHeapWriter::_buffer
    size_t _buffer_offset;

    // One or more fields in this object are pointing to non-null oops.
    bool _has_oop_pointers;

    // One or more fields in this object are pointing to MetaspaceObj
    bool _has_native_pointers;
  public:
    CachedOopInfo(oop orig_referrer, bool has_oop_pointers)
      : _orig_referrer(orig_referrer),
        _buffer_offset(0),
        _has_oop_pointers(has_oop_pointers),
        _has_native_pointers(false) {}
    oop orig_referrer()             const { return _orig_referrer;   }
    void set_buffer_offset(size_t offset) { _buffer_offset = offset; }
    size_t buffer_offset()          const { return _buffer_offset;   }
    bool has_oop_pointers()         const { return _has_oop_pointers; }
    bool has_native_pointers()      const { return _has_native_pointers; }
    void set_has_native_pointers()        { _has_native_pointers = true; }
  };

private:
  static const int INITIAL_TABLE_SIZE = 15889; // prime number
  static const int MAX_TABLE_SIZE     = 1000000;
  typedef ResizeableResourceHashtable<oop, CachedOopInfo,
      AnyObj::C_HEAP,
      mtClassShared,
      HeapShared::oop_hash> ArchivedObjectCache;
  static ArchivedObjectCache* _archived_object_cache;

  class DumpTimeKlassSubGraphInfoTable
    : public ResourceHashtable<Klass*, KlassSubGraphInfo,
                               137, // prime number
                               AnyObj::C_HEAP,
                               mtClassShared,
                               DumpTimeSharedClassTable_hash> {
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

  static CachedOopInfo make_cached_oop_info(oop obj, oop referrer);
  static ArchivedKlassSubGraphInfoRecord* archive_subgraph_info(KlassSubGraphInfo* info);
  static void archive_object_subgraphs(ArchivableStaticFieldInfo fields[],
                                       bool is_full_module_graph);

  // Archive object sub-graph starting from the given static field
  // in Klass k's mirror.
  static void archive_reachable_objects_from_static_field(
    InstanceKlass* k, const char* klass_name,
    int field_offset, const char* field_name);

  static void verify_subgraph_from_static_field(
    InstanceKlass* k, int field_offset) PRODUCT_RETURN;
  static void verify_reachable_objects_from(oop obj) PRODUCT_RETURN;
  static void verify_subgraph_from(oop orig_obj) PRODUCT_RETURN;
  static void check_special_subgraph_classes();

  static KlassSubGraphInfo* init_subgraph_info(Klass *k, bool is_full_module_graph);
  static KlassSubGraphInfo* get_subgraph_info(Klass *k);

  static void init_subgraph_entry_fields(TRAPS) NOT_CDS_JAVA_HEAP_RETURN;
  static void init_subgraph_entry_fields(ArchivableStaticFieldInfo fields[], TRAPS);

  // UseCompressedOops only: Used by decode_from_archive
  static address _narrow_oop_base;
  static int     _narrow_oop_shift;

  // !UseCompressedOops only: used to relocate pointers to the archived objects
  static ptrdiff_t _runtime_delta;

  typedef ResizeableResourceHashtable<oop, bool,
      AnyObj::C_HEAP,
      mtClassShared,
      HeapShared::oop_hash> SeenObjectsTable;

  static SeenObjectsTable *_seen_objects_table;

  // The "special subgraph" contains all the archived objects that are reachable
  // from the following roots:
  //    - interned strings
  //    - Klass::java_mirror() -- including aot-initialized mirrors such as those of Enum klasses.
  //    - ConstantPool::resolved_references()
  //    - Universe::<xxx>_exception_instance()
  static KlassSubGraphInfo* _dump_time_special_subgraph;              // for collecting info during dump time
  static ArchivedKlassSubGraphInfoRecord* _run_time_special_subgraph; // for initializing classes during run time.

  static GrowableArrayCHeap<oop, mtClassShared>* _pending_roots;
  static GrowableArrayCHeap<OopHandle, mtClassShared>* _root_segments;
  static int _root_segment_max_size_elems;
  static OopHandle _scratch_basic_type_mirrors[T_VOID+1];
  static MetaspaceObjToOopHandleTable* _scratch_objects_table;

  static void init_seen_objects_table() {
    assert(_seen_objects_table == nullptr, "must be");
    _seen_objects_table = new (mtClass)SeenObjectsTable(INITIAL_TABLE_SIZE, MAX_TABLE_SIZE);
  }
  static void delete_seen_objects_table() {
    assert(_seen_objects_table != nullptr, "must be");
    delete _seen_objects_table;
    _seen_objects_table = nullptr;
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

  static void start_recording_subgraph(InstanceKlass *k, const char* klass_name,
                                       bool is_full_module_graph);
  static void done_recording_subgraph(InstanceKlass *k, const char* klass_name);

  static bool has_been_seen_during_subgraph_recording(oop obj);
  static void set_has_been_seen_during_subgraph_recording(oop obj);
  static bool archive_object(oop obj, oop referrer, KlassSubGraphInfo* subgraph_info);

  static void resolve_classes_for_subgraphs(JavaThread* current, ArchivableStaticFieldInfo fields[]);
  static void resolve_classes_for_subgraph_of(JavaThread* current, Klass* k);
  static void clear_archived_roots_of(Klass* k);
  static const ArchivedKlassSubGraphInfoRecord*
               resolve_or_init_classes_for_subgraph_of(Klass* k, bool do_init, TRAPS);
  static void resolve_or_init(const char* klass_name, bool do_init, TRAPS);
  static void resolve_or_init(Klass* k, bool do_init, TRAPS);
  static void init_archived_fields_for(Klass* k, const ArchivedKlassSubGraphInfoRecord* record);

  static int init_loaded_regions(FileMapInfo* mapinfo, LoadedArchiveHeapRegion* loaded_regions,
                                 MemRegion& archive_space);
  static void sort_loaded_regions(LoadedArchiveHeapRegion* loaded_regions, int num_loaded_regions,
                                  uintptr_t buffer);
  static bool load_regions(FileMapInfo* mapinfo, LoadedArchiveHeapRegion* loaded_regions,
                           int num_loaded_regions, uintptr_t buffer);
  static void init_loaded_heap_relocation(LoadedArchiveHeapRegion* reloc_info,
                                          int num_loaded_regions);
  static void fill_failed_loaded_region();
  static void mark_native_pointers(oop orig_obj);
  static bool has_been_archived(oop orig_obj);
  static void prepare_resolved_references();
  static void archive_strings();
  static void archive_subgraphs();

  // PendingOop and PendingOopStack are used for recursively discovering all cacheable
  // heap objects. The recursion is done using PendingOopStack so we won't overflow the
  // C stack with deep reference chains.
  class PendingOop {
    oop _obj;
    oop _referrer;
    int _level;

  public:
    PendingOop() : _obj(nullptr), _referrer(nullptr), _level(-1) {}
    PendingOop(oop obj, oop referrer, int level) : _obj(obj), _referrer(referrer), _level(level) {}

    oop obj()      const { return _obj; }
    oop referrer() const { return _referrer; }
    int level()    const { return _level; }
  };

  class OopFieldPusher;
  using PendingOopStack = GrowableArrayCHeap<PendingOop, mtClassShared>;

  static PendingOop _object_being_archived;
  static bool walk_one_object(PendingOopStack* stack, int level, KlassSubGraphInfo* subgraph_info,
                              oop orig_obj, oop referrer);

 public:
  static void reset_archived_object_states(TRAPS);
  static void create_archived_object_cache() {
    _archived_object_cache =
      new (mtClass)ArchivedObjectCache(INITIAL_TABLE_SIZE, MAX_TABLE_SIZE);
  }
  static void destroy_archived_object_cache() {
    delete _archived_object_cache;
    _archived_object_cache = nullptr;
  }
  static ArchivedObjectCache* archived_object_cache() {
    return _archived_object_cache;
  }

  static int archive_exception_instance(oop exception);

  static bool archive_reachable_objects_from(int level,
                                             KlassSubGraphInfo* subgraph_info,
                                             oop orig_obj);

  static void add_to_dumped_interned_strings(oop string);
  static bool is_dumped_interned_string(oop o);

  // Scratch objects for archiving Klass::java_mirror()
  static void set_scratch_java_mirror(Klass* k, oop mirror);
  static void remove_scratch_objects(Klass* k);
  static void get_pointer_info(oop src_obj, bool& has_oop_pointers, bool& has_native_pointers);
  static void set_has_native_pointers(oop src_obj);

  // We use the HeapShared::roots() array to make sure that objects stored in the
  // archived heap region are not prematurely collected. These roots include:
  //
  //    - mirrors of classes that have not yet been loaded.
  //    - ConstantPool::resolved_references() of classes that have not yet been loaded.
  //    - ArchivedKlassSubGraphInfoRecords that have not been initialized
  //    - java.lang.Module objects that have not yet been added to the module graph
  //
  // When a mirror M becomes referenced by a newly loaded class K, M will be removed
  // from HeapShared::roots() via clear_root(), and K will be responsible for
  // keeping M alive.
  //
  // Other types of roots are also cleared similarly when they become referenced.

  // Dump-time only. Returns the index of the root, which can be used at run time to read
  // the root using get_root(index, ...).
  static int append_root(oop obj);
  static GrowableArrayCHeap<oop, mtClassShared>* pending_roots() { return _pending_roots; }

  // Dump-time and runtime
  static objArrayOop root_segment(int segment_idx);
  static oop get_root(int index, bool clear=false);

  // Run-time only
  static void clear_root(int index);

  static void get_segment_indexes(int index, int& segment_index, int& internal_index);

  static void setup_test_class(const char* test_class_name) PRODUCT_RETURN;
#endif // INCLUDE_CDS_JAVA_HEAP

 public:
  static void write_heap(ArchiveHeapInfo* heap_info) NOT_CDS_JAVA_HEAP_RETURN;
  static objArrayOop scratch_resolved_references(ConstantPool* src);
  static void add_scratch_resolved_references(ConstantPool* src, objArrayOop dest) NOT_CDS_JAVA_HEAP_RETURN;
  static void init_dumping() NOT_CDS_JAVA_HEAP_RETURN;
  static void init_scratch_objects_for_basic_type_mirrors(TRAPS) NOT_CDS_JAVA_HEAP_RETURN;
  static void init_box_classes(TRAPS) NOT_CDS_JAVA_HEAP_RETURN;
  static bool is_heap_region(int idx) {
    CDS_JAVA_HEAP_ONLY(return (idx == MetaspaceShared::hp);)
    NOT_CDS_JAVA_HEAP_RETURN_(false);
  }

  static void resolve_classes(JavaThread* current) NOT_CDS_JAVA_HEAP_RETURN;
  static void initialize_from_archived_subgraph(JavaThread* current, Klass* k) NOT_CDS_JAVA_HEAP_RETURN;

  static void init_for_dumping(TRAPS) NOT_CDS_JAVA_HEAP_RETURN;
  static void write_subgraph_info_table() NOT_CDS_JAVA_HEAP_RETURN;
  static void add_root_segment(objArrayOop segment_oop) NOT_CDS_JAVA_HEAP_RETURN;
  static void init_root_segment_sizes(int max_size_elems) NOT_CDS_JAVA_HEAP_RETURN;
  static void serialize_tables(SerializeClosure* soc) NOT_CDS_JAVA_HEAP_RETURN;

#ifndef PRODUCT
  static bool is_a_test_class_in_unnamed_module(Klass* ik) NOT_CDS_JAVA_HEAP_RETURN_(false);
  static void initialize_test_class_from_archive(TRAPS) NOT_CDS_JAVA_HEAP_RETURN;
#endif

  static void initialize_java_lang_invoke(TRAPS) NOT_CDS_JAVA_HEAP_RETURN;
  static void init_classes_for_special_subgraph(Handle loader, TRAPS) NOT_CDS_JAVA_HEAP_RETURN;

  static bool is_lambda_form_klass(InstanceKlass* ik) NOT_CDS_JAVA_HEAP_RETURN_(false);
  static bool is_lambda_proxy_klass(InstanceKlass* ik) NOT_CDS_JAVA_HEAP_RETURN_(false);
  static bool is_string_concat_klass(InstanceKlass* ik) NOT_CDS_JAVA_HEAP_RETURN_(false);
  static bool is_archivable_hidden_klass(InstanceKlass* ik) NOT_CDS_JAVA_HEAP_RETURN_(false);

  // Used by AOTArtifactFinder
  static void start_scanning_for_oops();
  static void end_scanning_for_oops();
  static void scan_java_class(Klass* k);
  static void scan_java_mirror(oop orig_mirror);
  static void copy_and_rescan_aot_inited_mirror(InstanceKlass* ik);
};

#if INCLUDE_CDS_JAVA_HEAP
class DumpedInternedStrings :
  public ResizeableResourceHashtable<oop, bool,
                           AnyObj::C_HEAP,
                           mtClassShared,
                           HeapShared::string_oop_hash>
{
public:
  DumpedInternedStrings(unsigned size, unsigned max_size) :
    ResizeableResourceHashtable<oop, bool,
                                AnyObj::C_HEAP,
                                mtClassShared,
                                HeapShared::string_oop_hash>(size, max_size) {}
};
#endif

#endif // SHARE_CDS_HEAPSHARED_HPP
