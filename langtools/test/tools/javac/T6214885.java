/*
 * @test  /nodynamiccopyright/
 * @bug 6214885
 * @summary This test exercises features provided by the new internal Diagnostics API
 * @compile/fail/ref=T6214885a.out -XDdiags=%b:%l%_%t%m|%p%m T6214885.java
 * @compile/fail/ref=T6214885b.out -XDdiags=%b:%l:%c%_%t%m|%p%m T6214885.java
 */
class T6214885
{
    public void m() {
        x = 1;
    }
}
