/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotLogging.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/archiveHeapLoader.inline.hpp"
#include "cds/archiveUtils.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/classListParser.hpp"
#include "cds/classListWriter.hpp"
#include "cds/dynamicArchive.hpp"
#include "cds/filemap.hpp"
#include "cds/heapShared.hpp"
#include "cds/lambdaProxyClassDictionary.hpp"
#include "cds/metaspaceShared.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "interpreter/bootstrapInfo.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/resourceArea.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/klass.inline.hpp"
#include "runtime/arguments.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/spinYield.hpp"

CHeapBitMap* ArchivePtrMarker::_ptrmap = nullptr;
CHeapBitMap* ArchivePtrMarker::_rw_ptrmap = nullptr;
CHeapBitMap* ArchivePtrMarker::_ro_ptrmap = nullptr;
VirtualSpace* ArchivePtrMarker::_vs;

bool ArchivePtrMarker::_compacted;

void ArchivePtrMarker::initialize(CHeapBitMap* ptrmap, VirtualSpace* vs) {
  assert(_ptrmap == nullptr, "initialize only once");
  assert(_rw_ptrmap == nullptr, "initialize only once");
  assert(_ro_ptrmap == nullptr, "initialize only once");
  _vs = vs;
  _compacted = false;
  _ptrmap = ptrmap;

  // Use this as initial guesstimate. We should need less space in the
  // archive, but if we're wrong the bitmap will be expanded automatically.
  size_t estimated_archive_size = MetaspaceGC::capacity_until_GC();
  // But set it smaller in debug builds so we always test the expansion code.
  // (Default archive is about 12MB).
  DEBUG_ONLY(estimated_archive_size = 6 * M);

  // We need one bit per pointer in the archive.
  _ptrmap->initialize(estimated_archive_size / sizeof(intptr_t));
}

void ArchivePtrMarker::initialize_rw_ro_maps(CHeapBitMap* rw_ptrmap, CHeapBitMap* ro_ptrmap) {
  address* buff_bottom = (address*)ArchiveBuilder::current()->buffer_bottom();
  address* rw_bottom   = (address*)ArchiveBuilder::current()->rw_region()->base();
  address* ro_bottom   = (address*)ArchiveBuilder::current()->ro_region()->base();

  // The bit in _ptrmap that cover the very first word in the rw/ro regions.
  size_t rw_start = rw_bottom - buff_bottom;
  size_t ro_start = ro_bottom - buff_bottom;

  // The number of bits used by the rw/ro ptrmaps. We might have lots of zero
  // bits at the bottom and top of rw/ro ptrmaps, but these zeros will be
  // removed by FileMapInfo::write_bitmap_region().
  size_t rw_size = ArchiveBuilder::current()->rw_region()->used() / sizeof(address);
  size_t ro_size = ArchiveBuilder::current()->ro_region()->used() / sizeof(address);

  // The last (exclusive) bit in _ptrmap that covers the rw/ro regions.
  // Note: _ptrmap is dynamically expanded only when an actual pointer is written, so
  // it may not be as large as we want.
  size_t rw_end = MIN2<size_t>(rw_start + rw_size, _ptrmap->size());
  size_t ro_end = MIN2<size_t>(ro_start + ro_size, _ptrmap->size());

  rw_ptrmap->initialize(rw_size);
  ro_ptrmap->initialize(ro_size);

  for (size_t rw_bit = rw_start; rw_bit < rw_end; rw_bit++) {
    rw_ptrmap->at_put(rw_bit - rw_start, _ptrmap->at(rw_bit));
  }

  for(size_t ro_bit = ro_start; ro_bit < ro_end; ro_bit++) {
    ro_ptrmap->at_put(ro_bit - ro_start, _ptrmap->at(ro_bit));
  }

  _rw_ptrmap = rw_ptrmap;
  _ro_ptrmap = ro_ptrmap;
}

