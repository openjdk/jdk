/*
 * Copyright (c) 2006, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @modules jdk.jdeps/com.sun.tools.classfile
 *          jdk.compiler/com.sun.tools.javac.util
 * @run main DetectMutableStaticFields
 */

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.Descriptor;
import com.sun.tools.classfile.Descriptor.InvalidDescriptor;
import com.sun.tools.classfile.Field;

import static javax.tools.JavaFileObject.Kind.CLASS;
import static com.sun.tools.classfile.AccessFlags.ACC_ENUM;
import static com.sun.tools.classfile.AccessFlags.ACC_FINAL;
import static com.sun.tools.classfile.AccessFlags.ACC_STATIC;

public class DetectMutableStaticFields {

    private static final String keyResource =
            "com/sun/tools/javac/tree/JCTree.class";

    private String[] packagesToSeekFor = new String[] {
        "javax.tools",
        "javax.lang.model",
        "com.sun.javadoc",
        "com.sun.source",
        "com.sun.tools.classfile",
        "com.sun.tools.doclets",
        "com.sun.tools.javac",
        "com.sun.tools.javadoc",
        "com.sun.tools.javah",
        "com.sun.tools.javap",
    };

    private static final Map<String, List<String>> classFieldsToIgnoreMap = new HashMap<>();

    static {
        classFieldsToIgnoreMap.
                put("javax/tools/ToolProvider",
                    Arrays.asList("instance"));
        classFieldsToIgnoreMap.
                put("com/sun/tools/javah/JavahTask",
                    Arrays.asList("versionRB"));
        classFieldsToIgnoreMap.
                put("com/sun/tools/classfile/Dependencies$DefaultFilter",
                    Arrays.asList("instance"));
        classFieldsToIgnoreMap.
                put("com/sun/tools/javap/JavapTask",
                    Arrays.asList("versionRB"));
        classFieldsToIgnoreMap.
                put("com/sun/tools/doclets/formats/html/HtmlDoclet",
                    Arrays.asList("docletToStart"));
        classFieldsToIgnoreMap.
                put("com/sun/tools/javac/util/JCDiagnostic",
                    Arrays.asList("fragmentFormatter"));
        classFieldsToIgnoreMap.
                put("com/sun/tools/javac/util/JavacMessages",
                    Arrays.asList("defaultBundle", "defaultMessages"));
        classFieldsToIgnoreMap.
                put("com/sun/tools/javac/file/ZipFileIndexCache",
                    Arrays.asList("sharedInstance"));
        classFieldsToIgnoreMap.
                put("com/sun/tools/javac/file/JRTIndex",
                    Arrays.asList("sharedInstance"));
        classFieldsToIgnoreMap.
                put("com/sun/tools/javac/main/JavaCompiler",
                    Arrays.asList("versionRB"));
        classFieldsToIgnoreMap.
                put("com/sun/tools/javac/code/Type",
                    Arrays.asList("moreInfo"));
        classFieldsToIgnoreMap.
                put("com/sun/tools/javac/util/SharedNameTable",
                    Arrays.asList("freelist"));
        classFieldsToIgnoreMap.
                put("com/sun/tools/javac/util/Log",
                    Arrays.asList("useRawMessages"));
    }

    private List<String> errors = new ArrayList<>();

    public static void main(String[] args) {
        try {
            new DetectMutableStaticFields().run();
        } catch (Exception ex) {
            throw new AssertionError(
                    "Exception during test execution with cause ",
                    ex.getCause());
        }
    }

    private void run()
        throws
            IOException,
            ConstantPoolException,
            InvalidDescriptor,
            URISyntaxException {

        URI resource = findResource(keyResource);
        if (resource == null) {
            throw new AssertionError("Resource " + keyResource +
                "not found in the class path");
        }
        analyzeResource(resource);

        if (errors.size() > 0) {
            for (String error: errors) {
                System.err.println(error);
            }
            throw new AssertionError("There are mutable fields, "
                + "please check output");
        }
    }

    URI findResource(String className) throws URISyntaxException {
        URI uri = getClass().getClassLoader().getResource(className).toURI();
        if (uri.getScheme().equals("jar")) {
            String ssp = uri.getRawSchemeSpecificPart();
            int sep = ssp.lastIndexOf("!");
            uri = new URI(ssp.substring(0, sep));
        } else if (uri.getScheme().equals("file")) {
            uri = new URI(uri.getPath().substring(0,
                    uri.getPath().length() - keyResource.length()));
        }
        return uri;
    }

    boolean shouldAnalyzePackage(String packageName) {
        for (String aPackage: packagesToSeekFor) {
            if (packageName.contains(aPackage)) {
                return true;
            }
        }
        return false;
    }

    void analyzeResource(URI resource)
        throws
            IOException,
            ConstantPoolException,
            InvalidDescriptor {
        JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = tool.getStandardFileManager(null, null, null)) {
            JavaFileManager.Location location =
                    StandardLocation.locationFor(resource.getPath());
            fm.setLocation(location, com.sun.tools.javac.util.List.of(
                    new File(resource.getPath())));

            for (JavaFileObject file : fm.list(location, "", EnumSet.of(CLASS), true)) {
                String className = fm.inferBinaryName(location, file);
                int index = className.lastIndexOf('.');
                String pckName = index == -1 ? "" : className.substring(0, index);
                if (shouldAnalyzePackage(pckName)) {
                    analyzeClassFile(ClassFile.read(file.openInputStream()));
                }
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

    void analyzeClassFile(ClassFile classFileToCheck)
        throws
            IOException,
            ConstantPoolException,
            Descriptor.InvalidDescriptor {
        boolean enumClass =
                (classFileToCheck.access_flags.flags & ACC_ENUM) != 0;
        boolean nonFinalStaticEnumField;
        boolean nonFinalStaticField;

        currentFieldsToIgnore =
                classFieldsToIgnoreMap.get(classFileToCheck.getName());

        for (Field field : classFileToCheck.fields) {
            if (ignoreField(field.getName(classFileToCheck.constant_pool))) {
                continue;
            }
            nonFinalStaticEnumField =
                    (field.access_flags.flags & (ACC_ENUM | ACC_FINAL)) == 0;
            nonFinalStaticField =
                    (field.access_flags.flags & ACC_STATIC) != 0 &&
                    (field.access_flags.flags & ACC_FINAL) == 0;
            if (enumClass ? nonFinalStaticEnumField : nonFinalStaticField) {
                errors.add("There is a mutable field named " +
                        field.getName(classFileToCheck.constant_pool) +
                        ", at class " +
                        classFileToCheck.getName());
            }
        }
    }

}
