/**
 * Title:        BBRouter
 * Description:  This application will monitor Routers using Multicast
 *               send reponses to the BBDISPLAY server.
 *
 * @author Charles Gagnon
 *
 * Copyright (c) 2003-2012 Unixrealm
 *
 * CODE PROVIDED AS A SAMPLE ONLY. PLEASE DO NOT USE OR REPRODUCE.
 *
 */

package com.unixrealm.monitors;

import java.util.*;
import java.text.DateFormat;

/*
 * Local packages
 */
import com.unixrealm.utils.*;
import com.unixrealm.multi.*;
/*
 * For the Router stuff
 */
import com.unixrealm.servers.routeorder.RouteOrderCommandType;
import com.unixrealm.servers.smartrouter.SmartRouterCommandType;
/*
 * For RV
 */
import com.tibco.tibrv.*;
import COM.TIBCO.hawk.ami.*;

import com.googlecode.jsendnsca.core.*;
import java.util.regex.MatchResult;

public class BBRouter {

    private static final String CLASS_STRING = "BBRouter";

    // The PropArgs Object
    private PropArgs myPropArgs = new PropArgs(CLASS_STRING, null, null);

    // Transport for AMI
    private TibrvRvdTransport rvTransport = null;

    /** Rendezvous queue for AMI */
    private TibrvQueue mRvQueue = null;

    // The AMI interface that communicated with Hawk
    private BBRouterAmi mBBRouterAmi = null;

    // The loop that sends out multicast every 4 1/2 minutes
    private Thread _multicastSend;

    // The hash where the Router status is stored
    private Hashtable<String, String> htRouterStatus = new Hashtable<String, String>();
    private Hashtable<String, Integer[]> htAmiRouterStatus = new Hashtable<String, Integer[]>();

    // My router status objects
    private Hashtable<String, RouterInstance> htRouterInstances = new Hashtable<String, RouterInstance>();
    
    // The BBAgent Object
    private BBAgent sendObject = new BBAgent();

    // The Nagios Agent
    private NagiosAgent nscaSend = new NagiosAgent();

    // The multicast dispatcher and the queue
    MultiCastDispatcher   myMCD;
    BBRouterMMQ myMCMQ;

    // Delay between each update - 4min30sec during tests.
    //private static final long SLEEPDELAY = 270000;   // 4.5 minutes in milliseconds
    private final long SLEEPDELAY = myPropArgs.getInt("SleepDelay");

    // Delay between each query
    //private static final long QUERYSPACER = 100;   // 1/10th second
    private final long QUERYSPACER = myPropArgs.getInt("QuerySpacer");

    // Timeout is how long we wait for responses from the Router
    //private static final long TIMEOUT = 15000;   // 15 seconds
    private final long TIMEOUT = myPropArgs.getInt("Timeout");

    // The bullets used in reports
    //private static final String GREENBULLET = "<img src=/bb/gifs/green.gif>";
    //private static final String REDBULLET = "<img src=/bb/gifs/red.gif>";
    //private static final String YELLOWBULLET = "<img src=/bb/gifs/yellow.gif>";
    private final String GREENBULLET = myPropArgs.get("GreenBullet");
    private final String REDBULLET = myPropArgs.get("RedBullet");
    private final String YELLOWBULLET = myPropArgs.get("YellowBUllet");

    // Get the marketopen and marketclose times for this app
    private final String OPENHOUR = myPropArgs.get("OpenHour");
    private final String OPENMINUTE = myPropArgs.get("OpenMinute");
    private final String CLOSEHOUR = myPropArgs.get("CloseHour");
    private final String CLOSEMINUTE = myPropArgs.get("CloseMinute");

    // Misc Variables
    boolean testingOpen;// Boolean to know if testing hours are open or closed.
    String color;       // BB status color (red, green or yellow)
    boolean loop;       // Loop variable
    String mcIP;        // Multicast IP
    int mcPort;         // Multicast Port
    int mcMaxK;         // Multicast Maximum message size
    String status;      // BB Status message
    String routersList;         // Regular routers only
    String smartRoutersList;    // Smart routers only
    String allRoutersList;      // Combined smart and regular routers
    String reportDate;  // BB date of reported message


