/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @test
 * @bug 5009712
 * @summary 1.4 javac should not accept the Covariant Return Type
 * @author gafter
 *
 * @compile/fail -source 1.4 ExtendCovariant2.java
 * @compile                  ExtendCovariant2.java
 */

/**
 * java.io.PrintStream java.io.PrintStream.append(char)
 *
 * overrides
 *
 * java.lang.Appendable java.lang.Appendable.append(char)
 *
 * Yet javac should allow extending PrintStream, as long as the user
 * doesn't directly override a covariant method in -source 1.4.
 **/
public class ExtendCovariant2 extends java.io.PrintStream {
    ExtendCovariant2() throws java.io.IOException {
        super("");
    }
    public java.io.PrintStream append(char c) {
        return this;
    }
}
