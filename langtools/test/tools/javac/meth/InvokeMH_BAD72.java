/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * ##test
 * ##bug 6754038
 * ##summary Generate call sites for method handle
 * ##author jrose
 *
 * ##compile/fail -source 7 -target 7 InvokeMH_BAD72.java
 */

/*
 * Standalone testing:
 * <code>
 * $ cd $MY_REPO_DIR/langtools
 * $ (cd make; make)
 * $ ./dist/bootstrap/bin/javac -d dist test/tools/javac/meth/InvokeMH_BAD72.java
 * $ javap -c -classpath dist meth.InvokeMH_BAD72
 * </code>
 */

package meth;

import java.dyn.MethodHandle;

public class InvokeMH_BAD72 {
    void test(MethodHandle mh_SiO,
              MethodHandle mh_vS,
              MethodHandle mh_vi,
              MethodHandle mh_vv) {
        Object o; String s; int i;  // for return type testing

        // next five must have sig = (String,int)Object
        mh_SiO.invoke("world", 123);
        mh_SiO.invoke("mundus", 456);
        Object k = "kosmos";
        mh_SiO.invoke((String)k, 789);
        o = mh_SiO.invoke((String)null, 000);
        o = mh_SiO.<Object>invoke("arda", -123);

        // sig = ()String
        s = mh_vS.<String>invoke();

        // sig = ()int
        i = mh_vi.<int>invoke();
        o = mh_vi.<int>invoke();
        //s = mh_vi.<int>invoke(); //BAD
        mh_vi.<int>invoke();

        // sig = ()void
    o = mh_vv.<void>invoke(); //BAD
        mh_vv.<void>invoke();
    }
}
