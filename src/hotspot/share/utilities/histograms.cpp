
#include "runtime/globals.hpp"
#include "utilities/histograms.hpp"
#include "utilities/debug.hpp"
#include "runtime/os.hpp"
#include <algorithm>
#include <iomanip>

// Provide for dynamically enabling/disabling timing code.
bool HistogramStopWatch::_enable_timing = true;  // Enable RtgcStopwatch.
bool HistogramTimer::_enable_histogram = true;  // Accumulate into histograms.

//--------------------------------------
// Static helper functions and data.

// Separate histograms and overview data in printout.
static char g_histogram_print_separator[] =
  "********************************************";
FILE* HistogramTimer::_outfile = NULL;

int HistogramTimer::print_with_commas(const char* prefix, unsigned long value) {
  int print_with_commas(FILE *stream, const char* prefix, unsigned long value);
  return print_with_commas(_outfile, prefix, value);
}

int HistogramTimer::print_time_value(const char* prefix,
                                     double nanoseconds,
                                     const char* postfix) {
  return ::print_time_value(_outfile, prefix, nanoseconds, postfix);
}

// Helper functions for ASCII art printing.
void HistogramTimer::print_characters(int count, char c) {
  while (count-- > 0)
    fputc(c, _outfile);
}

// Helper function for printing variable width ASCII art bar.
void HistogramTimer::print_bar(int length, int total) {
  print_characters(length, '-');
  fputc('O', _outfile);
  print_characters(total - length - 1, ' ');
}

// Helper function to identify the common paths seen in numerous
// timers, so that they can be printed onec at the start of the
// printout.  Compare strings, up to the |limit|, and find the first
// character offset of a difference.
size_t HistogramTimer::find_first_differing_offset(const char* left,
                                                   const char* right,
                                                   size_t limit) {
  size_t offset = 0;
  for (offset = 0; offset < limit; ++offset) {
    if (left[offset] != right[offset])
      return offset;
  }
  return offset;
}

//--------------------------------------
// HistogramTimer code.


// static
HistogramTimer* HistogramTimer::_head = NULL;

// Support broader dynamic range on slow computers, so that we don't
// get as many overflow bin contributions.
// static
HistogramTimer::HistogramRange HistogramTimer::_default_dynamic_range =
  HistogramTimer::MEDIUM_RANGE;

HistogramTimer::HistogramTimer(const char* stopwatch_name)
  : HistogramTimer(stopwatch_name, UNINITIALIZED_RANGE) {
}
HistogramTimer::HistogramTimer(const char* stopwatch_name, HistogramRange range)
  : HistogramTimer(NULL, NULL, 0, stopwatch_name, range) {
}

HistogramTimer::HistogramTimer(const char* file, const char* function, int line,
                               const char* description, HistogramRange range)
  : _call_count(0),
    _total_duration_ns(0),
    _max_duration_ns(0),
    _dynamic_range(UNINITIALIZED_RANGE),
    _start_line(line),
    _end_line(0),
    _start_file(file),
    _start_function(function),
    _end_file(file),
    _end_function(NULL),
    _description(description) {

  memset(&_histogram, 0, sizeof(_histogram));
  memset(&_bin_label, 0, sizeof(_bin_label));
  // Insert at the head of the global list.
  _next = _head;
  // Interthread memory order doesn't matter, so long as we do the
  // exchange atomically, so we are RELAXED.
  while(!__atomic_compare_exchange_n(&_head, &_next, this, true,
                                     __ATOMIC_RELAXED, __ATOMIC_RELAXED)) {
    // Keep trying.  Note that _next was updated.
  }
}

