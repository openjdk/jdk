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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public class TestBase {

    public Map<String, ? extends JavaFileObject> compile(String... sources) throws IOException,
            CompilationException {
        return compile(emptyList(), sources);
    }

    /**
     * @param options -  compiler options
     * @param sources
     * @return map where key is className, value is corresponding ClassFile.
     * @throws IOException
     */
    public Map<String, ? extends JavaFileObject> compile(List<String> options, String... sources) throws IOException,
            CompilationException {

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<? extends JavaFileObject> src = Stream.of(sources).map(ToolBox.JavaSource::new).collect(toList());

        try (InMemoryFileManager fileManager = new InMemoryFileManager(compiler.getStandardFileManager(null, null, null))) {
            boolean success = compiler.getTask(null, fileManager, null, options, null, src).call();
            if (!success) throw new CompilationException("Compilation Error");
            return fileManager.getClasses();
        }
    }

    public void assertEquals(Object actual, Object expected, String message) {
        if (!Objects.equals(actual, expected))
            throw new AssertionFailedException(format("%s%nGot: %s, Expected: ", message, actual, expected));
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

    public File getSourceFile(String fileName) {
        return new File(System.getProperty("test.src", "."), fileName);
    }

    public File getClassFile(String fileName) {
        return new File(System.getProperty("test.classes", TestBase.class.getResource(".").getPath()), fileName);
    }

    public File getClassFile(Class clazz) {
        return getClassFile(clazz.getName().replace(".", "/") + ".class");
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
