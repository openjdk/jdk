/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_SUPPORT_JFRRESOLUTION_HPP
#define SHARE_SUPPORT_JFRRESOLUTION_HPP

#include "memory/allocation.hpp"
#include "utilities/exceptions.hpp"

class CallInfo;
class ciKlass;
class ciMethod;
class GraphBuilder;
class JavaThread;
class Parse;

class JfrResolution : AllStatic {
 public:
  static void on_runtime_resolution(const CallInfo & info, TRAPS);
  static void on_c1_resolution(const GraphBuilder * builder, const ciKlass * holder, const ciMethod * target);
  static void on_c2_resolution(const Parse * parse, const ciKlass * holder, const ciMethod * target);
  static void on_jvmci_resolution(const Method* caller, const Method* target, TRAPS);
  static void on_backpatching(const Method* callee_method, JavaThread* jt);
};

#endif // SHARE_SUPPORT_JFRRESOLUTION_HPP
