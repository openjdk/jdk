/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_ARCHIVEUTILS_HPP
#define SHARE_CDS_ARCHIVEUTILS_HPP

#include "cds/cds_globals.hpp"
#include "cds/serializeClosure.hpp"
#include "logging/log.hpp"
#include "memory/metaspace.hpp"
#include "memory/virtualspace.hpp"
#include "runtime/nonJavaThread.hpp"
#include "runtime/semaphore.hpp"
#include "utilities/bitMap.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/macros.hpp"

class BootstrapInfo;
class ReservedSpace;
class VirtualSpace;

template<class E> class Array;
template<class E> class GrowableArray;

// ArchivePtrMarker is used to mark the location of pointers embedded in a CDS archive. E.g., when an
// InstanceKlass k is dumped, we mark the location of the k->_name pointer by effectively calling
// mark_pointer(/*ptr_loc=*/&k->_name). It's required that (_prt_base <= ptr_loc < _ptr_end). _ptr_base is
// fixed, but _ptr_end can be expanded as more objects are dumped.
class ArchivePtrMarker : AllStatic {
  static CHeapBitMap*  _ptrmap;
  static CHeapBitMap*  _rw_ptrmap;
  static CHeapBitMap*  _ro_ptrmap;
  static VirtualSpace* _vs;

  // Once _ptrmap is compacted, we don't allow bit marking anymore. This is to
  // avoid unintentional copy operations after the bitmap has been finalized and written.
  static bool         _compacted;

  static address* ptr_base() { return (address*)_vs->low();  } // committed lower bound (inclusive)
  static address* ptr_end()  { return (address*)_vs->high(); } // committed upper bound (exclusive)

public:
  static void initialize(CHeapBitMap* ptrmap, VirtualSpace* vs);
  static void initialize_rw_ro_maps(CHeapBitMap* rw_ptrmap, CHeapBitMap* ro_ptrmap);
  static void mark_pointer(address* ptr_loc);
  static void clear_pointer(address* ptr_loc);
  static void compact(address relocatable_base, address relocatable_end);
  static void compact(size_t max_non_null_offset);

  template <typename T>
  static void mark_pointer(T* ptr_loc) {
    mark_pointer((address*)ptr_loc);
  }

  template <typename T>
  static void set_and_mark_pointer(T* ptr_loc, T ptr_value) {
    *ptr_loc = ptr_value;
    mark_pointer(ptr_loc);
  }

  static CHeapBitMap* ptrmap() {
    return _ptrmap;
  }

  static CHeapBitMap* rw_ptrmap() {
    return _rw_ptrmap;
  }

  static CHeapBitMap* ro_ptrmap() {
    return _ro_ptrmap;
  }

  static void reset_map_and_vs() {
    _ptrmap = nullptr;
    _rw_ptrmap = nullptr;
    _ro_ptrmap = nullptr;
    _vs = nullptr;
  }
};

// SharedDataRelocator is used to shift pointers in the CDS archive.
//
// The CDS archive is basically a contiguous block of memory (divided into several regions)
// that contains multiple objects. The objects may contain direct pointers that point to other objects
// within the archive (e.g., InstanceKlass::_name points to a Symbol in the archive). During dumping, we
// built a bitmap that marks the locations of all these pointers (using ArchivePtrMarker, see comments above).
//
// The contents of the archive assumes that it's mapped at the default SharedBaseAddress (e.g. 0x800000000).
// If the archive ends up being mapped at a different address (e.g. 0x810000000), SharedDataRelocator
// is used to shift each marked pointer by a delta (0x10000000 in this example), so that it points to
// the actually mapped location of the target object.
class SharedDataRelocator: public BitMapClosure {
  // for all (address** p), where (is_marked(p) && _patch_base <= p && p < _patch_end) { *p += delta; }

  // Patch all pointers within this region that are marked.
  address* _patch_base;
  address* _patch_end;

  // Before patching, all pointers must point to this region.
  address _valid_old_base;
  address _valid_old_end;

  // After patching, all pointers must point to this region.
  address _valid_new_base;
  address _valid_new_end;

  // How much to relocate for each pointer.
  intx _delta;

