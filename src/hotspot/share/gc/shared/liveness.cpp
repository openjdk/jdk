#include "classfile/classLoaderDataGraph.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/liveness.hpp"
#include "gc/shared/oopStorageSet.inline.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/shared/markBitMap.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "jfr/jfrEvents.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/heapInspection.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/allocation.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/threads.hpp"
#include "services/memTracker.hpp"
#include "utilities/stack.inline.hpp"
#include "utilities/ticks.hpp"
#include "gc/shared/weakProcessor.inline.hpp"
#include "gc/shared/oopStorageSetParState.inline.hpp"

size_t LivenessEstimatorThread::live_heap_usage = 0;
size_t LivenessEstimatorThread::live_object_count = 0;

class VM_LivenessRootScan : public VM_Operation {
 public:
  explicit VM_LivenessRootScan(LivenessEstimatorThread* estimator)
    : _estimator(estimator) {}

  VMOp_Type type() const override {
    return VMOp_LivenessRootScan;
  }

  void doit() override {
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
  uint _task_num;

  template<typename OopT>
  void do_oop_work(OopT* location) {
    OopT o = RawAccess<>::oop_load(location);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);

      // Add tasks to worker's queue. Otherwise add in round robin scheduling.
      // Useful when marking roots with a single thread so all workers get a similar
      // amount of roots instead of all roots going into a single worker's queue
      Thread* current = Thread::current();
      if (current->is_Worker_thread()) {
        _task_num = WorkerThread::worker_id();
      } else {
        assert(current->is_VM_thread(), "Round robin should only be used on VM Thread");
        _task_num = (_task_num + 1) % _estimator->get_num_workers();
      }

      _estimator->do_oop(obj, _task_num);
    }
  }
 public:

  explicit LivenessOopClosure(LivenessEstimatorThread* estimator)
    : _estimator(estimator), _task_num(0) {}

  void do_oop(oop* o) override       { do_oop_work(o); }
  void do_oop(narrowOop* o) override { do_oop_work(o); }
};

class LivenessConcurrentMarkTask : public WorkerTask {
public:
  LivenessConcurrentMarkTask(LivenessEstimatorThread* estimator, KlassInfoTable* cit) :
    WorkerTask("Liveness Concurrent Mark"),
    _estimator(estimator),
    _cit(cit) {}

  explicit LivenessConcurrentMarkTask(LivenessEstimatorThread* estimator) :
    LivenessConcurrentMarkTask(estimator, nullptr) {}

  void work(uint worker_id) override {
    unsigned int collections = Universe::heap()->total_collections();

    SuspendibleThreadSetJoiner sst;

    if (collections != Universe::heap()->total_collections()) {
      // The gc has run while this thread was joining the collection set. The oops in the
      // mark stack from the root scan cannot be used.
      _estimator->_task_failed = true;
      return;
    }

    log_debug(gc, estimator)("Worker %d started", worker_id);

    MarkTaskQueue* queue = _estimator->_task_queues->queue(worker_id);

    // Attempt to get oop from queue. If none are available steal from another queue
    oop obj;
    LivenessOopClosure cl(_estimator);
    while (queue->pop_local(obj) || _estimator->_task_queues->steal(worker_id, obj)) {
      if (!_estimator->check_yield_and_continue(&sst)) {
        _estimator->_task_failed = true;
        return;
      }

      obj->oop_iterate(&cl);
      if (ConcLivenessHisto && _cit != nullptr) {
        _cit->record_instance(obj);
      }
    }
    log_debug(gc, estimator)("Worker %d done", worker_id);
  }

private:
  LivenessEstimatorThread* _estimator;
  KlassInfoTable* _cit;
};

class ThreadRootsTaskClosure : public ThreadClosure {
public:
  explicit ThreadRootsTaskClosure(LivenessEstimatorThread* estimator) :
    _estimator(estimator) {}

  void do_thread(Thread* thread) override {
    LivenessOopClosure cl(_estimator);

    thread->oops_do(&cl, nullptr);
  }

private:
  LivenessEstimatorThread* _estimator;
};

