/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6356530
 * @summary -Xlint:serial does not flag abstract classes with concrete methods/members
 * @compile/fail/ref=FinalVariableAssignedToInCatchBlockTest.out -XDrawDiagnostics FinalVariableAssignedToInCatchBlockTest.java
 */

import java.io.IOException;

public class FinalVariableAssignedToInCatchBlockTest {
    public void m1(int o)
    {
        final int i;
        try {
            if (o == 1) {
                throw new IOException();
            } else if (o == 2) {
                throw new InterruptedException();
            } else {
                throw new Exception();
            }
        } catch (IOException e) {
            i = 1;
        } catch (InterruptedException ie) {
            i = 2;
        } catch (Exception e) {
            i = 3;
        } finally {
            i = 4;
        }
    }

    public void m2(int o)
    {
        final int i;
        try {
            if (o == 1) {
                throw new IOException();
            } else if (o == 2) {
                throw new InterruptedException();
            } else {
                throw new Exception();
            }
        } catch (IOException e) {
            i = 1;
        } catch (InterruptedException ie) {
            i = 2;
        } catch (Exception e) {
            i = 3;
        }
    }

    public void m3(int o) throws Exception
    {
        final int i;
        try {
            if (o == 1) {
                throw new IOException();
            } else if (o == 2) {
                throw new InterruptedException();
            } else {
                throw new Exception();
            }
        } catch (IOException e) {
            i = 1;
        } catch (InterruptedException ie) {
            i = 2;
        }
        i = 3;
    }
}
