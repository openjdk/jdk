/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/support/jfrEventClass.hpp"

bool JdkJfrEvent::is(const Klass* k) {
  return JfrTraceId::is_jdk_jfr_event(k);
}

bool JdkJfrEvent::is(const jclass jc) {
  return JfrTraceId::is_jdk_jfr_event(jc);
}

void JdkJfrEvent::tag_as(const Klass* k) {
  JfrTraceId::tag_as_jdk_jfr_event(k);
}

bool JdkJfrEvent::is_subklass(const Klass* k) {
  return JfrTraceId::is_jdk_jfr_event_sub(k);
}

bool JdkJfrEvent::is_subklass(const jclass jc) {
  return JfrTraceId::is_jdk_jfr_event_sub(jc);
}

void JdkJfrEvent::tag_as_subklass(const Klass* k) {
  JfrTraceId::tag_as_jdk_jfr_event_sub(k);
}

void JdkJfrEvent::tag_as_subklass(const jclass jc) {
  JfrTraceId::tag_as_jdk_jfr_event_sub(jc);
}

bool JdkJfrEvent::is_a(const Klass* k) {
  return JfrTraceId::in_jdk_jfr_event_hierarchy(k);
}

bool JdkJfrEvent::is_a(const jclass jc) {
  return JfrTraceId::in_jdk_jfr_event_hierarchy(jc);
}

bool JdkJfrEvent::is_host(const Klass* k) {
  return JfrTraceId::is_event_host(k);
}

bool JdkJfrEvent::is_host(const jclass jc) {
  return JfrTraceId::is_event_host(jc);
}

void JdkJfrEvent::tag_as_host(const Klass* k) {
  JfrTraceId::tag_as_event_host(k);
}

void JdkJfrEvent::tag_as_host(const jclass jc) {
  JfrTraceId::tag_as_event_host(jc);
}

bool JdkJfrEvent::is_visible(const Klass* k) {
  return JfrTraceId::in_visible_set(k);
}

bool JdkJfrEvent::is_visible(const jclass jc) {
  return JfrTraceId::in_visible_set(jc);
}
