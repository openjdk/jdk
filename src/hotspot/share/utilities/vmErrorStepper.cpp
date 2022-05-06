/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "utilities/vmErrorStepper.hpp"

const char*       VMErrorStepper::_identity = "";
const char*       VMErrorStepper::_primary[] = {0};
int               VMErrorStepper::_steps = 0;
int               VMErrorStepper::_secondary[] = {0};
volatile jlong    VMErrorStepper::_reporting_start_time = -1;
volatile bool     VMErrorStepper::_reporting_did_timeout = false;
volatile jlong    VMErrorStepper::_step_start_time = -1;
volatile bool     VMErrorStepper::_step_did_timeout = false;

void VMErrorStepper::reset() {
  mark("");
  for (int i=0; i<STEPS_SIZE; i++) {
    _primary[i] = NULL;
    _secondary[i] = 0;
  }
}

bool VMErrorStepper::step() {
  _steps++;

  int i;
  for (i=0; i<STEPS_SIZE; i++) {
    if (_primary[i] == NULL) {
      break;
    } else {
      if (strcmp(_primary[i], _identity) == 0) {
        if (_secondary[i] >= _steps) {
          return false;
        } else {
          break;
        }
      }
    }
  }

#ifndef PRODUCT
  if (i == STEPS_SIZE) {
    fprintf(stderr, "ERROR: VMErrorStepper::STEPS_SIZE is too small!\n");
    exit(-1);
  }
#endif

  _primary[i] = _identity;
  _secondary[i] = _steps;

  record_step_start_time();
  _step_did_timeout = false;

  return true;
}

bool VMErrorStepper::check_reporting_timeout(jlong timeout) {
  const jlong reporting_start_time_l = get_reporting_start_time();
  const jlong now = get_current_timestamp();
  // Timestamp is stored in nanos.
  if (reporting_start_time_l > 0) {
    const jlong end = reporting_start_time_l + (jlong)ErrorLogTimeout * TIMESTAMP_TO_SECONDS_FACTOR;
    if (end <= now && !_reporting_did_timeout) {
      // We hit ErrorLogTimeout and we haven't interrupted the reporting
      // thread yet.
      _reporting_did_timeout = true;
    }
  }
  return _reporting_did_timeout;
}

bool VMErrorStepper::check_step_timeout(jlong timeout) {
  const jlong step_start_time_l = get_step_start_time();
  const jlong now = get_current_timestamp();
  if (step_start_time_l > 0) {
    // A step times out after a quarter of the total timeout. Steps are mostly fast unless they
    // hang for some reason, so this simple rule allows for three hanging step and still
    // hopefully leaves time enough for the rest of the steps to finish.
    const jlong end = step_start_time_l + timeout * TIMESTAMP_TO_SECONDS_FACTOR / 4;
    if (end <= now && !_step_did_timeout) {
      // The step timed out and we haven't interrupted the reporting
      // thread yet.
      _step_did_timeout = true;
    }
  }
  return _step_did_timeout;
}

// Helper, return current timestamp for timeout handling.
jlong VMErrorStepper::get_current_timestamp() {
  return os::javaTimeNanos();
}

void VMErrorStepper::record_reporting_start_time() {
  const jlong now = get_current_timestamp();
  Atomic::store(&_reporting_start_time, now);
}

jlong VMErrorStepper::get_reporting_start_time() {
  return Atomic::load(&_reporting_start_time);
}

void VMErrorStepper::record_step_start_time() {
  const jlong now = get_current_timestamp();
  Atomic::store(&_step_start_time, now);
}

jlong VMErrorStepper::get_step_start_time() {
  return Atomic::load(&_step_start_time);
}

void VMErrorStepper::clear_step_start_time() {
  return Atomic::store(&_step_start_time, (jlong)0);
}
