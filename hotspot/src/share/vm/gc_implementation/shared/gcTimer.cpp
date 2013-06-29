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

#include "precompiled.hpp"
#include "gc_implementation/shared/gcTimer.hpp"
#include "utilities/growableArray.hpp"

void GCTimer::register_gc_start(jlong time) {
  _time_partitions.clear();
  _gc_start = time;
}

void GCTimer::register_gc_end(jlong time) {
  assert(!_time_partitions.has_active_phases(),
      "We should have ended all started phases, before ending the GC");

  _gc_end = time;
}

void GCTimer::register_gc_pause_start(const char* name, jlong time) {
  _time_partitions.report_gc_phase_start(name, time);
}

void GCTimer::register_gc_pause_end(jlong time) {
  _time_partitions.report_gc_phase_end(time);
}

void GCTimer::register_gc_phase_start(const char* name, jlong time) {
  _time_partitions.report_gc_phase_start(name, time);
}

void GCTimer::register_gc_phase_end(jlong time) {
  _time_partitions.report_gc_phase_end(time);
}


void STWGCTimer::register_gc_start(jlong time) {
  GCTimer::register_gc_start(time);
  register_gc_pause_start("GC Pause", time);
}

void STWGCTimer::register_gc_end(jlong time) {
  register_gc_pause_end(time);
  GCTimer::register_gc_end(time);
}

void ConcurrentGCTimer::register_gc_pause_start(const char* name, jlong time) {
  GCTimer::register_gc_pause_start(name, time);
}

void ConcurrentGCTimer::register_gc_pause_end(jlong time) {
  GCTimer::register_gc_pause_end(time);
}

void PhasesStack::clear() {
  _next_phase_level = 0;
}

void PhasesStack::push(int phase_index) {
  assert(_next_phase_level < PHASE_LEVELS, "Overflow");

  _phase_indices[_next_phase_level] = phase_index;

  _next_phase_level++;
}

int PhasesStack::pop() {
  assert(_next_phase_level > 0, "Underflow");

  _next_phase_level--;

  return _phase_indices[_next_phase_level];
}

int PhasesStack::count() const {
  return _next_phase_level;
}


TimePartitions::TimePartitions() {
  _phases = new (ResourceObj::C_HEAP, mtGC) GrowableArray<PausePhase>(INITIAL_CAPACITY, true, mtGC);
  clear();
}

TimePartitions::~TimePartitions() {
  delete _phases;
  _phases = NULL;
}

void TimePartitions::clear() {
  _phases->clear();
  _active_phases.clear();
  _sum_of_pauses = 0;
  _longest_pause = 0;
}

void TimePartitions::report_gc_phase_start(const char* name, jlong time) {
  assert(_phases->length() <= 1000, "Too many recored phases?");

  int level = _active_phases.count();

  PausePhase phase;
  phase.set_level(level);
  phase.set_name(name);
  phase.set_start(time);

  int index = _phases->append(phase);

  _active_phases.push(index);
}

void TimePartitions::update_statistics(GCPhase* phase) {
  // FIXME: This should only be done for pause phases
  if (phase->level() == 0) {
    jlong pause = phase->end() - phase->start();
    _sum_of_pauses += pause;
    _longest_pause = MAX2(pause, _longest_pause);
  }
}

void TimePartitions::report_gc_phase_end(jlong time) {
  int phase_index = _active_phases.pop();
  GCPhase* phase = _phases->adr_at(phase_index);
  phase->set_end(time);
  update_statistics(phase);
}

int TimePartitions::num_phases() const {
  return _phases->length();
}

GCPhase* TimePartitions::phase_at(int index) const {
  assert(index >= 0, "Out of bounds");
  assert(index < _phases->length(), "Out of bounds");

  return _phases->adr_at(index);
}

jlong TimePartitions::sum_of_pauses() {
  return _sum_of_pauses;
}

jlong TimePartitions::longest_pause() {
  return _longest_pause;
}

bool TimePartitions::has_active_phases() {
  return _active_phases.count() > 0;
}

