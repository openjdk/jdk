/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.java.lang.instrument;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import jdk.test.lib.Utils;
import jdk.test.lib.util.JarUtils;

public class AgentJarBuilder {

    public static void build(String agentClass, String agentJar) throws IOException {
        Manifest mf = new Manifest();
        Attributes attrs = mf.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue("Can-Redefine-Classes", "true");
        attrs.putValue("Can-Retransform-Classes", "true");
        attrs.putValue("Premain-Class", agentClass);
        attrs.putValue("Agent-Class", agentClass);

        Path jarFile = Paths.get(".", agentJar);
        String testClasses = Utils.TEST_CLASSES;
        String agentPath = agentClass.replace(".", File.separator) + ".class";
        Path agentFile = Paths.get(testClasses, agentPath);
        Path dir = Paths.get(testClasses);
        JarUtils.createJarFile(jarFile, mf, dir, agentFile);
        System.out.println("Agent built:" + jarFile.toAbsolutePath());
    }

    public static void main(String argv[]) throws Exception {
        if (argv.length == 0) {
            throw new RuntimeException("Expected at least agent class name in arguments");
        }
        String agentClassName = argv[0];
        build(agentClassName, agentClassName + ".jar");
    }
}
