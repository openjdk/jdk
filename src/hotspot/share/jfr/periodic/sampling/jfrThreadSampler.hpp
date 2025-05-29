/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_PERIODIC_SAMPLING_JFRTHREADSAMPLER_HPP
#define SHARE_JFR_PERIODIC_SAMPLING_JFRTHREADSAMPLER_HPP

#include "jfr/utilities/jfrAllocation.hpp"

class JfrThreadSampler : public JfrCHeapObj {
  friend class JfrRecorder;
 private:
  void create_sampler(int64_t java_period_millis, int64_t native_period_millis);
  void update_run_state(int64_t java_period_millis, int64_t native_period_millis);
  void set_period(bool is_java_period, int64_t period_millis);

  JfrThreadSampler();
  ~JfrThreadSampler();

  static JfrThreadSampler& instance();
  static JfrThreadSampler* create();
  static void destroy();

 public:
  static void set_java_sample_period(int64_t period_millis);
  static void set_native_sample_period(int64_t period_millis);
};

#endif // SHARE_JFR_PERIODIC_SAMPLING_JFRTHREADSAMPLER_HPP
