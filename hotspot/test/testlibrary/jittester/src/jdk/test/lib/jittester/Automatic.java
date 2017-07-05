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

import jdk.test.lib.Pair;
import jdk.test.lib.jittester.factories.IRNodeBuilder;
import jdk.test.lib.jittester.jtreg.Printer;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.utils.FixedTrees;
import jdk.test.lib.jittester.utils.OptionResolver;
import jdk.test.lib.jittester.utils.OptionResolver.Option;
import jdk.test.lib.jittester.utils.PseudoRandom;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Automatic {
    private static final int MINUTES_TO_WAIT = Integer.getInteger("jdk.test.lib.jittester", 3);

    static String getJtregHeader(String mainClass, boolean addCompile) {
        String synopsis = "seed = '" + ProductionParams.seed.value() + "'"
                + ", specificSeed = '" + PseudoRandom.getCurrentSeed() + "'";
        StringBuilder header = new StringBuilder();
        header.append("/*\n * @test\n * @summary ")
                .append(synopsis)
                .append(" \n* @library / ../\n");
        if (addCompile) {
            header.append("\n * @compile ")
                    .append(mainClass)
                    .append(".java\n");
        }
        header.append(" * @run build jdk.test.lib.jittester.jtreg.JitTesterDriver "
                        + "jdk.test.lib.jittester.jtreg.Printer\n")
                .append(" * @run driver jdk.test.lib.jittester.jtreg.JitTesterDriver ")
                .append(mainClass)
                .append("\n */\n\n");
        if (ProductionParams.printHierarchy.value()) {
            header.append("/*\n")
                .append(Automatic.printHierarchy())
                .append("*/\n");
        }
        return header.toString();
    }

    private static Pair<IRNode, IRNode> generateIRTree(String name) {
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
        return new Pair<>(mainClass, privateClasses);
    }

    private static void initializeTestGenerator(String[] params) {
        OptionResolver parser = new OptionResolver();
        Option<String> propertyFileOpt = parser.addStringOption('p', "property-file",
                "conf/default.properties", "File to read properties from");
        ProductionParams.register(parser);
        parser.parse(params, propertyFileOpt);
        PseudoRandom.reset(ProductionParams.seed.value());
        TypesParser.parseTypesAndMethods(ProductionParams.classesFile.value(),
                ProductionParams.excludeMethodsFile.value());
        if (ProductionParams.specificSeed.isSet()) {
            PseudoRandom.setCurrentSeed(ProductionParams.specificSeed.value());
        }
    }

    public static void main(String[] args) {
        initializeTestGenerator(args);
        int counter = 0;
        try {
            Path testbaseDir = Paths.get(ProductionParams.testbaseDir.value());
            System.out.printf(" %13s | %8s | %8s | %8s |%n", "start time", "count", "generat",
                              "running");
            System.out.printf(" %13s | %8s | %8s | %8s |%n", "---", "---", "---","---");
            String path = getJavaPath();
            String javacPath = Paths.get(path, "javac").toString();
            String javaPath = Paths.get(path, "java").toString();

            // compile Printer class first. A common one for all tests
            ensureExisting(testbaseDir);
            ProcessBuilder pbPrinter = new ProcessBuilder(javacPath,
                    Paths.get(testbaseDir.toString(), "jdk", "test", "lib", "jittester",
                            "jtreg", "Printer.java").toString());
            runProcess(pbPrinter, testbaseDir.resolve("Printer").toString());
            do {
                double start = System.currentTimeMillis();
                System.out.print("[" + LocalTime.now() + "] |");
                String name = "Test_" + counter;
                Pair<IRNode, IRNode> irTree = generateIRTree(name);
                System.out.printf(" %8d |", counter);
                double generationTime = System.currentTimeMillis() - start;
                System.out.printf(" %8.0f |", generationTime);
                if (!ProductionParams.disableJavacodeGeneration.value()) {
                    JavaCodeGenerator generator = new JavaCodeGenerator();
                    String javaFile = generator.apply(irTree.first, irTree.second);
                    ProcessBuilder pb = new ProcessBuilder(javacPath, "-cp", testbaseDir.toString()
                            + ":" + generator.getTestbase().toString(), javaFile);
                    runProcess(pb, generator.getTestbase().resolve(name).toString());
                    start = System.currentTimeMillis();

                    // Run compiled class files
                    pb = new ProcessBuilder(javaPath, "-Xint", "-cp", testbaseDir.toString()
                            + ":" + generator.getTestbase().toString(), name);
                    String goldFile = name + ".gold";
                    runProcess(pb, generator.getTestbase().resolve(goldFile).toString());
                }

                if (!ProductionParams.disableBytecodeGeneration.value()) {
                    ByteCodeGenerator generator = new ByteCodeGenerator();
                    generator.apply(irTree.first, irTree.second);
                    generator.writeJtregBytecodeRunner(name);
                    // Run generated bytecode
                    ProcessBuilder pb = new ProcessBuilder(javaPath, "-Xint", "-Xverify", "-cp",
                            testbaseDir.toString() + ":" + generator.getTestbase().toString(),
                            name);
                    String goldFile = name + ".gold";
                    start = System.currentTimeMillis();
                    runProcess(pb, generator.getTestbase().resolve(goldFile).toString());
                }

                double runningTime = System.currentTimeMillis() - start;
                System.out.printf(" %8.0f |%n", runningTime);
                if (runningTime < TimeUnit.MINUTES.toMillis(MINUTES_TO_WAIT)) {
                    ++counter;
                }
            } while (counter < ProductionParams.numberOfTests.value());
        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
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

    private static int runProcess(ProcessBuilder pb, String name)
            throws IOException, InterruptedException {
        pb.redirectError(new File(name + ".err"));
        pb.redirectOutput(new File(name + ".out"));
        Process process = pb.start();
        if (process.waitFor(MINUTES_TO_WAIT, TimeUnit.MINUTES)) {
            try (FileWriter file = new FileWriter(name + ".exit")) {
                file.write(Integer.toString(process.exitValue()));
            }
            return process.exitValue();
        } else {
            process.destroyForcibly();
            return -1;
        }
    }

    private static String printHierarchy() {
        return TypeList.getAll().stream()
                .filter(t -> t instanceof TypeKlass)
                .map(t -> typeDescription((TypeKlass) t))
                .collect(Collectors.joining("\n","CLASS HIERARCHY:\n", "\n"));
    }

    private static String typeDescription(TypeKlass type) {
        StringBuilder result = new StringBuilder();
        String parents = type.getParentsNames().stream().collect(Collectors.joining(","));
        result.append(type.isAbstract() ? "abstract " : "")
              .append(type.isFinal() ? "final " : "")
              .append(type.isInterface() ? "interface " : "class ")
              .append(type.getName())
              .append(parents.isEmpty() ? "" : ": " + parents);
        return result.toString();
    }

    static void ensureExisting(Path path) {
        if (Files.notExists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
