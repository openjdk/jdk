/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test CodelistTest
 * @bug 8054889
 * @library /testlibrary
 * @modules java.base/sun.misc
 *          java.compiler
 *          java.management
 *          jdk.jvmstat/sun.jvmstat.monitor
 * @build jdk.test.lib.*
 * @build jdk.test.lib.dcmd.*
 * @build MethodIdentifierParser
 * @run testng CodelistTest
 * @summary Test of diagnostic command Compiler.codelist
 */

import org.testng.annotations.Test;
import org.testng.Assert;

import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.JMXExecutor;

import java.lang.reflect.Method;

public class CodelistTest {

    /**
     * This test calls Jcmd (diagnostic command tool) Compiler.codelist and then parses the output,
     * making sure that the first methods in the list is valid by reflection.
     *
     * Output example:
     *
     * 6 0 java.lang.System.arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V [0x00007f7b49200910, 0x00007f7b49200aa0 - 0x00007f7b49200d30]
     * 2 3 java.lang.String.indexOf(II)I [0x00007f7b49200d90, 0x00007f7b49200f60 - 0x00007f7b49201490]
     * 7 3 java.lang.Math.min(II)I [0x00007f7b4922f010, 0x00007f7b4922f180 - 0x00007f7b4922f338]
     * 8 3 java.lang.String.equals(Ljava/lang/Object;)Z [0x00007f7b4922fb10, 0x00007f7b4922fd40 - 0x00007f7b49230698]
     * 9 3 java.lang.AbstractStringBuilder.ensureCapacityInternal(I)V [0x00007f7b49232010, 0x00007f7b492321a0 - 0x00007f7b49232510]
     * 10 1 java.lang.Object.<init>()V [0x00007f7b49233e90, 0x00007f7b49233fe0 - 0x00007f7b49234118]
     *
     */

    public void run(CommandExecutor executor) {
        int ok   = 0;
        int fail = 0;

        // Get output from dcmd (diagnostic command)
        OutputAnalyzer output = executor.execute("Compiler.codelist");

        // Grab a method name from the output
        int count = 0;

        for (String line : output.asLines()) {
            count++;

            String[] parts = line.split(" ");
            // int compileID = Integer.parseInt(parts[0]);
            // int compileLevel = Integer.parseInt(parts[1]);
            String methodPrintedInLogFormat = parts[2];

            // skip inits, clinits, methodHandles and getUnsafe -
            // they can not be reflected
            if (methodPrintedInLogFormat.contains("<init>")) {
                continue;
            }
            if (methodPrintedInLogFormat.contains("<clinit>")) {
                continue;
            }
            if (methodPrintedInLogFormat.contains("MethodHandle")) {
                continue;
            }
            if (methodPrintedInLogFormat.contains("sun.misc.Unsafe.getUnsafe")) {
                continue;
            }
            if (methodPrintedInLogFormat.contains("jdk.internal.misc.Unsafe.getUnsafe")) {
                continue;
            }

            MethodIdentifierParser mf = new MethodIdentifierParser(methodPrintedInLogFormat);
            Method m = null;
            try {
                m = mf.getMethod();
            } catch (NoSuchMethodException e) {
                m = null;
            } catch (ClassNotFoundException e) {
                Assert.fail("Test error: Caught unexpected exception", e);
            }
            if (m == null) {
                Assert.fail("Test failed on: " + methodPrintedInLogFormat);
            }
            if (count > 10) {
                // Testing 10 entries is enough. Lets not waste time.
                break;
            }
        }
    }

    @Test
    public void jmx() {
        run(new JMXExecutor());
    }
}
