/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// Keeps track of the GC overhead (both concurrent and STW). It stores
// it in a large array and then prints it to tty at the end of the
// execution.

// See coTracker.hpp for the explanation on what groups are.

// Let's set a maximum number of concurrent overhead groups, to
// statically allocate any arrays we need and not to have to
// malloc/free them. This is just a bit more convenient.
enum {
  MaxGCOverheadGroupNum = 4
};

typedef struct {
  double _start_sec;
  double _end_sec;

  double _conc_overhead[MaxGCOverheadGroupNum];
  double _stw_overhead;
} GCOverheadReporterEntry;

class GCOverheadReporter {
  friend class COReportingThread;

private:
  enum PrivateConstants {
    DefaultReporterLength = 128 * 1024
  };

  // Reference to the single instance of this class.
  static GCOverheadReporter* _reporter;

  // These three references point to the array that contains the GC
  // overhead entries (_base is the base of the array, _top is the
  // address passed the last entry of the array, _curr is the next
  // entry to be used).
  GCOverheadReporterEntry* _base;
  GCOverheadReporterEntry* _top;
  GCOverheadReporterEntry* _curr;

  // The number of concurrent overhead groups.
  size_t _group_num;

  // The wall-clock time of the end of the last recorded period of GC
  // overhead.
  double _prev_end_sec;

  // Names for the concurrent overhead groups.
  const char* _group_names[MaxGCOverheadGroupNum];

  // Add a new entry to the large array. conc_overhead being NULL is
  // equivalent to an array full of 0.0s. conc_overhead should have a
  // length of at least _group_num.
  void add(double start_sec, double end_sec,
           double* conc_overhead,
           double stw_overhead);

  // Add an entry that represents concurrent GC overhead.
  // conc_overhead must be at least of length _group_num.
  // conc_overhead being NULL is equivalent to an array full of 0.0s.
  void add_conc_overhead(double start_sec, double end_sec,
                         double* conc_overhead) {
    add(start_sec, end_sec, conc_overhead, 0.0);
  }

  // Add an entry that represents STW GC overhead.
  void add_stw_overhead(double start_sec, double end_sec,
                        double stw_overhead) {
    add(start_sec, end_sec, NULL, stw_overhead);
  }

  // It records the start of a STW pause (i.e. it records the
  // concurrent overhead up to that point)
  void record_stw_start(double start_sec);

  // It records the end of a STW pause (i.e. it records the overhead
  // associated with the pause and adjusts all the trackers to reflect
  // the pause)
  void record_stw_end(double end_sec);

  // It queries all the trackers of their concurrent overhead and
  // records it.
  void collect_and_record_conc_overhead(double end_sec);

  // It prints the contents of the GC overhead array
  void print() const;


  // Constructor. The same preconditions for group_num and group_names
  // from initGCOverheadReporter apply here too.
  GCOverheadReporter(size_t group_num,
                     const char* group_names[],
                     size_t length = DefaultReporterLength);

public:

  // statics

  // It initialises the GCOverheadReporter and launches the concurrent
  // overhead reporting thread. Both actions happen only if the
  // GCOverheadReporting parameter is set. The length of the
  // group_names array should be >= group_num and group_num should be
  // <= MaxGCOverheadGroupNum. Entries group_namnes[0..group_num-1]
  // should not be NULL.
  static void initGCOverheadReporter(size_t group_num,
                                     const char* group_names[]);

  // The following three are provided for convenience and they are
  // wrappers around record_stw_start(start_sec), record_stw_end(end_sec),
  // and print(). Each of these checks whether GC overhead reporting
  // is on (i.e. _reporter != NULL) and, if it is, calls the
  // corresponding method. Saves from repeating this pattern again and
  // again from the places where they need to be called.
  static void recordSTWStart(double start_sec);
  static void recordSTWEnd(double end_sec);
  static void printGCOverhead();
};
