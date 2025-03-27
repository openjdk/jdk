/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/aotClassLocation.hpp"
#include "cds/aotConstantPoolResolver.hpp"
#include "cds/aotLinkedClassBulkLoader.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/archiveHeapLoader.hpp"
#include "cds/archiveHeapWriter.hpp"
#include "cds/cds_globals.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/cdsProtectionDomain.hpp"
#include "cds/classListParser.hpp"
#include "cds/classListWriter.hpp"
#include "cds/cppVtables.hpp"
#include "cds/dumpAllocStats.hpp"
#include "cds/dynamicArchive.hpp"
#include "cds/filemap.hpp"
#include "cds/finalImageRecipes.hpp"
#include "cds/heapShared.hpp"
#include "cds/lambdaFormInvokers.hpp"
#include "cds/lambdaProxyClassDictionary.hpp"
#include "cds/metaspaceShared.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/classLoaderDataShared.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/loaderConstraints.hpp"
#include "classfile/modules.hpp"
#include "classfile/placeholders.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/bytecodes.hpp"
#include "jvm_io.h"
#include "logging/log.hpp"
#include "logging/logMessage.hpp"
#include "logging/logStream.hpp"
#include "memory/memoryReserver.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "nmt/memTracker.hpp"
#include "oops/compressedKlass.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"
#include "sanitizers/leak.hpp"
#include "utilities/align.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#include "utilities/resourceHash.hpp"

ReservedSpace MetaspaceShared::_symbol_rs;
VirtualSpace MetaspaceShared::_symbol_vs;
bool MetaspaceShared::_archive_loading_failed = false;
bool MetaspaceShared::_remapped_readwrite = false;
void* MetaspaceShared::_shared_metaspace_static_top = nullptr;
intx MetaspaceShared::_relocation_delta;
char* MetaspaceShared::_requested_base_address;
Array<Method*>* MetaspaceShared::_archived_method_handle_intrinsics = nullptr;
bool MetaspaceShared::_use_optimized_module_handling = true;

// The CDS archive is divided into the following regions:
//     rw  - read-write metadata
//     ro  - read-only metadata and read-only tables
//     hp  - heap region
//     bm  - bitmap for relocating the above 7 regions.
//
// The rw and ro regions are linearly allocated, in the order of rw->ro.
// These regions are aligned with MetaspaceShared::core_region_alignment().
//
// These 2 regions are populated in the following steps:
// [0] All classes are loaded in MetaspaceShared::preload_classes(). All metadata are
//     temporarily allocated outside of the shared regions.
// [1] We enter a safepoint and allocate a buffer for the rw/ro regions.
// [2] C++ vtables are copied into the rw region.
// [3] ArchiveBuilder copies RW metadata into the rw region.
// [4] ArchiveBuilder copies RO metadata into the ro region.
// [5] SymbolTable, StringTable, SystemDictionary, and a few other read-only data
//     are copied into the ro region as read-only tables.
//
// The heap region is written by HeapShared::write_heap().
//
// The bitmap region is used to relocate the ro/rw/hp regions.

static DumpRegion _symbol_region("symbols");

char* MetaspaceShared::symbol_space_alloc(size_t num_bytes) {
  return _symbol_region.allocate(num_bytes);
}

// os::vm_allocation_granularity() is usually 4K for most OSes. However, some platforms
// such as linux-aarch64 and macos-x64 ...
// it can be either 4K or 64K and on macos-aarch64 it is 16K. To generate archives that are
// compatible for both settings, an alternative cds core region alignment can be enabled
// at building time:
//   --enable-compactible-cds-alignment
// Upon successful configuration, the compactible alignment then can be defined in:
//   os_linux_aarch64.cpp
//   os_bsd_x86.cpp
size_t MetaspaceShared::core_region_alignment() {
  return os::cds_core_region_alignment();
}

static bool shared_base_valid(char* shared_base) {
  // We check user input for SharedBaseAddress at dump time.

  // At CDS runtime, "shared_base" will be the (attempted) mapping start. It will also
  // be the encoding base, since the headers of archived base objects (and with Lilliput,
  // the prototype mark words) carry pre-computed narrow Klass IDs that refer to the mapping
  // start as base.
  //
  // On AARCH64, The "shared_base" may not be later usable as encoding base, depending on the
  // total size of the reserved area and the precomputed_narrow_klass_shift. This is checked
  // before reserving memory.  Here we weed out values already known to be invalid later.
  return AARCH64_ONLY(is_aligned(shared_base, 4 * G)) NOT_AARCH64(true);
}

class DumpClassListCLDClosure : public CLDClosure {
  static const int INITIAL_TABLE_SIZE = 1987;
  static const int MAX_TABLE_SIZE = 61333;

  fileStream *_stream;
  ResizeableResourceHashtable<InstanceKlass*, bool,
                              AnyObj::C_HEAP, mtClassShared> _dumped_classes;

  void dump(InstanceKlass* ik) {
    bool created;
    _dumped_classes.put_if_absent(ik, &created);
    if (!created) {
      return;
    }
    if (_dumped_classes.maybe_grow()) {
      log_info(cds, hashtables)("Expanded _dumped_classes table to %d", _dumped_classes.table_size());
    }
    if (ik->java_super()) {
      dump(ik->java_super());
    }
    Array<InstanceKlass*>* interfaces = ik->local_interfaces();
    int len = interfaces->length();
    for (int i = 0; i < len; i++) {
      dump(interfaces->at(i));
    }
    ClassListWriter::write_to_stream(ik, _stream);
  }

public:
  DumpClassListCLDClosure(fileStream* f)
  : CLDClosure(), _dumped_classes(INITIAL_TABLE_SIZE, MAX_TABLE_SIZE) {
    _stream = f;
  }

  void do_cld(ClassLoaderData* cld) {
    for (Klass* klass = cld->klasses(); klass != nullptr; klass = klass->next_link()) {
      if (klass->is_instance_klass()) {
        dump(InstanceKlass::cast(klass));
      }
    }
  }
};

void MetaspaceShared::dump_loaded_classes(const char* file_name, TRAPS) {
  fileStream stream(file_name, "w");
  if (stream.is_open()) {
    MutexLocker lock(ClassLoaderDataGraph_lock);
    MutexLocker lock2(ClassListFile_lock, Mutex::_no_safepoint_check_flag);
    DumpClassListCLDClosure collect_classes(&stream);
    ClassLoaderDataGraph::loaded_cld_do(&collect_classes);
  } else {
    THROW_MSG(vmSymbols::java_io_IOException(), "Failed to open file");
  }
}

static bool shared_base_too_high(char* specified_base, char* aligned_base, size_t cds_max) {
  // Caller should have checked that aligned_base was successfully aligned and is not nullptr.
  // Comparing specified_base with nullptr is UB.
  assert(aligned_base != nullptr, "sanity");
  assert(aligned_base >= specified_base, "sanity");

  if (max_uintx - uintx(aligned_base) < uintx(cds_max)) {
    // Not enough address space to hold an archive of cds_max bytes from aligned_base.
    return true;
  } else {
    return false;
  }
}

static char* compute_shared_base(size_t cds_max) {
  char* specified_base = (char*)SharedBaseAddress;
  size_t alignment = MetaspaceShared::core_region_alignment();
  if (UseCompressedClassPointers) {
    alignment = MAX2(alignment, Metaspace::reserve_alignment());
  }

  if (SharedBaseAddress == 0) {
    // Special meaning of -XX:SharedBaseAddress=0 -> Always map archive at os-selected address.
    return specified_base;
  }

  char* aligned_base = can_align_up(specified_base, alignment)
                           ? align_up(specified_base, alignment)
                           : nullptr;

  if (aligned_base != specified_base) {
    log_info(cds)("SharedBaseAddress (" INTPTR_FORMAT ") aligned up to " INTPTR_FORMAT,
                   p2i(specified_base), p2i(aligned_base));
  }

  const char* err = nullptr;
  if (aligned_base == nullptr) {
    err = "too high";
  } else if (shared_base_too_high(specified_base, aligned_base, cds_max)) {
    err = "too high";
  } else if (!shared_base_valid(aligned_base)) {
    err = "invalid for this platform";
  } else {
    return aligned_base;
  }

  // Arguments::default_SharedBaseAddress() is hard-coded in cds_globals.hpp. It must be carefully
  // picked that (a) the align_up() below will always return a valid value; (b) none of
  // the following asserts will fail.
  log_warning(cds)("SharedBaseAddress (" INTPTR_FORMAT ") is %s. Reverted to " INTPTR_FORMAT,
                   p2i((void*)SharedBaseAddress), err,
                   p2i((void*)Arguments::default_SharedBaseAddress()));

  specified_base = (char*)Arguments::default_SharedBaseAddress();
  aligned_base = align_up(specified_base, alignment);

  // Make sure the default value of SharedBaseAddress specified in globals.hpp is sane.
  assert(!shared_base_too_high(specified_base, aligned_base, cds_max), "Sanity");
  assert(shared_base_valid(aligned_base), "Sanity");
  return aligned_base;
}

