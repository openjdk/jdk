/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "prims/jvmtiAgent.hpp"
#include "prims/jvmtiAgentLibrary.hpp"
#include "prims/jvmtiEnvBase.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/threads.hpp"

#ifdef ASSERT
static void assert_exclusivity() {
  assert(Threads::number_of_threads() == 0 || JvmtiThreadState_lock->owned_by_self(), "invariant");
}
#endif

// Not a tautology, despite its name.
static inline bool is_live_or_dead_phase() {
  const jvmtiPhase phase = JvmtiEnvBase::get_phase();
  return phase == JVMTI_PHASE_LIVE || phase == JVMTI_PHASE_DEAD;
}

static inline const char* copy_string(const char* str) {
  return str != nullptr ? os::strdup_check_oom(str, mtServiceability) : nullptr;
}

JvmtiAgent::JvmtiAgent(const char* name, const char* options, bool is_absolute_path, bool xrun) :
  _name(copy_string(name)),
  _options(copy_string(options)),
  _is_absolute_path(is_absolute_path),
  _instrument_lib(strcmp(name, "instrument") == 0),
  _dynamic(is_live_or_dead_phase()),
  _xrun(xrun),
  _library(nullptr),
  _startup(Startup::NotEntered),
  _next(nullptr),
  _registered(false),
  _env(nullptr),
  _association(EnvAssociation::None),
  _timing(TimingState::Pending),
  _initialization_time(min_jlong),
  _initialization_duration(min_jlong) {}

JvmtiAgent::~JvmtiAgent() {
  assert(!is_registered(), "invariant");
  assert(!is_env_associated(), "invariant");
  assert(!has_env(), "invariant");
  assert(AtomicAccess::load(&_startup) != Startup::Executing, "invariant");
  FREE_C_HEAP_ARRAY(_name);
  FREE_C_HEAP_ARRAY(_options);
}

const char* JvmtiAgent::name() const {
  return _name;
}

const char* JvmtiAgent::options() const {
  return _options;
}

bool JvmtiAgent::is_absolute_path() const {
  return _is_absolute_path;
}

bool JvmtiAgent::is_dynamic() const {
  return _dynamic;
}

bool JvmtiAgent::is_instrument_lib() const {
  return _instrument_lib;
}

bool JvmtiAgent::is_java_agent() const {
  return is_instrument_lib();
}

bool JvmtiAgent::is_xrun() const {
  return _xrun;
}

void JvmtiAgent::convert_from_xrun() {
  assert(_xrun, "invariant");
  assert(AtomicAccess::load(&_startup) == Startup::NotEntered, "invariant");
  assert(AtomicAccess::load(&_association) == EnvAssociation::None, "invariant");
  assert(!is_dynamic(), "invariant");
  _xrun = false;
}

JvmtiAgentLibrary* JvmtiAgent::library() const {
  return AtomicAccess::load_acquire(&_library);
}

void JvmtiAgent::set_library(JvmtiAgentLibrary* library) {
  assert(library != nullptr, "invariant");
  assert(!has_library(), "invariant");
  assert(AtomicAccess::load(&_startup) == Startup::NotEntered, "invariant");
  assert(AtomicAccess::load(&_association) == EnvAssociation::None, "invariant");
  AtomicAccess::release_store(&_library, library);
}

bool JvmtiAgent::has_library() const {
  return library() != nullptr;
}

void JvmtiAgent::begin_startup() {
  assert(has_library(), "invariant");
  assert(library()->path() != nullptr, "invariant");
  assert(AtomicAccess::load(&_startup) == Startup::NotEntered, "invariant");
  assert(AtomicAccess::load(&_association) == EnvAssociation::None, "invariant");
  assert(is_timing_pending(), "invariant");
  assert(!is_registered(), "invariant");
  AtomicAccess::store(&_startup, Startup::Executing);
}

void JvmtiAgent::end_startup() {
  assert(AtomicAccess::load(&_startup) == Startup::Executing, "invariant");
  assert(!is_dynamic() || is_timing_finalized(), "invariant");
  AtomicAccess::store(&_startup, Startup::Returned);
}

bool JvmtiAgent::has_startup_returned() const {
  return AtomicAccess::load(&_startup) == Startup::Returned;
}

bool JvmtiAgent::is_registered() const {
  assert(!_registered || is_env_associated() || is_env_disposed(), "invariant");
  return _registered;
}

