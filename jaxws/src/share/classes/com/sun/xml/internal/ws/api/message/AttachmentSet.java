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
package com.sun.xml.internal.ws.api.message;

import com.sun.istack.internal.Nullable;

/**
 * A set of {@link Attachment} on a {@link Message}.
 *
 * <p>
 * A particular attention is made to ensure that attachments
 * can be read and parsed lazily as requested.
 *
 * @see Message#getAttachments()
 */
public interface AttachmentSet extends Iterable<Attachment> {
    /**
     * Gets the attachment by the content ID.
     *
     * @param contentId
     *      The content ID like "foo-bar-zot@abc.com", without
     *      surrounding '&lt;' and '>' used as the transfer syntax.
     *
     * @return null
     *      if no such attachment exist.
     */
    @Nullable
    Attachment get(String contentId);

    /**
     * Returns true if there's no attachment.
     */
    boolean isEmpty();

    /**
     * Adds an attachment to this set.
     *
     * <p>
     * Note that it's OK for an {@link Attachment} to belong to
     * more than one {@link AttachmentSet} (which is in fact
     * necessary when you wrap a {@link Message} into another.
     *
     * @param att
     *      must not be null.
     */
    public void add(Attachment att);

}
