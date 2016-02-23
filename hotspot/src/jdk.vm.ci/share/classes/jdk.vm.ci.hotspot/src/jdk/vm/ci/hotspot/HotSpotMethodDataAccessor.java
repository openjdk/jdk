/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import static jdk.vm.ci.hotspot.HotSpotVMConfig.config;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.TriState;

/**
 * Interface for accessor objects that encapsulate the logic for accessing the different kinds of
 * data in a HotSpot methodDataOop. This interface is similar to the interface {@link ProfilingInfo}
 * , but most methods require a MethodDataObject and the exact position within the methodData.
 */
public interface HotSpotMethodDataAccessor {

    /**
     * {@code DataLayout} tag values.
     */
    enum Tag {
        No(config().dataLayoutNoTag),
        BitData(config().dataLayoutBitDataTag),
        CounterData(config().dataLayoutCounterDataTag),
        JumpData(config().dataLayoutJumpDataTag),
        ReceiverTypeData(config().dataLayoutReceiverTypeDataTag),
        VirtualCallData(config().dataLayoutVirtualCallDataTag),
        RetData(config().dataLayoutRetDataTag),
        BranchData(config().dataLayoutBranchDataTag),
        MultiBranchData(config().dataLayoutMultiBranchDataTag),
        ArgInfoData(config().dataLayoutArgInfoDataTag),
        CallTypeData(config().dataLayoutCallTypeDataTag),
        VirtualCallTypeData(config().dataLayoutVirtualCallTypeDataTag),
        ParametersTypeData(config().dataLayoutParametersTypeDataTag),
        SpeculativeTrapData(config().dataLayoutSpeculativeTrapDataTag);

        private final int value;

        Tag(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Tag getEnum(int value) {
            Tag result = values()[value];
            assert value == result.value;
            return result;
        }
    }

    /**
     * Returns the {@link Tag} stored in the LayoutData header.
     *
     * @return tag stored in the LayoutData header
     */
    Tag getTag();

    /**
     * Returns the BCI stored in the LayoutData header.
     *
     * @return An integer &ge; 0 and &le; Short.MAX_VALUE, or -1 if not supported.
     */
    int getBCI(HotSpotMethodData data, int position);

    /**
     * Computes the size for the specific data at the given position.
     *
     * @return An integer &gt; 0.
     */
    int getSize(HotSpotMethodData data, int position);

    JavaTypeProfile getTypeProfile(HotSpotMethodData data, int position);

    JavaMethodProfile getMethodProfile(HotSpotMethodData data, int position);

    double getBranchTakenProbability(HotSpotMethodData data, int position);

    double[] getSwitchProbabilities(HotSpotMethodData data, int position);

    TriState getExceptionSeen(HotSpotMethodData data, int position);

    TriState getNullSeen(HotSpotMethodData data, int position);

    int getExecutionCount(HotSpotMethodData data, int position);

    StringBuilder appendTo(StringBuilder sb, HotSpotMethodData data, int pos);
}
