/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jshell.remote;

import java.util.regex.Pattern;

/**
 * Communication constants shared between the main process and the remote
 * execution process
 * @author Robert Field
 */
public class RemoteCodes {
    // Command codes
    public static final int CMD_EXIT       = 0;
    public static final int CMD_LOAD       = 1;
    public static final int CMD_INVOKE     = 3;
    public static final int CMD_CLASSPATH  = 4;
    public static final int CMD_VARVALUE   = 5;

    // Return result codes
    public static final int RESULT_SUCCESS   = 100;
    public static final int RESULT_FAIL      = 101;
    public static final int RESULT_EXCEPTION = 102;
    public static final int RESULT_CORRALLED = 103;
    public static final int RESULT_KILLED    = 104;

    public static final String DOIT_METHOD_NAME = "do_it$";
    public static final String replClass = "\\$REPL(?<num>\\d+)[A-Z]*";
    public static final Pattern prefixPattern = Pattern.compile("(REPL\\.)?" + replClass + "[\\$\\.]?");

}
