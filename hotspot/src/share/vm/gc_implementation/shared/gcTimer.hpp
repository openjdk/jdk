/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_SHARED_GCTIMER_HPP
#define SHARE_VM_GC_IMPLEMENTATION_SHARED_GCTIMER_HPP

#include "memory/allocation.hpp"
#include "prims/jni_md.h"
#include "utilities/macros.hpp"

class ConcurrentPhase;
class GCPhase;
class PausePhase;

template <class E> class GrowableArray;

class PhaseVisitor {
 public:
  virtual void visit(GCPhase* phase) = 0;
  virtual void visit(PausePhase* phase) { visit((GCPhase*)phase); }
  virtual void visit(ConcurrentPhase* phase) { visit((GCPhase*)phase); }
};

class GCPhase {
  const char* _name;
  int _level;
  jlong _start;
  jlong _end;

 public:
  void set_name(const char* name) { _name = name; }
  const char* name() { return _name; }

  int level() { return _level; }
  void set_level(int level) { _level = level; }

  jlong start() { return _start; }
  void set_start(jlong time) { _start = time; }

  jlong end() { return _end; }
  void set_end(jlong time) { _end = time; }

  virtual void accept(PhaseVisitor* visitor) = 0;
};

class PausePhase : public GCPhase {
 public:
  void accept(PhaseVisitor* visitor) {
    visitor->visit(this);
  }
};

class ConcurrentPhase : public GCPhase {
  void accept(PhaseVisitor* visitor) {
    visitor->visit(this);
  }
};

class PhasesStack {
 public:
  // FIXME: Temporary set to 5 (used to be 4), since Reference processing needs it.
  static const int PHASE_LEVELS = 5;

 private:
  int _phase_indices[PHASE_LEVELS];
  int _next_phase_level;

 public:
  PhasesStack() { clear(); }
  void clear();

  void push(int phase_index);
  int pop();
  int count() const;
};

class TimePartitions {
  static const int INITIAL_CAPACITY = 10;

  // Currently we only support pause phases.
  GrowableArray<PausePhase>* _phases;
  PhasesStack _active_phases;

  jlong _sum_of_pauses;
  jlong _longest_pause;

 public:
  TimePartitions();
  ~TimePartitions();
  void clear();

  void report_gc_phase_start(const char* name, jlong time);
  void report_gc_phase_end(jlong time);

  int num_phases() const;
  GCPhase* phase_at(int index) const;

  jlong sum_of_pauses();
  jlong longest_pause();

  bool has_active_phases();
 private:
  void update_statistics(GCPhase* phase);
};

class PhasesIterator {
 public:
  virtual bool has_next() = 0;
  virtual GCPhase* next() = 0;
};

class GCTimer : public ResourceObj {
  NOT_PRODUCT(friend class GCTimerTest;)
 protected:
  jlong _gc_start;
  jlong _gc_end;
  TimePartitions _time_partitions;

 public:
  virtual void register_gc_start(jlong time);
  virtual void register_gc_end(jlong time);

  void register_gc_phase_start(const char* name, jlong time);
  void register_gc_phase_end(jlong time);

  jlong gc_start() { return _gc_start; }
  jlong gc_end() { return _gc_end; }

  TimePartitions* time_partitions() { return &_time_partitions; }

  long longest_pause();
  long sum_of_pauses();

 protected:
  void register_gc_pause_start(const char* name, jlong time);
  void register_gc_pause_end(jlong time);
};

class STWGCTimer : public GCTimer {
 public:
  virtual void register_gc_start(jlong time);
  virtual void register_gc_end(jlong time);
};

class ConcurrentGCTimer : public GCTimer {
 public:
  void register_gc_pause_start(const char* name, jlong time);
  void register_gc_pause_end(jlong time);
};

class TimePartitionPhasesIterator {
  TimePartitions* _time_partitions;
  int _next;

 public:
  TimePartitionPhasesIterator(TimePartitions* time_partitions) : _time_partitions(time_partitions), _next(0) { }

  virtual bool has_next();
  virtual GCPhase* next();
};


/////////////// Unit tests ///////////////

#ifndef PRODUCT

class GCTimerAllTest {
 public:
  static void all();
};

#endif

#endif // SHARE_VM_GC_IMPLEMENTATION_SHARED_GCTIMER_HPP
