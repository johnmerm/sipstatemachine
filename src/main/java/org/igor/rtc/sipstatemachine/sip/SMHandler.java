package org.igor.rtc.sipstatemachine.sip;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.listener.StateMachineListener;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.transition.Transition;

public class SMHandler {
	
	@Autowired
	private StateMachine<States, Events> sipStateMachine;
	
	@Autowired
	private PromiseStateMachineListener psml;
	
	@PostConstruct
	public void init(){
		sipStateMachine.addStateListener(psml);
		sipStateMachine.start();
	}
	
	@PreDestroy
	public void destroy(){
		sipStateMachine.stop();
	}
	
	//First try unauthenticated register - then process 401 response
	public void register(String user,String passwd,String destAddress){
		Map<String,Object> header = new HashMap<>();
		header.put("user",user);
		header.put("passwd",passwd);
		header.put("destAddress",destAddress);
		
		Message<Events> message = new GenericMessage<Events>(Events.REGISTER,header);
		sipStateMachine.sendEvent(message);
	}

}
