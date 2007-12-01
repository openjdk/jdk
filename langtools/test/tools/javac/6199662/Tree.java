/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6199662 6325201
 * @summary javac: compilation success depends on compilation order
 *
 * @compile Tree.java TreeScanner.java TreeInfo.java
 * @compile TreeInfo.java TreeScanner.java Tree.java
 *
 * @compile -XDcompilePolicy=bytodo Tree.java TreeScanner.java TreeInfo.java
 * @compile -XDcompilePolicy=bytodo TreeInfo.java TreeScanner.java Tree.java
 *
 * @compile -XDcompilePolicy=byfile Tree.java TreeScanner.java TreeInfo.java
 * @compile -XDcompilePolicy=byfile TreeInfo.java TreeScanner.java Tree.java
 *
 * @compile -XDcompilePolicy=simple Tree.java TreeScanner.java TreeInfo.java
 * @compile -XDcompilePolicy=simple TreeInfo.java TreeScanner.java Tree.java
 *
 * @compile -XDcompilePolicy=check  Tree.java TreeScanner.java TreeInfo.java
 * @compile -XDcompilePolicy=check  TreeInfo.java TreeScanner.java Tree.java
 *
 * @compile -XDcompilePolicy=attr   Tree.java TreeScanner.java TreeInfo.java
 * @compile -XDcompilePolicy=attr   TreeInfo.java TreeScanner.java Tree.java
 */

package p;

public abstract class Tree {

    /** Visit this tree with a given visitor.
     */
    public abstract <E extends Throwable> void accept(Visitor<E> v) throws E;


    /** A generic visitor class for trees.
     */
    public static abstract class Visitor<E extends Throwable> {
        public void visitTree(Tree that)                   throws E { assert false; }
    }
}
