/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6754038 6979327
 * @summary Generate call sites for method handle
 * @author jrose
 *
 * @library ..
 * @compile/fail/ref=InvokeDynTrans.out -Werror -XDrawDiagnostics -source 7 -target 7 InvokeDynTrans.java
 */
//No: @run main/othervm -XX:+EnableInvokeDynamic meth.InvokeDyn

/*
 * Standalone testing:
 * <code>
 * $ cd $MY_REPO_DIR/langtools
 * $ (cd make; make)
 * $ ./dist/bootstrap/bin/javac -d dist test/tools/javac/meth/InvokeDyn.java
 * $ javap -c -classpath dist meth.InvokeDyn
 * </code>
 */

package meth;

import java.dyn.InvokeDynamic;

public class InvokeDynTrans {
    void test() throws Throwable {
        Object x = "hello";
        InvokeDynamic.greet(x, "world", 123);
        InvokeDynamic.greet(x, "mundus", 456);
        InvokeDynamic.greet(x, "kosmos", 789);
        InvokeDynamic.<String>cogitate(10.11121, 3.14);
        //InvokeDynamic.<void>#"yow: what I mean to say is, please treat this one specially"(null);
        InvokeDynamic.<int>invoke("goodbye");
    }
}
