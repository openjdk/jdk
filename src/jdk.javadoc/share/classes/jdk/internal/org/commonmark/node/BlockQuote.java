package jdk.internal.org.commonmark.node;

public class BlockQuote extends Block {

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}
