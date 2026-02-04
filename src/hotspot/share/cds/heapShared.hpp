/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotMetaspace.hpp"
#include "cds/dumpTimeClassInfo.hpp"
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
#include "utilities/hashTable.hpp"

#if INCLUDE_CDS_JAVA_HEAP
class DumpedInternedStrings;
class FileMapInfo;
class KlassSubGraphInfo;
class MetaspaceObjToOopHandleTable;
class ResourceBitMap;

struct ArchivableStaticFieldInfo;

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

enum class HeapArchiveMode {
  _uninitialized,
  _mapping,
  _streaming
};

class ArchiveMappedHeapHeader {
  size_t           _ptrmap_start_pos; // The first bit in the ptrmap corresponds to this position in the heap.
  size_t           _oopmap_start_pos; // The first bit in the oopmap corresponds to this position in the heap.
  HeapRootSegments _root_segments;    // Heap root segments info

public:
  ArchiveMappedHeapHeader();
  ArchiveMappedHeapHeader(size_t ptrmap_start_pos,
                          size_t oopmap_start_pos,
                          HeapRootSegments root_segments);

  size_t ptrmap_start_pos() const { return _ptrmap_start_pos; }
  size_t oopmap_start_pos() const { return _oopmap_start_pos; }
  HeapRootSegments root_segments() const { return _root_segments; }

  // This class is trivially copyable and assignable.
  ArchiveMappedHeapHeader(const ArchiveMappedHeapHeader&) = default;
  ArchiveMappedHeapHeader& operator=(const ArchiveMappedHeapHeader&) = default;
};


class ArchiveStreamedHeapHeader {
  size_t _forwarding_offset;                      // Offset of forwarding information in the heap region.
  size_t _roots_offset;                           // Start position for the roots
  size_t _root_highest_object_index_table_offset; // Offset of root dfs depth information
  size_t _num_roots;                              // Number of embedded roots
  size_t _num_archived_objects;                   // The number of archived heap objects

public:
  ArchiveStreamedHeapHeader();
  ArchiveStreamedHeapHeader(size_t forwarding_offset,
                            size_t roots_offset,
                            size_t num_roots,
                            size_t root_highest_object_index_table_offset,
                            size_t num_archived_objects);

  size_t forwarding_offset() const { return _forwarding_offset; }
  size_t roots_offset() const { return _roots_offset; }
  size_t num_roots() const { return _num_roots; }
  size_t root_highest_object_index_table_offset() const { return _root_highest_object_index_table_offset; }
  size_t num_archived_objects() const { return _num_archived_objects; }

  // This class is trivially copyable and assignable.
  ArchiveStreamedHeapHeader(const ArchiveStreamedHeapHeader&) = default;
  ArchiveStreamedHeapHeader& operator=(const ArchiveStreamedHeapHeader&) = default;
};

class ArchiveMappedHeapInfo {
  MemRegion _buffer_region;             // Contains the archived objects to be written into the CDS archive.
  CHeapBitMap _oopmap;
  CHeapBitMap _ptrmap;
  HeapRootSegments _root_segments;
  size_t _oopmap_start_pos;             // How many zeros were removed from the beginning of the bit map?
  size_t _ptrmap_start_pos;             // How many zeros were removed from the beginning of the bit map?

public:
  ArchiveMappedHeapInfo() :
    _buffer_region(),
    _oopmap(128, mtClassShared),
    _ptrmap(128, mtClassShared),
    _root_segments(),
    _oopmap_start_pos(),
    _ptrmap_start_pos() {}
  bool is_used() { return !_buffer_region.is_empty(); }

  MemRegion buffer_region() { return _buffer_region; }
  void set_buffer_region(MemRegion r) { _buffer_region = r; }

  char* buffer_start() { return (char*)_buffer_region.start(); }
  size_t buffer_byte_size() { return _buffer_region.byte_size();    }

  CHeapBitMap* oopmap() { return &_oopmap; }
  CHeapBitMap* ptrmap() { return &_ptrmap; }

  void set_oopmap_start_pos(size_t start_pos) { _oopmap_start_pos = start_pos; }
  void set_ptrmap_start_pos(size_t start_pos) { _ptrmap_start_pos = start_pos; }

  void set_root_segments(HeapRootSegments segments) { _root_segments = segments; };
  HeapRootSegments root_segments() { return _root_segments; }

  ArchiveMappedHeapHeader create_header();
};

class ArchiveStreamedHeapInfo {
  MemRegion _buffer_region;             // Contains the archived objects to be written into the CDS archive.
  CHeapBitMap _oopmap;
  size_t _roots_offset;                 // Offset of the HeapShared::roots() object, from the bottom
                                        // of the archived heap objects, in bytes.
  size_t _num_roots;

