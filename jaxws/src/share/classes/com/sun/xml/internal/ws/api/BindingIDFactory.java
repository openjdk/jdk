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

package com.sun.xml.internal.ws.api;

import com.sun.istack.internal.Nullable;
import com.sun.istack.internal.NotNull;

import javax.xml.ws.WebServiceException;

/**
 * Extension point to plug in additional {@link BindingID} parsing logic.
 *
 * <p>
 * When the JAX-WS RI is asked to parse a binding ID string into a {@link BindingID}
 * object, it uses service idiom to look for the implementations of this class
 * in the <tt>META-INF/services/...</tt>.
 *
 * @since JAX-WS 2.0.next
 * @author Kohsuke Kawaguchi
 * @see BindingID#parse(String)
 */
public abstract class BindingIDFactory {
    /**
     * Parses a binding ID string into {@link BindingID} if possible.
     *
     * @return
     *      a non-null return value would cause the JAX-WS RI to consider
     *      the parsing to be successful. No furhter {@link BindingIDFactory}
     *      will be consulted.
     *
     *      <p>
     *      Retruning a null value indicates that this factory doesn't understand
     *      this string, in which case the JAX-WS RI will keep asking next
     *      {@link BindingIDFactory}.
     *
     * @throws WebServiceException
     *      if the implementation understood the lexical value but it is not correct,
     *      this exception can be thrown to abort the parsing with error.
     *      No further {@link BindingIDFactory} will be consulted, and
     *      {@link BindingID#parse(String)} will throw the exception.
     */
    public abstract @Nullable BindingID parse(@NotNull String lexical) throws WebServiceException;
}
