/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
#include "classfile/classListParser.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/loaderConstraints.hpp"
#include "classfile/placeholders.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "code/codeCache.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/bytecodes.hpp"
#include "logging/log.hpp"
#include "logging/logMessage.hpp"
#include "memory/filemap.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/resourceArea.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/instanceClassLoaderKlass.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayKlass.hpp"
#include "prims/jvmtiRedefineClasses.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/signature.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vm_operations.hpp"
#include "utilities/align.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/hashtable.inline.hpp"
#if INCLUDE_G1GC
#include "gc/g1/g1Allocator.inline.hpp"
#include "gc/g1/g1CollectedHeap.hpp"
#endif

ReservedSpace MetaspaceShared::_shared_rs;
VirtualSpace MetaspaceShared::_shared_vs;
MetaspaceSharedStats MetaspaceShared::_stats;
bool MetaspaceShared::_has_error_classes;
bool MetaspaceShared::_archive_loading_failed = false;
bool MetaspaceShared::_remapped_readwrite = false;
bool MetaspaceShared::_open_archive_heap_region_mapped = false;
address MetaspaceShared::_cds_i2i_entry_code_buffers = NULL;
size_t MetaspaceShared::_cds_i2i_entry_code_buffers_size = 0;
size_t MetaspaceShared::_core_spaces_size = 0;

// The CDS archive is divided into the following regions:
//     mc  - misc code (the method entry trampolines)
//     rw  - read-write metadata
//     ro  - read-only metadata and read-only tables
//     md  - misc data (the c++ vtables)
//     od  - optional data (original class files)
//
//     s0  - shared strings(closed archive heap space) #0
//     s1  - shared strings(closed archive heap space) #1 (may be empty)
//     oa0 - open archive heap space #0
//     oa1 - open archive heap space #1 (may be empty)
//
// The mc, rw, ro, md and od regions are linearly allocated, starting from
// SharedBaseAddress, in the order of mc->rw->ro->md->od. The size of these 5 regions
// are page-aligned, and there's no gap between any consecutive regions.
//
// These 5 regions are populated in the following steps:
// [1] All classes are loaded in MetaspaceShared::preload_classes(). All metadata are
//     temporarily allocated outside of the shared regions. Only the method entry
//     trampolines are written into the mc region.
// [2] ArchiveCompactor copies RW metadata into the rw region.
// [3] ArchiveCompactor copies RO metadata into the ro region.
// [4] SymbolTable, StringTable, SystemDictionary, and a few other read-only data
//     are copied into the ro region as read-only tables.
// [5] C++ vtables are copied into the md region.
// [6] Original class files are copied into the od region.
//
// The s0/s1 and oa0/oa1 regions are populated inside MetaspaceShared::dump_java_heap_objects.
// Their layout is independent of the other 5 regions.

class DumpRegion {
private:
  const char* _name;
  char* _base;
  char* _top;
  char* _end;
  bool _is_packed;

  char* expand_top_to(char* newtop) {
    assert(is_allocatable(), "must be initialized and not packed");
    assert(newtop >= _top, "must not grow backwards");
    if (newtop > _end) {
      MetaspaceShared::report_out_of_space(_name, newtop - _top);
      ShouldNotReachHere();
    }
    MetaspaceShared::commit_shared_space_to(newtop);
    _top = newtop;
    return _top;
  }

public:
  DumpRegion(const char* name) : _name(name), _base(NULL), _top(NULL), _end(NULL), _is_packed(false) {}

  char* allocate(size_t num_bytes, size_t alignment=BytesPerWord) {
    char* p = (char*)align_up(_top, alignment);
    char* newtop = p + align_up(num_bytes, alignment);
    expand_top_to(newtop);
    memset(p, 0, newtop - p);
    return p;
  }

  void append_intptr_t(intptr_t n) {
    assert(is_aligned(_top, sizeof(intptr_t)), "bad alignment");
    intptr_t *p = (intptr_t*)_top;
    char* newtop = _top + sizeof(intptr_t);
    expand_top_to(newtop);
    *p = n;
  }

  char* base()      const { return _base;        }
  char* top()       const { return _top;         }
  char* end()       const { return _end;         }
  size_t reserved() const { return _end - _base; }
  size_t used()     const { return _top - _base; }
  bool is_packed()  const { return _is_packed;   }
  bool is_allocatable() const {
    return !is_packed() && _base != NULL;
  }

  void print(size_t total_bytes) const {
    tty->print_cr("%-3s space: " SIZE_FORMAT_W(9) " [ %4.1f%% of total] out of " SIZE_FORMAT_W(9) " bytes [%5.1f%% used] at " INTPTR_FORMAT,
                  _name, used(), percent_of(used(), total_bytes), reserved(), percent_of(used(), reserved()), p2i(_base));
  }
  void print_out_of_space_msg(const char* failing_region, size_t needed_bytes) {
    tty->print("[%-8s] " PTR_FORMAT " - " PTR_FORMAT " capacity =%9d, allocated =%9d",
               _name, p2i(_base), p2i(_top), int(_end - _base), int(_top - _base));
    if (strcmp(_name, failing_region) == 0) {
      tty->print_cr(" required = %d", int(needed_bytes));
    } else {
      tty->cr();
    }
  }

  void init(const ReservedSpace* rs) {
    _base = _top = rs->base();
    _end = rs->end();
  }
  void init(char* b, char* t, char* e) {
    _base = b;
    _top = t;
    _end = e;
  }

  void pack(DumpRegion* next = NULL) {
    assert(!is_packed(), "sanity");
    _end = (char*)align_up(_top, Metaspace::reserve_alignment());
    _is_packed = true;
    if (next != NULL) {
      next->_base = next->_top = this->_end;
      next->_end = MetaspaceShared::shared_rs()->end();
    }
  }
  bool contains(char* p) {
    return base() <= p && p < top();
  }
};


DumpRegion _mc_region("mc"), _ro_region("ro"), _rw_region("rw"), _md_region("md"), _od_region("od");
size_t _total_string_region_size = 0, _total_open_archive_region_size = 0;

char* MetaspaceShared::misc_code_space_alloc(size_t num_bytes) {
  return _mc_region.allocate(num_bytes);
}

char* MetaspaceShared::read_only_space_alloc(size_t num_bytes) {
  return _ro_region.allocate(num_bytes);
}

void MetaspaceShared::initialize_runtime_shared_and_meta_spaces() {
  assert(UseSharedSpaces, "Must be called when UseSharedSpaces is enabled");

  // If using shared space, open the file that contains the shared space
  // and map in the memory before initializing the rest of metaspace (so
  // the addresses don't conflict)
  address cds_address = NULL;
  FileMapInfo* mapinfo = new FileMapInfo();

  // Open the shared archive file, read and validate the header. If
  // initialization fails, shared spaces [UseSharedSpaces] are
  // disabled and the file is closed.
  // Map in spaces now also
  if (mapinfo->initialize() && map_shared_spaces(mapinfo)) {
    size_t cds_total = core_spaces_size();
    cds_address = (address)mapinfo->header()->region_addr(0);
#ifdef _LP64
    if (Metaspace::using_class_space()) {
      char* cds_end = (char*)(cds_address + cds_total);
      cds_end = (char *)align_up(cds_end, Metaspace::reserve_alignment());
      // If UseCompressedClassPointers is set then allocate the metaspace area
      // above the heap and above the CDS area (if it exists).
      Metaspace::allocate_metaspace_compressed_klass_ptrs(cds_end, cds_address);
      // map_heap_regions() compares the current narrow oop and klass encodings
      // with the archived ones, so it must be done after all encodings are determined.
      mapinfo->map_heap_regions();
    }
    Universe::set_narrow_klass_range(CompressedClassSpaceSize);
#endif // _LP64
  } else {
    assert(!mapinfo->is_open() && !UseSharedSpaces,
           "archive file not closed or shared spaces not disabled.");
  }
}

void MetaspaceShared::initialize_dumptime_shared_and_meta_spaces() {
  assert(DumpSharedSpaces, "should be called for dump time only");
  const size_t reserve_alignment = Metaspace::reserve_alignment();
  bool large_pages = false; // No large pages when dumping the CDS archive.
  char* shared_base = (char*)align_up((char*)SharedBaseAddress, reserve_alignment);

#ifdef _LP64
  // On 64-bit VM, the heap and class space layout will be the same as if
  // you're running in -Xshare:on mode:
  //
  //                              +-- SharedBaseAddress (default = 0x800000000)
  //                              v
  // +-..---------+---------+ ... +----+----+----+----+----+---------------+
  // |    Heap    | Archive |     | MC | RW | RO | MD | OD | class space   |
  // +-..---------+---------+ ... +----+----+----+----+----+---------------+
  // |<--   MaxHeapSize  -->|     |<-- UnscaledClassSpaceMax = 4GB ------->|
  //
  const uint64_t UnscaledClassSpaceMax = (uint64_t(max_juint) + 1);
  const size_t cds_total = align_down(UnscaledClassSpaceMax, reserve_alignment);
#else
  // We don't support archives larger than 256MB on 32-bit due to limited virtual address space.
  size_t cds_total = align_down(256*M, reserve_alignment);
#endif

  // First try to reserve the space at the specified SharedBaseAddress.
  _shared_rs = ReservedSpace(cds_total, reserve_alignment, large_pages, shared_base);
  if (_shared_rs.is_reserved()) {
    assert(shared_base == 0 || _shared_rs.base() == shared_base, "should match");
  } else {
    // Get a mmap region anywhere if the SharedBaseAddress fails.
    _shared_rs = ReservedSpace(cds_total, reserve_alignment, large_pages);
  }
  if (!_shared_rs.is_reserved()) {
    vm_exit_during_initialization("Unable to reserve memory for shared space",
                                  err_msg(SIZE_FORMAT " bytes.", cds_total));
  }

#ifdef _LP64
  // During dump time, we allocate 4GB (UnscaledClassSpaceMax) of space and split it up:
  // + The upper 1 GB is used as the "temporary compressed class space" -- preload_classes()
  //   will store Klasses into this space.
  // + The lower 3 GB is used for the archive -- when preload_classes() is done,
  //   ArchiveCompactor will copy the class metadata into this space, first the RW parts,
  //   then the RO parts.

  assert(UseCompressedOops && UseCompressedClassPointers,
      "UseCompressedOops and UseCompressedClassPointers must be set");

  size_t max_archive_size = align_down(cds_total * 3 / 4, reserve_alignment);
  ReservedSpace tmp_class_space = _shared_rs.last_part(max_archive_size);
  CompressedClassSpaceSize = align_down(tmp_class_space.size(), reserve_alignment);
  _shared_rs = _shared_rs.first_part(max_archive_size);

  // Set up compress class pointers.
  Universe::set_narrow_klass_base((address)_shared_rs.base());
  // Set narrow_klass_shift to be LogKlassAlignmentInBytes. This is consistent
  // with AOT.
  Universe::set_narrow_klass_shift(LogKlassAlignmentInBytes);
  // Set the range of klass addresses to 4GB.
  Universe::set_narrow_klass_range(cds_total);

  Metaspace::initialize_class_space(tmp_class_space);
  tty->print_cr("narrow_klass_base = " PTR_FORMAT ", narrow_klass_shift = %d",
                p2i(Universe::narrow_klass_base()), Universe::narrow_klass_shift());

  tty->print_cr("Allocated temporary class space: " SIZE_FORMAT " bytes at " PTR_FORMAT,
                CompressedClassSpaceSize, p2i(tmp_class_space.base()));
#endif

  // Start with 0 committed bytes. The memory will be committed as needed by
  // MetaspaceShared::commit_shared_space_to().
  if (!_shared_vs.initialize(_shared_rs, 0)) {
    vm_exit_during_initialization("Unable to allocate memory for shared space");
  }

  _mc_region.init(&_shared_rs);
  tty->print_cr("Allocated shared space: " SIZE_FORMAT " bytes at " PTR_FORMAT,
                _shared_rs.size(), p2i(_shared_rs.base()));
}

