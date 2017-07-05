/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6788531
 * @summary Tests public method lookup problem in EventHandler
 * @author Sergey Malenkov
 */

import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.beans.EventHandler;

public class Test6788531 {
    public static void main(String[] args) throws Exception {
        JButton button = new JButton("hi");
        button.addActionListener(EventHandler.create(ActionListener.class, new Private(), "run"));
        button.addActionListener(EventHandler.create(ActionListener.class, new PrivateGeneric(), "run", "generic"));
        button.doClick();
    }

    public static class Public {
        public void run() {
            throw new Error("method is overridden");
        }
    }

    static class Private extends Public {
        public void run() {
            System.out.println("default");
        }
    }

    public static class PublicGeneric<T> {
        public void run(T object) {
            throw new Error("method is overridden");
        }
    }

    static class PrivateGeneric extends PublicGeneric<String> {
        public void run(String string) {
            System.out.println(string);
        }
    }
}
