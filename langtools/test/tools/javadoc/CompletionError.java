/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8135307
 * @summary Check that CompletionFailures for missing classes are not incorrectly passed to
 *          the javadoc API clients.
 * @library /tools/lib
 * @modules jdk.javadoc com.sun.tools.javac.api
 *          jdk.jdeps/com.sun.tools.javap
 * @run main CompletionError
 */

import java.io.File;

import com.sun.javadoc.*;
import com.sun.tools.javadoc.Main;

public class CompletionError extends Doclet
{
    private static final String template =
            "public class CompletionErrorAuxiliary #extends CompletionErrorMissing# #implements CompletionErrorIntfMissing# {" +
            "   #public CompletionErrorMissing tf;#" +
            "   #public CompletionErrorMissing tm() { return null; }#" +
            "   #public void tm(CompletionErrorMissing m) {}#" +
            "   #public void tm() throws CompletionErrorExcMissing {}#" +
            "   #public <T extends CompletionErrorMissing> void tm() {}#" +
            "   public String toString() { return null; }" +
            "}";

    public static void main(String[] args) throws Exception {
        String[] templateParts = template.split("#");
        int sources = templateParts.length / 2;
        for (int source = 0; source < sources; source++) {
            StringBuilder testSource = new StringBuilder();
            for (int i = 0; i < templateParts.length; i += 2) {
                testSource.append(templateParts[i]);
                if (i == 2 * source) {
                    testSource.append(templateParts[i + 1]);
                }
            }
            test = 0;
            testsDone = false;
            while (!testsDone) {
                ToolBox tb = new ToolBox();
                tb.new JavacTask()
                  .sources(testSource.toString(),
                           "public class CompletionErrorMissing {}",
                           "public interface CompletionErrorIntfMissing {}",
                           "public class CompletionErrorExcMissing extends Exception {}")
                  .outdir(".")
                  .run()
                  .writeAll();
                tb.deleteFiles("CompletionErrorMissing.class", "CompletionErrorIntfMissing.class", "CompletionErrorExcMissing.class");
                // run javadoc:
                if (Main.execute("javadoc", "CompletionError", CompletionError.class.getClassLoader(),
                                 "-classpath", ".",
                                 System.getProperty("test.src", ".") + File.separatorChar + "CompletionError.java") != 0)
                    throw new Error();
            }
        }
    }

    private static int test;
    private static boolean testsDone;

    public static boolean start(com.sun.javadoc.RootDoc root) {
        ClassDoc aux = root.classNamed("CompletionErrorAuxiliary");
        if (aux == null)
            throw new AssertionError("Cannot find CompletionErrorAuxiliary");

        FieldDoc tf = findField(aux, "tf");
        MethodDoc tm = findMethod(aux, "tm");
        MethodDoc cm = findMethod(aux, "toString");
        switch (test) {
            case 0: aux.superclass(); break;
            case 1: aux.superclassType(); break;
            case 2: aux.interfaces(); break;
            case 3: aux.interfaceTypes(); break;
            case 4: if (tf != null) tf.type(); break;
            case 5: if (tm != null) tm.overriddenClass(); break;
            case 6: if (tm != null) tm.overriddenMethod(); break;
            case 7: if (tm != null) tm.overriddenType(); break;
            case 8:
                if (tm != null) {
                    for (Parameter p : tm.parameters()) {
                        p.type();
                    }
                }
                break;
            case 9: if (tm != null) tm.receiverType(); break;
            case 10: if (tm != null) tm.returnType(); break;
            case 11: if (tm != null) tm.thrownExceptionTypes(); break;
            case 12: if (tm != null) tm.thrownExceptions(); break;
            case 13:
                if (tm != null) {
                    for (TypeVariable tv : tm.typeParameters()) {
                        tv.bounds();
                    }
                }
                break;
            case 14: if (cm != null) cm.overriddenClass(); break;
            case 15: if (cm != null) cm.overriddenMethod(); break;
            case 16: if (cm != null) cm.overriddenType(); testsDone = true; break;
            default:
                throw new IllegalStateException("Unrecognized test!");
        }
        test++;
        return true;
    }

    private static MethodDoc findMethod(ClassDoc cd, String name) {
        for (MethodDoc m : cd.methods()) {
            if (name.equals(m.name()))
                return m;
        }

        return null;
    }

    private static FieldDoc findField(ClassDoc cd, String name) {
        for (FieldDoc m : cd.fields()) {
            if (name.equals(m.name()))
                return m;
        }

        return null;
    }
}
