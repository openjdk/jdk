/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package compiler.jvmci.compilerToVM;

import compiler.jvmci.common.testcases.MultipleImplementer2;
import compiler.jvmci.common.testcases.MultipleImplementersInterface;
import java.util.HashMap;
import java.util.Map;

/**
 * Class contains hard-coded constant pool tables for dummy classes used for
 * jdk.vm.ci.hotspot.CompilerToVM constant pool methods
 */
public class ConstantPoolTestsHelper {

    public enum ConstantTypes {
        CONSTANT_CLASS,
        CONSTANT_FIELDREF,
        CONSTANT_METHODREF,
        CONSTANT_INTERFACEMETHODREF,
        CONSTANT_STRING,
        CONSTANT_INTEGER,
        CONSTANT_FLOAT,
        CONSTANT_LONG,
        CONSTANT_DOUBLE,
        CONSTANT_NAMEANDTYPE,
        CONSTANT_UTF8,
        CONSTANT_METHODHANDLE,
        CONSTANT_METHODTYPE,
        CONSTANT_INVOKEDYNAMIC;
    }

    public enum DummyClasses {
        DUMMY_CLASS(MultipleImplementer2.class, CP_MAP_FOR_CLASS),
        DUMMY_INTERFACE(MultipleImplementersInterface.class, CP_MAP_FOR_INTERFACE);

        public final Class<?> klass;
        public final Map<Integer, ConstantPoolEntry> cp;

        DummyClasses(Class<?> klass, Map<Integer, ConstantPoolEntry> cp) {
            this.klass = klass;
            this.cp = cp;
        }
    }

    public static class ConstantPoolEntry {

        public final ConstantTypes type;
        public final Object value;

        public ConstantPoolEntry(ConstantTypes type, Object value) {
            this.type = type;
            this.value = value;
        }
    }

    private static final Map<Integer, ConstantPoolEntry> CP_MAP_FOR_CLASS
            = new HashMap<>();
    static {
        CP_MAP_FOR_CLASS.put(1, new ConstantPoolEntry(ConstantTypes.CONSTANT_METHODREF, new int[]{22, 68}));
        CP_MAP_FOR_CLASS.put(2, new ConstantPoolEntry(ConstantTypes.CONSTANT_CLASS, 69));
        CP_MAP_FOR_CLASS.put(3, new ConstantPoolEntry(ConstantTypes.CONSTANT_INTEGER, 2147483647));
        CP_MAP_FOR_CLASS.put(4, new ConstantPoolEntry(ConstantTypes.CONSTANT_FIELDREF, new int[]{35, 70}));
        CP_MAP_FOR_CLASS.put(5, new ConstantPoolEntry(ConstantTypes.CONSTANT_LONG, 9223372036854775807L));
        CP_MAP_FOR_CLASS.put(8, new ConstantPoolEntry(ConstantTypes.CONSTANT_FLOAT, 3.4028235E38F));
        CP_MAP_FOR_CLASS.put(10, new ConstantPoolEntry(ConstantTypes.CONSTANT_DOUBLE, 1.7976931348623157E308D));
        CP_MAP_FOR_CLASS.put(13, new ConstantPoolEntry(ConstantTypes.CONSTANT_STRING, 74));
        CP_MAP_FOR_CLASS.put(22, new ConstantPoolEntry(ConstantTypes.CONSTANT_CLASS, 83));
        CP_MAP_FOR_CLASS.put(23, new ConstantPoolEntry(ConstantTypes.CONSTANT_METHODREF, new int[]{22, 84}));
        CP_MAP_FOR_CLASS.put(24, new ConstantPoolEntry(ConstantTypes.CONSTANT_INTERFACEMETHODREF, new int[]{2, 85}));
        CP_MAP_FOR_CLASS.put(26, new ConstantPoolEntry(ConstantTypes.CONSTANT_INVOKEDYNAMIC, new int[]{0, 91}));
        CP_MAP_FOR_CLASS.put(29, new ConstantPoolEntry(ConstantTypes.CONSTANT_FIELDREF, new int[]{35, 94}));
        CP_MAP_FOR_CLASS.put(35, new ConstantPoolEntry(ConstantTypes.CONSTANT_CLASS, 100));
        CP_MAP_FOR_CLASS.put(68, new ConstantPoolEntry(ConstantTypes.CONSTANT_NAMEANDTYPE, new int[]{54, 55}));
        CP_MAP_FOR_CLASS.put(70, new ConstantPoolEntry(ConstantTypes.CONSTANT_NAMEANDTYPE, new int[]{48, 37}));
        CP_MAP_FOR_CLASS.put(84, new ConstantPoolEntry(ConstantTypes.CONSTANT_NAMEANDTYPE, new int[]{59, 55}));
        CP_MAP_FOR_CLASS.put(85, new ConstantPoolEntry(ConstantTypes.CONSTANT_NAMEANDTYPE, new int[]{103, 63}));
        CP_MAP_FOR_CLASS.put(91, new ConstantPoolEntry(ConstantTypes.CONSTANT_NAMEANDTYPE, new int[]{106, 107}));
        CP_MAP_FOR_CLASS.put(94, new ConstantPoolEntry(ConstantTypes.CONSTANT_NAMEANDTYPE, new int[]{36, 37}));
        CP_MAP_FOR_CLASS.put(104, new ConstantPoolEntry(ConstantTypes.CONSTANT_METHODREF, new int[]{110, 111}));
        CP_MAP_FOR_CLASS.put(105, new ConstantPoolEntry(ConstantTypes.CONSTANT_METHODREF, new int[]{35, 112}));
        CP_MAP_FOR_CLASS.put(110, new ConstantPoolEntry(ConstantTypes.CONSTANT_CLASS, 113));
        CP_MAP_FOR_CLASS.put(111, new ConstantPoolEntry(ConstantTypes.CONSTANT_NAMEANDTYPE, new int[]{114, 118}));
        CP_MAP_FOR_CLASS.put(112, new ConstantPoolEntry(ConstantTypes.CONSTANT_NAMEANDTYPE, new int[]{58, 55}));
    }

