/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html;

/**
 * Index item for search.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class SearchIndexItem {

    enum Category {
        MODULES,
        PACKAGES,
        TYPES,
        MEMBERS,
        SEARCH_TAGS
    }

    private Category category;
    private String label = "";
    private String url = "";
    private String containingModule = "";
    private String containingPackage = "";
    private String containingClass = "";
    private String holder = "";
    private String description = "";

    public void setLabel(String l) {
        label = l;
    }

    public String getLabel() {
        return label;
    }

    public void setUrl(String u) {
        url = u;
    }

    public String getUrl() {
        return url;
    }

    public void setContainingModule(String m) {
        containingModule = m;
    }

    public void setContainingPackage(String p) {
        containingPackage = p;
    }

    public void setContainingClass(String c) {
        containingClass = c;
    }

    public void setCategory(Category c) {
        category = c;
    }

    public void setHolder(String h) {
        holder = h;
    }

    public String getHolder() {
        return holder;
    }

    public void setDescription(String d) {
        description = d;
    }

    public String getDescription() {
        return description;
    }

    public String toString() {
        StringBuilder item = new StringBuilder("");
        switch (category) {
        case MODULES:
            item.append("{")
                    .append("\"l\":\"").append(label).append("\"")
                    .append("}");
            break;
        case PACKAGES:
            item.append("{");
            if (!containingModule.isEmpty()) {
                item.append("\"m\":\"").append(containingModule).append("\",");
            }
            item.append("\"l\":\"").append(label).append("\"");
            if (!url.isEmpty()) {
                item.append(",\"url\":\"").append(url).append("\"");
            }
            item.append("}");
            break;
        case TYPES:
            item.append("{");
            if (!containingPackage.isEmpty()) {
                item.append("\"p\":\"").append(containingPackage).append("\",");
            }
            item.append("\"l\":\"").append(label).append("\"");
            if (!url.isEmpty()) {
                item.append(",\"url\":\"").append(url).append("\"");
            }
            item.append("}");
            break;
        case MEMBERS:
            item.append("{")
                    .append("\"p\":\"").append(containingPackage).append("\",")
                    .append("\"c\":\"").append(containingClass).append("\",")
                    .append("\"l\":\"").append(label).append("\"");
            if (!url.isEmpty()) {
                item.append(",\"url\":\"").append(url).append("\"");
            }
            item.append("}");
            break;
        case SEARCH_TAGS:
            item.append("{")
                    .append("\"l\":\"").append(label).append("\",")
                    .append("\"h\":\"").append(holder).append("\",");
            if (!description.isEmpty()) {
                item.append("\"d\":\"").append(description).append("\",");
            }
            item.append("\"u\":\"").append(url).append("\"")
                    .append("}");
            break;
        default:
            throw new IllegalStateException("category not set");
        }
        return item.toString();
    }

    /**
     * Get the part of the label after the last dot, or whole label if no dots.
     *
     * @return the simple name
     */
    public String getSimpleName() {
        return label.substring(label.lastIndexOf('.') + 1);
    }
}
