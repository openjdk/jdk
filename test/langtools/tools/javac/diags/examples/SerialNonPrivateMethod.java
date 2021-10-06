/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

// key: compiler.warn.serial.method.not.private
// key: compiler.warn.serial.method.static
// key: compiler.warn.serial.method.unexpected.return.type
// key: compiler.warn.serial.concrete.instance.method
// key: compiler.warn.serial.method.one.arg
// key: compiler.warn.serial.method.parameter.type
// key: compiler.warn.serial.method.no.args
// key: compiler.warn.serial.method.unexpected.exception

// options: -Xlint:serial

import java.io.*;


abstract class SerialNonPrivateMethod implements Serializable {
    private static final long serialVersionUID = 42;

    private static class CustomObjectOutputStream extends ObjectOutputStream {
        public CustomObjectOutputStream() throws IOException,
                                                 SecurityException {}
    }

    // Should be private and have a single argument of type
    // ObjectOutputStream
    void writeObject(CustomObjectOutputStream stream) throws IOException {
        stream.defaultWriteObject();
    }

    // Should be private non-static and have one argument
    private static void readObject(ObjectInputStream stream, int retries)
        throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
    }

    // Should return void
    private int readObjectNoData() throws ObjectStreamException {
        return 42;
    }

    // Should be concrete instance method
    public abstract Object writeReplace() throws ObjectStreamException;

    // Should have no arguments and throw ObjectStreamException
    /*package*/ Object readResolve(int foo)
        throws ReflectiveOperationException { // Checked exception
        return null;
    }
}
