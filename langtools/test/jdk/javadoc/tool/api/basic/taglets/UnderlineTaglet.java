/*
 * Copyright (c) 2002, 2015, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the
 *  distribution.
 *
 * Neither the name of Oracle nor the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY
 * DAMAGES OR LIABILITIES  SUFFERED BY LICENSEE AS A RESULT OF OR
 * RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR
 * ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT,
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF
 * THE USE OF OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that Software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
 */

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.doctree.UnknownInlineTagTree;
import com.sun.source.util.SimpleDocTreeVisitor;
import jdk.javadoc.doclet.taglet.Taglet;

/**
 * A sample Inline Taglet representing {@underline ...}. This tag can
 * be used in any kind of {@link javax.lang.model.Element}.
 * The text is underlined.  For example,
 * "@underline UNDERLINE ME" would be shown as: <u>UNDERLINE ME</u>.
 *
 * @author Jamie Ho
 * @since 1.4
 */

public class UnderlineTaglet implements Taglet {

    private static final String NAME = "underline";

    /**
     * Return the name of this custom tag.
     */
    public String getName() {
        return NAME;
    }

    private final EnumSet<Location> allowedSet = EnumSet.allOf(Location.class);

    @Override
    public Set<Taglet.Location> getAllowedLocations() {
        return allowedSet;
    }

    /**
     * Will return true since this is an inline tag.
     * @return true since this is an inline tag.
     */

    public boolean isInlineTag() {
        return true;
    }

    /**
     * Given the <code>Tag</code> representation of this custom
     * tag, return its string representation.
     * @param tag he <code>Tag</code> representation of this custom tag.
     */
    public String toString(DocTree tag) {
        return "<u>" + getText(tag) + "</u>";
    }

    /**
     * This method should not be called since arrays of inline tags do not
     * exist.  Method {@link #tostring(Tag)} should be used to convert this
     * inline tag to a string.
     * @param tags the array of <code>Tag</code>s representing of this custom tag.
     */
    public String toString(List<? extends DocTree> tags) {
        return null;
    }

    static String getText(DocTree dt) {
        return new SimpleDocTreeVisitor<String, Void>() {
            @Override
            public String visitUnknownBlockTag(UnknownBlockTagTree node, Void p) {
                for (DocTree dt : node.getContent()) {
                    return dt.accept(this, null);
                }
                return "";
            }

            @Override
            public String visitUnknownInlineTag(UnknownInlineTagTree node, Void p) {
                for (DocTree dt : node.getContent()) {
                    return dt.accept(this, null);
                }
                return "";
            }

            @Override
            public String visitText(TextTree node, Void p) {
                return node.getBody();
            }

            @Override
            protected String defaultAction(DocTree node, Void p) {
                return "";
            }

        }.visit(dt, null);
    }
}

