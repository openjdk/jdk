/*
 * @test /nodynamiccopyright/
 * @compile -Xlint:dangling-doc-comments DanglingDocCommentsEnum.java
 * @compile/ref=empty.out -XDrawDiagnostics DanglingDocCommentsEnum.java
 * @compile/ref=DanglingDocCommentsEnum.enabled.out -XDrawDiagnostics -Xlint:dangling-doc-comments DanglingDocCommentsEnum.java
 */

// This is a test of duplicate and misplaced doc comments in an enum class, using traditional comments

/** Bad/Extra Enum Comment. */
/** Good Enum Comment. */
@Deprecated
/** Misplaced: after anno. */
public /** Misplaced: after mods. */ enum DanglingDocCommentsEnum /** Misplaced: after ident */
{
    /** Bad/Extra enum-member Comment. */
    /**
     * Good enum-member Comment.
     */
    E1;

    /** Bad/Extra Field Comment. */
    /**
     * Good Field Comment.
     */
    public int i;

    /** Bad/Extra Method Comment. */
    /**
     * Good Method Comment.
     */
    public void m1() {
    }

    @SuppressWarnings("dangling-doc-comments")
    /** Bad/misplaced/suppressed comment. */
    public void m2() {
    }
}