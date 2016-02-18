package org.igor.rtc.sipstatemachine.sip;

import java.net.NetworkInterface;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.sdp.SdpFactory;
import javax.sdp.SdpParseException;
import javax.sdp.SessionDescription;
import javax.sip.ClientTransaction;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;

import gov.nist.javax.sip.stack.SIPTransactionStack;

/**
 * A SIP Handler based on the JAIN SIP Reference implementation Tutorial found
 * at: http://www.oracle.com/technetwork/java/introduction-jain-sip-090386.html
 * 
 * @author grmsjac6
 *
 */

public class SIPHandler implements SipListener {
	private static final Logger LOG = LoggerFactory.getLogger(SIPHandler.class);

	@Autowired @Qualifier("sipStateMachine")
	private StateMachineFactory<States, Events> stateMachineFactory;
	

	public static final String userAgent = "MyClient";

	private int listeningPort;
	private String listeningProtocol;
	private SipFactory sipFactory;
	
	public SIPHandler(String protocol, int port,SipFactory sipFactory) {
		this.listeningProtocol = protocol;
		this.listeningPort = port;
		this.sipFactory = sipFactory;
	}

	
	
	private SipStack sipStack;
	private ListeningPoint point;
	private SipProvider sipProvider;

	private AddressFactory addressFactory;
	private HeaderFactory headerFactory;
	private MessageFactory messageFactory;

	private SdpFactory sdpFactory = SdpFactory.getInstance();

	
	private Map<String,StateMachine<States, Events>> callIdSM = new HashMap<>();
	
	private StateMachine<States, Events> getStateMachine(String callID){
		StateMachine<States, Events> sm = callIdSM.getOrDefault(callID, stateMachineFactory.getStateMachine());
		callIdSM.putIfAbsent(callID, sm);
		return sm;
	}
	
	@PostConstruct
	public void init() throws Exception {
		List<NetworkInterface> nifs = Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
				.filter(n -> n.getName().startsWith("eth") && n.getInterfaceAddresses().size() > 0)
				.collect(Collectors.toList());

		String ip = nifs.get(0).getInterfaceAddresses().get(0).getAddress().getHostAddress();
		Properties properties = new Properties();

		properties.setProperty("javax.sip.STACK_NAME", "IgorClient");


		sipStack = sipFactory.createSipStack(properties);

		point = sipStack.createListeningPoint(ip, listeningPort, listeningProtocol);

		sipProvider = sipStack.createSipProvider(point);
		sipProvider.addSipListener(this);

		addressFactory = sipFactory.createAddressFactory();
		headerFactory = sipFactory.createHeaderFactory();
		messageFactory = sipFactory.createMessageFactory();

		sipStack.start();
	}

	@PreDestroy
	public void destroy() {
		if (sipStack != null) {
			sipStack.stop();
		}
	}

	/**
	 * 
	 * @param from
	 *            originator user (e.g. e164 Number)
	 * @param to
	 *            destination in format "user@host:port"
	 * @param message
	 *            Message to be sent
	 * @throws ParseException
	 * @throws InvalidArgumentException
	 * @throws SipException
	 */
	private Request createRequest(String from, String to, String method, String viaBranch, String tag, String callId,
			long seqNo, String contentType, String body) throws Exception {
		int port = point.getPort();
		String host = point.getIPAddress();
		String transport = point.getTransport();

		SipURI fromAddress = addressFactory.createSipURI(from, host + ":" + port);
		Address fromNameAddress = addressFactory.createAddress(fromAddress);
		fromNameAddress.setDisplayName(from);
		if (tag == null) {
			tag = RandomStringUtils.randomAlphanumeric(10);
		}
		FromHeader fromHeader = headerFactory.createFromHeader(fromNameAddress, tag);

		String username = to.substring(to.indexOf(":") + 1, to.indexOf("@"));
		String address = to.substring(to.indexOf("@") + 1);

		SipURI toAddress = addressFactory.createSipURI(username, address);
		Address toNameAddress = addressFactory.createAddress(toAddress);
		toNameAddress.setDisplayName(username);
		ToHeader toHeader = headerFactory.createToHeader(toNameAddress, null);

		SipURI requestURI = addressFactory.createSipURI(username, address);
		requestURI.setTransportParam(listeningProtocol);

		ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
		if (viaBranch != null) {
			ViaHeader viaHeader = headerFactory.createViaHeader(host, port, listeningProtocol, viaBranch);
			viaHeaders.add(viaHeader);
		} else {
			ViaHeader viaHeader = headerFactory.createViaHeader(host, port, listeningProtocol, "branch1");
			viaHeaders.add(viaHeader);
		}

		CallIdHeader callIdHeader;
		if (callId != null) {
			callIdHeader = headerFactory.createCallIdHeader(callId);
		} else {
			callIdHeader = sipProvider.getNewCallId();
		}

		CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(seqNo == 0 ? 1 : seqNo, method);

		MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

		Request request = messageFactory.createRequest(requestURI, method, callIdHeader, cSeqHeader, fromHeader,
				toHeader, viaHeaders, maxForwards);

		SipURI contactURI = addressFactory.createSipURI(from, host);
		contactURI.setPort(port);
		contactURI.setTransportParam(transport);
		Address contactAddress = addressFactory.createAddress(contactURI);
		contactAddress.setDisplayName(from);
		ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
		request.addHeader(contactHeader);

		if (contentType != null && body != null) {
			String[] ct = contentType.split("/");
			ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader(ct[0], ct[1]);
			request.setContent(body, contentTypeHeader);
		}

		return request;
	}



