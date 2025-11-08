/*
  Define classes for use in timing durations of time spent in
  functions, blocks, or across function calls (potentially across
  threads).

  Macros supplied at the end of this file should be used, instead of
  the direct use of the classes.  This will allow the instrumentation
  to be "compiled out" of the source code.  See
  HISTOGRAM_COMPILE_FOR_TIMING for details.
*/

#ifndef SHARE_UTILITIES_HISTOGRAMS_HPP
#define SHARE_UTILITIES_HISTOGRAMS_HPP

// Uncomment this next #define to turn on timings.  If this line is
// commented, then all timing code will be removed from the source.

#define HISTOGRAM_COMPILE_FOR_TIMING

#include <cstdio>
#include <ctime>
#include "runtime/globals.hpp"

/*
  Durations are recorded for periods of time between two (associated)
  points in programs via an HistogramTimer.  All times are recorded and
  accumulated in microseconds.
*/
class HistogramTimer {
 public:
  enum HistogramRange {  // Large range implies low precision.
    UNINITIALIZED_RANGE = 0,
    FULL_RANGE = 1,  // 2^64 time units (600K years in microseconds)
    LARGE_RANGE = 2,  // 2^32 time units (about 1 hours in microseconds)
    MEDIUM_RANGE = 3,  // 2^21 time units (about 2.6 seconds in microseconds)
    SMALL_RANGE = 4,  // 2^16 time units (about 65ms in microseconds)
    TINY_RANGE = 5  // 2^12 time units (about 7.1ms in microseconds)
  };

  explicit HistogramTimer(const char* stopwatch_name);  // Start/end locations are optional.
  HistogramTimer(const char* stopwatch_name, HistogramRange range);
  HistogramTimer(const char* file, const char* start_function, int start_line,
                 const char* description, HistogramRange range = UNINITIALIZED_RANGE);

  void accumulate(unsigned long duration);  // Add duration into recording.
  // Print() all instances, using link list from _head->_next->....
  static void print_all();
  // Print statistics about this instance, as well as (optionally)
  // printing the histogram using ASCII art.
  void print(double wall_clock_seconds, size_t filename_prefix_skip = 0) const;

  // Optional calls made by a HistogramStopWatch to complete construction
  // at the end of a timing interval, when the timer is not local to a
  // single function.
  void set_start_location(const char* file, const char* function, int line);
  void set_end_location(const char* file, const char* function, int line);

 private:
  // Run though the linked list, moving larger histograms to the head
  // of the list. Use a bubble sort, so that when we come to print
  // again, we really just validate the sort order.
  static void sort_list();

  // Starting with the current instance, print contents of the
  // instance followed by the rest of the linked list using _next
  // pointers.
  void print_all_internal(double wall_clock_seconds) const;

  // Print complete histogram, including definitions of bin ranges,
  // ASCII art of bin sizes, as well is the count in each in and the
  // percentage supplied by that bin.  Also included as a preface is a
  // series of Percentile data information, such as the media, P90,
  // etc., and associated trimmed means (i.e., mean of data in
  // previous bins, up to the given percentile.)
  void print_histogram() const;

  // Fill out bin_labels_ array, after a decision has been made about
  // which HistogramRange to use. We could have waited until this was
  // needed for printing, but it is earlier so that the values can be
  // used for assert validation in a debug build (and we avoid all
  // concerns about races).  [Note: perhaps these labels should be
  // shared across all HistogramTimers using the same HistogramRange, but
  // they are not currently].
  void populate_bin_labels();

  // Scan the snapshot[] of the histogram, and gather stats for use in printing.
  void summarize_histogram(const unsigned long snapshot[],
                           unsigned long* total_count,
                           float* largest_bar_size,
                           size_t* last_nonzero_bin) const;

  // Print estimate of median, P90, P99, etc., as well as associated
  // trimmed means from histogram. The percentages[] array must be
  // terminated by a 1.0 (which won't be processed), and all
  // percentages must be in the range (0.0, 1.0), and strictly
  // increasing.
  void print_percentile_data(const unsigned long snapshot[],
                             unsigned long total,
                             const float percentages[],
                             unsigned long snapshot_max_duration) const;

  // The the integral width of a bin (i.e., the number of possible
  // integral values that could have been placed in the bin).
  unsigned long get_bin_width(size_t bin) const;
  // Optional bar scaling factor, that uses bin_width vs 1.
  unsigned long get_scaling_factor(size_t bin) const;

  // Helper functions for print_all() to create Print() requests that
  // skip a prefix.  It compares the file names in all the HistogramTimers
  // in the linked-list starting from the current instance, and
  // identifies the length of the common (directory) prefix that is
  // shared by all the timers (including both _start_file and
  // _end_file values).
  size_t find_first_differing_path_offset_in_filenames(
      const char** sample_baseline = NULL) const;

