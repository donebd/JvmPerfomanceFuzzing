package benchmark;

import java.io.IOException;

public class ExceptionHandlingPatterns {
    public int methodWithTryCatch(int[] array, int index) {
        try {
            return array[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            return -1;
        }
    }

    public int methodWithTryCatchFinally(int[] array, int index) {
        try {
            return array[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            return -1;
        } finally {
            // Блок finally всегда выполняется
            array[0] = 99;
        }
    }

    public int methodWithNestedTryCatch(int[] array, int index) {
        try {
            try {
                return array[index];
            } catch (NullPointerException e) {
                return -2;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            return -1;
        }
    }

    public static void main(String[] args) {
        ExceptionHandlingPatterns processor = new ExceptionHandlingPatterns();
        int[] array = new int[]{1, 2, 3, 4, 5};

        int result1 = processor.methodWithTryCatch(array, 10);
        int result2 = processor.methodWithTryCatchFinally(array, 2);
        int result3 = processor.methodWithNestedTryCatch(array, 1);

        System.out.println("Result 1: " + result1);
        System.out.println("Result 2: " + result2);
        System.out.println("Result 3: " + result3);
    }
}
