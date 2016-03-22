/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package taglets;

import java.lang.reflect.Layer;
import java.lang.reflect.Module;
import java.util.*;

import com.sun.javadoc.*;

import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.taglets.*;
import com.sun.tools.doclets.internal.toolkit.util.*;


public class CustomTag extends BaseTaglet {

    public CustomTag() {
        name = "customTag";
    }

    public static void register(Map tagletMap) {
       CustomTag tag = new CustomTag();
       Taglet t = (Taglet) tagletMap.get(tag.getName());
       if (t != null) {
           tagletMap.remove(tag.getName());
       }
       tagletMap.put(tag.getName(), tag);
    }

    /**
     * {@inheritDoc}
     */
    public Content getTagletOutput(Tag tag, TagletWriter writer) {
        ArrayList inlineTags = new ArrayList();
        inlineTags.add(new TextTag(tag.holder(), "<dt><span class=\"simpleTagLabel\">Custom Tag:</span></dt><dd>"));
        inlineTags.addAll(Arrays.asList(tag.inlineTags()));
        inlineTags.add(new TextTag(tag.holder(), "</dd>"));
        return writer.commentTagsToOutput(tag,
                (Tag[]) inlineTags.toArray(new Tag[] {}));
    }
}
