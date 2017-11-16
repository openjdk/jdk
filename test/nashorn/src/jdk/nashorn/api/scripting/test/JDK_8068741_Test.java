/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.api.scripting.test;

import javax.script.ScriptEngineFactory;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @test
 * @run testng jdk.nashorn.api.scripting.test.JDK_8068741_Test
 * @bug 8068741
 * @summary javax.script.ScriptEngineFactory.getMethodCallSyntax() spec allows null passed as an object
 */
public class JDK_8068741_Test {
    @Test
    public void testGetMethodCallSyntax() {
        ScriptEngineFactory fac = new NashornScriptEngineFactory();
        checkThrowsNPE(() -> fac.getMethodCallSyntax(null, "foo"));
        checkThrowsNPE(() -> fac.getMethodCallSyntax("obj", null));
        checkThrowsNPE(() -> fac.getMethodCallSyntax("obj", "foo", (String[])null));
        checkThrowsNPE(() -> fac.getMethodCallSyntax("obj", "foo", null, "xyz"));
        checkThrowsNPE(() -> fac.getMethodCallSyntax("obj", "foo", "xyz", null));
    }

    private void checkThrowsNPE(Runnable r) {
        boolean gotNPE = false;
        try {
            r.run();
        } catch (NullPointerException npe) {
            gotNPE = true;
            System.err.println("Got NPE as expected: " + npe);
        }
        Assert.assertTrue(gotNPE);
    }
}
