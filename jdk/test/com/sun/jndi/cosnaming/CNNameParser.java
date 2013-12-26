/*
 * Copyright (c) 2007, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4238914
 * @summary Tests that JNDI/COS naming parser supports the syntax
 * defined in the new INS standard.
 */

import javax.naming.*;

public class CNNameParser {

    public static void main(String[] args) throws Exception {

        NameParser parser = new com.sun.jndi.cosnaming.CNNameParser();

        for (int i = 0; i < compounds.length; i++) {
            checkCompound(parser, compounds[i], compoundComps[i]);
        }
    }

    private static void checkName(Name name, String[] comps) throws Exception {
        if (name.size() != comps.length) {
            throw new Exception(
                "test failed; incorrect component count in " + name + "; " +
                "expecting " + comps.length + " got " + name.size());
        }
        for (int i = 0; i < name.size(); i++) {
            if (!comps[i].equals(name.get(i))) {
                throw new Exception (
                    "test failed; invalid component in " + name + "; " +
                    "expecting '" + comps[i] + "' got '" + name.get(i) + "'");
            }
        }
    }

    private static void checkCompound(NameParser parser,
        String input, String[] comps) throws Exception {
        checkName(parser.parse(input), comps);
    }

    private static final String[] compounds = {
        "a/b/c",
        "a.b/c.d",
        "a",
        ".",
        "a.",
        "c.d",
        ".e",
        "a/x\\/y\\/z/b",
        "a\\.b.c\\.d/e.f",
        "a/b\\\\/c",
        "x\\\\.y",
        "x\\.y",
        "x.\\\\y",
        "x.y\\\\",
        "\\\\x.y",
        "a.b\\.c/d"
    };

    private static final String[][] compoundComps = {
        {"a", "b", "c"},
        {"a.b", "c.d"},
        {"a"},
        {"."},
        {"a"},
        {"c.d"},
        {".e"},
        {"a", "x\\/y\\/z", "b"},
        {"a\\.b.c\\.d", "e.f"},
        {"a", "b\\\\", "c"},
        {"x\\\\.y"},
        {"x\\.y"},
        {"x.\\\\y"},
        {"x.y\\\\"},
        {"\\\\x.y"},
        {"a.b\\.c", "d"},
    };
}
