package org.igor.rtc.sipstatemachine.sip;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.PreDestroy;
import javax.sdp.SdpException;
import javax.sdp.SdpParseException;
import javax.sdp.SessionDescription;

import org.igor.rtc.sipstatemachine.sip.media.MediaHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.annotation.EventHeaders;
import org.springframework.statemachine.annotation.OnStateEntry;
import org.springframework.statemachine.annotation.OnStateExit;
import org.springframework.statemachine.annotation.WithStateMachine;
import org.springframework.statemachine.listener.CompositeStateMachineListener;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
@WithStateMachine(name="sipStateMachine")
public class SMHandler {
	
	private Set<RingListener> ringListeners = new HashSet<>();
	
	
	
	public void register(RingListener listener){
		ringListeners.add(listener);
	}
	
	@Autowired
	private SIPHandler sipHandler;
	
	@Autowired
	private MediaHandler mediaHandler;
	
	
	
	
	
	
	
	//First try unauthenticated register - then process 401 response
	public void register(String user,String passwd,String destAddress){
		Map<String,Object> header = new HashMap<>();
		header.put("user",user);
		header.put("passwd",passwd);
		header.put("destAddress",destAddress);
		
		Message<Events> message = new GenericMessage<Events>(Events.REGISTER,header);
		
		String tmpKey = "tmp"+System.currentTimeMillis();
		sipHandler.getStateMachine(tmpKey).sendEvent(message);
		
	}
	
	
	@OnStateEntry(target="RINGING")
	public void ringing(@EventHeaders Map<String,Object> headers) throws SdpException{
		//TODO: send the event that phone is ringing & call picjup if accepted
		ringListeners.forEach(r -> {
			r.ringing(headers);
		});
	}
	
	//TODO: on user accepting call
	public void pickup(Map<String,Object> headers) throws SdpException{
		String callID = (String) headers.get("callID");
		
		StateMachine<States, Events> machine = sipHandler.getStateMachine(callID);
		if (machine !=null){
			SessionDescription offer = (SessionDescription) headers.get("offer");
			SessionDescription answer = mediaHandler.answer(callID,offer);
			headers = new HashMap<>(headers);
			headers.put("answer", answer);
			machine.sendEvent(new GenericMessage<Events>(Events.ANSWER, headers));
		}
	}
	
	@OnStateEntry(target="CONNECTED")
	public void connect(@EventHeaders Map<String,Object> headers){
		String callID = (String) headers.get("callID");
		mediaHandler.start(callID);
	}
	
	@OnStateExit(target="CONNECTED")
	public void disconnect(@EventHeaders Map<String,Object> headers){
		String callID = (String) headers.get("callID");
		mediaHandler.stop(callID);
	}
	
	
	
	

}
