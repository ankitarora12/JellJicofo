package org.jitsi.jicofo.auth;

/**
 * Class to collect client attributes.
 *
 * @author Nishant Lakra
 */
public class ClientAttributes {

	private String clientKey;
	private String clientName;
	private String roomName;
	private String machineUID;

	public ClientAttributes() {
		super();
		// TODO Auto-generated constructor stub
	}

	public ClientAttributes(String machineUID, String clientKey, String clientName, String roomName) {
		super();
		this.machineUID = machineUID;
		this.clientKey = clientKey;
		this.clientName = clientName;
		this.roomName = roomName;
	}

	public String getMachineUID() {
		return machineUID;
	}

	public void setMachineUID(String machineUID) {
		this.machineUID = machineUID;
	}

	public String getClientKey() {
		return clientKey;
	}

	public void setClientKey(String clientKey) {
		this.clientKey = clientKey;
	}

	public String getClientName() {
		return clientName;
	}

	public void setClientName(String clientName) {
		this.clientName = clientName;
	}

	public String getRoomName() {
		return roomName;
	}

	public void setRoomName(String roomName) {
		this.roomName = roomName;
	}

}