void MetaspaceShared::initialize_for_static_dump() {
  assert(CDSConfig::is_dumping_static_archive(), "sanity");
  log_info(cds)("Core region alignment: %zu", core_region_alignment());
  // The max allowed size for CDS archive. We use this to limit SharedBaseAddress
  // to avoid address space wrap around.
  size_t cds_max;
  const size_t reserve_alignment = core_region_alignment();

#ifdef _LP64
  const uint64_t UnscaledClassSpaceMax = (uint64_t(max_juint) + 1);
  cds_max = align_down(UnscaledClassSpaceMax, reserve_alignment);
#else
  // We don't support archives larger than 256MB on 32-bit due to limited
  //  virtual address space.
  cds_max = align_down(256*M, reserve_alignment);
#endif

  _requested_base_address = compute_shared_base(cds_max);
  SharedBaseAddress = (size_t)_requested_base_address;

  size_t symbol_rs_size = LP64_ONLY(3 * G) NOT_LP64(128 * M);
  _symbol_rs = MemoryReserver::reserve(symbol_rs_size,
                                       os::vm_allocation_granularity(),
                                       os::vm_page_size(),
                                       mtClassShared);
  if (!_symbol_rs.is_reserved()) {
    log_error(cds)("Unable to reserve memory for symbols: %zu bytes.", symbol_rs_size);
    MetaspaceShared::unrecoverable_writing_error();
  }
  _symbol_region.init(&_symbol_rs, &_symbol_vs);
}

// Called by universe_post_init()
void MetaspaceShared::post_initialize(TRAPS) {
  if (CDSConfig::is_using_archive()) {
    int size = AOTClassLocationConfig::runtime()->length();
    if (size > 0) {
      CDSProtectionDomain::allocate_shared_data_arrays(size, CHECK);
    }
  }
}

// Extra java.lang.Strings to be added to the archive
static GrowableArrayCHeap<OopHandle, mtClassShared>* _extra_interned_strings = nullptr;
// Extra Symbols to be added to the archive
static GrowableArrayCHeap<Symbol*, mtClassShared>* _extra_symbols = nullptr;
// Methods managed by SystemDictionary::find_method_handle_intrinsic() to be added to the archive
static GrowableArray<Method*>* _pending_method_handle_intrinsics = nullptr;

void MetaspaceShared::read_extra_data(JavaThread* current, const char* filename) {
  _extra_interned_strings = new GrowableArrayCHeap<OopHandle, mtClassShared>(10000);
  _extra_symbols = new GrowableArrayCHeap<Symbol*, mtClassShared>(1000);

  HashtableTextDump reader(filename);
  reader.check_version("VERSION: 1.0");

  while (reader.remain() > 0) {
    int utf8_length;
    int prefix_type = reader.scan_prefix(&utf8_length);
    ResourceMark rm(current);
    if (utf8_length == 0x7fffffff) {
      // buf_len will overflown 32-bit value.
      log_error(cds)("string length too large: %d", utf8_length);
      MetaspaceShared::unrecoverable_loading_error();
    }
    int buf_len = utf8_length+1;
    char* utf8_buffer = NEW_RESOURCE_ARRAY(char, buf_len);
    reader.get_utf8(utf8_buffer, utf8_length);
    utf8_buffer[utf8_length] = '\0';

    if (prefix_type == HashtableTextDump::SymbolPrefix) {
      _extra_symbols->append(SymbolTable::new_permanent_symbol(utf8_buffer));
    } else{
      assert(prefix_type == HashtableTextDump::StringPrefix, "Sanity");
      ExceptionMark em(current);
      JavaThread* THREAD = current; // For exception macros.
      oop str = StringTable::intern(utf8_buffer, THREAD);

      if (HAS_PENDING_EXCEPTION) {
        log_warning(cds, heap)("[line %d] extra interned string allocation failed; size too large: %d",
                               reader.last_line_no(), utf8_length);
        CLEAR_PENDING_EXCEPTION;
      } else {
#if INCLUDE_CDS_JAVA_HEAP
        if (ArchiveHeapWriter::is_string_too_large_to_archive(str)) {
          log_warning(cds, heap)("[line %d] extra interned string ignored; size too large: %d",
                                 reader.last_line_no(), utf8_length);
          continue;
        }
        // Make sure this string is included in the dumped interned string table.
        assert(str != nullptr, "must succeed");
        _extra_interned_strings->append(OopHandle(Universe::vm_global(), str));
#endif
      }
    }
  }
}

void MetaspaceShared::make_method_handle_intrinsics_shareable() {
  for (int i = 0; i < _pending_method_handle_intrinsics->length(); i++) {
    Method* m = ArchiveBuilder::current()->get_buffered_addr(_pending_method_handle_intrinsics->at(i));
    m->remove_unshareable_info();
    // Each method has its own constant pool (which is distinct from m->method_holder()->constants());
    m->constants()->remove_unshareable_info();
  }
}

void MetaspaceShared::write_method_handle_intrinsics() {
  int len = _pending_method_handle_intrinsics->length();
  _archived_method_handle_intrinsics = ArchiveBuilder::new_ro_array<Method*>(len);
  int word_size = _archived_method_handle_intrinsics->size();
  for (int i = 0; i < len; i++) {
    Method* m = _pending_method_handle_intrinsics->at(i);
    ArchiveBuilder::current()->write_pointer_in_buffer(_archived_method_handle_intrinsics->adr_at(i), m);
    word_size += m->size() + m->constMethod()->size() + m->constants()->size();
    if (m->constants()->cache() != nullptr) {
      word_size += m->constants()->cache()->size();
    }
  }
  log_info(cds)("Archived %d method handle intrinsics (%d bytes)", len, word_size * BytesPerWord);
}

// About "serialize" --
//
// This is (probably a badly named) way to read/write a data stream of pointers and
// miscellaneous data from/to the shared archive file. The usual code looks like this:
//
//     // These two global C++ variables are initialized during dump time.
//     static int _archived_int;
//     static MetaspaceObj* archived_ptr;
//
//     void MyClass::serialize(SerializeClosure* soc) {
//         soc->do_int(&_archived_int);
//         soc->do_int(&_archived_ptr);
//     }
//
//     At dumptime, these two variables are stored into the CDS archive.
//     At runtime, these two variables are loaded from the CDS archive.
//     In addition, the pointer is relocated as necessary.
//
// Some of the xxx::serialize() functions may have side effects and assume that
// the archive is already mapped. For example, SymbolTable::serialize_shared_table_header()
// unconditionally makes the set of archived symbols available. Therefore, we put most
// of these xxx::serialize() functions inside MetaspaceShared::serialize(), which
// is called AFTER we made the decision to map the archive.
//
// However, some of the "serialized" data are used to decide whether an archive should
// be mapped or not (e.g., for checking if the -Djdk.module.main property is compatible
// with the archive). The xxx::serialize() functions for these data must be put inside
// MetaspaceShared::early_serialize(). Such functions must not produce side effects that
// assume we will always decides to map the archive.

void MetaspaceShared::early_serialize(SerializeClosure* soc) {
  int tag = 0;
  soc->do_tag(--tag);
  CDS_JAVA_HEAP_ONLY(Modules::serialize_archived_module_info(soc);)
  soc->do_tag(666);
}

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

  // Need to do this first, as subsequent steps may call virtual functions
  // in archived Metadata objects.
  CppVtables::serialize(soc);
  soc->do_tag(--tag);

  // Dump/restore miscellaneous metadata.
  JavaClasses::serialize_offsets(soc);
  Universe::serialize(soc);
  soc->do_tag(--tag);

  // Dump/restore references to commonly used names and signatures.
  vmSymbols::serialize(soc);
  soc->do_tag(--tag);

  // Dump/restore the symbol/string/subgraph_info tables
  SymbolTable::serialize_shared_table_header(soc);
  StringTable::serialize_shared_table_header(soc);
  HeapShared::serialize_tables(soc);
  SystemDictionaryShared::serialize_dictionary_headers(soc);
  AOTLinkedClassBulkLoader::serialize(soc, true);
  FinalImageRecipes::serialize(soc);
  InstanceMirrorKlass::serialize_offsets(soc);

  // Dump/restore well known classes (pointers)
  SystemDictionaryShared::serialize_vm_classes(soc);
  soc->do_tag(--tag);

  CDS_JAVA_HEAP_ONLY(ClassLoaderDataShared::serialize(soc);)
  soc->do_ptr((void**)&_archived_method_handle_intrinsics);

  LambdaFormInvokers::serialize(soc);
  soc->do_tag(666);
}

