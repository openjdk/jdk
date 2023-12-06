/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 @bug 4908468
 @summary Linux Empty Choice throws NPE
 @key headful
 @run main EmptyChoiceTest
*/
import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.lang.reflect.InvocationTargetException;

public class EmptyChoiceTest
{
    Frame frame;
    Choice choice = null;

    public static void main(String[] args) throws
            InterruptedException,
            InvocationTargetException {
        EventQueue.invokeAndWait(() -> {
            EmptyChoiceTest emptyChoiceTest = new EmptyChoiceTest();
            emptyChoiceTest.init();
            emptyChoiceTest.test();
        });
    }

    public void init() {
        frame = new Frame();
        frame.setLayout(new BorderLayout());
        choice = new Choice();
        frame.add(choice, BorderLayout.NORTH);
        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.validate();
    }

    public void test () {
        try {
            int iWidth = choice.getWidth();
            int iHeight = choice.getHeight();
            Image componentImage =
                choice.createImage(iWidth, iHeight);
            Graphics graphics =
                componentImage.getGraphics();
            graphics.setClip(0, 0, iWidth, iHeight);
            choice.printAll(graphics);
            System.out.println("PrintAll successful!");
        } catch (NullPointerException exp) {
            throw new RuntimeException("Test failed. " +
                    "Empty Choice printAll throws NullPointerException");
        } catch (Exception exc){
            throw new RuntimeException("Test failed.", exc);
        } finally {
            frame.dispose();
        }
    }
}
