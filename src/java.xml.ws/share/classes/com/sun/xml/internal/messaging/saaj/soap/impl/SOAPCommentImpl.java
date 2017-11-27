/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.messaging.saaj.soap.impl;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Comment;
import org.w3c.dom.DOMException;
import org.w3c.dom.Text;

import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import static com.sun.xml.internal.messaging.saaj.soap.impl.TextImpl.log;

public class SOAPCommentImpl extends TextImpl<Comment> implements Comment {

    public SOAPCommentImpl(SOAPDocumentImpl ownerDoc, String text) {
        super(ownerDoc, text);
    }

    public SOAPCommentImpl(SOAPDocumentImpl ownerDoc, CharacterData data) {
        super(ownerDoc, data);
    }

    @Override
    protected Comment createN(SOAPDocumentImpl ownerDoc, String text) {
        return ownerDoc.getDomDocument().createComment(text);
    }

    @Override
    protected Comment createN(SOAPDocumentImpl ownerDoc, CharacterData data) {
        return (Comment) data;
    }

    @Override
    public boolean isComment() {
        return true;
    }

    @Override
    public Text splitText(int offset) throws DOMException {
        log.severe("SAAJ0113.impl.cannot.split.text.from.comment");
        throw new UnsupportedOperationException("Cannot split text from a Comment Node.");
    }

    @Override
    public Text replaceWholeText(String content) throws DOMException {
        log.severe("SAAJ0114.impl.cannot.replace.wholetext.from.comment");
        throw new UnsupportedOperationException("Cannot replace Whole Text from a Comment Node.");
    }

    @Override
    public String getWholeText() {
        //TODO: maybe we have to implement this in future.
        throw new UnsupportedOperationException("Not Supported");
    }

    @Override
    public boolean isElementContentWhitespace() {
        //TODO: maybe we have to implement this in future.
        throw new UnsupportedOperationException("Not Supported");
    }

}
