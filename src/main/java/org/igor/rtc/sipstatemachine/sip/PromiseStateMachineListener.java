package org.igor.rtc.sipstatemachine.sip;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;

public class PromiseStateMachineListener extends StateMachineListenerAdapter<States, Events>{
	private ConcurrentMap<Semaphore,States> semaphores = new ConcurrentHashMap<>();
	
	public Semaphore registerForState(States state){
		Semaphore lock = new Semaphore(0);
		semaphores.put(lock, state);
		return lock;
	}
	
	@Override
	public synchronized void stateEntered(State<States, Events> state) {
		List<Semaphore> sms = semaphores.entrySet().stream().filter(e->e.getValue() == state.getId()).map(e->e.getKey()).collect(Collectors.toList());
		sms.forEach(s->{
			s.release();
			semaphores.remove(s);
		});
		
		
		
	}

}
