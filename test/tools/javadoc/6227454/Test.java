/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6227454
 * @summary package.html and overview.html may not be read fully
 */

import java.io.*;

import com.sun.javadoc.Doclet;
import com.sun.javadoc.RootDoc;

public class Test extends Doclet {
    public static void main(String... args) throws Exception {
        new Test().run();
    }

    void run() throws Exception {
        test("<html><body>ABC      XYZ</body></html>");
        test("<html><body>ABC      XYZ</BODY></html>");
        test("<html><BODY>ABC      XYZ</body></html>");
        test("<html><BODY>ABC      XYZ</BODY></html>");
        test("<html><BoDy>ABC      XYZ</bOdY></html>");
        test("<html>      ABC      XYZ</bOdY></html>", "Body tag missing from HTML");
        test("<html><body>ABC      XYZ       </html>", "Close body tag missing from HTML");
        test("<html>      ABC      XYZ       </html>", "Body tag missing from HTML");
        test("<html><body>ABC" + bigText(8192, 40) + "XYZ</body></html>");

        if (errors > 0)
            throw new Exception(errors + " errors occurred");
    }

    void test(String text) throws IOException {
        test(text, null);
    }

    void test(String text, String expectError) throws IOException {
        testNum++;
        System.err.println("test " + testNum);
        File file = writeFile("overview" + testNum + ".html", text);
        String thisClassName = Test.class.getName();
        File testSrc = new File(System.getProperty("test.src"));
        String[] args = {
            "-bootclasspath",
                System.getProperty("java.class.path")
                + File.pathSeparator
                + System.getProperty("sun.boot.class.path"),
            "-classpath", ".",
            "-package",
            "-overview", file.getPath(),
            new File(testSrc, thisClassName + ".java").getPath()
        };

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        int rc = com.sun.tools.javadoc.Main.execute(
                "javadoc",
                pw, pw, pw,
                thisClassName,
                args);
        pw.close();
        String out = sw.toString();
        if (!out.isEmpty())
            System.err.println(out);
        System.err.println("javadoc exit: rc=" + rc);

        if (expectError == null) {
            if (rc != 0)
                error("unexpected exit from javadoc; rc:" + rc);
        } else {
            if (!out.contains(expectError))
                error("expected error text not found: " + expectError);
        }
    }

    String bigText(int lines, int lineLength) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lineLength; i++)
            sb.append(String.valueOf(i % 10));
        sb.append("\n");
        String line = sb.toString();
        sb.setLength(0);
        for (int i = 0; i < lines; i++)
            sb.append(line);
        return sb.toString();
    }

    File writeFile(String path, String body) throws IOException {
        File f = new File(path);
        FileWriter out = new FileWriter(f);
        try {
            out.write(body);
        } finally {
            out.close();
        }
        return f;
    }

    void error(String msg) {
        System.err.println("Error: " + msg);
        errors++;
    }

    int testNum;
    int errors;

    public static boolean start(RootDoc root) {
        String text = root.commentText();
        if (text.length() < 64)
            System.err.println("text: '" + text + "'");
        else
            System.err.println("text: '"
                    + text.substring(0, 20)
                    + "..."
                    + text.substring(text.length() - 20)
                    + "'");
        return text.startsWith("ABC") && text.endsWith("XYZ");
    }
}