class LivenessConcurrentRootMarkTask : public WorkerTask {
OopStorageSetStrongParState<false /* concurrent */, false /* is_const */> _oop_storage_strong_par_state;

public:
  explicit LivenessConcurrentRootMarkTask(LivenessEstimatorThread* estimator) :
    WorkerTask("Liveness Concurrent Mark"),
    _estimator(estimator),
    _strong_roots_scope(estimator->get_num_workers()) {}

  void work(uint worker_id) override {
    ResourceMark rm;

    log_debug(gc, estimator)("Worker %d started", worker_id);

    // Threads
    ThreadRootsTaskClosure thread_cl(_estimator);
    Threads::possibly_parallel_threads_do(true /*parallel */, &thread_cl);

    // Strong
    LivenessOopClosure oop_cl(_estimator);
    _oop_storage_strong_par_state.oops_do(&oop_cl);

    log_debug(gc, estimator)("Worker %d done", worker_id);
  }

private:
  LivenessEstimatorThread* _estimator;
  StrongRootsScope _strong_roots_scope; // needed for Threads::possibly_parallel_threads_do
};

LivenessEstimatorThread::LivenessEstimatorThread()
  : ConcurrentGCThread()
  , _lock(Mutex::safepoint - 1, "LivenessEstimator_lock", true)
  , _workers(nullptr)
  , _task_queues(nullptr)
  , _estimated_object_count(nullptr)
  , _estimated_object_size_words(nullptr)
  , _actual_object_count(0)
  , _actual_object_size_words(0)
  , _task_failed(false)
{
  // Give this thread a name
  set_name("LivenessEstimator");

  // This initializes the bitmap and reserves the memory, but does not commit it
  initialize_mark_bit_map();

  // This initializes the workers for parallel marking
  initialize_workers();

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

void LivenessEstimatorThread::initialize_workers() {
  _workers = new WorkerThreads("Liveness Worker Thread", ConcLivenessThreads);
  _workers->initialize_workers();
  _workers->set_active_workers(ConcLivenessThreads);

  _task_queues = new MarkTaskQueueSet(ConcLivenessThreads);

  _estimated_object_count = NEW_C_HEAP_ARRAY(size_t, ConcLivenessThreads, mtTracing);
  _estimated_object_size_words = NEW_C_HEAP_ARRAY(size_t, ConcLivenessThreads, mtTracing);

  for (uint i = 0; i < ConcLivenessThreads; i++) {
    MarkTaskQueue* q = new MarkTaskQueue();
    _task_queues->register_queue(i, q);

    _estimated_object_count[i] = 0;
    _estimated_object_size_words[i] = 0;
  }
}

void LivenessEstimatorThread::run_service() {
  while (!should_terminate()) {
    // Start with a wait because there is nothing interesting in the heap yet.
    MonitorLocker locker(&_lock,Monitor::SafepointCheckFlag::_no_safepoint_check_flag);
    bool timeout = locker.wait(ConcLivenessEstimateSeconds * MILLIUNITS);

    if (timeout && !is_concurrent_gc_active() && estimation_begin()) {
      log_info(gc, estimator)("Starting, scheduled: %s", BOOL_TO_STR(timeout));
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

  FREE_C_HEAP_ARRAY(size_t, _estimated_object_count);
  FREE_C_HEAP_ARRAY(size_t, _estimated_object_size_words);
}

bool LivenessEstimatorThread::estimation_begin() {
  for (uint i = 0; i < _task_queues->size(); i++) {
    assert(_task_queues->queue(i)->is_empty(), "Unexpected oops in mark stack %d", i);
  }

  for (uint i = 0; i < ConcLivenessThreads; i++) {
    _estimated_object_count[i] = 0;
    _estimated_object_size_words[i] = 0;
  }

  if (ConcLivenessVerify) {
    _actual_object_count = 0;
    _actual_object_size_words = 0;
  }

  return commit_bit_map_memory();
}

void LivenessEstimatorThread::estimation_end(bool completed) {
  uncommit_bit_map_memory();

  if (!completed) {
    for (uint i = 0; i < _task_queues->size(); i++) {
      _task_queues->queue(i)->set_empty();
    }
  } else {
    for (uint i = 0; i < _task_queues->size(); i++) {
      assert(_task_queues->queue(i)->is_empty(), "Should have empty mark stack (stack %d) if scan completed", i);
    }

    // Loop over worker counts and sizes to find the total
    size_t all_object_size_bytes = 0;
    size_t all_object_count = 0;
    for (uint i = 0; i < ConcLivenessThreads; i++) {
      all_object_size_bytes += _estimated_object_size_words[i];
      all_object_count += _estimated_object_count[i];
    }
    all_object_size_bytes *= HeapWordSize;

    log_info(gc, estimator)("Estimated: " SIZE_FORMAT " objects, total size " SIZE_FORMAT " (" SIZE_FORMAT "%s)",
                            all_object_count, all_object_size_bytes, byte_size_in_proper_unit(all_object_size_bytes), proper_unit_for_byte_size(all_object_size_bytes));

    send_live_set_estimate<EventLiveSetEstimate>(all_object_count, all_object_size_bytes);

    if (ConcLivenessVerify) {
      verify_estimate();
    }
  }
}

void LivenessEstimatorThread::verify_estimate() {
  // Loop over worker counts and sizes to find the total
  size_t all_object_size_words = 0;
  size_t all_object_count = 0;
  for (uint i = 0; i < ConcLivenessThreads; i++) {
    all_object_size_words += _estimated_object_size_words[i];
    all_object_count += _estimated_object_count[i];
  }

  _object_count_error.sample(all_object_count, _actual_object_count);
  _object_size_error.sample(all_object_size_words, _actual_object_size_words);

  long count_difference = long(_actual_object_count) - long(all_object_count);
  long size_difference = long(_actual_object_size_words) - long(all_object_size_words);

  log_info(gc, estimator)("Verified - estimate: %ld objects, %ld bytes.",
                      count_difference, size_difference * HeapWordSize);
}

class HistoClosure : public KlassInfoClosure {
 private:
  KlassInfoHisto* _cih;
 public:
  explicit HistoClosure(KlassInfoHisto* cih) : _cih(cih) {}

  void do_cinfo(KlassInfoEntry* cie) override {
    _cih->add(cie);
  }
};

bool LivenessEstimatorThread::estimate_liveness() {
  Ticks start = Ticks::now();
  // Run root scan on a safepoint. Much of this could be done concurrently,
  // but that would also take much longer to implement.
  VM_LivenessRootScan root_scan(this);
  VMThread::execute(&root_scan);

  Ticks after_vm_op = Ticks::now();

  unsigned int collections = Universe::heap()->total_collections();
  log_info(gc,estimator)("Total collections before root scan: " UINT32_FORMAT, collections);

  // This will block if the VM thread has already started another operation. We need
  // to check if that operation completed a GC because the oops from the root set may
  // no longer be valid.
  SuspendibleThreadSetJoiner sst;

  if (collections != Universe::heap()->total_collections()) {
    // The gc has run while this thread was joining the collection set. The oops in the
    // mark stack from the root scan cannot be used.
    log_info(gc,estimator)("Total collections after root scan: " UINT32_FORMAT, collections);
    return false;
  }

  KlassInfoTable cit(false);


  for (uint i = 0; i < _task_queues->size(); i++) {
    log_debug(gc, estimator)("Mark stack %d size after root scan: %d", i, _task_queues->queue(i)->size());
  }

  _task_failed = false;

  LivenessConcurrentMarkTask task(this, &cit);
  _workers->run_task(&task);

  // Check if task completed
  if (_task_failed) {
    return false;
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

class IsAliveClosure: public BoolObjectClosure {
public:
  bool do_object_b(oop p) override {
    return !CompressedOops::is_null(p);
  }
};

void LivenessEstimatorThread::do_roots() {
  assert(Thread::current()->is_VM_thread(), "Expected to be on safepoint here");
  Ticks start = Ticks::now();

  LivenessOopClosure cl(this);

  // This adds support for parallel weak oop marking
  // After initial testing this was noticably worse than
  // doing it with a single thread. Testing with an application
  // with many weak oops may cause this to show improvements
  // but for now it is disabled
  // IsAliveClosure isAliveClosure;
  // WeakProcessor::weak_oops_do(_workers, &isAliveClosure, &cl, 1);

  for (OopStorage* storage : OopStorageSet::Range<OopStorageSet::WeakId>()) {
    storage->oops_do(&cl);
  }

  Ticks a = Ticks::now();

  LivenessConcurrentRootMarkTask task(this);
  _workers->run_task(&task);

  Ticks b = Ticks::now();

  CLDToOopClosure cldt(&cl, ClassLoaderData::_claim_none);
  // ClassLoaderDataGraph::cld_do(&cldt);
  ClassLoaderDataGraph::always_strong_cld_do(&cldt);

  Ticks c = Ticks::now();

  MarkingCodeBlobClosure code_blob_closure(&cl, false, false);
  CodeCache::blobs_do(&code_blob_closure);
  Ticks d = Ticks::now();

  Tickspan total_time = Ticks::now() - start;

  log_info(gc, estimator)("Root scan timings:");
  log_info(gc, estimator)("    Total scan          : %fms", total_time.seconds() * MILLIUNITS);
  log_info(gc, estimator)("    WeakOopStorageSet   : %fms", (a - start).seconds() * MILLIUNITS);
  log_info(gc, estimator)("    StrongOops/Threads  : %fms", (b - a).seconds() * MILLIUNITS);
  log_info(gc, estimator)("    ClassLoaderDataGraph: %fms", (c - b).seconds() * MILLIUNITS);
  log_info(gc, estimator)("    CodeCache           : %fms", (d - c).seconds() * MILLIUNITS);
}

void LivenessEstimatorThread::do_oop(oop obj, uint task_num) {
  oop is_marked_obj = obj;

  if (Universe::heap()->kind() == Universe::heap()->Name::Z) {
    is_marked_obj = ZOop::from_address(ZAddress::offset(ZOop::to_address(obj)));
  }

  if (!_mark_bit_map.par_is_marked(is_marked_obj)) {
    if (_mark_bit_map.par_mark(obj)) {
      MarkTaskQueue* queue = _task_queues->queue(task_num);
      if (queue->push(obj)) {
        _estimated_object_count[task_num]++;
        _estimated_object_size_words[task_num] += obj->size();
      }
      else {
        log_warning(gc, estimator)("Mark stack is full");
      }
    }
  }
}

uint LivenessEstimatorThread::get_num_workers() {
  return _workers->active_workers();
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

  if (Universe::heap()->is_concurrent_gc_active()) {
    log_info(gc,estimator)("Concurrent GC is running.");
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
  LivenessEstimatorThread::set_live_heap_usage(size_bytes);
  LivenessEstimatorThread::set_live_object_count(count);

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
  // We don't want to run the estimate if concurrent gc threads are working.
  return Universe::heap()->is_concurrent_gc_active();
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
  for (uint i = 0; i < ConcLivenessThreads; i++) {
    MarkTaskQueue* queue = _task_queues->queue(i);

    oop obj;
    while (queue->pop_local(obj)) {
      obj->oop_iterate(&cl);
    }
  }

  // Loop over worker counts and sizes to find the total
  size_t all_object_size_words = 0;
  size_t all_object_count = 0;
  for (uint i = 0; i < ConcLivenessThreads; i++) {
    all_object_size_words += _estimated_object_size_words[i];
    all_object_count += _estimated_object_count[i];
  }

  _actual_object_count = all_object_count;
  _actual_object_size_words = all_object_size_words;

  size_t actual_object_size_bytes = _actual_object_size_words * HeapWordSize;
  send_live_set_estimate<EventLiveSetActual>(_actual_object_count, actual_object_size_bytes);

  log_info(gc, estimator)("Actual: " SIZE_FORMAT " objects, total size " SIZE_FORMAT " (" SIZE_FORMAT "%s)",
                          _actual_object_count, actual_object_size_bytes,
                          byte_size_in_proper_unit(actual_object_size_bytes),
                          proper_unit_for_byte_size(actual_object_size_bytes));

  for (uint i = 0; i < ConcLivenessThreads; i++) {
    _estimated_object_size_words[i] = 0;
    _estimated_object_count[i] = 0;
  }

  _mark_bit_map.clear();
}
