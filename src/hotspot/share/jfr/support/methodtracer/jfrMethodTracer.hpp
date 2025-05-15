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

#include "memory/allocation.hpp"
#include "jfr/utilities/jfrTypes.hpp"

class ClassFileParser;
class InstanceKlass;
class JavaThread;
class JfrFilter;
class JfrInstrumentedClass;
class JfrMethodProcessor;
class JfrTracedMethod;
class ModuleEntry;

template <typename E> class GrowableArray;

//
// Class responsible for holding filters, collecting methods to
// be instrumented and calling Java to create the appropriate bytecode.
///
class JfrMethodTracer: AllStatic {
 private:
  static const JfrFilter*                     _filter;               // Guarded by JfrMethodTracer_lock
  static const JfrFilter*                     _previous_filter;      // Guarded by JfrMethodTracer_lock
  static ModuleEntry*                         _jdk_jfr_module;       // Guarded by Module_lock
  static GrowableArray<JfrInstrumentedClass>* _instrumented_classes; // Guarded by ClassLoaderDataGraph_lock
  static GrowableArray<jlong>*                _unloaded_class_ids;   // Guarded by ClassLoaderDataGraph_lock

  static GrowableArray<jlong>* collect_new_timing_entries();
  static ModuleEntry* jdk_jfr_module();
  static void retransform(JNIEnv* env, GrowableArray<jclass>* class_array, TRAPS);
  static void set_filters(const JfrFilter* previous_filter, const JfrFilter* filter);
  static const JfrFilter* current_filter();
 public:
  static bool in_use();
  static jlongArray set_filters(JNIEnv* env, jobjectArray classes, jobjectArray methods, jobjectArray annotations, jintArray modifications, TRAPS);
  static void on_klass_creation(InstanceKlass*& ik, ClassFileParser& parser, TRAPS);
  static void on_klass_redefinition(const InstanceKlass* ik, Thread* thread);
  static GrowableArray<JfrInstrumentedClass>* instrumented_classes();
  static void add_instrumented_class(InstanceKlass* ik, GrowableArray<JfrTracedMethod>* methods);
  static void clear_instrumented_classes();
  static jlongArray drain_stale_class_ids(TRAPS);
  static void trim_instrumented_classes();
};

#endif // SHARE_JFR_SUPPORT_METHODTRACER_JFRMETHODTRACER_HPP
