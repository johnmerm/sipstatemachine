package org.igor.rtc.sipstatemachine.sip;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
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

public class SMHandlerTestPOC {

	
	
	
	private String userName;
	private String password;
	private String domain;
	private SMHandler smHandler;
	private PromiseStateMachineListener psml;
	
	@Before
	public void init() throws IOException{
		Properties testProperties = new Properties();
		testProperties.load(getClass().getResourceAsStream("test.properties"));
		
		userName = testProperties.getProperty("sipServer.userName");
		password = testProperties.getProperty("sipServer.password");
		domain = testProperties.getProperty("sipServer.domain");
		
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(SIPStateMachine.class);
		smHandler = ctx.getBean(SMHandler.class);
		psml = ctx.getBean(PromiseStateMachineListener.class);
	}
	
	
	@Test
	public void testRegisterFail() throws InterruptedException{
		Semaphore rs = psml.registerForState(States.REGISTERING);
		Semaphore as = psml.registerForState(States.AUTHENTICATING);
		Semaphore es = psml.registerForState(States.ERROR);
		
		smHandler.register(userName, password+"aaaa", domain);
		assertTrue(rs.tryAcquire(5, TimeUnit.SECONDS));
		assertTrue(as.tryAcquire(5, TimeUnit.SECONDS));
		assertFalse(es.tryAcquire(5, TimeUnit.SECONDS));
	}

	
	@Test
	public void testRegister() throws InterruptedException{
		Semaphore rs = psml.registerForState(States.REGISTERING);
		Semaphore as = psml.registerForState(States.AUTHENTICATING);
		Semaphore ds = psml.registerForState(States.REGISTERED);
		
		smHandler.register(userName, password, domain);
		assertTrue(rs.tryAcquire(5, TimeUnit.SECONDS));
		assertTrue(as.tryAcquire(5, TimeUnit.SECONDS));
		assertTrue(ds.tryAcquire(5, TimeUnit.SECONDS));
	}
}