static void rewrite_nofast_bytecode(const methodHandle& method) {
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

// [1] Rewrite all bytecodes as needed, so that the ConstMethod* will not be modified
//     at run time by RewriteBytecodes/RewriteFrequentPairs
// [2] Assign a fingerprint, so one doesn't need to be assigned at run-time.
void MetaspaceShared::rewrite_nofast_bytecodes_and_calculate_fingerprints(Thread* thread, InstanceKlass* ik) {
  for (int i = 0; i < ik->methods()->length(); i++) {
    methodHandle m(thread, ik->methods()->at(i));
    if (ik->can_be_verified_at_dumptime() && ik->is_linked()) {
      rewrite_nofast_bytecode(m);
    }
    Fingerprinter fp(m);
    // The side effect of this call sets method's fingerprint field.
    fp.fingerprint();
  }
}

class VM_PopulateDumpSharedSpace : public VM_Operation {
private:
  ArchiveHeapInfo _heap_info;
  FileMapInfo* _map_info;
  StaticArchiveBuilder& _builder;

  void dump_java_heap_objects();
  void dump_shared_symbol_table(GrowableArray<Symbol*>* symbols) {
    log_info(cds)("Dumping symbol table ...");
    SymbolTable::write_to_archive(symbols);
  }
  char* dump_early_read_only_tables();
  char* dump_read_only_tables(AOTClassLocationConfig*& cl_config);

public:

  VM_PopulateDumpSharedSpace(StaticArchiveBuilder& b) :
    VM_Operation(), _heap_info(), _map_info(nullptr), _builder(b) {}

  bool skip_operation() const { return false; }

  VMOp_Type type() const { return VMOp_PopulateDumpSharedSpace; }
  ArchiveHeapInfo* heap_info()  { return &_heap_info; }
  FileMapInfo* map_info() const { return _map_info; }
  void doit();   // outline because gdb sucks
  bool allow_nested_vm_operations() const { return true; }
}; // class VM_PopulateDumpSharedSpace

class StaticArchiveBuilder : public ArchiveBuilder {
public:
  StaticArchiveBuilder() : ArchiveBuilder() {}

  virtual void iterate_roots(MetaspaceClosure* it) {
    AOTArtifactFinder::all_cached_classes_do(it);
    SystemDictionaryShared::dumptime_classes_do(it);
    Universe::metaspace_pointers_do(it);
    vmSymbols::metaspace_pointers_do(it);

    // The above code should find all the symbols that are referenced by the
    // archived classes. We just need to add the extra symbols which
    // may not be used by any of the archived classes -- these are usually
    // symbols that we anticipate to be used at run time, so we can store
    // them in the RO region, to be shared across multiple processes.
    if (_extra_symbols != nullptr) {
      for (int i = 0; i < _extra_symbols->length(); i++) {
        it->push(_extra_symbols->adr_at(i));
      }
    }

    for (int i = 0; i < _pending_method_handle_intrinsics->length(); i++) {
      it->push(_pending_method_handle_intrinsics->adr_at(i));
    }
  }
};

char* VM_PopulateDumpSharedSpace::dump_early_read_only_tables() {
  ArchiveBuilder::OtherROAllocMark mark;

  CDS_JAVA_HEAP_ONLY(Modules::dump_archived_module_info());

  DumpRegion* ro_region = ArchiveBuilder::current()->ro_region();
  char* start = ro_region->top();
  WriteClosure wc(ro_region);
  MetaspaceShared::early_serialize(&wc);
  return start;
}

char* VM_PopulateDumpSharedSpace::dump_read_only_tables(AOTClassLocationConfig*& cl_config) {
  ArchiveBuilder::OtherROAllocMark mark;

  SystemDictionaryShared::write_to_archive();
  cl_config = AOTClassLocationConfig::dumptime()->write_to_archive();
  AOTClassLinker::write_to_archive();
  if (CDSConfig::is_dumping_preimage_static_archive()) {
    FinalImageRecipes::record_recipes();
  }
  MetaspaceShared::write_method_handle_intrinsics();

  // Write lambform lines into archive
  LambdaFormInvokers::dump_static_archive_invokers();

  // Write the other data to the output array.
  DumpRegion* ro_region = ArchiveBuilder::current()->ro_region();
  char* start = ro_region->top();
  WriteClosure wc(ro_region);
  MetaspaceShared::serialize(&wc);

  return start;
}

void VM_PopulateDumpSharedSpace::doit() {
  if (!CDSConfig::is_dumping_final_static_archive()) {
    guarantee(!CDSConfig::is_using_archive(), "We should not be using an archive when we dump");
  }

  DEBUG_ONLY(SystemDictionaryShared::NoClassLoadingMark nclm);

  _pending_method_handle_intrinsics = new (mtClassShared) GrowableArray<Method*>(256, mtClassShared);
  if (CDSConfig::is_dumping_method_handles()) {
    // When dumping AOT-linked classes, some classes may have direct references to a method handle
    // intrinsic. The easiest thing is to save all of them into the AOT cache.
    SystemDictionary::get_all_method_handle_intrinsics(_pending_method_handle_intrinsics);
  }

  AOTClassLocationConfig::dumptime_check_nonempty_dirs();

  NOT_PRODUCT(SystemDictionary::verify();)

  // Block concurrent class unloading from changing the _dumptime_table
  MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);

#if INCLUDE_CDS_JAVA_HEAP
  if (CDSConfig::is_dumping_heap() && _extra_interned_strings != nullptr) {
    for (int i = 0; i < _extra_interned_strings->length(); i ++) {
      OopHandle string = _extra_interned_strings->at(i);
      HeapShared::add_to_dumped_interned_strings(string.resolve());
    }
  }
#endif

  _builder.gather_source_objs();
  _builder.reserve_buffer();

  CppVtables::dumptime_init(&_builder);

  _builder.sort_metadata_objs();
  _builder.dump_rw_metadata();
  _builder.dump_ro_metadata();
  _builder.relocate_metaspaceobj_embedded_pointers();

  log_info(cds)("Make classes shareable");
  _builder.make_klasses_shareable();
  MetaspaceShared::make_method_handle_intrinsics_shareable();

  dump_java_heap_objects();
  dump_shared_symbol_table(_builder.symbols());

  char* early_serialized_data = dump_early_read_only_tables();
  AOTClassLocationConfig* cl_config;
  char* serialized_data = dump_read_only_tables(cl_config);

  if (CDSConfig::is_dumping_lambdas_in_legacy_mode()) {
    log_info(cds)("Adjust lambda proxy class dictionary");
    LambdaProxyClassDictionary::adjust_dumptime_table();
  }

  // The vtable clones contain addresses of the current process.
  // We don't want to write these addresses into the archive.
  CppVtables::zero_archived_vtables();

  // Write the archive file
  const char* static_archive;
  if (CDSConfig::is_dumping_final_static_archive()) {
    static_archive = AOTCache;
    FileMapInfo::free_current_info();
  } else {
    static_archive = CDSConfig::static_archive_path();
  }
  assert(static_archive != nullptr, "SharedArchiveFile not set?");
  _map_info = new FileMapInfo(static_archive, true);
  _map_info->populate_header(MetaspaceShared::core_region_alignment());
  _map_info->set_early_serialized_data(early_serialized_data);
  _map_info->set_serialized_data(serialized_data);
  _map_info->set_cloned_vtables(CppVtables::vtables_serialized_base());
  _map_info->header()->set_class_location_config(cl_config);
}

class CollectCLDClosure : public CLDClosure {
  GrowableArray<ClassLoaderData*> _loaded_cld;
  GrowableArray<OopHandle> _loaded_cld_handles; // keep the CLDs alive
  Thread* _current_thread;
public:
  CollectCLDClosure(Thread* thread) : _current_thread(thread) {}
  ~CollectCLDClosure() {
    for (int i = 0; i < _loaded_cld_handles.length(); i++) {
      _loaded_cld_handles.at(i).release(Universe::vm_global());
    }
  }
  void do_cld(ClassLoaderData* cld) {
    assert(cld->is_alive(), "must be");
    _loaded_cld.append(cld);
    _loaded_cld_handles.append(OopHandle(Universe::vm_global(), cld->holder()));
  }

  int nof_cld() const                { return _loaded_cld.length(); }
  ClassLoaderData* cld_at(int index) { return _loaded_cld.at(index); }
};

// Check if we can eagerly link this class at dump time, so we can avoid the
// runtime linking overhead (especially verification)
bool MetaspaceShared::may_be_eagerly_linked(InstanceKlass* ik) {
  if (!ik->can_be_verified_at_dumptime()) {
    // For old classes, try to leave them in the unlinked state, so
    // we can still store them in the archive. They must be
    // linked/verified at runtime.
    return false;
  }
  if (CDSConfig::is_dumping_dynamic_archive() && ik->is_shared_unregistered_class()) {
    // Linking of unregistered classes at this stage may cause more
    // classes to be resolved, resulting in calls to ClassLoader.loadClass()
    // that may not be expected by custom class loaders.
    //
    // It's OK to do this for the built-in loaders as we know they can
    // tolerate this.
    return false;
  }
  return true;
}

bool MetaspaceShared::link_class_for_cds(InstanceKlass* ik, TRAPS) {
  // Link the class to cause the bytecodes to be rewritten and the
  // cpcache to be created. Class verification is done according
  // to -Xverify setting.
  bool res = MetaspaceShared::try_link_class(THREAD, ik);
  AOTConstantPoolResolver::dumptime_resolve_constants(ik, CHECK_(false));
  return res;
}

void MetaspaceShared::link_shared_classes(bool jcmd_request, TRAPS) {
  AOTClassLinker::initialize();
  AOTClassInitializer::init_test_class(CHECK);

  if (!jcmd_request && !CDSConfig::is_dumping_final_static_archive()) {
    LambdaFormInvokers::regenerate_holder_classes(CHECK);
  }

  // Collect all loaded ClassLoaderData.
  CollectCLDClosure collect_cld(THREAD);
  {
    // ClassLoaderDataGraph::loaded_cld_do requires ClassLoaderDataGraph_lock.
    // We cannot link the classes while holding this lock (or else we may run into deadlock).
    // Therefore, we need to first collect all the CLDs, and then link their classes after
    // releasing the lock.
    MutexLocker lock(ClassLoaderDataGraph_lock);
    ClassLoaderDataGraph::loaded_cld_do(&collect_cld);
  }

  while (true) {
    bool has_linked = false;
    for (int i = 0; i < collect_cld.nof_cld(); i++) {
      ClassLoaderData* cld = collect_cld.cld_at(i);
      for (Klass* klass = cld->klasses(); klass != nullptr; klass = klass->next_link()) {
        if (klass->is_instance_klass()) {
          InstanceKlass* ik = InstanceKlass::cast(klass);
          if (may_be_eagerly_linked(ik)) {
            has_linked |= link_class_for_cds(ik, CHECK);
          }
        }
      }
    }

    if (!has_linked) {
      break;
    }
    // Class linking includes verification which may load more classes.
    // Keep scanning until we have linked no more classes.
  }

  if (CDSConfig::is_dumping_final_static_archive()) {
    FinalImageRecipes::apply_recipes(CHECK);
  }
}

