/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8164692
 * @summary Redefine previous_versions count goes negative
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @modules java.compiler
 *          java.instrument
 *          jdk.jartool/sun.tools.jar
 * @run main RedefineClassHelper
 * @run main/othervm -javaagent:redefineagent.jar -Xlog:redefine+class+iklass+add=trace,redefine+class+iklass+purge=trace RedefineCount
 */
public class RedefineCount {

    public static String newB =
                "class RedefineCount$B {" +
                "}";

    static class B { }

    public static void main(String[] args) throws Exception {

        // Redefine a class and create some garbage
        // Since there are no methods running, the previous version is never added to the
        // previous_version_list and the count should stay zero and not go negative
        RedefineClassHelper.redefineClass(B.class, newB);

        for (int i = 0; i < 20 ; i++) {
            String s = new String("some garbage");
            System.gc();
        }
    }
}
