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

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.Serializable;

/**
 * A class comment for testing.
 *
 * @author      Bhavesh Patel
 * @see C1
 * @since       JDK1.0
 */

public class C2 implements Serializable {

    /**
     * This field indicates title.
     */
    String title;

    public static enum ModalType {
        NO_EXCLUDE
    };

    /**
     * Constructor.
     *
     */
     public C2() {

     }

     public C2(String title) {

     }

     /**
     * Set visible.
     *
     * @param set boolean
     * @since 1.4
     * @deprecated As of JDK version 1.5, replaced by
     * {@link C1#setUndecorated(boolean) setUndecorated(boolean)}.
     */
     @Deprecated
     public void setVisible(boolean set) {
     }

     /**
     * Reads the object stream.
     *
     * @param s ObjectInputStream
     * @throws <code>IOException</code>
     * @deprecated As of JDK version 1.5, replaced by
     * {@link C1#setUndecorated(boolean) setUndecorated(boolean)}.
     */
     @Deprecated
     public void readObject(ObjectInputStream s) throws IOException {
     }
}
