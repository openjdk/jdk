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
 * @bug 8318026
 * @summary Test of diagnostic command VM.inspect
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run testng/othervm -Dvminspect.enabled=true -XX:+UnlockDiagnosticVMOptions VMInspectTest 
 */

/*
 * @test
 * @bug 8318026
 * @summary Test of diagnostic command VM.inspect
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run testng/othervm -Dvminspect.enabled=false VMInspectTest
 */

import org.testng.annotations.Test;
import org.testng.Assert;

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.PidJcmdExecutor;

import java.math.BigInteger;
import java.io.IOException;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VMInspectTest {

    // - locked <0x00000007dd0135e8> (a MyLock)
    static Pattern waiting_on_mylock =
        Pattern.compile("- waiting on \\<0x(\\p{XDigit}+)\\> \\(a MyLock\\)");

    //  tid=0x0000153418029c20
    static Pattern thread_id_line =
        Pattern.compile(" tid=0x(\\p{XDigit}+) ");

    public void run(CommandExecutor executor) throws ClassNotFoundException {
        DcmdTestClass test = new DcmdTestClass();
        test.work();
        BigInteger ptr = null;
        OutputAnalyzer output = null;

        // Testing VM.inspect requires UnlockDiagnosticVMOptions or a debug JVM.
        // This test runs with a System Property set as a hint whether UnlockDiagnosticVMOptions is set.
        boolean enabled = Platform.isDebugBuild() || Boolean.getBoolean("vminspect.enabled");
        System.out.println("VM.inspect should be enabled = " + enabled);
        if (!enabled) {
            // Use any pointer, command should be refused:
            output = executor.execute("VM.inspect 0x0");
            output.shouldContain("-XX:+UnlockDiagnosticVMOptions is required");
            return; // no more testing
        }

        // Tests where enabled:
        output = executor.execute("help");
        output.shouldNotContain("VM.inspect"); // VM.inspect is not promoted in help
        output = executor.execute("help VM.inspect");
        output.shouldContain("Syntax : VM.inspect"); // but help is available

        // Test VM.inspect:
        testFind(executor);
    }

    public void testFind(CommandExecutor executor) {
        boolean testMisaligned = true;
        OutputAnalyzer output = executor.execute("VM.inspect");
        output.shouldContain("Usage: VM.inspect");
        // Find and test a thread id:
        OutputAnalyzer threadPrintOutput = executor.execute("Thread.print");
        BigInteger ptr = findPointer(threadPrintOutput, thread_id_line, 1);
        output = executor.execute("VM.inspect " + pointerText(ptr));
        output.shouldContain(" is a thread");
        // verbose shows output like:
        // "main" #1 [17235] prio=5 os_prio=0 cpu=1265.79ms elapsed=6.12s tid=0x000014e37802bd80 nid=17235 in Object.wait()  [0x000014e3817d4000]
        //    java.lang.Thread.State: WAITING (on object monitor)
        // Thread: 0x000014e37802bd80  [0x4353] State: _running _at_poll_safepoint 0
        // ...
        // Also a debug vm shows: JavaThread state: _thread_blocked
        // ...
        output = executor.execute("VM.inspect -verbose " + pointerText(ptr));
        output.shouldContain("java.lang.Thread.State: WAITING");

        // Known bad pointers:
        output = executor.execute("VM.inspect 0x0");
        output.shouldContain("address not safe");

        output = executor.execute("VM.inspect -1");
        output.shouldContain("address not safe");

        // Find and test a Java Object:
        threadPrintOutput = executor.execute("Thread.print");
        ptr = findPointer(threadPrintOutput, waiting_on_mylock, 1);
        output = executor.execute("VM.inspect " + pointerText(ptr));
        System.out.println(output);
        // Some tests put ZGC options in test.java.opts, not test.vm.opts
        String testOpts = System.getProperty("test.vm.opts", "")
                          + System.getProperty("test.java.opts", "");
        if (!testOpts.contains("-XX:+UseZGC")) {
            output.shouldContain(" is an oop: ");
        } else {
            // ZGC has two variations:
            if (testOpts.contains("-XX:+ZGenerational")) {
                output.shouldContain("is a zaddress");
                testMisaligned = false;
            } else {
                output.shouldContain("is a good oop");
            }
        }
        output.shouldContain(" - ---- fields (total size");
        // " - private 'myInt' 'I' @12  12345 (0x00003039)"
        output.shouldContain(" - private 'myInt' 'I'");
        output.shouldContain(" 12345 (");

        // ZGenerational will show the raw memory of our misaligned pointer, e.g.
        // 0x0000040001852491 points into unknown readable memory: 0b 00 d8 57 14 00 00
        // ...so don't check for that error.
        if (testMisaligned) {
            BigInteger badPtr = ptr.add(BigInteger.ONE);
            output = executor.execute("VM.inspect " + pointerText(badPtr));
            output.shouldContain("misaligned");
            badPtr = badPtr.add(BigInteger.ONE);
            output = executor.execute("VM.inspect " + pointerText(badPtr));
            output.shouldContain("misaligned");
        }
    }

    public BigInteger findPointer(OutputAnalyzer output, Pattern pattern, int regexGroup) {
        Iterator<String> lines = output.asLines().iterator();
        Boolean foundMatch = false;
        BigInteger ptr = null;
        while (lines.hasNext()) {
            String line = lines.next();
            Matcher m = pattern.matcher(line);
            if (m.find()) {
                System.out.println("Matched line: " + line);
                foundMatch = true;
                String p = m.group(regexGroup);
                ptr = new BigInteger(p, 16);
                System.out.println("Using pointer: 0x" + ptr.toString(16));
                break;
            }
        }
        if (!foundMatch) {
            Assert.fail("Failed to find '" + pattern + "' in output:" + output.getOutput());
        }
        return ptr;
    }

    public static String pointerText(BigInteger p) {
        return "0x" + p.toString(16);
    }

    @Test
    public void cli() throws Throwable {
        run(new PidJcmdExecutor());
    }

   /**
    * JMX Diagnostic intentionally not implemented.
    */
    //@Test
    //public void jmx() throws ClassNotFoundException {
    //    run(new JMXExecutor());
    //}
}


class MyLock extends Object {
    private int myInt = 12345;
}

class DcmdTestClass {

    protected static MyLock lock = new MyLock();

    public void work() {{
        Runnable r = () -> {
          System.out.println("Hello");
          synchronized(lock) {
            try {
              lock.wait();
            } catch (Exception e) { }
          }
        };
        Thread t = new Thread(r);
        t.start();
      }
    }
}

