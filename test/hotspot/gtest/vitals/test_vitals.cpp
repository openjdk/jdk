/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 SAP SE. All rights reserved.
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
#include "runtime/globals.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"
#include "utilities/globalDefinitions.hpp"
#include "unittest.hpp"
#include "vitals/vitals.hpp"

//#define LOG(s) tty->print_raw(s);
#define LOG(s)

TEST_VM(vitals, report_with_explicit_default_settings) {
  char tmp[64*K];
  stringStream ss(tmp, sizeof(tmp));
  sapmachine_vitals::print_info_t info;
  ::memset(&info, 0xBB, sizeof(info));
  sapmachine_vitals::default_settings(&info);
  sapmachine_vitals::print_report(&ss, &info);
  LOG(tmp);
  if (EnableVitals) {
    ASSERT_NE(::strstr(tmp, "--jvm--"), (char*)NULL);
  }
}

TEST_VM(vitals, report_with_implicit_default_settings) {
  char tmp[64*K];
  stringStream ss(tmp, sizeof(tmp));
  sapmachine_vitals::print_report(&ss, NULL);
  LOG(tmp);
  if (EnableVitals) {
    ASSERT_NE(::strstr(tmp, "--jvm--"), (char*)NULL);
  }
}

TEST_VM(vitals, report_with_nownow) {
  char tmp[64*K];
  stringStream ss(tmp, sizeof(tmp));
  sapmachine_vitals::print_info_t info;
  ::memset(&info, 0xBB, sizeof(info));
  sapmachine_vitals::default_settings(&info);
  info.sample_now = true;
  for (int i = 0; i < 100; i ++) {
    ss.reset();
    sapmachine_vitals::print_report(&ss, NULL);
    if (EnableVitals) {
      ASSERT_NE(::strstr(tmp, "--jvm--"), (char*)NULL);
    }
  }
}
