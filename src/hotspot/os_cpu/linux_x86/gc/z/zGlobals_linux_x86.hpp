/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_LINUX_X86_ZGLOBALS_LINUX_X86_HPP
#define OS_CPU_LINUX_X86_ZGLOBALS_LINUX_X86_HPP

//
// Page Allocation Tiers
// ---------------------
//
//  Page Type     Page Size     Object Size Limit     Object Alignment
//  ------------------------------------------------------------------
//  Small         2M            <= 265K               <MinObjAlignmentInBytes>
//  Medium        32M           <= 4M                 4K
//  Large         X*M           > 4M                  2M
//  ------------------------------------------------------------------
//
//
// Address Space & Pointer Layout
// ------------------------------
//
//  +--------------------------------+ 0x00007FFFFFFFFFFF (127TB)
//  .                                .
//  .                                .
//  .                                .
//  +--------------------------------+ 0x0000140000000000 (20TB)
//  |         Remapped View          |
//  +--------------------------------+ 0x0000100000000000 (16TB)
//  |     (Reserved, but unused)     |
//  +--------------------------------+ 0x00000c0000000000 (12TB)
//  |         Marked1 View           |
//  +--------------------------------+ 0x0000080000000000 (8TB)
//  |         Marked0 View           |
//  +--------------------------------+ 0x0000040000000000 (4TB)
//  .                                .
//  +--------------------------------+ 0x0000000000000000
//
//
//   6                 4 4 4  4 4                                             0
//   3                 7 6 5  2 1                                             0
//  +-------------------+-+----+-----------------------------------------------+
//  |00000000 00000000 0|0|1111|11 11111111 11111111 11111111 11111111 11111111|
//  +-------------------+-+----+-----------------------------------------------+
//  |                   | |    |
//  |                   | |    * 41-0 Object Offset (42-bits, 4TB address space)
//  |                   | |
//  |                   | * 45-42 Metadata Bits (4-bits)  0001 = Marked0      (Address view 4-8TB)
//  |                   |                                 0010 = Marked1      (Address view 8-12TB)
//  |                   |                                 0100 = Remapped     (Address view 16-20TB)
//  |                   |                                 1000 = Finalizable  (Address view N/A)
//  |                   |
//  |                   * 46-46 Unused (1-bit, always zero)
//  |
//  * 63-47 Fixed (17-bits, always zero)
//

const size_t    ZPlatformPageSizeSmallShift    = 21; // 2M

const size_t    ZPlatformAddressOffsetBits     = 42; // 4TB

const uintptr_t ZPlatformAddressMetadataShift  = ZPlatformAddressOffsetBits;

const uintptr_t ZPlatformAddressSpaceStart     = (uintptr_t)1 << ZPlatformAddressOffsetBits;
const uintptr_t ZPlatformAddressSpaceSize      = ((uintptr_t)1 << ZPlatformAddressOffsetBits) * 4;

const size_t    ZPlatformNMethodDisarmedOffset = 4;

const size_t    ZPlatformCacheLineSize         = 64;

#endif // OS_CPU_LINUX_X86_ZGLOBALS_LINUX_X86_HPP
