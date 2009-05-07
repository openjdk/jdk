/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package org.relaxng.datatype;

/**
 * Signals Datatype related exceptions.
 *
 * @author <a href="mailto:jjc@jclark.com">James Clark</a>
 * @author <a href="mailto:kohsuke.kawaguchi@sun.com">Kohsuke KAWAGUCHI</a>
 */
public class DatatypeException extends Exception {

        public DatatypeException( int index, String msg ) {
                super(msg);
                this.index = index;
        }
        public DatatypeException( String msg ) {
                this(UNKNOWN,msg);
        }
        /**
         * A constructor for those datatype libraries which don't support any
         * diagnostic information at all.
         */
        public DatatypeException() {
                this(UNKNOWN,null);
        }


        private final int index;

        public static final int UNKNOWN = -1;

        /**
         * Gets the index of the content where the error occured.
         * UNKNOWN can be returned to indicate that no index information
         * is available.
         */
        public int getIndex() {
                return index;
        }
}
