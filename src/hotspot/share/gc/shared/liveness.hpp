#ifndef SHARE_GC_SHARED_LIVENESS_HPP
#define SHARE_GC_SHARED_LIVENESS_HPP

#include "gc/shared/concurrentGCThread.hpp"
#include "gc/shared/gc_globals.hpp"
#include "runtime/task.hpp"

class LivenessEstimatorThread : public  ConcurrentGCThread {
 public:
  LivenessEstimatorThread();

  // TODO: For simplicity, this should:
  //  1. Take a safepoint and scan the roots
  //  2. Finish heap walk concurrently computing liveness count
  //  3. Expose results (log them, emit JFR event)
  //
  // TODO: Participate in the SuspendibleThreadSet scheme to
  // avoid interfering with safepoint operations. Would be nice
  // to also not interfere with other concurrent mark activity
  // from GC, but this will require GC specific integrations.
  void run_service() override;
  void stop_service() override;

 private:
  bool estimate_liveness();
  void send_live_set_estimate(size_t live_set_bytes);

  Monitor _lock;
};



#endif //SHARE_GC_SHARED_LIVENESS_HPP
