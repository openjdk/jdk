/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4715486
  @summary Tests that FocusEvent.opposite is not serialized
  @key headful
  @run main OppositeSerialization
*/

import java.awt.Button;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.FocusEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;

import static java.lang.Integer.valueOf;

/**
 * "This is an AUTOMATIC test",
 * "however, that's what it does:",
 * "1. It tests that FocusEvent.opposite field is written",
 * "to serialized stream as null regardless of whether it",
 * "is actually null or not. For this purpose, we serialize",
 * "a FocusEvent with really huge opposite, and then check",
 * "if serialized object is huge or not.",
 * "2. It tests that FocusEvent.opposite deserializes as",
 * "null, even if it was serialized in the previous version",
 * "of JDK. For this purpose, file old.ser is included into",
 * "test. It is FocusEvent serialized with 1.4, with non-null",
 * "opposite. We check that after deserialization opposite",
 * "field is null"
 */
public class OppositeSerialization {
    static Button b1;
    static Frame b2;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        EventQueue.invokeAndWait(() -> {
            b1 = new Button("OppositeSerialization - Source");
            b2 = new Frame("OppositeSerialization - Opposite");
            b2.setLayout(new FlowLayout());

            for (int i = 0; i < 10000; i++) {
                String s = (valueOf(i)).toString();
                b2.add(new Button("Button" + s));
            }
        });

        FocusEvent evt1 = new FocusEvent(b1, FocusEvent.FOCUS_GAINED, false, b2);

        /*
         * Here we test that opposite component isn't serialized.
         * We created a really huge opposite component for a focus
         * event evt1 and now we'll see if the size of serialized data
         * is big.
         */
        try {
            FileOutputStream fos = new FileOutputStream("new.ser");
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(evt1);
            oos.flush();
        } catch (Exception e1) {
            System.out.println("Sorry! Couldn't write the stream");
            System.out.println("The test failed, but the reason is " +
                    "unrelated to the subject");
            throw new RuntimeException("The test couldn't write serialized data");
        }

        File file = new File("new.ser");
        if (file.length() > 50000) {
            System.out.println("The test failed: serialized " +
                    "FocusEvent too huge");
            System.err.println("Serialized FocusEvent is too huge.");
            System.err.println("Probably opposite field is " +
                    "serialized incorrectly.");
            throw new RuntimeException("Serialized FocusEvent is too huge");
        }

        /*
         * Here we test that opposite is not deserialized even if it is present
         * in the stream. old.ser is created with JDK1.4 using the following
         * source code:
         *
         * import java.awt.event.*;
         * import java.io.*;
         * import java.awt.*;
         *
         * public class OldFocusSerializer {
         *
         *     public static void main(String[] args) {
         *
         *         Button b1 = new Button("Source");
         *         Button b2 = new Button("Opposite");
         *
         *         FocusEvent evt1 = new FocusEvent(b1,
         *                                          FocusEvent.FOCUS_GAINED,
         *                                          false,
         *                                          b2);
         *
         *         try {
         *             FileOutputStream fos = new FileOutputStream("old.ser");
         *             ObjectOutputStream oos = new ObjectOutputStream(fos);
         *             oos.writeObject(evt1);
         *             oos.flush();
         *         } catch (IOException e) {
         *             System.out.println("Sorry! Couldn't write the stream");
         *         }
         *     }
         * }
         */
        FocusEvent evt2;
        String testPath = System.getProperty("test.src", ".");
        try {
            FileInputStream fis = new FileInputStream(testPath +
                    File.separator + "old.ser");
            ObjectInputStream ois = new ObjectInputStream(fis);
            evt2 = (FocusEvent)ois.readObject();
        } catch (Exception e2) {
            System.out.println("The test failed as it couldn't read the stream");
            throw new RuntimeException("The test couldn't read serialized data");
        }

        if (evt2.getOppositeComponent() != null) {
            System.out.println("The test failed: opposite component " +
                    "deserialized to non-null value");
            System.err.println("FocusEvent stored in old.ser should have " +
                    "null opposite field.");
            throw new RuntimeException("Non-null opposite component " +
                    "after deserialization");
        }

        if (b2 != null) {
            EventQueue.invokeAndWait(() -> b2.dispose());
        }

        System.out.println("The test passed");
    }
}
