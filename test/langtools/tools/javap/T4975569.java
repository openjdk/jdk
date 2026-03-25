/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=NoPreview
 * @bug 4975569 6622215 8034861
 * @summary javap doesn't print new flag bits
 * @modules jdk.jdeps/com.sun.tools.javap
 * @modules java.base/jdk.internal.misc
 * @comment Ensure that that this test is skipped if the test is run on a preview enabled
            VM as the compiled test class has not been forced into preview mode.
            Valhalla affects the outcome.
 * @requires !java.enablePreview
 * @run main T4975569
 */

/*
 * @test id=Preview
 * @bug 4975569 6622215 8034861
 * @summary javap doesn't print new flag bits - Preview
 * @modules jdk.jdeps/com.sun.tools.javap
 * @modules java.base/jdk.internal.misc
 * @enablePreview
 * @compile -XDforcePreview T4975569.java
 * @run main T4975569
 */

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.internal.misc.PreviewFeatures;

public class T4975569 {
    private static final String NEW_LINE = System.getProperty("line.separator");
    private static final String TEST_CLASSES = System.getProperty("test.classes", ".");

    public static void main(String... args) {
        new T4975569().run();
    }

    T4975569() {
        System.currentTimeMillis();
        super(); // Trigger forced preview
    }

    void run() {
        verify(Anno.class.getName(), "flags: \\(0x2600\\) ACC_INTERFACE, ACC_ABSTRACT, ACC_ANNOTATION");
        verify(E.class.getName(), PreviewFeatures.isEnabled()
                ? "flags: \\(0x4030\\) ACC_FINAL, ACC_IDENTITY, ACC_ENUM"
                : "flags: \\(0x4030\\) ACC_FINAL, ACC_SUPER, ACC_ENUM");
        verify(S.class.getName(),    "flags: \\(0x1040\\) ACC_BRIDGE, ACC_SYNTHETIC",
                                     "InnerClasses:\n  static [# =\\w]+; +// ");
        verify(V.class.getName(),    "void m\\(java.lang.String...\\)",
                                     "flags: \\(0x0080\\) ACC_VARARGS");
        verify(Prot.class.getName(), "InnerClasses:\n  protected [# =\\w]+; +// ");
        verify(Priv.class.getName(), new String[]{"-p"},
                                     "InnerClasses:\n  private [# =\\w]+; +// ");

        if (errors > 0)
            throw new Error(errors + " found.");
    }

    void verify(String className, String[] flags, String... expects) {
        String output = javap(className, Arrays.asList(flags));
        for (String expect: expects) {
            Pattern expectPattern = Pattern.compile(expect);
            Matcher matcher = expectPattern.matcher(output);
            if (!matcher.find()) {
                error(expect + " not found");
            }
        }
    }

    void verify(String className, String... expects) {
        verify(className, new String[0], expects);
    }

    int errors;
    void error(String msg) {
        System.err.println(msg.replace("\n", NEW_LINE));
        errors++;
    }

    String javap(String className, List<String> flags) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        List<String> args = new ArrayList<>(flags);
        args.addAll(Arrays.asList("-v", "-classpath", TEST_CLASSES, className));
        int rc = com.sun.tools.javap.Main.run(args.toArray(new String[args.size()]), out);
        out.close();
        String output = sw.toString();
        System.err.println("class " + className);
        System.err.println(output);

        if (rc != 0)
            throw new Error("javap failed. rc=" + rc);
        return output.replaceAll(NEW_LINE, "\n");
    }

    List x() { return null; }

    class V { void m(String... args) { } }
    enum E { e }
    @interface Anno { }
    static class S extends T4975569 {
        ArrayList x() { return null; }
    }

    protected class Prot { }
    private class Priv { int i; }
}
