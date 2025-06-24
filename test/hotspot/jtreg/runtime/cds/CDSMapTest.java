/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8308903
 * @summary Test the contents of -Xlog:aot+map
 * @requires vm.cds
 * @library /test/lib
 * @run driver CDSMapTest
 */

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.Platform;
import java.util.ArrayList;

public class CDSMapTest {
    public static void main(String[] args) throws Exception {
        doTest(false);

        if (Platform.is64bit()) {
            // There's no oop/klass compression on 32-bit.
            doTest(true);
        }
    }

    public static void doTest(boolean compressed) throws Exception {
        ArrayList<String> dumpArgs = new ArrayList<>();

        // Use the same heap size as make/Images.gmk
        dumpArgs.add("-Xmx128M");

        if (Platform.is64bit()) {
            // These options are available only on 64-bit.
            String sign = (compressed) ?  "+" : "-";
            dumpArgs.add("-XX:" + sign + "UseCompressedOops");
        }

        dump(dumpArgs);
    }

    static int id = 0;
    static void dump(ArrayList<String> args, String... more) throws Exception {
        String logName = "SharedArchiveFile" + (id++);
        String archiveName = logName + ".jsa";
        String mapName = logName + ".map";
        CDSOptions opts = (new CDSOptions())
            .addPrefix("-Xlog:cds=debug")
            .addPrefix("-Xlog:aot+map=debug,aot+map+oops=trace:file=" + mapName + ":none:filesize=0")
            .setArchiveName(archiveName)
            .addSuffix(args)
            .addSuffix(more);
        CDSTestUtils.createArchiveAndCheck(opts);

        CDSMapReader.MapFile mapFile = CDSMapReader.read(mapName);
        CDSMapReader.validate(mapFile);
    }
}
