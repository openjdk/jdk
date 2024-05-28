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
 * @bug 8331291
 * @summary Testing Attributes API.
 * @run junit AttributesTest
 */
import java.lang.classfile.AttributeMapper;
import java.lang.classfile.Attributes;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.TemporaryConstantPool;

class AttributesTest {

    @Test
    void testAttributesMapping() throws Exception {
        var cp = TemporaryConstantPool.INSTANCE;
        for (Field f : Attributes.class.getDeclaredFields()) {
            if (f.getName().startsWith("NAME_") && f.getType() == String.class) {
                Utf8Entry attrName = cp.utf8Entry((String)f.get(null));
                AttributeMapper<?> mapper = BoundAttribute.standardAttribute(attrName);
                assertNotNull(mapper, attrName.stringValue() + " 0x" + Integer.toHexString(attrName.hashCode()));
                assertEquals(attrName.stringValue(), mapper.name());
            }
        }
    }
}
