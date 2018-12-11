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

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class MetaspacePointerStamp extends AbstractPointerStamp {

    protected MetaspacePointerStamp(boolean nonNull, boolean alwaysNull) {
        super(nonNull, alwaysNull);
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        return tool.getWordKind();
    }

    @Override
    public Stamp empty() {
        // there is no empty pointer stamp
        return this;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        return constant.isDefaultForKind();
    }

    @Override
    public boolean hasValues() {
        return true;
    }

    @Override
    public Stamp join(Stamp other) {
        return defaultPointerJoin(other);
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        throw GraalError.shouldNotReachHere("metaspace pointer has no Java type");
    }

    protected void appendString(StringBuilder str) {
        str.append(nonNull() ? "!" : "").append(alwaysNull() ? " NULL" : "");
    }

}
