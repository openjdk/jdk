/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.api.recording.dump;

import java.nio.file.Path;
import jdk.jfr.Recording;

/**
 * @test
 * @summary Tests that it's possible to dump to /dev/null without a livelock
 * @requires vm.flagless
 * @requires vm.hasJFR & (os.family != "windows")
 * @library /test/lib
 * @run main/othervm -Xlog:jfr jdk.jfr.api.recording.dump.TestDumpDevNull
 */
public class TestDumpDevNull {

    public static void main(String[] args) throws Exception {
        try (Recording r1 = new Recording()) {
            r1.setDestination(Path.of("/dev/null"));
            r1.start();
            // Force a chunk rotation which ensures that jdk.jfr.internal.ChunkChannel
            // invokes FileChannel::transferFrom(ReadableByteChannel, position, count) twice.
            // FileChannel will return 0 the second time because position exceeds
            // FileChannel::size(), which is always 0 for /dev/null
            // Without proper handling of return value 0, the ChunkChannel will spin indefinitely.
            try (Recording r2 = new Recording()) {
                r2.start();
            }
        }
    }
}
