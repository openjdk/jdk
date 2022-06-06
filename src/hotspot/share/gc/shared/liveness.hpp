#ifndef SHARE_GC_SHARED_LIVENESS_HPP
#define SHARE_GC_SHARED_LIVENESS_HPP

#include "utilities/stack.hpp"
#include "gc/shared/concurrentGCThread.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/markBitMap.hpp"
#include "runtime/task.hpp"

typedef Stack<oop, mtTracing> EstimatorStack;
class SuspendibleThreadSetJoiner;

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

  // TODO: For simplicity, this should:
  //  1. Take a safepoint and scan the roots
  //  2. Finish heap walk concurrently computing liveness count
  //  3. Expose results (log them, emit JFR event)
  //
  // TODO: Would be nice to also not interfere with other concurrent mark activity
  // from GC, but this will require GC specific integrations.
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
  MarkBitMap _mark_bit_map;
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
