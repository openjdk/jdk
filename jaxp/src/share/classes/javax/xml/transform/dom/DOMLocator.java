/*
 * Copyright 2000-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.xml.transform.dom;

import javax.xml.transform.SourceLocator;

import org.w3c.dom.Node;


/**
 * Indicates the position of a node in a source DOM, intended
 * primarily for error reporting.  To use a DOMLocator, the receiver of an
 * error must downcast the {@link javax.xml.transform.SourceLocator}
 * object returned by an exception. A {@link javax.xml.transform.Transformer}
 * may use this object for purposes other than error reporting, for instance,
 * to indicate the source node that originated a result node.
 */
public interface DOMLocator extends SourceLocator {

    /**
     * Return the node where the event occurred.
     *
     * @return The node that is the location for the event.
     */
    public Node getOriginatingNode();
}
