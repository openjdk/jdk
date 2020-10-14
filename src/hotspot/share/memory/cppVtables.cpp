/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/log.hpp"
#include "memory/archiveUtils.hpp"
#include "memory/cppVtables.hpp"
#include "memory/metaspaceShared.hpp"
#include "oops/instanceClassLoaderKlass.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/methodData.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/typeArrayKlass.hpp"
#include "runtime/arguments.hpp"
#include "utilities/globalDefinitions.hpp"

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

static inline intptr_t* vtable_of(Metadata* m) {
  return *((intptr_t**)m);
}

static inline DumpRegion* mc_region() {
  return MetaspaceShared::misc_code_dump_space();
}

template <class T> class CppVtableCloner : public T {
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

  static bool is_valid_shared_object(const T* obj) {
    intptr_t* vptr = *(intptr_t**)obj;
    return vptr == _info->cloned_vtable();
  }

  static void init_orig_cpp_vtptr(int kind);
};

template <class T> CppVtableInfo* CppVtableCloner<T>::_info = NULL;

template <class T>
intptr_t* CppVtableCloner<T>::allocate(const char* name) {
  assert(is_aligned(mc_region()->top(), sizeof(intptr_t)), "bad alignment");
  int n = get_vtable_length(name);
  _info = (CppVtableInfo*)mc_region()->allocate(CppVtableInfo::byte_size(n));
  _info->set_vtable_size(n);

  intptr_t* p = clone_vtable(name, _info);
  assert((char*)p == mc_region()->top(), "must be");

  return _info->cloned_vtable();
}

