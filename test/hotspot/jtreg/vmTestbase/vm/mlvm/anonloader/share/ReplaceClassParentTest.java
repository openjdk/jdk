/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

package vm.mlvm.anonloader.share;

import jdk.internal.org.objectweb.asm.ClassReader;
import vm.mlvm.share.MlvmTest;
import vm.mlvm.share.MlvmTestExecutor;
import vm.mlvm.share.Env;
import vm.share.FileUtils;
import vm.share.UnsafeAccess;
import vm.share.options.Option;

/**
 * This is a base class for kind of tests, which modify the parent class name of test dummy class vm.mlvm.share.AnonkTestee01
 * with an arbitrary string, load the modified class using Unsafe.defineAnonymousClass and instantiate it.
 * <p>
 * The tests can extend this class or use it from command-line to provide the actual data:
 * <ul>
 *   <li>new parent class name,
 *   <li>optionally the list of expected exceptions to be thrown during class loading and instantiation
 *       (see {@link vm.mlvm.share.MlvmTest#setRequiredExceptions(Class<? extends Throwable>... classes)} for details)
 * </ul>
 */

public class ReplaceClassParentTest extends MlvmTest {

    @Option(name = "newParent", default_value = "", description = "String to replace the name of the parent class of the testee")
    private String newParentOpt;

    public void setReplaceParent(String newParent) {
        newParentOpt = newParent;
    }

    private static final Class<?> TESTEE_CLASS = AnonkTestee01.class;

    public ReplaceClassParentTest() {
    }

    public boolean run() throws Throwable {
        byte[] classBytes = FileUtils.readClass(TESTEE_CLASS.getName());
        ClassReader reader = new ClassReader(classBytes);
        int superclassNameIdx = reader.readUnsignedShort(reader.header + 4);
        int cpLength = reader.getItemCount();
        if (superclassNameIdx == 0) {
            throw new RuntimeException("Test bug: unable to find super class"
                    + " name index");
        }
        Env.traceDebug("Superclass name CP index: " + superclassNameIdx
                + "; replacing CP entry with '" + newParentOpt + "'");
        // now, construction of cp patch
        Object cpPatch[] = new Object[cpLength];
        cpPatch[superclassNameIdx] = newParentOpt;
        // define and instantiate
        UnsafeAccess.unsafe.defineAnonymousClass(TESTEE_CLASS, classBytes,
                cpPatch).newInstance();
        // Whether test should pass or fail should be specified via requireExceptions mechanism in MlvmTest
        return true;

    }

    public static void main(String[] args) {
        MlvmTestExecutor.launch(args);
    }
}
