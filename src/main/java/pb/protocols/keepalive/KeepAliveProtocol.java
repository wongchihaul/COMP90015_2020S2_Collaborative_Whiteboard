package pb.protocols.keepalive;

import java.time.Instant;
import java.util.logging.Logger;

import pb.Endpoint;
import pb.EndpointUnavailable;
import pb.Manager;
import pb.Utils;
import pb.protocols.Message;
import pb.protocols.Protocol;
import pb.protocols.IRequestReplyProtocol;

/**
 * Provides all of the protocol logic for both client and server to undertake
 * the KeepAlive protocol. In the KeepAlive protocol, the client sends a
 * KeepAlive request to the server every 20 seconds using
 * {@link pb.Utils#setTimeout(pb.protocols.ICallback, long)}. The server must
 * send a KeepAlive response to the client upon receiving the request. If the
 * client does not receive the response within 20 seconds (i.e. at the next time
 * it is to send the next KeepAlive request) it will assume the server is dead
 * and signal its manager using
 * {@link pb.Manager#endpointTimedOut(Endpoint,Protocol)}. If the server does
 * not receive a KeepAlive request at least every 20 seconds (again using
 * {@link pb.Utils#setTimeout(pb.protocols.ICallback, long)}), it will assume
 * the client is dead and signal its manager. Upon initialisation, the client
 * should send the KeepAlive request immediately, whereas the server will wait
 * up to 20 seconds before it assumes the client is dead. The protocol stops
 * when a timeout occurs.
 * 
 * @see {@link pb.Manager}
 * @see {@link pb.Endpoint}
 * @see {@link pb.protocols.Message}
 * @see {@link pb.protocols.keepalive.KeepAliveRequest}
 * @see {@link pb.protocols.keepalive.KeepAliveReply}
 * @see {@link pb.protocols.Protocol}
 * @see {@link pb.protocols.IRequestReplyProtocol}
 * @author aaron
 *
 */
public class KeepAliveProtocol extends Protocol implements IRequestReplyProtocol {
	private static Logger log = Logger.getLogger(KeepAliveProtocol.class.getName());
	
	/**
	 * Name of this protocol. 
	 */
	public static final String protocolName="KeepAliveProtocol";

	/**
	 * Default delay
	 */
	private static final long delay = 20000;

	/**
	 * Whether received reply or request.
	 */
	private volatile boolean recReply=false;
	private volatile boolean recRequest=false;



	/**
	 * Initialise the protocol with an endpoint and a manager.
	 * @param endpoint
	 * @param manager
	 */
	public KeepAliveProtocol(Endpoint endpoint, Manager manager) {
		super(endpoint,manager);
	}
	
	/**
	 * @return the name of the protocol
	 */
	@Override
	public String getProtocolName() {
		return protocolName;
	}

	/**
	 * 
	 */
	@Override
	public void stopProtocol() {
		endpoint.stopProtocol(this.getProtocolName());
	}
	
	/*
	 * Interface methods
	 */

	/**
	 * 
	 */
	public void startAsServer() {
		log.info("KAP start server");

		endpoint.run();        // Keep reading messages from the socket until interrupted. And response request immediately
	}
	
	/**
	 * 
	 */
	public void checkClientTimeout() {

	}
	
	/**
	 * 
	 */
	public void startAsClient() throws EndpointUnavailable {
		log.info("KAP start client");
		sendRequest(new KeepAliveRequest());
		endpoint.run();            // Keep reading messages from the socket until interrupted.
	}

	/**
	 * 
	 * @param msg
	 */
	@Override
	public void sendRequest(Message msg) throws EndpointUnavailable {
		try{
			endpoint.send(msg);
		} catch (EndpointUnavailable endpointUnavailable) {
			endpointUnavailable.printStackTrace();
		}
	}

	/**
	 * 
	 * @param msg
	 */
	@Override
	public void receiveReply(Message msg) {
		pb.Utils.getInstance().setTimeout(() -> {
			if (msg instanceof KeepAliveReply) {
				log.info("Received Reply");
				sendRequest(new KeepAliveRequest());
			} else {
				manager.endpointTimedOut(endpoint, this);
				stopProtocol();
			}
		}, delay);

	}

	/**
	 *
	 * @param msg
	 * @throws EndpointUnavailable 
	 */
	@Override
	public void receiveRequest(Message msg) throws EndpointUnavailable {
		pb.Utils.getInstance().setTimeout(() -> {
			if (msg instanceof KeepAliveRequest) {
				log.info("Received Request");
				sendReply(new KeepAliveReply());
			} else {
				manager.endpointTimedOut(endpoint, this);
				stopProtocol();
			}
		}, delay);

	}

	/**
	 * 
	 * @param msg
	 */
	@Override
	public void sendReply(Message msg) throws EndpointUnavailable {
		try{
			endpoint.send(msg);
		} catch (EndpointUnavailable endpointUnavailable) {
			endpointUnavailable.printStackTrace();
		}
	}
	
	
}
