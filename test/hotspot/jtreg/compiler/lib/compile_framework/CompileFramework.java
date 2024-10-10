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

package compiler.lib.compile_framework;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * This is the entry-point for the Compile Framework. Its purpose it to allow
 * compilation and execution of Java and Jasm sources generated at runtime.
 *
 * <p> Please reference the README.md for more details and examples.
 */
public class CompileFramework {
    private final List<SourceCode> javaSources = new ArrayList<>();
    private final List<SourceCode> jasmSources = new ArrayList<>();
    private final Path sourceDir = Utils.makeUniqueDir("compile-framework-sources-");
    private final Path classesDir = Utils.makeUniqueDir("compile-framework-classes-");
    private ClassLoader classLoader;

    /**
     * Set up a new Compile Framework instance, for a new compilation unit.
     */
    public CompileFramework() {}

    /**
     * Add a Java source to the compilation.
     *
     * @param className Class name of the class (e.g. "{@code p.xyz.YXZ}").
     * @param code Java code for the class, in the form of a {@link String}.
     */
    public void addJavaSourceCode(String className, String code) {
        javaSources.add(new SourceCode(className, "java", code));
    }

    /**
     * Add a Jasm source to the compilation.
     *
     * @param className Class name of the class (e.g. "{@code p.xyz.YXZ}").
     * @param code Jasm code for the class, in the form of a {@link String}.
     */
    public void addJasmSourceCode(String className, String code) {
        jasmSources.add(new SourceCode(className, "jasm", code));
    }

    /**
     * Compile all sources: store the sources to the {@link sourceDir} directory, compile
     * Java and Jasm sources and store the generated class-files in the {@link classesDir}
     * directory.
     */
    public void compile() {
        if (classLoader != null) {
            throw new CompileFrameworkException("Cannot compile twice!");
        }

        Utils.printlnVerbose("------------------ CompileFramework: SourceCode -------------------");
        Utils.printlnVerbose(sourceCodesAsString(jasmSources));
        Utils.printlnVerbose(sourceCodesAsString(javaSources));

        System.out.println("------------------ CompileFramework: Compilation ------------------");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Classes directory: " + classesDir);

        Compile.compileJasmSources(jasmSources, sourceDir, classesDir);
        Compile.compileJavaSources(javaSources, sourceDir, classesDir);
        classLoader = ClassLoaderBuilder.build(classesDir);
    }

    private static String sourceCodesAsString(List<SourceCode> sourceCodes) {
        StringBuilder builder = new StringBuilder();
        for (SourceCode sourceCode : sourceCodes) {
            builder.append("SourceCode: ").append(sourceCode.filePathName()).append(System.lineSeparator());
            builder.append(sourceCode.code()).append(System.lineSeparator());
        }
        return builder.toString();
    }

    /**
     * Access a class from the compiled code.
     *
     * @param name Name of the class to be retrieved.
     * @return The class corresponding to the {@code name}.
     */
    public Class<?> getClass(String name) {
        try {
            return Class.forName(name, true, classLoader);
        } catch (ClassNotFoundException e) {
            throw new CompileFrameworkException("Class not found:", e);
        }
    }

    /**
     * Invoke a static method from the compiled code.
     *
     * @param className Class name of a compiled class.
     * @param methodName Method name of the class.
     * @param args List of arguments for the method invocation.
     * @return Return value from the invocation.
     */
    public Object invoke(String className, String methodName, Object[] args) {
        Method method = findMethod(className, methodName);

        try {
            return method.invoke(null, args);
        } catch (IllegalAccessException e) {
            throw new CompileFrameworkException("Illegal access:", e);
        } catch (InvocationTargetException e) {
            throw new CompileFrameworkException("Invocation target:", e);
        }
    }

    private Method findMethod(String className, String methodName) {
        Class<?> c = getClass(className);
        Method[] methods = c.getDeclaredMethods();
        Method method = null;

        for (Method m : methods) {
            if (m.getName().equals(methodName)) {
                if (method != null) {
                  throw new CompileFrameworkException("Method name \"" + methodName + "\" not unique in class \n" + className + "\".");
                }
                method = m;
            }
        }

        if (method == null) {
            throw new CompileFrameworkException("Method \"" + methodName + "\" not found in class \n" + className + "\".");
        }

        return method;
    }

    /**
     * Returns the classpath appended with the {@link classesDir}, where
     * the compiled classes are stored. This enables another VM to load
     * the compiled classes. Note, the string is already backslash escaped,
     * so that Windows paths which use backslashes can be used directly
     * as strings.
     *
     * @return Classpath appended with the path to the compiled classes.
     */
    public String getEscapedClassPathOfCompiledClasses() {
        return Utils.getEscapedClassPathAndClassesDir(classesDir);
    }
}
