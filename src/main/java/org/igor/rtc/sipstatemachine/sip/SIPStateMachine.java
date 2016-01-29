package org.igor.rtc.sipstatemachine.sip;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigBuilder;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

@Configuration
@EnableStateMachine
public class SIPStateMachine extends EnumStateMachineConfigurerAdapter<States,Events> {
	
	@Bean
	public SIPHandler sipHandler(){
		return new SIPHandler(5060);
	}
	
	@Bean
	public SMHandler smHandler(){
		return new SMHandler();
	}
	
	@Bean
	public PromiseStateMachineListener psml(){
		return new PromiseStateMachineListener();
	}
	
	@Override
	public void configure(StateMachineStateConfigurer<States, Events> states) throws Exception {
		states.withStates().initial(States.INIT).states(EnumSet.allOf(States.class));
	}
	
	@Override
	public void configure(StateMachineConfigurationConfigurer<States, Events> config) throws Exception {
		
	}
	
	@Override
	public void configure(StateMachineTransitionConfigurer<States, Events> transitions) throws Exception {
		transitions.withExternal().source(States.INIT).target(States.REGISTERING).event(Events.REGISTER)
		.and().withExternal().source(States.REGISTERING).target(States.REGISTERED).event(Events.OK)
		.and().withExternal().source(States.REGISTERING).target(States.AUTHENTICATING).event(Events.WWW_AUTH)
		.and().withExternal().source(States.AUTHENTICATING).target(States.REGISTERED).event(Events.OK)
		.and().withExternal().source(States.REGISTERING).target(States.ERROR).event(Events.ERROR)
		.and().withExternal().source(States.AUTHENTICATING).target(States.ERROR).event(Events.ERROR);
		
	}

	
	public static void main(String[] args) throws IOException, InterruptedException {
		Properties testProperties = new Properties();
		testProperties.load(SIPStateMachine.class.getResourceAsStream("/test.properties"));
		
		String userName = testProperties.getProperty("sipServer.userName");
		String password = testProperties.getProperty("sipServer.password");
		String domain = testProperties.getProperty("sipServer.domain");
		
		
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(SIPStateMachine.class);
		SMHandler handler = ctx.getBean(SMHandler.class);
		PromiseStateMachineListener psml = ctx.getBean(PromiseStateMachineListener.class);
		Semaphore sm = psml.registerForState(States.AUTHENTICATING);
		Semaphore rm = psml.registerForState(States.REGISTERED);
		handler.register(userName,password, domain);
		
		boolean done = sm.tryAcquire(5, TimeUnit.SECONDS);
		System.out.println(done);
		done = rm.tryAcquire(5, TimeUnit.SECONDS);
		
		System.out.println(done);
		ctx.stop();
		ctx.destroy();
		ctx.close();
		
		
	}
}
