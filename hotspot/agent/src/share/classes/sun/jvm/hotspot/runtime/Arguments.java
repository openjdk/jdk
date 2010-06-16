/*
 * Copyright (c) 2004, 2006, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.runtime;

import java.util.*;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

/** This class represent VM's Arguments class -- command line args, flags etc.*/
public class Arguments {
    static {
        VM.registerVMInitializedObserver(new Observer() {
            public void update(Observable o, Object data) {
                initialize(VM.getVM().getTypeDataBase());
            }
        });
    }

    public static String getJVMFlags() {
        return buildString(jvmFlagsField, jvmFlagsCount);
    }

    public static String getJVMArgs() {
        return buildString(jvmArgsField, jvmArgsCount);
    }

    public static String getJavaCommand() {
        return getString(javaCommandField);
    }

    // Internals only below this point

    // Fields
    private static AddressField jvmFlagsField;
    private static AddressField jvmArgsField;
    private static AddressField javaCommandField;
    private static long jvmFlagsCount;
    private static long jvmArgsCount;

    private static synchronized void initialize(TypeDataBase db) {
        Type argumentsType = db.lookupType("Arguments");
        jvmFlagsField = argumentsType.getAddressField("_jvm_flags_array");
        jvmArgsField = argumentsType.getAddressField("_jvm_args_array");
        javaCommandField = argumentsType.getAddressField("_java_command");

        jvmArgsCount = argumentsType.getCIntegerField("_num_jvm_args").getValue();
        jvmFlagsCount = argumentsType.getCIntegerField("_num_jvm_flags").getValue();
    }

    private static String buildString(AddressField arrayField, long count) {
        StringBuilder sb = new StringBuilder();
        if (count > 0) {
            sb.append(getStringAt(arrayField, 0));
            for (long i = 1; i < count; i++) {
                sb.append(" ");
                sb.append(getStringAt(arrayField, i));
            }
        }
        return sb.toString();
    }

    private static String getString(AddressField field) {
        Address addr = field.getAddress();
        return CStringUtilities.getString(addr);
    }

    private static String getStringAt(AddressField field, long index) {
        Address addr = field.getAddress();
        return CStringUtilities.getString(addr.getAddressAt(index * VM.getVM().getAddressSize()));
    }
}
