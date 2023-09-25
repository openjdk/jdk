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

/*
 * @test
 * @summary Verify that when a child returns exit value 259, Process.waitFor does not throw
 * @bug 8294899
 * @requires (os.family == "windows")
 * @run junit WindowsExitValue
 */

import java.io.IOException;

import org.junit.*;
import static org.junit.Assert.*;

public class WindowsExitValue {

    @Test
    public void checkExit259() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("cmd", "/c", "exit /b 259").start();
        int exitValue = process.waitFor();
        assertEquals(exitValue, 259);
    }
}
