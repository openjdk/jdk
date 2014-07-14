/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.tools.*;

import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class TestBase {

    public static final String LINE_SEPARATOR = lineSeparator();

    private <S> InMemoryFileManager compile(
            List<String> options,
            Function<S, ? extends JavaFileObject> src2JavaFileObject,
            List<S> sources)
            throws IOException, CompilationException {

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<? extends JavaFileObject> src = sources.stream()
                .map(src2JavaFileObject)
                .collect(toList());

        DiagnosticCollector<? super JavaFileObject> dc = new DiagnosticCollector<>();
        try (InMemoryFileManager fileManager
                     = new InMemoryFileManager(compiler.getStandardFileManager(null, null, null))) {
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, dc, options, null, src);
            boolean success = task.call();
            if (!success) {
                String errorMessage = dc.getDiagnostics().stream()
                        .map(Object::toString)
                        .collect(joining("\n"));
                throw new CompilationException("Compilation Error\n\n" + errorMessage);
            }
            return fileManager;
        }
    }

    public InMemoryFileManager compile(String... sources)
            throws IOException, CompilationException {
        return compile(emptyList(), sources);
    }

    /**
     * @param options - compiler options
     * @param sources
     * @return map where key is className, value is corresponding ClassFile.
     * @throws IOException
     */
    public InMemoryFileManager compile(List<String> options, String...sources)
            throws IOException, CompilationException {
        return compile(options, ToolBox.JavaSource::new, asList(sources));
    }

    public InMemoryFileManager compile(String[]... sources) throws IOException,
            CompilationException {
        return compile(emptyList(), sources);
    }

    /**
     * @param options -  compiler options
     * @param sources - sources[i][0] - name of file, sources[i][1] - sources
     * @return map where key is className, value is corresponding ClassFile.
     * @throws IOException
     * @throws CompilationException
     */
    public InMemoryFileManager compile(List<String> options, String[]...sources)
            throws IOException, CompilationException {
        return compile(options, src -> new ToolBox.JavaSource(src[0], src[1]), asList(sources));
    }

    public void assertEquals(Object actual, Object expected, String message) {
        if (!Objects.equals(actual, expected))
            throw new AssertionFailedException(format("%s%nGot: %s, Expected: %s", message, actual, expected));
    }

    public void assertNull(Object actual, String message) {
        assertEquals(actual, null, message);
    }

    public void assertNotNull(Object actual, String message) {
        if (Objects.isNull(actual)) {
            throw new AssertionFailedException(message + " : Expected not null value");
        }
    }

    public void assertTrue(boolean actual, String message) {
        assertEquals(actual, true, message);
    }

    public void assertFalse(boolean actual, String message) {
        assertEquals(actual, false, message);
    }

    public File getSourceDir() {
        return new File(System.getProperty("test.src", "."));
    }

    public File getClassDir() {
        return new File(System.getProperty("test.classes", TestBase.class.getResource(".").getPath()));
    }

    public File getSourceFile(String fileName) {
        return new File(getSourceDir(), fileName);
    }

    public File getClassFile(String fileName) {
        return new File(getClassDir(), fileName);
    }

    public File getClassFile(Class clazz) {
        return getClassFile(clazz.getName().replace(".", "/") + ".class");
    }

    public void echo(String message) {
        System.err.println(message.replace("\n", LINE_SEPARATOR));
    }

    public void printf(String template, Object...args) {
        System.err.printf(template, Stream.of(args)
                .map(Objects::toString)
                .map(m -> m.replace("\n", LINE_SEPARATOR))
                .collect(toList())
                .toArray());

    }

    public static class CompilationException extends Exception {

        public CompilationException(String message) {
            super(message);
        }
    }

    public static class AssertionFailedException extends RuntimeException {
        public AssertionFailedException(String message) {
            super(message);
        }
    }
}
