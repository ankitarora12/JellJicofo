/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.jicofo.xmpp;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import org.jitsi.impl.protocol.xmpp.extensions.ConferenceIq;
import org.jitsi.impl.protocol.xmpp.extensions.LoginUrlIQ;
import org.jitsi.impl.protocol.xmpp.extensions.LogoutIq;
import org.jitsi.jicofo.FocusManager;
import org.jitsi.jicofo.JitsiMeetGlobalConfig;
import org.jitsi.jicofo.auth.AuthenticationAuthority;
import org.jitsi.jicofo.auth.ClientAttributes;
import org.jitsi.jicofo.auth.ClientAuthenticationAuthority;
import org.jitsi.jicofo.auth.ErrorFactory;
import org.jitsi.jicofo.exception.ClientKeyException;
import org.jitsi.jicofo.reservation.ReservationSystem;
import org.jitsi.meet.OSGi;
import org.jitsi.service.configuration.ConfigurationService;
import org.jitsi.util.StringUtils;
import org.jitsi.xmpp.component.ComponentBase;
import org.jitsi.xmpp.util.IQUtils;
import org.jivesoftware.smack.packet.XMPPError;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.xmpp.component.AbstractComponent;
import org.xmpp.packet.IQ;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.ColibriConferenceIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.ColibriStatsExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.ColibriStatsIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.ShutdownIQ;
import net.java.sip.communicator.util.Logger;
import net.java.sip.communicator.util.ServiceUtils;

/**
 * XMPP component that listens for {@link ConferenceIq} and allocates
 * {@link org.jitsi.jicofo.JitsiMeetConference}s appropriately.
 *
 * @author Pawel Domas
 */
public class FocusComponent extends ComponentBase implements BundleActivator {
	/**
	 * The logger.
	 */
	private final static Logger logger = Logger.getLogger(FocusComponent.class);

	// private static String CLIENT_KEY;

	/**
	 * Name of system and configuration property which specifies the JID from
	 * which shutdown requests will be accepted.
	 */
	public static final String SHUTDOWN_ALLOWED_JID_PNAME = "org.jitsi.jicofo.SHUTDOWN_ALLOWED_JID";

	/**
	 * The JID from which shutdown request are accepted.
	 */
	private String shutdownAllowedJid;

	/**
	 * Indicates if the focus is anonymous user or authenticated system admin.
	 */
	private final boolean isFocusAnonymous;

	/**
	 * The JID of focus user that will enter the MUC room. Can be user to
	 * recognize real focus of the conference.
	 */
	private final String focusAuthJid;

	/**
	 * The manager object that creates and expires
	 * {@link org.jitsi.jicofo.JitsiMeetConference}s.
	 */
	private FocusManager focusManager;

	/**
	 * (Optional)Authentication authority used to verify user requests.
	 */
	private AuthenticationAuthority authAuthority;

	private ClientAuthenticationAuthority clientAuthenticationAuthority;

	/**
	 * (Optional)Reservation system that manages new rooms allocation. Requires
	 * authentication system in order to verify user's identity.
	 */
	private ReservationSystem reservationSystem;

	/**
	 * Creates new instance of <tt>FocusComponent</tt>.
	 * 
	 * @param host
	 *            the hostname or IP address to which this component will be
	 *            connected.
	 * @param port
	 *            the port of XMPP server to which this component will connect.
	 * @param domain
	 *            the name of main XMPP domain on which this component will be
	 *            served.
	 * @param subDomain
	 *            the name of subdomain on which this component will be
	 *            available.
	 * @param secret
	 *            the password used by the component to authenticate with XMPP
	 *            server.
	 * @param anonymousFocus
	 *            indicates if the focus user is anonymous.
	 * @param focusAuthJid
	 *            the JID of authenticated focus user which will be advertised
	 *            to conference participants.
	 */
	public FocusComponent(String host, int port, String domain, String subDomain, String secret, boolean anonymousFocus,
			String focusAuthJid) {
		super(host, port, domain, subDomain, secret);

		this.isFocusAnonymous = anonymousFocus;
		this.focusAuthJid = focusAuthJid;
	}

	/**
	 * Initializes this component.
	 */
	public void init() {
		OSGi.start(this);
	}

