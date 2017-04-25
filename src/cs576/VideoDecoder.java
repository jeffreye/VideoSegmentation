package cs576;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Jeffreye on 4/1/2017.
 */
public class VideoDecoder {

    private static final int BATCH_WORKS = 20;
    private final String inputFile;
    private final String outputFile;
    private final int foregroundQuantizationValue;
    private final int backgroundQuantizationValue;
    private int width;
    private int height;



    public VideoDecoder(String inputFile, String outputFile, int foregroundQuantizationValue, int backgroundQuantizationValue) throws FileNotFoundException {
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.foregroundQuantizationValue = foregroundQuantizationValue;
        this.backgroundQuantizationValue = backgroundQuantizationValue;
    }

    public void decode() throws IOException {
        DataInputStream inputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(this.inputFile),1024*1024));
        width = inputStream.readInt();
        height = inputStream.readInt();
        int frameNumbers = inputStream.readInt();

        int frameCount = 0;
        CompletableFuture<Frame> lastFrame = null;
        CompletableFuture<Void> outputFuture = CompletableFuture.completedFuture(null);
        AtomicInteger processedInteger = new AtomicInteger(0);

        FileOutputStream outputStream = new FileOutputStream(outputFile);

        while (inputStream.available() != 0) {


            if (frameCount % BATCH_WORKS == 0 && lastFrame != null){
                while (!lastFrame.isDone()){
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.print("\r");
                    System.out.print("Frame " + Integer.toString(processedInteger.get()) + "/" + Integer.toString(frameNumbers) + " Processed.");

                }
            }
            
            int type = inputStream.read();
            if (type == Frame.INTERFRAME) {
                final Interframe frame = new Interframe(height, width, inputStream);

                lastFrame = CompletableFuture.supplyAsync(()->frame.reconstruct(foregroundQuantizationValue,backgroundQuantizationValue));

            } else if (type == Frame.PREDICTIVEFRAME) {
                final PredictiveFrame frame = new PredictiveFrame(height,width,inputStream);

                lastFrame = lastFrame.thenApplyAsync(last -> frame.reconstruct(last,foregroundQuantizationValue,backgroundQuantizationValue));
            }

            outputFuture.thenAcceptBoth(lastFrame, (o, last) -> {
                try {
                    outputStream.write(last.getRawImage());
                    outputStream.flush();
                    processedInteger.incrementAndGet();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            frameCount++;
        }


        inputStream.close();
        outputFuture.join();
        outputStream.close();
    }


    public static void main(String[] argv) {
        if (argv.length >= 2 && argv[0].endsWith(".cmp")) {
            try {
                String input = argv[0];
                String output = input.substring(0, input.length() - 4).concat(".raw");
                int quantizationForeground = Integer.parseInt(argv[1]);
                int quantizationBackground = Integer.parseInt(argv[2]);
                VideoDecoder decoder = new VideoDecoder(input, output, quantizationForeground,quantizationBackground);
                decoder.decode();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }

        } else {
            System.err.println("Invalid arguments");
            System.err.println("Only accepts file name as argument");
        }
    }
}
