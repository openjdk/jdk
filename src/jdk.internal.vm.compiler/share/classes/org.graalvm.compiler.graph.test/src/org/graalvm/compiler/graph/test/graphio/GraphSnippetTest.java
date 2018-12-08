/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.graph.test.graphio;

import java.io.File;
import java.lang.reflect.Method;
import static org.junit.Assert.assertTrue;
import org.junit.Assume;
import org.junit.Test;

public class GraphSnippetTest {
    @SuppressWarnings({"deprecation", "unused"})
    @Test
    public void dumpTheFile() throws Exception {
        Class<?> snippets = null;
        try {
            snippets = Class.forName("org.graalvm.graphio.GraphSnippets");
        } catch (ClassNotFoundException notFound) {
            Assume.assumeNoException("The snippets class has to be around", notFound);
        }
        Method dump = null;
        try {
            dump = snippets.getDeclaredMethod("dump", File.class);
            dump.setAccessible(true);
        } catch (RuntimeException ex) {
            Assume.assumeTrue("Only run the test, if the method is accessible", dump != null && dump.isAccessible());
        }
        File diamond = File.createTempFile("diamond", ".bgv");
        dump.invoke(null, diamond);
        assertTrue("File .bgv created: " + diamond, diamond.length() > 50);
    }
}