	/**
	 * This method is called by the SIP stack when a new request arrives.
	 */
	@Override
	public void processRequest(RequestEvent evt) {
		Map<String, Object> headers = new HashMap<>();

		Request request = evt.getRequest();
		ServerTransaction st = evt.getServerTransaction();
		headers.put("serverTransaction", st);
		headers.put("request", request);

		ContentTypeHeader cTypeHeader = (ContentTypeHeader) request.getHeader(ContentTypeHeader.NAME);
		FromHeader fromHeader = (FromHeader) request.getHeader(FromHeader.NAME);
		CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
		
		String callId = callIdHeader.getCallId();
		headers.put("from", fromHeader.getName());
		headers.put("callID", callId);

		System.err.println(request);
		switch (request.getMethod()) {
		case Request.INVITE:

			if ("application".equals(cTypeHeader.getContentType()) && "sdp".equals(cTypeHeader.getContentSubType())) {
				String sdpString = new String((byte[]) request.getContent());
				try {
					SessionDescription sdp = sdpFactory.createSessionDescription(sdpString);

					headers.put("offer", sdp);

					getStateMachine(callId).sendEvent(new GenericMessage<Events>(Events.OFFERED, headers));
				} catch (SdpParseException e) {
					headers.put("exception", e);
					getStateMachine(callId).sendEvent(new GenericMessage<Events>(Events.ERROR, headers));
				}
			}
			break;
		case Request.ACK:
			getStateMachine(callId).sendEvent(new GenericMessage<Events>(Events.ACK, headers));
			break;
		case Request.BYE:
			getStateMachine(callId).sendEvent(new GenericMessage<Events>(Events.HALT, headers));
			break;
		default:
			break;
		}
		/*
		 * Request req = evt.getRequest();
		 * 
		 * String method = req.getMethod(); FromHeader from = (FromHeader)
		 * req.getHeader("From");
		 * //messageProcessor.processMessage(from.getAddress().toString(), new
		 * String(req.getRawContent())); Response response = null; try { //
		 * Reply with OK response = messageFactory.createResponse(200, req);
		 * ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
		 * toHeader.setTag("888"); // This is mandatory as per the spec.
		 * ServerTransaction st = sipProvider.getNewServerTransaction(req);
		 * st.sendResponse(response); } catch (Throwable e) {
		 * e.printStackTrace(); //messageProcessor.processError(
		 * "Can't send OK reply."); }
		 */
	}

	/**
	 * This method is called by the SIP stack when there's no answer to a
	 * message. Note that this is treated differently from an error message.
	 */
	@Override
	public void processTimeout(TimeoutEvent evt) {

		ServerTransaction st = evt.getServerTransaction();
		ClientTransaction ct = evt.getClientTransaction();
		if (ct != null) {
			LOG.info("client trans timeout:", ct, !evt.isServerTransaction());
			//credentials.remove(ct);
		}
		if (st != null) {
			LOG.info("Server trans timeout", st, !evt.isServerTransaction());
		}
	}

	/**
	 * This method is called by the SIP stack when there's an asynchronous
	 * message transmission error.
	 */
	@Override
	public void processIOException(IOExceptionEvent evt) {
		LOG.info("processIOException", evt);
		// messageProcessor.processError("Previous message not sent: " + "I/O
		// Exception");
	}

	/**
	 * This method is called by the SIP stack when a dialog (session) ends.
	 */
	@Override
	public void processDialogTerminated(DialogTerminatedEvent evt) {
		LOG.info("processDialogTerminated", evt.getDialog());
	}

	/**
	 * This method is called by the SIP stack when a transaction ends.
	 */
	@Override
	public void processTransactionTerminated(TransactionTerminatedEvent evt) {
		ClientTransaction ct = evt.getClientTransaction();
		ServerTransaction st = evt.getServerTransaction();

		if (ct != null) {
			LOG.info("client trans terminated", ct);
			//credentials.remove(ct);
		}
		if (st != null) {
			LOG.info("Server trans terminated", ct);
		}
	}

	@Override
	public void processResponse(ResponseEvent responseEvent) {
		ClientTransaction clientTransaction = responseEvent.getClientTransaction();
		Response response = responseEvent.getResponse();
		CallIdHeader callIdHeader = (CallIdHeader) response.getHeader(CallIdHeader.NAME);
		String callId = callIdHeader.getCallId();
		Map<String, Object> header = new HashMap<>();

		System.err.println(response);
		if (response.getStatusCode() == Response.UNAUTHORIZED) {

			header.put("response", response);
			header.put("clientTransaction", clientTransaction);

			getStateMachine(callId).sendEvent(new GenericMessage<Events>(Events.WWW_AUTH, header));
		} else if (response.getStatusCode() > 401) {
			getStateMachine(callId).sendEvent(new GenericMessage<Events>(Events.ERROR, header));
		} else if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
			getStateMachine(callId).sendEvent(new GenericMessage<Events>(Events.OK, header));
		} else if (response.getStatusCode() >= 300 && response.getStatusCode() < 400) {
			// REDIRECT?
		} else {
			// WTF?
		}

	}

	public SipStack getSipStack() {
		return sipStack;
	}

	public HeaderFactory getHeaderFactory() {
		return headerFactory;
	}

	public int getListeningPort() {
		return listeningPort;
	}

	public String getListeningProtocol() {
		return listeningProtocol;
	}

	public SipFactory getSipFactory() {
		return sipFactory;
	}

	public ListeningPoint getListeningPoint() {
		return point;
	}

	public SipProvider getSipProvider() {
		return sipProvider;
	}

	public AddressFactory getAddressFactory() {
		return addressFactory;
	}

	public MessageFactory getMessageFactory() {
		return messageFactory;
	}
	

}