bool TimePartitionPhasesIterator::has_next() {
  return _next < _time_partitions->num_phases();
}

GCPhase* TimePartitionPhasesIterator::next() {
  assert(has_next(), "Must have phases left");
  return _time_partitions->phase_at(_next++);
}


/////////////// Unit tests ///////////////

#ifndef PRODUCT

class TimePartitionPhasesIteratorTest {
 public:
  static void all() {
    one_pause();
    two_pauses();
    one_sub_pause_phase();
    many_sub_pause_phases();
    many_sub_pause_phases2();
    max_nested_pause_phases();
  }

  static void validate_pause_phase(GCPhase* phase, int level, const char* name, jlong start, jlong end) {
    assert(phase->level() == level, "Incorrect level");
    assert(strcmp(phase->name(), name) == 0, "Incorrect name");
    assert(phase->start() == start, "Incorrect start");
    assert(phase->end() == end, "Incorrect end");
  }

  static void one_pause() {
    TimePartitions time_partitions;
    time_partitions.report_gc_phase_start("PausePhase", 2);
    time_partitions.report_gc_phase_end(8);

    TimePartitionPhasesIterator iter(&time_partitions);

    validate_pause_phase(iter.next(), 0, "PausePhase", 2, 8);
    assert(time_partitions.sum_of_pauses() == 8-2, "Incorrect");
    assert(time_partitions.longest_pause() == 8-2, "Incorrect");

    assert(!iter.has_next(), "Too many elements");
  }

  static void two_pauses() {
    TimePartitions time_partitions;
    time_partitions.report_gc_phase_start("PausePhase1", 2);
    time_partitions.report_gc_phase_end(3);
    time_partitions.report_gc_phase_start("PausePhase2", 4);
    time_partitions.report_gc_phase_end(6);

    TimePartitionPhasesIterator iter(&time_partitions);

    validate_pause_phase(iter.next(), 0, "PausePhase1", 2, 3);
    validate_pause_phase(iter.next(), 0, "PausePhase2", 4, 6);

    assert(time_partitions.sum_of_pauses() == 3, "Incorrect");
    assert(time_partitions.longest_pause() == 2, "Incorrect");

    assert(!iter.has_next(), "Too many elements");
  }

  static void one_sub_pause_phase() {
    TimePartitions time_partitions;
    time_partitions.report_gc_phase_start("PausePhase", 2);
    time_partitions.report_gc_phase_start("SubPhase", 3);
    time_partitions.report_gc_phase_end(4);
    time_partitions.report_gc_phase_end(5);

    TimePartitionPhasesIterator iter(&time_partitions);

    validate_pause_phase(iter.next(), 0, "PausePhase", 2, 5);
    validate_pause_phase(iter.next(), 1, "SubPhase", 3, 4);

    assert(time_partitions.sum_of_pauses() == 3, "Incorrect");
    assert(time_partitions.longest_pause() == 3, "Incorrect");

    assert(!iter.has_next(), "Too many elements");
  }

  static void max_nested_pause_phases() {
    TimePartitions time_partitions;
    time_partitions.report_gc_phase_start("PausePhase", 2);
    time_partitions.report_gc_phase_start("SubPhase1", 3);
    time_partitions.report_gc_phase_start("SubPhase2", 4);
    time_partitions.report_gc_phase_start("SubPhase3", 5);
    time_partitions.report_gc_phase_end(6);
    time_partitions.report_gc_phase_end(7);
    time_partitions.report_gc_phase_end(8);
    time_partitions.report_gc_phase_end(9);

    TimePartitionPhasesIterator iter(&time_partitions);

    validate_pause_phase(iter.next(), 0, "PausePhase", 2, 9);
    validate_pause_phase(iter.next(), 1, "SubPhase1", 3, 8);
    validate_pause_phase(iter.next(), 2, "SubPhase2", 4, 7);
    validate_pause_phase(iter.next(), 3, "SubPhase3", 5, 6);

    assert(time_partitions.sum_of_pauses() == 7, "Incorrect");
    assert(time_partitions.longest_pause() == 7, "Incorrect");

    assert(!iter.has_next(), "Too many elements");
  }

