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
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;

public final class Main {

  public static ArrayList<UsbCamera> cameras = new ArrayList<UsbCamera>();

  public static Pipeline pipeline;

  // vision
  private static int kPortVision = 0;
  public static int kWidth = 320;
  public static int kHeight = 240;
  private static int kFPS = 60;
  private static int kCompression = 50;

  // pilote
  private static int kPortPilote = 1;
  private static int kWidthPilote = 960;
  private static int kHeightPilote = 540;
  private static int kFPSPilote = 60;
  private static int kCompressionPilote = 50;

  private static NetworkTableEntry doSyncEntry;
  private static NetworkTableEntry gotSyncEntry;

  public static void main2(String... args) {
    CameraServer.getInstance().startAutomaticCapture(kPortPilote);
    while (true) {
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

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

    NetworkTable visionTable = ntinst.getTable("vision");

    doSyncEntry = visionTable.getEntry("do_synchronize");
    gotSyncEntry = visionTable.getEntry("got_synchronize");

    UsbCamera camVision = new UsbCamera("CamVision", kPortVision);
    camVision.setVideoMode(VideoMode.PixelFormat.kMJPEG, kWidth, kHeight, kFPS);
    camVision.setExposureManual(0);
    camVision.getProperty("contrast").set(100);
    camVision.getProperty("saturation").set(50);
    camVision.setWhiteBalanceManual(6500);
    camVision.setBrightness(0);
    camVision.setFPS(kFPS);

    // CameraServer.getInstance().startAutomaticCapture(kPortPilote);
    // startCameraPilote();


    // Tentative de connexion...
    NetworkTableEntry startVisionEntry = visionTable.getEntry("start_vision");
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
      System.out.println("LISTENER CREATED!!!!");
      gotSyncEntry.setDouble(gotSyncEntry.getDouble(0.0) + 1);
      ntinst.flush();
    }, EntryListenerFlags.kNew | EntryListenerFlags.kUpdate);

    visionTable.getEntry("pi_started").setBoolean(true);

    CvSink sourceVision =  CameraServer.getInstance().getVideo(camVision);
    CvSource outputVideoVision = CameraServer.getInstance().putVideo("OutputVision", kWidth, kHeight);
    outputVideoVision.setFPS(kFPS);

    MjpegServer serverVision = (MjpegServer) CameraServer.getInstance().getServer("serve_OutputVision");
    serverVision.setCompression(kCompression); //50
    serverVision.setFPS(kFPS);

    Mat inputVision = new Mat(kHeight,kWidth,CvType.CV_8UC3);    
    while(true){
      try {
        //obtenir l'image pour la vision
        sourceVision.grabFrame(inputVision);

        Core.flip(inputVision, inputVision, -1);

        //traiter l'image de la vision
        pipeline.process(inputVision);

        //afficher l'image
        outputVideoVision.putFrame(inputVision);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private static void startCameraPilote() {
    UsbCamera camPilote = new UsbCamera("CamPilote", kPortPilote);
    camPilote.setVideoMode(VideoMode.PixelFormat.kMJPEG, kWidthPilote, kHeightPilote, kFPSPilote);
    camPilote.setFPS(kFPSPilote);
    
    CvSink sourcePilote =  CameraServer.getInstance().getVideo(camPilote);
    CvSource outputVideoPilote = CameraServer.getInstance().putVideo("OutputPilote", kWidthPilote, kHeightPilote);
    outputVideoPilote.setFPS(kFPSPilote);

    MjpegServer serverPilote = (MjpegServer) CameraServer.getInstance().getServer("serve_OutputPilote");
    serverPilote.setCompression(kCompressionPilote);
    serverPilote.setFPS(kFPS);

    Mat outputPilote = new Mat(kHeightPilote,kWidthPilote,CvType.CV_8UC3);    
    new Thread( () -> {
      while(!Thread.currentThread().isInterrupted()){
        sourcePilote.grabFrame(outputPilote);
        outputVideoPilote.putFrame(outputPilote);
      }
    } ).start();
  }
}