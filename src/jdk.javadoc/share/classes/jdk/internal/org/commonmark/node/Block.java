package jdk.internal.org.commonmark.node;

/**
 * Block nodes such as paragraphs, list blocks, code blocks etc.
 */
public abstract class Block extends Node {

    public Block getParent() {
        return (Block) super.getParent();
    }

    @Override
    protected void setParent(Node parent) {
        if (!(parent instanceof Block)) {
            throw new IllegalArgumentException("Parent of block must also be block (can not be inline)");
        }
        super.setParent(parent);
    }
}
