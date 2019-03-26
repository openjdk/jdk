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

import java.util.Objects;

import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.Stamp;

import jdk.vm.ci.hotspot.HotSpotCompressedNullConstant;
import jdk.vm.ci.hotspot.HotSpotMemoryAccessProvider;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

public final class KlassPointerStamp extends MetaspacePointerStamp {

    private static final KlassPointerStamp KLASS = new KlassPointerStamp(false, false);

    private static final KlassPointerStamp KLASS_NON_NULL = new KlassPointerStamp(true, false);

    private static final KlassPointerStamp KLASS_ALWAYS_NULL = new KlassPointerStamp(false, true);

    private final CompressEncoding encoding;

    public static KlassPointerStamp klass() {
        return KLASS;
    }

    public static KlassPointerStamp klassNonNull() {
        return KLASS_NON_NULL;
    }

    public static KlassPointerStamp klassAlwaysNull() {
        return KLASS_ALWAYS_NULL;
    }

    private KlassPointerStamp(boolean nonNull, boolean alwaysNull) {
        this(nonNull, alwaysNull, null);
    }

    private KlassPointerStamp(boolean nonNull, boolean alwaysNull, CompressEncoding encoding) {
        super(nonNull, alwaysNull);
        this.encoding = encoding;
    }

    @Override
    public void accept(Visitor v) {
        super.accept(v);
        v.visitLong(encoding.getBase());
        v.visitInt(encoding.getShift());
    }

    @Override
    protected AbstractPointerStamp copyWith(boolean newNonNull, boolean newAlwaysNull) {
        return new KlassPointerStamp(newNonNull, newAlwaysNull, encoding);
    }

    @Override
    public boolean isCompatible(Stamp otherStamp) {
        if (this == otherStamp) {
            return true;
        }
        if (otherStamp instanceof KlassPointerStamp) {
            KlassPointerStamp other = (KlassPointerStamp) otherStamp;
            return Objects.equals(this.encoding, other.encoding);
        }
        return false;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        if (constant instanceof HotSpotMetaspaceConstant) {
            return ((HotSpotMetaspaceConstant) constant).asResolvedJavaType() != null;
        } else {
            return super.isCompatible(constant);
        }
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        if (isCompressed()) {
            if (HotSpotCompressedNullConstant.COMPRESSED_NULL.equals(c)) {
                return new KlassPointerStamp(false, true, encoding);
            }
        } else {
            if (JavaConstant.NULL_POINTER.equals(c)) {
                return KLASS_ALWAYS_NULL;
            }
        }

        assert c instanceof HotSpotMetaspaceConstant;
        assert ((HotSpotMetaspaceConstant) c).isCompressed() == isCompressed();
        if (nonNull()) {
            return this;
        }
        if (isCompressed()) {
            return new KlassPointerStamp(true, false, encoding);
        } else {
            return KLASS_NON_NULL;
        }
    }

    @Override
    public JavaConstant nullConstant() {
        if (isCompressed()) {
            return HotSpotCompressedNullConstant.COMPRESSED_NULL;
        } else {
            return super.nullConstant();
        }
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        if (isCompressed()) {
            return tool.getNarrowPointerKind();
        } else {
            return super.getLIRKind(tool);
        }
    }

    public boolean isCompressed() {
        return encoding != null;
    }

    public CompressEncoding getEncoding() {
        return encoding;
    }

    public KlassPointerStamp compressed(CompressEncoding newEncoding) {
        assert !isCompressed();
        return new KlassPointerStamp(nonNull(), alwaysNull(), newEncoding);
    }

    public KlassPointerStamp uncompressed() {
        assert isCompressed();
        return new KlassPointerStamp(nonNull(), alwaysNull());
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        HotSpotMemoryAccessProvider hsProvider = (HotSpotMemoryAccessProvider) provider;
        if (isCompressed()) {
            return hsProvider.readNarrowKlassPointerConstant(base, displacement);
        } else {
            return hsProvider.readKlassPointerConstant(base, displacement);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((encoding == null) ? 0 : encoding.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof KlassPointerStamp)) {
            return false;
        }
        KlassPointerStamp other = (KlassPointerStamp) obj;
        return Objects.equals(this.encoding, other.encoding);
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("Klass*");
        appendString(ret);
        if (isCompressed()) {
            ret.append("(compressed ").append(encoding).append(")");
        }
        return ret.toString();
    }
}
