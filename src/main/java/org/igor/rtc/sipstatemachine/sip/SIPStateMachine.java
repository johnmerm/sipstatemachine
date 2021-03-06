package org.igor.rtc.sipstatemachine.sip;

import java.util.EnumSet;

import javax.sip.SipFactory;

import org.igor.rtc.sipstatemachine.sip.media.MediaHandler;
import org.igor.rtc.sipstatemachine.sip.media.UDPEchoMediaHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.DefaultManagedTaskScheduler;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

@Configuration
@EnableStateMachineFactory
public class SIPStateMachine extends EnumStateMachineConfigurerAdapter<States,Events> {
	
	@Autowired
	private SipFactory sipfactory;
	
	
	
	@Bean @Autowired
	public SIPHandler sipHandler(){
		return new SIPHandler("tcp",5060,sipfactory);
	}
	
	@Bean @Autowired
	public SIPAuthHandler sipAuthHandler(SIPHandler sipHandler){
		return new SIPAuthHandler(sipHandler);
	}
	
	@Bean @Autowired
	public SIPCallHandler sipCallHandler(SIPHandler sipHandler){
		return new SIPCallHandler(sipHandler);
	}

	@Bean
	public SMHandler smHandler(){
		return new SMHandler();
	}
	
	@Bean
	public MediaHandler mediaHandler(){
		return new UDPEchoMediaHandler();
	}
	
	@Override
	public void configure(StateMachineStateConfigurer<States, Events> states) throws Exception {
		states
			.withStates()
				.initial(States.INIT)
				.states(EnumSet.allOf(States.class));
				
		
				
	}
	
	
	
	@Override
	public void configure(StateMachineConfigurationConfigurer<States, Events> config) throws Exception {
		config.withConfiguration().taskScheduler(new DefaultManagedTaskScheduler());
	}
	
	@Override
	public void configure(StateMachineTransitionConfigurer<States, Events> transitions) throws Exception {
		transitions
				.withExternal().source(States.INIT).target(States.REGISTERING).event(Events.REGISTER)
		.and()	.withExternal().source(States.REGISTERING).target(States.REGISTERED).event(Events.OK)
		.and()	.withExternal().source(States.REGISTERING).target(States.AUTHENTICATING).event(Events.WWW_AUTH)
		.and()	.withExternal().source(States.AUTHENTICATING).target(States.REGISTERED).event(Events.OK)
		.and()	.withExternal().source(States.REGISTERING).target(States.INIT).event(Events.ERROR)
		.and()	.withExternal().source(States.AUTHENTICATING).target(States.INIT).event(Events.ERROR)
		
		
		.and()	.withExternal().source(States.REGISTERED).event(Events.CALL).target(States.CALLING)
		.and()	.withInternal().source(States.CALLING).event(Events.TRYING)
		.and()	.withInternal().source(States.CALLING).event(Events.RINGING)
		
		.and()	.withExternal().source(States.CALLING).event(Events.OK).target(States.ANSWERED)
		.and()	.withExternal().source(States.ANSWERED).event(Events.ACK).target(States.CONNECTED)
		.and()	.withExternal().source(States.ANSWERED).event(Events.HALT).target(States.REGISTERED)
		.and()	.withExternal().source(States.CONNECTED).event(Events.HALT).target(States.REGISTERED)
		
		.and()	.withExternal().source(States.REGISTERED).event(Events.OFFERED).target(States.RINGING)
		.and()	.withExternal().source(States.RINGING).event(Events.ANSWER).target(States.ANSWERING)
		.and()	.withExternal().source(States.ANSWERING).event(Events.ACK).target(States.CONNECTED)
		;
		
	}
}
