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
#include "jfr/jfrEvents.hpp"
#include "jfr/support/jfrAdaptiveSampler.hpp"
#include "jfr/utilities/jfrRandom.inline.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/utilities/jfrTimeConverter.hpp"
#include "jfr/utilities/jfrTryLock.hpp"
#include "runtime/atomic.hpp"
#include "utilities/globalDefinitions.hpp"
#include <cmath>

JfrSamplerWindow::JfrSamplerWindow() :
  _params(),
  _end_ticks(0),
  _sampling_interval(1),
  _projected_population_size(0),
  _measured_population_size(0) {}

JfrAdaptiveSampler::JfrAdaptiveSampler() :
  _prng(this),
  _window_0(NULL),
  _window_1(NULL),
  _active_window(NULL),
  _avg_population_size(0),
  _ewma_population_size_alpha(0),
  _acc_debt_carry_limit(0),
  _acc_debt_carry_count(0),
  _lock(0) {}

JfrAdaptiveSampler::~JfrAdaptiveSampler() {
  delete _window_0;
  delete _window_1;
}

bool JfrAdaptiveSampler::initialize() {
  assert(_window_0 == NULL, "invariant");
  _window_0 = new JfrSamplerWindow();
  if (_window_0 == NULL) {
    return false;
  }
  assert(_window_1 == NULL, "invariant");
  _window_1 = new JfrSamplerWindow();
  if (_window_1 == NULL) {
    return false;
  }
  _active_window = _window_0;
  return true;
}

/*
 * The entry point to the sampler.
 */
bool JfrAdaptiveSampler::sample(int64_t timestamp) {
  bool expired_window;
  const bool result = active_window()->sample(timestamp, &expired_window);
  if (expired_window) {
    JfrTryLock mutex(&_lock);
    if (mutex.acquired()) {
      rotate_window(timestamp);
    }
  }
  return result;
}

inline const JfrSamplerWindow* JfrAdaptiveSampler::active_window() const {
  return Atomic::load_acquire(&_active_window);
}

inline int64_t now() {
  return JfrTicks::now().value();
}

inline bool JfrSamplerWindow::is_expired(int64_t timestamp) const {
  const int64_t end_ticks = Atomic::load(&_end_ticks);
  return timestamp == 0 ? now() >= end_ticks : timestamp >= end_ticks;
}

bool JfrSamplerWindow::sample(int64_t timestamp, bool* expired_window) const {
  assert(expired_window != NULL, "invariant");
  *expired_window = is_expired(timestamp);
  return *expired_window ? false : sample();
}

inline bool JfrSamplerWindow::sample() const {
  const size_t ordinal = Atomic::add(&_measured_population_size, static_cast<size_t>(1));
  return ordinal <= _projected_population_size && ordinal % _sampling_interval == 0;
}

// Called exclusively by the holder of the lock when a window is determined to have expired.
void JfrAdaptiveSampler::rotate_window(int64_t timestamp) {
  assert(_lock, "invariant");
  EventSamplerWindow event;
  const JfrSamplerWindow* const current = active_window();
  assert(current != NULL, "invariant");
  if (!current->is_expired(timestamp)) {
    // Someone took care of it.
    return;
  }
  fill(event, current);
  rotate(current);
  event.commit();
}

void JfrAdaptiveSampler::fill(EventSamplerWindow& event, const JfrSamplerWindow* expired) {
  assert(expired == active_window(), "invariant");
  const JfrSamplerParams& params = expired->params();
  event.set_setPoint(params.sample_points_per_window);
  event.set_windowDuration(params.window_duration_ms);
  const size_t sample_size = expired->sample_size();
  event.set_sampleSize(sample_size);
  event.set_sampleSizeRaw(expired->sample_size());
  const size_t population_size = expired->population_size();
  event.set_populationSize(population_size);
  event.set_ratio(population_size == 0 ? 0 : static_cast<double>(sample_size) / static_cast<double>(population_size));
  event.set_debt(expired->debt());
  event.set_accumulatedDebt(expired->accumulated_debt());
  event.set_lookbackCount(1 / _ewma_population_size_alpha);
}

