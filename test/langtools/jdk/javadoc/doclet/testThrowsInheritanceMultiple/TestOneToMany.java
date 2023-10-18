/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8067757 6509045 8295277
 * @library /tools/lib ../../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestOneToMany
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;
import java.nio.file.Paths;

public class TestOneToMany extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestOneToMany();
        tester.runTests();
    }

    private final ToolBox tb = new ToolBox();

    // These tests:
    //
    //   - Use own exceptions to not depend on platform links or a setup with
    //     the no-platform-links option
    //   - Enclose files in a package to exercise a typical source layout and
    //     avoid enumerating separate files in the javadoc command

    @Test
    public void testUncheckedException(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;

                public class MyRuntimeException extends RuntimeException { }
                """, """
                package x;

                public interface I {

                    /**
                     * @throws MyRuntimeException if this
                     * @throws MyRuntimeException if that
                     */
                    void m();
                }
                """, """
                package x;

                public interface I1 extends I {

                    @Override
                    void m() throws MyRuntimeException;
                }
                """, """
                package x;

                public class IImpl implements I {

                    @Override
                    public void m() throws MyRuntimeException { }
                }
                """, """
                package x;

                public class C {

                    /**
                     * @throws MyRuntimeException if this
                     * @throws MyRuntimeException if that
                     */
                    public void m();
                }
                """, """
                package x;

                public class C1 extends C {

                    @Override
                    public void m() throws MyRuntimeException { }
                }
                """);
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "x");
        checkExit(Exit.OK);
        checkOutput("x/IImpl.html", true, """
                <dl class="notes">
                <dt>Specified by:</dt>
                <dd><code><a href="I.html#m()">m</a></code>&nbsp;in interface&nbsp;<code><a href="I.html" title="interface in x">I</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - if this</dd>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - if that</dd>
                </dl>""");
        checkOutput("x/I1.html", true, """
                <dl class="notes">
                <dt>Specified by:</dt>
                <dd><code><a href="I.html#m()">m</a></code>&nbsp;in interface&nbsp;<code><a href="I.html" title="interface in x">I</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - if this</dd>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - if that</dd>
                </dl>""");
        checkOutput("x/C1.html", true, """
                <dl class="notes">
                <dt>Overrides:</dt>
                <dd><code><a href="C.html#m()">m</a></code>&nbsp;in class&nbsp;<code><a href="C.html" title="class in x">C</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - if this</dd>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - if that</dd>
                </dl>""");
    }

    @Test
    public void testUncheckedExceptionWithRedundantThrows(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;

                public class MyRuntimeException extends RuntimeException { }
                """, """
                package x;

                public interface I {

                    /**
                     * @throws MyRuntimeException if this
                     * @throws MyRuntimeException if that
                     */
                    void m() throws MyRuntimeException;
                }
                """, """
                package x;

                public interface I1 extends I {

                    @Override
                    void m() throws MyRuntimeException;
                }
                """, """
                package x;

                public class IImpl implements I {

                    @Override
                    public void m() throws MyRuntimeException { }
                }
                """, """
                package x;

                public class C {

                    /**
                     * @throws MyRuntimeException if this
                     * @throws MyRuntimeException if that
                     */
                    public void m() throws MyRuntimeException;
                }
                """, """
                package x;

                public class C1 extends C {

                    @Override
                    public void m() throws MyRuntimeException { }
                }
                """);
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "x");
        checkExit(Exit.OK);
        checkOutput("x/IImpl.html", true, """
                <dl class="notes">
                <dt>Specified by:</dt>
                <dd><code><a href="I.html#m()">m</a></code>&nbsp;in interface&nbsp;<code><a href="I.html" title="interface in x">I</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - if this</dd>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - if that</dd>
                </dl>""");
        checkOutput("x/I1.html", true, """
                <dl class="notes">
                <dt>Specified by:</dt>
                <dd><code><a href="I.html#m()">m</a></code>&nbsp;in interface&nbsp;<code><a href="I.html" title="interface in x">I</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - if this</dd>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - if that</dd>
                </dl>""");
        checkOutput("x/C1.html", true, """
                <dl class="notes">
                <dt>Overrides:</dt>
                <dd><code><a href="C.html#m()">m</a></code>&nbsp;in class&nbsp;<code><a href="C.html" title="class in x">C</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - if this</dd>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - if that</dd>
                </dl>""");
    }

    @Test
    public void testCheckedException(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;

                public class MyCheckedException extends Exception { }
                """, """
                package x;

                public interface I {

                    /**
                     * @throws MyCheckedException if this
                     * @throws MyCheckedException if that
                     */
                    void m() throws MyCheckedException;
                }
                """, """
                package x;

                public interface I1 extends I {

                    @Override
                    void m() throws MyCheckedException;
                }
                """, """
                package x;

                public class IImpl implements I {

                    @Override
                    public void m() throws MyCheckedException { }
                }
                """, """
                package x;

                public class C {

                    /**
                     * @throws MyCheckedException if this
                     * @throws MyCheckedException if that
                     */
                    public void m()  throws MyCheckedException;
                }
                """, """
                package x;

                public class C1 extends C {

                    @Override
                    public void m() throws MyCheckedException { }
                }
                """);
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "x");
        checkExit(Exit.OK);
        checkOutput("x/IImpl.html", true, """
                <dl class="notes">
                <dt>Specified by:</dt>
                <dd><code><a href="I.html#m()">m</a></code>&nbsp;in interface&nbsp;<code><a href="I.html" title="interface in x">I</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="MyCheckedException.html" title="class in x">MyCheckedException</a></code> - if this</dd>
                <dd><code><a href="MyCheckedException.html" title="class in x">MyCheckedException</a></code> - if that</dd>
                </dl>""");
        checkOutput("x/I1.html", true, """
                <dl class="notes">
                <dt>Specified by:</dt>
                <dd><code><a href="I.html#m()">m</a></code>&nbsp;in interface&nbsp;<code><a href="I.html" title="interface in x">I</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="MyCheckedException.html" title="class in x">MyCheckedException</a></code> - if this</dd>
                <dd><code><a href="MyCheckedException.html" title="class in x">MyCheckedException</a></code> - if that</dd>
                </dl>""");
        checkOutput("x/C1.html", true, """
                <dl class="notes">
                <dt>Overrides:</dt>
                <dd><code><a href="C.html#m()">m</a></code>&nbsp;in class&nbsp;<code><a href="C.html" title="class in x">C</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="MyCheckedException.html" title="class in x">MyCheckedException</a></code> - if this</dd>
                <dd><code><a href="MyCheckedException.html" title="class in x">MyCheckedException</a></code> - if that</dd>
                </dl>""");
    }

    @Test
    public void testSubExceptionDoubleInheritance(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;

                public class MyException extends Exception { }
                """, """
                package x;

                public class MySubException extends MyException { }
                """, """
                package x;

                public interface I {

                    /**
                     * @throws MyException if this
                     * @throws MySubException if that
                     */
                    void m() throws MyException, MySubException;
                }
                """, """
                package x;

                public interface I1 extends I {

                    @Override
                    void m() throws MyException, MySubException;
                }
                """);
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "x");
        checkExit(Exit.OK);
        checkOutput("x/I1.html", true, """
                <dl class="notes">
                <dt>Specified by:</dt>
                <dd><code><a href="I.html#m()">m</a></code>&nbsp;in interface&nbsp;<code><a href="I.html" title="interface in x">I</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="MyException.html" title="class in x">MyException</a></code> - if this</dd>
                <dd><code><a href="MySubException.html" title="class in x">MySubException</a></code> - if that</dd>
                </dl>""");
    }

    @Test
    public void testUncheckedExceptionTag(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;

                public class MyRuntimeException extends RuntimeException { }
                """, """
                package x;

                public interface I {

                    /**
                     * @throws MyRuntimeException if this
                     * @throws MyRuntimeException if that
                     */
                    void m();
                }
                """, """
                package x;

                public interface I1 extends I {

                    /**
                     * @throws MyRuntimeException {@inheritDoc}
                     */
                    @Override
                    void m();
                }
                """, """
                package x;

                public class IImpl implements I {

                    /**
                     * @throws MyRuntimeException {@inheritDoc}
                     */
                    @Override
                    public void m() { }
                }
                """, """
                package x;

                public class C {

                    /**
                     * @throws MyRuntimeException if this
                     * @throws MyRuntimeException if that
                     */
                    public void m();
                }
                """, """
                package x;

                public class C1 extends C {

                    /**
                     * @throws MyRuntimeException {@inheritDoc}
                     */
                    @Override
                    public void m() { }
                }
                """);
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "x");
        checkExit(Exit.OK);
        checkOutput("x/IImpl.html", true, """
                <dl class="notes">
                <dt>Specified by:</dt>
                <dd><code><a href="I.html#m()">m</a></code>&nbsp;in interface&nbsp;<code><a href="I.html" title="interface in x">I</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - if this</dd>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - if that</dd>
                </dl>""");
        checkOutput("x/I1.html", true, """
                <dl class="notes">
                <dt>Specified by:</dt>
                <dd><code><a href="I.html#m()">m</a></code>&nbsp;in interface&nbsp;<code><a href="I.html" title="interface in x">I</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - if this</dd>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - if that</dd>
                </dl>""");
        checkOutput("x/C1.html", true, """
                <dl class="notes">
                <dt>Overrides:</dt>
                <dd><code><a href="C.html#m()">m</a></code>&nbsp;in class&nbsp;<code><a href="C.html" title="class in x">C</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - if this</dd>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - if that</dd>
                </dl>""");
    }

    @Test
    public void testWholeShebang(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;

                public class MyRuntimeException extends RuntimeException { }
                """, """
                package x;

                public interface I {

                    /**
                     * @throws MyRuntimeException always
                     */
                    void m();
                }
                """, """
                package x;

                public interface I1 extends I {

                    /**
                     * @throws MyRuntimeException sometimes
                     * @throws MyRuntimeException rarely
                     * @throws MyRuntimeException "{@inheritDoc}"
                     */
                    @Override
                    void m();
                }
                """, """
                package x;

                public interface I2 extends I1 {

                    /**
                     * @throws MyRuntimeException occasionally
                     * @throws MyRuntimeException {@inheritDoc}
                     * @throws MyRuntimeException frequently
                     */
                    @Override
                    void m() throws MyRuntimeException,
                                    MyRuntimeException,
                                    MyRuntimeException,
                                    MyRuntimeException;
                }
                """);
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "x");
        checkExit(Exit.OK);
        checkOutput("x/I.html", true, """
                <dl class="notes">
                <dt>Throws:</dt>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - always</dd>
                </dl>""");
        checkOutput("x/I1.html", true, """
                <dl class="notes">
                <dt>Specified by:</dt>
                <dd><code><a href="I.html#m()">m</a></code>&nbsp;in interface&nbsp;<code><a href="I.html" title="interface in x">I</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - sometimes</dd>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - rarely</dd>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - "always"</dd>
                </dl>""");
        checkOutput("x/I2.html", true, """
                <dl class="notes">
                <dt>Specified by:</dt>
                <dd><code><a href="I.html#m()">m</a></code>&nbsp;in interface&nbsp;<code><a href="I.html" title="interface in x">I</a></code></dd>
                <dt>Specified by:</dt>
                <dd><code><a href="I1.html#m()">m</a></code>&nbsp;in interface&nbsp;<code><a href="I1.html" title="interface in x">I1</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - occasionally</dd>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - sometimes</dd>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - rarely</dd>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - "always"</dd>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - frequently</dd>
                </dl>""");
    }

    @Test
    public void testError(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;

                public class MyRuntimeException extends RuntimeException { }
                """, """
                package x;

                public interface I {

                    /**
                     * @throws MyRuntimeException sometimes
                     * @throws MyRuntimeException rarely
                     */
                    void m();
                }
                """, """
                package x;

                public interface I1 extends I {

                    /**
                     * @throws MyRuntimeException "{@inheritDoc}"
                     */
                    @Override
                    void m();
                }
                """);
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "x");
        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true, """
                I1.java:6: error: @inheritDoc cannot be used within this tag
                     * @throws MyRuntimeException "{@inheritDoc}"
                       ^
                       """);
    }

    @Test
    public void testDeeperError(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;

                public class MyRuntimeException extends RuntimeException { }
                """, """
                package x;

                public interface I {

                    /**
                     * @throws MyRuntimeException sometimes
                     * @throws MyRuntimeException rarely
                     */
                    void m();
                }
                """, """
                package x;

                public interface I1 extends I {

                    /**
                     * @throws MyRuntimeException "{@inheritDoc}"
                     */
                    @Override
                    void m();
                }
                """, """
                package x;

                public interface I2 extends I1 {

                    /**
                     * @throws MyRuntimeException '{@inheritDoc}'
                     */
                    @Override
                    void m();
                }
                """);
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "x");
        checkExit(Exit.ERROR);
        new OutputChecker(Output.OUT)
                .setExpectFound(true)
                .checkAnyOf(
                        """
                        I2.java:6: error: @inheritDoc cannot be used within this tag
                             * @throws MyRuntimeException '{@inheritDoc}'
                               ^""",
                        """
                        I1.java:6: error: @inheritDoc cannot be used within this tag
                             * @throws MyRuntimeException "{@inheritDoc}"
                               ^""");
    }

    @Test
    public void testFullExpansion(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;

                public class MyRuntimeException extends RuntimeException { }
                """, """
                package x;

                public interface Child extends Parent {

                    /**
                     * @throws MyRuntimeException child 1
                     * @throws MyRuntimeException {@inheritDoc}
                     */
                    @Override void m();
                }
                """, """
                package x;

                public interface Parent extends GrandParent {

                    /**
                     * @throws MyRuntimeException parent 1
                     * @throws MyRuntimeException {@inheritDoc}
                     */
                    @Override void m();
                }
                """, """
                package x;

                public interface GrandParent {

                    /**
                     * @throws MyRuntimeException grandparent 1
                     * @throws MyRuntimeException grandparent 2
                     */
                    void m();
                }
                """);
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "x");
        checkExit(Exit.OK);
        checkOutput("x/Child.html", true, """
                <dl class="notes">
                <dt>Specified by:</dt>
                <dd><code><a href="GrandParent.html#m()">m</a></code>&nbsp;in interface&nbsp;<code><a href="GrandParent.html" title="interface in x">GrandParent</a></code></dd>
                <dt>Specified by:</dt>
                <dd><code><a href="Parent.html#m()">m</a></code>&nbsp;in interface&nbsp;<code><a href="Parent.html" title="interface in x">Parent</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - child 1</dd>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - parent 1</dd>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - grandparent 1</dd>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - grandparent 2</dd>
                </dl>
                </div>
                </section>
                """);
    }

    @Test
    public void testChainEmbeddedInheritDoc(Path base) throws Exception {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package x;

                public class MyRuntimeException extends RuntimeException { }
                """, """
                package x;

                public interface Child extends Parent {

                    /**
                     * @throws MyRuntimeException "{@inheritDoc}"
                     */
                    @Override void m();
                }
                """, """
                package x;

                public interface Parent extends GrandParent {

                    /**
                     * @throws MyRuntimeException '{@inheritDoc}'
                     */
                    @Override void m();
                }
                """, """
                package x;

                public interface GrandParent {

                    /**
                     * @throws MyRuntimeException grandparent
                     */
                    void m();
                }
                """);
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "x");
        checkExit(Exit.OK);
        checkOutput("x/Child.html", true, """
                <dl class="notes">
                <dt>Specified by:</dt>
                <dd><code><a href="GrandParent.html#m()">m</a></code>&nbsp;in interface&nbsp;<code><a href="GrandParent.html" title="interface in x">GrandParent</a></code></dd>
                <dt>Specified by:</dt>
                <dd><code><a href="Parent.html#m()">m</a></code>&nbsp;in interface&nbsp;<code><a href="Parent.html" title="interface in x">Parent</a></code></dd>
                <dt>Throws:</dt>
                <dd><code><a href="MyRuntimeException.html" title="class in x">MyRuntimeException</a></code> - "'grandparent'"</dd>
                </dl>
                </div>
                </section>
                """);
    }
}
