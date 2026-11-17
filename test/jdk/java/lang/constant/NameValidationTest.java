/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8215510
 * @compile NameValidationTest.java
 * @run junit NameValidationTest
 * @summary unit tests for verifying member names
 */

import java.lang.constant.*;

import static java.lang.constant.DirectMethodHandleDesc.*;
import static java.lang.constant.ConstantDescs.*;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

public class NameValidationTest {

    private static final String[] badMemberNames = new String[] {"xx.xx", "zz;zz", "[l", "aa/aa", "<cinit>"};
    private static final String[] goodMemberNames = new String[] {"<clinit>", "<init>", "3", "~", "$", "qq"};

    private static final String[] badClassNames = new String[] {"zz;zz", "[l", "aa/aa", ".", "a..b"};
    private static final String[] goodClassNames = new String[] {"3", "~", "$", "qq", "a.a"};

    @Test
    public void testMemberNames() {
        DirectMethodHandleDesc mh = MethodHandleDesc.of(Kind.VIRTUAL, CD_String, "isEmpty", "()Z");
        for (String badName : badMemberNames) {
            assertThrows(IllegalArgumentException.class, () -> memberNamesHelper(badName, mh, CD_int, null), badName);
            assertThrows(IllegalArgumentException.class, () -> memberNamesHelper(badName, mh, CD_int, new ConstantDesc[0]), badName);
        }

        for (String goodName : goodMemberNames) {
            memberNamesHelper(goodName, mh, CD_int, null);
            memberNamesHelper(goodName, mh, CD_int, new ConstantDesc[0]);
        }
    }

    private void memberNamesHelper(String constantName,
                               DirectMethodHandleDesc bootstrapMethod,
                               ClassDesc constantType,
                               ConstantDesc... bootstrapArgs) {
        if (bootstrapArgs == null) {
            DynamicConstantDesc.ofNamed(bootstrapMethod, constantName, constantType);
        } else {
            DynamicConstantDesc.ofNamed(bootstrapMethod, constantName, constantType, bootstrapArgs);
        }
    }

    @Test
    public void testClassNames() {
        for (String badName : badClassNames) {
            assertThrows(IllegalArgumentException.class, () -> ClassDesc.of(badName), badName);
        }

        for (String goodName : goodClassNames) {
            ClassDesc.of(goodName);
        }
    }
}
