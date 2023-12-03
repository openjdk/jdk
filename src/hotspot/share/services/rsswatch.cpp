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
#include "runtime/os.hpp"
#include "runtime/java.hpp"
#include "runtime/task.hpp"
#include "services/rsswatch.hpp"
#include "utilities/parseInteger.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

static void check_rss(size_t limit) {
  const size_t rss = os::get_rss();
  log_trace(os, rss)("Rss=%zu", rss);
  if (rss >= limit) {
    vm_exit_out_of_memory(0, OOM_INTERNAL_LIMIT_ERROR,
                          "Resident Set Size (%zu bytes) reached RssLimit (%zu bytes).", rss, limit);
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

void RssWatcher::initialize(const char* limit_option, unsigned interval_ms) {
  assert(limit_option != nullptr && interval_ms > 0, "Invalid arguments");
  // Limit can be given as either absolute number or as a percentage of the available
  // memory on that machine.
  PeriodicTask* task = nullptr;

  // Percentage?
  double perc = 0;
  char sign;
  if (sscanf(limit_option, "%lf%c", &perc, &sign) == 2 && sign == '%') {
    if (perc > 100) {
      vm_exit_during_initialization("Failed to parse RssLimit", "Not a valid percentage");
    }
    task = new RssRelativeLimitTask(perc, interval_ms);
  } else {
    size_t limit = 0;
    if (!parse_integer(limit_option, &limit) || limit == 0) {
      vm_exit_during_initialization("Failed to parse RssLimit", "Not a valid limit size");
    }
    task = new RssAbsoluteLimitTask(limit, interval_ms);
  }
  if (task != nullptr) {
    task->enroll();
  }
}
