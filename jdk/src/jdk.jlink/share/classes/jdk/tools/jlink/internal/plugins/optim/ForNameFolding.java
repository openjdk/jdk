/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal.plugins.optim;

import java.io.IOException;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.tools.jlink.internal.plugins.asm.AsmPools;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.tools.jlink.internal.plugins.OptimizationPlugin.MethodOptimizer;
import jdk.tools.jlink.internal.plugins.asm.AsmModulePool;
import jdk.tools.jlink.internal.plugins.optim.ControlFlow.Block;
import jdk.tools.jlink.internal.plugins.optim.ReflectionOptimizer.Data;
import jdk.tools.jlink.internal.plugins.optim.ReflectionOptimizer.TypeResolver;


/**
 * MethodOptimizer that removes Class.forName when possible.
 * WARNING: This code is experimental.
 * TODO: Need to check that the type is accessible prior to replace with a constant.
 */
public class ForNameFolding implements MethodOptimizer {

    private int numNotReplaced;
    private int numReplacement;
    private int numRemovedHandlers;
    private int instructionsRemoved;

    private Consumer<String> logger;

    @Override
    public boolean optimize(Consumer<String> logger, AsmPools pools,
            AsmModulePool modulePool,
            ClassNode cn, MethodNode m, TypeResolver resolver) throws Exception {
        this.logger = logger;
        Data data = ReflectionOptimizer.replaceWithClassConstant(cn, m, createResolver(resolver));
        instructionsRemoved += data.removedInstructions();
        numRemovedHandlers += data.removedHandlers().size();
        for (Entry<String, Set<Block>> entry : data.removedHandlers().entrySet()) {
            logRemoval(cn.name + "." + m.name + "removed block for " + entry.getKey()
                    + " : " + entry.getValue());
        }
        return data.removedInstructions() > 0;
    }

    public TypeResolver createResolver(TypeResolver resolver) {
        return (ClassNode cn, MethodNode mn, String type) -> {
            ClassReader reader = resolver.resolve(cn, mn, type);
            if (reader == null) {
                logNotReplaced(type);
            } else {
                logReplaced(type);
            }
            return reader;
        };
    }

    private void logReplaced(String type) {
        numReplacement += 1;
    }

    private void logNotReplaced(String type) {
        numNotReplaced += 1;
        if (logger != null) {
            logger.accept(type + " not resolved");
        }
    }

    private void logRemoval(String content) {
        numRemovedHandlers += 1;
        if (logger != null) {
            logger.accept(content);
        }
    }

    @Override
    public void close() throws IOException {
        if (logger != null) {
            logger.accept("Class.forName Folding results:\n " + numReplacement
                    + " removed reflection. " + numRemovedHandlers
                    + " removed exception handlers."
                    + numNotReplaced + " types unknown. "
                    + instructionsRemoved + " instructions removed\n");
        }
    }
}
