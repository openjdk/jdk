/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classListParser.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/loaderConstraints.hpp"
#include "classfile/placeholders.hpp"
#include "classfile/sharedClassUtil.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "code/codeCache.hpp"
#include "gc/shared/gcLocker.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/bytecodes.hpp"
#include "logging/log.hpp"
#include "logging/logMessage.hpp"
#include "memory/filemap.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceClassLoaderKlass.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayKlass.hpp"
#include "prims/jvm.h"
#include "runtime/timerTrace.hpp"
#include "runtime/os.hpp"
#include "runtime/signature.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vm_operations.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/hashtable.inline.hpp"

int MetaspaceShared::_max_alignment = 0;

ReservedSpace* MetaspaceShared::_shared_rs = NULL;

MetaspaceSharedStats MetaspaceShared::_stats;

bool MetaspaceShared::_has_error_classes;
bool MetaspaceShared::_archive_loading_failed = false;
bool MetaspaceShared::_remapped_readwrite = false;
address MetaspaceShared::_cds_i2i_entry_code_buffers = NULL;
size_t MetaspaceShared::_cds_i2i_entry_code_buffers_size = 0;
SharedMiscRegion MetaspaceShared::_mc;
SharedMiscRegion MetaspaceShared::_md;
SharedMiscRegion MetaspaceShared::_od;

void SharedMiscRegion::initialize(ReservedSpace rs, size_t committed_byte_size,  SharedSpaceType space_type) {
  _vs.initialize(rs, committed_byte_size);
  _alloc_top = _vs.low();
  _space_type = space_type;
}

// NOT thread-safe, but this is called during dump time in single-threaded mode.
char* SharedMiscRegion::alloc(size_t num_bytes) {
  assert(DumpSharedSpaces, "dump time only");
  size_t alignment = sizeof(char*);
  num_bytes = align_size_up(num_bytes, alignment);
  _alloc_top = (char*)align_ptr_up(_alloc_top, alignment);
  if (_alloc_top + num_bytes > _vs.high()) {
    report_out_of_shared_space(_space_type);
  }

  char* p = _alloc_top;
  _alloc_top += num_bytes;

  memset(p, 0, num_bytes);
  return p;
}

void MetaspaceShared::initialize_shared_rs(ReservedSpace* rs) {
  assert(DumpSharedSpaces, "dump time only");
  _shared_rs = rs;

  size_t core_spaces_size = FileMapInfo::core_spaces_size();
  size_t metadata_size = SharedReadOnlySize + SharedReadWriteSize;

  // Split into the core and optional sections
  ReservedSpace core_data = _shared_rs->first_part(core_spaces_size);
  ReservedSpace optional_data = _shared_rs->last_part(core_spaces_size);

  // The RO/RW and the misc sections
  ReservedSpace shared_ro_rw = core_data.first_part(metadata_size);
  ReservedSpace misc_section = core_data.last_part(metadata_size);

  // Now split the misc code and misc data sections.
  ReservedSpace md_rs   = misc_section.first_part(SharedMiscDataSize);
  ReservedSpace mc_rs   = misc_section.last_part(SharedMiscDataSize);

  _md.initialize(md_rs, SharedMiscDataSize, SharedMiscData);
  _mc.initialize(mc_rs, SharedMiscCodeSize, SharedMiscCode);
  _od.initialize(optional_data, metadata_size, SharedOptional);
}

// Read/write a data stream for restoring/preserving metadata pointers and
// miscellaneous data from/to the shared archive file.

void MetaspaceShared::serialize(SerializeClosure* soc, GrowableArray<MemRegion> *string_space,
                                size_t* space_size) {
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
  StringTable::serialize(soc, string_space, space_size);
  soc->do_tag(--tag);

  soc->do_tag(666);
}

