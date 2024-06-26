/*
 * Copyright (c) 2016, 2024, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_G1_G1ARRAYSLICER_HPP
#define SHARE_GC_G1_G1ARRAYSLICER_HPP

#include "oops/oopsHierarchy.hpp"
#include "gc/g1/g1TaskQueueEntry.hpp"

class Klass;

class G1ArraySlicer {
public:
  virtual void scan_metadata(objArrayOop array) = 0;
  virtual void push_on_queue(G1TaskQueueEntry task) = 0;
  virtual size_t scan_array(objArrayOop array, int from, int len) = 0;

  size_t process_objArray(oop obj);
  size_t process_slice(objArrayOop array, int slice, int pow);
};

#endif // SHARE_GC_G1_G1ARRAYSLICER_HPP
