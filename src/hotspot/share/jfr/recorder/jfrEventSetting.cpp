/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/recorder/jfrEventSetting.inline.hpp"

JfrNativeSettings JfrEventSetting::_jvm_event_settings;
bool JfrEventSetting::_internal_types = false;

bool JfrEventSetting::set_threshold(jlong id, jlong threshold_ticks) {
  JfrEventId event_id = (JfrEventId)id;
  assert(bounds_check_event(event_id), "invariant");
  setting(event_id).threshold_ticks = threshold_ticks;
  return true;
}

void JfrEventSetting::set_miscellaneous(jlong id, jlong level) {
  JfrEventId event_id = (JfrEventId)id;
  assert(bounds_check_event(event_id), "invariant");
  setting(event_id).miscellaneous = level;
}

void JfrEventSetting::set_stacktrace(jlong id, bool enabled) {
  JfrEventId event_id = (JfrEventId)id;
  assert(bounds_check_event(event_id), "invariant");
  setting(event_id).stacktrace = enabled;
}

void JfrEventSetting::set_enabled(jlong id, bool enabled) {
  JfrEventId event_id = (JfrEventId)id;
  assert(bounds_check_event(event_id), "invariant");
  setting(event_id).enabled = enabled;
}

void JfrEventSetting::set_large(JfrEventId event_id) {
  assert(bounds_check_event(event_id), "invariant");
  setting(event_id).large = true;
}

void JfrEventSetting::unhide_internal_types() {
  _internal_types = true;
}

bool JfrEventSetting::is_internal_types_visible() {
  return _internal_types;
}


#ifdef ASSERT
bool JfrEventSetting::bounds_check_event(jlong id) {
  if ((unsigned)id < FIRST_EVENT_ID) {
    return false;
  }
  if ((unsigned)id > LAST_EVENT_ID) {
    return false;
  }
  return true;
}
#endif // ASSERT

