import sun.security.util.SecurityConstants;
import java.security.Permission;

public class CustomSecurityManager extends SecurityManager {
    @Override
    public void checkPermission(Permission perm) {
        if (perm.implies(SecurityConstants.AWT.TOPLEVEL_WINDOW_PERMISSION)) {
            throw new SecurityException();
        }
    }
}
