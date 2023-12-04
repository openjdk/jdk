/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "logging/log.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "runtime/task.hpp"
#include "services/rsswatch.hpp"
#include "utilities/parseInteger.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

static void check_rss(size_t limit) {
  const size_t rss = os::get_rss();
  log_trace(os, rss)("Rss=%zu", rss);
  if (rss >= limit) {
    fatal("Resident Set Size (%zu bytes) reached RssLimit (%zu bytes).", rss, limit);
  }
}

class RssAbsoluteLimitTask : public PeriodicTask {
  const size_t _limit;
public:
  RssAbsoluteLimitTask(size_t limit, unsigned interval_ms) :
    PeriodicTask(interval_ms), _limit(limit) {
    log_info(os, rss)("RssWatcher task: interval=%ums, limit=" PROPERFMT,
                      interval(), PROPERFMTARGS(_limit));
  }
  void task() override {
    check_rss(_limit);
  }
};

class RssRelativeLimitTask : public PeriodicTask {
  const double _percent;
  size_t _limit;

  void update_limit() {
    const size_t limit_100 = os::physical_memory();
    const size_t limit_x = (size_t)(((double)limit_100 * _percent) / 100.0f);
    if (limit_x != _limit) { // limit changed?
      _limit = limit_x;
      log_info(os, rss)("Setting RssWatcher limit to %zu bytes (%.2f%% of total memory of %zu bytes)",
                        limit_x, _percent, limit_100);
    }
  }

public:

  RssRelativeLimitTask(double limit_percent, unsigned interval_ms) :
    PeriodicTask(interval_ms), _percent(limit_percent), _limit(0) {
    log_info(os, rss)("RssWatcher task: interval=%ums, "
                      "limit=%.2f%% of total memory", interval(), _percent);
  }

  void task() override {
    update_limit();
    check_rss(_limit);
  }
};

static bool parse_percentage(const char* s, const char** tail, double* percentage) {
  double v = 0;
  char sign;
  int chars_read = 0;
  if (sscanf(s, "%lf%c%n", &v, &sign, &chars_read) >= 2 && sign == '%') {
    if (v > 100.0) {
      vm_exit_during_initialization("Failed to parse RssLimit", "Not a valid percentage");
    }
    *percentage = v;
    *tail = s + chars_read;
    return true;
  }
  return false;
}

static void parse_interval(const char* s, unsigned* interval) {
  char* tail;
  unsigned v = 0;
  if (!parse_integer_impl<unsigned>(s + 1, &tail, 10, &v)) {
    vm_exit_during_initialization("Failed to parse RssLimit", "Invalid interval");
  }
  if (strcmp(tail, "s") == 0) {
    v *= 1000;
  } else if (strcmp(tail, "ms") == 0) {
    // okay, ignored
  } else {
    vm_exit_during_initialization("Failed to parse RssLimit", "Invalid or missing interval unit");
  }
  // PeriodicTask has some limitations:
  // - minimum task time
  // - task time aligned to (non-power-of-2) alignment.
  // For convenience, we just adjust the interval.
  unsigned v2 = v;
  v2 /= PeriodicTask::interval_gran;
  v2 *= PeriodicTask::interval_gran;
  v2 = MAX2(v2, (unsigned)PeriodicTask::min_interval);
  if (v2 != v) {
    log_warning(os, rss)("RssLimit interval has been adjusted to %ums", v2);
  }
  (*interval) = v2;
}

void RssWatcher::initialize(const char* limit_option) {
  assert(limit_option != nullptr, "Invalid argument");

  // Format:
  // RssLimit=<size>[,<check frequency>]
  // <size>:              <percentage limit>|<absolute limit>
  // <percentage limit>:  <number between 0 and 100>%
  // <absolute limit>:    <memory size>
  // <check frequency>:   <number><unit>
  // <unit>:              "ms"|"s"

  // Examples:
  // RssLimit=100m
  // RssLimit=80%
  // RssLimit=80%,100ms
  // RssLimit=2g,30s
  static constexpr unsigned default_interval = MIN2((unsigned)PeriodicTask::max_interval, 10000u);
  unsigned interval = default_interval;
  bool is_absolute = true;
  size_t limit = 0;
  double percentage = 0;
  const char* s = limit_option;

  if (parse_percentage(s, &s, &percentage)) {
    is_absolute = false;
  } else {
    if (!parse_integer(s, (char**)&s, &limit) || limit == 0) {
      vm_exit_during_initialization("Failed to parse RssLimit", "Not a valid limit size");
    }
  }
  if (s[0] != '\0') {
    if (s[0] != ',') {
      vm_exit_during_initialization("Failed to parse RssLimit", "Expected comma");
    }
    parse_interval(s, &interval);
  }

  PeriodicTask* const task = (is_absolute) ?
       (PeriodicTask*) new RssAbsoluteLimitTask(limit, interval) :
       (PeriodicTask*) new RssRelativeLimitTask(percentage, interval);
  task->enroll();
}
