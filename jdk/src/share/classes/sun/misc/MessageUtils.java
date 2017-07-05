/*
 * Copyright (c) 1995, 2000, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.misc;

/**
 * MessageUtils: miscellaneous utilities for handling error and status
 * properties and messages.
 *
 * @author Herb Jellinek
 */

public class MessageUtils {
    // can instantiate it for to allow less verbose use - via instance
    // instead of classname

    public MessageUtils() { }

    public static String subst(String patt, String arg) {
        String args[] = { arg };
        return subst(patt, args);
    }

    public static String subst(String patt, String arg1, String arg2) {
        String args[] = { arg1, arg2 };
        return subst(patt, args);
    }

    public static String subst(String patt, String arg1, String arg2,
                               String arg3) {
        String args[] = { arg1, arg2, arg3 };
        return subst(patt, args);
    }

    public static String subst(String patt, String args[]) {
        StringBuilder result = new StringBuilder();
        int len = patt.length();
        for (int i = 0; i >= 0 && i < len; i++) {
            char ch = patt.charAt(i);
            if (ch == '%') {
                if (i != len) {
                    int index = Character.digit(patt.charAt(i + 1), 10);
                    if (index == -1) {
                        result.append(patt.charAt(i + 1));
                        i++;
                    } else if (index < args.length) {
                        result.append(args[index]);
                        i++;
                    }
                }
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    public static String substProp(String propName, String arg) {
        return subst(System.getProperty(propName), arg);
    }

    public static String substProp(String propName, String arg1, String arg2) {
        return subst(System.getProperty(propName), arg1, arg2);
    }

    public static String substProp(String propName, String arg1, String arg2,
                                   String arg3) {
        return subst(System.getProperty(propName), arg1, arg2, arg3);
    }

    /**
     *  Print a message directly to stderr, bypassing all the
     *  character conversion methods.
     *  @param msg   message to print
     */
    public static native void toStderr(String msg);

    /**
     *  Print a message directly to stdout, bypassing all the
     *  character conversion methods.
     *  @param msg   message to print
     */
    public static native void toStdout(String msg);


    // Short forms of the above

    public static void err(String s) {
        toStderr(s + "\n");
    }

    public static void out(String s) {
        toStdout(s + "\n");
    }

    // Print a stack trace to stderr
    //
    public static void where() {
        Throwable t = new Throwable();
        StackTraceElement[] es = t.getStackTrace();
        for (int i = 1; i < es.length; i++)
            toStderr("\t" + es[i].toString() + "\n");
    }

}
