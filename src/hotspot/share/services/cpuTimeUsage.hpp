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

#ifndef SHARE_SERVICES_CPUTIMEUSAGE_HPP
#define SHARE_SERVICES_CPUTIMEUSAGE_HPP

#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

namespace CPUTimeUsage {
  class GC : public AllStatic {
  public:
    static jlong total();
    static jlong gc_threads();
    static jlong vm_thread();
    static jlong stringdedup();
  };

  class Error : public AllStatic {
  private:
    static volatile bool _has_error;

  public:
    static bool has_error();
    static void mark_error();
  };
}

#endif // SHARE_SERVICES_CPUTIMEUSAGE_HPP
