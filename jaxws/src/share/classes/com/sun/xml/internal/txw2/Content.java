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

package com.sun.xml.internal.txw2;

/**
 * @author Kohsuke Kawaguchi
 */
abstract class Content {
    private Content next;

    /**
     * Returns null if the next token has not decided yet.
     */
    final Content getNext() {
        return next;
    }

    /**
     *
     * @param doc
     *      A {@link Content} object is so light-weight that
     *      it doesn't even remember what document it belongs to.
     *      So the caller needs to "remind" a {@link Content}
     *      who its owner is.
     */
    final void setNext(Document doc,Content next) {
        assert next!=null;
        assert this.next==null : "next of "+this+" is already set to "+this.next;
        this.next = next;
        doc.run();
    }

    /**
     * Returns true if this content is ready to be committed.
     */
    boolean isReadyToCommit() {
        return true;
    }

    /**
     * Returns true if this {@link Content} can guarantee that
     * no more new namespace decls is necessary for the currently
     * pending start tag.
     */
    abstract boolean concludesPendingStartTag();

    /**
     * Accepts a visitor.
     */
    abstract void accept(ContentVisitor visitor);

    /**
     * Called when this content is written to the output.
     */
    public void written() {
    }
}
