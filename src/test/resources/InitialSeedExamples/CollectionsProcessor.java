package benchmark;

import java.util.*;

public class CollectionsProcessor {
    public List<Integer> processArrayList(int size) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(i);
        }

        Iterator<Integer> it = list.iterator();
        while (it.hasNext()) {
            Integer val = it.next();
            if (val % 3 == 0) {
                it.remove();
            }
        }

        return list;
    }

    public List<Integer> processLinkedList(int size) {
        List<Integer> list = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            list.add(0, i); // добавление в начало
        }

        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) % 5 == 0) {
                list.set(i, list.get(i) * 2);
            }
        }

        return list;
    }

    public static void main(String[] args) {
        CollectionsProcessor processor = new CollectionsProcessor();
        List<Integer> result1 = processor.processArrayList(100);
        List<Integer> result2 = processor.processLinkedList(100);
        System.out.println("ArrayList elements: " + result1.size());
        System.out.println("LinkedList elements: " + result2.size());
    }
}
