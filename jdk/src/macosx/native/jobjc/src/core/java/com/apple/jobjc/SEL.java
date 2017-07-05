/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.jobjc;

public class SEL {
    static native long getSelectorPtr(String selectorName);
    static native String getSelectorName(long ptr);

    final long selPtr;

    SEL(long ptr) {
        this.selPtr = ptr;
    }

    public SEL(final String name) {
        this(getSelectorPtr(name));
    }

    @Override public String toString(){
        return ((int)selPtr) + " / " + selPtr + " : " + getSelectorName(selPtr);
    }

    /**
     * Converts something like "performSelectorOnMainThread_withObject_wait"
     * to "performSelectorOnMainThread:withObject:wait:"
     */
    public static String selectorName(String jMethodName, boolean hasArgs){
        String b = jMethodName.replaceAll("_", ":");
        return hasArgs ? b + ":" : b;
    }

    public static String jMethodName(String selectorName){
        return selectorName.replaceAll(":", "_").replaceAll("_$", "");
    }

    public static boolean validName(String selectorName){
        return selectorName.matches("^[a-zA-Z_][a-zA-Z0-9_:]*$");
    }
}
