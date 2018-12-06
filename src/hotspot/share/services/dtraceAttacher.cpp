/*
 * Copyright (c) 2006, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "code/codeCache.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/flags/jvmFlag.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vmOperations.hpp"
#include "services/dtraceAttacher.hpp"

#ifdef SOLARIS

class VM_DeoptimizeTheWorld : public VM_Operation {
 public:
  VMOp_Type type() const {
    return VMOp_DeoptimizeTheWorld;
  }
  void doit() {
    CodeCache::mark_all_nmethods_for_deoptimization();
    ResourceMark rm;
    DeoptimizationMarker dm;
    // Deoptimize all activations depending on marked methods
    Deoptimization::deoptimize_dependents();

    // Mark the dependent methods non entrant
    CodeCache::make_marked_nmethods_not_entrant();
  }
};

static void set_bool_flag(const char* flag, bool value) {
  JVMFlag::boolAtPut((char*)flag, strlen(flag), &value,
                              JVMFlag::ATTACH_ON_DEMAND);
}

// Enable only the "fine grained" flags. Do *not* touch
// the overall "ExtendedDTraceProbes" flag.
void DTrace::enable_dprobes(int probes) {
  bool changed = false;
  if (!DTraceAllocProbes && (probes & DTRACE_ALLOC_PROBES)) {
    set_bool_flag("DTraceAllocProbes", true);
    changed = true;
  }
  if (!DTraceMethodProbes && (probes & DTRACE_METHOD_PROBES)) {
    set_bool_flag("DTraceMethodProbes", true);
    changed = true;
  }
  if (!DTraceMonitorProbes && (probes & DTRACE_MONITOR_PROBES)) {
    set_bool_flag("DTraceMonitorProbes", true);
    changed = true;
  }

  if (changed) {
    // one or more flags changed, need to deoptimize
    VM_DeoptimizeTheWorld op;
    VMThread::execute(&op);
  }
}

// Disable only the "fine grained" flags. Do *not* touch
// the overall "ExtendedDTraceProbes" flag.
void DTrace::disable_dprobes(int probes) {
  bool changed = false;
  if (DTraceAllocProbes && (probes & DTRACE_ALLOC_PROBES)) {
    set_bool_flag("DTraceAllocProbes", false);
    changed = true;
  }
  if (DTraceMethodProbes && (probes & DTRACE_METHOD_PROBES)) {
    set_bool_flag("DTraceMethodProbes", false);
    changed = true;
  }
  if (DTraceMonitorProbes && (probes & DTRACE_MONITOR_PROBES)) {
    set_bool_flag("DTraceMonitorProbes", false);
    changed = true;
  }
  if (changed) {
    // one or more flags changed, need to deoptimize
    VM_DeoptimizeTheWorld op;
    VMThread::execute(&op);
  }
}

// Do clean-up on "all door clients detached" event.
void DTrace::detach_all_clients() {
  /*
   * We restore the state of the fine grained flags
   * to be consistent with overall ExtendedDTraceProbes.
   * This way, we will honour command line setting or the
   * last explicit modification of ExtendedDTraceProbes by
   * a call to set_extended_dprobes.
   */
  if (ExtendedDTraceProbes) {
    enable_dprobes(DTRACE_ALL_PROBES);
  } else {
    disable_dprobes(DTRACE_ALL_PROBES);
  }
}

void DTrace::set_extended_dprobes(bool flag) {
  // explicit setting of ExtendedDTraceProbes flag
  set_bool_flag("ExtendedDTraceProbes", flag);

  // make sure that the fine grained flags reflect the change.
  if (flag) {
    enable_dprobes(DTRACE_ALL_PROBES);
  } else {
    /*
     * FIXME: Revisit this: currently all-client-detach detection
     * does not work and hence disabled. The following scheme does
     * not work. So, we have to disable fine-grained flags here.
     *
     * disable_dprobes call has to be delayed till next "detach all "event.
     * This is to be  done so that concurrent DTrace clients that may
     * have enabled one or more fine grained dprobes and may be running
     * still. On "detach all" clients event, we would sync ExtendedDTraceProbes
     * with  fine grained flags which would take care of disabling fine grained flags.
     */
    disable_dprobes(DTRACE_ALL_PROBES);
  }
}

void DTrace::set_monitor_dprobes(bool flag) {
  // explicit setting of DTraceMonitorProbes flag
  set_bool_flag("DTraceMonitorProbes", flag);
}

#endif /* SOLARIS */
