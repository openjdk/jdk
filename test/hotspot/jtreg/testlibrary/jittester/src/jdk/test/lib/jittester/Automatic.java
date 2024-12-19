/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class Automatic {
    public static final int MINUTES_TO_WAIT = Integer.getInteger("jdk.test.lib.jittester", 3);

    private static List<TestsGenerator> getTestGenerators() {
        List<TestsGenerator> result = new ArrayList<>();
        Class<?> factoryClass;
        Function<String[], List<TestsGenerator>> factory;
        String[] factoryClassNames = ProductionParams.generatorsFactories.value().split(",");
        String[] generatorNames = ProductionParams.generators.value().split(",");
        for (String factoryClassName : factoryClassNames) {
            try {
                factoryClass = Class.forName(factoryClassName);
                factory = (Function<String[], List<TestsGenerator>>) factoryClass.newInstance();
            } catch (ReflectiveOperationException roe) {
                throw new Error("Can't instantiate generators factory", roe);
            }
            result.addAll(factory.apply(generatorNames));
        }
        return result;
    }

    public static void main(String[] args) {
        ProductionParams.initializeFromCmdline(args);
        IRTreeGenerator.initializeWithProductionParams();

        int counter = 0;
        System.out.printf("Generating %d tests...%n",  ProductionParams.numberOfTests.value());
        System.out.printf(" %13s | %8s | %8s | %8s |%n", "start time", "count", "generat",
                "running");
        System.out.printf(" %13s | %8s | %8s | %8s |%n", "---", "---", "---", "---");
        List<TestsGenerator> generators = getTestGenerators();
        do {
            double start = System.currentTimeMillis();
            try {
                System.out.print("[" + LocalTime.now() + "] |");
                String name = "Test_" + counter;
                var test = IRTreeGenerator.generateIRTree(name);
                System.out.printf(" %8d |", counter);
                long maxWaitTime = TimeUnit.MINUTES.toMillis(MINUTES_TO_WAIT);
                double generationTime = System.currentTimeMillis() - start;
                System.out.printf(" %8.0f |", generationTime);
                start = System.currentTimeMillis();
                Thread generatorThread = new Thread(() -> {
                    for (TestsGenerator generator : generators) {
                        generator.accept(test);
                    }
                });
                generatorThread.start();
                try {
                    generatorThread.join(maxWaitTime);
                } catch (InterruptedException ie) {
                    throw new Error("Test generation interrupted: " + ie, ie);
                }
                if (generatorThread.isAlive()) {
                    // maxTime reached, so, proceed to next test generation
                    generatorThread.interrupt();
                } else {
                    double runningTime = System.currentTimeMillis() - start;
                    System.out.printf(" %8.0f |%n", runningTime);
                    if (runningTime < maxWaitTime) {
                        ++counter;
                    }
                }
            } catch (RuntimeException ignored) {
                // Generation failures happen due to nature of fuzzing test generators,
                // such errors are ignored.
                System.out.println("Test_" + counter + " ignored, generation failed due to " +
                        ignored.getMessage());
            }
        } while (counter < ProductionParams.numberOfTests.value());
    }
}
