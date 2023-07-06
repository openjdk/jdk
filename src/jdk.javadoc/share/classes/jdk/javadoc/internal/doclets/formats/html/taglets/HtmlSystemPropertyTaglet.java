package jdk.javadoc.internal.doclets.formats.html.taglets;

import javax.lang.model.element.Element;

import com.sun.source.doctree.SystemPropertyTree;

import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.taglets.SystemPropertyTaglet;
import jdk.javadoc.internal.doclets.toolkit.taglets.TagletWriter;

public class HtmlSystemPropertyTaglet extends SystemPropertyTaglet {
    HtmlSystemPropertyTaglet(HtmlConfiguration config) {
        super(config);
    }


    @Override
    protected Content systemPropertyTagOutput(Element element, SystemPropertyTree tag, TagletWriter writer) {
        TagletWriterImpl w = (TagletWriterImpl) writer;
        String tagText = tag.getPropertyName().toString();
        return HtmlTree.CODE(w.createAnchorAndSearchIndex(element, tagText,
                resources.getText("doclet.System_Property"), tag));
    }
}
