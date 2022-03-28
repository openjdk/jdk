#include "classfile/classLoaderDataGraph.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/liveness.hpp"
#include "gc/shared/oopStorageSet.inline.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "jfr/jfrEvents.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/heapInspection.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/stack.inline.hpp"
#include "services/memTracker.hpp"
#include "utilities/ticks.hpp"


class VM_LivenessRootScan : public VM_Operation {
 public:
  explicit VM_LivenessRootScan(LivenessEstimatorThread* estimator)
    : _estimator(estimator) {}

  virtual VMOp_Type type() const override {
    return VMOp_LivenessRootScan;
  }

  virtual void doit() override {
    Ticks start = Ticks::now();
    if (ConcLivenessVerify) {
      // Walk the roots
      _estimator->do_roots();
      // Directly compute liveness by tracing through the heap on a safepoint.
      // This will save the results for use later to verify the concurrent scan.
      _estimator->compute_liveness();
    }

    // When verify is true, this will walk the roots a second time.
    _estimator->do_roots();
    _vm_op_time = Ticks::now() - start;
  }

 Tickspan _vm_op_time;

 private:
  LivenessEstimatorThread* _estimator;
};

class LivenessOopClosure : public BasicOopIterateClosure {
 private:
  LivenessEstimatorThread* _estimator;

  template<typename OopT>
  void do_oop_work(OopT* location) {
    OopT o = RawAccess<>::oop_load(location);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      _estimator->do_oop(obj);
    }
  }
 public:
  explicit LivenessOopClosure(LivenessEstimatorThread* estimator)
    : _estimator(estimator) {}

  virtual void do_oop(oop* o) override       { do_oop_work(o); }
  virtual void do_oop(narrowOop* o) override { do_oop_work(o); }
};

LivenessEstimatorThread::LivenessEstimatorThread()
  : ConcurrentGCThread()
  , _lock(Mutex::safepoint - 1, "LivenessEstimator_lock", true)
  , _estimated_object_count(0)
  , _estimated_object_size_words(0)
  , _actual_object_count(0)
  , _actual_object_size_words(0)
{
  // This initializes the bitmap and reserves the memory, but does not commit it
  initialize_mark_bit_map();

  // create the os thread using default priority
  create_and_start();
}

void LivenessEstimatorThread::initialize_mark_bit_map() {
  CollectedHeap* heap = Universe::heap();
  VirtualSpaceSummary summary = heap->create_heap_space_summary();
  size_t bitmap_page_size = UseLargePages ? (size_t)os::large_page_size() : (size_t)os::vm_page_size();
  size_t bitmap_size = MarkBitMap::compute_size(summary.reserved_size());
  bitmap_size = align_up(bitmap_size, bitmap_page_size);

  log_info(gc, estimator)("Start: " PTR_FORMAT ", max_capacity: " SIZE_FORMAT ", reserved_size: " SIZE_FORMAT,
               p2i(summary.start()), heap->max_capacity(), summary.reserved_size());

  ReservedSpace bitmap(bitmap_size, bitmap_page_size);
  MemTracker::record_virtual_memory_type(bitmap.base(), mtTracing);
  _mark_bit_map_region = MemRegion((HeapWord *) bitmap.base(), bitmap.size() / HeapWordSize);
  MemRegion heap_region = MemRegion((HeapWord *) summary.start(), summary.reserved_size() / HeapWordSize);
  _mark_bit_map.initialize(heap_region, _mark_bit_map_region);
}

void LivenessEstimatorThread::run_service() {
  while (!should_terminate()) {
    // Start with a wait because there is nothing interesting in the heap yet.
    MonitorLocker locker(&_lock,Monitor::SafepointCheckFlag::_no_safepoint_check_flag);
    bool timeout = locker.wait(ConcLivenessEstimateSeconds * MILLIUNITS);
    log_info(gc, estimator)("Starting, scheduled: %s", BOOL_TO_STR(timeout));

    if (!is_concurrent_gc_active() && estimation_begin()) {
      bool completed = estimate_liveness();
      log_info(gc, estimator)("Completed: %s", BOOL_TO_STR(completed));
      estimation_end(completed);
    }
  }

  if (ConcLivenessVerify) {
    LogTarget(Info, gc, estimator) lt;
    LogStream ls(lt);
    ls.print("Object count accuracy : ");
    _object_count_error.print_on(&ls);
    ls.print("Object size accuracy  : ");
    _object_size_error.print_on(&ls);
  }
}

bool LivenessEstimatorThread::estimation_begin() {
  assert(_mark_stack.is_empty(), "Unexpected oops in mark stack");
  _estimated_object_count = 0;
  _estimated_object_size_words = 0;
  if (ConcLivenessVerify) {
    _actual_object_count = 0;
    _actual_object_size_words = 0;
  }

  return commit_bit_map_memory();
}