void ArchivePtrMarker::mark_pointer(address* ptr_loc) {
  assert(_ptrmap != nullptr, "not initialized");
  assert(!_compacted, "cannot mark anymore");

  if (ptr_base() <= ptr_loc && ptr_loc < ptr_end()) {
    address value = *ptr_loc;
    // We don't want any pointer that points to very bottom of the archive, otherwise when
    // MetaspaceShared::default_base_address()==0, we can't distinguish between a pointer
    // to nothing (null) vs a pointer to an objects that happens to be at the very bottom
    // of the archive.
    assert(value != (address)ptr_base(), "don't point to the bottom of the archive");

    if (value != nullptr) {
      assert(uintx(ptr_loc) % sizeof(intptr_t) == 0, "pointers must be stored in aligned addresses");
      size_t idx = ptr_loc - ptr_base();
      if (_ptrmap->size() <= idx) {
        _ptrmap->resize((idx + 1) * 2);
      }
      assert(idx < _ptrmap->size(), "must be");
      _ptrmap->set_bit(idx);
      //tty->print_cr("Marking pointer [" PTR_FORMAT "] -> " PTR_FORMAT " @ %5zu", p2i(ptr_loc), p2i(*ptr_loc), idx);
    }
  }
}

void ArchivePtrMarker::clear_pointer(address* ptr_loc) {
  assert(_ptrmap != nullptr, "not initialized");
  assert(!_compacted, "cannot clear anymore");

  assert(ptr_base() <= ptr_loc && ptr_loc < ptr_end(), "must be");
  assert(uintx(ptr_loc) % sizeof(intptr_t) == 0, "pointers must be stored in aligned addresses");
  size_t idx = ptr_loc - ptr_base();
  assert(idx < _ptrmap->size(), "cannot clear pointers that have not been marked");
  _ptrmap->clear_bit(idx);
  //tty->print_cr("Clearing pointer [" PTR_FORMAT "] -> " PTR_FORMAT " @ %5zu", p2i(ptr_loc), p2i(*ptr_loc), idx);
}

class ArchivePtrBitmapCleaner: public BitMapClosure {
  CHeapBitMap* _ptrmap;
  address* _ptr_base;
  address  _relocatable_base;
  address  _relocatable_end;
  size_t   _max_non_null_offset;

public:
  ArchivePtrBitmapCleaner(CHeapBitMap* ptrmap, address* ptr_base, address relocatable_base, address relocatable_end) :
    _ptrmap(ptrmap), _ptr_base(ptr_base),
    _relocatable_base(relocatable_base), _relocatable_end(relocatable_end), _max_non_null_offset(0) {}

  bool do_bit(size_t offset) {
    address* ptr_loc = _ptr_base + offset;
    address  ptr_value = *ptr_loc;
    if (ptr_value != nullptr) {
      assert(_relocatable_base <= ptr_value && ptr_value < _relocatable_end, "do not point to arbitrary locations!");
      if (_max_non_null_offset < offset) {
        _max_non_null_offset = offset;
      }
    } else {
      _ptrmap->clear_bit(offset);
      DEBUG_ONLY(log_trace(aot, reloc)("Clearing pointer [" PTR_FORMAT  "] -> null @ %9zu", p2i(ptr_loc), offset));
    }

    return true;
  }

  size_t max_non_null_offset() const { return _max_non_null_offset; }
};

void ArchivePtrMarker::compact(address relocatable_base, address relocatable_end) {
  assert(!_compacted, "cannot compact again");
  ArchivePtrBitmapCleaner cleaner(_ptrmap, ptr_base(), relocatable_base, relocatable_end);
  _ptrmap->iterate(&cleaner);
  compact(cleaner.max_non_null_offset());
}

void ArchivePtrMarker::compact(size_t max_non_null_offset) {
  assert(!_compacted, "cannot compact again");
  _ptrmap->resize(max_non_null_offset + 1);
  _compacted = true;
}

char* DumpRegion::expand_top_to(char* newtop) {
  assert(is_allocatable(), "must be initialized and not packed");
  assert(newtop >= _top, "must not grow backwards");
  if (newtop > _end) {
    ArchiveBuilder::current()->report_out_of_space(_name, newtop - _top);
    ShouldNotReachHere();
  }

  commit_to(newtop);
  _top = newtop;

  if (_max_delta > 0) {
    uintx delta = ArchiveBuilder::current()->buffer_to_offset((address)(newtop-1));
    if (delta > _max_delta) {
      // This is just a sanity check and should not appear in any real world usage. This
      // happens only if you allocate more than 2GB of shared objects and would require
      // millions of shared classes.
      aot_log_error(aot)("Out of memory in the CDS archive: Please reduce the number of shared classes.");
      MetaspaceShared::unrecoverable_writing_error();
    }
  }

  return _top;
}

