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

import java.util.Collection;
import java.util.Optional;
import jdk.internal.classfile.CodeTransform;
import jdk.internal.classfile.Label;
import jdk.internal.classfile.TypeKind;
import jdk.internal.classfile.impl.CodeStackTrackerImpl;

/**
 * {@link CodeStackTracker} is a {@link CodeTransform} tracking stack content
 * and calculating max stack size.
 * <p>
 * Sample use:
 * <p>
 * {@snippet lang=java :
 *     var stackTracker = CodeStackTracker.of();
 *     codeBuilder.transforming(stackTracker, trackedBuilder -> {
 *         trackedBuilder.aload(0);
 *         trackedBuilder.lconst_0();
 *         trackedBuilder.ifThen(...);
 *         ...
 *         var stack = stackTracker.stack().get();
 *         int maxStack = stackTracker.maxStackSize().get();
 *     });
 * }
 */
public sealed interface CodeStackTracker extends CodeTransform permits CodeStackTrackerImpl {

    /**
     * Creates new instance of {@link CodeStackTracker} initialized with provided stack items.
     * @param initialStack initial stack content
     * @return new instance of {@link CodeStackTracker}
     */
    static CodeStackTracker of(TypeKind... initialStack) {
        return new CodeStackTrackerImpl(initialStack);
    }

    /**
      * Returns {@linkplain Collection} of {@linkplain TypeKind} representing current stack.
      * Returns an empty {@linkplain Optional} when the Stack content is unknown
      * (right after {@code xRETURN, ATHROW, GOTO, GOTO_W, LOOKUPSWITCH, TABLESWITCH} instructions).
      * <p>
      * Temporary unknown stack content can be recovered by binding of a {@linkplain Label} used as
      * target of a branch instruction from existing code with known stack (forward branch target),
      * or by binding of a {@linkplain Label} defining an exception handler (exception handler code start).
      *
      * @return actual stack content, or an empty {@linkplain Optional} if unknown
      */
    Optional<Collection<TypeKind>> stack();

    /**
      * Returns tracked max stack size.
      * Returns an empty {@linkplain Optional} when max stack size tracking has been lost.
      * <p>
      * Max stack size tracking is permanently lost when a stack instruction appears
      * and the actual stack content is unknown.
      *
      * @return tracked max stack size, or an empty {@linkplain Optional} if tracking has been lost
      */
    Optional<Integer> maxStackSize();
}
