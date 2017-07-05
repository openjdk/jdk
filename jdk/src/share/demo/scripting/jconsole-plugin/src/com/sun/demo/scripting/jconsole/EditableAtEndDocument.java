/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.sun.demo.scripting.jconsole;

import javax.swing.text.*;

/** This class implements a special type of document in which edits
 * can only be performed at the end, from "mark" to the end of the
 * document. This is used in ScriptShellPanel class as document for editor.
 */
public class EditableAtEndDocument extends PlainDocument {

    private static final long serialVersionUID = 5358116444851502167L;
    private int mark;

    @Override
    public void insertString(int offset, String text, AttributeSet a)
        throws BadLocationException {
        int len = getLength();
        super.insertString(len, text, a);
    }

    @Override
    public void remove(int offs, int len) throws BadLocationException {
        int start = offs;
        int end = offs + len;

        int markStart = mark;
        int markEnd = getLength();

        if ((end < markStart) || (start > markEnd)) {
            // no overlap
            return;
        }

        // Determine interval intersection
        int cutStart = Math.max(start, markStart);
        int cutEnd = Math.min(end, markEnd);
        super.remove(cutStart, cutEnd - cutStart);
    }

    public void setMark() {
        mark = getLength();
    }

    public String getMarkedText() throws BadLocationException {
        return getText(mark, getLength() - mark);
    }

    /** Used to reset the contents of this document */
    public void clear() {
        try {
            super.remove(0, getLength());
            setMark();
        } catch (BadLocationException e) {
        }
    }
}
