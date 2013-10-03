/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6493690 8024434
 * @summary javadoc should have a javax.tools.Tool service provider
 * @build APITest
 * @run main GetTask_FileManagerTest
 */

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

import javax.tools.DocumentationTool;
import javax.tools.DocumentationTool.DocumentationTask;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.ToolProvider;

import com.sun.tools.javac.nio.JavacPathFileManager;
import com.sun.tools.javac.nio.PathFileManager;
import com.sun.tools.javac.util.Context;

/**
 * Tests for DocumentationTool.getTask  fileManager  parameter.
 */
public class GetTask_FileManagerTest extends APITest {
    public static void main(String... args) throws Exception {
        new GetTask_FileManagerTest().run();
    }

    /**
     * Verify that an alternate file manager can be specified:
     * in this case, a PathFileManager.
     */
    @Test
    public void testFileManager() throws Exception {
        JavaFileObject srcFile = createSimpleJavaFileObject();
        DocumentationTool tool = ToolProvider.getSystemDocumentationTool();
        PathFileManager fm = new JavacPathFileManager(new Context(), false, null);
        Path outDir = getOutDir().toPath();
        fm.setLocation(DocumentationTool.Location.DOCUMENTATION_OUTPUT, Arrays.asList(outDir));
        Iterable<? extends JavaFileObject> files = Arrays.asList(srcFile);
        DocumentationTask t = tool.getTask(null, fm, null, null, null, files);
        if (t.call()) {
            System.err.println("task succeeded");
            checkFiles(outDir, standardExpectFiles);
        } else {
            throw new Exception("task failed");
        }
    }

    /**
     * Verify that exceptions from a bad file manager are thrown as expected.
     */
    @Test
    public void testBadFileManager() throws Exception {
        JavaFileObject srcFile = createSimpleJavaFileObject();
        DocumentationTool tool = ToolProvider.getSystemDocumentationTool();
        PathFileManager fm = new JavacPathFileManager(new Context(), false, null) {
            @Override
            public Iterable<JavaFileObject> list(Location location,
                    String packageName,
                    Set<Kind> kinds,
                    boolean recurse)
                    throws IOException {
                throw new UnexpectedError();
            }
        };
        Path outDir = getOutDir().toPath();
        fm.setLocation(DocumentationTool.Location.DOCUMENTATION_OUTPUT, Arrays.asList(outDir));
        Iterable<? extends JavaFileObject> files = Arrays.asList(srcFile);
        DocumentationTask t = tool.getTask(null, fm, null, null, null, files);
        try {
            t.call();
            error("call completed without exception");
        } catch (RuntimeException e) {
            Throwable c = e.getCause();
            if (c.getClass() == UnexpectedError.class)
                System.err.println("exception caught as expected: " + c);
            else
                throw e;
        }
    }

    public static class UnexpectedError extends Error { }

}