  // Following functions assist in producing nice ASCII-art outputs.
  static int print_with_commas(const char* prefix, unsigned long value);
  static void print_characters(int count, char c);
  static void print_bar(int length, int total);
  static size_t find_first_differing_offset(const char* left,
                                            const char* right,
                                            size_t limit);
  static int print_time_value(const char* prefix,
                              double nanoseconds,
                              const char* postfix);

  unsigned long _call_count;         // Count of Accumulate() calls.
  unsigned long _total_duration_ns;  // Sum of accumulated durations.
  unsigned long _max_duration_ns;    // Largest accumulated duration.
  HistogramRange _dynamic_range;
  static HistogramRange _default_dynamic_range;

  // The next two fields were placed here in the struct to slightly
  // speed up validation for the need to initialize the file and
  // function members. These fields are commonly loaded at the same
  // time as the above members, on a single cache line, which is
  // always used for a start() or stop() activity.
  int _start_line;
  int _end_line;

  // Underflow bin for 0 duration, plus an overflow bin, plus 64 bit
  // based bins (since we use 64 bit unsigned long time units).
  static const size_t HISTOGRAM_BIN_COUNT = 66;
  unsigned long _histogram[HISTOGRAM_BIN_COUNT];
  unsigned long _bin_label[HISTOGRAM_BIN_COUNT];  // Min range for a bin.

  // Associate the function::line that this timer covers, based on the
  // following information (plus _start_line and _end_line).  In some
  // timers, the end location is never specified (i.e., 0 for line
  // number, and NULL for function and file string), and is therefore
  // assumed to be the "end of the enclosing block" where the start
  // location was specified.
  const char* _start_file;
  const char* _start_function;
  const char* _end_file;
  const char* _end_function;
  const char* _description;  // Made explicit when function is not enough.

  // All timers are part of a singly linked list, and they register
  // themselves on that list as soon as they are constructed.
  static HistogramTimer* _head;
  HistogramTimer* _next;

  // This values allows for global control of whether we gather just
  // count/total/max, or we actually increment bins in a
  // histogram. The current code is probably fast enough that there is
  // really no advantage to disabling the histogramming feature.
  static bool _enable_histogram;

  static FILE* _outfile;  // Histogram output file.
};  // HistogramTimer

// A HistogramStopWatch is used to calculate a duration, that is
// accumulated into a timer.  An HistogramStopWatch is most commonly
// stack-allocated, for the duration of the block it is timing.  There
// may be multiple HistogramStopWatch instances associated with a single
// HistogramTimer, since several threads may be running, although it is
// common to have only one at any given time.  Instances,
// alternatively, may be allocated in the linker-supplied data
// segment, and then they can be carefully started/stopped to monitor
// cross thread activity. Extra care must be taken in that mode to
// assure that two different threads don't try to start a single
// stopwatch while it is already "running" (i.e., holding a
// _start_time in anticipation of performing an Accumulate() call).
struct HistogramStopWatch {  // Use struct instead of class for C compatibility.
 public:
  explicit HistogramStopWatch(HistogramTimer* timer);
  ~HistogramStopWatch();  // Calls Stop() if not done already.

  void start();  // Snapshot current time.
  void stop();   // Accumulated duration into timer.
  size_t elapsed();

  // When a global timer is used (to track time intervals across
  // threads, or between functions), we need to specify where we
  // start and stop our HistogramStopWatch.
  void start(const char* file, const char* function, int line);
  void stop(const char* file, const char* function, int line);

  // Get current time in *some* integral time units.  Current
  // implementation return time in milliseconds.
  static unsigned long get_time();  // Made public for C calling.

  // Helper for forwarding functions (set_start_location() and
  // Accumulate()) to underlying _timer. For use from C, when a
  // function local duration needs to be accumulated into the timer
  // from a C-stack local accumulation.
  HistogramTimer* get_timer() const { return _timer; }

 private:
  HistogramTimer* _timer;  // Place to aggregate into.
  unsigned long _start_time;  // If 0, then not running.
  static bool _enable_timing;  // Dynamically enable/disable recording.
};  // class HistogramStopWatch

// Used by other collectors to periodically dump the histogram times.
void dumpTimersSometimes();

/*
  The remainder of this file is dedicated to defining macros for use
  in C++ source code.
 */

#ifndef HISTOGRAM_COMPILE_FOR_TIMING

// Provide empty definitions for our macros.
#define HISTOGRAM_TIME_BLOCK
#define HISTOGRAM_TIME_DESCRIBED_BLOCK(description)
#define HISTOGRAM_DEFINE_GLOBAL_STOPWATCH(stopwatch)
#define HISTOGRAM_DECLARE_GLOBAL_STOPWATCH(stopwatch)
#define HISTOGRAM_START_GLOBAL_STOPWATCH(stop_watch)
#define HISTOGRAM_STOP_GLOBAL_STOPWATCH(stop_watch)

