/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8011805
 * @summary Update sun.tools.java class file reading/writing support to include
 *   the new constant pool entries (including invokedynamic)
 * @modules jdk.rmic/sun.rmi.rmic
 *          jdk.rmic/sun.tools.java
 * @run main CFCTest
 */

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import sun.tools.java.ClassDeclaration;
import sun.tools.java.Identifier;
import sun.rmi.rmic.BatchEnvironment;

public class CFCTest {

    /* Constant table */
    private static final int CONSTANT_UTF8 = 1;
    private static final int CONSTANT_INTEGER = 3;
    private static final int CONSTANT_FLOAT = 4;
    private static final int CONSTANT_LONG = 5;
    private static final int CONSTANT_DOUBLE = 6;
    private static final int CONSTANT_CLASS = 7;
    private static final int CONSTANT_STRING = 8;
    private static final int CONSTANT_FIELD = 9;
    private static final int CONSTANT_METHOD = 10;
    private static final int CONSTANT_INTERFACEMETHOD = 11;
    private static final int CONSTANT_NAMEANDTYPE = 12;
    private static final int CONSTANT_METHODHANDLE = 15;
    private static final int CONSTANT_METHODTYPE = 16;
    private static final int CONSTANT_INVOKEDYNAMIC = 18;

    String testClassName = this.getClass().getCanonicalName();
    String testClassPath = System.getProperty("test.classes", ".");

    interface I {
        int get();
    }

    public static void main(String[] args) throws Exception {
        new CFCTest().testNewConstants();
    }

    void testNewConstants() throws Exception {
        // Presence of lambda causes new constant pool constant types to be used
        I lam = () -> 88;
        if (lam.get() == 88) {
            System.out.println("Sanity passed: Lambda worked.");
        } else {
            throw new RuntimeException("Sanity failed: bad lambda execution");
        }

        // Verify that all the new constant pool constant types are present
        String clsName = testClassPath + File.separator + testClassName + ".class";
        ClassConstantChecker ccc = new ClassConstantChecker(clsName);
        ccc.checkFound(CONSTANT_METHODHANDLE);
        ccc.checkFound(CONSTANT_METHODTYPE);
        ccc.checkFound(CONSTANT_INVOKEDYNAMIC);

        // Heart of test: read the class file with the new constant types
        exerciseClassDefinition();
        System.out.println("ClassDefinition read without failure.\n");
   }

    /**
     * Failure is seen when getClassDefinition causes class read
     */
    void exerciseClassDefinition() throws Exception {
        BatchEnvironment env = new BatchEnvironment(System.out,
                BatchEnvironment.createClassPath(testClassPath, null),
                null);
        try {
            ClassDeclaration decl = env.getClassDeclaration(
                    Identifier.lookup(testClassName));
            decl.getClassDefinition(env);
        } finally {
            env.flushErrors();
            env.shutdown();
        }
    }

    private class ClassConstantChecker {

        private DataInputStream in;
        private boolean[] found;

        ClassConstantChecker(String clsName) throws IOException {
            in = new DataInputStream(new FileInputStream(clsName));
            found = new boolean[CONSTANT_INVOKEDYNAMIC + 20];
            try {
                check();
            } finally {
                in.close();
            }
        }

        void checkFound(int tag) throws Exception {
            if (found[tag]) {
                System.out.printf("Constant pool tag found: %d\n", tag);
            } else {
                throw new RuntimeException("Insufficient test, constant pool tag NOT found: " + tag);
            }
        }

        private void skip(int n) throws IOException {
            if (in.skipBytes(n) != n) {
                throw new EOFException();
            }
        }

        private void check() throws IOException {
            skip(8); // magic, version
            int count = in.readUnsignedShort();
            for (int i = 1; i < count; i++) {
                int j = i;
                // JVM 4.4 cp_info.tag
                int tag = in.readByte();
                found[tag] = true;
                switch (tag) {
                    case CONSTANT_UTF8:
                        in.readUTF();
                        break;
                    case CONSTANT_LONG:
                    case CONSTANT_DOUBLE:
                        skip(8);
                        break;
                    case CONSTANT_CLASS:
                    case CONSTANT_STRING:
                        skip(2);
                        break;
                    case CONSTANT_INTEGER:
                    case CONSTANT_FLOAT:
                    case CONSTANT_FIELD:
                    case CONSTANT_METHOD:
                    case CONSTANT_INTERFACEMETHOD:
                    case CONSTANT_NAMEANDTYPE:
                        skip(4);
                        break;

                    case CONSTANT_METHODHANDLE:
                        skip(3);
                        break;
                    case CONSTANT_METHODTYPE:
                        skip(2);
                        break;
                    case CONSTANT_INVOKEDYNAMIC:
                        skip(4);
                        break;

                    case 0:
                    default:
                        throw new ClassFormatError("invalid constant type: " + tag);
                }
            }
        }
    }
}
