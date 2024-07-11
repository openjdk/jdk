/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Red Hat, Inc. and/or its affiliates.
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

public class SystemMapTestBase {

    // e.g.
    // 0x00000007ff800000-0x00000007ff91a000      1155072 rw-p      1155072            0 4K   com              JAVAHEAP                              /shared/projects/openjdk/jdk-jdk/output-fastdebug/images/jdk/lib/server/classes.jsa
    private static final String range = "0x\\p{XDigit}+-0x\\p{XDigit}+";
    private static final String space = " +";
    private static final String someSize = "\\d+";
    private static final String pagesize = "(4K|8K|16K|64K|2M|16M|64M)";
    private static final String prot = "[rwsxp-]+";

    private static final String regexBase = range + space +
            someSize + space +
            prot + space +
            someSize + space +
            someSize + space +
            pagesize + space;

    private static final String regexBase_committed = regexBase + "com.*";
    private static final String regexBase_shared_and_committed = regexBase + "shrd,com.*";

    // java heap is either committed, non-shared, or - in case of ZGC - committed and shared.
    private static final String regexBase_java_heap = regexBase + "(shrd,)?com.*";
    protected static final String shouldMatchUnconditionally[] = {
        // java launcher
        regexBase_committed + "/bin/java",
        // libjvm
        regexBase_committed + "/lib/.*/libjvm.so",
        // heap segment, should be part of all user space apps on all architectures OpenJDK supports.
        regexBase_committed + "\\[heap\\]",
        // we should see the hs-perf data file, and it should appear as shared as well as committed
        regexBase_shared_and_committed + "hsperfdata_.*"
    };

    protected static final String shouldMatchIfNMTIsEnabled[] = {
        regexBase_java_heap + "JAVAHEAP.*",
        // metaspace
        regexBase_committed + "META.*",
        // parts of metaspace should be uncommitted
        regexBase + "-" + space + "META.*",
        // code cache
        regexBase_committed + "CODE.*",
        // Main thread stack
        regexBase_committed + "STACK.*main.*"
    };
}
