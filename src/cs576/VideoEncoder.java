package cs576;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Jeffreye on 4/1/2017.
 */
public class VideoEncoder {

    static final int MACROBLOCK_LENGTH = 16;

    private String inputFile;
    private String outputFile;
    private int width;
    private int height;
    private static final int k = 10;
    private static final int PREDICT_FRAMES = 4;

    private static final int BATCH_WORKS = 20;

    private ConcurrentSkipListMap<Integer, Frame> frames;

    public VideoEncoder(String inputFile, String outputFile, int width, int height) throws FileNotFoundException {
        this.outputFile = outputFile;
        this.inputFile = inputFile;
        this.width = width;
        this.height = height;
        frames = new ConcurrentSkipListMap<>();
    }


    byte[] readImage(InputStream inputStream) throws IOException {

        byte[] bytes = new byte[width * height * 3];
        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length && (numRead = inputStream.read(bytes, offset, bytes.length - offset)) >= 0) {
            offset += numRead;
        }
        return bytes;
    }

    public void encode() throws IOException {


        InputStream inputStream = new FileInputStream(inputFile);
        // Mark first frame as an I frame
        int frameCount = 0;
        int frameNumbers = inputStream.available() / (3 * height * width);

        final DataOutputStream outputStream =
                new DataOutputStream(
                new BufferedOutputStream(
                        new FileOutputStream(outputFile),
                        50*1024*1024)
                );
        outputStream.writeInt(width);
        outputStream.writeInt(height);

        outputStream.writeInt(frameNumbers);

        CompletableFuture<Frame> lastFrame = null;
        CompletableFuture<Void> outputFuture = CompletableFuture.completedFuture(null);
        AtomicInteger processedInteger = new AtomicInteger(0);

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

            byte[] frameBuffer = readImage(inputStream);

            if (frameCount % PREDICT_FRAMES == 0 && frameCount != frameNumbers) {
                lastFrame = CompletableFuture.supplyAsync(() -> new Interframe(frameBuffer, height, width));
            } else {
                lastFrame =
                        CompletableFuture
                        .supplyAsync(() ->  new PredictiveFrame(frameBuffer, height, width))
                        .thenCombineAsync(lastFrame,(curr,last)->curr.computeDiff(last,k));
            }

            outputFuture = outputFuture.thenAcceptBoth(lastFrame,(o,frame)->{
                if  (frame.getFrameType() == Frame.INTERFRAME){
                    // Interframe haven't segementated yet
                    return;
                }

                Frame referenceFrame = ((PredictiveFrame)frame).referenceFrame;
                if (referenceFrame.getFrameType() == Frame.INTERFRAME){
                    try {
                        outputStream.write(referenceFrame.getFrameType());
                        referenceFrame.serialize(outputStream);
                        processedInteger.incrementAndGet();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                try {
                    outputStream.write(frame.getFrameType());
                    frame.serialize(outputStream);
                    processedInteger.incrementAndGet();
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            frameCount++;

//            outputFuture.join();
//            System.out.print("\r");
//            System.out.print("Frame " + Integer.toString(frameCount) + "/" + Integer.toString(frameNumbers) + " Processed.");

        }

        inputStream.close();

        outputFuture.join();
        outputStream.close();

        System.out.println("\rDONE");
    }


    public static void main(String[] argv) {
        if (argv.length == 1 && argv[0].endsWith(".rgb")) {
            try {
                String input = argv[0];
                String output = input.substring(0, input.length() - 4).concat(".cmp");
                VideoEncoder encoder = new VideoEncoder(input, output, 960, 540);
                encoder.encode();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }

        } else {
            System.err.println("Invalid arguments");
            System.err.println("Only accepts file name as argument");
        }
    }
}
