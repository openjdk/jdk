/*
 * Copyright (c) 1999, 2003, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4236543
 * @summary rmic w/o -d should put class files in package directory
 * @author Dana Burns
 * @library ../../../../java/rmi/testlibrary
 *
 * @build StreamPipe
 * @build RmicDefault
 * @run main RmicDefault
 */

/*
 * Ensure that, in the absence of a -d argument, rmic will deposit
 * the generated class files in the package directory as opposed
 * to the old behaviour of depositing them in the local directory.
 * JavaTest/jtreg does not support setting the classpath yet, so do
 * the javac directly.
 */

import java.io.File;
import java.io.IOException;

public class RmicDefault {
    public static void main(String args[]) throws Exception {
        String javahome = System.getProperty("java.home");
        String testclasses = System.getProperty("test.classes");
        String userDir = System.getProperty("user.dir");

        if (javahome.regionMatches(true, javahome.length() - 4,
                                   File.separator + "jre", 0, 4))
        {
            javahome = javahome.substring(0, javahome.length() - 4);
        }

        Process javacProcess = Runtime.getRuntime().exec(
            javahome + File.separator + "bin" + File.separator +
            "javac -d " + testclasses + " " +
            System.getProperty("test.src") + File.separator + "packagedir" +
            File.separator + "RmicMeImpl.java");

        StreamPipe.plugTogether(javacProcess.getInputStream(), System.out);
        StreamPipe.plugTogether(javacProcess.getErrorStream(), System.out);

        javacProcess.waitFor();

        Process rmicProcess = Runtime.getRuntime().exec(
            javahome + File.separator + "bin" + File.separator +
            "rmic -classpath " + testclasses + " packagedir.RmicMeImpl");

        StreamPipe.plugTogether(rmicProcess.getInputStream(), System.out);
        StreamPipe.plugTogether(rmicProcess.getErrorStream(), System.err);

        rmicProcess.waitFor();

        File stub = new File(userDir + File.separator + "packagedir" +
                             File.separator + "RmicMeImpl_Stub.class");
        if (!stub.exists()) {
            throw new RuntimeException("TEST FAILED: could not find stub");
        }

        System.err.println("TEST PASSED");
    }
}
