/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4413817
 * @summary Verify that ObjectInputStream.resolveProxyClass can properly
 *          resolve a dynamic proxy class which implements a non-public
 *          interface not defined in the latest user defined class loader.
 */

import java.io.*;
import java.lang.reflect.*;

public class NonPublicInterface {

    static class Handler implements InvocationHandler, Serializable {
        public Object invoke(Object obj, Method meth, Object[] args) {
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        Class nonPublic = null;
        String[] nonPublicInterfaces = new String[] {
            "java.awt.Conditional",
            "java.util.zip.ZipConstants",
            "javax.swing.GraphicsWrapper",
            "javax.swing.JPopupMenu$Popup",
            "javax.swing.JTable$Resizable2",
            "javax.swing.JTable$Resizable3",
            "javax.swing.ToolTipManager$Popup",
            "sun.audio.Format",
            "sun.audio.HaePlayable",
            "sun.tools.agent.StepConstants",
        };
        for (int i = 0; i < nonPublicInterfaces.length; i++) {
            try {
                nonPublic = Class.forName(nonPublicInterfaces[i]);
                break;
            } catch (ClassNotFoundException ex) {
            }
        }
        if (nonPublic == null) {
            throw new Error("couldn't find system non-public interface");
        }

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(bout);
        oout.writeObject(Proxy.newProxyInstance(nonPublic.getClassLoader(),
            new Class[]{ nonPublic }, new Handler()));
        oout.close();
        ObjectInputStream oin = new ObjectInputStream(
            new ByteArrayInputStream(bout.toByteArray()));
        oin.readObject();
    }
}