// Call next_window_param() to report the expired window and to retreive params for the next window.
void JfrAdaptiveSampler::rotate(const JfrSamplerWindow* expired) {
  assert(expired == active_window(), "invariant");
  install(configure(next_window_params(expired), expired));
}

inline void JfrAdaptiveSampler::install(const JfrSamplerWindow* next) {
  assert(next != active_window(), "invariant");
  Atomic::release_store(&_active_window, next);
}

const JfrSamplerWindow* JfrAdaptiveSampler::configure(const JfrSamplerParams& params, const JfrSamplerWindow* expired) {
  assert(_lock, "invariant");
  if (params.reconfigure) {
    reconfigure_sampler(params, expired);
  }
  JfrSamplerWindow* const next = set_rate(params, expired);
  next->initialize(params);
  return next;
}

inline double compute_ewma_alpha_coefficient(size_t lookback_count) {
  return lookback_count <= 1 ? 1 : static_cast<double>(1) / static_cast<double>(lookback_count);
}

inline size_t compute_accumulated_debt_carry_limit(const JfrSamplerParams& params) {
  if (params.window_duration_ms == 0 || params.window_duration_ms >= MILLIUNITS) {
    return 1;
  }
  return MILLIUNITS / params.window_duration_ms;
}

void JfrAdaptiveSampler::reconfigure_sampler(const JfrSamplerParams& params, const JfrSamplerWindow* expired) {
  assert(params.reconfigure, "invariant");
  // Store updated params to both windows.
  const_cast<JfrSamplerWindow*>(expired)->_params = params;
  next_window(expired)->_params = params;
  _avg_population_size = 0;
  _ewma_population_size_alpha = compute_ewma_alpha_coefficient(params.window_lookback_count);
  _acc_debt_carry_limit = compute_accumulated_debt_carry_limit(params);
  _acc_debt_carry_count = _acc_debt_carry_limit;
  params.reconfigure = false;
}

inline int64_t millis_to_countertime(int64_t millis) {
  return JfrTimeConverter::nanos_to_countertime(millis * NANOSECS_PER_MILLISEC);
}

void JfrSamplerWindow::initialize(const JfrSamplerParams& params) {
  assert(_sampling_interval >= 1, "invariant");
  if (params.window_duration_ms == 0) {
    Atomic::store(&_end_ticks, static_cast<int64_t>(0));
    return;
  }
  Atomic::store(&_measured_population_size, static_cast<size_t>(0));
  const int64_t end_ticks = now() + millis_to_countertime(params.window_duration_ms);
  Atomic::store(&_end_ticks, end_ticks);
}

/*
 * Based on what it has learned from the past, the sampler creates a future 'projection',
 * a speculation, or model, of what the situation will be like during the next window.
 * This projection / model is used to derive values for the parameters, estimated for
 * collecting a sample set that, should the model hold, is as close as possible to the target,
 * i.e. the set point, which is a function of the number of sample_points_per_window + amortization.
 * The model is a geometric distribution over the number of trials / selections required until success.
 * For each window, the sampling interval is a random variable from this geometric distribution.
 */
JfrSamplerWindow* JfrAdaptiveSampler::set_rate(const JfrSamplerParams& params, const JfrSamplerWindow* expired) {
  JfrSamplerWindow* const next = next_window(expired);
  assert(next != expired, "invariant");
  const size_t sample_size = projected_sample_size(params, expired);
  if (sample_size == 0) {
    next->_projected_population_size = 0;
    return next;
  }
  next->_sampling_interval = sampling_interval(sample_size, expired);
  assert(next->_sampling_interval >= 1, "invariant");
  next->_projected_population_size = sample_size * next->_sampling_interval;
  return next;
}

inline JfrSamplerWindow* JfrAdaptiveSampler::next_window(const JfrSamplerWindow* expired) const {
  assert(expired != NULL, "invariant");
  return expired == _window_0 ? _window_1 : _window_0;
}

size_t JfrAdaptiveSampler::projected_sample_size(const JfrSamplerParams& params, const JfrSamplerWindow* expired) {
  return params.sample_points_per_window + amortization(expired);
}

