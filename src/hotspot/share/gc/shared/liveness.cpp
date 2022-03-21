#include "gc/shared/liveness.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
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
    MonitorLocker locker(&_lock);
    bool timeout = _lock.wait(ConcLivenessEstimateSeconds * MILLIUNITS);
    log_info(gc)("Wokeup, timeout: %s", BOOL_TO_STR(timeout));

    bool completed = estimate_liveness();
    log_info(gc)("Estimation completed: %s", BOOL_TO_STR(completed));
  }
}

void LivenessEstimatorThread::stop_service() {
  // We could have a long timeout on the wait before the estimator thread wakes up
  MonitorLocker locker(&_lock);
  _lock.notify();
  log_info(gc)("Notified estimator thread to wakeup.");
}

bool LivenessEstimatorThread::estimate_liveness() {
  // Simulate waiting for our vm operation to scan the roots to complete
  os::naked_sleep(10);

  // TODO: We _might_ able to use CollectedHeap::object_iterate here, but it's
  // not clear if all implementations will let it run outside of a safepoint. At
  // any rate, the implementations I inspected do not participate in any cancellation
  // scheme.

  SuspendibleThreadSetJoiner sst;
  // Simulate a concurrent heap walk with checks to see if we need to let
  // another vm operation run. We may want to abandon or resume the estimation
  // effort if we can determine whether a concurrent gc effort is underway.
  for (int i = 0; i < 1000; ++i) {
    if (sst.should_yield()) {
      SuspendibleThreadSet::yield();
      return false;
    }
    os::naked_sleep(10);
  }

  return true;
}
