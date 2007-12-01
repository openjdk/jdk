/*
 * Copyright 2000-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.jgss;

import org.ietf.jgss.*;

/**
 * This class helps overcome a limitation of the org.ietf.jgss.GSSException
 * class that does not allow the thrower to set a string corresponding to
 * the major code.
 */
public class GSSExceptionImpl extends GSSException {

    private static final long serialVersionUID = 4251197939069005575L;

    private String majorMessage;

    /**
     * A constructor that takes the majorCode as well as the mech oid that
     * will be appended to the standard message defined in its super class.
     */
    GSSExceptionImpl(int majorCode, Oid mech) {
        super(majorCode);
        this.majorMessage = super.getMajorString() + ": " + mech;
    }

    /**
     * A constructor that takes the majorCode as well as the message that
     * corresponds to it.
     */
    public GSSExceptionImpl(int majorCode, String majorMessage) {
        super(majorCode);
        this.majorMessage = majorMessage;
    }

    /**
     * A constructor that takes the majorCode and the exception cause.
     */
    public GSSExceptionImpl(int majorCode, Exception cause) {
        super(majorCode);
        initCause(cause);
    }

    /**
     * A constructor that takes the majorCode, the message that
     * corresponds to it, and the exception cause.
     */
    public GSSExceptionImpl(int majorCode, String majorMessage,
        Exception cause) {
        this(majorCode, majorMessage);
        initCause(cause);
    }

    /**
     * Returns the message that was embedded in this object, otherwise it
     * returns the default message that an org.ietf.jgss.GSSException
     * generates.
     */
    public String getMessage() {
        if (majorMessage != null)
            return majorMessage;
        else
            return super.getMessage();
    }

}
