/*
 * Copyright (c) 1998, 2011, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.sun.tools.example.debug.bdi;   //### does it belong here?

import com.sun.jdi.*;

public class Utils {

    /**
     * Return the thread status description.
     */
    public static String getStatus(ThreadReference thr) {
        int status = thr.status();
        String result;
        switch (status) {
          case ThreadReference.THREAD_STATUS_UNKNOWN:
            result = "unknown status";
            break;
          case ThreadReference.THREAD_STATUS_ZOMBIE:
            result = "zombie";
            break;
          case ThreadReference.THREAD_STATUS_RUNNING:
            result = "running";
            break;
          case ThreadReference.THREAD_STATUS_SLEEPING:
            result = "sleeping";
            break;
          case ThreadReference.THREAD_STATUS_MONITOR:
            result = "waiting to acquire a monitor lock";
            break;
          case ThreadReference.THREAD_STATUS_WAIT:
            result = "waiting on a condition";
            break;
          default:
            result = "<invalid thread status>";
        }
        if (thr.isSuspended()) {
            result += " (suspended)";
        }
        return result;
    }

    /**
     * Return a description of an object.
     */
    public static String description(ObjectReference ref) {
        ReferenceType clazz = ref.referenceType();
        long id = ref.uniqueID();  //### TODO use real id
        if (clazz == null) {
            return toHex(id);
        } else {
            return "(" + clazz.name() + ")" + toHex(id);
        }
    }

    /**
     * Convert a long to a hexadecimal string.
     */
    public static String toHex(long n) {
        char s1[] = new char[16];
        char s2[] = new char[18];

        // Store digits in reverse order.
        int i = 0;
        do {
            long d = n & 0xf;
            s1[i++] = (char)((d < 10) ? ('0' + d) : ('a' + d - 10));
        } while ((n >>>= 4) > 0);

        // Now reverse the array.
        s2[0] = '0';
        s2[1] = 'x';
        int j = 2;
        while (--i >= 0) {
            s2[j++] = s1[i];
        }
        return new String(s2, 0, j);
    }

    /**
     * Convert hexadecimal strings to longs.
     */
    public static long fromHex(String hexStr) {
        String str = hexStr.startsWith("0x") ?
            hexStr.substring(2).toLowerCase() : hexStr.toLowerCase();
        if (hexStr.length() == 0) {
            throw new NumberFormatException();
        }

        long ret = 0;
        for (int i = 0; i < str.length(); i++) {
            int c = str.charAt(i);
            if (c >= '0' && c <= '9') {
                ret = (ret * 16) + (c - '0');
            } else if (c >= 'a' && c <= 'f') {
                ret = (ret * 16) + (c - 'a' + 10);
            } else {
                throw new NumberFormatException();
            }
        }
        return ret;
    }


    /*
     * The next two methods are used by this class and by EventHandler
     * to print consistent locations and error messages.
     */
    public static String locationString(Location loc) {
        return  loc.declaringType().name() +
            "." + loc.method().name() + "(), line=" +
            loc.lineNumber();
    }

//### UNUSED.
/************************
    private String typedName(Method method) {
        // TO DO: Use method.signature() instead of method.arguments() so that
        // we get sensible results for classes without debugging info
        StringBuffer buf = new StringBuffer();
        buf.append(method.name());
        buf.append("(");
        Iterator it = method.arguments().iterator();
        while (it.hasNext()) {
            buf.append(((LocalVariable)it.next()).typeName());
            if (it.hasNext()) {
                buf.append(",");
            }
        }
        buf.append(")");
        return buf.toString();
    }
************************/

    public static boolean isValidMethodName(String s) {
        return isJavaIdentifier(s) ||
               s.equals("<init>") ||
               s.equals("<clinit>");
    }

    public static boolean isJavaIdentifier(String s) {
        if (s.length() == 0) {
            return false;
        }
        int cp = s.codePointAt(0);
        if (! Character.isJavaIdentifierStart(cp)) {
            return false;
        }
        for (int i = Character.charCount(cp); i < s.length(); i += Character.charCount(cp)) {
            cp = s.codePointAt(i);
            if (! Character.isJavaIdentifierPart(cp)) {
                return false;
            }
        }
        return true;
    }

}
