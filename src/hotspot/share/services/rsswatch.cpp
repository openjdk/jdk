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
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "runtime/task.hpp"
#include "services/rsswatch.hpp"
#include "utilities/parseInteger.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class RssLimitTask : public PeriodicTask {
protected:
  RssLimitTask() : PeriodicTask(RssLimitCheckInterval) {}
  void check_rss(size_t limit) {
    const size_t rss = os::get_RSS();
    log_trace(os, rss)("Rss=%zu", rss);
    if (rss >= limit) {
      fatal("Resident Set Size (%zu bytes) reached RssLimit (%zu bytes).", rss, limit);
    }
  }
};

class RssAbsoluteLimitTask : public RssLimitTask {
  const size_t _limit;
public:
  RssAbsoluteLimitTask(size_t limit) :
    _limit(limit) {
    log_info(os, rss)("RssWatcher task: interval=%ums, limit=" PROPERFMT,
                      interval(), PROPERFMTARGS(_limit));
  }
  void task() override {
    check_rss(_limit);
  }
};

class RssRelativeLimitTask : public RssLimitTask {
  const unsigned _percent;
  size_t _limit;

  void update_limit() {
    const size_t limit_100 = os::physical_memory();
    const size_t limit_x = (size_t)(((double)limit_100 * _percent) / 100.0f);
    if (limit_x != _limit) { // limit changed?
      _limit = limit_x;
      log_info(os, rss)("Setting RssWatcher limit to %zu bytes (%u%% of total memory of %zu bytes)",
                        limit_x, _percent, limit_100);
    }
  }

public:

  RssRelativeLimitTask(unsigned percent) :
    _percent(percent), _limit(0) {
    log_info(os, rss)("RssWatcher task: interval=%ums, "
                      "limit=%u%% of total memory", interval(), _percent);
  }

  void task() override {
    update_limit();
    check_rss(_limit);
  }
};

void RssWatcher::initialize() {

  if (RssLimit == 0 && RssLimitPercent == 0) {
    return;
  }

  if (RssLimit > 0 && RssLimitPercent > 0) {
    vm_exit_during_initialization("Please specify either RssLimit or RssLimitPercent, but not both");
  }

  if (os::get_RSS() == 0) {
    log_warning(os, rss)("RssLimit specified, but not supported by the Operating System.");
    return;
  }

  // Sanity-check the interval given. We use PeriodicTask, and that has some limitations:
  // - minimum task time
  // - task time aligned to (non-power-of-2) alignment.
  // For convenience, we just adjust the interval.
  unsigned interval = RssLimitCheckInterval;
  interval /= PeriodicTask::interval_gran;
  interval *= PeriodicTask::interval_gran;
  interval = MAX2(interval, (unsigned)PeriodicTask::min_interval);
  if (interval != RssLimitCheckInterval) {
    log_warning(os, rss)("RssLimit interval has been adjusted to %ums", interval);
  }

  RssLimitTask* const task = (RssLimit > 0) ?
       (RssLimitTask*)new RssAbsoluteLimitTask(RssLimit) :
       (RssLimitTask*)new RssRelativeLimitTask(RssLimitPercent);

  task->enroll();
}
