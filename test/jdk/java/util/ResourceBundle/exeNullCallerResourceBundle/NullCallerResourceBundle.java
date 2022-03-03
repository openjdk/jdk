/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/**
 * @test
 * @bug 8280902
 * @summary Test uses custom launcher that starts VM using JNI that verifies
 *          ResourceBundle::getBundle with null caller class functions properly
 *          using the system class loader unnamed module.  The custom launcher
 *          creates a properties file and passes the VM option to the JNI
 *          functionality for the resource lookup.
 * @library /test/lib
 * @requires os.family != "aix"
 * @run main/native NullCallerResourceBundle
 */

// Test disabled on AIX since we cannot invoke the JVM on the primordial thread.

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;

public class NullCallerResourceBundle {
    public static void main(String[] args) throws IOException {

        // build a properties file for the native test
        var propPath = Path.of(System.getProperty("test.classes"), "NullCallerResource.properties");
        try (var stream = Files.newOutputStream(propPath)) {
            var props = new Properties();
            props.put("message", "Hello!");
            props.save(stream, "Test property list");
        }

        var launcher = Path.of(System.getProperty("test.nativepath"), "NullCallerResourceBundle");
        var classpathAppend = "-Djava.class.path=" + System.getProperty("test.classes");
        var pb = new ProcessBuilder(launcher.toString(), classpathAppend);
        var env = pb.environment();

        var libDir = Platform.libDir().toString();
        var vmDir = Platform.jvmLibDir().toString();

        // set up shared library path
        var sharedLibraryPathEnvName = Platform.sharedLibraryPathVariableName();
        env.compute(sharedLibraryPathEnvName,
                (k, v) -> (v == null) ? libDir : v + File.pathSeparator + libDir);
        env.compute(sharedLibraryPathEnvName,
                (k, v) -> (v == null) ? vmDir : v + File.pathSeparator + vmDir);

        System.out.println("Launching: " + launcher + " shared library path: " +
                env.get(sharedLibraryPathEnvName));
        new OutputAnalyzer(pb.start())
                .outputTo(System.out)
                .errorTo(System.err)
                .shouldHaveExitValue(0);
    }

}

