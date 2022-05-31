/* @test
* @summary Test that tests the ResolutionErrorTable
*/

import java.io.File;
import java.io.*;

public class ErrorsDemo {
     static int x = 0;

     
     public static void main(String args[]) {
        String classDirectory = System.getProperty("test.classes");
        String filename = classDirectory + File.separator + "DeleteMe.class";
        File file = new File(filename); 
        boolean success = file.delete();
        
        System.out.println(success);

         for (int i = 0; i < 2; i++) {
             try {
                 new ErrorInInit();
             } catch (Throwable t) {
                 System.out.println(i + " = " + t);
             }
         }
         for (int i = 0; i < 2; i++) {
             try {
                 ErrorInResolution.doit();
             } catch (Throwable t) {
                 System.out.println(i + " = " + t);
             }
         }
     }
}

class ErrorInInit {
     static {
         if (true) {
             throw new RuntimeException("" + (ErrorsDemo.x++));
         }
     }
}

class DeleteMe {
     static int x;
}

class ErrorInResolution {
     static int doit() {
         return DeleteMe.x;
     }
}
