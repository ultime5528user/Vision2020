import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.vision.VisionPipeline;

public class Pipeline implements VisionPipeline {

    private static final Scalar kRed = new Scalar(0, 0, 255);
    private static final Scalar kGreen = new Scalar(0, 255, 0);
    private static final Scalar kBlue = new Scalar(255, 0, 0);
    private static final Scalar kYellow = new Scalar(0, 255, 255);
    private static final Scalar kPurple = new Scalar(255, 0, 255);

    private static final int kWidth = 640;
    private static final int kHeight = 480;

    private final static int kFlou = 2;
    private final static int kMedian = 3;
    private final static double kRedThreshold = 1;
    private final static double kBlueThreshold = 1;
    private final static double kThreshold = 15;
    private final static double kEpsilon = .05;
    private final static double kMinPAire = .006;
    private final static double kMinRatioAire = .2;
    private final static double kMaxRatioAire = .45;
    private final static double kMinRatioHW = 27;
    private final static double kMaxRatioHW = 62;

    public static NetworkTableInstance ntinst;
    public static NetworkTableEntry snapshotEntry;
    public static NetworkTableEntry angleEntry;

    /**
     * le format "timestamp<long>;found<boolean>;centreX<double>;hauteur<double>"
    */

    public Pipeline() {
        ntinst = NetworkTableInstance.getDefault();
        ntinst.setUpdateRate(10);

        snapshotEntry = ntinst.getTable("Vision").getEntry("Snapshot");
        angleEntry = ntinst.getTable("Vision").getEntry("RobotAngle");
    }

    @Override
    public void process(Mat in) {
        double angle = angleEntry.getNumber(0).doubleValue();
        
        // Flou
        // Gaussian
        // int flou = viewer.getNumber("flou");
        // flou = 2 * flou + 1;
        // Imgproc.GaussianBlur(img, img, new Size(flou, flou), 0, 0);

        // Box+Median
        Imgproc.medianBlur(in, in, kMedian * 2 + 1);
        Imgproc.boxFilter(in, in, -1, new Size(kFlou * 2 + 1, kFlou * 2 + 1));

        isolerVert(in, kRedThreshold, kBlueThreshold, kThreshold, in);

        List<MatOfPoint> contours = findContours(in);

        Optional<Particule> best = contours.stream().map(contour -> new Particule(contour, kEpsilon))
                .peek(particule -> drawContour(in, particule.convexHull, kGreen, 4))
                .peek(particule -> drawContour(in, particule.approxPoly, kBlue, 2))
                .peek(particule -> drawRotatedRect(in, particule.minAreaRect, kPurple, 3))
                .filter(particule -> particule.nbrCotes == 4)
                .filter(particule -> particule.convexHullArea / (double) (kWidth * kHeight) >= kMinPAire)
                .filter(particule -> particule.ratioAireContourVSConvex >= kMinRatioAire
                        && particule.ratioAireContourVSConvex <= kMaxRatioAire)
                .filter(particule -> particule.ratioHauteurLargeur >= kMinRatioHW
                        && particule.ratioHauteurLargeur <= kMaxRatioHW)
                .sorted((p1, p2) -> Double.compare(p2.height, p1.height)).findFirst();

        best.ifPresent(p -> {
            drawContour(in, p.contour, kYellow, 2);
            drawParticuleData(in, p, kRed, 2);
        });

        snapshotEntry.setString(angle + ";" + best.isPresent() + ";" + best.orElse(null).x + ";" + best.orElse(null).height);

        // draw on original and send to nt?

        // for (int i = 0; i < particules.size(); i++) {
        // Imgproc.drawContours(original, particules, i, kRed, 2);
        // }
    }

    private static void drawParticuleData(Mat image, Particule p, Scalar color, int thickness) {
        Imgproc.rectangle(image, p.rect.tl(), p.rect.br(), color, thickness);
        Imgproc.drawMarker(image, new Point((p.x + 1) / 2 * kWidth, p.rect.br().y - p.rect.height / 2), color,
                Imgproc.MARKER_CROSS, 20, thickness);
    }

    private static void drawContour(Mat image, MatOfPoint contour, Scalar color, int thickness) {
        Imgproc.drawContours(image, List.of(contour), 0, color, thickness);
    }