void DumpRegion::commit_to(char* newtop) {
  assert(CDSConfig::is_dumping_archive(), "sanity");
  char* base = _rs->base();
  size_t need_committed_size = newtop - base;
  size_t has_committed_size = _vs->committed_size();
  if (need_committed_size < has_committed_size) {
    return;
  }

  size_t min_bytes = need_committed_size - has_committed_size;
  size_t preferred_bytes = 1 * M;
  size_t uncommitted = _vs->reserved_size() - has_committed_size;

  size_t commit = MAX2(min_bytes, preferred_bytes);
  commit = MIN2(commit, uncommitted);
  assert(commit <= uncommitted, "sanity");

  if (!_vs->expand_by(commit, false)) {
    aot_log_error(aot)("Failed to expand shared space to %zu bytes",
                    need_committed_size);
    MetaspaceShared::unrecoverable_writing_error();
  }

  const char* which;
  if (_rs->base() == (char*)MetaspaceShared::symbol_rs_base()) {
    which = "symbol";
  } else {
    which = "shared";
  }
  log_debug(aot)("Expanding %s spaces by %7zu bytes [total %9zu bytes ending at %p]",
                 which, commit, _vs->actual_committed_size(), _vs->high());
}

char* DumpRegion::allocate(size_t num_bytes, size_t alignment) {
  // Always align to at least minimum alignment
  alignment = MAX2(SharedSpaceObjectAlignment, alignment);
  char* p = (char*)align_up(_top, alignment);
  char* newtop = p + align_up(num_bytes, (size_t)SharedSpaceObjectAlignment);
  expand_top_to(newtop);
  memset(p, 0, newtop - p);
  return p;
}

void DumpRegion::append_intptr_t(intptr_t n, bool need_to_mark) {
  assert(is_aligned(_top, sizeof(intptr_t)), "bad alignment");
  intptr_t *p = (intptr_t*)_top;
  char* newtop = _top + sizeof(intptr_t);
  expand_top_to(newtop);
  *p = n;
  if (need_to_mark) {
    ArchivePtrMarker::mark_pointer(p);
  }
}

void DumpRegion::print(size_t total_bytes) const {
  char* base = used() > 0 ? ArchiveBuilder::current()->to_requested(_base) : nullptr;
  log_debug(aot)("%s space: %9zu [ %4.1f%% of total] out of %9zu bytes [%5.1f%% used] at " INTPTR_FORMAT,
                 _name, used(), percent_of(used(), total_bytes), reserved(), percent_of(used(), reserved()),
                 p2i(base));
}

void DumpRegion::print_out_of_space_msg(const char* failing_region, size_t needed_bytes) {
  aot_log_error(aot)("[%-8s] " PTR_FORMAT " - " PTR_FORMAT " capacity =%9d, allocated =%9d",
                 _name, p2i(_base), p2i(_top), int(_end - _base), int(_top - _base));
  if (strcmp(_name, failing_region) == 0) {
    aot_log_error(aot)(" required = %d", int(needed_bytes));
  }
}

void DumpRegion::init(ReservedSpace* rs, VirtualSpace* vs) {
  _rs = rs;
  _vs = vs;
  // Start with 0 committed bytes. The memory will be committed as needed.
  if (!_vs->initialize(*_rs, 0)) {
    fatal("Unable to allocate memory for shared space");
  }
  _base = _top = _rs->base();
  _end = _rs->end();
}

void DumpRegion::pack(DumpRegion* next) {
  if (!is_packed()) {
    _end = (char*)align_up(_top, MetaspaceShared::core_region_alignment());
    _is_packed = true;
  }
  _end = (char*)align_up(_top, MetaspaceShared::core_region_alignment());
  _is_packed = true;
  if (next != nullptr) {
    next->_rs = _rs;
    next->_vs = _vs;
    next->_base = next->_top = this->_end;
    next->_end = _rs->end();
  }
}

void WriteClosure::do_ptr(void** p) {
  // Write ptr into the archive; ptr can be:
  //   (a) null                 -> written as 0
  //   (b) a "buffered" address -> written as is
  //   (c) a "source"   address -> convert to "buffered" and write
  // The common case is (c). E.g., when writing the vmClasses into the archive.
  // We have (b) only when we don't have a corresponding source object. E.g.,
  // the archived c++ vtable entries.
  address ptr = *(address*)p;
  if (ptr != nullptr && !ArchiveBuilder::current()->is_in_buffer_space(ptr)) {
    ptr = ArchiveBuilder::current()->get_buffered_addr(ptr);
  }
  // null pointers do not need to be converted to offsets
  if (ptr != nullptr) {
    ptr = (address)ArchiveBuilder::current()->buffer_to_offset(ptr);
  }
  _dump_region->append_intptr_t((intptr_t)ptr, false);
}

