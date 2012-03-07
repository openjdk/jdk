/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.codemodel.internal;

import java.util.HashMap;
import java.util.Map;

/**
 * JavaDoc comment.
 *
 * <p>
 * A javadoc comment consists of multiple parts. There's the main part (that comes the first in
 * in the comment section), then the parameter parts (@param), the return part (@return),
 * and the throws parts (@throws).
 *
 * TODO: it would be nice if we have JComment class and we can derive this class from there.
 */
public class JDocComment extends JCommentPart implements JGenerable {

        private static final long serialVersionUID = 1L;

        /** list of @param tags */
    private final Map<String,JCommentPart> atParams = new HashMap<String,JCommentPart>();

    /** list of xdoclets */
    private final Map<String,Map<String,String>> atXdoclets = new HashMap<String,Map<String,String>>();

    /** list of @throws tags */
    private final Map<JClass,JCommentPart> atThrows = new HashMap<JClass,JCommentPart>();

    /**
     * The @return tag part.
     */
    private JCommentPart atReturn = null;

    /** The @deprecated tag */
    private JCommentPart atDeprecated = null;

    private final JCodeModel owner;


    public JDocComment(JCodeModel owner) {
        this.owner = owner;
    }

    public JDocComment append(Object o) {
        add(o);
        return this;
    }

    /**
     * Append a text to a @param tag to the javadoc
     */
    public JCommentPart addParam( String param ) {
        JCommentPart p = atParams.get(param);
        if(p==null)
            atParams.put(param,p=new JCommentPart());
        return p;
    }

    /**
     * Append a text to an @param tag.
     */
    public JCommentPart addParam( JVar param ) {
        return addParam( param.name() );
    }


    /**
     * add an @throws tag to the javadoc
     */
    public JCommentPart addThrows( Class<? extends Throwable> exception ) {
        return addThrows( owner.ref(exception) );
    }

    /**
     * add an @throws tag to the javadoc
     */
    public JCommentPart addThrows( JClass exception ) {
        JCommentPart p = atThrows.get(exception);
        if(p==null)
            atThrows.put(exception,p=new JCommentPart());
        return p;
    }

    /**
     * Appends a text to @return tag.
     */
    public JCommentPart addReturn() {
        if(atReturn==null)
            atReturn = new JCommentPart();
        return atReturn;
    }

    /**
     * add an @deprecated tag to the javadoc, with the associated message.
     */
    public JCommentPart addDeprecated() {
        if(atDeprecated==null)
            atDeprecated = new JCommentPart();
        return atDeprecated;
    }

    /**
     * add an xdoclet.
     */
    public Map<String,String> addXdoclet(String name) {
        Map<String,String> p = atXdoclets.get(name);
        if(p==null)
            atXdoclets.put(name,p=new HashMap<String,String>());
        return p;
    }

    /**
     * add an xdoclet.
     */
    public Map<String,String> addXdoclet(String name, Map<String,String> attributes) {
        Map<String,String> p = atXdoclets.get(name);
        if(p==null)
            atXdoclets.put(name,p=new HashMap<String,String>());
        p.putAll(attributes);
        return p;
    }

    /**
     * add an xdoclet.
     */
    public Map<String,String> addXdoclet(String name, String attribute, String value) {
        Map<String,String> p = atXdoclets.get(name);
        if(p==null)
            atXdoclets.put(name,p=new HashMap<String,String>());
        p.put(attribute, value);
        return p;
    }

    public void generate(JFormatter f) {
        // I realized that we can't use StringTokenizer because
        // this will recognize multiple \n as one token.

        f.p("/**").nl();

        format(f," * ");

        f.p(" * ").nl();
        for (Map.Entry<String,JCommentPart> e : atParams.entrySet()) {
            f.p(" * @param ").p(e.getKey()).nl();
            e.getValue().format(f,INDENT);
        }
        if( atReturn != null ) {
            f.p(" * @return").nl();
            atReturn.format(f,INDENT);
        }
        for (Map.Entry<JClass,JCommentPart> e : atThrows.entrySet()) {
            f.p(" * @throws ").t(e.getKey()).nl();
            e.getValue().format(f,INDENT);
        }
        if( atDeprecated != null ) {
            f.p(" * @deprecated").nl();
            atDeprecated.format(f,INDENT);
        }
        for (Map.Entry<String,Map<String,String>> e : atXdoclets.entrySet()) {
            f.p(" * @").p(e.getKey());
            if (e.getValue() != null) {
                for (Map.Entry<String,String> a : e.getValue().entrySet()) {
                    f.p(" ").p(a.getKey()).p("= \"").p(a.getValue()).p("\"");
                }
            }
            f.nl();
        }
        f.p(" */").nl();
    }

    private static final String INDENT = " *     ";
}
