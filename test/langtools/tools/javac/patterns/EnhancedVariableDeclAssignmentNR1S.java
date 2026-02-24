/*
 * @test /nodynamiccopyright/
 * @summary Verify preview NR1S assignment conversions
 * @compile/fail/ref=EnhancedVariableDeclAssignmentNR1SNoPreview.out -XDrawDiagnostics EnhancedVariableDeclAssignmentNR1S.java
 * @compile/ref=EnhancedVariableDeclAssignmentNR1S.preview.out -XDrawDiagnostics -Xlint:preview --enable-preview --source ${jdk.version} EnhancedVariableDeclAssignmentNR1S.java
 * @run main/othervm --enable-preview EnhancedVariableDeclAssignmentNR1S
 */
import java.util.Objects;
import java.util.List;

public class EnhancedVariableDeclAssignmentNR1S {
    public static void main(String[] args) {
        assignmentFromSealedSupertype();
        arrayStoreFromSealedSupertype();
        returnFromSealedSupertype();
        genericAssignmentWithTypeVariable();
        interfaceToRecordAssignment();
        reassignmentFromSealedInterface();
        enhancedForElementNarrowing();
        returnFromParameterSealedSupertype(new SB1());
    }

    static sealed abstract class SA1 permits SB1 {}
    static final class SB1 extends SA1 {}
    static void assignmentFromSealedSupertype() {
        SA1 sa = new SB1();
        SB1 sb = sa;         // OK
    }
    static void arrayStoreFromSealedSupertype() {
        SA1 sa = new SB1();
        SB1[] sb = new SB1[1];
        sb[0] = sa;  // ok
    }
    static SB1 returnFromSealedSupertype() {
        SA1 sa = new SB1();
        return sa;   // ok
    }

    static sealed abstract class SA2<T> permits SB2 {}
    static final class SB2<T> extends SA2<T> {}
    static <T> void genericAssignmentWithTypeVariable() {
        SA2<T> sa = new SB2<>();  // WRC, OK
        SB2<T> sb = sa;
    }

    static record R1(int x) implements IR {}
    static sealed interface IR {}
    static void interfaceToRecordAssignment() {
        IR ir = new R1(42);
        R1 r1 = ir;             // OK
    }

    static sealed interface IFoo permits FooImpl {}
    static final class FooImpl implements IFoo {}
    static void reassignmentFromSealedInterface() {
        IFoo f = new FooImpl();
        FooImpl fi = new FooImpl();
        fi = f;      // OK
    }

    static void enhancedForElementNarrowing() {
        List<IFoo> fs = List.of(new FooImpl(), new FooImpl());
        int count = 0;
        for (FooImpl fi : fs) {
            count++;
        }
        assertEquals(2, count);
    }

    static SB1 returnFromParameterSealedSupertype(SA1 sa) {
        return sa; // OK
    }

    sealed interface I permits Mid {}
    non-sealed interface Mid extends I {}
    static final class Leaf implements Mid {}
    static void nonSealed1(I i) {
        Mid m = i;          // OK

        // equivalent exhaustive switch
        // int ret = switch (i) {
        //     case Mid m2 -> 0;
        // };
    }

    sealed interface II permits L {}
    sealed interface JJ permits L {}
    final class L implements JJ, II {}
    void multipleSafeSuperTypes1(II i) {
        L l = i; // OK
    }
    void multipleSafeSuperTypes2(JJ j) {
        L l = j; // OK
    }

    static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + "," +
                    "got: " + actual);
        }
    }
}