void HistogramTimer::accumulate(unsigned long duration) {
  if (UNINITIALIZED_RANGE == _dynamic_range) {
    _dynamic_range = _default_dynamic_range;
  }
  if (0 == _call_count) {
    populate_bin_labels();
  }

  __atomic_add_fetch(&_total_duration_ns, duration, __ATOMIC_RELAXED);
  unsigned long current_max = _max_duration_ns;
  while (duration > current_max  &&
         !__atomic_compare_exchange_n(&_max_duration_ns, &current_max, duration,
                                      true, __ATOMIC_RELAXED,
                                      __ATOMIC_RELAXED)) {
    // Keep trying.
  }
  __atomic_add_fetch(&_call_count, 1UL, __ATOMIC_RELAXED);

  if (!_enable_histogram) {
    return;
  }

  // This is a slightly tricky implementation of finding the
  // log(duration) in a certain base (we currently use base == nth
  // root-of-two, where n is the _dynamic_range setting).  If we only
  // wanted the bin to be log2(duration), we'd only have to find what
  // the most significant bit of duration is, and we'd use
  // __builtin_clzl() as seen below. Since we want a finer granularity
  // in our histogram (i.e., a smaller ratio than 2 between
  // consecutive buckets), we first raise duration to a power
  // (_dynamic_range), and then take the log2 of that result. The
  // histogram then has bucket demarcations that grow by a factor of 2
  // every n buckets.  This ends up wasting (never using) a few of the
  // smaller bins, but should be very very fast.
  size_t bin = 0;  // default to Underflow bin.
  if (0 != duration) {
    unsigned long scaled_value = duration;
    for (int i = 1; i < _dynamic_range; ++i) {
      unsigned long value = scaled_value * duration;  // May overflow!
      if (value / duration != scaled_value) {
        bin = HISTOGRAM_BIN_COUNT - 1;  // We overflowed.
        break;
      }
      scaled_value = value;
    }
    if (0 == bin) {
      // We never overflowed, so take log2 of result.
      bin = 64 - __builtin_clzl(scaled_value);
      assert((1L << (bin - 1)) & scaled_value, "");
      if (bin < 64) {
        assert((scaled_value >> bin) == 0, "");
      } else {
        assert(64 == bin, "");
      }
    }
  }
  if (bin >= HISTOGRAM_BIN_COUNT)  // Defensive coding.
    bin = HISTOGRAM_BIN_COUNT - 1;
  // Validate that correct bin was found.
  assert(duration >= _bin_label[bin], "");
  if (bin < HISTOGRAM_BIN_COUNT - 1) {
    assert(duration < _bin_label[bin + 1], "");
  }
  ++_histogram[bin];  // Ignore potential race.
}

// static
void HistogramTimer::print_all() {
  if (!_head) {
    // There are no timing histograms.
    return;
  }

  static time_t start_time;
  char buffer[256];
  static bool use_pid = true;
  if (_outfile == NULL) {
    time(&start_time);
    if (!use_pid) {
      _outfile = stderr;
    } else {
      FILE* newfile = fopen(HistogramFile, "w");
      assert(newfile != NULL, "");
      _outfile = newfile != NULL ? newfile : stderr;
    }
  }
  fseek(_outfile, 0, SEEK_SET);
  time_t rawtime;
  time(&rawtime);
  double wall_clock_seconds = difftime(rawtime, start_time);
  fprintf(_outfile, "%.2f minutes\n", wall_clock_seconds / 60);

  struct tm * timeinfo = localtime (&rawtime);
  strftime (buffer, sizeof(buffer), "//////////////////// %m/%d/%Y %T%n", timeinfo);
  fputs(buffer, _outfile);

  HistogramTimer::sort_list();
  _head->print_all_internal(wall_clock_seconds);

  fputs(buffer, _outfile);
  fflush(_outfile);
}

