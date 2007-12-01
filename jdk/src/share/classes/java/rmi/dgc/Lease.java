/*
 * Copyright 1996-1998 Sun Microsystems, Inc.  All Rights Reserved.
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
package java.rmi.dgc;

/**
 * A lease contains a unique VM identifier and a lease duration. A
 * Lease object is used to request and grant leases to remote object
 * references.
 */
public final class Lease implements java.io.Serializable {

    /**
     * @serial Virtual Machine ID with which this Lease is associated.
     * @see #getVMID
     */
    private VMID vmid;

    /**
     * @serial Duration of this lease.
     * @see #getValue
     */
    private long value;
    /** indicate compatibility with JDK 1.1.x version of class */
    private static final long serialVersionUID = -5713411624328831948L;

    /**
     * Constructs a lease with a specific VMID and lease duration. The
     * vmid may be null.
     * @param id VMID associated with this lease
     * @param duration lease duration
     */
    public Lease(VMID id, long duration)
    {
        vmid = id;
        value = duration;
    }

    /**
     * Returns the client VMID associated with the lease.
     * @return client VMID
     */
    public VMID getVMID()
    {
        return vmid;
    }

    /**
     * Returns the lease duration.
     * @return lease duration
     */
    public long getValue()
    {
        return value;
    }
}
