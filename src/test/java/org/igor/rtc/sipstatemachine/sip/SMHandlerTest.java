package org.igor.rtc.sipstatemachine.sip;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.sdp.SdpException;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.statemachine.ObjectStateMachine;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class)
public class SMHandlerTest implements ApplicationContextAware{

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
	
	
	
	@Override
	public void setApplicationContext(ApplicationContext ctx) throws BeansException {
		ctx.toString();
	}

	
	@Before
	public void init(){
		stateMachine.start();
	}
	
	@Test
	@Ignore
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
		Semaphore ds = psml.registerForState(States.REGISTERED);
		
		smHandler.register(userName, password, domain);
		
		assertTrue(ds.tryAcquire(5, TimeUnit.MINUTES));
		smHandler.destroy();
		System.exit(0);
	}
	
	
	@Test
	public void testWaitEvents() throws IOException{
		smHandler.register(userName, password, domain);
		
		System.in.read();
	}
	
	@Test
	public void testRinging() throws Exception{
		
		smHandler.register(new RingListener() {
			
			@Override
			public void ringing(Map<String, Object> headers) {
				try {
					smHandler.pickup(headers);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		});
		
		
		
		smHandler.register(userName, password, domain);
		
		
		System.in.read();
		
		
	}
}
