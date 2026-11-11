/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=dump
 * @bug 8385976
 * @summary AOT heap dumping and map logging work with ZGC and ZVerifyOops
 * @requires vm.cds & vm.cds.write.archived.java.heap & vm.gc.Z & vm.flagless
 * @library /test/lib
 * @build AOTMapTest AOTMapReader
 * @run driver/timeout=240 TestAOTMapWithZGC dump
 */

/*
 * @test id=load
 * @bug 8385976
 * @summary AOT heap map logging during archive loading works with ZGC and ZVerifyOops
 * @requires vm.cds & vm.cds.write.archived.java.heap & vm.gc.Z & vm.flagless
 * @library /test/lib
 * @build AOTMapTest AOTMapReader
 * @run driver/timeout=240 TestAOTMapWithZGC load
 */

import java.util.ArrayList;
import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;

public class TestAOTMapWithZGC {
    private static ArrayList<String> options(boolean compact, boolean verify) {
        ArrayList<String> options = new ArrayList<>();
        options.add("-Xmx128m");
        options.add("-XX:+UnlockDiagnosticVMOptions");
        options.add("-XX:+UseZGC");
        options.add("-XX:-UseCompressedOops");
        options.add("-XX:+AOTStreamableObjects");
        options.add("-XX:" + (compact ? "+" : "-") + "UseCompactObjectHeaders");
        if (Platform.isDebugBuild()) {
            options.add("-XX:" + (verify ? "+" : "-") + "ZVerifyOops");
        }
        return options;
    }

    private static void checkHeapObjects(String mapName) throws Exception {
        AOTMapReader.MapFile mapFile = AOTMapReader.read(mapName);
        Asserts.assertGT(mapFile.heapObjectCount(), 0, "No archived heap objects in " + mapName);
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        if (!mode.equals("dump") && !mode.equals("load")) {
            throw new IllegalArgumentException("Unexpected mode: " + mode);
        }
        for (boolean compact : new boolean[] { false, true }) {
            System.out.println("mode=" + mode + " compact=" + compact +
                               " verify=" + Platform.isDebugBuild());
            if (mode.equals("dump")) {
                String archive = AOTMapTest.dump(options(compact, true));
                // AOTMapTest uses the same stem for the archive and its dump map.
                checkHeapObjects(archive.replace(".jsa", ".map"));
            } else {
                // Do not let the dump failure hide the separate archive-loading path.
                String archive = AOTMapTest.dump(options(compact, false));
                checkHeapObjects(archive.replace(".jsa", ".map"));
                // Keep the archive mapping available for map logging.
                ArrayList<String> loadOptions = options(compact, true);
                loadOptions.add("-XX:-AOTEagerlyLoadObjects");
                AOTMapTest.exec(loadOptions, archive);
                checkHeapObjects(archive + ".exec.map");
            }
        }
    }
}
