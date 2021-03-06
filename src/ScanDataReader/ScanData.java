package ScanDataReader;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;

import java.io.*;
import java.util.ArrayList;

/**
 * Created by lahmann on 2016-12-08.
 */
public class ScanData {

    private File scanFile;

    private Integer versionNumber = -104;
    private Integer numberOfBins = 0;
    private Float pixelSize = (float) 3.27e-5;
    private Float pixelsPerBin = (float) 0.5;

    private Integer numXFrames;
    private Integer numYFrames;
    private Float   frameWidth;
    private Float   frameHeight;

    private Integer borderLimit = 85;
    private Integer contrastLimit = 75;
    private Integer eccentricityLimit = 40;
    private Integer M = 2;

    private String trailer;

    public Frame[][] frames;       // [yIndex][xIndex] ([0][0] -> Top left corner)



    // *************************************
    // Constructors using a pre written file
    // *************************************

    public ScanData(String filename) {
        this(new File(filename));
    }

    public ScanData(File file) {
        this.scanFile = file;

        // Attempt to build the file reader
        ScanFileReader reader;
        try {
            reader = new ScanFileReader(file);

            // Try to read the file
            try {

                // Read the header portion of the file
                readHeader(reader);

                // Keep reading frames until the bottom corner frames are filled (scans always start from the top-left)
                while (frames[numYFrames - 1][0] == null || frames[numYFrames - 1][numXFrames - 1] == null) {
                    Frame frame = reader.getNextFrame(this.frameWidth, this.frameHeight, this.pixelSize);
                    frames[frame.getyPositionIndex()][frame.getxPositionIndex()] = frame;
                }

                // Read the trailer portion of the file
                readTrailer(reader);
            }

            // We had an issue while reading the file (but after opening it)
            catch (IOException e) {
                e.printStackTrace();
                System.exit(-1);
            }

            // Close the file reader
            finally {
                reader.close();
            }
        }

        // We had an issue building the file reader
        catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }


    // *******************************
    // Header and trailer read methods
    // *******************************

    private void readHeader(ScanFileReader reader) throws IOException{
        this.versionNumber = reader.getNextInteger();

        this.numXFrames = reader.getNextInteger();
        this.numYFrames = reader.getNextInteger();

        this.frames = new Frame[numYFrames][numXFrames];

        this.numberOfBins = reader.getNextInteger();
        this.pixelSize = (float) 1e-4 * reader.getNextFloat();      // um -> cm
        this.pixelsPerBin = reader.getNextFloat();

        this.borderLimit = reader.getNextInteger();
        this.contrastLimit = reader.getNextInteger();
        this.eccentricityLimit = reader.getNextInteger();
        this.M = reader.getNextInteger();

        this.frameWidth  = pixelSize * reader.getNextInteger();      // Convert from pixels to cm
        this.frameHeight = pixelSize * reader.getNextInteger();      // Convert from pixels to cm
    }

    private void readTrailer(ScanFileReader reader) throws IOException{
        reader.getNextInteger();        // Need to skip the -1 at the end

        this.trailer = new String();
        while (reader.inputStream.available() > 0){
            trailer += (char) (short) reader.getNextByte();
        }
    }


    // **************************************************
    // Constructor using Frame Objects (for internal use)
    // **************************************************

    public ScanData(Frame[][] frames){
        this.frameHeight = Math.abs(frames[0][0].getyPosition() - frames[1][0].getyPosition());
        this.frameWidth  = Math.abs(frames[0][1].getxPosition() - frames[0][0].getxPosition());

        this.frames = frames;
    }


    // ***********************
    // Write scan file methods
    // ***********************

    public void writeToFile() {
        writeToFile(scanFile);
    }

