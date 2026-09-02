/*
 * Copyright (c) 2000, 2026, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jdi.ReferenceType.visibleMethods;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;
import java.io.*;

/**
 * This class is used as debugee application for the visibmethod002 JDI test.
 */

public class visibmethod002a {

    private static Log log = new Log(System.err);

    private final static String
        package_prefix = "nsk.jdi.ReferenceType.visibleMethods.";
//        package_prefix = "";    //  for DEBUG without package
    static String checked_class_name = package_prefix + "visibmethod002aClassForCheck";

    public static void main (String argv[]) {

        log.display("**> visibmethod002a: debugee started!");
        ArgumentHandler argHandler = new ArgumentHandler(argv);
        IOPipe pipe = argHandler.createDebugeeIOPipe();

        String checked_class_dir = (argHandler.getArguments())[0] + File.separator + "loadclass";

        visibmethod002aClassLoader customClassLoader = new visibmethod002aClassLoader(checked_class_dir, checked_class_name);
        try {
            customClassLoader.preloadClass(checked_class_name);
            log.display("--> visibmethod002a: checked class loaded but not prepared: " + checked_class_name);
        } catch (Throwable e) {  // ClassNotFoundException
            log.display("--> visibmethod002a: checked class NOT loaded: " + e);
        }

        log.display("**> visibmethod002a: waiting for \"quit\" signal...");
        pipe.println("ready");
        String instruction = pipe.readln();
        if (instruction.equals("quit")) {
            log.display("**> visibmethod002a: \"quit\" signal recieved!");
            log.display("**> visibmethod002a: completed succesfully!");
            System.exit(0/*STATUS_PASSED*/ + 95/*STATUS_TEMP*/);
        }
        System.err.println("!!**> visibmethod002a: unexpected signal (no \"quit\") - " + instruction);
        System.err.println("!!**> visibmethod002a: FAILED!");
        System.exit(2/*STATUS_FAILED*/ + 95/*STATUS_TEMP*/);
    }
}

/**
 * Custom class loader to load class without preparation.
 */
class visibmethod002aClassLoader extends ClassLoader {

    private String classPath;
    public static Class loadedClass;

    public visibmethod002aClassLoader(String classPath, String className) {
        super(visibmethod002aClassLoader.class.getClassLoader());
        this.classPath = classPath;
    }

    public void preloadClass (String className) throws ClassNotFoundException {
        loadedClass = findClass(className);
    }

    protected synchronized Class findClass(String className) throws ClassNotFoundException {
        String classFileName = classPath + "/" + className.replace('.', '/') + ".class";

        FileInputStream in;
        try {
            in = new FileInputStream(classFileName);
            if (in == null) {
                throw new ClassNotFoundException(classFileName);
            }
        } catch (FileNotFoundException e) {
            throw new ClassNotFoundException(classFileName, e);
        }

        int len;
        byte data[];
        try {
            len = in.available();
            data = new byte[len];
            for (int total = 0; total < data.length; ) {
                total += in.read(data, total, data.length - total);
            }
        } catch (IOException e) {
            throw new ClassNotFoundException(classFileName, e);
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                throw new ClassNotFoundException(classFileName, e);
            }
        }

        return defineClass(className, data, 0, data.length);
    }
}