  size_t _forwarding_offset;            // Offset of forwarding information from the bottom
  size_t _root_highest_object_index_table_offset; // Offset to root dfs depth information
  size_t _num_archived_objects;         // The number of archived objects written into the CDS archive.

public:
  ArchiveStreamedHeapInfo()
    : _buffer_region(),
      _oopmap(128, mtClassShared),
      _roots_offset(),
      _forwarding_offset(),
      _root_highest_object_index_table_offset(),
      _num_archived_objects() {}

  bool is_used() { return !_buffer_region.is_empty(); }

  void set_buffer_region(MemRegion r) { _buffer_region = r; }
  MemRegion buffer_region() { return _buffer_region; }
  char* buffer_start() { return (char*)_buffer_region.start(); }
  size_t buffer_byte_size() { return _buffer_region.byte_size();    }

  CHeapBitMap* oopmap() { return &_oopmap; }
  void set_roots_offset(size_t n) { _roots_offset = n; }
  size_t roots_offset() { return _roots_offset; }
  void set_num_roots(size_t n) { _num_roots = n; }
  size_t num_roots() { return _num_roots; }
  void set_forwarding_offset(size_t n) { _forwarding_offset = n; }
  void set_root_highest_object_index_table_offset(size_t n) { _root_highest_object_index_table_offset = n; }
  void set_num_archived_objects(size_t n) { _num_archived_objects = n; }
  size_t num_archived_objects() { return _num_archived_objects; }

  ArchiveStreamedHeapHeader create_header();
};

class HeapShared: AllStatic {
  friend class VerifySharedOopClosure;

public:
  static void initialize_loading_mode(HeapArchiveMode mode) NOT_CDS_JAVA_HEAP_RETURN;
  static void initialize_writing_mode() NOT_CDS_JAVA_HEAP_RETURN;

  inline static bool is_loading() NOT_CDS_JAVA_HEAP_RETURN_(false);

  inline static bool is_loading_streaming_mode() NOT_CDS_JAVA_HEAP_RETURN_(false);
  inline static bool is_loading_mapping_mode() NOT_CDS_JAVA_HEAP_RETURN_(false);

  inline static bool is_writing() NOT_CDS_JAVA_HEAP_RETURN_(false);

  inline static bool is_writing_streaming_mode() NOT_CDS_JAVA_HEAP_RETURN_(false);
  inline static bool is_writing_mapping_mode() NOT_CDS_JAVA_HEAP_RETURN_(false);

  static bool is_subgraph_root_class(InstanceKlass* ik);

  // Scratch objects for archiving Klass::java_mirror()
  static oop scratch_java_mirror(BasicType t)     NOT_CDS_JAVA_HEAP_RETURN_(nullptr);
  static oop scratch_java_mirror(Klass* k)        NOT_CDS_JAVA_HEAP_RETURN_(nullptr);
  static oop scratch_java_mirror(oop java_mirror) NOT_CDS_JAVA_HEAP_RETURN_(nullptr);
  static bool is_archived_boot_layer_available(JavaThread* current) NOT_CDS_JAVA_HEAP_RETURN_(false);

  static bool is_archived_heap_in_use() NOT_CDS_JAVA_HEAP_RETURN_(false);
  static bool can_use_archived_heap() NOT_CDS_JAVA_HEAP_RETURN_(false);
  static bool is_too_large_to_archive(size_t size);
  static bool is_string_too_large_to_archive(oop string);
  static bool is_too_large_to_archive(oop obj);

  static void initialize_streaming() NOT_CDS_JAVA_HEAP_RETURN;
  static void enable_gc() NOT_CDS_JAVA_HEAP_RETURN;
  static void materialize_thread_object() NOT_CDS_JAVA_HEAP_RETURN;
  static void add_to_dumped_interned_strings(oop string) NOT_CDS_JAVA_HEAP_RETURN;
  static void finalize_initialization(FileMapInfo* static_mapinfo) NOT_CDS_JAVA_HEAP_RETURN;

private:
#if INCLUDE_CDS_JAVA_HEAP
  static HeapArchiveMode _heap_load_mode;
  static HeapArchiveMode _heap_write_mode;

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
  static unsigned oop_handle_hash(OopHandle const& oh);
  static unsigned oop_handle_hash_raw(OopHandle const& oh);
  static bool oop_handle_equals(const OopHandle& a, const OopHandle& b);
  static unsigned string_oop_hash(oop const& string) {
    return java_lang_String::hash_code(string);
  }

  class CopyKlassSubGraphInfoToArchive;

  class CachedOopInfo {
    // Used by CDSHeapVerifier.
    OopHandle _orig_referrer;

    // The location of this object inside {AOTMappedHeapWriter, AOTStreamedHeapWriter}::_buffer
    size_t _buffer_offset;

    // One or more fields in this object are pointing to non-null oops.
    bool _has_oop_pointers;