#else  //  HISTOGRAM_COMPILE_FOR_TIMING is defined

#define HISTOGRAM_TIME_BLOCK HISTOGRAM_ALWAYS_TIME_BLOCK
#define HISTOGRAM_TIME_DESCRIBED_BLOCK HISTOGRAM_ALWAYS_TIME_DESCRIBED_BLOCK
#define HISTOGRAM_DEFINE_GLOBAL_STOPWATCH HISTOGRAM_ALWAYS_DEFINE_GLOBAL_STOPWATCH
#define HISTOGRAM_DECLARE_GLOBAL_STOPWATCH HISTOGRAM_ALWAYS_DECLARE_GLOBAL_STOPWATCH
#define HISTOGRAM_START_GLOBAL_STOPWATCH HISTOGRAM_ALWAYS_START_GLOBAL_STOPWATCH
#define HISTOGRAM_STOP_GLOBAL_STOPWATCH HISTOGRAM_ALWAYS_STOP_GLOBAL_STOPWATCH

#endif

// This first macro is the work-horse of this system.  It can be
// placed freely in any C++ code, and will time the duration from the
// insertion point, to the end of the enclosing block.  Note that
// several of these may be used in a single function, as each macro
// defines variable names that use the line number as a postfix, so as
// to avoid conflicts with surrounding timed regions.  It is common to
// use this simple unadorned (i.e., no special description) macro at
// the start of a function or method, to time the execution of the
// complete function.
#define HISTOGRAM_ALWAYS_TIME_BLOCK                                           \
  HISTOGRAM_DESCRIBED_BLOCK("" /* no description*/,                           \
                       HISTOGRAM_EXPAND_THEN_PASTE(histo_timer, __LINE__),    \
                       HISTOGRAM_EXPAND_THEN_PASTE(histo_stopwatch, __LINE__))

// The next 3 macros are ONLY used internally, by the above macro.
#define HISTOGRAM_PASTE(a, b) a ## b
#define HISTOGRAM_EXPAND_THEN_PASTE(pre, post) HISTOGRAM_PASTE(pre, post)
#define HISTOGRAM_DESCRIBED_BLOCK(description, timer_name, stopwatch_name)   \
  static HistogramTimer timer_name(__FILE__, __func__, __LINE__,             \
                              description);                                  \
  HistogramStopWatch stopwatch_name(&(timer_name));                          \
  (stopwatch_name).start();

//This is analagous to HISTOGRAM_TIME_BLOCK, but supports an explicit
//description of the block.  The description does not need to be
//quoted. This macro is commonly used in the interior of a function or
//method, and hence a description is valuably added to clarify the
//region that is being covered.
#define HISTOGRAM_ALWAYS_TIME_DESCRIBED_BLOCK(description)                        \
  HISTOGRAM_DESCRIBED_BLOCK(#description,                                         \
                       HISTOGRAM_EXPAND_THEN_PASTE(histogram_timer, __LINE__),    \
                       HISTOGRAM_EXPAND_THEN_PASTE(histogram_stopwatch, __LINE__))

// The next two macros should only be used at global scope.  The first
// one defines (and initializes) a variable by the given name. The
// second one may be used in a different file to declare the name for
// starting and/or stopping the stopwatch.
#define HISTOGRAM_ALWAYS_DEFINE_GLOBAL_STOPWATCH(stopwatch)          \
  HistogramTimer stopwatch ## _histogram_timer(#stopwatch);               \
  HistogramStopWatch stopwatch(& stopwatch ## _histogram_timer);

#define HISTOGRAM_ALWAYS_DECLARE_GLOBAL_STOPWATCH(stopwatch) \
  extern struct HistogramStopWatch stopwatch;

// Then next two macros are used in concert with the above GLOBAL
// declaration/definitions to start and stop associated stop watches.
// Note that they use a single global, and can only be used for a
// single timing interval (such as for traversing from the GC thread,
// to the VM thread, or vice versa).
#define HISTOGRAM_ALWAYS_START_GLOBAL_STOPWATCH(stopwatch)          \
  stopwatch.start(__FILE__, __func__, __LINE__);

#define HISTOGRAM_ALWAYS_STOP_GLOBAL_STOPWATCH(stopwatch)   \
  stopwatch.stop(__FILE__, __func__, __LINE__);

int print_with_commas(FILE *stream, const char* prefix, unsigned long value);
int print_time_value(FILE *stream,
                       const char* prefix,
                       double nanoseconds,
                       const char* postfix);
void print_spaces(FILE* stream, int count);

#endif // SHARE_UTILITIES_HISTOGRAMS_HPP
