/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;

import jdk.test.lib.*;

/*
 * @test
 * @bug 8050079
 * @summary Compiles a monomorphic call to finalizeObject() on a modified java.lang.Object to test C1 CHA.
 * @library /testlibrary
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          java.base/jdk.internal
 * @ignore 8132924
 * @compile -XDignore.symbol.file java/lang/Object.java TestMonomorphicObjectCall.java
 * @run main TestMonomorphicObjectCall
 */
public class TestMonomorphicObjectCall {

    private static void callFinalize(Object object) throws Throwable {
        // Call modified version of java.lang.Object::finalize() that is
        // not overridden by any subclass. C1 CHA should mark the call site
        // as monomorphic and inline the method.
        object.finalizeObject();
    }

    public static void main(String[] args) throws Throwable {
        if (args.length == 0) {
            byte[] bytecode = Files.readAllBytes(Paths.get(System.getProperty("test.classes") + File.separator +
                "java" + File.separator + "lang" + File.separator + "Object.class"));
            ClassFileInstaller.writeClassToDisk("java.lang.Object", bytecode, "mods/java.base");
            // Execute new instance with modified java.lang.Object
            executeTestJvm();
        } else {
            // Trigger compilation of 'callFinalize'
            callFinalize(new Object());
        }
    }

    public static void executeTestJvm() throws Throwable {
        // Execute test with modified version of java.lang.Object
        // in -Xbootclasspath.
        String[] vmOpts = new String[] {
                "-Xpatch:mods",
                "-Xcomp",
                "-XX:+IgnoreUnrecognizedVMOptions",
                "-XX:-VerifyDependencies",
                "-XX:CompileOnly=TestMonomorphicObjectCall::callFinalize",
                "-XX:CompileOnly=Object::finalizeObject",
                "-XX:TieredStopAtLevel=1",
                TestMonomorphicObjectCall.class.getName(),
                "true"};
        OutputAnalyzer output = ProcessTools.executeTestJvm(vmOpts);
        output.shouldHaveExitValue(0);
    }
}
