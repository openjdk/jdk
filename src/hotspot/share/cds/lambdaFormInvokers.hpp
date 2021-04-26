/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_LAMBDAFORMINVOKERS_HPP
#define SHARE_CDS_LAMBDAFORMINVOKERS_HPP
#include "memory/allStatic.hpp"
#include "runtime/handles.hpp"
#include "utilities/growableArray.hpp"

class ClassFileStream;

class LambdaFormInvokers : public AllStatic {
 private:
  static GrowableArrayCHeap<char*, mtClassShared>* _lambdaform_lines;
  static void reload_class(char* name, ClassFileStream& st, TRAPS);

 public:
  static void append(char* line);
  static void append_filtered(char* line);
  static void regenerate_holder_classes(TRAPS);
  static GrowableArrayCHeap<char*, mtClassShared>* lambdaform_lines() {
    return _lambdaform_lines;
  }
  static size_t total_bytes();
  static bool should_regenerate_holder_classes() {
    assert(DynamicDumpSharedSpaces, "Dynamic dump only");
    return _lambdaform_lines != nullptr && _lambdaform_lines->length() > 0;
  }
};
#endif // SHARE_CDS_LAMBDAFORMINVOKERS_HPP