address MetaspaceShared::cds_i2i_entry_code_buffers(size_t total_size) {
  if (DumpSharedSpaces) {
    if (_cds_i2i_entry_code_buffers == NULL) {
      _cds_i2i_entry_code_buffers = (address)misc_data_space_alloc(total_size);
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
class CollectClassesClosure : public KlassClosure {
  void do_klass(Klass* k) {
    _global_klass_objects->append_if_missing(k);
  }
};

static void remove_unshareable_in_classes() {
  for (int i = 0; i < _global_klass_objects->length(); i++) {
    Klass* k = _global_klass_objects->at(i);
    k->remove_unshareable_info();
  }
}

static void rewrite_nofast_bytecode(Method* method) {
  RawBytecodeStream bcs(method);
  while (!bcs.is_last_bytecode()) {
    Bytecodes::Code opcode = bcs.raw_next();
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
  intptr_t* next(int vtable_size) {
    return &_cloned_vtable[vtable_size];
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
  static intptr_t* allocate(const char* name, intptr_t* top, intptr_t* end);

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
intptr_t* CppVtableCloner<T>::allocate(const char* name, intptr_t* top, intptr_t* end) {
  int n = get_vtable_length(name);
  _info = (CppVtableInfo*)top;
  intptr_t* next = _info->next(n);

  if (next > end) {
    report_out_of_shared_space(SharedMiscData);
  }
  _info->set_vtable_size(n);

  intptr_t* p = clone_vtable(name, _info);
  assert(p == next, "must be");

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
  top = CppVtableCloner<c>::allocate(#c, top, end);

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
intptr_t* MetaspaceShared::allocate_cpp_vtable_clones(intptr_t* top, intptr_t* end) {
  assert(DumpSharedSpaces, "dump-time only");
  // Layout (each slot is a intptr_t):
  //   [number of slots in the first vtable = n1]
  //   [ <n1> slots for the first vtable]
  //   [number of slots in the first second = n2]
  //   [ <n2> slots for the second vtable]
  //   ...
  // The order of the vtables is the same as the CPP_VTAB_PATCH_TYPES_DO macro.
  CPP_VTABLE_PATCH_TYPES_DO(ALLOC_CPP_VTABLE_CLONE);
  return top;
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
  assert(is_in_shared_space(m), "must be");
  return CppVtableCloner<Method>::is_valid_shared_object(m);
}

// Closure for serializing initialization data out to a data area to be
// written to the shared file.

class WriteClosure : public SerializeClosure {
private:
  intptr_t* top;
  char* end;

  inline void check_space() {
    if ((char*)top + sizeof(intptr_t) > end) {
      report_out_of_shared_space(SharedMiscData);
    }
  }

public:
  WriteClosure(char* md_top, char* md_end) {
    top = (intptr_t*)md_top;
    end = md_end;
  }

  char* get_top() { return (char*)top; }

  void do_ptr(void** p) {
    check_space();
    *top = (intptr_t)*p;
    ++top;
  }

  void do_u4(u4* p) {
    void* ptr = (void*)(uintx(*p));
    do_ptr(&ptr);
  }

  void do_tag(int tag) {
    check_space();
    *top = (intptr_t)tag;
    ++top;
  }

  void do_region(u_char* start, size_t size) {
    if ((char*)top + size > end) {
      report_out_of_shared_space(SharedMiscData);
    }
    assert((intptr_t)start % sizeof(intptr_t) == 0, "bad alignment");
    assert(size % sizeof(intptr_t) == 0, "bad size");
    do_tag((int)size);
    while (size > 0) {
      *top = *(intptr_t*)start;
      ++top;
      start += sizeof(intptr_t);
      size -= sizeof(intptr_t);
    }
  }

  bool reading() const { return false; }
};

// This is for dumping detailed statistics for the allocations
// in the shared spaces.
class DumpAllocClosure : public Metaspace::AllocRecordClosure {
public:

  // Here's poor man's enum inheritance
#define SHAREDSPACE_OBJ_TYPES_DO(f) \
  METASPACE_OBJ_TYPES_DO(f) \
  f(SymbolHashentry) \
  f(SymbolBucket) \
  f(StringHashentry) \
  f(StringBucket) \
  f(Other)

#define SHAREDSPACE_OBJ_TYPE_DECLARE(name) name ## Type,
#define SHAREDSPACE_OBJ_TYPE_NAME_CASE(name) case name ## Type: return #name;

  enum Type {
    // Types are MetaspaceObj::ClassType, MetaspaceObj::SymbolType, etc
    SHAREDSPACE_OBJ_TYPES_DO(SHAREDSPACE_OBJ_TYPE_DECLARE)
    _number_of_types
  };

  static const char * type_name(Type type) {
    switch(type) {
    SHAREDSPACE_OBJ_TYPES_DO(SHAREDSPACE_OBJ_TYPE_NAME_CASE)
    default:
      ShouldNotReachHere();
      return NULL;
    }
  }

public:
  enum {
    RO = 0,
    RW = 1
  };

  int _counts[2][_number_of_types];
  int _bytes [2][_number_of_types];
  int _which;

  DumpAllocClosure() {
    memset(_counts, 0, sizeof(_counts));
    memset(_bytes,  0, sizeof(_bytes));
  };

  void iterate_metaspace(Metaspace* space, int which) {
    assert(which == RO || which == RW, "sanity");
    _which = which;
    space->iterate(this);
  }

  virtual void doit(address ptr, MetaspaceObj::Type type, int byte_size) {
    assert(int(type) >= 0 && type < MetaspaceObj::_number_of_types, "sanity");
    _counts[_which][type] ++;
    _bytes [_which][type] += byte_size;
  }

  void dump_stats(int ro_all, int rw_all, int md_all, int mc_all);
};

void DumpAllocClosure::dump_stats(int ro_all, int rw_all, int md_all, int mc_all) {
  rw_all += (md_all + mc_all); // md and mc are all mapped Read/Write
  int other_bytes = md_all + mc_all;

  // Calculate size of data that was not allocated by Metaspace::allocate()
  MetaspaceSharedStats *stats = MetaspaceShared::stats();

  // symbols
  _counts[RO][SymbolHashentryType] = stats->symbol.hashentry_count;
  _bytes [RO][SymbolHashentryType] = stats->symbol.hashentry_bytes;
  _bytes [RO][TypeArrayU4Type]    -= stats->symbol.hashentry_bytes;

  _counts[RO][SymbolBucketType] = stats->symbol.bucket_count;
  _bytes [RO][SymbolBucketType] = stats->symbol.bucket_bytes;
  _bytes [RO][TypeArrayU4Type] -= stats->symbol.bucket_bytes;

  // strings
  _counts[RO][StringHashentryType] = stats->string.hashentry_count;
  _bytes [RO][StringHashentryType] = stats->string.hashentry_bytes;
  _bytes [RO][TypeArrayU4Type]    -= stats->string.hashentry_bytes;

  _counts[RO][StringBucketType] = stats->string.bucket_count;
  _bytes [RO][StringBucketType] = stats->string.bucket_bytes;
  _bytes [RO][TypeArrayU4Type] -= stats->string.bucket_bytes;

  // TODO: count things like dictionary, vtable, etc
  _bytes[RW][OtherType] =  other_bytes;

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

  ResourceMark rm;
  LogMessage(cds) msg;
  stringStream info_stream;

  info_stream.print_cr("Detailed metadata info (rw includes md and mc):");
  info_stream.print_cr("%s", hdr);
  info_stream.print_cr("%s", sep);
  for (int type = 0; type < int(_number_of_types); type ++) {
    const char *name = type_name((Type)type);
    int ro_count = _counts[RO][type];
    int ro_bytes = _bytes [RO][type];
    int rw_count = _counts[RW][type];
    int rw_bytes = _bytes [RW][type];
    int count = ro_count + rw_count;
    int bytes = ro_bytes + rw_bytes;

    double ro_perc = 100.0 * double(ro_bytes) / double(ro_all);
    double rw_perc = 100.0 * double(rw_bytes) / double(rw_all);
    double perc    = 100.0 * double(bytes)    / double(ro_all + rw_all);

    info_stream.print_cr(fmt_stats, name,
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

  double all_ro_perc = 100.0 * double(all_ro_bytes) / double(ro_all);
  double all_rw_perc = 100.0 * double(all_rw_bytes) / double(rw_all);
  double all_perc    = 100.0 * double(all_bytes)    / double(ro_all + rw_all);

  info_stream.print_cr("%s", sep);
  info_stream.print_cr(fmt_stats, "Total",
                       all_ro_count, all_ro_bytes, all_ro_perc,
                       all_rw_count, all_rw_bytes, all_rw_perc,
                       all_count, all_bytes, all_perc);

  assert(all_ro_bytes == ro_all, "everything should have been counted");
  assert(all_rw_bytes == rw_all, "everything should have been counted");

  msg.info("%s", info_stream.as_string());
#undef fmt_stats
}

// Populate the shared space.

class VM_PopulateDumpSharedSpace: public VM_Operation {
private:
  ClassLoaderData* _loader_data;
  GrowableArray<Klass*> *_class_promote_order;
  VirtualSpace _md_vs;
  VirtualSpace _mc_vs;
  VirtualSpace _od_vs;
  GrowableArray<MemRegion> *_string_regions;

public:
  VM_PopulateDumpSharedSpace(ClassLoaderData* loader_data,
                             GrowableArray<Klass*> *class_promote_order) :
    _loader_data(loader_data) {
    _class_promote_order = class_promote_order;
  }

  VMOp_Type type() const { return VMOp_PopulateDumpSharedSpace; }
  void doit();   // outline because gdb sucks

private:
  void handle_misc_data_space_failure(bool success) {
    if (!success) {
      report_out_of_shared_space(SharedMiscData);
    }
  }
}; // class VM_PopulateDumpSharedSpace

void VM_PopulateDumpSharedSpace::doit() {
  Thread* THREAD = VMThread::vm_thread();
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

  // Remove all references outside the metadata
  tty->print("Removing unshareable information ... ");
  remove_unshareable_in_classes();
  tty->print_cr("done. ");

  // Set up the misc data, misc code and optional data segments.
  _md_vs = *MetaspaceShared::misc_data_region()->virtual_space();
  _mc_vs = *MetaspaceShared::misc_code_region()->virtual_space();
  _od_vs = *MetaspaceShared::optional_data_region()->virtual_space();
  char* md_low = _md_vs.low();
  char* md_top = MetaspaceShared::misc_data_region()->alloc_top();
  char* md_end = _md_vs.high();
  char* mc_low = _mc_vs.low();
  char* mc_top = MetaspaceShared::misc_code_region()->alloc_top();
  char* mc_end = _mc_vs.high();
  char* od_low = _od_vs.low();
  char* od_top = MetaspaceShared::optional_data_region()->alloc_top();
  char* od_end = _od_vs.high();

  char* vtbl_list = md_top;
  md_top = (char*)MetaspaceShared::allocate_cpp_vtable_clones((intptr_t*)md_top, (intptr_t*)md_end);

  // We don't use MC section anymore. We will remove it in a future RFE. For now, put one
  // byte inside so the region writing/mapping code works.
  mc_top ++;

  // Reorder the system dictionary.  (Moving the symbols affects
  // how the hash table indices are calculated.)
  // Not doing this either.

  SystemDictionary::reorder_dictionary();
  NOT_PRODUCT(SystemDictionary::verify();)
  SystemDictionary::copy_buckets(&md_top, md_end);

  SystemDictionary::copy_table(&md_top, md_end);

  // Write the other data to the output array.
  // SymbolTable, StringTable and extra information for system dictionary
  NOT_PRODUCT(SymbolTable::verify());
  NOT_PRODUCT(StringTable::verify());
  size_t ss_bytes = 0;
  char* ss_low;
  // The string space has maximum two regions. See FileMapInfo::write_string_regions() for details.
  _string_regions = new GrowableArray<MemRegion>(2);

  WriteClosure wc(md_top, md_end);
  MetaspaceShared::serialize(&wc, _string_regions, &ss_bytes);
  md_top = wc.get_top();
  ss_low = _string_regions->is_empty() ? NULL : (char*)_string_regions->first().start();

  // Print shared spaces all the time
  Metaspace* ro_space = _loader_data->ro_metaspace();
  Metaspace* rw_space = _loader_data->rw_metaspace();

  // Allocated size of each space (may not be all occupied)
  const size_t ro_alloced = ro_space->capacity_bytes_slow(Metaspace::NonClassType);
  const size_t rw_alloced = rw_space->capacity_bytes_slow(Metaspace::NonClassType);
  const size_t md_alloced = md_end-md_low;
  const size_t mc_alloced = mc_end-mc_low;
  const size_t od_alloced = od_end-od_low;
  const size_t total_alloced = ro_alloced + rw_alloced + md_alloced + mc_alloced
                             + ss_bytes + od_alloced;

  // Occupied size of each space.
  const size_t ro_bytes = ro_space->used_bytes_slow(Metaspace::NonClassType);
  const size_t rw_bytes = rw_space->used_bytes_slow(Metaspace::NonClassType);
  const size_t md_bytes = size_t(md_top - md_low);
  const size_t mc_bytes = size_t(mc_top - mc_low);
  const size_t od_bytes = size_t(od_top - od_low);

  // Percent of total size
  const size_t total_bytes = ro_bytes + rw_bytes + md_bytes + mc_bytes + ss_bytes + od_bytes;
  const double ro_t_perc = ro_bytes / double(total_bytes) * 100.0;
  const double rw_t_perc = rw_bytes / double(total_bytes) * 100.0;
  const double md_t_perc = md_bytes / double(total_bytes) * 100.0;
  const double mc_t_perc = mc_bytes / double(total_bytes) * 100.0;
  const double ss_t_perc = ss_bytes / double(total_bytes) * 100.0;
  const double od_t_perc = od_bytes / double(total_bytes) * 100.0;

  // Percent of fullness of each space
  const double ro_u_perc = ro_bytes / double(ro_alloced) * 100.0;
  const double rw_u_perc = rw_bytes / double(rw_alloced) * 100.0;
  const double md_u_perc = md_bytes / double(md_alloced) * 100.0;
  const double mc_u_perc = mc_bytes / double(mc_alloced) * 100.0;
  const double od_u_perc = od_bytes / double(od_alloced) * 100.0;
  const double total_u_perc = total_bytes / double(total_alloced) * 100.0;

#define fmt_space "%s space: " SIZE_FORMAT_W(9) " [ %4.1f%% of total] out of " SIZE_FORMAT_W(9) " bytes [%5.1f%% used] at " INTPTR_FORMAT
  tty->print_cr(fmt_space, "ro", ro_bytes, ro_t_perc, ro_alloced, ro_u_perc, p2i(ro_space->bottom()));
  tty->print_cr(fmt_space, "rw", rw_bytes, rw_t_perc, rw_alloced, rw_u_perc, p2i(rw_space->bottom()));
  tty->print_cr(fmt_space, "md", md_bytes, md_t_perc, md_alloced, md_u_perc, p2i(md_low));
  tty->print_cr(fmt_space, "mc", mc_bytes, mc_t_perc, mc_alloced, mc_u_perc, p2i(mc_low));
  tty->print_cr(fmt_space, "st", ss_bytes, ss_t_perc, ss_bytes,   100.0,     p2i(ss_low));
  tty->print_cr(fmt_space, "od", od_bytes, od_t_perc, od_alloced, od_u_perc, p2i(od_low));
  tty->print_cr("total   : " SIZE_FORMAT_W(9) " [100.0%% of total] out of " SIZE_FORMAT_W(9) " bytes [%5.1f%% used]",
                 total_bytes, total_alloced, total_u_perc);

  // During patching, some virtual methods may be called, so at this point
  // the vtables must contain valid methods (as filled in by CppVtableCloner::allocate).
  MetaspaceShared::patch_cpp_vtable_pointers();

  // The vtable clones contain addresses of the current process.
  // We don't want to write these addresses into the archive.
  MetaspaceShared::zero_cpp_vtable_clones_for_writing();

  // Create and write the archive file that maps the shared spaces.

  FileMapInfo* mapinfo = new FileMapInfo();
  mapinfo->populate_header(MetaspaceShared::max_alignment());
  mapinfo->set_misc_data_patching_start(vtbl_list);
  mapinfo->set_cds_i2i_entry_code_buffers(MetaspaceShared::cds_i2i_entry_code_buffers());
  mapinfo->set_cds_i2i_entry_code_buffers_size(MetaspaceShared::cds_i2i_entry_code_buffers_size());

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
    mapinfo->write_space(MetaspaceShared::ro, _loader_data->ro_metaspace(), true);
    mapinfo->write_space(MetaspaceShared::rw, _loader_data->rw_metaspace(), false);
    mapinfo->write_region(MetaspaceShared::md, _md_vs.low(),
                          pointer_delta(md_top, _md_vs.low(), sizeof(char)),
                          SharedMiscDataSize,
                          false, true);
    mapinfo->write_region(MetaspaceShared::mc, _mc_vs.low(),
                          pointer_delta(mc_top, _mc_vs.low(), sizeof(char)),
                          SharedMiscCodeSize,
                          true, true);
    mapinfo->write_string_regions(_string_regions);
    mapinfo->write_region(MetaspaceShared::od, _od_vs.low(),
                          pointer_delta(od_top, _od_vs.low(), sizeof(char)),
                          pointer_delta(od_end, _od_vs.low(), sizeof(char)),
                          true, false);
  }

  mapinfo->close();

  // Restore the vtable in case we invoke any virtual methods.
  MetaspaceShared::clone_cpp_vtables((intptr_t*)vtbl_list);

  if (log_is_enabled(Info, cds)) {
    DumpAllocClosure dac;
    dac.iterate_metaspace(_loader_data->ro_metaspace(), DumpAllocClosure::RO);
    dac.iterate_metaspace(_loader_data->rw_metaspace(), DumpAllocClosure::RW);

    dac.dump_stats(int(ro_bytes), int(rw_bytes), int(md_bytes), int(mc_bytes));
  }
#undef fmt_space
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

void MetaspaceShared::check_shared_class_loader_type(Klass* k) {
  if (k->is_instance_klass()) {
    InstanceKlass* ik = InstanceKlass::cast(k);
    u2 loader_type = ik->loader_type();
    ResourceMark rm;
    guarantee(loader_type != 0,
              "Class loader type is not set for this class %s", ik->name()->as_C_string());
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

  // Copy the verification constraints from C_HEAP-alloced GrowableArrays to RO-alloced
  // Arrays
  SystemDictionaryShared::finalize_verification_constraints();
}

void MetaspaceShared::prepare_for_dumping() {
  Arguments::check_unsupported_dumping_properties();
  ClassLoader::initialize_shared_path();
  FileMapInfo::allocate_classpath_entry_table();
}

// Preload classes from a list, populate the shared spaces and dump to a
// file.
void MetaspaceShared::preload_and_dump(TRAPS) {
  { TraceTime timer("Dump Shared Spaces", TRACETIME_LOG(Info, startuptime));
    ResourceMark rm;
    char class_list_path_str[JVM_MAXPATHLEN];

    tty->print_cr("Allocated shared space: " SIZE_FORMAT " bytes at " PTR_FORMAT,
                  MetaspaceShared::shared_rs()->size(),
                  p2i(MetaspaceShared::shared_rs()->base()));

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

    int class_count = 0;
    GrowableArray<Klass*>* class_promote_order = new GrowableArray<Klass*>();

    // sun.io.Converters
    static const char obj_array_sig[] = "[[Ljava/lang/Object;";
    SymbolTable::new_permanent_symbol(obj_array_sig, THREAD);

    // java.util.HashMap
    static const char map_entry_array_sig[] = "[Ljava/util/Map$Entry;";
    SymbolTable::new_permanent_symbol(map_entry_array_sig, THREAD);

    // Need to allocate the op here:
    // op.misc_data_space_alloc() will be called during preload_and_dump().
    ClassLoaderData* loader_data = ClassLoaderData::the_null_class_loader_data();
    VM_PopulateDumpSharedSpace op(loader_data, class_promote_order);

    tty->print_cr("Loading classes to share ...");
    _has_error_classes = false;
    class_count += preload_and_dump(class_list_path, class_promote_order,
                                    THREAD);
    if (ExtraSharedClassListFile) {
      class_count += preload_and_dump(ExtraSharedClassListFile, class_promote_order,
                                      THREAD);
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

    VMThread::execute(&op);
  }

  if (PrintSystemDictionaryAtExit) {
    SystemDictionary::print();
  }

  // Since various initialization steps have been undone by this process,
  // it is not reasonable to continue running a java process.
  exit(0);
}


int MetaspaceShared::preload_and_dump(const char* class_list_path,
                                      GrowableArray<Klass*>* class_promote_order,
                                      TRAPS) {
  ClassListParser parser(class_list_path);
  int class_count = 0;

    while (parser.parse_one_line()) {
      Klass* klass = ClassLoaderExt::load_one_class(&parser, THREAD);

      CLEAR_PENDING_EXCEPTION;
      if (klass != NULL) {
        if (log_is_enabled(Trace, cds)) {
          ResourceMark rm;
          log_trace(cds)("Shared spaces preloaded: %s", klass->external_name());
        }

        InstanceKlass* ik = InstanceKlass::cast(klass);

        // Should be class load order as per -Xlog:class+preorder
        class_promote_order->append(ik);

        // Link the class to cause the bytecodes to be rewritten and the
        // cpcache to be created. The linking is done as soon as classes
        // are loaded in order that the related data structures (klass and
        // cpCache) are located together.
        try_link_class(ik, THREAD);
        guarantee(!HAS_PENDING_EXCEPTION, "exception in link_class");

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
    if (!(ik->is_shared_boot_class())) {
      // The verification decision is based on BytecodeVerificationRemote
      // for non-system classes. Since we are using the NULL classloader
      // to load non-system classes during dumping, we need to temporarily
      // change BytecodeVerificationLocal to be the same as
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

// Return true if given address is in the mapped shared space.
bool MetaspaceShared::is_in_shared_space(const void* p) {
  return UseSharedSpaces && FileMapInfo::current_info()->is_in_shared_space(p);
}

// Return true if given address is in the misc data region
bool MetaspaceShared::is_in_shared_region(const void* p, int idx) {
  return UseSharedSpaces && FileMapInfo::current_info()->is_in_shared_region(p, idx);
}

bool MetaspaceShared::is_string_region(int idx) {
  return (idx >= MetaspaceShared::first_string &&
          idx < MetaspaceShared::first_string + MetaspaceShared::max_strings);
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

  char* _ro_base = NULL;
  char* _rw_base = NULL;
  char* _md_base = NULL;
  char* _mc_base = NULL;
  char* _od_base = NULL;

  // Map each shared region
  if ((_ro_base = mapinfo->map_region(ro)) != NULL &&
      mapinfo->verify_region_checksum(ro) &&
      (_rw_base = mapinfo->map_region(rw)) != NULL &&
      mapinfo->verify_region_checksum(rw) &&
      (_md_base = mapinfo->map_region(md)) != NULL &&
      mapinfo->verify_region_checksum(md) &&
      (_mc_base = mapinfo->map_region(mc)) != NULL &&
      mapinfo->verify_region_checksum(mc) &&
      (_od_base = mapinfo->map_region(od)) != NULL &&
      mapinfo->verify_region_checksum(od) &&
      (image_alignment == (size_t)max_alignment()) &&
      mapinfo->validate_classpath_entry_table()) {
    // Success (no need to do anything)
    return true;
  } else {
    // If there was a failure in mapping any of the spaces, unmap the ones
    // that succeeded
    if (_ro_base != NULL) mapinfo->unmap_region(ro);
    if (_rw_base != NULL) mapinfo->unmap_region(rw);
    if (_md_base != NULL) mapinfo->unmap_region(md);
    if (_mc_base != NULL) mapinfo->unmap_region(mc);
    if (_od_base != NULL) mapinfo->unmap_region(od);
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
  char* buffer = mapinfo->misc_data_patching_start();

  buffer = (char*)clone_cpp_vtables((intptr_t*)buffer);

  int sharedDictionaryLen = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  int number_of_entries = *(intptr_t*)buffer;
  buffer += sizeof(intptr_t);
  SystemDictionary::set_shared_dictionary((HashtableBucket<mtClass>*)buffer,
                                          sharedDictionaryLen,
                                          number_of_entries);
  buffer += sharedDictionaryLen;

  // The following data in the shared misc data region are the linked
  // list elements (HashtableEntry objects) for the shared dictionary
  // table.

  int len = *(intptr_t*)buffer;     // skip over shared dictionary entries
  buffer += sizeof(intptr_t);
  buffer += len;

  // Verify various attributes of the archive, plus initialize the
  // shared string/symbol tables
  intptr_t* array = (intptr_t*)buffer;
  ReadClosure rc(&array);
  serialize(&rc, NULL, NULL);

  // Initialize the run-time symbol table.
  SymbolTable::create_table();

  // Close the mapinfo file
  mapinfo->close();

  if (PrintSharedArchiveAndExit) {
    if (PrintSharedDictionary) {
      tty->print_cr("\nShared classes:\n");
      SystemDictionary::print_shared(false);
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

void MetaspaceShared::fixup_shared_string_regions() {
  FileMapInfo *mapinfo = FileMapInfo::current_info();
  mapinfo->fixup_string_regions();
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

int MetaspaceShared::count_class(const char* classlist_file) {
  if (classlist_file == NULL) {
    return 0;
  }
  char class_name[256];
  int class_count = 0;
  FILE* file = fopen(classlist_file, "r");
  if (file != NULL) {
    while ((fgets(class_name, sizeof class_name, file)) != NULL) {
      if (*class_name == '#') { // comment
        continue;
      }
      class_count++;
    }
    fclose(file);
  } else {
    char errmsg[JVM_MAXPATHLEN];
    os::lasterror(errmsg, JVM_MAXPATHLEN);
    tty->print_cr("Loading classlist failed: %s", errmsg);
    exit(1);
  }

  return class_count;
}

// the sizes are good for typical large applications that have a lot of shared
// classes
void MetaspaceShared::estimate_regions_size() {
  int class_count = count_class(SharedClassListFile);
  class_count += count_class(ExtraSharedClassListFile);

  if (class_count > LargeThresholdClassCount) {
    if (class_count < HugeThresholdClassCount) {
      SET_ESTIMATED_SIZE(Large, ReadOnly);
      SET_ESTIMATED_SIZE(Large, ReadWrite);
      SET_ESTIMATED_SIZE(Large, MiscData);
      SET_ESTIMATED_SIZE(Large, MiscCode);
    } else {
      SET_ESTIMATED_SIZE(Huge,  ReadOnly);
      SET_ESTIMATED_SIZE(Huge,  ReadWrite);
      SET_ESTIMATED_SIZE(Huge,  MiscData);
      SET_ESTIMATED_SIZE(Huge,  MiscCode);
    }
  }
}
