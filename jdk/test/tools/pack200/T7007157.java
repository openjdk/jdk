/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.Pack200;
/*
 * @test
 * @bug 7007157
 * @summary make sure the strip command works on an attribute
 * @compile -XDignore.symbol.file Utils.java T7007157.java
 * @run main T7007157
 * @author ksrini
 */
public class T7007157 {

    public static void main(String... args) throws IOException {
        File sdkHome = Utils.JavaSDK;
        File testJar = new File(new File(sdkHome, "lib"), "tools.jar");
        JarFile jarFile = new JarFile(testJar);
        File packFile = new File("foo.pack");
        Pack200.Packer packer = Pack200.newPacker();
        Map<String, String> p = packer.properties();
        // Take the time optimization vs. space
        p.put(packer.EFFORT, "1");  // CAUTION: do not use 0.
        // Make the memory consumption as effective as possible
        p.put(packer.SEGMENT_LIMIT, "10000");
        // ignore all JAR deflation requests to save time
        p.put(packer.DEFLATE_HINT, packer.FALSE);
        // save the file ordering of the original JAR
        p.put(packer.KEEP_FILE_ORDER, packer.TRUE);
        // strip the StackMapTables
        p.put(packer.CODE_ATTRIBUTE_PFX + "StackMapTable", packer.STRIP);
        FileOutputStream fos = null;
        try {
            // Write out to a jtreg scratch area
            fos = new FileOutputStream(packFile);
            // Call the packer
            packer.pack(jarFile, fos);
        } finally {
            Utils.close(fos);
            Utils.close(jarFile);
        }
    }
}
