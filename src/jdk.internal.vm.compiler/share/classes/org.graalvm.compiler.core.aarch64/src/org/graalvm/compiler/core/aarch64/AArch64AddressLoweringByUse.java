/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, Red Hat Inc. All rights reserved.
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



package org.graalvm.compiler.core.aarch64;

import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.common.AddressLoweringByUsePhase;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.meta.JavaConstant;

public class AArch64AddressLoweringByUse extends AddressLoweringByUsePhase.AddressLoweringByUse {
    private AArch64LIRKindTool kindtool;

    public AArch64AddressLoweringByUse(AArch64LIRKindTool kindtool) {
        this.kindtool = kindtool;
    }

    @Override
    public AddressNode lower(ValueNode use, Stamp stamp, AddressNode address) {
        if (address instanceof OffsetAddressNode) {
            OffsetAddressNode offsetAddress = (OffsetAddressNode) address;
            return doLower(stamp, offsetAddress.getBase(), offsetAddress.getOffset());
        } else {
            // must be an already transformed AArch64AddressNode
            return address;
        }
    }

    @Override
    public AddressNode lower(AddressNode address) {
        return lower(null, null, address);
    }

    private AddressNode doLower(Stamp stamp, ValueNode base, ValueNode index) {
        AArch64AddressNode ret = new AArch64AddressNode(base, index);
        AArch64Kind aarch64Kind = (stamp == null ? null : getAArch64Kind(stamp));

        // improve the address as much as possible
        boolean changed;
        do {
            changed = improve(aarch64Kind, ret);
        } while (changed);

        // avoid duplicates
        return base.graph().unique(ret);
    }

    protected boolean improve(AArch64Kind kind, AArch64AddressNode ret) {
        AArch64Address.AddressingMode mode = ret.getAddressingMode();
        // if we have already set a displacement or set to base only mode then we are done
        if (isDisplacementMode(mode) || isBaseOnlyMode(mode)) {
            return false;
        }
        ValueNode base = ret.getBase();
        ValueNode index = ret.getIndex();

        // avoid a constant or null base if possible
        if (base == null) {
            ret.setBase(index);
            ret.setIndex(base);
            return true;
        }
        // make sure any integral JavaConstant
        // is the index rather than the base
        // strictly we don't need the conditions on index
        // as we ought not to see two JavaConstant values
        if (base.isJavaConstant() && base.asJavaConstant().getJavaKind().isNumericInteger() &&
                        index != null && !index.isJavaConstant()) {
            ret.setBase(index);
            ret.setIndex(base);
            return true;
        }

        // if the base is an add then move it up
        if (index == null && base instanceof AddNode) {
            AddNode add = (AddNode) base;
            ret.setBase(add.getX());
            ret.setIndex(add.getY());
            return true;
        }

        // we can try to fold a JavaConstant index into a displacement
        if (index != null && index.isJavaConstant()) {
            JavaConstant javaConstant = index.asJavaConstant();
            if (javaConstant.getJavaKind().isNumericInteger()) {
                long disp = javaConstant.asLong();
                mode = immediateMode(kind, disp);
                if (isDisplacementMode(mode)) {
                    index = null;
                    // we can fold this in as a displacement
                    // but first see if we can pull up any additional
                    // constants added into the base
                    boolean tryNextBase = (base instanceof AddNode);
                    while (tryNextBase) {
                        AddNode add = (AddNode) base;
                        tryNextBase = false;
                        ValueNode child = add.getX();
                        if (child.isJavaConstant() && child.asJavaConstant().getJavaKind().isNumericInteger()) {
                            long newDisp = disp + child.asJavaConstant().asLong();
                            AArch64Address.AddressingMode newMode = immediateMode(kind, newDisp);
                            if (newMode != AArch64Address.AddressingMode.REGISTER_OFFSET) {
                                disp = newDisp;
                                mode = newMode;
                                base = add.getY();
                                ret.setBase(base);
                                tryNextBase = (base instanceof AddNode);
                            }
                        } else {
                            child = add.getY();
                            if (child.isJavaConstant() && child.asJavaConstant().getJavaKind().isNumericInteger()) {
                                long newDisp = disp + child.asJavaConstant().asLong();
                                AArch64Address.AddressingMode newMode = immediateMode(kind, newDisp);
                                if (newMode != AArch64Address.AddressingMode.REGISTER_OFFSET) {
                                    disp = newDisp;
                                    mode = newMode;
                                    base = add.getX();
                                    ret.setBase(base);
                                    tryNextBase = (base instanceof AddNode);
                                }
                            }
                        }
                    }
                    if (disp != 0) {
                        // ok now set the displacement in place of an index
                        ret.setIndex(null);
                        int scaleFactor = computeScaleFactor(kind, mode);
                        ret.setDisplacement(disp, scaleFactor, mode);
                    } else {
                        // reset to base register only
                        ret.setIndex(null);
                        ret.setDisplacement(0, 1, AArch64Address.AddressingMode.BASE_REGISTER_ONLY);
                    }
                    return true;
                }
            }
        }
        // nope cannot improve this any more
        return false;
    }

    private AArch64Kind getAArch64Kind(Stamp stamp) {
        LIRKind lirKind = stamp.getLIRKind(kindtool);
        if (!lirKind.isValue()) {
            if (!lirKind.isReference(0) || lirKind.getReferenceCount() != 1) {
                return null;
            }
        }

        return (AArch64Kind) lirKind.getPlatformKind();
    }

    private static AArch64Address.AddressingMode immediateMode(AArch64Kind kind, long value) {
        if (kind != null) {
            int size = kind.getSizeInBytes();
            // this next test should never really fail
            if ((value & (size - 1)) == 0) {
                long encodedValue = value / size;
                // assert value % size == 0
                // we can try for a 12 bit scaled offset
                if (NumUtil.isUnsignedNbit(12, encodedValue)) {
                    return AArch64Address.AddressingMode.IMMEDIATE_SCALED;
                }
            }
        }

        // we can try for a 9 bit unscaled offset
        if (NumUtil.isSignedNbit(9, value)) {
            return AArch64Address.AddressingMode.IMMEDIATE_UNSCALED;
        }

        // nope this index needs to be passed via offset register
        return AArch64Address.AddressingMode.REGISTER_OFFSET;
    }

    private static int computeScaleFactor(AArch64Kind kind, AArch64Address.AddressingMode mode) {
        if (mode == AArch64Address.AddressingMode.IMMEDIATE_SCALED) {
            return kind.getSizeInBytes();
        }
        return 1;
    }

    boolean isBaseOnlyMode(AArch64Address.AddressingMode addressingMode) {
        return addressingMode == AArch64Address.AddressingMode.BASE_REGISTER_ONLY;
    }

    private static boolean isDisplacementMode(AArch64Address.AddressingMode addressingMode) {
        switch (addressingMode) {
            case IMMEDIATE_POST_INDEXED:
            case IMMEDIATE_PRE_INDEXED:
            case IMMEDIATE_SCALED:
            case IMMEDIATE_UNSCALED:
                return true;
        }
        return false;
    }
}
