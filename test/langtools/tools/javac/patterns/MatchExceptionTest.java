/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8297118
 * @summary Verify javac uses MatchException or IncompatibleClassChangeError for exhaustive switches
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.classfile
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main/othervm --enable-preview MatchExceptionTest
 */

import java.nio.file.Path;

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Class_info;
import com.sun.tools.classfile.ConstantPool.CPInfo;
import java.util.Arrays;

import toolbox.JavacTask;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class MatchExceptionTest extends TestRunner {
    private static final String JAVA_VERSION = System.getProperty("java.specification.version");
    private static final String TEST_METHOD = "test";

    ToolBox tb;
    ClassFile cf;

    public MatchExceptionTest() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        MatchExceptionTest t = new MatchExceptionTest();
        t.runTests();
    }

    @Test
    public void testNestedPatternVariablesBytecode() throws Exception {
        String codeStatement = """
                class Test {
                    void test(E e) {
                      switch (e) {
                          case null -> {}
                          case S -> {}
                      };
                    }
                    enum E { S; }
                }""";
        String codeExpression = """
                class Test {
                    int test(E e) {
                      return switch (e) {
                          case S -> 0;
                      };
                    }
                    enum E { S; }
                }""";
        record Setup(boolean hasMatchException, String... options) {
            public String toString() {
                return "Setup[hasMatchException=" + hasMatchException +
                       ", options=" + Arrays.toString(options) + "]";
            }
        }
        Setup[] variants = new Setup[] {
            new Setup(false, "-source", "20"),
            new Setup(false, "-source", JAVA_VERSION),
            new Setup(true, "-source", JAVA_VERSION, "--enable-preview"),
        };
        record Source(String source, boolean needsPreview) {}
        Source[] sources = new Source[] {
            new Source(codeStatement, true),
            new Source(codeExpression, false),
        };
        Path curPath = Path.of(".");
        for (Source source : sources) {
            for (Setup variant : variants) {
                if (source.needsPreview &&
                    !Arrays.asList(variant.options).contains("--enable-preview")) {
                    continue;
                }
                new JavacTask(tb)
                        .options(variant.options)
                        .sources(source.source)
                        .outdir(curPath)
                        .run();

                cf = ClassFile.read(curPath.resolve("Test.class"));
                boolean incompatibleClassChangeErrror = false;
                boolean matchException = false;
                for (CPInfo entry : cf.constant_pool.entries()) {
                    if (entry.getTag() == ConstantPool.CONSTANT_Class) {
                        CONSTANT_Class_info clazz = (CONSTANT_Class_info) entry;
                        incompatibleClassChangeErrror |=
                                "java/lang/IncompatibleClassChangeError".equals(clazz.getName());
                        matchException |= "java/lang/MatchException".equals(clazz.getName());
                    }
                }
                if (variant.hasMatchException) {
                    assertTrue("Expected MatchException (" + variant + ")", matchException);
                    assertTrue("Did not expect IncompatibleClassChangeError (" + variant + ")",
                               !incompatibleClassChangeErrror);
                } else {
                    assertTrue("Did not expect MatchException (" + variant + ")", !matchException);
                    assertTrue("Expected IncompatibleClassChangeError (" + variant + ")",
                               incompatibleClassChangeErrror);
                }
            }
        }
    }

    void assertTrue(String message, boolean b) {
        if (!b) {
            throw new AssertionError(message);
        }
    }
}
