/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.test.lib.apps.LingeredApp;
import jtreg.SkippedException;

/**
 * @test
 * @bug 8314679
 * @summary Test clhsdb attach, detach, and then attach to different JVM
 * @requires vm.hasSA
 * @library /test/lib
 * @run main/othervm ClhsdbAttachDifferentJVMs
 */

public class ClhsdbAttachDifferentJVMs {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting ClhsdbAttach test");

        LingeredApp theApp1 = null;
        LingeredApp theApp2 = null;
        try {
            ClhsdbLauncher test = new ClhsdbLauncher();
            theApp1 = LingeredApp.startApp();
            System.out.println("Started LingeredApp with pid " + theApp1.getPid());
            theApp2 = LingeredApp.startApp();
            System.out.println("Started LingeredApp with pid " + theApp2.getPid());
            String attach1 = "attach " + theApp1.getPid();
            String attach2 = "attach " + theApp2.getPid();

            List<String> cmds = List.of(
                    "where",
                    attach1,
                    "threads",
                    "detach",
                    attach2,
                    "jstack");

            Map<String, List<String>> expStrMap = new HashMap<>();
            expStrMap.put("where", List.of(
                    "Command not valid until attached to a VM"));
            expStrMap.put(attach1, List.of(
                    "Attaching to process " + theApp1.getPid()));
            expStrMap.put("threads", List.of(
                    "Reference Handler"));
            expStrMap.put(attach2, List.of(
                    "Attaching to process " + theApp2.getPid()));
            expStrMap.put("jstack", List.of(
                    "Reference Handler"));

            Map<String, List<String>> unexpStrMap = new HashMap<>();
            unexpStrMap.put("jstack", List.of(
                    "WARNING"));

            test.run(-1, cmds, expStrMap, unexpStrMap);
        } catch (SkippedException se) {
            throw se;
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        } finally {
            LingeredApp.stopApp(theApp1);
            LingeredApp.stopApp(theApp2);
        }
        System.out.println("Test PASSED");
    }
}
