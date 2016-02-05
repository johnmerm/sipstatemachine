package org.igor.rtc.sipstatemachine.sip.media;

import javax.sdp.SdpFactory;
import javax.sdp.SessionDescription;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class UDPEchoMediaHandlerTest {
	
	
	@Test
	public void testAnswer() throws Exception{
		String offerSdpString = IOUtils.toString(getClass().getResourceAsStream("/sdpOffer.txt"));
		SessionDescription offerSdp = SdpFactory.getInstance().createSessionDescription(offerSdpString);
		UDPEchoMediaHandler handler = new UDPEchoMediaHandler();
		
		SessionDescription answer = handler.answer("callID",offerSdp);
		System.out.println(offerSdp);
		System.out.println(answer);
	}
}