void HistogramTimer::sort_list() {
  if (_head == NULL || _head->_next == NULL) {
    return;
  }
  // We are the only ones to reorder the list, so we just have to not
  // race with another invocation (so don't do that!!!).  Ther is NO
  // lock controlling access to the list.  The only race we need to
  // worry about is new histograms prepending themselves to the head
  // of the list, and modifying _head asynchronously, and for that we
  // will use an atomic instruction.
  bool sorted = false;
  while (!sorted) {
    sorted = true;
    HistogramTimer** prev = &_head;
    HistogramTimer* current = _head;
    HistogramTimer* next = current->_next;
    do {
      // Select sorting criteria, using either total_duration (which
      // highlights times that detract from overall performance), or
      // max_duration (which highlights outliers, impacting variance
      // in elements such as time-spent-in-a-safepoint).
      static bool sort_on_total = true;
      if ((sort_on_total && (current->_total_duration_ns < next->_total_duration_ns)) ||
          (!sort_on_total && (current->_max_duration_ns < next->_max_duration_ns))) {
        sorted = false;  // Exchange next two items.
        // These exchanges must be done atomically only for the real
        // head, since prepends (modifying _head) can happen any time.
        if (prev != &_head) {
          // Bypass current to unlink it (don't bother with atomic).
          *prev = next;
        } else {
          // Use care since we're impacting _head.
          if(!__atomic_compare_exchange_n(prev, &current, next, true,
                                          __ATOMIC_RELAXED, __ATOMIC_RELAXED)) {
            // Concurrent prepend happened, we'll just punt for now
            // andsort this out next time.
            return;
          }
        }
        // The rest doesn't need atomic
        current->_next = next->_next;  // Set current up for insertion.
        next->_next = current;  // Insert current after next.
        current = next;  // For clean iteration.
      }
      prev = &(current->_next);
      current = current->_next;
      next = current->_next;
    } while (next != NULL);
  }
}

void HistogramTimer::print_all_internal(double wall_clock_seconds) const {
  const char* baseline = NULL;
  size_t prefix_to_skip = find_first_differing_path_offset_in_filenames(&baseline);
  if (prefix_to_skip > 0) {
    assert(NULL != baseline, "");
    fprintf(_outfile, "Common prefix of %ld characters of paths will be omitted:\n"
            "%.*s\n%s\n",
            prefix_to_skip,
            (int)prefix_to_skip, baseline,
            g_histogram_print_separator);
  }
  for (const HistogramTimer* timer = this; NULL != timer; timer = timer->_next) {
    timer->print(wall_clock_seconds, prefix_to_skip);
  }
}

void HistogramTimer::print(double wall_clock_seconds,
                           size_t filename_prefix_skip) const {
  if (0 == _call_count) {
    return;
  }

  if (strlen(_description) > 0) {
    fprintf(_outfile, "%s", _description);
  }

  if (_start_function) {
    assert(filename_prefix_skip < strlen(_start_file), "");
    const char* truncated_file = _start_file + filename_prefix_skip;
    fprintf(_outfile, "--%s:%s%s:%d,",
            _start_function,
            (truncated_file == _start_file) ? "" : ".",
            truncated_file,
            _start_line);
    if (NULL == _end_function) {
      fprintf(_outfile, " till-end-of-block\n");
    } else {
      assert(filename_prefix_skip < strlen(_end_file), "");
      truncated_file = _end_file + filename_prefix_skip;
      fprintf(_outfile, " %s:%s%s:%d\n",
              _end_function,
              (truncated_file == _end_file) ? "" : ".",
              truncated_file,
              _end_line);
    }
  } else {
    fputc('\n', _outfile);
  }

  print_time_value("Total ", _total_duration_ns, "");
  if (wall_clock_seconds > 10.0) {
    // _total_duration_ns is in microseconds.
    float percent = (_total_duration_ns / (wall_clock_seconds * NANOSECS_PER_SEC)) * 100;
    if (percent > 0.001) {
      fprintf(_outfile, " (%.3g%% of wall time)", percent);
    }
  }
  print_with_commas(" in ", _call_count);
  float average = (float)_total_duration_ns / _call_count;
  print_time_value(" calls  Average=", average, "/call\n");

  print_histogram();

  fprintf(_outfile, "%s\n", g_histogram_print_separator);
}