    public void writeToFile(File file) {
        this.scanFile = file;

        // Attempt to build the file writer
        ScanFileWriter writer;
        try {
            writer = new ScanFileWriter(file);

            // Try to write the file
            try {

                // Write the header portion to the file
                writeHeader(writer);

                // Loop through and write all of the frame data to the file
                for (int yPosIndex = 0; yPosIndex < frames.length; yPosIndex++) {
                    for (int xPosIndex = 0; xPosIndex < frames[yPosIndex].length; xPosIndex++) {

                        // We have to imitate the "sweeping" ordering of the scan systems to be compatible with Fredrick's analysis code
                        if (yPosIndex % 2 == 0) {
                            // Left to right
                            writer.writeFrame(frames[yPosIndex][xPosIndex], frameWidth, frameHeight, pixelSize);
                        } else {
                            // Right to left
                            writer.writeFrame(frames[yPosIndex][frames[yPosIndex].length - 1 - xPosIndex], frameWidth, frameHeight, pixelSize);
                        }

                    }
                }

                // Write the trailer portion to the file
                writeTrailer(writer);
            }

            // We had an issue writing the file (after opening it)
            catch (IOException e){
                e.printStackTrace();
                System.exit(-1);
            }

            // Close the file writer
            finally {
                writer.close();
            }


        }

        // We had a problem building the file writer
        catch (IOException e){
            e.printStackTrace();
            System.exit(-1);
        }
    }


    // ********************************
    // Header and trailer write methods
    // ********************************

    private void writeHeader(ScanFileWriter writer) throws IOException{
        writer.writeInteger(this.versionNumber);

        writer.writeInteger(this.frames[0].length);
        writer.writeInteger(this.frames.length);

        writer.writeInteger(this.numberOfBins);
        writer.writeFloat(1e4f * this.pixelSize);
        writer.writeFloat(pixelsPerBin);

        writer.writeInteger(this.borderLimit);
        writer.writeInteger(this.contrastLimit);
        writer.writeInteger(this.eccentricityLimit);
        writer.writeInteger(this.M);

        writer.writeInteger((int) (this.frameWidth  / this.pixelSize));
        writer.writeInteger((int) (this.frameHeight / this.pixelSize));
    }

    private void writeTrailer(ScanFileWriter writer) throws IOException{
        writer.writeInteger(-1);
    }


    // ***************************************
    // Translate / rotate manipulation methods
    // ***************************************

    public void rotate(double angleInDegrees){
        double angle = Math.toRadians(angleInDegrees);

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;

        /**
         * Rotate all of the tracks in this scan file
         * and identify the new bounds for our scan file
         */
        Track[] tracks = getTracks();
        for (Track track : tracks){

            double oldX = track.getxPosition();
            double oldY = track.getyPosition();

            double x = oldX * Math.cos(angle) - oldY * Math.sin(angle);
            double y = oldX * Math.sin(angle) + oldY * Math.cos(angle);

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);

            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);

