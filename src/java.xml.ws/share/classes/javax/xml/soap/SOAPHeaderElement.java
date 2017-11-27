/*
 * Copyright (c) 2004, 2017, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.soap;

/**
 * An object representing the contents in the SOAP header part of the
 * SOAP envelope.
 * The immediate children of a {@code SOAPHeader} object can
 * be represented only as {@code SOAPHeaderElement} objects.
 * <P>
 * A {@code SOAPHeaderElement} object can have other
 * {@code SOAPElement} objects as its children.
 *
 * @since 1.6
 */
public interface SOAPHeaderElement extends SOAPElement {

    /**
     * Sets the actor associated with this {@code SOAPHeaderElement}
     * object to the specified actor. The default value of an actor is:
     *          {@code SOAPConstants.URI_SOAP_ACTOR_NEXT}
     * <P>
     * If this {@code SOAPHeaderElement} supports SOAP 1.2 then this call is
     * equivalent to {@link #setRole(String)}
     *
     * @param  actorURI a {@code String} giving the URI of the actor
     *           to set
     *
     * @exception IllegalArgumentException if there is a problem in
     * setting the actor.
     *
     * @see #getActor
     */
    public void setActor(String actorURI);

    /**
     * Sets the {@code Role} associated with this {@code SOAPHeaderElement}
     * object to the specified {@code Role}.
     *
     * @param uri the URI of the {@code Role}
     *
     * @throws SOAPException if there is an error in setting the role
     *
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Fault Role.
     *
     * @since 1.6, SAAJ 1.3
     */
    public void setRole(String uri) throws SOAPException;

    /**
     * Returns the uri of the <i>actor</i> attribute of this
     * {@code SOAPHeaderElement}.
     *<P>
     * If this {@code SOAPHeaderElement} supports SOAP 1.2 then this call is
     * equivalent to {@link #getRole()}
     * @return  a {@code String} giving the URI of the actor
     * @see #setActor
     */
    public String getActor();

    /**
     * Returns the value of the <i>Role</i> attribute of this
     * {@code SOAPHeaderElement}.
     *
     * @return a {@code String} giving the URI of the {@code Role}
     *
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Fault Role.
     *
     * @since 1.6, SAAJ 1.3
     */
    public String getRole();

    /**
     * Sets the mustUnderstand attribute for this {@code SOAPHeaderElement}
     * object to be either true or false.
     * <P>
     * If the mustUnderstand attribute is on, the actor who receives the
     * {@code SOAPHeaderElement} must process it correctly. This
     * ensures, for example, that if the {@code SOAPHeaderElement}
     * object modifies the message, that the message is being modified correctly.
     *
     * @param mustUnderstand {@code true} to set the mustUnderstand
     *        attribute to true; {@code false} to set it to false
     *
     * @exception IllegalArgumentException if there is a problem in
     * setting the mustUnderstand attribute
     * @see #getMustUnderstand
     * @see #setRelay
     */
    public void setMustUnderstand(boolean mustUnderstand);

    /**
     * Returns the boolean value of the mustUnderstand attribute for this
     * {@code SOAPHeaderElement}.
     *
     * @return {@code true} if the mustUnderstand attribute of this
     *        {@code SOAPHeaderElement} object is turned on; {@code false}
     *         otherwise
     */
    public boolean getMustUnderstand();

    /**
     * Sets the <i>relay</i> attribute for this {@code SOAPHeaderElement} to be
     * either true or false.
     * <P>
     * The SOAP relay attribute is set to true to indicate that the SOAP header
     * block must be relayed by any node that is targeted by the header block
     * but not actually process it. This attribute is ignored on header blocks
     * whose mustUnderstand attribute is set to true or that are targeted at
     * the ultimate reciever (which is the default). The default value of this
     * attribute is {@code false}.
     *
     * @param relay the new value of the <i>relay</i> attribute
     *
     * @exception SOAPException if there is a problem in setting the
     * relay attribute.
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Relay attribute.
     *
     * @see #setMustUnderstand
     * @see #getRelay
     *
     * @since 1.6, SAAJ 1.3
     */
    public void setRelay(boolean relay) throws SOAPException;

    /**
     * Returns the boolean value of the <i>relay</i> attribute for this
     * {@code SOAPHeaderElement}
     *
     * @return {@code true} if the relay attribute is turned on;
     * {@code false} otherwise
     *
     * @exception UnsupportedOperationException if this message does not
     *      support the SOAP 1.2 concept of Relay attribute.
     *
     * @see #getMustUnderstand
     * @see #setRelay
     *
     * @since 1.6, SAAJ 1.3
     */
    public boolean getRelay();
}
