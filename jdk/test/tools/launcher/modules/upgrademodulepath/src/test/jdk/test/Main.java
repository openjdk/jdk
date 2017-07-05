/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test;

import javax.transaction.Transaction;
import javax.enterprise.context.Scope;

/**
 * Uses an upgraded version of module java.transaction.
 */

public class Main {

    public static void main(String[] args) {

        ClassLoader scl = ClassLoader.getSystemClassLoader();
        ClassLoader pcl = ClassLoader.getPlatformClassLoader();
        assertTrue(pcl.getParent() == null);

        Transaction transaction = new Transaction();
        Scope scope = transaction.getScope();

        // javax.transaction.Transaction should be in module java.transaction
        // and defined by the platform class loader
        assertTrue(Transaction.class.getModule().getName().equals("java.transaction"));
        assertTrue(Transaction.class.getClassLoader() == pcl);

        // javax.enterprise.context.Scope should be in module java.enterprise
        // and defined by the application class loader
        assertTrue(Scope.class.getModule().getName().equals("java.enterprise"));
        assertTrue(Scope.class.getClassLoader() == scl);
    }

    static void assertTrue(boolean e) {
        if (!e) throw new RuntimeException();
    }

}