 public:
  SharedDataRelocator(address* patch_base, address* patch_end,
                      address valid_old_base, address valid_old_end,
                      address valid_new_base, address valid_new_end, intx delta) :
    _patch_base(patch_base), _patch_end(patch_end),
    _valid_old_base(valid_old_base), _valid_old_end(valid_old_end),
    _valid_new_base(valid_new_base), _valid_new_end(valid_new_end),
    _delta(delta) {
    log_debug(aot, reloc)("SharedDataRelocator::_patch_base     = " PTR_FORMAT, p2i(_patch_base));
    log_debug(aot, reloc)("SharedDataRelocator::_patch_end      = " PTR_FORMAT, p2i(_patch_end));
    log_debug(aot, reloc)("SharedDataRelocator::_valid_old_base = " PTR_FORMAT, p2i(_valid_old_base));
    log_debug(aot, reloc)("SharedDataRelocator::_valid_old_end  = " PTR_FORMAT, p2i(_valid_old_end));
    log_debug(aot, reloc)("SharedDataRelocator::_valid_new_base = " PTR_FORMAT, p2i(_valid_new_base));
    log_debug(aot, reloc)("SharedDataRelocator::_valid_new_end  = " PTR_FORMAT, p2i(_valid_new_end));
  }

  bool do_bit(size_t offset);
};

class DumpRegion {
private:
  const char* _name;
  char* _base;
  char* _top;
  char* _end;
  bool _is_packed;
  ReservedSpace* _rs;
  VirtualSpace* _vs;

  void commit_to(char* newtop);

public:
  DumpRegion(const char* name)
    : _name(name), _base(nullptr), _top(nullptr), _end(nullptr),
      _is_packed(false),
      _rs(nullptr), _vs(nullptr) {}

  char* expand_top_to(char* newtop);
  char* allocate(size_t num_bytes, size_t alignment = 0);

  void append_intptr_t(intptr_t n, bool need_to_mark = false) NOT_CDS_RETURN;

  char* base()      const { return _base;        }
  char* top()       const { return _top;         }
  char* end()       const { return _end;         }
  size_t reserved() const { return _end - _base; }
  size_t used()     const { return _top - _base; }
  bool is_packed()  const { return _is_packed;   }
  bool is_allocatable() const {
    return !is_packed() && _base != nullptr;
  }
  bool is_empty()   const { return _base == _top; }

  void print(size_t total_bytes) const;
  void print_out_of_space_msg(const char* failing_region, size_t needed_bytes);

  void init(ReservedSpace* rs, VirtualSpace* vs);

  void pack(DumpRegion* next = nullptr);

  bool contains(char* p) {
    return base() <= p && p < top();
  }
};

// Closure for serializing initialization data out to a data area to be
// written to the shared file.

class WriteClosure : public SerializeClosure {
private:
  DumpRegion* _dump_region;

public:
  WriteClosure(DumpRegion* r) {
    _dump_region = r;
  }

  void do_ptr(void** p);

  void do_u4(u4* p) {
    _dump_region->append_intptr_t((intptr_t)(*p));
  }

  void do_int(int* p) {
    _dump_region->append_intptr_t((intptr_t)(*p));
  }

  void do_bool(bool *p) {
    _dump_region->append_intptr_t((intptr_t)(*p));
  }

  void do_tag(int tag) {
    _dump_region->append_intptr_t((intptr_t)tag);
  }

  char* region_top() {
    return _dump_region->top();
  }

  bool reading() const { return false; }
};

// Closure for serializing initialization data in from a data area
// (ptr_array) read from the shared file.

class ReadClosure : public SerializeClosure {
private:
  intptr_t** _ptr_array;
  address _base_address;
  inline intptr_t nextPtr() {
    return *(*_ptr_array)++;
  }

public:
  ReadClosure(intptr_t** ptr_array, address base_address) :
    _ptr_array(ptr_array), _base_address(base_address) {}

  void do_ptr(void** p);
  void do_u4(u4* p);
  void do_int(int* p);
  void do_bool(bool *p);
  void do_tag(int tag);
  bool reading() const { return true; }
  char* region_top() { return nullptr; }
};

