/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester;

import jdk.test.lib.jittester.factories.IRNodeBuilder;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.FixedTrees;
import jdk.test.lib.jittester.utils.PseudoRandom;

/**
 * Generates IR trees for fuzzy test classes.
 */
public class IRTreeGenerator {

    /**
     * Generated Test - main and private classes trees along with random seed used for generation.
     */
    public record Test (long seed, IRNode mainClass, IRNode privateClasses) {};

    /**
     * Generates IR trees for main and private classes.
     *
     * @param name main class name
     * @return a pair (main class; private classes)
     */
    public static Test generateIRTree(String name) {
        long seed = PseudoRandom.getCurrentSeed();
        ProductionLimiter.resetTimer();
        //NB: SymbolTable is a widely-used singleton, hence all the locking.
        SymbolTable.removeAll();
        TypeList.removeAll();

        IRNodeBuilder builder = new IRNodeBuilder()
                .setPrefix(name)
                .setName(name)
                .setLevel(0);

        Long complexityLimit = ProductionParams.complexityLimit.value();
        IRNode privateClasses = null;
        if (!ProductionParams.disableClasses.value()) {
            long privateClassComlexity = (long) (complexityLimit * PseudoRandom.random());
            try {
                privateClasses = builder.setComplexityLimit(privateClassComlexity)
                        .getClassDefinitionBlockFactory()
                        .produce();
            } catch (ProductionFailedException ex) {
                ex.printStackTrace(System.out);
            }
        }
        long mainClassComplexity = (long) (complexityLimit * PseudoRandom.random());
        IRNode mainClass = null;
        try {
            mainClass = builder.setComplexityLimit(mainClassComplexity)
                    .getMainKlassFactory()
                    .produce();
            TypeKlass aClass = new TypeKlass(name);
            mainClass.getChild(1).addChild(FixedTrees.generateMainOrExecuteMethod(aClass, true));
            mainClass.getChild(1).addChild(FixedTrees.generateMainOrExecuteMethod(aClass, false));
        } catch (ProductionFailedException ex) {
            ex.printStackTrace(System.out);
        }
        return new Test(seed, mainClass, privateClasses);
    }

    /**
     * Initializes the generator from ProductionParams static class.
     */
    public static void initializeWithProductionParams() {
        TypesParser.parseTypesAndMethods(ProductionParams.classesFile.value(),
                ProductionParams.excludeMethodsFile.value());
        if (ProductionParams.specificSeed.isSet()) {
            PseudoRandom.setCurrentSeed(ProductionParams.specificSeed.value());
        }
    }

}
