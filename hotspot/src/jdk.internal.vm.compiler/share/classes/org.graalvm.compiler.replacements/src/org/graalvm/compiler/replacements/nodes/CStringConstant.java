/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.graalvm.compiler.core.common.type.DataPointerConstant;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Represents a compile-time constant zero-terminated UTF-8 string installed with the generated
 * code.
 */
public final class CStringConstant extends DataPointerConstant {

    private static final Charset UTF8 = Charset.forName("utf8");

    private final String string;

    public CStringConstant(String string) {
        super(1);
        assert string != null;
        this.string = string;
    }

    @Override
    public int getSerializedSize() {
        return string.getBytes(UTF8).length + 1;
    }

    @Override
    public void serialize(ByteBuffer buffer) {
        byte[] bytes = string.getBytes(UTF8);
        buffer.put(bytes);
        buffer.put((byte) 0);
    }

    @Override
    public String toValueString() {
        return "c\"" + string + "\"";
    }

    public static boolean intrinsify(GraphBuilderContext b, @SuppressWarnings("unused") ResolvedJavaMethod targetMethod, String string) {
        b.addPush(JavaKind.Object, new ConstantNode(new CStringConstant(string), StampFactory.pointer()));
        return true;
    }

    @NodeIntrinsic
    public static native Word cstring(@ConstantNodeParameter String string);
}
