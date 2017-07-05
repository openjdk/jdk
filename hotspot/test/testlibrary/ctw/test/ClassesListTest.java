/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @test ClassesListTest
 * @bug 8012447
 * @library /testlibrary /testlibrary/whitebox /testlibrary/ctw/src
 * @build sun.hotspot.tools.ctw.CompileTheWorld sun.hotspot.WhiteBox ClassesListTest Foo Bar
 * @run main ClassFileInstaller sun.hotspot.WhiteBox Foo Bar
 * @run main ClassesListTest prepare
 * @run main/othervm/timeout=600 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Dsun.hotspot.tools.ctw.logfile=ctw.log sun.hotspot.tools.ctw.CompileTheWorld classes.lst
 * @run main ClassesListTest check ctw.log
 * @summary testing of CompileTheWorld :: list of classes in file
 * @author igor.ignatyev@oracle.com
 */

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class ClassesListTest extends CtwTest {
    private static final String[] SHOULD_CONTAIN
            = {"# list: classes.lst", "Done (4 classes, "};

    private ClassesListTest() {
        super(SHOULD_CONTAIN);
    }

    public static void main(String[] args) throws Exception {
        new ClassesListTest().run(args);
    }

    protected void prepare() throws Exception {
        String path = "classes.lst";
        Files.copy(Paths.get(System.getProperty("test.src"), path),
                Paths.get(path), StandardCopyOption.REPLACE_EXISTING);
    }
}
