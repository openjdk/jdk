/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.constant.ClassDesc;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.test.lib.helpers.ClassFileInstaller;

/*
 * Helper class to write tests that redefine classes.
 * When main method is run, it will create a redefineagent.jar that can be used
 * with the -javaagent option to support redefining classes in jtreg tests.
 *
 * See sample test in test/testlibrary_tests/RedefineClassTest.java
 */

public class RedefineClassHelper {

    public static Instrumentation instrumentation;
    public static void premain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;
    }

    /**
     * Redefine a class
     *
     * @param clazz Class to redefine
     * @param javacode String with the new java code for the class to be redefined
     */
    public static void redefineClass(Class<?> clazz, String javacode) throws Exception {
        byte[] bytecode = InMemoryJavaCompiler.compile(clazz.getName(), javacode);
        redefineClass(clazz, bytecode);
    }

    /**
     * Redefine a class
     *
     * @param clazz Class to redefine
     * @param bytecode byte[] with the new class
     */
    public static void redefineClass(Class<?> clazz, byte[] bytecode) throws Exception {
        instrumentation.redefineClasses(new ClassDefinition(clazz, bytecode));
    }

    private static byte[] getBytecodes(ClassLoader loader, String name) throws Exception {
        try (InputStream is = loader.getResourceAsStream(name + ".class")) {
            byte[] buf = is.readAllBytes();
            System.out.println("sizeof(" + name + ".class) == " + buf.length);
            return buf;
        }
    }

    /*
     * Copy the class defined by `bytes`, replacing the name of the class with `newClassName`,
     * so that both old and new classes can be compiled by jtreg for the test.
     *
     * @param bytes read from the original class file.
     * @param newClassName new class name for the returned class representation
     * @return a copy of the class represented by `bytes` but with the name `newClassName`
     */
    public static byte[] replaceClassName(byte[] bytes, String newClassName) throws Exception {
        ClassModel classModel = ClassFile.of().parse(bytes);
        return ClassFile.of().build(ClassDesc.of(newClassName), classModel::forEach);
    }

    /*
     * Replace class name in bytecodes to the class we're trying to redefine, so that both
     * old and new classes can be compiled with jtreg for the test.
     *
     * @param loader ClassLoader to find the bytes for the old class.
     * @param oldClassName old class name.
     * @param newClassName new class name to replace with old class name.
     * @return a copy of the class represented by `bytes` but with the name `newClassName`
     */
    public static byte[] replaceClassName(ClassLoader loader, String oldClassName, String newClassName) throws Exception {
        byte[] buf = getBytecodes(loader, oldClassName);
        return replaceClassName(buf, newClassName);
    }

    /**
     * Main method to be invoked before test to create the redefineagent.jar
     */
    public static void main(String[] args) throws Exception {
        String manifest = "Premain-Class: RedefineClassHelper\nCan-Redefine-Classes: true\n";
        ClassFileInstaller.writeJar("redefineagent.jar", ClassFileInstaller.Manifest.fromString(manifest), "RedefineClassHelper");
    }
}
