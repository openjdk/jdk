/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime;

import javax.xml.bind.JAXBException;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.attachment.AttachmentMarshaller;
import javax.xml.bind.attachment.AttachmentUnmarshaller;

import com.sun.xml.internal.bind.api.BridgeContext;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallerImpl;

/**
 * {@link BridgeContext} implementation.
 *
 * @author Kohsuke Kawaguchi
 */
public final class BridgeContextImpl extends BridgeContext {

    public final UnmarshallerImpl unmarshaller;
    public final MarshallerImpl marshaller;

    BridgeContextImpl(JAXBContextImpl context) {
        unmarshaller = context.createUnmarshaller();
        marshaller = context.createMarshaller();
    }

    public void setErrorHandler(ValidationEventHandler handler) {
        try {
            unmarshaller.setEventHandler(handler);
            marshaller.setEventHandler(handler);
        } catch (JAXBException e) {
            // impossible
            throw new Error(e);
        }
    }

    public void setAttachmentMarshaller(AttachmentMarshaller m) {
        marshaller.setAttachmentMarshaller(m);
    }

    public void setAttachmentUnmarshaller(AttachmentUnmarshaller u) {
        unmarshaller.setAttachmentUnmarshaller(u);
    }

    public AttachmentMarshaller getAttachmentMarshaller() {
        return marshaller.getAttachmentMarshaller();
    }

    public AttachmentUnmarshaller getAttachmentUnmarshaller() {
        return unmarshaller.getAttachmentUnmarshaller();
    }
}
