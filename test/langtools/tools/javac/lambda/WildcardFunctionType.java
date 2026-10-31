/*
 * @test /nodynamiccopyright/
 * @bug 8375572
 * @summary stuck method references should consider free vars in the target type
 * @compile WildcardFunctionType.java
 */

class WildcardFunctionType {
    interface Bounded<Element extends Number> {
        void accept(Element value);
    }

    interface Recursive<Element extends Comparable<Element>> {
        void accept(Element value);
    }

    interface Dependent<Base, Element extends Base> {
        void accept(Element value);
    }

    interface CrossBound<Element extends Comparable<Other>, Other> {
        void accept(Element value);
    }

    interface StringConsumer {
        void accept(String value);
    }

    static void accept(Object value) {}

    static void choose(CrossBound<String, ?> action) {}
    static void choose(StringConsumer action) {}

    Bounded<?> unbound = value -> {};
    Bounded<? extends Integer> upperBound = WildcardFunctionType::accept;
    Bounded<? super Integer> lowerBound = WildcardFunctionType::accept;
    Recursive<String> concreteRecursive = WildcardFunctionType::accept;
    Dependent<Object, String> concreteDependent = value -> {};
    Dependent<? super String, String> independentWildcard = value -> {};
    Recursive<?> explicitUnbound = (String value) -> {};
    Recursive<? extends String> explicitUpperBound = (String value) -> {};
    Recursive<? super String> explicitLowerBound = (String value) -> {};
    Dependent<String, ?> explicitDependent = (String value) -> {};

    void overload() {
        choose(WildcardFunctionType::accept);
    }
}
