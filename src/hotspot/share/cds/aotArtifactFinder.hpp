/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_AOTARTIFACTFINDER_HPP
#define SHARE_CDS_AOTARTIFACTFINDER_HPP

#include "memory/allStatic.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/growableArray.hpp"

class InstanceKlass;
class MetaspaceClosure;

class AOTArtifactFinder : AllStatic {
  // All the classes that should be included in the AOT cache (in at least the "allocated" state)
  static GrowableArrayCHeap<InstanceKlass*, mtClassShared>* _classes;

  static GrowableArrayCHeap<InstanceKlass*, mtClassShared>* _pending_aot_inited_classes;


  static void start_scanning_for_oops();
  static void end_scanning_for_oops();
  static void scan_oops_in_instance_class(InstanceKlass* ik);
  static void scan_oops_in_array_class(ArrayKlass* ak);
  static bool is_lambda_proxy_class(InstanceKlass* ik);
public:
  static void initialize();
  static void find_artifacts();
  static void classes_do(MetaspaceClosure* it);
  static void add_aot_inited_class(InstanceKlass* ik);
  static void add_class(InstanceKlass* ik);
  static void dispose();

};

#endif // SHARE_CDS_AOTARTIFACTFINDER_HPP
