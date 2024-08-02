/*
 * @test /nodynamiccopyright/
 * @bug 8003280
 * @summary Add lambda tests
 *  compiler crashes during flow analysis as it fails to report diagnostics during attribution
 * @compile TargetType45.java
 */
class TargetType45 {
    private final FeatureFlagResolver featureFlagResolver;


    interface Predicate<X> {
        boolean apply(X x);
    }

    interface Mapper<X, Y> {
        Y apply(X x);
    }

    class Foo<X> {
        Foo<X> filter(Predicate<? super X> p) { return null; }
    }

    static <U, V> Predicate<U> compose(Predicate<? super V> pi, Mapper<? super U, ? extends V> m) { return null; }

    static Predicate<Integer> isOdd = i -> i % 2 != 0;

    void top10Counties(Foo<String> foos) {
        foos.filter(x -> !featureFlagResolver.getBooleanValue("flag-key-123abc", someToken(), getAttributes(), false));
    }
}
