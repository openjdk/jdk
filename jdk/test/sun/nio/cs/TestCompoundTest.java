/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 6427981
 * @summary Check the Compound_Text's canonical name and its aliases
 */

import java.nio.charset.*;
import java.util.Set;

public class TestCompoundTest {

    public static void main(String args[]) throws Exception
    {
        if (System.getProperty("os.name").startsWith("Windows"))
            return;
        Charset cs = Charset.forName("COMPOUND_TEXT");
        if (!cs.name().startsWith("x-"))
            throw new RuntimeException("FAILED: name does not start with x-");
        Set<String> aliases = cs.aliases();
        if (!aliases.contains("COMPOUND_TEXT") ||
            !aliases.contains("x-compound-text") ||
            !aliases.contains("x11-compound_text"))
            throw new RuntimeException("FAILED: alias name is missing");
    }
}
