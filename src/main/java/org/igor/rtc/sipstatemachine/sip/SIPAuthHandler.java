package org.igor.rtc.sipstatemachine.sip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.sip.ClientTransaction;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.UserAgentHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.ExtendedState;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.annotation.EventHeaders;
import org.springframework.statemachine.annotation.OnStateEntry;
import org.springframework.statemachine.annotation.WithStateMachine;

import gov.nist.javax.sip.clientauthutils.AccountManager;
import gov.nist.javax.sip.clientauthutils.AuthenticationHelper;
import gov.nist.javax.sip.clientauthutils.AuthenticationHelperImpl;
import gov.nist.javax.sip.clientauthutils.UserCredentials;
import gov.nist.javax.sip.stack.SIPTransactionStack;

@WithStateMachine
public class SIPAuthHandler implements AccountManager {

	private Map<ClientTransaction, UserCredentials> credentials = new HashMap<>();
	
	class MyUserCredentials implements UserCredentials {
		private String userName, password, sipDomain;

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
	
	
	private SIPHandler sipHandler;
	
	public SIPAuthHandler(SIPHandler sipHandler) {
		super();
		this.sipHandler = sipHandler;
	}


	private AuthenticationHelper authHelper;
	
	
	
	@Override
	public UserCredentials getCredentials(ClientTransaction challengedTransaction, String realm) {
		return credentials.get(challengedTransaction);
	}
	
	@PostConstruct
	public void init(){
		authHelper = new AuthenticationHelperImpl((SIPTransactionStack) sipHandler.getSipStack(), this, sipHandler.getHeaderFactory());
	}
	
	@OnStateEntry(target="REGISTERING")
	public void register(@EventHeaders Map<String,Object> headers,ExtendedState exState) throws Exception{
		String user = (String) headers.get("user");
		String passwd = (String)headers.get("passwd");
		String destAddress = (String) headers.get("destAddress");
		
		
		SipURI fromAddress = sipHandler.getAddressFactory().createSipURI(user, destAddress);
		Address fromNameAddress = sipHandler.getAddressFactory().createAddress(fromAddress);
		fromNameAddress.setDisplayName(user);
		
		String fromTag = RandomStringUtils.randomAlphanumeric(10);
		FromHeader fromHeader = sipHandler.getHeaderFactory().createFromHeader(fromNameAddress, fromTag);

		ToHeader toHeader = sipHandler.getHeaderFactory().createToHeader(fromNameAddress, null);

		SipURI requestURI = sipHandler.getAddressFactory().createSipURI(null, destAddress);
		

		String host = sipHandler.getListeningPoint().getIPAddress();
		int port = sipHandler.getListeningPoint().getPort();
		
		ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
		ViaHeader viaHeader = sipHandler.getHeaderFactory().createViaHeader(host, port, sipHandler.getListeningProtocol(), "branch1");
		viaHeaders.add(viaHeader);
		
		CallIdHeader callIdHeader= sipHandler.getSipProvider().getNewCallId();
		

		CSeqHeader cSeqHeader = sipHandler.getHeaderFactory().createCSeqHeader(1L,Request.REGISTER);

		MaxForwardsHeader maxForwards = sipHandler.getHeaderFactory().createMaxForwardsHeader(70);

		Request request = sipHandler.getMessageFactory().createRequest(requestURI, Request.REGISTER, callIdHeader, cSeqHeader,
				fromHeader, toHeader, viaHeaders, maxForwards);

		SipURI contactURI = sipHandler.getAddressFactory().createSipURI(user, host);
		contactURI.setTransportParam(sipHandler.getListeningProtocol());
		contactURI.setPort(port);
		
		Address contactAddress = sipHandler.getAddressFactory().createAddress(contactURI);
		contactAddress.setDisplayName(user);
		ContactHeader contactHeader = sipHandler.getHeaderFactory().createContactHeader(contactAddress);
		request.addHeader(contactHeader);

		ExpiresHeader expiresHeader = sipHandler.getHeaderFactory().createExpiresHeader(3600);
		request.addHeader(expiresHeader);
		
		UserAgentHeader userAgentHeader = sipHandler.getHeaderFactory().createUserAgentHeader(Arrays.asList("product"));
		request.addHeader(userAgentHeader);
		
		ClientTransaction ct = sipHandler.getSipProvider().getNewClientTransaction(request);
		credentials.put(ct, new MyUserCredentials(user, passwd, destAddress));
		
		System.err.println(request);
		ct.sendRequest();
	}
	
	
	@OnStateEntry(target="AUTHENTICATING")
	public void authenticating(@EventHeaders Map<String,Object> headers) throws Exception{
		Response challenge = (Response) headers.get("response");
		ClientTransaction challengedTransaction = (ClientTransaction) headers.get("clientTransaction");
		ClientTransaction ct = authHelper.handleChallenge(challenge, challengedTransaction, sipHandler.getSipProvider(), 0,true);
		
		Request newRegReq = ct.getRequest();
		
		System.err.println(newRegReq);
		ct.sendRequest();
	}

}
