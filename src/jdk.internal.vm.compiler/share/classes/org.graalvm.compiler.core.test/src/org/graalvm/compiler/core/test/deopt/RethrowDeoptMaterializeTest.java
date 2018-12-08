/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 */


package org.graalvm.compiler.core.test.deopt;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.FrameState;
import org.junit.Test;

public final class RethrowDeoptMaterializeTest extends GraalCompilerTest {

    private static final Object RETURN_VALUE = "1 2 3";
    private static final RuntimeException DUMMY_EXCEPTION = new RuntimeException();

    static class MyException extends RuntimeException {
        private static final long serialVersionUID = 0L;

        MyException(Throwable cause) {
            super(cause);
        }

        @SuppressWarnings("sync-override")
        @Override
        public final Throwable fillInStackTrace() {
            return null;
        }
    }

    public static Object executeDeoptRethrow(int action) {

        try {
            if (action != 0) {
                throw new MyException(DUMMY_EXCEPTION);
            } else if (action == 1) {
                throw new MyException(null);
            }
        } catch (RuntimeException t) {
            Throwable e = t.getCause();
            GraalDirectives.deoptimize();
            if (e != DUMMY_EXCEPTION) {
                throw t;
            }
        }
        return RETURN_VALUE;
    }

    /**
     * This tests that a state with {@link FrameState#rethrowException()} set to true can properly
     * throw an exception that must be rematerialized.
     */
    @Test
    public void testDeoptRethrow() {
        test("executeDeoptRethrow", 1);
    }
}
