/*
 * @test /nodynamiccopyright/
 * @library ..
 * @build DocLintTester
 * @run main DocLintTester -ref InsertImplicit.out InsertImplicit.java
 */

// tidy: Warning: inserting implicit <.*>

/**
 * </p>
 * <i> <blockquote> abc </blockquote> </i>
 */
public class InsertImplicit { }
