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
package com.sun.xml.internal.bind.api;

import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.attachment.AttachmentMarshaller;
import javax.xml.bind.attachment.AttachmentUnmarshaller;

/**
 * Holds thread specific state information for {@link Bridge}s,
 * to make {@link Bridge} thread-safe.
 *
 * <p>
 * This object cannot be used concurrently; two threads cannot
 * use the same object with {@link Bridge}s at the same time, nor
 * a thread can use a {@link BridgeContext} with one {@link Bridge} while
 * the same context is in use by another {@link Bridge}.
 *
 * <p>
 * {@link BridgeContext} is relatively a heavy-weight object, and
 * therefore it is expected to be cached by the JAX-RPC RI.
 *
 * <p>
 * <b>Subject to change without notice</b>.
 *
 * @author Kohsuke Kawaguchi
 * @since 2.0 EA1
 * @see Bridge
 * @deprecated
 *      The caller no longer needs to use this, as {@link Bridge} has
 *      methods that can work without {@link BridgeContext}.
 */
public abstract class BridgeContext {
    protected BridgeContext() {}

    /**
     * Registers the error handler that receives unmarshalling/marshalling errors.
     *
     * @param handler
     *      can be null, in which case all errors will be considered fatal.
     *
     * @since 2.0 EA1
     */
    public abstract void setErrorHandler(ValidationEventHandler handler);

    /**
     * Sets the {@link AttachmentMarshaller}.
     *
     * @since 2.0 EA1
     */
    public abstract void setAttachmentMarshaller(AttachmentMarshaller m);

    /**
     * Sets the {@link AttachmentUnmarshaller}.
     *
     * @since 2.0 EA1
     */
    public abstract void setAttachmentUnmarshaller(AttachmentUnmarshaller m);

    /**
     * Gets the last {@link AttachmentMarshaller} set through
     * {@link AttachmentMarshaller}.
     *
     * @since 2.0 EA2
     */
    public abstract AttachmentMarshaller getAttachmentMarshaller();

    /**
     * Gets the last {@link AttachmentUnmarshaller} set through
     * {@link AttachmentUnmarshaller}.
     *
     * @since 2.0 EA2
     */
    public abstract AttachmentUnmarshaller getAttachmentUnmarshaller();
}
