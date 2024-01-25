/*
 * Copyright (c) 2006, 2021, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8003967
 * @summary detect and remove all mutable implicit static enum fields in langtools
 * @enablePreview
 * @modules java.base/jdk.internal.classfile.impl
 *          jdk.compiler/com.sun.tools.javac.util
 * @run main DetectMutableStaticFields
 */

import java.lang.classfile.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;


import static javax.tools.JavaFileObject.Kind.CLASS;


public class DetectMutableStaticFields {

    private final String[] modules = {
        "java.compiler",
        "jdk.compiler",
        "jdk.javadoc",
        "jdk.jdeps"
    };

    private final String[] packagesToSeekFor = new String[] {
        "javax.tools",
        "javax.lang.model",
        "com.sun.source",
        "jdk.internal.classfile",
        "jdk.internal.classfile.attribute",
        "jdk.internal.classfile.constantpool",
        "jdk.internal.classfile.instruction",
        "jdk.internal.classfile.components",
        "jdk.internal.classfile.impl",
        "com.sun.tools.javac",
        "com.sun.tools.javah",
        "com.sun.tools.javap",
        "jdk.javadoc"
    };

    private static final Map<String, List<String>> classFieldsToIgnoreMap = new HashMap<>();
    private static void ignore(String className, String... fields) {
        classFieldsToIgnoreMap.put(className, Arrays.asList(fields));
    }

    static {
        ignore("javax/tools/ToolProvider", "instance");
        ignore("com/sun/tools/javah/JavahTask", "versionRB");
        ignore("com/sun/tools/javap/JavapTask", "versionRB");
        ignore("com/sun/tools/doclets/formats/html/HtmlDoclet", "docletToStart");
        ignore("com/sun/tools/javac/util/JCDiagnostic", "fragmentFormatter");
        ignore("com/sun/tools/javac/util/JavacMessages", "defaultBundle", "defaultMessages");
        ignore("com/sun/tools/javac/file/JRTIndex", "sharedInstance");
        ignore("com/sun/tools/javac/main/JavaCompiler", "versionRB");
        ignore("com/sun/tools/javac/code/Type", "moreInfo");
        ignore("com/sun/tools/javac/util/SharedNameTable", "freelist");
        ignore("com/sun/tools/javac/util/Log", "useRawMessages");

        // The following static fields are used for caches of information obtained
        // by reflective lookup, to avoid explicit references that are not available
        // when running javac on JDK 8.
        ignore("com/sun/tools/javac/util/JDK9Wrappers$Configuration",
                "resolveAndBindMethod", "configurationClass");
        ignore("com/sun/tools/javac/util/JDK9Wrappers$Layer",
                "bootMethod", "defineModulesWithOneLoaderMethod", "configurationMethod", "layerClass");
        ignore("com/sun/tools/javac/util/JDK9Wrappers$Module",
                "addExportsMethod", "addUsesMethod", "getModuleMethod", "getUnnamedModuleMethod");
        ignore("com/sun/tools/javac/util/JDK9Wrappers$ModuleDescriptor$Version",
                "versionClass", "parseMethod");
        ignore("com/sun/tools/javac/util/JDK9Wrappers$ModuleFinder",
                "moduleFinderClass", "ofMethod");
        ignore("com/sun/tools/javac/util/JDK9Wrappers$ServiceLoaderHelper",
                "loadMethod");
        ignore("com/sun/tools/javac/util/JDK9Wrappers$VMHelper",
                "vmClass", "getRuntimeArgumentsMethod");
        ignore("com/sun/tools/javac/util/JDK9Wrappers$JmodFile",
                "jmodFileClass", "checkMagicMethod");
    }

    private final List<String> errors = new ArrayList<>();

    public static void main(String[] args) {
        try {
            new DetectMutableStaticFields().run();
        } catch (Exception ex) {
            throw new AssertionError("Exception during test execution: " + ex, ex);
        }
    }

    private void run() throws IOException {

        JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = tool.getStandardFileManager(null, null, null)) {
            for (String module: modules) {
                analyzeModule(fm, module);
            }
        }

        if (errors.size() > 0) {
            for (String error: errors) {
                System.err.println(error);
            }
            throw new AssertionError("There are mutable fields, "
                + "please check output");
        }
    }

    boolean shouldAnalyzePackage(String packageName) {
        for (String aPackage: packagesToSeekFor) {
            if (packageName.contains(aPackage)) {
                return true;
            }
        }
        return false;
    }

    void analyzeModule(StandardJavaFileManager fm, String moduleName) throws IOException {
        JavaFileManager.Location location =
                fm.getLocationForModule(StandardLocation.SYSTEM_MODULES, moduleName);
        if (location == null)
            throw new AssertionError("can't find module " + moduleName);

        for (JavaFileObject file : fm.list(location, "", EnumSet.of(CLASS), true)) {
            String className = fm.inferBinaryName(location, file);
            int index = className.lastIndexOf('.');
            String pckName = index == -1 ? "" : className.substring(0, index);
            if (shouldAnalyzePackage(pckName)) {
                ClassModel classFile;
                try (InputStream input = file.openInputStream()) {
                    classFile = ClassFile.of().parse(input.readAllBytes());
                }
                analyzeClassFile(classFile);
            }
        }
    }

    List<String> currentFieldsToIgnore;

    boolean ignoreField(String field) {
        if (currentFieldsToIgnore != null) {
            for (String fieldToIgnore : currentFieldsToIgnore) {
                if (field.equals(fieldToIgnore)) {
                    return true;
                }
            }
        }
        return false;
    }

    void analyzeClassFile(ClassModel classFileToCheck) {
        boolean enumClass =
                (classFileToCheck.flags().flagsMask() & ClassFile.ACC_ENUM) != 0;
        boolean nonFinalStaticEnumField;
        boolean nonFinalStaticField;

        currentFieldsToIgnore =
                classFieldsToIgnoreMap.get(classFileToCheck.thisClass().asInternalName());

        for (FieldModel field : classFileToCheck.fields()) {
            if (ignoreField(field.fieldName().stringValue())) {
                continue;
            }
            nonFinalStaticEnumField =
                    (field.flags().flagsMask() & (ClassFile.ACC_ENUM | ClassFile.ACC_FINAL)) == 0;
            nonFinalStaticField =
                    (field.flags().flagsMask() & ClassFile.ACC_STATIC) != 0 &&
                    (field.flags().flagsMask() & ClassFile.ACC_FINAL) == 0;
            if (enumClass ? nonFinalStaticEnumField : nonFinalStaticField) {
                errors.add("There is a mutable field named " +
                        field.fieldName().stringValue() +
                        ", at class " +
                        classFileToCheck.thisClass().asInternalName());
            }
        }
    }

}