// Called by universe_post_init()
void MetaspaceShared::post_initialize(TRAPS) {
  if (UseSharedSpaces) {
    int size = FileMapInfo::get_number_of_shared_paths();
    if (size > 0) {
      SystemDictionaryShared::allocate_shared_data_arrays(size, THREAD);
      FileMapInfo::FileMapHeader* header = FileMapInfo::current_info()->header();
      ClassLoaderExt::init_paths_start_index(header->_app_class_paths_start_index);
      ClassLoaderExt::init_app_module_paths_start_index(header->_app_module_paths_start_index);
    }
  }

  if (DumpSharedSpaces) {
    if (SharedArchiveConfigFile) {
      read_extra_data(SharedArchiveConfigFile, THREAD);
    }
  }
}

void MetaspaceShared::read_extra_data(const char* filename, TRAPS) {
  HashtableTextDump reader(filename);
  reader.check_version("VERSION: 1.0");

  while (reader.remain() > 0) {
    int utf8_length;
    int prefix_type = reader.scan_prefix(&utf8_length);
    ResourceMark rm(THREAD);
    char* utf8_buffer = NEW_RESOURCE_ARRAY(char, utf8_length);
    reader.get_utf8(utf8_buffer, utf8_length);

    if (prefix_type == HashtableTextDump::SymbolPrefix) {
      SymbolTable::new_symbol(utf8_buffer, utf8_length, THREAD);
    } else{
      assert(prefix_type == HashtableTextDump::StringPrefix, "Sanity");
      utf8_buffer[utf8_length] = '\0';
      oop s = StringTable::intern(utf8_buffer, THREAD);
    }
  }
}

void MetaspaceShared::commit_shared_space_to(char* newtop) {
  assert(DumpSharedSpaces, "dump-time only");
  char* base = _shared_rs.base();
  size_t need_committed_size = newtop - base;
  size_t has_committed_size = _shared_vs.committed_size();
  if (need_committed_size < has_committed_size) {
    return;
  }

  size_t min_bytes = need_committed_size - has_committed_size;
  size_t preferred_bytes = 1 * M;
  size_t uncommitted = _shared_vs.reserved_size() - has_committed_size;

  size_t commit = MAX2(min_bytes, preferred_bytes);
  assert(commit <= uncommitted, "sanity");

  bool result = _shared_vs.expand_by(commit, false);
  if (!result) {
    vm_exit_during_initialization(err_msg("Failed to expand shared space to " SIZE_FORMAT " bytes",
                                          need_committed_size));
  }

  log_info(cds)("Expanding shared spaces by " SIZE_FORMAT_W(7) " bytes [total " SIZE_FORMAT_W(9)  " bytes ending at %p]",
                commit, _shared_vs.actual_committed_size(), _shared_vs.high());
}

// Read/write a data stream for restoring/preserving metadata pointers and
// miscellaneous data from/to the shared archive file.

void MetaspaceShared::serialize(SerializeClosure* soc) {
  int tag = 0;
  soc->do_tag(--tag);

  // Verify the sizes of various metadata in the system.
  soc->do_tag(sizeof(Method));
  soc->do_tag(sizeof(ConstMethod));
  soc->do_tag(arrayOopDesc::base_offset_in_bytes(T_BYTE));
  soc->do_tag(sizeof(ConstantPool));
  soc->do_tag(sizeof(ConstantPoolCache));
  soc->do_tag(objArrayOopDesc::base_offset_in_bytes());
  soc->do_tag(typeArrayOopDesc::base_offset_in_bytes(T_BYTE));
  soc->do_tag(sizeof(Symbol));

  // Dump/restore miscellaneous metadata.
  Universe::serialize(soc, true);
  soc->do_tag(--tag);

  // Dump/restore references to commonly used names and signatures.
  vmSymbols::serialize(soc);
  soc->do_tag(--tag);

  // Dump/restore the symbol and string tables
  SymbolTable::serialize(soc);
  StringTable::serialize(soc);
  soc->do_tag(--tag);

  serialize_well_known_classes(soc);
  soc->do_tag(--tag);

  soc->do_tag(666);
}

void MetaspaceShared::serialize_well_known_classes(SerializeClosure* soc) {
  java_lang_Class::serialize(soc);
  java_lang_String::serialize(soc);
  java_lang_System::serialize(soc);
  java_lang_ClassLoader::serialize(soc);
  java_lang_Throwable::serialize(soc);
  java_lang_Thread::serialize(soc);
  java_lang_ThreadGroup::serialize(soc);
  java_lang_AssertionStatusDirectives::serialize(soc);
  java_lang_ref_SoftReference::serialize(soc);
  java_lang_invoke_MethodHandle::serialize(soc);
  java_lang_invoke_DirectMethodHandle::serialize(soc);
  java_lang_invoke_MemberName::serialize(soc);
  java_lang_invoke_ResolvedMethodName::serialize(soc);
  java_lang_invoke_LambdaForm::serialize(soc);
  java_lang_invoke_MethodType::serialize(soc);
  java_lang_invoke_CallSite::serialize(soc);
  java_lang_invoke_MethodHandleNatives_CallSiteContext::serialize(soc);
  java_security_AccessControlContext::serialize(soc);
  java_lang_reflect_AccessibleObject::serialize(soc);
  java_lang_reflect_Method::serialize(soc);
  java_lang_reflect_Constructor::serialize(soc);
  java_lang_reflect_Field::serialize(soc);
  java_nio_Buffer::serialize(soc);
  reflect_ConstantPool::serialize(soc);
  reflect_UnsafeStaticFieldAccessorImpl::serialize(soc);
  java_lang_reflect_Parameter::serialize(soc);
  java_lang_Module::serialize(soc);
  java_lang_StackTraceElement::serialize(soc);
  java_lang_StackFrameInfo::serialize(soc);
  java_lang_LiveStackFrameInfo::serialize(soc);
  java_util_concurrent_locks_AbstractOwnableSynchronizer::serialize(soc);
}

address MetaspaceShared::cds_i2i_entry_code_buffers(size_t total_size) {
  if (DumpSharedSpaces) {
    if (_cds_i2i_entry_code_buffers == NULL) {
      _cds_i2i_entry_code_buffers = (address)misc_code_space_alloc(total_size);
      _cds_i2i_entry_code_buffers_size = total_size;
    }
  } else if (UseSharedSpaces) {
    assert(_cds_i2i_entry_code_buffers != NULL, "must already been initialized");
  } else {
    return NULL;
  }

  assert(_cds_i2i_entry_code_buffers_size == total_size, "must not change");
  return _cds_i2i_entry_code_buffers;
}

// CDS code for dumping shared archive.

// Global object for holding classes that have been loaded.  Since this
// is run at a safepoint just before exit, this is the entire set of classes.
static GrowableArray<Klass*>* _global_klass_objects;

static void collect_array_classes(Klass* k) {
  _global_klass_objects->append_if_missing(k);
  if (k->is_array_klass()) {
    // Add in the array classes too
    ArrayKlass* ak = ArrayKlass::cast(k);
    Klass* h = ak->higher_dimension();
    if (h != NULL) {
      h->array_klasses_do(collect_array_classes);
    }
  }
}

class CollectClassesClosure : public KlassClosure {
  void do_klass(Klass* k) {
    if (!(k->is_instance_klass() && InstanceKlass::cast(k)->is_in_error_state())) {
      if (k->is_instance_klass() && InstanceKlass::cast(k)->signers() != NULL) {
        // Mark any class with signers and don't add to the _global_klass_objects
        k->set_has_signer_and_not_archived();
      } else {
        _global_klass_objects->append_if_missing(k);
      }
    }
    if (k->is_array_klass()) {
      // Add in the array classes too
      ArrayKlass* ak = ArrayKlass::cast(k);
      Klass* h = ak->higher_dimension();
      if (h != NULL) {
        h->array_klasses_do(collect_array_classes);
      }
    }
  }
};

static void remove_unshareable_in_classes() {
  for (int i = 0; i < _global_klass_objects->length(); i++) {
    Klass* k = _global_klass_objects->at(i);
    if (!k->is_objArray_klass()) {
      // InstanceKlass and TypeArrayKlass will in turn call remove_unshareable_info
      // on their array classes.
      assert(k->is_instance_klass() || k->is_typeArray_klass(), "must be");
      k->remove_unshareable_info();
    }
  }
}

static void remove_java_mirror_in_classes() {
  for (int i = 0; i < _global_klass_objects->length(); i++) {
    Klass* k = _global_klass_objects->at(i);
    if (!k->is_objArray_klass()) {
      // InstanceKlass and TypeArrayKlass will in turn call remove_unshareable_info
      // on their array classes.
      assert(k->is_instance_klass() || k->is_typeArray_klass(), "must be");
      k->remove_java_mirror();
    }
  }
}

static void clear_basic_type_mirrors() {
  assert(!MetaspaceShared::is_heap_object_archiving_allowed(), "Sanity");
  Universe::set_int_mirror(NULL);
  Universe::set_float_mirror(NULL);
  Universe::set_double_mirror(NULL);
  Universe::set_byte_mirror(NULL);
  Universe::set_bool_mirror(NULL);
  Universe::set_char_mirror(NULL);
  Universe::set_long_mirror(NULL);
  Universe::set_short_mirror(NULL);
  Universe::set_void_mirror(NULL);
}

