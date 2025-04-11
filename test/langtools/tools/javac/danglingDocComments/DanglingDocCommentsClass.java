/*
 * @test /nodynamiccopyright/
 * @compile -Xlint:dangling-doc-comments DanglingDocCommentsClass.java
 * @compile/ref=empty.out -XDrawDiagnostics DanglingDocCommentsClass.java
 * @compile/ref=DanglingDocCommentsClass.enabled.out -XDrawDiagnostics -Xlint:dangling-doc-comments DanglingDocCommentsClass.java
 */

// This is a test of duplicate and misplaced doc comments in a class, using traditional comments

/** Bad/Extra Class Comment. */
/** Good Class Comment. */
@Deprecated
/** Misplaced: after anno. */
public /** Misplaced: after mods. */ class DanglingDocCommentsClass /** Misplaced: after ident */ {
    /** Bad/Extra Field Comment. */
    /** Good Field Comment. */
    public int i;

    /** Bad/Extra Method Comment. */
    /** Good Method Comment. */
    public void m1() { }

    @SuppressWarnings("dangling-doc-comments")
    /** Bad/misplaced/suppressed comment. */
    public void m2() { }

    public void m3(boolean b) {
        /****************
         * Box comment  *
         ***************/
        if (b) return;
    }

    public void m4a() {
        /** Not a doc comment. */
        System.out.println();
        /** Not a doc comment; not dangling for m4b */
    }

    /** Good comment for m4b; no dangling comments. */
    public void m4b() { }

    /** Comment ignored here: does not affect decls in block */
    static {
        /** Good comment. */
        int i = 0;
    }

    /** Dangling comment X */

    /**
     * The {@code @SuppressWarnings} annotation below retroactively
     * silences the warning about "Dangling comment X".
     */
    @SuppressWarnings("dangling-doc-comments")
    public void m5() { }
}
