
#include "precompiled.hpp"

// This test performs mocking of certain JVM functionality. This works by
// including the source file under test inside an anonymous namespace (which
// prevents linking conflicts) with the mocked symbols redefined.

// The include list should mirror the one found in the included source file -
// with the ones that should pick up the mocks removed. Those should be included
// later after the mocks have been defined.

#include <cmath>

// #include "jfr/jfrEvents.hpp"
#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrRandom.inline.hpp"
#include "jfr/utilities/jfrSpinlockHelper.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/utilities/jfrTimeConverter.hpp"
#include "jfr/utilities/jfrTryLock.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "utilities/globalDefinitions.hpp"

#include "unittest.hpp"

// #undef SHARE_JFR_SUPPORT_JFRADAPTIVESAMPLER_HPP

namespace {
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

  class MockJfrTickValue {
  private:
    jlong _ticks;
  public:
    MockJfrTickValue(jlong ticks) : _ticks(ticks) {};
    jlong value() {
      return _ticks;
    }
  };
  class MockJfrTicks {
  public:
    static jlong tick;
    static MockJfrTickValue now() {
      return MockJfrTickValue(tick);
    }
  };

  jlong MockJfrTicks::tick = 0;

  // Reincluding source files in the anonymous namespace unfortunately seems to
  // behave strangely with precompiled headers (only when using gcc though)
#ifndef DONT_USE_PRECOMPILED_HEADER
#define DONT_USE_PRECOMPILED_HEADER
#endif

#define JfrTicks MockJfrTicks
#define JfrTimeConverter MockJfrTimeConverter

#include "jfr/support/jfrAdaptiveSampler.hpp"
#include "jfr/support/jfrAdaptiveSampler.cpp"

#undef JfrTimeConverter
#undef JfrTicks
} // anonymous namespace

class JfrGTestAdaptiveSampling : public ::testing::Test {
 protected:
  const int max_population_per_window = 2000;
  const int min_population_per_window = 2;
  const int window_count = 10000;
  const clock_t window_duration_ms = 100;
  const size_t expected_sample_points_per_window = 50;
  const size_t expected_sample_points = expected_sample_points_per_window * (size_t)window_count;
  const size_t window_lookback_count = 50; // 50 windows == 5 seconds (for a window duration of 100 ms)
  const double max_sample_bias = 0.11;

  void SetUp() {
    // Ensure that tests are separated in time by spreading them by 24hrs apart
    MockJfrTicks::tick += (24 * 60 * 60) * NANOSECS_PER_SEC;
  }

  void TearDown() {
    // nothing
  }

  void assertDistributionProperties(int distr_slots, jlong* population, jlong* sample, size_t population_size, size_t sample_size, const char* msg) {
    size_t population_sum = 0;
    size_t sample_sum = 0;
    for (int i = 0; i < distr_slots; i++) {
      population_sum += i * population[i];
      sample_sum += i * sample[i];
    }

    double population_mean = population_sum / (double)population_size;
    double sample_mean = sample_sum / (double)sample_size;

    double population_variance = 0;
    double sample_variance = 0;
    for (int i = 0; i < distr_slots; i++) {
      double population_diff = i - population_mean;
      population_variance = population[i] * population_diff * population_diff;

      double sample_diff = i - sample_mean;
      sample_variance = sample[i] * sample_diff * sample_diff;
    }
    population_variance = population_variance / (population_size - 1);
    sample_variance = sample_variance / (sample_size - 1);
    double population_stdev = sqrt(population_variance);
    double sample_stdev = sqrt(sample_variance);

    // make sure the standard deviation is ok
    EXPECT_NEAR(population_stdev, sample_stdev, 0.5) << msg;
    // make sure that the subsampled set mean is within 2-sigma of the original set mean
    EXPECT_NEAR(population_mean, sample_mean, population_stdev) << msg;
    // make sure that the original set mean is within 2-sigma of the subsampled set mean
    EXPECT_NEAR(sample_mean, population_mean, sample_stdev) << msg;
  }

  typedef size_t(JfrGTestAdaptiveSampling::* incoming)() const;
  void test(incoming inc, size_t events_per_window, double expectation, const char* description);

 public:
  size_t incoming_uniform() const {
    return os::random() % max_population_per_window + min_population_per_window;
  }

  size_t incoming_bursty_10_percent() const {
    bool is_burst = (os::random() % 100) < 10; // 10% burst chance
    return is_burst ? max_population_per_window : min_population_per_window;
  }

  size_t incoming_bursty_90_percent() const {
    bool is_burst = (os::random() % 100) < 90; // 90% burst chance
    return is_burst ? max_population_per_window : min_population_per_window;
  }

  size_t incoming_low_rate() const {
    return min_population_per_window;
  }

  size_t incoming_high_rate() const {
    return max_population_per_window;
  }

