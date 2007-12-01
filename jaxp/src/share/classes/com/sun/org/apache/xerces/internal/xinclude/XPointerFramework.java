/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 2001-2003 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Xerces" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation and was
 * originally based on software copyright (c) 1999, International
 * Business Machines, Inc., http://www.apache.org.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */
package com.sun.org.apache.xerces.internal.xinclude;

import java.util.Stack;
import com.sun.org.apache.xerces.internal.xinclude.XPointerSchema;

public class XPointerFramework{

        /*
                Todo's by next integration.
                While constructing schema names and uris use a dynamic datastructure.
         */

    XPointerSchema [] fXPointerSchema;
    String [] fSchemaPointerName;
    String [] fSchemaPointerURI;
    String fSchemaPointer;
    String fCurrentSchemaPointer;
    Stack fSchemaNotAvailable;
    int fCountSchemaName = 0;
    int schemaLength = 0;
    XPointerSchema fDefaultXPointerSchema;

    public XPointerFramework(){
        this(null);
    }

    public XPointerFramework(XPointerSchema [] xpointerschema){
        fXPointerSchema = xpointerschema;
        fSchemaNotAvailable = new Stack();
    }

    public void reset(){
        fXPointerSchema = null;
        fXPointerSchema = null;
        fCountSchemaName = 0;
        schemaLength = 0;
        fSchemaPointerName = null;
        fSchemaPointerURI = null;
        fDefaultXPointerSchema = null;
        fCurrentSchemaPointer = null;
    }

    public void setXPointerSchema(XPointerSchema [] xpointerschema){
        fXPointerSchema = xpointerschema;
    }

    public void setSchemaPointer(String schemaPointer){
        fSchemaPointer = schemaPointer;
    }

    public XPointerSchema getNextXPointerSchema(){
        int  i=fCountSchemaName;
        if(fSchemaPointerName == null){
            getSchemaNames();
        }
        if(fDefaultXPointerSchema == null){
            getDefaultSchema();
        }
        if(fDefaultXPointerSchema.getXpointerSchemaName().equalsIgnoreCase(fSchemaPointerName[i])){
            fDefaultXPointerSchema.reset();
            fDefaultXPointerSchema.setXPointerSchemaPointer(fSchemaPointerURI[i]);
            fCountSchemaName = ++i;
            return  getDefaultSchema();
        }
        if(fXPointerSchema == null){
            fCountSchemaName = ++i;
            return null;
        }

        int fschemalength = fXPointerSchema.length;

        for(;fSchemaPointerName[i] != null; i++){
            for(int j=0; j<fschemalength; j++ ){
                if(fSchemaPointerName[i].equalsIgnoreCase(fXPointerSchema[j].getXpointerSchemaName())){
                    fXPointerSchema[j].setXPointerSchemaPointer(fSchemaPointerURI[i]);
                    fCountSchemaName = ++i;
                    return fXPointerSchema[j];
                }
            }

            if(fSchemaNotAvailable == null)
            fSchemaNotAvailable = new Stack();

            fSchemaNotAvailable.push(fSchemaPointerName[i]);
        }
        return null;
    }

    public XPointerSchema getDefaultSchema(){
        if(fDefaultXPointerSchema == null)
            fDefaultXPointerSchema = new XPointerElementHandler();
        return fDefaultXPointerSchema;
    }

    public void getSchemaNames(){
        int count =0;
        int index =0, lastindex =0;
        int schemapointerindex  =0, schemapointerURIindex=0;
        char c;
        int length = fSchemaPointer.length();
        fSchemaPointerName = new String [5];
        fSchemaPointerURI = new String [5];

        index = fSchemaPointer.indexOf('(');
        if( index <= 0)
            return;

        fSchemaPointerName[schemapointerindex++] = fSchemaPointer.substring(0, index++).trim();
        lastindex = index;
        String tempURI = null;
        count++;

        while(index < length){
            c = fSchemaPointer.charAt(index);
            if(c == '(')
                count++;
            if(c == ')')
                count--;
            if(count==0 ){
                tempURI = fSchemaPointer.substring(lastindex, index).trim();
                fSchemaPointerURI[schemapointerURIindex++] = getEscapedURI(tempURI);
                lastindex = index;
                if((index = fSchemaPointer.indexOf('(', lastindex)) != -1){
                    fSchemaPointerName[schemapointerindex++] = fSchemaPointer.substring(lastindex+1, index).trim();
                    count++;
                    lastindex = index+1;
                }
                else{
                    index = lastindex;
                }
            }
            index++;
        }
        schemaLength = schemapointerURIindex -1;
    }

    public String   getEscapedURI(String URI){
        return URI;
    }

    public int getSchemaCount(){
        return schemaLength;
    }

    public int getCurrentPointer(){
        return fCountSchemaName;
    }

}//XPointerFramwork