template <class T>
intptr_t* CppVtableCloner<T>::clone_vtable(const char* name, CppVtableInfo* info) {
  if (!DumpSharedSpaces) {
    assert(_info == 0, "_info is initialized only at dump time");
    _info = info; // Remember it -- it will be used by MetaspaceShared::is_valid_shared_method()
  }
  T tmp; // Allocate temporary dummy metadata object to get to the original vtable.
  int n = info->vtable_size();
  intptr_t* srcvtable = vtable_of(&tmp);
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

  intptr_t* avtable = vtable_of(&a);
  intptr_t* bvtable = vtable_of(&b);

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
  _cloned_cpp_vtptrs[c##_Kind] = CppVtableCloner<c>::allocate(#c); \
  ArchivePtrMarker::mark_pointer(&_cloned_cpp_vtptrs[c##_Kind]);

#define CLONE_CPP_VTABLE(c) \
  p = CppVtableCloner<c>::clone_vtable(#c, (CppVtableInfo*)p);

#define ZERO_CPP_VTABLE(c) \
 CppVtableCloner<c>::zero_vtable_clone();

#define INIT_ORIG_CPP_VTPTRS(c) \
  CppVtableCloner<c>::init_orig_cpp_vtptr(c##_Kind);

#define DECLARE_CLONED_VTABLE_KIND(c) c ## _Kind,

enum ClonedVtableKind {
  // E.g., ConstantPool_Kind == 0, InstanceKlass_Kind == 1, etc.
  CPP_VTABLE_PATCH_TYPES_DO(DECLARE_CLONED_VTABLE_KIND)
  _num_cloned_vtable_kinds
};

// This is a map of all the original vtptrs. E.g., for
//     ConstantPool *cp = new (...) ConstantPool(...) ; // a dynamically allocated constant pool
// the following holds true:
//     _orig_cpp_vtptrs[ConstantPool_Kind] ==  ((intptr_t**)cp)[0]
static intptr_t* _orig_cpp_vtptrs[_num_cloned_vtable_kinds];
static bool _orig_cpp_vtptrs_inited = false;

template <class T>
void CppVtableCloner<T>::init_orig_cpp_vtptr(int kind) {
  assert(kind < _num_cloned_vtable_kinds, "sanity");
  T tmp; // Allocate temporary dummy metadata object to get to the original vtable.
  intptr_t* srcvtable = vtable_of(&tmp);
  _orig_cpp_vtptrs[kind] = srcvtable;
}

// This is the index of all the cloned vtables. E.g., for
//     ConstantPool* cp = ....; // an archived constant pool
//     InstanceKlass* ik = ....;// an archived class
// the following holds true:
//     _cloned_cpp_vtptrs[ConstantPool_Kind]  == ((intptr_t**)cp)[0]
//     _cloned_cpp_vtptrs[InstanceKlass_Kind] == ((intptr_t**)ik)[0]
static intptr_t** _cloned_cpp_vtptrs = NULL;

void CppVtables::allocate_cloned_cpp_vtptrs() {
  assert(DumpSharedSpaces, "must");
  size_t vtptrs_bytes = _num_cloned_vtable_kinds * sizeof(intptr_t*);
  _cloned_cpp_vtptrs = (intptr_t**)mc_region()->allocate(vtptrs_bytes);
}

void CppVtables::serialize_cloned_cpp_vtptrs(SerializeClosure* soc) {
  soc->do_ptr((void**)&_cloned_cpp_vtptrs);
}

intptr_t* CppVtables::get_archived_cpp_vtable(MetaspaceObj::Type msotype, address obj) {
  if (!_orig_cpp_vtptrs_inited) {
    CPP_VTABLE_PATCH_TYPES_DO(INIT_ORIG_CPP_VTPTRS);
    _orig_cpp_vtptrs_inited = true;
  }

  Arguments::assert_is_dumping_archive();
  int kind = -1;
  switch (msotype) {
  case MetaspaceObj::SymbolType:
  case MetaspaceObj::TypeArrayU1Type:
  case MetaspaceObj::TypeArrayU2Type:
  case MetaspaceObj::TypeArrayU4Type:
  case MetaspaceObj::TypeArrayU8Type:
  case MetaspaceObj::TypeArrayOtherType:
  case MetaspaceObj::ConstMethodType:
  case MetaspaceObj::ConstantPoolCacheType:
  case MetaspaceObj::AnnotationsType:
  case MetaspaceObj::MethodCountersType:
  case MetaspaceObj::RecordComponentType:
    // These have no vtables.
    break;
  case MetaspaceObj::MethodDataType:
    // We don't archive MethodData <-- should have been removed in removed_unsharable_info
    ShouldNotReachHere();
    break;
  default:
    for (kind = 0; kind < _num_cloned_vtable_kinds; kind ++) {
      if (vtable_of((Metadata*)obj) == _orig_cpp_vtptrs[kind]) {
        break;
      }
    }
    if (kind >= _num_cloned_vtable_kinds) {
      fatal("Cannot find C++ vtable for " INTPTR_FORMAT " -- you probably added"
            " a new subtype of Klass or MetaData without updating CPP_VTABLE_PATCH_TYPES_DO",
            p2i(obj));
    }
  }

  if (kind >= 0) {
    assert(kind < _num_cloned_vtable_kinds, "must be");
    return _cloned_cpp_vtptrs[kind];
  } else {
    return NULL;
  }
}

// This can be called at both dump time and run time:
// - clone the contents of the c++ vtables into the space
//   allocated by allocate_cpp_vtable_clones()
void CppVtables::clone_cpp_vtables(intptr_t* p) {
  assert(DumpSharedSpaces || UseSharedSpaces, "sanity");
  CPP_VTABLE_PATCH_TYPES_DO(CLONE_CPP_VTABLE);
}

void CppVtables::zero_cpp_vtable_clones_for_writing() {
  assert(DumpSharedSpaces, "dump-time only");
  CPP_VTABLE_PATCH_TYPES_DO(ZERO_CPP_VTABLE);
}

// Allocate and initialize the C++ vtables, starting from top, but do not go past end.
char* CppVtables::allocate_cpp_vtable_clones() {
  char* cloned_vtables = mc_region()->top(); // This is the beginning of all the cloned vtables

  assert(DumpSharedSpaces, "dump-time only");
  // Layout (each slot is a intptr_t):
  //   [number of slots in the first vtable = n1]
  //   [ <n1> slots for the first vtable]
  //   [number of slots in the first second = n2]
  //   [ <n2> slots for the second vtable]
  //   ...
  // The order of the vtables is the same as the CPP_VTAB_PATCH_TYPES_DO macro.
  CPP_VTABLE_PATCH_TYPES_DO(ALLOC_CPP_VTABLE_CLONE);

  return cloned_vtables;
}

bool CppVtables::is_valid_shared_method(const Method* m) {
  assert(MetaspaceShared::is_in_shared_metaspace(m), "must be");
  return CppVtableCloner<Method>::is_valid_shared_object(m);
}
