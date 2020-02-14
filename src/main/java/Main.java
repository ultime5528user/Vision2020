/*----------------------------------------------------------------------------*/
/* Copyright (c) 2018 FIRST. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

import java.util.ArrayList;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import edu.wpi.cscore.CvSink;
import edu.wpi.cscore.CvSource;
import edu.wpi.cscore.MjpegServer;
import edu.wpi.cscore.UsbCamera;
import edu.wpi.cscore.VideoMode;
import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;

public final class Main {

  public static ArrayList<UsbCamera> cameras = new ArrayList<UsbCamera>();

  public static Pipeline pipeline;

  private Main() {
    pipeline = new Pipeline();
  }

  /**
   * Main.
   */
  public static void main(String... args) {
    // start NetworkTables
    NetworkTableInstance ntinst = NetworkTableInstance.getDefault();
    if (Constants.server) {
      System.out.println("Setting up NetworkTables server");
      ntinst.startServer();
    } else {
      System.out.println("Setting up NetworkTables client for team " + Constants.team);
      ntinst.startClientTeam(Constants.team);
    }

    UsbCamera camVision = new UsbCamera("CamVision", K.VISION_CAMERA_PORT);
    camVision.setVideoMode(VideoMode.PixelFormat.kMJPEG, K.WIDTH_VISION, K.HEIGHT_VISION, K.VISION_FPS);
    camVision.setExposureManual(0);
    camVision.getProperty("contrast").set(100);
    camVision.getProperty("saturation").set(50);
    camVision.setWhiteBalanceManual(6500);
    camVision.setBrightness(0);
    // camVision.setFPS(K.VISION_FPS);


    // Tentative de connexion...

    NetworkTableEntry startVisionEntry = NetworkTableInstance.getDefault().getTable("Vision").getEntry("START_VISION");
    int i = 0;
    while(!startVisionEntry.getBoolean(false)) {
      i++;
      System.out.println("Tentative de connexion #" + i + "...");
      try {
        Thread.sleep(1000);
      } catch (Exception e) { }

      if(i == 5)
        throw new RuntimeException("Impossible de se connecter. Redémarrage...");
    }
    

    CvSink sourceVision =  CameraServer.getInstance().getVideo(camVision);
    CvSource outputVideoVision = CameraServer.getInstance().putVideo("OutputVision", K.WIDTH_VISION, K.HEIGHT_VISION);
    outputVideoVision.setFPS(K.VISION_FPS);
    
    MjpegServer serverVision = (MjpegServer) CameraServer.getInstance().getServer("serve_OutputVision");
    
    serverVision.setCompression(50);
    serverVision.setFPS(K.VISION_FPS);

    Mat inputVision = new Mat(K.HEIGHT,K.WIDTH,CvType.CV_8UC3);    

    while(true){
      try {

        //obtenir l'image des caméras
        sourceVision.grabFrame(inputVision);

        //traiter l'image de la vision
        pipeline.process(inputVision);

        // if(pipeline.pause) {
        //   long time = System.currentTimeMillis();
        //   while(System.currentTimeMillis() - time <= 4000) {

        //     outputVideoVision.putFrame(inputVision);
        //   }
        //   pipeline.pause = false;
        // }

        //afficher l'image
        outputVideoVision.putFrame(inputVision);

      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
