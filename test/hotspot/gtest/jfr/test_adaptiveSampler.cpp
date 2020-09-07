#include "precompiled.hpp"
// This test performs mocking of certain JVM functionality. This works by
// including the source file under test inside an anonymous namespace (which
// prevents linking conflicts) with the mocked symbols redefined.

// The include list should mirror the one found in the included source file -
// with the ones that should pick up the mocks removed. Those should be included
// later after the mocks have been defined.
#include <cmath>
#include <random>

#include "jfr/utilities/jfrTime.hpp"
#include "jfr/utilities/jfrTimeConverter.hpp"
#include "runtime/atomic.hpp"
#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/samplerSupport.hpp"
#include "utilities/globalDefinitions.hpp"

#include "unittest.hpp"

// #undef SHARE_JFR_SUPPORT_JFRADAPTIVESAMPLER_HPP

namespace {
  class MockJfrTime : public ::JfrTime {
   public:
    static jlong tick;
    static bool is_ft_enabled() {
      return false;
    }
    static bool is_ft_supported() {
      return false;
    }
    static void initialize() {}
    static jlong frequency() {
      return 1000000000;
    }
    static const void* time_function() {
      return (const void*)MockJfrTime::current_tick;
    }

    static jlong current_tick() {
      return tick;
    }
  };

  class MockJfrTimeConverter : public ::JfrTimeConverter {
   public:
    static double nano_to_counter_multiplier(bool is_os_time = false) {
      return 1.0;
    }
    static jlong counter_to_nanos(jlong c, bool is_os_time = false) {
      return c;
    }
    static jlong counter_to_millis(jlong c, bool is_os_time = false) {
      return c * NANOS_PER_MILLISEC;
    }
    static jlong nanos_to_countertime(jlong c, bool as_os_time = false) {
      return c;
    }
  };

  jlong MockJfrTime::tick = 0;

// Reincluding source files in the anonymous namespace unfortunately seems to
// behave strangely with precompiled headers (only when using gcc though)
#ifndef DONT_USE_PRECOMPILED_HEADER
#define DONT_USE_PRECOMPILED_HEADER
#endif

#define JfrTime MockJfrTime
#define JfrTimeConverter MockJfrTimeConverter

#include "jfr/support/jfrAdaptiveSampler.hpp"
#include "jfr/support/jfrAdaptiveSampler.cpp"

#undef JfrTimeConverter
#undef JfrTime
} // anonymous namespace

class AdaptiveSampling : public ::testing::Test {
protected:
  const int max_events_per_window = 2000;
  const int min_events_per_window = 2;
  const int window_count = 10000;
  const clock_t window_duration_ms = 100;
  const size_t expected_hits_per_window = 50;
  const size_t expected_hits = expected_hits_per_window * (size_t)window_count;
  const double max_sample_bias = 0.11;

  void SetUp() {
    // Ensure that tests are separated in time by spreading them by 24hrs apart
    MockJfrTime::tick += (24 * 60 * 60) * NANOSECS_PER_SEC;
  }

  void TearDown() {
    // nothing
  }

  void assertDistributionProperties(int distr_slots, jlong* events, jlong* hits, size_t all_events, size_t all_hits, const char* msg) {
    size_t events_sum = 0;
    size_t hits_sum = 0;
    for (int i = 0; i < distr_slots; i++) {
      events_sum += i * events[i];
      hits_sum += i * hits[i];
    }

    double events_mean = events_sum / (double)all_events;
    double hits_mean = hits_sum / (double)all_hits;

    double events_variance = 0;
    double hits_variance = 0;
    for (int i = 0; i < distr_slots; i++) {
      double events_diff = i - events_mean;
      events_variance = events[i] * events_diff * events_diff;

      double hits_diff = i - hits_mean;
      hits_variance = hits[i] * hits_diff * hits_diff;
    }
    events_variance = events_variance / (all_events - 1);
    hits_variance = hits_variance / (all_hits - 1);
    double events_stdev = sqrt(events_variance);
    double hits_stdev = sqrt(hits_variance);

    // fprintf(stdout, "=== em: %f, hm: %f, ev: %f, estdev: %f, hstdev: %f\n", events_mean, hits_mean, events_variance, events_stdev, hits_stdev);

    // make sure the standard deviation is ok
    EXPECT_NEAR(events_stdev, hits_stdev, 0.1) << msg;
    // make sure that the subsampled set mean is within 2-sigma of the original set mean
    EXPECT_NEAR(events_mean, hits_mean, events_stdev) <<  msg;
    // make sure that the original set mean is within 2-sigma of the subsampled set mean
    EXPECT_NEAR(hits_mean, events_mean, hits_stdev) <<  msg;
  }
};

TEST_VM_F(AdaptiveSampling, uniform_rate) {
  fprintf(stdout, "=== uniform\n");
  jlong events[100] = {0};
  jlong hits[100] = {0};
  ::AdaptiveSampler* sampler = new ::FixedRateSampler(window_duration_ms, expected_hits_per_window, 60, 160);

  size_t all_events = 0;
  size_t all_hits = 0;
  for (int t = 0; t < window_count; t++) {
    size_t counter = 0;
    int incoming_events = os::random() % max_events_per_window + min_events_per_window;
    for (int i = 0; i < incoming_events; i++) {
      all_events++;
      int hit_index = os::random() % 100;
      events[hit_index] = events[hit_index] + 1;
      if (sampler->should_sample()) {
        counter++;
        hits[hit_index] = hits[hit_index] + 1;
      }
    }
    all_hits += counter;
    MockJfrTime::tick += window_duration_ms * NANOSECS_PER_MILLISEC + 1;
  }
  delete sampler;
  EXPECT_NEAR(expected_hits, all_hits, expected_hits * 0.25) << "Adaptive sampler: random uniform, all samples";

  assertDistributionProperties(100, events, hits, all_events, all_hits, "Adaptive sampler: random uniform, hit distribution");
}

