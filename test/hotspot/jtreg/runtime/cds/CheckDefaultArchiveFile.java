/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.cds
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *      -XX:+WhiteBoxAPI CheckDefaultArchiveFile
 */
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jdk.test.lib.Platform;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.whitebox.WhiteBox;
import jtreg.SkippedException;

public class CheckDefaultArchiveFile {
    public static void main(String[] args) throws Exception {
        WhiteBox wb = WhiteBox.getWhiteBox();
        String osArch = Platform.getOsArch();
        String vmName = System.getProperty("java.vm.name");
        String vmString = vmName + "(" + osArch + ")";
        String jsaString = wb.getDefaultArchivePath();
        System.out.println("Default CDS archive location:" + jsaString);
        String javaOptions = System.getProperty("test.java.opts");
        if (jsaString == null) {
            if (Platform.isDefaultCDSArchiveSupported()) {
                throw new RuntimeException("default CDS archive supported, but classes.jsa path null");
            }
        } else {
            Path jsa = Paths.get(jsaString);
            if (Platform.isDefaultCDSArchiveSupported()) {
                if (Files.exists(jsa)) {
                    System.out.println("Passed. " + vmString +
                                       ": has default " + jsaString + " file");
                } else {
                    if (javaOptions.indexOf("-XX:+UseCompactObjectHeaders") != -1 && jsaString.endsWith("_coh.jsa")) {
                       // JDK build may not include CDS archives for compact object headers.
                       throw new SkippedException("CDS archive for compact object headers " + jsaString + " is not available in this test JDK");
                    } else {
                        throw new RuntimeException(vmString + "has no " + jsaString);
                    }
                }
            } else {
                throw new SkippedException("Default CDS archive is not supported");
            }
        }
    }
}
