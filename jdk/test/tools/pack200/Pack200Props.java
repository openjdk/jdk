/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6575373 6969063
 * @summary verify default properties of the packer/unpacker and segment limit
 * @compile -XDignore.symbol.file Utils.java Pack200Props.java
 * @run main Pack200Props
 * @author ksrini
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Pack200;
import java.util.jar.Pack200.Packer;

/*
 * Run this against a large jar file, by default the packer should generate only
 * one segment, parse the output of the packer to verify if this is indeed true.
 */

public class Pack200Props {

    public static void main(String... args) throws IOException {
        verifyDefaults();
        File out = new File("test" + Utils.PACK_FILE_EXT);
        out.delete();
        verifySegmentLimit(out);
        Utils.cleanup();
    }

    static void verifySegmentLimit(File outFile) throws IOException {
        File sdkHome = Utils.JavaSDK;
        File testJar = Utils.createRtJar();

        System.out.println("using pack200: " + Utils.getPack200Cmd());

        List<String> cmdsList = new ArrayList<>();
        cmdsList.add(Utils.getPack200Cmd());
        cmdsList.add("-J-Xshare:off");
        cmdsList.add("-J-Xmx1280m");
        cmdsList.add("--effort=1");
        cmdsList.add("--verbose");
        cmdsList.add("--no-gzip");
        cmdsList.add(outFile.getName());
        cmdsList.add(testJar.getAbsolutePath());
        List<String> outList = Utils.runExec(cmdsList);

        int count = 0;
        for (String line : outList) {
            System.out.println(line);
            if (line.matches(".*Transmitted.*files of.*input bytes in a segment of.*bytes")) {
                count++;
            }
        }
        if (count == 0) {
            throw new RuntimeException("no segments or no output ????");
        } else if (count > 1) {
            throw new RuntimeException("multiple segments detected, expected 1");
        }
    }

    private static void verifyDefaults() {
        Map<String, String> expectedDefaults = new HashMap<>();
        Packer p = Pack200.newPacker();
        expectedDefaults.put("com.sun.java.util.jar.pack.disable.native",
                p.FALSE);
        expectedDefaults.put("com.sun.java.util.jar.pack.verbose", "0");
        expectedDefaults.put(p.CLASS_ATTRIBUTE_PFX + "CompilationID", "RUH");
        expectedDefaults.put(p.CLASS_ATTRIBUTE_PFX + "SourceID", "RUH");
        expectedDefaults.put(p.CODE_ATTRIBUTE_PFX + "CharacterRangeTable",
                "NH[PHPOHIIH]");
        expectedDefaults.put(p.CODE_ATTRIBUTE_PFX + "CoverageTable",
                "NH[PHHII]");
        expectedDefaults.put(p.DEFLATE_HINT, p.KEEP);
        expectedDefaults.put(p.EFFORT, "5");
        expectedDefaults.put(p.KEEP_FILE_ORDER, p.TRUE);
        expectedDefaults.put(p.MODIFICATION_TIME, p.KEEP);
        expectedDefaults.put(p.SEGMENT_LIMIT, "-1");
        expectedDefaults.put(p.UNKNOWN_ATTRIBUTE, p.PASS);

        Map<String, String> props = p.properties();
        int errors = 0;
        for (String key : expectedDefaults.keySet()) {
            String def = expectedDefaults.get(key);
            String x = props.get(key);
            if (x == null) {
                System.out.println("Error: key not found:" + key);
                errors++;
            } else {
                if (!def.equals(x)) {
                    System.out.println("Error: key " + key
                            + "\n  value expected: " + def
                            + "\n  value obtained: " + x);
                    errors++;
                }
            }
        }
        if (errors > 0) {
            throw new RuntimeException(errors +
                    " error(s) encountered in default properties verification");
        }
    }
}

