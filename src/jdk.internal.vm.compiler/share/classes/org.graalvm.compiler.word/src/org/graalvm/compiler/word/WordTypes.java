/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.word;

import static jdk.vm.ci.services.Services.IS_BUILDING_NATIVE_IMAGE;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.word.Word.Operation;
import jdk.internal.vm.compiler.word.WordBase;
import jdk.internal.vm.compiler.word.WordFactory;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Encapsulates information for Java types representing raw words (as opposed to Objects).
 */
public class WordTypes {

    /**
     * Resolved type for {@link WordBase}.
     */
    private final ResolvedJavaType wordBaseType;

    /**
     * Resolved type for {@link Word}.
     */
    private final ResolvedJavaType wordImplType;

    /**
     * Resolved type for {@link WordFactory}.
     */
    private final ResolvedJavaType wordFactoryType;

    /**
     * Resolved type for {@link ObjectAccess}.
     */
    private final ResolvedJavaType objectAccessType;

    /**
     * Resolved type for {@link BarrieredAccess}.
     */
    private final ResolvedJavaType barrieredAccessType;

    private final JavaKind wordKind;

    public WordTypes(MetaAccessProvider metaAccess, JavaKind wordKind) {
        this.wordKind = wordKind;
        this.wordBaseType = metaAccess.lookupJavaType(WordBase.class);
        this.wordImplType = metaAccess.lookupJavaType(Word.class);
        this.wordFactoryType = metaAccess.lookupJavaType(WordFactory.class);
        this.objectAccessType = metaAccess.lookupJavaType(ObjectAccess.class);
        this.barrieredAccessType = metaAccess.lookupJavaType(BarrieredAccess.class);

        if (!IS_BUILDING_NATIVE_IMAGE) {
            Word.ensureInitialized();
        }
        this.wordImplType.initialize();
    }

    /**
     * Determines if a given method denotes a word operation.
     */
    public boolean isWordOperation(ResolvedJavaMethod targetMethod) {
        final boolean isWordFactory = wordFactoryType.equals(targetMethod.getDeclaringClass());
        if (isWordFactory) {
            return !targetMethod.isConstructor();
        }
        final boolean isObjectAccess = objectAccessType.equals(targetMethod.getDeclaringClass());
        final boolean isBarrieredAccess = barrieredAccessType.equals(targetMethod.getDeclaringClass());
        if (isObjectAccess || isBarrieredAccess) {
            assert targetMethod.getAnnotation(Operation.class) != null : targetMethod + " should be annotated with @" + Operation.class.getSimpleName();
            return true;
        }
        return isWord(targetMethod.getDeclaringClass());
    }

    /**
     * Gets the method annotated with {@link Operation} based on a given method that represents a
     * word operation (but may not necessarily have the annotation).
     *
     * @param callingContextType the {@linkplain ResolvedJavaType type} from which
     *            {@code targetMethod} is invoked
     * @return the {@link Operation} method resolved for {@code targetMethod} if any
     */
    public ResolvedJavaMethod getWordOperation(ResolvedJavaMethod targetMethod, ResolvedJavaType callingContextType) {
        final boolean isWordBase = wordBaseType.isAssignableFrom(targetMethod.getDeclaringClass());
        ResolvedJavaMethod wordMethod = targetMethod;
        if (isWordBase && !targetMethod.isStatic()) {
            assert wordImplType.isLinked();
            wordMethod = wordImplType.resolveConcreteMethod(targetMethod, callingContextType);
        }
        assert wordMethod != null : targetMethod;
        return wordMethod;
    }

    /**
     * Determines if a given node has a word type.
     */
    public boolean isWord(ValueNode node) {
        return isWord(StampTool.typeOrNull(node));
    }

    /**
     * Determines if a given type is a word type.
     */
    public boolean isWord(JavaType type) {
        return type instanceof ResolvedJavaType && wordBaseType.isAssignableFrom((ResolvedJavaType) type);
    }

    /**
     * Gets the kind for a given type, returning the {@linkplain #getWordKind() word kind} if
     * {@code type} is a {@linkplain #isWord(JavaType) word type}.
     */
    public JavaKind asKind(JavaType type) {
        if (isWord(type)) {
            return wordKind;
        } else {
            return type.getJavaKind();
        }
    }

    public JavaKind getWordKind() {
        return wordKind;
    }

    /**
     * Gets the stamp for a given {@linkplain #isWord(JavaType) word type}.
     */
    public Stamp getWordStamp(ResolvedJavaType type) {
        assert isWord(type);
        return StampFactory.forKind(wordKind);
    }

    public ResolvedJavaType getWordImplType() {
        return wordImplType;
    }
}
