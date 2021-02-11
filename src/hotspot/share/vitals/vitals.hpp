/*
 * Copyright (c) 2019, 2021 SAP SE. All rights reserved.
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 *
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

#ifndef HOTSPOT_SHARE_VITALS_VITALS_HPP
#define HOTSPOT_SHARE_VITALS_VITALS_HPP

#include "utilities/globalDefinitions.hpp"

class outputStream;
class Thread;

namespace sapmachine_vitals {

  bool initialize();
  void cleanup();

  struct print_info_t {
    bool raw;
    bool csv;
    // Omit printing a legend.
    bool no_legend;
    // Reverse printing order (default: youngest-to-oldest; reversed: oldest-to-youngest)
    bool reverse_ordering;

    size_t scale;

    // if true, sample and print the current values too. If false,
    // just print the sample tables.
    bool sample_now;

  };

  void default_settings(print_info_t* out);

  // Print report to stream. Leave print_info NULL for default settings.
  void print_report(outputStream* st, const print_info_t* print_info = NULL);

  // Dump both textual and csv style reports to two files, "vitals_<pid>.txt" and "vitals_<pid>.csv".
  // If these files exist, they are overwritten.
  void dump_reports();

  // For printing in thread lists only.
  const Thread* samplerthread();

  namespace counters {
    void inc_classes_loaded(size_t count);
    void inc_classes_unloaded(size_t count);
    void inc_threads_created(size_t count);
  };

};

#endif /* HOTSPOT_SHARE_VITALS_VITALS_HPP */
