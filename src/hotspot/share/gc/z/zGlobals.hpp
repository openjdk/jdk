/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

// Granule shift/size
const size_t      ZGranuleSizeShift             = 21; // 2MB
const size_t      ZGranuleSize                  = (size_t)1 << ZGranuleSizeShift;

// Virtual memory to physical memory ratio
const size_t      ZVirtualToPhysicalRatio       = 16; // 16:1

// Max virtual memory ranges
const size_t      ZMaxVirtualReservations       = 100; // Each reservation at least 1% of total

// Page size shifts
const int         ZPageSizeSmallShift           = (int)ZGranuleSizeShift;
extern int        ZPageSizeMediumMaxShift;

// Page sizes
const size_t      ZPageSizeSmall                = (size_t)1 << ZPageSizeSmallShift;
extern size_t     ZPageSizeMediumMax;
extern size_t     ZPageSizeMediumMin;
extern bool       ZPageSizeMediumEnabled;

// Object size limits
const size_t      ZObjectSizeLimitSmall         = ZPageSizeSmall / 8; // 12.5% max waste
extern size_t     ZObjectSizeLimitMedium;

// Object alignment shifts
extern const int& ZObjectAlignmentSmallShift;
extern int        ZObjectAlignmentMediumShift;
const int         ZObjectAlignmentLargeShift    = ZGranuleSizeShift;

// Object alignments
extern const int& ZObjectAlignmentSmall;
extern int        ZObjectAlignmentMedium;
const int         ZObjectAlignmentLarge         = 1 << ZObjectAlignmentLargeShift;

// Cache line size
const size_t      ZCacheLineSize                = ZPlatformCacheLineSize;
#define           ZCACHE_ALIGNED                ATTRIBUTE_ALIGNED(ZCacheLineSize)

// Mark stripe size
const size_t      ZMarkStripeShift              = ZGranuleSizeShift;

// Max number of mark stripes
const size_t      ZMarkStripesMax               = 16; // Must be a power of two

// Mark cache size
const size_t      ZMarkCacheSize                = 1024; // Must be a power of two

// Partial array minimum size
const size_t      ZMarkPartialArrayMinSizeShift = 12; // 4K
const size_t      ZMarkPartialArrayMinSize      = (size_t)1 << ZMarkPartialArrayMinSizeShift;
const size_t      ZMarkPartialArrayMinLength    = ZMarkPartialArrayMinSize / oopSize;

// Max number of proactive/terminate flush attempts
const size_t      ZMarkProactiveFlushMax        = 10;

// Try complete mark timeout
const uint64_t    ZMarkCompleteTimeout          = 200; // us

#endif // SHARE_GC_Z_ZGLOBALS_HPP
