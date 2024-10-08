/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8324774 8327385
 * @summary Add DejaVu web fonts
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestFonts
 */

import java.io.IOException;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestFonts extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestFonts();
        tester.setup().runTests();
    }

    private final ToolBox tb = new ToolBox();
    Path src;

    TestFonts setup() throws IOException {
        src = Path.of("src");
        tb.writeJavaFiles(src, """
                   /**
                    * Simple dummy class.
                    */
                   public class Dummy {}
                   """);
        return this;
    }

    @Test
    public void testFonts(Path base) throws Exception {
        setUseDefaultOptions(false);
        javadoc("-d", base.resolve("out").toString(),
                src.resolve("Dummy.java").toString());
        checkExit(Exit.OK);
        checkOutput("resource-files/fonts/dejavu.css", true,
                """
                    /* DejaVu fonts v2.37 */""",
                """
                    @font-face {
                      font-family: 'DejaVu Sans Mono';
                      src: url('DejaVuLGCSansMono.woff2') format('woff2'),
                           url('DejaVuLGCSansMono.woff') format('woff');
                      font-weight: normal;
                      font-style: normal;
                    }""");
        checkFiles(true,
                "resource-files/fonts/DejaVuLGCSans-Bold.woff",
                "resource-files/fonts/DejaVuLGCSans-Bold.woff2",
                "resource-files/fonts/DejaVuLGCSans-BoldOblique.woff",
                "resource-files/fonts/DejaVuLGCSans-BoldOblique.woff2",
                "resource-files/fonts/DejaVuLGCSans-Oblique.woff",
                "resource-files/fonts/DejaVuLGCSans-Oblique.woff2",
                "resource-files/fonts/DejaVuLGCSans.woff",
                "resource-files/fonts/DejaVuLGCSans.woff2",
                "resource-files/fonts/DejaVuLGCSansMono-Bold.woff",
                "resource-files/fonts/DejaVuLGCSansMono-Bold.woff2",
                "resource-files/fonts/DejaVuLGCSansMono-BoldOblique.woff",
                "resource-files/fonts/DejaVuLGCSansMono-BoldOblique.woff2",
                "resource-files/fonts/DejaVuLGCSansMono-Oblique.woff",
                "resource-files/fonts/DejaVuLGCSansMono-Oblique.woff2",
                "resource-files/fonts/DejaVuLGCSansMono.woff",
                "resource-files/fonts/DejaVuLGCSansMono.woff2",
                "resource-files/fonts/DejaVuLGCSerif-Bold.woff",
                "resource-files/fonts/DejaVuLGCSerif-Bold.woff2",
                "resource-files/fonts/DejaVuLGCSerif-BoldItalic.woff",
                "resource-files/fonts/DejaVuLGCSerif-BoldItalic.woff2",
                "resource-files/fonts/DejaVuLGCSerif-Italic.woff",
                "resource-files/fonts/DejaVuLGCSerif-Italic.woff2",
                "resource-files/fonts/DejaVuLGCSerif.woff",
                "resource-files/fonts/DejaVuLGCSerif.woff2");
    }

    @Test
    public void testNoFontsOption(Path base) throws Exception {
        setUseDefaultOptions(false);
        javadoc("-d", base.resolve("out").toString(),
                "--no-fonts",
                src.resolve("Dummy.java").toString());
        checkExit(Exit.OK);
        checkFiles(true, "resource-files/copy.svg",
                "resource-files/glass.png",
                "resource-files/jquery-ui.min.css",
                "resource-files/link.svg",
                "resource-files/stylesheet.css",
                "resource-files/x.png");
        checkFiles(false, "resource-files/fonts");
    }
}
