package jdk.javadoc.internal.doclets.formats.html.taglets;

import java.util.Optional;

import javax.lang.model.type.TypeMirror;

import jdk.javadoc.internal.doclets.formats.html.Contents;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.HtmlLinkInfo;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.taglets.TagletWriter;
import jdk.javadoc.internal.doclets.toolkit.taglets.ThrowsTaglet;

public class HtmlThrowsTaglet extends ThrowsTaglet {
    private final HtmlConfiguration config;
    private final Contents contents;

    HtmlThrowsTaglet(HtmlConfiguration config) {
        super(config);
        this.config = config;
        contents = config.contents;
    }


    @Override
    public Content getThrowsHeader() {
        return HtmlTree.DT(contents.throws_);
    }


    @Override
    public Content throwsTagOutput(TypeMirror throwsType, Optional<Content> content, TagletWriter writer) {
        var w = (TagletWriterImpl) writer;
        var linkInfo = new HtmlLinkInfo(config, HtmlLinkInfo.Kind.PLAIN, throwsType);
        var link = w.getHtmlWriter().getLink(linkInfo);
        var concat = new ContentBuilder(HtmlTree.CODE(link));
        if (content.isPresent()) {
            concat.add(" - ");
            concat.add(content.get());
        }
        return HtmlTree.DD(concat);
    }
}
