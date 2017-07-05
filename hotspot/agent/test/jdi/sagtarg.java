/*
 * Copyright 2002-2004 Sun Microsystems, Inc.  All Rights Reserved.
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
 *
 */

// This is the target debuggee for sagtest.java.
// It just waits which lets the test call all the JDI
// methods on it.

import java.util.List;

interface MyInterface {
    public void myMethod();
}


abstract class MySuper implements MyInterface {
}

class sagtarg extends MySuper {
    public static void main(String[] args){
        String stringVar = "localVar1";
        int    intVar = 89;
        List<String> genVar = null;
        System.out.println("Howdy!");
        String myStr = "";
        synchronized(myStr) {
            try {
                myStr.wait();
            } catch (InterruptedException ee) {
            }
        }
        System.out.println("Goodbye from sagtarg!");
    }

    public void myMethod() {
    }
}
