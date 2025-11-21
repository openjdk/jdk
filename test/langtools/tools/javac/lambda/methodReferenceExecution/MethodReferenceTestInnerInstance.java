/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8003639
 * @summary convert lambda testng tests to jtreg and add them
 * @run junit MethodReferenceTestInnerInstance
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * @author Robert Field
 */

public class MethodReferenceTestInnerInstance {

    @Test
    public void testMethodReferenceInnerInstance() {
        cia().cib().testMethodReferenceInstance();
    }

    @Test
    public void testMethodReferenceInnerExternal() {
        cia().cib().testMethodReferenceExternal();
    }

    interface SI {
        String m(Integer a);
    }

    class CIA {

        String xI(Integer i) {
            return "xI:" + i;
        }

        public class CIB {

            public void testMethodReferenceInstance() {
                SI q;

                q = CIA.this::xI;
                assertEquals("xI:55", q.m(55));
            }

            public void testMethodReferenceExternal() {
                SI q;

                q = (new E())::xI;
                assertEquals("ExI:77", q.m(77));
            }
        }

        CIB cib() {
            return new CIB();
        }

        class E {

            String xI(Integer i) {
                return "ExI:" + i;
            }
        }

    }

    CIA cia() {
        return new CIA();
    }
}
