/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test 8355034
 * @requires vm.jvmci
 * @modules jdk.internal.vm.ci/jdk.vm.ci.code
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI jdk.vm.ci.code.test.InstalledCodeTest
 */

package jdk.vm.ci.code.test;

import jdk.vm.ci.code.InstalledCode;
import org.junit.Assert;
import org.junit.Test;

public class InstalledCodeTest {

    @Test
    public void testNullName() {
        new InstalledCode(null);
    }

    @Test
    public void testTooLongName() {
        String longName = new String(new char[InstalledCode.MAX_NAME_LENGTH]).replace('\0', 'A');
        new InstalledCode(longName);
        try {
            String tooLongName = longName + "X";
            new InstalledCode(tooLongName);
        } catch (IllegalArgumentException iae) {
            // Threw IllegalArgumentException as expected.
            return;
        }
        Assert.fail("expected IllegalArgumentException");
    }
}