class ArchiveUtils {
  template <typename T> static Array<T>* archive_non_ptr_array(GrowableArray<T>* tmp_array);
  template <typename T> static Array<T>* archive_ptr_array(GrowableArray<T>* tmp_array);

public:
  static void log_to_classlist(BootstrapInfo* bootstrap_specifier, TRAPS) NOT_CDS_RETURN;
  static bool has_aot_initialized_mirror(InstanceKlass* src_ik);

  template <typename T, ENABLE_IF(!std::is_pointer<T>::value)>
  static Array<T>* archive_array(GrowableArray<T>* tmp_array) {
    return archive_non_ptr_array(tmp_array);
  }

  template <typename T, ENABLE_IF(std::is_pointer<T>::value)>
  static Array<T>* archive_array(GrowableArray<T>* tmp_array) {
    return archive_ptr_array(tmp_array);
  }
};

class HeapRootSegments {
private:
  size_t _base_offset;
  size_t _count;
  int _roots_count;
  size_t _max_size_in_bytes;
  int _max_size_in_elems;

public:
  size_t base_offset() { return _base_offset; }
  size_t count() { return _count; }
  int roots_count() { return _roots_count; }
  size_t max_size_in_bytes() { return _max_size_in_bytes; }
  int max_size_in_elems() { return _max_size_in_elems; }

  size_t size_in_bytes(size_t seg_idx);
  int size_in_elems(size_t seg_idx);
  size_t segment_offset(size_t seg_idx);

  // Trivial copy assignments are allowed to copy the entire object representation.
  // We also inline this class into archive header. Therefore, it is important to make
  // sure any gaps in object representation are initialized to zeroes. This is why
  // constructors memset before doing field assignments.
  HeapRootSegments() {
    memset(this, 0, sizeof(*this));
  }
  HeapRootSegments(size_t base_offset, int roots_count, int max_size_in_bytes, int max_size_in_elems) {
    memset(this, 0, sizeof(*this));
    _base_offset = base_offset;
    _count = (roots_count + max_size_in_elems - 1) / max_size_in_elems;
    _roots_count = roots_count;
    _max_size_in_bytes = max_size_in_bytes;
    _max_size_in_elems = max_size_in_elems;
  }

  // This class is trivially copyable and assignable.
  HeapRootSegments(const HeapRootSegments&) = default;
  HeapRootSegments& operator=(const HeapRootSegments&) = default;
};

class ArchiveWorkers;

// A task to be worked on by worker threads
class ArchiveWorkerTask : public CHeapObj<mtInternal> {
  friend class ArchiveWorkers;
private:
  const char* _name;
  int _max_chunks;
  volatile int _chunk;

  void run();

  void configure_max_chunks(int max_chunks);

public:
  ArchiveWorkerTask(const char* name) :
      _name(name), _max_chunks(0), _chunk(0) {}
  const char* name() const { return _name; }
  virtual void work(int chunk, int max_chunks) = 0;
};

class ArchiveWorkerThread : public NamedThread {
  friend class ArchiveWorkers;
private:
  ArchiveWorkers* const _pool;

  void post_run() override;

public:
  ArchiveWorkerThread(ArchiveWorkers* pool);
  const char* type_name() const override { return "Archive Worker Thread"; }
  void run() override;
};

// Special archive workers. The goal for this implementation is to startup fast,
// distribute spiky workloads efficiently, and shutdown immediately after use.
// This makes the implementation quite different from the normal GC worker pool.
class ArchiveWorkers : public StackObj {
  friend class ArchiveWorkerThread;
private:
  // Target number of chunks per worker. This should be large enough to even
  // out work imbalance, and small enough to keep bookkeeping overheads low.
  static constexpr int CHUNKS_PER_WORKER = 4;
  static int max_workers();

  Semaphore _end_semaphore;

  int _num_workers;
  int _started_workers;
  int _finish_tokens;

  typedef enum { UNUSED, WORKING, SHUTDOWN } State;
  volatile State _state;

  ArchiveWorkerTask* _task;

  void run_as_worker();
  void start_worker_if_needed();

  void run_task_single(ArchiveWorkerTask* task);
  void run_task_multi(ArchiveWorkerTask* task);

  bool is_parallel();

public:
  ArchiveWorkers();
  ~ArchiveWorkers();
  void run_task(ArchiveWorkerTask* task);
};

#endif // SHARE_CDS_ARCHIVEUTILS_HPP
