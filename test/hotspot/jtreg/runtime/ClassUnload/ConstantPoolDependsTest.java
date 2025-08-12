/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test ConstantPoolDependsTest
 * @bug 8210094
 * @summary Create ClassLoader dependency from initiating loader to class loader through constant pool reference
 * @requires vm.opt.final.ClassUnloading
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @compile p2/c2.java MyDiffClassLoader.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -Xmn8m -XX:+UnlockDiagnosticVMOptions -Xlog:class+unload -XX:+WhiteBoxAPI ConstantPoolDependsTest
 */

import jdk.test.whitebox.WhiteBox;
import jdk.test.lib.classloader.ClassUnloadCommon;

import java.lang.ref.Reference;
import java.util.List;
import java.util.Set;
public class ConstantPoolDependsTest {
    public static WhiteBox wb = WhiteBox.getWhiteBox();
    public static final String MY_TEST = "ConstantPoolDependsTest$c1c";

    public static class c1c {
        private void test() throws Exception {
            // ConstantPool.klass_at_impl loads through constant pool and creates dependency
            p2.c2 c2_obj = new p2.c2();
            c2_obj.method2();
        }

        public c1c () throws Exception {
            test();
            ClassUnloadCommon.triggerUnloading();  // should not unload anything
            test();
            ClassUnloadCommon.triggerUnloading();  // should not unload anything
        }
    }

    static void test() throws Throwable {

        // now use the same loader to load class MyTest
        Class MyTest_class = new MyDiffClassLoader(MY_TEST).loadClass(MY_TEST);

        try {
            // Call MyTest to load p2.c2 twice and call p2.c2.method2
            MyTest_class.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Test FAILED if NoSuchMethodException is thrown");
        }
        ClassUnloadCommon.triggerUnloading();  // should not unload anything
        ClassUnloadCommon.failIf(!wb.isClassAlive(MY_TEST), "should not be unloaded");
        ClassUnloadCommon.failIf(!wb.isClassAlive("p2.c2"), "should not be unloaded");
        // Should not unload anything before here because MyTest_class is kept alive by the following fence.
        Reference.reachabilityFence(MyTest_class);
    }

    public static void main(String args[]) throws Throwable {
        test();
        Set<String> aliveClasses = ClassUnloadCommon.triggerUnloading(List.of(MY_TEST, "p2.c2"));
        ClassUnloadCommon.failIf(!aliveClasses.isEmpty(), "should be unloaded: " + aliveClasses);
    }
}
