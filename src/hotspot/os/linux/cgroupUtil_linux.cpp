/*
 * Copyright (c) 2024, Red Hat, Inc.
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

#include "cgroupUtil_linux.hpp"

int CgroupUtil::processor_count(CgroupCpuController* cpu_ctrl, int host_cpus) {
  assert(host_cpus > 0, "physical host cpus must be positive");
  int limit_count = host_cpus;
  int quota  = cpu_ctrl->cpu_quota();
  int period = cpu_ctrl->cpu_period();
  int quota_count = 0;
  int result = 0;

  if (quota > -1 && period > 0) {
    quota_count = ceilf((float)quota / (float)period);
    log_trace(os, container)("CPU Quota count based on quota/period: %d", quota_count);
  }

  // Use quotas
  if (quota_count != 0) {
    limit_count = quota_count;
  }

  result = MIN2(host_cpus, limit_count);
  log_trace(os, container)("OSContainer::active_processor_count: %d", result);
  return result;
}