void JvmtiAgent::mark_registered() {
  DEBUG_ONLY(assert_exclusivity();)
  assert(!is_xrun(), "invariant");
  assert(has_library(), "invariant");
  assert(library()->path() != nullptr, "invariant");
  assert(is_env_associated(), "invariant");
  assert(has_startup_returned(), "invariant");
  assert(!is_registered(), "invariant");
  _registered = true;
}

JvmtiAgent* JvmtiAgent::next() const {
  return AtomicAccess::load_acquire(&_next);
}

bool JvmtiAgent::can_associate_env() const {
  return AtomicAccess::load(&_startup) == Startup::Executing &&
    AtomicAccess::load(&_association) == EnvAssociation::None;
}

bool JvmtiAgent::is_env_associated() const {
  return AtomicAccess::load(&_association) == EnvAssociation::Associated;
}

bool JvmtiAgent::is_env_disposed() const {
  return AtomicAccess::load(&_association) == EnvAssociation::Disposed;
}

bool JvmtiAgent::has_env() const {
  return AtomicAccess::load(&_env) != nullptr;
}

void JvmtiAgent::associate_env(const jvmtiEnv* env) {
  DEBUG_ONLY(assert_exclusivity();)
  assert(env != nullptr, "invariant");
  assert(!has_env(), "invariant");
  assert(has_library(), "invariant");
  assert(library()->path() != nullptr, "invariant");
  assert(can_associate_env(), "invariant");
  AtomicAccess::store(&_env, env);
  AtomicAccess::store(&_association, EnvAssociation::Associated);
}

void JvmtiAgent::mark_env_disposed() {
  DEBUG_ONLY(assert_exclusivity();)
  assert(has_env(), "invariant");
  assert(is_env_associated(), "invariant");
  AtomicAccess::store(&_env, static_cast<const jvmtiEnv*>(nullptr));
  AtomicAccess::store(&_association, EnvAssociation::Disposed);
}

bool JvmtiAgent::matches_env(const jvmtiEnv* rhs_env) const {
  assert(rhs_env != nullptr, "invariant");
  return AtomicAccess::load(&_env) == rhs_env;
}

bool JvmtiAgent::is_timing_pending() const {
  return AtomicAccess::load(&_timing) == TimingState::Pending;
}

bool JvmtiAgent::is_timing_measuring() const {
  return AtomicAccess::load(&_timing) == TimingState::Measuring;
}

bool JvmtiAgent::is_timing_finalized() const {
  return AtomicAccess::load_acquire(&_timing) == TimingState::Finalized;
}

void JvmtiAgent::finalize_timing() {
  assert(!is_timing_finalized(), "invariant");
  AtomicAccess::release_store(&_timing, TimingState::Finalized);
}

void JvmtiAgent::begin_initialization_timing() {
  DEBUG_ONLY(if (is_registered()) { assert_exclusivity(); })
  assert(is_timing_pending(), "invarinat");
  AtomicAccess::store(&_timing, TimingState::Measuring);
  assert(_initialization_time.value() == min_jlong, "invariant");
  _initialization_time = Ticks::now();
}

void JvmtiAgent::end_initialization_timing() {
  DEBUG_ONLY(if (is_registered()) { assert_exclusivity(); })
  assert(is_timing_measuring(), "invariant");
  assert(_initialization_duration.value() == min_jlong, "invariant");
  _initialization_duration = Ticks::now() - _initialization_time;
  finalize_timing();
}

void JvmtiAgent::finalize_unavailable_timing() {
  DEBUG_ONLY(assert_exclusivity();)
  assert(has_startup_returned(), "invariant");
  assert(is_timing_pending(), "invariant");
  assert(is_env_disposed(), "invariant");
  assert(_initialization_time.value() == min_jlong, "invariant");
  assert(_initialization_duration.value() == min_jlong, "invariant");
  finalize_timing();
}

void JvmtiAgent::finalize_timing_without_vm_init_callback() {
  DEBUG_ONLY(assert_exclusivity();)
  assert(has_startup_returned(), "invariant");
  assert(!is_dynamic(), "invariant");
  assert(is_xrun() || (is_registered() && is_env_associated()), "invariant");
  begin_initialization_timing();
  _initialization_duration = Tickspan();
  finalize_timing();
}

bool JvmtiAgent::has_initialization_time() const {
  assert(is_timing_finalized(), "antecedent is false");
  return _initialization_time.value() != min_jlong;
}

const Ticks& JvmtiAgent::initialization_time() const {
  assert(is_timing_finalized(), "antecedent is false");
  return _initialization_time;
}

const Tickspan& JvmtiAgent::initialization_duration() const {
  assert(is_timing_finalized(), "antecedent is false");
  return _initialization_duration;
}
