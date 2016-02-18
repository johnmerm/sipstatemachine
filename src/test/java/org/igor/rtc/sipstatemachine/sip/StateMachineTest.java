package org.igor.rtc.sipstatemachine.sip;


import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Properties;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.ListeningPoint;
import javax.sip.ResponseEvent;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Response;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.header.CallID;
import gov.nist.javax.sip.stack.SIPTransactionStack;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class)
public class StateMachineTest {

	@Configuration
	@EnableStateMachine
	@Import(SIPStateMachine.class)
	static class Config{
		@Bean
		public SipFactory mockfactory() throws Exception{
			SipStack mockStack = mock(SipStackImpl.class);
			ListeningPoint mockPoint = mock(ListeningPoint.class);
			SipProvider mockProvider = mock(SipProvider.class);
			
			when(mockStack.createListeningPoint(anyString(), anyInt(), anyString())).thenReturn(mockPoint);
			when(mockStack.createSipProvider(eq(mockPoint))).thenReturn(mockProvider);
			
			SipFactory mock = mock(SipFactory.class); 
			
			
			SipFactory real = SipFactory.getInstance();
			
			when(mock.createSipStack(notNull(Properties.class))).thenReturn(mockStack);
			when(mock.createAddressFactory()).thenReturn(real.createAddressFactory());
			when(mock.createHeaderFactory()).thenReturn(real.createHeaderFactory());
			when(mock.createMessageFactory()).thenReturn(real.createMessageFactory());
			return mock;
		}
		
		
		
	}
	
	@Autowired
	private SIPHandler sipHandler;
	
	@Autowired @Qualifier("authStateMachine")
	private StateMachine<States, Events> authStateMachine;
	
	@Test
	public void dryRun(){
		sipHandler.toString();
	}
	
	@Test
	public void testRegisterHandler(){
		authStateMachine.start();
		authStateMachine.sendEvent(Events.REGISTER);
		
		Response rsp_401 = mock(Response.class);
		when(rsp_401.getStatusCode()).thenReturn(401);
		when(rsp_401.getHeader(eq(CallIdHeader.NAME))).thenReturn(new CallID("callId"));
		
		ResponseEvent rspEvent = new ResponseEvent(this, mock(ClientTransaction.class), mock(Dialog.class), rsp_401);
		sipHandler.processResponse(rspEvent);
		
		
		Response rsp_200 = mock(Response.class);
		when(rsp_200.getStatusCode()).thenReturn(200);
		when(rsp_200.getHeader(eq(CallIdHeader.NAME))).thenReturn(new CallID("callId"));
		
		rspEvent = new ResponseEvent(null, mock(ClientTransaction.class), mock(Dialog.class), rsp_401);
		sipHandler.processResponse(rspEvent);
	}
}