    // One or more fields in this object are pointing to MetaspaceObj
    bool _has_native_pointers;
  public:
    CachedOopInfo(OopHandle orig_referrer, bool has_oop_pointers)
      : _orig_referrer(orig_referrer),
        _buffer_offset(0),
        _has_oop_pointers(has_oop_pointers),
        _has_native_pointers(false) {}
    oop orig_referrer() const;
    void set_buffer_offset(size_t offset) { _buffer_offset = offset; }
    size_t buffer_offset()          const { return _buffer_offset;   }
    bool has_oop_pointers()         const { return _has_oop_pointers; }
    bool has_native_pointers()      const { return _has_native_pointers; }
    void set_has_native_pointers()        { _has_native_pointers = true; }
  };

private:
  static const int INITIAL_TABLE_SIZE = 15889; // prime number
  static const int MAX_TABLE_SIZE     = 1000000;
  typedef ResizeableHashTable<OopHandle, CachedOopInfo,
      AnyObj::C_HEAP,
      mtClassShared,
      HeapShared::oop_handle_hash_raw,
      HeapShared::oop_handle_equals> ArchivedObjectCache;
  static ArchivedObjectCache* _archived_object_cache;

  class DumpTimeKlassSubGraphInfoTable
    : public HashTable<Klass*, KlassSubGraphInfo,
                               137, // prime number
                               AnyObj::C_HEAP,
                               mtClassShared,
                               DumpTimeSharedClassTable_hash> {};

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

  typedef ResizeableHashTable<oop, bool,
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
  static size_t _num_new_walked_objs;
  static size_t _num_new_archived_objs;
  static size_t _num_old_recorded_klasses;

  // Statistics (for all archived subgraphs)
  static size_t _num_total_subgraph_recordings;
  static size_t _num_total_walked_objs;
  static size_t _num_total_archived_objs;
  static size_t _num_total_recorded_klasses;
  static size_t _num_total_verifications;

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

  static bool has_been_archived(oop orig_obj);
  static void prepare_resolved_references();
  static void archive_subgraphs();
  static void copy_java_mirror(oop orig_mirror, oop scratch_m);

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

  static CachedOopInfo* get_cached_oop_info(oop orig_obj);

  static int archive_exception_instance(oop exception);

  static bool archive_reachable_objects_from(int level,
                                             KlassSubGraphInfo* subgraph_info,
                                             oop orig_obj);

  static bool is_dumped_interned_string(oop o);

  // Scratch objects for archiving Klass::java_mirror()
  static void set_scratch_java_mirror(Klass* k, oop mirror);
  static void remove_scratch_objects(Klass* k);
  static bool is_metadata_field(oop src_obj, int offset);
  template <typename T> static void do_metadata_offsets(oop src_obj, T callback);
  static void remap_dumped_metadata(oop src_obj, address archived_object);
  inline static void remap_loaded_metadata(oop obj);
  inline static oop maybe_remap_referent(bool is_java_lang_ref, size_t field_offset, oop referent);
  static void get_pointer_info(oop src_obj, bool& has_oop_pointers, bool& has_native_pointers);
  static void set_has_native_pointers(oop src_obj);
  static uintptr_t archive_location(oop src_obj);

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
  static void finish_materialize_objects() NOT_CDS_JAVA_HEAP_RETURN;

  static void write_heap(ArchiveMappedHeapInfo* mapped_heap_info, ArchiveStreamedHeapInfo* streamed_heap_info) NOT_CDS_JAVA_HEAP_RETURN;
  static objArrayOop scratch_resolved_references(ConstantPool* src);
  static void add_scratch_resolved_references(ConstantPool* src, objArrayOop dest) NOT_CDS_JAVA_HEAP_RETURN;
  static void init_dumping() NOT_CDS_JAVA_HEAP_RETURN;
  static void init_scratch_objects_for_basic_type_mirrors(TRAPS) NOT_CDS_JAVA_HEAP_RETURN;
  static void init_box_classes(TRAPS) NOT_CDS_JAVA_HEAP_RETURN;
  static bool is_heap_region(int idx) {
    CDS_JAVA_HEAP_ONLY(return (idx == AOTMetaspace::hp);)
    NOT_CDS_JAVA_HEAP_RETURN_(false);
  }
  static void delete_tables_with_raw_oops() NOT_CDS_JAVA_HEAP_RETURN;

  static void resolve_classes(JavaThread* current) NOT_CDS_JAVA_HEAP_RETURN;
  static void initialize_from_archived_subgraph(JavaThread* current, Klass* k) NOT_CDS_JAVA_HEAP_RETURN;

  static void init_for_dumping(TRAPS) NOT_CDS_JAVA_HEAP_RETURN;
  static void init_heap_writer() NOT_CDS_JAVA_HEAP_RETURN;
  static void write_subgraph_info_table() NOT_CDS_JAVA_HEAP_RETURN;
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

  static void log_heap_roots();

  static intptr_t log_target_location(oop source_oop);
  static void log_oop_info(outputStream* st, oop source_oop, address archived_object_start, address archived_object_end);
  static void log_oop_info(outputStream* st, oop source_oop);
  static void log_oop_details(oop source_oop, address buffered_addr);
};

#endif // SHARE_CDS_HEAPSHARED_HPP
