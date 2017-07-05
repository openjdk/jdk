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
package com.sun.tools.internal.xjc.util;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;

/**
 * @author Kohsuke Kawaguchi
 */
public class MimeTypeRange {
    public final String majorType;
    public final String subType;

    public final Map<String,String> parameters = new HashMap<String, String>();

    /**
     * Each media-range MAY be followed by one or more accept-params,
     * beginning with the "q" parameter for indicating a relative quality
     * factor. The first "q" parameter (if any) separates the media-range
     * parameter(s) from the accept-params. Quality factors allow the user
     * or user agent to indicate the relative degree of preference for that
     * media-range, using the qvalue scale from 0 to 1 (section 3.9). The
     * default value is q=1.
     */
    public final float q;

    // accept-extension is not implemented

    public static List<MimeTypeRange> parseRanges(String s) throws ParseException {
        StringCutter cutter = new StringCutter(s,true);
        List<MimeTypeRange> r = new ArrayList<MimeTypeRange>();
        while(cutter.length()>0) {
            r.add(new MimeTypeRange(cutter));
        }
        return r;
    }

    public MimeTypeRange(String s) throws ParseException {
        this(new StringCutter(s,true));
    }

    /**
     * Used only to produce the static constants within this class.
     */
    private static MimeTypeRange create(String s) {
        try {
            return new MimeTypeRange(s);
        } catch (ParseException e) {
            // we only use this method for known inputs
            throw new Error(e);
        }
    }

    /**
     * @param cutter
     *      A string like "text/html; charset=utf-8;
     */
    private MimeTypeRange(StringCutter cutter) throws ParseException {
        majorType = cutter.until("/");
        cutter.next("/");
        subType = cutter.until("[;,]");

        float q = 1.0f;

        while(cutter.length()>0) {
            String sep = cutter.next("[;,]");
            if(sep.equals(","))
                break;

            String key = cutter.until("=");
            cutter.next("=");
            String value;
            char ch = cutter.peek();
            if(ch=='"') {
                // quoted
                cutter.next("\"");
                value = cutter.until("\"");
                cutter.next("\"");
            } else {
                value = cutter.until("[;,]");
            }

            if(key.equals("q")) {
                q = Float.parseFloat(value);
            } else {
                parameters.put(key,value);
            }
        }

        this.q = q;
    }

    public MimeType toMimeType() throws MimeTypeParseException {
        // due to the additional error check done in the MimeType class,
        // an error at this point is possible
        return new MimeType(toString());
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(majorType+'/'+subType);
        if(q!=1)
            sb.append("; q=").append(q);

        for( Map.Entry<String,String> p : parameters.entrySet() ) {
            // I'm too lazy to quote the value
            sb.append("; ").append(p.getKey()).append('=').append(p.getValue());
        }
        return sb.toString();
    }

    public static final MimeTypeRange ALL = create("*/*");

    /**
     * Creates a range by merging all the given types.
     */
    public static MimeTypeRange merge( Collection<MimeTypeRange> types ) {
        if(types.size()==0)     throw new IllegalArgumentException();
        if(types.size()==1)     return types.iterator().next();

        String majorType=null;
        for (MimeTypeRange mt : types) {
            if(majorType==null)     majorType = mt.majorType;
            if(!majorType.equals(mt.majorType))
                return ALL;
        }

        return create(majorType+"/*");
    }

    public static void main(String[] args) throws ParseException {
        for( MimeTypeRange m : parseRanges(args[0]))
            System.out.println(m.toString());
    }
}
