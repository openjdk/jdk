/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/x/xGlobals.hpp"

uint32_t   XGlobalPhase                = XPhaseRelocate;
uint32_t   XGlobalSeqNum               = 1;

size_t     XPageSizeMediumShift;
size_t     XPageSizeMedium;

size_t     XObjectSizeLimitMedium;

const int& XObjectAlignmentSmallShift  = LogMinObjAlignmentInBytes;
int        XObjectAlignmentMediumShift;

const int& XObjectAlignmentSmall       = MinObjAlignmentInBytes;
int        XObjectAlignmentMedium;

uintptr_t  XAddressGoodMask;
uintptr_t  XAddressBadMask;
uintptr_t  XAddressWeakBadMask;

static uint32_t* XAddressCalculateBadMaskHighOrderBitsAddr() {
  const uintptr_t addr = reinterpret_cast<uintptr_t>(&XAddressBadMask);
  return reinterpret_cast<uint32_t*>(addr + XAddressBadMaskHighOrderBitsOffset);
}

uint32_t*  XAddressBadMaskHighOrderBitsAddr = XAddressCalculateBadMaskHighOrderBitsAddr();

size_t     XAddressOffsetBits;
uintptr_t  XAddressOffsetMask;
size_t     XAddressOffsetMax;

size_t     XAddressMetadataShift;
uintptr_t  XAddressMetadataMask;

uintptr_t  XAddressMetadataMarked;
uintptr_t  XAddressMetadataMarked0;
uintptr_t  XAddressMetadataMarked1;
uintptr_t  XAddressMetadataRemapped;
uintptr_t  XAddressMetadataFinalizable;

const char* XGlobalPhaseToString() {
  switch (XGlobalPhase) {
  case XPhaseMark:
    return "Mark";

  case XPhaseMarkCompleted:
    return "MarkCompleted";

  case XPhaseRelocate:
    return "Relocate";

  default:
    return "Unknown";
  }
}
