/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_SUPPORT_METHODTRACER_JFRTRACETAGGING_HPP
#define SHARE_JFR_SUPPORT_METHODTRACER_JFRTRACETAGGING_HPP

#include "jfr/support/methodtracer/jfrTracedMethod.hpp"
#include "memory/allStatic.hpp"

class InstanceKlass;
class Method;

template <typename E> class GrowableArray;

//
// Class responsible for setting setting sticky, epoch, and timing bits.
//
class JfrTraceTagging : AllStatic {
 private:
  static void tag_dynamic(const InstanceKlass* ik);
  static void tag_dynamic(const Method* method);
  static void tag_sticky(const InstanceKlass* ik);
  static void tag_sticky(const Method* method);
  static void tag_sticky(const GrowableArray<JfrTracedMethod>* methods);
  static void tag_sticky_enqueue(const InstanceKlass* ik);
 public:
  static void clear_sticky(const InstanceKlass* ik, bool dynamic_tag = true);
  static void tag_sticky(const InstanceKlass* ik, const GrowableArray<JfrTracedMethod>* methods);
  static void tag_sticky_for_retransform_klass(const InstanceKlass* existing_klass, const InstanceKlass* scratch_klass, const GrowableArray<JfrTracedMethod>* methods, bool timing);
  static void on_klass_redefinition(const InstanceKlass* ik, const InstanceKlass* scratch_klass);
};

#endif /* SHARE_JFR_SUPPORT_METHODTRACER_JFRTRACETAGGING_HPP */
