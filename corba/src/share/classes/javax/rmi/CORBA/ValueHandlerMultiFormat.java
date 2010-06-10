/*
 * Copyright (c) 2002, 2006, Oracle and/or its affiliates. All rights reserved.
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

package javax.rmi.CORBA;

/**
 * Java to IDL ptc 02-01-12 1.5.1.5
 * @since 1.5
 */
public interface ValueHandlerMultiFormat extends ValueHandler {

    /**
     * Returns the maximum stream format version for
     * RMI/IDL custom value types that is supported
     * by this ValueHandler object. The ValueHandler
     * object must support the returned stream format version and
     * all lower versions.
     *
     * An ORB may use this value to include in a standard
     * IOR tagged component or service context to indicate to other
     * ORBs the maximum RMI-IIOP stream format that it
     * supports.  If not included, the default for GIOP 1.2
     * is stream format version 1, and stream format version
     * 2 for GIOP 1.3 and higher.
     */
    byte getMaximumStreamFormatVersion();

    /**
     * Allows the ORB to pass the stream format
     * version for RMI/IDL custom value types. If the ORB
     * calls this method, it must pass a stream format version
     * between 1 and the value returned by the
     * getMaximumStreamFormatVersion method inclusive,
     * or else a BAD_PARAM exception with standard minor code
     * will be thrown.
     *
     * If the ORB calls the older ValueHandler.writeValue(OutputStream,
     * Serializable) method, stream format version 1 is implied.
     *
     * The ORB output stream passed to the ValueHandlerMultiFormat.writeValue
     * method must implement the ValueOutputStream interface, and the
     * ORB input stream passed to the ValueHandler.readValue method must
     * implement the ValueInputStream interface.
     */
    void writeValue(org.omg.CORBA.portable.OutputStream out,
                    java.io.Serializable value,
                    byte streamFormatVersion);
}
