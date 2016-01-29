package org.igor.rtc.sipstatemachine.sip;

import java.net.InetAddress;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
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
import javax.sip.header.AllowHeader;
import javax.sip.header.AuthorizationHeader;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.UserAgentHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.statemachine.ExtendedState;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.annotation.EventHeaders;
import org.springframework.statemachine.annotation.OnTransition;
import org.springframework.statemachine.annotation.WithStateMachine;

import gov.nist.javax.sip.address.GenericURI;
import gov.nist.javax.sip.clientauthutils.AccountManager;
import gov.nist.javax.sip.clientauthutils.AuthenticationHelper;
import gov.nist.javax.sip.clientauthutils.AuthenticationHelperImpl;
import gov.nist.javax.sip.clientauthutils.UserCredentials;
import gov.nist.javax.sip.stack.SIPTransactionStack;
/**
 * A SIP Handler based on the JAIN SIP Reference implementation
 * Tutorial found at: http://www.oracle.com/technetwork/java/introduction-jain-sip-090386.html
 * @author grmsjac6
 *
 */
@WithStateMachine(name="sipStateMachine")
public class SIPHandler implements SipListener,AccountManager{
	private static final Logger LOG= LoggerFactory.getLogger(SIPHandler.class); 
	
	@Autowired
    private StateMachine<States, Events> stateMachine;
	
	private static final String userAgent = "MyClient";
	
	
	private int tcpListeningPort;
	
	public SIPHandler(int port) {
		this.tcpListeningPort = port;
	}

	
	
	private SipFactory sipFactory;
	private SipStack sipStack;
	private ListeningPoint tcpPoint;
	private SipProvider sipProvider;
	
	
	private AddressFactory addressFactory;
    private HeaderFactory headerFactory;
    private MessageFactory messageFactory; 
	
    private AuthenticationHelper authHelper;
    
    
    private Map<ClientTransaction,UserCredentials> credentials = new HashMap<>();
    
    @Override
    public UserCredentials getCredentials(ClientTransaction challengedTransaction, String realm) {
    	return credentials.get(challengedTransaction);
    }
    
	@PostConstruct
	public void init() throws Exception {
		String ip = InetAddress.getLocalHost().getHostAddress();
		Properties properties = new Properties();

		properties.setProperty("javax.sip.STACK_NAME", "IgorClient");

		sipFactory = SipFactory.getInstance();
		sipStack = sipFactory.createSipStack(properties);
		
		
		tcpPoint = sipStack.createListeningPoint(ip,tcpListeningPort, "tcp");

		sipProvider = sipStack.createSipProvider(tcpPoint);
		sipProvider.addSipListener(this);
		
		
		addressFactory = sipFactory.createAddressFactory();
		headerFactory = sipFactory.createHeaderFactory();
		messageFactory = sipFactory.createMessageFactory();
		
		
		authHelper = new AuthenticationHelperImpl((SIPTransactionStack)sipStack, this, headerFactory);
		sipStack.start();
	}
	
	@PreDestroy
	public void destroy(){
		if (sipStack!=null){
			sipStack.stop();
		}
	}

