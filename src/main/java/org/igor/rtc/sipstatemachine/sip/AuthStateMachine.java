package org.igor.rtc.sipstatemachine.sip;

import java.util.EnumSet;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.DefaultManagedTaskScheduler;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

@Configuration
@EnableStateMachine(name="authStateMachine")
public class AuthStateMachine extends EnumStateMachineConfigurerAdapter<States,Events> {
	@Override
	public void configure(StateMachineStateConfigurer<States, Events> states) throws Exception {
		states.withStates().initial(States.INIT).states(EnumSet.allOf(States.class)).end(States.REGISTERED);
	}
	
	
	
	@Override
	public void configure(StateMachineConfigurationConfigurer<States, Events> config) throws Exception {
		config.withConfiguration().taskScheduler(new DefaultManagedTaskScheduler());
	}
	
	@Override
	public void configure(StateMachineTransitionConfigurer<States, Events> transitions) throws Exception {
		transitions.withExternal().source(States.INIT).target(States.REGISTERING).event(Events.REGISTER)
		.and().withExternal().source(States.REGISTERING).target(States.REGISTERED).event(Events.OK)
		.and().withExternal().source(States.REGISTERING).target(States.AUTHENTICATING).event(Events.WWW_AUTH)
		.and().withExternal().source(States.AUTHENTICATING).target(States.REGISTERED).event(Events.OK)
		.and().withExternal().source(States.REGISTERING).target(States.INIT).event(Events.ERROR)
		.and().withExternal().source(States.AUTHENTICATING).target(States.INIT).event(Events.ERROR);
	}
}
