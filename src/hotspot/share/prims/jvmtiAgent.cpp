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
#include "prims/jvmtiAgentLifecycle.hpp"
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
  _next(nullptr),
  _initialization_time(min_jlong),
  _initialization_duration(min_jlong),
  _name(copy_string(name)),
  _options(copy_string(options)),
  _path(nullptr),
  _resolver(nullptr),
  _env(nullptr),
  _startup(Startup::NotEntered),
  _association(EnvAssociation::None),
  _timing(Timing::Unstarted),
  _is_absolute_path(is_absolute_path),
  _instrument_lib(strcmp(name, "instrument") == 0),
  _dynamic(is_live_or_dead_phase()),
  _xrun(xrun),
  _registered(false),
  _static(JvmtiAgentLifecycle::is_static(this)) {}

JvmtiAgent::~JvmtiAgent() {
  assert(!is_registered(), "invariant");
  assert(!is_associated(), "invariant");
  assert(!has_env(), "invariant");
  assert(AtomicAccess::load(&_startup) != Startup::Executing, "invariant");
  FREE_C_HEAP_ARRAY(_name);
  FREE_C_HEAP_ARRAY(_options);
  FREE_C_HEAP_ARRAY(_path);
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

bool JvmtiAgent::is_static() const {
  return _static;
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

JvmtiResolver* JvmtiAgent::resolver() const {
  return AtomicAccess::load_acquire(&_resolver);
}

bool JvmtiAgent::has_resolver() const {
  return resolver() != nullptr;
}

void JvmtiAgent::set_resolver(JvmtiResolver* resolver) {
  assert(resolver != nullptr, "invariant");
  assert(!has_resolver(), "invariant");
  assert(AtomicAccess::load(&_startup) == Startup::NotEntered, "invariant");
  assert(AtomicAccess::load(&_association) == EnvAssociation::None, "invariant");
  AtomicAccess::release_store(&_resolver, resolver);
}

const char* JvmtiAgent::path() const {
  return AtomicAccess::load_acquire(&_path);
}

bool JvmtiAgent::has_path() const {
  return path() != nullptr;
}

void JvmtiAgent::set_path(const char* path) {
  assert(path != nullptr, "invariant");
  assert(!has_path(), "invariant");
  assert(has_resolver(), "invariant");
  assert(AtomicAccess::load(&_startup) == Startup::NotEntered, "invariant");
  assert(AtomicAccess::load(&_association) == EnvAssociation::None, "invariant");
  AtomicAccess::release_store(&_path, copy_string(path));
}

void JvmtiAgent::begin_startup() {
  assert(has_resolver(), "invariant");
  assert(has_path(), "invariant");
  assert(AtomicAccess::load(&_startup) == Startup::NotEntered, "invariant");
  assert(AtomicAccess::load(&_association) == EnvAssociation::None, "invariant");
  assert(is_timing_unstarted(), "invariant");
  assert(!is_registered(), "invariant");
  AtomicAccess::store(&_startup, Startup::Executing);
}

void JvmtiAgent::end_startup() {
  assert(AtomicAccess::load(&_startup) == Startup::Executing, "invariant");
  assert(!is_dynamic() || is_timing_published(), "invariant");
  AtomicAccess::store(&_startup, Startup::Returned);
}

bool JvmtiAgent::has_startup_returned() const {
  return AtomicAccess::load(&_startup) == Startup::Returned;
}

bool JvmtiAgent::is_registered() const {
  assert(!_registered || is_associated() || is_disposed(), "invariant");
  return _registered;
}

void JvmtiAgent::mark_registered() {
  DEBUG_ONLY(assert_exclusivity();)
  assert(!is_xrun(), "invariant");
  assert(has_resolver(), "invariant");
  assert(has_path(), "invariant");
  assert(is_associated(), "invariant");
  assert(has_startup_returned(), "invariant");
  assert(!is_registered(), "invariant");
  _registered = true;
}

JvmtiAgent* JvmtiAgent::next() const {
  return AtomicAccess::load_acquire(&_next);
}

bool JvmtiAgent::can_associate() const {
  return AtomicAccess::load(&_startup) == Startup::Executing &&
    AtomicAccess::load(&_association) == EnvAssociation::None;
}

bool JvmtiAgent::is_associated() const {
  return AtomicAccess::load(&_association) == EnvAssociation::Associated;
}

bool JvmtiAgent::is_disposed() const {
  return AtomicAccess::load(&_association) == EnvAssociation::Disposed;
}

bool JvmtiAgent::has_env() const {
  return AtomicAccess::load(&_env) != nullptr;
}

void JvmtiAgent::set_env(const jvmtiEnv* env) {
  DEBUG_ONLY(assert_exclusivity();)
  assert(env != nullptr, "invariant");
  assert(!has_env(), "invariant");
  assert(has_resolver(), "invariant");
  assert(has_path(), "invariant");
  assert(can_associate(), "invariant");
  AtomicAccess::store(&_env, env);
  AtomicAccess::store(&_association, EnvAssociation::Associated);
}

void JvmtiAgent::clear_env() {
  DEBUG_ONLY(assert_exclusivity();)
  assert(has_env(), "invariant");
  assert(is_associated(), "invariant");
  AtomicAccess::store(&_env, static_cast<const jvmtiEnv*>(nullptr));
  AtomicAccess::store(&_association, EnvAssociation::Disposed);
}

bool JvmtiAgent::equals(const jvmtiEnv* rhs_env) const {
  assert(rhs_env != nullptr, "invariant");
  return AtomicAccess::load(&_env) == rhs_env;
}

bool JvmtiAgent::is_timing_unstarted() const {
  return AtomicAccess::load(&_timing) == Timing::Unstarted;
}

bool JvmtiAgent::is_initializing() const {
  return AtomicAccess::load(&_timing) == Timing::Measuring;
}

void JvmtiAgent::publish_timing() {
  assert(!is_timing_published(), "invariant");
  AtomicAccess::release_store(&_timing, Timing::Published);
}

void JvmtiAgent::initialization_begin() {
  DEBUG_ONLY(if (is_registered()) { assert_exclusivity(); })
  assert(is_timing_unstarted(), "invarinat");
  AtomicAccess::store(&_timing, Timing::Measuring);
  assert(_initialization_time.value() == min_jlong, "invariant");
  _initialization_time = Ticks::now();
}

void JvmtiAgent::initialization_end() {
  DEBUG_ONLY(if (is_registered()) { assert_exclusivity(); })
  assert(is_initializing(), "invariant");
  assert(_initialization_duration.value() == min_jlong, "invariant");
  _initialization_duration = Ticks::now() - _initialization_time;
  publish_timing();
}

void JvmtiAgent::initialization_unavailable() {
  DEBUG_ONLY(assert_exclusivity();)
  assert(has_startup_returned(), "invariant");
  assert(is_timing_unstarted(), "invariant");
  assert(is_disposed(), "invariant");
  assert(_initialization_time.value() == min_jlong, "invariant");
  assert(_initialization_duration.value() == min_jlong, "invariant");
  publish_timing();
}

void JvmtiAgent::initialization_without_vm_init_callback() {
  DEBUG_ONLY(assert_exclusivity();)
  assert(has_startup_returned(), "invariant");
  assert(!is_dynamic(), "invariant");
  assert(is_xrun() || (is_registered() && is_associated()), "invariant");
  initialization_begin();
  _initialization_duration = Tickspan();
  publish_timing();
}

bool JvmtiAgent::is_timing_published() const {
  return AtomicAccess::load_acquire(&_timing) == Timing::Published;
}

bool JvmtiAgent::has_initialization_time() const {
  assert(is_timing_published(), "antecedent is false");
  return _initialization_time.value() != min_jlong;
}

const Ticks& JvmtiAgent::initialization_time() const {
  assert(is_timing_published(), "antecedent is false");
  return _initialization_time;
}

const Tickspan& JvmtiAgent::initialization_duration() const {
  assert(is_timing_published(), "antecedent is false");
  return _initialization_duration;
}
