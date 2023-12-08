/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import com.oracle.jmx.remote.rest.json.parser.JSONParser;
import com.oracle.jmx.remote.rest.json.parser.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * @test @modules java.management.rest
 * @run main JSONTest
 */
public class JSONTest {

    public static void main(String[] args) throws ParseException {
        JSONGraphGenerator graphGen = new JSONGraphGenerator(50);
        JSONElement jElem = graphGen.generateJsonGraph(20000);
        System.out.println("Graph Generated");
        String str = jElem.toJsonString();
        System.out.println(str);

        JSONParser parser = new JSONParser(str);

        com.oracle.jmx.remote.rest.json.JSONElement parse = parser.parse();
        String resultJson = parse.toJsonString();
        System.out.println(resultJson);
    }
}

interface JSONElement {

    String toJsonString();
}

interface Visitable {

    public void accept(NodeVisitor visitor);
}

interface NodeVisitor {

    public void visit(Node node);

    //visit other concrete items
    public void visit(Digit19 digit12);

    public void visit(Digits dvd);
}

class Node implements Visitable {

    final private String label;

    public Node(String label) {
        children = new LinkedList<>();
        this.label = label;
    }

    public Node() {
        this("");
    }

    public void add(Node node) {
        if (!children.contains(node)) {
            children.add(node);
        }
    }

    public String getLabel() {
        return label;
    }
    List<Node> children;

    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }
}

class Digit19 extends Node {

    Random rnd = new Random();

    public Digit19() {
        super();
    }

    @Override
    public String getLabel() {
        return "" + (rnd.nextInt(9) + 1);
    }
}

class Digits extends Node {

    Random rnd = new Random();

    public Digits() {
        super();
    }

    @Override
    public String getLabel() {
        return "" + (rnd.nextInt(10));
    }
}

class JSONNumberGenerator {

    private final static Node root;
    Number num;

    static {
        root = new Node("R");
        Node minus1 = new Node("-");
        Node zero = new Node("0");
        Node digit19 = new Digit19();
        Node digits1 = new Digits();
        Node dot = new Node(".");
        Node digits2 = new Digits();
        Node e = new Node("e");
        Node E = new Node("E");
        Node plus = new Node("+");
        Node minus2 = new Node("-");
        Node digits3 = new Digits();
        Node terminal = new Node("T");

        root.add(zero);
        root.add(minus1);
        root.add(digit19);

        minus1.add(zero);
        minus1.add(digit19);

        zero.add(dot);
//        zero.add(e);
//        zero.add(E);
        zero.add(terminal);

        digit19.add(dot);
        digit19.add(digits1);
//        digit19.add(e);
//        digit19.add(E);
        digit19.add(terminal);

        digits1.add(dot);
        digits1.add(digits1);
//        digits1.add(e);
//        digits1.add(E);
        digits1.add(terminal);

        dot.add(digits2);

        digits2.add(digits2);
        digits2.add(e);
        digits2.add(E);
        digits2.add(terminal);

        e.add(plus);
        e.add(minus2);
        e.add(digits3);

        E.add(plus);
        E.add(minus2);
        E.add(digits3);

        plus.add(digits3);
        minus2.add(digits3);

        digits3.add(digits3);
        digits3.add(terminal);
    }

    private static class NumberNodeVisitor implements NodeVisitor {

        private final StringBuilder sbuf = new StringBuilder();
        Random rnd = new Random();

        public NumberNodeVisitor() {
        }

        @Override
        public void visit(Node node) {
            if (!node.getLabel().equals("R")) {
                sbuf.append(node.getLabel());
            }
            if (node.children.size() > 0) {
                Node child = node.children.get(rnd.nextInt(node.children.size()));
                if (!child.getLabel().equals("T")) {
                    visit(child);
                }
            } else {
                System.out.println("Found node " + node.getLabel() + " with children : " + node.children.size());
            }
        }

        @Override
        public void visit(Digit19 digit12) {
            sbuf.append(digit12.getLabel());
            Node child = digit12.children.get(rnd.nextInt(digit12.children.size()));
            if (!child.getLabel().equals("T")) {
                visit(child);
            }
        }

        @Override
        public void visit(Digits digits) {
            sbuf.append(digits.getLabel());
            Node child = digits.children.get(rnd.nextInt(digits.children.size()));
            if (!child.getLabel().equals("T")) {
                visit(child);
            }
        }

        public String getNumber() {
            return sbuf.toString();
        }

    }

    public String generate() {
        NumberNodeVisitor visitor = new NumberNodeVisitor();
        visitor.visit(root);
        // System.out.println(visitor.getNumber());
//        Double.parseDouble(visitor.getNumber());
        return visitor.getNumber();
    }
}

class TestJsonObject extends LinkedHashMap<String, JSONElement> implements JSONElement {

