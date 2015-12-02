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
 * @bug 8130880
 * @library lib
 * @run main sampleapi.SampleApiDefaultRunner -o:out/src
 * @run main SampleApiTest
 */

import com.sun.tools.javadoc.*;
import java.nio.file.Paths;

public class SampleApiTest {

    public static void main(String... args) {

        // html4
        Main.execute(
            new String[] {
                "-d", "out/doc.html4",
                "-verbose",
                "-private",
                "-use",
                "-splitindex",
                "-linksource",
                "-html4",
                "-javafx",
                "-windowtitle", "SampleAPI",
                "-sourcepath", "out/src",
                "sampleapi.simple",
                "sampleapi.tiny",
                "sampleapi.fx"
            });

        // html5
        Main.execute(
            new String[] {
                "-d", "out/doc.html5",
                "-verbose",
                "-private",
                "-use",
                "-splitindex",
                "-linksource",
                "-html5",
                "-javafx",
                "-windowtitle", "SampleAPI",
                "-sourcepath", "out/src",
                "sampleapi.simple",
                "sampleapi.tiny",
                "sampleapi.fx"
            });
    }
}
