package org.igor.rtc.sipstatemachine.sip;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.PreDestroy;
import javax.sdp.SdpException;
import javax.sdp.SdpParseException;
import javax.sdp.SessionDescription;

import org.igor.rtc.sipstatemachine.sip.media.MediaHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.statemachine.ExtendedState;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.annotation.EventHeaders;
import org.springframework.statemachine.annotation.OnTransition;
import org.springframework.statemachine.annotation.WithStateMachine;
import org.springframework.statemachine.listener.CompositeStateMachineListener;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
@WithStateMachine(name="sipStateMachine")
public class SMHandler {
	
	private Set<RingListener> ringListeners = new HashSet<>();
	private StateMachine<States, Events> sipStateMachine;
	
	public void register(RingListener listener){
		ringListeners.add(listener);
	}
	
	@Autowired
	private SIPHandler sipHandler;
	
	@Autowired
	private PromiseStateMachineListener psml;
	
	@Autowired
	private void setStateMachine(StateMachine<States, Events> sipStateMachine){
		
		CompositeStateMachineListener<States, Events> csml = new CompositeStateMachineListener<>();
		csml.register(psml);
		csml.register(new StateMachineListenerAdapter<States,Events>(){
			@Override
			public void stateEntered(State<States, Events> state) {
				System.err.println("State Entered:"+(state!=null?state.getId():null));
			}
			
			@Override
			public void stateExited(State<States, Events> state) {
				System.err.println("State Exited:"+(state!=null?state.getId():null));
			}
			@Override
			public void stateChanged(State<States, Events> from, State<States, Events> to) {
				
			}
			
			@Override
			public void eventNotAccepted(Message<Events> event) {
				System.err.println("Not accepted event:"+event.getPayload());
			}
		});
		sipStateMachine.addStateListener(csml);
		this.sipStateMachine = sipStateMachine;
	}
	
	
	@Autowired
	private MediaHandler mediaHandler;
	
	
	
	
	@PreDestroy
	public void destroy(){
		sipStateMachine.stop();
		sipHandler.destroy();
		
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
	
	
	@OnTransition(target="RINGING")
	public void ringing(@EventHeaders Map<String,Object> headers, ExtendedState exState) throws SdpException{
		//TODO: send the event that phone is ringing & call picjup if accepted
		ringListeners.forEach(r -> {
			r.ringing(headers);
		});
	}
	
	//TODO: on user accepting call
	public void pickup(Map<String,Object> headers) throws SdpException{
		
		SessionDescription offer = (SessionDescription) headers.get("offer");
		SessionDescription answer = mediaHandler.answer(offer);
		headers = new HashMap<>(headers);
		headers.put("answer", answer);
		sipStateMachine.sendEvent(new GenericMessage<Events>(Events.ANSWER, headers));
	}
	
	@OnTransition(target="CONNECTED")
	public void connect(@EventHeaders Map<String,Object> headers, ExtendedState exState){
		SessionDescription answer = (SessionDescription)headers.get("answer");
		try {
			long sessionId = answer.getOrigin().getSessionId();
			mediaHandler.start(sessionId);
		} catch (SdpParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	
	

}
