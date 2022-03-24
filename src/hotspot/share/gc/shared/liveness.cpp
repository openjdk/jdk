#include "classfile/classLoaderDataGraph.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/liveness.hpp"
#include "gc/shared/oopStorageSet.inline.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "jfr/jfrEvents.hpp"
#include "logging/log.hpp"
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
    if (ConcLivenessVerify) {
      // Walk the roots
      _estimator->do_roots();
      // Directly compute liveness by tracing through the heap on a safepoint.
      // This will save the results for use later to verify the concurrent scan.
      _estimator->compute_liveness();
    }

    // When verify is true, this will walk the roots a second time.
    _estimator->do_roots();
  }

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
  , _all_object_count(0)
  , _all_object_size_words(0)
  , _verified_object_count(0)
  , _verified_object_size_words(0)
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
}

bool LivenessEstimatorThread::estimation_begin() {
  assert(_mark_stack.is_empty(), "Unexpected oops in mark stack");
  _all_object_count = 0;
  _all_object_size_words = 0;
  if (ConcLivenessVerify) {
    _verified_object_count = 0;
    _verified_object_size_words = 0;
  }

  return commit_bit_map_memory();
}

void LivenessEstimatorThread::estimation_end(bool completed) {
  uncommit_bit_map_memory();

  if (!completed) {
    _mark_stack.clear(true);
  } else {
    assert(_mark_stack.is_empty(), "Should have empty mark stack if scan completed");

    size_t all_object_size_bytes = _all_object_size_words * HeapWordSize;
    send_live_set_estimate(_all_object_count, all_object_size_bytes);

    if (ConcLivenessVerify) {
      long count_difference = long(_verified_object_count) - long(_all_object_count);
      long size_difference = long(_verified_object_size_words) - long(_all_object_size_words);

      log_info(gc, estimator)("Verified - estimate: " INT64_FORMAT " objects, " INT64_FORMAT " bytes.",
        count_difference, size_difference * HeapWordSize);
    }
  }
}

bool LivenessEstimatorThread::estimate_liveness() {
  Ticks start = Ticks::now();
  // Run root scan on a safepoint. Much of this could be done concurrently,
  // but that would also take much longer to implement.
  VM_LivenessRootScan root_scan(this);
  VMThread::execute(&root_scan);

  Tickspan root_scan_time = Ticks::now() - start;

  log_info(gc, estimator)("Mark stack size after root scan: " SIZE_FORMAT, _mark_stack.size());

  LivenessOopClosure cl(this);
  SuspendibleThreadSetJoiner sst;
  while (!_mark_stack.is_empty()) {
    oop obj = _mark_stack.pop();
    obj->oop_iterate(&cl);
    if (!check_yield_and_continue(&sst)) {
      return false;
    }
  }

  Tickspan total_scan_time = Ticks::now() - start;
  Tickspan non_root_scan_time = total_scan_time - root_scan_time;

  log_info(gc, estimator)("Phase timings:");
  log_info(gc, estimator)("    Total scan       : %fms", total_scan_time.seconds() * MILLIUNITS);
  log_info(gc, estimator)("    Root scan (pause): %fms", root_scan_time.seconds() * MILLIUNITS);
  log_info(gc, estimator)("    Non-root scan    : %fms", non_root_scan_time.seconds() * MILLIUNITS);

  return true;
}

void LivenessEstimatorThread::do_roots() {
  assert(Thread::current()->is_VM_thread(), "Expected to be on safepoint here");

  LivenessOopClosure cl(this);
  OopStorageSet::strong_oops_do(&cl);
  Threads::oops_do(&cl, NULL);

  CLDToOopClosure cldt(&cl, ClassLoaderData::_claim_none);
  ClassLoaderDataGraph::always_strong_cld_do(&cldt);

  // TODO: do we need to do anything with the code cache? 
  // Compare GenCollectedHeap::process_roots with RootSetClosure::process
}

void LivenessEstimatorThread::do_oop(oop obj) {
  if (!_mark_bit_map.is_marked(obj)) {
    if (_mark_stack.is_full()) {
      log_warning(gc, estimator)("Mark stack is full");
    } else {
      _mark_bit_map.mark(obj);
      _mark_stack.push(obj);
      _all_object_count++;
      _all_object_size_words += obj->size();
    }
  }
}

bool LivenessEstimatorThread::check_yield_and_continue(SuspendibleThreadSetJoiner* sst) {
  if (sst->should_yield()) {
    // Shenandoah may not update this count, but other collectors seem to.
    unsigned int collections = Universe::heap()->total_collections();

    // This blocks the caller until the vm operation has executed.
    SuspendibleThreadSet::yield();

    if (collections != Universe::heap()->total_collections()) {
      // Heap has been collected, pointer in the mark queues may be invalid.
      return false;
    }
  }

  return !should_terminate();
}

void LivenessEstimatorThread::stop_service() {
  // We could have a long timeout on the wait before the estimator thread wakes up
  MonitorLocker locker(&_lock);
  _lock.notify();
  log_info(gc, estimator)("Notified estimator thread to wakeup.");
}

void LivenessEstimatorThread::send_live_set_estimate(size_t count, size_t size_bytes) {
  log_info(gc, estimator)("Summary: " SIZE_FORMAT " objects, total size " SIZE_FORMAT " bytes", count, size_bytes);

  EventLiveSetEstimate evt;
  if (evt.should_commit()) {
    log_info(gc, estimator)("Sending JFR event");
    evt.set_objectCount(count);
    evt.set_size(size_bytes);
    evt.commit();
  } else {
    log_info(gc, estimator)("Skipping JFR event because it's disabled");
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

  _verified_object_count = _all_object_count;
  _verified_object_size_words = _all_object_size_words;
  _all_object_count = 0;
  _all_object_size_words = 0;
  _mark_bit_map.clear();
}
