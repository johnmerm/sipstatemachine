package org.igor.rtc.sipstatemachine.sip;

import java.util.Map;

public interface RingListener {
	public void ringing(Map<String,Object> headers);
}
