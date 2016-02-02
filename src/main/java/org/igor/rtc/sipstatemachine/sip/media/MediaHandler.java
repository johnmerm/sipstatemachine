package org.igor.rtc.sipstatemachine.sip.media;

import javax.sdp.SdpException;
import javax.sdp.SessionDescription;

public interface MediaHandler {
	public SessionDescription answer(SessionDescription offer) throws SdpException;

	public void start(long sessionId);
}
