/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.model;

/**
 * Constructor declaration.
 *
 * <p>
 * a constructor declaration consists of a set of fields to be initialized.
 * For example, if a class is defined as:
 *
 * <pre>
 * Class: Foo
 *   Field: String a
 *   Field: int b
 *   Field: BigInteger c
 * </pre>
 *
 * Then a constructor declaration of {"a","c"} will conceptually
 * generate the following consturctor:
 *
 * <pre>
 * Foo( String _a, BigInteger _c ) {
 *   a=_a; c=_c;
 * }
 * </pre>
 *
 * (Only conceptually, because Foo will likely to become an interface
 * so we can't simply generate a constructor like this.)
 *
 * @author
 *    <a href="mailto:kohsuke.kawaguchi@sun.com">Kohsuke KAWAGUCHI</a>
 */
public class Constructor
{
    // Since Constructor is typically built when there is no FieldItem
    // nor FieldUse, we need to rely on Strings.
    public Constructor( String[] _fields ) { this.fields = _fields; }

    /** array of field names to be initialized. */
    public final String[] fields;
}
