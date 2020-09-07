package pb.client;


import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import pb.Endpoint;
import pb.EndpointUnavailable;
import pb.Manager;
import pb.ProtocolAlreadyRunning;
import pb.Utils;
import pb.protocols.IRequestReplyProtocol;
import pb.protocols.Protocol;
import pb.protocols.keepalive.KeepAliveProtocol;
import pb.protocols.session.SessionProtocol;

/**
 * Manages the connection to the server and the client's state.
 * 
 * @see {@link pb.Manager}
 * @see {@link pb.Endpoint}
 * @see {@link pb.protocols.Protocol}
 * @see {@link pb.protocols.IRequestReplyProtocol}
 * @author aaron
 *
 */
public class ClientManager extends Manager {
	private static Logger log = Logger.getLogger(ClientManager.class.getName());
	private SessionProtocol sessionProtocol;
	private KeepAliveProtocol keepAliveProtocol;
	private Socket socket;
	private String host;
	private int port;
	private final Socket[] sockets = new Socket[1];
	private final int[] reconTimes = new int[]{0};
	private final boolean[] isReconnects = new boolean[]{true};
	
	public ClientManager(String host,int port) throws UnknownHostException, IOException {
		this.host = host;
		this.port = port;
		socket=new Socket(InetAddress.getByName(host),port);
		sockets[0] = socket;
		Endpoint endpoint = new Endpoint(socket,this);
		endpoint.start();
		
		// simulate the client shutting down after 2mins
		// this will be removed when the client actually does something
		// controlled by the user
//		Utils.getInstance().setTimeout(()->{
//			try {
//				sessionProtocol.stopSession();
//			} catch (EndpointUnavailable e) {
//				//ignore...
//			}
//		}, 120000);


		try {
			// just wait for this thread to terminate
			endpoint.join();
		} catch (InterruptedException e) {
			// just make sure the ioThread is going to terminate
			endpoint.close();
		}

		Utils.getInstance().cleanUp(); //this one would cancel settimeout in tryReconnect() so I move it to tryReconnect()
	}

	/**
	 * The endpoint is ready to use.
	 * @param endpoint
	 */
	@Override
	public void endpointReady(Endpoint endpoint) {
		log.info("connection with server established");
		sessionProtocol = new SessionProtocol(endpoint,this);
		try {
			// we need to add it to the endpoint before starting it
			endpoint.handleProtocol(sessionProtocol);
			sessionProtocol.startAsClient();
		} catch (EndpointUnavailable e) {
			log.severe("connection with server terminated abruptly");
			endpoint.close();
		} catch (ProtocolAlreadyRunning e) {
			// hmmm, so the server is requesting a session start?
			log.warning("server initiated the session protocol... weird");
		}
		keepAliveProtocol = new KeepAliveProtocol(endpoint,this);
		try {
			// we need to add it to the endpoint before starting it
			endpoint.handleProtocol(keepAliveProtocol);
			keepAliveProtocol.startAsClient();
		} catch (EndpointUnavailable e) {
			log.severe("connection with server terminated abruptly");
			endpoint.close();
		} catch (ProtocolAlreadyRunning e) {
			// hmmm, so the server is requesting a session start?
			log.warning("server initiated the session protocol... weird");
		}
	}

	/**
	 * The endpoint close() method has been called and completed.
	 * @param endpoint
	 */
	public void endpointClosed(Endpoint endpoint) {
		log.info("connection with server terminated");
	}

	/**
	 * The endpoint has abruptly disconnected. It can no longer
	 * send or receive data.
	 * @param endpoint
	 */
	@Override
	public void endpointDisconnectedAbruptly(Endpoint endpoint) {
		log.severe("connection with server terminated abruptly");
		endpoint.close();
		tryReconnect(this.sockets, reconTimes, isReconnects);

	}

	/**
	 * An invalid message was received over the endpoint.
	 * @param endpoint
	 */
	@Override
	public void endpointSentInvalidMessage(Endpoint endpoint) {
		log.severe("server sent an invalid message");
		endpoint.close();

	}



	/**
	 * The protocol on the endpoint is not responding.
	 * @param endpoint
	 */
	@Override
	public void endpointTimedOut(Endpoint endpoint,Protocol protocol) {
		log.severe("server has timed out");
		endpoint.close();

	}


	/**
	 * The protocol on the endpoint has been violated.
	 * @param endpoint
	 */
	@Override
	public void protocolViolation(Endpoint endpoint,Protocol protocol) {
		log.severe("protocol with server has been violated: "+protocol.getProtocolName());
		endpoint.close();
	}

	/**
	 * The session protocol is indicating that a session has started.
	 * @param endpoint
	 */
	@Override
	public void sessionStarted(Endpoint endpoint) {
		log.info("session has started with server");
		
		// we can now start other protocols with the server
	}

	/**
	 * The session protocol is indicating that the session has stopped. 
	 * @param endpoint
	 */
	@Override
	public void sessionStopped(Endpoint endpoint) {
		log.info("session has stopped with server");
		endpoint.close(); // this will stop all the protocols as well
	}
	

	/**
	 * The endpoint has requested a protocol to start. If the protocol
	 * is allowed then the manager should tell the endpoint to handle it
	 * using {@link pb.Endpoint#handleProtocol(Protocol)}
	 * before returning true.
	 * @param protocol
	 * @return true if the protocol was started, false if not (not allowed to run)
	 */
	@Override
	public boolean protocolRequested(Endpoint endpoint, Protocol protocol) {
		// the only protocols in this system are this kind...
		try {
			((IRequestReplyProtocol)protocol).startAsClient();
			endpoint.handleProtocol(protocol);
			return true;
		} catch (EndpointUnavailable e) {
			// very weird... should log this
			return false;
		} catch (ProtocolAlreadyRunning e) {
			// even more weird... should log this too
			return false;
		}
	}


	public void tryReconnect(Socket[] sockets, int[] reconTimes, boolean[] isReconnects) {
		if (reconTimes[0] == 10) {
			Utils.getInstance().cleanUp();
		}
		else
		{
			pb.Utils.getInstance().setTimeout(() -> {
						log.info("*******1.isReconnect is " + isReconnects[0]);
						log.info("*******1.socket is " + sockets[0] + " and it is closed?: " + sockets[0].isClosed());
						log.info("*******1.reconTime is " + reconTimes[0]);
						if (sockets[0].isClosed() && isReconnects[0]) {
							try {
								sockets[0] = new Socket(InetAddress.getByName(this.host), this.port);
								log.info("*******2.socket is " + sockets[0]);
								log.info("*******1.host is " + this.host);
								log.info("*******1.port is " + this.port);
								log.info("*********TRY TO RECONNECT " + reconTimes[0] + " TIMES*********" );
								Endpoint endpoint1 = new Endpoint(sockets[0], this);
								endpoint1.start();
								isReconnects[0] = false;
								try {
									endpoint1.join();
								} catch (InterruptedException e) {
									endpoint1.close();
								}
								Utils.getInstance().cleanUp();
							} catch (IOException e) {
								log.info("**********SOCKET THIS TIME FAILED, TRY AGAIN**********");
								isReconnects[0] = true;
								++ reconTimes[0];
								tryReconnect(sockets, reconTimes, isReconnects);
							}
						}
					}
					, 5000);
		}
	}

}
