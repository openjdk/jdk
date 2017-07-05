/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6581254
 * @summary Allow "~" in config to support windows short path
 * @author Valerie Peng
 */

import java.security.*;
import java.io.*;

public class ConfigShortPath {

    public static void main(String[] args) {
        String testSrc = System.getProperty("test.src", ".");
        String configFile = testSrc + File.separator + "csp.cfg";
        System.out.println("Testing against " + configFile);
        try {
            Provider p = new sun.security.pkcs11.SunPKCS11(configFile);
        } catch (ProviderException pe) {
            String cause = pe.getCause().getMessage();
            if (cause.indexOf("Unexpected token") != -1) {
                // re-throw to indicate test failure
                throw pe;
            }
        }
    }
}