void HistogramTimer::set_start_location(const char* const file,
                                        const char* const function,
                                        const int line) {
  // We use pointer equality, since the there should be one call site
  // to pass in the constant string.
  if (_start_line) {
    assert(file == _start_file, "");
    assert(function == _start_function, "");
    assert(line == _start_line, "");
  } else {
    _start_file = file;
    _start_function = function;
    _start_line = line;
  }
}

void HistogramTimer::set_end_location(const char* const file,
                                      const char* const function,
                                      const int line) {
  // We use pointer equality, since the there should be one call site
  // to pass in the constant string.
  if (_end_line) {
    assert(file == _end_file, "");
    assert(function == _end_function, "");
    assert(line == _end_line, "");
  } else {
    _end_file = file;
    _end_function = function;
    _end_line = line;
  }
}

void HistogramTimer::print_histogram() const {
  unsigned long snapshot[HISTOGRAM_BIN_COUNT];
  memcpy(snapshot, _histogram, sizeof(snapshot));
  unsigned long total_count = 0;  // Call counts included in histogram.
  float largest_bar_size = 0.0;  // Largest bin count per bin width ratio.
  size_t last_nonzero_bin = 0;
  summarize_histogram(snapshot, &total_count, &largest_bar_size,
                      &last_nonzero_bin);

  // The _max_duration_ns is updated asynchronously relative to the
  // _histogram in Accumulate(), as there is no write barrier used.
  // On the unlikely probability that there is a conflict, create a
  // snapshot_max_duration that is at least consistent with our
  // current histogram snapshot.
  unsigned long snapshot_max_duration = _max_duration_ns;
  if (snapshot_max_duration < _bin_label[last_nonzero_bin]) {
    snapshot_max_duration = _bin_label[last_nonzero_bin];
  }

  float percentages[] = {.5, .9, .99, .999, .9999, 1.0}; // Terminate with 1.0.
  print_percentile_data(snapshot, total_count, percentages,
                        snapshot_max_duration);
  print_time_value(" P100=    ", (double) snapshot_max_duration, "    ");
  double average = (double)_total_duration_ns / (double)_call_count;
  print_time_value("  Actual mean=", average, "/call\n");

  if (0 == total_count)
    return;  // There is no data in histogram.

  assert(_bin_label[HISTOGRAM_BIN_COUNT - 1] > 0, "");
  fputc('\n', _outfile);

  assert(last_nonzero_bin < HISTOGRAM_BIN_COUNT, "");
  unsigned long cumulative_count = 0;
  bool among_sequence_of_empty_bins = false;  // Help to elide empty bins.
  bool printing_started_for_zero_width_bin = false;
  bool printed_remnant_cdf = false;
  for (size_t i = 0; i <= last_nonzero_bin; ++i) {
    if (among_sequence_of_empty_bins && 0 == snapshot[i]) {
      continue;
    }

    static int print_widths[TINY_RANGE] = {15, 14, 12, 9, 8};
    const int half_label_print_width = print_widths[_dynamic_range - 1];

    if (among_sequence_of_empty_bins) {  // A series of empty bins.
      assert(snapshot[i] > 0, "");
      // Finish printing previous label for the empty region.
      int printed = 1 + print_with_commas("", _bin_label[i]);
      fputc(')', _outfile);
      print_characters(half_label_print_width - printed, ' ');
      fprintf(_outfile, "...\n");
      among_sequence_of_empty_bins = false;
    }

    if (!printing_started_for_zero_width_bin) {
      // Start printing this bin's label.
      int printed = 1 + print_with_commas("[", _bin_label[i]);
      fputc(',', _outfile);
      print_characters(half_label_print_width - printed, ' ');
    }

    if (0 == get_bin_width(i)) {
      assert(0 == snapshot[i], "");  // Nothing gets collected.
      // Bin width is actually zero (which can happen with some
      // initial bins), so wait for a bin that has the *chance* to
      // hold counts.  We'll then and use its _bin_label[i+1] to
      // describe the top-end range of integral values that landed in
      // the combined bins.
      printing_started_for_zero_width_bin = true;
      continue;
    }
    printing_started_for_zero_width_bin = false;

    if (0 == snapshot[i] && 0 == snapshot[i+1]) {
      // Wait patiently for some nonzero bin.
      assert(i < last_nonzero_bin, "");  // due to loop constraint!
      among_sequence_of_empty_bins = true;
      continue;
    }

    // Finish printing our current bin's max-value label.
    int printed;
    if (i < HISTOGRAM_BIN_COUNT - 1) {
      printed = 1 + print_with_commas("", _bin_label[i + 1]);
      fputc(')', _outfile);
    } else {
      printed = fprintf(_outfile, "over)");
    }
    print_characters(half_label_print_width - printed, ' ');

    // Draw ASCII art bargraph, with right side stat data.
    const size_t total_bar_chars = 80;  // Max width of bar graph.
    // We avoided division by zero for for useless zero-width bins.
    size_t bar_chars = (snapshot[i] * 1.0 / get_scaling_factor(i)) *
      total_bar_chars / largest_bar_size;
    float percentage = (snapshot[i] * 100.0) / total_count;;
    print_bar(bar_chars, total_bar_chars + 1);
    print_with_commas("  (", snapshot[i]);
    fprintf(_outfile, " = %3.1f%%)", percentage);

    cumulative_count += snapshot[i];
    double cumulative_percentage = (cumulative_count * 100.0) / total_count;
    if (cumulative_percentage <= 99.9 || cumulative_count == total_count) {
      fprintf(_outfile, " {%.3g%%}\n", cumulative_percentage);
    } else {
      // It is more interesting to start printing the remaining percentage.
      if (!printed_remnant_cdf) {
        fprintf(_outfile, " {~%.3g%% with %.3g%% remaining}\n",
                cumulative_percentage, 100.0 - cumulative_percentage);
      } else {
        fprintf(_outfile, " {%.3g%% remaining}\n",
                100.0 - cumulative_percentage);
      }
      printed_remnant_cdf = true;
    }
  }
}

