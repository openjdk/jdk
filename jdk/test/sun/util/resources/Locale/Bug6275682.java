/**
    @test
    @summary Verifying that the language names starts with lowercase in spanish
    @bug 6275682
*/

import java.util.Locale;

public class Bug6275682 {

   public static void main (String[] args) throws Exception {
        Locale es = new Locale ("es");
        String[] isoLangs = es.getISOLanguages ();
        String error = "";

        for (int i = 0; i < isoLangs.length; i++) {
            Locale current = new Locale (isoLangs[i]);
            String localeString = current.getDisplayLanguage (es);
            String startLetter = localeString.substring (0,1);
            if (!startLetter.toLowerCase (es).equals (startLetter)){
                error = error + "\n\t"+ isoLangs[i] + " " + localeString;
            }
        }

        if (error.length () > 0){
            throw new Exception ("\nFollowing language names starts with upper-case letter: "
                    + error + "\nLower-case expected!");
        }

    }
}
