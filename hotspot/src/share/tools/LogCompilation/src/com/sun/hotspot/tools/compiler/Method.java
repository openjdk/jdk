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
 *
 */

package com.sun.hotspot.tools.compiler;

import java.util.Arrays;

public class Method implements Constants {

    private String holder;
    private String name;
    private String returnType;
    private String arguments;
    private String bytes;
    private String iicount;
    private String flags;

    String decodeFlags(int osr_bci) {
        int f = Integer.parseInt(getFlags());
        char[] c = new char[4];
        Arrays.fill(c, ' ');
        if (osr_bci >= 0) {
            c[0] = '%';
        }
        if ((f & JVM_ACC_SYNCHRONIZED) != 0) {
            c[1] = 's';
        }
        return new String(c);
    }

    String format(int osr_bci) {
        if (osr_bci >= 0) {
            return getHolder().replace('/', '.') + "::" + getName() + " @ " + osr_bci + " (" + getBytes() + " bytes)";
        } else {
            return getHolder().replace('/', '.') + "::" + getName() + " (" + getBytes() + " bytes)";
        }
    }

    @Override
    public String toString() {
        return getHolder().replace('/', '.') + "::" + getName() + " (" + getBytes() + " bytes)";
    }

    public String getHolder() {
        return holder;
    }

    public void setHolder(String holder) {
        this.holder = holder;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    public String getBytes() {
        return bytes;
    }

    public void setBytes(String bytes) {
        this.bytes = bytes;
    }

    public String getIICount() {
        return iicount;
    }

    public void setIICount(String iicount) {
        this.iicount = iicount;
    }

    public String getFlags() {
        return flags;
    }

    public void setFlags(String flags) {
        this.flags = flags;
    }
}
