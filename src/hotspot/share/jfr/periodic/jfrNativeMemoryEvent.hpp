/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_PERIODIC_JFRNATIVEMEMORYEVENT_HPP
#define SHARE_JFR_PERIODIC_JFRNATIVEMEMORYEVENT_HPP

#include "nmt/memflags.hpp"
#include "nmt/nmtUsage.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"

// MemJFRReporter is only used by threads sending periodic JFR
// events. These threads are synchronized at a higher level,
// so no more synchronization is needed.
class JfrNativeMemoryEvent : public AllStatic {
private:
  static void send_type_event(const Ticks& starttime, MEMFLAGS flag, size_t reserved, size_t committed);
 public:
  static void send_total_event(const Ticks& timestamp);
  static void send_type_events(const Ticks& timestamp);
};

#endif //SHARE_JFR_PERIODIC_JFRNATIVEMEMORYEVENT_HPP
