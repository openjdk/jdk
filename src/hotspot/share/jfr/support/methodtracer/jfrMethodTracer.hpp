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

#ifndef SHARE_JFR_SUPPORT_METHODTRACER_JFRMETHODTRACER_HPP
#define SHARE_JFR_SUPPORT_METHODTRACER_JFRMETHODTRACER_HPP

#include "jni.h"
#include "jfr/support/methodtracer/jfrInstrumentedClass.hpp"
#include "jfr/support/methodtracer/jfrTracedMethod.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "memory/allocation.hpp"

class ClassFileParser;
class InstanceKlass;
class JavaThread;
class JfrFilterClassClosure;
class Klass;
class ModuleEntry;

template <typename E> class GrowableArray;

//
// Class responsible for installing and evaluating filters, collecting methods to
// be instrumented and calling Java to create the appropriate bytecode.
///
class JfrMethodTracer: AllStatic {
 private:
  static ModuleEntry*                         _jdk_jfr_module;          // Guarded by Module_lock
  static GrowableArray<JfrInstrumentedClass>* _instrumented_classes;    // Guarded by ClassLoaderDataGraph_lock
  static GrowableArray<jlong>*                _timing_entries;          // Guarded by ClassLoaderDataGraph_lock

  static ModuleEntry* jdk_jfr_module();
  static void add_timing_entry(traceid klass_id);
  static void retransform(JNIEnv* env, const JfrFilterClassClosure& classes, TRAPS);
  static void add_instrumented_class(InstanceKlass* ik, GrowableArray<JfrTracedMethod>* methods);

 public:
  static bool in_use();
  static jlongArray drain_stale_class_ids(TRAPS);
  static void add_to_unloaded_set(const Klass* k);
  static void trim_instrumented_classes(bool trim);
  static GrowableArray<JfrInstrumentedClass>* instrumented_classes();
  static void on_klass_redefinition(const InstanceKlass* ik, bool has_timing);
  static void on_klass_creation(InstanceKlass*& ik, ClassFileParser& parser, TRAPS);
  static jlongArray set_filters(JNIEnv* env,
                                jobjectArray classes,
                                jobjectArray methods,
                                jobjectArray annotations,
                                jintArray modifications,
                                TRAPS);
};

#endif // SHARE_JFR_SUPPORT_METHODTRACER_JFRMETHODTRACER_HPP
