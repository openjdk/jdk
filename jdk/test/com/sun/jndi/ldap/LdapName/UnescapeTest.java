/*
 * @test
 * @bug 4892070
 * @summary java gets hung in
 *      com.sun.jndi.ldap.LdapName$TypeAndValue.unescapeValue()
 */

import com.sun.jndi.ldap.LdapName;

public class UnescapeTest  {

    public static void main(String[] args) {

        try {

            // The buggy code hangs in the method unescapeAttributeValue()
            System.out.println(LdapName.unescapeAttributeValue("\\uvw"));

        } catch (IllegalArgumentException e) {
            System.out.println("Caught the right exception");
        }

    }
}