void MetaspaceShared::prepare_for_dumping() {
  assert(CDSConfig::is_dumping_archive(), "sanity");
  CDSConfig::check_unsupported_dumping_module_options();
}

// Preload classes from a list, populate the shared spaces and dump to a
// file.
void MetaspaceShared::preload_and_dump(TRAPS) {
  CDSConfig::DumperThreadMark dumper_thread_mark(THREAD);
  ResourceMark rm(THREAD);
  StaticArchiveBuilder builder;
  preload_and_dump_impl(builder, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    if (PENDING_EXCEPTION->is_a(vmClasses::OutOfMemoryError_klass())) {
      log_error(cds)("Out of memory. Please run with a larger Java heap, current MaxHeapSize = "
                     "%zuM", MaxHeapSize/M);
      MetaspaceShared::writing_error();
    } else {
      log_error(cds)("%s: %s", PENDING_EXCEPTION->klass()->external_name(),
                     java_lang_String::as_utf8_string(java_lang_Throwable::message(PENDING_EXCEPTION)));
      MetaspaceShared::writing_error("Unexpected exception, use -Xlog:cds,exceptions=trace for detail");
    }
  }

  if (CDSConfig::new_aot_flags_used()) {
    if (CDSConfig::is_dumping_preimage_static_archive()) {
      // We are in the JVM that runs the training run. Continue execution,
      // so that it can finish all clean-up and return the correct exit
      // code to the OS.
      tty->print_cr("AOTConfiguration recorded: %s", AOTConfiguration);
    } else {
      // The JLI launcher only recognizes the "old" -Xshare:dump flag.
      // When the new -XX:AOTMode=create flag is used, we can't return
      // to the JLI launcher, as the launcher will fail when trying to
      // run the main class, which is not what we want.
      tty->print_cr("AOTCache creation is complete: %s", AOTCache);
      vm_exit(0);
    }
  }
}

#if INCLUDE_CDS_JAVA_HEAP && defined(_LP64)
void MetaspaceShared::adjust_heap_sizes_for_dumping() {
  if (!CDSConfig::is_dumping_heap() || UseCompressedOops) {
    return;
  }
  // CDS heap dumping requires all string oops to have an offset
  // from the heap bottom that can be encoded in 32-bit.
  julong max_heap_size = (julong)(4 * G);

  if (MinHeapSize > max_heap_size) {
    log_debug(cds)("Setting MinHeapSize to 4G for CDS dumping, original size = %zuM", MinHeapSize/M);
    FLAG_SET_ERGO(MinHeapSize, max_heap_size);
  }
  if (InitialHeapSize > max_heap_size) {
    log_debug(cds)("Setting InitialHeapSize to 4G for CDS dumping, original size = %zuM", InitialHeapSize/M);
    FLAG_SET_ERGO(InitialHeapSize, max_heap_size);
  }
  if (MaxHeapSize > max_heap_size) {
    log_debug(cds)("Setting MaxHeapSize to 4G for CDS dumping, original size = %zuM", MaxHeapSize/M);
    FLAG_SET_ERGO(MaxHeapSize, max_heap_size);
  }
}
#endif // INCLUDE_CDS_JAVA_HEAP && _LP64

void MetaspaceShared::get_default_classlist(char* default_classlist, const size_t buf_size) {
  const char* filesep = os::file_separator();
  jio_snprintf(default_classlist, buf_size, "%s%slib%sclasslist",
               Arguments::get_java_home(), filesep, filesep);
}

void MetaspaceShared::preload_classes(TRAPS) {
  char default_classlist[JVM_MAXPATHLEN];
  const char* classlist_path;

  get_default_classlist(default_classlist, JVM_MAXPATHLEN);
  if (SharedClassListFile == nullptr) {
    classlist_path = default_classlist;
  } else {
    classlist_path = SharedClassListFile;
  }

  log_info(cds)("Loading classes to share ...");
  ClassListParser::parse_classlist(classlist_path,
                                   ClassListParser::_parse_all, CHECK);
  if (ExtraSharedClassListFile) {
    ClassListParser::parse_classlist(ExtraSharedClassListFile,
                                     ClassListParser::_parse_all, CHECK);
  }
  if (classlist_path != default_classlist) {
    struct stat statbuf;
    if (os::stat(default_classlist, &statbuf) == 0) {
      // File exists, let's use it.
      ClassListParser::parse_classlist(default_classlist,
                                       ClassListParser::_parse_lambda_forms_invokers_only, CHECK);
    }
  }

  // Some classes are used at CDS runtime but are not loaded, and therefore archived, at
  // dumptime. We can perform dummmy calls to these classes at dumptime to ensure they
  // are archived.
  exercise_runtime_cds_code(CHECK);

  log_info(cds)("Loading classes to share: done.");
}

void MetaspaceShared::exercise_runtime_cds_code(TRAPS) {
  // Exercise the manifest processing code
  const char* dummy = "Manifest-Version: 1.0\n";
  CDSProtectionDomain::create_jar_manifest(dummy, strlen(dummy), CHECK);

  // Exercise FileSystem and URL code
  CDSProtectionDomain::to_file_URL("dummy.jar", Handle(), CHECK);
}

void MetaspaceShared::preload_and_dump_impl(StaticArchiveBuilder& builder, TRAPS) {
  if (CDSConfig::is_dumping_classic_static_archive()) {
    // We are running with -Xshare:dump
    preload_classes(CHECK);

    if (SharedArchiveConfigFile) {
      log_info(cds)("Reading extra data from %s ...", SharedArchiveConfigFile);
      read_extra_data(THREAD, SharedArchiveConfigFile);
      log_info(cds)("Reading extra data: done.");
    }
  }

  if (CDSConfig::is_dumping_preimage_static_archive()) {
    log_info(cds)("Reading lambda form invokers from JDK default classlist ...");
    char default_classlist[JVM_MAXPATHLEN];
    get_default_classlist(default_classlist, JVM_MAXPATHLEN);
    struct stat statbuf;
    if (os::stat(default_classlist, &statbuf) == 0) {
      ClassListParser::parse_classlist(default_classlist,
                                       ClassListParser::_parse_lambda_forms_invokers_only, CHECK);
    }
  }

  if (CDSConfig::is_dumping_final_static_archive()) {
    if (ExtraSharedClassListFile) {
      log_info(cds)("Loading extra classes from %s ...", ExtraSharedClassListFile);
      ClassListParser::parse_classlist(ExtraSharedClassListFile,
                                       ClassListParser::_parse_all, CHECK);
    }
  }

  // Rewrite and link classes
  log_info(cds)("Rewriting and linking classes ...");

  // Link any classes which got missed. This would happen if we have loaded classes that
  // were not explicitly specified in the classlist. E.g., if an interface implemented by class K
  // fails verification, all other interfaces that were not specified in the classlist but
  // are implemented by K are not verified.
  link_shared_classes(false/*not from jcmd*/, CHECK);
  log_info(cds)("Rewriting and linking classes: done");

#if INCLUDE_CDS_JAVA_HEAP
  if (CDSConfig::is_dumping_heap()) {
    if (!HeapShared::is_archived_boot_layer_available(THREAD)) {
      log_info(cds)("archivedBootLayer not available, disabling full module graph");
      CDSConfig::stop_dumping_full_module_graph();
    }
    HeapShared::init_for_dumping(CHECK);
    ArchiveHeapWriter::init();
    if (CDSConfig::is_dumping_full_module_graph()) {
      ClassLoaderDataShared::ensure_module_entry_tables_exist();
      HeapShared::reset_archived_object_states(CHECK);
    }

    if (CDSConfig::is_dumping_method_handles()) {
      // This assert means that the MethodType and MethodTypeForm tables won't be
      // updated concurrently when we are saving their contents into a side table.
      assert(CDSConfig::allow_only_single_java_thread(), "Required");

      JavaValue result(T_VOID);
      JavaCalls::call_static(&result, vmClasses::MethodType_klass(),
                             vmSymbols::createArchivedObjects(),
                             vmSymbols::void_method_signature(),
                             CHECK);
    }

    if (CDSConfig::is_initing_classes_at_dump_time()) {
      // java.lang.Class::reflectionFactory cannot be archived yet. We set this field
      // to null, and it will be initialized again at runtime.
      log_debug(cds)("Resetting Class::reflectionFactory");
      TempNewSymbol method_name = SymbolTable::new_symbol("resetArchivedStates");
      Symbol* method_sig = vmSymbols::void_method_signature();
      JavaValue result(T_VOID);
      JavaCalls::call_static(&result, vmClasses::Class_klass(),
                             method_name, method_sig, CHECK);

      // Perhaps there is a way to avoid hard-coding these names here.
      // See discussion in JDK-8342481.
    }

    // Do this at the very end, when no Java code will be executed. Otherwise
    // some new strings may be added to the intern table.
    StringTable::allocate_shared_strings_array(CHECK);
  } else {
    log_info(cds)("Not dumping heap, reset CDSConfig::_is_using_optimized_module_handling");
    CDSConfig::stop_using_optimized_module_handling();
  }
#endif

  VM_PopulateDumpSharedSpace op(builder);
  VMThread::execute(&op);

  if (!write_static_archive(&builder, op.map_info(), op.heap_info())) {
    THROW_MSG(vmSymbols::java_io_IOException(), "Encountered error while dumping");
  }
}

