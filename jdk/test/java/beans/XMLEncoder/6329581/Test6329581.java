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
 * @bug 6329581
 * @summary Tests encoding of a class with custom ClassLoader
 * @author Sergey Malenkov
 */

import java.beans.ExceptionListener;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

public class Test6329581 implements ExceptionListener {

    public static void main(String[] args) throws Exception {
        ExceptionListener listener = new Test6329581();
        // write bean to byte array
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        XMLEncoder encoder = new XMLEncoder(out);
        encoder.setExceptionListener(listener);
        encoder.writeObject(getClassLoader("beans.jar").loadClass("test.Bean").newInstance());
        encoder.close();
        // read bean from byte array
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        XMLDecoder decoder = new XMLDecoder(in, null, listener, getClassLoader("beans.jar"));
        Object object = decoder.readObject();
        decoder.close();

        if (!object.getClass().getClassLoader().getClass().equals(URLClassLoader.class)) {
            throw new Error("bean is loaded with unexpected class loader");
        }
    }

    private static ClassLoader getClassLoader(String name) throws Exception {
        StringBuilder sb = new StringBuilder(256);
        sb.append("file:");
        sb.append(System.getProperty("test.src", "."));
        sb.append(File.separatorChar);
        sb.append(name);

        URL[] url = { new URL(sb.toString()) };
        return new URLClassLoader(url);
    }

    public void exceptionThrown(Exception exception) {
        throw new Error("unexpected exception", exception);
    }
}
