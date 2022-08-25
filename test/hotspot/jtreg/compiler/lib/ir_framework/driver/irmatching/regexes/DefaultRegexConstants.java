/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.regexes;

/**
 * This class provides constants for defining default regexes.
 */
public class DefaultRegexConstants {
    public static final String START = "(\\d+(\\s){2}(";
    public static final String MID = ".*)+(\\s){2}===.*";
    public static final String END = ")";
    public static final String IS_REPLACED = "#IS_REPLACED#"; // Is replaced by an additional user-defined string.
    public static final String STORE_OF_CLASS_POSTFIX = "(:|\\+)\\S* \\*" + END;
    public static final String LOAD_OF_CLASS_POSTFIX = "(:|\\+)\\S* \\*" + END;
}
