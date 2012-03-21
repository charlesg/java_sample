/**
 * Title:        NagiosAgent
 * Description:  This class sends messages to a Nagios server.
 *
 * Copyright:    Copyright (c) 2010-12 Unixrealm
 * @author Charles Gagnon
 *
 * CODE PROVIDED AS A SAMPLE ONLY. PLEASE DO NOT USE OR REPRODUCE.
 *
 */

package com.unixrealm.monitors;

import java.io.*;
import com.googlecode.jsendnsca.core.*;
import com.googlecode.jsendnsca.core.builders.*;

/*
 * Local packages
 */
import com.unixrealm.utils.*;

public class NagiosAgent {

     private static final String CLASS_STRING = "NagiosAgent";

     private PropArgs myPropArgs = null;

     private NagiosPassiveCheckSender nscaSender;


     public NagiosAgent() {

          myPropArgs = new PropArgs(CLASS_STRING, null, null);

          NagiosSettings nagiosSettings = new NagiosSettingsBuilder()
                  .withNagiosHost(myPropArgs.get("nagiosServer"))
                  .withPort(myPropArgs.getInt("nagiosPort"))
                  .withConnectionTimeout(myPropArgs.getInt("connectionTimeout"))
                  .withResponseTimeout(myPropArgs.getInt("responseTimeout"))
                  .withPassword(myPropArgs.get("password"))
                  .withEncryption(myPropArgs.getInt("nagiosEncryption"))
                  .create();

          nscaSender = new NagiosPassiveCheckSender(nagiosSettings);

     }


     public void nscaSend(String host, Level level, String service, String msg) throws Exception {

         MessagePayload payload = new MessagePayloadBuilder()
                 .withHostname(host)
                 .withLevel(level)
                 .withServiceName(service)
                 .withMessage(msg)
                 .create();

         try {

             Log.println(Log.NOTICE, CLASS_STRING + ": nscaSend w/ "
                     + "host=" + host + " "
                     + "level=" + level + " "
                     + "service=" + service + " "
                     + "msg=" + msg + " "
                     + ";"
                     );

             nscaSender.send(payload);

         } catch (NagiosException e) {
             e.printStackTrace();
         } catch (IOException e) {
             e.printStackTrace();
         }

     } //nscaSend

     
} //NagiosAgent
