/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.ws.processor.model;

import com.sun.tools.internal.ws.processor.model.java.JavaParameter;
import com.sun.tools.internal.ws.wsdl.framework.Entity;
import com.sun.tools.internal.ws.wsdl.document.MessagePart;

import javax.jws.WebParam.Mode;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author WS Development Team
 */
public class Parameter extends ModelObject {
    private final String entityName;

    public Parameter(String name, Entity entity) {
        super(entity);
        this.name = name;
        if(entity instanceof com.sun.tools.internal.ws.wsdl.document.Message){
            this.entityName = ((com.sun.tools.internal.ws.wsdl.document.Message)entity).getName();
        }else if(entity instanceof MessagePart){
            this.entityName = ((MessagePart)entity).getName();
        }else{
            this.entityName = name;
        }

    }


    public String getEntityName() {
        return entityName;
    }

    public String getName() {
        return name;
    }

    public void setName(String s) {
        name = s;
    }

    public JavaParameter getJavaParameter() {
        return javaParameter;
    }

    public void setJavaParameter(JavaParameter p) {
        javaParameter = p;
    }

    public AbstractType getType() {
        return type;
    }

    public void setType(AbstractType t) {
        type = t;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String t) {
        typeName = t;
    }

    public Block getBlock() {
        return block;
    }

    public void setBlock(Block d) {
        block = d;
    }

    public Parameter getLinkedParameter() {
        return link;
    }

    public void setLinkedParameter(Parameter p) {
        link = p;
    }

    public boolean isEmbedded() {
        return embedded;
    }

    public void setEmbedded(boolean b) {
        embedded = b;
    }

    public void accept(ModelVisitor visitor) throws Exception {
        visitor.visit(this);
    }

    private String name;
    private JavaParameter javaParameter;
    private AbstractType type;
    private Block block;
    private Parameter link;
    private boolean embedded;
    private String typeName;
    private String customName;
    private Mode mode;

    public int getParameterIndex() {
        return parameterOrderPosition;
    }

    public void setParameterIndex(int parameterOrderPosition) {
        this.parameterOrderPosition = parameterOrderPosition;
    }

    public boolean isReturn(){
        return (parameterOrderPosition == -1);
    }

    // 0 is the first parameter, -1 is the return type
    private int parameterOrderPosition;
    /**
     * @return Returns the customName.
     */
    public String getCustomName() {
        return customName;
    }
    /**
     * @param customName The customName to set.
     */
    public void setCustomName(String customName) {
        this.customName = customName;
    }

    private List<String> annotations = new ArrayList<String>();

    /**
     * @return Returns the annotations.
     */
    public List<String> getAnnotations() {
        return annotations;
    }


    /**
     * @param annotations The annotations to set.
     */
    public void setAnnotations(List<String> annotations) {
        this.annotations = annotations;
    }

    public void setMode(Mode mode){
        this.mode = mode;
    }

    public boolean isIN(){
        return (mode == Mode.IN);
    }

    public boolean isOUT(){
        return (mode == Mode.OUT);
    }

    public boolean isINOUT(){
        return (mode == Mode.INOUT);
    }



}
