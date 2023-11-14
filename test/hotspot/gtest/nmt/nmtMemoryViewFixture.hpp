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
 */

#include "utilities/globalDefinitions.hpp"
#include "nmt/virtualMemoryView.hpp"
#include "unittest.hpp"

using VMV = VirtualMemoryView;

class NmtVirtualMemoryViewTest : public testing::Test  {
public:
  NmtVirtualMemoryViewTest() {}
  ~NmtVirtualMemoryViewTest() override {}
  // Must match that of VirtualMemoryView.
  enum class OverlappingResult {
    NoOverlap,
    EntirelyEnclosed,
    SplitInMiddle,
    ShortenedFromLeft,
    ShortenedFromRight,
  };
  struct R{uint64_t start; uint64_t end;};
  struct OutR{int len; R out[3]; NmtVirtualMemoryViewTest::OverlappingResult result;};
  OutR overlap(R a, R b) {
    VMV::TrackedOffsetRange aa{(address)a.start, (size_t)a.end-a.start};
    VMV::Range bb{(address)b.start, (size_t)b.end-b.start};
    VMV::TrackedOffsetRange out[3];
    int len;
    VMV::OverlappingResult ores = VMV::overlap_of(aa, bb, out, &len);
    OutR ret;
    ret.result = (OverlappingResult)(int)ores;
    ret.len = len;
    for (int i = 0; i < len; i++) {
      ret.out[i] = R{(uint64_t)out[i].start, (uint64_t)(out[i].start + out[i].size)};
    };
    return ret;
  };
};
