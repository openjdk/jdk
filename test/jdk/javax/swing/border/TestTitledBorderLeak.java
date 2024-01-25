/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

import javax.swing.border.TitledBorder;

/**
 * @test
 * @bug 8204963
 * @summary Verifies TitledBorder's memory leak
 * @library /javax/swing/regtesthelpers
 * @build Util
 * @run main/timeout=60/othervm -mx32m TestTitledBorderLeak
 */
public final class TestTitledBorderLeak {

    public static void main(String[] args) throws Exception {
        Reference<TitledBorder> border = getTitleBorder();
        int attempt = 0;
        while (border.get() != null) {
            Util.generateOOME();
            System.out.println("Not freed, attempt: " + attempt++);
        }
    }

    private static Reference<TitledBorder> getTitleBorder() {
        TitledBorder tb = new TitledBorder("");
        return new WeakReference<>(tb);
    }
}
