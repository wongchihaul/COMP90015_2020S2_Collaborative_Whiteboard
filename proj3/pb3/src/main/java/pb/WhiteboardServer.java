package pb;

import org.apache.commons.cli.*;
import pb.managers.IOThread;
import pb.managers.ServerManager;
import pb.managers.endpoint.Endpoint;
import pb.utils.Utils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Simple whiteboard server to provide whiteboard peer notifications.
 * @author aaron
 *
 */
public class WhiteboardServer {
	private static Logger log = Logger.getLogger(WhiteboardServer.class.getName());
	
	/**
	 * Emitted by a client to tell the server that a board is being shared. Argument
	 * must have the format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String shareBoard = "SHARE_BOARD";

	/**
	 * Emitted by a client to tell the server that a board is no longer being
	 * shared. Argument must have the format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unshareBoard = "UNSHARE_BOARD";

	/**
	 * The server emits this event:
	 * <ul>
	 * <li>to all connected clients to tell them that a board is being shared</li>
	 * <li>to a newly connected client, it emits this event several times, for all
	 * boards that are currently known to be being shared</li>
	 * </ul>
	 * Argument has format "host:port:boardid"
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String sharingBoard = "SHARING_BOARD";

	/**
	 * The server emits this event:
	 * <ul>
	 * <li>to all connected clients to tell them that a board is no longer
	 * shared</li>
	 * </ul>
	 * Argument has format "host:port:boardid"
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unsharingBoard = "UNSHARING_BOARD";

	/**
	 * Emitted by the server to a client to let it know that there was an error in a
	 * received argument to any of the events above. Argument is the error message.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String error = "ERROR";

	/**
	 * Default port number.
	 */
	private static int port = Utils.indexServerPort;

	private static final Set<Endpoint> liveEndpoints = new HashSet<>();


	private static void help(Options options) {
		String header = "PB Whiteboard Server for Unimelb COMP90015\n\n";
		String footer = "\ncontact aharwood@unimelb.edu.au for issues.";
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("pb.IndexServer", header, options, footer, true);
		System.exit(-1);
	}
	
	public static void main( String[] args ) throws IOException, InterruptedException
    {
    	// set a nice log format
		System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tl:%1$tM:%1$tS:%1$tL] [%4$s] %2$s: %5$s%n");
        
    	// parse command line options
        Options options = new Options();
        options.addOption("port",true,"server port, an integer");
        options.addOption("password",true,"password for server");
        
       
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
			cmd = parser.parse( options, args);
		} catch (ParseException e1) {
			help(options);
		}
        
        if(cmd.hasOption("port")){
			try {
				port = Integer.parseInt(cmd.getOptionValue("port"));
			} catch (NumberFormatException e) {
				System.out.println("-port requires a port number, parsed: " + cmd.getOptionValue("port"));
				help(options);
			}
		}

		// create a server manager and setup event handlers
		ServerManager serverManager;

		if (cmd.hasOption("password")) {
			serverManager = new ServerManager(port, cmd.getOptionValue("password"));
		} else {
			serverManager = new ServerManager(port);
		}

		/**
		 * TODO: Put some server related code here.
		 */
		serverManager.on(ServerManager.sessionStarted, eventArgs -> {
			Endpoint endpoint = (Endpoint) eventArgs[0];
			synchronized (liveEndpoints) {
				liveEndpoints.add(endpoint);
			}
			log.info("Client session started: " + endpoint.getOtherEndpointId());
			endpoint.on(shareBoard, eventArgs1 -> {
				liveEndpoints.forEach(e -> e.emit(sharingBoard, eventArgs1[0]));
			}).on(unshareBoard, eventArgs1 ->
					liveEndpoints.forEach(e -> e.emit(unsharingBoard, eventArgs1[0]))
			);
		}).on(ServerManager.sessionStopped, (eventArgs) -> {
			Endpoint endpoint = (Endpoint) eventArgs[0];
			log.info("Client session ended: " + endpoint.getOtherEndpointId());
		}).on(ServerManager.sessionError, (eventArgs) -> {
			Endpoint endpoint = (Endpoint) eventArgs[0];
			log.warning("Client session ended in error: " + endpoint.getOtherEndpointId());
		}).on(IOThread.ioThread, (eventArgs) -> {
			String peerport = (String) eventArgs[0];
			// we don't need this info, but let's log it
			log.info("using Internet address: " + peerport);
		}).on(error, (eventArgs) -> {
			log.severe((String) eventArgs[0]);
		});


		// start up the server
		log.info("Whiteboard Server starting up");
		serverManager.start();
		// nothing more for the main thread to do
		serverManager.join();
		Utils.getInstance().cleanUp();

	}

}