    private static void drawRotatedRect(Mat image, RotatedRect rect, Scalar color, int thickness) {
        Point[] vertices = new Point[4];
        rect.points(vertices);

        for (int i = 0; i < 4; i++)
            Imgproc.line(image, vertices[i], vertices[(i + 1) % 4], color, thickness);
    }

    private static void isolerVert(Mat input, double red, double blue, double threshold, Mat out) {
        ArrayList<Mat> channels = new ArrayList<>();
        Core.split(input, channels);

        Mat blueMat = channels.get(0);
        Mat greenMat = channels.get(1);
        Mat redMat = channels.get(2);

        // garder le vert seulement
        Core.addWeighted(greenMat, 1.0, redMat, -red, 0.0, out);
        Core.addWeighted(out, 1.0, blueMat, -blue, 0.0, out);

        redMat.release();
        blueMat.release();

        Core.inRange(out, new Scalar(threshold), new Scalar(255), out);
    }

    private static List<MatOfPoint> findContours(Mat input) {
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();
        int method = Imgproc.CHAIN_APPROX_SIMPLE;
        Imgproc.findContours(input, contours, hierarchy, Imgproc.RETR_LIST, method);
        return contours;
    }

    private static Point[] getConvexHullPoints(MatOfPoint contour, Point[] contourPoints) {
        MatOfInt hull = new MatOfInt();
        Imgproc.convexHull(contour, hull);
        Point[] hullPoints = new Point[hull.rows()];
        List<Integer> hullContourIdxList = hull.toList();
        for (int i = 0; i < hullContourIdxList.size(); i++) {
            hullPoints[i] = contourPoints[hullContourIdxList.get(i)];
        }

        return hullPoints;
    }

    private static MatOfPoint approxPoly(MatOfPoint2f shapeMat, double epsilon) {

        double perimeter = Imgproc.arcLength(shapeMat, true);

        MatOfPoint2f approx2f = new MatOfPoint2f();
        Imgproc.approxPolyDP(shapeMat, approx2f, epsilon * perimeter, true);

        MatOfPoint approx = new MatOfPoint();
        approx2f.convertTo(approx, CvType.CV_32S);

        return approx;
    }

    public static class Particule {

        public final double x;
        public final double height;

        public final MatOfPoint contour;
        public final Point[] contourPoints;
        public final MatOfPoint2f contour2f;

        public final MatOfPoint convexHull;
        public final Point[] convexHullPoints;

        public final MatOfPoint approxPoly;
        public final long nbrCotes;

        public final double contourArea;
        public final double convexHullArea;
        public final double ratioAireContourVSConvex;

        public final Rect rect;
        public final RotatedRect minAreaRect;
        public final double ratioHauteurLargeur;

        public Particule(MatOfPoint contour, double epsilon) {
            this.contour = contour;
            this.contourPoints = contour.toArray();
            this.contour2f = new MatOfPoint2f(contourPoints);

            this.contourArea = Imgproc.contourArea(contour);

            this.convexHullPoints = getConvexHullPoints(contour, contourPoints);
            this.convexHull = new MatOfPoint(convexHullPoints);

            Moments p = Imgproc.moments(convexHull);
            this.x = (p.get_m10() / p.get_m00()) * 2 / (double) kWidth - 1;
            // this.y = (p.get_m01() / p.get_m00());
            // centreX * 2 / (double) this.width - 1;

            this.convexHullArea = Imgproc.contourArea(convexHull);

            this.approxPoly = approxPoly(new MatOfPoint2f(convexHullPoints), epsilon);
            this.nbrCotes = this.approxPoly.total();

            this.ratioAireContourVSConvex = contourArea / convexHullArea;

            this.rect = Imgproc.boundingRect(contour2f);
            this.minAreaRect = Imgproc.minAreaRect(contour2f);

            double width;
            double height;
            if (rect.height > rect.width) {
                height = Math.max(minAreaRect.size.height, minAreaRect.size.width);
                width = Math.min(minAreaRect.size.height, minAreaRect.size.width);
            } else {
                height = Math.min(minAreaRect.size.height, minAreaRect.size.width);
                width = Math.max(minAreaRect.size.height, minAreaRect.size.width);
            }

            this.ratioHauteurLargeur = height / width;
            this.height = height / kHeight;
        }
    }
}