void ReadClosure::do_ptr(void** p) {
  assert(*p == nullptr, "initializing previous initialized pointer.");
  intptr_t obj = nextPtr();
  assert(obj >= 0, "sanity.");
  *p = (obj != 0) ? (void*)(_base_address + obj) : (void*)obj;
}

void ReadClosure::do_u4(u4* p) {
  intptr_t obj = nextPtr();
  *p = (u4)(uintx(obj));
}

void ReadClosure::do_int(int* p) {
  intptr_t obj = nextPtr();
  *p = (int)(intx(obj));
}

void ReadClosure::do_bool(bool* p) {
  intptr_t obj = nextPtr();
  *p = (bool)(uintx(obj));
}

void ReadClosure::do_tag(int tag) {
  int old_tag;
  old_tag = (int)(intptr_t)nextPtr();
  // do_int(&old_tag);
  assert(tag == old_tag, "tag doesn't match (%d, expected %d)", old_tag, tag);
  FileMapInfo::assert_mark(tag == old_tag);
}

void ArchiveUtils::log_to_classlist(BootstrapInfo* bootstrap_specifier, TRAPS) {
  if (ClassListWriter::is_enabled()) {
    if (LambdaProxyClassDictionary::is_supported_invokedynamic(bootstrap_specifier)) {
      const constantPoolHandle& pool = bootstrap_specifier->pool();
      if (SystemDictionaryShared::is_builtin_loader(pool->pool_holder()->class_loader_data())) {
        // Currently lambda proxy classes are supported only for the built-in loaders.
        ResourceMark rm(THREAD);
        int pool_index = bootstrap_specifier->bss_index();
        ClassListWriter w;
        w.stream()->print("%s %s", ClassListParser::lambda_proxy_tag(), pool->pool_holder()->name()->as_C_string());
        CDSIndyInfo cii;
        ClassListParser::populate_cds_indy_info(pool, pool_index, &cii, CHECK);
        GrowableArray<const char*>* indy_items = cii.items();
        for (int i = 0; i < indy_items->length(); i++) {
          w.stream()->print(" %s", indy_items->at(i));
        }
        w.stream()->cr();
      }
    }
  }
}

bool ArchiveUtils::has_aot_initialized_mirror(InstanceKlass* src_ik) {
  if (SystemDictionaryShared::is_excluded_class(src_ik)) {
    assert(!ArchiveBuilder::current()->has_been_buffered(src_ik), "sanity");
    return false;
  }
  return ArchiveBuilder::current()->get_buffered_addr(src_ik)->has_aot_initialized_mirror();
}

size_t HeapRootSegments::size_in_bytes(size_t seg_idx) {
  assert(seg_idx < _count, "In range");
  return objArrayOopDesc::object_size(size_in_elems(seg_idx)) * HeapWordSize;
}

int HeapRootSegments::size_in_elems(size_t seg_idx) {
  assert(seg_idx < _count, "In range");
  if (seg_idx != _count - 1) {
    return _max_size_in_elems;
  } else {
    // Last slice, leftover
    return _roots_count % _max_size_in_elems;
  }
}

size_t HeapRootSegments::segment_offset(size_t seg_idx) {
  assert(seg_idx < _count, "In range");
  return _base_offset + seg_idx * _max_size_in_bytes;
}

ArchiveWorkers::ArchiveWorkers() :
        _end_semaphore(0),
        _num_workers(max_workers()),
        _started_workers(0),
        _finish_tokens(0),
        _state(UNUSED),
        _task(nullptr) {}

ArchiveWorkers::~ArchiveWorkers() {
  assert(Atomic::load(&_state) != WORKING, "Should not be working");
}

int ArchiveWorkers::max_workers() {
  // The pool is used for short-lived bursty tasks. We do not want to spend
  // too much time creating and waking up threads unnecessarily. Plus, we do
  // not want to overwhelm large machines. This is why we want to be very
  // conservative about the number of workers actually needed.
  return MAX2(0, log2i_graceful(os::active_processor_count()));
}

bool ArchiveWorkers::is_parallel() {
  return _num_workers > 0;
}

void ArchiveWorkers::start_worker_if_needed() {
  while (true) {
    int cur = Atomic::load(&_started_workers);
    if (cur >= _num_workers) {
      return;
    }
    if (Atomic::cmpxchg(&_started_workers, cur, cur + 1, memory_order_relaxed) == cur) {
      new ArchiveWorkerThread(this);
      return;
    }
  }
}

