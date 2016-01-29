package org.igor.rtc.sipstatemachine.sip;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.ConfigurableApplicationContext;
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

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class)
public class SMHandlerTest {

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
	}
	
	
	
	@Value("${sipServer.userName}")
	private String userName;
	
	@Value("${sipServer.password}")
	private String password;
	
	@Value("${sipServer.domain}")
	private String domain;
	
	
	@Autowired
	private StateMachine<States, Events> stateMachine;
	
	@Autowired
	private SMHandler smHandler;
	
	@Autowired
	private PromiseStateMachineListener psml;
	
	@Autowired
	private ConfigurableApplicationContext ctx;
	
	
	@Before
	public void init(){
		stateMachine.start();
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
