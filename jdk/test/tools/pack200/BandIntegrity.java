/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @summary test ensures the proper sequencing of bands, dump bands as well.
 * @compile -XDignore.symbol.file Utils.java BandIntegrity.java
 * @run main BandIntegrity
 * @key intermittent
 * @author ksrini
 */
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/*
 * This makes use of the optDebugBands to ensure the bands are read in the
 * same sequence as it was written. The caveat is that this works only with
 * the java unpacker, therefore it will work only with --repack such that
 * the java packer and unpacker must be called in the same java instance.
 */
public class BandIntegrity {
    public static void main(String... args) throws IOException {
        File testFile = new File("test.jar");
        Utils.jar("cvf", testFile.getName(),
                "-C", Utils.TEST_CLS_DIR.getAbsolutePath(),
                ".");
        List<String> scratch = new ArrayList<>();
        // band debugging works only with java unpacker
        scratch.add("com.sun.java.util.jar.pack.disable.native=true");
        scratch.add("com.sun.java.util.jar.pack.debug.bands=true");
        // while at it, might as well exercise this functionality
        scratch.add("com.sun.java.util.jar.pack.dump.bands=true");
        scratch.add("pack.unknown.attribute=error");
        File configFile = new File("pack.conf");
        Utils.createFile(configFile, scratch);
        File outFile = new File("out.jar");
        Utils.repack(testFile, outFile, true,
                "-v", "--config-file=" + configFile.getName());
        Utils.cleanup();
    }
}
