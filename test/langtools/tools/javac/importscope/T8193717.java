/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8193717
 * @summary Check that code with a lot named imports can compile.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 * @build toolbox.ToolBox toolbox.JavapTask
 * @run main T8193717
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.constant.ClassDesc;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import java.lang.classfile.*;

import toolbox.JavacTask;
import toolbox.ToolBox;

public class T8193717 {
    public static void main(String... args) throws IOException {
        new T8193717().run();
    }

    private static final int CLASSES = 50000;

    private void run() throws IOException {
        StringBuilder imports = new StringBuilder();
        StringBuilder use = new StringBuilder();

        for (int c = 0; c < CLASSES; c++) {
            String simpleName = getSimpleName(c);
            String pack = "p";
            imports.append("import " + pack + "." + simpleName + ";\n");
            use.append(simpleName + " " + simpleName + ";\n");
        }
        String source = imports.toString() + "public class T {\n" + use.toString() + "}";
        ToolBox tb = new ToolBox();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
            fm.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(Paths.get(".")));
            new JavacTask(tb).sources(source)
                             .options("-XDshould-stop.ifError=ATTR",
                                      "-XDshould-stop.ifNoError=ATTR") //the source is too big for a classfile
                             .fileManager(new TestJFM(fm))
                             .run();
        }
    }

    private static String getSimpleName(int c) {
        return "T" + String.format("%0" + (int) Math.ceil(Math.log10(CLASSES)) + "d", c);
    }

    private byte[] generateClassFile(String name) throws IOException {
        byte[] bytes = ClassFile.of().build(ClassDesc.of(name), classBuilder -> {
            classBuilder.withSuperclass(ClassDesc.ofInternalName("java/lang/Object"))
                    .withVersion(51, 0)
                    .withFlags(ClassFile.ACC_ABSTRACT | ClassFile.ACC_INTERFACE | ClassFile.ACC_PUBLIC);
        });
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(bytes);
        return baos.toByteArray();
    }

    final class TestJFM extends ForwardingJavaFileManager<JavaFileManager> {

        public TestJFM(JavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public Iterable<JavaFileObject> list(Location location, String packageName,
                                             Set<Kind> kinds, boolean recurse) throws IOException {
            if (location == StandardLocation.CLASS_PATH) {
                if (packageName.equals("p")) {
                    try {
                        List<JavaFileObject> result = new ArrayList<>(CLASSES);

                        for (int c = 0; c < CLASSES; c++) {
                            result.add(new TestJFO("p." + getSimpleName(c)));
                        }

                        return result;
                    } catch (URISyntaxException ex) {
                        throw new IllegalStateException(ex);
                    }
                }
            }
            return super.list(location, packageName, kinds, recurse);
        }

        @Override
        public String inferBinaryName(Location location, JavaFileObject file) {
            if (file instanceof TestJFO) {
                return ((TestJFO) file).name;
            }
            return super.inferBinaryName(location, file);
        }

        private class TestJFO extends SimpleJavaFileObject {

            private final String name;

            public TestJFO(String name) throws URISyntaxException {
                super(new URI("mem://" + name.replace(".", "/") + ".class"), Kind.CLASS);
                this.name = name;
            }

            @Override
            public InputStream openInputStream() throws IOException {
                return new ByteArrayInputStream(generateClassFile(name));
            }
        }

    }
}
