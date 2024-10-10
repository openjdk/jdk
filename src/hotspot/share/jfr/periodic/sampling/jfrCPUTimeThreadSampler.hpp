/*
 * Copyright (c) 2024, SAP SE. All rights reserved.
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

#ifndef SHARE_JFR_PERIODIC_SAMPLING_JFRCPUTIMETHREADSAMPLER_HPP
#define SHARE_JFR_PERIODIC_SAMPLING_JFRCPUTIMETHREADSAMPLER_HPP

#include "jfr/utilities/jfrAllocation.hpp"

class JavaThread;

#if defined(LINUX)


class JfrCPUTimeThreadSampler;

class JfrCPUTimeThreadSampling : public JfrCHeapObj {
  friend class JfrRecorder;
 private:

  JfrCPUTimeThreadSampler* _sampler;

  void create_sampler(int64_t period_millis);
  void set_sampling_period(int64_t period_millis);

  JfrCPUTimeThreadSampling();
  ~JfrCPUTimeThreadSampling();

  static JfrCPUTimeThreadSampling& instance();
  static JfrCPUTimeThreadSampling* create();
  static void destroy();

  static void set_actual_sample_period(int64_t period_millis);
  void update_run_state(int64_t period_millis);

 public:
  static void set_sample_period(int64_t period_millis);
  static int64_t get_actual_sample_period();
  static void set_throttle(double throttle);

  static void on_javathread_create(JavaThread* thread);
  static void on_javathread_terminate(JavaThread* thread);
  void handle_timer_signal(void* context);
};

#else

// a basic implementation on other platforms that
// emits warnings

class JfrCPUTimeThreadSampling : public JfrCHeapObj {
  friend class JfrRecorder;
private:
  static JfrCPUTimeThreadSampling& instance();
  static JfrCPUTimeThreadSampling* create();
  static void destroy();

 public:
  static void set_sample_period(int64_t period_millis);
  static int64_t get_actual_sample_period();
  static void set_throttle(double throttle);

  static void on_javathread_create(JavaThread* thread);
  static void on_javathread_terminate(JavaThread* thread);
};

#endif // defined(LINUX)


#endif // SHARE_JFR_PERIODIC_SAMPLING_JFRCPUTIMETHREADSAMPLER_HPP
