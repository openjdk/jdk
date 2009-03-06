/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package pkg1;

import java.io.IOException;
import java.io.Serializable;

/**
 * A class comment for testing.
 *
 * @author      Bhavesh Patel
 * @see C2
 * @since       JDK1.0
 */

public class C1 implements Serializable {

    /**
     * This field indicates whether the C1 is undecorated.
     *
     * @see #setUndecorated(boolean)
     * @since 1.4
     * @serial
     * @deprecated As of JDK version 1.5, replaced by
     * {@link C1#setUndecorated(boolean) setUndecorated(boolean)}.
     */
     @Deprecated
    public boolean undecorated = false;

    private String title;

    /**
     * This enum specifies the possible modal exclusion types.
     *
     * @since 1.6
     */
    public static enum ModalExclusionType {
        /**
         * No modal exclusion.
         */
        NO_EXCLUDE,
        /**
         * <code>APPLICATION_EXCLUDE</code> indicates that a top-level window
         * won't be blocked by any application-modal dialogs. Also, it isn't
         * blocked by document-modal dialogs from outside of its child hierarchy.
         */
        APPLICATION_EXCLUDE
    };

    /**
     * Constructor.
     *
     * @param title the title
     * @param test boolean value
     * @exception IllegalArgumentException if the <code>owner</code>'s
     *     <code>GraphicsConfiguration</code> is not from a screen device
     * @exception HeadlessException
     */
     public C1(String title, boolean test) {

     }

     public C1(String title) {

     }

    /**
     * Method comments.
     * @param  undecorated <code>true</code> if no decorations are
     *         to be enabled;
     *         <code>false</code> if decorations are to be enabled.
     * @see    #readObject()
     * @since 1.4
     */
    public void setUndecorated(boolean undecorated) {
        /* Make sure we don't run in the middle of peer creation.*/
    }

    /**
     * @see #setUndecorated(boolean)
     */
    public void readObject() throws IOException {

    }
}