	/**
	 * Method will be called by OSGi after {@link #init()} is called.
	 */
	@Override
	public void start(BundleContext bc) throws Exception {
		ConfigurationService configService = ServiceUtils.getService(bc, ConfigurationService.class);

		loadConfig(configService, "org.jitsi.jicofo");

		if (!isPingTaskStarted())
			startPingTask();

		this.shutdownAllowedJid = configService.getString(SHUTDOWN_ALLOWED_JID_PNAME);

		authAuthority = ServiceUtils.getService(bc, AuthenticationAuthority.class);
		focusManager = ServiceUtils.getService(bc, FocusManager.class);
		reservationSystem = ServiceUtils.getService(bc, ReservationSystem.class);
		clientAuthenticationAuthority = JitsiMeetGlobalConfig.clientAuthenticationAuthority;
	}

	/**
	 * Releases resources used by this instance.
	 */
	public void dispose() {
		OSGi.stop(this);
	}

	/**
	 * Methods will be invoked by OSGi after {@link #dispose()} is called.
	 */
	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		authAuthority = null;
		focusManager = null;
		reservationSystem = null;
	}

	@Override
	public String getDescription() {
		return "Manages Jitsi Meet conferences";
	}

	@Override
	public String getName() {
		return "Jitsi Meet Focus";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String[] discoInfoFeatureNamespaces() {
		return new String[] { ConferenceIq.NAMESPACE };
	}

	@Override
	protected IQ handleIQGet(IQ iq) throws Exception {
		try {
			org.jivesoftware.smack.packet.IQ smackIq = IQUtils.convert(iq);
			if (smackIq instanceof ColibriStatsIQ) {
				// Reply with stats
				ColibriStatsIQ statsReply = new ColibriStatsIQ();

				statsReply.setType(org.jivesoftware.smack.packet.IQ.Type.RESULT);
				statsReply.setPacketID(iq.getID());
				statsReply.setTo(iq.getFrom().toString());

				int conferenceCount = focusManager.getConferenceCount();

				// Return conference count
				statsReply.addStat(new ColibriStatsExtension.Stat("conferences", Integer.toString(conferenceCount)));
				statsReply.addStat(new ColibriStatsExtension.Stat("graceful_shutdown",
						focusManager.isShutdownInProgress() ? "true" : "false"));

				return IQUtils.convert(statsReply);
			} else if (smackIq instanceof LoginUrlIQ) {
				org.jivesoftware.smack.packet.IQ result = handleAuthUrlIq((LoginUrlIQ) smackIq);
				return IQUtils.convert(result);
			} else {
				return super.handleIQGet(iq);
			}
		} catch (Exception e) {
			logger.error(e, e);
			throw e;
		}
	}

	/**
	 * Handles an <tt>org.xmpp.packet.IQ</tt> stanza of type <tt>set</tt> which
	 * represents a request.
	 *
	 * @param iq
	 *            the <tt>org.xmpp.packet.IQ</tt> stanza of type <tt>set</tt>
	 *            which represents the request to handle
	 * @return an <tt>org.xmpp.packet.IQ</tt> stanza which represents the
	 *         response to the specified request or <tt>null</tt> to reply with
	 *         <tt>feature-not-implemented</tt>
	 * @throws Exception
	 *             to reply with <tt>internal-server-error</tt> to the specified
	 *             request
	 * @see AbstractComponent#handleIQSet(IQ)
	 */
	@Override
	public IQ handleIQSet(IQ iq) throws Exception {
		try {
			org.jivesoftware.smack.packet.IQ smackIq = IQUtils.convert(iq);
			String clientName = null;
			if (smackIq instanceof ConferenceIq) {

				ConferenceIq conferenceIq = (ConferenceIq) smackIq;
				String clientKey = conferenceIq.getPropertiesMap().get("clientKey");

				if (clientKey != null && !clientKey.isEmpty()) {

					Properties prop = new Properties();
					String propFileName = "/root/JellRTC-Jitsi/Keys/ClientKeys.properties";

					InputStream inputStream = new FileInputStream(propFileName);

					if (inputStream != null) {
						prop.load(inputStream);
					}

					if (prop.isEmpty()) {
						throw new FileNotFoundException(
								"property file '" + propFileName + "' does not contain any keys");
					}
					clientName = prop.getProperty(clientKey);

					if (clientName != null && !clientName.isEmpty()) {

						boolean roomExists = focusManager.getConference(conferenceIq.getRoom()) != null;

						if (!roomExists && authAuthority != null) {
							Map<String, String> propertyMap = conferenceIq.getPropertiesMap();
							ClientAttributes clientAttributes = new ClientAttributes(conferenceIq.getMachineUID(),
									clientKey, clientName, conferenceIq.getRoom());
							String sessionId = clientAuthenticationAuthority.authenticateUser(clientAttributes,
									propertyMap);

							conferenceIq.setSessionId(sessionId);
						}

					} else {
						throw new ClientKeyException("Client Name / Client Key pair mismatch. Please Retry");
					}
				} else {
					throw new ClientKeyException("Client Name / Client Key pair mismatch. Please Retry");
				}

				org.jivesoftware.smack.packet.IQ response = handleConferenceIq((ConferenceIq) smackIq);

				return response != null ? IQUtils.convert(response) : null;

			} else if (smackIq instanceof ShutdownIQ) {
				ShutdownIQ gracefulShutdownIQ = (ShutdownIQ) smackIq;

				if (!gracefulShutdownIQ.isGracefulShutdown()) {
					return IQUtils.convert(org.jivesoftware.smack.packet.IQ.createErrorResponse(smackIq,
							new XMPPError(XMPPError.Condition.bad_request)));
				}

				String from = gracefulShutdownIQ.getFrom();
				String bareFrom = org.jivesoftware.smack.util.StringUtils.parseBareAddress(from);

				if (StringUtils.isNullOrEmpty(shutdownAllowedJid) || !shutdownAllowedJid.equals(bareFrom)) {
					// Forbidden
					XMPPError forbiddenError = new XMPPError(XMPPError.Condition.forbidden);

					logger.warn("Client : " + clientName + "Rejected shutdown request from: " + from);

					return IQUtils
							.convert(org.jivesoftware.smack.packet.IQ.createErrorResponse(smackIq, forbiddenError));
				}

				logger.info("Client : " + clientName + "Accepted shutdown request from: " + from);

				focusManager.enableGracefulShutdownMode();

				return IQUtils.convert(org.jivesoftware.smack.packet.IQ.createResultIQ(smackIq));
			} else if (smackIq instanceof LogoutIq) {
				logger.info("Client : " + clientName + "Logout IQ received: " + iq.toXML());

				if (authAuthority == null) {
					return null;
				}

				org.jivesoftware.smack.packet.IQ smackResult = authAuthority.processLogoutIq((LogoutIq) smackIq);

				return smackResult != null ? IQUtils.convert(smackResult) : null;
			} else {
				return super.handleIQSet(iq);
			}

		} catch (Exception e) {
			logger.error(e, e);
			throw e;
		}
	}

	/**
	 * Additional logic added for conference IQ processing like authentication
	 * and room reservation.
	 *
	 * @param query
	 *            <tt>ConferenceIq</tt> query
	 * @param response
	 *            <tt>ConferenceIq</tt> response which can be modified during
	 *            this processing.
	 * @param roomExists
	 *            <tt>true</tt> if room mentioned in the <tt>query</tt> already
	 *            exists.
	 *
	 * @return <tt>null</tt> if everything went ok or an error/response IQ which
	 *         should be returned to the user
	 */
	public org.jivesoftware.smack.packet.IQ processExtensions(ConferenceIq query, ConferenceIq response,
			boolean roomExists) {

		// Authentication
		if (authAuthority != null) {
			org.jivesoftware.smack.packet.IQ authErrorOrResponse = authAuthority.processAuthentication(query, response);

			// Checks if authentication module wants to cancel further
			// processing and eventually returns it's response
			if (authErrorOrResponse != null) {
				return authErrorOrResponse;
			}
			// Only authenticated users are allowed to create new rooms
			/*
			 * if (!roomExists) { identity =
			 * authAuthority.getUserIdentity(peerJid); if (identity == null) {
			 * // Error not authorized return
			 * ErrorFactory.createNotAuthorizedError(query, null); } }
			 */
		}

		// Check room reservation?
		if (!roomExists && reservationSystem != null) {
			String room = query.getRoom();

			String identity = null;
			ReservationSystem.Result result = reservationSystem.createConference(identity, room);

			logger.info("Create room result: " + result + " for " + room);

			if (result.getCode() != ReservationSystem.RESULT_OK) {
				return ErrorFactory.createReservationError(query, result);
			}
		}

		return null;
	}

	private org.jivesoftware.smack.packet.IQ handleConferenceIq(ConferenceIq query) throws Exception {

		String client = clientAuthenticationAuthority.getClientNameFromSessionMap(query.getRoom());

		if (client != null && !client.isEmpty()) {

			logger.info("client : " + client + " : conference: " + query.toString());
			ConferenceIq response = new ConferenceIq();
			String room = query.getRoom();

			logger.info("client : " + client + " : Focus request for room: " + room);

			boolean roomExists = focusManager.getConference(room) != null;
			logger.info("client : " + client + " :Room Found " + roomExists);

			if (focusManager.isShutdownInProgress() && !roomExists) {
				// Service unavailable
				return ColibriConferenceIQ.createGracefulShutdownErrorResponse(query);
			}

			// Authentication and reservations system logic
			org.jivesoftware.smack.packet.IQ error = processExtensions(query, response, roomExists);
			if (error != null) {
				return error;
			}

			boolean ready = focusManager.conferenceRequest(room, query.getPropertiesMap());

			if (!isFocusAnonymous && authAuthority == null) {
				// Focus is authenticated system admin, so we let them in
				// immediately. Focus will get OWNER anyway.
				ready = true;
			}

			response.setType(org.jivesoftware.smack.packet.IQ.Type.RESULT);
			response.setPacketID(query.getPacketID());
			response.setFrom(query.getTo());
			response.setTo(query.getFrom());
			response.setRoom(query.getRoom());
			response.setReady(ready);

			// Config
			response.setFocusJid(focusAuthJid);

			// Authentication module enabled?
			response.addProperty(new ConferenceIq.Property("authentication", String.valueOf(authAuthority != null)));

			if (authAuthority != null) {
				response.addProperty(
						new ConferenceIq.Property("externalAuth", String.valueOf(authAuthority.isExternal())));
			}

			if (focusManager.getJitsiMeetServices().getSipGateway() != null) {
				response.addProperty(new ConferenceIq.Property("sipGatewayEnabled", "true"));
			}
			log.info("client : " + client + " : response " + response);
			return response;
		} else {
			throw new ClientKeyException("Client Name / Client Key pair mismatch. Please Retry");
		}

	}

	private org.jivesoftware.smack.packet.IQ handleAuthUrlIq(LoginUrlIQ authUrlIq) {
		if (authAuthority == null) {
			XMPPError error = new XMPPError(XMPPError.Condition.service_unavailable);
			return org.jivesoftware.smack.packet.IQ.createErrorResponse(authUrlIq, error);
		}

		String peerFullJid = authUrlIq.getFrom();
		String roomName = authUrlIq.getRoom();
		if (StringUtils.isNullOrEmpty(roomName)) {
			XMPPError error = new XMPPError(XMPPError.Condition.no_acceptable);
			return org.jivesoftware.smack.packet.IQ.createErrorResponse(authUrlIq, error);
		}

		LoginUrlIQ result = new LoginUrlIQ();
		result.setType(org.jivesoftware.smack.packet.IQ.Type.RESULT);
		result.setPacketID(authUrlIq.getPacketID());
		result.setTo(authUrlIq.getFrom());

		boolean popup = authUrlIq.getPopup() != null && authUrlIq.getPopup();

		String machineUID = authUrlIq.getMachineUID();
		if (StringUtils.isNullOrEmpty(machineUID)) {
			XMPPError error = new XMPPError(XMPPError.Condition.bad_request,
					"missing mandatory attribute 'machineUID'");
			return org.jivesoftware.smack.packet.IQ.createErrorResponse(authUrlIq, error);
		}

		String authUrl = authAuthority.createLoginUrl(machineUID, peerFullJid, roomName, popup);

		result.setUrl(authUrl);

		return result;
	}
}