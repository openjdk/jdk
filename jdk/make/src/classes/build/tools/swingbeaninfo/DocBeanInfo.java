/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.swingbeaninfo;

import java.util.HashMap;

/**
 * Class that holds information for populating a FeatureDescriptor. For the class,
 * This information represents the BeanDescriptor, for a property, it represents
 * a PropertyDescriptor.
 */
public class DocBeanInfo {

    // Values of the BeanFlags
    public static final int BOUND = 1;
    public static final int EXPERT = 2;
    public static final int CONSTRAINED = 4;
    public static final int HIDDEN = 8;
    public static final int PREFERRED = 16 ;

    public String name;
    public int beanflags;
    public String desc;
    public String displayname;
    public String propertyeditorclass;
    public String customizerclass;

    public HashMap attribs;
    public HashMap enums;

    public DocBeanInfo(){}

    public DocBeanInfo(String p, int flags, String d,
                         String displayname, String pec, String cc,
                         HashMap attribs, HashMap enums) {
        this.name = p;
        this.beanflags = flags;
        this.desc = d;
        this.displayname = displayname;
        this.propertyeditorclass = pec;
        this.customizerclass = cc;

        this.attribs = attribs;
        this.enums = enums;
    }

    public String toString() {
        StringBuffer buffer = new StringBuffer("*****");
        buffer.append("\nProperty: " + name);
        buffer.append("\tDescription: " + desc);
        buffer.append("\nDisplayname: " + displayname);
        buffer.append("\nPropertyEditorClass: " + propertyeditorclass);
        buffer.append("\nCustomizerClass: " + customizerclass);

        if ((beanflags & BOUND) != 0)
            buffer.append("\nBound: true");

        if ((beanflags & EXPERT) != 0)
            buffer.append("\nExpert: true");

        if ((beanflags & CONSTRAINED) != 0)
            buffer.append("\nConstrained: true");

        if ((beanflags & HIDDEN) !=0)
            buffer.append("\nHidden:  true");

        if ((beanflags & PREFERRED) !=0)

        if (attribs != null)
            buffer.append(attribs.toString());

        if (enums != null)
            buffer.append(enums.toString());

        return buffer.toString();
    }

}
