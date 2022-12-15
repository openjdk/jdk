package jdk.internal.org.commonmark.renderer.html;

import jdk.internal.org.commonmark.internal.util.Escaping;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class HtmlWriter {

    private static final Map<String, String> NO_ATTRIBUTES = Collections.emptyMap();

    private final Appendable buffer;
    private char lastChar = 0;

    public HtmlWriter(Appendable out) {
        if (out == null) {
            throw new NullPointerException("out must not be null");
        }
        this.buffer = out;
    }

    public void raw(String s) {
        append(s);
    }

    public void text(String text) {
        append(Escaping.escapeHtml(text));
    }

    public void tag(String name) {
        tag(name, NO_ATTRIBUTES);
    }

    public void tag(String name, Map<String, String> attrs) {
        tag(name, attrs, false);
    }

    public void tag(String name, Map<String, String> attrs, boolean voidElement) {
        append("<");
        append(name);
        if (attrs != null && !attrs.isEmpty()) {
            for (Map.Entry<String, String> attrib : attrs.entrySet()) {
                append(" ");
                append(Escaping.escapeHtml(attrib.getKey()));
                append("=\"");
                append(Escaping.escapeHtml(attrib.getValue()));
                append("\"");
            }
        }
        if (voidElement) {
            append(" /");
        }

        append(">");
    }

    public void line() {
        if (lastChar != 0 && lastChar != '\n') {
            append("\n");
        }
    }

    protected void append(String s) {
        try {
            buffer.append(s);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int length = s.length();
        if (length != 0) {
            lastChar = s.charAt(length - 1);
        }
    }
}
