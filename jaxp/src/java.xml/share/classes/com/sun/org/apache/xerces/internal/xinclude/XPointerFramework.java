/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
