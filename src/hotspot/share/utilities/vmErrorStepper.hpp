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

#ifndef SHARE_UTILITIES_VMERROR_STEPPER_HPP
#define SHARE_UTILITIES_VMERROR_STEPPER_HPP

#include "utilities/globalDefinitions.hpp"

// Helper class that remembers the place where an instruction caused
// a secondary crash during error reporting, and can fast forward
// beyond that point to resume error reporting.
class VMErrorStepper : public AllStatic {

// Factor to translate the timestamp to seconds.
#define TIMESTAMP_TO_SECONDS_FACTOR (1000 * 1000 * 1000)

#define STEPS_SIZE 96

private:
  static const char* _identity;
  static const char* _primary[STEPS_SIZE];
  static int _steps;
  static int _secondary[STEPS_SIZE];

  // Timeout handling:
  // Timestamp at which error reporting started; -1 if no error reporting in progress.
  static volatile jlong _reporting_start_time;
  // Whether or not error reporting did timeout.
  static volatile bool _reporting_did_timeout;
  // Timestamp at which the last error reporting step started; -1 if no error reporting
  //   in progress.
  static volatile jlong _step_start_time;
  // Whether or not the last error reporting step did timeout.
  static volatile bool _step_did_timeout;

  // Helper function to get the current timestamp.
  static jlong get_current_timestamp();

public:
  // Identity must be unique!
  static void mark(const char* identity) { _identity = (char*)identity; _steps = 0; }
  static const char* identify() { return _identity; }
  static bool step();
  static void reset();

  static bool check_reporting_timeout(jlong timeout);
  static bool reporting_did_timeout() { return _reporting_did_timeout; }
  static int64_t reporting_timeout() { return (int64_t)
    ((get_current_timestamp() - _reporting_start_time) / TIMESTAMP_TO_SECONDS_FACTOR); }

  static bool check_step_timeout(jlong timeout);
  static bool step_did_timeout() { return _step_did_timeout; }
  static int64_t step_timeout() { return (int64_t)
    ((get_current_timestamp() - _step_start_time) / TIMESTAMP_TO_SECONDS_FACTOR); }

  // Accessors to get/set the start times for step and total timeout.
  static void record_reporting_start_time();
  static jlong get_reporting_start_time();
  static void record_step_start_time();
  static jlong get_step_start_time();
  static void clear_step_start_time();
};

#endif // SHARE_UTILITIES_VMERROR_STEPPER_HPP
