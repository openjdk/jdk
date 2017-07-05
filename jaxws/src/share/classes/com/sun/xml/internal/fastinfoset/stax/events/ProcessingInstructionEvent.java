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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */


package com.sun.xml.internal.fastinfoset.stax.events ;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.ProcessingInstruction;


public class ProcessingInstructionEvent extends EventBase implements ProcessingInstruction {

    private String targetName;
    private String _data;

    public ProcessingInstructionEvent() {
        init();
    }

    public ProcessingInstructionEvent(String targetName, String data) {
        this.targetName = targetName;
        _data = data;
        init();
    }

    protected void init() {
        setEventType(XMLStreamConstants.PROCESSING_INSTRUCTION);
    }

    public String getTarget() {
        return targetName;
    }

    public void setTarget(String targetName) {
        this.targetName = targetName;
    }

    public void setData(String data) {
        _data = data;
    }

    public String getData() {
        return _data;
    }

    public String toString() {
        if(_data != null && targetName != null)
            return "<?" + targetName + " " + _data + "?>";
        if(targetName != null)
            return "<?" + targetName + "?>";
        if(_data != null)
            return "<?" + _data + "?>";
        else
            return "<??>";
    }

}