    //***************************************************************************
    //*                          Accessor Methods       
    //***************************************************************************
    //* Returns Router Status table 
    //*
    public Hashtable getStatus() { 

         
        String Output = htAmiRouterStatus.toString();

        // Log.println(Log.NOTICE, CLASS_STRING + ": Will return the htAmiRouterStatus: " + Output);

        return htAmiRouterStatus;

    } 

    public BBRouter() {

        try {

            Log.println(Log.NOTICE, "Starting BBRouter $Revision: 1.7 $.");
            Log.println(Log.NOTICE, "Initializing RV.");

            // See if the market is open or not.
            testingOpen = checkTestingHours(); 

            if ( testingOpen ) {
                Log.println(Log.NOTICE, CLASS_STRING + ": Testing is ON" );
            } else {
                Log.println(Log.NOTICE, CLASS_STRING + ": Testing is OFF (outside of testing hours)" );
            }

            // Let's setup the AMI
            String amiRvService = myPropArgs.get("ami.rvService", null);
            String amiRvDaemon = myPropArgs.get("ami.rvDaemon", null);
            String amiRvNetwork = myPropArgs.get("ami.rvNetwork", null);

            Log.println(Log.NOTICE, CLASS_STRING + ": Will setup RV AMI transport with Service="
                        + amiRvService +" Daemon=" + amiRvDaemon +
                        " and Network=" + amiRvNetwork );

            // Create an AMI API session.
            try {
               Log.print(Log.NOTICE, CLASS_STRING + ": Calling BBRouterAmi with " + amiRvService + " " + amiRvNetwork + " " + amiRvDaemon );
               mBBRouterAmi = new BBRouterAmi( this, amiRvService, amiRvNetwork, amiRvDaemon);

            } catch ( AmiException e) {

               Log.println(Log.NOTICE, CLASS_STRING + ": Failed to create AMI Session");
               e.printStackTrace();
               System.exit(1); 

            } //try

            Log.println(Log.NOTICE, CLASS_STRING + ": AMI ready.");

            //
            //  Let's start the MultiCastDispatcher first so we can get the panic button commands
            //
            mcIP = myPropArgs.get("MultiCastDispatcher.defaultIP");
            mcPort = myPropArgs.getInt("MultiCastDispatcher.defaultPort", -1);
            mcMaxK = myPropArgs.getInt("maxMultiCastMessageK", 16);

            Log.println(Log.NOTICE, CLASS_STRING + ": MultiCastDispatcher.defaultIP   = " + mcIP);
            Log.println(Log.NOTICE, CLASS_STRING + ": MultiCastDispatcher.defaultPort = " + mcPort);
            Log.println(Log.NOTICE, CLASS_STRING + ": maxMultiCastMessageK            = " + mcMaxK);

            try {
                myMCD = new MultiCastDispatcher(mcIP, mcPort, mcMaxK);
                myMCD.start();
                myMCD.addSubscription(new RouterStatusCallback(), "RouteOrderShortStatusResponse");
                myMCD.addSubscription(new SmartRouterCallback(), "SmartRouterShortStatusResponse");

            } catch (Exception e) {
                Log.println(Log.ERROR, CLASS_STRING + ": Cannot start multicast dispatcher: " + e.toString());
                Log.printStackTrace(e);
            }

            myMCMQ = new BBRouterMMQ();
            myMCMQ.setBBRouter(this);

            loop=true;

            routersList = myPropArgs.get("RouteOrder.PRODInstanceList");
            if ( ! routersList.isEmpty() ) {
                routersList = routersList + ",";               
            }
            smartRoutersList = myPropArgs.get("SmartRouter.PRODInstanceList");
            allRoutersList = routersList + smartRoutersList;

            Log.println(Log.NOTICE, CLASS_STRING + ": main : Status collector is ENABLED for these Routers : " + allRoutersList);

            // This program runs until input is terminated.
        
            _multicastSend = new Thread(new MulticastSend());

            _multicastSend.start();
         
        } //try

        catch (Exception e) {

            e.printStackTrace();

        } //catch

    } //BBRouter

