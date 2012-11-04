/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Automatic test for checking correctness of default resolution
 */

import shapegen.*;

import com.sun.source.util.JavacTask;

import java.net.URI;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class FDTest {

    enum TestKind {
        POSITIVE,
        NEGATIVE;

        Collection<Hierarchy> getHierarchy(HierarchyGenerator hg) {
            return this == POSITIVE ?
                    hg.getOK() : hg.getErr();
        }
    }

    public static void main(String[] args) throws Exception {
        //create default shared JavaCompiler - reused across multiple compilations
        JavaCompiler comp = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fm = comp.getStandardFileManager(null, null, null);

        HierarchyGenerator hg = new HierarchyGenerator();
        for (TestKind tk : TestKind.values()) {
            for (Hierarchy hs : tk.getHierarchy(hg)) {
                new FDTest(tk, hs).run(comp, fm);
            }
        }
    }

    TestKind tk;
    Hierarchy hs;
    DefenderTestSource source;
    DiagnosticChecker diagChecker;

    FDTest(TestKind tk, Hierarchy hs) {
        this.tk = tk;
        this.hs = hs;
        this.source = new DefenderTestSource();
        this.diagChecker = new DiagnosticChecker();
    }

    void run(JavaCompiler tool, StandardJavaFileManager fm) throws Exception {
        JavacTask ct = (JavacTask)tool.getTask(null, fm, diagChecker,
                Arrays.asList("-XDallowDefaultMethods"), null, Arrays.asList(source));
        try {
            ct.analyze();
        } catch (Throwable ex) {
            throw new AssertionError("Error thrown when analyzing the following source:\n" + source.getCharContent(true));
        }
        check();
    }

    void check() {
        boolean errorExpected = tk == TestKind.NEGATIVE;
        if (errorExpected != diagChecker.errorFound) {
            throw new AssertionError("problem in source: \n" +
                    "\nerror found = " + diagChecker.errorFound +
                    "\nerror expected = " + errorExpected +
                    "\n" + dumpHierarchy() +
                    "\n" + source.getCharContent(true));
        }
    }

    String dumpHierarchy() {
        StringBuilder buf = new StringBuilder();
        buf.append("root = " + hs.root + "\n");
        for (ClassCase cc : hs.all) {
            buf.append("  class name = " + cc.getName() + "\n");
            buf.append("    class OK = " + cc.get_OK() + "\n");
            buf.append("    prov = " + cc.get_mprov() + "\n");

        }
        return buf.toString();
    }

    class DefenderTestSource extends SimpleJavaFileObject {

        String source;

        public DefenderTestSource() {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            StringBuilder buf = new StringBuilder();
            List<ClassCase> defaultRef = new ArrayList<>();
            for (ClassCase cc : hs.all) {
                hs.genClassDef(buf, cc, null, defaultRef);
            }
            source = buf.toString();
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    static class DiagnosticChecker implements javax.tools.DiagnosticListener<JavaFileObject> {

        boolean errorFound;

        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errorFound = true;
            }
        }
    }
}
