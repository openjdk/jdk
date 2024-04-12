/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XGLOBALS_HPP
#define SHARE_GC_X_XGLOBALS_HPP

#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include CPU_HEADER(gc/x/xGlobals)

// Collector name
const char* const XName                         = "The Z Garbage Collector";

// Global phase state
extern uint32_t   XGlobalPhase;
const uint32_t    XPhaseMark                    = 0;
const uint32_t    XPhaseMarkCompleted           = 1;
const uint32_t    XPhaseRelocate                = 2;
const char*       XGlobalPhaseToString();

// Global sequence number
extern uint32_t   XGlobalSeqNum;

// Granule shift/size
const size_t      XGranuleSizeShift             = 21; // 2MB
const size_t      XGranuleSize                  = (size_t)1 << XGranuleSizeShift;

// Number of heap views
const size_t      XHeapViews                    = XPlatformHeapViews;

// Virtual memory to physical memory ratio
const size_t      XVirtualToPhysicalRatio       = 16; // 16:1

// Page types
const uint8_t     XPageTypeSmall                = 0;
const uint8_t     XPageTypeMedium               = 1;
const uint8_t     XPageTypeLarge                = 2;

// Page size shifts
const size_t      XPageSizeSmallShift           = XGranuleSizeShift;
extern size_t     XPageSizeMediumShift;

// Page sizes
const size_t      XPageSizeSmall                = (size_t)1 << XPageSizeSmallShift;
extern size_t     XPageSizeMedium;

// Object size limits
const size_t      XObjectSizeLimitSmall         = XPageSizeSmall / 8; // 12.5% max waste
extern size_t     XObjectSizeLimitMedium;

// Object alignment shifts
extern const int& XObjectAlignmentSmallShift;
extern int        XObjectAlignmentMediumShift;
const int         XObjectAlignmentLargeShift    = XGranuleSizeShift;

// Object alignments
extern const int& XObjectAlignmentSmall;
extern int        XObjectAlignmentMedium;
const int         XObjectAlignmentLarge         = 1 << XObjectAlignmentLargeShift;

//
// Good/Bad mask states
// --------------------
//
//                 GoodMask         BadMask          WeakGoodMask     WeakBadMask
//                 --------------------------------------------------------------
//  Marked0        001              110              101              010
//  Marked1        010              101              110              001
//  Remapped       100              011              100              011
//

// Good/bad masks
extern uintptr_t  XAddressGoodMask;
extern uintptr_t  XAddressBadMask;
extern uintptr_t  XAddressWeakBadMask;

// The bad mask is 64 bit. Its high order 32 bits contain all possible value combinations
// that this mask will have. Therefore, the memory where the 32 high order bits are stored,
// can be used as a 32 bit GC epoch counter, that has a different bit pattern every time
// the bad mask is flipped. This provides a pointer to said 32 bits.
extern uint32_t*  XAddressBadMaskHighOrderBitsAddr;
const int         XAddressBadMaskHighOrderBitsOffset = LITTLE_ENDIAN_ONLY(4) BIG_ENDIAN_ONLY(0);

// Pointer part of address
extern size_t     XAddressOffsetBits;
const  size_t     XAddressOffsetShift           = 0;
extern uintptr_t  XAddressOffsetMask;
extern size_t     XAddressOffsetMax;

// Metadata part of address
const size_t      XAddressMetadataBits          = 4;
extern size_t     XAddressMetadataShift;
extern uintptr_t  XAddressMetadataMask;

// Metadata types
extern uintptr_t  XAddressMetadataMarked;
extern uintptr_t  XAddressMetadataMarked0;
extern uintptr_t  XAddressMetadataMarked1;
extern uintptr_t  XAddressMetadataRemapped;
extern uintptr_t  XAddressMetadataFinalizable;

// Cache line size
const size_t      XCacheLineSize                = XPlatformCacheLineSize;
#define           XCACHE_ALIGNED                ATTRIBUTE_ALIGNED(XCacheLineSize)

// Mark stack space
extern uintptr_t  XMarkStackSpaceStart;
const size_t      XMarkStackSpaceExpandSize     = (size_t)1 << 25; // 32M

// Mark stack and magazine sizes
const size_t      XMarkStackSizeShift           = 11; // 2K
const size_t      XMarkStackSize                = (size_t)1 << XMarkStackSizeShift;
const size_t      XMarkStackHeaderSize          = (size_t)1 << 4; // 16B
const size_t      XMarkStackSlots               = (XMarkStackSize - XMarkStackHeaderSize) / sizeof(uintptr_t);
const size_t      XMarkStackMagazineSize        = (size_t)1 << 15; // 32K
const size_t      XMarkStackMagazineSlots       = (XMarkStackMagazineSize / XMarkStackSize) - 1;

// Mark stripe size
const size_t      XMarkStripeShift              = XGranuleSizeShift;

// Max number of mark stripes
const size_t      XMarkStripesMax               = 16; // Must be a power of two

// Mark cache size
const size_t      XMarkCacheSize                = 1024; // Must be a power of two

// Partial array minimum size
const size_t      XMarkPartialArrayMinSizeShift = 12; // 4K
const size_t      XMarkPartialArrayMinSize      = (size_t)1 << XMarkPartialArrayMinSizeShift;

// Max number of proactive/terminate flush attempts
const size_t      XMarkProactiveFlushMax        = 10;
const size_t      XMarkTerminateFlushMax        = 3;

// Try complete mark timeout
const uint64_t    XMarkCompleteTimeout          = 200; // us

#endif // SHARE_GC_X_XGLOBALS_HPP
