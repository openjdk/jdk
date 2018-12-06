/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_LEAKPROFILER_STARTOPERATION_HPP
#define SHARE_VM_LEAKPROFILER_STARTOPERATION_HPP

#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/leakprofiler/leakProfiler.hpp"
#include "jfr/leakprofiler/sampling/objectSampler.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "logging/log.hpp"
#include "runtime/vmOperations.hpp"

// Safepoint operation for starting leak profiler object sampler
class StartOperation : public VM_Operation {
 private:
  jlong _sample_count;
 public:
  StartOperation(jlong sample_count) :
    _sample_count(sample_count) {
  }

  Mode evaluation_mode() const {
    return _safepoint;
  }

  VMOp_Type type() const {
    return VMOp_GC_HeapInspection;
  }

  virtual void doit() {
    assert(!LeakProfiler::is_running(), "invariant");
    jint queue_size = JfrOptionSet::old_object_queue_size();
    LeakProfiler::set_object_sampler(new ObjectSampler(queue_size));
    log_trace(jfr, system)( "Object sampling started");
  }
};

#endif // SHARE_VM_LEAKPROFILER_STARTOPERATION_HPP
