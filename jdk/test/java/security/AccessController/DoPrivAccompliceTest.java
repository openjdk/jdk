/*
 * Copyright (c) 2007, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @test
 * @bug 8048362
 * @compile ../../../lib/testlibrary/JavaToolUtils.java
 *                             DoPrivAccomplice.java DoPrivTest.java
 * @summary Tests the doPrivileged with accomplice Generate two jars
 * (DoPrivTest.jar and DoPrivAccomplice.jar) and grant permission to
 * DoPrivAccmplice.jar for reading user.home property from a PrivilagedAction.
 * Run DoPrivTest.jar and try to access user.home property using
 * DoPrivAccmplice.jar.
 * @run main/othervm DoPrivAccompliceTest
 */

public class DoPrivAccompliceTest {

    private static final String PWD = System.getProperty("test.classes", "./");
    private static final String ACTION_SOURCE = "DoPrivAccomplice";
    private static final String TEST_SOURCE = "DoPrivTest";

    public static void createPolicyFile(URI codebaseURL) throws IOException {
        String codebase = codebaseURL.toString();
        String quotes = "\"";
        StringBuilder policyFile = new StringBuilder();
        policyFile.append("grant codeBase ").append(quotes).
                append(codebase).append(quotes).append("{\n").
                append("permission java.util.PropertyPermission ").
                append(quotes).append("user.name").append(quotes).
                append(",").append(quotes).append("read").append(quotes).
                append(";\n};");
        try (FileWriter writer = new FileWriter(new File(PWD, "java.policy"))) {
            writer.write(policyFile.toString());
            writer.close();
        } catch (IOException e) {
            System.err.println("Error while creating policy file");
            throw e;
        }
    }

    public static void main(String[] args) throws Exception {
        final File class1 = new File(PWD, ACTION_SOURCE + ".class");
        final File class2 = new File(PWD, TEST_SOURCE + ".class");
        final File jarFile1 = new File(PWD, ACTION_SOURCE + ".jar");
        final File jarFile2 = new File(PWD, TEST_SOURCE + ".jar");
        System.out.println("Compilation successfull");
        JavaToolUtils.createJar(jarFile1, Arrays.asList(new File[]{class1}));
        System.out.println("Created jar file " + jarFile1);
        JavaToolUtils.createJar(jarFile2, Arrays.asList(new File[]{class2}));
        System.out.println("Created jar file " + jarFile2);
        createPolicyFile(jarFile1.toURI());

        List<String> commands = new ArrayList<>();
        final String pathSepartor = System.getProperty("path.separator");
        commands.add("-Djava.security.manager");
        commands.add("-Djava.security.policy=" + PWD + "/java.policy");
        commands.add("-classpath");
        commands.add(PWD + "/" + TEST_SOURCE + ".jar" + pathSepartor
                + PWD + "/" + ACTION_SOURCE + ".jar");
        commands.add(TEST_SOURCE);
        if (JavaToolUtils.runJava(commands) == 0) {
            System.out.println("Test PASSES");
        }
    }

}
