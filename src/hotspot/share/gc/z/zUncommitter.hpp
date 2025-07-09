/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZUNCOMMITTER_HPP
#define SHARE_GC_Z_ZUNCOMMITTER_HPP

#include "gc/z/zLock.hpp"
#include "gc/z/zThread.hpp"
#include "utilities/ticks.hpp"

class ZPartition;

class ZUncommitter : public ZThread {
private:
  const uint32_t         _id;
  ZPartition* const      _partition;
  mutable ZConditionLock _lock;
  bool                   _stop;
  double                 _cancel_time;
  uint64_t               _next_cycle_timeout;
  uint64_t               _next_uncommit_timeout;
  double                 _cycle_start;
  size_t                 _to_uncommit;
  size_t                 _uncommitted;

  bool wait(uint64_t timeout) const;
  bool should_continue() const;

  uint64_t to_millis(double seconds) const;

  void update_next_cycle_timeout(double from_time);
  void update_next_cycle_timeout_on_cancel();
  void update_next_cycle_timeout_on_finish();

  void reset_uncommit_cycle();
  void deactivate_uncommit_cycle();
  bool activate_uncommit_cycle();
  void register_uncommit(size_t size);

  bool uncommit_cycle_is_finished() const;
  bool uncommit_cycle_is_active() const;
  bool uncommit_cycle_is_canceled() const;

  size_t uncommit();

  void update_statistics(size_t uncommitted, Ticks start, Tickspan* accumulated_time) const;

protected:
  virtual void run_thread();
  virtual void terminate();

public:
  ZUncommitter(uint32_t id, ZPartition* partition);

  void cancel_uncommit_cycle();
};

#endif // SHARE_GC_Z_ZUNCOMMITTER_HPP
