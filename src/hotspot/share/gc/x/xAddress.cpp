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

#include "precompiled.hpp"
#include "gc/x/xAddress.hpp"
#include "gc/x/xGlobals.hpp"

void XAddress::set_good_mask(uintptr_t mask) {
  XAddressGoodMask = mask;
  XAddressBadMask = XAddressGoodMask ^ XAddressMetadataMask;
  XAddressWeakBadMask = (XAddressGoodMask | XAddressMetadataRemapped | XAddressMetadataFinalizable) ^ XAddressMetadataMask;
}

void XAddress::initialize() {
  XAddressOffsetBits = XPlatformAddressOffsetBits();
  XAddressOffsetMask = (((uintptr_t)1 << XAddressOffsetBits) - 1) << XAddressOffsetShift;
  XAddressOffsetMax = (uintptr_t)1 << XAddressOffsetBits;

  XAddressMetadataShift = XPlatformAddressMetadataShift();
  XAddressMetadataMask = (((uintptr_t)1 << XAddressMetadataBits) - 1) << XAddressMetadataShift;

  XAddressMetadataMarked0 = (uintptr_t)1 << (XAddressMetadataShift + 0);
  XAddressMetadataMarked1 = (uintptr_t)1 << (XAddressMetadataShift + 1);
  XAddressMetadataRemapped = (uintptr_t)1 << (XAddressMetadataShift + 2);
  XAddressMetadataFinalizable = (uintptr_t)1 << (XAddressMetadataShift + 3);

  XAddressMetadataMarked = XAddressMetadataMarked0;
  set_good_mask(XAddressMetadataRemapped);
}

void XAddress::flip_to_marked() {
  XAddressMetadataMarked ^= (XAddressMetadataMarked0 | XAddressMetadataMarked1);
  set_good_mask(XAddressMetadataMarked);
}

void XAddress::flip_to_remapped() {
  set_good_mask(XAddressMetadataRemapped);
}
