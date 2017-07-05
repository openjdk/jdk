/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.codemodel.internal;


/**
 * Modifier constants.
 */
public final class JMod {
    public final static int NONE         = 0x000;
    public final static int PUBLIC       = 0x001;
    public final static int PROTECTED    = 0x002;
    public final static int PRIVATE      = 0x004;
    public final static int FINAL        = 0x008;
    public final static int STATIC       = 0x010;
    public final static int ABSTRACT     = 0x020;
    public final static int NATIVE       = 0x040;
    public final static int SYNCHRONIZED = 0x080;
    public final static int TRANSIENT    = 0x100;
    public final static int VOLATILE     = 0x200;
}
