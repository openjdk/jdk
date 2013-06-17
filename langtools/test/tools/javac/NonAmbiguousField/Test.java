/*
 * @test
 * @bug 4053724
 * @summary Certain non-ambiguous field references were reported by the
 *          compiler as ambigous.
 * @author turnidge
 *
 * @compile one/Parent.java two/Child.java
 * @compile/fail one/Parent2.java two/Child2.java
 */
