/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 */
package com.sun.beans.decoder;

/**
 * This class is intended to handle &lt;long&gt; element.
 * This element specifies {@code long} values.
 * The class {@link Long} is used as wrapper for these values.
 * The result value is created from text of the body of this element.
 * The body parsing is described in the class {@link StringElementHandler}.
 * For example:<pre>
 * &lt;long&gt;0xFFFF&lt;/long&gt;</pre>
 * is shortcut to<pre>
 * &lt;method name="decode" class="java.lang.Long"&gt;
 *     &lt;string&gt;0xFFFF&lt;/string&gt;
 * &lt;/method&gt;</pre>
 * which is equivalent to {@code Long.decode("0xFFFF")} in Java code.
 * <p>The following atribute is supported:
 * <dl>
 * <dt>id
 * <dd>the identifier of the variable that is intended to store the result
 * </dl>
 *
 * @since 1.7
 *
 * @author Sergey A. Malenkov
 */
final class LongElementHandler extends StringElementHandler {

    /**
     * Creates {@code long} value from
     * the text of the body of this element.
     *
     * @param argument  the text of the body
     * @return evaluated {@code long} value
     */
    @Override
    public Object getValue(String argument) {
        return Long.decode(argument);
    }
}
