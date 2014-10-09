/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
// Use fully qualified names to avoid accidentally capturing dependencies in import statements.

package pkg;

import pkg2.*;                                          // pkg2 as a whole
import pkg3.Cls3;                                       // pkg3.Cls3
import pkg25.Cls25;                                     // pkg25.Cls25
import nondependency.pkg26.Cls26;                       // pkg26.Cls26 (but not nondependency)
import pkg28.Cls28.Inner28;                             // pkg29.Cls28, pkg29.Cls28.Inner28
import static pkg29.Cls29.Inner29;                      // pkg29.Cls29, pkg29.Cls29.Inner29
import static pkg30.Cls30.*;                            // pkg30.Cls30 as a whole

@pkg5.Anno5                                             // pkg5.Anno5
public class Test<S extends pkg23.Cls23>                // pkg23.Cls23
        extends pkg4.Cls4/*extends pkg11.Cls11*/<pkg6.Cls6/*extends pkg12.Cls12*/>   // pkg4.Cls4, pkg11.Cls11, pkg6.Cls6, pkg12.Cls12
        implements pkg7.Cls7, pkg8.Cls8<pkg9.Cls9> {    // pkg7.Cls7, pkg8.Cls8, pkg9.Cls9

    pkg27.Cls27 cls27[][][] = new pkg27.Cls27[0][0][0]; // pkg27.Cls27

    pkg2.Cls2 cls2;
    pkg19.Cls19 f19;                                    // pkg19.Cls19

    public static void main(String[] args) {            // java.lang.String
        pkg10.Cls10 o = new pkg10.Cls10();              // pkg10.Cls10

        o.getCls13().getCls14().getCls15();             // pkg13.Cls13, pkg14.Cls14, pkg15.Cls15
        pkg23.Cls23.f24 = null;                         // pkg23.Cls23, pkg24.Cls24
    }

    static pkg16.Cls16 m1(pkg17.Cls17 o) {              // pkg16.Cls16, pkg17.Cls17
        return null;
    }

    public <T extends pkg18.Cls18> void m2() {          // pkg18.Cls18
    }

    public <T> T m3() {
        T t;
        t = null;
        return t;
    }

    @pkg20.Anno20(pkg21.Cls21.class)                    // pkg20.Anno20, pkg21.Cls21
    private void m3(@pkg22.Anno22 String s) {           // pkg22.Anno22
        Runnable r = () -> { System.out.println("hello"); };
    }

    private void m4() throws Cls25 {                    // pkg25.Cls25
    }
}
