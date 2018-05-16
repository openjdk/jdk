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

#ifndef SHARE_VM_JFR_LEAKPROFILER_LEAKPROFILER_HPP
#define SHARE_VM_JFR_LEAKPROFILER_LEAKPROFILER_HPP

#include "memory/allocation.hpp"

class BoolObjectClosure;
class ObjectSampler;
class OopClosure;
class Thread;

class LeakProfiler : public AllStatic {
  friend class ClassUnloadTypeSet;
  friend class EmitEventOperation;
  friend class ObjectSampleCheckpoint;
  friend class StartOperation;
  friend class StopOperation;
  friend class TypeSet;
  friend class WriteObjectSampleStacktrace;

 private:
  static ObjectSampler* _object_sampler;

  static void set_object_sampler(ObjectSampler* object_sampler);
  static ObjectSampler* object_sampler();

  static void suspend();
  static void resume();
  static bool is_suspended();

 public:
  static bool start(jint sample_count);
  static bool stop();
  static void emit_events(jlong cutoff_ticks, bool emit_all);
  static bool is_running();

  static void sample(HeapWord* object, size_t size, JavaThread* thread);

  // Called by GC
  static void oops_do(BoolObjectClosure* is_alive, OopClosure* f);
};

#endif // SHARE_VM_JFR_LEAKPROFILER_LEAKPROFILER_HPP
