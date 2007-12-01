/*
 * @test  /nodynamiccopyright/
 * @bug     6253161
 * @summary Compiler will fail to find the correct location of serial warnings for anonymous inner classes
 * @author  Seetharama Avadhanam
 * @compile -Xlint:serial -XDdev T6253161a.java
 * @compile/ref=T6253161a.out -Xlint:serial -XDdev -XDrawDiagnostics -XDstdout T6253161a.java
 */
import java.util.List;
import java.util.ArrayList;

public class T6253161a {
    @SuppressWarnings("unchecked")
    public void anonymousMethod(){
           List list = new ArrayList<String>(){
           static final long serialVersionUID = 1;
           List list = new ArrayList<Integer>();
           public List<Integer> getMyList(){
                final List floatList = new ArrayList<Float>(){
                    // Blank ....
                };
                for(int i=0;i<10;i++)
                    list.add((Float)(floatList.get(i)) * 11.232F * i);
                return list;
            }
         }.getMyList();
    }
}