    private static final Map<Integer, ConstantPoolEntry> CP_MAP_FOR_INTERFACE
            = new HashMap<>();
    static {
        CP_MAP_FOR_INTERFACE.put(1, new ConstantPoolEntry(ConstantTypes.CONSTANT_CLASS, 48));
        CP_MAP_FOR_INTERFACE.put(5, new ConstantPoolEntry(ConstantTypes.CONSTANT_INTERFACEMETHODREF, new int[]{13, 52}));
        CP_MAP_FOR_INTERFACE.put(6, new ConstantPoolEntry(ConstantTypes.CONSTANT_CLASS, 53));
        CP_MAP_FOR_INTERFACE.put(7, new ConstantPoolEntry(ConstantTypes.CONSTANT_INVOKEDYNAMIC, new int[]{0, 58}));
        CP_MAP_FOR_INTERFACE.put(8, new ConstantPoolEntry(ConstantTypes.CONSTANT_METHODREF, new int[]{6, 59}));
        CP_MAP_FOR_INTERFACE.put(9, new ConstantPoolEntry(ConstantTypes.CONSTANT_METHODREF, new int[]{6, 60}));
        CP_MAP_FOR_INTERFACE.put(12, new ConstantPoolEntry(ConstantTypes.CONSTANT_FIELDREF, new int[]{13, 63}));
        CP_MAP_FOR_INTERFACE.put(13, new ConstantPoolEntry(ConstantTypes.CONSTANT_CLASS, 64));
        CP_MAP_FOR_INTERFACE.put(17, new ConstantPoolEntry(ConstantTypes.CONSTANT_INTEGER, 2147483647));
        CP_MAP_FOR_INTERFACE.put(20, new ConstantPoolEntry(ConstantTypes.CONSTANT_LONG, 9223372036854775807l));
        CP_MAP_FOR_INTERFACE.put(24, new ConstantPoolEntry(ConstantTypes.CONSTANT_FLOAT, 3.4028235E38f));
        CP_MAP_FOR_INTERFACE.put(27, new ConstantPoolEntry(ConstantTypes.CONSTANT_DOUBLE, 1.7976931348623157E308d));
        CP_MAP_FOR_INTERFACE.put(31, new ConstantPoolEntry(ConstantTypes.CONSTANT_STRING, 65));
        CP_MAP_FOR_INTERFACE.put(52, new ConstantPoolEntry(ConstantTypes.CONSTANT_NAMEANDTYPE, new int[]{34, 35}));
        CP_MAP_FOR_INTERFACE.put(55, new ConstantPoolEntry(ConstantTypes.CONSTANT_METHODHANDLE, new int[]{6, 67}));
        CP_MAP_FOR_INTERFACE.put(56, new ConstantPoolEntry(ConstantTypes.CONSTANT_METHODTYPE, 35));
        CP_MAP_FOR_INTERFACE.put(57, new ConstantPoolEntry(ConstantTypes.CONSTANT_METHODHANDLE, new int[]{9, 5}));
        CP_MAP_FOR_INTERFACE.put(58, new ConstantPoolEntry(ConstantTypes.CONSTANT_NAMEANDTYPE, new int[]{68, 69}));
        CP_MAP_FOR_INTERFACE.put(59, new ConstantPoolEntry(ConstantTypes.CONSTANT_NAMEANDTYPE, new int[]{70, 71}));
        CP_MAP_FOR_INTERFACE.put(60, new ConstantPoolEntry(ConstantTypes.CONSTANT_NAMEANDTYPE, new int[]{72, 35}));
        CP_MAP_FOR_INTERFACE.put(63, new ConstantPoolEntry(ConstantTypes.CONSTANT_NAMEANDTYPE, new int[]{32, 33}));
        CP_MAP_FOR_INTERFACE.put(67, new ConstantPoolEntry(ConstantTypes.CONSTANT_METHODREF, new int[]{73, 74}));
        CP_MAP_FOR_INTERFACE.put(73, new ConstantPoolEntry(ConstantTypes.CONSTANT_CLASS, 75));
        CP_MAP_FOR_INTERFACE.put(74, new ConstantPoolEntry(ConstantTypes.CONSTANT_NAMEANDTYPE, new int[]{76, 80}));
        CP_MAP_FOR_INTERFACE.put(77, new ConstantPoolEntry(ConstantTypes.CONSTANT_CLASS, 82));
    }
}
