/*
 * Copyright (C) 2020 THL A29 Limited, a Tencent company. All rights reserved.
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

package gc.ergonomics;

/*
 * @test TestMinHeapSize
 * @bug 8257230
 * @summary Check ergonomics decided on compatible initial and minimum heap sizes
 * @library /test/lib
 * @requires os.family == "linux"
 * @run main/othervm gc.ergonomics.TestMinHeapSize
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;


public class TestMinHeapSize {

    public static void main(String[] args) throws Throwable {
        String cmd = ProcessTools.getCommandLine(ProcessTools.createJavaProcessBuilder(
                     "-XX:MinHeapSize=1537m", "-XX:CompressedClassSpaceSize=64m", "-version"));

        int ulimitV = 3145728; // 3G
        var pb = new ProcessBuilder(
                "sh", "-c",
                "ulimit -v " + ulimitV + "; " + cmd);

        // lower MALLOC_ARENA_MAX b/c we limited virtual memory, see JDK-8043516
        pb.environment().put("MALLOC_ARENA_MAX", "4");

        var oa = ProcessTools.executeCommand(pb);

        oa.shouldNotContain("hs_err")
          .shouldNotContain("Internal Error")
          .shouldHaveExitValue(0);
    }
}
