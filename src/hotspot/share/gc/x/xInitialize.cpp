/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/x/xAddress.hpp"
#include "gc/x/xBarrierSet.hpp"
#include "gc/x/xCPU.hpp"
#include "gc/x/xGlobals.hpp"
#include "gc/x/xHeuristics.hpp"
#include "gc/x/xInitialize.hpp"
#include "gc/x/xLargePages.hpp"
#include "gc/x/xNUMA.hpp"
#include "gc/x/xStat.hpp"
#include "gc/x/xThreadLocalAllocBuffer.hpp"
#include "gc/x/xTracer.hpp"
#include "logging/log.hpp"
#include "runtime/vm_version.hpp"

XInitialize::XInitialize(XBarrierSet* barrier_set) {
  log_info(gc, init)("Initializing %s", XName);
  log_info(gc, init)("Version: %s (%s)",
                     VM_Version::vm_release(),
                     VM_Version::jdk_debug_level());
  log_info(gc, init)("Using deprecated non-generational mode");

  // Early initialization
  XAddress::initialize();
  XNUMA::initialize();
  XCPU::initialize();
  XStatValue::initialize();
  XThreadLocalAllocBuffer::initialize();
  XTracer::initialize();
  XLargePages::initialize();
  XHeuristics::set_medium_page_size();
  XBarrierSet::set_barrier_set(barrier_set);

  pd_initialize();
}
