/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @library /lib/testlibrary /test/lib
 * @modules jdk.jartool/sun.tools.jar
 * @build LingeredAppForJps
 * @build LingeredApp
 * @run main/othervm TestJps
 */

 /*
  * Notes:
  *   @modules tag is ignored in driver mode, so need main/othervm
  *
  *   Launching the process with relative path to an app jar file is not tested
  *
  *   This test resides in default package, so correct appearance
  *   of the full package name actually is not tested.
  */

import java.util.List;
import java.io.File;

public class TestJps {

    public static void testJpsClass() throws Throwable {
        LingeredApp app = new LingeredAppForJps();
        try {
            LingeredApp.startApp(JpsHelper.getVmArgs(), app);
            JpsHelper.runJpsVariants(app.getPid(),
                LingeredAppForJps.getProcessName(), LingeredAppForJps.getFullProcessName(), app.getLockFileName());

        } finally {
            LingeredApp.stopApp(app);
        }
    }

    public static void testJpsJar() throws Throwable {
        // Get any jar exception as early as possible
        File jar = LingeredAppForJps.buildJar();

        // Jar created go to the main test
        LingeredAppForJps app = new LingeredAppForJps();
        try {
            LingeredAppForJps.startAppJar(JpsHelper.getVmArgs(), app, jar);
            JpsHelper.runJpsVariants(app.getPid(),
                LingeredAppForJps.getProcessName(jar), LingeredAppForJps.getFullProcessName(jar), app.getLockFileName());
        } finally {
            LingeredAppForJps.stopApp(app);
        }

    }

    public static void main(String[] args) throws Throwable {
        testJpsClass();
        testJpsJar();
    }
}
