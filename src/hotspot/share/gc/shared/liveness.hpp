#ifndef SHARE_GC_SHARED_LIVENESS_HPP
#define SHARE_GC_SHARED_LIVENESS_HPP

#include "gc/shared/concurrentGCThread.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/markBitMap.hpp"
#include "gc/shared/taskqueue.hpp"
#include "runtime/task.hpp"
#include "gc/z/zOop.hpp"
#include "gc/z/zAddress.hpp"

typedef GenericTaskQueue<oop, mtTracing>              MarkTaskQueue;
typedef GenericTaskQueueSet<MarkTaskQueue, mtTracing> MarkTaskQueueSet;

class SuspendibleThreadSetJoiner;

class LivenessMarkBitMap : public MarkBitMap {
  public:
    // ZGC uses pointers that hold metadata about the object
    // check_mark calls Heap::is_in which requires an unmasked pointer
    // set_bit requires a masked pointer
    // Because of this we must override this method
    bool par_mark(oop obj) {
      HeapWord* addr = cast_from_oop<HeapWord*>(obj);
      check_mark(addr);

      if (Universe::heap()->kind() == Universe::heap()->Name::Z) {
        obj = ZOop::from_address(ZAddress::offset(ZOop::to_address(obj)));
        addr = cast_from_oop<HeapWord*>(obj);
      }

      return _bm.par_set_bit(addr_to_offset(addr));
    }

    bool par_is_marked(oop obj) const {
      HeapWord* addr = cast_from_oop<HeapWord*>(obj);

      assert(_covered.contains(addr),
            "Address " PTR_FORMAT " is outside underlying space from " PTR_FORMAT " to " PTR_FORMAT,
            p2i(addr), p2i(_covered.start()), p2i(_covered.end()));
      
      return _bm.par_at(addr_to_offset(addr));
    }
};

class EstimationErrorTracker {
 public:
  EstimationErrorTracker() : _upper_error(0), _lower_error(0) {}

  void sample(size_t estimation, size_t actual) {
    if (estimation < actual) {
      size_t diff = actual - estimation;
      if (diff > _lower_error) {
        _lower_error = diff;
      }
    } else if (estimation > actual) {
      size_t diff = estimation - actual;
      if (diff > _upper_error) {
        _upper_error = diff;
      }
    }
  }

  void print_on(outputStream* st) const {
    st->print_cr("[-" SIZE_FORMAT ",+" SIZE_FORMAT "]", _lower_error, _upper_error);
  }
 private:
  size_t _upper_error;
  size_t _lower_error;
};

class LivenessEstimatorThread : public  ConcurrentGCThread {
  friend class VM_LivenessRootScan;
  friend class LivenessOopClosure;
  friend class LivenessConcurrentMarkTask;
  friend class LivenessConcurrentRootMarkTask;


 public:
  LivenessEstimatorThread();

  virtual void run_service() override;
  virtual void stop_service() override;

  virtual const char* type_name() const override;

  static size_t get_live_heap_usage() { return live_heap_usage; }
  static size_t get_live_object_count() { return live_object_count; }
  
 private:
  bool estimation_begin();
  void estimation_end(bool completed);
  void initialize_mark_bit_map();
  void initialize_workers();
  bool commit_bit_map_memory();
  bool uncommit_bit_map_memory();

  bool check_yield_and_continue(SuspendibleThreadSetJoiner* sst);
  bool is_concurrent_gc_active();
  bool estimate_liveness();
  void do_roots();
  void do_oop(oop obj, uint task_num);
  uint get_num_workers();
  template<typename EventT>
  void send_live_set_estimate(size_t count, size_t size_bytes);

  Monitor _lock;
  LivenessMarkBitMap _mark_bit_map;
  MemRegion _mark_bit_map_region;

  WorkerThreads* _workers;
  MarkTaskQueueSet* _task_queues;

  size_t* _estimated_object_count;
  size_t* _estimated_object_size_words;

  // For verification
  void compute_liveness();
  void verify_estimate();
  size_t _actual_object_count;
  size_t _actual_object_size_words;
  EstimationErrorTracker _object_count_error;
  EstimationErrorTracker _object_size_error;

  volatile bool _task_failed;

  static void set_live_heap_usage(size_t usage) { live_heap_usage = usage; }
  static void set_live_object_count(size_t count) { live_object_count = count; }

  static size_t live_heap_usage;
  static size_t live_object_count;
};



#endif //SHARE_GC_SHARED_LIVENESS_HPP
