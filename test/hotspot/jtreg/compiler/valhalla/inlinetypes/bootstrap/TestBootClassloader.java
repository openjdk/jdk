/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.valhalla.inlinetypes.bootstrap;

import java.lang.reflect.Method;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

/*
 * @test
 * @key randomness
 * @bug 8280006
 * @summary Test that field flattening works as expected if value classes of
 *          holder and field were loaded by different class loaders (bootstrap + app).
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile ValueOnBootclasspath.java InstallBootstrapClasses.java TestBootClassloader.java
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm compiler.valhalla.inlinetypes.bootstrap.InstallBootstrapClasses
 * @run main/othervm -Xbootclasspath/a:boot -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:-TieredCompilation -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.bootstrap.TestBootClassloader::test*
 *                   -XX:CompileCommand=inline,*::get* compiler.valhalla.inlinetypes.bootstrap.TestBootClassloader
 */

public class TestBootClassloader {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final int COMP_LEVEL_FULL_OPTIMIZATION = 4;

    @LooselyConsistentValue
    static value class Wrapper1 {
        @NullRestricted
        ValueOnBootclasspath val; // Type will be loaded by boot classloader

        public Wrapper1(ValueOnBootclasspath val) {
            this.val = val;
        }

        Object get() {
            return val.get();
        }
    }

    @LooselyConsistentValue
    static value class Wrapper2 {
        @NullRestricted
        Wrapper1 val;

        public Wrapper2(Wrapper1 val) {
            this.val = val;
        }

        Object get() {
            return val.get();
        }
    }

    static Object test1(Wrapper1 w) {
        return w.get();
    }

    static Object test2(Wrapper2 w) {
        return w.get();
    }

    public static void main(String[] args) throws Exception {
        Wrapper1 wrapper1 = new Wrapper1(new ValueOnBootclasspath());
        Wrapper2 wrapper2 = new Wrapper2(wrapper1);
        for (int i = 0; i < 50_000; ++i) {
            test1(wrapper1);
            test2(wrapper2);
        }
        Method method = TestBootClassloader.class.getDeclaredMethod("test1", Wrapper1.class);
        Asserts.assertTrue(WB.isMethodCompilable(method, COMP_LEVEL_FULL_OPTIMIZATION, false), "Test1 method not compilable");
        Asserts.assertTrue(WB.isMethodCompiled(method), "Test1 method not compiled");

        method = TestBootClassloader.class.getDeclaredMethod("test2", Wrapper2.class);
        Asserts.assertTrue(WB.isMethodCompilable(method, COMP_LEVEL_FULL_OPTIMIZATION, false), "Test2 method not compilable");
        Asserts.assertTrue(WB.isMethodCompiled(method), "Test2 method not compiled");
    }
}
