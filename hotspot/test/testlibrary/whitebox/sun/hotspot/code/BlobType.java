/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.hotspot.code;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.EnumSet;

import sun.hotspot.WhiteBox;

public enum BlobType {
    // Execution level 1 and 4 (non-profiled) nmethods (including native nmethods)
    MethodNonProfiled(0, "CodeHeap 'non-profiled nmethods'"),
    // Execution level 2 and 3 (profiled) nmethods
    MethodProfiled(1, "CodeHeap 'profiled nmethods'"),
    // Non-nmethods like Buffers, Adapters and Runtime Stubs
    NonNMethod(2, "CodeHeap 'non-nmethods'") {
        @Override
        public boolean allowTypeWhenOverflow(BlobType type) {
            return super.allowTypeWhenOverflow(type)
                    || type == BlobType.MethodNonProfiled;
        }
    },
    // All types (No code cache segmentation)
    All(3, "CodeCache");

    public final int id;
    private final String beanName;

    private BlobType(int id, String beanName) {
        this.id = id;
        this.beanName = beanName;
    }

    public MemoryPoolMXBean getMemoryPool() {
        for (MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
            String name = bean.getName();
            if (beanName.equals(name)) {
                return bean;
            }
        }
        return null;
    }

    public boolean allowTypeWhenOverflow(BlobType type) {
        return type == this;
    }

    public static EnumSet<BlobType> getAvailable() {
        WhiteBox whiteBox = WhiteBox.getWhiteBox();
        if (!whiteBox.getBooleanVMFlag("SegmentedCodeCache")) {
            // only All for non segmented world
            return EnumSet.of(All);
        }
        if (System.getProperty("java.vm.info").startsWith("interpreted ")) {
            // only NonNMethod for -Xint
            return EnumSet.of(NonNMethod);
        }

        EnumSet<BlobType> result = EnumSet.complementOf(EnumSet.of(All));
        if (!whiteBox.getBooleanVMFlag("TieredCompilation")
                || whiteBox.getIntxVMFlag("TieredStopAtLevel") <= 1) {
            // there is no MethodProfiled in non tiered world or pure C1
            result.remove(MethodProfiled);
        }
        return result;
    }
}
