/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.impl;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class CatchBuilderImpl implements CodeBuilder.CatchBuilder {
    final CodeBuilder b;
    final BlockCodeBuilderImpl tryBlock;
    final Label tryCatchEnd;
    BlockCodeBuilderImpl catchBlock;

    public CatchBuilderImpl(CodeBuilder b, BlockCodeBuilderImpl tryBlock, Label tryCatchEnd) {
        this.b = b;
        this.tryBlock = tryBlock;
        this.tryCatchEnd = tryCatchEnd;
    }

    @Override
    public CodeBuilder.CatchBuilder catching(ClassDesc exceptionType, Consumer<CodeBuilder.BlockCodeBuilder> catchHandler) {
        return catchingMulti(exceptionType == null ? List.of() : List.of(exceptionType), catchHandler);
    }

    @Override
    public CodeBuilder.CatchBuilder catchingMulti(List<ClassDesc> exceptionTypes, Consumer<CodeBuilder.BlockCodeBuilder> catchHandler) {
        Objects.requireNonNull(exceptionTypes);
        Objects.requireNonNull(catchHandler);

        // nullable list of CP entries - null means catching all (0)
        List<ClassEntry> entries = new ArrayList<>(Math.max(1, exceptionTypes.size()));
        if (exceptionTypes.isEmpty()) {
            entries.add(null);
        } else {
            for (var exceptionType : exceptionTypes) {
                var entry = b.constantPool().classEntry(exceptionType); // throws IAE
                entries.add(entry);
            }
        }
        // End validation

        if (catchBlock == null) {
            if (tryBlock.reachable()) {
                b.branch(Opcode.GOTO, tryCatchEnd);
            }
        }

        // Finish prior catch block
        if (catchBlock != null) {
            catchBlock.end();
            if (catchBlock.reachable()) {
                b.branch(Opcode.GOTO, tryCatchEnd);
            }
        }

        catchBlock = new BlockCodeBuilderImpl(b, tryCatchEnd);
        Label tryStart = tryBlock.startLabel();
        Label tryEnd = tryBlock.endLabel();
        for (var entry : entries) {
            // This accepts null for catching all
            catchBlock.exceptionCatch(tryStart, tryEnd, catchBlock.startLabel(), entry);
        }
        catchBlock.start();
        catchHandler.accept(catchBlock);

        return this;
    }

    @Override
    public void catchingAll(Consumer<CodeBuilder.BlockCodeBuilder> catchAllHandler) {
        catchingMulti(List.of(), catchAllHandler);
    }

    public void finish() {
        if (catchBlock != null) {
            catchBlock.end();
        }
        b.labelBinding(tryCatchEnd);
    }
}
