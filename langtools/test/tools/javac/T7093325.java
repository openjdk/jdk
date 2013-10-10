/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7093325 8006694
 * @summary Redundant entry in bytecode exception table
 *  temporarily workaround combo tests are causing time out in several platforms
 * @library lib
 * @build JavacTestingAbstractThreadedTest
 * @run main/othervm T7093325
 */

// use /othervm to avoid jtreg timeout issues (CODETOOLS-7900047)
// see JDK-8006746

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import com.sun.source.util.JavacTask;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.ConstantPool.*;
import com.sun.tools.classfile.Method;

public class T7093325
    extends JavacTestingAbstractThreadedTest
    implements Runnable {

    enum StatementKind {
        THROW("throw new RuntimeException();", false, false),
        RETURN_NONEMPTY("System.out.println(); return;", true, false),
        RETURN_EMPTY("return;", true, true),
        APPLY("System.out.println();", true, false);

        String stmt;
        boolean canInline;
        boolean empty;

        private StatementKind(String stmt, boolean canInline, boolean empty) {
            this.stmt = stmt;
            this.canInline = canInline;
            this.empty = empty;
        }
    }

    enum CatchArity {
        NONE(""),
        ONE("catch (A a) { #S1 }"),
        TWO("catch (B b) { #S2 }"),
        THREE("catch (C c) { #S3 }"),
        FOUR("catch (D d) { #S4 }");

        String catchStr;

        private CatchArity(String catchStr) {
            this.catchStr = catchStr;
        }

        String catchers() {
            if (this.ordinal() == 0) {
                return catchStr;
            } else {
                return CatchArity.values()[this.ordinal() - 1].catchers() +
                        catchStr;
            }
        }
    }

    public static void main(String... args) throws Exception {
        for (CatchArity ca : CatchArity.values()) {
            for (StatementKind stmt0 : StatementKind.values()) {
                if (ca.ordinal() == 0) {
                    pool.execute(new T7093325(ca, stmt0));
                    continue;
                }
                for (StatementKind stmt1 : StatementKind.values()) {
                    if (ca.ordinal() == 1) {
                        pool.execute(new T7093325(ca, stmt0, stmt1));
                        continue;
                    }
                    for (StatementKind stmt2 : StatementKind.values()) {
                        if (ca.ordinal() == 2) {
                            pool.execute(new T7093325(ca, stmt0, stmt1, stmt2));
                            continue;
                        }
                        for (StatementKind stmt3 : StatementKind.values()) {
                            if (ca.ordinal() == 3) {
                                pool.execute(
                                    new T7093325(ca, stmt0, stmt1, stmt2, stmt3));
                                continue;
                            }
                            for (StatementKind stmt4 : StatementKind.values()) {
                                if (ca.ordinal() == 4) {
                                    pool.execute(
                                        new T7093325(ca, stmt0, stmt1,
                                                     stmt2, stmt3, stmt4));
                                    continue;
                                }
                                for (StatementKind stmt5 : StatementKind.values()) {
                                    pool.execute(
                                        new T7093325(ca, stmt0, stmt1, stmt2,
                                                     stmt3, stmt4, stmt5));
                                }
                            }
                        }
                    }
                }
            }
        }

        checkAfterExec();
    }

    /** instance decls **/

    CatchArity ca;
    StatementKind[] stmts;

    public T7093325(CatchArity ca, StatementKind... stmts) {
        this.ca = ca;
        this.stmts = stmts;
    }

    @Override
    public void run() {
        int id = checkCount.incrementAndGet();
        final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        JavaSource source = new JavaSource(id);
        JavacTask ct = (JavacTask)tool.getTask(null, fm.get(), null,
                null, null, Arrays.asList(source));
        ct.call();
        verifyBytecode(source, id);
    }

    void verifyBytecode(JavaSource source, int id) {
        boolean lastInlined = false;
        boolean hasCode = false;
        int gapsCount = 0;
        for (int i = 0; i < stmts.length ; i++) {
            lastInlined = stmts[i].canInline;
            hasCode = hasCode || !stmts[i].empty;
            if (lastInlined && hasCode) {
                hasCode = false;
                gapsCount++;
            }
        }
        if (!lastInlined) {
            gapsCount++;
        }

        File compiledTest = new File(String.format("Test%s.class", id));
        try {
            ClassFile cf = ClassFile.read(compiledTest);
            if (cf == null) {
                throw new Error("Classfile not found: " +
                                compiledTest.getName());
            }

            Method test_method = null;
            for (Method m : cf.methods) {
                if (m.getName(cf.constant_pool).equals("test")) {
                    test_method = m;
                    break;
                }
            }

            if (test_method == null) {
                throw new Error("Method test() not found in class Test");
            }

            Code_attribute code = null;
            for (Attribute a : test_method.attributes) {
                if (a.getName(cf.constant_pool).equals(Attribute.Code)) {
                    code = (Code_attribute)a;
                    break;
                }
            }

            if (code == null) {
                throw new Error("Code attribute not found in method test()");
            }

            int actualGapsCount = 0;
            for (int i = 0; i < code.exception_table_length ; i++) {
                int catchType = code.exception_table[i].catch_type;
                if (catchType == 0) { //any
                    actualGapsCount++;
                }
            }

            if (actualGapsCount != gapsCount) {
                throw new Error("Bad exception table for test()\n" +
                            "expected gaps: " + gapsCount + "\n" +
                            "found gaps: " + actualGapsCount + "\n" +
                            source);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new Error("error reading " + compiledTest +": " + e);
        }

    }

    class JavaSource extends SimpleJavaFileObject {

        static final String source_template =
                "class A extends RuntimeException {} \n" +
                "class B extends RuntimeException {} \n" +
                "class C extends RuntimeException {} \n" +
                "class D extends RuntimeException {} \n" +
                "class E extends RuntimeException {} \n" +
                "class Test#ID {\n" +
                "   void test() {\n" +
                "   try { #S0 } #C finally { System.out.println(); }\n" +
                "   }\n" +
                "}";

        String source;

        public JavaSource(int id) {
            super(URI.create(String.format("myfo:/Test%s.java", id)),
                  JavaFileObject.Kind.SOURCE);
            source = source_template.replace("#C", ca.catchers());
            source = source.replace("#S0", stmts[0].stmt);
            source = source.replace("#ID", String.valueOf(id));
            for (int i = 1; i < ca.ordinal() + 1; i++) {
                source = source.replace("#S" + i, stmts[i].stmt);
            }
        }

        @Override
        public String toString() {
            return source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

}
