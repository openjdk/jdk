/*
 * @test /nodynamiccopyright/
 * @bug 4041851
 * @summary The gramamr allows java files without class or interface
 *          declarations; when the compiler encountered this, it failed
 *          to check the validity of import declarations.
 * @author turnidge
 *
 * @compile/fail/ref=NoClass.out -XDrawDiagnostics  NoClass.java
 */

import nonexistent.pack.cls;
