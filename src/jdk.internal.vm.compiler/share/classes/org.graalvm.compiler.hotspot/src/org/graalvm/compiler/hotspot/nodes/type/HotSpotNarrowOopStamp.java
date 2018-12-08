/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.nodes.type;

import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.CompressionNode.CompressionOp;
import org.graalvm.compiler.nodes.type.NarrowOopStamp;

import jdk.vm.ci.hotspot.HotSpotCompressedNullConstant;
import jdk.vm.ci.hotspot.HotSpotMemoryAccessProvider;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class HotSpotNarrowOopStamp extends NarrowOopStamp {
    private HotSpotNarrowOopStamp(ResolvedJavaType type, boolean exactType, boolean nonNull, boolean alwaysNull, CompressEncoding encoding) {
        super(type, exactType, nonNull, alwaysNull, encoding);
    }

    @Override
    protected AbstractObjectStamp copyWith(ResolvedJavaType type, boolean exactType, boolean nonNull, boolean alwaysNull) {
        return new HotSpotNarrowOopStamp(type, exactType, nonNull, alwaysNull, getEncoding());
    }

    public static Stamp compressed(AbstractObjectStamp stamp, CompressEncoding encoding) {
        return new HotSpotNarrowOopStamp(stamp.type(), stamp.isExactType(), stamp.nonNull(), stamp.alwaysNull(), encoding);
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        try {
            HotSpotMemoryAccessProvider hsProvider = (HotSpotMemoryAccessProvider) provider;
            return hsProvider.readNarrowOopConstant(base, displacement);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public JavaConstant nullConstant() {
        return HotSpotCompressedNullConstant.COMPRESSED_NULL;
    }

    @Override
    public boolean isCompatible(Constant other) {
        if (other instanceof HotSpotObjectConstant) {
            return ((HotSpotObjectConstant) other).isCompressed();
        }
        return true;
    }

    public static Stamp mkStamp(CompressionOp op, Stamp input, CompressEncoding encoding) {
        switch (op) {
            case Compress:
                if (input instanceof ObjectStamp) {
                    // compressed oop
                    return HotSpotNarrowOopStamp.compressed((ObjectStamp) input, encoding);
                } else if (input instanceof KlassPointerStamp) {
                    // compressed klass pointer
                    return ((KlassPointerStamp) input).compressed(encoding);
                }
                break;
            case Uncompress:
                if (input instanceof NarrowOopStamp) {
                    // oop
                    assert encoding.equals(((NarrowOopStamp) input).getEncoding());
                    return ((NarrowOopStamp) input).uncompressed();
                } else if (input instanceof KlassPointerStamp) {
                    // metaspace pointer
                    assert encoding.equals(((KlassPointerStamp) input).getEncoding());
                    return ((KlassPointerStamp) input).uncompressed();
                }
                break;
        }
        throw GraalError.shouldNotReachHere(String.format("Unexpected input stamp %s", input));
    }

}
