/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.gc.x;

import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.types.Field;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;

public class XGlobals {
    private static Field instanceField;

    // Global phase state
    public static int XPhaseRelocate;

    public static byte XPageTypeSmall;
    public static byte XPageTypeMedium;
    public static byte XPageTypeLarge;

    // Granule size shift
    public static long XGranuleSizeShift;

    // Page size shifts
    public static long XPageSizeSmallShift;
    public static long XPageSizeMediumShift;

    // Object alignment shifts
    public static int  XObjectAlignmentMediumShift;
    public static int  XObjectAlignmentLargeShift;

    // Pointer part of address
    public static long XAddressOffsetShift;

    // Pointer part of address
    public static long XAddressOffsetBits;
    public static long XAddressOffsetMax;

    static {
        VM.registerVMInitializedObserver((o, d) -> initialize(VM.getVM().getTypeDataBase()));
    }

    private static synchronized void initialize(TypeDataBase db) {
        Type type = db.lookupType("XGlobalsForVMStructs");

        instanceField = type.getField("_instance_p");

        XPhaseRelocate = db.lookupIntConstant("XPhaseRelocate").intValue();

        XPageTypeSmall = db.lookupIntConstant("XPageTypeSmall").byteValue();
        XPageTypeMedium = db.lookupIntConstant("XPageTypeMedium").byteValue();
        XPageTypeLarge = db.lookupIntConstant("XPageTypeLarge").byteValue();

        XGranuleSizeShift = db.lookupLongConstant("XGranuleSizeShift").longValue();

        XPageSizeSmallShift = db.lookupLongConstant("XPageSizeSmallShift").longValue();
        XPageSizeMediumShift = db.lookupLongConstant("XPageSizeMediumShift").longValue();

        XObjectAlignmentMediumShift = db.lookupIntConstant("XObjectAlignmentMediumShift").intValue();
        XObjectAlignmentLargeShift = db.lookupIntConstant("XObjectAlignmentLargeShift").intValue();

        XAddressOffsetShift = db.lookupLongConstant("XAddressOffsetShift").longValue();

        XAddressOffsetBits = db.lookupLongConstant("XAddressOffsetBits").longValue();
        XAddressOffsetMax  = db.lookupLongConstant("XAddressOffsetMax").longValue();
    }

    private static XGlobalsForVMStructs instance() {
        return new XGlobalsForVMStructs(instanceField.getAddress());
    }

    public static int XGlobalPhase() {
        return instance().XGlobalPhase();
    }

    public static int XGlobalSeqNum() {
        return instance().XGlobalSeqNum();
    }

    public static long XAddressOffsetMask() {
        return instance().XAddressOffsetMask();
    }

    public static long XAddressMetadataMask() {
        return instance().XAddressMetadataMask();
    }

    public static long XAddressMetadataFinalizable() {
        return instance().XAddressMetadataFinalizable();
    }

    public static long XAddressGoodMask() {
        return instance().XAddressGoodMask();
    }

    public static long XAddressBadMask() {
        return instance().XAddressBadMask();
    }

    public static long XAddressWeakBadMask() {
        return instance().XAddressWeakBadMask();
    }

    public static int XObjectAlignmentSmallShift() {
        return instance().XObjectAlignmentSmallShift();
    }

    public static int XObjectAlignmentSmall() {
        return instance().XObjectAlignmentSmall();
    }
}
