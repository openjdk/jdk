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
 * @test id=posix_spawn
 * @summary Check that we don't leak FDs
 * @requires os.family != "windows"
 * @library /test/lib
 * @run main/othervm/native -Djdk.lang.Process.launchMechanism=posix_spawn -agentlib:FDLeaker FDLeakTest
 */

/**
 * @test id=fork
 * @summary Check that we don't leak FDs
 * @requires os.family != "windows"
 * @library /test/lib
 * @run main/othervm/native -Djdk.lang.Process.launchMechanism=fork -agentlib:FDLeaker FDLeakTest
 */

/**
 * @test id=vfork
 * @summary Check that we don't leak FDs
 * @requires os.family == "linux"
 * @library /test/lib
 * @run main/othervm/native -Djdk.lang.Process.launchMechanism=vfork -agentlib:FDLeaker FDLeakTest
 */

import jdk.test.lib.process.ProcessTools;
public class FDLeakTest {
    // This test has two native parts:
    // - a library invoked with -agentlib that ensures that, in the parent JVM, we open
    //   a native fd without setting FD_CLOEXEC (libFDLeaker.c). This is necessary because
    //   there is no way to do this from Java: if Java functions correctly, all files the
    //   user could open via its APIs should be marked with FD_CLOEXEC.
    // - a small native executable that tests - without using /proc - whether any file
    //   descriptors other than stdin/out/err are open.
    //
    // What should happen: In the child process, between the initial fork and the exec of
    // the target binary, we should close all filedescriptors that are not stdin/out/err.
    // If that works, the child process should not see any other file descriptors save
    // those three.
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createNativeTestProcessBuilder("FDLeakTester");
        pb.inheritIO();
        Process p = pb.start();
        p.waitFor();
        if (p.exitValue() != 0) {
            throw new RuntimeException("Failed");
        }
    }
}
