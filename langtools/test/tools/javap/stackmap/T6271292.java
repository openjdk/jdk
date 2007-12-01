/*
 * Copyright 2005-2007 Sun Microsystems, Inc.  All Rights Reserved.
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


public class T6271292 extends SuperClass {

    public static void main(String[] args) {
        SuperClass s = new SuperClass((args[0].equals("0")) ? 0 : 1);
        s.test();
    }
    T6271292(boolean b) {
        super(b ? 1 : 2);
    }
}

class SuperClass {
    double d;
    SuperClass(double dd) { d = dd; }

    double test() {
        if (d == 0) {
            return d;
        } else if (d > 0) {
            return d++;
        } else {
            return d--;
        }
    }
}
