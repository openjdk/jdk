/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @test CdsSameObjectAlignment
 * @summary Testing CDS (class data sharing) using varying object alignment.
 *          Using same object alignment for each dump/load pair
 * @requires vm.cds
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 */

import jdk.test.lib.Platform;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

public class CdsSameObjectAlignment {
    public static void main(String[] args) throws Exception {
        String nativeWordSize = System.getProperty("sun.arch.data.model");
        if (!Platform.is64bit()) {
            System.out.println("ObjectAlignmentInBytes for CDS is only " +
                "supported on 64bit platforms; this plaform is " +
                nativeWordSize);
            System.out.println("Skipping the test");
        } else {
            dumpAndLoadSharedArchive(8);
            dumpAndLoadSharedArchive(16);
            dumpAndLoadSharedArchive(32);
            dumpAndLoadSharedArchive(64);
        }
    }

    private static void
    dumpAndLoadSharedArchive(int objectAlignmentInBytes) throws Exception {
        String objectAlignmentArg = "-XX:ObjectAlignmentInBytes="
            + objectAlignmentInBytes;
        System.out.println("dumpAndLoadSharedArchive(): objectAlignmentInBytes = "
            + objectAlignmentInBytes);

        CDSTestUtils.createArchiveAndCheck(objectAlignmentArg);
        CDSTestUtils.runWithArchiveAndCheck(objectAlignmentArg);
    }
}