    public boolean checkTestingHours() {

        boolean testingOpen;

        // Create a GregorianCalendar with the Eastern Daylight time zone
        // and the current date and time
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(new java.util.Date());

        // We never test on Weekends
        int dayofweek = calendar.get(Calendar.DAY_OF_WEEK);

        if ( dayofweek == 7 || dayofweek == 1 ) {

            testingOpen = false;

        } else {

            // testOpen Calendar
            Calendar testOpen = new GregorianCalendar();
            testOpen.setTime(new java.util.Date());
            testOpen.clear(Calendar.HOUR_OF_DAY); // so doesn't override
            testOpen.set(Calendar.HOUR_OF_DAY, Integer.parseInt(OPENHOUR));
            testOpen.clear(Calendar.MINUTE); // so doesn't override
            testOpen.set(Calendar.MINUTE, Integer.parseInt(OPENMINUTE));
            testOpen.clear(Calendar.SECOND); // so doesn't override
            testOpen.set(Calendar.SECOND, 0);

            // testClose Calendar
            Calendar testClose = new GregorianCalendar();
            testClose.setTime(new java.util.Date());
            testClose.clear(Calendar.HOUR_OF_DAY); // so doesn't override
            testClose.set(Calendar.HOUR_OF_DAY, Integer.parseInt(CLOSEHOUR));
            testClose.clear(Calendar.MINUTE); // so doesn't override
            testClose.set(Calendar.MINUTE, Integer.parseInt(CLOSEMINUTE));
            testClose.clear(Calendar.SECOND); // so doesn't override
            testClose.set(Calendar.SECOND, 0);


            if ( calendar.after(testOpen) && calendar.before(testClose) ) {
                testingOpen = true;
            } else {
                testingOpen = false;
            }
        }

        return testingOpen;

    } //checkTestingHours

    public void sendUpdate() {

        try {

            DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.LONG);

            dateFormat.setTimeZone(TimeZone.getDefault());

            String reportDate = dateFormat.format(Calendar.getInstance().getTime());

            int color = sendObject.GREEN;

            String msgline = myPropArgs.get("System","none") + " - TEST Everything Okay";

            String badmsgs = "";

            sendObject.bbnotify(myPropArgs.get("System","none").replace('.',',') + ".msgs",color,
                                reportDate+" "+msgline+" "+badmsgs);

        } //try

