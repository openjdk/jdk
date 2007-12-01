/*
 * @test
 * @bug 4278121
 * @summary Ensure that calling unbind() on an unbound name returns
 *      successfully.
 */

import javax.naming.*;

public class UnbindIdempotent {

    public static void main(String[] args) throws Exception {

        // Create registry on port 1099 if one is not already running.
        try {
            java.rmi.registry.LocateRegistry.createRegistry(1099);
        } catch (java.rmi.RemoteException e) {
        }

        Context ictx = new InitialContext();
        Context rctx;
        try {
            rctx = (Context)ictx.lookup("rmi://localhost:1099");
        } catch (NamingException e) {
            // Unable to set up for test.
            return;
        }

        // Attempt to unbind a name that is not already bound.
        try {
            rctx.unbind("_bogus_4278121_");
        } catch (NameNotFoundException e) {
            throw new Exception("Test failed:  unbind() call not idempotent");
        }
    }
}
