/**
 * Title:        MultiCastMessageQueue
 * Description:  This is a multicast message queue designed to hold multicast messages
 *               until they can be processed
 *
 * Copyright:    Copyright (c) 1997-2012
 *
 * Copyright (c) 1997-2012 Unixrealm
 * All rights reserved.
 *
 * CODE PROVIDED AS A SAMPLE ONLY. PLEASE DO NOT USE OR REPRODUCE.
 *
 */

package com.unixrealm.monitors;

import java.util.*;
import java.lang.*;

import com.unixrealm.multi.*;
import com.unixrealm.utils.*;


public class BBRouterMMQ implements Runnable
{
    public final static String CLASS_NAME = "MultiCastMessageQueue";

    private BBRouter myBBRouter = null;

    private Vector pendingMessage = null;


    public BBRouterMMQ()
    {
       pendingMessage = new Vector(100);

       Thread delayThread = new Thread(this, CLASS_NAME);
       delayThread.setDaemon(true);
       delayThread.start();
    }


    public String getVersion()
    {
       return this.getClass() + ": $Id: BBRouterMMQ.java,v 1.1 2008/04/28 19:06:14 charlesg Exp $";
    }


    public void setBBRouter(BBRouter bbrouter)
    {
       myBBRouter = bbrouter;
    }


    public BBRouter getBBRouter()
    {
        return myBBRouter;
    }
    
    public boolean isEmpty()
    {
       return (pendingMessage.size() < 2);
    }


    public void addMessage(MultiCastMessage mcm)
    {
       synchronized (pendingMessage)
       {
          pendingMessage.addElement(mcm);
       }
    }


    //
    // this is the delayed cancel checker
    //
    public void run()
    {
       int sleepTime = PropArgs.StaticPropArgs.getInt("DefaultThreadSleep", 100);

       while (true)
       {
          try
          {
             Thread.currentThread().yield();
             Thread.currentThread().sleep(sleepTime);
          }
          catch(Exception e)
          {
             // barf - discard
          }

          while (pendingMessage.size() > 0)
          {
             synchronized (pendingMessage)
             {
                processMessage((MultiCastMessage)pendingMessage.firstElement());
                pendingMessage.removeElementAt(0);
             }
          }
       }
    }


    private void processMessage(MultiCastMessage mcm)
    {
       try
       {
          myBBRouter.processMultiCastMessage(mcm);
       }
       catch (Exception e)
       {
          //  Something's wrong here
          Log.println(CLASS_NAME + ": processing multicast message '" + mcm.toString() + "' :: " + e.toString());
       }
    }
}