void HistogramTimer::populate_bin_labels() {
  if (_bin_label[HISTOGRAM_BIN_COUNT - 1] > 0)
    return;  // We've already populated them.

  assert(UNINITIALIZED_RANGE != _dynamic_range, "Cannot use uninitialized range (0).");

  float bin_label = 0.0;  // Current min value for bin being populated.
  // The following is the usual ratio between consecutive bin labels.
  float bin_label_ratio = powf(2.0, 1.0f / _dynamic_range);
  // Every _dynamic_range bins, we re-align on an exact power of
  // two. This variable provides the next planned re-alignment.
  float next_power_of_two_label = 1.0;

  for (size_t i = 0; i < HISTOGRAM_BIN_COUNT; ++i) {
    _bin_label[i] = ceil(bin_label);
    if (i % _dynamic_range == 0){
      bin_label = next_power_of_two_label;
      next_power_of_two_label *= 2.0;
    } else {
      bin_label *= bin_label_ratio;
    }
  }
}

void HistogramTimer::summarize_histogram(const unsigned long snapshot[],
                                         unsigned long* total_count,
                                         float* largest_bar_size,
                                         size_t* last_nonzero_bin) const {
  assert(_bin_label[HISTOGRAM_BIN_COUNT - 1] > 0, "");
  unsigned long total = 0;
  float large_bar_size = 0.0;
  size_t big_nonzero_bin = 0;
  for (size_t i = 0; i < HISTOGRAM_BIN_COUNT; ++i) {
    total += snapshot[i];
    if (snapshot[i] > 0) {
      big_nonzero_bin = i;
    }

    // Optionally scale the bar_size based on width, so that we don't
    // show a bigger bar just because the width is giant, and many
    // samples were collected in a bin.
    float bar_size = float(snapshot[i]) / float(get_scaling_factor(i));
    if (large_bar_size < bar_size) {
      large_bar_size = bar_size;
    }
  }
  *total_count = total;
  *largest_bar_size = large_bar_size;
  *last_nonzero_bin = big_nonzero_bin;
}

