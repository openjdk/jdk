/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * This file models a few cases where Elements.overrides produces a false
 * positive which warrants @apiNote.
 */

// S.java does not compile because it violates the JLS rules for overrides
class S {

    public void m() { }
}

// `protected` is a weaker modifier than `public`
class T1 extends S {

    protected void m() { }
}

// `package-private` is a weaker modifier than `public`
class T2 extends S {

    void m() { }
}

// `private` methods cannot override public method
class T3 extends S {

    private void m() { }
}

// return type int is not compatible with void
class T4 extends S {

    public int m() { return 0; }
}

// adding a checked exception violates the override rule
class T5 extends S {

    public void m() throws Exception { }
}
