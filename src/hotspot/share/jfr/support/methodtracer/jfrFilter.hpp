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

#ifndef SHARE_JFR_SUPPORT_METHODTRACER_JFRFILTER_HPP
#define SHARE_JFR_SUPPORT_METHODTRACER_JFRFILTER_HPP

#include "jni.h"
#include "memory/allocation.hpp"
#include "oops/annotations.hpp"

class InstanceKlass;
class Method;
class ModuleEntry;
class Symbol;

//
// Class that holds the configured filters.
//
// For information on how they are configured,
// see jdk.jfr.internal.JVM::setMethodTraceFilters(...).
//
class JfrFilter : public CHeapObj<mtTracing> {
 private:
  Symbol** _class_names;
  Symbol** _method_names;
  Symbol** _annotation_names;
  int*     _modifications;
  int      _count;

  JfrFilter(Symbol** class_names,
            Symbol** method_names,
            Symbol** annotation_names,
            int* modifications,
            int count);
 public:
  static JfrFilter* from(jobjectArray classses,
                         jobjectArray methods,
                         jobjectArray annotations,
                         jintArray modifications, TRAPS);
  static int combine_bits(int a, int b);
  ~JfrFilter();
  bool can_instrument_method(const Method* m) const;
  bool can_instrument_class(const InstanceKlass* m) const;
  bool can_instrument_module(const ModuleEntry* ik) const;
  int class_modifications(const InstanceKlass* klass, bool log) const;
  int method_modifications(const Method* method) const;
  bool match(const InstanceKlass* klass) const;
  bool match_annotations(const InstanceKlass* klass, AnnotationArray* annotation, const Symbol* symbol, bool log) const;
  // Requires ResourceMark
  void log(const char* caption) const;
};

#endif // SHARE_JFR_SUPPORT_METHODTRACER_JFRFILTER_HPP
