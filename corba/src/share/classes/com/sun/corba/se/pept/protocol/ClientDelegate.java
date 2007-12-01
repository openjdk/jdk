/*
 * Copyright 2001-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.pept.protocol;

import com.sun.corba.se.pept.broker.Broker;
import com.sun.corba.se.pept.transport.ContactInfoList;

/**
 * <p>The presentation block interacts with the PEPt architecture
 * via the <code>ClientDelegate</code>.</p>
 *
 * @author Harold Carr
 */
public interface ClientDelegate
{
    /**
     * The {@link com.sun.corba.se.pept.broker.Broker Broker} associated
     * with an invocation.
     *
     * @return {@link com.sun.corba.se.pept.broker.Broker Broker}
     */
    public Broker getBroker();

    /**
     * Get the
     * {@link com.sun.corba.se.pept.transport.ContactInfoList ContactInfoList}
     * which represents they encoding/protocol/transport combinations that
     * may be used to contact the service.
     *
     * @return
     * {@link com.sun.corba.se.pept.transport.ContactInfoList ContactInfoList}
     */
    public ContactInfoList getContactInfoList();
}

// End of file.