static void rewrite_nofast_bytecode(Method* method) {
  BytecodeStream bcs(method);
  while (!bcs.is_last_bytecode()) {
    Bytecodes::Code opcode = bcs.next();
    switch (opcode) {
    case Bytecodes::_getfield:      *bcs.bcp() = Bytecodes::_nofast_getfield;      break;
    case Bytecodes::_putfield:      *bcs.bcp() = Bytecodes::_nofast_putfield;      break;
    case Bytecodes::_aload_0:       *bcs.bcp() = Bytecodes::_nofast_aload_0;       break;
    case Bytecodes::_iload: {
      if (!bcs.is_wide()) {
        *bcs.bcp() = Bytecodes::_nofast_iload;
      }
      break;
    }
    default: break;
    }
  }
}

// Walk all methods in the class list to ensure that they won't be modified at
// run time. This includes:
// [1] Rewrite all bytecodes as needed, so that the ConstMethod* will not be modified
//     at run time by RewriteBytecodes/RewriteFrequentPairs
// [2] Assign a fingerprint, so one doesn't need to be assigned at run-time.
static void rewrite_nofast_bytecodes_and_calculate_fingerprints() {
  for (int i = 0; i < _global_klass_objects->length(); i++) {
    Klass* k = _global_klass_objects->at(i);
    if (k->is_instance_klass()) {
      InstanceKlass* ik = InstanceKlass::cast(k);
      for (int i = 0; i < ik->methods()->length(); i++) {
        Method* m = ik->methods()->at(i);
        rewrite_nofast_bytecode(m);
        Fingerprinter fp(m);
        // The side effect of this call sets method's fingerprint field.
        fp.fingerprint();
      }
    }
  }
}

static void relocate_cached_class_file() {
  for (int i = 0; i < _global_klass_objects->length(); i++) {
    Klass* k = _global_klass_objects->at(i);
    if (k->is_instance_klass()) {
      InstanceKlass* ik = InstanceKlass::cast(k);
      JvmtiCachedClassFileData* p = ik->get_archived_class_data();
      if (p != NULL) {
        int size = offset_of(JvmtiCachedClassFileData, data) + p->length;
        JvmtiCachedClassFileData* q = (JvmtiCachedClassFileData*)_od_region.allocate(size);
        q->length = p->length;
        memcpy(q->data, p->data, p->length);
        ik->set_archived_class_data(q);
      }
    }
  }
}

NOT_PRODUCT(
static void assert_not_anonymous_class(InstanceKlass* k) {
  assert(!(k->is_anonymous()), "cannot archive anonymous classes");
}

// Anonymous classes are not stored inside any dictionaries. They are created by
// SystemDictionary::parse_stream() with a non-null host_klass.
static void assert_no_anonymoys_classes_in_dictionaries() {
  ClassLoaderDataGraph::dictionary_classes_do(assert_not_anonymous_class);
})

// Objects of the Metadata types (such as Klass and ConstantPool) have C++ vtables.
// (In GCC this is the field <Type>::_vptr, i.e., first word in the object.)
//
// Addresses of the vtables and the methods may be different across JVM runs,
// if libjvm.so is dynamically loaded at a different base address.
//
// To ensure that the Metadata objects in the CDS archive always have the correct vtable:
//
// + at dump time:  we redirect the _vptr to point to our own vtables inside
//                  the CDS image
// + at run time:   we clone the actual contents of the vtables from libjvm.so
//                  into our own tables.

// Currently, the archive contain ONLY the following types of objects that have C++ vtables.
#define CPP_VTABLE_PATCH_TYPES_DO(f) \
  f(ConstantPool) \
  f(InstanceKlass) \
  f(InstanceClassLoaderKlass) \
  f(InstanceMirrorKlass) \
  f(InstanceRefKlass) \
  f(Method) \
  f(ObjArrayKlass) \
  f(TypeArrayKlass)

class CppVtableInfo {
  intptr_t _vtable_size;
  intptr_t _cloned_vtable[1];
public:
  static int num_slots(int vtable_size) {
    return 1 + vtable_size; // Need to add the space occupied by _vtable_size;
  }
  int vtable_size()           { return int(uintx(_vtable_size)); }
  void set_vtable_size(int n) { _vtable_size = intptr_t(n); }
  intptr_t* cloned_vtable()   { return &_cloned_vtable[0]; }
  void zero()                 { memset(_cloned_vtable, 0, sizeof(intptr_t) * vtable_size()); }
  // Returns the address of the next CppVtableInfo that can be placed immediately after this CppVtableInfo
  static size_t byte_size(int vtable_size) {
    CppVtableInfo i;
    return pointer_delta(&i._cloned_vtable[vtable_size], &i, sizeof(u1));
  }
};

template <class T> class CppVtableCloner : public T {
  static intptr_t* vtable_of(Metadata& m) {
    return *((intptr_t**)&m);
  }
  static CppVtableInfo* _info;

  static int get_vtable_length(const char* name);

public:
  // Allocate and initialize the C++ vtable, starting from top, but do not go past end.
  static intptr_t* allocate(const char* name);

  // Clone the vtable to ...
  static intptr_t* clone_vtable(const char* name, CppVtableInfo* info);

  static void zero_vtable_clone() {
    assert(DumpSharedSpaces, "dump-time only");
    _info->zero();
  }

  // Switch the vtable pointer to point to the cloned vtable.
  static void patch(Metadata* obj) {
    assert(DumpSharedSpaces, "dump-time only");
    *(void**)obj = (void*)(_info->cloned_vtable());
  }

  static bool is_valid_shared_object(const T* obj) {
    intptr_t* vptr = *(intptr_t**)obj;
    return vptr == _info->cloned_vtable();
  }
};

template <class T> CppVtableInfo* CppVtableCloner<T>::_info = NULL;

template <class T>
intptr_t* CppVtableCloner<T>::allocate(const char* name) {
  assert(is_aligned(_md_region.top(), sizeof(intptr_t)), "bad alignment");
  int n = get_vtable_length(name);
  _info = (CppVtableInfo*)_md_region.allocate(CppVtableInfo::byte_size(n), sizeof(intptr_t));
  _info->set_vtable_size(n);

  intptr_t* p = clone_vtable(name, _info);
  assert((char*)p == _md_region.top(), "must be");

  return p;
}

template <class T>
intptr_t* CppVtableCloner<T>::clone_vtable(const char* name, CppVtableInfo* info) {
  if (!DumpSharedSpaces) {
    assert(_info == 0, "_info is initialized only at dump time");
    _info = info; // Remember it -- it will be used by MetaspaceShared::is_valid_shared_method()
  }
  T tmp; // Allocate temporary dummy metadata object to get to the original vtable.
  int n = info->vtable_size();
  intptr_t* srcvtable = vtable_of(tmp);
  intptr_t* dstvtable = info->cloned_vtable();

  // We already checked (and, if necessary, adjusted n) when the vtables were allocated, so we are
  // safe to do memcpy.
  log_debug(cds, vtables)("Copying %3d vtable entries for %s", n, name);
  memcpy(dstvtable, srcvtable, sizeof(intptr_t) * n);
  return dstvtable + n;
}

// To determine the size of the vtable for each type, we use the following
// trick by declaring 2 subclasses:
//
//   class CppVtableTesterA: public InstanceKlass {virtual int   last_virtual_method() {return 1;}    };
//   class CppVtableTesterB: public InstanceKlass {virtual void* last_virtual_method() {return NULL}; };
//
// CppVtableTesterA and CppVtableTesterB's vtables have the following properties:
// - Their size (N+1) is exactly one more than the size of InstanceKlass's vtable (N)
// - The first N entries have are exactly the same as in InstanceKlass's vtable.
// - Their last entry is different.
//
// So to determine the value of N, we just walk CppVtableTesterA and CppVtableTesterB's tables
// and find the first entry that's different.
//
// This works on all C++ compilers supported by Oracle, but you may need to tweak it for more
// esoteric compilers.

template <class T> class CppVtableTesterB: public T {
public:
  virtual int last_virtual_method() {return 1;}
};

template <class T> class CppVtableTesterA : public T {
public:
  virtual void* last_virtual_method() {
    // Make this different than CppVtableTesterB::last_virtual_method so the C++
    // compiler/linker won't alias the two functions.
    return NULL;
  }
};

template <class T>
int CppVtableCloner<T>::get_vtable_length(const char* name) {
  CppVtableTesterA<T> a;
  CppVtableTesterB<T> b;

  intptr_t* avtable = vtable_of(a);
  intptr_t* bvtable = vtable_of(b);

  // Start at slot 1, because slot 0 may be RTTI (on Solaris/Sparc)
  int vtable_len = 1;
  for (; ; vtable_len++) {
    if (avtable[vtable_len] != bvtable[vtable_len]) {
      break;
    }
  }
  log_debug(cds, vtables)("Found   %3d vtable entries for %s", vtable_len, name);

  return vtable_len;
}

