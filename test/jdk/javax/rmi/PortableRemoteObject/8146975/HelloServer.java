/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import javax.naming.InitialContext;
import javax.naming.Context;

public class HelloServer {

    static final int MAX_RETRY = 10;
    static final int ONE_SECOND = 1000;

    public static void main(String[] args) {
        int retryCount = 0;
        while (retryCount < MAX_RETRY) {
            try {
                // Step 1: Instantiate the Hello servant
                HelloImpl helloRef = new HelloImpl();

                // Step 2: Publish the reference in the Naming Service
                // using JNDI API
                Context initialNamingContext = new InitialContext();
                initialNamingContext.rebind("HelloService", helloRef);

                System.out.println("Hello Server: Ready...");
                break;
            } catch (Exception e) {
                System.out.println("Server initialization problem: " + e);
                e.printStackTrace();
                retryCount++;
                try {
                    Thread.sleep(ONE_SECOND);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }
}
