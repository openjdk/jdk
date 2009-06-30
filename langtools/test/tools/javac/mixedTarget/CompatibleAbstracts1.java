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
 * @compile                  CompatibleAbstracts1.java
 * @compile                  CompatibleAbstracts2.java
 * @compile      -source 1.4 CompatibleAbstracts2.java
 * @compile                  CompatibleAbstracts3.java
 * @compile/fail -source 1.4 CompatibleAbstracts3.java
 * @compile                  CompatibleAbstracts4.java
 * @compile/fail -source 1.4 CompatibleAbstracts4.java
 * @compile                  CompatibleAbstracts5.java
 * @compile/fail -source 1.4 CompatibleAbstracts5.java
 */

interface A {
    A f();
}

interface B extends A {
    B f();
}

interface C {
    B f();
}

interface D {
    D f();
}
