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
 * @summary Test SecurityManager methods
 * @run junit Basic
 */

import java.security.AccessControlContext;
import java.security.AccessControlException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Basic {

    @Test
    void testGetClassContext() {
        var sm = new SecurityManager() {
            @Override
            public Class<?>[] getClassContext() {
                return super.getClassContext();
            }
        };
        Class<?>[] stack = sm.getClassContext();
        assertEquals(sm.getClass(), stack[0]);      // currently executing method
        assertEquals(this.getClass(), stack[1]);    // caller
    }

    @Test
    void testGetSecurityContext() {
        var sm = new SecurityManager();
        var acc = (AccessControlContext) sm.getSecurityContext();
        assertThrows(AccessControlException.class,
                     () -> acc.checkPermission(new RuntimePermission("foo")));
        assertNull(acc.getDomainCombiner());
    }

    @Test
    void testGetThreadGroup() {
        var sm = new SecurityManager();
        assertEquals(Thread.currentThread().getThreadGroup(), sm.getThreadGroup());
    }
}