/* @test  /nodynamiccopyright/
   @bug 4037020 4785453
   @summary Verify that ClassModifier "synchronized" is not allowed.
   @author dps

   @run shell SynchronizedClass.sh
*/

public synchronized class SynchronizedClass { } // ERROR
