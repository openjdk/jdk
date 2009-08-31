/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
package com.sun.tools.internal.ws.wscompile;

import com.sun.istack.internal.NotNull;
import com.sun.tools.internal.ws.processor.modeler.wsdl.ConsoleErrorReporter;

import java.io.File;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.Arrays;

/**
 * @author Vivek Pandey
 */
public class DefaultAuthTester {
    public static void main(String[] args) throws BadCommandLineException {
        DefaultAuthenticator da = new MyAuthenticator(new ConsoleErrorReporter(System.out), new File("c:\\Users\\vivekp\\.metro\\auth"));

        PasswordAuthentication pa = da.getPasswordAuthentication();
        if(pa!= null && pa.getUserName().equals("vivek") && Arrays.equals(pa.getPassword(), "test".toCharArray()))
            System.out.println("Success!");
        else
            System.out.println("Failiure!");

    }

    private static class MyAuthenticator extends DefaultAuthenticator{

        public MyAuthenticator(@NotNull ErrorReceiver receiver, @NotNull File authfile) throws BadCommandLineException {
            super(receiver, authfile);
        }

        protected URL getRequestingURL() {
            try {
                return new URL("http://foo.com/myservice?wsdl");
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
