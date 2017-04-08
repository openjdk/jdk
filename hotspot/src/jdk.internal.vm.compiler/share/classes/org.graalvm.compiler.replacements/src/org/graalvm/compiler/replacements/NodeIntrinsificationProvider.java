/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.graphbuilderconf.NodeIntrinsicPluginFactory.InjectionProvider;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public class NodeIntrinsificationProvider implements InjectionProvider {

    private final MetaAccessProvider metaAccess;
    private final SnippetReflectionProvider snippetReflection;
    private final ForeignCallsProvider foreignCalls;
    private final WordTypes wordTypes;

    public NodeIntrinsificationProvider(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection, ForeignCallsProvider foreignCalls, WordTypes wordTypes) {
        this.metaAccess = metaAccess;
        this.snippetReflection = snippetReflection;
        this.foreignCalls = foreignCalls;
        this.wordTypes = wordTypes;
    }

    @Override
    public Stamp getReturnStamp(Class<?> type, boolean nonNull) {
        JavaKind kind = JavaKind.fromJavaClass(type);
        if (kind == JavaKind.Object) {
            ResolvedJavaType returnType = metaAccess.lookupJavaType(type);
            if (wordTypes.isWord(returnType)) {
                return wordTypes.getWordStamp(returnType);
            } else {
                return StampFactory.object(TypeReference.createWithoutAssumptions(returnType), nonNull);
            }
        } else {
            return StampFactory.forKind(kind);
        }
    }

    @Override
    public <T> T getInjectedArgument(Class<T> type) {
        T injected = snippetReflection.getInjectedNodeIntrinsicParameter(type);
        if (injected != null) {
            return injected;
        } else if (type.equals(ForeignCallsProvider.class)) {
            return type.cast(foreignCalls);
        } else if (type.equals(SnippetReflectionProvider.class)) {
            return type.cast(snippetReflection);
        } else {
            throw new GraalError("Cannot handle injected argument of type %s.", type.getName());
        }
    }
}
