/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_PERIODIC_SAMPLING_JFRSAMPLEMONITOR_HPP
#define SHARE_JFR_PERIODIC_SAMPLING_JFRSAMPLEMONITOR_HPP

#include "jfr/periodic/sampling/jfrSampleRequest.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "memory/allocation.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutex.hpp"

class JfrSampleMonitor : public StackObj {
 private:
  JfrThreadLocal* const _tl;
  Monitor* const _sample_monitor;
  mutable bool _waiting;
 public:
  JfrSampleMonitor(JfrThreadLocal* tl) :
    _tl(tl), _sample_monitor(tl->sample_monitor()), _waiting(false) {
    assert(tl != nullptr, "invariant");
    assert(_sample_monitor != nullptr, "invariant");
    _sample_monitor->lock_without_safepoint_check();
  }

  bool is_waiting() const {
    assert_lock_strong(_sample_monitor);
    _waiting = _tl->sample_state() == WAITING_FOR_NATIVE_SAMPLE;
    return _waiting;
  }

  void install_java_sample_request() {
    assert_lock_strong(_sample_monitor);
    assert(_waiting, "invariant");
    assert(_tl->sample_state() == WAITING_FOR_NATIVE_SAMPLE, "invariant");
    JfrSampleRequest request;
    request._sample_ticks = JfrTicks::now();
    _tl->set_sample_request(request);
    _tl->set_sample_state(JAVA_SAMPLE);
    _sample_monitor->notify_all();
  }

  ~JfrSampleMonitor() {
    assert_lock_strong(_sample_monitor);
    if (!_waiting) {
      _tl->set_sample_state(NO_SAMPLE);
      _sample_monitor->notify_all();
    }
    _sample_monitor->unlock();
  }
};

#endif // SHARE_JFR_PERIODIC_SAMPLING_JFRSAMPLEMONITOR_HPP
