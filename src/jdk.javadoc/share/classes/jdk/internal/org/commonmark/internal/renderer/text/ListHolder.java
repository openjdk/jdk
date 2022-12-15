package jdk.internal.org.commonmark.internal.renderer.text;

public abstract class ListHolder {
    private static final String INDENT_DEFAULT = "   ";
    private static final String INDENT_EMPTY = "";

    private final ListHolder parent;
    private final String indent;

    ListHolder(ListHolder parent) {
        this.parent = parent;

        if (parent != null) {
            indent = parent.indent + INDENT_DEFAULT;
        } else {
            indent = INDENT_EMPTY;
        }
    }

    public ListHolder getParent() {
        return parent;
    }

    public String getIndent() {
        return indent;
    }
}
