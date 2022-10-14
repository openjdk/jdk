/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4702233
 * @summary Testing current and old(removed) public fields in AccessibleAction,
 * AccessibleContext, AccessibleRelation, AccessibleRole and AccessibleState.
 * @run main AccessiblePropertiesTest
 */

import java.lang.reflect.Field;

public class AccessiblePropertiesTest {

    private static void checkFields(String className, String[][] fields,
        String[] oldFields) {
        try {
            Class<?> klass = Class.forName(className);

            if (klass.getFields().length != fields.length) {
                throw new RuntimeException("Fields in " + className
                    + " were changed. Test should be updated!");
            }

            for (int i = 0; i < fields.length; ++i) {
                String key = fields[i][0];
                String value = fields[i][1];
                Field field = klass.getDeclaredField(key);
                String current = field.get(String.class).toString();

                if (!current.equals(value)) {
                    throw new RuntimeException(
                        "Field " + field.getName() + " current value=" + current
                        + " , expected value=" + value);
                }
            }

            for (int i = 0; i < oldFields.length; ++i) {
                String key = oldFields[i];

                try {
                    klass.getDeclaredField(key);

                    throw new RuntimeException(key + " exists in " + klass);
                } catch (NoSuchFieldException ignored) {
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        checkFields(AccessibleActionConstants.CLASS_NAME,
            AccessibleActionConstants.FIELDS,
            AccessibleActionConstants.OLD_FIELDS);

        checkFields(AccessibleRelationConstants.CLASS_NAME,
            AccessibleRelationConstants.FIELDS,
            AccessibleRelationConstants.OLD_FIELDS);

        checkFields(AccessibleRoleConstants.CLASS_NAME,
            AccessibleRoleConstants.FIELDS, AccessibleRoleConstants.OLD_FIELDS);

        checkFields(AccessibleStateConstants.CLASS_NAME,
            AccessibleStateConstants.FIELDS,
            AccessibleStateConstants.OLD_FIELDS);

        checkFields(AccessibleContextConstants.CLASS_NAME,
            AccessibleContextConstants.FIELDS,
            AccessibleContextConstants.OLD_FIELDS);
    }
}

