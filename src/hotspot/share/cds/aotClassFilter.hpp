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

#ifndef SHARE_CDS_AOTCLASSFILTER_HPP
#define SHARE_CDS_AOTCLASSFILTER_HPP

#include "memory/allStatic.hpp"
#include "utilities/debug.hpp"

class InstanceKlass;
class Thread;

// Used by SystemDictionaryShared/AOTArtifactFinder to filter out classes that
// shouldn't be included into the AOT cache -- for example, classes that are used only
// in the training/assembly phases for building contents in the AOT cache.
//
// The only use case today is in lambdaFormInvokers.cpp.
class AOTClassFilter : AllStatic {
public:

  // Filters should be defined using RAII pattern
  class FilterMark {
  public:
    FilterMark();
    ~FilterMark();
    virtual bool is_aot_tooling_class(InstanceKlass* ik) = 0;
  };

  // Called when ik is being loaded. Return true iff this class is loaded
  // only because it's used by the AOT tooling code.
  static bool is_aot_tooling_class(InstanceKlass* ik);

private:
  // For the time being, we allow at most one filter.
  static FilterMark* _current_mark;
  static Thread* _filtering_thread;
};

#endif // SHARE_CDS_AOTCLASSFILTER_HPP