bool MetaspaceShared::write_static_archive(ArchiveBuilder* builder, FileMapInfo* map_info, ArchiveHeapInfo* heap_info) {
  // relocate the data so that it can be mapped to MetaspaceShared::requested_base_address()
  // without runtime relocation.
  builder->relocate_to_requested();

  map_info->open_for_write();
  if (!map_info->is_open()) {
    return false;
  }
  builder->write_archive(map_info, heap_info);

  if (AllowArchivingWithJavaAgent) {
    log_warning(cds)("This %s was created with AllowArchivingWithJavaAgent. It should be used "
            "for testing purposes only and should not be used in a production environment", CDSConfig::type_of_archive_being_loaded());
  }
  return true;
}

// Returns true if the class's status has changed.
bool MetaspaceShared::try_link_class(JavaThread* current, InstanceKlass* ik) {
  ExceptionMark em(current);
  JavaThread* THREAD = current; // For exception macros.
  assert(CDSConfig::is_dumping_archive(), "sanity");

  if (ik->is_shared() && !CDSConfig::is_dumping_final_static_archive()) {
    assert(CDSConfig::is_dumping_dynamic_archive(), "must be");
    return false;
  }

  if (ik->is_loaded() && !ik->is_linked() && ik->can_be_verified_at_dumptime() &&
      !SystemDictionaryShared::has_class_failed_verification(ik)) {
    bool saved = BytecodeVerificationLocal;
    if (ik->is_shared_unregistered_class() && ik->class_loader() == nullptr) {
      // The verification decision is based on BytecodeVerificationRemote
      // for non-system classes. Since we are using the null classloader
      // to load non-system classes for customized class loaders during dumping,
      // we need to temporarily change BytecodeVerificationLocal to be the same as
      // BytecodeVerificationRemote. Note this can cause the parent system
      // classes also being verified. The extra overhead is acceptable during
      // dumping.
      BytecodeVerificationLocal = BytecodeVerificationRemote;
    }
    ik->link_class(THREAD);
    if (HAS_PENDING_EXCEPTION) {
      ResourceMark rm(THREAD);
      log_warning(cds)("Preload Warning: Verification failed for %s",
                    ik->external_name());
      CLEAR_PENDING_EXCEPTION;
      SystemDictionaryShared::set_class_has_failed_verification(ik);
    } else {
      assert(!SystemDictionaryShared::has_class_failed_verification(ik), "sanity");
      ik->compute_has_loops_flag_for_methods();
    }
    BytecodeVerificationLocal = saved;
    return true;
  } else {
    return false;
  }
}

void VM_PopulateDumpSharedSpace::dump_java_heap_objects() {
  if (CDSConfig::is_dumping_heap()) {
    HeapShared::write_heap(&_heap_info);
  } else {
    CDSConfig::log_reasons_for_not_dumping_heap();
  }
}

void MetaspaceShared::set_shared_metaspace_range(void* base, void *static_top, void* top) {
  assert(base <= static_top && static_top <= top, "must be");
  _shared_metaspace_static_top = static_top;
  MetaspaceObj::set_shared_metaspace_range(base, top);
}

bool MetaspaceShared::is_shared_dynamic(void* p) {
  if ((p < MetaspaceObj::shared_metaspace_top()) &&
      (p >= _shared_metaspace_static_top)) {
    return true;
  } else {
    return false;
  }
}

bool MetaspaceShared::is_shared_static(void* p) {
  if (is_in_shared_metaspace(p) && !is_shared_dynamic(p)) {
    return true;
  } else {
    return false;
  }
}

// This function is called when the JVM is unable to load the specified archive(s) due to one
// of the following conditions.
// - There's an error that indicates that the archive(s) files were corrupt or otherwise damaged.
// - When -XX:+RequireSharedSpaces is specified, AND the JVM cannot load the archive(s) due
//   to version or classpath mismatch.
void MetaspaceShared::unrecoverable_loading_error(const char* message) {
  log_error(cds)("An error has occurred while processing the %s.", CDSConfig::type_of_archive_being_loaded());
  if (message != nullptr) {
    log_error(cds)("%s", message);
  }

  if (CDSConfig::is_dumping_final_static_archive()) {
    vm_exit_during_initialization("Must be a valid AOT configuration generated by the current JVM", AOTConfiguration);
  } else if (CDSConfig::new_aot_flags_used()) {
    vm_exit_during_initialization("Unable to use AOT cache.", nullptr);
  } else {
    vm_exit_during_initialization("Unable to use shared archive.", nullptr);
  }
}

// This function is called when the JVM is unable to write the specified CDS archive due to an
// unrecoverable error.
void MetaspaceShared::unrecoverable_writing_error(const char* message) {
  writing_error(message);
  vm_direct_exit(1);
}

// This function is called when the JVM is unable to write the specified CDS archive due to a
// an error. The error will be propagated
void MetaspaceShared::writing_error(const char* message) {
  log_error(cds)("An error has occurred while writing the shared archive file.");
  if (message != nullptr) {
    log_error(cds)("%s", message);
  }
}

void MetaspaceShared::initialize_runtime_shared_and_meta_spaces() {
  assert(CDSConfig::is_using_archive(), "Must be called when UseSharedSpaces is enabled");
  MapArchiveResult result = MAP_ARCHIVE_OTHER_FAILURE;

  FileMapInfo* static_mapinfo = open_static_archive();
  FileMapInfo* dynamic_mapinfo = nullptr;

  if (static_mapinfo != nullptr) {
    log_info(cds)("Core region alignment: %zu", static_mapinfo->core_region_alignment());
    dynamic_mapinfo = open_dynamic_archive();

    log_info(cds)("ArchiveRelocationMode: %d", ArchiveRelocationMode);

    // First try to map at the requested address
    result = map_archives(static_mapinfo, dynamic_mapinfo, true);
    if (result == MAP_ARCHIVE_MMAP_FAILURE) {
      // Mapping has failed (probably due to ASLR). Let's map at an address chosen
      // by the OS.
      log_info(cds)("Try to map archive(s) at an alternative address");
      result = map_archives(static_mapinfo, dynamic_mapinfo, false);
    }
  }

  if (result == MAP_ARCHIVE_SUCCESS) {
    bool dynamic_mapped = (dynamic_mapinfo != nullptr && dynamic_mapinfo->is_mapped());
    char* cds_base = static_mapinfo->mapped_base();
    char* cds_end =  dynamic_mapped ? dynamic_mapinfo->mapped_end() : static_mapinfo->mapped_end();
    // Register CDS memory region with LSan.
    LSAN_REGISTER_ROOT_REGION(cds_base, cds_end - cds_base);
    set_shared_metaspace_range(cds_base, static_mapinfo->mapped_end(), cds_end);
    _relocation_delta = static_mapinfo->relocation_delta();
    _requested_base_address = static_mapinfo->requested_base_address();
    if (dynamic_mapped) {
      // turn AutoCreateSharedArchive off if successfully mapped
      AutoCreateSharedArchive = false;
    }
  } else {
    set_shared_metaspace_range(nullptr, nullptr, nullptr);
    if (CDSConfig::is_dumping_dynamic_archive()) {
      log_warning(cds)("-XX:ArchiveClassesAtExit is unsupported when base CDS archive is not loaded. Run with -Xlog:cds for more info.");
    }
    UseSharedSpaces = false;
    // The base archive cannot be mapped. We cannot dump the dynamic shared archive.
    AutoCreateSharedArchive = false;
    CDSConfig::disable_dumping_dynamic_archive();
    log_info(cds)("Unable to map shared spaces");
    if (PrintSharedArchiveAndExit) {
      MetaspaceShared::unrecoverable_loading_error("Unable to use shared archive.");
    } else if (RequireSharedSpaces) {
      MetaspaceShared::unrecoverable_loading_error("Unable to map shared spaces");
    }
  }

  // If mapping failed and -XShare:on, the vm should exit
  bool has_failed = false;
  if (static_mapinfo != nullptr && !static_mapinfo->is_mapped()) {
    has_failed = true;
    delete static_mapinfo;
  }
  if (dynamic_mapinfo != nullptr && !dynamic_mapinfo->is_mapped()) {
    has_failed = true;
    delete dynamic_mapinfo;
  }
  if (RequireSharedSpaces && has_failed) {
      MetaspaceShared::unrecoverable_loading_error("Unable to map shared spaces");
  }
}

FileMapInfo* MetaspaceShared::open_static_archive() {
  const char* static_archive = CDSConfig::static_archive_path();
  assert(static_archive != nullptr, "sanity");
  FileMapInfo* mapinfo = new FileMapInfo(static_archive, true);
  if (!mapinfo->initialize()) {
    delete(mapinfo);
    return nullptr;
  }
  return mapinfo;
}