void LivenessEstimatorThread::estimation_end(bool completed) {
  uncommit_bit_map_memory();

  if (!completed) {
    _mark_stack.clear(true);
  } else {
    assert(_mark_stack.is_empty(), "Should have empty mark stack if scan completed");

    size_t all_object_size_bytes = _estimated_object_size_words * HeapWordSize;
    log_info(gc, estimator)("Estimated: " SIZE_FORMAT " objects, total size " SIZE_FORMAT " (" SIZE_FORMAT "%s)",
                            _estimated_object_count, all_object_size_bytes, byte_size_in_proper_unit(all_object_size_bytes), proper_unit_for_byte_size(all_object_size_bytes));

    send_live_set_estimate<EventLiveSetEstimate>(_estimated_object_count, all_object_size_bytes);

    if (ConcLivenessVerify) {
      verify_estimate();
    }
  }
}

void LivenessEstimatorThread::verify_estimate() {
  _object_count_error.sample(_estimated_object_count, _actual_object_count);
  _object_size_error.sample(_estimated_object_size_words, _actual_object_size_words);

  long count_difference = long(_actual_object_count) - long(_estimated_object_count);
  long size_difference = long(_actual_object_size_words) - long(_estimated_object_size_words);

  log_info(gc, estimator)("Verified - estimate: " INT64_FORMAT " objects, " INT64_FORMAT " bytes.",
                      count_difference, size_difference * HeapWordSize);
}

class HistoClosure : public KlassInfoClosure {
 private:
  KlassInfoHisto* _cih;
 public:
  explicit HistoClosure(KlassInfoHisto* cih) : _cih(cih) {}

  void do_cinfo(KlassInfoEntry* cie) {
    _cih->add(cie);
  }
};

bool LivenessEstimatorThread::estimate_liveness() {
  Ticks start = Ticks::now();
  // Run root scan on a safepoint. Much of this could be done concurrently,
  // but that would also take much longer to implement.
  VM_LivenessRootScan root_scan(this);
  VMThread::execute(&root_scan);

  SuspendibleThreadSetJoiner sst;
  if (!check_yield_and_continue(&sst)) {
    return false;
  }

  KlassInfoTable cit(false);

  Ticks after_vm_op = Ticks::now();

  log_info(gc, estimator)("Mark stack size after root scan: " SIZE_FORMAT, _mark_stack.size());

  LivenessOopClosure cl(this);
  while (!_mark_stack.is_empty()) {
    if (!check_yield_and_continue(&sst)) {
      return false;
    }

    oop obj = _mark_stack.pop();
    obj->oop_iterate(&cl);
    if (ConcLivenessHisto) {
      cit.record_instance(obj);
    }
  }
  Ticks after_scan = Ticks::now();

  if (ConcLivenessHisto) {
    // Print heap histogram
    KlassInfoHisto histo(&cit);
    HistoClosure hc(&histo);
    cit.iterate(&hc);
    histo.sort();
    LogTarget(Info, gc, estimator, classhisto) lt;
    LogStream ls(lt);
    histo.print_histo_on(&ls);
  }

  Ticks finish = Ticks::now();

  log_info(gc, estimator)("Phase timings:");
  log_info(gc, estimator)("    Total                   : %fms", (finish - start).seconds() * MILLIUNITS);
  log_info(gc, estimator)("    Root scan (at safepoint): %fms", root_scan._vm_op_time.seconds() * MILLIUNITS);
  log_info(gc, estimator)("    Non-root scan           : %fms", (after_scan - after_vm_op).seconds() * MILLIUNITS);
  if (ConcLivenessHisto) {
    log_info(gc, estimator)("    Histogram               : %fms", (finish - after_scan).seconds() * MILLIUNITS);
  }

  return true;
}

void LivenessEstimatorThread::do_roots() {
  assert(Thread::current()->is_VM_thread(), "Expected to be on safepoint here");
  Ticks start = Ticks::now();

  StrongRootsScope roots_scope(0);
  LivenessOopClosure cl(this);

  OopStorageSet::strong_oops_do(&cl);
  for (OopStorage* storage : OopStorageSet::Range<OopStorageSet::WeakId>()) {
    storage->oops_do(&cl);
  }
  Ticks a = Ticks::now();

  Threads::oops_do(&cl, NULL);
  Ticks b = Ticks::now();

  CLDToOopClosure cldt(&cl, ClassLoaderData::_claim_none);
  // ClassLoaderDataGraph::cld_do(&cldt);
  ClassLoaderDataGraph::always_strong_cld_do(&cldt);

  Ticks c = Ticks::now();

  MarkingCodeBlobClosure code_blob_closure(&cl, false);
  CodeCache::blobs_do(&code_blob_closure);
  Ticks d = Ticks::now();

  Tickspan total_time = Ticks::now() - start;

  log_info(gc, estimator)("Root scan timings:");
  log_info(gc, estimator)("    Total scan          : %fms", total_time.seconds() * MILLIUNITS);
  log_info(gc, estimator)("    OopStorageSet       : %fms", (a - start).seconds() * MILLIUNITS);
  log_info(gc, estimator)("    Threads             : %fms", (b - a).seconds() * MILLIUNITS);
  log_info(gc, estimator)("    ClassLoaderDataGraph: %fms", (c - b).seconds() * MILLIUNITS);
  log_info(gc, estimator)("    CodeCache           : %fms", (d - c).seconds() * MILLIUNITS);
}

