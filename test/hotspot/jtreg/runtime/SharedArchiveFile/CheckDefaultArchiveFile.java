/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @test Default CDS archive file
 * @summary JDK platforms/binaries do not support default CDS archive should
 *          not contain classes.jsa in the default location.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *      -XX:+WhiteBoxAPI CheckDefaultArchiveFile
 */
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jdk.test.lib.Platform;
import jdk.test.lib.cds.CDSTestUtils;
import jtreg.SkippedException;
import sun.hotspot.WhiteBox;

public class CheckDefaultArchiveFile {
    public static void main(String[] args) throws Exception {
        WhiteBox wb = WhiteBox.getWhiteBox();
        String osArch = Platform.getOsArch();
        String vmName = System.getProperty("java.vm.name");
        String vmString = vmName + "(" + osArch + ")";
        String jsaString = wb.getDefaultArchivePath();
        Path jsa = Paths.get(jsaString);
        if (Platform.isDefaultCDSArchiveSupported()) {
            if (Files.exists(jsa)) {
                System.out.println("Passed. " + vmString +
                                   ": has default classes.jsa file");
            } else {
                throw new RuntimeException(vmString + "has no " + jsaString);
            }
        } else {
            throw new SkippedException("Default CDS archive is not supported");
        }
    }
}
