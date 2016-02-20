package org.igor.rtc.sipstatemachine.sip;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Properties;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.ListeningPoint;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.header.CallID;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class)
public class StateMachineTest {

	@Configuration
	@EnableStateMachine
	@Import(SIPStateMachine.class)
	static class Config{
		
		
		
		@Bean
		public PropertyPlaceholderConfigurer ppc(){
			PropertyPlaceholderConfigurer ppc = new PropertyPlaceholderConfigurer();
			
			Resource test = new ClassPathResource("test.properties");
			ppc.setLocation(test);
			return ppc;
		}
		
		@Bean
		public SipFactory mockfactory() throws Exception{
			SipStack mockStack = mock(SipStackImpl.class);
			
			SipProvider mockProvider = mock(SipProvider.class);
			
			when(mockProvider.getNewCallId()).thenAnswer(inv->new CallID(System.currentTimeMillis()+""));
			when(mockProvider.getNewClientTransaction(any(Request.class))).thenAnswer(inv->{
				Request r = inv.getArgumentAt(0, Request.class);
				ClientTransaction c = mock(ClientTransaction.class);
				when(c.getRequest()).thenReturn(r);
				return c;
			});

			when(mockProvider.getNewServerTransaction(any(Request.class))).thenAnswer(inv->{
				Request r = inv.getArgumentAt(0, Request.class);
				ServerTransaction s = mock(ServerTransaction.class);
				when(s.getRequest()).thenReturn(r);
				return s;
			});
			
			when(mockStack.createListeningPoint(anyString(), anyInt(), anyString())).thenAnswer(inv->{
				ListeningPoint mockPoint = mock(ListeningPoint.class);
				String ipAddress = inv.getArgumentAt(0, String.class);
				int port = inv.getArgumentAt(1, Integer.class);
				String protocol = inv.getArgumentAt(2, String.class);
				
				when(mockPoint.getIPAddress()).thenReturn(ipAddress);
				when(mockPoint.getPort()).thenReturn(port);
				when(mockPoint.getTransport()).thenReturn(protocol);
				when(mockPoint.getSentBy()).thenReturn("mockSentBy");
				
				return mockPoint;
			});
			when(mockStack.createSipProvider(any(ListeningPoint.class))).thenReturn(mockProvider);
			
			SipFactory mock = mock(SipFactory.class); 
			
			
			SipFactory real = SipFactory.getInstance();
			
			when(mock.createSipStack(notNull(Properties.class))).thenReturn(mockStack);
			when(mock.createAddressFactory()).thenReturn(real.createAddressFactory());
			when(mock.createHeaderFactory()).thenReturn(real.createHeaderFactory());
			when(mock.createMessageFactory()).thenReturn(real.createMessageFactory());
			return mock;
		}
		
		
		
	}
	
	
	@Value("${sipServer.userName}")
	private String userName;
	
	@Value("${sipServer.password}")
	private String password;
	
	@Value("${sipServer.domain}")
	private String domain;
	
	@Autowired
	private SIPHandler sipHandler;
	
	@Autowired
	private SMHandler smHandler;
	
	
	@Test
	public void dryRun(){
		sipHandler.toString();
	}
	
	@Test
	public void testRegisterHandler(){
		
		smHandler.register(userName, password, domain);
		
		Response rsp_401 = mock(Response.class);
		when(rsp_401.getStatusCode()).thenReturn(401);
		when(rsp_401.getHeader(eq(CallIdHeader.NAME))).thenReturn(new CallID("callId"));
		
		ResponseEvent rspEvent = new ResponseEvent(this, mock(ClientTransaction.class), mock(Dialog.class), rsp_401);
		sipHandler.processResponse(rspEvent);
		
		
		StateMachine<States, Events> stateMachine = sipHandler.getStateMachine("callId");
		assertNotNull(stateMachine);
		assertEquals(States.AUTHENTICATING,stateMachine.getState().getId());
		
		Response rsp_200 = mock(Response.class);
		when(rsp_200.getStatusCode()).thenReturn(200);
		when(rsp_200.getHeader(eq(CallIdHeader.NAME))).thenReturn(new CallID("callId"));
		
		rspEvent = new ResponseEvent(this, mock(ClientTransaction.class), mock(Dialog.class), rsp_200);
		sipHandler.processResponse(rspEvent);
		
		
		
		assertEquals(States.REGISTERED,stateMachine.getState().getId());
	}
}