void HistogramTimer::print_percentile_data(const unsigned long snapshot[],
                                           unsigned long total,
                                           const float* percentages,
                                           unsigned long snapshot_max_duration) const {
  if (0 == total)
    return;  // Nothing to print.
  assert(_bin_label[HISTOGRAM_BIN_COUNT - 1] > 0, "");

  unsigned long tally = 0;  // Running sum of bin counts.
  // We work to estimate the mean up to the specified trim count
  // level, by maintaining a running total of the product of bin
  // counts times the labels.  We only divide by the count (to
  // calculate the trimmed mean) when we are about to print.
  float under_weighted_tally = 0.0;  // Lower bound.
  float over_weighted_tally = 0.0;  // Upper bound.
  size_t bin = 0;

  for (size_t printing = 0; 1.0 != percentages[printing]; ++printing) {
    assert(0.0 < percentages[printing], "");
    assert(1.0 > percentages[printing], "");
    assert(percentages[printing] < percentages[printing + 1], "");
    int printed = fprintf(_outfile, " P%.6g=", percentages[printing] * 100);
    print_characters(10 - printed, ' ');
    unsigned long target_count = ceil(total * percentages[printing]);

    while (bin < HISTOGRAM_BIN_COUNT) {
      if (tally + snapshot[bin] < target_count) {
        tally+= snapshot[bin];
        // Assume all samples fell at exactly the integral label.
        under_weighted_tally += snapshot[bin] * 1.0 * _bin_label[bin];
        // Assume all samples fell just below the next integral label.
        over_weighted_tally += snapshot[bin] * 1.0 * (_bin_label[bin] + get_bin_width(bin) - 1);
        ++bin;  // This bin doesn't contain target_count.
        continue;
      }
      // For the [0, 1) bin, we'll use linear interplotation to
      // estimate where the top of a bin containing the target_count
      // would land, while the later bins will interpolate in the log
      // domain, as bins are growing exponentially there.  When we are
      // in the overflow bin, we can't intepolate, so we just note
      // that fact.
      if (HISTOGRAM_BIN_COUNT - 1 <= bin) {
        fprintf(_outfile, " can't interpolate in overflow bucket\n");
        return;  // No chance for larger percentages to print either!
      }
      unsigned long remnant = target_count - tally;
      assert(remnant <= snapshot[bin], "");
      // Interpolate between integer bin labels to estimate where
      // the remnant would be labeled.
      float below = _bin_label[bin];
      float above = MIN2(snapshot_max_duration,
                             _bin_label[bin] + get_bin_width(bin) - 1);
      if (0 < bin) {
        below = log(below);  // Transition to log domain.
        above = log(above);
      }
      float target_label = ((remnant * above) + (snapshot[bin] - remnant) * below) / snapshot[bin];

      if (0 < bin) {
        target_label = exp(target_label);  // Return from log domain.
      }
      float under_trimmed_mean = (under_weighted_tally + remnant * 1.0 *
                                                         _bin_label[bin]) / target_count;
      float over_trimmed_mean = (over_weighted_tally + remnant *
                                 target_label) / target_count;
      /* The above, using the calculated target label for a bin
         upper bound, seems arguably better than using the full
         bin_width extent of the bucket to estimate the upper bound
         for final contributions to this trimmed mean.
         float over_trimmed_mean = (over_weighted_tally + remnant * 1.0 *
         (_bin_label[bin] + get_bin_width(bin) - 1))) / target_count;
      */
      float percent_of_bin = remnant * 100.0 / snapshot[bin];
      printed = print_time_value("~", target_label, "");
      print_characters(13 - printed, ' ');

      printed = print_time_value(" trimmed mean ~",
                                 (under_trimmed_mean + over_trimmed_mean) / 2, "");
      print_characters(25 - printed, ' ');

      printed = print_time_value("   (bounded by [", under_trimmed_mean, ",");
      printed += print_time_value(" ", over_trimmed_mean, "])");

#if 0 // Most useful for debugging
      print_characters(38 - printed, ' ');
      printed = print_with_commas("Pn in bin for ", _bin_label[bin]);
      printed += fprintf(_outfile, "us+, (%.1f%% of bin)", percent_of_bin);
      // Printcharacters(40 - printed, ' ');
#endif // 0

      fputc('\n', _outfile);
      // Don't increment bin, as the next percentage *might* also
      // use it!
      break;  // Leave bin loop and Possibly get a new percentage.
    }
  }
}

