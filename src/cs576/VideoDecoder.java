package cs576;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
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


    private JFrame frame;
    private JLabel lbIm1;
    private Queue<BufferedImage> imgs;
    private ArrayList<BufferedImage> allImgs;
    private Timer timer;

    private boolean doneDecoding;

    public VideoDecoder(String inputFile, String outputFile, int foregroundQuantizationValue, int backgroundQuantizationValue,int fps) throws FileNotFoundException {
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.foregroundQuantizationValue = foregroundQuantizationValue;
        this.backgroundQuantizationValue = backgroundQuantizationValue;


        imgs = new ArrayDeque<>(10);
        allImgs = new ArrayList<>(100);
//        img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        showWindow(fps);
    }

    public void showWindow(int fps){
        frame = new JFrame();
        GridBagLayout gLayout = new GridBagLayout();
        frame.getContentPane().setLayout(gLayout);
        String result = String.format("Video height: %d, width: %d", height, width);
        JLabel lbText1 = new JLabel(result);
        lbText1.setHorizontalAlignment(SwingConstants.CENTER);

        lbIm1 = new JLabel(new ImageIcon(new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB)));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.weightx = 0.5;
        c.gridx = 0;
        c.gridy = 0;
        frame.getContentPane().add(lbText1, c);


        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 1;
        frame.getContentPane().add(lbIm1, c);

        frame.setTitle("Video Player");
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);


        timer = new Timer(1000 / fps, e -> refreshImage());
        timer.setInitialDelay(0);
        timer.start();
    }

    private void refreshImage(){

        if (imgs.size() == 0)
            if(doneDecoding)
                imgs.addAll(allImgs);
            else
                return;

        lbIm1.setIcon(new ImageIcon(imgs.remove()));
    }

    public void decode() throws IOException {
        DataInputStream inputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(this.inputFile), 960*540*3*2));
        width = inputStream.readInt();
        height = inputStream.readInt();
        int frameNumbers = inputStream.readInt();

        decodeSync(inputStream, frameNumbers);

        doneDecoding = true;
        inputStream.close();
    }
    public void decodeSync(DataInputStream inputStream, int frameNumbers) throws IOException {
        int frameCount = 0;
        int[] rawImage = new int[height*width];


//        FileOutputStream outputStream = new FileOutputStream(outputFile);
        SegmentedFrame lastFrame = new SegmentedFrame(height,width);
        while (inputStream.available() != 0) {

            lastFrame = lastFrame.loadFrom(inputStream)
                    .reconstruct(lastFrame,foregroundQuantizationValue,backgroundQuantizationValue);

//            outputStream.write(lastFrame.getRawImage());
//            outputStream.flush();
            lastFrame.getRawImage(rawImage);

            BufferedImage img = new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
            int ind = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    img.setRGB(x, y, rawImage[ind++]);
                }
            }

            imgs.add(img);
            allImgs.add(img);

            frameCount++;

            System.out.print("\r");
            System.out.print("Frame " + Integer.toString(frameCount) + "/" + Integer.toString(frameNumbers) + " Processed.");
        }
        System.out.println("\rDONE");
    }

    public void decodeAsync(DataInputStream inputStream, int frameNumbers) throws IOException {
        int frameCount = 0;
        CompletableFuture<SegmentedFrame> lastFrame = CompletableFuture.completedFuture(null);
        CompletableFuture<Void> outputFuture = CompletableFuture.completedFuture(null);
        AtomicInteger processedInteger = new AtomicInteger(0);

        FileOutputStream outputStream = new FileOutputStream(outputFile);

        while (inputStream.available() != 0) {


            if (frameCount % BATCH_WORKS == 0 && lastFrame != null) {
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

            CompletableFuture<SegmentedFrame> current = CompletableFuture.completedFuture(new SegmentedFrame(height, width, inputStream));
            lastFrame = lastFrame.thenCombineAsync(current, (last, curr) -> curr.reconstruct(last, foregroundQuantizationValue, backgroundQuantizationValue));

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
                VideoDecoder decoder = new VideoDecoder(input, output, quantizationForeground, quantizationBackground,30);
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