void LivenessEstimatorThread::do_oop(oop obj) {
  if (!_mark_bit_map.is_marked(obj)) {
    if (_mark_stack.is_full()) {
      log_warning(gc, estimator)("Mark stack is full");
    } else {
      _mark_bit_map.mark(obj);
      _mark_stack.push(obj);
      _estimated_object_count++;
      _estimated_object_size_words += obj->size();
    }
  }
}

bool LivenessEstimatorThread::check_yield_and_continue(SuspendibleThreadSetJoiner* sst) {
  if (sst->should_yield()) {
    // Shenandoah may not update this count, but other collectors seem to.
    unsigned int collections = Universe::heap()->total_collections();
    log_info(gc,estimator)("Total collections before safepoint: " UINT32_FORMAT, collections);
    // This blocks the caller until the vm operation has executed.
    SuspendibleThreadSet::yield();

    if (collections != Universe::heap()->total_collections()) {
      // Heap has been collected, pointer in the mark queues may be invalid.
      log_info(gc,estimator)("Total collections after safepoint: " UINT32_FORMAT, Universe::heap()->total_collections());
      return false;
    }
  }

  if (Universe::heap()->is_gc_active()) {
    log_info(gc,estimator)("GC is running.");
    return false;
  }

  return !should_terminate();
}

void LivenessEstimatorThread::stop_service() {
  // We could have a long timeout on the wait before the estimator thread wakes up
  MonitorLocker locker(&_lock);
  _lock.notify();
  log_info(gc, estimator)("Notified estimator thread to wakeup.");
}

template<typename EventT>
void LivenessEstimatorThread::send_live_set_estimate(size_t count, size_t size_bytes) {
  EventT evt;
  if (evt.should_commit()) {
    log_info(gc, estimator)("Sending JFR event: %d", evt.id());
    evt.set_objectCount(count);
    evt.set_size(size_bytes);
    evt.commit();
  } else {
    log_info(gc, estimator)("Skipping JFR event (%d) because it's disabled", evt.id());
  }
}

bool LivenessEstimatorThread::is_concurrent_gc_active() {
  // TODO: Implement this. Maybe add a virtual function to CollectedHeap?
  // We don't want to run the estimate if concurrent gc threads are working.
  return false;
}

const char* LivenessEstimatorThread::type_name() const {
  return "LivenessEstimator";
}

bool LivenessEstimatorThread::commit_bit_map_memory() {
  if (!os::commit_memory((char*)_mark_bit_map_region.start(), _mark_bit_map_region.byte_size(), false)) {
    log_warning(gc, estimator)("Could not commit native memory for marking bitmap, estimator failed");
    return false;
  }
  return true;
}

bool LivenessEstimatorThread::uncommit_bit_map_memory() {
  if (!os::uncommit_memory((char*)_mark_bit_map_region.start(), _mark_bit_map_region.byte_size())) {
    log_warning(gc, estimator)("Could not uncommit native memory for marking bitmap");
    return false;
  }
  return true;
}

void LivenessEstimatorThread::compute_liveness() {
  assert_at_safepoint();
  assert(ConcLivenessVerify, "Only for verification");

  LivenessOopClosure cl(this);
  while (!_mark_stack.is_empty()) {
    oop obj = _mark_stack.pop();
    obj->oop_iterate(&cl);
  }

  _actual_object_count = _estimated_object_count;
  _actual_object_size_words = _estimated_object_size_words;

  size_t actual_object_size_bytes = _actual_object_size_words * HeapWordSize;
  send_live_set_estimate<EventLiveSetActual>(_actual_object_count, actual_object_size_bytes);

  log_info(gc, estimator)("Actual: " SIZE_FORMAT " objects, total size " SIZE_FORMAT " (" SIZE_FORMAT "%s)",
                          _actual_object_count, actual_object_size_bytes,
                          byte_size_in_proper_unit(actual_object_size_bytes),
                          proper_unit_for_byte_size(actual_object_size_bytes));

  _estimated_object_count = 0;
  _estimated_object_size_words = 0;
  _mark_bit_map.clear();
}