unsigned long HistogramTimer::get_scaling_factor(size_t bin) const {
  static bool use_scaling = false;
  return use_scaling ? get_bin_width(bin) : 1;
}

unsigned long HistogramTimer::get_bin_width(size_t bin) const {
  assert(_bin_label[HISTOGRAM_BIN_COUNT - 1] > 0, "");
  if (bin >= HISTOGRAM_BIN_COUNT - 1)
    bin = HISTOGRAM_BIN_COUNT - 2;  // Use last width for overflow bin_width.
  // Non-integral bin labels don't really change the bin_width, since
  // we only collect integral durations.  Hence, for scaling purposes,
  // the width is the difference between the corresponding integer
  // values.
  return _bin_label[bin + 1] - _bin_label[bin];
}

// Find the number of characters that are common to all file paths, in
// all timers in this linked list. Returns the number of characters,
// and if |sample_baseline| is non-NULL, also returns an example of a
// string where the skippable common prefix is visible.
size_t HistogramTimer::find_first_differing_path_offset_in_filenames(
     const char** sample_baseline) const {
  const char* baseline = _start_file;
  const HistogramTimer* instance = this;
  while (NULL == baseline) {
    assert(NULL == _end_file, "");
    instance = instance->_next;
    if (NULL == instance)
      return 0;
    baseline = instance->_start_file;
  }
  if (NULL != sample_baseline) {
    *sample_baseline = baseline;
  }
  size_t differing_offset = strlen(baseline);
  while (differing_offset > 0) {
    if (NULL != instance->_end_file && instance->_call_count > 0) {
      differing_offset = find_first_differing_offset(baseline, instance->_end_file,
                                                     differing_offset);
    }
    instance = instance->_next;
    if (NULL == instance)
      break;  // End of list.
    if (NULL != instance->_start_file && instance->_call_count > 0) {
      differing_offset = find_first_differing_offset(baseline,
                                                     instance->_start_file,
                                                     differing_offset);
    }
  }  // Scanned all RtgcTimers.
  // Find the last path separator before the differing begins.
  size_t slash_offset = 0;
  for (size_t i = 1; i < differing_offset; ++i) {
    if ('/' == baseline[i])
      slash_offset = i;
  }
  return slash_offset;
}

//-------------------------------------------
HistogramStopWatch::HistogramStopWatch(HistogramTimer* timer)
  : _timer(timer),
    _start_time(0) {
}

HistogramStopWatch::~HistogramStopWatch() {
  stop();
}

void HistogramStopWatch::start() {
  if (_enable_timing) {
    assert(0 == _start_time, "");
    _start_time = get_time();
  }
}

void HistogramStopWatch::stop() {
  if (0 == _start_time) {
    // There was no interval to conclude.
    return;
  }
  unsigned long now = get_time();
  assert(now >= _start_time, "");
  _timer->accumulate(now - _start_time);
  _start_time = 0;
}

size_t HistogramStopWatch::elapsed() {
  size_t now = get_time();
  if (now >= _start_time) {
    return now - _start_time;
  }
  return 0;
}

void HistogramStopWatch::start(const char* file, const char* function, int line) {
  _timer->set_start_location(file, function, line);
  start();
}

void HistogramStopWatch::stop(const char* file, const char* function, int line) {
  stop();  // Stop timing before doing other work.
  _timer->set_end_location(file, function, line);
}