FileMapInfo* MetaspaceShared::open_dynamic_archive() {
  if (CDSConfig::is_dumping_dynamic_archive()) {
    return nullptr;
  }
  const char* dynamic_archive = CDSConfig::dynamic_archive_path();
  if (dynamic_archive == nullptr) {
    return nullptr;
  }

  FileMapInfo* mapinfo = new FileMapInfo(dynamic_archive, false);
  if (!mapinfo->initialize()) {
    delete(mapinfo);
    if (RequireSharedSpaces) {
      MetaspaceShared::unrecoverable_loading_error("Failed to initialize dynamic archive");
    }
    return nullptr;
  }
  return mapinfo;
}

// use_requested_addr:
//  true  = map at FileMapHeader::_requested_base_address
//  false = map at an alternative address picked by OS.
MapArchiveResult MetaspaceShared::map_archives(FileMapInfo* static_mapinfo, FileMapInfo* dynamic_mapinfo,
                                               bool use_requested_addr) {
  if (use_requested_addr && static_mapinfo->requested_base_address() == nullptr) {
    log_info(cds)("Archive(s) were created with -XX:SharedBaseAddress=0. Always map at os-selected address.");
    return MAP_ARCHIVE_MMAP_FAILURE;
  }

  PRODUCT_ONLY(if (ArchiveRelocationMode == 1 && use_requested_addr) {
      // For product build only -- this is for benchmarking the cost of doing relocation.
      // For debug builds, the check is done below, after reserving the space, for better test coverage
      // (see comment below).
      log_info(cds)("ArchiveRelocationMode == 1: always map archive(s) at an alternative address");
      return MAP_ARCHIVE_MMAP_FAILURE;
    });

  if (ArchiveRelocationMode == 2 && !use_requested_addr) {
    log_info(cds)("ArchiveRelocationMode == 2: never map archive(s) at an alternative address");
    return MAP_ARCHIVE_MMAP_FAILURE;
  };

  if (dynamic_mapinfo != nullptr) {
    // Ensure that the OS won't be able to allocate new memory spaces between the two
    // archives, or else it would mess up the simple comparison in MetaspaceObj::is_shared().
    assert(static_mapinfo->mapping_end_offset() == dynamic_mapinfo->mapping_base_offset(), "no gap");
  }

  ReservedSpace total_space_rs, archive_space_rs, class_space_rs;
  MapArchiveResult result = MAP_ARCHIVE_OTHER_FAILURE;
  char* mapped_base_address = reserve_address_space_for_archives(static_mapinfo,
                                                                 dynamic_mapinfo,
                                                                 use_requested_addr,
                                                                 total_space_rs,
                                                                 archive_space_rs,
                                                                 class_space_rs);
  if (mapped_base_address == nullptr) {
    result = MAP_ARCHIVE_MMAP_FAILURE;
    log_debug(cds)("Failed to reserve spaces (use_requested_addr=%u)", (unsigned)use_requested_addr);
  } else {

#ifdef ASSERT
    // Some sanity checks after reserving address spaces for archives
    //  and class space.
    assert(archive_space_rs.is_reserved(), "Sanity");
    if (Metaspace::using_class_space()) {
      // Class space must closely follow the archive space. Both spaces
      //  must be aligned correctly.
      assert(class_space_rs.is_reserved(),
             "A class space should have been reserved");
      assert(class_space_rs.base() >= archive_space_rs.end(),
             "class space should follow the cds archive space");
      assert(is_aligned(archive_space_rs.base(),
                        core_region_alignment()),
             "Archive space misaligned");
      assert(is_aligned(class_space_rs.base(),
                        Metaspace::reserve_alignment()),
             "class space misaligned");
    }
#endif // ASSERT

    log_info(cds)("Reserved archive_space_rs [" INTPTR_FORMAT " - " INTPTR_FORMAT "] (%zu) bytes",
                   p2i(archive_space_rs.base()), p2i(archive_space_rs.end()), archive_space_rs.size());
    log_info(cds)("Reserved class_space_rs   [" INTPTR_FORMAT " - " INTPTR_FORMAT "] (%zu) bytes",
                   p2i(class_space_rs.base()), p2i(class_space_rs.end()), class_space_rs.size());

    if (MetaspaceShared::use_windows_memory_mapping()) {
      // We have now reserved address space for the archives, and will map in
      //  the archive files into this space.
      //
      // Special handling for Windows: on Windows we cannot map a file view
      //  into an existing memory mapping. So, we unmap the address range we
      //  just reserved again, which will make it available for mapping the
      //  archives.
      // Reserving this range has not been for naught however since it makes
      //  us reasonably sure the address range is available.
      //
      // But still it may fail, since between unmapping the range and mapping
      //  in the archive someone else may grab the address space. Therefore
      //  there is a fallback in FileMap::map_region() where we just read in
      //  the archive files sequentially instead of mapping it in. We couple
      //  this with use_requested_addr, since we're going to patch all the
      //  pointers anyway so there's no benefit to mmap.
      if (use_requested_addr) {
        assert(!total_space_rs.is_reserved(), "Should not be reserved for Windows");
        log_info(cds)("Windows mmap workaround: releasing archive space.");
        MemoryReserver::release(archive_space_rs);
        // Mark as not reserved
        archive_space_rs = {};
      }
    }
    MapArchiveResult static_result = map_archive(static_mapinfo, mapped_base_address, archive_space_rs);
    MapArchiveResult dynamic_result = (static_result == MAP_ARCHIVE_SUCCESS) ?
                                     map_archive(dynamic_mapinfo, mapped_base_address, archive_space_rs) : MAP_ARCHIVE_OTHER_FAILURE;

    DEBUG_ONLY(if (ArchiveRelocationMode == 1 && use_requested_addr) {
      // This is for simulating mmap failures at the requested address. In
      //  debug builds, we do it here (after all archives have possibly been
      //  mapped), so we can thoroughly test the code for failure handling
      //  (releasing all allocated resource, etc).
      log_info(cds)("ArchiveRelocationMode == 1: always map archive(s) at an alternative address");
      if (static_result == MAP_ARCHIVE_SUCCESS) {
        static_result = MAP_ARCHIVE_MMAP_FAILURE;
      }
      if (dynamic_result == MAP_ARCHIVE_SUCCESS) {
        dynamic_result = MAP_ARCHIVE_MMAP_FAILURE;
      }
    });

    if (static_result == MAP_ARCHIVE_SUCCESS) {
      if (dynamic_result == MAP_ARCHIVE_SUCCESS) {
        result = MAP_ARCHIVE_SUCCESS;
      } else if (dynamic_result == MAP_ARCHIVE_OTHER_FAILURE) {
        assert(dynamic_mapinfo != nullptr && !dynamic_mapinfo->is_mapped(), "must have failed");
        // No need to retry mapping the dynamic archive again, as it will never succeed
        // (bad file, etc) -- just keep the base archive.
        log_warning(cds, dynamic)("Unable to use shared archive. The top archive failed to load: %s",
                                  dynamic_mapinfo->full_path());
        result = MAP_ARCHIVE_SUCCESS;
        // TODO, we can give the unused space for the dynamic archive to class_space_rs, but there's no
        // easy API to do that right now.
      } else {
        result = MAP_ARCHIVE_MMAP_FAILURE;
      }
    } else if (static_result == MAP_ARCHIVE_OTHER_FAILURE) {
      result = MAP_ARCHIVE_OTHER_FAILURE;
    } else {
      result = MAP_ARCHIVE_MMAP_FAILURE;
    }
  }

  if (result == MAP_ARCHIVE_SUCCESS) {
    SharedBaseAddress = (size_t)mapped_base_address;
#ifdef _LP64
        if (Metaspace::using_class_space()) {
          // Set up ccs in metaspace.
          Metaspace::initialize_class_space(class_space_rs);

          // Set up compressed Klass pointer encoding: the encoding range must
          //  cover both archive and class space.
          address cds_base = (address)static_mapinfo->mapped_base();
          address ccs_end = (address)class_space_rs.end();
          assert(ccs_end > cds_base, "Sanity check");
          if (INCLUDE_CDS_JAVA_HEAP || UseCompactObjectHeaders) {
            // The CDS archive may contain narrow Klass IDs that were precomputed at archive generation time:
            // - every archived java object header (only if INCLUDE_CDS_JAVA_HEAP)
            // - every archived Klass' prototype   (only if +UseCompactObjectHeaders)
            //
            // In order for those IDs to still be valid, we need to dictate base and shift: base should be the
            // mapping start, shift the shift used at archive generation time.
            address precomputed_narrow_klass_base = cds_base;
            const int precomputed_narrow_klass_shift = ArchiveBuilder::precomputed_narrow_klass_shift();
            CompressedKlassPointers::initialize_for_given_encoding(
              cds_base, ccs_end - cds_base, // Klass range
              precomputed_narrow_klass_base, precomputed_narrow_klass_shift // precomputed encoding, see ArchiveBuilder
            );
          } else {
            // Let JVM freely chose encoding base and shift
            CompressedKlassPointers::initialize (
              cds_base, ccs_end - cds_base // Klass range
              );
          }
          // map_or_load_heap_region() compares the current narrow oop and klass encodings
          // with the archived ones, so it must be done after all encodings are determined.
          static_mapinfo->map_or_load_heap_region();
        }
#endif // _LP64
    log_info(cds)("initial optimized module handling: %s", CDSConfig::is_using_optimized_module_handling() ? "enabled" : "disabled");
    log_info(cds)("initial full module graph: %s", CDSConfig::is_using_full_module_graph() ? "enabled" : "disabled");
  } else {
    unmap_archive(static_mapinfo);
    unmap_archive(dynamic_mapinfo);
    release_reserved_spaces(total_space_rs, archive_space_rs, class_space_rs);
  }

  return result;
}