	/**
	 * 
	 * @param from originator user (e.g. e164 Number)
	 * @param to destination in format "user@host:port"
	 * @param message Message to be sent
	 * @throws ParseException
	 * @throws InvalidArgumentException
	 * @throws SipException
	 */
	private Request createRequest(String from, String to, 
			String method,
			String viaBranch,
			String tag,
			String callId,long seqNo,
			String contentType,String body) throws Exception{
		int port = tcpPoint.getPort();
		String host = tcpPoint.getIPAddress();
		String transport = tcpPoint.getTransport();
		
		SipURI fromAddress = addressFactory.createSipURI(from, host + ":" + port);
		Address fromNameAddress = addressFactory.createAddress(fromAddress);
		fromNameAddress.setDisplayName(from);
		if (tag==null){
			tag=RandomStringUtils.randomAlphanumeric(10);
		}
		FromHeader fromHeader = headerFactory.createFromHeader(fromNameAddress, tag);

		String username = to.substring(to.indexOf(":") + 1, to.indexOf("@"));
		String address = to.substring(to.indexOf("@") + 1);

		SipURI toAddress = addressFactory.createSipURI(username, address);
		Address toNameAddress = addressFactory.createAddress(toAddress);
		toNameAddress.setDisplayName(username);
		ToHeader toHeader = headerFactory.createToHeader(toNameAddress, null);

		SipURI requestURI = addressFactory.createSipURI(username, address);
		requestURI.setTransportParam("tcp");

		ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
		if (viaBranch !=null){
			ViaHeader viaHeader = headerFactory.createViaHeader(host, port, "tcp", viaBranch);
			viaHeaders.add(viaHeader);
		}else{
			ViaHeader viaHeader = headerFactory.createViaHeader(host, port, "tcp", "branch1");
			viaHeaders.add(viaHeader);
		}
		
		CallIdHeader callIdHeader;
		if (callId !=null){
			callIdHeader = headerFactory.createCallIdHeader(callId);
		}else{
			callIdHeader = sipProvider.getNewCallId();
		}

		CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(seqNo==0?1:seqNo, method);

		MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

		Request request = messageFactory.createRequest(requestURI, method, callIdHeader, cSeqHeader,
				fromHeader, toHeader, viaHeaders, maxForwards);

		SipURI contactURI = addressFactory.createSipURI(from, host);
		contactURI.setPort(port);
		contactURI.setTransportParam(transport);
		Address contactAddress = addressFactory.createAddress(contactURI);
		contactAddress.setDisplayName(from);
		ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
		request.addHeader(contactHeader);

		if (contentType!=null && body!=null){
			String[] ct = contentType.split("/");
			ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader(ct[0], ct[1]);
			request.setContent(body, contentTypeHeader);
		}

		return request;
	}
	
	class MyUserCredentials implements UserCredentials{
		private String userName,password,sipDomain;
		
		public MyUserCredentials(String userName, String password, String sipDomain) {
			super();
			this.userName = userName;
			this.password = password;
			this.sipDomain = sipDomain;
		}

		@Override
		public String getPassword() {
			return password;
		}
		
		@Override
		public String getUserName() {
			return userName;
		}
		
		@Override
		public String getSipDomain() {
			return sipDomain;
		}
	}
	@OnTransition(target="REGISTERING")
	public void register(@EventHeaders Map<String,Object> headers) throws Exception{
		String user = (String) headers.get("user");
		String passwd = (String)headers.get("passwd");
		String destAddress = (String) headers.get("destAddress");
		
		
		SipURI fromAddress = addressFactory.createSipURI(user, destAddress);
		Address fromNameAddress = addressFactory.createAddress(fromAddress);
		fromNameAddress.setDisplayName(user);
		
		String fromTag = RandomStringUtils.randomAlphanumeric(10);
		FromHeader fromHeader = headerFactory.createFromHeader(fromNameAddress, fromTag);

		ToHeader toHeader = headerFactory.createToHeader(fromNameAddress, null);

		SipURI requestURI = addressFactory.createSipURI(null, destAddress);
		

		String host = tcpPoint.getIPAddress();
		int port = tcpPoint.getPort();
		
		ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
		ViaHeader viaHeader = headerFactory.createViaHeader(host, port, "tcp", "branch1");
		viaHeaders.add(viaHeader);
		
		CallIdHeader callIdHeader= sipProvider.getNewCallId();
		

		CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L,Request.REGISTER);

		MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

		Request request = messageFactory.createRequest(requestURI, Request.REGISTER, callIdHeader, cSeqHeader,
				fromHeader, toHeader, viaHeaders, maxForwards);

		SipURI contactURI = addressFactory.createSipURI(user, host);
		contactURI.setTransportParam("tcp");
		contactURI.setPort(port);
		
		Address contactAddress = addressFactory.createAddress(contactURI);
		contactAddress.setDisplayName(user);
		ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
		request.addHeader(contactHeader);

		ExpiresHeader expiresHeader = headerFactory.createExpiresHeader(3600);
		request.addHeader(expiresHeader);
		
