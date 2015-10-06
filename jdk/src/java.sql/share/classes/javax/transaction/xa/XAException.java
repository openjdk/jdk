/*
 * Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
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

package javax.transaction.xa;

/**
 * The XAException is thrown by the Resource Manager (RM) to inform the
 * Transaction Manager of an error encountered by the involved transaction.
 *
 */
public class XAException extends Exception {

    /**
     * Specify serialVersionUID for backward compatibility
     */
    private static final long serialVersionUID = -8249683284832867751L;

    /**
     * The error code with which to create the SystemException.
     *
     * @serial The error code for the exception
     */
    public int errorCode;

    /**
     * Create an XAException.
     */
    public XAException() {
        super();
    }

    /**
     * Create an XAException with a given string.
     *
     * @param s The <code>String</code> object containing the exception
     *          message.
     */
    public XAException(String s) {
        super(s);
    }

    /**
     * Create an XAException with a given error code.
     *
     * @param errcode The error code identifying the exception.
     */
    public XAException(int errcode) {
        super();
        errorCode = errcode;
    }

    /**
     * The inclusive lower bound of the rollback codes.
     */
    public final static int XA_RBBASE = 100;

    /**
     * Indicates that the rollback was caused by an unspecified reason.
     */
    public final static int XA_RBROLLBACK = XA_RBBASE;

    /**
     * Indicates that the rollback was caused by a communication failure.
     */
    public final static int XA_RBCOMMFAIL = XA_RBBASE + 1;

    /**
     * A deadlock was detected.
     */
    public final static int XA_RBDEADLOCK = XA_RBBASE + 2;

    /**
     * A condition that violates the integrity of the resource was detected.
     */
    public final static int XA_RBINTEGRITY = XA_RBBASE + 3;

    /**
     * The resource manager rolled back the transaction branch for a reason
     * not on this list.
     */
    public final static int XA_RBOTHER = XA_RBBASE + 4;

    /**
     * A protocol error occurred in the resource manager.
     */
    public final static int XA_RBPROTO = XA_RBBASE + 5;

    /**
     * A transaction branch took too long.
     */
    public final static int XA_RBTIMEOUT = XA_RBBASE + 6;

    /**
     * May retry the transaction branch.
     */
    public final static int XA_RBTRANSIENT = XA_RBBASE + 7;

    /**
     * The inclusive upper bound of the rollback error code.
     */
    public final static int XA_RBEND = XA_RBTRANSIENT;

    /**
     * Resumption must occur where the suspension occurred.
     */
    public final static int XA_NOMIGRATE = 9;

    /**
     * The transaction branch may have been heuristically completed.
     */
    public final static int XA_HEURHAZ = 8;

    /**
     * The transaction branch has been heuristically committed.
     */
    public final static int XA_HEURCOM = 7;

    /**
     * The transaction branch has been heuristically rolled back.
     */
    public final static int XA_HEURRB = 6;

    /**
     * The transaction branch has been heuristically committed and
     * rolled back.
     */
    public final static int XA_HEURMIX = 5;

    /**
     * Routine returned with no effect and may be reissued.
     */
    public final static int XA_RETRY = 4;

    /**
     * The transaction branch was read-only and has been committed.
     */
    public final static int XA_RDONLY = 3;

    /**
     * There is an asynchronous operation already outstanding.
     */
    public final static int XAER_ASYNC = -2;

    /**
     * A resource manager error has occurred in the transaction branch.
     */
    public final static int XAER_RMERR = -3;

    /**
     * The XID is not valid.
     */
    public final static int XAER_NOTA = -4;

    /**
     * Invalid arguments were given.
     */
    public final static int XAER_INVAL = -5;

    /**
     * Routine was invoked in an inproper context.
     */
    public final static int XAER_PROTO = -6;

    /**
     * Resource manager is unavailable.
     */
    public final static int XAER_RMFAIL = -7;

    /**
     * The XID already exists.
     */
    public final static int XAER_DUPID = -8;

    /**
     * The resource manager is doing work outside a global transaction.
     */
    public final static int XAER_OUTSIDE = -9;
}