/*
 * When the sampler is configured to maintain a rate, is employs the concepts
 * of 'debt' and 'accumulated debt'. 'Accumulated debt' can be thought of as
 * a cumulative error term, and is indicative for how much the sampler is
 * deviating from a set point, i.e. the ideal target rate. Debt accumulates naturally
 * as a function of undersampled windows, caused by system fluctuations,
 * i.e. too small populations.
 *
 * A specified rate is implicitly a _maximal_ rate, so the sampler must ensure
 * to respect this 'limit'. Rates are normalized as per-second ratios, hence the
 * limit to respect is on a per second basis. During this second, the sampler
 * has freedom to dynamically re-adjust, and it does so by 'amortizing'
 * accumulated debt over a certain number of windows that fall within the second.
 *
 * Intuitively, accumulated debt 'carry over' from the predecessor to the successor
 * window if within the allowable time frame (determined in # of 'windows' given by
 * _acc_debt_carry_limit). The successor window will sample more points to make amends,
 * or 'amortize' debt accumulated by its predecessor(s).
 */
size_t JfrAdaptiveSampler::amortization(const JfrSamplerWindow* expired) {
  assert(expired != NULL, "invariant");
  const intptr_t accumulated_debt = expired->accumulated_debt();
  assert(accumulated_debt <= 0, "invariant");
  return -accumulated_debt; // negation
  /*
  if (_acc_debt_carry_count == _acc_debt_carry_limit) {
    _acc_debt_carry_count = 1;
    return 0;
  }
  ++_acc_debt_carry_count;
  return -expired->accumulated_debt(); // negation
  */
}

inline size_t JfrSamplerWindow::max_sample_size() const {
  return _projected_population_size / _sampling_interval;
}

// The sample size is derived from the measured population size.
size_t JfrSamplerWindow::sample_size() const {
  const size_t size = population_size();
  return size > _projected_population_size ? max_sample_size() : size / _sampling_interval;
}

size_t JfrSamplerWindow::population_size() const {
  return Atomic::load(&_measured_population_size);
}

intptr_t JfrSamplerWindow::accumulated_debt() const {
  return static_cast<intptr_t>(_params.sample_points_per_window - max_sample_size()) + debt();
}

intptr_t JfrSamplerWindow::debt() const {
  return static_cast<intptr_t>(sample_size() - _params.sample_points_per_window);
}

/*
 * Exponentially Weighted Moving Average (EWMA):
 *
 * Y is a datapoint (at time t)
 * S is the current EMWA (at time t-1)
 * alpha represents the degree of weighting decrease, a constant smoothing factor between 0 and 1.
 *
 * A higher alpha discounts older observations faster.
 * Returns the new EWMA for S
*/
/*

inline double exponentially_weighted_moving_average(double Y, double alpha, double S) {
  return alpha * Y + (1 - alpha) * S;
}

inline static double next_window_population_size(const JfrSamplerWindow* expired, double alpha, double avg_population_size) {
  assert(expired != NULL, "invariant");
  return exponentially_weighted_moving_average(expired->population_size(), alpha, avg_population_size);
}
*/

/*
 * Inverse transform sampling from a uniform to a geometric distribution.
 *
 * PMF: f(x)  = P(X=x) = ((1-p)^x-1)p
 *
 * CDF: F(x)  = P(X<=x) = 1 - (1-p)^x
 *
 * Inv
 * CDF: F'(u) = ceil(ln(1-u) / ln (1-p)) // u = random uniform, 0.0 < u < 1.0
 *
 */
inline size_t next_geometric(double p, double u) {
  assert(u >= 0.0, "invariant");
  assert(u <= 1.0, "invariant");
  if (u == 0.0) {
    u = 0.01;
  } else if (u == 1.0) {
    u = 0.99;
  }
  // Inverse CDF for the geometric distribution.
  return ceil(log(1.0 - u) / log(1.0 - p));
}

size_t JfrAdaptiveSampler::sampling_interval(size_t sample_size, const JfrSamplerWindow* expired) const {
  assert(sample_size > 0, "invariant");
  assert(expired != NULL, "invariant");
  if (expired->population_size() <= sample_size) {
    return 1;
  }
  assert(expired->population_size() > 0, "invariant");
  const double projected_probability = static_cast<double>(sample_size) / static_cast<double>(expired->population_size());
  return next_geometric(projected_probability, _prng.next_uniform());
}
