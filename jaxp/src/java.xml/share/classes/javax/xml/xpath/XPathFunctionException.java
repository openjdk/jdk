/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.xpath;

/**
 * <code>XPathFunctionException</code> represents an error with an XPath function.</p>
 *
 * @author  <a href="mailto:Norman.Walsh@Sun.com">Norman Walsh</a>
 * @author  <a href="mailto:Jeff.Suttor@Sun.com">Jeff Suttor</a>
 * @since 1.5
 */
public class XPathFunctionException extends XPathExpressionException {

    /**
     * <p>Stream Unique Identifier.</p>
     */
    private static final long serialVersionUID = -1837080260374986980L;

    /**
     * <p>Constructs a new <code>XPathFunctionException</code> with the specified detail <code>message</code>.</p>
     *
     * <p>The <code>cause</code> is not initialized.</p>
     *
     * <p>If <code>message</code> is <code>null</code>,
     * then a <code>NullPointerException</code> is thrown.</p>
     *
     * @param message The detail message.
     *
     * @throws NullPointerException When <code>message</code> is
     *   <code>null</code>.
     */
    public XPathFunctionException(String message) {
        super(message);
    }

    /**
     * <p>Constructs a new <code>XPathFunctionException</code> with the specified <code>cause</code>.</p>
     *
     * <p>If <code>cause</code> is <code>null</code>,
     * then a <code>NullPointerException</code> is thrown.</p>
     *
     * @param cause The cause.
     *
     * @throws NullPointerException if <code>cause</code> is <code>null</code>.
     */
    public XPathFunctionException(Throwable cause) {
        super(cause);
    }
}
