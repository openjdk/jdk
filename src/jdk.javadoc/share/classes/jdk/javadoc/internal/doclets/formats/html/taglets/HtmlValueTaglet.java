package jdk.javadoc.internal.doclets.formats.html.taglets;

import javax.lang.model.element.VariableElement;

import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.HtmlLinkInfo;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.taglets.ValueTaglet;

public class HtmlValueTaglet extends ValueTaglet {
    HtmlValueTaglet(HtmlConfiguration config) {
        super(config);
    }


    @Override
    public Content valueTagOutput(VariableElement field, String constantVal, boolean includeLink) {
        TagletWriterImpl tw = (TagletWriterImpl) tagletWriter;
        return includeLink
                ? tw.getHtmlWriter().getDocLink(HtmlLinkInfo.Kind.LINK_TYPE_PARAMS_AND_BOUNDS, field, constantVal)
                : Text.of(constantVal);
    }
}
