/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4429043 4493595 6332756 6709457
 * @summary The FileChannel file locking
 */

import java.io.*;
import java.nio.channels.*;
import static java.nio.file.StandardOpenOption.*;

/**
 * Testing FileChannel's lock method.
 */

public class Lock {

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            if(args[0].equals("1")) {
                MadWriter mw = new MadWriter(args[1], false);
            } else {
                MadWriter mw = new MadWriter(args[1], true);
            }
            return;
        }
        File blah = File.createTempFile("blah", null);
        blah.deleteOnExit();
        RandomAccessFile raf = new RandomAccessFile(blah, "rw");
        raf.write(1);
        raf.close();
        test1(blah, "1");
        test1(blah, "2");
        test2(blah, true);
        test2(blah, false);
        test3(blah);
        test4(blah);
        blah.delete();
    }

    private static void test2(File blah, boolean b) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(blah, "rw");
        FileChannel channel = raf.getChannel();
        FileLock lock;
        if (b)
            lock = channel.lock();
        else
            lock = channel.tryLock();
        lock.release();
        channel.close();
    }

    static void test1(File blah, String str) throws Exception {

        // Grab the lock
        RandomAccessFile fis = new RandomAccessFile(blah, "rw");
        FileChannel fc = fis.getChannel();
        FileLock lock = null;

        if (str.equals("1")) {
            lock = fc.lock(0, 10, false);
            if (lock == null)
                throw new RuntimeException("Lock should not return null");
            try {
                FileLock lock2 = fc.lock(5, 10, false);
                throw new RuntimeException("Overlapping locks allowed");
            } catch (OverlappingFileLockException e) {
                // Correct result
            }
        }

        // Exec the tamperer
        String command = System.getProperty("java.home") +
            File.separator + "bin" + File.separator + "java";
        String testClasses = System.getProperty("test.classes");
        if (testClasses != null)
            command += " -cp " + testClasses;
        command += " Lock " + str + " " + blah;
        Process p = Runtime.getRuntime().exec(command);

        BufferedReader in = new BufferedReader
            (new InputStreamReader(p.getInputStream()));

        String s;
        int count = 0;
        while ((s = in.readLine()) != null) {
            if (!s.equals("good")) {
                if (File.separatorChar == '/') {
                    // Fails on windows over NFS...
                    throw new RuntimeException("Failed: "+s);
                }
            }
            count++;
        }

        if (count == 0) {
            in = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((s = in.readLine()) != null) {
                System.err.println("Error output: " + s);
            }
            throw new RuntimeException("Failed, no output");
        }

        // Clean up
        if (lock != null) {
            /* Check multiple releases */
            lock.release();
            lock.release();
        }
        fc.close();
        fis.close();
    }

    // The overlap check for file locks should be JVM-wide
    private static void test3(File blah) throws Exception {
        FileChannel fc1 = new RandomAccessFile(blah, "rw").getChannel();
        FileChannel fc2 = new RandomAccessFile(blah, "rw").getChannel();

        // lock via one channel, and then attempt to lock the same file
        // using a second channel
        FileLock fl1 = fc1.lock();
        try {
            fc2.tryLock();
            throw new RuntimeException("Overlapping locks allowed");
        } catch (OverlappingFileLockException x) {
        }
        try {
            fc2.lock();
            throw new RuntimeException("Overlapping locks allowed");
        } catch (OverlappingFileLockException x) {
        }

        // release lock and the attempt to lock with the second channel
        // should succeed.
        fl1.release();
        FileLock fl2 = fc2.lock();
        try {
            fc1.lock();
            throw new RuntimeException("Overlapping locks allowed");
        } catch (OverlappingFileLockException x) {
        }

        fc1.close();
        fc2.close();
    }

    /**
     * Test file locking when file is opened for append
     */
    static void test4(File blah) throws Exception {
        try (FileChannel fc = new FileOutputStream(blah, true).getChannel()) {
            fc.tryLock().release();
            fc.tryLock(0L, 1L, false).release();
            fc.lock().release();
            fc.lock(0L, 1L, false).release();
        }
        try (FileChannel fc = FileChannel.open(blah.toPath(), APPEND)) {
            fc.tryLock().release();
            fc.tryLock(0L, 1L, false).release();
            fc.lock().release();
            fc.lock(0L, 1L, false).release();
        }
    }
}

class MadWriter {
    public MadWriter(String s, boolean b) throws Exception {
        File f = new File(s);
        RandomAccessFile fos = new RandomAccessFile(f, "rw");
        FileChannel fc = fos.getChannel();
        if (fc.tryLock(10, 10, false) == null) {
            System.out.println("bad: Failed to grab adjacent lock");
        }
        FileLock lock = fc.tryLock(0, 10, false);
        if (lock == null) {
            if (b)
                System.out.println("bad");
            else
                System.out.println("good");
        } else {
            if (b)
                System.out.println("good");
            else
                System.out.println("bad");
        }
        fc.close();
        fos.close();
    }

}
