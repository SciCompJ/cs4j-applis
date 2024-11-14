/**
 * 
 */
package cs4j.fasga;

import java.io.File;
import java.io.IOException;

import net.sci.array.Array2D;
import net.sci.array.color.RGB8Array2D;
import net.sci.array.binary.BinaryArray2D;
import net.sci.array.numeric.ScalarArray2D;
import net.sci.array.numeric.UInt8Array;
import net.sci.array.numeric.UInt8Array2D;
import net.sci.image.Image;
import net.sci.image.morphology.MorphologicalReconstruction;
import net.sci.image.segmentation.OtsuThreshold;

/**
 * @author dlegland
 *
 */
public class NormalizeBackground
{
    public static final BinaryArray2D segmentImage_RGB8(RGB8Array2D array)
    {
        System.out.println("Conversion toUInt8");
        UInt8Array2D gray8 = array.convertToUInt8();
        
        System.out.println("Segmentation using Otsu Threshold");
        BinaryArray2D segStem = (BinaryArray2D) new OtsuThreshold().processScalar(gray8);
        
        // Fill holes, and keep mask of the background
        segStem = segStem.complement();
        segStem = (BinaryArray2D) MorphologicalReconstruction.fillHoles(segStem);
        segStem = segStem.complement();
        
        return segStem;
    }
    
    /**
     * Normalizes the background of the input image, assuming the structures are
     * dark over a bright background, and that the background can be modelled
     * with a polynomial of the coordinates.
     * 
     * @param image
     *            the image to normalize (either UInt8 or RGB8)
     * @param mask
     *            the binary mask of the background
     * @param maxDegree
     *            the maximal degree of the fitting polynomial
     * @return an array the same class as the input array, normalized by the
     *         background estimate.
     */
    public final static Array2D<?> normalizeBackground(Array2D<?> image, BinaryArray2D mask, int maxDegree)
    {
        if (image instanceof UInt8Array2D)
        {
            return normalizeBackground_UInt8((UInt8Array2D) image, mask, maxDegree);
        }
        else if (image instanceof RGB8Array2D)
        {
            return normalizeBackground_RGB8((RGB8Array2D) image, mask, maxDegree);
        }
        else
        {
            throw new IllegalArgumentException("Unable to handle array with class: " + image.getClass().getName());
        }
    }
    
    /**
     * Computes background model from image and mask, with the specified degree,
     * and computes normalized ratio of input image with background. 
     */
    public final static RGB8Array2D normalizeBackground_RGB8(RGB8Array2D image, BinaryArray2D mask, int maxDegree)
    {
        RGB8Array2D res = RGB8Array2D.create(image.size(0), image.size(1));
        
        // iterate over channels
        for (int c = 0; c < 3; c++)
        {
            UInt8Array2D channel = image.channel(c);
            UInt8Array2D channel2 = normalizeBackground_UInt8(channel, mask, maxDegree);
            res.setChannel(c, channel2);
        }
        return res;
    }

    public final static UInt8Array2D normalizeBackground_UInt8(UInt8Array2D image, BinaryArray2D mask, int maxDegree)
    {
        ScalarArray2D<?> bgFit = fitBackground(image, mask, 2, 4);
        return normalizeBrightBackground(image, bgFit, 0.0, 1.0);
    }
    
    /**
     * Estimates a background image by fitting a polynomial model of the values
     * of the input image within the mask.
     * 
     * @param image
     *            the image used to estimate background model
     * @param mask
     *            the binary mask for values to estimate
     * @param maxDegree
     *            the maximum degree of the polynomial (2 or 3 is often
     *            sufficient)
     * @param samplingStep
     *            can be used to reduce the computation time on large images
     *            (typical value is 2)
     * @return a new image the same size as the input image containing estimate
     *         of the background value for each pixel
     */
    public final static ScalarArray2D<?> fitBackground(UInt8Array2D image, BinaryArray2D mask, int maxDegree, int samplingStep)
    {
        PolynomialBackground pbg = new PolynomialBackground(maxDegree);
        return pbg.fitBackground(image, mask, samplingStep);
    }
    
    /**
     * Computes normalized division on scalar images. The ratio of the two images
     * is expected to be within 0 and 1. The bounds can be adjusted to enhance
     * the contrast of the resulting image.
     * 
     * @param image
     *            the image to normalize
     * @param bgImage
     *            A scalar image processor containing the estimate of the
     *            (bright) background
     * @param lower
     *            the lower bound of the result that will be mapped to 0
     * @param upper
     *            the upper bound of the result that will be mapped to 255
     * @return a new instance of UInt8Array2D containing normalized result of
     *         division
     */
    public final static UInt8Array2D normalizeBrightBackground(UInt8Array2D image,
            ScalarArray2D<?> bgImage, double lower, double upper)
    {
        int sizeX = image.size(0);
        int sizeY = image.size(1);
        UInt8Array2D result = UInt8Array2D.create(sizeX, sizeY);

        double extent = upper - lower;
        double val1, val2, res;

        // Iterate on image pixels, and choose result value depending on mask
        for (int x = 0; x < sizeX; x++)
        {
            for (int y = 0; y < sizeY; y++)
            {
                val1 = image.getValue(x, y);
                val2 = bgImage.getValue(x, y);

                // compute normalized ratio
                res = 255 * ((val1 / val2) - lower) / extent;
                res = Math.max(Math.min(res, 255), 0);

                result.setInt(x, y, (int) res);
            }
        }

        return result;
    }

    /**
     * @param args
     * @throws IOException 
     */
    public static void main(String[] args) throws IOException
    {
        System.out.println("Check current dir: " + new File(".").getCanonicalPath());
        
        // check file
        File inputFile = new File("./files/maize/6635b.jpg");
        System.out.println("  file exists: " + inputFile.exists());
        
        // Read image data
        System.out.println("Read input image");
        Image image = Image.readImage(inputFile);
        RGB8Array2D array = (RGB8Array2D) image.getData();
        
        int sizeX = array.size(0);
        int sizeY = array.size(1);
        System.out.println("  image size: " + sizeX + "x" + sizeY);
        
        UInt8Array2D arrayG8 = array.convertToUInt8();
        
        
        BinaryArray2D segStem = segmentImage_RGB8(array);

        System.out.println("Create segmented image");
        Image segStemImage = new Image(segStem);
        segStemImage.setName("Segmented");
        segStemImage.show();
        
        ScalarArray2D<?> bgFit = fitBackground(arrayG8, segStem, 2, 4);
        UInt8Array2D bgFitG8 = UInt8Array2D.wrap(UInt8Array.convert(bgFit));
        
        Image bgImage = new Image(bgFitG8);
        bgImage.setName("Background Estimate");
        bgImage.show();
//        System.out.println("Save mask image 'segStem.png'");
//        File outputFile = new File("./files/segStem.png");
//        System.out.println("  segStem output File: " + outputFile.getAbsolutePath());
//        ImageWriter writer = new ImageIOImageWriter(outputFile);
//        writer.writeImage(segStemImage);

        UInt8Array2D arrayG8N = normalizeBrightBackground(arrayG8, bgFit, 0, 1);
        Image resImageG8 = new Image(arrayG8N);
        resImageG8.setName("Normalized");
        resImageG8.show();
        
        
        RGB8Array2D normalized = normalizeBackground_RGB8(array, segStem, 2);
        Image normalizedImage = new Image(normalized);
        normalizedImage.setName("RGB8 Normalized");
        normalizedImage.show();
    }
}
