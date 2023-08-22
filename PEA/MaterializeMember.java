import java.util.*;

class MaterializeMember {
    // inspired by TestNG-7.3.0
    // https://github.com/testng-team/testng/blob/9ae7d3d9d19e5aa51e9610549c72e883718e82e5/src/main/java/org/testng/internal/reflect/ReflectionRecipes.java#L369C56-L369C80
    private static class ListBackedImmutableQueue<T> {
        private final List<T> backingList;

        ListBackedImmutableQueue(final T[] elements) {
            backingList = new ArrayList<>(elements.length);
            Collections.addAll(backingList, elements);
        }

        T poll() {
            if (!backingList.isEmpty()) {
                return backingList.remove(0);
            }
            throw new RuntimeException("Queue exhausted");
        }
    }

    public void test() {
        Integer[] parameters = new Integer[3];
        for (int i = 0; i < parameters.length; ++i) {
            parameters[i] = Integer.valueOf(i);
        }
        var queue = new ListBackedImmutableQueue<Integer>(parameters);
        for (Integer p : parameters) {
            Integer t = queue.poll();
            if (!t.equals(p)) {
                throw new RuntimeException("wrong result!");
            }
        }
    }

    public static void main(String[] args) {
        MaterializeMember kase = new MaterializeMember();
        for (int i = 0; i < 300_000; ++i) {
            kase.test();
        }
    }
}
