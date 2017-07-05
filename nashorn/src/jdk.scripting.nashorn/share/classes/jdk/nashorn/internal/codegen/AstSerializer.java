/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.nashorn.internal.codegen;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.Statement;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * This static utility class performs serialization of FunctionNode ASTs to a byte array.
 * The format is a standard Java serialization stream, deflated.
 */
final class AstSerializer {
    // Experimentally, we concluded that compression level 4 gives a good tradeoff between serialization speed
    // and size.
    private static final int COMPRESSION_LEVEL = Options.getIntProperty("nashorn.serialize.compression", 4);
    static byte[] serialize(final FunctionNode fn) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Deflater deflater = new Deflater(COMPRESSION_LEVEL);
        try (final ObjectOutputStream oout = new ObjectOutputStream(new DeflaterOutputStream(out, deflater))) {
            oout.writeObject(removeInnerFunctionBodies(fn));
        } catch (final IOException e) {
            throw new AssertionError("Unexpected exception serializing function", e);
        } finally {
            deflater.end();
        }
        return out.toByteArray();
    }

    private static FunctionNode removeInnerFunctionBodies(final FunctionNode fn) {
        return (FunctionNode)fn.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            public Node leaveBlock(final Block block) {
                if (lc.isFunctionBody() && lc.getFunction(block) != lc.getOutermostFunction()) {
                    return block.setStatements(lc, Collections.<Statement>emptyList());
                }
                return super.leaveBlock(block);
            }
        });
    }
}
