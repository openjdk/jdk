/*
 * @test /nodynamiccopyright/
 * @bug 8074346
 * @author sadayapalam
 * @summary Test that type annotation on a qualified type doesn't cause spurious 'cannot find symbol' errors
 * @compile/fail/ref=T8074346.out -XDrawDiagnostics T8074346.java
*/

abstract class T8074346 implements
        @T8074346_TA @T8074346_TB java.util.Map<@T8074346_TA java.lang.String, java.lang.@T8074346_TA String>,
        java.util.@T8074346_TA List {
}

@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE)
@interface T8074346_TA { }

@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE)
@interface T8074346_TB { }