#define ALLOC_CPP_VTABLE_CLONE(c) \
  CppVtableCloner<c>::allocate(#c);

#define CLONE_CPP_VTABLE(c) \
  p = CppVtableCloner<c>::clone_vtable(#c, (CppVtableInfo*)p);

#define ZERO_CPP_VTABLE(c) \
 CppVtableCloner<c>::zero_vtable_clone();

// This can be called at both dump time and run time.
intptr_t* MetaspaceShared::clone_cpp_vtables(intptr_t* p) {
  assert(DumpSharedSpaces || UseSharedSpaces, "sanity");
  CPP_VTABLE_PATCH_TYPES_DO(CLONE_CPP_VTABLE);
  return p;
}

void MetaspaceShared::zero_cpp_vtable_clones_for_writing() {
  assert(DumpSharedSpaces, "dump-time only");
  CPP_VTABLE_PATCH_TYPES_DO(ZERO_CPP_VTABLE);
}

// Allocate and initialize the C++ vtables, starting from top, but do not go past end.
void MetaspaceShared::allocate_cpp_vtable_clones() {
  assert(DumpSharedSpaces, "dump-time only");
  // Layout (each slot is a intptr_t):
  //   [number of slots in the first vtable = n1]
  //   [ <n1> slots for the first vtable]
  //   [number of slots in the first second = n2]
  //   [ <n2> slots for the second vtable]
  //   ...
  // The order of the vtables is the same as the CPP_VTAB_PATCH_TYPES_DO macro.
  CPP_VTABLE_PATCH_TYPES_DO(ALLOC_CPP_VTABLE_CLONE);
}

// Switch the vtable pointer to point to the cloned vtable. We assume the
// vtable pointer is in first slot in object.
void MetaspaceShared::patch_cpp_vtable_pointers() {
  int n = _global_klass_objects->length();
  for (int i = 0; i < n; i++) {
    Klass* obj = _global_klass_objects->at(i);
    if (obj->is_instance_klass()) {
      InstanceKlass* ik = InstanceKlass::cast(obj);
      if (ik->is_class_loader_instance_klass()) {
        CppVtableCloner<InstanceClassLoaderKlass>::patch(ik);
      } else if (ik->is_reference_instance_klass()) {
        CppVtableCloner<InstanceRefKlass>::patch(ik);
      } else if (ik->is_mirror_instance_klass()) {
        CppVtableCloner<InstanceMirrorKlass>::patch(ik);
      } else {
        CppVtableCloner<InstanceKlass>::patch(ik);
      }
      ConstantPool* cp = ik->constants();
      CppVtableCloner<ConstantPool>::patch(cp);
      for (int j = 0; j < ik->methods()->length(); j++) {
        Method* m = ik->methods()->at(j);
        CppVtableCloner<Method>::patch(m);
        assert(CppVtableCloner<Method>::is_valid_shared_object(m), "must be");
      }
    } else if (obj->is_objArray_klass()) {
      CppVtableCloner<ObjArrayKlass>::patch(obj);
    } else {
      assert(obj->is_typeArray_klass(), "sanity");
      CppVtableCloner<TypeArrayKlass>::patch(obj);
    }
  }
}

bool MetaspaceShared::is_valid_shared_method(const Method* m) {
  assert(is_in_shared_metaspace(m), "must be");
  return CppVtableCloner<Method>::is_valid_shared_object(m);
}

// Closure for serializing initialization data out to a data area to be
// written to the shared file.

class WriteClosure : public SerializeClosure {
private:
  DumpRegion* _dump_region;

public:
  WriteClosure(DumpRegion* r) {
    _dump_region = r;
  }

  void do_ptr(void** p) {
    _dump_region->append_intptr_t((intptr_t)*p);
  }

  void do_u4(u4* p) {
    void* ptr = (void*)(uintx(*p));
    do_ptr(&ptr);
  }

  void do_tag(int tag) {
    _dump_region->append_intptr_t((intptr_t)tag);
  }

  void do_oop(oop* o) {
    if (*o == NULL) {
      _dump_region->append_intptr_t(0);
    } else {
      assert(MetaspaceShared::is_heap_object_archiving_allowed(),
             "Archiving heap object is not allowed");
      _dump_region->append_intptr_t(
        (intptr_t)CompressedOops::encode_not_null(*o));
    }
  }

  void do_region(u_char* start, size_t size) {
    assert((intptr_t)start % sizeof(intptr_t) == 0, "bad alignment");
    assert(size % sizeof(intptr_t) == 0, "bad size");
    do_tag((int)size);
    while (size > 0) {
      _dump_region->append_intptr_t(*(intptr_t*)start);
      start += sizeof(intptr_t);
      size -= sizeof(intptr_t);
    }
  }

  bool reading() const { return false; }
};

// This is for dumping detailed statistics for the allocations
// in the shared spaces.
class DumpAllocStats : public ResourceObj {
public:

  // Here's poor man's enum inheritance
#define SHAREDSPACE_OBJ_TYPES_DO(f) \
  METASPACE_OBJ_TYPES_DO(f) \
  f(SymbolHashentry) \
  f(SymbolBucket) \
  f(StringHashentry) \
  f(StringBucket) \
  f(Other)

  enum Type {
    // Types are MetaspaceObj::ClassType, MetaspaceObj::SymbolType, etc
    SHAREDSPACE_OBJ_TYPES_DO(METASPACE_OBJ_TYPE_DECLARE)
    _number_of_types
  };

  static const char * type_name(Type type) {
    switch(type) {
    SHAREDSPACE_OBJ_TYPES_DO(METASPACE_OBJ_TYPE_NAME_CASE)
    default:
      ShouldNotReachHere();
      return NULL;
    }
  }

public:
  enum { RO = 0, RW = 1 };

  int _counts[2][_number_of_types];
  int _bytes [2][_number_of_types];

  DumpAllocStats() {
    memset(_counts, 0, sizeof(_counts));
    memset(_bytes,  0, sizeof(_bytes));
  };

  void record(MetaspaceObj::Type type, int byte_size, bool read_only) {
    assert(int(type) >= 0 && type < MetaspaceObj::_number_of_types, "sanity");
    int which = (read_only) ? RO : RW;
    _counts[which][type] ++;
    _bytes [which][type] += byte_size;
  }

  void record_other_type(int byte_size, bool read_only) {
    int which = (read_only) ? RO : RW;
    _bytes [which][OtherType] += byte_size;
  }
  void print_stats(int ro_all, int rw_all, int mc_all, int md_all);
};

void DumpAllocStats::print_stats(int ro_all, int rw_all, int mc_all, int md_all) {
  // Calculate size of data that was not allocated by Metaspace::allocate()
  MetaspaceSharedStats *stats = MetaspaceShared::stats();

  // symbols
  _counts[RO][SymbolHashentryType] = stats->symbol.hashentry_count;
  _bytes [RO][SymbolHashentryType] = stats->symbol.hashentry_bytes;

  _counts[RO][SymbolBucketType] = stats->symbol.bucket_count;
  _bytes [RO][SymbolBucketType] = stats->symbol.bucket_bytes;

  // strings
  _counts[RO][StringHashentryType] = stats->string.hashentry_count;
  _bytes [RO][StringHashentryType] = stats->string.hashentry_bytes;

  _counts[RO][StringBucketType] = stats->string.bucket_count;
  _bytes [RO][StringBucketType] = stats->string.bucket_bytes;

  // TODO: count things like dictionary, vtable, etc
  _bytes[RW][OtherType] += mc_all + md_all;
  rw_all += mc_all + md_all; // mc/md are mapped Read/Write

  // prevent divide-by-zero
  if (ro_all < 1) {
    ro_all = 1;
  }
  if (rw_all < 1) {
    rw_all = 1;
  }

  int all_ro_count = 0;
  int all_ro_bytes = 0;
  int all_rw_count = 0;
  int all_rw_bytes = 0;

// To make fmt_stats be a syntactic constant (for format warnings), use #define.
#define fmt_stats "%-20s: %8d %10d %5.1f | %8d %10d %5.1f | %8d %10d %5.1f"
  const char *sep = "--------------------+---------------------------+---------------------------+--------------------------";
  const char *hdr = "                        ro_cnt   ro_bytes     % |   rw_cnt   rw_bytes     % |  all_cnt  all_bytes     %";

  LogMessage(cds) msg;

  msg.info("Detailed metadata info (excluding od/st regions; rw stats include md/mc regions):");
  msg.info("%s", hdr);
  msg.info("%s", sep);
  for (int type = 0; type < int(_number_of_types); type ++) {
    const char *name = type_name((Type)type);
    int ro_count = _counts[RO][type];
    int ro_bytes = _bytes [RO][type];
    int rw_count = _counts[RW][type];
    int rw_bytes = _bytes [RW][type];
    int count = ro_count + rw_count;
    int bytes = ro_bytes + rw_bytes;

    double ro_perc = percent_of(ro_bytes, ro_all);
    double rw_perc = percent_of(rw_bytes, rw_all);
    double perc    = percent_of(bytes, ro_all + rw_all);

    msg.info(fmt_stats, name,
                         ro_count, ro_bytes, ro_perc,
                         rw_count, rw_bytes, rw_perc,
                         count, bytes, perc);

    all_ro_count += ro_count;
    all_ro_bytes += ro_bytes;
    all_rw_count += rw_count;
    all_rw_bytes += rw_bytes;
  }

  int all_count = all_ro_count + all_rw_count;
  int all_bytes = all_ro_bytes + all_rw_bytes;

  double all_ro_perc = percent_of(all_ro_bytes, ro_all);
  double all_rw_perc = percent_of(all_rw_bytes, rw_all);
  double all_perc    = percent_of(all_bytes, ro_all + rw_all);

  msg.info("%s", sep);
  msg.info(fmt_stats, "Total",
                       all_ro_count, all_ro_bytes, all_ro_perc,
                       all_rw_count, all_rw_bytes, all_rw_perc,
                       all_count, all_bytes, all_perc);

  assert(all_ro_bytes == ro_all, "everything should have been counted");
  assert(all_rw_bytes == rw_all, "everything should have been counted");

#undef fmt_stats
}

// Populate the shared space.

class VM_PopulateDumpSharedSpace: public VM_Operation {
private:
  GrowableArray<MemRegion> *_closed_archive_heap_regions;
  GrowableArray<MemRegion> *_open_archive_heap_regions;

  void dump_java_heap_objects() NOT_CDS_JAVA_HEAP_RETURN;
  void dump_symbols();
  char* dump_read_only_tables();
  void print_region_stats();
  void print_heap_region_stats(GrowableArray<MemRegion> *heap_mem,
                               const char *name, const size_t total_size);
public:

  VMOp_Type type() const { return VMOp_PopulateDumpSharedSpace; }
  void doit();   // outline because gdb sucks
  static void write_region(FileMapInfo* mapinfo, int region, DumpRegion* space, bool read_only,  bool allow_exec);
  bool allow_nested_vm_operations() const { return true; }
}; // class VM_PopulateDumpSharedSpace

class SortedSymbolClosure: public SymbolClosure {
  GrowableArray<Symbol*> _symbols;
  virtual void do_symbol(Symbol** sym) {
    assert((*sym)->is_permanent(), "archived symbols must be permanent");
    _symbols.append(*sym);
  }
  static int compare_symbols_by_address(Symbol** a, Symbol** b) {
    if (a[0] < b[0]) {
      return -1;
    } else if (a[0] == b[0]) {
      return 0;
    } else {
      return 1;
    }
  }

public:
  SortedSymbolClosure() {
    SymbolTable::symbols_do(this);
    _symbols.sort(compare_symbols_by_address);
  }
  GrowableArray<Symbol*>* get_sorted_symbols() {
    return &_symbols;
  }
};

// ArchiveCompactor --
//
// This class is the central piece of shared archive compaction -- all metaspace data are
// initially allocated outside of the shared regions. ArchiveCompactor copies the
// metaspace data into their final location in the shared regions.

class ArchiveCompactor : AllStatic {
  static DumpAllocStats* _alloc_stats;
  static SortedSymbolClosure* _ssc;

  static unsigned my_hash(const address& a) {
    return primitive_hash<address>(a);
  }
  static bool my_equals(const address& a0, const address& a1) {
    return primitive_equals<address>(a0, a1);
  }
  typedef ResourceHashtable<
      address, address,
      ArchiveCompactor::my_hash,   // solaris compiler doesn't like: primitive_hash<address>
      ArchiveCompactor::my_equals, // solaris compiler doesn't like: primitive_equals<address>
      16384, ResourceObj::C_HEAP> RelocationTable;
  static RelocationTable* _new_loc_table;

public:
  static void initialize() {
    _alloc_stats = new(ResourceObj::C_HEAP, mtInternal)DumpAllocStats;
    _new_loc_table = new(ResourceObj::C_HEAP, mtInternal)RelocationTable;
  }
  static DumpAllocStats* alloc_stats() {
    return _alloc_stats;
  }

  static void allocate(MetaspaceClosure::Ref* ref, bool read_only) {
    address obj = ref->obj();
    int bytes = ref->size() * BytesPerWord;
    char* p;
    size_t alignment = BytesPerWord;
    char* oldtop;
    char* newtop;

    if (read_only) {
      oldtop = _ro_region.top();
      p = _ro_region.allocate(bytes, alignment);
      newtop = _ro_region.top();
    } else {
      oldtop = _rw_region.top();
      p = _rw_region.allocate(bytes, alignment);
      newtop = _rw_region.top();
    }
    memcpy(p, obj, bytes);
    bool isnew = _new_loc_table->put(obj, (address)p);
    log_trace(cds)("Copy: " PTR_FORMAT " ==> " PTR_FORMAT " %d", p2i(obj), p2i(p), bytes);
    assert(isnew, "must be");

    _alloc_stats->record(ref->msotype(), int(newtop - oldtop), read_only);
    if (ref->msotype() == MetaspaceObj::SymbolType) {
      uintx delta = MetaspaceShared::object_delta(p);
      if (delta > MAX_SHARED_DELTA) {
        // This is just a sanity check and should not appear in any real world usage. This
        // happens only if you allocate more than 2GB of Symbols and would require
        // millions of shared classes.
        vm_exit_during_initialization("Too many Symbols in the CDS archive",
                                      "Please reduce the number of shared classes.");
      }
    }
  }

  static address get_new_loc(MetaspaceClosure::Ref* ref) {
    address* pp = _new_loc_table->get(ref->obj());
    assert(pp != NULL, "must be");
    return *pp;
  }

private:
  // Makes a shallow copy of visited MetaspaceObj's
  class ShallowCopier: public UniqueMetaspaceClosure {
    bool _read_only;
  public:
    ShallowCopier(bool read_only) : _read_only(read_only) {}

    virtual void do_unique_ref(Ref* ref, bool read_only) {
      if (read_only == _read_only) {
        allocate(ref, read_only);
      }
    }
  };

  // Relocate embedded pointers within a MetaspaceObj's shallow copy
  class ShallowCopyEmbeddedRefRelocator: public UniqueMetaspaceClosure {
  public:
    virtual void do_unique_ref(Ref* ref, bool read_only) {
      address new_loc = get_new_loc(ref);
      RefRelocator refer;
      ref->metaspace_pointers_do_at(&refer, new_loc);
    }
  };

  // Relocate a reference to point to its shallow copy
  class RefRelocator: public MetaspaceClosure {
  public:
    virtual bool do_ref(Ref* ref, bool read_only) {
      if (ref->not_null()) {
        ref->update(get_new_loc(ref));
      }
      return false; // Do not recurse.
    }
  };

#ifdef ASSERT
  class IsRefInArchiveChecker: public MetaspaceClosure {
  public:
    virtual bool do_ref(Ref* ref, bool read_only) {
      if (ref->not_null()) {
        char* obj = (char*)ref->obj();
        assert(_ro_region.contains(obj) || _rw_region.contains(obj),
               "must be relocated to point to CDS archive");
      }
      return false; // Do not recurse.
    }
  };
#endif

public:
  static void copy_and_compact() {
    // We should no longer allocate anything from the metaspace, so that
    // we can have a stable set of MetaspaceObjs to work with.
    Metaspace::freeze();

    ResourceMark rm;
    SortedSymbolClosure the_ssc; // StackObj
    _ssc = &the_ssc;

    tty->print_cr("Scanning all metaspace objects ... ");
    {
      // allocate and shallow-copy RW objects, immediately following the MC region
      tty->print_cr("Allocating RW objects ... ");
      _mc_region.pack(&_rw_region);

      ResourceMark rm;
      ShallowCopier rw_copier(false);
      iterate_roots(&rw_copier);
    }
    {
      // allocate and shallow-copy of RO object, immediately following the RW region
      tty->print_cr("Allocating RO objects ... ");
      _rw_region.pack(&_ro_region);

      ResourceMark rm;
      ShallowCopier ro_copier(true);
      iterate_roots(&ro_copier);
    }
    {
      tty->print_cr("Relocating embedded pointers ... ");
      ResourceMark rm;
      ShallowCopyEmbeddedRefRelocator emb_reloc;
      iterate_roots(&emb_reloc);
    }
    {
      tty->print_cr("Relocating external roots ... ");
      ResourceMark rm;
      RefRelocator ext_reloc;
      iterate_roots(&ext_reloc);
    }

#ifdef ASSERT
    {
      tty->print_cr("Verifying external roots ... ");
      ResourceMark rm;
      IsRefInArchiveChecker checker;
      iterate_roots(&checker);
    }
#endif


    // cleanup
    _ssc = NULL;
  }

  // We must relocate the System::_well_known_klasses only after we have copied the
  // java objects in during dump_java_heap_objects(): during the object copy, we operate on
  // old objects which assert that their klass is the original klass.
  static void relocate_well_known_klasses() {
    {
      tty->print_cr("Relocating SystemDictionary::_well_known_klasses[] ... ");
      ResourceMark rm;
      RefRelocator ext_reloc;
      SystemDictionary::well_known_klasses_do(&ext_reloc);
    }
    // NOTE: after this point, we shouldn't have any globals that can reach the old
    // objects.

    // We cannot use any of the objects in the heap anymore (except for the objects
    // in the CDS shared string regions) because their headers no longer point to
    // valid Klasses.
  }

  static void iterate_roots(MetaspaceClosure* it) {
    GrowableArray<Symbol*>* symbols = _ssc->get_sorted_symbols();
    for (int i=0; i<symbols->length(); i++) {
      it->push(symbols->adr_at(i));
    }
    if (_global_klass_objects != NULL) {
      // Need to fix up the pointers
      for (int i = 0; i < _global_klass_objects->length(); i++) {
        // NOTE -- this requires that the vtable is NOT yet patched, or else we are hosed.
        it->push(_global_klass_objects->adr_at(i));
      }
    }
    FileMapInfo::metaspace_pointers_do(it);
    SystemDictionary::classes_do(it);
    Universe::metaspace_pointers_do(it);
    SymbolTable::metaspace_pointers_do(it);
    vmSymbols::metaspace_pointers_do(it);
  }

  static Klass* get_relocated_klass(Klass* orig_klass) {
    assert(DumpSharedSpaces, "dump time only");
    address* pp = _new_loc_table->get((address)orig_klass);
    assert(pp != NULL, "must be");
    Klass* klass = (Klass*)(*pp);
    assert(klass->is_klass(), "must be");
    return klass;
  }
};

DumpAllocStats* ArchiveCompactor::_alloc_stats;
SortedSymbolClosure* ArchiveCompactor::_ssc;
ArchiveCompactor::RelocationTable* ArchiveCompactor::_new_loc_table;

void VM_PopulateDumpSharedSpace::write_region(FileMapInfo* mapinfo, int region_idx,
                                              DumpRegion* dump_region, bool read_only,  bool allow_exec) {
  mapinfo->write_region(region_idx, dump_region->base(), dump_region->used(), read_only, allow_exec);
}

void VM_PopulateDumpSharedSpace::dump_symbols() {
  tty->print_cr("Dumping symbol table ...");

  NOT_PRODUCT(SymbolTable::verify());
  SymbolTable::write_to_archive();
}

char* VM_PopulateDumpSharedSpace::dump_read_only_tables() {
  char* oldtop = _ro_region.top();
  // Reorder the system dictionary. Moving the symbols affects
  // how the hash table indices are calculated.
  SystemDictionary::reorder_dictionary_for_sharing();

  tty->print("Removing java_mirror ... ");
  if (!MetaspaceShared::is_heap_object_archiving_allowed()) {
    clear_basic_type_mirrors();
  }
  remove_java_mirror_in_classes();
  tty->print_cr("done. ");
  NOT_PRODUCT(SystemDictionary::verify();)

  size_t buckets_bytes = SystemDictionary::count_bytes_for_buckets();
  char* buckets_top = _ro_region.allocate(buckets_bytes, sizeof(intptr_t));
  SystemDictionary::copy_buckets(buckets_top, _ro_region.top());

  size_t table_bytes = SystemDictionary::count_bytes_for_table();
  char* table_top = _ro_region.allocate(table_bytes, sizeof(intptr_t));
  SystemDictionary::copy_table(table_top, _ro_region.top());

  // Write the other data to the output array.
  WriteClosure wc(&_ro_region);
  MetaspaceShared::serialize(&wc);

  char* newtop = _ro_region.top();
  ArchiveCompactor::alloc_stats()->record_other_type(int(newtop - oldtop), true);
  return buckets_top;
}

void VM_PopulateDumpSharedSpace::doit() {
  Thread* THREAD = VMThread::vm_thread();

  FileMapInfo::check_nonempty_dir_in_shared_path_table();

  NOT_PRODUCT(SystemDictionary::verify();)
  // The following guarantee is meant to ensure that no loader constraints
  // exist yet, since the constraints table is not shared.  This becomes
  // more important now that we don't re-initialize vtables/itables for
  // shared classes at runtime, where constraints were previously created.
  guarantee(SystemDictionary::constraints()->number_of_entries() == 0,
            "loader constraints are not saved");
  guarantee(SystemDictionary::placeholders()->number_of_entries() == 0,
          "placeholders are not saved");
  // Revisit and implement this if we prelink method handle call sites:
  guarantee(SystemDictionary::invoke_method_table() == NULL ||
            SystemDictionary::invoke_method_table()->number_of_entries() == 0,
            "invoke method table is not saved");

  // At this point, many classes have been loaded.
  // Gather systemDictionary classes in a global array and do everything to
  // that so we don't have to walk the SystemDictionary again.
  _global_klass_objects = new GrowableArray<Klass*>(1000);
  CollectClassesClosure collect_classes;
  ClassLoaderDataGraph::loaded_classes_do(&collect_classes);

  tty->print_cr("Number of classes %d", _global_klass_objects->length());
  {
    int num_type_array = 0, num_obj_array = 0, num_inst = 0;
    for (int i = 0; i < _global_klass_objects->length(); i++) {
      Klass* k = _global_klass_objects->at(i);
      if (k->is_instance_klass()) {
        num_inst ++;
      } else if (k->is_objArray_klass()) {
        num_obj_array ++;
      } else {
        assert(k->is_typeArray_klass(), "sanity");
        num_type_array ++;
      }
    }
    tty->print_cr("    instance classes   = %5d", num_inst);
    tty->print_cr("    obj array classes  = %5d", num_obj_array);
    tty->print_cr("    type array classes = %5d", num_type_array);
  }

  // Ensure the ConstMethods won't be modified at run-time
  tty->print("Updating ConstMethods ... ");
  rewrite_nofast_bytecodes_and_calculate_fingerprints();
  tty->print_cr("done. ");

  // Move classes from platform/system dictionaries into the boot dictionary
  SystemDictionary::combine_shared_dictionaries();

  // Make sure all classes have a correct loader type.
  ClassLoaderData::the_null_class_loader_data()->dictionary()->classes_do(MetaspaceShared::check_shared_class_loader_type);

  // Remove all references outside the metadata
  tty->print("Removing unshareable information ... ");
  remove_unshareable_in_classes();
  tty->print_cr("done. ");

  // We don't support archiving anonymous classes. Verify that they are not stored in
  // the any dictionaries.
  NOT_PRODUCT(assert_no_anonymoys_classes_in_dictionaries());

  SystemDictionaryShared::finalize_verification_constraints();

  ArchiveCompactor::initialize();
  ArchiveCompactor::copy_and_compact();

  dump_symbols();

  // Dump supported java heap objects
  _closed_archive_heap_regions = NULL;
  _open_archive_heap_regions = NULL;
  dump_java_heap_objects();

  ArchiveCompactor::relocate_well_known_klasses();

  char* read_only_tables_start = dump_read_only_tables();
  _ro_region.pack(&_md_region);

  char* vtbl_list = _md_region.top();
  MetaspaceShared::allocate_cpp_vtable_clones();
  _md_region.pack(&_od_region);

  // Relocate the archived class file data into the od region
  relocate_cached_class_file();
  _od_region.pack();

  // The 5 core spaces are allocated consecutively mc->rw->ro->md->od, so there total size
  // is just the spaces between the two ends.
  size_t core_spaces_size = _od_region.end() - _mc_region.base();
  assert(core_spaces_size == (size_t)align_up(core_spaces_size, Metaspace::reserve_alignment()),
         "should already be aligned");

  // During patching, some virtual methods may be called, so at this point
  // the vtables must contain valid methods (as filled in by CppVtableCloner::allocate).
  MetaspaceShared::patch_cpp_vtable_pointers();

  // The vtable clones contain addresses of the current process.
  // We don't want to write these addresses into the archive.
  MetaspaceShared::zero_cpp_vtable_clones_for_writing();

  // Create and write the archive file that maps the shared spaces.

  FileMapInfo* mapinfo = new FileMapInfo();
  mapinfo->populate_header(os::vm_allocation_granularity());
  mapinfo->set_read_only_tables_start(read_only_tables_start);
  mapinfo->set_misc_data_patching_start(vtbl_list);
  mapinfo->set_cds_i2i_entry_code_buffers(MetaspaceShared::cds_i2i_entry_code_buffers());
  mapinfo->set_cds_i2i_entry_code_buffers_size(MetaspaceShared::cds_i2i_entry_code_buffers_size());
  mapinfo->set_core_spaces_size(core_spaces_size);

  for (int pass=1; pass<=2; pass++) {
    if (pass == 1) {
      // The first pass doesn't actually write the data to disk. All it
      // does is to update the fields in the mapinfo->_header.
    } else {
      // After the first pass, the contents of mapinfo->_header are finalized,
      // so we can compute the header's CRC, and write the contents of the header
      // and the regions into disk.
      mapinfo->open_for_write();
      mapinfo->set_header_crc(mapinfo->compute_header_crc());
    }
    mapinfo->write_header();

    // NOTE: md contains the trampoline code for method entries, which are patched at run time,
    // so it needs to be read/write.
    write_region(mapinfo, MetaspaceShared::mc, &_mc_region, /*read_only=*/false,/*allow_exec=*/true);
    write_region(mapinfo, MetaspaceShared::rw, &_rw_region, /*read_only=*/false,/*allow_exec=*/false);
    write_region(mapinfo, MetaspaceShared::ro, &_ro_region, /*read_only=*/true, /*allow_exec=*/false);
    write_region(mapinfo, MetaspaceShared::md, &_md_region, /*read_only=*/false,/*allow_exec=*/false);
    write_region(mapinfo, MetaspaceShared::od, &_od_region, /*read_only=*/true, /*allow_exec=*/false);

    _total_string_region_size = mapinfo->write_archive_heap_regions(
                                        _closed_archive_heap_regions,
                                        MetaspaceShared::first_string,
                                        MetaspaceShared::max_strings);
    _total_open_archive_region_size = mapinfo->write_archive_heap_regions(
                                        _open_archive_heap_regions,
                                        MetaspaceShared::first_open_archive_heap_region,
                                        MetaspaceShared::max_open_archive_heap_region);
  }

  mapinfo->close();

  // Restore the vtable in case we invoke any virtual methods.
  MetaspaceShared::clone_cpp_vtables((intptr_t*)vtbl_list);

  print_region_stats();

  if (log_is_enabled(Info, cds)) {
    ArchiveCompactor::alloc_stats()->print_stats(int(_ro_region.used()), int(_rw_region.used()),
                                                 int(_mc_region.used()), int(_md_region.used()));
  }

  if (PrintSystemDictionaryAtExit) {
    SystemDictionary::print();
  }
  // There may be other pending VM operations that operate on the InstanceKlasses,
  // which will fail because InstanceKlasses::remove_unshareable_info()
  // has been called. Forget these operations and exit the VM directly.
  vm_direct_exit(0);
}

void VM_PopulateDumpSharedSpace::print_region_stats() {
  // Print statistics of all the regions
  const size_t total_reserved = _ro_region.reserved()  + _rw_region.reserved() +
                                _mc_region.reserved()  + _md_region.reserved() +
                                _od_region.reserved()  +
                                _total_string_region_size +
                                _total_open_archive_region_size;
  const size_t total_bytes = _ro_region.used()  + _rw_region.used() +
                             _mc_region.used()  + _md_region.used() +
                             _od_region.used()  +
                             _total_string_region_size +
                             _total_open_archive_region_size;
  const double total_u_perc = percent_of(total_bytes, total_reserved);

  _mc_region.print(total_reserved);
  _rw_region.print(total_reserved);
  _ro_region.print(total_reserved);
  _md_region.print(total_reserved);
  _od_region.print(total_reserved);
  print_heap_region_stats(_closed_archive_heap_regions, "st", total_reserved);
  print_heap_region_stats(_open_archive_heap_regions, "oa", total_reserved);

  tty->print_cr("total    : " SIZE_FORMAT_W(9) " [100.0%% of total] out of " SIZE_FORMAT_W(9) " bytes [%5.1f%% used]",
                 total_bytes, total_reserved, total_u_perc);
}

void VM_PopulateDumpSharedSpace::print_heap_region_stats(GrowableArray<MemRegion> *heap_mem,
                                                         const char *name, const size_t total_size) {
  int arr_len = heap_mem == NULL ? 0 : heap_mem->length();
  for (int i = 0; i < arr_len; i++) {
      char* start = (char*)heap_mem->at(i).start();
      size_t size = heap_mem->at(i).byte_size();
      char* top = start + size;
      tty->print_cr("%s%d space: " SIZE_FORMAT_W(9) " [ %4.1f%% of total] out of " SIZE_FORMAT_W(9) " bytes [100.0%% used] at " INTPTR_FORMAT,
                    name, i, size, size/double(total_size)*100.0, size, p2i(start));

  }
}

// Update a Java object to point its Klass* to the new location after
// shared archive has been compacted.
void MetaspaceShared::relocate_klass_ptr(oop o) {
  assert(DumpSharedSpaces, "sanity");
  Klass* k = ArchiveCompactor::get_relocated_klass(o->klass());
  o->set_klass(k);
}

Klass* MetaspaceShared::get_relocated_klass(Klass *k) {
  assert(DumpSharedSpaces, "sanity");
  return ArchiveCompactor::get_relocated_klass(k);
}

class LinkSharedClassesClosure : public KlassClosure {
  Thread* THREAD;
  bool    _made_progress;
 public:
  LinkSharedClassesClosure(Thread* thread) : THREAD(thread), _made_progress(false) {}

  void reset()               { _made_progress = false; }
  bool made_progress() const { return _made_progress; }

  void do_klass(Klass* k) {
    if (k->is_instance_klass()) {
      InstanceKlass* ik = InstanceKlass::cast(k);
      // Link the class to cause the bytecodes to be rewritten and the
      // cpcache to be created. Class verification is done according
      // to -Xverify setting.
      _made_progress |= MetaspaceShared::try_link_class(ik, THREAD);
      guarantee(!HAS_PENDING_EXCEPTION, "exception in link_class");

      ik->constants()->resolve_class_constants(THREAD);
    }
  }
};

class CheckSharedClassesClosure : public KlassClosure {
  bool    _made_progress;
 public:
  CheckSharedClassesClosure() : _made_progress(false) {}

  void reset()               { _made_progress = false; }
  bool made_progress() const { return _made_progress; }
  void do_klass(Klass* k) {
    if (k->is_instance_klass() && InstanceKlass::cast(k)->check_sharing_error_state()) {
      _made_progress = true;
    }
  }
};

void MetaspaceShared::check_shared_class_loader_type(InstanceKlass* ik) {
  ResourceMark rm;
  if (ik->shared_classpath_index() == UNREGISTERED_INDEX) {
    guarantee(ik->loader_type() == 0,
            "Class loader type must not be set for this class %s", ik->name()->as_C_string());
  } else {
    guarantee(ik->loader_type() != 0,
            "Class loader type must be set for this class %s", ik->name()->as_C_string());
  }
}

void MetaspaceShared::link_and_cleanup_shared_classes(TRAPS) {
  // We need to iterate because verification may cause additional classes
  // to be loaded.
  LinkSharedClassesClosure link_closure(THREAD);
  do {
    link_closure.reset();
    ClassLoaderDataGraph::loaded_classes_do(&link_closure);
    guarantee(!HAS_PENDING_EXCEPTION, "exception in link_class");
  } while (link_closure.made_progress());

  if (_has_error_classes) {
    // Mark all classes whose super class or interfaces failed verification.
    CheckSharedClassesClosure check_closure;
    do {
      // Not completely sure if we need to do this iteratively. Anyway,
      // we should come here only if there are unverifiable classes, which
      // shouldn't happen in normal cases. So better safe than sorry.
      check_closure.reset();
      ClassLoaderDataGraph::loaded_classes_do(&check_closure);
    } while (check_closure.made_progress());

    if (IgnoreUnverifiableClassesDuringDump) {
      // This is useful when running JCK or SQE tests. You should not
      // enable this when running real apps.
      SystemDictionary::remove_classes_in_error_state();
    } else {
      tty->print_cr("Please remove the unverifiable classes from your class list and try again");
      exit(1);
    }
  }
}

void MetaspaceShared::prepare_for_dumping() {
  Arguments::check_unsupported_dumping_properties();
  ClassLoader::initialize_shared_path();
}

// Preload classes from a list, populate the shared spaces and dump to a
// file.
void MetaspaceShared::preload_and_dump(TRAPS) {
  { TraceTime timer("Dump Shared Spaces", TRACETIME_LOG(Info, startuptime));
    ResourceMark rm;
    char class_list_path_str[JVM_MAXPATHLEN];
    // Preload classes to be shared.
    // Should use some os:: method rather than fopen() here. aB.
    const char* class_list_path;
    if (SharedClassListFile == NULL) {
      // Construct the path to the class list (in jre/lib)
      // Walk up two directories from the location of the VM and
      // optionally tack on "lib" (depending on platform)
      os::jvm_path(class_list_path_str, sizeof(class_list_path_str));
      for (int i = 0; i < 3; i++) {
        char *end = strrchr(class_list_path_str, *os::file_separator());
        if (end != NULL) *end = '\0';
      }
      int class_list_path_len = (int)strlen(class_list_path_str);
      if (class_list_path_len >= 3) {
        if (strcmp(class_list_path_str + class_list_path_len - 3, "lib") != 0) {
          if (class_list_path_len < JVM_MAXPATHLEN - 4) {
            jio_snprintf(class_list_path_str + class_list_path_len,
                         sizeof(class_list_path_str) - class_list_path_len,
                         "%slib", os::file_separator());
            class_list_path_len += 4;
          }
        }
      }
      if (class_list_path_len < JVM_MAXPATHLEN - 10) {
        jio_snprintf(class_list_path_str + class_list_path_len,
                     sizeof(class_list_path_str) - class_list_path_len,
                     "%sclasslist", os::file_separator());
      }
      class_list_path = class_list_path_str;
    } else {
      class_list_path = SharedClassListFile;
    }

    tty->print_cr("Loading classes to share ...");
    _has_error_classes = false;
    int class_count = preload_classes(class_list_path, THREAD);
    if (ExtraSharedClassListFile) {
      class_count += preload_classes(ExtraSharedClassListFile, THREAD);
    }
    tty->print_cr("Loading classes to share: done.");

    log_info(cds)("Shared spaces: preloaded %d classes", class_count);

    // Rewrite and link classes
    tty->print_cr("Rewriting and linking classes ...");

    // Link any classes which got missed. This would happen if we have loaded classes that
    // were not explicitly specified in the classlist. E.g., if an interface implemented by class K
    // fails verification, all other interfaces that were not specified in the classlist but
    // are implemented by K are not verified.
    link_and_cleanup_shared_classes(CATCH);
    tty->print_cr("Rewriting and linking classes: done");

    SystemDictionary::clear_invoke_method_table();

    VM_PopulateDumpSharedSpace op;
    VMThread::execute(&op);
  }
}


int MetaspaceShared::preload_classes(const char* class_list_path, TRAPS) {
  ClassListParser parser(class_list_path);
  int class_count = 0;

    while (parser.parse_one_line()) {
      Klass* klass = ClassLoaderExt::load_one_class(&parser, THREAD);
      if (HAS_PENDING_EXCEPTION) {
        if (klass == NULL &&
             (PENDING_EXCEPTION->klass()->name() == vmSymbols::java_lang_ClassNotFoundException())) {
          // print a warning only when the pending exception is class not found
          tty->print_cr("Preload Warning: Cannot find %s", parser.current_class_name());
        }
        CLEAR_PENDING_EXCEPTION;
      }
      if (klass != NULL) {
        if (log_is_enabled(Trace, cds)) {
          ResourceMark rm;
          log_trace(cds)("Shared spaces preloaded: %s", klass->external_name());
        }

        if (klass->is_instance_klass()) {
          InstanceKlass* ik = InstanceKlass::cast(klass);

          // Link the class to cause the bytecodes to be rewritten and the
          // cpcache to be created. The linking is done as soon as classes
          // are loaded in order that the related data structures (klass and
          // cpCache) are located together.
          try_link_class(ik, THREAD);
          guarantee(!HAS_PENDING_EXCEPTION, "exception in link_class");
        }

        class_count++;
      }
    }

  return class_count;
}

// Returns true if the class's status has changed
bool MetaspaceShared::try_link_class(InstanceKlass* ik, TRAPS) {
  assert(DumpSharedSpaces, "should only be called during dumping");
  if (ik->init_state() < InstanceKlass::linked) {
    bool saved = BytecodeVerificationLocal;
    if (ik->loader_type() == 0 && ik->class_loader() == NULL) {
      // The verification decision is based on BytecodeVerificationRemote
      // for non-system classes. Since we are using the NULL classloader
      // to load non-system classes for customized class loaders during dumping,
      // we need to temporarily change BytecodeVerificationLocal to be the same as
      // BytecodeVerificationRemote. Note this can cause the parent system
      // classes also being verified. The extra overhead is acceptable during
      // dumping.
      BytecodeVerificationLocal = BytecodeVerificationRemote;
    }
    ik->link_class(THREAD);
    if (HAS_PENDING_EXCEPTION) {
      ResourceMark rm;
      tty->print_cr("Preload Warning: Verification failed for %s",
                    ik->external_name());
      CLEAR_PENDING_EXCEPTION;
      ik->set_in_error_state();
      _has_error_classes = true;
    }
    BytecodeVerificationLocal = saved;
    return true;
  } else {
    return false;
  }
}

#if INCLUDE_CDS_JAVA_HEAP
void VM_PopulateDumpSharedSpace::dump_java_heap_objects() {
  if (!MetaspaceShared::is_heap_object_archiving_allowed()) {
    if (log_is_enabled(Info, cds)) {
      log_info(cds)(
        "Archived java heap is not supported as UseG1GC, "
        "UseCompressedOops and UseCompressedClassPointers are required."
        "Current settings: UseG1GC=%s, UseCompressedOops=%s, UseCompressedClassPointers=%s.",
        BOOL_TO_STR(UseG1GC), BOOL_TO_STR(UseCompressedOops),
        BOOL_TO_STR(UseCompressedClassPointers));
    }
    return;
  }

  {
    NoSafepointVerifier nsv;

    // Cache for recording where the archived objects are copied to
    MetaspaceShared::create_archive_object_cache();

    tty->print_cr("Dumping objects to closed archive heap region ...");
    NOT_PRODUCT(StringTable::verify());
    // The closed space has maximum two regions. See FileMapInfo::write_archive_heap_regions() for details.
    _closed_archive_heap_regions = new GrowableArray<MemRegion>(2);
    MetaspaceShared::dump_closed_archive_heap_objects(_closed_archive_heap_regions);

    tty->print_cr("Dumping objects to open archive heap region ...");
    _open_archive_heap_regions = new GrowableArray<MemRegion>(2);
    MetaspaceShared::dump_open_archive_heap_objects(_open_archive_heap_regions);

    MetaspaceShared::destroy_archive_object_cache();
  }

  G1HeapVerifier::verify_archive_regions();
}

void MetaspaceShared::dump_closed_archive_heap_objects(
                                    GrowableArray<MemRegion> * closed_archive) {
  assert(is_heap_object_archiving_allowed(), "Cannot dump java heap objects");

  Thread* THREAD = Thread::current();
  G1CollectedHeap::heap()->begin_archive_alloc_range();

  // Archive interned string objects
  StringTable::write_to_archive();

  G1CollectedHeap::heap()->end_archive_alloc_range(closed_archive,
                                                   os::vm_allocation_granularity());
}

void MetaspaceShared::dump_open_archive_heap_objects(
                                    GrowableArray<MemRegion> * open_archive) {
  assert(UseG1GC, "Only support G1 GC");
  assert(UseCompressedOops && UseCompressedClassPointers,
         "Only support UseCompressedOops and UseCompressedClassPointers enabled");

  Thread* THREAD = Thread::current();
  G1CollectedHeap::heap()->begin_archive_alloc_range(true /* open */);

  java_lang_Class::archive_basic_type_mirrors(THREAD);

  MetaspaceShared::archive_klass_objects(THREAD);

  G1CollectedHeap::heap()->end_archive_alloc_range(open_archive,
                                                   os::vm_allocation_granularity());
}

unsigned MetaspaceShared::obj_hash(oop const& p) {
  assert(!p->mark()->has_bias_pattern(),
         "this object should never have been locked");  // so identity_hash won't safepoin
  unsigned hash = (unsigned)p->identity_hash();
  return hash;
}

MetaspaceShared::ArchivedObjectCache* MetaspaceShared::_archive_object_cache = NULL;
oop MetaspaceShared::find_archived_heap_object(oop obj) {
  assert(DumpSharedSpaces, "dump-time only");
  ArchivedObjectCache* cache = MetaspaceShared::archive_object_cache();
  oop* p = cache->get(obj);
  if (p != NULL) {
    return *p;
  } else {
    return NULL;
  }
}

oop MetaspaceShared::archive_heap_object(oop obj, Thread* THREAD) {
  assert(DumpSharedSpaces, "dump-time only");

  oop ao = find_archived_heap_object(obj);
  if (ao != NULL) {
    // already archived
    return ao;
  }

  int len = obj->size();
  if (G1CollectedHeap::heap()->is_archive_alloc_too_large(len)) {
    return NULL;
  }

  int hash = obj->identity_hash();
  oop archived_oop = (oop)G1CollectedHeap::heap()->archive_mem_allocate(len);
  if (archived_oop != NULL) {
    Copy::aligned_disjoint_words((HeapWord*)obj, (HeapWord*)archived_oop, len);
    relocate_klass_ptr(archived_oop);
    ArchivedObjectCache* cache = MetaspaceShared::archive_object_cache();
    cache->put(obj, archived_oop);
  }
  log_debug(cds)("Archived heap object " PTR_FORMAT " ==> " PTR_FORMAT,
                 p2i(obj), p2i(archived_oop));
  return archived_oop;
}

oop MetaspaceShared::materialize_archived_object(oop obj) {
  assert(obj != NULL, "sanity");
  return G1CollectedHeap::heap()->materialize_archived_object(obj);
}

void MetaspaceShared::archive_klass_objects(Thread* THREAD) {
  int i;
  for (i = 0; i < _global_klass_objects->length(); i++) {
    Klass* k = _global_klass_objects->at(i);

    // archive mirror object
    java_lang_Class::archive_mirror(k, CHECK);

    // archive the resolved_referenes array
    if (k->is_instance_klass()) {
      InstanceKlass* ik = InstanceKlass::cast(k);
      ik->constants()->archive_resolved_references(THREAD);
    }
  }
}

bool MetaspaceShared::is_archive_object(oop p) {
  return (p == NULL) ? false : G1ArchiveAllocator::is_archive_object(p);
}

void MetaspaceShared::fixup_mapped_heap_regions() {
  FileMapInfo *mapinfo = FileMapInfo::current_info();
  mapinfo->fixup_mapped_heap_regions();
}
#endif // INCLUDE_CDS_JAVA_HEAP

// Closure for serializing initialization data in from a data area
// (ptr_array) read from the shared file.

class ReadClosure : public SerializeClosure {
private:
  intptr_t** _ptr_array;

  inline intptr_t nextPtr() {
    return *(*_ptr_array)++;
  }

public:
  ReadClosure(intptr_t** ptr_array) { _ptr_array = ptr_array; }

  void do_ptr(void** p) {
    assert(*p == NULL, "initializing previous initialized pointer.");
    intptr_t obj = nextPtr();
    assert((intptr_t)obj >= 0 || (intptr_t)obj < -100,
           "hit tag while initializing ptrs.");
    *p = (void*)obj;
  }

  void do_u4(u4* p) {
    intptr_t obj = nextPtr();
    *p = (u4)(uintx(obj));
  }

  void do_tag(int tag) {
    int old_tag;
    old_tag = (int)(intptr_t)nextPtr();
    // do_int(&old_tag);
    assert(tag == old_tag, "old tag doesn't match");
    FileMapInfo::assert_mark(tag == old_tag);
  }

  void do_oop(oop *p) {
    narrowOop o = (narrowOop)nextPtr();
    if (o == 0 || !MetaspaceShared::open_archive_heap_region_mapped()) {
      p = NULL;
    } else {
      assert(MetaspaceShared::is_heap_object_archiving_allowed(),
             "Archived heap object is not allowed");
      assert(MetaspaceShared::open_archive_heap_region_mapped(),
             "Open archive heap region is not mapped");
      *p = CompressedOops::decode_not_null(o);
    }
  }

  void do_region(u_char* start, size_t size) {
    assert((intptr_t)start % sizeof(intptr_t) == 0, "bad alignment");
    assert(size % sizeof(intptr_t) == 0, "bad size");
    do_tag((int)size);
    while (size > 0) {
      *(intptr_t*)start = nextPtr();
      start += sizeof(intptr_t);
      size -= sizeof(intptr_t);
    }
  }

  bool reading() const { return true; }
};

// Return true if given address is in the misc data region
bool MetaspaceShared::is_in_shared_region(const void* p, int idx) {
  return UseSharedSpaces && FileMapInfo::current_info()->is_in_shared_region(p, idx);
}

bool MetaspaceShared::is_in_trampoline_frame(address addr) {
  if (UseSharedSpaces && is_in_shared_region(addr, MetaspaceShared::mc)) {
    return true;
  }
  return false;
}

void MetaspaceShared::print_shared_spaces() {
  if (UseSharedSpaces) {
    FileMapInfo::current_info()->print_shared_spaces();
  }
}


// Map shared spaces at requested addresses and return if succeeded.
bool MetaspaceShared::map_shared_spaces(FileMapInfo* mapinfo) {
  size_t image_alignment = mapinfo->alignment();

#ifndef _WINDOWS
  // Map in the shared memory and then map the regions on top of it.
  // On Windows, don't map the memory here because it will cause the
  // mappings of the regions to fail.
  ReservedSpace shared_rs = mapinfo->reserve_shared_memory();
  if (!shared_rs.is_reserved()) return false;
#endif

  assert(!DumpSharedSpaces, "Should not be called with DumpSharedSpaces");

  char* ro_base = NULL; char* ro_top;
  char* rw_base = NULL; char* rw_top;
  char* mc_base = NULL; char* mc_top;
  char* md_base = NULL; char* md_top;
  char* od_base = NULL; char* od_top;

  // Map each shared region
  if ((mc_base = mapinfo->map_region(mc, &mc_top)) != NULL &&
      (rw_base = mapinfo->map_region(rw, &rw_top)) != NULL &&
      (ro_base = mapinfo->map_region(ro, &ro_top)) != NULL &&
      (md_base = mapinfo->map_region(md, &md_top)) != NULL &&
      (od_base = mapinfo->map_region(od, &od_top)) != NULL &&
      (image_alignment == (size_t)os::vm_allocation_granularity()) &&
      mapinfo->validate_shared_path_table()) {
    // Success -- set up MetaspaceObj::_shared_metaspace_{base,top} for
    // fast checking in MetaspaceShared::is_in_shared_metaspace() and
    // MetaspaceObj::is_shared().
    //
    // We require that mc->rw->ro->md->od to be laid out consecutively, with no
    // gaps between them. That way, we can ensure that the OS won't be able to
    // allocate any new memory spaces inside _shared_metaspace_{base,top}, which
    // would mess up the simple comparision in MetaspaceShared::is_in_shared_metaspace().
    assert(mc_base < ro_base && mc_base < rw_base && mc_base < md_base && mc_base < od_base, "must be");
    assert(od_top  > ro_top  && od_top  > rw_top  && od_top  > md_top  && od_top  > mc_top , "must be");
    assert(mc_top == rw_base, "must be");
    assert(rw_top == ro_base, "must be");
    assert(ro_top == md_base, "must be");
    assert(md_top == od_base, "must be");

    MetaspaceObj::_shared_metaspace_base = (void*)mc_base;
    MetaspaceObj::_shared_metaspace_top  = (void*)od_top;
    return true;
  } else {
    // If there was a failure in mapping any of the spaces, unmap the ones
    // that succeeded
    if (ro_base != NULL) mapinfo->unmap_region(ro);
    if (rw_base != NULL) mapinfo->unmap_region(rw);
    if (mc_base != NULL) mapinfo->unmap_region(mc);
    if (md_base != NULL) mapinfo->unmap_region(md);
    if (od_base != NULL) mapinfo->unmap_region(od);
#ifndef _WINDOWS
    // Release the entire mapped region
    shared_rs.release();
#endif
    // If -Xshare:on is specified, print out the error message and exit VM,
    // otherwise, set UseSharedSpaces to false and continue.
    if (RequireSharedSpaces || PrintSharedArchiveAndExit) {
      vm_exit_during_initialization("Unable to use shared archive.", "Failed map_region for using -Xshare:on.");
    } else {
      FLAG_SET_DEFAULT(UseSharedSpaces, false);
    }
    return false;
  }
}

// Read the miscellaneous data from the shared file, and
// serialize it out to its various destinations.

void MetaspaceShared::initialize_shared_spaces() {
  FileMapInfo *mapinfo = FileMapInfo::current_info();
  _cds_i2i_entry_code_buffers = mapinfo->cds_i2i_entry_code_buffers();
  _cds_i2i_entry_code_buffers_size = mapinfo->cds_i2i_entry_code_buffers_size();
  _core_spaces_size = mapinfo->core_spaces_size();
  char* buffer = mapinfo->misc_data_patching_start();
  clone_cpp_vtables((intptr_t*)buffer);

  // The rest of the data is now stored in the RW region
  buffer = mapinfo->read_only_tables_start();
  int sharedDictionaryLen = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  int number_of_entries = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  SystemDictionary::set_shared_dictionary((HashtableBucket<mtClass>*)buffer,
                                          sharedDictionaryLen,
                                          number_of_entries);
  buffer += sharedDictionaryLen;

  // The following data are the linked list elements
  // (HashtableEntry objects) for the shared dictionary table.

  int len = *(intptr_t*)buffer;     // skip over shared dictionary entries
  buffer += sizeof(intptr_t);
  buffer += len;

  // Verify various attributes of the archive, plus initialize the
  // shared string/symbol tables
  intptr_t* array = (intptr_t*)buffer;
  ReadClosure rc(&array);
  serialize(&rc);

  // Initialize the run-time symbol table.
  SymbolTable::create_table();

  // Close the mapinfo file
  mapinfo->close();

  if (PrintSharedArchiveAndExit) {
    if (PrintSharedDictionary) {
      tty->print_cr("\nShared classes:\n");
      SystemDictionary::print_shared(tty);
    }
    if (_archive_loading_failed) {
      tty->print_cr("archive is invalid");
      vm_exit(1);
    } else {
      tty->print_cr("archive is valid");
      vm_exit(0);
    }
  }
}

// JVM/TI RedefineClasses() support:
bool MetaspaceShared::remap_shared_readonly_as_readwrite() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");

  if (UseSharedSpaces) {
    // remap the shared readonly space to shared readwrite, private
    FileMapInfo* mapinfo = FileMapInfo::current_info();
    if (!mapinfo->remap_shared_readonly_as_readwrite()) {
      return false;
    }
    _remapped_readwrite = true;
  }
  return true;
}

void MetaspaceShared::report_out_of_space(const char* name, size_t needed_bytes) {
  // This is highly unlikely to happen on 64-bits because we have reserved a 4GB space.
  // On 32-bit we reserve only 256MB so you could run out of space with 100,000 classes
  // or so.
  _mc_region.print_out_of_space_msg(name, needed_bytes);
  _rw_region.print_out_of_space_msg(name, needed_bytes);
  _ro_region.print_out_of_space_msg(name, needed_bytes);
  _md_region.print_out_of_space_msg(name, needed_bytes);
  _od_region.print_out_of_space_msg(name, needed_bytes);

  vm_exit_during_initialization(err_msg("Unable to allocate from '%s' region", name),
                                "Please reduce the number of shared classes.");
}
