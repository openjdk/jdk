/*
 * @test
 * @bug 4278094
 * @summary Ensure that setValuesCaseSensitive() does not leave name
 *      in an invalid state.
 */

import com.sun.jndi.ldap.LdapName;

public class Case {

    public static void main(String[] args) throws Exception {

        LdapName name = new LdapName("cn=Kuwabatake Sanjuro");
        name.setValuesCaseSensitive(false);
        name.size();    // will throw exception if rdns is null
    }
}
