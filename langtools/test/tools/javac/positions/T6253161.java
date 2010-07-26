/*
 * @test  /nodynamiccopyright/
 * @bug     6253161
 * @summary Compiler will fail to find the correct location of serial warnings for anonymous inner classes
 * @author  Seetharama Avadhanam
 * @compile -Xlint:serial -XDdev T6253161.java
 * @compile/ref=T6253161.out -Xlint:serial -XDdev -XDrawDiagnostics T6253161.java
 */
import java.util.List;
import java.util.ArrayList;

public class T6253161 {
    @SuppressWarnings("unchecked")
    public void anonymousMethod(){
           List list = new ArrayList<String>(){
           static final long serialVersionUID = 1;
           List list = new ArrayList<Integer>();
           public List<Integer> getMyList(){
                final List floatList = new ArrayList<Float>(){
                    List integerList = new ArrayList<Float>();
                    public List<Float> getMyList(){
                        for(int i=0;i<10;i++)
                            integerList.add((int)((Float.parseFloat(i+""))+(1.11F)));
                        return (List)(Object)integerList;
                    }
                    public void testMethods(){
                        //...
                    }
                }.getMyList();
                for(int i=0;i<10;i++)
                    list.add((Float)(floatList.get(i)) * 11.232F * i);
                return list;
            }
         }.getMyList();
    }
}
