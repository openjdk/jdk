/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.util.SerializationUtils;
import jdk.test.lib.hexdump.HexPrinter;
import jdk.test.lib.hexdump.ObjectStreamPrinter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Map;
import java.util.stream.Stream;

/*
 * @test
 * @bug 8331224
 * @summary Test missing class throws CNFE before creating record or setting class fields
 * @library /test/lib
 * @run junit SerialProxyClassNotFound
 */

/**
 * Verify the correct exception (CNFE) is thrown when the class is not found during
 * deserialization. Field values for objects should result in CNFE *before* creating the
 * record or initializing the fields of a class.
 * The issue is exposed by the use of serialization proxy classes; the proxies
 * typically have types that are different from the original object type and
 * result in ClassCastException.
 */
public class SerialProxyClassNotFound implements Serializable {

    private static Stream<Arguments> Cases() {
        return Stream.of(
                Arguments.of(new Record1(Map.of("aaa", new XX()))), // Map uses serial proxy
                Arguments.of(new Class2(Map.of("bbb", new XX()))),  // Map uses serial proxy
                Arguments.of(new Record4(new Class3(new XX()))),       // Class3 uses serial proxy
                Arguments.of(new Class5(new Class3(new XX()))),        // Class3 uses serial proxy
                Arguments.of(new Class6(new Class3(new XX())))         // Class3 uses serial proxy
        );
    }
    @ParameterizedTest
    @MethodSource("Cases")
    void checkForCNFE(Object obj) throws ClassNotFoundException, IOException {
        // A record with field containing a Map with an entry for a class that is not found
        byte[] bytes = SerializationUtils.serialize(obj);

        // Scan bytes looking for "$XX"; replace all occurrences
        boolean replaced = false;
        for (int off = 0; off < bytes.length - 3; off++) {
            if (bytes[off] == '$' && bytes[off + 1] == 'X' && bytes[off + 2] == 'X') {
                // Modify bytes to change name of class to SerialProxyClassNotFound$YY
                bytes[off + 1] = 'Y';
                bytes[off + 2] = 'Y';
                replaced = true;
            }
        }
        if (!replaced) {
            // Not found, Debug dump the bytes to locate the index of the XX class name
            HexPrinter.simple()
                    .formatter(ObjectStreamPrinter.formatter())
                    .dest(System.err)
                    .format(bytes);
            fail("'$XX' of `SerialProxyClassNotFound$XX` not found in serialized bytes ");
        }

        try {
            Object o = SerializationUtils.deserialize(bytes);
            System.out.println("Deserialized obj: " + o);
            HexPrinter.simple()
                    .formatter(ObjectStreamPrinter.formatter())
                    .dest(System.err)
                    .format(bytes);
            fail("deserialize should have thrown ClassNotFoundException");
        } catch (ClassNotFoundException cnfe) {
            assertEquals(cnfe.getMessage(),
                    "SerialProxyClassNotFound$YY",
                    "CNFE message incorrect");
        } catch (IOException ioe) {
            HexPrinter.simple()
                    .formatter(ObjectStreamPrinter.formatter())
                    .dest(System.err)
                    .format(bytes);
            throw ioe;
        }
    }

    // A class with a readily identifiable name
    static class XX implements Serializable { }

    // A record holding a Map holding a reference to a readily identifiable (but deleted) class
    record Record1(Map<String,XX> arg) implements Serializable {};

    // Class holding a Map holding a reference to a (deleted) class
    static class Class2 implements Serializable {
        Map<String, XX> arg;
        Class2(Map<String,XX> arg) {
            this.arg = arg;
        }
        public String toString() {
            return "Class2[arg=" + arg + "]";
        }
    }

    // Class3 a holder of a reference to a class that will be "deleted"
    static class Class3 implements Serializable {
        XX arg;
        Class3(XX arg) {
            this.arg = arg;
        }
        private Object writeReplace() {
            return new Class3Proxy(arg);
        }
        public String toString() {
            return "Class3[arg=" + arg + "]";
        }
    }

    // Serial proxy for Class3
    record Class3Proxy(XX arg) implements Serializable {
        private Object readResolve() {
            return new Class3(arg);
        }
    }

    // Record holding a Class3
    record Record4(Class3 arg) implements Serializable {}

    // Holder class without custom readObject
    // Causes !hasSpecialReadMethod path through OIS.readSerialData
    static class Class5 implements Serializable {
        Class3 arg;
        Class5(Class3 arg) {
            this.arg = arg;
        }
        public String toString() {
            return "Class5[arg=" + arg + "]";
        }
    }

    // Holder class with custom readObject
    // Causes hasSpecialReadMethod path taken through OIS.readSerialData
    static class Class6 implements Serializable {
        Class3 arg;
        Class6(Class3 arg) {
            this.arg = arg;
        }
        private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
            ois.defaultReadObject();
        }
        public String toString() {
            return "Class6[arg=" + arg + "]";
        }
    }

}
