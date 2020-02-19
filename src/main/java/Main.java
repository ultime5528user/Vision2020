/*----------------------------------------------------------------------------*/
/* Copyright (c) 2018 FIRST. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

import java.util.ArrayList;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import edu.wpi.cscore.CvSink;
import edu.wpi.cscore.CvSource;
import edu.wpi.cscore.MjpegServer;
import edu.wpi.cscore.UsbCamera;
import edu.wpi.cscore.VideoMode;
import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.networktables.EntryListenerFlags;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;

public final class Main {

  public static ArrayList<UsbCamera> cameras = new ArrayList<UsbCamera>();

  public static Pipeline pipeline;

  private static int kPort = 0;
  private static int kWidth = 160;
  private static int kHeight = 120;
  private static int kFPS = 60;
  
  private static NetworkTableEntry doSyncEntry;
  private static NetworkTableEntry gotSyncEntry;

  /**
   * Main.
   */
  public static void main(String... args) {
    pipeline = new Pipeline();

    // start NetworkTables
    NetworkTableInstance ntinst = NetworkTableInstance.getDefault();
    if (Constants.server) {
      System.out.println("Setting up NetworkTables server");
      ntinst.startServer();
    } else {
      System.out.println("Setting up NetworkTables client for team " + Constants.team);
      ntinst.startClientTeam(Constants.team);
    }

    doSyncEntry = ntinst.getEntry("do_synchronize");
    gotSyncEntry = ntinst.getEntry("got_synchronize");

    UsbCamera camVision = new UsbCamera("CamVision", kPort);
    camVision.setVideoMode(VideoMode.PixelFormat.kMJPEG, kWidth, kHeight, kFPS);
    camVision.setExposureManual(10);
    camVision.getProperty("contrast").set(100);
    camVision.getProperty("saturation").set(50);
    camVision.setWhiteBalanceManual(6500);
    camVision.setBrightness(100);
    camVision.setFPS(kFPS);


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

    // Synchronization
    doSyncEntry.addListener(notif -> {
      gotSyncEntry.setDouble(gotSyncEntry.getDouble(0.0) + 1);
      ntinst.flush();
    }, EntryListenerFlags.kNew | EntryListenerFlags.kUpdate);

    NetworkTableInstance.getDefault().getTable("Vision").getEntry("pi_started").setBoolean(true);

    CvSink sourceVision =  CameraServer.getInstance().getVideo(camVision);
    CvSource outputVideoVision = CameraServer.getInstance().putVideo("OutputVision", kWidth, kHeight);
    outputVideoVision.setFPS(kFPS);
    
    MjpegServer serverVision = (MjpegServer) CameraServer.getInstance().getServer("serve_OutputVision");
    
    serverVision.setCompression(50);
    serverVision.setFPS(kFPS);

    Mat inputVision = new Mat(kHeight,kWidth,CvType.CV_8UC3);    

    while(true){
      try {
        Core.flip(inputVision, inputVision, -1);

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
