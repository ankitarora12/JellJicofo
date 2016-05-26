package org.jitsi.jicofo.auth;

import java.util.Map;

import org.jitsi.impl.protocol.xmpp.extensions.ConferenceIq;
import org.jivesoftware.smack.packet.IQ;

import net.java.sip.communicator.util.Logger;

/**
 * Common class for {@link AuthenticationAuthority} implementations.
 *
 * @author Nishant Lakra
 */
public class ClientAuthenticationImpl extends AbstractAuthAuthority implements AuthenticationAuthority {
	/**
	 * The logger.
	 */
	private final static Logger logger = Logger.getLogger(ClientAuthenticationImpl.class);

	/**
	 * Synchronization root.
	 */
	protected final Object syncRoot = new Object();

	/**
	 * Method called by the servlet in order to create new authentication
	 * session.
	 *
	 * @param clientAttributes
	 *            that will be used to distinguish between the sessions for the
	 *            same login name on different machines.
	 * @param properties
	 *            the map of client attributes/headers to be logged.
	 * @return <tt>sessionID</tt> if user has been authenticated successfully or
	 *         and have an active session <tt>new sessionID</tt> if given user
	 *         has no active session.
	 */
	public String authenticateUser(ClientAttributes clientAttributes, Map<String, String> properties) {
		synchronized (syncRoot) {
			
			AuthenticationSession session = findSessionForIdentity(clientAttributes.getRoomName(),
					clientAttributes.getClientKey());

			if (session == null) {
				session = createNewSession(clientAttributes.getMachineUID(), clientAttributes.getClientKey(),
						clientAttributes.getRoomName(), properties, clientAttributes.getClientName());
				logger.info("Created session for client : " + clientAttributes.getClientName());
			}

			return session.getSessionId();
		}
	}
	
	/**
	 * Method called by the servlet in order to create new authentication
	 * session.
	 *
	 * @param roomName
	 *            that will be used as a key to find an active sessions from
	 *            session map.
	 * @return <tt>clientName</tt> if active session is present in session memory map  
	 * 		   <tt>Null</tt>  if no active session found in session memory map 
	 *
	 */

	public String getClientNameFromSessionMap(String roomName) {
		synchronized (syncRoot) {
			String clientName = findClientNameForRoomName(roomName);

			if (clientName != null) {
				return clientName;
			}
			return null;
		}
	}

	
	/**
	 * Method called by the servlet in order to get active session from
	 * 						session memory map.
	 *
	 * @param roomName
	 *            that will be used as a key to find an active sessions from
	 *            session map.
	 * @return <tt>AuthenticationSession</tt> if active session is present in session memory map  
	 * 		   <tt>Null</tt>  if no active session found in session memory map 
	 *
	 */
	public AuthenticationSession getSessionFormSessionMap(String roomName) {
		synchronized (syncRoot) {
			AuthenticationSession session = findSessionForRoomName(roomName);

			if (session != null) {
				return session;
			}
			return null;
		}
	}

	/**
	 * The map of user JIDs to {@link AuthenticationSession}.
	 */

	@Override
	public String createLoginUrl(String machineUID, String peerFullJid, String roomName, boolean popup) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isExternal() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	protected IQ processAuthLocked(ConferenceIq query, ConferenceIq response) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected String createLogoutUrl(String sessionId) {
		// TODO Auto-generated method stub
		return null;
	}

}
