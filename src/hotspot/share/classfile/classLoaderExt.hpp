/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CLASSFILE_CLASSLOADEREXT_HPP
#define SHARE_CLASSFILE_CLASSLOADEREXT_HPP

#include "classfile/classLoader.hpp"
#include "classfile/moduleEntry.hpp"
#include "utilities/macros.hpp"

class ClassListParser;

class ClassLoaderExt: public ClassLoader { // AllStatic
public:
#if INCLUDE_CDS
public:
  // Called by JVMTI code to add boot classpath

  static void append_boot_classpath(ClassPathEntry* new_entry);

  static int compare_module_names(const char** p1, const char** p2);
  static void record_result_for_builtin_loader(s2 classpath_index, InstanceKlass* result, bool redefined);
#endif // INCLUDE_CDS
};

#endif // SHARE_CLASSFILE_CLASSLOADEREXT_HPP