		UserAgentHeader userAgentHeader = headerFactory.createUserAgentHeader(Arrays.asList("product"));
		request.addHeader(userAgentHeader);
		
		ClientTransaction ct = sipProvider.getNewClientTransaction(request);
		credentials.put(ct, new MyUserCredentials(user, passwd, destAddress));
		
		System.err.println(request);
		ct.sendRequest();
	}
	
	
	@OnTransition(target="AUTHENTICATING")
	public void authenticating(@EventHeaders Map<String,Object> headers, ExtendedState exState) throws Exception{
		Response challenge = (Response) headers.get("response");
		ClientTransaction challengedTransaction = (ClientTransaction) headers.get("clientTransaction");
		ClientTransaction ct = authHelper.handleChallenge(challenge, challengedTransaction, sipProvider, 0,true);
		
		Request newRegReq = ct.getRequest();
		
		System.err.println(newRegReq);
		ct.sendRequest();
	}
	/**
	 * This method is called by the SIP stack when a new request arrives.
	 */
	@Override
	public void processRequest(RequestEvent evt) {
		LOG.info("processRequest",evt);
		
		/*
		Request req = evt.getRequest();

		String method = req.getMethod();
		FromHeader from = (FromHeader) req.getHeader("From");
		//messageProcessor.processMessage(from.getAddress().toString(), new String(req.getRawContent()));
		Response response = null;
		try { // Reply with OK
			response = messageFactory.createResponse(200, req);
			ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
			toHeader.setTag("888"); // This is mandatory as per the spec.
			ServerTransaction st = sipProvider.getNewServerTransaction(req);
			st.sendResponse(response);
		} catch (Throwable e) {
			e.printStackTrace();
			//messageProcessor.processError("Can't send OK reply.");
		}
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
		if (ct !=null){
			LOG.info("client trans timeout:",ct,!evt.isServerTransaction());
			credentials.remove(ct);
		}
		if (st !=null){
			LOG.info("Server trans timeout",st,!evt.isServerTransaction());
		}
	}

	/**
	 * This method is called by the SIP stack when there's an asynchronous
	 * message transmission error.
	 */
	@Override
	public void processIOException(IOExceptionEvent evt) {
		LOG.info("processIOException",evt);
		//messageProcessor.processError("Previous message not sent: " + "I/O Exception");
	}

	/**
	 * This method is called by the SIP stack when a dialog (session) ends.
	 */
	@Override
	public void processDialogTerminated(DialogTerminatedEvent evt) {
		LOG.info("processDialogTerminated",evt.getDialog());
	}

	/**
	 * This method is called by the SIP stack when a transaction ends.
	 */
	@Override
	public void processTransactionTerminated(TransactionTerminatedEvent evt) {
		ClientTransaction ct = evt.getClientTransaction();
		ServerTransaction st= evt.getServerTransaction();
		
		if (ct !=null){
			LOG.info("client trans terminated",ct);
			credentials.remove(ct);
		}
		if (st !=null){
			LOG.info("Server trans terminated",ct);
		}
	}
	
	@Override
	public void processResponse(ResponseEvent responseEvent) {
		ClientTransaction clientTransaction = responseEvent.getClientTransaction();
		Response response = responseEvent.getResponse();
		Map<String,Object> header = new HashMap<>();
		
		System.err.println(response);
		if (response.getStatusCode() == Response.UNAUTHORIZED) {
			
			header.put("response", response);
			header.put("clientTransaction", clientTransaction);
			
			stateMachine.sendEvent(new GenericMessage<Events>(Events.WWW_AUTH,header));
		}else if (response.getStatusCode() >401){
			stateMachine.sendEvent(new GenericMessage<Events>(Events.ERROR,header));
		}else if (response.getStatusCode() >=200 && response.getStatusCode()<300){
			stateMachine.sendEvent(new GenericMessage<Events>(Events.OK,header));
		}else if (response.getStatusCode() >=300 && response.getStatusCode()<400){
			//REDIRECT?
		}else{
			//WTF?
		}
		
		
	}
	
	
	

}
