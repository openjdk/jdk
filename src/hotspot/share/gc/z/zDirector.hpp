/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZDIRECTOR_HPP
#define SHARE_GC_Z_ZDIRECTOR_HPP

#include "gc/shared/concurrentGCThread.hpp"
#include "gc/shared/gcCause.hpp"
#include "gc/z/zMetronome.hpp"

class ZDirector : public ConcurrentGCThread {
private:
  static const double one_in_1000;

  ZMetronome _metronome;

  void sample_allocation_rate() const;

  bool is_first() const;
  bool is_warm() const;

  bool rule_timer() const;
  bool rule_warmup() const;
  bool rule_allocation_rate() const;
  bool rule_proactive() const;
  GCCause::Cause make_gc_decision() const;

protected:
  virtual void run_service();
  virtual void stop_service();

public:
  ZDirector();
};

#endif // SHARE_GC_Z_ZDIRECTOR_HPP
