/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8263043
 * @summary Add test to verify order of tag output
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestTagOrder
 */

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

/**
 * Tests the order of the output of block tags in the generated output.
 * There is a default order, embodies in the order of declaration of tags in
 * {@code Tagl;etManager}, but this can be overridden on the command line by
 * specifying {@code -tag} options in the desired order.
 */
public class TestTagOrder extends JavadocTester {
    public static void main(String... args) throws Exception {
        TestTagOrder tester = new TestTagOrder();
        tester.runTests(m -> new Object[] { Path.of(m.getName()) });
    }

    ToolBox tb = new ToolBox();
    Path src = Path.of("src");
    Map<String, String> expect = new LinkedHashMap<>();

    TestTagOrder() throws IOException {
        tb.writeJavaFiles(src,
                """
                    package p;
                    /** Class C. */
                    public class C {
                        /**
                         * This is method m.
                         * @param p1 first parameter
                         * @param p2 second parameter
                         * @return zero
                         * @throws IllegalArgumentException well, never
                         * @since 1.0
                         * @see <a href="http://example.com">example</a>
                         */
                        public int m(int p1, int p2) throws IllegalArgumentException { 
                            return 0; 
                        }
                    }
                    """);

        // The following adds map entries in the default order of appearance in the output.
        // Note that the list is not otherwise ordered, such as alphabetically.

        expect.put("@param", """
                <dt>Parameters:</dt>
                <dd><code>p1</code> - first parameter</dd>
                <dd><code>p2</code> - second parameter</dd>
                """);

        expect.put("@return", """
                <dt>Returns:</dt>
                <dd>zero</dd>
                """);

        expect.put("@throws", """
                <dt>Throws:</dt>
                <dd><code>java.lang.IllegalArgumentException</code> - well, never</dd>
                """);

        expect.put("@since", """
                <dt>Since:</dt>
                <dd>1.0</dd>
                """);

        expect.put("@see", """
                <dt>See Also:</dt>
                <dd><a href="http://example.com">example</a></dd>
                """);
    }

    @Test
    public void testDefault(Path base) {
        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "--no-platform-links",
                "p");
        checkExit(Exit.OK);

        checkOutput("p/C.html", true,
                "<dl class=\"notes\">\n"
                + String.join("", expect.values())
                + "</dl>");
    }

    @Test
    public void testAlpha(Path base) {
        List<String> args = new ArrayList<>();
        args.addAll(List.of(
                "-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "--no-platform-links"));

        SortedMap<String, String> e = new TreeMap<>(Comparator.naturalOrder());
        e.putAll(expect);
        e.keySet().forEach(t -> { args.add("-tag"); args.add(t.substring(1)); });
        args.add("p");

        javadoc(args.toArray(new String[args.size()]));
        checkExit(Exit.OK);

        checkOutput("p/C.html", true,
                "<dl class=\"notes\">\n"
                        + String.join("", e.values())
                        + "</dl>");
    }

    @Test
    public void testReverse(Path base) {
        List<String> args = new ArrayList<>();
        args.addAll(List.of(
                "-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "--no-platform-links"));

        SortedMap<String, String> e = new TreeMap<>(Comparator.reverseOrder());
        e.putAll(expect);
        e.keySet().forEach(t -> { args.add("-tag"); args.add(t.substring(1)); });
        args.add("p");

        javadoc(args.toArray(new String[args.size()]));
        checkExit(Exit.OK);

        checkOutput("p/C.html", true,
                "<dl class=\"notes\">\n"
                        + String.join("", e.values())
                        + "</dl>");
    }
}