  size_t incoming_burst_eval(size_t& count, size_t mod_value) const {
    return count++ % 10 == mod_value ? max_population_per_window : 0;
  }

  size_t incoming_early_burst() const {
    static size_t count = 1;
    return incoming_burst_eval(count, 1);
  }

  size_t incoming_mid_burst() const {
    static size_t count = 1;
    return incoming_burst_eval(count, 5);
  }

  size_t incoming_late_burst() const {
    static size_t count = 1;
    return incoming_burst_eval(count, 0);
  }
};

void JfrGTestAdaptiveSampling::test(JfrGTestAdaptiveSampling::incoming inc, size_t sample_points_per_window, double error_factor, const char* const description) {
  assert(description != NULL, "invariant");
  char output[1024] = "Adaptive sampling: ";
  strcat(output, description);
  fprintf(stdout, "=== %s\n", output);
  jlong population[100] = { 0 };
  jlong sample[100] = { 0 };
  ::JfrGTestFixedRateSampler sampler = ::JfrGTestFixedRateSampler(expected_sample_points_per_window, window_duration_ms, window_lookback_count);
  EXPECT_TRUE(sampler.initialize());

  size_t population_size = 0;
  size_t sample_size = 0;
  for (int t = 0; t < window_count; t++) {
    const size_t incoming_events = (this->*inc)();
    for (size_t i = 0; i < incoming_events; i++) {
      ++population_size;
      size_t index = os::random() % 100;
      population[index] += 1;
      if (sampler.sample()) {
        ++sample_size;
        sample[index] += 1;
      }
    }
    MockJfrTicks::tick += window_duration_ms * NANOSECS_PER_MILLISEC + 1;
    sampler.sample(); // window rotation
  }

  const size_t target_sample_size = sample_points_per_window * window_count;
  EXPECT_NEAR(target_sample_size, sample_size, expected_sample_points * error_factor) << output;
  strcat(output, ", hit distribution");
  assertDistributionProperties(100, population, sample, population_size, sample_size, output);
}

TEST_VM_F(JfrGTestAdaptiveSampling, uniform_rate) {
  test(&JfrGTestAdaptiveSampling::incoming_uniform, expected_sample_points_per_window, 0.05, "random uniform, all samples");
}

TEST_VM_F(JfrGTestAdaptiveSampling, low_rate) {
  test(&JfrGTestAdaptiveSampling::incoming_low_rate, min_population_per_window, 0.05, "low rate");
}

TEST_VM_F(JfrGTestAdaptiveSampling, high_rate) {
  test(&JfrGTestAdaptiveSampling::incoming_high_rate, expected_sample_points_per_window, 0.02, "high rate");
}

TEST_VM_F(JfrGTestAdaptiveSampling, early_burst) {
  test(&JfrGTestAdaptiveSampling::incoming_early_burst, expected_sample_points_per_window, 0.9, "early burst");
}

TEST_VM_F(JfrGTestAdaptiveSampling, mid_burst) {
  test(&JfrGTestAdaptiveSampling::incoming_mid_burst, expected_sample_points_per_window, 0.5, "mid burst");
}

TEST_VM_F(JfrGTestAdaptiveSampling, late_burst) {
  test(&JfrGTestAdaptiveSampling::incoming_late_burst, expected_sample_points_per_window, 0.0, "late burst");
}

TEST_VM_F(JfrGTestAdaptiveSampling, bursty_rate_10_percent) {
  test(&JfrGTestAdaptiveSampling::incoming_bursty_10_percent, expected_sample_points_per_window, 0.96, "bursty 10%");
}

TEST_VM_F(JfrGTestAdaptiveSampling, bursty_rate_90_percent) {
  test(&JfrGTestAdaptiveSampling::incoming_bursty_10_percent, expected_sample_points_per_window, 0.96, "bursty 90%");
}

/*

TEST_VM_F(JfrGTestAdaptiveSampling, uniform_rate) {
  run(&JfrGTestAdaptiveSampling::uniform_incoming, 0.25, "random uniform, all samples");
  /*
  fprintf(stdout, "=== uniform\n");
  jlong population[100] = { 0 };
  jlong sample[100] = { 0 };
  ::JfrFixedRateSampler sampler = ::JfrFixedRateSampler(expected_hits_per_window, window_duration_ms, default_window_lookback_count);
  EXPECT_TRUE(sampler.initialize());

  size_t population_size = 0;
  size_t sample_size = 0;
  for (int t = 0; t < window_count; t++) {
    int incoming_events = os::random() % max_events_per_window + min_events_per_window;
    for (int i = 0; i < incoming_events; i++) {
      ++population_size;
      int index = os::random() % 100;
      population[index] += 1;
      if (sampler.sample()) {
        ++sample_size;
        sample[index] += 1;
      }
    }
    MockTicks::tick += window_duration_ms * NANOSECS_PER_MILLISEC + 1;
  }
  EXPECT_NEAR(expected_hits, sample_size, expected_hits * 0.25) << "Adaptive sampler: random uniform, all samples";
  assertDistributionProperties(100, population, sample, population_size, sample_size, "Adaptive sampler: random uniform, hit distribution");
  */

