/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.pept.transport;

import java.util.Iterator;

/**
 * <code>ContactInfoIterator</code> is used to retrieve individual
 * {@link com.sun.corba.se.pept.transport.ContactInfo ContactInfo}.
 *
 * @author Harold Carr
 */
public interface ContactInfoListIterator
    extends
        Iterator
{
    /**
     * The underlying list for this iterator.
     *
     * @return The underlying list for this iterator.
     */
    public ContactInfoList getContactInfoList();

    /**
     * Used to report information to the iterator to be used
     * in future invocations.
     *
     * @param contactInfo The
     * {@link com.sun.corba.se.pept.transport.ContactInfo ContactInfo}
     * obtained from this iterator which resulted in a successful invocation.
     */
    public void reportSuccess(ContactInfo contactInfo);

    /**
     * Used to report information to the iterator to be used
     * in future invocations.
     *
     * @param contactInfo The
     * {@link com.sun.corba.se.pept.transport.ContactInfo ContactInfo}
     * in effect when an invocation exception occurs.
     * @param exception The
     * {@link java.lang.RuntimeException RuntimeException}.
     *
     * @return Returns true if the request should be retried.
     */
    public boolean reportException(ContactInfo contactInfo,
                                   RuntimeException exception);

    /**
     * The exception to report to the presentation block.
     *
     * @return If the iterator reaches the end before the invocation
     * is successful one returns this exception (previously reported to
     * the iterator via {@link #reportException}).

     */
    public RuntimeException getFailureException();
}

// End of file.
