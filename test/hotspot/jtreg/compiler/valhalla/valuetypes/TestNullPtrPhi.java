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

/**
 * @test
 * @bug 8393446
 * @summary [Valhalla] The assert added by JDK-8389623 is too strong
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @run main/othervm -XX:CompileCommand=compileonly,${test.main.class}::test -Xbatch
 *                   ${test.main.class}
 */

package compiler.valhalla.valuetypes;

public class TestNullPtrPhi {

    static value class Val {}

    interface SomeObject {
        Object val();
    }

    // To avoid creating new exception on each call.
    static final RuntimeException EXCEPTION = new RuntimeException();

    static final class NotValue implements SomeObject {
        public Object val() {
            throw EXCEPTION;
        }
    }

    static final class NotNullValue implements SomeObject {
        public Object val() {
            return new Val();
        }
    }

    public static void main(String[] args) {
        SomeObject notVal     = new NotValue();
        SomeObject notNullVal = new NotNullValue();
        for (int i = 0; i < 20_000; i++) {
            boolean notNull = (i & 1) == 0;
            Object result = test(notNull ? notNullVal : notVal);
            if (notNull != (result != null)) {
                throw new RuntimeException("wrong result");
            }
        }
    }

    static Object test(SomeObject obj) {
        Object result = null;
        try {
            result = obj.val();
        } catch (Throwable t) {
            // Keep result == null
        }
        return result;
    }
}