        catch (Exception e) {

            e.printStackTrace();

        } //catch

    } //sendUpdate

    private class SmartRouterCallback implements MultiCastMessageCallback {

        public void onMsg(MultiCastMessage msg) {

            myMCMQ.addMessage(msg);

        } //onmsg

    } //

    private class RouterStatusCallback implements MultiCastMessageCallback {

        public void onMsg(MultiCastMessage msg) {

            myMCMQ.addMessage(msg);

        } //onmsg

    } //RouterStatusCallback


    protected void processMultiCastMessage(MultiCastMessage msg) {

        try {

            // Will contain the status on each run
            Integer[] amistatus = new Integer[6]; // The array for the AMI status
                        
            int len_ = msg.toString().length();

            String to_ = (String)msg.getToHeader();

            String interface_ = (String)msg.getField("instance");

            String subject_ = (String)msg.getSubjectHeader();

            Log.println(Log.NOTICE, CLASS_STRING + ": processMultiCastMessage : Subject = '"+subject_ +"', From = '"+interface_
                        +"', To = '" +to_+"', Compressed Length = "+len_);

            if ( (to_ != null) && to_.equals(myMCD.getIdentifier()) ) {

                if  (subject_.equals("RouteOrderShortStatusResponse")) {
                    
                    // "instance" -->  Name of server instance
                    // "started"  -->  Time server was started
                    // "host_name"  -->  Unix host name
                    // "java_version"  -->  Version of Java used to compile jar file
                    // "java_vendor"  -->  Vendor of Java runtime
                    // "jar_file_name"  -->  Name of Java jar file
                    // "jar_file_date"  -->  Date jar file was created
                    // "web_port"  -->  Web port of db update server
                    // "retry_queue_size"  -->  Current size of update retry queue

                    String started = (String)msg.getField("started");

                    String hostName = (String)msg.getField("host_name");

                    String swp = (String)msg.getField("web_port");
                    int wp = ((swp != null) ? Integer.parseInt(swp) : 0);
                   
                    String rstatus = (String)msg.getField("status");

                    String soc = (String)msg.getField("open_order_count");
                    int oc = ((soc != null) ? Integer.parseInt(soc) : 0);

                    RouterInstance routerObject = new RouterInstance();
                    
                    routerObject.update(rstatus);
                    
                    routerObject.setName(interface_);
                    
                    DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.LONG);
                    dateFormat.setTimeZone(TimeZone.getDefault());
                    String reportDate = dateFormat.format(Calendar.getInstance().getTime());
                    
                    // Let's check the levels
                    
                    // In case we do not override the color, let's leave it
                    // green.
                    String color = "green";
                    String connStatus = GREENBULLET+" "+interface_+" is running.";
                    String amiconnStatus = "TRUE";

                    // do we need to adjust the WARN or PANICK
                    
                    String status = "status router-"+interface_+".status "+routerObject.getColor()+" "+reportDate+
                                    " Router interface responded.\n"+connStatus+"\n"+
                                    "Router available on <a href=http://"+hostName+":"+swp+">http://"+hostName+":"+swp+"</a>\n\n"+
                                    "\n"+routerObject.fullTable+"\n\n"; 
                    
                    amistatus[0] = 1; // RESPONDING
                    amistatus[1] = oc; // Order Count
                    amistatus[2] = routerObject.htQueueStats.get("Handler0").getCurrent(); // Handler0 Queue
                    amistatus[3] = routerObject.htQueueStats.get("Handler1").getCurrent(); // Handler1 Queue
                    amistatus[4] = routerObject.htQueueStats.get("Handler2").getCurrent(); // Handler2 Queue
                    amistatus[5] = routerObject.htQueueStats.get("Handler3").getCurrent(); // Handler3 Queue

                    htRouterStatus.put(interface_,status);
                    htAmiRouterStatus.put(interface_,amistatus);
                    htRouterInstances.put(interface_, routerObject);

                } else if (subject_.equals("SmartRouterShortStatusResponse")) {

                    // "instance" -->  Name of server instance
                    // "started"  -->  Time server was started
                    // "host_name"  -->  Unix host name
                    // "java_version"  -->  Version of Java used to compile jar file
                    // "java_vendor"  -->  Vendor of Java runtime
                    // "jar_file_name"  -->  Name of Java jar file
                    // "jar_file_date"  -->  Date jar file was created
                    // "web_port"  -->  Web port of db update server
                    // "retry_queue_size"  -->  Current size of update retry queue

                    String started = (String)msg.getField("started");

                    String hostName = (String)msg.getField("host_name");

                    String swp = (String)msg.getField("web_port");
                    int wp = ((swp != null) ? Integer.parseInt(swp) : 0);

                    String rstatus = (String)msg.getField("status");

                    String soc = (String)msg.getField("open_order_count");
                    int oc = ((soc != null) ? Integer.parseInt(soc) : 0);

                    RouterInstance routerObject = new RouterInstance();

                    routerObject.update(rstatus);

                    routerObject.setName(interface_);

                    DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.LONG);
                    dateFormat.setTimeZone(TimeZone.getDefault());
                    String reportDate = dateFormat.format(Calendar.getInstance().getTime());

                    // Let's check the levels

                    // In case we do not override the color, let's leave it
                    // green.
                    String color = "green";
                    String connStatus = GREENBULLET+" "+interface_+" is running.";
                    String amiconnStatus = "TRUE";

                    // do we need to adjust the WARN or PANICK

                    String status = "status router-"+interface_+".status "+routerObject.getColor()+" "+reportDate+
                                    " Router interface responded.\n"+connStatus+"\n"+
                                    "Router available on <a href=http://"+hostName+":"+swp+">http://"+hostName+":"+swp+"</a>\n\n"+
                                    "\n"+routerObject.fullTable+"\n\n";

                    amistatus[0] = 1; // RESPONDING
                    amistatus[1] = oc; // Order Count
                    amistatus[2] = routerObject.htQueueStats.get("Handler0").getCurrent(); // Handler0 Queue
                    amistatus[3] = routerObject.htQueueStats.get("Handler1").getCurrent(); // Handler1 Queue
                    amistatus[4] = routerObject.htQueueStats.get("Handler2").getCurrent(); // Handler2 Queue
                    amistatus[5] = routerObject.htQueueStats.get("Handler3").getCurrent(); // Handler3 Queue

                    htRouterStatus.put(interface_,status);
                    htAmiRouterStatus.put(interface_,amistatus);
                    htRouterInstances.put(interface_, routerObject);

                } else {

                    Log.println(Log.ERROR, CLASS_STRING + ":RouterStatusCallback : I don't know how to process a '" + subject_ + "' message - I have ignored it.");

                }

            } else {

                Log.println(Log.ERROR, CLASS_STRING + ":RouterStatusCallback : MultiCast message was not for me - I have ignored it.");

            }

        } //try

        catch (Exception e) {

            e.printStackTrace();

        } //catch

    } // processMultiCastMessage



    public static void debug(String outString) {

        Log.println(outString);

    }

    
    public class MulticastSend implements Runnable {

        public void run() {

        try {
            
            //
            // The monitoring loop, every 4 1/2 minutes we send a multicast
            // update and wait 15 seconds for a reply for the OFAs.
            //

            while (loop) {

                // Will contain the status on each run
                Integer[] amistatus = new Integer[6]; // The array for the AMI status

                // See if the market is open or not.
                testingOpen = checkTestingHours(); 

                // Build a hash table with an ERROR report
                // for each Router we are testing. The OFAs who
                // reply with multicast will see their status
                // overwritten. If testing is not yet open, we initialize
                // to green.

                DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.LONG);
                dateFormat.setTimeZone(TimeZone.getDefault());
                reportDate = dateFormat.format(Calendar.getInstance().getTime());

                if ( ! testingOpen ) {
                    color = "green";
                } else {
                    color = "red";
                }

                String routersTokens[] = allRoutersList.split(",");

                for (int i = routersTokens.length - 1; i >=0; i --) {

                    String interface_ = routersTokens[i];

                    if ( ! testingOpen ) {
                        status = "status router-" + interface_ +
                                 ".status " + color + " " + reportDate +
                                 " Testing Disabled outside testing hours.\n\n";
                        amistatus[0] = 1;   // Responding
                        amistatus[1] = 0;   // Order Count
                        amistatus[2] = 0;   // Handler0 Queue - 0 outside testing hours
                        amistatus[3] = 0;   // Handler0 Queue - 0 outside testing hours
                        amistatus[4] = 0;   // Handler0 Queue - 0 outside testing hours
                        amistatus[5] = 0;   // Handler0 Queue - 0 outside testing hours

                    } else {
                        status = "status router-" + interface_ +
                                 ".status " + color + " " + reportDate +
                                 " Router Error: has not responded\n\n";
                        amistatus[0] = 0;   // Responding - Initialize to NO (0)
                        amistatus[1] = 0;   // Order Count
                        amistatus[2] = 0;   // Handler0 Queue - 0 outside testing hours
                        amistatus[3] = 0;   // Handler0 Queue - 0 outside testing hours
                        amistatus[4] = 0;   // Handler0 Queue - 0 outside testing hours
                        amistatus[5] = 0;   // Handler0 Queue - 0 outside testing hours

                    }
                    
                    htRouterStatus.put(interface_, status);
                    htAmiRouterStatus.put(interface_, amistatus);

                } // for routersTokens

                // Send multicast requests to all Router processes.
                //
               
                routersTokens = routersList.split(",");

                for (int i = routersTokens.length - 1; i >=0; i --) {

                    MultiCastMessage msg = new MultiCastMessage();

                    msg.setSubjectHeader(RouteOrderCommandType.REQUEST_HEADER);
                    msg.add("interface", routersTokens[i]);
                    msg.add("command", RouteOrderCommandType.REPORTSHORTSTATUS.getCommand());
                    msg.add("args", "");

                    try {
                        Log.println(Log.NOTICE, CLASS_STRING + ": getStatusAction: " + msg.toString());
                        myMCD.send(msg);
                    } catch (Exception e) {
                        Log.println(Log.ERROR, CLASS_STRING + ": getStatusAction: " + e.toString());
                    }

                    try {
                        Thread.currentThread().sleep(QUERYSPACER);
                    } catch ( Exception e ) {
                        Log.println(Log.ERROR, CLASS_STRING + ": sleep failed: " + e.toString());
                    }

                } // for each Router


                // Send multicast requests to all SmartRouter processes.
                //

                routersTokens = smartRoutersList.split(",");

                for (int i = routersTokens.length - 1; i >=0; i --) {

                    MultiCastMessage msg = new MultiCastMessage();

                    msg.setSubjectHeader(SmartRouterCommandType.REQUEST_HEADER);
                    msg.add("interface", routersTokens[i]);
                    msg.add("command", SmartRouterCommandType.REPORTSHORTSTATUS.getCommand());
                    msg.add("args", "");

                    try {
                        Log.println(Log.NOTICE, CLASS_STRING + ": getStatusAction: " + msg.toString());
                        myMCD.send(msg);
                    } catch (Exception e) {
                        Log.println(Log.ERROR, CLASS_STRING + ": getStatusAction: " + e.toString());
                    }

                    try {
                        Thread.currentThread().sleep(QUERYSPACER);
                    } catch ( Exception e ) {
                        Log.println(Log.ERROR, CLASS_STRING + ": sleep failed: " + e.toString());
                    }

                } // for each SmartRouter

                // Give the Router a chance to respond...
                try {
                    Thread.currentThread().sleep(TIMEOUT);
                } catch ( Exception e ) {
                    Log.println(Log.ERROR, CLASS_STRING + ": sleep failed: " + e.toString());
                }

                String ComboMsg = "";
                int myCounter = 1;

                for (Enumeration e = htRouterStatus.elements() ; e.hasMoreElements() ; ) {

                    if ( myCounter == 1 ) {
                        ComboMsg = "combo\n";
                    }

                    //String status = "status router-" + interface_ +
                    //                ".status " + color + " " + reportDate +
                    //                " Router Error: Router has not reponded\n\n";
                    String status = "" + e.nextElement();
                    String page = status.replaceFirst("status","page");
                    
                    //Log.println(Log.NOTICE, CLASS_STRING + ": nscaSend : parsing : " + status + "###;");

                    Scanner s = new Scanner(status);
                    s.findInLine("^status router-(\\w+).status .*");
                    MatchResult result = s.match();
                    String inst = result.group(1);
                    
                    if ( status.matches("^status.*red.*\n\n") ) {

                        Log.println(Log.NOTICE, CLASS_STRING + ": main : Sending RED Status message to BBDISPLAY.");
                        sendObject.bbsend(myPropArgs.get("BBdisplay","none"),status,myPropArgs.get("BBdisplayPort","1984"));
                        Log.println(Log.NOTICE, CLASS_STRING + ": main : Sending RED Page message to BBPAGER.");
                        sendObject.bbsend(myPropArgs.get("BBpager","none"),page,myPropArgs.get("BBdisplayPort","1984"));

                        //Nagios
                        nscaSend.nscaSend("stark", Level.CRITICAL, inst, status);

                    } else {

                        //Nagios
                        nscaSend.nscaSend("stark", Level.OK, inst, status);
                        
                        ComboMsg = ComboMsg+status;
                        Log.println(Log.NOTICE, CLASS_STRING + ": main : Building combo message\n" + ComboMsg);

                    }

                    if ( myCounter == 10 ) {

                        Log.println(Log.NOTICE, CLASS_STRING + ": main : Sending Combo message to BBDISPLAY.");
                        sendObject.bbsend(myPropArgs.get("BBdisplay","none"),ComboMsg,myPropArgs.get("BBdisplayPort","1984"));
                        myCounter = 1;

                    } else {

                        myCounter++;

                    }

                }

                Log.println(Log.NOTICE, CLASS_STRING + ": main : Sending Combo message to BBDISPLAY.");
                sendObject.bbsend(myPropArgs.get("BBdisplay","none"),ComboMsg,myPropArgs.get("BBdisplayPort","1984"));

                if (myPropArgs.get("TestSingle","").equalsIgnoreCase("yes")) loop=false;

                Thread.currentThread().sleep(SLEEPDELAY);

            } //while

        } catch (Exception e) {

                e.printStackTrace();

        } //catch

        }

    }

    class RouterInstance {
        
        String instanceName;
        
        String fullTable;
                
        Hashtable<String, QueueStats> htQueueStats = new Hashtable<String, QueueStats>();

        String color;
        
        // Update my values
        void update (String rawResponse) {
            
            try {
                // Clear the table
                String htmlTable = "<table><tr><td>HandlerName</td><td>Current</td><td>Peek</td><td>Date of peek</td></tr>\n";

                // We need to parse the raw reponse and build the array of queues
                StringTokenizer rawStatusRow = new StringTokenizer(rawResponse,"\r\n|\r|\n");
                int row = 0;
                htmlTable = htmlTable + "<tr>";
                while (rawStatusRow.hasMoreElements()) {

                    int col = 0;
                    StringTokenizer rawStatusItem = new StringTokenizer(rawStatusRow.nextElement().toString(),"\t");
                    QueueStats qs = new QueueStats();
                    
                    while (rawStatusItem.hasMoreElements() && col < 4) {
                        
                        String itemRead = rawStatusItem.nextElement().toString();
                        
                        switch ( col ) {
                            
                            case 0:
                                qs.handlerName = itemRead; break;
                               
                            case 1:
                                qs.currentQueue = Integer.parseInt(itemRead); break;
                                
                            case 2:
                                qs.peekQueue = Integer.parseInt(itemRead); break;
                                
                            case 3:
                                qs.timeOfPeek = itemRead; break;
                                
                            default:
                                break;
                        }
                        
                        htmlTable = htmlTable + "<td>" + itemRead + "</td>";
                        col++;

                    }

                    this.htQueueStats.put(qs.handlerName, qs);
                    
                    htmlTable = htmlTable + "</tr>";

                    row++;

                }

                htmlTable = htmlTable + "</table>";

                this.fullTable = htmlTable;
                
            } // try
            
            catch (Exception e) {

                e.printStackTrace();

            } //catch
            
        }
                
        
        void setName(String name) {
            
            this.instanceName = name;
            
        }
        
        String getColor() {
            
            // Assume we're okay
            this.color= "green";
            
            for (Enumeration e = htQueueStats.keys() ; e.hasMoreElements() ; ) {
                
                // check the peek in [2]
                // XXX need a variable for the 200 limit
                // XXX need to add other checks

                QueueStats currentItem = this.htQueueStats.get(e.nextElement());
                
                if (currentItem.currentQueue > 100) {
                    this.color = "yellow";
                }
                
                if (currentItem.peekQueue > 300) {
                    this.color = "red";
                }
            }
            
            return this.color;
        }
        
        class QueueStats {
            
            String handlerName;
            
            int currentQueue;
            
            int peekQueue;
            
            String timeOfPeek;
            
            int getPeek() {
                return this.peekQueue;
            }
            
            int getCurrent() {
                return this.currentQueue;
            }
            
        }
           
    }
    
    class Handler {
        
        String handlerName;
        int currentQueue;
        int peekQueue;
        String dateOfPeekQueue;
        
    }
        
    public static void main(String[] args) {

        BBRouter me = new BBRouter();

        me.invokedStandalone = true;

    } //main


    private boolean invokedStandalone = false;

}
