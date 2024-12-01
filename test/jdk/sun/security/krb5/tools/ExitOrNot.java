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

/*
 * @test
 * @bug 8316964
 * @summary check exit code in kinit, klist, and ktab
 * @requires os.family == "windows"
 * @library /test/lib
 * @modules java.security.jgss/sun.security.krb5.internal.tools
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.lib.SecurityTools;

public class ExitOrNot {

    private static final int BAD = Platform.isWindows() ? -1 : 255;

    public static void main(String[] args) throws Exception {

        // launching the tool still exits
        SecurityTools.kinit("u@R p1 p2")
                .shouldHaveExitValue(BAD);

        SecurityTools.klist("-x")
                .shouldHaveExitValue(BAD);

        SecurityTools.ktab("-x")
                .shouldHaveExitValue(BAD);

        // calling the run() methods returns the exit code
        Asserts.assertEQ(new sun.security.krb5.internal.tools.Kinit()
                .run("u@R p1 p2".split(" ")), -1);
        Asserts.assertEQ(new sun.security.krb5.internal.tools.Klist()
                .run("-x".split(" ")), -1);
        Asserts.assertEQ(new sun.security.krb5.internal.tools.Ktab()
                .run("-x".split(" ")), -1);
    }
}
