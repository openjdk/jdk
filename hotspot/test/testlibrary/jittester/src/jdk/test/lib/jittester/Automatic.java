/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.SymbolTable;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.factories.IRNodeBuilder;
import jdk.test.lib.jittester.TypesParser;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.visitors.JavaCodeVisitor;
import jdk.test.lib.jittester.utils.OptionResolver;
import jdk.test.lib.jittester.utils.OptionResolver.Option;
import jdk.test.lib.jittester.utils.PseudoRandom;

public class Automatic {
    public static final int minutesToWait = 3;

    private static String makeTestCase(String name) {
        SymbolTable.removeAll();
        TypeList.removeAll();
        StringBuilder resultVis = new StringBuilder();
        StringBuilder headerBuilder = new StringBuilder();
        try {
            IRNodeBuilder builder = new IRNodeBuilder()
                    .setPrefix(name)
                    .setName(name)
                    .setLevel(0);

            JavaCodeVisitor vis = new JavaCodeVisitor();
            String synopsis = "seed = '" + ProductionParams.seed.value() + "'";
            String pathPrefix = ProductionParams.testbaseDir.value()
                    .replaceAll("([^/]+)", "..");
            headerBuilder
                    .append("/*\n")
                    .append(" * @test\n")
                    .append(" * @summary ")
                        .append(synopsis)
                        .append("\n")
                    .append(" * @compile ")
                        .append(name)
                        .append(".java\n")
                    .append(" * @run build jdk.test.lib.jittester.jtreg.JitTesterDriver\n")
                    .append(" * @run driver jdk.test.lib.jittester.jtreg.JitTesterDriver ")
                        .append(name)
                        .append("\n")
                    .append(" */\n\n");


            if (!ProductionParams.disableClasses.value()) {
                long comlexityLimit = (long) (ProductionParams.complexityLimit.value()
                        * PseudoRandom.random());
                IRNode privateClasses = builder.setComplexityLimit(comlexityLimit)
                        .getClassDefinitionBlockFactory()
                        .produce();
                if (privateClasses != null) {
                    resultVis.append(privateClasses.accept(vis));
                }
            }
            long mainComplexityLimit = (long) (ProductionParams.complexityLimit.value()
                    * PseudoRandom.random());
            IRNode mainClass = builder.setComplexityLimit(mainComplexityLimit)
                    .getMainKlassFactory()
                    .produce();
            resultVis.append(mainClass.accept(vis));

            if (ProductionParams.printHierarchy.value()) {
                headerBuilder
                        .append("/*\n")
                        .append(Automatic.printHierarchy())
                        .append("*/\n");
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
        return headerBuilder.append(resultVis).toString();
    }

    private static void initializeTestGenerator(String[] params) {
        OptionResolver parser = new OptionResolver();
        Option<String> propertyFileOpt = parser.addStringOption('p', "property-file", "",
                "File to read properties from");
        ProductionParams.register(parser);
        parser.parse(params, propertyFileOpt);
        jdk.test.lib.jittester.utils.PseudoRandom.reset(ProductionParams.seed.value());
        TypesParser.parseTypesAndMethods(ProductionParams.classesFile.value(), ProductionParams.excludeMethodsFile.value());
    }

    public static void main(String[] args) {
        initializeTestGenerator(args);
        int counter = 0;
        try {
            String testbaseDir = ProductionParams.testbaseDir.value();
            do {
                double start = System.currentTimeMillis();
                String name = "Test_" + counter;
                generateTestFile(name);
                double generationTime = System.currentTimeMillis() - start;
                String path = getJavaPath();
                ProcessBuilder pb = new ProcessBuilder(path + "javac", testbaseDir + "/" + name + ".java");
                runProcess(pb, testbaseDir + "/" + name);

                start = System.currentTimeMillis();
                pb = new ProcessBuilder(path + "java", "-Xint", "-cp", testbaseDir, name);
                name = name + ".gold";
                runProcess(pb, testbaseDir + "/" + name);
                double runningTime = System.currentTimeMillis() - start;
                System.out.printf("%4d : generation time (ms) : %8.0f running time (ms) : %8.0f\n",
                                  counter, generationTime, runningTime);
                if (runningTime < TimeUnit.MINUTES.toMillis(minutesToWait))
                ++counter;
            } while (counter < ProductionParams.numberOfTests.value());
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(Automatic.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static String getJavaPath() {
        String[] env = { "JDK_HOME", "JAVA_HOME", "BOOTDIR" };
        for (String name : env) {
            String path = System.getenv(name);
            if (path != null) {
                return path + "/bin/";
            }
        }
        return "";
    }

    private static void runProcess(ProcessBuilder pb, String name)
            throws IOException, InterruptedException {
        pb.redirectError(new File(name + ".err"));
        pb.redirectOutput(new File(name + ".out"));
        Process process = pb.start();
        if (process.waitFor(minutesToWait, TimeUnit.MINUTES)) {
            try (FileWriter file = new FileWriter(name + ".exit")) {
                file.write(Integer.toString(process.exitValue()));
            }
        } else {
            process.destroyForcibly();
        }
        TimeUnit.MILLISECONDS.sleep(300);
    }

    private static void generateTestFile(String testName) {
        String code = makeTestCase(testName);
        String testbaseDir = ProductionParams.testbaseDir.value();
        String fileName = testbaseDir + "/" + testName + ".java";
        try (FileWriter file = new FileWriter(fileName)) {
            file.write(code);
            //file.close();
        } catch (IOException ex) {
            Logger.getLogger(Automatic.class.getName())
                  .log(Level.SEVERE, " Cannot write to file " + fileName, ex);
        }
    }

    private static String printHierarchy() {
        String r = "CLASS HIERARCHY:\n";
        for (Type t : TypeList.getAll()) {
            if (t instanceof TypeKlass) {
                TypeKlass k = (TypeKlass) t;
                if (k.isAbstract()) {
                    r += "abstract ";
                }
                if (k.isFinal()) {
                    r += "final ";
                }
                if (k.isInterface()) {
                    r += "interface ";
                } else {
                    r += "class ";
                }
                r += k.getName() + ": ";
                HashSet<String> parents = k.getParentsNames();
                if (parents != null) {
                    Iterator<String> n = parents.iterator();
                    int size = parents.size();
                    for (int i = 0; n.hasNext() && i < size - 1; i++) {
                        r += n.next() + ", ";
                    }
                    if (n.hasNext()) {
                        r += n.next();
                    }
                }
                r += "\n";
            }
        }
        return r;
    }
}
