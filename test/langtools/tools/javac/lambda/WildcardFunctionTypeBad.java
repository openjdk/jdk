/*
 * @test /nodynamiccopyright/
 * @bug 8375572 8205052
 * @summary Reject dependent wildcard bounds and ill-formed derived functional targets
 * @compile/fail/ref=WildcardFunctionTypeBad.out -XDrawDiagnostics WildcardFunctionTypeBad.java
 */

class WildcardFunctionTypeBad {
    interface Recursive<Element extends Comparable<Element>> {
        void accept(Element value);
    }

    interface Dependent<Base, Element extends Base> {
        void accept(Element value);
    }

    interface CrossBound<Element extends Comparable<Other>, Other> {
        void accept(Element value);
    }

    static void accept(Object value) {}

    Recursive<?> recursiveUnboundLambda = value -> {};
    Recursive<? extends String> recursiveUpperLambda = value -> {};
    Recursive<? super String> recursiveLowerLambda = value -> {};
    Recursive<?> recursiveUnboundReference = WildcardFunctionTypeBad::accept;
    Recursive<? extends String> recursiveUpperReference = WildcardFunctionTypeBad::accept;
    Recursive<? super String> recursiveLowerReference = WildcardFunctionTypeBad::accept;
    Dependent<String, ?> dependentUnboundLambda = value -> {};
    Dependent<String, ? extends String> dependentUpperLambda = value -> {};
    Dependent<String, ? super String> dependentLowerLambda = value -> {};
    Dependent<String, ?> dependentUnboundReference = WildcardFunctionTypeBad::accept;
    Dependent<String, ? extends String> dependentUpperReference = WildcardFunctionTypeBad::accept;
    Dependent<String, ? super String> dependentLowerReference = WildcardFunctionTypeBad::accept;
    CrossBound<String, ?> derivedLambda = value -> {};
    CrossBound<String, ?> derivedReference = WildcardFunctionTypeBad::accept;
    Object derivedIntersection =
            (CrossBound<String, ?> & java.io.Serializable) WildcardFunctionTypeBad::accept;
}
