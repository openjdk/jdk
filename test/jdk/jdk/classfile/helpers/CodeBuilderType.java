/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package helpers;

import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.function.Consumer;

import jdk.internal.classfile.impl.BlockCodeBuilderImpl;
import jdk.internal.classfile.impl.BufferedCodeBuilder;
import jdk.internal.classfile.impl.ChainedCodeBuilder;
import jdk.internal.classfile.impl.DirectCodeBuilder;

/**
 * A utility to test behavior of an API across all implementations of CodeBuilder.
 */
public enum CodeBuilderType {
    DIRECT(DirectCodeBuilder.class, true) {
        @Override
        Consumer<ClassBuilder> asClassHandler0(String name, MethodTypeDesc mtd, int flags, Consumer<CodeBuilder> codeHandler) {
            return clb -> clb.withMethodBody(name, mtd, flags, codeHandler);
        }
    },
    BUFFERED(BufferedCodeBuilder.class, true) {
        @Override
        Consumer<ClassBuilder> asClassHandler0(String name, MethodTypeDesc mtd, int flags, Consumer<CodeBuilder> codeHandler) {
            var bytes = ClassFile.of().build(ClassDesc.of("Dummy"), clb -> clb.withMethod(name, mtd, flags, _ -> {
            }));
            var mm = ClassFile.of().parse(bytes).methods().getFirst();
            return clb -> clb.transformMethod(mm, new MethodTransform() {
                @Override
                public void accept(MethodBuilder builder, MethodElement element) {
                }

                @Override
                public void atEnd(MethodBuilder builder) {
                    builder.withCode(codeHandler);
                }
            }.andThen(MethodTransform.ACCEPT_ALL));
        }
    },
    CHAINED(ChainedCodeBuilder.class, false) {
        @Override
        Consumer<ClassBuilder> asClassHandler0(String name, MethodTypeDesc mtd, int flags, Consumer<CodeBuilder> codeHandler) {
            return clb -> clb.withMethodBody(name, mtd, flags, cob -> cob.transforming(CodeTransform.ACCEPT_ALL, codeHandler));
        }
    },
    BLOCK(BlockCodeBuilderImpl.class, false) {
        @Override
        Consumer<ClassBuilder> asClassHandler0(String name, MethodTypeDesc mtd, int flags, Consumer<CodeBuilder> codeHandler) {
            return clb -> clb.withMethodBody(name, mtd, flags, cob -> cob.block(codeHandler::accept));
        }
    };

    public final Class<? extends CodeBuilder> clz;
    public final boolean terminal;

    CodeBuilderType(Class<? extends CodeBuilder> clz, boolean operational) {
        this.clz = clz;
        this.terminal = operational;
    }

    public Consumer<ClassBuilder> asClassHandler(String name, MethodTypeDesc mtd, int flags, Consumer<CodeBuilder> codeHandler) {
        Consumer<CodeBuilder> actualHandler = cob -> {
            assert clz.isInstance(cob) : cob.getClass() + " != " + clz;
            codeHandler.accept(cob);
        };
        return asClassHandler0(name, mtd, flags, actualHandler);
    }
    abstract Consumer<ClassBuilder> asClassHandler0(String name, MethodTypeDesc mtd, int flags, Consumer<CodeBuilder> codeHandler);
}
