package cs4j.fasga;

import java.io.File;
import java.io.IOException;

import net.sci.array.scalar.ScalarArray;
import net.sci.array.scalar.UInt8Array;
import net.sci.array.color.RGB8Array2D;
import net.sci.array.scalar.BinaryArray2D;
import net.sci.array.scalar.UInt8Array2D;
import net.sci.image.Image;
import net.sci.image.analyze.LabelIntensities;
import net.sci.image.binary.BinaryImages;
import net.sci.image.io.ImageIOImageWriter;
import net.sci.image.io.ImageWriter;
import net.sci.image.morphology.MorphologicalFiltering;
import net.sci.image.morphology.MorphologicalReconstruction;
import net.sci.image.morphology.strel.Strel2D;
import net.sci.image.process.segment.OtsuThreshold;
import net.sci.table.Table;
import net.sci.table.io.DelimitedTableWriter;
import net.sci.table.io.TableWriter;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) throws IOException
    {
        // Number of points for the profile
        int nClasses = 100;
        
        System.out.println( "Starting processing of FASGA image..." );
        
        System.out.println("Check current dir: " + new File(".").getCanonicalPath());
        
        File inputFile = new File("./files/maize/maizeFasga_6635b_corr.png");
        System.out.println("  file exists: " + inputFile.exists());
        
        System.out.println("Read input image");
        Image image = Image.readImage(inputFile);
        RGB8Array2D array = (RGB8Array2D) image.getData();
        
        int sizeX = array.size(0);
        int sizeY = array.size(1);
        System.out.println("  image size: " + sizeX + "x" + sizeY);
        
        long t0 = System.nanoTime();
        
        // segmentation of stem region
        System.out.print("Conversion toUInt8");
        UInt8Array2D gray8 = array.convertToUInt8();
        long t1 = System.nanoTime();
        double dt = t1 - t0;
        System.out.println("  (time: " + (dt/1e6) + " ms)");

        System.out.print("Segmentation using Otsu Threshold");
        BinaryArray2D segStem = (BinaryArray2D) new OtsuThreshold().processScalar(gray8);
        long t2 = System.nanoTime();
        dt = t2 - t1;
        System.out.println("  (time: " + (dt/1e6) + " ms)");

        System.out.print("Compute complement");
        segStem = segStem.complement();
        long t3 = System.nanoTime();
        dt = t3 - t2;
        System.out.println("  (time: " + (dt/1e6) + " ms)");

        System.out.print("Fill Holes");
        segStem = (BinaryArray2D) MorphologicalReconstruction.fillHoles(segStem);
        UInt8Array2D segStem8 = (UInt8Array2D) UInt8Array.convert(segStem);
        long t4 = System.nanoTime();
        dt = t4 - t3;
        System.out.println("  (time: " + (dt/1e6) + " ms)");
        // use: segStem = (BinaryArray2D) new FillHoles().process(segStem); ?
        // use: segStem = (BinaryArray2D) FillHoles.fillHoles(segStem); ?
        
        System.out.println("Create segmented image");
        Image segStemImage = new Image(segStem8.times(255));        

        System.out.println("Save mask image 'segStem.png'");
        File outputFile = new File("./files/segStem.png");
        System.out.println("  segStem output File: " + outputFile.getAbsolutePath());
        ImageWriter writer = new ImageIOImageWriter(outputFile);
        writer.writeImage(segStemImage);

        // Compute distance map
        System.out.print("Compute distance map");
        t4 = System.nanoTime();
        ScalarArray<?> distMap = BinaryImages.distanceMap(segStem);
        long t5 = System.nanoTime();
        dt = t5 - t4;
        System.out.println("  (time: " + (dt/1e6) + " ms)");

        System.out.print("Convert to classes");
        double maxDist = distMap.maxValue();
        // V4: use function
        UInt8Array classes = UInt8Array.convert(distMap.apply(v -> Math.min(v * nClasses / maxDist, nClasses - 1)));
        // add 1 only within stem
        for (int[] pos : segStem.positions())
        {
            if (segStem.getBoolean(pos))
            {
                classes.setValue(classes.getValue(pos) + 1, pos);
            }
        }
//        // V3
//        UInt8Array classes = UInt8Array.convert(distMap.times(100.0 / maxDist).plus(1));
        // V2
//        UInt8Array classes = UInt8Array.convert(Math.add(Math.multiply(distMap, 100 / maxDist), 1));
        // V1
//        UInt8Array2D distMap8 = UInt8Array2D.create(sizeX, sizeY);
//        for (int y = 0; y < sizeY; y++)
//        {
//            for (int x = 0; x < sizeX; x++)
//            {
//            	int v8 = (int) Math.round(distMap.getValue(new int[]{x, y}) * 255 / maxDist);
//            	distMap8.setInt(x, y, v8);
//            }
//        }
        
        long t6 = System.nanoTime();
        dt = t6 - t5;
        System.out.println("  (time: " + (dt/1e6) + " ms)");
        
        System.out.println("Save distance map images");
        UInt8Array2D distMap8 = UInt8Array2D.wrap(UInt8Array.convert(distMap.times(255.0 / maxDist)));
        Image distMapImage = new Image(distMap8, image);
        new ImageIOImageWriter(new File("./files/distMap.png")).writeImage(distMapImage);

        Image classesImage = new Image(classes, image);
        new ImageIOImageWriter(new File("./files/classes.png")).writeImage(classesImage);
        
        
        // split channels
        System.out.println("Morphological filtering");
        UInt8Array2D red = array.channel(0);
        UInt8Array2D green = array.channel(1);
        UInt8Array2D blue = array.channel(2);
        
        Strel2D strel = Strel2D.Shape.SQUARE.fromRadius(10);
        UInt8Array2D redF = UInt8Array2D.wrap((UInt8Array) MorphologicalFiltering.opening(red, strel));
        UInt8Array2D greenF = UInt8Array2D.wrap((UInt8Array) MorphologicalFiltering.opening(green, strel));
        UInt8Array2D blueF = UInt8Array2D.wrap((UInt8Array) MorphologicalFiltering.opening(blue, strel));
        
        // create the label list
        int[] labels = new int[nClasses];
        for (int i = 1; i <= nClasses; i++)
        {
            labels[i-1] = i;
        }
        
        // compute profiles
        System.out.println("Compute profiles");
        double[] redProfile = LabelIntensities.mean(redF, classes, labels);
        double[] greenProfile = LabelIntensities.mean(greenF, classes, labels);
        double[] blueProfile = LabelIntensities.mean(blueF, classes, labels);
        
        Table table = Table.create(nClasses, 3);
        table.setColumnNames(new String[]{"Red", "Green", "Blue"});
        for (int i = 0; i < nClasses; i++)
        {
            table.setValue(i, 0, redProfile[i]);
            table.setValue(i, 1, greenProfile[i]);
            table.setValue(i, 2, blueProfile[i]);
        }
        
        System.out.println("Save result tables");
        TableWriter tableWriter = new DelimitedTableWriter();
        tableWriter.writeTable(table, new File("profiles.txt"));
        
        System.out.println( "End of processing." );
    }
}
