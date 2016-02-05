package org.igor.rtc.sipstatemachine.sip.media;

import javax.sdp.SdpException;
import javax.sdp.SessionDescription;

public interface MediaHandler {
	public SessionDescription answer(String callID,SessionDescription offer) throws SdpException;

	public void start(String callID);
	
	public void stop(String callID);
}
