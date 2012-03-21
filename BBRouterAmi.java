//******************************************************************************
//*
//* Module:  BBRouterAmi.java
//*
//* Purpose: Implements the TIBCO Hawk Application Management Interface (AMI)
//*          for BBRouter, an Router monitoring app in Java.
//*
//*          Copyright 1998-2007 Charles Gagnon, charlesg@unixrealm.com
//*
//* CODE PROVIDED AS A SAMPLE ONLY. PLEASE DO NOT USE OR REPRODUCE.  
//*          
//******************************************************************************
package com.unixrealm.monitors;

import com.tibco.tibrv.*;
import COM.TIBCO.hawk.ami.*;
import java.util.*;
import com.unixrealm.utils.Log; /* Local logggin class */

//******************************************************************************
//* Class: BBRouterAmi
//*
//* Implements the TIBCO Hawk Application Management Interface (AMI) for the
//* BBRouter application.
//*
//******************************************************************************
public class BBRouterAmi
{

   /** Class String **/
   private static final String CLASS_STRING = "BBRouterAmi";

   /** Instance of BBRouter application being exposed via AMI.*/
   private BBRouter mBBRouter = null;

   /** Instance of AMI API session.*/
   private AmiSession  mAMI  = null;

   /** The TibRV queue */
   private TibrvQueue rvQueue;

   //***************************************************************************
   //* Method: BBRouterAmi.<init>
   //*
   //* Constructs an instance of the TIBCO Hawk Application Management Interface
   //*  (AMI) for the BBRouter application.
   //*
   //*  @param inBBRouter   Instance of BBRouter application being instrumented.
   //*  @param rvTransport RV transport for AMI communication with the Hawk agent.
   //*  @param rvQueue     RV queue for AMI communication with the Hawk agent.
   //***************************************************************************

   public BBRouterAmi(
      BBRouter inBBRouter,
      String rvService,
      String rvNetwork,
      String rvDaemon)
      throws AmiException
   {
      // Preserve BBRouter instance.
      mBBRouter = inBBRouter;

      try {

          // Open RV
          // Create a Rendezvous Rendezvous queue to be used for AMI.
          try {
                Tibrv.open();
                rvQueue = new TibrvQueue();
          } catch (TibrvException e ) {
                Log.println(Log.NOTICE, CLASS_STRING + ": Failed to open Tibrv");
                e.printStackTrace();
                System.exit(1); 
          } // try

          Thread st = new Thread(new dispatcher(), "Hawk RV Dispatcher");
          st.setDaemon(true);
          st.start();
          
          mAMI = new AmiSession( rvService, rvNetwork, rvDaemon, rvQueue,
                                 "com.unixrealm.monitors.BBRouter",
                                 "BBRouter",
                                 "AMI Interface to the BBRouter java class which monitors the state of Routers" +
                                 "using Multicast status messages.", null );

          Log.println(Log.NOTICE, CLASS_STRING + ": AMI init " + rvService + " " + rvNetwork + " " + rvDaemon);

          mAMI.addMethod ( new methodGetStatus() );

          // AMI session is itself AMI enabled. Add its AMI methods as well.
          //   getMaxThreads
          //   setMaxThreads
          mAMI.addMethods( mAMI );

          // Creates the following common methods for this Session:
          //   getReleaseVersion
          //   getTraceLevel
          //   setTraceLevel
          //   getTraceParameters
          //   setTraceParameters
          mAMI.createCommonMethods("BBRouter",
                                   "0.1._V01",
                                   "now",
                                   4,
                                   5,
                                   0);

          // Annouce our existence.
          mAMI.announce();

      } catch ( AmiException e ) {

          e.printStackTrace();

      }

    }

    public class dispatcher implements Runnable
    {
          public void run()
          {
               while( true ) {
                    try { 
                         rvQueue.dispatch(); 
                    } catch(java.lang.Throwable caughtThrowable ) { 
                         break;
                    }
               }

               try {
                    terminate();
               } catch (Exception e) {
                    Log.println(Log.NOTICE, "Error shutting down Rv queue: "+ e.toString());
               }

          }
    }


   //***************************************************************************
   //* Method: BBRouterAmi.terminate
   //*
   /** This method is used to shutdown the AMI session.
    ***************************************************************************/
   public void terminate()
     throws AmiException
   {
     mAMI.stop();
   }

