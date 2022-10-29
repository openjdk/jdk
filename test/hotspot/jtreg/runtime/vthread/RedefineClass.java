/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017 SAP SE. All rights reserved.
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

/**
 * @test
 * @summary Regression test based on runtime/Metaspace/DefineClass.java
 * @compile --enable-preview -source ${jdk.version} RedefineClass.java
 * @run main/othervm --enable-preview -Djdk.attach.allowAttachSelf RedefineClass
 */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.util.concurrent.CountDownLatch;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import com.sun.tools.attach.VirtualMachine;

public class RedefineClass {

    private static Instrumentation instrumentation;

    public void getID(CountDownLatch start, CountDownLatch stop) {
        String id = "AAAAAAAA";
        System.out.println(id);

        try {
            // Signal that we've entered the activation..
            start.countDown();
            //..and wait until we can leave it.
            stop.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(id);

        return;
    }

    private static class MyThread extends Thread {
        private RedefineClass dc;
        private CountDownLatch start, stop;

        public MyThread(RedefineClass dc, CountDownLatch start, CountDownLatch stop) {
            this.dc = dc;
            this.start = start;
            this.stop = stop;
        }

        public void run() {
            dc.getID(start, stop);
        }
    }

    public static void agentmain(String args, Instrumentation inst) {
        System.out.println("Loading Java Agent.");
        instrumentation = inst;
    }


    private static void loadInstrumentationAgent(String myName, byte[] buf) throws Exception {
        // Create agent jar file on the fly
        Manifest m = new Manifest();
        m.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        m.getMainAttributes().put(new Attributes.Name("Agent-Class"), myName);
        m.getMainAttributes().put(new Attributes.Name("Can-Redefine-Classes"), "true");
        File jarFile = File.createTempFile("agent", ".jar");
        jarFile.deleteOnExit();
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(jarFile), m);
        jar.putNextEntry(new JarEntry(myName.replace('.', '/') + ".class"));
        jar.write(buf);
        jar.close();
        String pid = Long.toString(ProcessHandle.current().pid());
        System.out.println("Our pid is = " + pid);
        VirtualMachine vm = VirtualMachine.attach(pid);
        vm.loadAgent(jarFile.getAbsolutePath());
    }

    private static byte[] getBytecodes(String myName) throws Exception {
        InputStream is = RedefineClass.class.getResourceAsStream(myName + ".class");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
        buf = baos.toByteArray();
        System.out.println("sizeof(" + myName + ".class) == " + buf.length);
        return buf;
    }

    private static int getStringIndex(String needle, byte[] buf) {
        return getStringIndex(needle, buf, 0);
    }

    private static int getStringIndex(String needle, byte[] buf, int offset) {
        outer:
        for (int i = offset; i < buf.length - offset - needle.length(); i++) {
            for (int j = 0; j < needle.length(); j++) {
                if (buf[i + j] != (byte)needle.charAt(j)) continue outer;
            }
            return i;
        }
        return 0;
    }

    private static void replaceString(byte[] buf, String name, int index) {
        for (int i = index; i < index + name.length(); i++) {
            buf[i] = (byte)name.charAt(i - index);
        }
    }

    public static final int ITERATIONS = 10;


    public static void main(String[] args) throws Exception {
        Thread main = Thread.ofVirtual().name("test").start(RedefineClass::main);
        main.join();
    }

    public static void main() {
        try {
            String myName = RedefineClass.class.getName();
            byte[] buf = getBytecodes(myName.substring(myName.lastIndexOf(".") + 1));
            int iterations = ITERATIONS;

            loadInstrumentationAgent(myName, buf);
            int index = getStringIndex("AAAAAAAA", buf);
            CountDownLatch stop = new CountDownLatch(1);

            Thread[] threads = new Thread[iterations];
            for (int i = 0; i < iterations; i++) {
                buf[index] = (byte) ('A' + i + 1); // Change string constant in getID() which is legal in redefinition
                instrumentation.redefineClasses(new ClassDefinition(RedefineClass.class, buf));
                RedefineClass dc = RedefineClass.class.newInstance();
                CountDownLatch start = new CountDownLatch(1);
                (threads[i] = new MyThread(dc, start, stop)).start();

                start.await(); // Wait until the new thread entered the getID() method

            }
            // We expect to have one instance for each redefinition because they are all kept alive by an activation
            // plus the initial version which is kept active by this main method.
            stop.countDown(); // Let all threads leave the RedefineClass.getID() activation..
            // ..and wait until really all of them returned from RedefineClass.getID()
            for (int i = 0; i < iterations; i++) {
                threads[i].join();
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
