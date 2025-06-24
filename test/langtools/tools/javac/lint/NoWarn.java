/**
 * @test /nodynamiccopyright/
 * @bug 6183484 8352612
 * @summary Restrict -Xlint:none to affect only lint categories, while -nowarn disables all warnings
 * @compile/ref=NoWarn1.out -XDfind=diamond -XDrawDiagnostics -Xlint:none                           NoWarn.java
 * @compile/ref=NoWarn2.out -XDfind=diamond -XDrawDiagnostics -Xlint:divzero,unchecked              NoWarn.java
 * @compile/ref=NoWarn2.out -XDfind=diamond -XDrawDiagnostics -Xlint:none,divzero,unchecked         NoWarn.java
 * @compile/ref=NoWarn3.out -XDfind=diamond -XDrawDiagnostics -Xlint:none                   -nowarn NoWarn.java
 * @compile/ref=NoWarn4.out -XDfind=diamond -XDrawDiagnostics -Xlint:divzero,unchecked      -nowarn NoWarn.java
 * @compile/ref=NoWarn4.out -XDfind=diamond -XDrawDiagnostics -Xlint:none,divzero,unchecked -nowarn NoWarn.java
 */
import java.util.*;
class NoWarn {
    Set<?> z = null;                        // Mandatory  Lint  Lint Category   How can it be suppressed?
                                            // ---------  ----  -------------   -------------------------
    sun.misc.Unsafe b;                      //    Yes      No        N/A        Not possible
    Set<String> a = new HashSet<String>();  //    No       No        N/A        "-nowarn" only (requires -XDfind=diamond)
    Set<String> d = (Set<String>)z;         //    Yes      Yes    "unchecked"   "-Xlint:-unchecked" only
    int c = 1/0;                            //    No       Yes    "divzero"     "-Xlint:-divzero" or "-nowarn"
}