    @Override
    public String toJsonString() {
        if (isEmpty()) {
            return null;
        }

        StringBuilder sbuild = new StringBuilder();
        sbuild.append("{");
        keySet().forEach((elem) -> {
            sbuild.append(elem).append(": ").
                    append((get(elem) != null) ? get(elem).toJsonString() : "null").append(",");
        });

        sbuild.deleteCharAt(sbuild.lastIndexOf(","));
        sbuild.append("}");
        return sbuild.toString();
    }
}

class TestJsonArray extends ArrayList<JSONElement> implements JSONElement {

    @Override
    public String toJsonString() {
        if (isEmpty()) {
            return null;
        }
        StringBuilder sbuild = new StringBuilder();
        sbuild.append("[");
        Iterator<JSONElement> itr = iterator();
        while (itr.hasNext()) {
            JSONElement val = itr.next();
            if (val != null) {
                sbuild.append(val.toJsonString()).append(", ");
            } else {
                sbuild.append("null").append(", ");
            }
        }

        sbuild.deleteCharAt(sbuild.lastIndexOf(","));
        sbuild.append("]");
        return sbuild.toString();
    }
}

class TestJsonPrimitive implements JSONElement {

    private final String s;

    public TestJsonPrimitive(String s) {
        this.s = s;
    }

    @Override
    public String toJsonString() {
        return s;
    }
}

class JSONStringGenerator {

    private static final int minStringLength = 0;
    private static final int maxStringLength = 10;

    private final Random rnd = new Random(System.currentTimeMillis());

    private static final String specials = "\b" + "\f" + "\n" + "\r" + "\t" + "\\" + "\"";    // TODO: Special characters '/', '\', '"', "\\uxxxx"
    private static final String alphanums = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public String generate() {
        char ch;
        StringBuilder sbuf = new StringBuilder();
        int len = minStringLength + rnd.nextInt(maxStringLength - minStringLength + 1);
        sbuf.append("\"");
        for (int i = 0; i < len; i++) {
            if (rnd.nextInt(10) == 1) { // 1/10 chances of a control character
                ch = specials.charAt(rnd.nextInt(specials.length()));
            } else {
//                ch = alphanums.charAt(rnd.nextInt(alphanums.length()));
                ch = (char) rnd.nextInt(Character.MAX_VALUE + 1);
            }
            switch (ch) {
                case '\"':
                case '\\':
                    sbuf.append('\\');
            }
            sbuf.append(ch);
        }
        sbuf.append("\"");
        return sbuf.toString();
    }
}

class JSONGraphGenerator {

    JSONStringGenerator stringGen;
    JSONNumberGenerator numGen;
    private final int maxChildPerNode;
    static Random rnd = new Random(System.currentTimeMillis());

    public JSONGraphGenerator(int maxChildPerNode) {
        this.maxChildPerNode = maxChildPerNode;
        stringGen = new JSONStringGenerator();
        numGen = new JSONNumberGenerator();
    }

    private TestJsonPrimitive generatePrimitiveData() {
        int primitiveTypre = rnd.nextInt(10) + 1;
        switch (primitiveTypre) {
            case 1:
            case 2:
            case 3:
            case 4:
                return new TestJsonPrimitive(stringGen.generate());
            case 5:
            case 6:
            case 7:
            case 8:
                return new TestJsonPrimitive(numGen.generate());
            case 9:
                return new TestJsonPrimitive(Boolean.toString(rnd.nextBoolean()));
            case 10:
                return null;
        }
        return null;
    }

    public TestJsonObject generateJsonObject(int size) {
        TestJsonObject jobj = new TestJsonObject();
        if (size <= maxChildPerNode) {
            for (int i = 0; i < size; i++) {
                jobj.put(stringGen.generate(), generatePrimitiveData());
            }
        } else {
            int newSize = size;
            do {
                int childSize = rnd.nextInt(newSize);
                jobj.put(stringGen.generate(), generateJsonGraph(childSize));
                newSize = newSize - childSize;
            } while (newSize > maxChildPerNode);
            jobj.put(stringGen.generate(), generateJsonGraph(newSize));
        }
        return jobj;
    }

    public TestJsonArray generateJsonArray(int size) {
        TestJsonArray array = new TestJsonArray();
        if (size <= maxChildPerNode) {
            for (int i = 0; i < size; i++) {
                array.add(generatePrimitiveData());
            }
        } else if (size >= maxChildPerNode) {
            int newSize = size;
            do {
                int childSize = rnd.nextInt(newSize);
                array.add(generateJsonGraph(childSize));
                newSize = newSize - childSize;
            } while (newSize > maxChildPerNode);
            array.add(generateJsonGraph(newSize));
        }
        return array;
    }

    public JSONElement generateJsonGraph(int size) {
        if (rnd.nextBoolean()) {
            return generateJsonArray(size);
        } else {
            return generateJsonObject(size);
        }
    }
}
