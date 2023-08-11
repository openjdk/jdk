/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.components;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.CodeTransform;
import jdk.internal.classfile.Label;
import jdk.internal.classfile.impl.CodeRelabelerImpl;

/**
 * A code relabeler is a {@link CodeTransform} replacing all occurrences
 * of {@link jdk.internal.classfile.Label} in the transformed code with new instances.
 * All {@link jdk.internal.classfile.instruction.LabelTarget} instructions are adjusted accordingly.
 * Relabeled code graph is identical to the original.
 * <p>
 * Primary purpose of CodeRelabeler is for repeated injections of the same code blocks.
 * Repeated injection of the same code block must be relabeled, so each instance of
 * {@link jdk.internal.classfile.Label} is bound in the target bytecode exactly once.
 */
public sealed interface CodeRelabeler extends CodeTransform permits CodeRelabelerImpl {

    /**
     * Creates a new instance of CodeRelabeler.
     * @return a new instance of CodeRelabeler
     */
    static CodeRelabeler of() {
        return of(new IdentityHashMap<>());
    }

    /**
     * Creates a new instance of CodeRelabeler storing the label mapping into the provided map.
     * @param map label map actively used for relabeling
     * @return a new instance of CodeRelabeler
     */
    static CodeRelabeler of(Map<Label, Label> map) {
        return of((l, cob) -> map.computeIfAbsent(l, ll -> cob.newLabel()));
    }

    /**
     * Creates a new instance of CodeRelabeler using provided {@link java.util.function.BiFunction}
     * to re-label the code.
     * @param mapFunction
     * @return a new instance of CodeRelabeler
     */
    static CodeRelabeler of(BiFunction<Label, CodeBuilder, Label> mapFunction) {
        return new CodeRelabelerImpl(mapFunction);
    }

    /**
     * Access method to internal re-labeling function.
     * @param label source label
     * @param codeBuilder builder to create new labels
     * @return target label
     */
    Label relabel(Label label, CodeBuilder codeBuilder);
}
