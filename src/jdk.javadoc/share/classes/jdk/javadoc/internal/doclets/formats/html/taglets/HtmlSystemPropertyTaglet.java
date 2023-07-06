package jdk.javadoc.internal.doclets.formats.html.taglets;

import javax.lang.model.element.Element;

import com.sun.source.doctree.SystemPropertyTree;

import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.taglets.SystemPropertyTaglet;

public class HtmlSystemPropertyTaglet extends SystemPropertyTaglet {
    HtmlSystemPropertyTaglet(HtmlConfiguration config) {
        super(config);
    }


    @Override
    protected Content systemPropertyTagOutput(Element element, SystemPropertyTree tag) {
        TagletWriterImpl tw = (TagletWriterImpl) tagletWriter;
        String tagText = tag.getPropertyName().toString();
        return HtmlTree.CODE(tw.createAnchorAndSearchIndex(element, tagText,
                resources.getText("doclet.System_Property"), tag));
    }
}
