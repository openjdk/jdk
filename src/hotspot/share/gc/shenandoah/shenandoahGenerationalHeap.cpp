/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahInitLogger.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahRegulatorThread.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"

#include "logging/log.hpp"

class ShenandoahGenerationalInitLogger : public ShenandoahInitLogger {
public:
  static void print() {
    ShenandoahGenerationalInitLogger logger;
    logger.print_all();
  }

  void print_heap() override {
    ShenandoahInitLogger::print_heap();

    ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();

    ShenandoahYoungGeneration* young = heap->young_generation();
    log_info(gc, init)("Young Generation Soft Size: " EXACTFMT, EXACTFMTARGS(young->soft_max_capacity()));
    log_info(gc, init)("Young Generation Max: " EXACTFMT, EXACTFMTARGS(young->max_capacity()));

    ShenandoahOldGeneration* old = heap->old_generation();
    log_info(gc, init)("Old Generation Soft Size: " EXACTFMT, EXACTFMTARGS(old->soft_max_capacity()));
    log_info(gc, init)("Old Generation Max: " EXACTFMT, EXACTFMTARGS(old->max_capacity()));
  }

protected:
  void print_gc_specific() override {
    ShenandoahInitLogger::print_gc_specific();

    ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();
    log_info(gc, init)("Young Heuristics: %s", heap->young_generation()->heuristics()->name());
    log_info(gc, init)("Old Heuristics: %s", heap->old_generation()->heuristics()->name());
  }
};

ShenandoahGenerationalHeap* ShenandoahGenerationalHeap::heap() {
  CollectedHeap* heap = Universe::heap();
  return checked_cast<ShenandoahGenerationalHeap*>(heap);
}

void ShenandoahGenerationalHeap::print_init_logger() const {
  ShenandoahGenerationalInitLogger logger;
  logger.print_all();
}

ShenandoahGenerationalHeap::ShenandoahGenerationalHeap(ShenandoahCollectorPolicy* policy) :
  ShenandoahHeap(policy),
  _regulator_thread(nullptr) { }

void ShenandoahGenerationalHeap::initialize_controller() {
  auto control_thread = new ShenandoahGenerationalControlThread();
  _control_thread = control_thread;
  _regulator_thread = new ShenandoahRegulatorThread(control_thread);
}

void ShenandoahGenerationalHeap::gc_threads_do(ThreadClosure* tcl) const {
  if (!shenandoah_policy()->is_at_shutdown()) {
    ShenandoahHeap::gc_threads_do(tcl);
    tcl->do_thread(regulator_thread());
  }
}

void ShenandoahGenerationalHeap::stop() {
  regulator_thread()->stop();
  ShenandoahHeap::stop();
}
