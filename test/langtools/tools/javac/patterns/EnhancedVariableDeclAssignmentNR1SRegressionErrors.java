/*
 * @test /nodynamiccopyright/
 * @summary Ensure non-NR1S assignments stay rejected in all modes
 * @compile/fail/ref=EnhancedVariableDeclAssignmentNR1SRegressionErrors.out -XDrawDiagnostics EnhancedVariableDeclAssignmentNR1SRegressionErrors.java
 * @compile/fail/ref=EnhancedVariableDeclAssignmentNR1SRegressionErrors.out --enable-preview --source ${jdk.version} -XDrawDiagnostics EnhancedVariableDeclAssignmentNR1SRegressionErrors.java
 */
public class EnhancedVariableDeclAssignmentNR1SRegressionErrors {
    static sealed abstract class SA2<T> permits SB2 {}
    static final class SB2<T> extends SA2<T> {}
    static void rejectMismatchedGenericArguments() {
        SA2<Integer> sa = new SB2<>();
        SB2<String> sb = sa;    // always error
    }
    static void rejectAssignmentWithWildcard(SA2<String> sa) {
        SB2<?> sb = sa;         // always error
    }

    static sealed interface SI permits Mid {}
    static non-sealed interface Mid extends SI {}
    static final class Leaf implements Mid {}
    static void rejectIndirectPermittedSubtypeAssignment() {
        SI si = new Leaf();
        Leaf leaf = si;         // always error
    }

    static sealed class ConcreteSA permits ConcreteSB {}
    static final class ConcreteSB extends ConcreteSA {}
    static void rejectConcreteSealedSuperclassAssignment() {
        ConcreteSA sa = new ConcreteSA();
        ConcreteSB sb = sa;     // always error
    }

    static sealed abstract class MultiSA permits MultiSB1, MultiSB2 {}
    static final class MultiSB1 extends MultiSA {}
    static final class MultiSB2 extends MultiSA {}
    static void rejectAbstractSealedSuperclassWithMultipleSubclasses() {
        MultiSA sa = new MultiSB1();
        MultiSB1 sb = sa;       // always error
    }

    static sealed interface MultiSI permits MultiImpl1, MultiImpl2 {}
    static final class MultiImpl1 implements MultiSI {}
    static final class MultiImpl2 implements MultiSI {}
    static void rejectSealedInterfaceWithMultipleSubtypes() {
        MultiSI si = new MultiImpl1();
        MultiImpl1 impl = si;   // always error
    }

    static sealed interface I<T> permits A, B {}
    static final class A implements I<String> {}
    static final class B implements I<Integer> {}
    static void rejectSealedTypeWithNonApplicableSubtypes() {
        I<Long> i = null;
        A a = i;                // always error
    }

    class E {}
    sealed interface A1 permits B1, C1, D1 {}
    static non-sealed abstract class B1 implements A1 {}
    static non-sealed abstract class C1 extends B1 implements A1 {}
    static final class D1 extends C1 implements A1 {}
    static void rejectSealedTypeWithUnrelated() {
        E e = null;
        B1 b = e;               // always error
    }

    interface J {}
    static sealed interface II<T> permits A2, B2 {}
    static final class A2 implements II<String>, J {}
    static final class B2 implements II<Integer>, J {}
    static void rejectSealedWithTwoPermittedNotApplicable(II<Long> i) {
        J j = i;                // always error
    }

    static interface JG<T> {}
    static sealed interface III<T> permits A3, B3 {}
    static final class A3 implements III<String>, JG<String> {}
    static final class B3 implements III<Integer>, JG<Integer> {}
    static void rejectUncheckedGenericTarget(III<String> i) {
        JG<String> j = i;       // always error
    }

    static sealed interface S permits C {}
    static sealed class C implements S permits L1, L2 {}
    static final class L1 extends C {}
    static final class L2 extends C {}
    static void rejectSealedWithTwoApplicablePermitted(S s) {
        L1 l = s;               // always error
    }

    // Errors despite the existence of exhaustive switches
    // taken from test/langtools/tools/javac/patterns/Exhaustiveness.java
    sealed interface Base permits Special, Value {}
    non-sealed interface Value extends Base {}
    sealed interface Special extends Base permits SpecialValue {}
    non-sealed interface SpecialValue extends Value, Special {}
    static void nonSealed2(final Base base) {
        Value value = base;     // error

        // equivalent exhaustive switch
        // int ret = switch (base) {
        //     case Value value2 -> 0;
        // };
    }

    static sealed interface GI1<T> permits GA1, GB1 {}
    static final class GA1 implements GI1<String> {}
    static final class GB1 implements GI1<Integer> {}
    static void genericDirectLeafInterface(GI1<String> i) {
        GA1 a = i;              // error

        // equivalent exhaustive switch
        // int x = switch (i) {
        //     case GA1 a2 -> 0;
        // };
    }

    static sealed interface GI2<T> permits GMid2, GOther2 {}
    static non-sealed interface GMid2<T> extends GI2<T> {}
    static final class GMid2String implements GMid2<String> {}
    static final class GOther2 implements GI2<Integer> {}
    static void genericNonSealedTarget(GI2<String> i) {
        GMid2<?> m = i;         // error

        // equivalent exhaustive switch
        // int x = switch (i) {
        //     case GMid2<?> m2 -> 0;
        // };
    }

    interface JJJ {}
    static sealed interface IIII<T> permits AAA2, BBB2 {}
    static final class AAA2 implements IIII<Long>, JJJ {}
    static final class BBB2 implements IIII<Long>, JJJ {}
    static void assignmentToCommonInterface(II<Long> i) {
        JJJ j = i;              // error

        // equivalent exhaustive switch
        // int ret = switch (i) {
        //     case JJJ j2 -> 0;
        // };
    }
}
