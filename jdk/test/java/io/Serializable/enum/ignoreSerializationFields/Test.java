/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
 * @bug 4838379
 * @summary Verify that serialVersionUID and serialPersistentFields
 *          declarations made by enum types and constants are ignored.
 */

import java.io.*;
import java.util.Arrays;

enum Foo {

    foo,
    bar {
        private static final long serialVersionUID = 2L;
        // bar is implemented as an inner class instance, so the following
        // declaration would cause a compile-time error
        // private static final ObjectStreamField[] serialPersistentFields = {
        //    new ObjectStreamField("gub", Float.TYPE)
        // };
    };

    private static final long serialVersionUID = 1L;
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("blargh", Integer.TYPE)
    };
}

public class Test {
    public static void main(String[] args) throws Exception {
        Class[] classes =
            { Foo.class, Foo.foo.getClass(), Foo.bar.getClass() };
        for (int i = 0; i < classes.length; i++) {
            ObjectStreamClass desc = ObjectStreamClass.lookup(classes[i]);
            if (desc.getSerialVersionUID() != 0L) {
                throw new Error(
                    classes[i] + " has non-zero serialVersionUID: " +
                    desc.getSerialVersionUID());
            }
            ObjectStreamField[] fields = desc.getFields();
            if (fields.length > 0) {
                throw new Error(
                    classes[i] + " has non-empty list of fields: " +
                    Arrays.asList(fields));
            }
        }
    }
}
