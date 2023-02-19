/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.x509;

import sun.security.util.*;

/**
 * This interface specifies the abstract methods which have to be
 * implemented by all the members of the GeneralNames ASN.1 object.
 *
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 */
public interface GeneralNameInterface extends DerEncoder {
    /**
     * The list of names supported.
     */
    int NAME_ANY = 0;
    int NAME_RFC822 = 1;
    int NAME_DNS = 2;
    int NAME_X400 = 3;
    int NAME_DIRECTORY = 4;
    int NAME_EDI = 5;
    int NAME_URI = 6;
    int NAME_IP = 7;
    int NAME_OID = 8;

    /**
     * The list of constraint results.
     */
    int NAME_DIFF_TYPE = -1; /* input name is different type from name (i.e. does not constrain) */
    int NAME_MATCH = 0;      /* input name matches name */
    int NAME_NARROWS = 1;    /* input name narrows name */
    int NAME_WIDENS = 2;     /* input name widens name */
    int NAME_SAME_TYPE = 3;  /* input name does not match, narrow, or widen, but is same type */

    /**
     * Return the type of the general name, as
     * defined above.
     */
    int getType();

    /**
     * Return type of constraint inputName places on this name:<ul>
     *   <li>NAME_DIFF_TYPE = -1: input name is different type from name (i.e. does not constrain).
     *   <li>NAME_MATCH = 0: input name matches name.
     *   <li>NAME_NARROWS = 1: input name narrows name (is lower in the naming subtree)
     *   <li>NAME_WIDENS = 2: input name widens name (is higher in the naming subtree)
     *   <li>NAME_SAME_TYPE = 3: input name does not match or narrow name, but is same type.
     * </ul>.  These results are used in checking NameConstraints during
     * certification path verification.
     *
     * @param inputName to be checked for being constrained
     * @return constraint type above
     * @throws UnsupportedOperationException if name is same type, but comparison operations are
     *          not supported for this name type.
     */
    int constrains(GeneralNameInterface inputName) throws UnsupportedOperationException;

    /**
     * Return subtree depth of this name for purposes of determining
     * NameConstraints minimum and maximum bounds and for calculating
     * path lengths in name subtrees.
     *
     * @return distance of name from root
     * @throws UnsupportedOperationException if not supported for this name type
     */
    int subtreeDepth() throws UnsupportedOperationException;
}
