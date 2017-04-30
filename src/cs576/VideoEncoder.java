package cs576;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
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
    private static final int k =10;

    private static final int BATCH_WORKS = 20;

    private ConcurrentSkipListMap<Integer, Frame> frames;

    private ByteBuffer bytes;

    public VideoEncoder(String inputFile, String outputFile, int width, int height) throws FileNotFoundException {
        this.outputFile = outputFile;
        this.inputFile = inputFile;
        this.width = width;
        this.height = height;
        frames = new ConcurrentSkipListMap<>();

        bytes = ByteBuffer.allocate(height * width * 3);
    }


    byte[] readImage(FileChannel inputStream) throws IOException {

        int numRead = 0;
        bytes.clear();
        while (bytes.hasRemaining() && (numRead = inputStream.read(bytes)) >= 0) {

        }
        return bytes.array();
    }

    public void encode() throws IOException {


        FileInputStream inputStream = new FileInputStream(inputFile);
        // Mark first frame as an I frame
        int frameNumbers = inputStream.available() / (3 * height * width);

        FileOutputStream outputStream = new FileOutputStream(outputFile);


        ByteBuffer buffer = ByteBuffer.allocateDirect(3 * 4);
        buffer.putInt(width);
        buffer.putInt(height);
        buffer.putInt(frameNumbers);

        buffer.flip();
        outputStream.getChannel().write(buffer);

        encodeSync(inputStream.getChannel(), outputStream.getChannel(), frameNumbers);

        inputStream.close();
        outputStream.close();

    }

    public void encodeSync(FileChannel inputStream, FileChannel outputStream, int frameNumbers) throws IOException {
        int frameCount = 0;
        SegmentedFrame prev = new SegmentedFrame(readImage(inputStream), height, width);
        while (frameCount < frameNumbers) {
            SegmentedFrame curr = new SegmentedFrame(readImage(inputStream), height, width)
                    .computeDiff(prev, k);


            prev.serialize(outputStream);
            frameCount++;

            prev = curr;

            // release reference so that GC could collect them
            final int RESERVED_FRAMES = 10;
            SegmentedFrame f = curr;
            for (int i = 0; i < RESERVED_FRAMES && f != null; i++) {
                f = f.referenceFrame;
            }
            if (f != null)
                f.referenceFrame = null;

            System.out.print("\r");
            System.out.print("Frame " + Integer.toString(frameCount) + "/" + Integer.toString(frameNumbers) + " Processed.");

        }

        prev.serialize(outputStream);
        frameCount++;

        System.out.println("\rDONE");
    }

    public void encodeAsync(FileChannel inputStream, DataOutputStream outputStream, int frameNumbers) throws IOException {
        int frameCount = 0;
        CompletableFuture<SegmentedFrame> lastFrame =
                CompletableFuture.completedFuture(readImage(inputStream))
                        .thenApplyAsync(firstFrame -> new SegmentedFrame(firstFrame, height, width));

        CompletableFuture<Void> outputFuture = CompletableFuture.completedFuture(null);
        AtomicInteger processedInteger = new AtomicInteger(0);

        while (frameCount < frameNumbers) {

            if (frameCount % BATCH_WORKS == 0 && frameCount != 0) {
                while (!lastFrame.isDone()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.print("\r");
                    System.out.print("Frame " + Integer.toString(processedInteger.get()) + "/" + Integer.toString(frameNumbers) + " Processed.");

                }
            }

            lastFrame = CompletableFuture
                    .completedFuture(readImage(inputStream))
                    .thenApplyAsync(frameBuffer -> new SegmentedFrame(frameBuffer, height, width))
                    .thenCombine(lastFrame, (curr, last) -> curr.computeDiff(last, k));

            outputFuture = outputFuture.thenAcceptBoth(lastFrame, (o, frame) -> {
                SegmentedFrame previousFrame = frame.referenceFrame;
                if (previousFrame.referenceFrame == null) {
                    try {
                        previousFrame.serialize(outputStream);
                        processedInteger.incrementAndGet();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                try {
                    frame.serialize(outputStream);
                    processedInteger.incrementAndGet();
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // release reference so that GC could collect them
                previousFrame.referenceFrame = null;
            });

            frameCount++;
        }


        outputFuture.join();

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
