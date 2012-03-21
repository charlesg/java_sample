/**
 * Title:        BBAgent
 * Description:  This class sends messages to a BBDISPLAY server (http://bb4.com)
 *
 * Copyright:    Copyright (c) 2003
 * @author Charles Gagnon
 *
 * Copyright (c) 2003 Unixrealm
 *
 * CODE PROVIDED AS A SAMPLE ONLY. PLEASE DO NOT USE OR REPRODUCE.
 *
 */

package com.unixrealm.monitors;

/*
 * Version history:
 * $Id: BBAgent.java,v 1.1 2005/12/16 16:12:05 charlesg Exp $
 *
 * 11-20-03 Created first copy of Class
 *
 */

import java.net.Socket;
import java.net.InetAddress;
import java.io.*;
import java.util.Properties;

/*
 * Local packages
 */
import com.unixrealm.rv.*;    /* Local TIBCO class */
import com.unixrealm.utils.*; /* Local utilities class */
import com.unixrealm.multi.*; /* Local multicast class */

public class BBAgent {

     final int GREEN=1;

     final int YELLOW=2;

     final int PURPLE=3;

     final int RED=4;

     final int BLUE=5;

     final String[] colorDesc={"","green","yellow","purple","red","blue"};

     private static final String CLASS_STRING = "BBAgent";
     
     private PropArgs myPropArgs = null;

     
     public BBAgent() {

          myPropArgs = new PropArgs(CLASS_STRING, null, null);

     }


     public void bbsend(String ip, String data,String port) throws Exception {
     
          InetAddress ipAddress=InetAddress.getByName(ip);

          int portno = (new Integer(port)).intValue();

          Socket sendSocket = new Socket(ipAddress, portno);

          sendSocket.setSoTimeout(3000);

          OutputStreamWriter outs=new OutputStreamWriter(sendSocket.getOutputStream());

          outs.write(data);

          //debug("Send returned: %d\n",n);

          outs.close();

          sendSocket.close();

     } //bbsend


     public void bbsendstr(String statmsg) throws Exception {

          bbsend(myPropArgs.get("BBdisplay","none"),statmsg,myPropArgs.get("BBdisplayPort","1984"));

     } //bbsendstr

     
     public void bbnotify(String action1,int color,String action2) throws Exception {

          bbsend(myPropArgs.get("BBdisplay","none"),action1+" "+colorDesc[color]+" "+action2,myPropArgs.get("BBdisplayPort","1984"));

          if (myPropArgs.get("PageLevels","red purple").indexOf(colorDesc[color])>=0) {

                bbsend(myPropArgs.get("BBpager"),"page "+action1+" "+colorDesc[color]+" "+action2,myPropArgs.get("BBpagerPort","1984"));

          } //

     } //bbnotify

} //BBAgent
