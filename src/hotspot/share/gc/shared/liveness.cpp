#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/liveness.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "jfr/jfrEvents.hpp"
#include "logging/log.hpp"

LivenessEstimatorThread::LivenessEstimatorThread()
  : ConcurrentGCThread()
  , _lock(Mutex::safepoint - 1, "LivenessEstimator_lock", true)
{
  // create the os thread using default priority
  create_and_start();
}

void LivenessEstimatorThread::run_service() {
  while (!should_terminate()) {
    // Start with a wait because there is nothing interesting in the heap yet.
    MonitorLocker locker(&_lock,Monitor::SafepointCheckFlag::_no_safepoint_check_flag);
    bool timeout = locker.wait(ConcLivenessEstimateSeconds * MILLIUNITS);
    log_info(gc)("Wokeup, timeout: %s", BOOL_TO_STR(timeout));

    if (!is_concurrent_gc_active()) {
      bool completed = estimate_liveness();
      log_info(gc)("Estimation completed: %s", BOOL_TO_STR(completed));
    }
  }
}

void LivenessEstimatorThread::stop_service() {
  // We could have a long timeout on the wait before the estimator thread wakes up
  MonitorLocker locker(&_lock);
  _lock.notify();
  log_info(gc)("Notified estimator thread to wakeup.");
}

void LivenessEstimatorThread::send_live_set_estimate(size_t live_set_bytes) {
  EventLiveSetEstimate evt;
  if (evt.should_commit()) {
    evt.set_estimatedLiveSetSize(live_set_bytes);
    evt.commit();
  }
}

bool LivenessEstimatorThread::estimate_liveness() {
  // Simulate waiting for our vm operation to scan the roots to complete
  os::naked_sleep(10);

  SuspendibleThreadSetJoiner sst;
  // Simulate a concurrent heap walk with checks to see if we need to let
  // another vm operation run. We may want to abandon or resume the estimation
  // effort if we can determine whether a concurrent gc effort is underway.
  for (int i = 0; i < 1000; ++i) {
    if (sst.should_yield()) {

      // Shenandoah may not update this, other collectors seem to.
      unsigned int collections = Universe::heap()->total_collections();

      // This blocks the caller until the vm operation has executed.
      SuspendibleThreadSet::yield();

      if (collections != Universe::heap()->total_collections()) {
        // Heap has been collected, pointer in the mark queues may be invalid.
        return false;
      }
    }
    os::naked_sleep(10);
  }

  return true;
}

bool LivenessEstimatorThread::is_concurrent_gc_active() {
  // TODO: Implement this. Maybe add a virtual function to CollectedHeap?
  // We don't want to run the estimate if concurrent gc threads are working.
  return false;
}

const char* LivenessEstimatorThread::type_name() const {
  return "LivenessEstimator";
}