            track.setxPosition((float) x);
            track.setyPosition((float) y);
        }

        /**
         * Create a new grid of frames that'll fit these bounds
         * while maintaining the old frameHeight and frameWidth
         */
        int numX = (int) Math.ceil((maxX - minX) / frameWidth);
        int numY = (int) Math.ceil((maxY - minY) / frameHeight);
        frames = new Frame[numY][numX];

        double dx = numX * frameWidth  - (maxX - minX);     // Increase in x dim
        double dy = numY * frameHeight - (maxY - minY);     // Increase in y dim

        minX -= 0.5*dx;
        maxX += 0.5*dx;
        minY -= 0.5*dy;
        maxY += 0.5*dy;

        int number = 0;
        for (int i = 0; i < frames.length; i++){
            for (int j = 0; j < frames[i].length; j++){
                number++;

                /**
                 * We have to populate the frames "as scanned" to
                 * be consistent with Fredrick's code
                 */
                int xIndex;
                int yIndex = i;
                if (i % 2 == 0) xIndex = j;
                else            xIndex = numX - j - 1;


                double x = minX + (xIndex + 0.5) * frameWidth;
                double y = maxY - (yIndex + 0.5) * frameHeight;


                Frame frame = new Frame();
                frame.setNumber(number);
                frame.setxPosition((float) x);
                frame.setyPosition((float) y);
                frame.setxPositionIndex(xIndex);
                frame.setyPositionIndex(yIndex);

                frame.setFocus(0.0);                // Fake value
                frame.setFrameWidth(frameWidth);
                frame.setFrameHeight(frameHeight);
                frame.setPixelSize(pixelSize);
                frames[yIndex][xIndex] = frame;
            }
        }


        /**
         * Distribute tracks into new frame grid
         */
        for (Track track : tracks){
            double x = track.getxPosition();
            double y = track.getyPosition();

            int xIndex = (int) Math.floor(numX * (x - minX) / (maxX - minX));
            int yIndex = (int) Math.floor(numY * (maxY - y) / (maxY - minY));

            frames[yIndex][xIndex].addTrack(track);
        }
    }


    // *****************
    // Get Frame methods
    // *****************

    public Double getFrameArea(){
        return (double) this.frameHeight*this.frameWidth;
    }

    public Frame[][] getFrames(AnalysisCuts cuts){
        int maxYIndex = 0, minYIndex = (frames.length-1);           // Note the reverse in x and y is because 0,0 is top left (as opposed to bottom left)

        int minXIndex = 0, maxXIndex = (frames[0].length - 1);

        if (cuts.isIndexMode()){

            if (cuts.isUseYLimits()) {
                maxYIndex = (frames.length - 1) - (int) cuts.getMaxY();       // Flip it
                minYIndex = (frames.length - 1) - (int) cuts.getMinY();
            }

            if (cuts.isUseXLimits()) {
                minXIndex = (int) cuts.getMinX();
                maxXIndex = (int) cuts.getMaxX();



            }
        }

        // We need to determine the indexes using physical units
        else {
            if (cuts.isUseYLimits()) {
                for (int i = 0; i < frames.length; i++) {
                    if (frames[i][0].getyPosition() < cuts.getMinY()) minYIndex--;
                    if (frames[i][0].getyPosition() > cuts.getMaxY()) maxYIndex++;
                }
            }

            if (cuts.isUseXLimits()) {
                for (int j = 0; j < frames[0].length; j++) {
                    if (frames[0][j].getxPosition() < cuts.getMinX()) minXIndex++;
                    if (frames[0][j].getxPosition() > cuts.getMaxX()) maxXIndex--;
                }
            }
        }

        int Ny = minYIndex - maxYIndex + 1;

        int Nx = maxXIndex - minXIndex + 1;
        Frame[][] filteredFrames = new Frame[Ny][Nx];
        for (int i = 0; i < Ny; i++){
            int oldI = i + maxYIndex;

            for (int j = 0; j < Nx; j++){
                int oldJ = j + minXIndex;
                filteredFrames[i][j] = frames[oldI][oldJ];
            }
        }
        return filteredFrames;
    }

    public Frame[][] getFrames(){
        return frames;
    }


    // *****************
    // Get Track methods
    // *****************

    public Track[] getTracks(AnalysisCuts cuts){
        ArrayList<Track> tracks = new ArrayList<>();

        Frame[][] frames = getFrames(cuts);
        for (int i = 0; i < frames.length; i++){
            for (int j = 0; j < frames[0].length; j++){
                for (Track track : frames[i][j].getTracks(cuts)){
                    tracks.add(track);
                }
            }
        }

        Track[] array = new Track[tracks.size()];
        for (int i = 0; i < tracks.size(); i++){
            array[i] = tracks.get(i);
        }

        return array;
    }

    public Track[] getTracks(){
        ArrayList<Track> tracks = new ArrayList<>();

        for (int i = 0; i < frames.length; i++){
            for (int j = 0; j < frames[0].length; j++){
                for (Track track : frames[i][j].getTracks()){
                    tracks.add(track);
                }
            }
        }

        Track[] array = new Track[tracks.size()];
        for (int i = 0; i < tracks.size(); i++){
            array[i] = tracks.get(i);
        }

        return array;
    }


    // ***********************
    // Get binned data methods
    // ***********************

    public XYZData getNxy(AnalysisCuts cuts) throws Exception{

        Frame[][] frames = this.getFrames(cuts);
        double[][] totalTracks = new double[frames.length][frames[0].length];
        double[] xPositions = new double[frames[0].length];
        double[] yPositions = new double[frames.length];


        for (int i = 0; i < frames[0].length; i++)
            xPositions[i] = frames[0][i].getxPosition();

        for (int i = 0; i < frames.length; i++)
            yPositions[i] = frames[i][0].getyPosition();



        for (int i = 0; i < frames.length; i++){
            for (int j = 0; j < frames[i].length; j++){
                totalTracks[i][j] = frames[i][j].getTracks(cuts).size();
            }
        }

        XYZData data = new XYZData();
        data.xData = xPositions;
        data.yData = yPositions;
        data.zData = totalTracks;

        return data;
    }

    public XYZData getDxy(AnalysisCuts cuts) throws Exception{

        Frame[][] frames = this.getFrames(cuts);
        double[][] averageDiameter = new double[frames.length][frames[0].length];
        double[] xPositions = new double[frames[0].length];
        double[] yPositions = new double[frames.length];


        for (int i = 0; i < frames[0].length; i++)
            xPositions[i] = frames[0][i].getxPosition();

        for (int i = 0; i < frames.length; i++)
            yPositions[i] = frames[i][0].getyPosition();



        for (int i = 0; i < frames.length; i++){
            for (int j = 0; j < frames[i].length; j++){
                ArrayList<Track> tracks = frames[i][j].getTracks(cuts);
                for (Track track : tracks){
                    averageDiameter[i][j] += track.getDiameter();
                }
                if (tracks.size() != 0) {
                    averageDiameter[i][j] /= tracks.size();
                }
            }
        }

        XYZData data = new XYZData();
        data.xData = xPositions;
        data.yData = yPositions;
        data.zData = averageDiameter;

        return data;
    }

    public XYZData getFluenceMap(AnalysisCuts cuts) throws Exception{

        Frame[][] frames = this.getFrames(cuts);
        double[][] trackFluence = new double[frames.length][frames[0].length];
        double[] xPositions = new double[frames[0].length];
        double[] yPositions = new double[frames.length];

        for (int i = 0; i < frames.length; i++){
            yPositions[i] = frames[i][0].getyPosition();
            for (int j = 0; j < frames[i].length; j++){
                trackFluence[i][j] = frames[i][j].getTracks(cuts).size() / getFrameArea();
                xPositions[j] = frames[i][j].getxPosition();
            }
        }

        XYZData data = new XYZData();
        data.xData = xPositions;
        data.yData = yPositions;
        data.zData = trackFluence;

        return data;
    }

    public XYZData getNcd(AnalysisCuts cuts) throws Exception{

        final int numContrastBins = cuts.getMaxContrast() + 1;
        final int numDiameterBins = 101;

        Track[] tracks           = getTracks(cuts);

        /**
         * Create the contrast bins
         */
        int minC = cuts.getMinContrast();
        int maxC = cuts.getMaxContrast();
        double[] contrastCuts = new double[numContrastBins];
        for (int i = 0; i < contrastCuts.length; i++){
            double fraction = i / (double) (numContrastBins - 1);
            contrastCuts[i] = minC + (maxC - minC) * fraction;
        }


        /**
         * Create the diameter bins
         */
        double minD = cuts.getMinDiameter();
        double maxD = cuts.getMaxDiameter();
        double[] diameterCuts = new double[numDiameterBins];
        for (int i = 0; i < diameterCuts.length; i++){
            double fraction = i / (double) (numDiameterBins - 1);
            diameterCuts[i] = minD + (maxD - minD) * fraction;
        }


        /**
         * Sort all of the tracks
         */
        double[][] totalTracks = new double[contrastCuts.length][diameterCuts.length];
        for (Track track : tracks){
            int c = track.getContrast();
            int contrastIndex = Math.floorDiv((numContrastBins-1) * (c - minC), (maxC - minC));

            double d = track.getDiameter();
            int diameterIndex = (int) Math.floor((numDiameterBins-1) * (d - minD) / (maxD - minD));

            totalTracks[contrastIndex][diameterIndex]++;
        }

        XYZData data = new XYZData();
        data.xData = diameterCuts;
        data.yData = contrastCuts;
        data.zData = totalTracks;

        return data;
    }

    public XYZData getNxd(AnalysisCuts cuts, AnalysisCuts backgroundCuts) throws Exception{

        final int numXBins = 100;
        final double[] xBounds = new double[] {-3.0, 3.0};

        final int numDBins = 100;
        final double[] dBounds = {0.0, 30.0};


        // Get the tracks
        Track[] tracks   = getTracks(cuts);
        Track[] bgTracks = getTracks(backgroundCuts);


        // Create the xNodes
        double[] xNodes = new double[numXBins + 1];
        double[] xBins  = new double[numXBins];
        for (int i = 0; i < xNodes.length; i++){
            double fraction = i / (double) (xNodes.length - 1);
            xNodes[i] = xBounds[0] + (xBounds[1] - xBounds[0]) * fraction;

            if (i > 0)  xBins[i-1] = 0.5*(xNodes[i] + xNodes[i-1]);
        }


        // Create the dNodes
        double[] dNodes = new double[numDBins + 1];
        double[] dBins  = new double[numDBins];
        for (int i = 0; i < dNodes.length; i++){
            double fraction = i / (double) (dNodes.length - 1);
            dNodes[i] = dBounds[0] + (dBounds[1] - dBounds[0]) * fraction;

            if (i > 0)  dBins[i-1] = 0.5*(dNodes[i] + dNodes[i-1]);
        }


        // Get the average background levels
        double[] Nd_background = new double[dBins.length];
        for (Track track : bgTracks){
            double d = track.getDiameter();
            int dIndex = (int) Math.floor((numDBins-1) * (d - dBounds[0]) / (dBounds[1] - dBounds[0]));

            try {
                Nd_background[dIndex]++;
            } catch (IndexOutOfBoundsException e){

            }
        }


        // Sort all off the tracks
        double[][] Nxd = new double[dBins.length][xBins.length];
        for (Track track : tracks){
            double x = track.getxPosition();
            int xIndex = (int) Math.floor(xBins.length * (x - xBounds[0]) / (xBounds[1] - xBounds[0]));

            double d = track.getDiameter();
            int dIndex = (int) Math.floor((numDBins-1) * (d - dBounds[0]) / (dBounds[1] - dBounds[0]));

            try {
                Nxd[dIndex][xIndex]++;
            } catch (IndexOutOfBoundsException e){

            }
        }


        // Background subtract
        double scaleFactor = xBins[1] - xBins[0];
        scaleFactor /= (backgroundCuts.getMaxX() - backgroundCuts.getMinX());
        for (int i = 0; i < dBins.length; i++){
            for (int j = 0; j < xBins.length; j++){
                Nxd[i][j] -= Nd_background[i] * scaleFactor;
            }
        }

        XYZData data = new XYZData();
        data.xData = xBins;
        data.yData = dBins;
        data.zData = Nxd;

        return data;

    }



    /**
     * Built in data classes (glorified structs) for storing data
     */
    public class XYData{
        public double[] xData;
        public double[] yData;
    }

    public class XYZData{
        public double[] xData;
        public double[] yData;
        public double[][] zData;
    }

}