TEST_VM_F(AdaptiveSampling, bursty_rate_10p) {
  fprintf(stdout, "=== bursty 10\n");
  jlong events[100] = {0};
  jlong hits[100] = {0};
  ::AdaptiveSampler* sampler = new ::FixedRateSampler(window_duration_ms, expected_hits_per_window, 60, 160);

  size_t all_events = 0;
  size_t all_hits = 0;
  for (int t = 0; t < window_count; t++) {
    size_t counter = 0;
    bool is_burst = (os::random() % 100) < 10; // 10% burst chance
    int incoming_events = is_burst ? max_events_per_window : min_events_per_window;
    for (int i = 0; i < incoming_events; i++) {
      all_events++;
      int hit_index = os::random() % 100;
      events[hit_index] = events[hit_index] + 1;
      if (sampler->should_sample()) {
        counter++;
        hits[hit_index] = hits[hit_index] + 1;
      }
    }
    all_hits += counter;
    MockJfrTime::tick += window_duration_ms * NANOSECS_PER_MILLISEC + 1;
  }
  delete sampler;
  EXPECT_NEAR(expected_hits, all_hits, expected_hits * 0.25) << "Adaptive sampler: bursty 10%";

  assertDistributionProperties(100, events, hits, all_events, all_hits, "Adaptive sampler: bursty 10%, hit distribution");
}

TEST_VM_F(AdaptiveSampling, bursty_rate_90p) {
  fprintf(stdout, "=== bursty 90\n");
  jlong events[100] = {0};
  jlong hits[100] = {0};
  ::AdaptiveSampler* sampler = new ::FixedRateSampler(window_duration_ms, expected_hits_per_window, 60, 160);

  size_t all_events = 0;
  size_t all_hits = 0;
  for (int t = 0; t < window_count; t++) {
    size_t counter = 0;
    bool is_burst = (os::random() % 100) < 90; // 90% burst chance
    int incoming_events = is_burst ? max_events_per_window : min_events_per_window;
    for (int i = 0; i < incoming_events; i++) {
      all_events++;
      int hit_index = os::random() % 100;
      events[hit_index] = events[hit_index] + 1;
      if (sampler->should_sample()) {
        counter++;
        hits[hit_index] = hits[hit_index] + 1;
      }
    }
    all_hits += counter;
    MockJfrTime::tick += window_duration_ms * NANOSECS_PER_MILLISEC + 1;
  }
  delete sampler;
  EXPECT_NEAR(expected_hits, all_hits, expected_hits * max_sample_bias) << "Adaptive sampler: bursty 90%";

  assertDistributionProperties(100, events, hits, all_events, all_hits, "Adaptive sampler: bursty 90%, hit distribution");
}

TEST_VM_F(AdaptiveSampling, low_rate) {
  fprintf(stdout, "=== low\n");
  jlong events[100] = {0};
  jlong hits[100] = {0};
  ::AdaptiveSampler* sampler = new ::FixedRateSampler(window_duration_ms, expected_hits_per_window, 60, 160);

  size_t all_events = 0;
  size_t all_hits = 0;
  for (int t = 0; t < window_count; t++) {
    size_t counter = 0;
    for (int i = 0; i < min_events_per_window; i++) {
      all_events++;
      int hit_index = os::random() % 100;
      events[hit_index] = events[hit_index] + 1;
      if (sampler->should_sample()) {
        counter++;
        hits[hit_index] = hits[hit_index] + 1;
      }
    }
    all_hits += counter;
    MockJfrTime::tick += window_duration_ms * NANOSECS_PER_MILLISEC + 1;
  }
  delete sampler;
  size_t target_samples = min_events_per_window * window_count;
  EXPECT_NEAR(target_samples, all_hits, expected_hits * 0.01) << "Adaptive sampler: below target";

  assertDistributionProperties(100, events, hits, all_events, all_hits, "Adaptive sampler: below target, hit distribution");
}

TEST_VM_F(AdaptiveSampling, high_rate) {
  fprintf(stdout, "=== high\n");
  jlong events[100] = {0};
  jlong hits[100] = {0};
  ::AdaptiveSampler* sampler = new ::FixedRateSampler(window_duration_ms, expected_hits_per_window, 60, 160);

  size_t all_events = 0;
  size_t all_hits = 0;
  for (int t = 0; t < window_count; t++) {
    size_t counter = 0;
    for (int i = 0; i < max_events_per_window; i++) {
      all_events++;
      int hit_index = os::random() % 100;
      events[hit_index] = events[hit_index] + 1;
      if (sampler->should_sample()) {
        counter++;
        hits[hit_index] = hits[hit_index] + 1;
      }
    }
    all_hits += counter;
    MockJfrTime::tick += window_duration_ms * NANOSECS_PER_MILLISEC + 1;
  }
  delete sampler;
  EXPECT_NEAR(expected_hits, all_hits, expected_hits * 0.05) << "Adaptive sampler: above target";

  assertDistributionProperties(100, events, hits, all_events, all_hits, "Adaptive sampler: above target, hit distribution");
}
