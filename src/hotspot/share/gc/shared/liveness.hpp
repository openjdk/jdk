#ifndef SHARE_GC_SHARED_LIVENESS_HPP
#define SHARE_GC_SHARED_LIVENESS_HPP

#include "utilities/stack.hpp"
#include "gc/shared/concurrentGCThread.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/markBitMap.hpp"
#include "runtime/task.hpp"

typedef Stack<oop, mtTracing> EstimatorStack;
class SuspendibleThreadSetJoiner;

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
  void send_live_set_estimate(size_t count, size_t size_bytes);

  Monitor _lock;
  MarkBitMap _mark_bit_map;
  MemRegion _mark_bit_map_region;
  EstimatorStack _mark_stack;
  size_t _all_object_count;
  size_t _all_object_size_words;
};



#endif //SHARE_GC_SHARED_LIVENESS_HPP