unsigned long HistogramStopWatch::get_time() {
  return os::javaTimeNanos();
}

void dumpTimers(void) {
  HistogramTimer::print_all();
}

extern void dumpTimersSometimes() {
  static unsigned long busy = 0;

  unsigned long current = 0;  // Hopefully it has this value.
  unsigned long desired = 1;  // Change it to being busy.
  if (!__atomic_compare_exchange(&busy, &current, &desired, false,
                                 __ATOMIC_RELAXED, __ATOMIC_RELAXED)) {
    return;
  }
  assert (busy == 1, "Check that our exchange worked");
  int frequency = 20;
  static int counter = 0;
  if (counter++ % frequency == 0) {
    dumpTimers();
  }
  assert (busy == 1, "Check that our exchange worked");
  busy = 0;
}

// Helper function for print() and print_histogram(). It prints
// integers with comma separators inserted for every group of 3 digits
// of distance from the decimal point.  For example, it will print
// 1000 as "1,000".  As a convenience to callers, based on the use of
// this function in this file, which generally always followed the
// printing of some string, the prefix argument is provided, and it
// prints immediately before the suppled integer is printed with
// comma-enhancement.  It returns a count of the number of characters
// printed, exactly as printf() would.
int print_with_commas(FILE *stream, const char* prefix, unsigned long value) {
  unsigned long thousands = 1;
  while (value >= thousands * 1000) {
    unsigned next_thousands = 1000 * thousands;
    if (next_thousands / 1000 != thousands) {
      break; // Avoid overflow.
    }
    thousands = next_thousands;
  }

  int printed = 0;
  while (0 != thousands) {
    unsigned long digits = value / thousands;
    value -= thousands * digits;
    if (0 == printed) {
      printed += fprintf(stream, "%s%lu", prefix, digits);
    } else {
      // Include separator, plus leading (ongoing?) zeros.
      printed += fprintf(stream, ",%03ld", digits);
    }
    thousands /= 1000;
  }
  return printed;
}

// Help to nicely print floating point representations of
// micro-seconds, eliding the fractional 3 digits (showing the nano
// seconds portion) when the value is large (1000us or higher), so
// that we don't wastefully print the tiny digits, and progressively
// moving to milliseconds and seconds as durations grow larger.
int print_time_value(FILE *stream,
                     const char* prefix,
                     double nanoseconds,
                     const char* postfix) {
  int integral_portion_only = 0;
  const char* units = "ns";  // Microseconds if we don't scale at all.
  if (round(nanoseconds) < 1000.0) {
    integral_portion_only = 1;  // Don't show fractions of nanoseconds.
  } else if (round(nanoseconds) >= 1000.) {
    nanoseconds /= 1000.;
    units = "us";  // Microseconds
    if (round(nanoseconds) >= 1000.) {
      nanoseconds /= 1000.;
      units = "Ms";  // Milliseconds (make sure the M stands out).
      if (round(nanoseconds) >= 1000.) {
        nanoseconds /= 1000;
        units = "sec"; // Seconds
        if (round(nanoseconds) >= 60.) {
          nanoseconds /= 60.;
          units = "min";  // Minutes.
        }
      }
    }
  }
  // Try to give at least 3 significant digits in any units.
  int printed = 0;
  if (integral_portion_only || round(nanoseconds) >= 100.) {
    printed += print_with_commas(stream, prefix, (unsigned long) round(nanoseconds));
  } else if (round(10. * nanoseconds) >= 100.) {
    printed += fprintf(stream, "%s%.1f", prefix, nanoseconds);
  } else {
    printed += fprintf(stream, "%s%.2f", prefix, nanoseconds);
  }

  fputs(units, stream);
  fputs(postfix, stream);
  return printed + strlen(units) + strlen(postfix);
}

// print count spaces, if count is positive.
void print_spaces(FILE* stream, int count) {
  while (count-- > 0) {
    fputc(' ', stream);
  }
}
