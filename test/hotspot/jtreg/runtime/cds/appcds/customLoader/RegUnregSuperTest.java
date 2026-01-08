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

import jdk.test.lib.process.OutputAnalyzer;

/**
 * @test id=unreg
 * @summary Tests that if a super class is listed as unregistered it is archived
 * as such even if a class with the same name has also been loaded from the
 * classpath.
 *
 * @requires vm.cds
 * @requires vm.cds.custom.loaders
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/RegUnregSuperApp.java test-classes/CustomLoadee3.java test-classes/CustomLoadee3Child.java
 * @run main RegUnregSuperTest unreg
 */

/**
 * @test id=reg
 * @summary If an unregistered class U is specified to have a registered
 * supertype S1 named SN but an unregistered class S2 also named SN has already
 * been loaded S2 will be incorrectly used as the supertype of U instead of S1
 * due to limitations in the loading mechanism of unregistered classes. For this
 * reason U should not be loaded at all and an appropriate warning should be
 * printed.
 *
 * @requires vm.cds
 * @requires vm.cds.custom.loaders
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/RegUnregSuperApp.java test-classes/CustomLoadee3.java test-classes/CustomLoadee3Child.java
 * @run main RegUnregSuperTest reg
 */

public class RegUnregSuperTest {
    public static void main(String[] args) throws Exception {
        final String variant = args[0];

        final String appJar = JarBuilder.build(
            "app", "RegUnregSuperApp", "DirectClassLoader", "CustomLoadee3", "CustomLoadee3Child"
        );
        OutputAnalyzer out;

        final String classlist[] = new String[] {
            "java/lang/Object id: 0",
            "CustomLoadee3 id: 1",
            "CustomLoadee3 id: 2 super: 0 source: " + appJar,
            "CustomLoadee3Child id: 3 super: " + ("reg".equals(variant) ? "1" : "2") + " source: " + appJar
        };
        out = TestCommon.testDump(appJar, classlist, "-Xlog:cds+class=debug");
        out.shouldContain("app   CustomLoadee3"); // Not using \n as below because it'll be "app   CustomLoadee3 aot-linked" with AOTClassLinking
        out.shouldNotContain("app   CustomLoadee3Child");
        out.shouldContain("unreg CustomLoadee3\n"); // Accepts "unreg CustomLoadee3" but not "unreg CustomLoadee3Child"
        if ("reg".equals(variant)) {
            out.shouldNotContain("unreg CustomLoadee3Child");
            out.shouldContain("CustomLoadee3Child (id 3) has super-type CustomLoadee3 (id 1) obstructed by another class with the same name");
        } else {
            out.shouldContain("unreg CustomLoadee3Child");
            out.shouldNotContain("[warning]");
        }

        out = TestCommon.exec(appJar, "-Xlog:class+load", "RegUnregSuperApp", variant);
        TestCommon.checkExec(
            out,
            "CustomLoadee3Child source: " + ("reg".equals(variant) ? "file:" : "shared objects file")
        );
    }
}
