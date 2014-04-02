/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8025524 8031625
 * @summary Test for constructor name which should be a non-qualified name.
 * @author Bhavesh Patel
 * @library ../lib/
 * @build JavadocTester TestConstructors
 * @run main TestConstructors
 */

public class TestConstructors extends JavadocTester {

    private static final String BUG_ID = "8025524";

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + FS + "pkg1" + FS + "Outer.html",
            "<dt><span class=\"seeLabel\">See Also:</span></dt>" + NL +
            "<dd><a href=\"../pkg1/Outer.Inner.html#Inner--\"><code>Inner()</code></a>, " + NL +
            "<a href=\"../pkg1/Outer.Inner.html#Inner-int-\"><code>Inner(int)</code></a>, " + NL +
            "<a href=\"../pkg1/Outer.Inner.NestedInner.html#NestedInner--\"><code>NestedInner()</code></a>, " + NL +
            "<a href=\"../pkg1/Outer.Inner.NestedInner.html#NestedInner-int-\"><code>NestedInner(int)</code></a>, " + NL +
            "<a href=\"../pkg1/Outer.html#Outer--\"><code>Outer()</code></a>, " + NL +
            "<a href=\"../pkg1/Outer.html#Outer-int-\"><code>Outer(int)</code></a>"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.html",
            "Link: <a href=\"../pkg1/Outer.Inner.html#Inner--\"><code>Inner()</code></a>, " +
            "<a href=\"../pkg1/Outer.html#Outer-int-\"><code>Outer(int)</code></a>, " +
            "<a href=\"../pkg1/Outer.Inner.NestedInner.html#NestedInner-int-\"><code>" +
            "NestedInner(int)</code></a>"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.html",
            "<a href=\"../pkg1/Outer.html#Outer--\">Outer</a></span>()"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.html",
            "<a name=\"Outer--\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.html",
            "<a href=\"../pkg1/Outer.html#Outer-int-\">Outer</a></span>(int&nbsp;i)"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.html",
            "<a name=\"Outer-int-\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.Inner.html",
            "<a href=\"../pkg1/Outer.Inner.html#Inner--\">Inner</a></span>()"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.Inner.html",
            "<a name=\"Inner--\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.Inner.html",
            "<a href=\"../pkg1/Outer.Inner.html#Inner-int-\">Inner</a></span>(int&nbsp;i)"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.Inner.html",
            "<a name=\"Inner-int-\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.Inner.NestedInner.html",
            "<a href=\"../pkg1/Outer.Inner.NestedInner.html#NestedInner--\">NestedInner</a></span>()"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.Inner.NestedInner.html",
            "<a name=\"NestedInner--\">"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.Inner.NestedInner.html",
            "<a href=\"../pkg1/Outer.Inner.NestedInner.html#NestedInner-int-\">NestedInner</a></span>(int&nbsp;i)"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.Inner.NestedInner.html",
            "<a name=\"NestedInner-int-\">"
        }
    };

    private static final String[][] NEGATED_TEST = {
        {BUG_ID + FS + "pkg1" + FS + "Outer.Inner.html",
            "Outer.Inner--"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.Inner.html",
            "Outer.Inner-int-"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.Inner.NestedInner.html",
            "Outer.Inner.NestedInner--"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.Inner.NestedInner.html",
            "Outer.Inner.NestedInner-int-"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.html",
            "<a href=\"../pkg1/Outer.Inner.html#Outer.Inner--\"><code>Outer.Inner()</code></a>"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.html",
            "<a href=\"../pkg1/Outer.Inner.html#Outer.Inner-int-\"><code>Outer.Inner(int)</code></a>"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.html",
            "<a href=\"../pkg1/Outer.Inner.NestedInner.html#Outer.Inner.NestedInner--\"><code>Outer.Inner.NestedInner()</code></a>"
        },
        {BUG_ID + FS + "pkg1" + FS + "Outer.html",
            "<a href=\"../pkg1/Outer.Inner.NestedInner.html#Outer.Inner.NestedInner-int-\"><code>Outer.Inner.NestedInner(int)</code></a>"
        }
    };

    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg1"
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) throws Exception {
        TestConstructors tester = new TestConstructors();
        run(tester, ARGS, TEST, NEGATED_TEST);
        tester.printSummary();
    }

    /**
     * {@inheritDoc}
     */
    public String getBugId() {
        return BUG_ID;
    }

    /**
     * {@inheritDoc}
     */
    public String getBugName() {
        return getClass().getName();
    }
}
