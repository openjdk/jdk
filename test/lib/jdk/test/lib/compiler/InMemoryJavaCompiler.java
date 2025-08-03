/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.compiler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * {@code InMemoryJavaCompiler} can be used for compiling a {@link
 * CharSequence} to a {@code byte[]}.
 *
 * The compiler will not use the file system at all, instead using a {@link
 * ByteArrayOutputStream} for storing the byte code. For the source code, any
 * kind of {@link CharSequence} can be used, e.g. {@link String}, {@link
 * StringBuffer} or {@link StringBuilder}.
 *
 * The {@code InMemoryCompiler} can easily be used together with a {@code
 * ByteClassLoader} to easily compile and load source code in a {@link String}:
 *
 * <pre>
 * {@code
 * import jdk.test.lib.compiler.InMemoryJavaCompiler;
 * import jdk.test.lib.ByteClassLoader;
 *
 * class Example {
 *     public static void main(String[] args) {
 *         String className = "Foo";
 *         String sourceCode = "public class " + className + " {" +
 *                             "    public void bar() {" +
 *                             "        System.out.println("Hello from bar!");" +
 *                             "    }" +
 *                             "}";
 *         byte[] byteCode = InMemoryJavaCompiler.compile(className, sourceCode);
 *         Class fooClass = ByteClassLoader.load(className, byteCode);
 *     }
 * }
 * }
 * </pre>
 */
public class InMemoryJavaCompiler {

    private static class FileManagerWrapper extends ForwardingJavaFileManager<JavaFileManager> {
        private static final Location PATCH_LOCATION = new Location() {
            @Override
            public String getName() {
                return "patch module location";
            }

            @Override
            public boolean isOutputLocation() {
                return false;
            }
        };
        private final SourceFile srcFile;
        private ClassFile clsFile;
        private final String moduleOverride;

        public FileManagerWrapper(SourceFile file, String moduleOverride) {
            super(getCompiler().getStandardFileManager(null, null, null));
            this.srcFile = file;
            this.moduleOverride = moduleOverride;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                   Kind kind, FileObject sibling)
            throws IOException {
            if (!srcFile.getClassName().equals(className)) {
                throw new IOException("Expected class with name " + srcFile.getClassName() +
                                      ", but got " + className);
            }
            clsFile = new ClassFile(className);
            return clsFile;
        }

        @Override
        public Location getLocationForModule(Location location, JavaFileObject fo) throws IOException {
            if (fo == srcFile && moduleOverride != null) {
                return PATCH_LOCATION;
            }
            return super.getLocationForModule(location, fo);
        }

        @Override
        public String inferModuleName(Location location) throws IOException {
            if (location == PATCH_LOCATION) {
                return moduleOverride;
            }
            return super.inferModuleName(location);
        }

        @Override
        public boolean hasLocation(Location location) {
            return super.hasLocation(location) || location == StandardLocation.PATCH_MODULE_PATH;
        }

        public byte[] getByteCode() {
            return clsFile.toByteArray();
        }

    }

    // Wraper for class file
    static class ClassFile extends SimpleJavaFileObject {

        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        protected ClassFile(String name) {
            super(URI.create("memo:///" + name.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        }

        @Override
        public ByteArrayOutputStream openOutputStream() { return this.baos; }

        byte[] toByteArray() { return baos.toByteArray(); }
    }

    // File manager which spawns ClassFile instances by demand
    static class FileManager extends ForwardingJavaFileManager<JavaFileManager> {

        private Map<String, ClassFile> classesMap = new HashMap<String, ClassFile>();

        protected FileManager(JavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public ClassFile getJavaFileForOutput(Location location, String name, JavaFileObject.Kind kind, FileObject source) {
            ClassFile classFile = new ClassFile(name);
            classesMap.put(name, classFile);
            return classFile;
        }

        public Map<String, byte[]> getByteCode() {
            Map<String, byte[]> result = new HashMap<String, byte[]>();
            for (Entry<String, ClassFile> entry : classesMap.entrySet()) {
                result.put(entry.getKey(), entry.getValue().toByteArray());
            }
            return result;
        }
    }

    // Wrapper for source file
    static class SourceFile extends SimpleJavaFileObject {

        private CharSequence sourceCode;
        private String className;

        public SourceFile(String name, CharSequence sourceCode) {
            super(URI.create("memo:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.sourceCode = sourceCode;
            this.className = name;
        }

        @Override
        public CharSequence getCharContent(boolean ignore) {
            return this.sourceCode;
        }

        public String getClassName() {
            return this.className;
        }
    }

    /**
     * Compiles the list of classes with the given map of binary name and source code.
     * This overloaded version of compile is useful for batch compile use cases, or
     * if a compilation unit produces multiple class files. Returns a map from
     * class binary names to class file content.
     *
     * @param inputMap The map containing the name of the class and corresponding source code
     * @throws RuntimeException if the compilation did not succeed
     * @return The resulting byte code from the compilation
     */
    public static Map<String, byte[]> compile(Map<String, ? extends CharSequence> inputMap) {
        Collection<JavaFileObject> sourceFiles = new LinkedList<JavaFileObject>();
        for (Entry<String, ? extends CharSequence> entry : inputMap.entrySet()) {
            sourceFiles.add(new SourceFile(entry.getKey(), entry.getValue()));
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        FileManager fileManager = new FileManager(compiler.getStandardFileManager(null, null, null));

        Writer writer = new StringWriter();
        Boolean exitCode = compiler.getTask(writer, fileManager, null, null, null, sourceFiles).call();
        if (!exitCode) {
            System.out.println("*********** javac output begin ***********");
            System.out.println(writer.toString());
            System.out.println("*********** javac output end ***********");
            throw new RuntimeException("Test bug: in memory compilation failed.");
        }
        return fileManager.getByteCode();
    }

    /**
     * Compiles the class with the given name and source code.
     *
     * @param className The name of the class
     * @param sourceCode The source code for the class with name {@code className}
     * @param options additional command line options
     * @throws RuntimeException if the compilation did not succeed or if closing
     *         the {@code JavaFileManager} used for the compilation did not succeed
     * @return The resulting byte code from the compilation
     */
    public static byte[] compile(String className, CharSequence sourceCode, String... options) {
        SourceFile file = new SourceFile(className, sourceCode);
        List<String> opts = new ArrayList<>();
        String moduleOverride = null;
        for (String opt : options) {
            if (opt.startsWith("--patch-module=")) {
                moduleOverride = opt.substring("--patch-module=".length());
            } else {
                opts.add(opt);
            }
        }
        try (FileManagerWrapper fileManager = new FileManagerWrapper(file, moduleOverride)) {
            CompilationTask task = getCompiler().getTask(null, fileManager, null, opts, null, Arrays.asList(file));
            if (!task.call()) {
                throw new RuntimeException("Could not compile " + className + " with source code " + sourceCode);
            }

            return fileManager.getByteCode();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private static JavaCompiler getCompiler() {
        return ToolProvider.getSystemJavaCompiler();
    }
}
