/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
import java.io.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.security.*;

public class Deadlock2 {
    public static void main(String[] args) throws Exception {
        File file = new File("object.tmp");
        final byte[] bytes = new byte[(int) file.length()];
        FileInputStream fileInputStream = new FileInputStream(file);
        int read = fileInputStream.read(bytes);
        if (read != file.length()) {
            throw new Exception("Didn't read all");
        }
        Thread.sleep(1000);

        Runnable xmlRunnable = new Runnable() {
                public void run() {
                    try {
                        DocumentBuilderFactory.newInstance();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };

        Runnable readObjectRunnable = new Runnable() {
                public void run() {
                    try {
                        ObjectInputStream objectInputStream =
                            new ObjectInputStream(new ByteArrayInputStream(bytes));
                        Object o = objectInputStream.readObject();
                        System.out.println(o.getClass());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };

        Thread thread1 = new Thread(readObjectRunnable, "Read Object");
        Thread thread2 = new Thread(xmlRunnable, "XML");

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();
    }
}
