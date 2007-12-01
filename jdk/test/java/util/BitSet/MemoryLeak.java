/*
 * Copyright 1997 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 4091185
 * @summary Repeatedly OR BitSets; No OutOfMemoryException should result
 */

import java.util.*;

/**
 * This is a simple test class that repeatedly ORs two
 * BitSets of unequal size together. Previously this
 * caused an exponential growth in the memory underlying
 * the BitSets quickly using all available memory
 */
public class MemoryLeak {

   public static void main(String[] args) {

        //create 2 test bitsets
        BitSet setOne = new BitSet();
        BitSet setTwo = new BitSet();

        setOne.set(64);
        setTwo.set(129);

        //test for bug #4091185
        //exponential set growth causing memory depletion
        for (int i = 0; i < 50; i++) {
            setOne.or(setTwo);
            setTwo.or(setOne);
        }
    }
}
