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

/**
 * This class is launched in a sub-process by the main test,
 * SASymbolTableTest.java.
 *
 * This class does nothing in particular. It just sleeps for 120
 * seconds so SASymbolTableTestAgent can have a chance to examine its
 * SymbolTable. This process should be killed by the parent process
 * after SASymbolTableTestAgent has completed testing.
 */
public class SASymbolTableTestAttachee {
    public static void main(String args[]) throws Throwable {
        System.out.println("SASymbolTableTestAttachee: sleeping to wait for SA tool to attach ...");
        Thread.sleep(120 * 1000);
    }
}
