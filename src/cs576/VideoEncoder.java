package cs576;

import java.io.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
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
        segment(PREDICT_FRAMES);

        DataOutputStream outputStream = new DataOutputStream(new FileOutputStream(outputFile));
        outputStream.writeInt(width);
        outputStream.writeInt(height);
        outputStream.writeInt(frames.size());

        int frameCount = 0;
        for (Frame f : frames.values()) {
            System.out.print("\rFrame " + Integer.toString(++frameCount) + "/" + Integer.toString(frames.size()) + " Serialized.");
            outputStream.write(f.getFrameType());
            f.serialize(outputStream);
            outputStream.flush();
        }

        outputStream.close();
    }

    /**
     * Semantic Layering of Video
     *
     * @param predictFrames the number of predict frames after an Intraframe
     */
    private void segment(int predictFrames) throws IOException {

        InputStream inputStream = new FileInputStream(inputFile);
        // Mark first frame as an I frame
        int frameCount = 0;
        int frameNumbers = inputStream.available() / (3 * height * width);

        CompletableFuture[] futures = new CompletableFuture[frameNumbers];

        AtomicInteger processedFrame = new AtomicInteger(0);

        while (inputStream.available() != 0) {

            byte[] frameBuffer = readImage(inputStream);
            int frameIndex = frameCount++;

            CompletableFuture<Interframe> interframeCompletableFuture = CompletableFuture
                    .supplyAsync(() -> {
                        Interframe current = new Interframe(frameBuffer, height, width);
                        frames.put(frameIndex, current);
                        return current;
                    });
            interframeCompletableFuture.exceptionally(throwable -> {
                throwable.printStackTrace(System.err);
                return null;
            });
            interframeCompletableFuture.thenRun(() -> processedFrame.incrementAndGet());
            futures[frameIndex] = interframeCompletableFuture;


            for (int i = 0; i < predictFrames && inputStream.available() != 0; i++) {
                byte[] predictFrameBuffer = readImage(inputStream);
                int predictFrameIndex = frameCount++;

                CompletableFuture<Void> predictframeFuture = interframeCompletableFuture.thenAcceptAsync(interframe -> {
                    frames.put(predictFrameIndex, new PredictiveFrame(interframe, predictFrameBuffer, k));
                });
                predictframeFuture.exceptionally(throwable -> {
                    throwable.printStackTrace(System.err);
                    return null;
                });
                predictframeFuture.thenRun(() -> processedFrame.incrementAndGet());
                futures[predictFrameIndex] = predictframeFuture;
            }

        }
        inputStream.close();

        CompletableFuture<Void> allfutures = CompletableFuture.allOf(futures);

        while (!allfutures.isDone()) {
            System.out.print("\r");
            System.out.print("Frame " + Integer.toString(processedFrame.get()) + "/" + Integer.toString(frameNumbers) + " Processed.");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (allfutures.isCompletedExceptionally())
            allfutures.exceptionally(throwable -> {
                throwable.printStackTrace(System.err);
                return null;
            });

        System.out.println("\rFIRST STAGE DONE");

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
