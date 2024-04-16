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
 * @run testng/othervm -Dvminspect.enabled=true -XX:+EnableVMInspectCommand VMInspectTest
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

    // Event: 2301.131 Thread 0x00007fc6cc2866a0 nmethod 1298 0x00007fc6b4f82610 code [0x00007fc6b4f82a40, 0x00007fc6b4f83aa0]
    static Pattern compilation_event =
        Pattern.compile("Event: .* Thread .* nmethod \\d+ 0x(\\p{XDigit}+) code ");

    // Compressed class space mapped at: 0x00007fc62f000000-0x00007fc66f000000, reserved size: 1073741824
    static Pattern compressed_class_space =
        Pattern.compile("Compressed class space mapped at: 0x(\\p{XDigit}+)\\-0x(\\p{XDigit}+), ");

    // Narrow klass base: 0x00007fc62e000000, Narrow klass shift: 0, Narrow klass range: 0x100000000
    static Pattern narrow_klass_base =
        Pattern.compile("Narrow klass base: 0x(\\p{XDigit}+), ");

    //  tid=0x0000153418029c20
    static Pattern thread_id_line =
        Pattern.compile(" tid=0x(\\p{XDigit}+) ");

    public void run(CommandExecutor executor) throws ClassNotFoundException {
        DcmdTestClass test = new DcmdTestClass();
        test.work();
        BigInteger ptr = null;
        OutputAnalyzer output = null;

        // VM.inspect requires EnableVMInspectCommand or a debug JVM.
        // This test runs with a System Property set as a hint whether EnableVMInspectCommand is set.
        boolean enabled = Platform.isDebugBuild() || Boolean.getBoolean("vminspect.enabled");
        System.out.println("VM.inspect should be enabled = " + enabled);
        if (!enabled) {
            // Use any pointer, command should be refused:
            output = executor.execute("VM.inspect 0x0");
            output.shouldContain("-XX:+EnableVMInspectCommand is required");
            return; // no more testing
        }

        // Tests with VM.inspect enabled:
        output = executor.execute("help");
        output.shouldNotContain("VM.inspect"); // VM.inspect is not promoted in help
        output = executor.execute("help VM.inspect");
        output.shouldContain("Syntax : VM.inspect"); // but help is available

        testInspectAddress(executor);
        testInspectAddressThread(executor);
        testInspectAddressNMethod(executor);
        testInspectAddressMetadata(executor);

        // Some tests put ZGC options in test.java.opts, not test.vm.opts
        String testOpts = System.getProperty("test.vm.opts", "")
                          + System.getProperty("test.java.opts", "");

        boolean isZGC = testOpts.contains("-XX:+UseZGC");
        boolean isGenZGC = testOpts.contains("-XX:+ZGenerational");

        testInspectJavaObject(executor, isZGC, isGenZGC);
    }

    public void testInspectAddress(CommandExecutor executor) {
        // Test that address is mandatory:
        // java.lang.IllegalArgumentException: The argument 'address' is mandatory.
        OutputAnalyzer output = executor.execute("VM.inspect");
        output.shouldContain("is mandatory");

        // Known bad pointers:
        output = executor.execute("VM.inspect 0x0");
        output.shouldContain("address not safe");
        output = executor.execute("VM.inspect -1");
        output.shouldContain("address not safe");
    }

    public void testInspectAddressThread(CommandExecutor executor) {
        // Find and test a thread id:
        OutputAnalyzer jcmdOutput = executor.execute("Thread.print", true /* silent */);
        BigInteger ptr = findPointer(jcmdOutput, thread_id_line, 1, true);
        OutputAnalyzer output = executor.execute("VM.inspect " + pointerText(ptr));
        output.shouldContain(" is a thread");

        // Using -verbose on a thread pointer shows output like:
        // "main" #1 [17235] prio=5 os_prio=0 cpu=1265.79ms elapsed=6.12s tid=0x000014e37802bd80 nid=17235 in Object.wait()  [0x000014e3817d4000]
        //    java.lang.Thread.State: WAITING (on object monitor)
        // Thread: 0x000014e37802bd80  [0x4353] State: _running _at_poll_safepoint 0
        // ...
        // Also a debug vm shows: JavaThread state: _thread_blocked
        // ...
        output = executor.execute("VM.inspect -verbose " + pointerText(ptr));
        output.shouldContain("java.lang.Thread.State: WAITING");
    }

    public void testInspectAddressNMethod(CommandExecutor executor) {
        // Find and test a compiled method:
        OutputAnalyzer jcmdOutput = executor.execute("VM.info", true /* silent */);
        BigInteger ptr = findPointer(jcmdOutput, compilation_event, 1, false);
        if (ptr != null) {
            OutputAnalyzer output = executor.execute("VM.inspect " + pointerText(ptr));
            System.out.println(output);
            output.shouldContain("Compiled method ");
        } else{
            System.out.println("No compilation event found.");
        }
    }

    public void testInspectAddressMetadata(CommandExecutor executor) {
        // Test pointer into metadata:
        OutputAnalyzer jcmdOutput = executor.execute("VM.info", true /* silent */);
        BigInteger ptr = findPointer(jcmdOutput, compressed_class_space, 1, false);
        if (ptr != null) {
            OutputAnalyzer output = executor.execute("VM.inspect " + pointerText(ptr));
            System.out.println(output);
            output.shouldContain("metadata");
        } else{
            System.out.println("No Compressed class space found.");
        }

        ptr = findPointer(jcmdOutput, narrow_klass_base, 1, false);
        if (ptr != null) {
            OutputAnalyzer output = executor.execute("VM.inspect " + pointerText(ptr));
            System.out.println(output);
            output.shouldContain("metadata");
        } else {
            System.out.println("No narrow klass base found.");
        }
    }

    public static final int OBJECT_TRIES = 3;

    public void testInspectJavaObject(CommandExecutor executor, boolean isZGC, boolean isGenZGC) {
        // Find and test a Java Object:
        // Process is live.  Very rarely, an Object seen in Thread.print may move due to GC,
        // so make a few attempts.
        BigInteger ptr = null;
        for (int i = 0; i < OBJECT_TRIES; i++) {
            System.gc();
            ptr = testInspectJavaObjectPointer(executor, isZGC, isGenZGC);
            if (ptr != null) {
                break;
            }
        }
        if (ptr == null) {
            throw new RuntimeException("Failed to inspect Java object from thread dump.");
        }

        // Test misaligned object pointer:
        // ZGenerational will show the raw memory of our misaligned pointer, e.g.
        // 0x0000040001852491 points into unknown readable memory: 0b 00 d8 57 14 00 00
        // ...so don't check for this error.
        if (!isGenZGC) {
            BigInteger badPtr = ptr.add(BigInteger.ONE);
            OutputAnalyzer output = executor.execute("VM.inspect " + pointerText(badPtr));
            output.shouldContain("misaligned");
            badPtr = badPtr.add(BigInteger.ONE);
            output = executor.execute("VM.inspect " + pointerText(badPtr));
            output.shouldContain("misaligned");
        }
    }

    public BigInteger testInspectJavaObjectPointer(CommandExecutor executor, boolean isZGC, boolean isGenZGC) {
        // Inspect the MyLock object found in Thread.print output.
        String expected = " is an oop: ";
        if (isZGC) {
            // ZGC has two variations:
            expected =  isGenZGC ? "is a zaddress" : "is a good oop";
        }
        OutputAnalyzer jcmdOutput = executor.execute("Thread.print");
        BigInteger ptr = findPointer(jcmdOutput, waiting_on_mylock, 1, true);
        OutputAnalyzer output = executor.execute("VM.inspect " + pointerText(ptr));
        if (!output.contains(expected)) {
            System.out.println("VM.inspect does not find expected text for 0x" + ptr.toString(16));
            return null;
        }

        output.shouldContain(" - ---- fields (total size");
        // " - private 'myInt' 'I' @12  12345 (0x00003039)"
        output.shouldContain(" - private 'myInt' 'I'");
        output.shouldContain(" 12345 (");
        return ptr;
    }

    public BigInteger findPointer(OutputAnalyzer output, Pattern pattern, int regexGroup, boolean mustFind) {
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
            String msg = "Failed to find '" + pattern + "' in output:" + output.getOutput();
            if (mustFind) {
                Assert.fail(msg);
            } else {
                System.out.println(msg);
            }
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

    public void work() {
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

