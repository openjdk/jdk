/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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

package java.awt;

/**
 * Thrown when code that is dependent on a keyboard, display, or mouse
 * is called in an environment that does not support a keyboard, display,
 * or mouse.
 *
 * @since 1.4
 * @author  Michael Martak
 */
public class HeadlessException extends UnsupportedOperationException {
    /*
     * JDK 1.4 serialVersionUID
     */
    private static final long serialVersionUID = 167183644944358563L;

    /**
     * Constructs new {@code HeadlessException}
     */
    public HeadlessException() {}

    /**
     * Create a new instance with the specified detailed error message.
     *
     * @param  msg the error message
     */
    public HeadlessException(String msg) {
        super(msg);
    }

    /**
     * {@inheritDoc}
     */
    public String getMessage() {
        String superMessage = super.getMessage();
        String headlessMessage = GraphicsEnvironment.getHeadlessMessage();

        if (superMessage == null) {
            return headlessMessage;
        } else if (headlessMessage == null) {
            return superMessage;
        } else {
            return superMessage + headlessMessage;
        }
    }
}
