package benchmark;

// Java program for implementation
// of Bubble Sort

class BubbleSort {

    public BubbleSort() {}

    void bubbleSort(int arr[])
    {
        int n = arr.length;

        for (int i = 0; i < n - 1; i++)
            for (int j = 0; j < n - i - 1; j++)
                if (arr[j] > arr[j + 1]) {

                    // swap temp and arr[i]
                    int temp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = temp;
                }
    }

    // Driver method to test above
    public static void main(String args[])
    {
        try {
            BubbleSort var1 = new BubbleSort();
            int[] var2 = new int[]{64, 34, 25, 12, 0, 23, 2, 3, 43, 54, 65 ,65 ,4,3 ,24,324,23};
            var1.bubbleSort(var2);
            int var3 = var2.length;

            for (int var4 = 0; var4 < var3; ++var4) {
                System.out.print(var2[var4] + " ");
            }

            System.out.println();
        } catch (Exception e) {
            e.printStackTrace();  // Просто для обработки всех исключений
        }
    }
}