  static void many_sub_pause_phases() {
    TimePartitions time_partitions;
    time_partitions.report_gc_phase_start("PausePhase", 2);

    time_partitions.report_gc_phase_start("SubPhase1", 3);
    time_partitions.report_gc_phase_end(4);
    time_partitions.report_gc_phase_start("SubPhase2", 5);
    time_partitions.report_gc_phase_end(6);
    time_partitions.report_gc_phase_start("SubPhase3", 7);
    time_partitions.report_gc_phase_end(8);
    time_partitions.report_gc_phase_start("SubPhase4", 9);
    time_partitions.report_gc_phase_end(10);

    time_partitions.report_gc_phase_end(11);

    TimePartitionPhasesIterator iter(&time_partitions);

    validate_pause_phase(iter.next(), 0, "PausePhase", 2, 11);
    validate_pause_phase(iter.next(), 1, "SubPhase1", 3, 4);
    validate_pause_phase(iter.next(), 1, "SubPhase2", 5, 6);
    validate_pause_phase(iter.next(), 1, "SubPhase3", 7, 8);
    validate_pause_phase(iter.next(), 1, "SubPhase4", 9, 10);

    assert(time_partitions.sum_of_pauses() == 9, "Incorrect");
    assert(time_partitions.longest_pause() == 9, "Incorrect");

    assert(!iter.has_next(), "Too many elements");
  }

  static void many_sub_pause_phases2() {
    TimePartitions time_partitions;
    time_partitions.report_gc_phase_start("PausePhase", 2);

    time_partitions.report_gc_phase_start("SubPhase1", 3);
    time_partitions.report_gc_phase_start("SubPhase11", 4);
    time_partitions.report_gc_phase_end(5);
    time_partitions.report_gc_phase_start("SubPhase12", 6);
    time_partitions.report_gc_phase_end(7);
    time_partitions.report_gc_phase_end(8);
    time_partitions.report_gc_phase_start("SubPhase2", 9);
    time_partitions.report_gc_phase_start("SubPhase21", 10);
    time_partitions.report_gc_phase_end(11);
    time_partitions.report_gc_phase_start("SubPhase22", 12);
    time_partitions.report_gc_phase_end(13);
    time_partitions.report_gc_phase_end(14);
    time_partitions.report_gc_phase_start("SubPhase3", 15);
    time_partitions.report_gc_phase_end(16);

    time_partitions.report_gc_phase_end(17);

    TimePartitionPhasesIterator iter(&time_partitions);

    validate_pause_phase(iter.next(), 0, "PausePhase", 2, 17);
    validate_pause_phase(iter.next(), 1, "SubPhase1", 3, 8);
    validate_pause_phase(iter.next(), 2, "SubPhase11", 4, 5);
    validate_pause_phase(iter.next(), 2, "SubPhase12", 6, 7);
    validate_pause_phase(iter.next(), 1, "SubPhase2", 9, 14);
    validate_pause_phase(iter.next(), 2, "SubPhase21", 10, 11);
    validate_pause_phase(iter.next(), 2, "SubPhase22", 12, 13);
    validate_pause_phase(iter.next(), 1, "SubPhase3", 15, 16);

    assert(time_partitions.sum_of_pauses() == 15, "Incorrect");
    assert(time_partitions.longest_pause() == 15, "Incorrect");

    assert(!iter.has_next(), "Too many elements");
  }
};

class GCTimerTest {
public:
  static void all() {
    gc_start();
    gc_end();
  }

  static void gc_start() {
    GCTimer gc_timer;
    gc_timer.register_gc_start(1);

    assert(gc_timer.gc_start() == 1, "Incorrect");
  }

  static void gc_end() {
    GCTimer gc_timer;
    gc_timer.register_gc_start(1);
    gc_timer.register_gc_end(2);

    assert(gc_timer.gc_end() == 2, "Incorrect");
  }
};

void GCTimerAllTest::all() {
  GCTimerTest::all();
  TimePartitionPhasesIteratorTest::all();
}

#endif
