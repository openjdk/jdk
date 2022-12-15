package jdk.internal.org.commonmark.node;

public class Code extends Node {

    private String literal;

    public Code() {
    }

    public Code(String literal) {
        this.literal = literal;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    public String getLiteral() {
        return literal;
    }

    public void setLiteral(String literal) {
        this.literal = literal;
    }
}
