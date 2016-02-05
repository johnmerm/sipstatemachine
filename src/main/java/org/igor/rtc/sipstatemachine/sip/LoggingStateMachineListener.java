package org.igor.rtc.sipstatemachine.sip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.listener.StateMachineListener;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.transition.Transition;

public class LoggingStateMachineListener implements StateMachineListener<States, Events>{
	private static final Logger logger = LoggerFactory.getLogger(StateMachineListener.class);
	
	
	@Override
	public void transitionStarted(Transition<States, Events> transition) {
		logger.trace("transitionStarted",transition);
		
	}
	
	@Override
	public void transitionEnded(Transition<States, Events> transition) {
		logger.trace("transitionEnded",transition);
		
	}
	
	@Override
	public void transition(Transition<States, Events> transition) {
		logger.trace("transition",transition);
		
	}
	
	@Override
	public void stateMachineStopped(StateMachine<States, Events> stateMachine) {
		logger.trace("stateMachineStopped",stateMachine);
		
	}
	
	@Override
	public void stateMachineStarted(StateMachine<States, Events> stateMachine) {
		logger.trace("stateMachineStarted",stateMachine);
		
	}
	
	@Override
	public void stateMachineError(StateMachine<States, Events> stateMachine, Exception exception) {
		logger.trace("stateMachineError",stateMachine);
		
	}
	
	@Override
	public void stateExited(State<States, Events> state) {
		logger.trace("stateExited",state);
		
	}
	
	@Override
	public void stateEntered(State<States, Events> state) {
		logger.trace("stateEntered",state);
		
	}
	
	@Override
	public void stateChanged(State<States, Events> from, State<States, Events> to) {
		logger.trace("stateEntered",from,to);
		
	}
	
	@Override
	public void extendedStateChanged(Object key, Object value) {
		logger.trace("extendedStateChanged",key,value);
		
	}
	
	@Override
	public void eventNotAccepted(Message<Events> event) {
		logger.trace("eventNotAccepted",event);
		
	}

	@Override
	public void stateContext(StateContext<States, Events> stateContext) {
		logger.trace("stateContext",stateContext);
		
	}
}
