/*
 * Copyright (c) 2000, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;

/**
 * @test
 * @bug     4385429
 * @summary Certain legacy chained exceptions throw IllegalArgumentException
 *          upon deserialization if "causative exception" is null.
 * @author  Josh Bloch
 */
public class LegacyChainedExceptionSerialization {
    private static Throwable[] broken = {
        new ClassNotFoundException(),
        new ExceptionInInitializerError(),
        new java.lang.reflect.UndeclaredThrowableException(null),
        new java.lang.reflect.InvocationTargetException(null),
        new java.security.PrivilegedActionException(null),
        new java.awt.print.PrinterIOException(null)
    };

    public static void main(String[] args) throws Exception {
        for (int i=0; i<broken.length; i++)
            test(broken[i]);
    }

    private static void test(Throwable e) throws Exception {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bout);
        out.writeObject(e);
        out.flush();

        ByteArrayInputStream bin =
            new ByteArrayInputStream(bout.toByteArray());
        ObjectInputStream in = new ObjectInputStream(bin);
        Throwable clone = (Throwable) in.readObject();
    }
}
