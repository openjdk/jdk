/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.bind.unmarshaller;

import org.xml.sax.SAXException;

/**
 * Runs by UnmarshallingContext after all the parsing is done.
 *
 * Primarily used to resolve forward IDREFs, but it can run any action.
 *
 * @author Kohsuke Kawaguchi
 */
public interface Patcher {
    /**
     * Runs an post-action.
     *
     * @throws SAXException
     *      if an error is found during the action, it should be reporeted to the context.
     *      The context may then throw a {@link SAXException} to abort the processing,
     *      and that's when you can throw a {@link SAXException}.
     */
    void run() throws SAXException;
}
