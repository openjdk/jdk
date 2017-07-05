/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @test SetForceInlineMethodTest
 * @library /testlibrary /testlibrary/whitebox
 * @build SetForceInlineMethodTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI SetForceInlineMethodTest
 * @author igor.ignatyev@oracle.com
 */
public class SetForceInlineMethodTest extends CompilerWhiteBoxTest {

    public static void main(String[] args) throws Exception {
        new SetForceInlineMethodTest().runTest();
    }

    protected void test() throws Exception {
        if (WHITE_BOX.testSetForceInlineMethod(METHOD, true)) {
            throw new RuntimeException("on start " + METHOD
                    + " must be not force inlineable");
        }
        if (!WHITE_BOX.testSetForceInlineMethod(METHOD, true)) {
            throw new RuntimeException("after first change to true " + METHOD
                    + " must be force inlineable");
        }
        if (!WHITE_BOX.testSetForceInlineMethod(METHOD, false)) {
            throw new RuntimeException("after second change to true " + METHOD
                    + " must be still force inlineable");
        }
        if (WHITE_BOX.testSetForceInlineMethod(METHOD, false)) {
            throw new RuntimeException("after first change to false" + METHOD
                    + " must be not force inlineable");
        }
        if (WHITE_BOX.testSetForceInlineMethod(METHOD, false)) {
            throw new RuntimeException("after second change to false " + METHOD
                    + " must be not force inlineable");
        }
    }
}
