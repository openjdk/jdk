/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "precompiled.hpp"
#include "jfr/recorder/jfrEventSetting.inline.hpp"
#include "jfr/recorder/service/jfrEventThrottler.hpp"
#include "jfr/utilities/jfrAllocation.hpp"
#include "runtime/semaphore.inline.hpp"

class JfrEventThrottlerSettingsLocker : public StackObj {
 private:
  Semaphore* const _semaphore;
 public:
  JfrEventThrottlerSettingsLocker(JfrEventThrottler* throttler) : _semaphore(throttler->_semaphore) {
    assert(_semaphore != NULL, "invariant");
    _semaphore->wait();
  }
  ~JfrEventThrottlerSettingsLocker() {
    _semaphore->signal();
  }
};

constexpr static const intptr_t event_throttler_disabled = -2;
constexpr static const JfrSamplerParams _disabled_params = {
                                                             0, // rate
                                                             0, // window duration
                                                             0, // window lookback count
                                                             false // reconfigure
                                                           };

JfrEventThrottler::JfrEventThrottler(JfrEventId event_id) :
  JfrAdaptiveSampler(),
  _last_params(),
  _semaphore(NULL),
  _rate_per_second(0),
  _event_id(event_id),
  _disabled(false),
  _update(false) {}

bool JfrEventThrottler::initialize() {
  _semaphore = new Semaphore(1);
  return _semaphore != NULL && JfrAdaptiveSampler::initialize();
}

JfrEventThrottler::~JfrEventThrottler() {
  delete _semaphore;
}

/*
 * The event throttler currently only supports a single configuration option:
 * 
 * - rate per second  throttle dynamically to maintain a continuous, maximal rate per second
 *
 * Multiple options may be added in the future.
 */
void JfrEventThrottler::configure(intptr_t rate_per_second) {
  JfrEventThrottlerSettingsLocker sl(this);
  _rate_per_second = rate_per_second;
  _update = true;
}

// There is currently only one throttler instance, for JfrObjectAllocationSampleEvent.
// When there is a need for more, we will introduce a map indexed by the event id.
static JfrEventThrottler* _throttler = NULL;

bool JfrEventThrottler::create() {
  assert(_throttler == NULL, "invariant");
  _throttler = new JfrEventThrottler(JfrObjectAllocationSampleEvent);
  return _throttler != NULL && _throttler->initialize();
}

void JfrEventThrottler::destroy() {
  delete _throttler;
  _throttler = NULL;
}

JfrEventThrottler* JfrEventThrottler::for_event(JfrEventId event_id) {
  assert(_throttler != NULL, "JfrEventThrottler has not been properly initialized");
  assert(event_id == JfrObjectAllocationSampleEvent, "invariant");
  return _throttler;
}

bool JfrEventThrottler::accept(JfrEventId event_id, int64_t timestamp) {
  JfrEventThrottler* const throttler = for_event(event_id);
  assert(throttler != NULL, "invariant");
  return throttler->_disabled ? true : throttler->sample(timestamp);
}

/*
 * Rates lower than or equal to the 'low rate upper bound', are considered special.
 * They will use a window of duration one second, because the rates are so low they
 * do not justify the overhead of more frequent window rotations.
 */
constexpr static const intptr_t low_rate_upper_bound = 9;
constexpr static const size_t default_window_duration_ms = 200;   // 5 windows per second

/*
 * Breaks down an overall rate per second to a number of sample points per window.
 */
inline void set_sample_points_per_window(JfrSamplerParams& params, intptr_t rate_per_second) {
  assert(rate_per_second != event_throttler_disabled, "invariant");
  assert(rate_per_second >= 0, "invariant");
  if (rate_per_second <= low_rate_upper_bound) {
    params.sample_points_per_window = static_cast<size_t>(rate_per_second);
  }
  // Window duration is in milliseconds and the rate_is in sample points per second.
  const double rate_per_ms = static_cast<double>(rate_per_second) / static_cast<double>(MILLIUNITS);
  params.sample_points_per_window = rate_per_ms * default_window_duration_ms;
}

/*
 * The window_lookback_count defines the history in number of windows to take into account
 * when the JfrAdaptiveSampler engine is calcualting an expected weigthed moving average (EWMA).
 * It only applies to contexts where a rate is specified. Technically, it determines the alpha
 * coefficient in an EMWA formula.
 */
constexpr static const size_t default_window_lookback_count = 25; // 25 windows == 5 seconds (for default window duration of 200 ms)

inline void set_window_duration(JfrSamplerParams& params) {
  if (params.sample_points_per_window <= low_rate_upper_bound) {
    // For low rates.
    params.window_duration_ms = MILLIUNITS; // 1 second
    params.window_lookback_count = 5; // 5 windows == 5 seconds
    return;
  }
  params.window_duration_ms = default_window_duration_ms;
  params.window_lookback_count = default_window_lookback_count; // 5 seconds
}

inline bool is_disabled(intptr_t rate_per_second) {
  return rate_per_second == event_throttler_disabled;
}

const JfrSamplerParams& JfrEventThrottler::update_params(const JfrSamplerWindow* expired) {
  JfrEventThrottlerSettingsLocker sl(this);
  _disabled = is_disabled(_rate_per_second);
  if (_disabled) {
    return _disabled_params;
  }
  set_sample_points_per_window(_last_params, _rate_per_second);
  set_window_duration(_last_params);
  _last_params.reconfigure = true;
  _update = false;
  return _last_params;
}

/*
 * This is the feedback control loop when using the JfrAdaptiveSampler engine.
 *
 * The engine calls this when a sampler window has expired, providing the
 * client with an opportunity to perform some analysis. To reciprocate, the client
 * returns a set of parameters, possibly updated, for the engine to apply to the next window.
 *
 * Try to keep relatively quick, since the engine is currently inside a critical section,
 * in the process of rotating windows.
 */
const JfrSamplerParams& JfrEventThrottler::next_window_params(const JfrSamplerWindow* expired) {
  assert(expired != NULL, "invariant");
  if (_update) {
    return update_params(expired); // Updates _last_params in-place.
  }
  return _disabled ? _disabled_params : _last_params;
}
