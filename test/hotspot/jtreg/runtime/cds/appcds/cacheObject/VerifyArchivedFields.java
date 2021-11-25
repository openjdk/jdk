/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @summary run with -XX:VerifyArchivedFields=2 for more expensive verification
 *          of the archived heap objects.
 * @requires vm.cds.write.archived.java.heap
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar Hello.jar Hello
 * @run driver VerifyArchivedFields
 */

import jdk.test.lib.helpers.ClassFileInstaller;

public class VerifyArchivedFields {
    // Note: -XX:VerifyArchivedFields=2 will force a GC every time when
    // HeapShared::initialize_from_archived_subgraph(Klass* k, ...) is called. This ensures
    // that it's safe to do a GC even when the oop->klass() of some archived heap objects
    // are not yet loaded into the system dictionary.
    public static void main(String[] args) throws Exception {
        TestCommon.test(ClassFileInstaller.getJarPath("Hello.jar"),
                        TestCommon.list("Hello"),
                        "-XX:+UnlockDiagnosticVMOptions",
                        "-XX:VerifyArchivedFields=2",
                        "-Xlog:cds=debug",
                        "-Xlog:cds+heap=debug",
                        "-Xlog:gc*=debug",
                        "Hello");
  }
}
