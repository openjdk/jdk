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

package valueclasses;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;

public class ValueClassHelper {
    static boolean clinit_called_for_BytePair;
    static boolean clinit_called_for_BytePairWrapper;
    static boolean clinit_called_for_BytePairWrapperWrapper;
    static boolean clinit_called_for_CharPair;
    static boolean clinit_called_for_IntegerWrapper;
    static boolean clinit_called_for_ShortPair;
    static boolean clinit_called_for_ShortPairWrapper;

    public static void assertAOTInited_BytePair() {
        new BytePair(1, 2);
        if (clinit_called_for_BytePair == true) {
            throw new RuntimeException("BytePair.<clinit> must not execute, as this clas should be AOT-initialized");
        }
    }

    public static void assertAOTInited_BytePairWrapper() {
        new BytePairWrapper(1, 2);
        if (clinit_called_for_BytePairWrapper == true) {
            throw new RuntimeException("BytePairWrapper.<clinit> must not execute, as this clas should be AOT-initialized");
        }
    }

    public static void assertAOTInited_BytePairWrapperWrapper() {
        new BytePairWrapperWrapper(1, 2);
        if (clinit_called_for_BytePairWrapperWrapper == true) {
            throw new RuntimeException("BytePairWrapperWrapper.<clinit> must not execute, as this clas should be AOT-initialized");
        }
    }

    public static void assertAOTInited_CharPair() {
        new CharPair('a', 'b');
        if (clinit_called_for_CharPair == true) {
            throw new RuntimeException("CharPair.<clinit> must not execute, as this clas should be AOT-initialized");
        }
    }

    public static void assertAOTInited_ShortPair() {
        new ShortPair((short)0, (short)1);
        if (clinit_called_for_ShortPair == true) {
            throw new RuntimeException("ShortPair.<clinit> must not execute, as this clas should be AOT-initialized");
        }
     }

    public static void assertAOTInited_IntegerWrapper() {
        new IntegerWrapper(0);
        if (clinit_called_for_IntegerWrapper == true) {
            throw new RuntimeException("IntegerWrapper.<clinit> must not execute, as this clas should be AOT-initialized");
        }
    }

    public static void assertAOTInited_ShortPairWrapper() {
        new ShortPairWrapper(0, 1);
        if (clinit_called_for_ShortPairWrapper == true) {
            throw new RuntimeException("ShortPairWrapper.<clinit> must not execute, as this clas should be AOT-initialized");
        }
    }

}
