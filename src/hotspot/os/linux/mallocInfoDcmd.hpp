/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_LINUX_MALLOCINFODCMD_HPP
#define OS_LINUX_MALLOCINFODCMD_HPP

#include "services/diagnosticCommand.hpp"

class outputStream;

class MallocInfoDcmd : public DCmd {
public:
  MallocInfoDcmd(outputStream* output, bool heap) : DCmd(output, heap) {}
  static const char* name() {
    return "System.native_heap_info";
  }
  static const char* description() {
    return "Attempts to output information regarding native heap usage through malloc_info(3). If unsuccessful outputs \"Error: \" and a reason.";
  }
  static const char* impact() {
    return "Low";
  }
  static const JavaPermission permission() {
    JavaPermission p = { "java.lang.management.ManagementPermission", "monitor", nullptr };
    return p;
  }
  void execute(DCmdSource source, TRAPS) override;
};

#endif // OS_LINUX_MALLOCINFODCMD_HPP
