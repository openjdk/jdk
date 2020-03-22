/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.meta;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.compiler.nodes.gc.CardTableBarrierSet;
import org.graalvm.compiler.nodes.gc.G1BarrierSet;
import org.graalvm.compiler.nodes.java.AbstractNewObjectNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class HotSpotPlatformConfigurationProvider implements PlatformConfigurationProvider {
    private final BarrierSet barrierSet;

    private final boolean canVirtualizeLargeByteArrayAccess;

    public HotSpotPlatformConfigurationProvider(GraalHotSpotVMConfig config, MetaAccessProvider metaAccess) {
        this.barrierSet = createBarrierSet(config, metaAccess);
        this.canVirtualizeLargeByteArrayAccess = config.deoptimizationSupportLargeAccessByteArrayVirtualization;
    }

    @Override
    public boolean canVirtualizeLargeByteArrayAccess() {
        return canVirtualizeLargeByteArrayAccess;
    }

    @Override
    public BarrierSet getBarrierSet() {
        return barrierSet;
    }

    private BarrierSet createBarrierSet(GraalHotSpotVMConfig config, MetaAccessProvider metaAccess) {
        boolean useDeferredInitBarriers = config.useDeferredInitBarriers;
        ResolvedJavaType objectArrayType = metaAccess.lookupJavaType(Object[].class);
        if (config.useG1GC) {
            ResolvedJavaField referentField = HotSpotReplacementsUtil.referentField(metaAccess);
            return new G1BarrierSet(objectArrayType, referentField) {
                @Override
                protected boolean writeRequiresPostBarrier(FixedAccessNode node, ValueNode writtenValue) {
                    if (!super.writeRequiresPostBarrier(node, writtenValue)) {
                        return false;
                    }
                    return !useDeferredInitBarriers || !isWriteToNewObject(node);
                }
            };
        } else {
            return new CardTableBarrierSet(objectArrayType) {
                @Override
                protected boolean writeRequiresBarrier(FixedAccessNode node, ValueNode writtenValue) {
                    if (!super.writeRequiresBarrier(node, writtenValue)) {
                        return false;
                    }
                    return !useDeferredInitBarriers || !isWriteToNewObject(node);
                }

            };
        }
    }

    /**
     * For initializing writes, the last allocation executed by the JVM is guaranteed to be
     * automatically card marked so it's safe to skip the card mark in the emitted code.
     */
    protected boolean isWriteToNewObject(FixedAccessNode node) {
        if (!node.getLocationIdentity().isInit()) {
            return false;
        }
        // This is only allowed for the last allocation in sequence
        ValueNode base = node.getAddress().getBase();
        if (base instanceof AbstractNewObjectNode) {
            Node pred = node.predecessor();
            while (pred != null) {
                if (pred == base) {
                    return true;
                }
                if (pred instanceof AbstractNewObjectNode) {
                    node.getDebug().log(DebugContext.INFO_LEVEL, "Disallowed deferred init because %s was last allocation instead of %s", pred, base);
                    return false;
                }
                pred = pred.predecessor();
            }
        }
        node.getDebug().log(DebugContext.INFO_LEVEL, "Unable to find allocation for deferred init for %s with base %s", node, base);
        return false;
    }
}
