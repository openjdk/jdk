/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package compiler.stable;

import sun.hotspot.WhiteBox;

import java.lang.reflect.Method;

public class StableConfiguration {
    static final WhiteBox WB = WhiteBox.getWhiteBox();
    public static final boolean isStableEnabled;
    public static final boolean isServerWithStable;

    static {
        Boolean value = WB.getBooleanVMFlag("FoldStableValues");
        isStableEnabled = (value == null ? false : value);
        isServerWithStable = isStableEnabled && get();
        System.out.println("@Stable:         " + (isStableEnabled ? "enabled" : "disabled"));
        System.out.println("Server Compiler: " + get());
    }

    // The method 'get' below returns true if the method is server compiled
    // and is used by the Stable tests to determine whether methods in
    // general are being server compiled or not as the -XX:+FoldStableValues
    // option is only applicable to -server.
    //
    // On aarch64 we DeOptimize when patching. This means that when the
    // method is compiled as a result of -Xcomp it DeOptimizes immediately.
    // The result is that getMethodCompilationLevel returns 0. This means
    // the method returns true based on java.vm.name.
    //
    // However when the tests are run with -XX:+TieredCompilation and
    // -XX:TieredStopAtLevel=1 this fails because methods will always
    // be client compiled.
    //
    // Solution is to add a simple method 'get1' which should never be
    // DeOpted and use that to determine the compilation level instead.
    static void get1() {
    }

    // ::get() is among immediately compiled methods.
    static boolean get() {
        try {
            get1();
            Method m = StableConfiguration.class.getDeclaredMethod("get1");
            int level = WB.getMethodCompilationLevel(m);
            if (level > 0) {
              return (level == 4);
            } else {
              String javaVM = System.getProperty("java.vm.name", "");
              if (javaVM.contains("Server")) return true;
              if (javaVM.contains("Client")) return false;
              throw new Error("Unknown VM type: "+javaVM);
            }
        } catch (NoSuchMethodException e) {
            throw new Error(e);
        }
    }
}
