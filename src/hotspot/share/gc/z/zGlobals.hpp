/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZGLOBALS_HPP
#define SHARE_GC_Z_ZGLOBALS_HPP

#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include CPU_HEADER(gc/z/zGlobals)

// Collector name
const char* const ZName                         = "The Z Garbage Collector";

// Global phase state
extern uint32_t   ZGlobalPhase;
const uint32_t    ZPhaseMark                    = 0;
const uint32_t    ZPhaseMarkCompleted           = 1;
const uint32_t    ZPhaseRelocate                = 2;

// Global sequence number
extern uint32_t   ZGlobalSeqNum;

// Granule shift/size
const size_t      ZGranuleSizeShift             = ZPlatformGranuleSizeShift;
const size_t      ZGranuleSize                  = (size_t)1 << ZGranuleSizeShift;

// Max heap size shift/size
const size_t      ZMaxHeapSizeShift             = ZPlatformMaxHeapSizeShift;
const size_t      ZMaxHeapSize                  = (size_t)1 << ZMaxHeapSizeShift;

// Page types
const uint8_t     ZPageTypeSmall                = 0;
const uint8_t     ZPageTypeMedium               = 1;
const uint8_t     ZPageTypeLarge                = 2;

// Page size shifts
const size_t      ZPageSizeSmallShift           = ZGranuleSizeShift;
const size_t      ZPageSizeMediumShift          = ZPageSizeSmallShift + 4;

// Page sizes
const size_t      ZPageSizeSmall                = (size_t)1 << ZPageSizeSmallShift;
const size_t      ZPageSizeMedium               = (size_t)1 << ZPageSizeMediumShift;

// Object size limits
const size_t      ZObjectSizeLimitSmall         = (ZPageSizeSmall / 8);  // Allow 12.5% waste
const size_t      ZObjectSizeLimitMedium        = (ZPageSizeMedium / 8); // Allow 12.5% waste

// Object alignment shifts
extern const int& ZObjectAlignmentSmallShift;
const int         ZObjectAlignmentMediumShift   = ZPageSizeMediumShift - 13; // 8192 objects per page
const int         ZObjectAlignmentLargeShift    = ZPageSizeSmallShift;

// Object alignments
extern const int& ZObjectAlignmentSmall;
const int         ZObjectAlignmentMedium        = 1 << ZObjectAlignmentMediumShift;
const int         ZObjectAlignmentLarge         = 1 << ZObjectAlignmentLargeShift;

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
extern uintptr_t  ZAddressGoodMask;
extern uintptr_t  ZAddressBadMask;
extern uintptr_t  ZAddressWeakBadMask;

// Pointer base address
extern uintptr_t  ZAddressBase;

// Pointer part of address
extern size_t     ZAddressOffsetBits;
const  size_t     ZAddressOffsetShift           = 0;
extern uintptr_t  ZAddressOffsetMask;
extern size_t     ZAddressOffsetMax;

// Metadata part of address
const size_t      ZAddressMetadataBits          = 4;
extern size_t     ZAddressMetadataShift;
extern uintptr_t  ZAddressMetadataMask;

// Metadata types
extern uintptr_t  ZAddressMetadataMarked;
extern uintptr_t  ZAddressMetadataMarked0;
extern uintptr_t  ZAddressMetadataMarked1;
extern uintptr_t  ZAddressMetadataRemapped;
extern uintptr_t  ZAddressMetadataFinalizable;

// NMethod entry barrier
const size_t      ZNMethodDisarmedOffset        = ZPlatformNMethodDisarmedOffset;

// Cache line size
const size_t      ZCacheLineSize                = ZPlatformCacheLineSize;

// Mark stack space
extern uintptr_t  ZMarkStackSpaceStart;
const size_t      ZMarkStackSpaceExpandSize     = (size_t)1 << 25; // 32M

// Mark stack and magazine sizes
const size_t      ZMarkStackSizeShift           = 11; // 2K
const size_t      ZMarkStackSize                = (size_t)1 << ZMarkStackSizeShift;
const size_t      ZMarkStackHeaderSize          = (size_t)1 << 4; // 16B
const size_t      ZMarkStackSlots               = (ZMarkStackSize - ZMarkStackHeaderSize) / sizeof(uintptr_t);
const size_t      ZMarkStackMagazineSize        = (size_t)1 << 15; // 32K
const size_t      ZMarkStackMagazineSlots       = (ZMarkStackMagazineSize / ZMarkStackSize) - 1;

// Mark stripe size
const size_t      ZMarkStripeShift              = ZGranuleSizeShift;

// Max number of mark stripes
const size_t      ZMarkStripesMax               = 16; // Must be a power of two

// Mark cache size
const size_t      ZMarkCacheSize                = 1024; // Must be a power of two

// Partial array minimum size
const size_t      ZMarkPartialArrayMinSizeShift = 12; // 4K
const size_t      ZMarkPartialArrayMinSize      = (size_t)1 << ZMarkPartialArrayMinSizeShift;

// Max number of proactive/terminate flush attempts
const size_t      ZMarkProactiveFlushMax        = 10;
const size_t      ZMarkTerminateFlushMax        = 3;

// Try complete mark timeout
const uint64_t    ZMarkCompleteTimeout          = 1; // ms

#endif // SHARE_GC_Z_ZGLOBALS_HPP
