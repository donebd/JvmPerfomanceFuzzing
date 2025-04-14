package benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CollectionBenchmark {

    public List<Integer> processCollection(int size) {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            numbers.add(i * 2);
        }

        Collections.shuffle(numbers);
        numbers.sort(Integer::compare);

        List<Integer> filtered = new ArrayList<>();
        for (Integer num : numbers) {
            if (num % 3 == 0) {
                filtered.add(num);
            }
        }

        return filtered;
    }

    public static void main(String[] args) {
        CollectionBenchmark cb = new CollectionBenchmark();

        List<Integer> result1 = cb.processCollection(1000);
        List<Integer> result2 = cb.processCollection(5000);

        System.out.println("Result 1 size: " + result1.size());
        System.out.println("Result 2 size: " + result2.size());
    }
}
