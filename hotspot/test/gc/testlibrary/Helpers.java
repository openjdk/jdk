/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package gc.testlibrary;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.OutputAnalyzer;
import sun.hotspot.WhiteBox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Helpers {

    /**
     * Size of a long field in bytes
     */
    public static final int SIZE_OF_LONG = 8;

    // In case of 128 byte padding
    private static final int MAX_PADDING_SIZE = 128;

    /**
     * According class file format theoretical amount of fields in class is u2 which is (256 * 256 - 1).
     * Some service info takes place in constant pool and we really could make a class with only (256 * 256 - 29)
     * fields.
     * Since the exact value is not so important and I would like to avoid issues that may be caused by future changes/
     * different archs etc I selected (256 * 256 - 32) for this constant.
     * The test works with other values too but the smaller the number the more classes we need to generate and it takes
     * more time
     */
    private static final int MAXIMUM_AMOUNT_OF_FIELDS_IN_CLASS = 256 * 256 - 32;

    /**
     * Detects amount of extra bytes required to allocate a byte array.
     * Allocating a byte[n] array takes more then just n bytes in the heap.
     * Extra bytes are required to store object reference and the length.
     * This amount depends on bitness and other factors.
     *
     * @return byte[] memory overhead
     */
    public static int detectByteArrayAllocationOverhead() {

        WhiteBox whiteBox = WhiteBox.getWhiteBox();

        int zeroLengthByteArraySize = (int) whiteBox.getObjectSize(new byte[0]);

        // Since we do not know is there any padding in zeroLengthByteArraySize we cannot just take byte[0] size as overhead
        for (int i = 1; i < MAX_PADDING_SIZE + 1; ++i) {
            int realAllocationSize = (int) whiteBox.getObjectSize(new byte[i]);
            if (realAllocationSize != zeroLengthByteArraySize) {
                // It means we did not have any padding on previous step
                return zeroLengthByteArraySize - (i - 1);
            }
        }
        throw new Error("We cannot find byte[] memory overhead - should not reach here");
    }

    /**
     * Compiles a java class
     *
     * @param className class name
     * @param root      root directory - where .java and .class files will be put
     * @param source    class source
     * @throws IOException if cannot write file to specified directory
     */
    public static void compileClass(String className, Path root, String source) throws IOException {
        Path sourceFile = root.resolve(className + ".java");
        Files.write(sourceFile, source.getBytes());

        JDKToolLauncher jar = JDKToolLauncher.create("javac")
                .addToolArg("-d")
                .addToolArg(root.toAbsolutePath().toString())
                .addToolArg("-cp")
                .addToolArg(System.getProperty("java.class.path") + File.pathSeparator + root.toAbsolutePath())
                .addToolArg(sourceFile.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(jar.getCommand());
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
    }

    /**
     * Generates class with specified name, which extends specified class, with specified constructor and specified
     * count of long fields
     * Generated class will looks like this:
     * public class ClassName extends SuperClass {
     * ClassName() {super();}
     * long f0;
     * ...
     * long fNNN;
     * <p>
     * }
     *
     * @param className   class name
     * @param superClass  super class. if null - no extends clause
     * @param constructor constructor. if null - no constructor
     * @param fieldCount  count of long fields
     * @return class text
     */
    public static String generate(String className, String superClass, String constructor, long fieldCount) {

        StringBuilder builder = new StringBuilder();
        builder.append(String.format("public class %s%s {\n", className, superClass == null ? ""
                : " extends " + superClass));

        if (constructor != null) {
            builder.append(constructor);
        }

        for (int i = 0; i < fieldCount; ++i) {
            builder.append(String.format("long f%d;\n", i));
        }

        builder.append("}\n");
        return builder.toString();
    }

    /**
     * Changes string from enum notation to class notation - i.e. "VERY_SMALL_CAT" to "VerySmallCat"
     *
     * @param enumName string in enum notation
     * @return string in class notation
     */
    public static String enumNameToClassName(String enumName) {
        if (enumName == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        boolean toLowerCase = false;
        for (int i = 0; i < enumName.length(); ++i) {
            if (enumName.charAt(i) == '_') {
                toLowerCase = false;
            } else {
                builder.append(toLowerCase ? String.valueOf(enumName.charAt(i)).toLowerCase() :
                        String.valueOf(enumName.charAt(i)));
                toLowerCase = true;
            }

        }
        return builder.toString();
    }

    /**
     * Generates and compiles class with instance of specified size and load it in specified class loader
     * Generated class will looks like this:
     * public class ClassName extends SuperClass {
     * long f0;
     * ...
     * long fNNN;
     * <p>
     * }
     *
     * @param classLoader  class loader
     * @param className    generated class name
     * @param instanceSize size of generated class' instance. Size should be aligned by 8 bytes
     * @param workDir      working dir where generated classes are put and compiled
     * @param prefix       prefix for service classes (ones we use to create chain of inheritance).
     *                     The names will be prefix_1, prefix_2,.., prefix_n
     * @return Class object of generated and compiled class loaded in specified class loader
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static Class<?> generateCompileAndLoad(ClassLoader classLoader, String className, long instanceSize,
                                                  Path workDir, String prefix)
            throws IOException, ClassNotFoundException {

        if (instanceSize % SIZE_OF_LONG != 0L) {
            throw new Error(String.format("Test bug: only sizes aligned by 8 bytes are supported and %d was specified",
                    instanceSize));
        }

        long instanceSizeWithoutObjectHeader = instanceSize - WhiteBox.getWhiteBox().getObjectSize(new Object());

        int generatedClassesCount;
        int fieldsInLastClassCount;

        int sizeOfLastFile = (int) (instanceSizeWithoutObjectHeader
                % (MAXIMUM_AMOUNT_OF_FIELDS_IN_CLASS * SIZE_OF_LONG));

        if (sizeOfLastFile != 0) {
            generatedClassesCount = (int) instanceSizeWithoutObjectHeader
                    / (MAXIMUM_AMOUNT_OF_FIELDS_IN_CLASS * SIZE_OF_LONG) + 1;
            fieldsInLastClassCount = sizeOfLastFile / SIZE_OF_LONG;
        } else {
            generatedClassesCount = (int) instanceSizeWithoutObjectHeader
                    / (MAXIMUM_AMOUNT_OF_FIELDS_IN_CLASS * SIZE_OF_LONG);
            fieldsInLastClassCount = MAXIMUM_AMOUNT_OF_FIELDS_IN_CLASS;
        }

        for (int i = 0; i < generatedClassesCount; i++) {
            // for the last generated class we use specified class name
            String clsName = (i == generatedClassesCount - 1) ? className : prefix + i;

            Helpers.compileClass(clsName, workDir,
                    Helpers.generate(
                            clsName,
                            // for first generated class we don't have 'extends'
                            (i == 0 ? null : prefix + (i - 1)),
                            null,
                            // for the last generated class we use different field count
                            (i == generatedClassesCount - 1) ? fieldsInLastClassCount
                                    : MAXIMUM_AMOUNT_OF_FIELDS_IN_CLASS));
        }
        return classLoader.loadClass(className);
    }

    /**
     * Waits until Concurent Mark Cycle finishes
     * @param wb  Whitebox instance
     * @param sleepTime sleep time
     */
    public static void waitTillCMCFinished(WhiteBox wb, int sleepTime) {
        while (wb.g1InConcurrentMark()) {
            if (sleepTime > -1) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    System.out.println("Got InterruptedException while waiting for ConcMarkCycle to finish");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

}
