/*
 * Copyright (c) 1999, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_POSIX_SUSPENDRESUME_POSIX_HPP
#define OS_POSIX_SUSPENDRESUME_POSIX_HPP

// Suspend/resume support for POSIX platforms
// Protocol:
//
// a thread starts in SR_RUNNING
//
// SR_RUNNING can go to
//   * SR_SUSPEND_REQUEST when the WatcherThread wants to suspend it
// SR_SUSPEND_REQUEST can go to
//   * SR_RUNNING if WatcherThread decides it waited for SR_SUSPENDED too long (timeout)
//   * SR_SUSPENDED if the stopped thread receives the signal and switches state
// SR_SUSPENDED can go to
//   * SR_WAKEUP_REQUEST when the WatcherThread has done the work and wants to resume
// SR_WAKEUP_REQUEST can go to
//   * SR_RUNNING when the stopped thread receives the signal
//   * SR_WAKEUP_REQUEST on timeout (resend the signal and try again)
class SuspendResume {
public:
  enum State {
    SR_RUNNING,
    SR_SUSPEND_REQUEST,
    SR_SUSPENDED,
    SR_WAKEUP_REQUEST
  };

private:
  volatile State _state;

private:
  /* try to switch state from state "from" to state "to"
   * returns the state set after the method is complete
   */
  State switch_state(State from, State to);

public:
  SuspendResume() : _state(SR_RUNNING) { }

  State state() const { return _state; }

  State request_suspend() {
    return switch_state(SR_RUNNING, SR_SUSPEND_REQUEST);
  }

  State cancel_suspend() {
    return switch_state(SR_SUSPEND_REQUEST, SR_RUNNING);
  }

  State suspended() {
    return switch_state(SR_SUSPEND_REQUEST, SR_SUSPENDED);
  }

  State request_wakeup() {
    return switch_state(SR_SUSPENDED, SR_WAKEUP_REQUEST);
  }

  State running() {
    return switch_state(SR_WAKEUP_REQUEST, SR_RUNNING);
  }

  bool is_running() const {
    return _state == SR_RUNNING;
  }

  bool is_suspended() const {
    return _state == SR_SUSPENDED;
  }
};

#endif // OS_POSIX_SUSPENDRESUME_POSIX_HPP
