#ifndef SHARE_GC_SHARED_LIVENESS_HPP
#define SHARE_GC_SHARED_LIVENESS_HPP

#include "utilities/stack.hpp"
#include "gc/shared/concurrentGCThread.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/markBitMap.hpp"
#include "runtime/task.hpp"
#include "gc/z/zOop.hpp"
#include "gc/z/zAddress.hpp"

typedef Stack<oop, mtTracing> EstimatorStack;
class SuspendibleThreadSetJoiner;

class LivenessMarkBitMap : public MarkBitMap {
  public:
    // ZGC uses pointers that hold metadata about the object
    // check_mark calls Heap::is_in which requires an unmasked pointer
    // set_bit requires a masked pointer
    // Because of this we must override this method
    void mark(oop obj) {
      HeapWord* addr = cast_from_oop<HeapWord*>(obj);
      check_mark(addr);

      if (Universe::heap()->kind() == Universe::heap()->Name::Z) {
        obj = ZOop::from_address(ZAddress::offset(ZOop::to_address(obj)));
        addr = cast_from_oop<HeapWord*>(obj);
      }

      return _bm.set_bit(addr_to_offset(addr));
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
  bool commit_bit_map_memory();
  bool uncommit_bit_map_memory();

  bool check_yield_and_continue(SuspendibleThreadSetJoiner* sst);
  bool is_concurrent_gc_active();
  bool estimate_liveness();
  void do_roots();
  void do_oop(oop obj);
  template<typename EventT>
  void send_live_set_estimate(size_t count, size_t size_bytes);

  Monitor _lock;
  LivenessMarkBitMap _mark_bit_map;
  MemRegion _mark_bit_map_region;
  EstimatorStack _mark_stack;

  size_t _estimated_object_count;
  size_t _estimated_object_size_words;

  // For verification
  void compute_liveness();
  void verify_estimate();
  size_t _actual_object_count;
  size_t _actual_object_size_words;
  EstimationErrorTracker _object_count_error;
  EstimationErrorTracker _object_size_error;

  static void set_live_heap_usage(size_t usage) { live_heap_usage = usage; }
  static void set_live_object_count(size_t count) { live_object_count = count; }

  static size_t live_heap_usage;
  static size_t live_object_count;
};



#endif //SHARE_GC_SHARED_LIVENESS_HPP
