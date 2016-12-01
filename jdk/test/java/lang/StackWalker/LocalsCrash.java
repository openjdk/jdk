/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8147039
 * @summary Test for -Xcomp crash that happened before 8147039 fix
 * @modules java.base/java.lang:open
 * @run testng/othervm -Xcomp LocalsCrash
 */

import org.testng.annotations.*;
import java.lang.reflect.*;
import java.util.List;
import java.util.stream.Collectors;

public class LocalsCrash {
    static Class<?> liveStackFrameClass;
    static Method getStackWalker;

    static {
        try {
            liveStackFrameClass = Class.forName("java.lang.LiveStackFrame");
            getStackWalker = liveStackFrameClass.getMethod("getStackWalker");
            getStackWalker.setAccessible(true);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    private StackWalker walker;

    LocalsCrash() {
        try {
            walker = (StackWalker) getStackWalker.invoke(null);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    public void test00() { doStackWalk(); }

    @Test
    public void test01() { doStackWalk(); }

    private synchronized List<StackWalker.StackFrame> doStackWalk() {
        try {
            // Unused local variables will become dead
            int x = 10;
            char c = 'z';
            String hi = "himom";
            long l = 1000000L;
            double d =  3.1415926;

            return walker.walk(s -> s.collect(Collectors.toList()));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
