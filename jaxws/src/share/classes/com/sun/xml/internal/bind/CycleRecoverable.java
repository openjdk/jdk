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

package com.sun.xml.internal.bind;

import javax.xml.bind.Marshaller;

/**
 * Optional interface that can be implemented by JAXB-bound objects
 * to handle cycles in the object graph.
 *
 * <p>
 * As discussed in <a href="https://jaxb.dev.java.net/guide/Mapping_cyclic_references_to_XML.html">
 * the users' guide</a>, normally a cycle in the object graph causes the marshaller to report an error,
 * and when an error is found, the JAXB RI recovers by cutting the cycle arbitrarily.
 * This is not always a desired behavior.
 *
 * <p>
 * Implementing this interface allows user application to change this behavior.
 * Also see <a href="http://forums.java.net/jive/thread.jspa?threadID=13670">this related discussion</a>.
 *
 * @since JAXB 2.1 EA2
 * @author Kohsuke Kawaguchi
 */
public interface CycleRecoverable {
    /**
     * Called when a cycle is detected by the JAXB RI marshaller
     * to nominate a new object to be marshalled instead.
     *
     * @param context
     *      This object is provided by the JAXB RI to inform
     *      the object about the marshalling process that's going on.
     *
     *
     * @return
     *      the object to be marshalled instead of <tt>this</tt> object.
     *      Or return null to indicate that the JAXB RI should behave
     *      just like when your object does not implement {@link CycleRecoverable}
     *      (IOW, cut the cycle arbitrarily and try to go on.)
     */
    Object onCycleDetected(Context context);

    /**
     * This interface is implemented by the JAXB RI to provide
     * information about the on-going marshalling process.
     *
     * <p>
     * We may add more methods in the future, so please do not
     * implement this interface in your application.
     */
    public interface Context {
        /**
         * Returns the marshaller object that's doing the marshalling.
         *
         * @return
         *      always non-null.
         */
        Marshaller getMarshaller();
    }
}
