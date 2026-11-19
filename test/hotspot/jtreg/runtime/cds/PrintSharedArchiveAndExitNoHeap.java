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

/**
 * @test
 * @bug 8373411
 * @summary Testing -XX:+PrintSharedArchiveAndExit option with no shared heap
 * @requires vm.cds
 * @library /test/lib
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class PrintSharedArchiveAndExitNoHeap {
    public static void main(String[] args) throws Exception {
        // This used to crash when trying to figure out if interned string should be printed
        OutputAnalyzer oa = ProcessTools.executeTestJava("-XX:AOTCacheOutput=./PrintSharedArchiveAndExitNoHeap.jsa", "-XX:+PrintSharedArchiveAndExit", "-version");
        oa.shouldHaveExitValue(0);
        // AOTCacheOutput forks a process, if that crashes then the above check is not enough
        // and we have to check the output to figure out if it crashed.
        oa.shouldNotContain("Internal Error");
    }
}