/*
}

TEST_VM_F(JfrGTestAdaptiveSampling, bursty_rate_10p) {
  fprintf(stdout, "=== bursty 10\n");
  jlong events[100] = { 0 };
  jlong hits[100] = { 0 };
  ::JfrFixedRateSampler sampler = ::JfrFixedRateSampler(expected_hits_per_window, window_duration_ms, default_window_lookback_count);
  EXPECT_TRUE(sampler.initialize());

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
      if (sampler.sample()) {
        counter++;
        hits[hit_index] = hits[hit_index] + 1;
      }
    }
    all_hits += counter;
    MockTicks::tick += window_duration_ms * NANOSECS_PER_MILLISEC + 1;
  }
  EXPECT_NEAR(expected_hits, all_hits, expected_hits * 0.25) << "Adaptive sampler: bursty 10%";
  assertDistributionProperties(100, events, hits, all_events, all_hits, "Adaptive sampler: bursty 10%, hit distribution");
}

TEST_VM_F(JfrGTestAdaptiveSampling, bursty_rate_90p) {
  fprintf(stdout, "=== bursty 90\n");
  jlong events[100] = { 0 };
  jlong hits[100] = { 0 };
  ::JfrFixedRateSampler sampler = ::JfrFixedRateSampler(expected_hits_per_window, window_duration_ms, default_window_lookback_count);
  EXPECT_TRUE(sampler.initialize());

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
      if (sampler.sample()) {
        counter++;
        hits[hit_index] = hits[hit_index] + 1;
      }
    }
    all_hits += counter;
    MockTicks::tick += window_duration_ms * NANOSECS_PER_MILLISEC + 1;
  }
  EXPECT_NEAR(expected_hits, all_hits, expected_hits * max_sample_bias) << "Adaptive sampler: bursty 90%";
  assertDistributionProperties(100, events, hits, all_events, all_hits, "Adaptive sampler: bursty 90%, hit distribution");
}

TEST_VM_F(JfrGTestAdaptiveSampling, low_rate) {
  fprintf(stdout, "=== low\n");
  jlong events[100] = { 0 };
  jlong hits[100] = { 0 };
  ::JfrFixedRateSampler sampler = ::JfrFixedRateSampler(expected_hits_per_window, window_duration_ms, default_window_lookback_count);
  EXPECT_TRUE(sampler.initialize());

  size_t all_events = 0;
  size_t all_hits = 0;
  for (int t = 0; t < window_count; t++) {
    size_t counter = 0;
    for (int i = 0; i < min_events_per_window; i++) {
      all_events++;
      int hit_index = os::random() % 100;
      events[hit_index] = events[hit_index] + 1;
      if (sampler.sample()) {
        counter++;
        hits[hit_index] = hits[hit_index] + 1;
      }
    }
    all_hits += counter;
    MockTicks::tick += window_duration_ms * NANOSECS_PER_MILLISEC + 1;
  }
  size_t target_samples = min_events_per_window * window_count;
  EXPECT_NEAR(target_samples, all_hits, expected_hits * 0.01) << "Adaptive sampler: below target";
  assertDistributionProperties(100, events, hits, all_events, all_hits, "Adaptive sampler: below target, hit distribution");
}

TEST_VM_F(JfrGTestAdaptiveSampling, high_rate) {
  fprintf(stdout, "=== high\n");
  jlong events[100] = { 0 };
  jlong hits[100] = { 0 };
  ::JfrFixedRateSampler sampler = ::JfrFixedRateSampler(expected_hits_per_window, window_duration_ms, default_window_lookback_count);
  EXPECT_TRUE(sampler.initialize());

  size_t all_events = 0;
  size_t all_hits = 0;
  for (int t = 0; t < window_count; t++) {
    size_t counter = 0;
    for (int i = 0; i < max_events_per_window; i++) {
      all_events++;
      int hit_index = os::random() % 100;
      events[hit_index] = events[hit_index] + 1;
      if (sampler.sample()) {
        counter++;
        hits[hit_index] = hits[hit_index] + 1;
      }
    }
    all_hits += counter;
    MockTicks::tick += window_duration_ms * NANOSECS_PER_MILLISEC + 1;
  }
  EXPECT_NEAR(expected_hits, all_hits, expected_hits * 0.05) << "Adaptive sampler: above target";
  assertDistributionProperties(100, events, hits, all_events, all_hits, "Adaptive sampler: above target, hit distribution");
}

*/
