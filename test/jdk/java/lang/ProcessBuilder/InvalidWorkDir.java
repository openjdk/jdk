/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=FORK
 * @bug 8379967
 * @summary Check that passing an invalid work dir yields a corresponding IOE text.
 * @requires (os.family != "windows")
 * @requires vm.flagless
 * @library /test/lib
 * @run main/othervm -Xmx64m -Djdk.lang.Process.launchMechanism=FORK InvalidWorkDir
 */

/**
 * @test id=POSIX_SPAWN
 * @bug 8379967
 * @summary Check that passing an invalid work dir yields a corresponding IOE text.
 * @requires (os.family != "windows")
 * @requires vm.flagless
 * @library /test/lib
 * @run main/othervm -Xmx64m -Djdk.lang.Process.launchMechanism=FORK InvalidWorkDir
 */

import jdk.test.lib.process.OutputAnalyzer;

import java.io.File;
import java.io.IOException;

public class InvalidWorkDir {

    public static void main(String[] args) {
        ProcessBuilder bld = new ProcessBuilder("ls").directory(new File("./doesnotexist"));
        try(Process p = bld.start()) {
            throw new RuntimeException("IOE expected");
        } catch (IOException e) {
            if (!e.getMessage().matches(".*Failed to access working directory.*No such file or directory.*")) {
                throw new RuntimeException(String.format("got IOE but with different text (%s)", e.getMessage()));
            }
        }
    }

}
