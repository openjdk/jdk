/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.beans.beancontext.BeanContextSupport;

/**
 * @test
 * @bug 8238170
 * @summary Some basic tests
 */
public final class AddRemove {

    public static void main(String[] args) {
        BeanContextSupport bcs = new BeanContextSupport();
        if (!bcs.isEmpty()) {
            throw new RuntimeException("The new context is not empty");
        }
        Object child1 = new Object();
        bcs.add(child1);
        if (bcs.size() != 1) {
            throw new RuntimeException("Expected one element");
        }
        Object child2 = new Object();
        bcs.add(child2);
        if (bcs.size() != 2) {
            throw new RuntimeException("Expected two elements");
        }
        bcs.remove(child1);
        if (bcs.size() != 1) {
            throw new RuntimeException("Expected one element");
        }
        if (bcs.toArray()[0] != child2) {
            throw new RuntimeException("Wrong element");
        }
        bcs.remove(child2);
        if (!bcs.isEmpty()) {
            throw new RuntimeException("The context is not empty");
        }
    }
}
