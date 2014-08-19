/*
 * Copyright (c) 1998, 2001, Oracle and/or its affiliates. All rights reserved.
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

package org.omg.CORBA;

/**
 * An object that represents an ORB service: its <code>service_detail_type</code>
 * field contains the type of the ORB service, and its <code>service_detail</code>
 * field contains a description of the ORB service.

 *
 * @author RIP Team
 */
public final class ServiceDetail implements org.omg.CORBA.portable.IDLEntity
{
    /**
     * The type of the ORB service that this <code>ServiceDetail</code>
     * object represents.
     */
    public int service_detail_type;

    /**
     * The data describing the ORB service that this <code>ServiceDetail</code>
     * object represents.
     */
    public byte[] service_detail;

    /**
     * Constructs a <code>ServiceDetail</code> object with 0 for the type of
     * ORB service and an empty description.
     */
    public ServiceDetail() { }

    /**
     * Constructs a <code>ServiceDetail</code> object with the given
     * ORB service type and the given description.
     *
     * @param service_detail_type an <code>int</code> specifying the type of
     *                            ORB service
     * @param service_detail a <code>byte</code> array describing the ORB service
     */
    public ServiceDetail(int service_detail_type, byte[] service_detail) {
        this.service_detail_type = service_detail_type;
        this.service_detail = service_detail;
    }
}
