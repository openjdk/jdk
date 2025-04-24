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

/*
 * @test
 * @bug 8334165
 * @summary Test that jmx.serial.form is not recognised.
 *
 * @run main/othervm -Djmx.serial.form=1.0 SerialCompatRemovedTest
 * @run main/othervm SerialCompatRemovedTest
 */

import java.io.*;
import java.util.*;
import javax.management.ObjectName;

public class SerialCompatRemovedTest {

    public static void main(String[] args) throws Exception {
        ObjectStreamClass osc = ObjectStreamClass.lookup(ObjectName.class);
        // Serial form has no fields, uses writeObject, so we should never see
        // non-zero field count here:
        if (osc.getFields().length != 0) {
            throw new Exception("ObjectName using old serial form?: fields: " +
                    Arrays.asList(osc.getFields()));
        }
    }
}

