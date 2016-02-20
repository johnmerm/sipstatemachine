package org.igor.rtc.sipstatemachine.sip;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import javax.sdp.SessionDescription;
import javax.sip.InvalidArgumentException;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.annotation.EventHeaders;
import org.springframework.statemachine.annotation.OnStateEntry;
import org.springframework.statemachine.annotation.WithStateMachine;

@WithStateMachine
public class SIPCallHandler{

	private SIPHandler sipHandler;
	
	
	
	public SIPCallHandler(SIPHandler sipHandler) {
		super();
		this.sipHandler = sipHandler;
	}


	@OnStateEntry(source = "RINGING", target = "ANSWERING")
	public void answering(@EventHeaders Map<String, Object> headers) {
		ServerTransaction st = (ServerTransaction) headers.get("serverTransaction");
		SessionDescription answer = (SessionDescription) headers.get("answer");
		Request request = (Request) headers.get("request");
		CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
		String callID = callIdHeader.getCallId();
		StateMachine<States, Events> stateMachine = sipHandler.getStateMachine(callID);
		try {
			ContentTypeHeader cth = sipHandler.getHeaderFactory().createContentTypeHeader("application", "sdp");
			Response okResponse = sipHandler.getMessageFactory().createResponse(Response.OK, request, cth,
					answer.toString().getBytes());

			String user = (String) headers.get("from");

			String host = sipHandler.getListeningPoint().getIPAddress();
			int port = sipHandler.getListeningPoint().getPort();

			SipURI contactURI = sipHandler.getAddressFactory().createSipURI(user, host);
			contactURI.setPort(port);
			contactURI.setTransportParam(sipHandler.getListeningProtocol());
			Address contactAddress = sipHandler.getAddressFactory().createAddress(contactURI);
			contactAddress.setDisplayName(user);

			ContactHeader contactHeader = sipHandler.getHeaderFactory().createContactHeader(contactAddress);
			okResponse.setHeader(contactHeader);
			Map<String, Object> newHeaders = new HashMap<>(headers);
			// As a thread?
			try {
				ServerTransaction st2;
				if (st == null) {
					st2 = sipHandler.getSipProvider().getNewServerTransaction(request);
					newHeaders.put("serverTransaction", st2);
				} else {
					st2 = st;
				}

				st2.sendResponse(okResponse);
				// stateMachine.sendEvent(new GenericMessage<Events>(Events.ACK,
				// newHeaders));
			} catch (InvalidArgumentException | SipException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			// -- As a thread?

		} catch (ParseException e) {
			headers = new HashMap<>();
			headers.put("exception", e);
			stateMachine.sendEvent(new GenericMessage<Events>(Events.ERROR, headers));
		}
	}
}
