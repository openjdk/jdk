/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test StripJavaDebugAttributesPlugin
 * @author Jean-Francois Denise
 * @library ../../lib
 * @build tests.*
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.internal.plugins
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.jlink/jdk.tools.jmod
 *          java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          jdk.compiler
 * @run main StripJavaDebugAttributesPluginTest
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.CodeAttribute;
import jdk.tools.jlink.internal.ResourcePoolManager;
import jdk.tools.jlink.internal.plugins.StripJavaDebugAttributesPlugin;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolEntry;
import tests.Helper;

public class StripJavaDebugAttributesPluginTest {
    public static void main(String[] args) throws Exception {
        new StripJavaDebugAttributesPluginTest().test();
    }

    public void test() throws Exception {
        Helper helper = Helper.newHelper();
        if (helper == null) {
            // Skip test if the jmods directory is missing (e.g. exploded image)
            System.err.println("Test not run, NO jmods directory");
            return;
        }

        List<String> classes = Arrays.asList("toto.Main", "toto.com.foo.bar.X");
        Path moduleFile = helper.generateModuleCompiledClasses(
                helper.getJmodSrcDir(), helper.getJmodClassesDir(), "leaf1", classes);
        Path moduleInfo = moduleFile.resolve("module-info.class");

        // Classes have been compiled in debug.
        List<Path> covered = new ArrayList<>();
        byte[] infoContent = Files.readAllBytes(moduleInfo);
        try (Stream<Path> stream = Files.walk(moduleFile)) {
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext(); ) {
                Path p = iterator.next();
                if (Files.isRegularFile(p) && p.toString().endsWith(".class")) {
                    byte[] content = Files.readAllBytes(p);
                    String path = "/" + helper.getJmodClassesDir().relativize(p).toString();
                    String moduleInfoPath = path + "/module-info.class";
                    check(path, content, moduleInfoPath, infoContent);
                    covered.add(p);
                }
            }
        }
        if (covered.isEmpty()) {
            throw new AssertionError("No class to compress");
        } else {
            System.err.println("removed debug attributes from "
                    + covered.size() + " classes");
        }
    }

    private void check(String path, byte[] content, String infoPath, byte[] moduleInfo) throws Exception {
        path = path.replace('\\', '/');
        StripJavaDebugAttributesPlugin debug = new StripJavaDebugAttributesPlugin();
        debug.configure(new HashMap<>());
        ResourcePoolEntry result1 = stripDebug(debug, ResourcePoolEntry.create(path,content), path, infoPath, moduleInfo);

        if (!path.endsWith("module-info.class")) {
            if (result1.contentLength() >= content.length) {
                throw new AssertionError("Class size not reduced, debug info not "
                        + "removed for " + path);
            }
            checkDebugAttributes(result1.contentBytes());
        }

        ResourcePoolEntry result2 = stripDebug(debug, result1, path, infoPath, moduleInfo);
        if (result1.contentLength() != result2.contentLength()) {
            throw new AssertionError("removing debug info twice reduces class size of "
                    + path);
        }
        checkDebugAttributes(result1.contentBytes());
    }

    private ResourcePoolEntry stripDebug(Plugin debug, ResourcePoolEntry classResource,
            String path, String infoPath, byte[] moduleInfo) throws Exception {
        ResourcePoolManager resources = new ResourcePoolManager();
        resources.add(classResource);
        if (!path.endsWith("module-info.class")) {
            ResourcePoolEntry res2 = ResourcePoolEntry.create(infoPath, moduleInfo);
            resources.add(res2);
        }
        ResourcePoolManager results = new ResourcePoolManager();
        ResourcePool resPool = debug.transform(resources.resourcePool(),
                results.resourcePoolBuilder());
        System.out.println(classResource.path());

        return resPool.findEntry(classResource.path()).get();
    }

    private <T extends Attribute<T>> void checkDebugAttributes(byte[] strippedClassFile) {
        ClassModel classFile = Classfile.of().parse(strippedClassFile);
        for (MethodModel method : classFile.methods()) {
            String methodName = method.methodName().stringValue();
            CodeAttribute code = method.findAttribute(Attributes.CODE).orElseThrow();
            if (code.findAttribute(Attributes.LINE_NUMBER_TABLE).orElse(null) != null) {
                throw new AssertionError("Debug attribute was not removed: " + "LINE_NUMBER_TABLE" +
                        " from method " + classFile.thisClass().asInternalName() + "#" + methodName);
            }
            if (code.findAttribute(Attributes.LOCAL_VARIABLE_TABLE).orElse(null) != null) {
                throw new AssertionError("Debug attribute was not removed: " + "LOCAL_VARIABLE_TABLE" +
                        " from method " + classFile.thisClass().asInternalName() + "#" + methodName);
            }
            if (code.findAttribute(Attributes.LOCAL_VARIABLE_TYPE_TABLE).orElse(null) != null) {
                throw new AssertionError("Debug attribute was not removed: " + "LOCAL_VARIABLE_TYPE_TABLE" +
                        " from method " + classFile.thisClass().asInternalName() + "#" + methodName);
            }
        }
    }
}
