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
* @test TestCMSForegroundFlags
* @key gc
* @bug 8027132
* @summary Test that the deprecated CMS foreground collector flags print warning messages
* @library /testlibrary
* @run main TestCMSForegroundFlags -XX:-UseCMSCompactAtFullCollection UseCMSCompactAtFullCollection
* @run main TestCMSForegroundFlags -XX:CMSFullGCsBeforeCompaction=4 CMSFullGCsBeforeCompaction
* @run main TestCMSForegroundFlags -XX:-UseCMSCollectionPassing UseCMSCollectionPassing
*/

import com.oracle.java.testlibrary.OutputAnalyzer;
import com.oracle.java.testlibrary.ProcessTools;

public class TestCMSForegroundFlags {
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new Exception("Expected two arguments,flagValue and flagName");
    }
    String flagValue = args[0];
    String flagName = args[1];

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(flagValue, "-version");
    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldContain("warning: " + flagName + " is deprecated and will likely be removed in a future release.");
    output.shouldNotContain("error");
    output.shouldHaveExitValue(0);
  }
}