// This will reserve two address spaces suitable to house Klass structures, one
//  for the cds archives (static archive and optionally dynamic archive) and
//  optionally one move for ccs.
//
// Since both spaces must fall within the compressed class pointer encoding
//  range, they are allocated close to each other.
//
// Space for archives will be reserved first, followed by a potential gap,
//  followed by the space for ccs:
//
// +-- Base address             A        B                     End
// |                            |        |                      |
// v                            v        v                      v
// +-------------+--------------+        +----------------------+
// | static arc  | [dyn. arch]  | [gap]  | compr. class space   |
// +-------------+--------------+        +----------------------+
//
// (The gap may result from different alignment requirements between metaspace
//  and CDS)
//
// If UseCompressedClassPointers is disabled, only one address space will be
//  reserved:
//
// +-- Base address             End
// |                            |
// v                            v
// +-------------+--------------+
// | static arc  | [dyn. arch]  |
// +-------------+--------------+
//
// Base address: If use_archive_base_addr address is true, the Base address is
//  determined by the address stored in the static archive. If
//  use_archive_base_addr address is false, this base address is determined
//  by the platform.
//
// If UseCompressedClassPointers=1, the range encompassing both spaces will be
//  suitable to en/decode narrow Klass pointers: the base will be valid for
//  encoding, the range [Base, End) and not surpass the max. range for that encoding.
//
// Return:
//
// - On success:
//    - total_space_rs will be reserved as whole for archive_space_rs and
//      class_space_rs if UseCompressedClassPointers is true.
//      On Windows, try reserve archive_space_rs and class_space_rs
//      separately first if use_archive_base_addr is true.
//    - archive_space_rs will be reserved and large enough to host static and
//      if needed dynamic archive: [Base, A).
//      archive_space_rs.base and size will be aligned to CDS reserve
//      granularity.
//    - class_space_rs: If UseCompressedClassPointers=1, class_space_rs will
//      be reserved. Its start address will be aligned to metaspace reserve
//      alignment, which may differ from CDS alignment. It will follow the cds
//      archive space, close enough such that narrow class pointer encoding
//      covers both spaces.
//      If UseCompressedClassPointers=0, class_space_rs remains unreserved.
// - On error: null is returned and the spaces remain unreserved.
char* MetaspaceShared::reserve_address_space_for_archives(FileMapInfo* static_mapinfo,
                                                          FileMapInfo* dynamic_mapinfo,
                                                          bool use_archive_base_addr,
                                                          ReservedSpace& total_space_rs,
                                                          ReservedSpace& archive_space_rs,
                                                          ReservedSpace& class_space_rs) {

  address const base_address = (address) (use_archive_base_addr ? static_mapinfo->requested_base_address() : nullptr);
  const size_t archive_space_alignment = core_region_alignment();

  // Size and requested location of the archive_space_rs (for both static and dynamic archives)
  assert(static_mapinfo->mapping_base_offset() == 0, "Must be");
  size_t archive_end_offset  = (dynamic_mapinfo == nullptr) ? static_mapinfo->mapping_end_offset() : dynamic_mapinfo->mapping_end_offset();
  size_t archive_space_size = align_up(archive_end_offset, archive_space_alignment);

  if (!Metaspace::using_class_space()) {
    // Get the simple case out of the way first:
    // no compressed class space, simple allocation.

    // When running without class space, requested archive base should be aligned to cds core alignment.
    assert(is_aligned(base_address, archive_space_alignment),
             "Archive base address unaligned: " PTR_FORMAT ", needs alignment: %zu.",
             p2i(base_address), archive_space_alignment);

    archive_space_rs = MemoryReserver::reserve((char*)base_address,
                                               archive_space_size,
                                               archive_space_alignment,
                                               os::vm_page_size());
    if (archive_space_rs.is_reserved()) {
      assert(base_address == nullptr ||
             (address)archive_space_rs.base() == base_address, "Sanity");
      // Register archive space with NMT.
      MemTracker::record_virtual_memory_tag(archive_space_rs.base(), mtClassShared);
      return archive_space_rs.base();
    }
    return nullptr;
  }

#ifdef _LP64

  // Complex case: two spaces adjacent to each other, both to be addressable
  //  with narrow class pointers.
  // We reserve the whole range spanning both spaces, then split that range up.

  const size_t class_space_alignment = Metaspace::reserve_alignment();

  // When running with class space, requested archive base must satisfy both cds core alignment
  // and class space alignment.
  const size_t base_address_alignment = MAX2(class_space_alignment, archive_space_alignment);
  assert(is_aligned(base_address, base_address_alignment),
           "Archive base address unaligned: " PTR_FORMAT ", needs alignment: %zu.",
           p2i(base_address), base_address_alignment);

  size_t class_space_size = CompressedClassSpaceSize;
  assert(CompressedClassSpaceSize > 0 &&
         is_aligned(CompressedClassSpaceSize, class_space_alignment),
         "CompressedClassSpaceSize malformed: %zu", CompressedClassSpaceSize);

  const size_t ccs_begin_offset = align_up(archive_space_size, class_space_alignment);
  const size_t gap_size = ccs_begin_offset - archive_space_size;

  // Reduce class space size if it would not fit into the Klass encoding range
  constexpr size_t max_encoding_range_size = 4 * G;
  guarantee(archive_space_size < max_encoding_range_size - class_space_alignment, "Archive too large");
  if ((archive_space_size + gap_size + class_space_size) > max_encoding_range_size) {
    class_space_size = align_down(max_encoding_range_size - archive_space_size - gap_size, class_space_alignment);
    log_info(metaspace)("CDS initialization: reducing class space size from %zu to %zu",
        CompressedClassSpaceSize, class_space_size);
    FLAG_SET_ERGO(CompressedClassSpaceSize, class_space_size);
  }

  const size_t total_range_size =
      archive_space_size + gap_size + class_space_size;

  // Test that class space base address plus shift can be decoded by aarch64, when restored.
  const int precomputed_narrow_klass_shift = ArchiveBuilder::precomputed_narrow_klass_shift();
  if (!CompressedKlassPointers::check_klass_decode_mode(base_address, precomputed_narrow_klass_shift,
                                                        total_range_size)) {
    log_info(cds)("CDS initialization: Cannot use SharedBaseAddress " PTR_FORMAT " with precomputed shift %d.",
                  p2i(base_address), precomputed_narrow_klass_shift);
    use_archive_base_addr = false;
  }

  assert(total_range_size > ccs_begin_offset, "must be");
  if (use_windows_memory_mapping() && use_archive_base_addr) {
    if (base_address != nullptr) {
      // On Windows, we cannot safely split a reserved memory space into two (see JDK-8255917).
      // Hence, we optimistically reserve archive space and class space side-by-side. We only
      // do this for use_archive_base_addr=true since for use_archive_base_addr=false case
      // caller will not split the combined space for mapping, instead read the archive data
      // via sequential file IO.
      address ccs_base = base_address + archive_space_size + gap_size;
      archive_space_rs = MemoryReserver::reserve((char*)base_address,
                                                 archive_space_size,
                                                 archive_space_alignment,
                                                 os::vm_page_size());
      class_space_rs   = MemoryReserver::reserve((char*)ccs_base,
                                                 class_space_size,
                                                 class_space_alignment,
                                                 os::vm_page_size());
    }
    if (!archive_space_rs.is_reserved() || !class_space_rs.is_reserved()) {
      release_reserved_spaces(total_space_rs, archive_space_rs, class_space_rs);
      return nullptr;
    }
    // NMT: fix up the space tags
    MemTracker::record_virtual_memory_tag(archive_space_rs.base(), mtClassShared);
    MemTracker::record_virtual_memory_tag(class_space_rs.base(), mtClass);
  } else {
    if (use_archive_base_addr && base_address != nullptr) {
      total_space_rs = MemoryReserver::reserve((char*) base_address,
                                               total_range_size,
                                               base_address_alignment,
                                               os::vm_page_size());
    } else {
      // We did not manage to reserve at the preferred address, or were instructed to relocate. In that
      // case we reserve wherever possible, but the start address needs to be encodable as narrow Klass
      // encoding base since the archived heap objects contain narrow Klass IDs pre-calculated toward the start
      // of the shared Metaspace. That prevents us from using zero-based encoding and therefore we won't
      // try allocating in low-address regions.
      total_space_rs = Metaspace::reserve_address_space_for_compressed_classes(total_range_size, false /* optimize_for_zero_base */);
    }

    if (!total_space_rs.is_reserved()) {
      return nullptr;
    }

    // Paranoid checks:
    assert(!use_archive_base_addr || (address)total_space_rs.base() == base_address,
           "Sanity (" PTR_FORMAT " vs " PTR_FORMAT ")", p2i(base_address), p2i(total_space_rs.base()));
    assert(is_aligned(total_space_rs.base(), base_address_alignment), "Sanity");
    assert(total_space_rs.size() == total_range_size, "Sanity");

    // Now split up the space into ccs and cds archive. For simplicity, just leave
    //  the gap reserved at the end of the archive space. Do not do real splitting.
    archive_space_rs = total_space_rs.first_part(ccs_begin_offset,
                                                 (size_t)archive_space_alignment);
    class_space_rs = total_space_rs.last_part(ccs_begin_offset);
    MemTracker::record_virtual_memory_split_reserved(total_space_rs.base(), total_space_rs.size(),
                                                     ccs_begin_offset, mtClassShared, mtClass);
  }
  assert(is_aligned(archive_space_rs.base(), archive_space_alignment), "Sanity");
  assert(is_aligned(archive_space_rs.size(), archive_space_alignment), "Sanity");
  assert(is_aligned(class_space_rs.base(), class_space_alignment), "Sanity");
  assert(is_aligned(class_space_rs.size(), class_space_alignment), "Sanity");


  return archive_space_rs.base();

#else
  ShouldNotReachHere();
  return nullptr;
#endif

}

