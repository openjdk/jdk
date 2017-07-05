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
package com.sun.xml.internal.ws.message;

import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.api.message.Attachment;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Default dumb {@link AttachmentSet} implementation backed by {@link ArrayList}.
 *
 * <p>
 * The assumption here is that the number of attachments are small enough to
 * justify linear search in {@link #get(String)}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class AttachmentSetImpl implements AttachmentSet {

    private final ArrayList<Attachment> attList = new ArrayList<Attachment>();

    /**
     * Creates an empty {@link AttachmentSet}.
     */
    public AttachmentSetImpl() {
    }

    /**
     * Creates an {@link AttachmentSet} by copying contents from another.
     */
    public AttachmentSetImpl(Iterable<Attachment> base) {
        for (Attachment a : base)
            add(a);
    }

    public Attachment get(String contentId) {
        for( int i=attList.size()-1; i>=0; i-- ) {
            Attachment a = attList.get(i);
            if(a.getContentId().equals(contentId))
                return a;
        }
        return null;
    }

    public boolean isEmpty() {
        return attList.isEmpty();
    }

    public void add(Attachment att) {
        attList.add(att);
    }

    public Iterator<Attachment> iterator() {
        return attList.iterator();
    }

}
