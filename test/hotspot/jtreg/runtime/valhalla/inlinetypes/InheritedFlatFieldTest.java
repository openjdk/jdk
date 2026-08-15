/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package runtime.valhalla.inlinetypes;

import java.lang.reflect.Field;

import jdk.internal.vm.annotation.NullRestricted;
import jdk.test.lib.Asserts;

/*
 * @test
 * @summary Test JNI access to an inherited flat field
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @run main/othervm/native --enable-native-access=ALL-UNNAMED
 *                          runtime.valhalla.inlinetypes.InheritedFlatFieldTest
 */
public class InheritedFlatFieldTest {
    static {
        System.loadLibrary("InheritedFlatFieldTest");
    }

    static value class Value {
        int x;

        Value(int x) {
            this.x = x;
        }
    }

    static class BaseHolder {
        @NullRestricted
        Value baseValue;

        BaseHolder() {
            baseValue = new Value(11);
            super();
        }
    }

    static class DerivedHolder extends BaseHolder {
        @NullRestricted
        Value derivedValue;

        DerivedHolder() {
            derivedValue = new Value(22);
            super();
        }
    }

    private static native Value readBaseValue(Object obj);
    private static native Value readDerivedValue(Object obj);
    private static native void writeBaseValue(Object obj, Value value);

    public static void main(String[] args) throws Exception {
        BaseHolder base = new BaseHolder();
        Value baseRead = readBaseValue(base);
        Asserts.assertNotNull(baseRead);
        Asserts.assertEquals(baseRead.x, 11, "Unexpected base class field JNI read");

        Value baseReplacement = new Value(33);
        writeBaseValue(base, baseReplacement);
        Asserts.assertEquals(base.baseValue.x, 33, "Base class JNI write did not update base field");
        Asserts.assertEquals(readBaseValue(base).x, 33, "Base class JNI read after write returned wrong value");

        DerivedHolder derived = new DerivedHolder();
        Value derivedRead = readDerivedValue(derived);
        Asserts.assertNotNull(derivedRead);
        Asserts.assertEquals(derivedRead.x, 22, "Unexpected subclass field JNI read");

        Value inheritedRead = readBaseValue(derived);
        Asserts.assertNotNull(inheritedRead);
        Asserts.assertEquals(inheritedRead.x, 11, "Unexpected inherited base class field JNI read");

        Value derivedReplacement = new Value(44);
        writeBaseValue(derived, derivedReplacement);
        Asserts.assertEquals(derived.baseValue.x, 44, "JNI write did not update base field");
        Asserts.assertEquals(readBaseValue(derived).x, 44, "JNI read after write returned wrong value");
        Asserts.assertEquals(readDerivedValue(derived).x, 22, "Subclass field should be unchanged");
    }
}
