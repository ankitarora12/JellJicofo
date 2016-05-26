package org.jitsi.jicofo.auth;

import java.util.Map;

public interface ClientAuthentication {
	
	public AuthenticationSession createNewSession(
	           String machineUID, String authIdentity, String roomName,
	            Map<String, String> properties);
	
	    public AuthenticationSession findSessionForIdentity(
	            String machineUID, String authIdentity);
	    
	    public void destroySession(String sessionId);
	    
	    public AuthenticationSession getSession(String sessionId);

}
