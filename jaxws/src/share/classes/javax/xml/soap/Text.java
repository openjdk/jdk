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
/*
 * $Id: Text.java,v 1.3 2005/04/05 20:34:16 mk125090 Exp $
 * $Revision: 1.3 $
 * $Date: 2005/04/05 20:34:16 $
 */


package javax.xml.soap;

/**
 * A representation of a node whose value is text.  A <code>Text</code> object
 * may represent text that is content or text that is a comment.
 *
 */
public interface Text extends Node, org.w3c.dom.Text {

    /**
     * Retrieves whether this <code>Text</code> object represents a comment.
     *
     * @return <code>true</code> if this <code>Text</code> object is a
     *         comment; <code>false</code> otherwise
     */
    public boolean isComment();
}
