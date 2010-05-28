/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.*;
import java.io.*;

/*
 * @test
 * @bug 4341304 4485668 4966728
 * @summary Test that methods readResolve and writeReplace show
 * up in serialized-form.html the same way that readObject and writeObject do.
 * If the doclet includes readResolve and writeReplace in the serialized-form
 * documentation that same way the it includes readObject and writeObject, the
 * test passes.  This also tests that throws tag information is correctly shown
 * in the serialized form page.
 * Make sure see tags work in serialized form.
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestSerializedForm
 * @run main TestSerializedForm
 */

public class TestSerializedForm extends JavadocTester implements Serializable {

    private static final String BUG_ID = "4341304-4485668-4966728";

    private static final String[][] TEST = {
        {BUG_ID + FS + "serialized-form.html",
            "protected java.lang.Object <STRONG>readResolve</STRONG>()"},
        {BUG_ID + FS + "serialized-form.html",
            "protected java.lang.Object <STRONG>writeReplace</STRONG>()"},
        {BUG_ID + FS + "serialized-form.html",
            "protected java.lang.Object <STRONG>readObjectNoData</STRONG>()"},
        {BUG_ID + FS + "serialized-form.html",
            "See Also"},
    };

    private static final String[][] NEGATED_TEST = NO_TEST;
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR,
        SRC_DIR + FS + "TestSerializedForm.java"
    };

    /**
     * @serial
     * @see TestSerializedForm
     */
    public final int SERIALIZABLE_CONSTANT = 1;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestSerializedForm tester = new TestSerializedForm();
        int actualExitCode = run(tester, ARGS, TEST, NEGATED_TEST);
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

    /**
     * @throws IOException when there is an I/O error.
     * @serial
     */
    private void readObject(ObjectInputStream s) {}

    /**
     * @throws IOException when there is an I/O error.
     * @serial
     */
    private void writeObject(ObjectOutputStream s) {}

    /**
     * @throws IOException when there is an I/O error.
     * @serialData This is a serial data comment.
     */
    protected Object readResolve(){return null;}

    /**
     * @throws IOException when there is an I/O error.
     * @serialData This is a serial data comment.
     */
    protected Object writeReplace(){return null;}

    /**
     * @throws IOException when there is an I/O error.
     * @serialData This is a serial data comment.
     */
    protected Object readObjectNoData() {
        return null;
    }

}
