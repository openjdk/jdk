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
* TODO
*/
public class CompileFramework {
    private List<SourceCode> sourceCodes = new ArrayList<>();
    private final Path sourceDir = Utils.getTempDir("compile-framework-sources-");
    private final Path classesDir = Utils.getTempDir("compile-framework-classes-");
    private ClassLoader classLoader;

    /**
    * TODO change name to escaped too???
    */
    public String getClassPathOfCompiledClasses() {
        return Utils.getEscapedClassPathAndClassesDir(classesDir);
    }

    /**
    * TODO
    */
    public void addJavaSourceCode(String className, String code) {
        sourceCodes.add(new SourceCode(className, code, SourceCode.Kind.JAVA));
    }

    /**
    * TODO
    */
    public void addJasmSourceCode(String className, String code) {
        sourceCodes.add(new SourceCode(className, code, SourceCode.Kind.JASM));
    }

    private String sourceCodesAsString() {
        StringBuilder builder = new StringBuilder();
        for (SourceCode sourceCode : sourceCodes) {
            builder.append("SourceCode: ").append(sourceCode.filePathName()).append(System.lineSeparator());
            builder.append(sourceCode.code).append(System.lineSeparator());
        }
        return builder.toString();
    }

    /**
    * TODO
    */
    public void compile() {
        if (classLoader != null) {
            throw new CompileFrameworkException("Cannot compile twice!");
        }

        Utils.printlnVerbose("------------------ CompileFramework: SourceCode -------------------");
        Utils.printlnVerbose(sourceCodesAsString());

        List<SourceCode> javaSources = new ArrayList<SourceCode>();
        List<SourceCode> jasmSources = new ArrayList<SourceCode>();
        for (SourceCode sourceCode : sourceCodes) {
            switch (sourceCode.kind) {
                case SourceCode.Kind.JASM -> { jasmSources.add(sourceCode);  }
                case SourceCode.Kind.JAVA -> { javaSources.add(sourceCode);  }
            }
        }

        System.out.println("------------------ CompileFramework: Compilation ------------------");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Classes directory: " + classesDir);

        Compile.compileJasmSources(jasmSources, sourceDir, classesDir);
        Compile.compileJavaSources(javaSources, sourceDir, classesDir);
        classLoader = ClassLoaderBuilder.build(classesDir);
    }

    /**
    * TODO
    */
    public Class<?> getClass(String name) {
        try {
            return Class.forName(name, true, classLoader);
        } catch (ClassNotFoundException e) {
            throw new CompileFrameworkException("Class not found:", e);
        }
    }

    /**
    * TODO
    */
    public Object invoke(String className, String methodName, Object[] args) {
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

        try {
            return method.invoke(null, args);
        } catch (IllegalAccessException e) {
            throw new CompileFrameworkException("Illegal access:", e);
        } catch (InvocationTargetException e) {
            throw new CompileFrameworkException("Invocation target:", e);
        }
    }
}