void MetaspaceShared::release_reserved_spaces(ReservedSpace& total_space_rs,
                                              ReservedSpace& archive_space_rs,
                                              ReservedSpace& class_space_rs) {
  if (total_space_rs.is_reserved()) {
    log_debug(cds)("Released shared space (archive + class) " INTPTR_FORMAT, p2i(total_space_rs.base()));
    MemoryReserver::release(total_space_rs);
    total_space_rs = {};
  } else {
    if (archive_space_rs.is_reserved()) {
      log_debug(cds)("Released shared space (archive) " INTPTR_FORMAT, p2i(archive_space_rs.base()));
      MemoryReserver::release(archive_space_rs);
      archive_space_rs = {};
    }
    if (class_space_rs.is_reserved()) {
      log_debug(cds)("Released shared space (classes) " INTPTR_FORMAT, p2i(class_space_rs.base()));
      MemoryReserver::release(class_space_rs);
      class_space_rs = {};
    }
  }
}

static int archive_regions[]     = { MetaspaceShared::rw, MetaspaceShared::ro };
static int archive_regions_count = 2;

MapArchiveResult MetaspaceShared::map_archive(FileMapInfo* mapinfo, char* mapped_base_address, ReservedSpace rs) {
  assert(CDSConfig::is_using_archive(), "must be runtime");
  if (mapinfo == nullptr) {
    return MAP_ARCHIVE_SUCCESS; // The dynamic archive has not been specified. No error has happened -- trivially succeeded.
  }

  mapinfo->set_is_mapped(false);
  if (mapinfo->core_region_alignment() != (size_t)core_region_alignment()) {
    log_info(cds)("Unable to map CDS archive -- core_region_alignment() expected: %zu"
                  " actual: %zu", mapinfo->core_region_alignment(), core_region_alignment());
    return MAP_ARCHIVE_OTHER_FAILURE;
  }

  MapArchiveResult result =
    mapinfo->map_regions(archive_regions, archive_regions_count, mapped_base_address, rs);

  if (result != MAP_ARCHIVE_SUCCESS) {
    unmap_archive(mapinfo);
    return result;
  }

  if (!mapinfo->validate_class_location()) {
    unmap_archive(mapinfo);
    return MAP_ARCHIVE_OTHER_FAILURE;
  }

  if (mapinfo->is_static()) {
    // Currently, only static archive uses early serialized data.
    char* buffer = mapinfo->early_serialized_data();
    intptr_t* array = (intptr_t*)buffer;
    ReadClosure rc(&array, (intptr_t)mapped_base_address);
    early_serialize(&rc);
  }

  if (!mapinfo->validate_aot_class_linking()) {
    unmap_archive(mapinfo);
    return MAP_ARCHIVE_OTHER_FAILURE;
  }

  mapinfo->set_is_mapped(true);
  return MAP_ARCHIVE_SUCCESS;
}

void MetaspaceShared::unmap_archive(FileMapInfo* mapinfo) {
  assert(CDSConfig::is_using_archive(), "must be runtime");
  if (mapinfo != nullptr) {
    mapinfo->unmap_regions(archive_regions, archive_regions_count);
    mapinfo->unmap_region(MetaspaceShared::bm);
    mapinfo->set_is_mapped(false);
  }
}

// For -XX:PrintSharedArchiveAndExit
class CountSharedSymbols : public SymbolClosure {
 private:
   int _count;
 public:
   CountSharedSymbols() : _count(0) {}
  void do_symbol(Symbol** sym) {
    _count++;
  }
  int total() { return _count; }

};

// Read the miscellaneous data from the shared file, and
// serialize it out to its various destinations.

void MetaspaceShared::initialize_shared_spaces() {
  FileMapInfo *static_mapinfo = FileMapInfo::current_info();

  // Verify various attributes of the archive, plus initialize the
  // shared string/symbol tables.
  char* buffer = static_mapinfo->serialized_data();
  intptr_t* array = (intptr_t*)buffer;
  ReadClosure rc(&array, (intptr_t)SharedBaseAddress);
  serialize(&rc);

  // Finish up archived heap initialization. These must be
  // done after ReadClosure.
  static_mapinfo->patch_heap_embedded_pointers();
  ArchiveHeapLoader::finish_initialization();
  Universe::load_archived_object_instances();

  // Close the mapinfo file
  static_mapinfo->close();

  static_mapinfo->unmap_region(MetaspaceShared::bm);

  FileMapInfo *dynamic_mapinfo = FileMapInfo::dynamic_info();
  if (dynamic_mapinfo != nullptr) {
    intptr_t* buffer = (intptr_t*)dynamic_mapinfo->serialized_data();
    ReadClosure rc(&buffer, (intptr_t)SharedBaseAddress);
    ArchiveBuilder::serialize_dynamic_archivable_items(&rc);
    DynamicArchive::setup_array_klasses();
    dynamic_mapinfo->close();
    dynamic_mapinfo->unmap_region(MetaspaceShared::bm);
  }

  LogStreamHandle(Info, cds) lsh;
  if (lsh.is_enabled()) {
    lsh.print("Using AOT-linked classes: %s (static archive: %s aot-linked classes",
              BOOL_TO_STR(CDSConfig::is_using_aot_linked_classes()),
              static_mapinfo->header()->has_aot_linked_classes() ? "has" : "no");
    if (dynamic_mapinfo != nullptr) {
      lsh.print(", dynamic archive: %s aot-linked classes",
                dynamic_mapinfo->header()->has_aot_linked_classes() ? "has" : "no");
    }
    lsh.print_cr(")");
  }

  // Set up LambdaFormInvokers::_lambdaform_lines for dynamic dump
  if (CDSConfig::is_dumping_dynamic_archive()) {
    // Read stored LF format lines stored in static archive
    LambdaFormInvokers::read_static_archive_invokers();
  }

  if (PrintSharedArchiveAndExit) {
    // Print archive names
    if (dynamic_mapinfo != nullptr) {
      tty->print_cr("\n\nBase archive name: %s", CDSConfig::static_archive_path());
      tty->print_cr("Base archive version %d", static_mapinfo->version());
    } else {
      tty->print_cr("Static archive name: %s", static_mapinfo->full_path());
      tty->print_cr("Static archive version %d", static_mapinfo->version());
    }

    SystemDictionaryShared::print_shared_archive(tty);
    if (dynamic_mapinfo != nullptr) {
      tty->print_cr("\n\nDynamic archive name: %s", dynamic_mapinfo->full_path());
      tty->print_cr("Dynamic archive version %d", dynamic_mapinfo->version());
      SystemDictionaryShared::print_shared_archive(tty, false/*dynamic*/);
    }

    // collect shared symbols and strings
    CountSharedSymbols cl;
    SymbolTable::shared_symbols_do(&cl);
    tty->print_cr("Number of shared symbols: %d", cl.total());
    tty->print_cr("Number of shared strings: %zu", StringTable::shared_entry_count());
    tty->print_cr("VM version: %s\r\n", static_mapinfo->vm_version());
    if (FileMapInfo::current_info() == nullptr || _archive_loading_failed) {
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

  if (CDSConfig::is_using_archive()) {
    // remap the shared readonly space to shared readwrite, private
    FileMapInfo* mapinfo = FileMapInfo::current_info();
    if (!mapinfo->remap_shared_readonly_as_readwrite()) {
      return false;
    }
    if (FileMapInfo::dynamic_info() != nullptr) {
      mapinfo = FileMapInfo::dynamic_info();
      if (!mapinfo->remap_shared_readonly_as_readwrite()) {
        return false;
      }
    }
    _remapped_readwrite = true;
  }
  return true;
}

void MetaspaceShared::print_on(outputStream* st) {
  if (CDSConfig::is_using_archive()) {
    st->print("CDS archive(s) mapped at: ");
    address base = (address)MetaspaceObj::shared_metaspace_base();
    address static_top = (address)_shared_metaspace_static_top;
    address top = (address)MetaspaceObj::shared_metaspace_top();
    st->print("[" PTR_FORMAT "-" PTR_FORMAT "-" PTR_FORMAT "), ", p2i(base), p2i(static_top), p2i(top));
    st->print("size %zu, ", top - base);
    st->print("SharedBaseAddress: " PTR_FORMAT ", ArchiveRelocationMode: %d.", SharedBaseAddress, ArchiveRelocationMode);
  } else {
    st->print("CDS archive(s) not mapped");
  }
  st->cr();
}
