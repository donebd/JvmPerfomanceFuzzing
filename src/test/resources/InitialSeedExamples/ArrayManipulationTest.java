package benchmark;

import java.util.Arrays;

public class ArrayManipulationTest {
    public int[] createAndFillArray(int size) {
        int[] array = new int[size];
        for (int i = 0; i < size; i++) {
            array[i] = i * 2;
        }
        return array;
    }

    public int sumArray(int[] array) {
        int sum = 0;
        for (int i = 0; i < array.length; i++) {
            sum += array[i];
        }
        return sum;
    }

    public void modifyArrayInPlace(int[] array) {
        for (int i = 0; i < array.length; i++) {
            array[i] = array[i] * 3 + 1;
        }
    }

    public int[] mergeArrays(int[] array1, int[] array2) {
        int[] result = new int[array1.length + array2.length];
        System.arraycopy(array1, 0, result, 0, array1.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }

    public static void main(String[] args) {
        ArrayManipulationTest test = new ArrayManipulationTest();
        int[] array1 = test.createAndFillArray(1000);
        int[] array2 = test.createAndFillArray(500);

        int sum = test.sumArray(array1);
        test.modifyArrayInPlace(array2);
        int[] merged = test.mergeArrays(array1, array2);

        System.out.println("Sum: " + sum);
        System.out.println("Modified array first element: " + array2[0]);
        System.out.println("Merged array length: " + merged.length);
    }
}
