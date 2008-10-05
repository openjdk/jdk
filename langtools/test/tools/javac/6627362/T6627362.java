/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6627362
 * @summary javac generates code that uses array.clone,
 *          which is not available on JavaCard
 */

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

public class T6627362 {
    static String testSrc = System.getProperty("test.src", ".");

    public static void main(String... args) throws Exception {
        new T6627362().run();
    }

    public void run() throws Exception {
        testStandard();
        testNoClone();
        if (errors > 0)
            throw new Error(errors + " test cases failed");
    }

    void testStandard() throws Exception {
        // compile and disassemble E.java, check for reference to Object.clone()
        File x = new File(testSrc, "x");
        String[] jcArgs = { "-d", ".",
                            new File(x, "E.java").getPath() };
        compile(jcArgs);

        String[] jpArgs = { "-classpath", ".", "-c", "E" };

        StringWriter sw = new StringWriter();
        javap(new PrintWriter(sw, true), jpArgs);
        check(sw.toString(), "Method \"[LE;\".clone:()Ljava/lang/Object;");
        callValues();
    }

    void testNoClone() throws Exception {
        // compile and disassemble E.java, using modified Object.java,
        // check for reference to System.arraycopy
        File x = new File(testSrc, "x");
        String[] jcArgs = { "-d", ".",
                            new File(x, "E.java").getPath(),
                            new File(x, "Object.java").getPath()};
        compile(jcArgs);

        String[] jpArgs = { "-classpath", ".", "-c", "E" };

        StringWriter sw = new StringWriter();
        javap(new PrintWriter(sw, true), jpArgs);
        check(sw.toString(), "//Method java/lang/System.arraycopy:(Ljava/lang/Object;ILjava/lang/Object;II)V");
        callValues();
    }

    void compile(String... args) {
        int rc = com.sun.tools.javac.Main.compile(args);
        if (rc != 0)
            throw new Error("javac failed: " + Arrays.asList(args) + ": " + rc);
    }

    void javap(PrintWriter out, String... args) throws Exception {
        // for now, we have to exec javap
        File javaHome = new File(System.getProperty("java.home"));
        if (javaHome.getName().equals("jre"))
            javaHome = javaHome.getParentFile();
        File javap = new File(new File(javaHome, "bin"), "javap");
        String[] cmd = new String[args.length + 1];
        cmd[0] = javap.getPath();
        System.arraycopy(args, 0, cmd, 1, args.length);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.getOutputStream().close();
        BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = in.readLine()) != null)
            out.println(line);
        int rc = p.waitFor();
        if (rc != 0)
            throw new Error("javap failed: " + Arrays.asList(args) + ": " + rc);
    }

    void check(String s, String require) {
        if (s.indexOf(require) == -1) {
            System.err.println("Can't find " + require);
            errors++;
        }
    }

    void callValues() {
        try {
            File dot = new File(System.getProperty("user.dir"));
            ClassLoader cl = new URLClassLoader(new URL[] { dot.toURL() });
            Class<?> e_class = cl.loadClass("E");
            Method m = e_class.getMethod("values", new Class[] { });
            //System.err.println(m);
            Object o = m.invoke(null, (Object[]) null);
            List<Object> v = Arrays.asList((Object[]) o);
            if (!v.toString().equals("[a, b, c]"))
                throw new Error("unexpected result for E.values(): " + v);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    int errors;
}

