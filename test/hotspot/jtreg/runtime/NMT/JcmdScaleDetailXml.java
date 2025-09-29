/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test the NMT scale parameter with detail tracking level
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -XX:NativeMemoryTracking=detail JcmdScaleDetailXml
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import java.io.File;

import jdk.test.lib.JDKToolFinder;

public class JcmdScaleDetailXml {

    public static String[] getCommmand(String pid, String scale, File xmlFile) throws Exception {
      return new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.native_memory", "detail", "scale=" + scale,
                            "xmlformat", "file=" + xmlFile.getAbsolutePath()};
    }

    public static NMTXmlUtils runAndCreateXmlReport(String scale, String xmlFilename) throws Exception {
      ProcessBuilder pb = new ProcessBuilder();
      String pid = Long.toString(ProcessTools.getProcessId());
      File xmlFile = File.createTempFile(xmlFilename, ".xml");
      pb.command(getCommmand(pid, scale, xmlFile));
      pb.start().waitFor();
      return new NMTXmlUtils(xmlFile);
    }

    public static void main(String args[]) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        String pid = Long.toString(ProcessTools.getProcessId());
        NMTXmlUtils nmtXml;

        nmtXml = runAndCreateXmlReport("KB", "nmt_detail_KB_");
        nmtXml.shouldBeReportType("Detail");
        nmtXml.shouldBeScale("KB");


        nmtXml = runAndCreateXmlReport("MB", "nmt_detail_MB_");
        nmtXml.shouldBeReportType("Detail");
        nmtXml.shouldBeScale("MB");

        nmtXml = runAndCreateXmlReport("GB", "nmt_detail_GB_");
        nmtXml.shouldBeReportType("Detail");
        nmtXml.shouldBeScale("GB");
    }
}
