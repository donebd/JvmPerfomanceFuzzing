package benchmark;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LambdaAndStreams {
    public List<Integer> processWithLoops(List<Integer> input) {
        List<Integer> result = new java.util.ArrayList<>();
        for (Integer num : input) {
            if (num % 2 == 0) {
                result.add(num * num);
            }
        }
        return result;
    }

    public List<Integer> processWithStreams(List<Integer> input) {
        return input.stream()
                .filter(num -> num % 2 == 0)
                .map(num -> num * num)
                .collect(Collectors.toList());
    }

    public static void main(String[] args) {
        LambdaAndStreams processor = new LambdaAndStreams();
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        List<Integer> result1 = processor.processWithLoops(numbers);
        List<Integer> result2 = processor.processWithStreams(numbers);

        System.out.println("Loop result: " + result1);
        System.out.println("Stream result: " + result2);
    }
}