   //***************************************************************************
   //* Method: BBRouterAmi.sendUnsolicitedMsg
   //*
   /** This method is used send unsolicited messages to the Hawk agent.
    ***************************************************************************/
   public void sendUnsolicitedMsg(
     AmiAlertType inType,
     String       inText,
     Integer      inID )
     throws AmiException
   {
     mAMI.sendUnsolicitedMsg( inType, inText, inID );
   }
   

   /***************************************************************************
   * Inner Class: methodGetStatus
   *
   * This class implements the BBRouterAmi getStatus AMI method.
   * Returns a table with the status of every Routers discovered.
   ***************************************************************************/
    public class methodGetStatus extends AmiMethod
    {

      /************************************************************************
      * Method: methodGetStatus.<init>
      *
      * Constructs an instance of the BBRouterAmi AMI method methodGetDelay().
      ************************************************************************/
      public methodGetStatus()
      {
         super( "getStatus",
                "This method returns the current status of all the routers discovered.",
                AmiConstants.METHOD_TYPE_INFO,
                "Router"
                );
      }


      /** This AMI method takes no arguments.*/
      public AmiParameterList getArguments()
      {
           return( null );
      }


      /************************************************************************
       * Method: methodGetStatus.getReturns
       *
       * This method returns an AMIParameterList describing the values returned
       *  by the getDelay AMI method.
       *
       *  @returns AmiParameterList describing return values for this method.
       ************************************************************************/
       public AmiParameterList getReturns()
       {
            AmiParameterList theReturns = new AmiParameterList();
            theReturns.addElement( new AmiParameter( "Router", "The Router instance name.", "string" ) );
            theReturns.addElement( new AmiParameter( "Responding", "Is the Router responding.", true ) );
            theReturns.addElement( new AmiParameter( "OrderCount", "Number of Open Orders", 0 ) );
            theReturns.addElement( new AmiParameter( "Handler0", "Handler0 Current Queue", 0 ) );
            theReturns.addElement( new AmiParameter( "Handler1", "Handler1 Current Queue", 0 ) );
            theReturns.addElement( new AmiParameter( "Handler2", "Handler2 Current Queue", 0 ) );
            theReturns.addElement( new AmiParameter( "Handler3", "Handler3 Current Queue", 0 ) );
            return( theReturns );
       }


      /************************************************************************
       * This method processes invocations of the getStatus AMI method.
       *
       *  @param inParms Parameters for method invocation.
       *  @returns AmiParameterList of return values for this method.
       ************************************************************************/
       public AmiParameterList onInvoke( AmiParameterList inParms ) throws AmiException
       {
            AmiParameterList theValues = null;

            try { 
                theValues = new AmiParameterList();
             
                Hashtable htAmiRouterStatus = mBBRouter.getStatus();

                Log.println(Log.NOTICE, CLASS_STRING + ": Responding to getStatus from Hawk.");

                Enumeration e = htAmiRouterStatus.keys();

                //for (Enumeration e = htAmiRouterStatus.keys() ; e.hasMoreElements() ; ) {
                while (e.hasMoreElements()) {
                
                     String instance = (String)e.nextElement();

                     Integer[] amistatus = (Integer[])htAmiRouterStatus.get(instance);

                     Log.println(Log.NOTICE, CLASS_STRING + "Retreiving status for " + instance + "."
                              );
                     //Log.println(Log.NOTICE, CLASS_STRING + ": Responses " + instance + " " + amistatus[3]);

                     boolean respond = (amistatus[0].equals(1)) ? true : false;

                     theValues.addElement( new AmiParameter( "Router", instance ) );
                     theValues.addElement( new AmiParameter( "Responding", respond ) );
                     theValues.addElement( new AmiParameter( "OrderCount", amistatus[1] ) );
                     theValues.addElement( new AmiParameter( "Handler0", amistatus[2] ) );
                     theValues.addElement( new AmiParameter( "Handler1", amistatus[3] ) );
                     theValues.addElement( new AmiParameter( "Handler2", amistatus[4] ) );
                     theValues.addElement( new AmiParameter( "Handler3", amistatus[5] ) );

                } //for

            } catch ( Exception caughtException ) { 
                throw( new AmiException( AmiErrors.AMI_REPLY_ERR, caughtException.getMessage() ));
            }

            return( theValues );
       }

    }

}
