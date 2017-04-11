/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/methodCounters.hpp"
#include "runtime/handles.inline.hpp"

MethodCounters* MethodCounters::allocate(methodHandle mh, TRAPS) {
  ClassLoaderData* loader_data = mh->method_holder()->class_loader_data();
  return new(loader_data, size(), false, MetaspaceObj::MethodCountersType, THREAD) MethodCounters(mh);
}

void MethodCounters::clear_counters() {
  invocation_counter()->reset();
  backedge_counter()->reset();
  set_interpreter_throwout_count(0);
  set_interpreter_invocation_count(0);
  set_nmethod_age(INT_MAX);
#ifdef TIERED
  set_prev_time(0);
  set_rate(0);
  set_highest_comp_level(0);
  set_highest_osr_comp_level(0);
#endif
}


int MethodCounters::highest_comp_level() const {
#ifdef TIERED
  return _highest_comp_level;
#else
  return CompLevel_none;
#endif
}

void MethodCounters::set_highest_comp_level(int level) {
#ifdef TIERED
  _highest_comp_level = level;
#endif
}

int MethodCounters::highest_osr_comp_level() const {
#ifdef TIERED
  return _highest_osr_comp_level;
#else
  return CompLevel_none;
#endif
}

void MethodCounters::set_highest_osr_comp_level(int level) {
#ifdef TIERED
  _highest_osr_comp_level = level;
#endif
}


void MethodCounters::print_value_on(outputStream* st) const {
  assert(is_methodCounters(), "must be methodCounters");
  st->print("method counters");
  print_address_on(st);
}