void ArchiveWorkers::run_task(ArchiveWorkerTask* task) {
  assert(Atomic::load(&_state) == UNUSED, "Should be unused yet");
  assert(Atomic::load(&_task) == nullptr, "Should not have running tasks");
  Atomic::store(&_state, WORKING);

  if (is_parallel()) {
    run_task_multi(task);
  } else {
    run_task_single(task);
  }

  assert(Atomic::load(&_state) == WORKING, "Should be working");
  Atomic::store(&_state, SHUTDOWN);
}

void ArchiveWorkers::run_task_single(ArchiveWorkerTask* task) {
  // Single thread needs no chunking.
  task->configure_max_chunks(1);

  // Execute the task ourselves, as there are no workers.
  task->work(0, 1);
}

void ArchiveWorkers::run_task_multi(ArchiveWorkerTask* task) {
  // Multiple threads can work with multiple chunks.
  task->configure_max_chunks(_num_workers * CHUNKS_PER_WORKER);

  // Set up the run and publish the task. Issue one additional finish token
  // to cover the semaphore shutdown path, see below.
  Atomic::store(&_finish_tokens, _num_workers + 1);
  Atomic::release_store(&_task, task);

  // Kick off pool startup by starting a single worker, and proceed
  // immediately to executing the task locally.
  start_worker_if_needed();

  // Execute the task ourselves, while workers are catching up.
  // This allows us to hide parts of task handoff latency.
  task->run();

  // Done executing task locally, wait for any remaining workers to complete.
  // Once all workers report, we can proceed to termination. To do this safely,
  // we need to make sure every worker has left. A spin-wait alone would suffice,
  // but we do not want to burn cycles on it. A semaphore alone would not be safe,
  // since workers can still be inside it as we proceed from wait here. So we block
  // on semaphore first, and then spin-wait for all workers to terminate.
  _end_semaphore.wait();
  SpinYield spin;
  while (Atomic::load(&_finish_tokens) != 0) {
    spin.wait();
  }

  OrderAccess::fence();

  assert(Atomic::load(&_finish_tokens) == 0, "All tokens are consumed");
}

void ArchiveWorkers::run_as_worker() {
  assert(is_parallel(), "Should be in parallel mode");

  ArchiveWorkerTask* task = Atomic::load_acquire(&_task);
  task->run();

  // All work done in threads should be visible to caller.
  OrderAccess::fence();

  // Signal the pool the work is complete, and we are exiting.
  // Worker cannot do anything else with the pool after this.
  if (Atomic::sub(&_finish_tokens, 1, memory_order_relaxed) == 1) {
    // Last worker leaving. Notify the pool it can unblock to spin-wait.
    // Then consume the last token and leave.
    _end_semaphore.signal();
    int last = Atomic::sub(&_finish_tokens, 1, memory_order_relaxed);
    assert(last == 0, "Should be");
  }
}

void ArchiveWorkerTask::run() {
  while (true) {
    int chunk = Atomic::load(&_chunk);
    if (chunk >= _max_chunks) {
      return;
    }
    if (Atomic::cmpxchg(&_chunk, chunk, chunk + 1, memory_order_relaxed) == chunk) {
      assert(0 <= chunk && chunk < _max_chunks, "Sanity");
      work(chunk, _max_chunks);
    }
  }
}

void ArchiveWorkerTask::configure_max_chunks(int max_chunks) {
  if (_max_chunks == 0) {
    _max_chunks = max_chunks;
  }
}

ArchiveWorkerThread::ArchiveWorkerThread(ArchiveWorkers* pool) : NamedThread(), _pool(pool) {
  set_name("ArchiveWorkerThread");
  if (os::create_thread(this, os::os_thread)) {
    os::start_thread(this);
  } else {
    vm_exit_during_initialization("Unable to create archive worker",
                                  os::native_thread_creation_failed_msg());
  }
}

void ArchiveWorkerThread::run() {
  // Avalanche startup: each worker starts two others.
  _pool->start_worker_if_needed();
  _pool->start_worker_if_needed();

  // Set ourselves up.
  os::set_priority(this, NearMaxPriority);

  // Work.
  _pool->run_as_worker();
}

void ArchiveWorkerThread::post_run() {
  this->NamedThread::post_run();
  delete this;
}
